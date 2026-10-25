package com.sokoul.iptv.data.repo

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.IndexCandidate
import com.sokoul.iptv.data.db.TmdbMatchEntity
import com.sokoul.iptv.data.db.TmdbMetaEntity
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.player.PlaybackActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Part du catalogue dont la fiche TMDB est complète (ou dont on sait qu'elle n'existe pas). */
data class IndexCoverage(val done: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 100 else (done * 100L / total).toInt()
    val complete: Boolean get() = done >= total
}

/** Requêtes de l'indexation, communes aux films et aux séries. */
object IndexQuery {
    private fun table(type: ContentType) = if (type == ContentType.SERIES) "series" else "movies"

    /**
     * Versions à indexer, les plus récemment ajoutées d'abord (une ligne par version : à dédoublonner).
     * Parcourt l'index (playlistId, ajout) et s'arrête à [limit] : jamais de regroupement de tout le catalogue.
     * À faire : fiche absente ou d'avant la v5, sauf recherche déjà faite sans résultat sûr.
     */
    fun pending(pid: Long, type: ContentType, limit: Int): SimpleSQLiteQuery {
        val added = if (type == ContentType.SERIES) "lastModified" else "added"
        val key = "COALESCE(v.groupKey, 'id:' || v.id)"
        return SimpleSQLiteQuery(
            "SELECT $key AS workKey, v.id AS id, v.title AS title, v.year AS year FROM ${table(type)} v " +
                "WHERE v.playlistId = ? " +
                "AND NOT EXISTS (SELECT 1 FROM tmdb_match m LEFT JOIN tmdb_meta t ON t.type = m.type AND t.tmdbId = m.tmdbId " +
                "WHERE m.playlistId = ? AND m.type = ? AND m.workKey = $key AND (m.tmdbId IS NULL OR t.keywords IS NOT NULL)) " +
                "AND NOT EXISTS (SELECT 1 FROM tmdb_meta d WHERE v.groupKey >= 'tmdb:' AND v.groupKey < 'tmdb;' " +
                "AND d.type = ? AND d.tmdbId = CAST(substr(v.groupKey, 6) AS INTEGER) AND d.keywords IS NOT NULL) " +
                "ORDER BY v.$added DESC LIMIT ?",
            arrayOf<Any>(pid, pid, type.name, type.name, limit),
        )
    }

    /** Œuvres terminées : correspondances établies (ou introuvables) et clés « tmdb:N » dont la fiche est complète. */
    fun done(pid: Long, type: ContentType) = SimpleSQLiteQuery(
        "SELECT COUNT(*) FROM (SELECT workKey, tmdbId FROM tmdb_match WHERE playlistId = ? AND type = ? " +
            "UNION SELECT DISTINCT groupKey, CAST(substr(groupKey, 6) AS INTEGER) FROM ${table(type)} " +
            "WHERE playlistId = ? AND groupKey >= 'tmdb:' AND groupKey < 'tmdb;') w " +
            "LEFT JOIN tmdb_meta t ON t.type = ? AND t.tmdbId = w.tmdbId WHERE w.tmdbId IS NULL OR t.keywords IS NOT NULL",
        arrayOf<Any>(pid, type.name, pid, type.name),
    )

    /** Nombre d'œuvres (regroupement complet : coûteux, mis en cache par [CatalogIndexer]). */
    fun total(pid: Long, type: ContentType) = SimpleSQLiteQuery(
        "SELECT COUNT(*) FROM (SELECT 1 FROM ${table(type)} WHERE playlistId = ? GROUP BY COALESCE(groupKey, 'id:' || id))",
        arrayOf<Any>(pid),
    )
}

/**
 * Indexation du catalogue : complète la fiche TMDB (genres, langue, votes, mots-clés) de chaque œuvre,
 * pour l'Explorer, les ambiances et les micro-genres.
 * - 2 requêtes TMDB à la fois, jamais le serveur IPTV ;
 * - écriture par lots ([BATCH] titres ou [FLUSH_MS]) en une seule transaction : les écrans ne sont
 *   prévenus qu'une fois par lot ;
 * - pause complète tant qu'un lecteur tourne (VOD, TV, multi-écran) ;
 * - nouveautés d'abord.
 */
@Singleton
class CatalogIndexer @Inject constructor(
    private val db: AppDatabase,
    private val tmdb: TmdbClient,
    private val settings: SettingsStore,
    private val enrich: EnrichmentRepository,
) {
    // réglables par les tests
    internal var now: () -> Long = System::currentTimeMillis
    internal var awaitIdle: suspend () -> Unit = { PlaybackActivity.awaitIdle(); com.sokoul.iptv.player.ScreenLoading.awaitIdle() }

    // nombre d'œuvres : regroupement de tout le catalogue, recompté au plus toutes les 10 min
    private val totals = HashMap<Long, Pair<Long, Int>>()
    private val totalsLock = Mutex()

    suspend fun coverage(pid: Long): IndexCoverage {
        val b = db.browse()
        val total = totalsLock.withLock {
            totals[pid]?.takeIf { now() - it.first < TOTAL_TTL_MS }?.second
                ?: TYPES.sumOf { b.count(IndexQuery.total(pid, it)) }.also { totals[pid] = now() to it }
        }
        val done = TYPES.sumOf { b.count(IndexQuery.done(pid, it)) }
        return IndexCoverage(done.coerceAtMost(total), total)
    }

    /**
     * Indexe jusqu'à la fin ou jusqu'à [budgetMs] (les tâches de fond Android sont limitées dans le temps).
     * @return vrai si tout le catalogue est indexé.
     */
    suspend fun run(pid: Long, budgetMs: Long = 8 * 60_000L): Boolean {
        val api = settings.current().tmdbApiKey
        if (api.isBlank()) return true
        val start = now()
        val skipped = HashSet<String>() // échec passager (réseau) : retenté à la prochaine tâche
        val done = HashSet<ContentType>()
        while (now() - start < budgetMs) {
            for (type in TYPES) {
                if (type in done) continue
                // ni requête ni écriture pendant la lecture ou le chargement d'un écran (la base reste à l'écran)
                awaitIdle()
                // une ligne par version : marge pour les œuvres à plusieurs versions et les échecs passagers
                val limit = (BATCH + skipped.size) * 4
                val rows = db.browse().indexCandidates(IndexQuery.pending(pid, type, limit))
                val todo = rows.distinctBy { it.workKey }.filter { "$type|${it.workKey}" !in skipped }.take(BATCH)
                if (todo.isEmpty()) {
                    if (rows.size >= limit) return false // que des échecs passagers en tête : réessayé plus tard
                    done += type; continue
                }
                batch(api, pid, type, todo).forEach { skipped += "$type|$it" }
            }
            if (done.size == TYPES.size) return true
        }
        return false
    }

    /** Traite un lot ; renvoie les clés en échec passager. */
    private suspend fun batch(api: String, pid: Long, type: ContentType, todo: List<IndexCandidate>): List<String> = coroutineScope {
        val kind = if (type == ContentType.MOVIE) "movie" else "tv"
        val gate = Semaphore(PARALLEL)
        val lock = Mutex()
        val matches = ArrayList<TmdbMatchEntity>()
        val metas = ArrayList<TmdbMetaEntity>()
        var lastFlush = now()
        var titles = 0
        suspend fun flush() {
            if (matches.isEmpty() && metas.isEmpty()) return
            awaitIdle()
            val m = matches.toList(); val t = metas.toList()
            matches.clear(); metas.clear(); titles = 0
            db.withTransaction {
                if (m.isNotEmpty()) db.browse().putMatches(m)
                if (t.isNotEmpty()) db.browse().putMetas(t)
            }
            lastFlush = now()
            enrich.notifyUpdated()
        }
        val failed = todo.map { c ->
            async {
                gate.withPermit {
                    awaitIdle()
                    val r = runCatching { resolve(api, pid, type, kind, c) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Timber.w(it, "Indexation TMDB") }
                        .getOrNull()
                    lock.withLock {
                        r?.first?.let { matches += it }
                        r?.second?.let { metas += it }
                        if (r != null) titles++
                        if (titles >= BATCH || now() - lastFlush >= FLUSH_MS) flush()
                    }
                    if (r == null || (r.second == null && r.first?.tmdbId != null)) c.workKey else null
                }
            }
        }.awaitAll().filterNotNull()
        lock.withLock { flush() }
        failed
    }

    /** Correspondance (si elle manquait) et fiche complète d'une œuvre. */
    private suspend fun resolve(api: String, pid: Long, type: ContentType, kind: String, c: IndexCandidate): Pair<TmdbMatchEntity?, TmdbMetaEntity?> {
        val t = now()
        var match: TmdbMatchEntity? = null
        val id = tmdbIdOfKey(c.workKey)
            ?: db.browse().matches(pid, type, listOf(c.workKey)).firstOrNull()?.tmdbId
            ?: tmdb.match(api, kind, c.title, c.year, store = false).also { match = TmdbMatchEntity(pid, type, c.workKey, it, t) }
            ?: return match to null
        val info = tmdb.details(api, kind, id, full = false, store = false) ?: return match to null
        return match to info.toMeta(type, t)
    }

    companion object {
        const val BATCH = 45
        const val FLUSH_MS = 12_000L
        const val PARALLEL = 2
        private const val TOTAL_TTL_MS = 10 * 60_000L
        private val TYPES = listOf(ContentType.MOVIE, ContentType.SERIES)
    }
}
