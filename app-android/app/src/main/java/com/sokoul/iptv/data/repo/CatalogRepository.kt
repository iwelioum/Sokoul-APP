package com.sokoul.iptv.data.repo

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
) {
    private val vodInfoCache = ConcurrentHashMap<String, XtVodInfo>()

    fun categories(pid: Long, type: ContentType): Flow<List<CategoryWithPrefs>> = db.categories().observe(pid, type)

    fun channels(pid: Long, categoryId: String): Flow<List<ChannelEntity>> = when (categoryId) {
        SpecialCategory.FAVORITES -> db.channels().observeFavorites(pid)
        SpecialCategory.RECENT -> db.channels().observeRecent(pid)
        SpecialCategory.ALL -> db.channels().observeAllVisible(pid)
        else -> db.channels().observeByCategory(pid, categoryId)
    }

    suspend fun channelList(pid: Long, categoryId: String?): List<ChannelEntity> = when (categoryId) {
        null, SpecialCategory.ALL, SpecialCategory.RECENT -> db.channels().listAllVisible(pid)
        SpecialCategory.FAVORITES -> db.channels().listFavorites(pid).ifEmpty { db.channels().listAllVisible(pid) }
        else -> db.channels().listByCategory(pid, categoryId)
    }

    suspend fun channel(pid: Long, id: String) = db.channels().get(pid, id)
    suspend fun channelByNumber(pid: Long, n: Int) = db.channels().getByNumber(pid, n)

    fun movies(pid: Long, categoryId: String): Flow<List<MovieEntity>> = when (categoryId) {
        SpecialCategory.FAVORITES -> db.movies().observeFavorites(pid)
        SpecialCategory.RECENT, SpecialCategory.ALL -> db.movies().observeRecent(pid, 300)
        else -> db.movies().observeByCategory(pid, categoryId)
    }

    fun recentMovies(pid: Long) = db.movies().observeRecent(pid)
    fun favoriteMovies(pid: Long) = db.movies().observeFavorites(pid)

    fun series(pid: Long, categoryId: String): Flow<List<SeriesEntity>> = when (categoryId) {
        SpecialCategory.FAVORITES -> db.series().observeFavorites(pid)
        SpecialCategory.RECENT, SpecialCategory.ALL -> db.series().observeRecent(pid, 300)
        else -> db.series().observeByCategory(pid, categoryId)
    }

    fun recentSeries(pid: Long) = db.series().observeRecent(pid)
    fun favoriteSeries(pid: Long) = db.series().observeFavorites(pid)

    suspend fun movie(pid: Long, id: String) = db.movies().get(pid, id)
    suspend fun seriesItem(pid: Long, id: String) = db.series().get(pid, id)
    fun observeSeries(pid: Long, id: String) = db.series().observe(pid, id)
    fun episodes(pid: Long, sid: String): Flow<List<EpisodeEntity>> = db.series().observeEpisodes(pid, sid)
    fun seriesDetails(pid: Long, sid: String): Flow<SeriesDetailsEntity?> = db.series().observeDetails(pid, sid)
    suspend fun episode(pid: Long, sid: String, eid: String) = db.series().episode(pid, sid, eid)
    suspend fun episodeList(pid: Long, sid: String) = db.series().episodes(pid, sid)

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
        db.series().deleteEpisodes(p.id, seriesId)
        db.series().upsertEpisodes(episodes)
        val s = info.series
        db.series().upsertDetails(
            SeriesDetailsEntity(
                p.id, seriesId, System.currentTimeMillis(), s?.plot, s?.cast, s?.director, s?.genre, s?.releaseDate, s?.backdrop, s?.trailer,
            )
        )
    }

    suspend fun movieDetails(p: PlaylistEntity, movie: MovieEntity): MovieDetails {
        val key = "${p.id}:${movie.id}"
        val xt = if (p.type == PlaylistType.XTREAM) {
            vodInfoCache[key] ?: runCatching {
                playlists.auth(p)?.let { xtream.vodInfo(it, movie.id.toLong()) }
            }.getOrNull()?.also { vodInfoCache[key] = it }
        } else null
        val tm = tmdb.movie(settings.current().tmdbApiKey, movie.title, movie.year, xt?.tmdbId ?: movie.tmdbId)
        return MovieDetails(movie, xt, tm)
    }

    suspend fun seriesTmdb(series: SeriesEntity): TmdbInfo? =
        tmdb.tv(settings.current().tmdbApiKey, series.title, series.year, series.tmdbId)

    suspend fun search(pid: Long, query: String): SearchResults {
        val q = SyncEngine.searchable(query.trim())
        if (q.length < 2) return SearchResults()
        val now = System.currentTimeMillis()
        val programs = db.programs().search(pid, query.trim(), now, 30)
        val channelsByKey = HashMap<String, ChannelEntity?>()
        return SearchResults(
            channels = db.channels().search(pid, q),
            movies = db.movies().search(pid, q),
            series = db.series().search(pid, q),
            programs = programs.map { pr ->
                pr to channelsByKey.getOrPut(pr.channelKey) { findChannelForKey(pid, pr.channelKey) }
            },
        )
    }

    private suspend fun findChannelForKey(pid: Long, key: String): ChannelEntity? =
        if (key.startsWith(EpgRepository.XT_PREFIX)) db.channels().get(pid, key.removePrefix(EpgRepository.XT_PREFIX))
        else db.channels().byEpgKey(pid, key)
}
