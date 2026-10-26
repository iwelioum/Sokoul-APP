package com.sokoul.iptv.ui.screens.guide

import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import java.util.Calendar

/** Règles de temps du guide (fenêtre visible, sauts, jours), sans Compose : testées en JVM. */
internal object GuideTime {
    const val HALF_HOUR = 30 * 60_000L
    /** Saut des touches ⏪ / ⏩ : 2 h. */
    const val JUMP = 4 * HALF_HOUR

    fun align(t: Long) = t - Math.floorMod(t, HALF_HOUR)

    /** Fenêtre par défaut : la demi-heure en cours, précédée d'une demi-heure pour voir ce qui vient de commencer. */
    fun defaultWindow(now: Long) = align(now) - HALF_HOUR

    fun dayStart(t: Long): Long = Calendar.getInstance().run {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /** Débuts des [count] jours proposés, à partir d'aujourd'hui (calendrier : sûr aux changements d'heure). */
    fun days(now: Long, count: Int = 7): List<Long> {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart(now) }
        return List(count) { i -> if (i == 0) c.timeInMillis else c.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis }
    }

    /**
     * Saut vers un jour : aujourd'hui → maintenant ; un autre jour → même heure que le programme focalisé
     * (on compare « ce soir » à « demain soir »). Renvoie (début de fenêtre, instant focalisé).
     */
    fun jumpTo(day: Long, now: Long, focus: Long): Pair<Long, Long> {
        if (day == dayStart(now)) return defaultWindow(now) to now
        val c = Calendar.getInstance().apply { timeInMillis = focus }
        val target = Calendar.getInstance().apply {
            timeInMillis = day
            set(Calendar.HOUR_OF_DAY, c.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, c.get(Calendar.MINUTE))
        }.timeInMillis
        return (align(target) - HALF_HOUR) to target
    }

    /** ⏪ / ⏩ : la fenêtre glisse de [by] et le focus se pose sur sa deuxième demi-heure. */
    fun shift(window: Long, by: Long): Pair<Long, Long> = (window + by) to (window + by + HALF_HOUR)

    /** Étiquette au-dessus du titre dans l'aperçu : quand, et si le replay est possible. */
    fun kicker(p: ProgramEntity, now: Long, catchup: Boolean): String = when {
        p.start <= now && p.end > now -> "EN CE MOMENT"
        p.end <= now -> if (catchup) "DÉJÀ DIFFUSÉ · REPLAY" else "DÉJÀ DIFFUSÉ"
        else -> "${dayLabel(p.start, now).uppercase()} · ${formatClock(p.start)}"
    }

    /** Durée lisible : « 45 min », « 1 h 30 », « 2 h ». */
    fun duration(p: ProgramEntity): String {
        val m = ((p.end - p.start) / 60_000L).toInt().coerceAtLeast(0)
        return when {
            m < 60 -> "$m min"
            m % 60 == 0 -> "${m / 60} h"
            else -> "${m / 60} h ${(m % 60).toString().padStart(2, '0')}"
        }
    }

    /** Temps restant d'un programme en cours : « reste 25 min ». */
    fun remaining(p: ProgramEntity, now: Long): String {
        val m = ((p.end - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
        return if (m < 60) "reste $m min" else "reste ${m / 60} h ${(m % 60).toString().padStart(2, '0')}"
    }
}
