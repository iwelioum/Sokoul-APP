package com.sokoul.iptv.player

import android.view.Window
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Adapte la fréquence de l'écran à celle du film (23,976 / 24 / 25 / 50 Hz) : plus de saccades
 * sur les travellings d'un film à 24 images/s affiché en 60 Hz. Le mode d'origine revient en quittant.
 */
object FrameRateMatcher {
    data class Mode(val id: Int, val width: Int, val height: Int, val refresh: Float)

    /**
     * Mode à choisir pour [fps] : même définition que l'écran actuel, fréquence multiple entière
     * de celle du film. Au moins 47 Hz quand c'est possible (25 i/s → 50 Hz : menus fluides), sinon la plus
     * basse (24 i/s → 24 Hz, les TV n'ont pas de 48 Hz). Null : rien à changer.
     */
    fun pick(modes: List<Mode>, current: Mode, fps: Float): Int? {
        if (fps < 10f || fps > 121f) return null
        if (multiple(current.refresh, fps) != null) return null
        return modes.asSequence()
            .filter { it.width == current.width && it.height == current.height }
            .mapNotNull { m -> multiple(m.refresh, fps)?.let { k -> m to k } }
            .minByOrNull { (m, k) -> (if (m.refresh >= 47f) 0 else 100_000) + k * 1000 + abs(m.refresh - fps * k) }
            ?.first?.id
    }

    /** k si [refresh] ≈ k × [fps] (à 0,05 % près : 23,976 ≠ 24, 29,97 ≠ 30), sinon null. */
    private fun multiple(refresh: Float, fps: Float): Int? {
        val k = (refresh / fps).roundToInt()
        if (k < 1 || k > 4) return null
        return k.takeIf { abs(refresh - fps * k) / refresh < 0.0005f }
    }

    private val STANDARD = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 47.952f, 48f, 50f, 59.94f, 60f)

    /**
     * Fréquence d'un fichier qui ne l'indique pas (MKV) : durée moyenne entre images sur ~5 s, ramenée à la
     * fréquence standard la plus proche (à 0,5 % près). Les horodatages arrondis à la milliseconde se compensent
     * sur 120 images : 23,976 et 24 restent distincts. Null : mesure impossible (trous, saut).
     */
    fun estimate(ptsUs: List<Long>): Float? {
        if (ptsUs.size < 2) return null
        if (ptsUs.zipWithNext().any { (a, b) -> b <= a || b - a > 200_000 }) return null
        val fps = (ptsUs.size - 1) * 1_000_000f / (ptsUs.last() - ptsUs.first())
        val best = STANDARD.minBy { abs(it - fps) }
        return best.takeIf { abs(best - fps) / best < 0.005f }
    }

    /** Relève les horodatages des images rendues (fil de lecture) et appelle [onFps] une fois la mesure faite. */
    class Estimator(private val onFps: (Float) -> Unit) : androidx.media3.exoplayer.video.VideoFrameMetadataListener {
        private val pts = ArrayList<Long>(FRAMES)
        @Volatile private var done = false

        override fun onVideoFrameAboutToBeRendered(presentationTimeUs: Long, releaseTimeNs: Long, format: androidx.media3.common.Format, mediaFormat: android.media.MediaFormat?) {
            if (done) return
            if (pts.isNotEmpty() && (presentationTimeUs <= pts.last() || presentationTimeUs - pts.last() > 200_000)) pts.clear()
            pts += presentationTimeUs
            if (pts.size < FRAMES) return
            done = true
            estimate(pts)?.let(onFps)
        }
    }

    private const val FRAMES = 120

    /** Applique et restaure le mode d'affichage d'une fenêtre. */
    class Switcher(private val window: Window) {
        private var original: Int? = null
        private var lastFps = 0f

        fun match(fps: Float) {
            if (fps <= 0f || abs(fps - lastFps) < 0.01f) return
            lastFps = fps
            @Suppress("DEPRECATION")
            val display = window.windowManager.defaultDisplay ?: return
            val all = display.supportedModes.map { Mode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
            val now = display.mode.let { Mode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
            val target = pick(all, now, fps) ?: return
            if (original == null) original = window.attributes.preferredDisplayModeId
            window.attributes = window.attributes.also { it.preferredDisplayModeId = target }
        }

        fun restore() {
            lastFps = 0f
            val o = original ?: return
            original = null
            window.attributes = window.attributes.also { it.preferredDisplayModeId = o }
        }
    }
}
