package com.sokoul.tvvolume

import android.accessibilityservice.AccessibilityService
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
 * Service d'accessibilité : lit uniquement les touches Volume +, Volume − et Muet de la télécommande et les envoie à
 * la TV TCL par le Wi-Fi. Si la TV ne répond pas, le service s'efface 30 s : la box retrouve son volume normal.
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


    override fun onKeyEvent(event: KeyEvent): Boolean {
        val ip = prefs.tvIp
        if (ip.isEmpty() || !TclProtocol.isPrivateIpv4(ip)) return false
        val now = SystemClock.uptimeMillis()
        if (now < pausedUntil) return false
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
        main.removeCallbacksAndMessages(null)
        hide.run()
        sender.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val PAUSE_MS = 30_000L
    }
}
