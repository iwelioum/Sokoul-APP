package com.sokoul.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.desktop.player.PlayerStatus
import com.sokoul.desktop.player.VideoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Replay
import com.sokoul.iptv.ui.components.ButtonKind
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.SokoulButton as IptvButton
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.screens.player.DelayedSpinner
import com.sokoul.iptv.ui.screens.player.ErrorCard
import com.sokoul.iptv.ui.screens.player.LiveInfoBar
import com.sokoul.iptv.ui.screens.player.LiveProgram
import com.sokoul.iptv.ui.screens.player.LoadingLogo
import com.sokoul.iptv.ui.screens.player.PlayerAction
import com.sokoul.iptv.ui.screens.player.PlayerButton
import com.sokoul.iptv.ui.screens.player.VodControls
import com.sokoul.iptv.ui.screens.player.VodTopBar

private val hiddenCursor: PointerIcon by lazy {
    PointerIcon(java.awt.Toolkit.getDefaultToolkit().createCustomCursor(java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), java.awt.Point(0, 0), "sans"))
}

/** Image vidéo (libVLC → Bitmap Skia), mise à l'échelle sans déformation sur fond noir. */
@Composable
fun VideoSurface(frame: VideoFrame, modifier: Modifier = Modifier) {
    val tick by frame.frameTick.collectAsState()
    Canvas(modifier.background(SokoulColors.VideoBlack)) {
        @Suppress("UNUSED_EXPRESSION") tick
        frame.read { bmp ->
            val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
            if (w <= 0f || h <= 0f) return@read
            // largeur affichée corrigée des pixels non carrés (SD anamorphosée)
            val shownW = w * frame.pixelAspect
            val s = minOf(size.width / shownW, size.height / h)
            val dw = shownW * s; val dh = h * s
            val img = org.jetbrains.skia.Image.makeFromBitmap(bmp)
            drawIntoCanvas { c ->
                c.nativeCanvas.drawImageRect(
                    img, org.jetbrains.skia.Rect.makeWH(w, h),
                    org.jetbrains.skia.Rect.makeXYWH((size.width - dw) / 2, (size.height - dh) / 2, dw, dh),
                    org.jetbrains.skia.SamplingMode.LINEAR, null, true,
                )
            }
            img.close()
        }
    }
}

/**
 * Lecteur plein cadre avec la surimpression de la tablette : la vidéo est dessinée par Compose, le bandeau du haut
 * (`VodTopBar`) et les commandes (`VodControls`, ou `LiveInfoBar` en direct) passent par-dessus et se masquent après
 * 3 s sans mouvement de souris (le curseur aussi). Clic = pause, double-clic = plein écran, molette = volume.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(app: AppState) {
    val player = app.player
    val now = app.playing ?: return
    if (player == null) {
        Box(Modifier.fillMaxSize().background(SokoulColors.VideoBlack), contentAlignment = Alignment.Center) {
            ErrorCard("Lecteur indisponible", "Le lecteur vidéo (libVLC) n'a pas été trouvé dans le dossier de l'application.", "Réinstallez Sokoul.") {
                IptvButton("Retour", app::closePlayer, icon = Icons.AutoMirrored.Filled.ArrowBack)
            }
        }
        return
    }
    val st by player.status.collectAsState()
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var visible by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf<String?>(null) }
    // essais : SOKOUL_CONTROLS=1 garde la barre affichée (capture sans souris)
    val keep = remember { System.getenv("SOKOUL_CONTROLS") == "1" }
    LaunchedEffect(lastMove, st.paused, menu, st.playing) {
        visible = true
        if (!keep && !st.paused && menu == null && st.playing) { delay(3_000); visible = false }
    }
    LaunchedEffect(now) { while (true) { delay(10_000); app.saveProgress() } }
    val touch = { lastMove = System.currentTimeMillis() }

    Box(
        Modifier.fillMaxSize().background(SokoulColors.VideoBlack)
            .onPointerEvent(PointerEventType.Move) { touch() }
            .onPointerEvent(PointerEventType.Scroll) { e -> e.changes.firstOrNull()?.scrollDelta?.y?.let { player.volume(if (it < 0) 5 else -5); touch() } }
            .pointerHoverIcon(if (visible) PointerIcon.Default else hiddenCursor),
    ) {
        VideoSurface(
            player.frame,
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { app.fullscreen = !app.fullscreen }, onTap = { player.togglePause() })
            },
        )
        Center(app, now, st)
        AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(400)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                val back = PlayerAction("Retour", Icons.AutoMirrored.Filled.ArrowBack, onClick = app::closePlayer)
                val tracks = trackActions(st) { menu = it }
                val screen = PlayerAction(if (app.fullscreen) "Quitter le plein écran" else "Plein écran", if (app.fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, iconOnly = true) { app.fullscreen = !app.fullscreen }
                val mute = PlayerAction(if (st.muted) "Rétablir le son" else "Couper le son", if (st.muted || st.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, iconOnly = true, onClick = player::toggleMute)
                when (now) {
                    is NowPlaying.Live -> {
                        Box(Modifier.align(Alignment.TopStart).padding(16.dp)) { PlayerButton(back, compact = true) }
                        Box(Modifier.align(Alignment.BottomStart)) {
                            LiveBar(app, now, st, listOf(
                                PlayerAction("Chaîne précédente", Icons.Filled.KeyboardArrowUp) { app.zap(-1) },
                                PlayerAction("Chaîne suivante", Icons.Filled.KeyboardArrowDown) { app.zap(1) },
                            ) + tracks + listOf(mute, screen))
                        }
                    }
                    else -> {
                        val t = now as? NowPlaying.Title
                        val title = t?.title ?: (now as NowPlaying.Iptv).movie.name
                        val badge = listOfNotNull(t?.stream?.release?.let { r -> listOfNotNull(r.language?.name, r.source?.label).joinToString(" · ").ifBlank { null } }, st.video)
                            .joinToString(" · ").ifBlank { null }
                        Box(Modifier.align(Alignment.TopStart)) { VodTopBar(title, t?.subtitle, badge, listOf(back)) }
                        Box(Modifier.align(Alignment.BottomStart)) {
                            VodControls(
                                position = st.timeMs, duration = st.lengthMs, playing = st.playing && !st.paused,
                                onBack10 = { player.seekBy(-10_000) }, onPlayPause = player::togglePause, onFwd30 = { player.seekBy(30_000) },
                                pills = tracks + listOfNotNull(
                                    t?.let { PlayerAction("Versions", Icons.Outlined.Layers) { app.sources = it } },
                                    mute, screen,
                                ),
                                onSeekTo = { ms -> if (st.lengthMs > 0) player.seekTo(ms.toFloat() / st.lengthMs) },
                                onInteract = touch,
                            )
                        }
                    }
                }
            }
        }
    }
    menu?.let { m -> TrackDialog(m, st, player) { menu = null } }
}

/** Pastilles « Audio » et « Sous-titres » (seulement s'il y a un choix), avec la piste en cours en texte. */
private fun trackActions(st: PlayerStatus, open: (String) -> Unit): List<PlayerAction> = listOfNotNull(
    if (st.audio.size > 1) PlayerAction("Audio", Icons.Outlined.Audiotrack, text = st.audio.firstOrNull { it.first == st.audioId }?.second?.let(::shortTrack)) { open("audio") } else null,
    if (st.subtitles.size > 1) PlayerAction("Sous-titres", Icons.Outlined.Subtitles, text = st.subtitles.firstOrNull { it.first == st.subtitleId }?.second?.let(::shortTrack) ?: "Désactivés") { open("subs") } else null,
)

/** « Piste 1 - [Français] » → « Français » : le nom lisible d'une piste libVLC. */
private fun shortTrack(label: String): String =
    Regex("""\[([^\]]+)]""").find(label)?.groupValues?.get(1) ?: label.substringAfter(" - ").ifBlank { label }

@Composable
private fun LiveBar(app: AppState, live: NowPlaying.Live, st: PlayerStatus, actions: List<PlayerAction>) {
    val c = live.list[live.index]
    val nn by produceState<LiveLogic.NowNext?>(null, c.id) {
        val p = app.playlist ?: return@produceState
        val t = System.currentTimeMillis()
        value = withContext(Dispatchers.IO) { c.epgKey?.let { LiveLogic.nowNext(app.db.programs(p.id, listOf(it), t - 6 * 3600_000L, t + 6 * 3600_000L), t) } }
    }
    val favorite = remember(c.id, app.dataVersion) { app.playlist?.let { c.id in app.db.favoriteIds(it.id) } ?: false }
    fun prog(p: com.sokoul.desktop.data.Program?) = p?.let { LiveProgram(it.title, it.start, it.end, it.description) }
    LiveInfoBar(c.number, c.logo, c.name, favorite, prog(nn?.now), prog(nn?.next), st.video, actions)
}

/** Au centre : ouverture du titre (fond de l'œuvre), mise en mémoire, ou erreur avec actions. */
@Composable
private fun Center(app: AppState, now: NowPlaying, st: PlayerStatus) {
    val t = now as? NowPlaying.Title
    val error = t?.error ?: st.error
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> ErrorCard("Lecture impossible", error, null) {
                IptvButton("Réessayer", { if (t != null) app.playTitle(t, t.stream.takeIf { st.error != null }) else app.player?.retry() }, icon = Icons.Filled.Replay, kind = ButtonKind.Primary)
                if (t != null) IptvButton("Autre version", { app.sources = t }, icon = Icons.Outlined.Layers)
                IptvButton("Retour", app::closePlayer, icon = Icons.AutoMirrored.Filled.ArrowBack)
            }
            t != null && (t.step != null || !st.playing) -> LoadingLogo(t.title, t.image,t.step ?: "Ouverture du flux…", t.stream?.title)
            st.buffering || (!st.playing && st.error == null) -> DelayedSpinner()
        }
    }
}

/** Choix d'une piste audio ou de sous-titres (`OptionsDialog` de la tablette). */
@Composable
private fun TrackDialog(kind: String, st: PlayerStatus, player: DesktopPlayer, onClose: () -> Unit) {
    val audio = kind == "audio"
    val items = if (audio) st.audio else st.subtitles
    val selected = if (audio) st.audioId else st.subtitleId
    OptionsDialog(
        if (audio) "Audio" else "Sous-titres",
        items.map { (id, label) -> MenuOption(shortTrack(label), selected = id == selected) { if (audio) player.setAudio(id) else player.setSubtitles(id) } },
        onClose,
    )
}

