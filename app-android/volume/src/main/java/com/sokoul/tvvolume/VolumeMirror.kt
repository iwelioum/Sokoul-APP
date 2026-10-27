package com.sokoul.tvvolume

/**
 * Sur Android TV, le système garde pour lui Volume + / − : un service d'accessibilité ne les reçoit pas.
 * On suit donc le volume de la box : il est tenu à un « point d'ancrage » (un cran sous le maximum, pour voir
 * les deux sens) ; chaque cran de différence devient une touche envoyée à la TV, puis la box revient à l'ancrage.
 */
object VolumeMirror {
    /** Au plus quelques crans par changement (touche maintenue) : jamais de rafale vers la TV. */
    const val MAX_STEPS = 3

    fun anchor(max: Int): Int = (max - 1).coerceAtLeast(1)

    /**
     * Touches à envoyer pour un changement de volume de la box ([prev] → [value]).
     * Le retour à l'ancrage (fait par le service lui-même) ne compte pas.
     */
    fun keysFor(prev: Int, value: Int, anchor: Int): List<TvKey> {
        if (value == anchor || prev < 0 || value == prev) return emptyList()
        val key = if (value > prev) TvKey.VOL_UP else TvKey.VOL_DOWN
        return List(minOf(kotlin.math.abs(value - prev), MAX_STEPS)) { key }
    }
}
