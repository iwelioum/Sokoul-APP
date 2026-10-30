package com.sokoul.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
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
import com.sokoul.desktop.ui.BrowseScreen
import com.sokoul.desktop.ui.DetailScreen
import com.sokoul.desktop.ui.GuideScreen
import com.sokoul.desktop.ui.HomeScreen
import com.sokoul.desktop.ui.LiveScreen
import com.sokoul.desktop.ui.PlayerScreen
import com.sokoul.desktop.ui.SearchScreen
import com.sokoul.desktop.ui.SettingsScreen
import com.sokoul.desktop.ui.SokoulColors
import com.sokoul.desktop.ui.SokoulTheme
import com.sokoul.desktop.ui.SourcesDialog
import com.sokoul.desktop.ui.TopNav

/** Erreur non rattrapée : écrite dans `%APPDATA%\Sokoul\crash.log` (l'app installée n'a pas de console). */
private fun logCrash(t: Throwable) = runCatching {
    java.io.File(com.sokoul.desktop.data.AppDirs.data, "crash.log").appendText(
        "${java.time.LocalDateTime.now()} ${com.sokoul.iptv.data.net.Redact.text(t.stackTraceToString())}\n",
    )
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, t -> logCrash(t) }
    com.sokoul.iptv.data.net.CoreLog.warn = { msg, e -> System.err.println("$msg : ${e?.javaClass?.simpleName}") }
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
            size = if (saved?.size == 4) DpSize(saved[2].dp, saved[3].dp) else DpSize(1600.dp, 940.dp),
            position = if (saved?.size == 4) WindowPosition(saved[0].dp, saved[1].dp) else WindowPosition.PlatformDefault,
        )
        fun quit() {
            if (state.placement == WindowPlacement.Floating) {
                db.putSetting(Settings.WINDOW, listOf(state.position.x.value, state.position.y.value, state.size.width.value, state.size.height.value).joinToString(",") { it.toInt().toString() })
            }
            db.putSetting(Settings.LAST_SCREEN, app.screen.name)
            app.closePlayer()
            player?.release()
            db.close()
            exitApplication()
        }
        // essais (skill sokoul-desktop-check) : écran imposé, fenêtre qui ne prend pas le focus de l'utilisateur
        val trial = System.getenv("SOKOUL_ROUTE")?.takeIf { it.isNotBlank() }
        Window(
            onCloseRequest = ::quit, state = state, title = "Sokoul",
            icon = androidx.compose.ui.res.painterResource("sokoul.png"),
            onPreviewKeyEvent = { e -> handleKey(app, e) },
            focusable = trial == null,
        ) {
            LaunchedEffect(app.fullscreen) { state.placement = if (app.fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating }
            LaunchedEffect(Unit) {
                if (trial != null) { TrialRoute.apply(app, trial); return@LaunchedEffect }
                db.setting(Settings.LAST_SCREEN)?.let { s -> runCatching { Screen.valueOf(s) }.getOrNull() }
                    ?.takeIf { it != Screen.SEARCH }?.let { app.go(it) }
                // chaînes et guide plus vieux que 12 h : mis à jour en arrière-plan, la liste s'affiche avec l'existant
                app.playlist?.let { p -> if (sync.isStale(p)) { sync.sync(p); app.reloadPlaylist() } }
            }
            SokoulTheme { AppContent(app) }
        }
    }
}

/** Contenu de la fenêtre : écran courant (ou écran poussé), barre du haut, lecteur et fenêtre Sources par-dessus. */
@Composable
fun AppContent(app: AppState) {
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        val route = app.route
        when {
            route is Route.Detail -> DetailScreen(app, route.kind, route.id)
            else -> when (app.screen) {
                Screen.HOME -> HomeScreen(app)
                Screen.MOVIES -> BrowseScreen(app, "movie")
                Screen.SERIES -> BrowseScreen(app, "tv")
                Screen.LIVE -> LiveScreen(app)
                Screen.GUIDE -> GuideScreen(app)
                Screen.SEARCH -> SearchScreen(app)
                Screen.SETTINGS -> SettingsScreen(app)
            }
        }
        if (app.playing == null) {
            TopNav(app.screen, app.navSolid, onSelect = app::go, onBack = if (app.stack.isNotEmpty()) ({ app.back() }) else null)
        } else PlayerScreen(app)
        app.sources?.let { SourcesDialog(app, it) }
    }
}

/**
 * Raccourcis de toute la fenêtre. Lecteur : Espace, ← →, ↑ ↓, M, F, Page préc./suiv. ; partout : Échap (retour),
 * F11 (plein écran), / (recherche, hors saisie de texte).
 */
private fun handleKey(app: AppState, e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val p = app.player
    if (e.key == Key.F11) { app.fullscreen = !app.fullscreen; return true }
    if (e.key == Key.Escape) return app.back()
    if (app.playing != null && app.sources == null && p != null) {
        when (e.key) {
            Key.Spacebar -> p.togglePause()
            Key.DirectionLeft -> p.seekBy(-10_000)
            Key.DirectionRight -> p.seekBy(10_000)
            Key.DirectionUp -> p.volume(5)
            Key.DirectionDown -> p.volume(-5)
            Key.M -> p.toggleMute()
            Key.F -> app.fullscreen = !app.fullscreen
            Key.PageUp -> app.zap(-1)
            Key.PageDown -> app.zap(1)
            else -> return false
        }
        return true
    }
    // pas sur les écrans à champ de saisie (Direct, Réglages : « http:// »)
    // clavier AZERTY : « / » = Maj + « : », reconnu par son caractère
    if ((e.key == Key.Slash || e.key == Key.NumPadDivide || (e.nativeKeyEvent as? java.awt.event.KeyEvent)?.keyChar == '/') && !e.isCtrlPressed && app.playing == null && app.screen in setOf(Screen.HOME, Screen.MOVIES, Screen.SERIES, Screen.GUIDE)) {
        app.go(Screen.SEARCH); return true
    }
    return false
}
