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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.ui.draw.clipToBounds
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
import com.sokoul.iptv.ui.screens.player.NEXT_COUNTDOWN_S
import com.sokoul.iptv.ui.screens.player.NextUpCard
import androidx.compose.foundation.layout.BoxScope

private val hiddenCursor: PointerIcon by lazy {
    PointerIcon(java.awt.Toolkit.getDefaultToolkit().createCustomCursor(java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), java.awt.Point(0, 0), "sans"))
}

/** Image vidéo (libVLC → Bitmap Skia), mise à l'échelle sans déformation sur fond noir. */
@Composable
fun VideoSurface(frame: VideoFrame, modifier: Modifier = Modifier) {
    val tick by frame.frameTick.collectAsState()
    val aspect by frame.aspect.collectAsState()
    Canvas(modifier.background(SokoulColors.VideoBlack).clipToBounds()) {
        @Suppress("UNUSED_EXPRESSION") tick
        frame.read { bmp ->
            val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
            if (w <= 0f || h <= 0f) return@read
            // largeur affichée corrigée des pixels non carrés (SD anamorphosée)
            val (x, y, dw, dh) = com.sokoul.desktop.player.VideoFit.rect(size.width, size.height, w * frame.pixelAspect / h, aspect)
            val img = org.jetbrains.skia.Image.makeFromBitmap(bmp)
            drawIntoCanvas { c ->
                c.nativeCanvas.drawImageRect(
                    img, org.jetbrains.skia.Rect.makeWH(w, h), org.jetbrains.skia.Rect.makeXYWH(x, y, dw, dh),
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
    // essais : SOKOUL_CONTROLS=1 garde la barre affichée (capture sans souris) ; route play:…:lang ouvre un panneau
    val keep = remember { System.getenv("SOKOUL_CONTROLS") == "1" }
    val trialMenu by com.sokoul.desktop.TrialRoute.playerMenuFlow.collectAsState()
    LaunchedEffect(trialMenu) { trialMenu?.let { menu = it; com.sokoul.desktop.TrialRoute.playerMenu = null } }
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
                // panneau ouvert : un clic sur la vidéo le ferme (comme Retour sur la tablette)
                detectTapGestures(onDoubleTap = { app.fullscreen = !app.fullscreen }, onTap = { if (menu != null) menu = null else player.togglePause() })
            },
        )
        if (app.mini) { MiniOverlay(app, player, st); return@Box }
        Center(app, now, st)
        val panel = menu == "lang" || menu == "subs"
        AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(400)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                val back = PlayerAction("Retour", Icons.AutoMirrored.Filled.ArrowBack, onClick = app::closePlayer)
                val reduce = PlayerAction("Fenêtre réduite", Icons.Filled.PictureInPictureAlt) { app.fullscreen = false; app.mini = true }
                val tracks = trackActions(now, st) { menu = it }
                val more = PlayerAction("Plus", Icons.Filled.MoreHoriz, iconOnly = true) { menu = "more" }
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
                        Box(Modifier.align(Alignment.TopStart)) { VodTopBar(title, t?.subtitle, badge, listOf(back, reduce)) }
                        // panneau latéral ouvert : les commandes s'effacent, la vidéo reste à gauche (tablette)
                        if (!panel) Box(Modifier.align(Alignment.BottomStart)) {
                            VodControls(
                                position = st.timeMs, duration = st.lengthMs, playing = st.playing && !st.paused,
                                onBack10 = { player.seekBy(-10_000) }, onPlayPause = player::togglePause, onFwd30 = { player.seekBy(30_000) },
                                pills = tracks + listOf(more, mute, screen),
                                onSeekTo = { ms -> if (st.lengthMs > 0) player.seekTo(ms.toFloat() / st.lengthMs) },
                                onInteract = touch,
                            )
                        }
                    }
                }
            }
        }
        (now as? NowPlaying.Title)?.takeIf { it.kind == "tv" && it.season != null && it.episode != null }?.let { t -> NextUp(app, t, st) }
        when (menu) {
            "lang" -> (now as? NowPlaying.Title)?.let { t -> LanguageQualityPanel(app, t, st, player, Modifier.align(Alignment.CenterEnd)) { menu = null } }
                ?: TrackDialog("audio", st, player) { menu = null }
            "subs" -> SubtitlePanel(st, player, Modifier.align(Alignment.CenterEnd))
            else -> Unit
        }
    }
    when (menu) {
        "audio" -> TrackDialog("audio", st, player) { menu = null }
        "more" -> MoreMenu(player, st) { menu = null }
        else -> Unit
    }
}

/**
 * Pastilles de la tablette : « Français · 4K · RD » (langue et qualité, panneau latéral) et sous-titres (« Désactivés »
 * ou la piste en cours). Film de l'abonnement : la langue seule (pas d'autres versions à proposer).
 */
private fun trackActions(now: NowPlaying, st: PlayerStatus, open: (String) -> Unit): List<PlayerAction> {
    val audio = st.audio.firstOrNull { it.first == st.audioId }?.second?.let(::shortTrack)
    val t = now as? NowPlaying.Title
    return listOfNotNull(
        if (t != null) PlayerAction(
            "Langue et qualité", Icons.Outlined.Audiotrack,
            text = listOfNotNull(audio, t.stream?.release?.quality?.let(com.sokoul.iptv.ui.components.SourceLabels::quality), "RD").joinToString(" · "),
        ) { open("lang") }
        else if (st.audio.size > 1) PlayerAction("Audio", Icons.Outlined.Audiotrack, text = audio) { open("audio") } else null,
        PlayerAction("Sous-titres", Icons.Outlined.Subtitles, text = st.subtitles.firstOrNull { it.first == st.subtitleId && it.first >= 0 }?.second?.let(::shortTrack) ?: "Désactivés") { open("subs") },
    )
}

/**
 * « Épisode suivant » (tablette) : pendant le générique ou à la fin du fichier, carte en bas à droite ; compte à
 * rebours de 20 s puis lancement si le réglage « Épisode suivant automatique » est actif.
 */
@Composable
private fun BoxScope.NextUp(app: AppState, t: NowPlaying.Title, st: PlayerStatus) {
    val s = t.season ?: return
    val e = t.episode ?: return
    val next by produceState<Triple<Int, Int, com.sokoul.iptv.data.net.TmdbEpisode?>?>(null, t.tmdbId, s, e) {
        val info = app.catalog.details("tv", t.tmdbId)
        val eps = app.catalog.season(t.tmdbId, s)
        val n = com.sokoul.desktop.player.NextEpisodeRule.next(s, e, eps.size.takeIf { it > 0 } ?: e, info?.seasons ?: s) ?: return@produceState
        val ep = if (n.first == s) eps.firstOrNull { it.episode == n.second } else app.catalog.season(t.tmdbId, n.first).firstOrNull { it.episode == n.second }
        value = Triple(n.first, n.second, ep)
    }
    val n = next ?: return
    val ended = !st.playing && !st.buffering && st.lengthMs > 0 && st.timeMs >= st.lengthMs - 1_000
    var dismissed by remember(t.tmdbId, s, e) { mutableStateOf(false) }
    if (dismissed || !com.sokoul.desktop.player.NextEpisodeRule.offer(st.timeMs, st.lengthMs, ended)) return
    val auto = remember { app.prefs.autoNext }
    var seconds by remember(t.tmdbId, s, e) { mutableStateOf(if (auto) NEXT_COUNTDOWN_S else null) }
    fun playNext() = app.playTitle(t.copy(subtitle = "S${n.first} · É${n.second}" + (n.third?.name?.let { " · $it" } ?: ""), image = n.third?.still ?: t.image, season = n.first, episode = n.second, stream = null, step = null, error = null))
    LaunchedEffect(t.tmdbId, s, e, auto) {
        if (!auto) return@LaunchedEffect
        while ((seconds ?: 0) > 0) { delay(1_000); seconds = (seconds ?: 1) - 1 }
        playNext()
    }
    NextUpCard(n.first, n.second, n.third?.name, n.third?.still ?: t.image, seconds, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 150.dp), ::playNext) { dismissed = true }
}

/** Menu « … » : vitesse de lecture et format d'image. */
@Composable
private fun MoreMenu(player: DesktopPlayer, st: PlayerStatus, onClose: () -> Unit) {
    val aspect by player.frame.aspect.collectAsState()
    OptionsDialog(
        "Lecture",
        PLAYBACK_RATES.map { r -> MenuOption("Vitesse : ${rateLabel(r)}", selected = r == st.rate) { player.setRate(r) } } +
            com.sokoul.desktop.player.VideoAspect.entries.map { a -> MenuOption("Image : ${a.label}", selected = a == aspect) { player.frame.aspect.value = a } },
        onClose,
    )
}

/** Fenêtre réduite : la vidéo seule ; au survol, lecture/pause et « Agrandir ». */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MiniOverlay(app: AppState, player: DesktopPlayer, st: PlayerStatus) {
    var hover by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().onPointerEvent(PointerEventType.Enter) { hover = true }.onPointerEvent(PointerEventType.Exit) { hover = false },
    ) {
        if (hover) {
            Box(Modifier.fillMaxSize().background(SokoulColors.Scrim))
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerButton(PlayerAction(if (st.paused) "Lecture" else "Pause", if (st.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, onClick = player::togglePause), compact = true)
                PlayerButton(PlayerAction("Agrandir", Icons.Filled.OpenInFull, primary = true) { app.mini = false }, compact = false)
            }
        }
    }
}

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

