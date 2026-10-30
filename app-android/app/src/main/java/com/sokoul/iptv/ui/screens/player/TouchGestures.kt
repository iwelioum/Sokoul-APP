package com.sokoul.iptv.ui.screens.player

/** Zone de l'image où commence un glissement vertical : luminosité à gauche, volume à droite, zapping au centre. */
enum class GestureZone { LEFT, CENTER, RIGHT }

fun gestureZone(x: Float, width: Float): GestureZone = when {
    width <= 0f -> GestureZone.CENTER
    x < width / 3f -> GestureZone.LEFT
    x > width * 2f / 3f -> GestureZone.RIGHT
    else -> GestureZone.CENTER
}

/** Niveau après un déplacement vertical : toute la hauteur de l'image = 100 %, vers le haut (dy < 0) augmente. */
fun levelAfterDrag(level: Float, dyPx: Float, heightPx: Float): Float =
    if (heightPx <= 0f) level.coerceIn(0f, 1f) else (level - dyPx / heightPx).coerceIn(0f, 1f)

/** Format demandé par un pincement : écarter (≥ 1,15) remplit l'écran, rapprocher (≤ 0,87) revient à « Adapter ». */
enum class PinchDecision { FILL, FIT }

fun pinchDecision(startDist: Float, dist: Float): PinchDecision? {
    if (startDist <= 0f) return null
    val ratio = dist / startDist
    return when {
        ratio >= 1.15f -> PinchDecision.FILL
        ratio <= 0.87f -> PinchDecision.FIT
        else -> null
    }
}

/** Luminosité minimale appliquée au doigt : l'écran ne doit jamais paraître éteint. */
const val MIN_GESTURE_BRIGHTNESS = 0.02f

/**
 * Luminosité de la fenêtre (pour la séance de lecture seulement, sans permission) et volume média.
 * [restore] rend à l'écran la luminosité réglée dans Android.
 */
class PlayerGestureControls(private val context: android.content.Context) {
    private val window: android.view.Window? get() = context.findActivity()?.window
    private val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager

    /** Dernier niveau posé au doigt : reprendre là où on s'était arrêté (y compris après l'image dans l'image). */
    private var applied: Float? = null

    fun brightness(): Float {
        applied?.let { return it }
        val current = window?.attributes?.screenBrightness ?: -1f
        if (current >= 0f) return current
        // luminosité système (0–255, courbe non linéaire selon les appareils) : simple point de départ approché
        return runCatching {
            android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f).coerceIn(0.1f, 1f)
    }

    /** Applique et renvoie le niveau réellement retenu. */
    fun setBrightness(level: Float): Float {
        val v = level.coerceIn(MIN_GESTURE_BRIGHTNESS, 1f)
        applied = v
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = v } }
        return v
    }

    fun volume(): Float {
        val a = audio ?: return 0f
        val max = a.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return a.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max
    }

    /** Volume média sans le panneau système (l'indicateur du lecteur le remplace) ; renvoie le niveau atteint. */
    fun setVolume(level: Float): Float {
        val a = audio ?: return 0f
        val max = a.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val steps = Math.round(level.coerceIn(0f, 1f) * max)
        runCatching { a.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, steps, 0) }
        return steps.toFloat() / max
    }

    fun restore() {
        window?.let { w ->
            if (w.attributes.screenBrightness >= 0f) {
                w.attributes = w.attributes.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
