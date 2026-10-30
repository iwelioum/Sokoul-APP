package com.sokoul.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopSync
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.desktop.ui.GuideScreen
import com.sokoul.desktop.ui.HomeScreen
import com.sokoul.desktop.ui.CatalogScreen
import com.sokoul.desktop.ui.LiveScreen
import com.sokoul.desktop.ui.NavRail
import com.sokoul.desktop.ui.PlayerScreen
import com.sokoul.desktop.ui.MoviePlayerScreen
import com.sokoul.desktop.ui.SettingsScreen
import com.sokoul.desktop.ui.SokoulColors
import com.sokoul.desktop.ui.SokoulTheme
import com.sokoul.desktop.ui.WelcomeScreen

/** Erreur non rattrapée : écrite dans `%APPDATA%\Sokoul\crash.log` (l'app installée n'a pas de console). */
private fun logCrash(t: Throwable) = runCatching {
    java.io.File(com.sokoul.desktop.data.AppDirs.data, "crash.log").appendText(
        "${java.time.LocalDateTime.now()} ${com.sokoul.iptv.data.net.Redact.text(t.stackTraceToString())}\n",
    )
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, t -> logCrash(t) }
    try { start() } catch (t: Throwable) { logCrash(t); throw t }
}

private fun start() {
    val db = DesktopDb.open()
    val sync = DesktopSync(db)
    val player = DesktopPlayer.create()
    val app = AppState(db, sync, player)
    application {
        val saved = remember { db.setting(Settings.WINDOW)?.split(',')?.mapNotNull { it.toIntOrNull() } }
        val state = rememberWindowState(
            size = if (saved?.size == 4) DpSize(saved[2].dp, saved[3].dp) else DpSize(1440.dp, 900.dp),
            position = if (saved?.size == 4) WindowPosition(saved[0].dp, saved[1].dp) else WindowPosition.PlatformDefault,
        )
        fun quit() {
            if (state.placement == WindowPlacement.Floating) {
                db.putSetting(Settings.WINDOW, listOf(state.position.x.value, state.position.y.value, state.size.width.value, state.size.height.value).joinToString(",") { it.toInt().toString() })
            }
            db.putSetting(Settings.LAST_SCREEN, app.screen.name)
            player?.release()
            db.close()
            exitApplication()
        }
        Window(
            onCloseRequest = ::quit, state = state, title = "Sokoul",
            icon = androidx.compose.ui.res.painterResource("sokoul.png"),
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.F11) { app.fullscreen = !app.fullscreen; true } else false
            },
        ) {
            LaunchedEffect(app.fullscreen) { state.placement = if (app.fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating }
            LaunchedEffect(Unit) {
                db.setting(Settings.LAST_SCREEN)?.let { s -> runCatching { Screen.valueOf(s) }.getOrNull() }
                    ?.takeIf { it != Screen.WELCOME && app.playlist != null }?.let { app.screen = it }
                // chaînes et guide plus vieux que 12 h : mis à jour en arrière-plan, la liste s'affiche avec l'existant
                app.playlist?.let { p -> if (sync.isStale(p)) { sync.sync(p); app.reloadPlaylist() } }
            }
            SokoulTheme {
                Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
                    when {
                        app.playing != null -> PlayerScreen(app)
                        app.playingMovie != null -> MoviePlayerScreen(app)
                        app.screen == Screen.WELCOME -> WelcomeScreen(app)
                        else -> Row(Modifier.fillMaxSize()) {
                            NavRail(app.screen) { app.screen = it }
                            Box(Modifier.weight(1f).fillMaxSize()) {
                                when (app.screen) {
                                    Screen.HOME -> HomeScreen(app)
                                    Screen.MOVIES -> CatalogScreen(app, "Films")
                                    Screen.SERIES -> CatalogScreen(app, "Séries")
                                    Screen.LIVE -> LiveScreen(app)
                                    Screen.GUIDE -> GuideScreen(app)
                                    Screen.SEARCH -> CatalogScreen(app, "Recherche")
                                    Screen.LIBRARY -> CatalogScreen(app, "Bibliothèque")
                                    Screen.SETTINGS -> SettingsScreen(app)
                                    Screen.WELCOME -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
