package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.AppScope
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.TmdbMatchEntity
import com.sokoul.iptv.data.db.TmdbMetaEntity
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.data.net.TmdbItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enrichissement TMDB des œuvres affichées (rangées, héros, fiches) : correspondance titre+année → TMDB,
 * puis fiche française (fond, logo, accroche, saga). Uniquement des appels TMDB, jamais le serveur IPTV.
 * Au plus [PARALLEL] requêtes simultanées, dans l'ordre des demandes (héros d'abord).
 */
@Singleton
class EnrichmentRepository @Inject constructor(
    private val db: AppDatabase,
    private val tmdb: TmdbClient,
    private val settings: SettingsStore,
    private val vod: VodRepository,
) {
    private val gate = Semaphore(PARALLEL)
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val _updates = MutableStateFlow(0L)

    /** Incrémenté à chaque nouvelle métadonnée enregistrée (les écrans se redécorent). */
    val updates: StateFlow<Long> = _updates

    suspend fun apiKey(): String = settings.current().tmdbApiKey

    /** Métadonnées déjà connues, sans réseau. */
    suspend fun known(pid: Long, type: ContentType, keys: Collection<String>): Map<String, TmdbMetaEntity> {
        if (keys.isEmpty()) return emptyMap()
        val dao = db.browse()
        val out = HashMap<String, TmdbMetaEntity>(keys.size * 2)
        val direct = keys.mapNotNull { k -> tmdbIdOfKey(k)?.let { k to it } }
        if (direct.isNotEmpty()) {
            val metas = direct.map { it.second }.distinct().chunked(500).flatMap { dao.metaByIds(type, it) }.associateBy { it.tmdbId }
            direct.forEach { (k, id) -> metas[id]?.let { out[k] = it } }
        }
        keys.filter { tmdbIdOfKey(it) == null }.distinct().chunked(500).forEach { chunk ->
            dao.metaFor(pid, type, chunk).forEach { out[it.workKey] = it.meta }
        }
        return out
    }

    /** Œuvres décorées avec leurs métadonnées connues. */
    suspend fun decorate(pid: Long, works: List<Work>): List<Work> {
        if (works.isEmpty()) return works
        val byType = works.groupBy { it.type }.mapValues { (t, l) -> known(pid, t, l.map { it.key }) }
        return works.map { w -> w.withMeta(byType[w.type]?.get(w.key)) }
    }

    /** Nouvelles fiches écrites ailleurs (indexation) : les écrans se redécorent une fois par lot. */
    fun notifyUpdated() { _updates.value = _updates.value + 1 }

    /** Demande l'enrichissement en arrière-plan ; l'ordre de la liste donne la priorité. */
    fun request(pid: Long, works: List<Work>) {
        if (works.isEmpty()) return
        AppScope.launch {
            val api = apiKey()
            if (api.isBlank()) return@launch
            val todo = works.distinctBy { it.uid }.filter { inFlight.add("$pid|${it.uid}") }
            todo.forEach { w ->
                launch {
                    try {
                        gate.withPermit {
                            // l'écran qui se charge passe avant les cartes à compléter
                            com.sokoul.iptv.player.ScreenLoading.awaitIdle()
                            if (enrichOne(api, pid, w)) _updates.value = _updates.value + 1
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Enrichissement TMDB")
                    } finally {
                        inFlight.remove("$pid|${w.uid}")
                    }
                }
            }
        }
    }

    /** Enrichit une œuvre tout de suite (fiche ouverte) et renvoie ses métadonnées. */
    suspend fun ensure(pid: Long, w: Work): TmdbMetaEntity? {
        val api = apiKey()
        if (api.isNotBlank()) {
            // la fiche ouverte passe devant la file des rangées
            runCatching { if (enrichOne(api, pid, w)) _updates.value = _updates.value + 1 }
                .onFailure { Timber.w(it, "Enrichissement TMDB (fiche)") }
        }
        return known(pid, w.type, listOf(w.key))[w.key]
    }

    /** @return vrai si une métadonnée a été écrite. */
    private suspend fun enrichOne(api: String, pid: Long, w: Work): Boolean {
        val dao = db.browse()
        val now = System.currentTimeMillis()
        val kind = if (w.type == ContentType.MOVIE) "movie" else "tv"
        var id = tmdbIdOfKey(w.key)
        var linked = false
        if (id == null) {
            val m = dao.matches(pid, w.type, listOf(w.key)).firstOrNull()
            if (m != null) {
                if (m.tmdbId == null && now - m.checkedAt < RETRY_MS) return false
                id = m.tmdbId
            }
            if (id == null) {
                id = tmdb.match(api, kind, w.providerTitle, w.year)
                dao.putMatch(TmdbMatchEntity(pid, w.type, w.key, id, now))
                linked = id != null
                if (id == null) return false
            }
        }
        val existing = dao.meta(w.type, id)
        var wrote = false
        // fiche d'avant la v5 (sans mots-clés) : complétée à son prochain affichage
        if (existing == null || existing.fetchedAt < now - STALE_MS || existing.keywords == null) {
            val info = tmdb.details(api, kind, id, full = false) ?: return false
            dao.putMeta(info.toMeta(w.type, now))
            wrote = true
        }
        // les autres versions publiées sous le même identifiant TMDB rejoignent l'œuvre (une fois, à la découverte :
        // réécrire le lien à chaque affichage occupait la base pour rien)
        if ((linked || wrote) && !w.key.startsWith("tmdb:") && !w.key.startsWith("id:")) {
            runCatching { vod.linkTmdb(pid, w.type, w.id, null, w.key, id) }
        }
        // rien de nouveau : pas de redécoration des écrans
        return wrote || linked
    }

    /**
     * Mémorise des correspondances trouvées par une liste TMDB (tendances, saga…) et une fiche partielle
     * (fond, affiche, résumé) quand l'œuvre n'en a pas encore : affichage immédiat, complété plus tard.
     */
    suspend fun remember(pid: Long, type: ContentType, pairs: List<Pair<TmdbItem, String>>) {
        if (pairs.isEmpty()) return
        val dao = db.browse()
        val now = System.currentTimeMillis()
        dao.putMatches(pairs.filter { tmdbIdOfKey(it.second) == null }.map { (item, key) -> TmdbMatchEntity(pid, type, key, item.id, now) })
        for ((item, _) in pairs) {
            if (dao.meta(type, item.id) != null) continue
            dao.putMeta(
                TmdbMetaEntity(
                    type = type, tmdbId = item.id, title = item.title, originalTitle = item.originalTitle,
                    overview = item.overview, overviewLang = item.overview?.let { "fr" }, tagline = null, genres = null,
                    poster = item.poster, backdrop = item.backdrop, logo = null, rating = item.rating, year = item.year,
                    runtimeMin = null, seasons = null, collectionId = null, collectionName = null, certification = null,
                    originalLanguage = item.originalLanguage, genreIds = packIds(item.genreIds), voteCount = item.voteCount,
                    // fiche partielle : sera complétée (logo, accroche, saga) au prochain enrichissement
                    fetchedAt = 0,
                )
            )
        }
    }

    companion object {
        const val PARALLEL = 4
        private const val RETRY_MS = 30L * 24 * 3600_000L
        private const val STALE_MS = 30L * 24 * 3600_000L
    }
}
