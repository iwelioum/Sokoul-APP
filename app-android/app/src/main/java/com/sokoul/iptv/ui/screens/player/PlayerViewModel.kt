package com.sokoul.iptv.ui.screens.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.EpisodeResolver
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.EpgRepository
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.NowNext
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.ResolvedStream
import com.sokoul.iptv.data.repo.StreamResolver
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.player.PlayerFactory
import com.sokoul.iptv.player.ProviderGate
import com.sokoul.iptv.player.StreamPlayer
import com.sokoul.iptv.player.StreamState
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.live.ChannelActions
import com.sokoul.iptv.ui.util.formatRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import com.sokoul.iptv.data.net.providers.Cue
import com.sokoul.iptv.data.net.providers.SubtitleQuery
import com.sokoul.iptv.data.net.providers.SubtitleResult
import com.sokoul.iptv.data.net.providers.SubtitlesClient

/**
 * Piste proposée dans le panneau. [online] : sous-titre trouvé en ligne (téléchargé seulement si choisi) ;
 * [info] : ligne d'état non sélectionnable (« Recherche en ligne… »).
 */
data class TrackOption(
    val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean,
    val online: SubtitleResult? = null, val info: Boolean = false,
)

/** Sous-titres en ligne de l'œuvre en cours (OpenSubtitles, Subdl) ; [enabled] faux sans clé. */
data class OnlineSubs(
    val enabled: Boolean = false,
    val searching: Boolean = false,
    val searched: Boolean = false,
    val results: List<SubtitleResult> = emptyList(),
    val activeRef: String? = null,
    val loadingRef: String? = null,
    val failed: Boolean = false,
)

data class PlayerUi(
    val title: String = "",
    val subtitle: String? = null,
    val image: String? = null,
    val isLive: Boolean = false,
    val channel: ChannelEntity? = null,
    val nowNext: NowNext? = null,
    val isFavorite: Boolean = false,
    val canCatchup: Boolean = false,
    val nextEpisode: EpisodeEntity? = null,
    val resumedFromMs: Long = 0,
    val error: String? = null,
    val videoInfo: String = "",
    /** Contenu d'un groupe verrouillé : le code parental est demandé avant la lecture. */
    val needsPin: Boolean = false,
    val pinError: String? = null,
    /** Message ponctuel (ex. passage automatique à une autre version). */
    val notice: String? = null,
    /** Version en cours (« VF · FHD ») quand l'œuvre en compte plusieurs. */
    val versionLabel: String? = null,
    /** Épisode en cours (saison, numéro), pour le panneau des épisodes. */
    val currentSe: Pair<Int, Int>? = null,
    /** Fin d'épisode sans lecture automatique : la carte « Épisode suivant » reste affichée. */
    val endedWithNext: Boolean = false,
    /** Fond de la série : image de secours de la carte « Épisode suivant » et du panneau des épisodes. */
    val seriesBackdrop: String? = null,
    /** Résumé de l'épisode en cours (écran de pause). */
    val overview: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val factory: PlayerFactory,
    private val settingsStore: SettingsStore,
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val epg: EpgRepository,
    private val resolver: StreamResolver,
    private val library: LibraryRepository,
    private val actions: ChannelActions,
    private val db: AppDatabase,
    private val vod: VodRepository,
    private val gate: ProviderGate,
    private val subtitles: SubtitlesClient,
) : ViewModel() {

    val route: PlayerRoute = savedState.toRoute()

    private val _player = MutableStateFlow<StreamPlayer?>(null)
    val player: StateFlow<StreamPlayer?> = _player.asStateFlow()

    private val _ui = MutableStateFlow(PlayerUi(isLive = route.kind == PlayKind.LIVE))
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    val tracks: StateFlow<Tracks> = _tracks.asStateFlow()

    private val _zapList = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val zapList: StateFlow<List<ChannelEntity>> = _zapList.asStateFlow()

    // --- univers TV : zapping « vraie box », groupes, mini-guide ---
    /** Zapping : bandeau immédiat, flux ouvert après l'arrêt (flux en cours fermé dès le 1er appui). */
    private val zapper = com.sokoul.iptv.ui.screens.tv.Zapper(
        viewModelScope,
        onStart = { nowNextJob?.cancel(); _player.value?.stop() },
        onCommit = { playChannel(it) },
    )
    val zapPending: StateFlow<ChannelEntity?> = zapper.pending
    /** Réglage « Sens du zapping ». */
    var zapInverted = false
        private set
    private val _groups = MutableStateFlow<List<LiveGroup>>(emptyList())
    /** Groupes du direct : favoris et toutes les chaînes d'abord, puis ceux du fournisseur. */
    val groups: StateFlow<List<LiveGroup>> = _groups.asStateFlow()
    private val _group = MutableStateFlow<String?>(null)
    val currentGroup: StateFlow<String?> = _group.asStateFlow()

    private var playlist: PlaylistEntity? = null
    private var previousChannel: ChannelEntity? = null
    private var episodes: List<EpisodeEntity> = emptyList()
    private var currentEpisode: EpisodeEntity? = null
    private var progressJob: Job? = null
    private var nowNextJob: Job? = null
    var autoplayNext = true
        private set

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) { _tracks.value = tracks }
        override fun onVideoSizeChanged(videoSize: VideoSize) = updateVideoInfo()
    }

    init {
        viewModelScope.launch {
            val settings = settingsStore.current()
            autoplayNext = settings.autoplayNextEpisode
            zapInverted = settings.zapInverted
            resizeMode.value = settings.resizeMode
            val sp = StreamPlayer(factory.create(settings), factory, viewModelScope, gate)
            sp.player.addListener(listener)
            _player.value = sp
            watchEnd(sp)
            playlist = playlists.get(route.playlistId)
            startSafely()
        }
    }

    /** Format d'image (index dans les modes du lecteur), mémorisé dans les réglages. */
    val resizeMode = MutableStateFlow(0)

    fun setResizeMode(i: Int) {
        resizeMode.value = i
        viewModelScope.launch { settingsStore.update { it.copy(resizeMode = i) } }
    }

    private var started = false

    private suspend fun startSafely() {
        _ui.update { it.copy(error = null) }
        try {
            start()
            started = true
        } catch (e: Exception) {
            _ui.update { it.copy(error = e.userMessage()) }
        }
    }

    /** Fin de lecture : position finale enregistrée (l'épisode passe « vu »), puis enchaînement. */
    private fun watchEnd(sp: StreamPlayer) {
        viewModelScope.launch {
            sp.state.collect { s ->
                if (s is StreamState.Ended && !_ui.value.isLive) {
                    saveNow()
                    val next = _ui.value.nextEpisode
                    if (next != null && next.id != autoplayCancelledFor) {
                        if (autoplayNext) playNextEpisode() else _ui.update { it.copy(endedWithNext = true) }
                    }
                }
            }
        }
    }

    /** Action mise en attente du code parental. */
    private var pendingAfterPin: (suspend () -> Unit)? = null

    /**
     * Contrôle parental appliqué à la lecture elle-même : couvre les favoris, l'historique, la recherche,
     * l'accueil, le zapping et les notifications, pas seulement la liste des groupes.
     */
    private suspend fun allowed(type: ContentType, categoryId: String?, then: suspend () -> Unit): Boolean {
        if (categoryId == null || !catalog.isCategoryLocked(route.playlistId, type, categoryId) || actions.parental.canAccessLocked()) return true
        pendingAfterPin = then
        _ui.update { it.copy(needsPin = true, pinError = null) }
        return false
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            val r = actions.parental.tryUnlock(pin)
            if (!r.ok) { _ui.update { it.copy(pinError = r.message) }; return@launch }
            _ui.update { it.copy(needsPin = false, pinError = null) }
            val action = pendingAfterPin
            pendingAfterPin = null
            try { action?.invoke() } catch (e: Exception) { _ui.update { it.copy(error = e.userMessage()) } }
        }
    }

    private suspend fun start() {
        if (route.kind == PlayKind.RECORDING) {
            // Un enregistrement reste lisible même si sa playlist a été supprimée.
            val r = db.recordings().get(route.id.toLongOrNull() ?: -1) ?: throw IllegalStateException("Enregistrement introuvable")
            val path = r.filePath?.takeIf { File(it).isFile } ?: throw IllegalStateException("Fichier d'enregistrement introuvable")
            _ui.update { it.copy(title = r.title, subtitle = "${r.channelName} · ${formatRange(r.startAt, r.endAt)}", isLive = false) }
            play(ResolvedStream(listOf(Uri.fromFile(File(path)).toString()), settingsStore.current().userAgent, null, false), r.title, 0)
            return
        }
        val p = playlist ?: throw IllegalStateException("Playlist introuvable")
        when (route.kind) {
            PlayKind.LIVE -> {
                val c = catalog.channel(p.id, route.id) ?: throw IllegalStateException("Chaîne introuvable")
                _group.value = route.categoryId ?: c.categoryId
                viewModelScope.launch { _zapList.value = catalog.channelList(p.id, _group.value) }
                viewModelScope.launch { loadGroups(p.id) }
                playChannel(c)
            }
            PlayKind.CATCHUP -> {
                val c = catalog.channel(p.id, route.id) ?: throw IllegalStateException("Chaîne introuvable")
                if (allowed(ContentType.LIVE, c.categoryId) { playCatchup(c, route.start, route.end) }) playCatchup(c, route.start, route.end)
            }
            PlayKind.MOVIE -> {
                val m = catalog.movie(p.id, route.id) ?: throw IllegalStateException("Film introuvable")
                if (!allowed(ContentType.MOVIE, m.categoryId) { start() }) return
                // Versions de l'œuvre : celle demandée d'abord, puis les autres dans l'ordre de préférence (repli)
                val others = vod.movieVersions(p.id, m).filter { it.id != m.id }
                movieVersions = listOf(m) + vod.orderedMovies(p.id, others)
                movieIndex = 0
                // reprise partagée : la dernière position connue, quelle que soit la version regardée
                val resume = if (route.fromStart) 0 else LibraryRepository.resumeFrom(vod.latestMovieProgress(p.id, movieVersions.map { it.id }))
                playMovie(p, m, resume)
                watchMovieFailures(p)
            }
            PlayKind.EPISODE -> {
                val sid = route.parentId ?: throw IllegalStateException("Série inconnue")
                // liste déjà en cache : une erreur réseau du fournisseur ne bloque pas la lecture
                val cached = catalog.episodeList(p.id, sid).isNotEmpty()
                if (cached) runCatching { catalog.ensureEpisodes(p, sid) } else catalog.ensureEpisodes(p, sid)
                val series = catalog.seriesItem(p.id, sid)
                // toutes les versions de la série : l'épisode suivant peut venir d'une autre version (trou comblé)
                seriesVersionIds = series?.let { s -> listOf(sid) + vod.orderedSeries(p.id, vod.seriesVersions(p.id, s)).map { it.id }.filter { it != sid } } ?: listOf(sid)
                reloadEpisodes(p)
                val e = episodes.firstOrNull { it.id == route.id } ?: catalog.episode(p.id, sid, route.id) ?: throw IllegalStateException("Épisode introuvable")
                if (!allowed(ContentType.SERIES, series?.categoryId) { playEpisode(e, fromStart = route.fromStart) }) return
                playEpisode(e, fromStart = route.fromStart)
            }
            PlayKind.RECORDING -> Unit
        }
    }

    private fun play(stream: ResolvedStream, title: String, startMs: Long) {
        _ui.update { it.copy(error = null, endedWithNext = false) }
        // même œuvre (autre version, nouvelle tentative) : le sous-titre en ligne choisi reste affiché
        val content = when (route.kind) {
            PlayKind.MOVIE -> movieVersions.firstOrNull()?.id
            PlayKind.EPISODE -> currentEpisode?.let { "${it.season}:${it.episode}" }
            else -> title
        }
        if (content != subsContent) { subsContent = content; resetOnlineSubtitles() }
        _player.value?.play(stream, title, startMs)
        updateVideoInfo()
    }

    // --- Films : versions et repli ------------------------------------------------------

    private var movieVersions: List<com.sokoul.iptv.data.db.MovieEntity> = emptyList()
    private var movieIndex = 0
    private var fallbackJob: Job? = null

    private fun versionLabel(m: com.sokoul.iptv.data.db.MovieEntity) =
        com.sokoul.iptv.ui.components.SourceLabels.text(com.sokoul.iptv.domain.PlaybackSources.fromIptv(com.sokoul.iptv.data.repo.VodVersion(m.id, m.name, m.language, m.quality, m.categoryId)))

    private suspend fun playMovie(p: PlaylistEntity, m: com.sokoul.iptv.data.db.MovieEntity, startMs: Long) {
        _ui.update {
            it.copy(
                title = m.title, subtitle = listOfNotNull(m.year?.toString(), m.quality, m.language).joinToString(" · ").ifEmpty { null },
                image = m.poster, isLive = false, resumedFromMs = startMs,
                versionLabel = if (movieVersions.size > 1) versionLabel(m) else null, seriesBackdrop = null, overview = null,
            )
        }
        play(resolver.movie(p, m), m.title, startMs)
        trackProgress { pos, dur -> library.saveProgress(p.id, ContentType.MOVIE, m.id, null, m.title, m.year?.toString(), m.poster, pos, dur) }
    }

    /**
     * Source en échec (retirée, refusée…) : on passe à la version suivante après une pause,
     * une seule tentative par version (pas de rafale de connexions vers le fournisseur).
     */
    private fun watchMovieFailures(p: PlaylistEntity) {
        if (movieVersions.size < 2) return
        fallbackJob?.cancel()
        fallbackJob = viewModelScope.launch {
            val sp = _player.value ?: return@launch
            sp.state.collect { s ->
                // un refus du fournisseur vaut pour tout le compte : changer de version ne servirait qu'à insister
                if (s !is StreamState.Failed || s.refused || movieIndex >= movieVersions.lastIndex) return@collect
                val failed = movieVersions[movieIndex]
                val next = movieVersions[movieIndex + 1]
                _ui.update { it.copy(notice = "Version ${versionLabel(failed)} indisponible, lecture de la version ${versionLabel(next)}…") }
                delay(FALLBACK_PAUSE_MS)
                if (_player.value?.state?.value !is StreamState.Failed) return@collect // relancée entre-temps (Réessayer)
                val pos = sp.player.currentPosition.takeIf { it > 5_000 } ?: _ui.value.resumedFromMs
                movieIndex++
                try { playMovie(p, next, pos) } catch (e: Exception) { _ui.update { it.copy(error = e.userMessage()) } }
                _ui.update { it.copy(notice = null) }
            }
        }
    }

    /** Lance une autre version du film en cours (menu du lecteur). */
    fun switchVersion(id: String) {
        val p = playlist ?: return
        val i = movieVersions.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        viewModelScope.launch {
            saveNow()
            val pos = _player.value?.player?.currentPosition?.takeIf { it > 5_000 } ?: 0
            movieIndex = i
            try { playMovie(p, movieVersions[i], pos) } catch (e: Exception) { _ui.update { it.copy(error = e.userMessage()) } }
        }
    }

    fun movieVersionOptions(): List<Pair<String, String>> = movieVersions.map { it.id to versionLabel(it) }
    fun currentMovieId(): String? = movieVersions.getOrNull(movieIndex)?.id

    // --- Direct -------------------------------------------------------------------------
    fun playChannel(c: ChannelEntity) {
        val p = playlist ?: return
        val current = _ui.value.channel
        if (current != null && current.id != c.id) previousChannel = current
        progressJob?.cancel()
        viewModelScope.launch {
            if (!allowed(ContentType.LIVE, c.categoryId) { playChannel(c) }) {
                _player.value?.stop()
                _ui.update { it.copy(title = c.name, subtitle = null, image = c.logo, isLive = true, channel = c, nowNext = null) }
                return@launch
            }
            val stream = try { resolver.live(p, c) } catch (e: Exception) { _ui.update { it.copy(error = e.userMessage()) }; return@launch }
            _ui.update {
                it.copy(
                    title = c.name, subtitle = null, image = c.logo, isLive = true, channel = c, nowNext = null,
                    canCatchup = resolver.supportsCatchup(p, c), resumedFromMs = 0,
                )
            }
            play(stream, c.name, 0)
            library.markChannelWatched(p.id, c.id, c.name, c.logo)
            settingsStore.update { s -> s.copy(lastChannelId = c.id) }
            refreshFavorite(c)
            watchNowNext(c)
        }
    }

    private fun watchNowNext(c: ChannelEntity) {
        nowNextJob?.cancel()
        nowNextJob = viewModelScope.launch {
            while (isActive) {
                val nn = epg.nowNext(route.playlistId, c)
                _ui.update { if (it.channel?.id == c.id) it.copy(nowNext = nn) else it }
                val wait = nn.now?.end?.minus(System.currentTimeMillis())?.coerceIn(5_000, 60_000) ?: 60_000
                delay(wait)
            }
        }
    }

    private suspend fun refreshFavorite(c: ChannelEntity) {
        val ids = db.library().allFavorites(route.playlistId)
        _ui.update { it.copy(isFavorite = ids.any { f -> f.type == ContentType.LIVE && f.itemId == c.id }) }
    }

    /** ▲▼ / CH± : bandeau tout de suite, flux après [com.sokoul.iptv.ui.screens.tv.ZAP_SETTLE_MS] sans appui. */
    fun zap(delta: Int) = zapper.step(_zapList.value, _ui.value.channel, delta)

    /** ◀ en plein écran : alterner entre les deux dernières chaînes. */
    fun previous() { previousChannel?.let { zapper.select(it) } }

    /** Retour pendant le zapping : on reste sur la chaîne regardée (son flux est rouvert). */
    fun cancelZap(): Boolean {
        if (!zapper.cancel()) return false
        _ui.value.channel?.let { playChannel(it) }
        return true
    }

    private suspend fun loadGroups(pid: Long) {
        val cats = catalog.categories(pid, ContentType.LIVE).first().filter { !it.hidden }
        _groups.value = listOf(
            LiveGroup(com.sokoul.iptv.data.repo.SpecialCategory.FAVORITES, "Favoris"),
            LiveGroup(com.sokoul.iptv.data.repo.SpecialCategory.ALL, "Toutes les chaînes"),
        ) + cats.map { LiveGroup(it.categoryId, it.name) }
    }

    /** Groupe choisi dans la liste des chaînes : la liste et le zapping portent sur lui. */
    fun selectGroup(id: String) {
        val p = playlist ?: return
        viewModelScope.launch {
            _group.value = id
            _zapList.value = catalog.channelList(p.id, id)
        }
    }

    /** Programme en cours de chaque chaîne de la liste (par identifiant de chaîne). */
    suspend fun nowFor(list: List<ChannelEntity>): Map<String, com.sokoul.iptv.data.db.ProgramEntity> =
        runCatching { epg.nowFor(route.playlistId, list) }.getOrDefault(emptyMap())

    /** Mini-guide : programmes de la chaîne regardée sur les prochaines heures. */
    suspend fun upcoming(c: ChannelEntity, hours: Int = 8): List<com.sokoul.iptv.data.db.ProgramEntity> {
        val now = System.currentTimeMillis()
        return runCatching { epg.range(route.playlistId, c, now - 3 * 3_600_000L, now + hours * 3_600_000L) }.getOrDefault(emptyList())
            .filter { it.end > now }.take(8)
    }

    fun jumpToNumber(n: Int) {
        viewModelScope.launch {
            val c = _zapList.value.firstOrNull { it.number == n } ?: catalog.channelByNumber(route.playlistId, n)
            if (c != null) playChannel(c)
        }
    }

    fun toggleFavorite() {
        val c = _ui.value.channel ?: return
        viewModelScope.launch {
            actions.toggleFavorite(route.playlistId, c)
            refreshFavorite(c)
        }
    }

    fun record(minutes: Int, done: (String) -> Unit) {
        val c = _ui.value.channel ?: return
        viewModelScope.launch {
            val nn = _ui.value.nowNext
            val now = nn?.now
            done(
                if (minutes <= 0 && now != null) actions.recordProgram(route.playlistId, c, now)
                else actions.recordNow(route.playlistId, c, minutes.coerceAtLeast(30), now?.title)
            )
        }
    }

    fun addToMultiview(): String? {
        val c = _ui.value.channel ?: return null
        return actions.addToMultiview(route.playlistId, c)
    }

    /** Replay du programme en cours depuis son début. */
    fun restartCurrentProgram() {
        val c = _ui.value.channel ?: return
        val now = _ui.value.nowNext?.now ?: return
        viewModelScope.launch { playCatchup(c, now.start, now.end) }
    }

    private suspend fun playCatchup(c: ChannelEntity, start: Long, end: Long) {
        val p = playlist ?: return
        progressJob?.cancel()
        nowNextJob?.cancel()
        val title = epg.range(p.id, c, start, start + 1).firstOrNull()?.title ?: c.name
        _ui.update { it.copy(title = title, subtitle = "${c.name} · Replay · ${formatRange(start, end)}", image = c.logo, isLive = false, channel = c, nowNext = null) }
        play(resolver.catchup(p, c, start, end), title, 0)
    }

    fun backToLive() { _ui.value.channel?.let { playChannel(it) } }

    // --- Séries ------------------------------------------------------------------------

    /** Versions de la série en cours (celle lancée en tête) : leurs épisodes forment la liste fusionnée. */
    private var seriesVersionIds: List<String> = emptyList()

    private val _episodesFlow = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    /** Épisodes de toutes les versions (un par saison/numéro), pour le panneau « Épisodes » du lecteur. */
    val episodesFlow: StateFlow<List<EpisodeEntity>> = _episodesFlow.asStateFlow()

    private var allSeriesEpisodes: List<EpisodeEntity> = emptyList()

    private val _tmdbEpisodes = MutableStateFlow<Map<Pair<Int, Int>, com.sokoul.iptv.data.net.TmdbEpisode>>(emptyMap())
    /** Titres, résumés et vignettes TMDB (en français) des saisons consultées, comme sur la fiche. */
    val tmdbEpisodes: StateFlow<Map<Pair<Int, Int>, com.sokoul.iptv.data.net.TmdbEpisode>> = _tmdbEpisodes.asStateFlow()
    private var tmdbSeries: com.sokoul.iptv.data.net.TmdbInfo? = null
    private var tmdbSeriesLoaded = false
    private val tmdbLock = kotlinx.coroutines.sync.Mutex()
    private val tmdbSeasons = mutableSetOf<Int>()

    /** Charge (cache 7 jours, comme la fiche) les épisodes TMDB d'une saison ; sans clé ou hors ligne : rien. */
    fun loadTmdbSeason(season: Int) {
        val series = currentSeries ?: return
        viewModelScope.launch {
            val tm = tmdbLock.withLock {
                if (!tmdbSeriesLoaded) {
                    tmdbSeriesLoaded = true
                    tmdbSeries = runCatching { catalog.seriesTmdb(series) }.getOrNull()
                    tmdbSeries?.backdrop?.let { b -> _ui.update { it.copy(seriesBackdrop = b) } }
                }
                tmdbSeries
            } ?: return@launch
            if (!tmdbSeasons.add(season)) return@launch
            val list = runCatching { catalog.tmdbSeason(tm.id, season) }.getOrDefault(emptyList())
            if (list.isEmpty()) return@launch
            _tmdbEpisodes.value = _tmdbEpisodes.value + list.associateBy { it.season to it.episode }
            refreshEpisodeTexts()
        }
    }

    /** Sous-titre et résumé de l'épisode en cours avec les textes TMDB dès qu'ils arrivent. */
    private fun refreshEpisodeTexts() {
        val e = currentEpisode ?: return
        val v = com.sokoul.iptv.data.repo.EpisodeMerge.merge(e, _tmdbEpisodes.value[e.season to e.episode], null)
        _ui.update { if (it.currentSe != e.season to e.episode) it else it.copy(subtitle = seLabel(e.season, e.episode, v.title), overview = v.overview ?: it.overview) }
    }

    private var currentSeries: com.sokoul.iptv.data.db.SeriesEntity? = null

    private suspend fun reloadEpisodes(p: PlaylistEntity) {
        val all = db.vod().episodesIn(p.id, seriesVersionIds)
        episodes = VodRepository.mergeEpisodes(all, seriesVersionIds)
        allSeriesEpisodes = all
        _episodesFlow.value = episodes
    }

    /** Reprise d'un épisode : dernière position connue pour cette saison/épisode, toutes versions confondues. */
    private suspend fun episodeResume(p: PlaylistEntity, e: EpisodeEntity): Long {
        val progress = db.vod().seriesProgressIn(p.id, seriesVersionIds.ifEmpty { listOf(e.seriesId) })
        val bySe = EpisodeResolver.progressBySe(progress, allSeriesEpisodes + e)
        return LibraryRepository.resumeFrom(bySe[e.season to e.episode])
    }

    private suspend fun playEpisode(e: EpisodeEntity, fromStart: Boolean = false) {
        val p = playlist ?: return
        currentEpisode = e
        val series = catalog.seriesItem(p.id, e.seriesId)
        val resume = if (fromStart) 0 else episodeResume(p, e)
        val label = "S%02dE%02d".format(e.season, e.episode)
        val clean = com.sokoul.iptv.data.repo.EpisodeMerge.cleanTitle(e.title, e.episode)
        val title = series?.let { com.sokoul.iptv.data.repo.cleanDisplayTitle(it.title, it.year) } ?: e.title
        _ui.update {
            it.copy(
                title = title, subtitle = seLabel(e.season, e.episode, clean), image = e.image ?: series?.poster,
                isLive = false, channel = null, nextEpisode = EpisodeResolver.nextAfter(episodes, e.season to e.episode),
                resumedFromMs = resume, currentSe = e.season to e.episode, endedWithNext = false,
                seriesBackdrop = tmdbSeries?.backdrop ?: series?.backdrop ?: series?.poster, overview = e.plot?.takeIf { it.isNotBlank() } ?: series?.plot,
            )
        }
        if (series != null) currentSeries = series
        refreshEpisodeTexts()
        loadTmdbSeason(e.season)
        _ui.value.nextEpisode?.season?.takeIf { it != e.season }?.let { loadTmdbSeason(it) }
        play(resolver.episode(p, e), e.title, resume)
        trackProgress { pos, dur ->
            library.saveProgress(
                p.id, ContentType.SERIES, e.id, e.seriesId, series?.title ?: e.title, "$label · ${e.title}", e.image ?: series?.poster,
                pos, dur, season = e.season, episode = e.episode,
            )
        }
    }

    fun playNextEpisode() {
        val next = _ui.value.nextEpisode ?: return
        playEpisodeNow(next)
    }

    /** Changement d'épisode depuis le lecteur (suivant, panneau « Épisodes »). */
    fun playEpisodeNow(e: EpisodeEntity) {
        // appuis répétés (OK + fin de lecture simultanés) : un seul lancement
        if (switching == e.id) return
        switching = e.id
        viewModelScope.launch {
            try {
                saveNow()
                playEpisode(e)
            } catch (ex: Exception) {
                _ui.update { it.copy(error = ex.userMessage()) }
            } finally {
                switching = null
            }
        }
    }

    fun restartFromBeginning() {
        _player.value?.player?.seekTo(0)
        _ui.update { it.copy(resumedFromMs = 0) }
    }

    /** « Annuler » sur la carte « Épisode suivant » : pas d'enchaînement à la fin de cet épisode. */
    fun cancelAutoplay(nextId: String) { autoplayCancelledFor = nextId }
    private var autoplayCancelledFor: String? = null
    private var switching: String? = null

    fun dismissEndCard() = _ui.update { it.copy(endedWithNext = false) }

    // --- Progression -------------------------------------------------------------------

    private var saver: (suspend (Long, Long) -> Unit)? = null

    private fun trackProgress(save: suspend (Long, Long) -> Unit) {
        saver = save
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveNow()
            }
        }
    }

    suspend fun saveNow() {
        val p = _player.value?.player ?: return
        val dur = p.duration
        val pos = p.currentPosition
        if (dur == C.TIME_UNSET || dur <= 0 || pos < 5_000) return
        saver?.invoke(pos, dur)
    }

    /** Pause (bouton, télécommande) : la position est enregistrée tout de suite. */
    fun pauseAndSave() {
        _player.value?.player?.pause()
        viewModelScope.launch { saveNow() }
    }

    /** Application en arrière-plan sans image dans l'image : pause et sauvegarde (direct : connexion fermée). */
    fun onBackground() {
        val sp = _player.value ?: return
        if (_ui.value.isLive) { sp.stop(); return }
        sp.player.pause()
        viewModelScope.launch { saveNow() }
    }

    /** Retour au premier plan après [onBackground] : un direct est relancé (la VOD reste en pause). */
    fun onForeground() {
        val sp = _player.value ?: return
        if (_ui.value.isLive && sp.state.value == StreamState.Idle) sp.retryNow()
    }

    // --- Sous-titres en ligne ---------------------------------------------------------------

    private val _onlineSubs = MutableStateFlow(OnlineSubs())
    val onlineSubs: StateFlow<OnlineSubs> = _onlineSubs.asStateFlow()
    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    /** Sous-titre en ligne choisi, affiché par-dessus la vidéo (sans relancer le flux). */
    val cues: StateFlow<List<Cue>> = _cues.asStateFlow()
    private var subsKey: SubtitleQuery? = null
    private var subsJob: Job? = null
    private var subsContent: String? = null
    /** Sous-titres du flux désactivés avant le choix d'un sous-titre en ligne (état rendu ensuite). */
    private var textDisabledBefore = false

    /** Ouverture du panneau des pistes : une recherche par œuvre (jamais de téléchargement ici). */
    fun searchOnlineSubtitles() {
        if (_ui.value.isLive || route.kind == PlayKind.RECORDING) return
        viewModelScope.launch {
            if (!subtitles.available()) { _onlineSubs.update { it.copy(enabled = false) }; return@launch }
            val q = subtitleQuery() ?: return@launch
            if (q == subsKey && (_onlineSubs.value.searching || _onlineSubs.value.searched)) return@launch
            subsKey = q
            _onlineSubs.value = OnlineSubs(enabled = true, searching = true)
            subsJob?.cancel()
            subsJob = viewModelScope.launch {
                val list = runCatching { subtitles.search(q) }.getOrDefault(emptyList())
                if (subsKey == q) _onlineSubs.update { it.copy(searching = false, searched = true, results = list) }
            }
        }
    }

    private suspend fun subtitleQuery(): SubtitleQuery? = when (route.kind) {
        PlayKind.MOVIE -> movieVersions.getOrNull(movieIndex)?.let { m -> runCatching { catalog.movieTmdb(m) }.getOrNull()?.id }
            ?.let { SubtitleQuery(it, isMovie = true) }
        PlayKind.EPISODE -> {
            val e = currentEpisode
            val series = currentSeries
            val tm = if (e == null || series == null) null else tmdbLock.withLock {
                if (!tmdbSeriesLoaded) { tmdbSeriesLoaded = true; tmdbSeries = runCatching { catalog.seriesTmdb(series) }.getOrNull() }
                tmdbSeries
            }
            if (tm == null || e == null) null else SubtitleQuery(tm.id, isMovie = false, season = e.season, episode = e.episode)
        }
        else -> null
    }

    /** Choix d'un sous-titre en ligne : téléchargé maintenant, puis affiché à la place des sous-titres du flux. */
    private fun pickOnlineSubtitle(r: SubtitleResult) {
        if (_onlineSubs.value.activeRef == r.ref) return
        val key = subsKey
        _onlineSubs.update { it.copy(loadingRef = r.ref, failed = false) }
        viewModelScope.launch {
            val cues = runCatching { subtitles.download(r) }.getOrDefault(emptyList())
            if (subsKey != key) return@launch
            if (cues.isEmpty()) { _onlineSubs.update { it.copy(loadingRef = null, failed = true) }; return@launch }
            _player.value?.player?.let { exo ->
                if (_onlineSubs.value.activeRef == null) textDisabledBefore = C.TRACK_TYPE_TEXT in exo.trackSelectionParameters.disabledTrackTypes
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            }
            _cues.value = cues
            _onlineSubs.update { it.copy(loadingRef = null, activeRef = r.ref) }
        }
    }

    private fun clearOnlineSubtitle() {
        _cues.value = emptyList()
        _onlineSubs.update { it.copy(activeRef = null, loadingRef = null) }
    }

    /** Autre œuvre ou autre épisode : les sous-titres en ligne de la précédente ne valent plus. */
    private fun resetOnlineSubtitles() {
        subsJob?.cancel()
        subsKey = null
        if (_onlineSubs.value.activeRef != null && !textDisabledBefore) {
            _player.value?.player?.let { exo -> exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build() }
        }
        _cues.value = emptyList()
        _onlineSubs.value = OnlineSubs()
    }

    private fun onlineOptions(): List<TrackOption> {
        val s = _onlineSubs.value
        if (!s.enabled) return emptyList()
        return when {
            s.searching -> listOf(TrackOption("Recherche en ligne…", null, -2, false, info = true))
            s.results.isEmpty() -> listOf(TrackOption("Aucun sous-titre en ligne", null, -2, false, info = true))
            else -> s.results.map { r ->
                val label = onlineSubtitleLabel(r) + when (r.ref) { s.loadingRef -> " (téléchargement…)"; else -> "" }
                TrackOption(label, null, -3, r.ref == s.activeRef, online = r)
            } + listOfNotNull(if (s.failed) TrackOption("Téléchargement impossible, essayez un autre", null, -2, false, info = true) else null)
        }
    }

    // --- Pistes ------------------------------------------------------------------------

    fun audioOptions(): List<TrackOption> = trackOptions(C.TRACK_TYPE_AUDIO)
    fun textOptions(): List<TrackOption> =
        listOf(TrackOption("Désactivés", null, -1, _onlineSubs.value.activeRef == null && _tracks.value.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected })) +
            trackOptions(C.TRACK_TYPE_TEXT) + onlineOptions()

    private fun trackOptions(type: Int): List<TrackOption> = _tracks.value.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i -> TrackOption(describe(g.getTrackFormat(i), type), g, i, g.isTrackSelected(i)) }
    }.let(::numberDuplicates)

    /**
     * Choix de piste : appliqué tout de suite et retenu par **langue**, pour que l'épisode
     * ou la version suivante (autres groupes de pistes) garde la même langue.
     */
    fun selectTrack(type: Int, option: TrackOption) {
        if (option.info) return
        option.online?.let { pickOnlineSubtitle(it); return }
        if (type == C.TRACK_TYPE_TEXT) clearOnlineSubtitle()
        val exo: ExoPlayer = _player.value?.player ?: return
        val builder = exo.trackSelectionParameters.buildUpon()
        if (option.group == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            val lang = option.group.getTrackFormat(option.index).language
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
            if (!lang.isNullOrBlank() && lang != C.LANGUAGE_UNDETERMINED) {
                if (type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(lang) else builder.setPreferredTextLanguage(lang)
            }
        }
        exo.trackSelectionParameters = builder.build()
    }

    private fun describe(f: Format, type: Int): String =
        trackLabel(f.language, f.label, if (type == C.TRACK_TYPE_AUDIO) f.channelCount else 0, if (type == C.TRACK_TYPE_AUDIO) f.sampleMimeType else null, type == C.TRACK_TYPE_AUDIO)

    private fun updateVideoInfo() {
        val exo = _player.value?.player ?: return
        val v = exo.videoFormat
        val a = exo.audioFormat
        val info = buildList {
            if (v != null && v.width > 0) add("${v.width}×${v.height}${if (v.frameRate > 0) " · ${v.frameRate.toInt()} i/s" else ""}")
            v?.sampleMimeType?.substringAfter('/')?.uppercase()?.let(::add)
            a?.sampleMimeType?.substringAfter('/')?.uppercase()?.let(::add)
            if (v != null && v.bitrate > 0) add("${v.bitrate / 1000} kb/s")
        }.joinToString(" · ")
        _ui.update { it.copy(videoInfo = info) }
    }

    /** « Réessayer » : relance tout le démarrage si rien n'a pu être chargé, sinon le flux (pause fournisseur respectée). */
    fun retry() {
        if (!started) viewModelScope.launch { startSafely() }
        else {
            _ui.update { it.copy(error = null) }
            _player.value?.retryNow()
        }
    }

    /** Pause imposée par le fournisseur restante (ms), pour le compte à rebours de « Réessayer ». */
    fun providerWaitMs(): Long = gate.remainingMs()

    companion object {
        const val FALLBACK_PAUSE_MS = 4_000L
    }
    fun state(): StateFlow<StreamState>? = _player.value?.state

    override fun onCleared() {
        val sp = _player.value
        val save = saver
        val p = sp?.player
        if (p != null && save != null) {
            val pos = p.currentPosition
            val dur = p.duration
            if (dur > 0 && pos > 5_000) {
                // sauvegarde finale hors du viewModelScope (déjà annulé), sans bloquer le thread principal
                com.sokoul.iptv.data.AppScope.launch { runCatching { save(pos, dur) } }
            }
        }
        sp?.player?.removeListener(listener)
        sp?.release()
        super.onCleared()
    }
}

/**
 * Nom français d'une langue de piste (« fre » / « fr » → « Français »). Null pour les codes sans langue
 * (« und » indéterminée, « mul », « zxx ») ou inconnus : on n'affiche jamais un code brut comme « Und ».
 */
internal fun trackLanguageName(code: String?): String? {
    val c = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it !in setOf("und", "mul", "zxx", "mis", "qaa") } ?: return null
    val name = java.util.Locale(c).getDisplayLanguage(java.util.Locale.FRENCH)
    return name.takeIf { it.isNotBlank() && !it.equals(c, ignoreCase = true) }?.replaceFirstChar { it.uppercase() }
}

/** « S1 · É3 · Titre », sans répéter « Épisode 3 » quand l'épisode n'a pas de vrai titre. */
internal fun seLabel(season: Int, episode: Int, title: String): String =
    if (title == "Épisode $episode") "S$season · É$episode" else "S$season · É$episode · $title"

private val TRACK_FLAGS = listOf(
    Regex("""\b(simplified|simplifié|hans)\b""", RegexOption.IGNORE_CASE) to "simplifié",
    Regex("""\b(traditional|traditionnel|hant)\b""", RegexOption.IGNORE_CASE) to "traditionnel",
    Regex("""\b(brazil(ian)?|brésil|pt-br)\b""", RegexOption.IGNORE_CASE) to "Brésil",
    Regex("""\b(canadian|canada|québec|quebec|fr-ca)\b""", RegexOption.IGNORE_CASE) to "Canada",
    Regex("""\b(latin( american)?|latam|latino|es-419)\b""", RegexOption.IGNORE_CASE) to "Amérique latine",
    Regex("""\b(sdh|cc|malentendants?)\b""", RegexOption.IGNORE_CASE) to "SDH",
    Regex("""\b(forced|forcés?)\b""", RegexOption.IGNORE_CASE) to "forcés",
    Regex("""\b(commentary|commentaires?)\b""", RegexOption.IGNORE_CASE) to "commentaire",
    Regex("""\b(audio ?description|ad)\b""", RegexOption.IGNORE_CASE) to "audiodescription",
)

/**
 * Libellé lisible d'une piste : « Anglais · SDH », « Français · 5.1 · Dolby Digital+ ».
 * Le libellé brut du fichier (« GalaxyTV - small excellence! », « X-MEDIA3-CUES ») n'est jamais affiché tel quel :
 * il sert seulement à repérer SDH / forcés / commentaire, ou de nom quand la langue est inconnue et qu'il est court.
 */
internal fun trackLabel(language: String?, label: String?, channels: Int, mime: String?, audio: Boolean): String {
    val raw = label?.trim().orEmpty()
    val flags = TRACK_FLAGS.filter { (re, _) -> re.containsMatchIn(raw) }.map { it.second }
    val name = trackLanguageName(language)
        ?: raw.takeIf { it.isNotEmpty() && it.length <= 20 && " - " !in it && !it.contains("x-media3", ignoreCase = true) && flags.isEmpty() }
        ?: if (audio) "Piste principale" else "Sous-titres"
    val ch = when (channels) { 1 -> "mono"; 2 -> "stéréo"; 6 -> "5.1"; 8 -> "7.1"; else -> null }
    val codec = when (mime?.lowercase()) {
        "audio/mp4a-latm", "audio/aac" -> "AAC"
        "audio/ac3" -> "Dolby Digital"
        "audio/eac3" -> "Dolby Digital+"
        "audio/eac3-joc" -> "Dolby Atmos"
        "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
        "audio/true-hd" -> "Dolby TrueHD"
        "audio/opus" -> "Opus"
        "audio/mpeg" -> "MP3"
        "audio/flac" -> "FLAC"
        else -> null
    }
    return (listOf(name) + flags + listOfNotNull(ch, codec)).joinToString(" · ")
}

/** Pistes au libellé identique (« Anglais », « Anglais ») : numérotées pour rester distinguables. */
internal fun numberDuplicates(options: List<TrackOption>): List<TrackOption> {
    val counts = options.groupingBy { it.label }.eachCount()
    val seen = HashMap<String, Int>()
    return options.map { o ->
        if ((counts[o.label] ?: 0) < 2) o else o.copy(label = "${o.label} (${seen.merge(o.label, 1, Int::plus)})")
    }
}

/**
 * Sous-titre en ligne : « Français · Subdl · Street Fighter 2026 1080p WEB ». La langue vient du code
 * (« fr », « FR ») ou du nom anglais (« French ») ; la version (nom de fichier) distingue les résultats.
 */
internal fun onlineSubtitleLabel(r: SubtitleResult): String {
    val lang = r.language.trim()
    val name = trackLanguageName(lang.take(3).takeIf { lang.length <= 3 })
        ?: java.util.Locale.getAvailableLocales().firstOrNull { it.getDisplayLanguage(java.util.Locale.ENGLISH).equals(lang, ignoreCase = true) }
            ?.getDisplayLanguage(java.util.Locale.FRENCH)?.replaceFirstChar { it.uppercase() }
        ?: lang.ifEmpty { "Sous-titres" }
    val release = r.release?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?.let { if (Regex("""\.(srt|vtt|ass|sub|zip)$""", RegexOption.IGNORE_CASE).containsMatchIn(r.release)) it else r.release }
        ?.replace(Regex("[._]+"), " ")?.replace(Regex("""\s+"""), " ")?.trim()
    return listOfNotNull(name, if (r.hearingImpaired) "SDH" else null, r.service.label, release?.take(40)).joinToString(" · ")
}

/** Groupe de chaînes proposé dans la liste du direct. */
data class LiveGroup(val id: String, val name: String)
