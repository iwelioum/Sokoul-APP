package com.sokoul.desktop.player

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import java.util.Timer
import kotlin.concurrent.schedule

/** Ce que l'on lit : lien du flux et en-têtes exigés par certains fournisseurs ; [live] = direct (pas de barre de temps). */
data class StreamRequest(
    val url: String,
    val userAgent: String?,
    val referrer: String?,
    val title: String,
    val live: Boolean = true,
    val startMs: Long = 0,
)

data class PlayerStatus(
    val title: String = "",
    val playing: Boolean = false,
    val paused: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val audio: List<Pair<Int, String>> = emptyList(),
    val audioId: Int = -1,
    val subtitles: List<Pair<Int, String>> = emptyList(),
    val subtitleId: Int = -1,
    val video: String? = null,
    val timeMs: Long = 0,
    val lengthMs: Long = 0,
    val volume: Int = 100,
    val muted: Boolean = false,
)

/**
 * Image vidéo courante, dessinée par Compose : libVLC écrit chaque image (RV32 = BGRA) directement dans la mémoire
 * d'une Bitmap Skia ([frameTick] avance à chaque image). Rien de natif à l'écran : barre et menus passent par-dessus.
 */
class VideoFrame {
    private val lock = Any()
    var bitmap: Bitmap? = null
        private set
    private var target: ByteBuffer? = null
    val frameTick = MutableStateFlow(0L)
    val size = MutableStateFlow(0 to 0)

    fun allocate(w: Int, h: Int) = synchronized(lock) {
        bitmap?.close()
        val b = Bitmap().apply { allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)) }
        val pm = b.peekPixels() ?: error("Pixels vidéo inaccessibles")
        target = Pointer(pm.addr).getByteBuffer(0, pm.rowBytes.toLong() * h)
        bitmap = b
        size.value = w to h
    }

    fun write(src: ByteBuffer) {
        synchronized(lock) {
            val dst = target ?: return
            val s = src.duplicate().apply { rewind() }
            val d = dst.duplicate().apply { rewind() }
            if (s.remaining() > d.remaining()) s.limit(s.position() + d.remaining())
            d.put(s)
            bitmap?.notifyPixelsChanged()
        }
        frameTick.value++
    }

    /** Dessin sous verrou : l'image n'est jamais lue pendant que libVLC l'écrit. */
    fun <T> read(block: (Bitmap) -> T): T? = synchronized(lock) { bitmap?.let(block) }

    fun clear() = synchronized(lock) { bitmap?.close(); bitmap = null; target = null; size.value = 0 to 0 }
}

/**
 * Lecteur VLC (libVLC fourni avec l'app). **Une seule instance** : changer de flux arrête le précédent avant
 * d'ouvrir le suivant, l'abonnement IPTV n'autorisant qu'une connexion. Reconnexion automatique du direct (3 essais).
 */
class DesktopPlayer private constructor(private val factory: MediaPlayerFactory) {
    val frame = VideoFrame()
    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()
    private val _status = MutableStateFlow(PlayerStatus())
    val status: StateFlow<PlayerStatus> = _status
    private var current: StreamRequest? = null
    private var retries = 0
    private val timer = Timer("sokoul-reconnect", true)

    init {
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(
            object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    // définition décodée limitée à 2560 px de large (copie d'image par image raisonnable)
                    val scale = minOf(1.0, MAX_WIDTH.toDouble() / sourceWidth.coerceAtLeast(1))
                    val w = (sourceWidth * scale).toInt().coerceAtLeast(2) and 1.inv()
                    val h = (sourceHeight * scale).toInt().coerceAtLeast(2) and 1.inv()
                    frame.allocate(w, h)
                    return RV32BufferFormat(w, h)
                }
                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
            },
            RenderCallback { _, nativeBuffers, _ -> nativeBuffers.firstOrNull()?.let(frame::write) },
            true,
        ))
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                retries = 0
                _status.value = _status.value.copy(playing = true, paused = false, buffering = false, error = null)
                // volume et muet choisis avant l'ouverture du flux : appliqués au flux qui démarre
                val (vol, muted) = _status.value.volume to _status.value.muted
                mediaPlayer.submit { mediaPlayer.audio().setVolume(vol); mediaPlayer.audio().setMute(muted) }
                current?.startMs?.takeIf { it > 0 && !current!!.live && _status.value.timeMs < 1000 }?.let { start ->
                    mediaPlayer.submit { mediaPlayer.controls().setTime(start) }
                }
            }
            override fun paused(mediaPlayer: MediaPlayer) { _status.value = _status.value.copy(paused = true) }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _status.value = _status.value.copy(buffering = newCache < 100f)
            }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) { _status.value = _status.value.copy(timeMs = newTime) }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) { _status.value = _status.value.copy(lengthMs = newLength) }
            override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: uk.co.caprica.vlcj.media.TrackType, id: Int) = refreshTracks()
            override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) = refreshTracks()
            override fun error(mediaPlayer: MediaPlayer) = reconnect("Le flux a été interrompu")
            override fun finished(mediaPlayer: MediaPlayer) {
                if (current?.live == true) reconnect("Le flux s'est arrêté")
                else _status.value = _status.value.copy(playing = false, paused = false, timeMs = _status.value.lengthMs)
            }
        })
    }

    fun play(req: StreamRequest) {
        // une seule connexion : le flux précédent est fermé avant d'ouvrir le suivant
        player.controls().stop()
        current = req
        retries = 0
        _status.value = PlayerStatus(title = req.title, buffering = true, volume = _status.value.volume, muted = _status.value.muted)
        open(req)
    }

    private fun open(req: StreamRequest) {
        val opts = buildList {
            add(":network-caching=${if (req.live) 1500 else 3000}")
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
        val resumeAt = _status.value.timeMs
        timer.schedule(RETRY_DELAY_MS * retries) {
            if (current === req) player.submit { open(if (req.live) req else req.copy(startMs = resumeAt)) }
        }
    }

    fun retry() { current?.let { play(it) } }

    fun stop() {
        current = null
        player.controls().stop()
        frame.clear()
        _status.value = PlayerStatus(volume = _status.value.volume, muted = _status.value.muted)
    }

    fun togglePause() = player.controls().pause()

    fun seekBy(deltaMs: Long) {
        if (current?.live != false) return
        val len = _status.value.lengthMs
        val t = (player.status().time() + deltaMs).coerceIn(0, if (len > 0) len - 1000 else Long.MAX_VALUE)
        player.controls().setTime(t)
        _status.value = _status.value.copy(timeMs = t)
    }

    fun seekTo(fraction: Float) {
        if (current?.live != false) return
        val len = _status.value.lengthMs.takeIf { it > 0 } ?: return
        val t = (len * fraction.coerceIn(0f, 1f)).toLong()
        player.controls().setTime(t)
        _status.value = _status.value.copy(timeMs = t)
    }

    fun setAudio(id: Int) { player.audio().setTrack(id); refreshTracks() }
    fun setSubtitles(id: Int) { player.subpictures().setTrack(id); refreshTracks() }

    fun volume(delta: Int) = setVolume(_status.value.volume + delta)

    fun setVolume(v: Int) {
        val vol = v.coerceIn(0, 150)
        player.audio().setVolume(vol)
        if (_status.value.muted) player.audio().setMute(false)
        _status.value = _status.value.copy(volume = vol, muted = false)
    }

    fun toggleMute() {
        val m = !_status.value.muted
        player.audio().setMute(m)
        _status.value = _status.value.copy(muted = m)
    }

    /** Format d'image : null = d'origine, sinon « 16:9 », « 4:3 »… */
    fun setAspect(ratio: String?) = player.video().setAspectRatio(ratio)

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
        player.release()
        factory.release()
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2_000L
        private const val MAX_WIDTH = 2560

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
                DesktopPlayer(MediaPlayerFactory(null as NativeDiscovery?, "--no-video-title-show", "--quiet", "--no-snapshot-preview", "--no-osd"))
            }.getOrNull()
        }
    }
}
