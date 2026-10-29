package com.sokoul.iptv.ui.screens.player

import com.sokoul.iptv.player.timeshift.LiveTimeshift
import com.sokoul.iptv.player.timeshift.TimeshiftPolicy
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import com.sokoul.iptv.data.repo.toVersion
import com.sokoul.iptv.data.repo.toQuality
import timber.log.Timber
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

/** État de la recherche Real-Debrid affiché en tête du panneau des versions. */
/** Recherche Real-Debrid : non configuré, en cours, fichiers du compte seulement (pas d'extension de recherche), terminée. */
enum class SourceSearch { OFF, SEARCHING, ACCOUNT, DONE }

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
    /** « Ça coupe ? » : version plus légère proposée (identifiant, libellé). */
    val lighter: Pair<String, String>? = null,
    /** Toutes les sources de l'œuvre, toutes origines classées ensemble, la source en cours marquée. */
    val sources: List<PlayerSource> = emptyList(),
    /** Recherche des sources hors abonnement (Real-Debrid) : désactivée, en cours ou terminée. */
    val searchState: SourceSearch = SourceSearch.DONE,
    /** Ordre des langues des Réglages (sections du panneau « Langue et qualité »). */
    val languages: List<String> = com.sokoul.iptv.data.repo.VersionPicker.DEFAULT_LANGUAGES,
    /** Ce que l'on regarde, mesuré sur le flux : « 1080p · HEVC · HDR10 · VF · IPTV ». */
    val nowPlaying: String? = null,
    /** Lancement en cours, jusqu'à la première image : ce qui se passe (« Recherche de la meilleure version… »). */
    val starting: String? = null,
    /** Logo TMDB de l'œuvre et fond, affichés pendant le lancement. */
    val logo: String? = null,
    val backdrop: String? = null,
    /** « Décalage du son » de la séance : le son est retardé de tant de millisecondes (0 = aucun). */
    val audioDelayMs: Long = 0,
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
    private val recorder: com.sokoul.iptv.recording.RecordingScheduler,
    private val debrid: com.sokoul.iptv.data.debrid.DebridSources,
    private val rdResolver: com.sokoul.iptv.data.debrid.DebridResolver,
    private val cloud: com.sokoul.iptv.data.debrid.DebridCloud,
    private val trakt: com.sokoul.iptv.data.trakt.TraktSync,
    private val browse: com.sokoul.iptv.data.repo.BrowseRepository,
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
        override fun onTracksChanged(tracks: Tracks) { _tracks.value = tracks; if (!tracks.isEmpty) updateVideoInfo() }
        override fun onVideoSizeChanged(videoSize: VideoSize) = updateVideoInfo()
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) onSeek(oldPosition.positionMs, newPosition.positionMs)
        }
    }

    init {
        viewModelScope.launch {
            val settings = settingsStore.current()
            autoplayNext = settings.autoplayNextEpisode
            zapInverted = settings.zapInverted
            timeshiftAllowed = settings.timeshift
            defaultDecoder = settings.decoderMode
            resizeMode.value = settings.resizeMode
            val sp = StreamPlayer(factory.create(settings), factory, viewModelScope, gate)
            sp.player.addListener(listener)
            _player.value = sp
            watchEnd(sp)
            watchStutter(sp)
            watchFirstFrame(sp)
            if (!_ui.value.isLive) startingStatus(if (route.kind in WORK_KINDS) "Recherche de la meilleure version…" else "Chargement…")
            playlist = playlists.get(route.playlistId) ?: if (route.kind == PlayKind.CLOUD || route.kind == PlayKind.DEBRID) playlists.activeNow() else null
            startSafely()
        }
    }

    /** Format d'image (index dans les modes du lecteur), mémorisé dans les réglages. */
    val resizeMode = MutableStateFlow(0)

    /** Réglages en direct (apparence des sous-titres) : changés dans Réglages, appliqués sans rouvrir le flux. */
    val settings: StateFlow<com.sokoul.iptv.data.AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, com.sokoul.iptv.data.AppSettings())

    // --- vitesse de lecture et minuterie (VOD) -------------------------------------------------

    private val _speed = MutableStateFlow(1f)
    /** Vitesse de lecture de la séance (films et séries ; le direct reste à ×1). */
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun setSpeed(f: Float) {
        _speed.value = f
        _player.value?.player?.setPlaybackSpeed(f)
    }

    /** Son en avance sur l'image : retardé de [ms] (séance en cours ; changement de lecteur compris). */
    fun setAudioDelay(ms: Long) {
        _ui.update { it.copy(audioDelayMs = ms) }
        _player.value?.player?.let { factory.setAudioDelay(it, ms) }
    }

    private val _sleep = MutableStateFlow<SleepTimer.State?>(null)
    val sleepTimer: StateFlow<SleepTimer.State?> = _sleep.asStateFlow()
    private val _sleepDone = MutableStateFlow(false)
    /** Minuterie écoulée : lecture en pause et position enregistrée ; l'écran ferme le lecteur. */
    val sleepDone: StateFlow<Boolean> = _sleepDone.asStateFlow()
    private var sleepJob: Job? = null

    /** null : minuterie arrêtée. */
    fun setSleepTimer(choice: SleepTimer.Choice?) {
        sleepJob?.cancel()
        if (choice == null) { _sleep.value = null; return }
        val s = SleepTimer.start(choice, System.currentTimeMillis())
        _sleep.value = s
        val due = s.dueAt ?: return // fin de l'épisode : déclenchée par la fin de lecture (watchEnd)
        sleepJob = viewModelScope.launch {
            while (System.currentTimeMillis() < due) delay(minOf(30_000L, due - System.currentTimeMillis()).coerceAtLeast(1))
            sleepNow()
        }
    }

    private fun sleepNow() {
        _sleep.value = null
        if (_ui.value.isLive) _player.value?.stop() else pauseAndSave()
        _sleepDone.value = true
    }

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
            _ui.update { it.copy(error = e.userMessage(), starting = null) }
        }
    }

    /** Écran de lancement (logo) retiré à la première image, ou dès qu'une erreur s'affiche. */
    private fun watchFirstFrame(sp: StreamPlayer) {
        viewModelScope.launch {
            sp.state.collect { s ->
                if (_ui.value.starting != null && (s is StreamState.Playing || s is StreamState.Failed || s is StreamState.Busy || s is StreamState.Waiting)) {
                    _ui.update { it.copy(starting = null) }
                }
            }
        }
    }

    private fun startingStatus(text: String) = _ui.update { it.copy(starting = text) }

    /** Logo et fond de l'œuvre (fiche TMDB en cache) pour l'écran de lancement. */
    private fun showArt(info: com.sokoul.iptv.data.net.TmdbInfo?) {
        if (info == null) return
        _ui.update { it.copy(logo = info.logo ?: it.logo, backdrop = info.backdrop ?: it.backdrop) }
    }

    /**
     * Lecture sans version imposée : attend au plus [StartPick.WAIT_MS] la recherche Real-Debrid, puis renvoie la clé
     * Real-Debrid si elle passe devant l'IPTV dans le classement commun ; null : la version IPTV demandée part.
     */
    private suspend fun bestDebridAtStart(query: suspend () -> com.sokoul.iptv.data.debrid.DebridQuery?): String? {
        if (!debrid.enabled()) { viewModelScope.launch { setDebridQuery(query()) }; return null }
        val found = kotlinx.coroutines.withTimeoutOrNull(StartPick.WAIT_MS) { setDebridQuery(query()); debridStreamsNow() }
        if (found == null) {
            if (debridQuery == null) viewModelScope.launch { setDebridQuery(query()) }
            com.sokoul.iptv.data.debrid.SourceLog.add("Lecture : recherche ${com.sokoul.iptv.ui.components.SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID)} trop longue, lancement de la meilleure version connue")
            return null
        }
        return StartPick.debridFirst(buildSources())
    }

    /** Fin de lecture : position finale enregistrée (l'épisode passe « vu »), puis enchaînement. */
    private fun watchEnd(sp: StreamPlayer) {
        viewModelScope.launch {
            sp.state.collect { s ->
                if (s is StreamState.Ended && !_ui.value.isLive) {
                    saveNow()
                    // minuterie « fin de l'épisode » : on s'arrête là, sans enchaîner
                    if (_sleep.value?.choice == SleepTimer.Choice.EndOfItem) { sleepNow(); return@collect }
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
        _ui.update { it.copy(needsPin = true, pinError = null, starting = null) }
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
                // fiche TMDB en cache : logo de l'écran de lancement et œuvre à chercher sur Real-Debrid
                val info = viewModelScope.async { runCatching { catalog.movieTmdb(m) }.getOrNull().also(::showArt) }
                val tmdbQuery: suspend () -> com.sokoul.iptv.data.debrid.DebridQuery? = {
                    route.query?.let(com.sokoul.iptv.data.debrid.DebridQuery::decode)
                        ?: info.await()?.id?.let { com.sokoul.iptv.data.debrid.DebridQuery(it, series = false) }
                }
                // version Real-Debrid choisie sur la fiche, ou connexion IPTV occupée (enregistrement, pause après refus)
                if (route.source != null || iptvBlocked()) {
                    setDebridQuery(tmdbQuery())
                    movieUi(m, resume)
                    if (playDebrid(m.title, resume, route.source, movieSaver(p, m), prepare = true)) {
                        if (route.source == null) _ui.update { it.copy(notice = iptvBusyNotice()) }
                        watchFailures()
                        return
                    }
                }
                // la meilleure version part directement (attente bornée de la recherche Real-Debrid)
                movieUi(m, resume)
                val best = bestDebridAtStart(tmdbQuery)
                if (best != null && playDebrid(m.title, resume, best, movieSaver(p, m), single = true)) {
                    watchFailures()
                    return
                }
                playMovie(p, m, resume)
                watchFailures()
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
            PlayKind.CLOUD -> {
                val title = route.title ?: com.sokoul.iptv.ui.components.SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID)
                val itemId = com.sokoul.iptv.data.debrid.DebridCloud.KEY_PREFIX + route.id
                val resume = if (route.fromStart) 0 else LibraryRepository.resumeFrom(library.progress(p.id, ContentType.MOVIE, itemId))
                _ui.update { it.copy(title = title, subtitle = "Mes fichiers " + com.sokoul.iptv.ui.components.SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID), isLive = false, resumedFromMs = resume, seriesBackdrop = null, overview = null) }
                val url = cloud.playable(route.id)
                // image TMDB trouvée pour la rangée (liste en cache) : reprise illustrée sur l'accueil
                cloudItem = runCatching { cloud.items().firstOrNull { it.id == route.id } }.getOrNull()
                val art = cloudItem
                    ?.let { i -> (i.backdrop ?: i.poster)?.let { com.sokoul.iptv.data.repo.tmdbSized(it, "w780") } }
                if (art != null) _ui.update { it.copy(image = art) }
                play(ResolvedStream(listOf(url), settingsStore.current().userAgent, null, isLive = false, provider = false), title, resume)
                trackProgress { pos, dur -> library.saveProgress(p.id, ContentType.MOVIE, itemId, null, title, com.sokoul.iptv.ui.components.SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID), art, pos, dur) }
            }
            PlayKind.DEBRID -> {
                // titre absent de l'abonnement : uniquement Real-Debrid
                val q = com.sokoul.iptv.data.debrid.DebridQuery.decode(route.query ?: route.id) ?: throw IllegalStateException("Titre inconnu")
                val title = route.title ?: com.sokoul.iptv.ui.components.SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID)
                val itemId = "tmdb:" + q.encode()
                val resume = if (route.fromStart) 0 else LibraryRepository.resumeFrom(library.progress(p.id, if (q.series) ContentType.SERIES else ContentType.MOVIE, itemId))
                _ui.update {
                    it.copy(
                        title = title, subtitle = if (q.series) seLabel(q.season ?: 0, q.episode ?: 0, "") else null,
                        isLive = false, resumedFromMs = resume, seriesBackdrop = null, overview = null,
                    )
                }
                viewModelScope.launch { showArt(runCatching { catalog.tmdbById(q.series, q.tmdbId) }.getOrNull()) }
                setDebridQuery(q)
                // film aussi dans l'abonnement (sous un autre nom) : ses versions IPTV rejoignent la liste et le secours
                if (!q.series) loadIptvTwin(p, q.tmdbId)
                if (!playDebrid(title, resume, route.source, debridOnlySaver(), prepare = true)) {
                    // aucune version Real-Debrid lisible : l'abonnement prend le relais s'il a le film
                    val twin = movieVersions.firstOrNull()
                    if (twin == null || iptvBlocked()) throw IllegalStateException("Aucune version " + com.sokoul.iptv.ui.components.SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID) + " prête pour ce titre" + (lastRdError?.let { " (dernière erreur : $it)" } ?: ""))
                    movieIndex = 0
                    playMovie(p, twin, resume)
                }
                watchFailures()
            }
            PlayKind.RECORDING -> Unit
        }
    }

    private fun play(stream: ResolvedStream, title: String, startMs: Long) {
        if (timeshift.active) stopTimeshift(backToNetwork = false)
        lastPlay = Triple(stream, title, startMs)
        _ui.update { it.copy(error = null, endedWithNext = false) }
        // même œuvre (autre version, nouvelle tentative) : le sous-titre en ligne choisi reste affiché
        val content = when (route.kind) {
            PlayKind.MOVIE -> movieVersions.firstOrNull()?.id
            PlayKind.EPISODE -> currentEpisode?.let { "${it.season}:${it.episode}" }
            else -> title
        }
        if (content != subsContent) { subsContent = content; resetOnlineSubtitles(); loadIntro() }
        stutters = 0
        lighterOffered = false
        if (_ui.value.lighter != null) _ui.update { it.copy(lighter = null) }
        nowVideo = null
        _player.value?.play(stream, title, startMs)
        updateVideoInfo()
        refreshSources()
    }

    private var lastPlay: Triple<ResolvedStream, String, Long>? = null

    // --- Pause du direct (timeshift) ---------------------------------------------------

    private var timeshiftAllowed = true
    val timeshift = LiveTimeshift(viewModelScope, newBuffer = {
        val dir = factory.timeshiftDir()
        com.sokoul.iptv.player.timeshift.TimeshiftBuffer(dir, maxBytes = TimeshiftPolicy.capBytes(freeBytes(dir), com.sokoul.iptv.data.repo.DeviceCaps.lowMemory))
    })
    val timeshiftState get() = timeshift.state
    private var tickJob: Job? = null

    init {
        // Un enregistrement démarre : la pause du direct (qui tient la connexion) s'arrête, le lecteur attend.
        viewModelScope.launch {
            gate.recording.collect { if (it != null && timeshift.active) stopTimeshift(backToNetwork = true) }
        }
    }

    private fun freeBytes(dir: File): Long = dir.apply { mkdirs() }.usableSpace

    /**
     * ⏯ en direct. Première pause : la connexion du lecteur est fermée et l'écrivain prend la seule connexion
     * autorisée pour remplir le tampon ; reprise = lecture du tampon. Ensuite ⏯ met en pause / reprend la lecture
     * locale (l'écriture continue). Pause du direct coupée ou disque trop plein : simple pause du lecteur.
     */
    fun liveTogglePause() {
        val sp = _player.value ?: return
        if (!timeshift.active) {
            val stream = lastPlay?.first
            val url = stream?.urls?.firstOrNull()
            if (!timeshiftAllowed || stream == null || url == null) { sp.player.playWhenReady = !sp.player.playWhenReady; return }
            if (gate.recording.value != null) { sp.player.playWhenReady = !sp.player.playWhenReady; return }
            if (!TimeshiftPolicy.canStart(freeBytes(factory.timeshiftDir()), com.sokoul.iptv.data.repo.DeviceCaps.lowMemory)) {
                _ui.update { it.copy(notice = "Pause du direct indisponible : moins de 3 Go libres sur l'appareil") }
                sp.player.playWhenReady = !sp.player.playWhenReady
                return
            }
            sp.stop()
            val pump = com.sokoul.iptv.recording.StreamPump(factory.http)
            timeshift.start(onRefused = { gate.noteRefusal() }) { keepGoing, sink -> pump.pump(url, stream.userAgent, stream.referrer, keepGoing, sink) }
            startTicker()
            return
        }
        if (sp.isLocal) sp.player.playWhenReady = !sp.player.playWhenReady
        else playLocalAt(timeshift.state.value.position)
    }

    /** −10 s / +30 s dans la fenêtre enregistrée (sans réseau). */
    fun timeshiftSeek(deltaMs: Long) {
        if (!timeshift.active) return
        playLocalAt(timeshift.seekTarget(deltaMs))
    }

    /** « Retour au direct » : au bout du tampon (l'écriture continue, la pause reste possible). */
    fun timeshiftToLive() {
        if (!timeshift.active) return
        playLocalAt(Long.MAX_VALUE)
    }

    private fun playLocalAt(time: Long) {
        val sp = _player.value ?: return
        val buf = timeshift.buffer ?: return
        val offset = timeshift.sourceAt(time) ?: return
        sp.playLocal(factory.localSource(com.sokoul.iptv.player.timeshift.TimeshiftDataSource.Factory(buf, offset)))
    }

    /** Fin de la pause du direct : tampon effacé ; [backToNetwork] = reprendre le flux réseau de la chaîne. */
    private fun stopTimeshift(backToNetwork: Boolean) {
        tickJob?.cancel()
        timeshift.stop()
        if (backToNetwork) lastPlay?.let { (s, t, _) -> _player.value?.play(s, t, 0) }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (timeshift.active) {
                val sp = _player.value
                val local = sp?.isLocal == true
                timeshift.tick(if (local) sp?.player?.currentPosition else null, paused = !local || sp?.player?.playWhenReady != true)
                val st = sp?.state?.value
                val err = timeshift.state.value.error
                // écriture arrêtée (refus, coupures) ou lecture locale finie : retour au flux réseau, sans écran noir
                if ((err != null && (!local || st is StreamState.Ended)) || (local && st is StreamState.Failed)) {
                    _ui.update { it.copy(notice = err ?: "Pause du direct interrompue : retour au direct") }
                    stopTimeshift(backToNetwork = true)
                    break
                }
                delay(500)
            }
        }
    }


    /** Décodage des réglages (affiché quand aucun choix de séance n'est fait). */
    var defaultDecoder = com.sokoul.iptv.data.DecoderMode.AUTO
        private set

    /** Décodage choisi pour cette séance seulement (réglages du lecteur), null = celui des réglages. */
    var sessionDecoder: com.sokoul.iptv.data.DecoderMode? = null
        private set

    /**
     * Change le décodage pendant la lecture : le lecteur est recréé (le moteur de décodage ne se change pas à chaud)
     * puis le flux rouvert là où il en était. L'ancien est libéré d'abord : jamais deux connexions au fournisseur.
     */
    fun setSessionDecoder(mode: com.sokoul.iptv.data.DecoderMode) {
        viewModelScope.launch {
            val old = _player.value ?: return@launch
            if (timeshift.active) stopTimeshift(backToNetwork = false)
            val (stream, title, _) = lastPlay ?: return@launch
            val pos = if (_ui.value.isLive) 0L else old.player.currentPosition
            sessionDecoder = mode
            old.player.removeListener(listener)
            old.release()
            val sp = StreamPlayer(factory.create(settingsStore.current().copy(decoderMode = mode)), factory, viewModelScope, gate)
            sp.player.addListener(listener)
            _player.value = sp
            watchEnd(sp)
            watchStutter(sp)
            if (!_ui.value.isLive) sp.player.setPlaybackSpeed(_speed.value) // nouveau lecteur : même vitesse, même décalage du son
            factory.setAudioDelay(sp.player, _ui.value.audioDelayMs)
            sp.play(stream, title, pos)
        }
    }

    // --- Films : versions et repli ------------------------------------------------------

    private var movieVersions: List<com.sokoul.iptv.data.db.MovieEntity> = emptyList()
    private var movieIndex = 0
    private var fallbackJob: Job? = null

    private fun versionLabel(m: com.sokoul.iptv.data.db.MovieEntity) =
        com.sokoul.iptv.ui.components.SourceLabels.text(com.sokoul.iptv.domain.PlaybackSources.fromIptv(com.sokoul.iptv.data.repo.VodVersion(m.id, m.name, m.language, m.quality, m.categoryId)))

    private fun movieUi(m: com.sokoul.iptv.data.db.MovieEntity, startMs: Long) = _ui.update {
        it.copy(
            title = m.title, subtitle = listOfNotNull(m.year?.toString(), m.quality, m.language).joinToString(" · ").ifEmpty { null },
            image = m.poster, isLive = false, resumedFromMs = startMs,
            versionLabel = if (movieVersions.size > 1) versionLabel(m) else null, seriesBackdrop = null, overview = null,
        )
    }

    /** Reprise enregistrée sous la fiche IPTV du film, quelle que soit la source lue (IPTV ou Real-Debrid). */
    private fun movieSaver(p: PlaylistEntity, m: com.sokoul.iptv.data.db.MovieEntity): suspend (Long, Long) -> Unit =
        { pos, dur -> library.saveProgress(p.id, ContentType.MOVIE, m.id, null, m.title, m.year?.toString(), m.poster, pos, dur) }

    /**
     * Titre lancé « Real-Debrid seul » (recherche, saga, reprise) mais présent dans l'abonnement : ses versions IPTV
     * sont chargées comme pour un film de l'abonnement (liste « Langue et qualité », secours).
     */
    private suspend fun loadIptvTwin(p: PlaylistEntity, tmdbId: Int) {
        val w = runCatching { browse.inCatalog(p.id, ContentType.MOVIE, tmdbId) }.getOrNull() ?: return
        val m = catalog.movie(p.id, w.id) ?: return
        val others = vod.movieVersions(p.id, m).filter { it.id != m.id }
        movieVersions = vod.orderedMovies(p.id, listOf(m) + others)
        movieIndex = 0
        com.sokoul.iptv.data.debrid.SourceLog.add("Abonnement : « ${m.title} » trouvé aussi en IPTV (${movieVersions.size} versions)")
    }

    private suspend fun playMovie(p: PlaylistEntity, m: com.sokoul.iptv.data.db.MovieEntity, startMs: Long) {
        currentDebrid = null
        movieUi(m, startMs)
        play(resolver.movie(p, m), m.title, startMs)
        // reprise partagée entre versions : toujours sous la version demandée au départ
        trackProgress(movieSaver(p, movieVersions.firstOrNull() ?: m))
    }

    /**
     * Source en échec : on passe à une autre, quelle que soit son origine (une tentative par source, pas de rafale) :
     * même fichier dans une autre rubrique, puis les autres sources de la même version (même langue, même qualité),
     * puis les autres qualités de la même langue. Connexion IPTV prise ou refusée : aucune requête IPTV, toutes langues.
     * Rien d'autre dans la langue : on le dit, l'utilisateur choisit dans « Langue et qualité ».
     */
    private fun watchFailures() {
        if (route.kind == PlayKind.CLOUD) return
        fallbackJob?.cancel()
        fallbackJob = viewModelScope.launch {
            val sp = _player.value ?: return@launch
            sp.state.collect { s ->
                val onIptv = currentDebrid == null
                val blocked = onIptv && (s is StreamState.Busy || (s is StreamState.Failed && s.refused))
                if (!blocked && s !is StreamState.Failed) return@collect
                val failedId = currentSourceId() ?: return@collect
                val pos = sp.player.currentPosition.takeIf { it > 5_000 } ?: _ui.value.resumedFromMs
                val was = _ui.value.sources.firstOrNull { it.current }
                // lien Real-Debrid expiré en cours de lecture : on redemande un lien une fois et on reprend au même instant
                val f = s as? StreamState.Failed
                val rd = currentDebrid
                if (rd != null && f?.code in EXPIRED_CODES && sp.player.currentPosition > 5_000 && relinked.add(rd.key)) {
                    com.sokoul.iptv.data.debrid.SourceLog.add("Lecture : lien ${com.sokoul.iptv.ui.components.SourceLabels.originName(rd.toSource().origin)} expiré (${f?.code}), nouveau lien demandé")
                    _ui.update { it.copy(notice = "Lien expiré, reprise de la lecture…") }
                    if (playSource(failedId, pos, single = true)) return@collect
                }
                if (!blocked) {
                    markFailed(failedId)
                    (s as? StreamState.Failed)?.let { f ->
                        failReasons[failedId] = f.message
                        com.sokoul.iptv.data.debrid.SourceLog.add("Échec " + com.sokoul.iptv.ui.components.SourceLabels.originName(currentDebrid?.toSource()?.origin ?: com.sokoul.iptv.domain.SourceOrigin.IPTV) + " : " + f.message)
                    }
                    _ui.update { it.copy(notice = "Source indisponible, recherche d'une autre…") }
                    delay(FALLBACK_PAUSE_MS)
                    if (_player.value?.state?.value !is StreamState.Failed) return@collect // relancée entre-temps (Réessayer)
                }
                if (debridQuery != null && debrid.enabled()) debridStreamsNow()
                val list = withMeasure(buildSources())
                val order = SourceCatalog.fallbackOrder(
                    list, failedId, languages(),
                    skip = if (blocked) setOf(com.sokoul.iptv.domain.SourceOrigin.IPTV) else emptySet(), anyLanguage = blocked,
                )
                for (id in order) {
                    if (playSource(id, pos, single = true)) {
                        val now = _ui.value.sources.firstOrNull { it.current } ?: list.firstOrNull { it.id == id || id in it.mergedIds }
                        _ui.update { it.copy(notice = if (blocked) iptvBusyNotice() else fallbackNotice(was, now, failReasons[failedId])) }
                        return@collect
                    }
                }
                val lang = was?.let { PlayerSources.languageName(it.language, it.region) }
                _ui.update {
                    it.copy(notice = if (lang != null) "Aucune autre source en $lang : choisissez une autre langue dans « Langue et qualité »" else "Aucune autre source disponible")
                }
            }
        }
    }

    /** « Source indisponible : lecture via Real-Debrid · même version » / « … · 720p ». */
    private fun fallbackNotice(was: PlayerSource?, now: PlayerSource?, reason: String? = null): String {
        val why = reason?.let { " ($it)" } ?: ""
        if (now == null) return "Source indisponible$why : lecture d'une autre source"
        val same = was != null && SourceCatalog.versionKey(was) == SourceCatalog.versionKey(now)
        return "Source indisponible$why : lecture via " + com.sokoul.iptv.ui.components.SourceLabels.originName(now.origin) +
            if (same) " · même version" else " · " + PlayerSources.short(now)
    }

    /** Identifiant de la source lue (même forme que [PlayerSource.id]). */
    private fun currentSourceId(): String? = currentDebrid?.let { RD_PREFIX + it.key } ?: when (route.kind) {
        PlayKind.EPISODE -> currentEpisode?.let { PlayerSources.episodeId(it.seriesId, it.id) }
        PlayKind.MOVIE -> movieVersions.getOrNull(movieIndex)?.id
        PlayKind.DEBRID -> movieVersions.getOrNull(movieIndex)?.id
        PlayKind.CLOUD -> "cloud"
        else -> null
    }

    /** Échec retenu 24 h pour toutes les origines (gardé en base, même après fermeture du lecteur). */
    private fun markFailed(id: String) {
        val row = if (id.startsWith(RD_PREFIX)) {
            val key = id.removePrefix(RD_PREFIX)
            failedDebrid += key
            RD_FAIL_PREFIX + key
        } else {
            failedIptv += id
            IPTV_FAIL_PREFIX + id
        }
        saveFailure(row, System.currentTimeMillis())
    }

    /** [at] = 0 efface l'échec (choisi à nouveau à la main). */
    private fun saveFailure(row: String, at: Long) {
        val p = playlist ?: return
        val probe = com.sokoul.iptv.data.db.VodProbeEntity(p.id, probeType(), row, fromPlayer = true, fetchedAt = at)
        viewModelScope.launch { runCatching { db.vod().saveProbe(probe) } }
    }

    /** Raison du dernier échec de chaque source dans cette session (id comme [PlayerSource.id]). */
    private val failReasons = HashMap<String, String>()
    /** Versions Real-Debrid dont le lien expiré a déjà été redemandé (une fois par version). */
    private val relinked = HashSet<String>()
    private var switchJob: Job? = null
    private val failedIptv = HashSet<String>()
    private val iptvFailuresRead = HashSet<String>()

    /** Échecs IPTV des dernières 24 h (lus une fois par source). */
    private suspend fun loadIptvFailures(ids: List<String>) {
        val p = playlist ?: return
        val todo = ids.filter { iptvFailuresRead.add(it) }
        if (todo.isEmpty()) return
        runCatching {
            val rows = todo.chunked(400).flatMap { c -> db.vod().probes(p.id, probeType(), c.map { IPTV_FAIL_PREFIX + it }) }
            failedIptv += readFailures(rows, IPTV_FAIL_PREFIX, System.currentTimeMillis())
        }.onFailure { Timber.w("Échecs IPTV : %s", it.message) }
    }

    /** Lance une autre source de l'œuvre en cours (panneau « Langue et qualité »), au même instant. */
    fun switchVersion(id: String) {
        // un choix plus récent remplace le précédent : une résolution en retard ne lance jamais l'ancienne source
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            saveNow()
            val pos = _player.value?.player?.currentPosition?.takeIf { it > 5_000 } ?: 0
            failReasons -= id
            if (id.startsWith(RD_PREFIX)) {
                if (failedDebrid.remove(id.removePrefix(RD_PREFIX))) saveFailure(RD_FAIL_PREFIX + id.removePrefix(RD_PREFIX), 0)
            } else if (failedIptv.remove(id)) saveFailure(IPTV_FAIL_PREFIX + id, 0)
            if (!playSource(id, pos, single = true, prepare = true) && _ui.value.notice?.contains("%") != true) _ui.update { it.copy(notice = "Cette source n'a pas pu être préparée, la lecture continue") }
        }
    }

    /**
     * Lit la source [id] : version IPTV d'un film, épisode d'une série en double (« ep:… ») ou flux Real-Debrid (« rd:… »).
     * @param single Real-Debrid : cette version seulement (sinon les suivantes si elle n'est pas prête)
     * @return false si elle n'a pas pu être lancée
     */
    private suspend fun playSource(id: String, pos: Long, single: Boolean, prepare: Boolean = false): Boolean {
        val p = playlist ?: return false
        if (id.startsWith(RD_PREFIX)) {
            val key = id.removePrefix(RD_PREFIX)
            return when (route.kind) {
                PlayKind.EPISODE -> currentEpisode?.let { e -> playDebrid(e.title, pos, key, episodeSaver(p, e), single, prepare) } ?: false
                PlayKind.MOVIE -> movieVersions.firstOrNull()?.let { m -> playDebrid(m.title, pos, key, movieSaver(p, m), single, prepare) } ?: false
                else -> playDebrid(_ui.value.title, pos, key, debridOnlySaver(), single, prepare)
            }
        }
        return try {
            val ep = PlayerSources.parseEpisodeId(id)
            if (ep != null) {
                val (sid, eid) = ep
                val e = allSeriesEpisodes.firstOrNull { it.seriesId == sid && it.id == eid }
                    ?: currentEpisode?.takeIf { it.seriesId == sid && it.id == eid } ?: return false
                currentEpisode = e
                playEpisodeIptv(e, pos)
                return true
            }
            val i = movieVersions.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return false
            movieIndex = i
            playMovie(p, movieVersions[i], pos)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Source %s écartée : %s", id, e.message)
            false
        }
    }

    // --- Sources affichées dans le lecteur (panneaux Sources et Audio, bandeau de qualité) ----------

    private data class NowVideo(val width: Int, val height: Int, val codec: String?, val hdr: String?)
    private var nowVideo: NowVideo? = null
    private var sourcesJob: Job? = null
    private var categoryNamesCache: Map<ContentType, Map<String, String>> = emptyMap()
    private val seriesCache = HashMap<String, com.sokoul.iptv.data.db.SeriesEntity?>()
    private var cloudItem: com.sokoul.iptv.data.debrid.CloudItem? = null

    private fun mimeCodec(m: String?): String? = when (m) {
        androidx.media3.common.MimeTypes.VIDEO_H265, androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION -> "hevc"
        androidx.media3.common.MimeTypes.VIDEO_H264 -> "h264"
        androidx.media3.common.MimeTypes.VIDEO_AV1 -> "av1"
        androidx.media3.common.MimeTypes.AUDIO_E_AC3, androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC -> "eac3"
        androidx.media3.common.MimeTypes.AUDIO_AC3 -> "ac3"
        androidx.media3.common.MimeTypes.AUDIO_TRUEHD -> "truehd"
        androidx.media3.common.MimeTypes.AUDIO_DTS, androidx.media3.common.MimeTypes.AUDIO_DTS_HD -> "dts"
        androidx.media3.common.MimeTypes.AUDIO_AAC -> "aac"
        else -> m?.substringAfter('/')
    }

    /** Recalcule la liste des sources (aucune requête réseau : base locale et versions Real-Debrid déjà trouvées). */
    private fun refreshSources() {
        sourcesJob?.cancel()
        sourcesJob = viewModelScope.launch {
            val list = try { buildSources() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Timber.w("Sources : %s", e.message); emptyList() }
            val search = when {
                _ui.value.isLive || route.kind == PlayKind.CLOUD -> SourceSearch.DONE
                !runCatching { debrid.enabled() }.getOrDefault(false) -> SourceSearch.OFF
                debridQuery != null && debridStreams == null -> SourceSearch.SEARCHING
                !runCatching { debrid.searchEnabled() }.getOrDefault(false) -> SourceSearch.ACCOUNT
                else -> SourceSearch.DONE
            }
            _ui.update { it.copy(sources = list, searchState = search, languages = languages()) }
            publishNow()
        }
    }

    private fun languages(): List<String> = com.sokoul.iptv.data.repo.VersionPicker.parseOrder(settingsCache?.vodLanguages)

    private suspend fun categoryNames(pid: Long, type: ContentType): Map<String, String> =
        categoryNamesCache[type] ?: vod.categoryNames(pid, type).also { categoryNamesCache = categoryNamesCache + (type to it) }

    private suspend fun seriesById(pid: Long, id: String) = seriesCache.getOrPut(id) { catalog.seriesItem(pid, id) }

    /** Type sous lequel on range les mesures et échecs Real-Debrid de l'œuvre en cours. */
    private fun probeType(): ContentType = when {
        route.kind == PlayKind.EPISODE -> ContentType.SERIES
        debridQuery?.series == true -> ContentType.SERIES
        else -> ContentType.MOVIE
    }

    /** Mesures (qualité réelle) des flux Real-Debrid de l'œuvre, lues en base puis complétées en lecture. */
    private val debridMeasured = HashMap<String, com.sokoul.iptv.data.repo.VodQuality>()

    private suspend fun loadDebridMemory(streams: List<com.sokoul.iptv.data.debrid.DebridStream>) {
        val p = playlist ?: return
        if (streams.isEmpty()) return
        val keys = streams.map { it.key }
        runCatching {
            val rows = keys.chunked(400).flatMap { c -> db.vod().probes(p.id, probeType(), c.map { RD_MEASURE_PREFIX + it } + c.map { RD_FAIL_PREFIX + it }) }
            val (failed, measured) = readDebridMemory(rows, System.currentTimeMillis())
            failedDebrid += failed
            debridMeasured.putAll(measured)
        }.onFailure { Timber.w("Mémoire Real-Debrid : %s", it.message) }
    }

    private var lastPrefs: com.sokoul.iptv.data.debrid.SourceRanker.Prefs? = null

    /** Préférences du classement : Réglages, réseau réel, mesures et échecs connus. */
    private suspend fun rankPrefs(): com.sokoul.iptv.data.debrid.SourceRanker.Prefs {
        settingsCache = settingsStore.current()
        val base = runCatching { debrid.prefs(iptvBlocked()) }.getOrElse {
            com.sokoul.iptv.data.debrid.SourceRanker.Prefs(languages = languages(), iptvBlocked = iptvBlocked())
        }
        return base.copy(debridMeasured = HashMap(debridMeasured), failed = failedIptv + failedDebrid).also { lastPrefs = it }
    }

    /**
     * Toutes les sources de l'œuvre, classées ensemble par `SourceRanker` (aucune origine favorisée) :
     * versions IPTV (film, ou même épisode dans les séries en double), fichiers Real-Debrid, ou fichier perso.
     */
    private suspend fun buildSources(): List<PlayerSource> {
        val p = playlist
        val rd = currentDebrid
        if (p == null || _ui.value.isLive) return emptyList()
        if (route.kind == PlayKind.CLOUD) return listOf(PlayerSources.fromCloud(cloudItem, route.title))
        // versions IPTV, identifiées comme dans le lecteur (id du film, ou « ep:série|épisode »)
        val iptv: List<Pair<com.sokoul.iptv.data.repo.VodVersion, Boolean>> = when (route.kind) {
            PlayKind.MOVIE, PlayKind.DEBRID -> if (movieVersions.isEmpty()) emptyList() else {
                val names = categoryNames(p.id, ContentType.MOVIE)
                val q = vod.qualities(p.id, ContentType.MOVIE, movieVersions.map { it.id })
                movieVersions.mapIndexed { i, m -> m.toVersion().copy(categoryName = names[m.categoryId], measured = q[m.id]) to (rd == null && i == movieIndex) }
            }
            PlayKind.EPISODE -> {
                val e = currentEpisode
                if (e == null) emptyList() else {
                    val order = seriesVersionIds.withIndex().associate { it.value to it.index }
                    val same = (allSeriesEpisodes + e).filter { it.season == e.season && it.episode == e.episode }
                        .distinctBy { it.seriesId to it.id }.sortedBy { order[it.seriesId] ?: Int.MAX_VALUE }
                    val names = categoryNames(p.id, ContentType.SERIES)
                    val q = vod.qualities(p.id, ContentType.SERIES, same.map { it.seriesId }.distinct())
                    same.mapNotNull { ep ->
                        val s = seriesById(p.id, ep.seriesId) ?: return@mapNotNull null
                        s.toVersion().copy(id = PlayerSources.episodeId(ep.seriesId, ep.id), categoryName = names[s.categoryId], measured = q[s.id]) to
                            (rd == null && ep.id == e.id && ep.seriesId == e.seriesId)
                    }
                }
            }
            else -> emptyList()
        }
        loadIptvFailures(iptv.map { it.first.id })
        val currentIptv = iptv.firstOrNull { it.second }?.first?.id
        val streams = (debridStreams.orEmpty() + listOfNotNull(rd)).distinctBy { it.key }
        val prefs = rankPrefs()
        return com.sokoul.iptv.data.debrid.SourceRanker.rank(iptv.map { it.first }, streams, prefs).map { c ->
            when (c) {
                is com.sokoul.iptv.data.debrid.Candidate.Iptv ->
                    PlayerSources.fromIptv(c.version, current = c.version.id == currentIptv).copy(failed = c.version.id in failedIptv, failReason = failReasons[c.version.id])
                is com.sokoul.iptv.data.debrid.Candidate.Debrid -> {
                    val s = PlayerSources.fromDebrid(c.stream, current = rd?.key == c.stream.key, id = RD_PREFIX + c.stream.key)
                    val m = debridMeasured[c.stream.key]?.takeIf { it.hasImage }
                    val measured = if (m == null) s else s.copy(
                        quality = PlayerSources.qualityOf(m.resolution) ?: s.quality,
                        codec = m.videoCodec?.let(com.sokoul.iptv.parser.MediaInfo::codec) ?: s.codec,
                        hdr = m.hdr ?: s.hdr, basis = QualityBasis.MEASURED,
                    )
                    val mm = debridMeasured[c.stream.key]
                    PlayerSources.withHeard(measured, mm?.audioLanguage, mm?.fromPlayer == true).copy(failed = c.stream.key in failedDebrid, failReason = failReasons[RD_PREFIX + c.stream.key])
                }
            }
        }
    }

    /** La source en cours prend la mesure du flux (définition, codec, HDR) dès la première image. */
    private fun withMeasure(list: List<PlayerSource>): List<PlayerSource> {
        val v = nowVideo ?: return list
        return list.map { s ->
            if (!s.current) s
            else s.copy(
                quality = PlayerSources.qualityOf(com.sokoul.iptv.parser.MediaInfo.resolution(v.width, v.height)) ?: s.quality,
                codec = v.codec ?: s.codec, hdr = v.hdr, basis = QualityBasis.MEASURED,
            )
        }
    }

    /** Bandeau de qualité : mesure du flux en cours + langue et origine de la source. */
    private fun publishNow() {
        val v = nowVideo
        val sources = withMeasure(_ui.value.sources)
        val cur = sources.firstOrNull { it.current }
        val text = PlayerSources.banner(v?.width, v?.height, v?.codec, v?.hdr, cur)
        _ui.update { if (it.isLive) it.copy(nowPlaying = null) else it.copy(nowPlaying = text, sources = sources) }
    }

    // --- Real-Debrid : versions trouvées hors abonnement --------------------------------------

    private var debridQuery: com.sokoul.iptv.data.debrid.DebridQuery? = null
    private var debridStreams: List<com.sokoul.iptv.data.debrid.DebridStream>? = null
    private var debridLoad: kotlinx.coroutines.Deferred<List<com.sokoul.iptv.data.debrid.DebridStream>>? = null
    private var currentDebrid: com.sokoul.iptv.data.debrid.DebridStream? = null
    private val failedDebrid = HashSet<String>()
    /** Dernière erreur Real-Debrid (affichée quand plus aucune version ne se lance). */
    private var lastRdError: String? = null

    /** Connexion IPTV indisponible : enregistrement en cours, ou pause imposée après un refus. */
    private fun iptvBlocked() = gate.recording.value != null || gate.remainingMs() > 0

    private fun iptvBusyNotice(): String {
        val rd = com.sokoul.iptv.ui.components.SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID)
        return gate.recording.value?.let { "$it est en cours d'enregistrement : lecture via $rd" } ?: "Connexion IPTV en pause : lecture via $rd"
    }

    private fun setDebridQuery(q: com.sokoul.iptv.data.debrid.DebridQuery?) {
        if (q == debridQuery) return
        debridQuery = q
        debridStreams = null
        debridLoad = null
        failedDebrid.clear()
        debridMeasured.clear()
        if (q != null) viewModelScope.launch { if (debrid.enabled()) debridStreamsNow() }
        refreshSources()
    }

    /** Versions RD de l'œuvre en cours (une seule recherche, partagée), avec ce qu'on en sait déjà (mesures, échecs). */
    private suspend fun debridStreamsNow(): List<com.sokoul.iptv.data.debrid.DebridStream> {
        debridStreams?.let { return it }
        val q = debridQuery ?: return emptyList()
        val d = debridLoad ?: viewModelScope.async {
            runCatching { debrid.find(q) }.getOrDefault(emptyList()).also { loadDebridMemory(it) }
        }.also { debridLoad = it }
        return d.await().also { if (debridQuery == q) { debridStreams = it; refreshSources() } }
    }

    /** Versions Real-Debrid seules, dans l'ordre du classement commun. */
    private var settingsCache: com.sokoul.iptv.data.AppSettings? = null

    /**
     * Lit la version RD [key], sinon (si ![single]) la meilleure : jusqu'à 4 essais, une version pas prête est écartée.
     * @return false si aucune version Real-Debrid n'est lisible (l'appelant garde la source en cours)
     */
    private suspend fun playDebrid(title: String, startMs: Long, key: String?, save: suspend (Long, Long) -> Unit, single: Boolean = false, prepare: Boolean = false): Boolean {
        val q = debridQuery ?: return false
        if (!debrid.enabled()) return false
        val streams = debridStreamsNow()
        val prefs = rankPrefs()
        val wanted = listOfNotNull(key?.let { k -> streams.firstOrNull { it.key == k } ?: currentDebrid?.takeIf { it.key == k } })
        val order = if (single && wanted.isNotEmpty()) wanted else {
            // essai automatique : seulement ce qui peut démarrer tout de suite (une version à télécharger ne l'est pas)
            val ranked = com.sokoul.iptv.data.debrid.SourceRanker.rank(emptyList(), streams.filter { it.key !in failedDebrid && it.readyNow != false }, prefs)
                .mapNotNull { (it as? com.sokoul.iptv.data.debrid.Candidate.Debrid)?.stream }
            // chaque essai est court (~5 s au plus) : 6 versions avant d'abandonner
            (wanted + ranked).distinctBy { it.key }.take(6)
        }
        val rdName = com.sokoul.iptv.ui.components.SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID)
        var preparing: Pair<String, Int>? = null
        var notCached = 0
        for (s in order) {
            val label = com.sokoul.iptv.ui.components.SourceLabels.text(s.toSource())
            // lancement : l'état s'affiche sous le logo, pas en bandeau
            _ui.update { if (it.starting != null) it.copy(starting = "Préparation de la version $label…") else it.copy(notice = "Préparation de la version $label sur $rdName…") }
            val url = try {
                rdResolver.resolve(s, q, keep = prepare && s.key == key)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.sokoul.iptv.data.debrid.DebridPreparing) {
                // pas une panne : le fichier se télécharge dans le compte, il sera « dans votre compte » une fois prêt
                markPreparing(s.key, e.progress)
                preparing = label to e.progress
                null
            } catch (e: com.sokoul.iptv.data.debrid.DebridNotCached) {
                Timber.i("Version Real-Debrid pas en cache, écartée pour ce lancement")
                notCached++
                null
            } catch (e: Exception) {
                Timber.w("Version Real-Debrid écartée : ${e.message}")
                failReasons[RD_PREFIX + s.key] = com.sokoul.iptv.data.debrid.SourceLog.reason(e)
                lastRdError = failReasons[RD_PREFIX + s.key]
                markFailed(RD_PREFIX + s.key)
                null
            } ?: continue
            currentDebrid = s
            _ui.update { it.copy(notice = null, versionLabel = "$label · " + com.sokoul.iptv.ui.components.SourceLabels.originShort(com.sokoul.iptv.domain.SourceOrigin.DEBRID)) }
            play(ResolvedStream(listOf(url), settingsStore.current().userAgent, null, isLive = false, provider = false), title, startMs)
            trackProgress(save)
            guardPlaceholder(s)
            return true
        }
        val p = preparing
        _ui.update {
            it.copy(
                notice = when {
                    p != null -> "$rdName télécharge la version ${p.first} (${p.second} %). Elle sera lisible une fois prête, marquée « dans votre compte »."
                    order.isEmpty() -> null
                    notCached == order.size ->
                        "Aucune de ces versions n'est encore en cache sur $rdName : choisissez-en une dans « Langue et qualité » pour la faire télécharger"
                    else -> "Aucune version $rdName prête pour le moment" + (lastRdError?.let { " (dernière erreur : $it)" } ?: "")
                },
            )
        }
        return false
    }

    /**
     * Une extension peut renvoyer une courte vidéo « téléchargement en cours » au lieu du film : durée de moins de
     * 3 min pour un film ou un épisode = version pas prête. Le flux est rejeté (secours), rien n'est enregistré.
     */
    private fun guardPlaceholder(s: com.sokoul.iptv.data.debrid.DebridStream) {
        placeholderJob?.cancel()
        placeholderJob = viewModelScope.launch {
            repeat(40) {
                delay(500)
                if (currentDebrid?.key != s.key) return@launch
                val p = _player.value ?: return@launch
                val dur = p.player.duration
                if (dur != C.TIME_UNSET && dur > 0) {
                    if (dur < PLACEHOLDER_MAX_MS) {
                        val why = "vidéo d'attente de l'extension (${dur / 1000} s) : version pas prête sur " + com.sokoul.iptv.ui.components.SourceLabels.originName(s.toSource().origin)
                        com.sokoul.iptv.data.debrid.SourceLog.add("Lecture « ${s.title.take(80)} » : $why")
                        failReasons[RD_PREFIX + s.key] = why
                        p.reject("Version pas prête : $why")
                    }
                    return@launch
                }
            }
        }
    }
    private var placeholderJob: Job? = null

    /** Version en cours de téléchargement dans le compte : affichée « en préparation · n % », jamais « en échec ». */
    private fun markPreparing(key: String, progress: Int) {
        failedDebrid -= key
        debridStreams = debridStreams?.map { if (it.key == key) it.copy(preparing = progress, inAccount = false) else it }
        refreshSources()
    }

    /** Titre hors abonnement : reprise rangée sous « tmdb:… » (rangée « Reprendre » unique). */
    private fun debridOnlySaver(): suspend (Long, Long) -> Unit = { pos, dur ->
        val q = debridQuery
        val p = playlist
        if (q != null && p != null) library.saveProgress(
            p.id, if (q.series) ContentType.SERIES else ContentType.MOVIE, "tmdb:" + q.encode(), null,
            _ui.value.title, _ui.value.subtitle, _ui.value.image, pos, dur,
            season = q.season.takeIf { q.series }, episode = q.episode.takeIf { q.series },
        )
    }

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

    fun hasPrevious() = previousChannel != null

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

    /** Programmes de la chaîne regardée : celui en cours puis la suite, sur la journée. */
    suspend fun upcoming(c: ChannelEntity, hours: Int = 24): List<com.sokoul.iptv.data.db.ProgramEntity> {
        val now = System.currentTimeMillis()
        return runCatching { epg.range(route.playlistId, c, now - 3 * 3_600_000L, now + hours * 3_600_000L) }.getOrDefault(emptyList())
            .filter { it.end > now }.take(60)
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
        // même source que l'épisode précédent (Real-Debrid choisi), version RD demandée par la fiche, ou IPTV occupée
        val wanted = route.source?.takeIf { e.id == route.id && !routeSourceUsed }
        val preferRd = wanted != null || currentDebrid != null || iptvBlocked()
        // épisode suivant lancé depuis la carte : écran de lancement jusqu'à la première image
        if (started && _ui.value.starting == null) startingStatus("Recherche de la meilleure version…")
        val q = seriesTmdbId()?.let { com.sokoul.iptv.data.debrid.DebridQuery(it, series = true, season = e.season, episode = e.episode) }
        showArt(tmdbSeries)
        if (preferRd) {
            routeSourceUsed = true
            setDebridQuery(q)
            if (playDebrid(e.title, resume, wanted, episodeSaver(p, e), prepare = true)) {
                if (wanted == null && currentDebrid != null && iptvBlocked()) _ui.update { it.copy(notice = iptvBusyNotice()) }
                watchFailures()
                return
            }
        }
        // la meilleure version part directement (attente bornée de la recherche Real-Debrid)
        val best = bestDebridAtStart { q }
        if (best != null && playDebrid(e.title, resume, best, episodeSaver(p, e), single = true)) {
            watchFailures()
            return
        }
        playEpisodeIptv(e, resume)
        watchFailures()
    }

    private var routeSourceUsed = false

    private suspend fun seriesTmdbId(): Int? {
        val series = currentSeries ?: return null
        return tmdbLock.withLock {
            if (!tmdbSeriesLoaded) {
                tmdbSeriesLoaded = true
                tmdbSeries = runCatching { catalog.seriesTmdb(series) }.getOrNull()
            }
            tmdbSeries?.id
        }
    }

    private fun episodeSaver(p: PlaylistEntity, e: EpisodeEntity): suspend (Long, Long) -> Unit {
        val series = currentSeries
        val label = "S%02dE%02d".format(e.season, e.episode)
        return { pos, dur ->
            library.saveProgress(
                p.id, ContentType.SERIES, e.id, e.seriesId, series?.title ?: e.title, "$label · ${e.title}", e.image ?: series?.poster,
                pos, dur, season = e.season, episode = e.episode,
            )
        }
    }

    private suspend fun playEpisodeIptv(e: EpisodeEntity, startMs: Long) {
        val p = playlist ?: return
        currentDebrid = null
        play(resolver.episode(p, e), e.title, startMs)
        trackProgress(episodeSaver(p, e))
        if (debridStreams.orEmpty().isNotEmpty()) _ui.update { it.copy(versionLabel = com.sokoul.iptv.ui.components.SourceLabels.originShort(com.sokoul.iptv.domain.SourceOrigin.IPTV)) }
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

    /** Titre en cours pour Trakt (identifiants TMDB du film ou de l'épisode), null en direct ou sans identifiant. */
    private fun traktItem(): com.sokoul.iptv.data.trakt.TraktItem? {
        if (_ui.value.isLive) return null
        val q = debridQuery ?: return null
        return if (q.series) q.season?.let { s -> q.episode?.let { e -> com.sokoul.iptv.data.trakt.TraktItem(q.tmdbId, s, e) } }
        else com.sokoul.iptv.data.trakt.TraktItem(q.tmdbId)
    }

    private fun percent(): Float? {
        val p = _player.value?.player ?: return null
        val dur = p.duration
        return if (dur == C.TIME_UNSET || dur <= 0) null else p.currentPosition * 100f / dur
    }

    suspend fun saveNow() {
        val p = _player.value?.player ?: return
        val dur = p.duration
        val pos = p.currentPosition
        if (dur == C.TIME_UNSET || dur <= 0 || pos < 5_000) return
        // film ou épisode de moins de 3 min : vidéo d'attente ou d'erreur, jamais une reprise ni un « vu »
        if (!_ui.value.isLive && dur < PLACEHOLDER_MAX_MS) return
        saver?.invoke(pos, dur)
        if (p.isPlaying) trakt.playing(traktItem(), pos * 100f / dur)
    }

    /** Pause (bouton, télécommande) : la position est enregistrée tout de suite. */
    fun pauseAndSave() {
        _player.value?.player?.pause()
        trakt.paused(percent())
        viewModelScope.launch { saveNow() }
    }

    /** Application en arrière-plan sans image dans l'image : pause et sauvegarde (direct : connexion fermée). */
    fun onBackground() {
        val sp = _player.value ?: return
        // écran quitté : la pause du direct s'arrête (la seule connexion est libérée)
        if (timeshift.active) stopTimeshift(backToNetwork = false)
        if (_ui.value.isLive) { sp.stop(); return }
        sp.player.pause()
        trakt.paused(percent())
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
        PlayKind.DEBRID -> debridQuery?.let { SubtitleQuery(it.tmdbId, isMovie = !it.series, season = it.season, episode = it.episode) }
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
        _subOffset.value = 0
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

    // --- Home cinéma : décalage des sous-titres, « Passer l'intro », « Ça coupe ? » ----------

    private val _subOffset = MutableStateFlow(0L)
    /** Décalage des sous-titres (positif = plus tard). Remis à zéro à chaque nouvelle œuvre. */
    val subOffsetMs: StateFlow<Long> = _subOffset.asStateFlow()

    /** Sous-titre en ligne : avance ou retard ; sous-titres du flux : retard seulement. */
    fun shiftSubtitles(deltaMs: Long) {
        _subOffset.value = if (deltaMs == 0L) 0 else SubtitleOffset.shift(_subOffset.value, deltaMs, delayOnly = _onlineSubs.value.activeRef == null)
    }

    val frameRateMatch: StateFlow<Boolean> = settingsStore.settings.map { it.frameRateMatch }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private val _intro = MutableStateFlow<IntroSkip.Window?>(null)
    /** Moment de l'intro appris pour cette série (null = rien d'appris). */
    val intro: StateFlow<IntroSkip.Window?> = _intro.asStateFlow()
    private var introKey: String? = null
    private var skippingIntro = false

    private fun introSeries(): String? = when {
        route.kind == PlayKind.EPISODE -> currentEpisode?.seriesId?.let { "${route.playlistId}:$it" }
        debridQuery?.series == true -> "tmdb:${debridQuery?.tmdbId}"
        else -> null
    }

    private fun loadIntro() {
        val k = introSeries()
        introKey = k
        _intro.value = null
        if (k != null) viewModelScope.launch {
            settingsStore.introSkip(k)?.let { (a, b) -> if (introKey == k) _intro.value = IntroSkip.Window(a, b) }
        }
    }

    fun skipIntro() {
        val w = _intro.value ?: return
        val exo = _player.value?.player ?: return
        skippingIntro = true
        exo.seekTo(w.endMs)
    }

    private var lastSeekAt = 0L

    /** Saut manuel de 45 s à 3 min au début d'un épisode : retenu comme intro de la série. */
    private fun onSeek(fromMs: Long, toMs: Long) {
        lastSeekAt = System.currentTimeMillis()
        if (skippingIntro) { skippingIntro = false; return }
        val k = introSeries() ?: return
        val w = IntroSkip.learn(fromMs, toMs) ?: return
        _intro.value = w
        viewModelScope.launch { settingsStore.setIntroSkip(k, w.startMs, w.endMs) }
    }

    private var stutters = 0
    private var lighterOffered = false

    /**
     * « Ça coupe ? » : après 2 coupures (chargement de plus de 1,5 s en cours de lecture, hors saut),
     * une version plus légère est proposée.
     */
    private fun watchStutter(sp: StreamPlayer) {
        viewModelScope.launch {
            var playedOnce = false
            sp.state.collectLatest { s ->
                when (s) {
                    StreamState.Playing -> playedOnce = true
                    StreamState.Loading, StreamState.Idle -> playedOnce = false
                    StreamState.Buffering, is StreamState.Reconnecting -> {
                        if (!playedOnce || _ui.value.isLive || System.currentTimeMillis() - lastSeekAt < 3_000) return@collectLatest
                        delay(1_500)
                        if (++stutters >= 2) offerLighter()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun offerLighter() {
        if (lighterOffered) return
        // toutes les sources de l'œuvre (IPTV, épisodes des séries en double, Real-Debrid) : celles qui démarrent tout de suite
        fun typed(s: PlayerSource) = com.sokoul.iptv.domain.PlaybackSource(
            id = s.id, origin = s.origin, quality = s.quality, language = s.language, region = s.region, sizeBytes = s.sizeBytes,
        )
        val all = _ui.value.sources
        val current = all.firstOrNull { it.current } ?: return
        val options = all.filter { !it.failed && it.ready != false }.map { it.id to typed(it) }
        val pick = LighterVersion.pick(current.id, typed(current), options) ?: return
        lighterOffered = true
        _ui.update { it.copy(lighter = pick.first to com.sokoul.iptv.ui.components.SourceLabels.text(pick.second)) }
    }

    fun acceptLighter() {
        val id = _ui.value.lighter?.first ?: return
        _ui.update { it.copy(lighter = null) }
        switchVersion(id)
    }

    fun dismissLighter() { _ui.update { it.copy(lighter = null) } }

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

    private val measuredItems = HashSet<String>()

    /** Qualité constatée en lecture (plus sûre que celle du panel) : retenue pour la fiche et le choix des versions. */
    private fun saveMeasuredQuality(v: Format?, a: Format?) {
        val p = playlist ?: return
        if (v == null || v.width <= 0) return
        val rdNow = currentDebrid
        // fichier Real-Debrid : mesure rangée sous sa clé (affichée « mesurée » la prochaine fois), jamais sous la version IPTV
        val (type, id) = if (rdNow != null) probeType() to (RD_MEASURE_PREFIX + rdNow.key) else when (route.kind) {
            PlayKind.MOVIE -> ContentType.MOVIE to (movieVersions.getOrNull(movieIndex)?.id ?: return)
            PlayKind.EPISODE -> ContentType.SERIES to (currentEpisode?.seriesId ?: return)
            else -> return
        }
        // toutes les pistes audio du fichier (« fr,en ») : la langue réelle de la version, pas seulement celle écoutée
        val heard = com.sokoul.iptv.domain.SpokenLanguages.store(
            _tracks.value.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).language } },
        ) ?: a?.language
        if (!measuredItems.add("$type:$id:${v.width}x${v.height}:$heard")) return
        val hdr = when (v.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> if (v.sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION) "Dolby Vision" else "HDR10"
            C.COLOR_TRANSFER_HLG -> "HLG"
            else -> null
        }
        val probe = com.sokoul.iptv.data.db.VodProbeEntity(
            p.id, type, id, v.width, v.height, mimeCodec(v.sampleMimeType), hdr, v.frameRate.takeIf { it > 0 }?.toDouble(),
            mimeCodec(a?.sampleMimeType), a?.channelCount?.takeIf { it > 0 }, heard,
            (v.bitrate.takeIf { it > 0 } ?: v.averageBitrate.takeIf { it > 0 })?.div(1000),
            (_player.value?.player?.duration?.takeIf { it > 0 } ?: 0L).div(1000).toInt().takeIf { it > 0 },
            true, System.currentTimeMillis(),
        )
        if (rdNow != null) probe.toQuality()?.let { debridMeasured[rdNow.key] = it }
        viewModelScope.launch { runCatching { db.vod().saveProbe(probe) } }
    }

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
        nowVideo = v?.takeIf { it.width > 0 }?.let {
            NowVideo(it.width, it.height, com.sokoul.iptv.parser.MediaInfo.codec(mimeCodec(it.sampleMimeType)), PlayerSources.hdrOf(it.colorInfo?.colorTransfer, it.sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION))
        }
        _ui.update { it.copy(videoInfo = info) }
        saveMeasuredQuality(v, a)
        publishNow()
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

    /** « Arrêter l'enregistrement » depuis l'écran « connexion occupée » : la lecture reprend seule ensuite. */
    fun stopRecordings() {
        viewModelScope.launch { db.recordings().active().forEach { recorder.cancel(it.id) } }
    }

    companion object {
        /** Films et épisodes : plusieurs versions possibles, la meilleure est cherchée avant de lancer. */
        private val WORK_KINDS = setOf(PlayKind.MOVIE, PlayKind.EPISODE, PlayKind.DEBRID)
        const val FALLBACK_PAUSE_MS = 4_000L
        /** En dessous : vidéo d'attente ou d'erreur, pas un film ni un épisode. */
        const val PLACEHOLDER_MAX_MS = 180_000L
        /** Lien direct Real-Debrid périmé (refusé ou disparu) : redemandé une fois avant de changer de source. */
        private val EXPIRED_CODES = setOf(403, 404, 410)
        /** Menu « Version » : identifiant d'une version Real-Debrid, et de l'épisode de l'abonnement. */
        const val RD_PREFIX = "rd:"
        /** Mesure et échec d'un flux Real-Debrid, rangés dans `vod_probe` sous ces préfixes + clé du flux. */
        const val RD_MEASURE_PREFIX = "rd:"
        const val RD_FAIL_PREFIX = "rdfail:"
        const val RD_FAIL_TTL_MS = 24 * 3_600_000L
        /** Échec d'une version IPTV (« fail:id »), gardé 24 h comme pour Real-Debrid. */
        const val IPTV_FAIL_PREFIX = "fail:"

        /** Échecs de moins de 24 h rangés sous [prefix] (un échec effacé a la date 0). */
        fun readFailures(rows: List<com.sokoul.iptv.data.db.VodProbeEntity>, prefix: String, now: Long): Set<String> =
            rows.filter { it.itemId.startsWith(prefix) && now - it.fetchedAt < RD_FAIL_TTL_MS }.map { it.itemId.removePrefix(prefix) }.toSet()

        /**
         * Ce que la base sait des flux Real-Debrid (lignes `vod_probe` « rd:clé » et « rdfail:clé ») :
         * échecs de moins de 24 h et qualités mesurées, par clé de flux.
         */
        fun readDebridMemory(rows: List<com.sokoul.iptv.data.db.VodProbeEntity>, now: Long): Pair<Set<String>, Map<String, com.sokoul.iptv.data.repo.VodQuality>> {
            val failed = readFailures(rows, RD_FAIL_PREFIX, now)
            val measured = rows.filter { it.itemId.startsWith(RD_MEASURE_PREFIX) && !it.itemId.startsWith(RD_FAIL_PREFIX) }
                .mapNotNull { r -> r.toQuality()?.let { r.itemId.removePrefix(RD_MEASURE_PREFIX) to it } }.toMap()
            return failed to measured
        }
    }
    fun state(): StateFlow<StreamState>? = _player.value?.state

    override fun onCleared() {
        trakt.stopped(percent())
        tickJob?.cancel()
        timeshift.stop()
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
