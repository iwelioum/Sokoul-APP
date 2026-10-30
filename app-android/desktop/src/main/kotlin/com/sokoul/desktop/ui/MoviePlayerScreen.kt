package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import com.sokoul.desktop.AppState
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

@Composable
fun MoviePlayerScreen(app: AppState) {
    val player = app.player
    val movie = app.playingMovie
    if (player == null || movie == null) {
        NoVlcScreen(app)
        return
    }
    val status by player.status.collectAsState()
    DisposableEffect(app) {
        val dispatcher = KeyEventDispatcher { event ->
            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            when (event.keyCode) {
                KeyEvent.VK_ESCAPE -> { if (app.fullscreen) app.fullscreen = false else app.closePlayer(); true }
                KeyEvent.VK_SPACE -> { player.togglePause(); true }
                KeyEvent.VK_F, KeyEvent.VK_F11 -> { app.fullscreen = !app.fullscreen; true }
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
                factory = { player.component },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!app.fullscreen) {
            Row(
                Modifier.fillMaxWidth().background(SokoulColors.Surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SokoulButton("Retour", app::closePlayer, primary = false, icon = Icons.AutoMirrored.Filled.ArrowBack)
                Column(Modifier.weight(1f)) {
                    Text(movie.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        status.error ?: if (status.buffering) "Connexion…" else "Lecture",
                        color = if (status.error != null) SokoulColors.Live else SokoulColors.TextSecondary,
                    )
                }
                if (status.error != null) SokoulButton("Réessayer", player::retry)
                SokoulButton("", { app.fullscreen = !app.fullscreen }, primary = false, icon = if (app.fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen)
            }
        }
    }
}
