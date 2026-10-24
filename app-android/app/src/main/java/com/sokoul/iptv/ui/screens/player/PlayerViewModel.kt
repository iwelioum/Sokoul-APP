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
import com.sokoul.iptv.data.repo.EpgRepository
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.NowNext
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.ResolvedStream
import com.sokoul.iptv.data.repo.StreamResolver
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.player.PlayerFactory
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TrackOption(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

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
            val sp = StreamPlayer(factory.create(settings), factory, viewModelScope)
            sp.player.addListener(listener)
            _player.value = sp
            playlist = playlists.get(route.playlistId)
            try {
                start()
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.userMessage()) }
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
                viewModelScope.launch { _zapList.value = catalog.channelList(p.id, route.categoryId) }
                playChannel(c)
            }
            PlayKind.CATCHUP -> {
                val c = catalog.channel(p.id, route.id) ?: throw IllegalStateException("Chaîne introuvable")
                if (allowed(ContentType.LIVE, c.categoryId) { playCatchup(c, route.start, route.end) }) playCatchup(c, route.start, route.end)
            }
            PlayKind.MOVIE -> {
                val m = catalog.movie(p.id, route.id) ?: throw IllegalStateException("Film introuvable")
                if (!allowed(ContentType.MOVIE, m.categoryId) { start() }) return
                val resume = resumePosition(ContentType.MOVIE, m.id)
                _ui.update { it.copy(title = m.title, subtitle = listOfNotNull(m.year?.toString(), m.quality).joinToString(" · ").ifEmpty { null }, image = m.poster, isLive = false, resumedFromMs = resume) }
                play(resolver.movie(p, m), m.title, resume)
                trackProgress { pos, dur -> library.saveProgress(p.id, ContentType.MOVIE, m.id, null, m.title, m.year?.toString(), m.poster, pos, dur) }
            }
            PlayKind.EPISODE -> {
                val sid = route.parentId ?: throw IllegalStateException("Série inconnue")
                catalog.ensureEpisodes(p, sid)
                episodes = catalog.episodeList(p.id, sid)
                val e = episodes.firstOrNull { it.id == route.id } ?: catalog.episode(p.id, sid, route.id) ?: throw IllegalStateException("Épisode introuvable")
                val seriesCat = catalog.seriesItem(p.id, sid)?.categoryId
                if (!allowed(ContentType.SERIES, seriesCat) { playEpisode(e) }) return
                playEpisode(e)
            }
            PlayKind.RECORDING -> Unit
        }
    }

    private suspend fun resumePosition(type: ContentType, id: String): Long =
        LibraryRepository.resumeFrom(library.progress(route.playlistId, type, id))

    private fun play(stream: ResolvedStream, title: String, startMs: Long) {
        _ui.update { it.copy(error = null) }
        _player.value?.play(stream, title, startMs)
        updateVideoInfo()
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

    fun zap(delta: Int) {
        val list = _zapList.value
        val current = _ui.value.channel ?: return
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.id == current.id }
        val next = list[((if (i < 0) 0 else i) + delta).mod(list.size)]
        playChannel(next)
    }

    fun previous() { previousChannel?.let { playChannel(it) } }

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

    private suspend fun playEpisode(e: EpisodeEntity) {
        val p = playlist ?: return
        currentEpisode = e
        val series = catalog.seriesItem(p.id, e.seriesId)
        val resume = resumePosition(ContentType.SERIES, e.id)
        val label = "S%02dE%02d".format(e.season, e.episode)
        val idx = episodes.indexOfFirst { it.id == e.id }
        _ui.update {
            it.copy(
                title = series?.title ?: e.title, subtitle = "$label · ${e.title}", image = e.image ?: series?.poster,
                isLive = false, channel = null, nextEpisode = episodes.getOrNull(idx + 1), resumedFromMs = resume,
            )
        }
        play(resolver.episode(p, e), e.title, resume)
        trackProgress { pos, dur ->
            library.saveProgress(p.id, ContentType.SERIES, e.id, e.seriesId, series?.title ?: e.title, "$label · ${e.title}", e.image ?: series?.poster, pos, dur)
        }
    }

    fun playNextEpisode() {
        val next = _ui.value.nextEpisode ?: return
        viewModelScope.launch { saveNow(); playEpisode(next) }
    }

    fun restartFromBeginning() { _player.value?.player?.seekTo(0) }

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

    // --- Pistes ------------------------------------------------------------------------

    fun audioOptions(): List<TrackOption> = trackOptions(C.TRACK_TYPE_AUDIO)
    fun textOptions(): List<TrackOption> =
        listOf(TrackOption("Désactivés", null, -1, _tracks.value.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected })) +
            trackOptions(C.TRACK_TYPE_TEXT)

    private fun trackOptions(type: Int): List<TrackOption> = _tracks.value.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i -> TrackOption(describe(g.getTrackFormat(i), type), g, i, g.isTrackSelected(i)) }
    }

    fun selectTrack(type: Int, option: TrackOption) {
        val exo: ExoPlayer = _player.value?.player ?: return
        val builder = exo.trackSelectionParameters.buildUpon()
        if (option.group == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        }
        exo.trackSelectionParameters = builder.build()
    }

    private fun describe(f: Format, type: Int): String {
        val lang = f.language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.FRENCH).replaceFirstChar { c -> c.uppercase() } }
        val parts = listOfNotNull(
            f.label ?: lang ?: "Piste",
            if (type == C.TRACK_TYPE_AUDIO) when (f.channelCount) { 1 -> "mono"; 2 -> "stéréo"; 6 -> "5.1"; 8 -> "7.1"; else -> null } else null,
            f.sampleMimeType?.substringAfter('/')?.uppercase()?.replace("MP4A-LATM", "AAC"),
        )
        return parts.joinToString(" · ")
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
        _ui.update { it.copy(videoInfo = info) }
    }

    fun retry() = _player.value?.retryNow()
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
