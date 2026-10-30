package com.sokoul.desktop.player

import com.sun.jna.NativeLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.io.File
import java.util.Timer
import kotlin.concurrent.schedule

/** Ce que l'on lit : lien du flux et en-têtes exigés par certains fournisseurs. */
data class StreamRequest(val url: String, val userAgent: String?, val referrer: String?, val title: String)

data class PlayerStatus(
    val title: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val audio: List<Pair<Int, String>> = emptyList(),
    val audioId: Int = -1,
    val subtitles: List<Pair<Int, String>> = emptyList(),
    val subtitleId: Int = -1,
    val video: String? = null,
)

/**
 * Lecteur VLC (libVLC fourni avec l'app). **Une seule instance** par fenêtre : changer de chaîne arrête le flux en
 * cours avant d'ouvrir le suivant, l'abonnement n'autorisant qu'une connexion. Reconnexion automatique (3 essais).
 */
class DesktopPlayer private constructor(private val factory: MediaPlayerFactory) {
    val component: EmbeddedMediaPlayerComponent = EmbeddedMediaPlayerComponent(factory, null, null, null, null)
    private val player: MediaPlayer get() = component.mediaPlayer()
    private val _status = MutableStateFlow(PlayerStatus())
    val status: StateFlow<PlayerStatus> = _status
    private var current: StreamRequest? = null
    private var retries = 0
    private val timer = Timer("sokoul-reconnect", true)

    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                retries = 0
                _status.value = _status.value.copy(playing = true, buffering = false, error = null)
            }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _status.value = _status.value.copy(buffering = newCache < 100f)
            }
            override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: uk.co.caprica.vlcj.media.TrackType, id: Int) = refreshTracks()
            override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) = refreshTracks()
            override fun error(mediaPlayer: MediaPlayer) = reconnect("Le flux a été interrompu")
            override fun finished(mediaPlayer: MediaPlayer) = reconnect("Le flux s'est arrêté")
        })
    }

    fun play(req: StreamRequest) {
        // une seule connexion : le flux précédent est fermé avant d'ouvrir le suivant
        player.controls().stop()
        current = req
        retries = 0
        _status.value = PlayerStatus(title = req.title, buffering = true)
        open(req)
    }

    private fun open(req: StreamRequest) {
        val opts = buildList {
            add(":network-caching=1500")
            add(":http-reconnect")
            req.userAgent?.takeIf { it.isNotBlank() }?.let { add(":http-user-agent=$it") }
            req.referrer?.takeIf { it.isNotBlank() }?.let { add(":http-referrer=$it") }
        }
        player.media().play(req.url, *opts.toTypedArray())
    }

    private fun reconnect(reason: String) {
        val req = current ?: return
        if (retries >= MAX_RETRIES) {
            _status.value = _status.value.copy(playing = false, buffering = false, error = "$reason. Vérifiez la connexion ou réessayez plus tard.")
            return
        }
        retries++
        _status.value = _status.value.copy(buffering = true, error = null)
        timer.schedule(RETRY_DELAY_MS * retries) { if (current === req) player.submit { open(req) } }
    }

    fun retry() { current?.let { play(it) } }

    fun stop() {
        current = null
        player.controls().stop()
        _status.value = PlayerStatus()
    }

    fun togglePause() = player.controls().pause()

    fun setAudio(id: Int) { player.audio().setTrack(id); refreshTracks() }
    fun setSubtitles(id: Int) { player.subpictures().setTrack(id); refreshTracks() }
    fun volume(delta: Int) { player.audio().setVolume((player.audio().volume() + delta).coerceIn(0, 150)) }

    /** Format d'image : null = d'origine, sinon « 16:9 », « 4:3 »… ; « fill » = remplir la fenêtre. */
    fun setAspect(ratio: String?) {
        if (ratio == "fill") {
            val c = component
            player.video().setAspectRatio("${c.width}:${c.height}")
        } else player.video().setAspectRatio(ratio)
    }

    private fun refreshTracks() {
        val audio = player.audio().trackDescriptions().filter { it.id() >= 0 }.map { it.id() to it.description() }
        val subs = player.subpictures().trackDescriptions().map { it.id() to if (it.id() < 0) "Désactivés" else it.description() }
        val dim = player.video().videoDimension()
        _status.value = _status.value.copy(
            audio = audio, audioId = player.audio().track(), subtitles = subs, subtitleId = player.subpictures().track(),
            video = dim?.let { "${it.width}×${it.height}" },
        )
    }

    fun release() {
        stop()
        timer.cancel()
        component.release()
        factory.release()
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2_000L

        /** Dossier de libVLC : ressources de l'app installée, ou `desktop/resources/windows/vlc` en développement. */
        fun vlcDir(): File? {
            val candidates = listOfNotNull(
                System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") },
                File("resources/windows/vlc"),
                File("desktop/resources/windows/vlc"),
                File("app-android/desktop/resources/windows/vlc"),
            )
            return candidates.firstOrNull { File(it, "libvlc.dll").isFile }
        }

        /** Crée le lecteur, ou null si libVLC est introuvable (écran d'explication à la place). */
        fun create(): DesktopPlayer? {
            val dir = vlcDir() ?: return null
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), dir.absolutePath)
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), dir.absolutePath)
            return runCatching {
                DesktopPlayer(MediaPlayerFactory(null as NativeDiscovery?, "--no-video-title-show", "--quiet", "--no-snapshot-preview"))
            }.getOrNull()
        }
    }
}
