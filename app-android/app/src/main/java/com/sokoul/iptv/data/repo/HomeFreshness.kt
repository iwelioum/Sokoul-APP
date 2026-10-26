package com.sokoul.iptv.data.repo

/**
 * État dans lequel une page a été construite : heure, jour, dernière lecture connue, nombre de favoris
 * (le profil de goûts se recalcule à partir de ces deux derniers).
 */
data class PageStamp(val builtAt: Long, val day: Int, val lastPlay: Long, val favorites: Int)

/** Règles d'un accueil « vivant » : quand le recalculer, quels titres mettre en avant (pures, testées). */
object HomeFreshness {
    const val MAX_AGE_MS = 30 * 60_000L
    const val WEEK_MS = 7 * 24 * 3600_000L

    enum class Reason { DAY, PLAYED, TASTE, AGE }

    /**
     * Raison de recalculer la page, ou null si elle est encore juste : le jour a changé, une lecture a eu lieu
     * depuis, Ma liste a changé (goûts), ou la page a plus de 30 min.
     */
    fun reason(stamp: PageStamp, now: Long, day: Int, lastPlay: Long, favorites: Int): Reason? = when {
        day != stamp.day -> Reason.DAY
        lastPlay > stamp.lastPlay -> Reason.PLAYED
        favorites != stamp.favorites -> Reason.TASTE
        now - stamp.builtAt >= MAX_AGE_MS -> Reason.AGE
        else -> null
    }

    /** Date d'ajout du fournisseur (secondes Unix) dans les 7 derniers jours ; les dates fantaisistes sont ignorées. */
    fun addedThisWeek(addedSec: Long, now: Long): Boolean {
        val ms = addedSec * 1000
        return ms in (now - WEEK_MS)..(now + 24 * 3600_000L)
    }

    /**
     * Séries suivies qui ont reçu du nouveau depuis le dernier visionnage : mise à jour du fournisseur ([modifiedSec],
     * secondes Unix) postérieure à la dernière lecture ([watched], ms). Les plus récemment mises à jour d'abord.
     */
    fun <T> newEpisodes(series: List<T>, id: (T) -> String, modifiedSec: (T) -> Long, watched: Map<String, Long>): List<T> =
        series.filter { s -> watched[id(s)]?.let { w -> modifiedSec(s) * 1000 > w } == true }
            .sortedByDescending(modifiedSec)

    /**
     * Héros : titres du jour, réordonnés selon les goûts ([affinity] 0..1) sans perdre l'actualité (le rang dans la
     * tendance compte pour moitié). [n] titres au plus.
     */
    fun <T> heroPick(pool: List<T>, affinity: (T) -> Double, n: Int): List<T> {
        if (pool.size <= 1) return pool.take(n)
        val last = (pool.size - 1).toDouble()
        return pool.withIndex()
            .sortedByDescending { (i, w) -> affinity(w).coerceIn(0.0, 1.0) + (1.0 - i / last) }
            .map { it.value }
            .take(n)
    }
}
