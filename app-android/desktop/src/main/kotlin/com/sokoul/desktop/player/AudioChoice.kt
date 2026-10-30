package com.sokoul.desktop.player

/**
 * Piste audio à l'ouverture : libVLC prend la première piste quand aucune n'est marquée « fr » (vu sur une version
 * « MULTI » turque et anglaise : le film démarrait en turc). On préfère le français, sinon l'anglais (VO la plus
 * répandue), sinon on laisse le choix de libVLC.
 */
object AudioChoice {
    private val french = Regex("""\b(fr|fre|fra|french|fran[cç]ais|vf|vff|vfq|vfi|truefrench)\b""", RegexOption.IGNORE_CASE)
    private val english = Regex("""\b(en|eng|english|anglais)\b""", RegexOption.IGNORE_CASE)

    /** Une piste française parmi [tracks] (id, description). */
    fun hasFrench(tracks: List<Pair<Int, String>>): Boolean = tracks.any { french.containsMatchIn(it.second) }

    /** Identifiant de la piste à prendre parmi [tracks] (id, description), ou null pour garder celle de libVLC. */
    fun pick(tracks: List<Pair<Int, String>>): Int? =
        tracks.firstOrNull { french.containsMatchIn(it.second) }?.first
            ?: tracks.firstOrNull { english.containsMatchIn(it.second) }?.first
}
