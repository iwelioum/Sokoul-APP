package com.sokoul.desktop.player

/** Épisode suivant d'une série (logique pure, testée). */
object NextEpisodeRule {
    /** Fin d'épisode : les 30 dernières secondes (générique), ou la fin du fichier. */
    const val CREDITS_MS = 30_000L

    /** Suivant de SsEe : É+1 dans la saison, sinon É1 de la saison suivante, sinon rien (fin de série). */
    fun next(season: Int, episode: Int, episodesInSeason: Int, seasonCount: Int): Pair<Int, Int>? = when {
        episode < episodesInSeason -> season to episode + 1
        season < seasonCount -> season + 1 to 1
        else -> null
    }

    /** La carte « Épisode suivant » s'affiche-t-elle ? */
    fun offer(timeMs: Long, lengthMs: Long, ended: Boolean): Boolean =
        ended || (lengthMs > 60_000 && lengthMs - timeMs in 0..CREDITS_MS)
}
