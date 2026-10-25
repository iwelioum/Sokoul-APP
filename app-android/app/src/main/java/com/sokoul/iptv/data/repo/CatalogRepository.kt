package com.sokoul.iptv.data.repo

import androidx.room.withTransaction
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryPrefEntity
import com.sokoul.iptv.data.db.CategoryWithPrefs
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.db.SeriesDetailsEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.XtreamClient
import com.sokoul.iptv.parser.XtVodInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

object SpecialCategory {
    const val FAVORITES = "__fav"
    const val RECENT = "__recent"
    const val ALL = "__all"
}

data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val programs: List<Pair<ProgramEntity, ChannelEntity?>> = emptyList(),
    /** Nombre de versions par id affiché (doublons regroupés). */
    val versions: Map<String, Int> = emptyMap(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty() && programs.isEmpty()
}

data class MovieDetails(val movie: MovieEntity, val xtream: XtVodInfo?, val tmdb: TmdbInfo?)

@Singleton
class CatalogRepository @Inject constructor(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
    private val xtream: XtreamClient,
    private val tmdb: TmdbClient,
    private val settings: SettingsStore,
    private val parental: com.sokoul.iptv.ui.ParentalGate,
) {
    private val vodInfoCache = ConcurrentHashMap<String, XtVodInfo>()

    fun categories(pid: Long, type: ContentType): Flow<List<CategoryWithPrefs>> = db.categories().observe(pid, type)

    fun channels(pid: Long, categoryId: String): Flow<List<ChannelEntity>> = when (categoryId) {
        SpecialCategory.FAVORITES -> hideLocked(pid, ContentType.LIVE, db.channels().observeFavorites(pid)) { it.categoryId }
        SpecialCategory.RECENT -> hideLocked(pid, ContentType.LIVE, db.channels().observeRecent(pid)) { it.categoryId }
        SpecialCategory.ALL -> hideLocked(pid, ContentType.LIVE, db.channels().observeAllVisible(pid)) { it.categoryId }
        else -> db.channels().observeByCategory(pid, categoryId)
    }

    /**
     * Listes transverses (Tout, Favoris, Récents, Accueil) : le contenu des groupes verrouillés
     * n'y apparaît pas tant que le code parental n'a pas été saisi.
     */
    private fun <T> hideLocked(pid: Long, type: ContentType, source: Flow<List<T>>, category: (T) -> String?): Flow<List<T>> =
        combine(source, db.categories().observeLocked(pid, type), parental.unlocked) { list, locked, _ ->
            if (locked.isEmpty() || parental.canAccessLocked()) list
            else locked.toSet().let { set -> list.filter { category(it) !in set } }
        }

    suspend fun channelList(pid: Long, categoryId: String?): List<ChannelEntity> = when (categoryId) {
        null, SpecialCategory.ALL, SpecialCategory.RECENT -> db.channels().listAllVisible(pid)
        // Pas de repli sur toutes les chaînes : zapper dans « Favoris » vide ne doit pas parcourir 28 000 chaînes
        SpecialCategory.FAVORITES -> db.channels().listFavorites(pid)
        else -> db.channels().listByCategory(pid, categoryId)
    }

    suspend fun channel(pid: Long, id: String) = db.channels().get(pid, id)
    suspend fun channelByNumber(pid: Long, n: Int) = db.channels().getByNumber(pid, n)

    fun movies(pid: Long, categoryId: String): Flow<List<MovieEntity>> = when (categoryId) {
        SpecialCategory.FAVORITES -> favoriteMovies(pid)
        SpecialCategory.RECENT, SpecialCategory.ALL -> hideLocked(pid, ContentType.MOVIE, db.movies().observeRecent(pid, 300)) { it.categoryId }
        else -> db.movies().observeByCategory(pid, categoryId)
    }

    fun recentMovies(pid: Long) = hideLocked(pid, ContentType.MOVIE, db.movies().observeRecent(pid)) { it.categoryId }
    fun favoriteMovies(pid: Long) = hideLocked(pid, ContentType.MOVIE, db.movies().observeFavorites(pid)) { it.categoryId }

    fun series(pid: Long, categoryId: String): Flow<List<SeriesEntity>> = when (categoryId) {
        SpecialCategory.FAVORITES -> favoriteSeries(pid)
        SpecialCategory.RECENT, SpecialCategory.ALL -> hideLocked(pid, ContentType.SERIES, db.series().observeRecent(pid, 300)) { it.categoryId }
        else -> db.series().observeByCategory(pid, categoryId)
    }

    fun recentSeries(pid: Long) = hideLocked(pid, ContentType.SERIES, db.series().observeRecent(pid)) { it.categoryId }
    fun favoriteSeries(pid: Long) = hideLocked(pid, ContentType.SERIES, db.series().observeFavorites(pid)) { it.categoryId }

    suspend fun movie(pid: Long, id: String) = db.movies().get(pid, id)
    suspend fun seriesItem(pid: Long, id: String) = db.series().get(pid, id)
    fun observeSeries(pid: Long, id: String) = db.series().observe(pid, id)
    fun episodes(pid: Long, sid: String): Flow<List<EpisodeEntity>> = db.series().observeEpisodes(pid, sid)
    fun seriesDetails(pid: Long, sid: String): Flow<SeriesDetailsEntity?> = db.series().observeDetails(pid, sid)
    suspend fun episode(pid: Long, sid: String, eid: String) = db.series().episode(pid, sid, eid)
    suspend fun episodeList(pid: Long, sid: String) = db.series().episodes(pid, sid)

    suspend fun isCategoryLocked(pid: Long, type: ContentType, cat: String): Boolean =
        !cat.startsWith("__") && db.categories().getPref(pid, type, cat)?.locked == true

    suspend fun setCategoryPref(pid: Long, type: ContentType, cat: String, hidden: Boolean? = null, locked: Boolean? = null) {
        val current = db.categories().getPref(pid, type, cat) ?: CategoryPrefEntity(pid, type, cat)
        db.categories().upsertPref(current.copy(hidden = hidden ?: current.hidden, locked = locked ?: current.locked))
    }

    /** Charge (ou rafraîchit après 12 h) les épisodes d'une série Xtream. */
    suspend fun ensureEpisodes(p: PlaylistEntity, seriesId: String, force: Boolean = false) {
        if (p.type != PlaylistType.XTREAM) return
        val cached = db.series().details(p.id, seriesId)
        val hasEpisodes = db.series().episodes(p.id, seriesId).isNotEmpty()
        if (!force && cached != null && hasEpisodes && System.currentTimeMillis() - cached.fetchedAt < 12 * 3600_000L) return
        val auth = playlists.auth(p) ?: return
        val info = xtream.seriesInfo(auth, seriesId.toLong())
        val episodes = info.episodes.map { e ->
            EpisodeEntity(
                playlistId = p.id, seriesId = seriesId, id = e.id, season = e.season, episode = e.episodeNum, title = e.title,
                plot = e.plot, image = e.image ?: info.seasonCovers[e.season], durationSecs = e.durationSecs,
                extension = e.extension, streamUrl = null, rating = e.rating,
            )
        }
        val s = info.series
        replaceEpisodes(
            db,
            p.id, seriesId, episodes, hasEpisodes,
            SeriesDetailsEntity(
                p.id, seriesId, System.currentTimeMillis(), s?.plot, s?.cast, s?.director, s?.genre, s?.releaseDate, s?.backdrop, s?.trailer,
            ),
        )
    }


    suspend fun movieDetails(p: PlaylistEntity, movie: MovieEntity, tmdbHint: Int? = null): MovieDetails {
        val key = "${p.id}:${movie.id}"
        val xt = if (p.type == PlaylistType.XTREAM) {
            vodInfoCache[key] ?: runCatching {
                playlists.auth(p)?.let { xtream.vodInfo(it, movie.id.toLong()) }
            }.getOrNull()?.also { vodInfoCache[key] = it }
        } else null
        // identifiant TMDB : fournisseur, sinon correspondance déjà établie par l'app (rangées, regroupement)
        val tm = tmdb.movie(settings.current().tmdbApiKey, movie.title, movie.year, xt?.tmdbId?.takeIf { it.isNotBlank() } ?: movie.tmdbId?.takeIf { it.isNotBlank() } ?: tmdbHint?.toString())
        return MovieDetails(movie, xt, tm)
    }

    suspend fun seriesTmdb(series: SeriesEntity, tmdbHint: Int? = null): TmdbInfo? =
        tmdb.tv(settings.current().tmdbApiKey, series.title, series.year, series.tmdbId?.takeIf { it.isNotBlank() } ?: tmdbHint?.toString())

    /** Épisodes d'une saison en français (titres, résumés, vignettes TMDB). */
    suspend fun tmdbSeason(tvId: Int, season: Int) = tmdb.season(settings.current().tmdbApiKey, tvId, season)

    suspend fun search(pid: Long, query: String): SearchResults {
        val trimmed = query.trim().take(MAX_QUERY)
        val q = likeEscape(SyncEngine.searchable(trimmed))
        if (trimmed.length < 2) return SearchResults()
        val now = System.currentTimeMillis()
        val programs = db.programs().search(pid, likeEscape(trimmed), now, 30)
        val channelsByKey = HashMap<String, ChannelEntity?>()
        // Groupes verrouillés : invisibles dans la recherche tant que le code n'a pas été saisi
        val open = parental.isOpen()
        suspend fun locked(type: ContentType) = if (open) emptySet() else db.categories().observeLocked(pid, type).first().toSet()
        val lockedLive = locked(ContentType.LIVE)
        val lockedMovies = locked(ContentType.MOVIE)
        val lockedSeries = locked(ContentType.SERIES)
        return SearchResults(
            channels = db.channels().search(pid, q).filter { it.categoryId !in lockedLive },
            movies = db.movies().search(pid, q).filter { it.categoryId !in lockedMovies },
            series = db.series().search(pid, q).filter { it.categoryId !in lockedSeries },
            programs = programs.map { pr ->
                pr to channelsByKey.getOrPut(pr.channelKey) { findChannelForKey(pid, pr.channelKey) }
            }.filter { (_, c) -> c == null || c.categoryId !in lockedLive },
        )
    }

    companion object {
        const val MAX_QUERY = 100

        /** Échappe les jokers SQL LIKE (`%`, `_`) : « 100% » ne doit pas tout renvoyer. */
        fun likeEscape(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }

    private suspend fun findChannelForKey(pid: Long, key: String): ChannelEntity? =
        if (key.startsWith(EpgRepository.XT_PREFIX)) db.channels().get(pid, key.removePrefix(EpgRepository.XT_PREFIX))
        else db.channels().byEpgKey(pid, key)
}

/**
 * Remplace les épisodes d'une série et la date de mise à jour en une seule transaction : les écrans ne voient
 * jamais une liste vide ou à moitié écrite, même si l'app est tuée ou si l'écriture échoue en cours de route.
 * Une réponse vide (erreur passagère du fournisseur) ne remplace jamais une liste déjà connue.
 * [beforeInsert] sert uniquement aux tests (simuler une panne au milieu de l'écriture).
 */
internal suspend fun replaceEpisodes(
    db: AppDatabase,
    pid: Long, seriesId: String, episodes: List<EpisodeEntity>, hadEpisodes: Boolean, details: SeriesDetailsEntity,
    beforeInsert: suspend () -> Unit = {},
) {
    if (episodes.isEmpty() && hadEpisodes) return
    db.withTransaction {
        db.series().deleteEpisodes(pid, seriesId)
        beforeInsert()
        db.series().upsertEpisodes(episodes)
        db.series().upsertDetails(details)
    }
}
