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
     * de celle du film, la plus basse possible (24 Hz plutôt que 48). Null : rien à changer.
     */
    fun pick(modes: List<Mode>, current: Mode, fps: Float): Int? {
        if (fps < 10f || fps > 121f) return null
        if (multiple(current.refresh, fps) != null) return null
        return modes.asSequence()
            .filter { it.width == current.width && it.height == current.height }
            .mapNotNull { m -> multiple(m.refresh, fps)?.let { k -> m to k } }
            .minByOrNull { (m, k) -> k * 1000 + abs(m.refresh - fps * k) }
            ?.first?.id
    }

    /** k si [refresh] ≈ k × [fps] (à 0,1 % près : 23,976 ≠ 24), sinon null. */
    private fun multiple(refresh: Float, fps: Float): Int? {
        val k = (refresh / fps).roundToInt()
        if (k < 1 || k > 4) return null
        return k.takeIf { abs(refresh - fps * k) / refresh < 0.001f }
    }

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
