package com.sokoul.iptv.player

/**
 * Repère une vidéo figée alors que la lecture continue (le son avance, plus aucune image affichée).
 * Logique pure, testée à part : [StreamPlayer] lui passe le compteur d'images affichées chaque seconde.
 *
 * @param stallMs durée sans nouvelle image avant de réparer
 * @param maxRecoveries réparations au plus par flux (une chaîne « image fixe » ne doit pas boucler)
 */
class VideoStallDetector(private val stallMs: Long = 4_000, private val maxRecoveries: Int = 2) {
    private var lastCount = -1
    private var lastChangeAt = 0L
    var recoveries = 0
        private set

    fun reset() {
        lastCount = -1
        lastChangeAt = 0L
        recoveries = 0
    }

    /** @return vrai s'il faut réparer la vidéo maintenant. */
    fun sample(nowMs: Long, renderedFrames: Int, playing: Boolean, hasVideo: Boolean): Boolean {
        if (!playing || !hasVideo) { lastCount = -1; return false }
        if (renderedFrames != lastCount) {
            lastCount = renderedFrames
            lastChangeAt = nowMs
            return false
        }
        if (nowMs - lastChangeAt < stallMs || recoveries >= maxRecoveries) return false
        recoveries++
        lastChangeAt = nowMs // laisse le temps à la réparation d'agir
        return true
    }
}
