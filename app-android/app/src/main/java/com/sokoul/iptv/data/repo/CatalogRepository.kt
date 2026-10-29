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
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

object SpecialCategory {
    const val FAVORITES = "__fav"
    const val RECENT = "__recent"
    const val ALL = "__all"
}

/** Ce que la recherche interroge ; le périmètre par défaut dépend de la page d'où on l'ouvre. */
enum class SearchKind(val label: String, val type: ContentType) {
    MOVIES("Films", ContentType.MOVIE),
    SERIES("Séries", ContentType.SERIES),
    CHANNELS("Chaînes", ContentType.LIVE),
    PROGRAMS("Programmes TV", ContentType.LIVE);

    companion object {
        val ALL: Set<SearchKind> = entries.toSet()

        /** Accueil → films et séries ; Films → films ; Séries → séries ; TV → chaînes et programmes. */
        fun forOrigin(origin: String): Set<SearchKind> = when (origin) {
            SearchOrigin.MOVIES -> setOf(MOVIES)
            SearchOrigin.SERIES -> setOf(SERIES)
            SearchOrigin.TV -> setOf(CHANNELS, PROGRAMS)
            SearchOrigin.HOME -> setOf(MOVIES, SERIES)
            else -> ALL
        }
    }
}

/** Page d'origine de la recherche (paramètre de route). */
object SearchOrigin {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val TV = "tv"
    const val ALL = "all"
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
    private val gate: com.sokoul.iptv.player.ProviderGate,
) {
    private val vodInfoCache = ConcurrentHashMap<String, XtVodInfo>()

    fun categories(pid: Long, type: ContentType): Flow<List<CategoryWithPrefs>> = db.categories().observe(pid, type)

    /** Nombre de chaînes par groupe (identifiant du groupe → nombre). */
    fun channelCounts(pid: Long): Flow<Map<String, Int>> = db.channels().observeCounts(pid).map { l -> l.associate { it.k to it.c } }

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

    /** Profil Enfant : 0 = selon le nom, 1 = toujours visible, 2 = jamais (voir KidsAccess). */
    suspend fun setCategoryKids(pid: Long, type: ContentType, cat: String, kids: Int) {
        val current = db.categories().getPref(pid, type, cat) ?: CategoryPrefEntity(pid, type, cat)
        db.categories().upsertPref(current.copy(kids = kids))
    }

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
        // qualité de cette version de la série : celle du premier épisode mesuré par le panel
        info.episodes.firstNotNullOfOrNull { it.media }?.let { media ->
            val old = db.vod().probes(p.id, ContentType.SERIES, listOf(seriesId)).firstOrNull()
            if (old?.fromPlayer != true) db.vod().saveProbe(media.toProbe(p.id, ContentType.SERIES, seriesId, System.currentTimeMillis()))
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
        xt?.media?.let { media ->
            runCatching {
                val old = db.vod().probes(p.id, ContentType.MOVIE, listOf(movie.id)).firstOrNull()
                if (old?.fromPlayer != true) db.vod().saveProbe(media.toProbe(p.id, ContentType.MOVIE, movie.id, System.currentTimeMillis()))
            }
        }
        // identifiant TMDB : fournisseur, sinon correspondance déjà établie par l'app (rangées, regroupement)
        val tm = tmdb.movie(settings.current().tmdbApiKey, movie.title, movie.year, xt?.tmdbId?.takeIf { it.isNotBlank() } ?: movie.tmdbId?.takeIf { it.isNotBlank() } ?: tmdbHint?.toString())
        return MovieDetails(movie, xt, tm)
    }

    /**
     * Qualité réelle des versions d'un film, mesurée par le panel (get_vod_info, des appels d'API, pas des flux) :
     * une version à la fois, une fois par mois, arrêt au premier échec ou si le fournisseur vient de refuser une connexion.
     * @return nombre de versions nouvellement mesurées
     */
    suspend fun probeMovies(p: PlaylistEntity, movies: List<MovieEntity>): Int {
        if (p.type != PlaylistType.XTREAM || movies.isEmpty()) return 0
        val auth = playlists.auth(p) ?: return 0
        val known = db.vod().probes(p.id, ContentType.MOVIE, movies.map { it.id }).associateBy { it.itemId }
        val now = System.currentTimeMillis()
        var measured = 0
        for (m in movies) {
            val k = known[m.id]
            if (k != null && (k.fromPlayer || now - k.fetchedAt < PROBE_TTL_MS)) continue
            if (gate.remainingMs() > 0) break
            val key = "${p.id}:${m.id}"
            val info = vodInfoCache[key] ?: try {
                xtream.vodInfo(auth, m.id.toLong()) ?: com.sokoul.iptv.parser.XtVodInfo(null, null, null, null, null, null, null, null, null, null, null, null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                timber.log.Timber.i("Mesure des versions interrompue : ${e.javaClass.simpleName}")
                break
            }
            vodInfoCache[key] = info
            // panel muet : on note quand même la tentative pour ne pas redemander avant un mois
            db.vod().saveProbe(info.media?.toProbe(p.id, ContentType.MOVIE, m.id, now) ?: com.sokoul.iptv.data.db.VodProbeEntity(p.id, ContentType.MOVIE, m.id, fetchedAt = now))
            if (info.media != null) measured++
            kotlinx.coroutines.delay(PROBE_PAUSE_MS)
        }
        return measured
    }

    /** Fiche TMDB d'un film sans appel au fournisseur (cache 7 jours) : sert au lecteur (sous-titres en ligne). */
    suspend fun movieTmdb(movie: MovieEntity): TmdbInfo? =
        tmdb.movie(settings.current().tmdbApiKey, movie.title, movie.year, movie.tmdbId?.takeIf { it.isNotBlank() })

    suspend fun seriesTmdb(series: SeriesEntity, tmdbHint: Int? = null): TmdbInfo? =
        tmdb.tv(settings.current().tmdbApiKey, series.title, series.year, series.tmdbId?.takeIf { it.isNotBlank() } ?: tmdbHint?.toString())

    /** Identifiant IMDb d'une série TMDB (cache 30 jours) : la fiche légère ne contient pas les identifiants externes. */
    suspend fun seriesImdb(tmdbId: Int): String? = tmdb.imdbId(settings.current().tmdbApiKey, "tv", tmdbId)

    /** Fiche TMDB par identifiant (images seulement, cache) : titre lu hors abonnement. */
    suspend fun tmdbById(series: Boolean, id: Int): TmdbInfo? =
        tmdb.details(settings.current().tmdbApiKey, if (series) "tv" else "movie", id, full = false)

    /** Épisodes d'une saison en français (titres, résumés, vignettes TMDB). */
    suspend fun tmdbSeason(tvId: Int, season: Int) = tmdb.season(settings.current().tmdbApiKey, tvId, season)

    /**
     * Recherche limitée au périmètre [scope] : seules les requêtes utiles partent
     * (depuis Films, ni les chaînes ni le guide TV ne sont interrogés).
     */
    suspend fun search(pid: Long, query: String, scope: Set<SearchKind> = SearchKind.ALL): SearchResults {
        val trimmed = query.trim().take(MAX_QUERY)
        val q = likeEscape(SyncEngine.searchable(trimmed))
        if (trimmed.length < 2 || scope.isEmpty()) return SearchResults()
        val now = System.currentTimeMillis()
        val channelsByKey = HashMap<String, ChannelEntity?>()
        val locked = lockedSets(pid, scope)
        val lockedLive = locked[ContentType.LIVE].orEmpty()
        return SearchResults(
            channels = if (SearchKind.CHANNELS in scope) db.channels().search(pid, q).filter { it.categoryId !in lockedLive } else emptyList(),
            movies = if (SearchKind.MOVIES in scope) db.movies().search(pid, q).filter { it.categoryId !in locked[ContentType.MOVIE].orEmpty() } else emptyList(),
            series = if (SearchKind.SERIES in scope) db.series().search(pid, q).filter { it.categoryId !in locked[ContentType.SERIES].orEmpty() } else emptyList(),
            programs = if (SearchKind.PROGRAMS !in scope) emptyList() else db.programs().search(pid, likeEscape(trimmed), now, 30).map { pr ->
                pr to channelsByKey.getOrPut(pr.channelKey) { findChannelForKey(pid, pr.channelKey) }
            }.filter { (_, c) -> c == null || c.categoryId !in lockedLive },
        )
    }

    /**
     * Complétion (3 propositions dès 2 lettres) : titres dont un mot commence par la saisie,
     * sur les noms du catalogue (`searchName`).
     */
    suspend fun suggest(pid: Long, query: String, scope: Set<SearchKind> = SearchKind.ALL, limit: Int = 3): List<String> {
        val trimmed = query.trim().take(MAX_QUERY)
        if (trimmed.length < 2) return emptyList()
        val q = likeEscape(SyncEngine.searchable(trimmed))
        val locked = lockedSets(pid, scope)
        val rows = buildList {
            if (SearchKind.MOVIES in scope) db.movies().suggest(pid, q).filter { it.categoryId !in locked[ContentType.MOVIE].orEmpty() }.let(::addAll)
            if (SearchKind.SERIES in scope) db.series().suggest(pid, q).filter { it.categoryId !in locked[ContentType.SERIES].orEmpty() }.let(::addAll)
            if (SearchKind.CHANNELS in scope) db.channels().suggest(pid, q).filter { it.categoryId !in locked[ContentType.LIVE].orEmpty() }.let(::addAll)
        }
        // titres qui commencent par la saisie d'abord, puis l'ordre du catalogue (les plus récents)
        val typed = SyncEngine.searchable(trimmed)
        return rows.map { it.title.trim() }
            .filter { SyncEngine.searchable(it) != typed }
            .distinctBy { SyncEngine.searchable(it) }
            .sortedBy { if (SyncEngine.searchable(it).startsWith(typed)) 0 else 1 }
            .take(limit)
    }

    // Groupes verrouillés : invisibles dans la recherche tant que le code n'a pas été saisi
    private suspend fun lockedSets(pid: Long, scope: Set<SearchKind>): Map<ContentType, Set<String>> {
        if (parental.isOpen()) return emptyMap()
        val types = scope.map { it.type }.toSet()
        return types.associateWith { db.categories().observeLocked(pid, it).first().toSet() }
    }

    companion object {
        const val MAX_QUERY = 100
        const val PROBE_TTL_MS = 30L * 24 * 3600_000
        const val PROBE_PAUSE_MS = 300L

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
