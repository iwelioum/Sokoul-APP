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
 * Lecteur plein cadre : la vidéo est dessinée par Compose, la barre passe par-dessus et se masque après 3 s sans
 * mouvement de souris (le curseur aussi). Clic = pause, double-clic = plein écran.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(app: AppState) {
    val player = app.player
    val now = app.playing ?: return
    if (player == null) {
        Box(Modifier.fillMaxSize().background(SokoulColors.Background), contentAlignment = Alignment.Center) {
            EmptyState(
                "Lecteur indisponible", "Le lecteur vidéo (libVLC) n'a pas été trouvé dans le dossier de l'application. Réinstallez Sokoul.",
                action = "Retour", onAction = app::closePlayer,
            )
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

    Box(
        Modifier.fillMaxSize().background(SokoulColors.VideoBlack)
            .onPointerEvent(PointerEventType.Move) { lastMove = System.currentTimeMillis() }
            .onPointerEvent(PointerEventType.Scroll) { e -> e.changes.firstOrNull()?.scrollDelta?.y?.let { player.volume(if (it < 0) 5 else -5); lastMove = System.currentTimeMillis() } }
            .pointerHoverIcon(if (visible) PointerIcon.Default else hiddenCursor),
    ) {
        VideoSurface(
            player.frame,
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { app.fullscreen = !app.fullscreen }, onTap = { if (menu != null) menu = null else player.togglePause() })
            },
        )
        Center(app, now, st)
        AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(400)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                TopBar(app, now, st, Modifier.align(Alignment.TopStart))
                BottomBar(app, player, now, st, { menu = if (menu == it) null else it }, Modifier.align(Alignment.BottomStart))
                menu?.let { m -> TrackMenu(m, st, player, { menu = null }, Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 120.dp)) }
            }
        }
    }
}

@Composable
private fun TopBar(app: AppState, now: NowPlaying, st: PlayerStatus, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .padding(horizontal = 32.dp, vertical = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Retour (Échap)", app::closePlayer)
        Column(Modifier.padding(start = 18.dp).weight(1f)) {
            when (now) {
                is NowPlaying.Live -> {
                    val c = now.list[now.index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge("EN DIRECT", SokoulColors.Live, Color.White)
                        Text("  ${c.number}  ${c.name}", style = Type.PlayerTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                is NowPlaying.Title -> {
                    Text(now.title, style = Type.PlayerTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    now.subtitle?.let { Text(it, style = Type.Small) }
                }
                is NowPlaying.Iptv -> Text(now.movie.name, style = Type.PlayerTitle, maxLines = 1)
            }
        }
        listOfNotNull(st.video, (now as? NowPlaying.Title)?.stream?.release?.let { r -> listOfNotNull(r.source?.label, r.language?.name).joinToString(" · ").ifBlank { null } })
            .forEach { Badge(it, SokoulColors.GlassLight, modifier = Modifier.padding(start = 8.dp)) }
    }
}

/** Au centre : chargement, étape de préparation, ou erreur avec actions. */
@Composable
private fun Center(app: AppState, now: NowPlaying, st: PlayerStatus) {
    val t = now as? NowPlaying.Title
    val error = t?.error ?: st.error
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Column(
                Modifier.widthIn(max = 520.dp).background(SokoulColors.Panel, Radius.Panel).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Lecture impossible", style = Type.Section)
                Text(error, Modifier.padding(top = 8.dp), style = Type.Body, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SokoulButton("Réessayer", { if (t != null) app.playTitle(t, t.stream.takeIf { st.error != null }) else app.player?.retry() })
                    if (t != null) SokoulButton("Autre version", { app.sources = t }, style = ButtonStyle.Glass)
                    SokoulButton("Fermer", app::closePlayer, style = ButtonStyle.Ghost)
                }
            }
            t?.step != null || st.buffering || (!st.playing && st.error == null) -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(54.dp), color = SokoulColors.Accent, strokeWidth = 4.dp, trackColor = SokoulColors.GlassLight)
                Text(t?.step ?: if (st.playing) "Mise en mémoire…" else "Ouverture du flux…", Modifier.padding(top = 18.dp), style = Type.BodyStrong)
                t?.stream?.let { Text(it.title, Modifier.padding(top = 6.dp).widthIn(max = 640.dp), style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun BottomBar(app: AppState, player: DesktopPlayer, now: NowPlaying, st: PlayerStatus, onMenu: (String) -> Unit, modifier: Modifier) {
    val live = now is NowPlaying.Live
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 32.dp, end = 32.dp, top = 60.dp, bottom = 24.dp),
    ) {
        if (live) LiveProgramme(app, now as NowPlaying.Live) else Seekbar(st, player::seekTo)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(if (st.paused || !st.playing) Icons.Filled.PlayArrow else Icons.Filled.Pause, "Lecture / pause (Espace)", player::togglePause, filled = true, size = 52.dp)
            HSpace(12.dp)
            if (live) {
                RoundIconButton(Icons.Filled.KeyboardArrowUp, "Chaîne précédente (Page préc.)", { app.zap(-1) })
                HSpace(8.dp)
                RoundIconButton(Icons.Filled.KeyboardArrowDown, "Chaîne suivante (Page suiv.)", { app.zap(1) })
            } else {
                RoundIconButton(Icons.Filled.Replay10, "Reculer de 10 s (←)", { player.seekBy(-10_000) })
                HSpace(8.dp)
                RoundIconButton(Icons.Filled.Forward10, "Avancer de 10 s (→)", { player.seekBy(10_000) })
            }
            HSpace(16.dp)
            RoundIconButton(if (st.muted || st.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Muet (M)", player::toggleMute)
            VolumeBar(if (st.muted) 0 else st.volume) { player.setVolume(it) }
            Spacer(Modifier.weight(1f))
            if (st.audio.size > 1) { RoundIconButton(Icons.Outlined.Audiotrack, "Pistes audio", { onMenu("audio") }); HSpace(8.dp) }
            if (st.subtitles.size > 1) { RoundIconButton(Icons.Outlined.Subtitles, "Sous-titres", { onMenu("subs") }); HSpace(8.dp) }
            (now as? NowPlaying.Title)?.let { t -> RoundIconButton(Icons.Outlined.Layers, "Sources", { app.sources = t }); HSpace(8.dp) }
            RoundIconButton(if (app.fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Plein écran (F)", { app.fullscreen = !app.fullscreen })
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Seekbar(st: PlayerStatus, onSeek: (Float) -> Unit) {
    var width by remember { mutableStateOf(1) }
    var hover by remember { mutableStateOf<Float?>(null) }
    val fraction = if (st.lengthMs > 0) st.timeMs.toFloat() / st.lengthMs else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(timecode(st.timeMs), style = Type.Meta.copy(color = SokoulColors.TextPrimary))
        Box(
            Modifier.weight(1f).padding(horizontal = 16.dp).height(22.dp).onSizeChanged { width = it.width }
                .onPointerEvent(PointerEventType.Move) { e -> hover = (e.changes.first().position.x / width).coerceIn(0f, 1f) }
                .onPointerEvent(PointerEventType.Exit) { hover = null }
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(width) { detectTapGestures { o -> onSeek((o.x / width).coerceIn(0f, 1f)) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            val thick = if (hover != null) 6.dp else 4.dp
            Box(Modifier.fillMaxWidth().height(thick).background(Color.White.copy(alpha = 0.22f), Radius.Tiny))
            hover?.let { Box(Modifier.fillMaxWidth(it).height(thick).background(Color.White.copy(alpha = 0.25f), Radius.Tiny)) }
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(thick).background(SokoulColors.Accent, Radius.Tiny))
            // au survol : pastille sur la position courante, temps visé au-dessus du curseur
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (hover != null) {
                    Box(Modifier.offset(x = maxWidth * fraction.coerceIn(0f, 1f) - 6.dp).size(12.dp).background(Color.White, Radius.Round))
                    if (st.lengthMs > 0) Badge(
                        timecode((hover!! * st.lengthMs).toLong()), SokoulColors.Panel,
                        modifier = Modifier.offset(x = (maxWidth * hover!! - 24.dp).coerceAtLeast(0.dp), y = (-30).dp),
                    )
                }
            }
        }
        Text("-" + timecode((st.lengthMs - st.timeMs).coerceAtLeast(0)), style = Type.Meta)
    }
}

@Composable
private fun VolumeBar(volume: Int, onChange: (Int) -> Unit) {
    var width by remember { mutableStateOf(1) }
    Box(
        Modifier.padding(start = 10.dp).width(110.dp).height(22.dp).onSizeChanged { width = it.width }.pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(width) { detectTapGestures { o -> onChange((o.x / width * 100).toInt()) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.22f), Radius.Tiny))
        Box(Modifier.fillMaxWidth((volume / 100f).coerceIn(0f, 1f)).height(4.dp).background(Color.White, Radius.Tiny))
    }
}

@Composable
private fun LiveProgramme(app: AppState, live: NowPlaying.Live) {
    val c = live.list[live.index]
    val nn by produceState<LiveLogic.NowNext?>(null, c.id) {
        val p = app.playlist ?: return@produceState
        val t = System.currentTimeMillis()
        value = withContext(Dispatchers.IO) { c.epgKey?.let { LiveLogic.nowNext(app.db.programs(p.id, listOf(it), t - 6 * 3600_000L, t + 6 * 3600_000L), t) } }
    }
    val cur = nn?.now
    Column {
        Text(cur?.title ?: "Programme non communiqué", style = Type.ItemTitle.copy(fontSize = Type.ItemTitle.fontSize * 1.2f))
        if (cur != null) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(clock(cur.start), style = Type.Meta)
            Box(Modifier.weight(1f).padding(horizontal = 14.dp).height(4.dp).background(Color.White.copy(alpha = 0.22f), Radius.Tiny)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(nn!!.progress(System.currentTimeMillis())).background(SokoulColors.Live, Radius.Tiny))
            }
            Text(clock(cur.end), style = Type.Meta)
            nn?.next?.let { Text("   Ensuite : ${it.title}", style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun TrackMenu(kind: String, st: PlayerStatus, player: DesktopPlayer, onClose: () -> Unit, modifier: Modifier) {
    val items = if (kind == "audio") st.audio else st.subtitles
    val selected = if (kind == "audio") st.audioId else st.subtitleId
    Column(modifier.width(320.dp).background(SokoulColors.Panel, Radius.Panel).padding(vertical = 10.dp)) {
        Text(if (kind == "audio") "Piste audio" else "Sous-titres", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = Type.Overline)
        Column(Modifier.verticalScroll(rememberScrollState()).height((items.size.coerceAtMost(8) * 44).dp)) {
            items.forEach { (id, label) ->
                val h = rememberHover()
                Row(
                    Modifier.fillMaxWidth().background(if (h.active) SokoulColors.SurfaceHigher else Color.Transparent)
                        .interactive(h) { if (kind == "audio") player.setAudio(id) else player.setSubtitles(id); onClose() }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(if (id == selected) SokoulColors.Accent else Color.Transparent, Radius.Round))
                    Text(label, Modifier.padding(start = 12.dp), style = Type.Small.copy(color = SokoulColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
