package com.sokoul.iptv.ui.screens.player

import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.VideoQuality
import java.util.Locale
import kotlin.math.abs

/**
 * « Passer l'intro » appris : un saut en avant de 45 s à 3 min dans les 10 premières minutes d'un épisode
 * est retenu pour la série ; dans les épisodes suivants, la pastille apparaît autour du même moment.
 */
object IntroSkip {
    const val MIN_JUMP_MS = 45_000L
    const val MAX_JUMP_MS = 180_000L
    const val ZONE_MS = 600_000L

    data class Window(val startMs: Long, val endMs: Long)

    fun learn(fromMs: Long, toMs: Long): Window? =
        if (fromMs in 0 until ZONE_MS && toMs - fromMs in MIN_JUMP_MS..MAX_JUMP_MS) Window(fromMs, toMs) else null

    /** Pastille visible de 15 s avant le début appris jusqu'à 10 s avant la fin. */
    fun visible(posMs: Long, w: Window): Boolean =
        posMs < ZONE_MS && posMs >= (w.startMs - 15_000).coerceAtLeast(0) && posMs < w.endMs - 10_000
}

/** Décalage des sous-titres par pas de 0,25 s. Sous-titres du flux : retard seulement (Media3 ne permet pas l'avance). */
object SubtitleOffset {
    const val STEP_MS = 250L
    const val MAX_MS = 10_000L

    fun shift(currentMs: Long, deltaMs: Long, delayOnly: Boolean): Long =
        (currentMs + deltaMs).coerceIn(if (delayOnly) 0 else -MAX_MS, MAX_MS)

    /** « +0,25 s », « −1,50 s », « 0 s ». */
    fun label(ms: Long): String = when {
        ms == 0L -> "0 s"
        else -> (if (ms > 0) "+" else "−") + String.format(Locale.FRANCE, "%.2f s", abs(ms) / 1000.0)
    }
}

/**
 * « Ça coupe ? » : version plus légère que [current] parmi [options] (déjà classées) — définition plus basse,
 * ou même définition mais au moins 40 % plus petite. Même langue de préférence ; la SD en dernier recours.
 */
object LighterVersion {
    fun pick(currentId: String, current: PlaybackSource, options: List<Pair<String, PlaybackSource>>): Pair<String, PlaybackSource>? {
        val q = current.quality
        fun lighter(o: PlaybackSource): Boolean {
            val oq = o.quality
            if (q != null && oq != null && oq.ordinal > q.ordinal) return true
            val size = current.sizeBytes
            val oSize = o.sizeBytes
            return oq == q && size != null && oSize != null && oSize < size * 0.6
        }
        val c = options.filter { it.first != currentId && lighter(it.second) }.sortedBy { it.second.quality == VideoQuality.SD }
        return c.firstOrNull { it.second.language == current.language } ?: c.firstOrNull()
    }
}
