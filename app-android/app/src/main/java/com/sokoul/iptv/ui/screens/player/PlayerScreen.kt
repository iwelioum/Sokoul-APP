package com.sokoul.iptv.ui.screens.player

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.nav.MultiviewRoute
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.theme.LocalIsTv
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatDuration
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.rememberNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class Menu { MAIN, AUDIO, SUBS, ASPECT, RECORD, INFO, VERSION }

private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Adapter",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Remplir (rogner)",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Étirer",
)

@Composable
fun PlayerScreen(nav: NavHostController, route: PlayerRoute) {
    val vm: PlayerViewModel = hiltViewModel()
    val sp by vm.player.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val zapList by vm.zapList.collectAsStateWithLifecycle()
    val inPip by PlaybackUiState.inPip.collectAsStateWithLifecycle()
    val fallbackState = remember { MutableStateFlow<StreamState>(StreamState.Idle) }
    val streamState by (sp?.state ?: fallbackState).collectAsState()
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()

    var infoVisible by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    var listVisible by remember { mutableStateOf(false) }
    var listIndex by remember { mutableIntStateOf(0) }
    var numberBuffer by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var nextCountdown by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }

    fun showInfo() { infoVisible = true; infoTick++ }
    val exo = sp?.player

    // --- intégration système : PiP, MediaSession, écran allumé ---
    DisposableEffect(Unit) {
        PlaybackUiState.pipEligible = true
        // plein écran immersif pendant la lecture
        val window = (context as? android.app.Activity)?.window
        val insets = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            PlaybackUiState.pipEligible = false
            insets?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(exo) {
        val session = exo?.let { runCatching { MediaSession.Builder(context, it).setId("sokoul-${System.nanoTime()}").build() }.getOrNull() }
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        exo?.addListener(l)
        onDispose { exo?.removeListener(l); session?.release() }
    }

    LaunchedEffect(infoVisible, infoTick, menu, listVisible) {
        if (infoVisible && menu == null && !listVisible) { delay(if (ui.isLive) 6_000 else 4_000); infoVisible = false }
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
    LaunchedEffect(ui.resumedFromMs) {
        if (ui.resumedFromMs > 0) Toast.makeText(context, "Reprise à ${formatDuration(ui.resumedFromMs)} — Menu › « Depuis le début » pour recommencer", Toast.LENGTH_LONG).show()
    }
    // position / durée pour les contenus à la demande + épisode suivant
    LaunchedEffect(exo, ui.isLive) {
        while (exo != null && !ui.isLive) {
            position = exo.currentPosition
            duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0
            val next = ui.nextEpisode
            val remaining = duration - position
            nextCountdown = if (next != null && vm.autoplayNext && duration > 60_000 && remaining in 1..20_000) (remaining / 1000).toInt() else null
            delay(500)
        }
    }
    LaunchedEffect(streamState) {
        if (streamState is StreamState.Ended) {
            if (ui.nextEpisode != null && vm.autoplayNext) vm.playNextEpisode() else nav.popBackStack()
        }
    }
    LaunchedEffect(sp) { if (sp != null) runCatching { focus.requestFocus() } }

    BackHandler(enabled = listVisible || numberBuffer.isNotEmpty() || seekPreview != null || (infoVisible && !ui.isLive && isTv)) {
        when {
            listVisible -> listVisible = false
            numberBuffer.isNotEmpty() -> numberBuffer = ""
            seekPreview != null -> seekPreview = null
            else -> infoVisible = false
        }
    }

    fun seekBy(deltaMs: Long) {
        val p = exo ?: return
        val base = seekPreview ?: p.currentPosition
        val target = (base + deltaMs).coerceIn(0, (p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE) - 1_000)
        seekPreview = target
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
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (menu != null) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                val repeat = e.nativeKeyEvent.repeatCount
                if (e.type == KeyEventType.KeyUp) {
                    return@onKeyEvent code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                // touches numériques : saisie d'un numéro de chaîne
                if (ui.isLive && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                    if (numberBuffer.length < 5) numberBuffer += (code - KeyEvent.KEYCODE_0).toString()
                    return@onKeyEvent true
                }
                when (code) {
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_SETTINGS -> { menu = Menu.MAIN; true }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { exo?.let { if (it.isPlaying) it.pause() else it.play() }; showInfo(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { exo?.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { exo?.pause(); showInfo(); true }
                    KeyEvent.KEYCODE_PROG_RED -> { if (ui.isLive) vm.record(0) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }; true }
                    KeyEvent.KEYCODE_LAST_CHANNEL -> { vm.previous(); true }
                    else -> if (ui.isLive) {
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (listVisible) listIndex = (listIndex - 1).coerceAtLeast(0) else { vm.zap(-1) }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (listVisible) listIndex = (listIndex + 1).coerceAtMost((zapList.size - 1).coerceAtLeast(0)) else { vm.zap(1) }
                                true
                            }
                            KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(1); true }
                            KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); true }
                            KeyEvent.KEYCODE_PAGE_UP -> { if (listVisible) listIndex = (listIndex - 10).coerceAtLeast(0); true }
                            KeyEvent.KEYCODE_PAGE_DOWN -> { if (listVisible) listIndex = (listIndex + 10).coerceAtMost((zapList.size - 1).coerceAtLeast(0)); true }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                if (repeat > 0) return@onKeyEvent true
                                if (listVisible) {
                                    zapList.getOrNull(listIndex)?.let { vm.playChannel(it) }
                                    listVisible = false
                                } else {
                                    listIndex = zapList.indexOfFirst { it.id == ui.channel?.id }.coerceAtLeast(0)
                                    listVisible = true
                                    infoVisible = false
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (listVisible) listVisible = false else {
                                    listIndex = zapList.indexOfFirst { it.id == ui.channel?.id }.coerceAtLeast(0)
                                    listVisible = true; infoVisible = false
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> { listVisible = false; showInfo(); true }
                            else -> false
                        }
                    } else {
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                seekBy(-when { repeat > 20 -> 60_000L; repeat > 5 -> 30_000L; else -> 10_000L }); true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                seekBy(when { repeat > 20 -> 120_000L; repeat > 5 -> 60_000L; else -> 30_000L }); true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                if (repeat == 0) {
                                    if (nextCountdown != null) vm.playNextEpisode()
                                    else if (!infoVisible) showInfo() else exo?.let { if (it.isPlaying) it.pause() else it.play() }
                                    infoTick++
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { showInfo(); true }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> { vm.playNextEpisode(); true }
                            else -> false
                        }
                    }
                }
            }
            .focusable()
            .pointerInput(ui.isLive) {
                detectTapGestures(
                    onTap = { if (infoVisible || listVisible) { infoVisible = false; listVisible = false } else showInfo() },
                    onDoubleTap = { offset ->
                        if (!ui.isLive) seekBy(if (offset.x < size.width / 2) -10_000L else 30_000L)
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
                    view.resizeMode = resizeModes[resizeIndex].first
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!inPip) {
            if (!ui.needsPin) StatusOverlay(streamState, ui.error, onRetry = { vm.retry() }, onBack = { nav.popBackStack() })
            if (ui.needsPin) {
                com.sokoul.iptv.ui.components.PinDialog(
                    "Contenu verrouillé — code parental",
                    onResult = vm::submitPin,
                    onDismiss = { nav.popBackStack() },
                    error = ui.pinError,
                )
            }

            AnimatedVisibility(infoVisible && !listVisible, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                if (ui.isLive) LiveInfoBar(ui) else VodControls(
                    ui, isPlaying, seekPreview ?: position, duration,
                    onPlayPause = { exo?.let { if (it.isPlaying) it.pause() else it.play() }; showInfo() },
                    onSeek = { exo?.seekTo(it); showInfo() },
                    onBack10 = { seekBy(-10_000) },
                    onFwd30 = { seekBy(30_000) },
                    onNext = { vm.playNextEpisode() },
                )
            }
            AnimatedVisibility(infoVisible && !listVisible, Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
                TopActions(
                    onBack = { nav.popBackStack() },
                    onMenu = { menu = Menu.MAIN },
                    onList = if (ui.isLive) ({ listIndex = zapList.indexOfFirst { it.id == ui.channel?.id }.coerceAtLeast(0); listVisible = true }) else null,
                    onPip = if (!isTv) ({ (context as? MainActivity)?.enterPipIfPossible() }) else null,
                )
            }
            AnimatedVisibility(listVisible, Modifier.align(Alignment.CenterStart), enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
                ChannelListOverlay(zapList, listIndex, ui.channel?.id) { i -> zapList.getOrNull(i)?.let { vm.playChannel(it) }; listVisible = false }
            }
            if (numberBuffer.isNotEmpty()) {
                Text(
                    numberBuffer, Modifier.align(Alignment.TopEnd).padding(32.dp).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(horizontal = 22.dp, vertical = 10.dp),
                    fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            nextCountdown?.let { s ->
                NextEpisodeCard(ui, s, Modifier.align(Alignment.BottomEnd).padding(32.dp)) { vm.playNextEpisode() }
            }
        }
    }

    // --- menus ---
    when (menu) {
        Menu.MAIN -> OptionsDialog(
            ui.title,
            buildList {
                if (ui.versionLabel != null) add(MenuOption("Version : ${ui.versionLabel}", Icons.Default.Info) { scope.launch { delay(50); menu = Menu.VERSION } })
                add(MenuOption("Audio", Icons.Default.Audiotrack) { scope.launch { delay(50); menu = Menu.AUDIO } })
                add(MenuOption("Sous-titres", Icons.Default.ClosedCaption) { scope.launch { delay(50); menu = Menu.SUBS } })
                add(MenuOption("Format d'image : ${resizeModes[resizeIndex].second}", Icons.Default.AspectRatio) { scope.launch { delay(50); menu = Menu.ASPECT } })
                if (ui.isLive) {
                    add(MenuOption(if (ui.isFavorite) "Retirer des favoris" else "Ajouter aux favoris", if (ui.isFavorite) Icons.Default.StarBorder else Icons.Default.Star) { vm.toggleFavorite() })
                    if (ui.canCatchup && ui.nowNext?.now != null) add(MenuOption("Revoir depuis le début du programme", Icons.Default.History) { vm.restartCurrentProgram() })
                    add(MenuOption("Enregistrer", Icons.Default.FiberManualRecord) { scope.launch { delay(50); menu = Menu.RECORD } })
                    add(MenuOption("Chaîne précédente", Icons.Default.SwapVert) { vm.previous() })
                    add(MenuOption("Ajouter au multi-écran", Icons.Default.GridView) { vm.addToMultiview()?.let { toast(context, it) }; nav.navigate(MultiviewRoute) })
                } else {
                    add(MenuOption("Depuis le début", Icons.Default.Replay) { vm.restartFromBeginning() })
                    if (ui.nextEpisode != null) add(MenuOption("Épisode suivant", Icons.Default.SkipNext) { vm.playNextEpisode() })
                    if (ui.channel != null) add(MenuOption("Retour au direct", Icons.Default.LiveTv) { vm.backToLive() })
                }
                add(MenuOption("Infos du flux", Icons.Default.Info) { scope.launch { delay(50); menu = Menu.INFO } })
            },
            onDismiss = { if (menu == Menu.MAIN) menu = null },
        )
        Menu.AUDIO -> {
            val opts = vm.audioOptions()
            OptionsDialog(
                "Piste audio",
                opts.ifEmpty { listOf(TrackOption("Aucune piste alternative", null, -1, true)) }.map { o ->
                    MenuOption(o.label, Icons.Default.Audiotrack, o.selected) { if (o.group != null) vm.selectTrack(C.TRACK_TYPE_AUDIO, o) }
                },
                onDismiss = { menu = null },
            )
        }
        Menu.SUBS -> OptionsDialog(
            "Sous-titres",
            vm.textOptions().map { o -> MenuOption(o.label, Icons.Default.ClosedCaption, o.selected) { vm.selectTrack(C.TRACK_TYPE_TEXT, o) } },
            onDismiss = { menu = null },
        )
        Menu.ASPECT -> OptionsDialog(
            "Format d'image",
            resizeModes.mapIndexed { i, (_, label) -> MenuOption(label, Icons.Default.AspectRatio, i == resizeIndex) { resizeIndex = i } },
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
            vm.movieVersionOptions().map { (id, label) -> MenuOption(label, Icons.Default.Info, id == vm.currentMovieId()) { vm.switchVersion(id) } },
            onDismiss = { menu = null },
        )
        null -> Unit
    }
    LaunchedEffect(ui.notice) { ui.notice?.let { toast(context, it) } }
    LaunchedEffect(menu) { if (menu == null) runCatching { focus.requestFocus() } }
}

private fun toast(context: android.content.Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

@Composable
private fun StatusOverlay(state: StreamState, error: String?, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null || state is StreamState.Failed -> Column(
                Modifier.widthIn(max = 560.dp).background(Color(0xE6000000), RoundedCornerShape(16.dp)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Lecture impossible", style = MaterialTheme.typography.headlineSmall)
                Text(error ?: (state as StreamState.Failed).message, Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary, textAlign = TextAlign.Center)
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton("Réessayer", onRetry, icon = Icons.Default.Replay, primary = true)
                    AppButton("Retour", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack)
                }
            }
            state is StreamState.Loading -> CircularProgressIndicator(color = SokoulColors.Accent)
            state is StreamState.Reconnecting -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SokoulColors.Warning)
                Text(
                    "Reconnexion… (${state.attempt})", Modifier.padding(top = 12.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun LiveInfoBar(ui: PlayerUi) {
    val now = rememberNow(15_000)
    val c = ui.channel
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000), Color(0xF2000000))))
            .padding(start = 40.dp, end = 40.dp, top = 60.dp, bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c != null) {
                Text(c.number.toString(), Modifier.padding(end = 18.dp), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                ChannelLogo(c.logo, c.name, Modifier.size(120.dp, 72.dp))
            }
            Column(Modifier.weight(1f).padding(start = 22.dp)) {
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
                    p.description?.let { Text(it, Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                } else {
                    Text(ui.subtitle ?: "Pas d'information de programme", color = SokoulColors.TextMuted)
                }
                ui.nowNext?.next?.let { n ->
                    Text("Ensuite · ${formatClock(n.start)}  ${n.title}", Modifier.padding(top = 6.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 24.dp)) {
                Text(formatClock(now), style = MaterialTheme.typography.headlineSmall)
                if (ui.videoInfo.isNotBlank()) Text(ui.videoInfo, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text("◀ liste  ·  ▲▼ zapper  ·  Menu options", Modifier.padding(top = 6.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VodControls(
    ui: PlayerUi, playing: Boolean, position: Long, duration: Long,
    onPlayPause: () -> Unit, onSeek: (Long) -> Unit, onBack10: () -> Unit, onFwd30: () -> Unit, onNext: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(start = 40.dp, end = 40.dp, top = 80.dp, bottom = 28.dp)
    ) {
        Text(ui.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ui.subtitle?.let { Text(it, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Slider(
            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { if (duration > 0) onSeek((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusable(false),
            colors = SliderDefaults.colors(thumbColor = SokoulColors.Accent, activeTrackColor = SokoulColors.Accent, inactiveTrackColor = Color(0x44FFFFFF)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${formatDuration(position)} / ${formatDuration(duration)}", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            IconTap(Icons.Default.Replay10, onBack10)
            IconTap(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, onPlayPause, big = true)
            IconTap(Icons.Default.Forward30, onFwd30)
            if (ui.nextEpisode != null) IconTap(Icons.Default.SkipNext, onNext)
            Spacer(Modifier.weight(1f))
            Text("−${formatDuration((duration - position).coerceAtLeast(0))}", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun IconTap(icon: androidx.compose.ui.graphics.vector.ImageVector, onTap: () -> Unit, big: Boolean = false) {
    Box(
        Modifier.padding(horizontal = 10.dp).size(if (big) 60.dp else 46.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50))
            .pointerInput(onTap) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(if (big) 34.dp else 26.dp), tint = Color.White) }
}

@Composable
private fun TopActions(onBack: () -> Unit, onMenu: () -> Unit, onList: (() -> Unit)?, onPip: (() -> Unit)?) {
    Row(Modifier.padding(20.dp)) {
        IconTap(Icons.AutoMirrored.Filled.ArrowBack, onBack)
        if (onList != null) IconTap(Icons.Default.List, onList)
        if (onPip != null) IconTap(Icons.Default.PictureInPicture, onPip)
        IconTap(Icons.Default.MoreVert, onMenu)
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
        Modifier.width(420.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xF20A0A0F), Color(0xCC0A0A0F)))).padding(vertical = 24.dp),
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

@Composable
private fun NextEpisodeCard(ui: PlayerUi, seconds: Int, modifier: Modifier, onPlay: () -> Unit) {
    val next = ui.nextEpisode ?: return
    Column(
        modifier.width(320.dp).background(Color(0xE6111118), RoundedCornerShape(14.dp)).padding(18.dp)
            .pointerInput(next.id) { detectTapGestures { onPlay() } }
    ) {
        Text("Épisode suivant dans $seconds s", color = SokoulColors.Accent, style = MaterialTheme.typography.labelLarge)
        Text("S%02dE%02d · %s".format(next.season, next.episode, next.title), Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("OK / ⏭ pour lancer maintenant", Modifier.padding(top = 6.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
