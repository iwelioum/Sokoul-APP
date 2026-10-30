package com.sokoul.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopSync
import com.sokoul.desktop.data.Playlist
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.iptv.data.PinHasher

enum class Screen { WELCOME, LIVE, GUIDE, SETTINGS }

/** État global de l'app PC : base, synchronisation, lecteur (unique), écran courant et playlist active. */
class AppState(val db: DesktopDb, val sync: DesktopSync, val player: DesktopPlayer?) {
    var screen by mutableStateOf(Screen.LIVE)
    var playlist by mutableStateOf<Playlist?>(null)
        private set
    /** Lecture plein cadre : liste de zapping et chaîne courante (null = pas de lecteur ouvert). */
    var playing by mutableStateOf<Pair<List<Channel>, Int>?>(null)
    var fullscreen by mutableStateOf(false)
    /** Code parental saisi pendant cette session (groupes verrouillés accessibles jusqu'à la fermeture). */
    var unlocked by mutableStateOf(false)
    /** Incrémenté après une synchronisation ou un import : les écrans rechargent. */
    var dataVersion by mutableStateOf(0)

    init { reloadPlaylist() }

    fun reloadPlaylist() {
        val all = db.playlists()
        val active = db.setting(Settings.ACTIVE_PLAYLIST)?.toLongOrNull()
        playlist = all.firstOrNull { it.id == active } ?: all.firstOrNull()
        if (playlist == null) screen = Screen.WELCOME
        else if (screen == Screen.WELCOME) screen = Screen.LIVE
        dataVersion++
    }

    fun selectPlaylist(id: Long) {
        db.putSetting(Settings.ACTIVE_PLAYLIST, id.toString())
        reloadPlaylist()
    }

    fun hasPin(): Boolean = !db.setting(Settings.PIN).isNullOrBlank()

    fun checkPin(pin: String): Boolean {
        val stored = db.setting(Settings.PIN) ?: return true
        return PinHasher.verify(pin, stored).also { if (it) unlocked = true }
    }

    fun play(list: List<Channel>, index: Int) {
        val p = playlist ?: return
        val c = list.getOrNull(index) ?: return
        playing = list to index
        player?.play(com.sokoul.desktop.data.LiveLogic.stream(p, c, sync.userAgent()))
    }

    fun zap(delta: Int) {
        val (list, i) = playing ?: return
        play(list, com.sokoul.desktop.data.LiveLogic.zap(list.size, i, delta))
    }

    /** Fermer le lecteur rend la connexion à l'abonnement. */
    fun closePlayer() {
        player?.stop()
        playing = null
        fullscreen = false
    }
}
