package com.sokoul.iptv.ui.screens.player

import com.sokoul.iptv.ui.theme.Radius
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import com.sokoul.iptv.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Memory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.sokoul.iptv.MainActivity
import com.sokoul.iptv.PlaybackUiState
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.player.StreamState
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.nav.MultiviewRoute
import com.sokoul.iptv.ui.screens.tv.ChannelOverlay
import com.sokoul.iptv.ui.screens.tv.GroupsPanel
import com.sokoul.iptv.ui.screens.tv.MiniGuide
import com.sokoul.iptv.ui.screens.tv.DrawerWidths
import com.sokoul.iptv.ui.screens.tv.ZapBanner
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Search
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.theme.LocalIsTv
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatDuration
import com.sokoul.iptv.ui.util.rememberNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class Menu { MAIN, ASPECT, RECORD, INFO, DECODER }

private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Adapter",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Remplir (rogner)",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Étirer",
)

/**
 * Format d'image appliqué : celui choisi, sauf écran en portrait (tablette ou téléphone tenus debout) où la vidéo
 * s'adapte toujours à la largeur : « Remplir » y rognerait les deux tiers de l'image.
 */
internal fun effectiveResizeMode(index: Int, portrait: Boolean): Int =
    if (portrait) AspectRatioFrameLayout.RESIZE_MODE_FIT else resizeModes[index.coerceIn(0, resizeModes.lastIndex)].first

private fun isSelect(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
private fun isDpad(code: Int) = isSelect(code) || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
    code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT

@Composable
fun PlayerScreen(nav: NavHostController, route: PlayerRoute) {
    val vm: PlayerViewModel = hiltViewModel()
    val sp by vm.player.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val zapList by vm.zapList.collectAsStateWithLifecycle()
    val episodes by vm.episodesFlow.collectAsStateWithLifecycle()
    val tmdbEpisodes by vm.tmdbEpisodes.collectAsStateWithLifecycle()
    val resizeIndex by vm.resizeMode.collectAsStateWithLifecycle()
    val inPip by PlaybackUiState.inPip.collectAsStateWithLifecycle()
    val fallbackState = remember { MutableStateFlow<StreamState>(StreamState.Idle) }
    val streamState by (sp?.state ?: fallbackState).collectAsState()
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val l = LocalLayout.current
    val scope = rememberCoroutineScope()

    // barre visible au démarrage du direct (nom de la chaîne) ; en VOD, elle s'ouvre à la demande
    var infoVisible by remember { mutableStateOf(route.kind == com.sokoul.iptv.ui.nav.PlayKind.LIVE) }
    var infoTick by remember { mutableIntStateOf(0) }
    var listVisible by remember { mutableStateOf(false) }
    var listIndex by remember { mutableIntStateOf(0) }
    // univers TV : groupes (◀ dans la liste), mini-guide (▶), bandeau de zapping
    var groupsVisible by remember { mutableStateOf(false) }
    var groupIndex by remember { mutableIntStateOf(0) }
    var guideVisible by remember { mutableStateOf(false) }
    var guideIndex by remember { mutableIntStateOf(0) }
    // OK : détails de la chaîne (bandeau enrichi + actions focalisables)
    var detailsVisible by remember { mutableStateOf(false) }
    val detailsFocus = remember { FocusRequester() }
    val zapPending by vm.zapPending.collectAsStateWithLifecycle()
    // pause du direct : barre visible pendant la pause et 5 s après une action
    val ts by vm.timeshiftState.collectAsStateWithLifecycle()
    var tsBar by remember { mutableStateOf(false) }
    var tsTick by remember { mutableIntStateOf(0) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val currentGroup by vm.currentGroup.collectAsStateWithLifecycle()
    var numberBuffer by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var episodesVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var nextCountdown by remember { mutableStateOf<Int?>(null) }
    var nextDismissedFor by remember { mutableStateOf<String?>(null) }
    // panneau latéral ouvert (Audio, Sous-titres, Sources)
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    // bouton qui a ouvert le panneau : il reprend le focus à la fermeture
    var returnTo by remember { mutableStateOf<PlayerPanel?>(null) }
    val pillFocus = remember { PlayerPanel.entries.associateWith { FocusRequester() } }
    // pause prolongée (TV) : écran « En pause » à la place de la barre
    var pauseScreen by remember { mutableStateOf(false) }
    // position au début d'un saut sans barre (pour afficher le cumul « +1 min 30 »)
    var seekOrigin by remember { mutableLongStateOf(0L) }
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val onlineState by vm.onlineSubs.collectAsStateWithLifecycle()
    val subOffset by vm.subOffsetMs.collectAsStateWithLifecycle()
    val onlineActive = vm.onlineSubs.collectAsStateWithLifecycle().value.activeRef != null
    // sous-titres du flux retardés : dessinés par DelayedStreamCues, celui du lecteur est masqué
    val streamDelay = if (onlineActive) 0L else subOffset.coerceAtLeast(0)
    val intro by vm.intro.collectAsStateWithLifecycle()
    val frameMatch by vm.frameRateMatch.collectAsStateWithLifecycle()
    var introDoneFor by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val scrubberFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    fun showInfo() { infoVisible = true; infoTick++ }
    fun tsShow() { tsBar = true; tsTick++ }
    LaunchedEffect(tsTick, ts.paused, ts.active) { if (!ts.active) tsBar = false else if (!ts.paused) { delay(5_000); tsBar = false } }
    fun applyLive(n: LiveNav) {
        listVisible = n.panels.list; groupsVisible = n.panels.groups; guideVisible = n.panels.guide; detailsVisible = n.panels.details
        listIndex = n.list; groupIndex = n.group; guideIndex = n.guide
    }
    // rappel « Reprise à … » affiché une seule fois (5 s), pas à chaque fermeture de la barre
    var resumeHintDone by remember(ui.resumedFromMs) { mutableStateOf(false) }
    LaunchedEffect(ui.resumedFromMs) { if (ui.resumedFromMs > 0) { delay(5_000); resumeHintDone = true } }
    val exo = sp?.player

    val errorShown = ui.error != null || streamState is StreamState.Failed || streamState is StreamState.Waiting || streamState is StreamState.Busy
    val showNextCard = !ui.isLive && ui.nextEpisode != null && ui.nextEpisode?.id != nextDismissedFor &&
        (ui.endedWithNext || nextCountdown != null)
    // un panneau ou une carte a le focus : les flèches servent à s'y déplacer
    val modal = menu != null || detailsVisible || episodesVisible || (panel != null) || showNextCard || errorShown || ui.needsPin
    // « Passer l'intro » : une fois par épisode, dans la fenêtre apprise pour la série
    val introFor = "${ui.title}|${ui.subtitle}|${ui.currentSe}"
    val introShown = !ui.isLive && !modal && !pauseScreen && introDoneFor != introFor && intro?.let { IntroSkip.visible(position, it) } == true
    LaunchedEffect(ui.lighter) { if (ui.lighter != null) { delay(15_000); vm.dismissLighter() } }

    // fréquence de l'écran calée sur celle du film (VOD seulement : en direct, chaque zapping ferait clignoter la TV)
    val rateSwitch = remember { (context as? android.app.Activity)?.window?.let { com.sokoul.iptv.player.FrameRateMatcher.Switcher(it) } }
    DisposableEffect(Unit) { onDispose { rateSwitch?.restore() } }
    DisposableEffect(exo, ui.isLive, frameMatch, isTv) {
        val sw = rateSwitch
        if (exo == null || sw == null) return@DisposableEffect onDispose {}
        // tablette, téléphone : écran à fréquence fixe, rien à caler
        if (ui.isLive || !frameMatch || !isTv) { sw.restore(); return@DisposableEffect onDispose {} }
        fun check() { exo.videoFormat?.frameRate?.takeIf { it > 0 }?.let(sw::match) }
        val lis = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = check()
            override fun onRenderedFirstFrame() = check()
        }
        // fichier sans fréquence annoncée (MKV) : mesurée sur les premières images
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val estimator = com.sokoul.iptv.player.FrameRateMatcher.Estimator { fps ->
            main.post { if ((exo.videoFormat?.frameRate ?: -1f) <= 0f) sw.match(fps) }
        }
        exo.addListener(lis)
        exo.setVideoFrameMetadataListener(estimator)
        check()
        onDispose { exo.removeListener(lis); exo.clearVideoFrameMetadataListener(estimator) }
    }

    // --- intégration système : PiP, MediaSession, plein écran, arrière-plan ---
    DisposableEffect(Unit) {
        PlaybackUiState.pipEligible = true
        val window = (context as? android.app.Activity)?.window
        val insets = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            PlaybackUiState.pipEligible = false
            insets?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pipNow by rememberUpdatedState(inPip)
    DisposableEffect(lifecycle) {
        // écran quitté sans image dans l'image (Accueil, autre appli, veille) : pause + sauvegarde, connexion libérée en direct
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> if (!pipNow) vm.onBackground()
                Lifecycle.Event.ON_START -> vm.onForeground()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    DisposableEffect(exo) {
        val session = exo?.let { runCatching { MediaSession.Builder(context, it).setId("sokoul-${System.nanoTime()}").build() }.getOrNull() }
        val lis = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        exo?.addListener(lis)
        onDispose { exo?.removeListener(lis); session?.release() }
    }

    // masquage automatique, jamais pendant une pause ni quand un panneau est ouvert
    LaunchedEffect(infoVisible, infoTick, menu, listVisible, isPlaying, episodesVisible, (panel != null)) {
        if (infoVisible && menu == null && !listVisible && !episodesVisible && !(panel != null) && (isPlaying || ui.isLive)) {
            delay(if (ui.isLive) 6_000 else 5_000); infoVisible = false
        }
    }
    // 10 s de pause sans toucher la télécommande : écran « En pause » (TV, à la demande seulement)
    LaunchedEffect(isPlaying, infoTick, modal, streamState) {
        pauseScreen = false
        if (isTv && !ui.isLive && !isPlaying && !modal && exo != null && streamState is StreamState.Playing) {
            delay(10_000)
            pauseScreen = true
            infoVisible = false
        }
    }
    LaunchedEffect(listVisible, listIndex, groupsVisible, groupIndex) {
        if (listVisible || groupsVisible) { delay(12_000); listVisible = false; groupsVisible = false }
    }
    LaunchedEffect(guideVisible, guideIndex) { if (guideVisible) { delay(20_000); guideVisible = false } }
    var guidePrograms by remember(ui.channel?.id) { mutableStateOf<List<com.sokoul.iptv.data.db.ProgramEntity>?>(null) }
    LaunchedEffect(guideVisible, ui.channel?.id) { val c = ui.channel; if (guideVisible && c != null) guidePrograms = vm.upcoming(c) }
    // détails : se referment seuls après 15 s sans toucher la télécommande ; focus sur la première action
    LaunchedEffect(detailsVisible, infoTick) { if (detailsVisible) { delay(15_000); detailsVisible = false } }
    LaunchedEffect(detailsVisible) { if (detailsVisible) repeat(15) { delay(40); if (runCatching { detailsFocus.requestFocus() }.isSuccess) return@LaunchedEffect } }
    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isNotEmpty()) {
            delay(com.sokoul.iptv.ui.screens.tv.NUMBER_SETTLE_MS)
            numberBuffer.toIntOrNull()?.let { vm.jumpToNumber(it); showInfo() }
            numberBuffer = ""
        }
    }
    LaunchedEffect(ui.channel?.id) { if (ui.isLive) showInfo() }
    // position / durée pour les contenus à la demande + compte à rebours de l'épisode suivant
    LaunchedEffect(exo, ui.isLive) {
        while (exo != null && !ui.isLive) {
            position = exo.currentPosition
            duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0
            val remaining = duration - position
            nextCountdown = if (ui.nextEpisode != null && duration > 60_000 && remaining in 1..20_000) (remaining / 1000).toInt() else null
            delay(500)
        }
    }
    LaunchedEffect(streamState) {
        if (streamState is StreamState.Ended && !ui.isLive && (ui.nextEpisode == null || ui.nextEpisode?.id == nextDismissedFor)) nav.popBackStack()
    }
    // focus : barre de lecture à l'ouverture des commandes, écran racine sinon (pour recevoir les touches)
    LaunchedEffect(sp) { if (sp != null) runCatching { focus.requestFocus() } }
    LaunchedEffect(infoVisible, modal) {
        if (modal) return@LaunchedEffect
        // la barre entre en animation : son curseur n'est pas toujours prêt à la première image (vu sur la box,
        // le focus restait sur l'écran et le premier ▼ ne servait à rien) → nouvel essai à chaque image, 600 ms au plus
        repeat(15) {
            delay(40)
            val done = runCatching {
                if (infoVisible && !ui.isLive) {
                    if (l.isTouch) true
                    else returnTo?.let { p -> pillFocus.getValue(p).requestFocus().also { ok -> if (ok) returnTo = null } } ?: scrubberFocus.requestFocus()
                } else focus.requestFocus()
            }.getOrDefault(false)
            if (done) return@LaunchedEffect
        }
    }

    // « Retour » : ferme d'abord ce qui est ouvert (panneau, liste, saisie, barre), sinon quitte le lecteur
    fun backConsumed(): Boolean = when {
        // Retour ferme seulement l'écran de pause (sans montrer la barre) : un second Retour quitte le lecteur
        pauseScreen -> { pauseScreen = false; true }
        panel != null -> { panel = null; true }
        detailsVisible -> { detailsVisible = false; true }
        tsBar -> { tsBar = false; true }
        // zapping en cours : on reste sur la chaîne regardée
        zapPending != null -> vm.cancelZap()
        groupsVisible -> { groupsVisible = false; listVisible = true; true }
        guideVisible -> { guideVisible = false; true }
        episodesVisible -> { episodesVisible = false; true }
        listVisible -> { listVisible = false; true }
        numberBuffer.isNotEmpty() -> { numberBuffer = ""; true }
        seekPreview != null -> { seekPreview = null; true }
        infoVisible && !ui.isLive && isTv -> { infoVisible = false; true }
        // erreur ou attente du fournisseur : sinon Retour retire seulement le focus du bouton (vu sur la box)
        // et l'essai automatique repart → on quitte le lecteur tout de suite
        errorShown -> { nav.popBackStack(); true }
        else -> false
    }
    BackHandler(enabled = pauseScreen || detailsVisible || tsBar || zapPending != null || groupsVisible || guideVisible || (panel != null) || episodesVisible || listVisible || numberBuffer.isNotEmpty() || seekPreview != null || (infoVisible && !ui.isLive && isTv)) { backConsumed() }
    // touche Retour interceptée avant Compose : sinon elle retire seulement le focus de la barre (télécommande « muette »)
    var backDownConsumed by remember { mutableStateOf(false) }
    // filet de sécurité : le lecteur ne reste jamais sans focus (hors menus, qui ont leur propre fenêtre)
    var rootHasFocus by remember { mutableStateOf(true) }
    LaunchedEffect(rootHasFocus, menu, ui.needsPin, inPip) {
        if (rootHasFocus || menu != null || ui.needsPin || inPip) return@LaunchedEffect
        delay(150)
        runCatching { if (infoVisible && !ui.isLive && !l.isTouch && !modal) scrubberFocus.requestFocus() else focus.requestFocus() }
    }

    /**
     * Saut avec aperçu : les appuis se cumulent puis le déplacement est appliqué une seule fois.
     * [reveal] = afficher la barre (saut depuis la barre) ; sinon retour visuel latéral seulement.
     */
    fun seekBy(deltaMs: Long, reveal: Boolean = true) {
        val p = exo ?: return
        if (seekPreview == null) seekOrigin = p.currentPosition
        val base = seekPreview ?: p.currentPosition
        seekPreview = clampSeek(base + deltaMs, p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L)
        if (reveal) showInfo() else infoTick++
    }

    fun playPause() {
        if (ui.isLive) { vm.liveTogglePause(); infoVisible = false; tsShow(); return }
        exo?.let { if (it.isPlaying) vm.pauseAndSave() else it.play() }
        showInfo()
    }

    // valide le déplacement accumulé après une courte pause (comme sur les box du commerce)
    LaunchedEffect(seekPreview) {
        val target = seekPreview ?: return@LaunchedEffect
        delay(700)
        exo?.seekTo(target)
        seekPreview = null
    }

    Box(
        Modifier.fillMaxSize().background(SokoulColors.VideoBlack)
            .onPreviewKeyEvent { e ->
                // écran de pause : la première touche le ferme (OK reprend la lecture, Retour le ferme seul, les autres montrent la barre)
                if (pauseScreen && e.type == KeyEventType.KeyDown) {
                    val code = e.nativeKeyEvent.keyCode
                    pauseScreen = false
                    if (isSelect(code) || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY) playPause()
                    else if (code != KeyEvent.KEYCODE_BACK) showInfo()
                    backDownConsumed = code == KeyEvent.KEYCODE_BACK
                    return@onPreviewKeyEvent true
                }
                if (e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && menu == null) {
                    // appui long : tout fermer d'un coup et revenir à la vidéo
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 1 && ui.isLive) {
                        vm.cancelZap(); listVisible = false; groupsVisible = false; guideVisible = false; detailsVisible = false; panel = null; numberBuffer = ""; infoVisible = false
                        backDownConsumed = true
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 1) return@onPreviewKeyEvent true
                    if (e.type == KeyEventType.KeyDown) { backDownConsumed = e.nativeKeyEvent.repeatCount == 0 && backConsumed(); return@onPreviewKeyEvent backDownConsumed }
                    if (e.type == KeyEventType.KeyUp && backDownConsumed) { backDownConsumed = false; return@onPreviewKeyEvent true }
                    return@onPreviewKeyEvent false
                }
                if (e.type == KeyEventType.KeyDown) infoTick++
                false
            }
            .onFocusChanged { rootHasFocus = it.hasFocus }
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (menu != null) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                val repeat = e.nativeKeyEvent.repeatCount
                if (e.type == KeyEventType.KeyUp) {
                    return@onKeyEvent code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                // touches multimédia : toujours actives
                when (code) {
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_SETTINGS -> {
                        if (ui.isLive && guideVisible) { guideVisible = false; nav.navigate(com.sokoul.iptv.ui.nav.GuideRoute) }
                        else if (!modal || detailsVisible) { detailsVisible = false; menu = Menu.MAIN }
                        return@onKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { playPause(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { exo?.play(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.pauseAndSave(); showInfo(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_PROG_RED -> { if (ui.isLive) vm.record(0) { toast(context, it) }; return@onKeyEvent true }
                    KeyEvent.KEYCODE_LAST_CHANNEL -> { vm.previous(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> { if (!ui.isLive) vm.playNextEpisode(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { if (!ui.isLive) seekBy(-seekStep(repeat, back = true)) else if (ts.active) { vm.timeshiftSeek(-10_000); tsShow() }; return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (!ui.isLive) seekBy(seekStep(repeat, back = false)) else if (ts.active) { vm.timeshiftSeek(30_000); tsShow() }; return@onKeyEvent true }
                }
                // direct : chaîne en erreur, ▲▼ zappent toujours ; le reste va aux boutons
                if (modal && !(ui.isLive && errorShown && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN))) return@onKeyEvent false
                if (ui.isLive && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                    if (numberBuffer.length < 5) numberBuffer += (code - KeyEvent.KEYCODE_0).toString()
                    return@onKeyEvent true
                }
                // barre de la pause du direct : ◀ −10 s, ▶ +30 s, OK pause/reprise, ▲ retour au direct, ▼ masquer
                if (ui.isLive && ts.active && tsBar && !listVisible && !groupsVisible && !guideVisible) {
                    val handled = when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { vm.timeshiftSeek(-10_000); tsShow(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.timeshiftSeek(30_000); tsShow(); true }
                        KeyEvent.KEYCODE_DPAD_UP -> { vm.timeshiftToLive(); tsShow(); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { tsBar = false; true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { if (repeat == 0) vm.liveTogglePause(); tsShow(); true }
                        else -> false
                    }
                    if (handled) return@onKeyEvent true
                }
                if (ui.isLive) {
                    val r = LiveKeys.reduce(
                        code, repeat,
                        LiveNav(LivePanels(listVisible, groupsVisible, guideVisible, detailsVisible), listIndex, groupIndex, guideIndex),
                        LiveLists(
                            channels = zapList.size, groups = groups.size, programs = guidePrograms?.size ?: 0,
                            currentChannel = zapList.indexOfFirst { it.id == ui.channel?.id }, currentGroup = groups.indexOfFirst { it.id == currentGroup },
                        ),
                        inverted = vm.zapInverted,
                    ) ?: return@onKeyEvent false
                    applyLive(r.nav)
                    if (r.hideInfo) infoVisible = false
                    when (val c = r.cmd) {
                        is LiveCmd.Zap -> vm.zap(c.delta)
                        is LiveCmd.Play -> zapList.getOrNull(c.index)?.let { vm.playChannel(it) }
                        is LiveCmd.SelectGroup -> groups.getOrNull(c.index)?.let { vm.selectGroup(it.id) }
                        null -> Unit
                    }
                    true
                } else {
                    // VOD : commandes affichées → la navigation au focus prend la main
                    if (infoVisible || !isDpad(code)) return@onKeyEvent false
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-seekStep(repeat, back = true), reveal = false); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(seekStep(repeat, back = false), reveal = false); true }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { showInfo(); true }
                        else -> {
                            if (repeat == 0) when {
                                ui.lighter != null -> vm.acceptLighter()
                                introShown -> { introDoneFor = introFor; vm.skipIntro() }
                                else -> playPause()
                            }
                            true
                        }
                    }
                }
            }
            .focusable()
            .pointerInput(ui.isLive) {
                detectTapGestures(
                    onTap = {
                        // un tap sur l'image ferme ce qui est ouvert (listes, programmes, détails, pause du direct), sinon montre / masque la barre
                        when {
                            listVisible || groupsVisible || guideVisible || detailsVisible || tsBar -> {
                                listVisible = false; groupsVisible = false; guideVisible = false; detailsVisible = false; tsBar = false
                            }
                            infoVisible -> infoVisible = false
                            else -> showInfo()
                        }
                    },
                    onDoubleTap = { offset ->
                        // double-tap : même chemin que le D-pad (cumul, bornes, un seul déplacement)
                        if (!ui.isLive) seekBy(if (offset.x < size.width / 2) -10_000L else 30_000L, reveal = false)
                    },
                )
            }
            // tactile, direct : glisser vers le haut / le bas sur l'image zappe (sens réglable comme ▲▼)
            .then(if (l.isTouch && ui.isLive) Modifier.pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { change, dy -> drag += dy; change.consume() },
                    onDragEnd = {
                        val free = !listVisible && !groupsVisible && !guideVisible && !detailsVisible && panel == null && menu == null && !tsBar
                        if (free && kotlin.math.abs(drag) > SwipeZapDistance.toPx()) {
                            vm.zap(swipeZapDelta(swipedUp = drag < 0, inverted = vm.zapInverted))
                            infoVisible = false
                        }
                    },
                )
            } else Modifier)
    ) {
        if (exo != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        keepScreenOn = true
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setKeepContentOnPlayerReset(true)
                        isFocusable = false
                    }
                },
                update = { view ->
                    view.player = exo
                    view.subtitleView?.visibility = if (streamDelay > 0) android.view.View.INVISIBLE else android.view.View.VISIBLE
                    view.resizeMode = effectiveResizeMode(resizeIndex, portrait = l.heightDp > l.widthDp)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        OnlineCues(vm, exo, subOffset, Modifier.align(Alignment.BottomCenter).padding(bottom = if (infoVisible && !inPip) 190.dp else if (inPip) 8.dp else 56.dp))
        DelayedStreamCues(exo, streamDelay, Modifier.fillMaxSize())
        if (!inPip) {
            val lighter = ui.lighter
            val bottom = if (infoVisible) 190.dp else 56.dp
            when {
                lighter != null -> OkPrompt("Ça coupe ? Passer à une version plus légère", lighter.second, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = bottom)) { vm.acceptLighter() }
                introShown -> OkPrompt("Passer l'intro", null, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = bottom)) { introDoneFor = introFor; vm.skipIntro() }
            }
        }

        if (!inPip) {
            if (!ui.needsPin) StatusOverlay(streamState, ui.error, retryFocus, onRetry = { vm.retry() }, onBack = { nav.popBackStack() }, onStopRecording = vm::stopRecordings)
            if (ui.needsPin) {
                com.sokoul.iptv.ui.components.PinDialog(
                    "Contenu verrouillé — code parental",
                    onResult = vm::submitPin,
                    onDismiss = { nav.popBackStack() },
                    error = ui.pinError,
                )
            }

            // reprise : rappel discret en haut à gauche, qui glisse et s'efface
            AnimatedVisibility(
                !ui.isLive && ui.resumedFromMs > 0 && !infoVisible && !resumeHintDone && !pauseScreen, Modifier.align(Alignment.TopStart),
                enter = slideInVertically(Motion.enter()) { -it / 2 } + fadeIn(Motion.enter()), exit = fadeOut(Motion.exit()),
            ) { ResumeHint(ui.resumedFromMs) }

            // saut sans barre (D-pad, double-tap) : retour latéral cumulé
            val previewTarget = seekPreview
            AnimatedVisibility(
                !ui.isLive && previewTarget != null && !infoVisible, Modifier.fillMaxHeight().align(if ((previewTarget ?: 0) >= seekOrigin) Alignment.CenterEnd else Alignment.CenterStart),
                enter = fadeIn(Motion.enter()), exit = fadeOut(Motion.exit()),
            ) {
                var last by remember { mutableLongStateOf(0L) }
                if (previewTarget != null) last = previewTarget
                SeekFeedback(last - seekOrigin, last)
            }

            // actions du direct : bandeau des détails (OK) et, en tactile, barre ouverte d'un tap
            fun liveActions(): List<PlayerAction> = buildList {
                if (l.isTouch) add(PlayerAction(if (ts.active && ts.paused) "Reprendre" else "Pause du direct", if (ts.active && ts.paused) Icons.Default.PlayArrow else Icons.Default.Pause) { detailsVisible = false; playPause() })
                add(PlayerAction("Programmes", Icons.Default.CalendarViewWeek) { detailsVisible = false; infoVisible = false; guideIndex = 0; guideVisible = true })
                add(PlayerAction("Chaînes", Icons.Default.List) { detailsVisible = false; infoVisible = false; listIndex = zapList.indexOfFirst { it.id == ui.channel?.id }.coerceAtLeast(0); listVisible = true })
                if (ui.canCatchup && ui.nowNext?.now != null) add(PlayerAction("Revoir depuis le début", Icons.Default.History) { detailsVisible = false; vm.restartCurrentProgram() })
                add(PlayerAction("Enregistrer", Icons.Default.FiberManualRecord) { detailsVisible = false; menu = Menu.RECORD })
                add(PlayerAction(if (ui.isFavorite) "Retirer des favoris" else "Favori", if (ui.isFavorite) Icons.Default.StarBorder else Icons.Default.Star) { vm.toggleFavorite() })
                if (vm.hasPrevious()) add(PlayerAction("Chaîne précédente", Icons.Default.SwapVert) { detailsVisible = false; vm.previous() })
                add(PlayerAction("Réglages", Icons.Default.Tune) { detailsVisible = false; menu = Menu.MAIN })
            }
            val controlsShown = !tsBar && infoVisible && !listVisible && !groupsVisible && !guideVisible && zapPending == null && !episodesVisible && !(panel != null) && !errorShown && !pauseScreen && !showNextCard
            if (!ui.isLive) {
                AnimatedVisibility(
                    controlsShown, Modifier.align(Alignment.TopCenter),
                    enter = slideInVertically(Motion.enter()) { -it / 3 } + fadeIn(Motion.enter()), exit = slideOutVertically(Motion.exit()) { -it / 3 } + fadeOut(Motion.exit()),
                ) {
                    VodTopBar(
                        ui,
                        leading = if (l.isTouch) listOf(
                            PlayerAction("Retour", Icons.AutoMirrored.Filled.ArrowBack) { nav.popBackStack() },
                            PlayerAction("Image dans l'image", Icons.Default.PictureInPicture) { (context as? MainActivity)?.enterPipIfPossible() },
                        ) else emptyList(),
                    )
                }
            }
            AnimatedVisibility(
                controlsShown, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(Motion.enter()) { it / 4 } + fadeIn(Motion.enter()), exit = slideOutVertically(Motion.exit()) { it / 4 } + fadeOut(Motion.exit()),
            ) {
                if (ui.isLive) LiveInfoBar(ui, actions = if (l.isTouch) liveActions() else null, health = healthOf(streamState)) else VodControls(
                    position, duration, seekPreview,
                    buffering = streamState is StreamState.Buffering,
                    playing = isPlaying,
                    scrubberFocus = scrubberFocus, playFocus = playFocus,
                    onBack10 = { seekBy(-10_000L) },
                    onPlayPause = ::playPause,
                    onFwd30 = { seekBy(30_000L) },
                    pills = vodPills(
                        version = remember(tracks, ui.sources) { versionPillText(ui.sources.firstOrNull { it.current }, audioPillText(vm.audioOptions())) },
                        subtitles = remember(tracks, onlineState) { subtitlePillText(vm.textOptions()) },
                        onVersions = { panel = PlayerPanel.VERSIONS; returnTo = PlayerPanel.VERSIONS }, onSubtitles = { panel = PlayerPanel.SUBTITLES; returnTo = PlayerPanel.SUBTITLES },
                        onMore = { menu = Menu.MAIN }, focus = pillFocus,
                    ),
                    tools = vodTools(ui, hasEpisodes = episodes.size > 1, onEpisodes = { episodesVisible = true }, onNext = { vm.playNextEpisode() }),
                    onSeekStep = { seekBy(it) },
                    onSeekTo = { exo?.seekTo(it); showInfo() },
                    onInteract = { infoTick++ },
                )
            }
            if (l.isTouch && ui.isLive) {
                AnimatedVisibility(infoVisible && !listVisible && !episodesVisible, Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
                    TopBarActions(
                        buildList {
                            add(PlayerAction("Retour", Icons.AutoMirrored.Filled.ArrowBack) { nav.popBackStack() })
                            if (ui.isLive) add(PlayerAction("Chaînes", Icons.Default.List) { listIndex = zapList.indexOfFirst { it.id == ui.channel?.id }.coerceAtLeast(0); listVisible = true })
                            add(PlayerAction("Image dans l'image", Icons.Default.PictureInPicture) { (context as? MainActivity)?.enterPipIfPossible() })
                            if (ui.isLive) add(PlayerAction("Options", Icons.Default.MoreVert) { menu = Menu.MAIN })
                        }
                    )
                }
            }
            // univers TV : liste des chaînes, groupes, mini-guide, bandeau de zapping
            val side = if (l.isCompact) 8.dp else 40.dp
            val clock = rememberNow(30_000)
            // largeurs des tiroirs bornées à l'écran (tablette en portrait : 800 dp pour catégories + chaînes)
            val drawers = remember(l.widthDp) { DrawerWidths.of(l.widthDp) }
            AnimatedVisibility(listVisible, Modifier.align(Alignment.CenterStart).padding(start = if (groupsVisible) drawers.groups.dp else 0.dp), enter = slideInHorizontally(Motion.enter()) { -it } + fadeIn(Motion.enter()), exit = slideOutHorizontally(Motion.exit()) { -it } + fadeOut(Motion.exit())) {
                var nowMap by remember { mutableStateOf<Map<String, com.sokoul.iptv.data.db.ProgramEntity>>(emptyMap()) }
                LaunchedEffect(zapList) { nowMap = vm.nowFor(zapList) }
                ChannelOverlay(
                    groups.firstOrNull { it.id == currentGroup }?.name ?: "Chaînes", zapList, listIndex, ui.channel?.id, nowMap, clock, withGroups = groupsVisible,
                    width = (if (groupsVisible) drawers.channelsBeside else drawers.channels).dp,
                    // tactile : pas de touche ◀ pour ouvrir les catégories
                    onGroups = if (l.isTouch && !groupsVisible) ({ groupIndex = groups.indexOfFirst { it.id == currentGroup }.coerceAtLeast(0); groupsVisible = true }) else null,
                ) { i -> zapList.getOrNull(i)?.let { vm.playChannel(it) }; listVisible = false; groupsVisible = false }
            }
            AnimatedVisibility(groupsVisible, Modifier.align(Alignment.CenterStart), enter = slideInHorizontally(Motion.enter()) { -it } + fadeIn(Motion.enter()), exit = slideOutHorizontally(Motion.exit()) { -it } + fadeOut(Motion.exit())) {
                GroupsPanel(groups, groupIndex, currentGroup, width = drawers.groups.dp) { i -> groups.getOrNull(i)?.let { vm.selectGroup(it.id) }; groupsVisible = false; listIndex = 0; listVisible = true }
            }
            AnimatedVisibility(guideVisible && ui.channel != null, Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally(Motion.enter()) { it } + fadeIn(Motion.enter()), exit = slideOutHorizontally(Motion.exit()) { it } + fadeOut(Motion.exit())) {
                ui.channel?.let { c -> MiniGuide(c, guidePrograms, guideIndex, clock, width = drawers.guide.dp, onPick = { guideIndex = it }) }
            }
            AnimatedVisibility(
                ui.isLive && ts.active && tsBar && !listVisible && !guideVisible, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(Motion.enter()) { it / 4 } + fadeIn(Motion.enter()), exit = slideOutVertically(Motion.exit()) { it / 4 } + fadeOut(Motion.exit()),
            ) {
                TimeshiftBar(
                    ts, ui.channel?.name ?: ui.title,
                    onSeek = { vm.timeshiftSeek(it); tsShow() }, onToggle = { vm.liveTogglePause(); tsShow() }, onLive = { vm.timeshiftToLive(); tsShow() },
                )
            }
            if (ui.isLive && ts.active && !tsBar && !ts.atLive && !listVisible && !guideVisible) {
                // tactile : toucher le repère rouvre la barre de la pause du direct
                DelayBadge(ts.behindMs, Modifier.align(Alignment.TopEnd).padding(24.dp).then(if (l.isTouch) Modifier.pointerInput(Unit) { detectTapGestures { tsShow() } } else Modifier))
            }
            // OK : détails de la chaîne (bandeau enrichi + actions)
            AnimatedVisibility(
                detailsVisible && ui.channel != null, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(Motion.enter()) { it / 4 } + fadeIn(Motion.enter()), exit = slideOutVertically(Motion.exit()) { it / 4 } + fadeOut(Motion.exit()),
            ) {
                LiveInfoBar(
                    ui,
                    actions = liveActions(),
                    firstAction = detailsFocus,
                    health = healthOf(streamState),
                )
            }
            // bandeau de zapping (ou de saisie d'un numéro) : la chaîne visée, avant l'ouverture du flux
            val typed = numberBuffer.toIntOrNull()?.let { n -> zapList.firstOrNull { it.number == n } }
            val banner = zapPending ?: typed
            AnimatedVisibility(banner != null, Modifier.align(Alignment.BottomStart).padding(start = side, bottom = if (l.isCompact) 16.dp else 40.dp), enter = fadeIn(tween(Motion.FAST)), exit = fadeOut(Motion.exit())) {
                var lastBanner by remember { mutableStateOf(banner) }
                if (banner != null) lastBanner = banner
                lastBanner?.let { ZapBanner(it, number = if (zapPending == null && typed != null) numberBuffer else null) }
            }
            AnimatedVisibility(episodesVisible, Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
                EpisodesPanel(episodes, ui.currentSe, ui.seriesBackdrop ?: ui.image, Modifier, tmdb = tmdbEpisodes, onSeason = vm::loadTmdbSeason) { e -> episodesVisible = false; vm.playEpisodeNow(e) }
            }
            AnimatedVisibility(panel != null, Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
                // le panneau reste affiché pendant sa sortie
                var shown by remember { mutableStateOf(panel) }
                if (panel != null) shown = panel
                val resumeAt = formatDuration(position)
                when (shown) {
                    PlayerPanel.VERSIONS -> {
                        val audioOpts = remember(tracks) { vm.audioOptions() }
                        VersionPanel(
                            audioOpts, if (ui.isLive) emptyList() else ui.sources, ui.languages, if (ui.isLive) SourceSearch.DONE else ui.searchState, resumeAt,
                            onTrack = { vm.selectTrack(C.TRACK_TYPE_AUDIO, it) }, onSource = { panel = null; vm.switchVersion(it.id) },
                        )
                    }
                    PlayerPanel.SUBTITLES -> {
                        val online by vm.onlineSubs.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { vm.searchOnlineSubtitles() }
                        val textOpts = remember(tracks, online) { vm.textOptions() }
                        val on = textOpts.firstOrNull()?.selected == false
                        SubtitlePanel(textOpts, offsetMs = if (on) subOffset else null, delayOnly = online.activeRef == null, onShift = vm::shiftSubtitles) { vm.selectTrack(C.TRACK_TYPE_TEXT, it) }
                    }
                    null -> Unit
                }
            }
            AnimatedVisibility(pauseScreen, enter = fadeIn(tween(Motion.SLOW)), exit = fadeOut(Motion.exit())) {
                PauseScreen(ui, position, duration)
            }
            if (numberBuffer.isNotEmpty() && typed == null) {
                Text(
                    numberBuffer, Modifier.align(Alignment.TopEnd).padding(32.dp).background(SokoulColors.black(0.8f), Radius.Card).padding(horizontal = 22.dp, vertical = 10.dp),
                    style = com.sokoul.iptv.ui.theme.Type.ChannelNumber, color = SokoulColors.OnVideo,
                )
            }
            // carte « Épisode suivant » : glisse depuis la droite ; le dernier épisode connu reste affiché pendant la sortie
            val next = ui.nextEpisode
            var lastNext by remember { mutableStateOf(next) }
            if (next != null) lastNext = next
            AnimatedVisibility(
                showNextCard && next != null && !episodesVisible && !(panel != null), Modifier.align(Alignment.BottomEnd),
                enter = slideInHorizontally(Motion.enter()) { it / 2 } + fadeIn(Motion.enter()), exit = slideOutHorizontally(Motion.exit()) { it / 2 } + fadeOut(Motion.exit()),
            ) {
                lastNext?.let { n ->
                    NextUpCard(
                        n, seconds = if (vm.autoplayNext && !ui.endedWithNext) nextCountdown else null,
                        // l'image de l'épisode suivant d'abord (dans la carte), sinon le fond de la série — jamais l'épisode en cours
                        fallbackImage = ui.seriesBackdrop, playFocus = nextFocus, tmdb = tmdbEpisodes[n.season to n.episode],
                        modifier = Modifier.padding(if (l.isCompact) 12.dp else 40.dp),
                        onPlay = { vm.playNextEpisode() },
                        onCancel = {
                            nextDismissedFor = n.id
                            vm.cancelAutoplay(n.id)
                            if (ui.endedWithNext) { vm.dismissEndCard(); nav.popBackStack() }
                        },
                    )
                }
            }
        }
    }

    // --- menus ---
    when (menu) {
        Menu.MAIN -> OptionsDialog(
            if (ui.isLive) "Réglages du lecteur" else ui.title,
            buildList {
                // direct : d'abord le lecteur (son, image, décodage, statistiques), puis ce que propose une box TV
                if (ui.isLive) {
                    add(MenuOption("Audio", Icons.Default.Audiotrack) { panel = PlayerPanel.VERSIONS })
                    add(MenuOption("Sous-titres", Icons.Default.ClosedCaption) { panel = PlayerPanel.SUBTITLES })
                    add(MenuOption("Format d'image : ${resizeModes[resizeIndex.coerceIn(0, resizeModes.lastIndex)].second}", Icons.Default.AspectRatio) { scope.launch { delay(50); menu = Menu.ASPECT } })
                    add(MenuOption("Décodage : ${(vm.sessionDecoder ?: vm.defaultDecoder).label}", Icons.Default.Memory) { scope.launch { delay(50); menu = Menu.DECODER } })
                    add(MenuOption("Statistiques du flux", Icons.Default.Info) { scope.launch { delay(50); menu = Menu.INFO } })
                }
                if (!ui.isLive) add(MenuOption("Depuis le début", Icons.Default.Replay) { vm.restartFromBeginning() })
                // direct : d'abord ce que propose une box TV (guide, enregistrer, favori, recherche, enregistrements)
                if (ui.isLive) {
                    add(MenuOption("Guide TV", Icons.Default.CalendarViewWeek) { nav.navigate(com.sokoul.iptv.ui.nav.GuideRoute) })
                    add(MenuOption("Enregistrer", Icons.Default.FiberManualRecord) { scope.launch { delay(50); menu = Menu.RECORD } })
                    add(MenuOption(if (ui.isFavorite) "Retirer des favoris" else "Ajouter aux favoris", if (ui.isFavorite) Icons.Default.StarBorder else Icons.Default.Star) { vm.toggleFavorite() })
                    add(MenuOption("Rechercher une chaîne", Icons.Default.Search) { nav.navigate(com.sokoul.iptv.ui.nav.SearchRoute(com.sokoul.iptv.data.repo.SearchOrigin.TV)) })
                    add(MenuOption("Enregistrements", Icons.Default.VideoLibrary) { nav.navigate(com.sokoul.iptv.ui.nav.RecordingsRoute) })
                    if (ui.canCatchup && ui.nowNext?.now != null) add(MenuOption("Revoir depuis le début du programme", Icons.Default.History) { vm.restartCurrentProgram() })
                    // tactile : pas de touche ◀ pour revenir à la chaîne précédente
                    if (l.isTouch) add(MenuOption("Chaîne précédente", Icons.Default.SwapVert) { vm.previous() })
                }
                if (!ui.isLive) add(MenuOption("Format d'image : ${resizeModes[resizeIndex.coerceIn(0, resizeModes.lastIndex)].second}", Icons.Default.AspectRatio) { scope.launch { delay(50); menu = Menu.ASPECT } })
                if (ui.isLive) {
                    add(MenuOption("Ajouter au multi-écran", Icons.Default.GridView) { vm.addToMultiview()?.let { toast(context, it) }; nav.navigate(MultiviewRoute) })
                } else {
                    if (ui.channel != null) add(MenuOption("Retour au direct", Icons.Default.LiveTv) { vm.backToLive() })
                }
                if (!ui.isLive) add(MenuOption("Infos du flux", Icons.Default.Info) { scope.launch { delay(50); menu = Menu.INFO } })
            },
            onDismiss = { if (menu == Menu.MAIN) menu = null },
        )
        Menu.ASPECT -> OptionsDialog(
            "Format d'image",
            resizeModes.mapIndexed { i, (_, label) -> MenuOption(label, Icons.Default.AspectRatio, i == resizeIndex) { vm.setResizeMode(i) } },
            onDismiss = { menu = null },
        )
        Menu.RECORD -> OptionsDialog(
            "Enregistrer ${ui.channel?.name ?: ""}",
            buildList {
                ui.nowNext?.now?.let { now ->
                    add(MenuOption("Jusqu'à la fin de « ${now.title} »", Icons.Default.FiberManualRecord) { vm.record(0) { toast(context, it) } })
                }
                listOf(30, 60, 120, 180).forEach { m -> add(MenuOption("$m minutes", Icons.Default.FiberManualRecord) { vm.record(m) { toast(context, it) } }) }
            },
            onDismiss = { menu = null },
        )
        Menu.INFO -> OptionsDialog(
            "Infos du flux",
            listOf(MenuOption(ui.videoInfo.ifBlank { "Informations indisponibles" }) {}),
            subtitle = when (val s = streamState) {
                is StreamState.Reconnecting -> "Reconnexion n°${s.attempt}"
                is StreamState.Failed -> s.message
                else -> null
            },
            onDismiss = { menu = null },
        )
        Menu.DECODER -> OptionsDialog(
            "Décodage (cette séance)",
            com.sokoul.iptv.data.DecoderMode.entries.map { m ->
                MenuOption(m.label, Icons.Default.Memory, m == (vm.sessionDecoder ?: vm.defaultDecoder)) { vm.setSessionDecoder(m) }
            },
            subtitle = "Si l'image se fige ou saccade, essayez un autre mode. Le flux est rouvert ; le réglage par défaut reste dans Réglages › Lecture.",
            onDismiss = { menu = null },
        )
        null -> Unit
    }
    LaunchedEffect(ui.notice) { ui.notice?.let { toast(context, it) } }
    LaunchedEffect(menu) {
        if (menu == null) runCatching { if (infoVisible && !ui.isLive && !l.isTouch) scrubberFocus.requestFocus() else focus.requestFocus() }
    }
}

/**
 * Pastilles du lecteur à la demande, toujours dans le même ordre : Version (langue, qualité et origine de ce qu'on regarde :
 * « Français · 1080p · IPTV », un seul choix puisque changer de langue change souvent de fichier), Sous-titres, puis Plus (icône).
 */
internal fun vodPills(
    version: String, subtitles: String?,
    onVersions: () -> Unit, onSubtitles: () -> Unit, onMore: () -> Unit,
    focus: Map<PlayerPanel, FocusRequester> = emptyMap(),
): List<PlayerAction> = listOf(
    PlayerAction("Langue et qualité", Icons.Default.Audiotrack, text = version, focus = focus[PlayerPanel.VERSIONS], onClick = onVersions),
    PlayerAction("Sous-titres", Icons.Default.ClosedCaption, text = subtitles ?: "Désactivés", focus = focus[PlayerPanel.SUBTITLES], onClick = onSubtitles),
    PlayerAction("Plus d'options", Icons.Default.MoreHoriz, iconOnly = true, onClick = onMore),
)

/** Boutons ronds après ⏯ (séries) : Épisodes, Suivant. */
internal fun vodTools(ui: PlayerUi, hasEpisodes: Boolean, onEpisodes: () -> Unit, onNext: () -> Unit): List<PlayerAction> = buildList {
    if (hasEpisodes) add(PlayerAction("Épisodes", Icons.Default.VideoLibrary, onClick = onEpisodes))
    if (ui.nextEpisode != null) add(PlayerAction("Épisode suivant", Icons.Default.SkipNext, onClick = onNext))
}

/** Pastille Audio : la langue de la piste en cours (« Français »), sans canaux ni codec. */
internal fun audioPillText(options: List<TrackOption>, fallback: String? = null): String? {
    val label = (options.firstOrNull { it.selected } ?: options.singleOrNull())?.label?.substringBefore(" · ")?.takeIf { it.isNotBlank() }
    // piste sans langue déclarée (« Piste principale ») : la langue annoncée par la source
    return if (label == null || label.startsWith("Piste")) fallback ?: label else label
}

/** Pastille Version : langue entendue (piste choisie, sinon langue de la source), qualité, origine : « Français · 1080p · IPTV ». */
internal fun versionPillText(current: PlayerSource?, trackLanguage: String?): String = listOfNotNull(
    trackLanguage?.takeUnless { it.startsWith("Piste") } ?: current?.let { it.language?.name ?: it.region },
    current?.quality?.let(com.sokoul.iptv.ui.components.SourceLabels::quality),
    current?.origin?.let(com.sokoul.iptv.ui.components.SourceLabels::originShort),
).joinToString(" · ").ifEmpty { "Langue et qualité" }

/** Pastille Sous-titres : « Désactivés » ou la langue choisie. */
internal fun subtitlePillText(options: List<TrackOption>): String =
    options.drop(1).firstOrNull { it.selected && !it.info }?.label?.substringBefore(" · ") ?: "Désactivés"

private fun toast(context: android.content.Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()


/**
 * Bandeau du direct. Avec [actions] (touche OK) : détails de la chaîne — résumé, santé du flux et rangée d'actions
 * focalisables ; sinon simple bandeau d'information avec les légendes de touches.
 */
@Composable
private fun LiveInfoBar(ui: PlayerUi, actions: List<PlayerAction>? = null, firstAction: FocusRequester? = null, health: StreamHealth? = null) {
    val now = rememberNow(15_000)
    val c = ui.channel
    val compact = LocalLayout.current.isCompact
    val side = if (compact) 16.dp else 40.dp
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.black(0.9f), SokoulColors.black(0.95f))))
            .padding(start = side, end = side, top = if (compact) 40.dp else 60.dp, bottom = if (compact) 20.dp else 32.dp)
    ) {
        Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c != null) {
                if (!compact) Text(c.number.toString(), Modifier.padding(end = 18.dp), style = com.sokoul.iptv.ui.theme.Type.ChannelNumberMedium, color = SokoulColors.OnVideo)
                ChannelLogo(c.logo, c.name, if (compact) Modifier.size(64.dp, 40.dp) else Modifier.size(120.dp, 72.dp))
            }
            Column(Modifier.weight(1f).padding(start = if (compact) 12.dp else 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.title, style = if (compact) MaterialTheme.typography.headlineSmall else com.sokoul.iptv.ui.theme.Type.PlayerTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (ui.isFavorite) Icon(Icons.Default.Star, null, Modifier.padding(start = 8.dp).size(18.dp), tint = SokoulColors.Warning)
                    if (ui.canCatchup) Icon(Icons.Default.History, null, Modifier.padding(start = 8.dp).size(18.dp), tint = SokoulColors.TextSecondary)
                }
                val p = ui.nowNext?.now
                if (p != null) {
                    Text(p.title, style = MaterialTheme.typography.titleLarge, color = SokoulColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(formatClock(p.start), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.padding(horizontal = 10.dp).weight(1f).height(4.dp).background(SokoulColors.white(0.2f), Radius.Tiny)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(ui.nowNext.progress(now)).background(SokoulColors.Accent, Radius.Tiny))
                        }
                        Text(formatClock(p.end), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                    if (!compact) p.description?.let { Text(it, Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = if (actions != null) 4 else 2, overflow = TextOverflow.Ellipsis) }
                } else {
                    Text(ui.subtitle ?: "Pas d'information de programme", color = SokoulColors.TextMuted)
                }
                ui.nowNext?.next?.let { n ->
                    Text("Ensuite · ${formatClock(n.start)}  ${n.title}", Modifier.padding(top = 6.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            if (!compact) Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 24.dp)) {
                Text(formatClock(now), style = MaterialTheme.typography.headlineSmall)
                if (health != null) HealthBadge(health, Modifier.padding(top = 4.dp))
                if (ui.videoInfo.isNotBlank()) Text(ui.videoInfo, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        // actions des détails : rangée pleine largeur qui défile au focus (jamais coupée)
        if (actions != null) {
            androidx.compose.foundation.lazy.LazyRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(actions.size) { i ->
                    PillButton(actions[i], if (i == 0 && firstAction != null) Modifier.focusRequester(firstAction) else Modifier)
                }
            }
        }
        }
        if (!compact) KeyHints(
            if (actions != null) listOf(KeyHint("◀▶", "Choisir"), KeyHint("OK", "Valider"), KeyHint("Retour", "Fermer"))
            else listOf(KeyHint("OK", "Détails"), KeyHint("◀", "Chaînes"), KeyHint("▶", "Programmes"), KeyHint("▲▼", "Zapper"), KeyHint("Menu", "Réglages")),
            Modifier.align(Alignment.BottomEnd).padding(top = 8.dp).offset(y = 18.dp),
        )
    }
}

/** Santé du flux en un coup d'œil : pastille verte, orange ou rouge et un mot. */
internal enum class StreamHealth(val label: String) { GOOD("Flux stable"), WEAK("Flux fragile"), BAD("Flux interrompu") }

internal fun healthOf(s: StreamState): StreamHealth = when (s) {
    is StreamState.Playing -> StreamHealth.GOOD
    is StreamState.Failed, is StreamState.Waiting, is StreamState.Busy -> StreamHealth.BAD
    else -> StreamHealth.WEAK
}

@Composable
private fun HealthBadge(h: StreamHealth, modifier: Modifier = Modifier) {
    val color = when (h) { StreamHealth.GOOD -> SokoulColors.Success; StreamHealth.WEAK -> SokoulColors.Warning; StreamHealth.BAD -> SokoulColors.Live }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, Radius.Round))
        Text(h.label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextSecondary, maxLines = 1)
    }
}




/**
 * Sous-titre en ligne choisi (OpenSubtitles, Subdl) : dessiné par-dessus la vidéo, synchronisé sur la position
 * du lecteur (lue 5 fois par seconde), sans relancer le flux. Lecture dans ce composant seulement (pas de
 * recomposition de tout l'écran).
 */
@Composable
private fun OnlineCues(vm: PlayerViewModel, exo: androidx.media3.common.Player?, offsetMs: Long, modifier: Modifier) {
    val cues by vm.cues.collectAsStateWithLifecycle()
    if (cues.isEmpty() || exo == null) return
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cues, exo, offsetMs) {
        while (true) {
            text = com.sokoul.iptv.data.net.providers.SubtitleFile.at(cues, exo.currentPosition - offsetMs)?.text
            delay(200)
        }
    }
    val t = text ?: return
    val l = LocalLayout.current
    Text(
        t, modifier.padding(horizontal = 48.dp).background(SokoulColors.black(0.7f), Radius.Tiny).padding(horizontal = 12.dp, vertical = 4.dp),
        color = SokoulColors.OnVideo, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = if (l.isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
    )
}

/**
 * Sous-titres du flux retardés de [delayMs] : chaque changement de sous-titre est noté avec la position
 * du lecteur puis affiché [delayMs] plus tard. Un saut vide la file (pas d'ancien texte qui ressurgit).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun DelayedStreamCues(exo: androidx.media3.common.Player?, delayMs: Long, modifier: Modifier) {
    if (exo == null || delayMs <= 0) return
    val queue = remember(exo) { ArrayDeque<Pair<Long, List<androidx.media3.common.text.Cue>>>() }
    var shown by remember(exo) { mutableStateOf<List<androidx.media3.common.text.Cue>>(emptyList()) }
    DisposableEffect(exo) {
        val lis = object : Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                queue.addLast(exo.currentPosition to cueGroup.cues)
                while (queue.size > 200) queue.removeFirst()
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) { queue.clear(); shown = emptyList() }
            }
        }
        exo.addListener(lis)
        onDispose { exo.removeListener(lis) }
    }
    LaunchedEffect(exo, delayMs) {
        while (true) {
            val due = exo.currentPosition - delayMs
            var next: List<androidx.media3.common.text.Cue>? = null
            while (queue.isNotEmpty() && queue.first().first <= due) next = queue.removeFirst().second
            if (next != null) shown = next
            delay(100)
        }
    }
    AndroidView(
        factory = { ctx -> androidx.media3.ui.SubtitleView(ctx).apply { setUserDefaultStyle(); setUserDefaultTextSize() } },
        update = { it.setCues(shown) },
        modifier = modifier,
    )
}

/** Pastille « OK : … » en bas à droite (Passer l'intro, version plus légère). Touche OK ou appui. */
@Composable
private fun OkPrompt(title: String, detail: String?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.background(SokoulColors.black(0.78f), Radius.Card)
            .border(com.sokoul.iptv.ui.theme.Edge.Hairline, SokoulColors.white(0.25f), Radius.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("OK", Modifier.background(SokoulColors.OnVideo, Radius.Tiny).padding(horizontal = 8.dp, vertical = 2.dp), color = SokoulColors.OnLight, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = SokoulColors.OnVideo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (detail != null) Text(detail, color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
