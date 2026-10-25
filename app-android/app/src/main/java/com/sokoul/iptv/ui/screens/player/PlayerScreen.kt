package com.sokoul.iptv.ui.screens.player

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

private enum class Menu { MAIN, ASPECT, RECORD, INFO, VERSION }

private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Adapter",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Remplir (rogner)",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Étirer",
)

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
    var numberBuffer by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var episodesVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var nextCountdown by remember { mutableStateOf<Int?>(null) }
    var nextDismissedFor by remember { mutableStateOf<String?>(null) }
    var tracksVisible by remember { mutableStateOf(false) }
    // pause prolongée (TV) : écran « En pause » à la place de la barre
    var pauseScreen by remember { mutableStateOf(false) }
    // position au début d'un saut sans barre (pour afficher le cumul « +1 min 30 »)
    var seekOrigin by remember { mutableLongStateOf(0L) }
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val scrubberFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    fun showInfo() { infoVisible = true; infoTick++ }
    // rappel « Reprise à … » affiché une seule fois (5 s), pas à chaque fermeture de la barre
    var resumeHintDone by remember(ui.resumedFromMs) { mutableStateOf(false) }
    LaunchedEffect(ui.resumedFromMs) { if (ui.resumedFromMs > 0) { delay(5_000); resumeHintDone = true } }
    val exo = sp?.player

    val errorShown = ui.error != null || streamState is StreamState.Failed || streamState is StreamState.Waiting
    val showNextCard = !ui.isLive && ui.nextEpisode != null && ui.nextEpisode?.id != nextDismissedFor &&
        (ui.endedWithNext || nextCountdown != null)
    // un panneau ou une carte a le focus : les flèches servent à s'y déplacer
    val modal = menu != null || episodesVisible || tracksVisible || showNextCard || errorShown || ui.needsPin

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
    LaunchedEffect(infoVisible, infoTick, menu, listVisible, isPlaying, episodesVisible, tracksVisible) {
        if (infoVisible && menu == null && !listVisible && !episodesVisible && !tracksVisible && (isPlaying || ui.isLive)) {
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
    LaunchedEffect(listVisible, listIndex) {
        if (listVisible) { delay(12_000); listVisible = false }
    }
    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isNotEmpty()) {
            delay(1_600)
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
        delay(40)
        runCatching {
            if (infoVisible && !ui.isLive) { if (l.isTouch) Unit else scrubberFocus.requestFocus() } else focus.requestFocus()
        }
    }

    // « Retour » : ferme d'abord ce qui est ouvert (panneau, liste, saisie, barre), sinon quitte le lecteur
    fun backConsumed(): Boolean = when {
        pauseScreen -> { pauseScreen = false; showInfo(); true }
        tracksVisible -> { tracksVisible = false; true }
        episodesVisible -> { episodesVisible = false; true }
        listVisible -> { listVisible = false; true }
        numberBuffer.isNotEmpty() -> { numberBuffer = ""; true }
        seekPreview != null -> { seekPreview = null; true }
        infoVisible && !ui.isLive && isTv -> { infoVisible = false; true }
        else -> false
    }
    BackHandler(enabled = pauseScreen || tracksVisible || episodesVisible || listVisible || numberBuffer.isNotEmpty() || seekPreview != null || (infoVisible && !ui.isLive && isTv)) { backConsumed() }
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
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                // écran de pause : la première touche le ferme (OK reprend la lecture, les autres montrent la barre)
                if (pauseScreen && e.type == KeyEventType.KeyDown) {
                    val code = e.nativeKeyEvent.keyCode
                    pauseScreen = false
                    if (isSelect(code) || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY) playPause() else showInfo()
                    backDownConsumed = code == KeyEvent.KEYCODE_BACK
                    return@onPreviewKeyEvent true
                }
                if (e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && menu == null) {
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
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_SETTINGS -> { if (!modal) menu = Menu.MAIN; return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { playPause(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { exo?.play(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.pauseAndSave(); showInfo(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_PROG_RED -> { if (ui.isLive) vm.record(0) { toast(context, it) }; return@onKeyEvent true }
                    KeyEvent.KEYCODE_LAST_CHANNEL -> { vm.previous(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> { if (!ui.isLive) vm.playNextEpisode(); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { if (!ui.isLive) seekBy(-seekStep(repeat, back = true)); return@onKeyEvent true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (!ui.isLive) seekBy(seekStep(repeat, back = false)); return@onKeyEvent true }
                }
                // direct : chaîne en erreur, ▲▼ zappent toujours ; le reste va aux boutons
                if (modal && !(ui.isLive && errorShown && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN))) return@onKeyEvent false
                if (ui.isLive && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                    if (numberBuffer.length < 5) numberBuffer += (code - KeyEvent.KEYCODE_0).toString()
                    return@onKeyEvent true
                }
                if (ui.isLive) liveKey(
                    code, repeat, listVisible, zapList, ui,
                    setListIndex = { listIndex = it(listIndex) }, setList = { listVisible = it }, hideInfo = { infoVisible = false },
                    showInfo = ::showInfo, vm = vm, currentListIndex = { listIndex },
                ) else {
                    // VOD : commandes affichées → la navigation au focus prend la main
                    if (infoVisible || !isDpad(code)) return@onKeyEvent false
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-seekStep(repeat, back = true), reveal = false); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(seekStep(repeat, back = false), reveal = false); true }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { showInfo(); true }
                        else -> { if (repeat == 0) playPause(); true }
                    }
                }
            }
            .focusable()
            .pointerInput(ui.isLive) {
                detectTapGestures(
                    onTap = { if (infoVisible || listVisible) { infoVisible = false; listVisible = false } else showInfo() },
                    onDoubleTap = { offset ->
                        // double-tap : même chemin que le D-pad (cumul, bornes, un seul déplacement)
                        if (!ui.isLive) seekBy(if (offset.x < size.width / 2) -10_000L else 30_000L, reveal = false)
                    },
                )
            }
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
                    view.resizeMode = resizeModes[resizeIndex.coerceIn(0, resizeModes.lastIndex)].first
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!inPip) {
            if (!ui.needsPin) StatusOverlay(streamState, ui.error, retryFocus, onRetry = { vm.retry() }, onBack = { nav.popBackStack() })
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

            val controlsShown = infoVisible && !listVisible && !episodesVisible && !tracksVisible && !errorShown && !pauseScreen && !showNextCard
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
                if (ui.isLive) LiveInfoBar(ui) else VodControls(
                    position, duration, seekPreview,
                    buffering = streamState is StreamState.Buffering,
                    playing = isPlaying,
                    scrubberFocus = scrubberFocus, playFocus = playFocus,
                    onBack10 = { seekBy(-10_000L) },
                    onPlayPause = ::playPause,
                    onFwd30 = { seekBy(30_000L) },
                    pills = vodPills(
                        ui, hasEpisodes = episodes.size > 1,
                        onEpisodes = { episodesVisible = true },
                        onNext = { vm.playNextEpisode() },
                        onTracks = { tracksVisible = true },
                        onMore = { menu = Menu.MAIN },
                    ),
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
            AnimatedVisibility(listVisible, Modifier.align(Alignment.CenterStart), enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
                ChannelListOverlay(zapList, listIndex, ui.channel?.id) { i -> zapList.getOrNull(i)?.let { vm.playChannel(it) }; listVisible = false }
            }
            AnimatedVisibility(episodesVisible, Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
                EpisodesPanel(episodes, ui.currentSe, ui.seriesBackdrop ?: ui.image, Modifier) { e -> episodesVisible = false; vm.playEpisodeNow(e) }
            }
            AnimatedVisibility(
                tracksVisible, Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(Motion.enter()) { it } + fadeIn(Motion.enter()), exit = slideOutHorizontally(Motion.exit()) { it } + fadeOut(Motion.exit()),
            ) {
                val audioOpts = remember(tracks) { vm.audioOptions() }
                val textOpts = remember(tracks) { vm.textOptions() }
                TracksPanel(audioOpts, textOpts) { type, o -> vm.selectTrack(type, o) }
            }
            AnimatedVisibility(pauseScreen, enter = fadeIn(tween(Motion.SLOW)), exit = fadeOut(Motion.exit())) {
                PauseScreen(ui, position, duration)
            }
            if (numberBuffer.isNotEmpty()) {
                Text(
                    numberBuffer, Modifier.align(Alignment.TopEnd).padding(32.dp).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(horizontal = 22.dp, vertical = 10.dp),
                    fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            // carte « Épisode suivant » : glisse depuis la droite ; le dernier épisode connu reste affiché pendant la sortie
            val next = ui.nextEpisode
            var lastNext by remember { mutableStateOf(next) }
            if (next != null) lastNext = next
            AnimatedVisibility(
                showNextCard && next != null && !episodesVisible && !tracksVisible, Modifier.align(Alignment.BottomEnd),
                enter = slideInHorizontally(Motion.enter()) { it / 2 } + fadeIn(Motion.enter()), exit = slideOutHorizontally(Motion.exit()) { it / 2 } + fadeOut(Motion.exit()),
            ) {
                lastNext?.let { n ->
                    NextUpCard(
                        n, seconds = if (vm.autoplayNext && !ui.endedWithNext) nextCountdown else null,
                        // l'image de l'épisode suivant d'abord (dans la carte), sinon le fond de la série — jamais l'épisode en cours
                        fallbackImage = ui.seriesBackdrop, playFocus = nextFocus,
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
            ui.title,
            buildList {
                if (!ui.isLive) add(MenuOption("Depuis le début", Icons.Default.Replay) { vm.restartFromBeginning() })
                if (ui.versionLabel != null) add(MenuOption("Version : ${ui.versionLabel}", Icons.Default.Layers) { scope.launch { delay(50); menu = Menu.VERSION } })
                if (ui.isLive) add(MenuOption("Audio et sous-titres", Icons.Default.ClosedCaption) { tracksVisible = true })
                add(MenuOption("Format d'image : ${resizeModes[resizeIndex.coerceIn(0, resizeModes.lastIndex)].second}", Icons.Default.AspectRatio) { scope.launch { delay(50); menu = Menu.ASPECT } })
                if (ui.isLive) {
                    add(MenuOption(if (ui.isFavorite) "Retirer des favoris" else "Ajouter aux favoris", if (ui.isFavorite) Icons.Default.StarBorder else Icons.Default.Star) { vm.toggleFavorite() })
                    if (ui.canCatchup && ui.nowNext?.now != null) add(MenuOption("Revoir depuis le début du programme", Icons.Default.History) { vm.restartCurrentProgram() })
                    add(MenuOption("Enregistrer", Icons.Default.FiberManualRecord) { scope.launch { delay(50); menu = Menu.RECORD } })
                    add(MenuOption("Chaîne précédente", Icons.Default.SwapVert) { vm.previous() })
                    add(MenuOption("Ajouter au multi-écran", Icons.Default.GridView) { vm.addToMultiview()?.let { toast(context, it) }; nav.navigate(MultiviewRoute) })
                } else {
                    if (ui.channel != null) add(MenuOption("Retour au direct", Icons.Default.LiveTv) { vm.backToLive() })
                }
                add(MenuOption("Infos du flux", Icons.Default.Info) { scope.launch { delay(50); menu = Menu.INFO } })
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
        Menu.VERSION -> OptionsDialog(
            "Version",
            vm.movieVersionOptions().map { (id, label) -> MenuOption(label, Icons.Default.Layers, id == vm.currentMovieId()) { vm.switchVersion(id) } },
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
 * Pastilles du lecteur à la demande, toujours dans le même ordre (seules celles qui n'ont pas de sens
 * pour le titre sont absentes) : Épisodes, Suivant, Audio et sous-titres, Plus.
 */
internal fun vodPills(
    ui: PlayerUi, hasEpisodes: Boolean,
    onEpisodes: () -> Unit, onNext: () -> Unit, onTracks: () -> Unit, onMore: () -> Unit,
): List<PlayerAction> = buildList {
    if (hasEpisodes) add(PlayerAction("Épisodes", Icons.Default.VideoLibrary, onClick = onEpisodes))
    if (ui.nextEpisode != null) add(PlayerAction("Suivant", Icons.Default.SkipNext, onClick = onNext))
    add(PlayerAction("Audio et sous-titres", Icons.Default.ClosedCaption, onClick = onTracks))
    add(PlayerAction("Plus", Icons.Default.MoreHoriz, onClick = onMore))
}

/** Touches du direct : zapping, liste des chaînes. */
private fun liveKey(
    code: Int, repeat: Int, listVisible: Boolean, zapList: List<ChannelEntity>, ui: PlayerUi,
    setListIndex: ((Int) -> Int) -> Unit, setList: (Boolean) -> Unit, hideInfo: () -> Unit, showInfo: () -> Unit,
    vm: PlayerViewModel, currentListIndex: () -> Int,
): Boolean {
    val last = (zapList.size - 1).coerceAtLeast(0)
    fun openList() {
        setListIndex { zapList.indexOfFirst { it.id == ui.channel?.id }.coerceAtLeast(0) }
        setList(true); hideInfo()
    }
    return when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> { if (listVisible) setListIndex { (it - 1).coerceAtLeast(0) } else vm.zap(-1); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { if (listVisible) setListIndex { (it + 1).coerceAtMost(last) } else vm.zap(1); true }
        KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(1); true }
        KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); true }
        KeyEvent.KEYCODE_PAGE_UP -> { if (listVisible) setListIndex { (it - 10).coerceAtLeast(0) }; true }
        KeyEvent.KEYCODE_PAGE_DOWN -> { if (listVisible) setListIndex { (it + 10).coerceAtMost(last) }; true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            if (repeat > 0) return true
            if (listVisible) {
                zapList.getOrNull(currentListIndex())?.let { vm.playChannel(it) }
                setList(false)
            } else openList()
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> { if (listVisible) setList(false) else openList(); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { setList(false); showInfo(); true }
        else -> false
    }
}

private fun toast(context: android.content.Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()


@Composable
private fun LiveInfoBar(ui: PlayerUi) {
    val now = rememberNow(15_000)
    val c = ui.channel
    val compact = LocalLayout.current.isCompact
    val side = if (compact) 16.dp else 40.dp
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000), Color(0xF2000000))))
            .padding(start = side, end = side, top = if (compact) 40.dp else 60.dp, bottom = if (compact) 20.dp else 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c != null) {
                if (!compact) Text(c.number.toString(), Modifier.padding(end = 18.dp), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                ChannelLogo(c.logo, c.name, if (compact) Modifier.size(64.dp, 40.dp) else Modifier.size(120.dp, 72.dp))
            }
            Column(Modifier.weight(1f).padding(start = if (compact) 12.dp else 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (ui.isFavorite) Icon(Icons.Default.Star, null, Modifier.padding(start = 8.dp).size(18.dp), tint = SokoulColors.Warning)
                    if (ui.canCatchup) Icon(Icons.Default.History, null, Modifier.padding(start = 8.dp).size(18.dp), tint = SokoulColors.TextSecondary)
                }
                val p = ui.nowNext?.now
                if (p != null) {
                    Text(p.title, style = MaterialTheme.typography.titleLarge, color = SokoulColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(formatClock(p.start), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.padding(horizontal = 10.dp).weight(1f).height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(ui.nowNext.progress(now)).background(SokoulColors.Accent, RoundedCornerShape(2.dp)))
                        }
                        Text(formatClock(p.end), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                    if (!compact) p.description?.let { Text(it, Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                } else {
                    Text(ui.subtitle ?: "Pas d'information de programme", color = SokoulColors.TextMuted)
                }
                ui.nowNext?.next?.let { n ->
                    Text("Ensuite · ${formatClock(n.start)}  ${n.title}", Modifier.padding(top = 6.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            if (!compact) Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 24.dp)) {
                Text(formatClock(now), style = MaterialTheme.typography.headlineSmall)
                if (ui.videoInfo.isNotBlank()) Text(ui.videoInfo, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (LocalLayout.current.isTv) Text("◀ liste  ·  ▲▼ zapper  ·  Menu options", Modifier.padding(top = 6.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@Composable
private fun ChannelListOverlay(channels: List<ChannelEntity>, highlighted: Int, currentId: String?, onPick: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(highlighted) {
        val visible = state.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (highlighted <= first || highlighted >= last) state.scrollToItem((highlighted - 4).coerceAtLeast(0))
    }
    LazyColumn(
        Modifier.fillMaxWidth(0.9f).widthIn(max = 420.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xF20A0A0F), Color(0xCC0A0A0F)))).padding(vertical = 24.dp),
        state = state,
    ) {
        itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
            val hl = i == highlighted
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)
                    .background(if (hl) SokoulColors.Accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .pointerInput(i) { detectTapGestures { onPick(i) } }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.number.toString(), Modifier.width(48.dp), color = if (hl) Color.White else SokoulColors.TextMuted, style = MaterialTheme.typography.titleSmall)
                ChannelLogo(c.logo, c.name, Modifier.size(56.dp, 34.dp))
                Text(
                    c.name, Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (hl) Color.White else SokoulColors.TextPrimary, fontWeight = if (c.id == currentId) FontWeight.Bold else FontWeight.Normal,
                )
                if (c.id == currentId) Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = if (hl) Color.White else SokoulColors.Accent)
            }
        }
    }
}

