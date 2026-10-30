package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.LiveLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

/**
 * Lecture en direct. La vidéo (VLC) est un composant natif : rien ne se dessine par-dessus, la barre est donc
 * sous l'image (masquée en plein écran). Les raccourcis passent par AWT : ils marchent même quand la vidéo a le focus.
 */
@Composable
fun PlayerScreen(app: AppState) {
    val player = app.player ?: return NoVlcScreen(app)
    val (list, index) = app.playing ?: return
    val channel = list.getOrNull(index) ?: return
    val status by player.status.collectAsState()
    val p = app.playlist ?: return

    val nn by produceState<LiveLogic.NowNext?>(null, channel.id) {
        value = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            channel.epgKey?.let { LiveLogic.nowNext(app.db.programs(p.id, listOf(it), now - 6 * 3600_000L, now + 6 * 3600_000L), now) }
        }
    }

    // raccourcis clavier : flèches / Page = zapping, F = plein écran, Échap = retour, Espace = pause, +/- = volume
    DisposableEffect(app) {
        val dispatcher = KeyEventDispatcher { e ->
            if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            when (e.keyCode) {
                KeyEvent.VK_UP, KeyEvent.VK_PAGE_UP -> { app.zap(-1); true }
                KeyEvent.VK_DOWN, KeyEvent.VK_PAGE_DOWN -> { app.zap(1); true }
                KeyEvent.VK_F, KeyEvent.VK_F11 -> { app.fullscreen = !app.fullscreen; true }
                KeyEvent.VK_ESCAPE -> { if (app.fullscreen) app.fullscreen = false else app.closePlayer(); true }
                KeyEvent.VK_SPACE -> { player.togglePause(); true }
                KeyEvent.VK_ADD, KeyEvent.VK_PLUS -> { player.volume(10); true }
                KeyEvent.VK_SUBTRACT, KeyEvent.VK_MINUS -> { player.volume(-10); true }
                else -> false
            }
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        onDispose { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher) }
    }

    Column(Modifier.fillMaxSize().background(SokoulColors.VideoBlack)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SwingPanel(
                background = SokoulColors.VideoBlack,
                factory = {
                    player.component.apply {
                        // double-clic = plein écran ; molette = zapping
                        val mouse = object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) { if (e.clickCount == 2) app.fullscreen = !app.fullscreen }
                            override fun mouseWheelMoved(e: MouseWheelEvent) { app.zap(if (e.wheelRotation > 0) 1 else -1) }
                        }
                        videoSurfaceComponent().addMouseListener(mouse)
                        videoSurfaceComponent().addMouseWheelListener(mouse)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!app.fullscreen) {
            PlayerBar(app, channel.number, channel.name, nn, status)
        }
    }
}

@Composable
private fun PlayerBar(app: AppState, number: Int, name: String, nn: LiveLogic.NowNext?, status: com.sokoul.desktop.player.PlayerStatus) {
    val player = app.player ?: return
    Row(
        Modifier.fillMaxWidth().background(SokoulColors.Surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SokoulButton("Retour", app::closePlayer, primary = false, icon = Icons.AutoMirrored.Filled.ArrowBack)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text("$number  $name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = when {
                status.error != null -> status.error
                status.buffering -> "Connexion…"
                nn?.now != null -> "${nn.now.title}  ·  ${clock(nn.now.start)} – ${clock(nn.now.end)}" + (nn.next?.let { "  ·  ensuite ${it.title}" } ?: "")
                else -> status.video?.let { "En direct · $it" } ?: "En direct"
            }
            Text(line, style = MaterialTheme.typography.bodySmall, color = if (status.error != null) SokoulColors.Live else SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (status.error != null) SokoulButton("Réessayer", player::retry)
        SokoulButton("", { app.zap(-1) }, primary = false, icon = Icons.Default.KeyboardArrowUp)
        SokoulButton("", { app.zap(1) }, primary = false, icon = Icons.Default.KeyboardArrowDown)
        TrackMenu("Audio", Icons.Default.Audiotrack, status.audio, status.audioId, player::setAudio)
        TrackMenu("Sous-titres", Icons.Default.ClosedCaption, status.subtitles, status.subtitleId, player::setSubtitles)
        AspectMenu { player.setAspect(it) }
        SokoulButton("", { app.fullscreen = !app.fullscreen }, primary = false, icon = if (app.fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen)
    }
}

@Composable
private fun TrackMenu(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tracks: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SokoulButton(label, { open = true }, primary = false, icon = icon, enabled = tracks.isNotEmpty())
        DropdownMenu(open, { open = false }) {
            tracks.forEach { (id, name) ->
                DropdownMenuItem(text = { Text((if (id == selected) "✓ " else "    ") + name) }, onClick = { onPick(id); open = false })
            }
        }
    }
}

@Composable
private fun AspectMenu(onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SokoulButton("Format", { open = true }, primary = false, icon = Icons.Default.AspectRatio)
        DropdownMenu(open, { open = false }) {
            listOf(null to "D'origine", "16:9" to "16:9", "4:3" to "4:3", "fill" to "Remplir la fenêtre").forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onPick(v); open = false })
            }
        }
    }
}

@Composable
fun NoVlcScreen(app: AppState) {
    Box(Modifier.fillMaxSize().background(SokoulColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Le lecteur vidéo est introuvable", style = MaterialTheme.typography.headlineSmall)
            Text("Les fichiers de VLC fournis avec Sokoul manquent : réinstallez l'application.", Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary)
            SokoulButton("Retour", app::closePlayer, Modifier.padding(top = 18.dp))
        }
    }
}
