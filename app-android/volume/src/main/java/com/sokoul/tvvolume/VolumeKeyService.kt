package com.sokoul.tvvolume

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
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

    /** Volume + / − et Muet tels que la box les applique à elle-même (voir [VolumeMirror]). */
    private val volumeWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ip = activeIp() ?: return
            if (intent.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return
            when (intent.action) {
                VOLUME_CHANGED -> {
                    val keys = VolumeMirror.keysFor(intent.getIntExtra(EXTRA_PREV, -1), intent.getIntExtra(EXTRA_VALUE, -1), anchor)
                    if (keys.isEmpty()) return
                    keys.forEach { send(ip, it) }
                    holdAnchor()
                }
                MUTE_CHANGED -> if (intent.getBooleanExtra(EXTRA_MUTED, false)) {
                    send(ip, TvKey.MUTE)
                    runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter().apply { addAction(VOLUME_CHANGED); addAction(MUTE_CHANGED) }
        ContextCompat.registerReceiver(this, volumeWatcher, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (activeIp() != null) holdAnchor()
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
        // touches que le système laisse passer jusqu'ici (Muet, selon les box) ; sinon c'est [volumeWatcher]
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
        runCatching { unregisterReceiver(volumeWatcher) }
        main.removeCallbacksAndMessages(null)
        hide.run()
        sender.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val PAUSE_MS = 30_000L
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"
        const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        const val EXTRA_PREV = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
        const val EXTRA_MUTED = "android.media.EXTRA_STREAM_VOLUME_MUTED"
    }
}
