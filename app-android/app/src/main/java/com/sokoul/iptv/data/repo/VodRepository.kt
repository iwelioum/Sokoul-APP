package com.sokoul.iptv.data.repo

import androidx.room.withTransaction
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.GroupRow
import com.sokoul.iptv.data.db.GroupSummary
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.db.TmdbLinkEntity
import com.sokoul.iptv.data.db.VodChoiceEntity
import com.sokoul.iptv.ui.ParentalGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Une carte de grille : la version affichée et le nombre de versions de l'œuvre. */
data class Grouped<T>(val item: T, val versions: Int = 1)

data class CatalogStats(
    val movies: Int, val movieWorks: Int, val moviesWithTmdb: Int,
    val series: Int, val seriesWorks: Int, val seriesWithTmdb: Int,
    val biggestMovies: List<GroupSummary>, val biggestSeries: List<GroupSummary>,
    val pending: Int,
)

/**
 * Regroupement des doublons films/séries : une fiche par œuvre, plusieurs versions (sources).
 * Les lignes du fournisseur ne sont jamais modifiées ni supprimées : seule la colonne `groupKey` est écrite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class VodRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val parental: ParentalGate,
) {
    private val regroupLock = Mutex()

    val groupingOn: Flow<Boolean> = settings.settings.map { it.groupDuplicates }.distinctUntilChanged()

    // --- calcul des groupes ---------------------------------------------------------------

    /** Recalcule les clés d'une playlist ; seules les lignes dont la clé change sont réécrites. */
    suspend fun regroup(pid: Long, type: ContentType) = regroupLock.withLock {
        val dao = db.vod()
        val rows: List<GroupRow> = if (type == ContentType.MOVIE) dao.movieRows(pid) else dao.seriesRows(pid)
        if (rows.isEmpty()) return@withLock 0
        val links = dao.links(pid, type).associate { it.itemId to it.tmdbId }
        val keys = VodGrouping.assign(rows.map { VodGrouping.Row(it.id, it.title, it.year, it.tmdbId?.takeIf(String::isNotBlank) ?: links[it.id]) })
        val changed = rows.filter { keys[it.id] != it.groupKey }
        val table = if (type == ContentType.MOVIE) "movies" else "series"
        // requête compilée, par lots : 159k lignes au premier passage
        changed.chunked(4000).forEach { chunk ->
            db.withTransaction {
                val st = db.openHelper.writableDatabase.compileStatement("UPDATE $table SET groupKey = ? WHERE playlistId = ? AND id = ?")
                for (r in chunk) {
                    st.bindString(1, keys.getValue(r.id)); st.bindLong(2, pid); st.bindString(3, r.id)
                    st.executeUpdateDelete(); st.clearBindings()
                }
                st.close()
            }
        }
        Timber.d("Regroupement $type playlist $pid : ${changed.size} clés mises à jour sur ${rows.size}")
        changed.size
    }

    /** Premier lancement après la mise à jour : calcule les clés absentes (lignes antérieures à la migration). */
    suspend fun regroupIfNeeded() {
        // règles modifiées depuis le dernier calcul : tout est recalculé (seules les clés qui changent sont réécrites)
        val rulesChanged = settings.groupingRulesVersion() < VodGrouping.RULES_VERSION
        for (p in db.playlists().getAll()) {
            if (rulesChanged || db.vod().ungroupedMovies(p.id) > 0) regroup(p.id, ContentType.MOVIE)
            if (rulesChanged || db.vod().ungroupedSeries(p.id) > 0) regroup(p.id, ContentType.SERIES)
        }
        if (rulesChanged) settings.setGroupingRulesVersion(VodGrouping.RULES_VERSION)
    }

    /**
     * Mémorise l'identifiant TMDB trouvé à l'ouverture d'une fiche et rattache l'œuvre aux autres versions
     * portant le même identifiant. Un identifiant fourni par le serveur reste prioritaire.
     */
    suspend fun linkTmdb(pid: Long, type: ContentType, itemId: String, providerTmdb: String?, currentKey: String?, tmdbId: Int?) {
        if (tmdbId == null || tmdbId <= 0 || !providerTmdb.isNullOrBlank()) return
        val dao = db.vod()
        dao.putLink(TmdbLinkEntity(pid, type, itemId, tmdbId.toString()))
        val target = VodGrouping.tmdbKey(tmdbId.toString()) ?: return
        val from = currentKey ?: return
        if (from == target || from.startsWith("tmdb:")) return
        regroupLock.withLock {
            if (type == ContentType.MOVIE) dao.moveMovieGroup(pid, from, target) else dao.moveSeriesGroup(pid, from, target)
        }
    }

    // --- versions d'une œuvre -------------------------------------------------------------

    private suspend fun lockedCategories(pid: Long, type: ContentType): Set<String> =
        if (parental.canAccessLocked()) emptySet() else db.categories().observeLocked(pid, type).first().toSet()

    /** Toutes les versions visibles d'un film (celle demandée en tête si le regroupement est désactivé). */
    suspend fun movieVersions(pid: Long, movie: MovieEntity): List<MovieEntity> {
        val key = movie.groupKey ?: return listOf(movie)
        if (!settings.current().groupDuplicates) return listOf(movie)
        val locked = lockedCategories(pid, ContentType.MOVIE)
        val all = db.vod().movieVersions(pid, key).filter { it.categoryId !in locked }
        return if (all.any { it.id == movie.id }) all else listOf(movie) + all
    }

    suspend fun seriesVersions(pid: Long, series: SeriesEntity): List<SeriesEntity> {
        val key = series.groupKey ?: return listOf(series)
        if (!settings.current().groupDuplicates) return listOf(series)
        val locked = lockedCategories(pid, ContentType.SERIES)
        val all = db.vod().seriesVersions(pid, key).filter { it.categoryId !in locked }
        return if (all.any { it.id == series.id }) all else listOf(series) + all
    }

    suspend fun latestMovieProgress(pid: Long, ids: List<String>) = if (ids.isEmpty()) null else db.vod().latestMovieProgress(pid, ids)
    suspend fun episodesIn(pid: Long, sids: List<String>) = if (sids.isEmpty()) emptyList() else db.vod().episodesIn(pid, sids)
    suspend fun seriesProgressIn(pid: Long, sids: List<String>) = if (sids.isEmpty()) emptyList() else db.vod().seriesProgressIn(pid, sids)
    suspend fun lastEpisodeProgress(pid: Long, sids: List<String>) = if (sids.isEmpty()) null else db.vod().lastEpisodeProgressIn(pid, sids)
    fun observeLatestMovieProgress(pid: Long, ids: List<String>) = db.vod().observeLatestMovieProgress(pid, ids)
    fun observeSeriesProgress(pid: Long, sids: List<String>) = db.vod().observeSeriesProgressIn(pid, sids)
    fun observeEpisodes(pid: Long, sids: List<String>) = db.vod().observeEpisodesIn(pid, sids)
    fun observeAnyFavorite(pid: Long, type: ContentType, ids: List<String>) = db.vod().observeAnyFavorite(pid, type, ids)

    /** « Ma liste » sur l'œuvre : retire toutes les versions favorites, sinon ajoute la version affichée. */
    /** Ajoute ou retire de Ma liste ; true si le titre vient d'être ajouté. */
    suspend fun toggleFavorite(pid: Long, type: ContentType, ids: List<String>, shownId: String): Boolean {
        val favs = db.vod().favoriteAmong(pid, type, ids)
        if (favs.isNotEmpty()) { favs.forEach { db.library().removeFavorite(pid, type, it) }; return false }
        db.library().addFavorite(com.sokoul.iptv.data.db.FavoriteEntity(pid, type, shownId, profileId = db.currentProfileId()))
        return true
    }

    suspend fun clearMovieProgress(pid: Long, ids: List<String>) = ids.forEach { db.library().deleteProgress(pid, ContentType.MOVIE, it) }

    suspend fun categoryNames(pid: Long, type: ContentType): Map<String, String> =
        db.categories().observe(pid, type).first().associate { it.categoryId to it.name }

    suspend fun choice(pid: Long, type: ContentType, key: String?): String? = key?.let { db.vod().choice(pid, type, it) }

    suspend fun setChoice(pid: Long, type: ContentType, key: String?, itemId: String) {
        if (key != null) db.vod().putChoice(VodChoiceEntity(pid, type, key, itemId))
    }

    suspend fun languageOrder(): List<String> = VersionPicker.parseOrder(settings.current().vodLanguages)


    /** Qualité réelle mesurée des versions (absentes : pas encore mesurées). */
    suspend fun qualities(pid: Long, type: ContentType, ids: List<String>): Map<String, VodQuality> =
        if (ids.isEmpty()) emptyMap()
        else ids.chunked(500).flatMap { db.vod().probes(pid, type, it) }.mapNotNull { p -> p.toQuality()?.let { p.itemId to it } }.toMap()

    suspend fun orderedMovies(pid: Long, versions: List<MovieEntity>, failed: Set<String> = emptySet()): List<MovieEntity> {
        if (versions.size <= 1) return versions
        val byId = versions.associateBy { it.id }
        val preferred = choice(pid, ContentType.MOVIE, versions.first().groupKey)
        val q = qualities(pid, ContentType.MOVIE, versions.map { it.id })
        return VersionPicker.order(versions.map { it.toVersion().copy(measured = q[it.id]) }, languageOrder(), preferred, failed, DeviceCaps.supports4k)
            .mapNotNull { byId[it.id] }
    }

    suspend fun orderedSeries(pid: Long, versions: List<SeriesEntity>): List<SeriesEntity> {
        if (versions.size <= 1) return versions
        val byId = versions.associateBy { it.id }
        val preferred = choice(pid, ContentType.SERIES, versions.first().groupKey)
        val q = qualities(pid, ContentType.SERIES, versions.map { it.id })
        return VersionPicker.order(versions.map { it.toVersion().copy(measured = q[it.id]) }, languageOrder(), preferred, emptySet(), DeviceCaps.supports4k)
            .mapNotNull { byId[it.id] }
    }

    // --- grilles ----------------------------------------------------------------------------

    /** Une carte par œuvre (première occurrence conservée : l'ordre de la liste est respecté). */
    fun movieCards(pid: Long, source: Flow<List<MovieEntity>>, limit: Int = Int.MAX_VALUE): Flow<List<Grouped<MovieEntity>>> =
        combine(source, groupingOn) { l, on -> l to on }.mapLatest { (list, on) ->
            if (!on) list.take(limit).map { Grouped(it) }
            else withCounts(dedupe(list) { it.keyOf() }.take(limit), { it.keyOf() }) { keys -> countMovies(pid, keys) }
        }

    fun seriesCards(pid: Long, source: Flow<List<SeriesEntity>>, limit: Int = Int.MAX_VALUE): Flow<List<Grouped<SeriesEntity>>> =
        combine(source, groupingOn) { l, on -> l to on }.mapLatest { (list, on) ->
            if (!on) list.take(limit).map { Grouped(it) }
            else withCounts(dedupe(list) { it.keyOf() }.take(limit), { it.keyOf() }) { keys -> countSeries(pid, keys) }
        }

    suspend fun groupMovies(pid: Long, list: List<MovieEntity>): List<Grouped<MovieEntity>> =
        if (!settings.current().groupDuplicates) list.map { Grouped(it) }
        else withCounts(dedupe(list) { it.keyOf() }, { it.keyOf() }) { keys -> countMovies(pid, keys) }

    suspend fun groupSeries(pid: Long, list: List<SeriesEntity>): List<Grouped<SeriesEntity>> =
        if (!settings.current().groupDuplicates) list.map { Grouped(it) }
        else withCounts(dedupe(list) { it.keyOf() }, { it.keyOf() }) { keys -> countSeries(pid, keys) }

    private suspend fun countMovies(pid: Long, keys: List<String>) =
        keys.chunked(500).flatMap { db.vod().movieCounts(pid, it) }.associate { it.k to it.c }

    private suspend fun countSeries(pid: Long, keys: List<String>) =
        keys.chunked(500).flatMap { db.vod().seriesCounts(pid, it) }.associate { it.k to it.c }

    private suspend fun <T> withCounts(list: List<T>, key: (T) -> String, counts: suspend (List<String>) -> Map<String, Int>): List<Grouped<T>> {
        // seules les clés partagées (tmdb:/t:) peuvent compter plusieurs versions
        val keys = list.map(key).filter { !it.startsWith("id:") }
        val c = if (keys.isEmpty()) emptyMap() else counts(keys)
        return list.map { Grouped(it, c[key(it)] ?: 1) }
    }

    /**
     * « Continuer à regarder » : un film regardé dans plusieurs versions n'apparaît qu'une fois (la plus récente).
     * La liste d'entrée est triée de la plus récente à la plus ancienne.
     */
    suspend fun <T> dedupeProgress(pid: Long, list: List<T>, type: (T) -> ContentType, id: (T) -> String): List<T> {
        if (!settings.current().groupDuplicates || list.size < 2) return list
        val movieIds = list.filter { type(it) == ContentType.MOVIE }.map(id)
        val seriesIds = list.filter { type(it) == ContentType.SERIES }.map(id)
        val mk = if (movieIds.isEmpty()) emptyMap() else db.vod().movieKeys(pid, movieIds.distinct()).associate { it.id to (it.k ?: "id:${it.id}") }
        val sk = if (seriesIds.isEmpty()) emptyMap() else db.vod().seriesKeys(pid, seriesIds.distinct()).associate { it.id to (it.k ?: "id:${it.id}") }
        val seen = HashSet<String>()
        return list.filter {
            val i = id(it)
            val k = if (type(it) == ContentType.MOVIE) "m|" + (mk[i] ?: "id:$i") else "s|" + (sk[i] ?: "id:$i")
            seen.add(k)
        }
    }

    // --- statistiques -----------------------------------------------------------------------

    suspend fun stats(pid: Long): CatalogStats {
        val d = db.vod()
        return CatalogStats(
            movies = db.movies().count(pid), movieWorks = d.movieGroups(pid), moviesWithTmdb = d.moviesWithTmdb(pid),
            series = db.series().count(pid), seriesWorks = d.seriesGroups(pid), seriesWithTmdb = d.seriesWithTmdbCount(pid),
            biggestMovies = d.biggestMovieGroups(pid), biggestSeries = d.biggestSeriesGroups(pid),
            pending = d.ungroupedMovies(pid) + d.ungroupedSeries(pid),
        )
    }

    companion object {
        /**
         * Épisodes d'une série en plusieurs versions : tous ceux de la version principale, puis ceux des autres
         * versions dont la saison/épisode manque encore (aucun trou, aucun doublon entre versions).
         */
        fun mergeEpisodes(all: List<com.sokoul.iptv.data.db.EpisodeEntity>, versionOrder: List<String>): List<com.sokoul.iptv.data.db.EpisodeEntity> {
            val bySeries = all.groupBy { it.seriesId }
            val present = HashSet<Pair<Int, Int>>()
            val out = ArrayList<com.sokoul.iptv.data.db.EpisodeEntity>(all.size)
            val order = versionOrder + (bySeries.keys - versionOrder.toSet())
            for (sid in order) {
                val eps = bySeries[sid] ?: continue
                val added = eps.filter { (it.season to it.episode) !in present }
                out += added
                added.forEach { present += it.season to it.episode }
            }
            return out.sortedWith(compareBy({ it.season }, { it.episode }))
        }

        fun <T> dedupe(list: List<T>, key: (T) -> String): List<T> {
            val seen = HashSet<String>(list.size * 2)
            return list.filter { seen.add(key(it)) }
        }
    }
}

fun MovieEntity.keyOf() = VodGrouping.keyOf(id, groupKey)
fun SeriesEntity.keyOf() = VodGrouping.keyOf(id, groupKey)

fun MovieEntity.toVersion() = VodVersion(id, name, language, quality, categoryId)
fun SeriesEntity.toVersion(): VodVersion {
    val info = com.sokoul.iptv.parser.TitleTagger.parse(name)
    return VodVersion(id, name, info.language, info.quality, categoryId)
}

/** Capacités de décodage de l'appareil (pour ne pas lancer en premier une 4K illisible). */
object DeviceCaps {
    val supports4k: Boolean by lazy {
        runCatching {
            val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { t -> t.equals("video/hevc", true) || t.equals("video/avc", true) } &&
                    info.supportedTypes.any { t ->
                        runCatching { info.getCapabilitiesForType(t).videoCapabilities?.isSizeSupported(3840, 2160) == true }.getOrDefault(false)
                    }
            }
        }.getOrDefault(true)
    }

    /** Décodeur Dolby Vision présent : sinon une version DV passe après une HDR10 de même définition. */
    val dolbyVision: Boolean by lazy {
        runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("video/dolby-vision", true) }
            }
        }.getOrDefault(true)
    }

    /**
     * Télé capable d'afficher le HDR (lu au lancement) : sinon une version SDR passe devant une HDR de même
     * définition, l'image HDR convertie par la box étant souvent terne (Fire TV branché sur une télé SDR).
     */
    @Volatile var hdrDisplay: Boolean = true
        private set

    /**
     * Appareil modeste (Fire TV Stick, box d'entrée de gamme : 192 Mo par app ou moins) : multi-écran réduit,
     * caches d'images et pause du direct plus petits.
     */
    @Volatile var lowMemory: Boolean = false
        private set

    fun read(context: android.content.Context) {
        hdrDisplay = runCatching {
            val dm = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty()
        }.getOrNull() ?: true
        lowMemory = runCatching {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            am.isLowRamDevice || am.memoryClass <= LOW_MEMORY_CLASS_MB
        }.getOrDefault(false)
    }

    private const val LOW_MEMORY_CLASS_MB = 256
}
