package com.sokoul.tvvolume

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Service d'accessibilité : Volume +, Volume − et Muet de la télécommande envoyés à la TV TCL par le Wi-Fi
 * (le système garde Volume + / − pour lui : on suit le volume de la box, voir [VolumeMirror]).
 * Si la TV ne répond pas, le service s'efface 30 s : la box retrouve son volume normal.
 */
class VolumeKeyService : AccessibilityService() {
    private val policy = KeyPolicy()
    private val remote = TclRemote()
    private val sender = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs(this) }
    @Volatile private var pausedUntil = 0L
    private var bubble: TextView? = null
    private val hide = Runnable { bubble?.let { runCatching { windowManager().removeView(it) } }; bubble = null }


    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val anchor by lazy { VolumeMirror.anchor(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }

    /**
     * Volume + / − et Muet tels que la box les applique à elle-même (voir [VolumeMirror]). Lu toutes les [POLL_MS] :
     * l'avis du système (VOLUME_CHANGED_ACTION) arrive ~0,5 s plus tard, la box aurait le temps de baisser de
     * plusieurs crans en même temps que la TV. Ici elle revient à l'ancrage presque aussitôt, et chaque cran compte.
     */
    private val poller = HandlerThread("volume-tcl").apply { start() }
    private val pollHandler = Handler(poller.looper)
    private var wasActive = false
    private val poll = object : Runnable {
        override fun run() {
            runCatching { check() }
            pollHandler.postDelayed(this, POLL_MS)
        }
    }

    private fun check() {
        val ip = activeIp()
        if (ip == null) { wasActive = false; return }
        if (!wasActive) {
            // (re)démarrage ou fin de pause : la box est remise à l'ancrage sans rien envoyer à la TV
            wasActive = true
            holdAnchor()
            return
        }
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            send(ip, TvKey.MUTE)
        }
        val keys = VolumeMirror.keysFor(anchor, audio.getStreamVolume(AudioManager.STREAM_MUSIC), anchor)
        if (keys.isEmpty()) return
        holdAnchor()
        keys.forEach { send(ip, it) }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        pollHandler.removeCallbacks(poll)
        pollHandler.post(poll)
    }

    /** La box revient à l'ancrage, sans afficher sa propre barre de volume. */
    private fun holdAnchor() = runCatching {
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != anchor) audio.setStreamVolume(AudioManager.STREAM_MUSIC, anchor, 0)
    }

    /** Adresse de la TV si le service doit agir (adresse valide, pas en pause), sinon null. */
    private fun activeIp(): String? {
        val ip = prefs.tvIp
        if (ip.isEmpty() || !TclProtocol.isPrivateIpv4(ip)) return null
        return ip.takeIf { SystemClock.uptimeMillis() >= pausedUntil }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // touches que le système laisse passer jusqu'ici (Muet, selon les box) ; sinon c'est [poll]
        val ip = activeIp() ?: return false
        val now = SystemClock.uptimeMillis()
        return when (val d = policy.decide(event.keyCode, event.action, event.repeatCount, now)) {
            KeyDecision.Pass -> false
            KeyDecision.Consume -> true
            is KeyDecision.Send -> { send(ip, d.key); true }
        }
    }

    private fun send(ip: String, key: TvKey) {
        show("TV : ${key.label}")
        sender.execute {
            if (remote.send(ip, key)) return@execute
            // éteinte ? Appui long sur Muet : on tente le réveil réseau si l'adresse MAC est connue
            if (key == TvKey.POWER && prefs.tvMac.isNotEmpty() && remote.wake(prefs.tvMac)) {
                main.post { show("TV : réveil demandé") }
                return@execute
            }
            pausedUntil = SystemClock.uptimeMillis() + PAUSE_MS
            main.post { show("TV injoignable : volume de la box pendant 30 s") }
        }
    }

    /** Petite bulle en haut à droite, 1,2 s. */
    private fun show(text: String) = main.post {
        runCatching {
            val tv = bubble ?: TextView(this).also { v ->
                v.setTextColor(0xFFFFFFFF.toInt())
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                val pad = (16 * resources.displayMetrics.density).toInt()
                v.setPadding(pad, pad / 2, pad, pad / 2)
                v.background = GradientDrawable().apply { cornerRadius = pad.toFloat(); setColor(0xE61A1730.toInt()) }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply { gravity = Gravity.TOP or Gravity.END; x = pad * 2; y = pad * 2 }
                windowManager().addView(v, lp)
                bubble = v
            }
            tv.text = text
            main.removeCallbacks(hide)
            main.postDelayed(hide, 1_200)
        }
    }

    private fun windowManager() = getSystemService(WINDOW_SERVICE) as WindowManager

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pollHandler.removeCallbacksAndMessages(null)
        poller.quitSafely()
        main.removeCallbacksAndMessages(null)
        hide.run()
        sender.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val PAUSE_MS = 30_000L
        const val POLL_MS = 20L
    }
}
