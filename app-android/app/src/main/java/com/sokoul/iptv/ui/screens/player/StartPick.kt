package com.sokoul.iptv.ui.screens.player

import com.sokoul.iptv.domain.SourceOrigin

/**
 * Lecture lancée sans version imposée : la première source lisible du classement commun part directement,
 * au lieu de démarrer l'IPTV puis de changer de version une fois Real-Debrid trouvé.
 */
object StartPick {
    /** Attente maximale de la recherche Real-Debrid avant de lancer ce qui est déjà connu. */
    const val WAIT_MS = 6_000L

    /**
     * Clé Real-Debrid à lancer si elle passe devant l'IPTV, null pour garder la version IPTV demandée.
     * Une source en échec, ou une version Real-Debrid à télécharger, ne compte pas : elle ne démarrerait pas.
     */
    fun debridFirst(ranked: List<PlayerSource>, prefix: String = PlayerViewModel.RD_PREFIX): String? {
        val top = ranked.firstOrNull { !it.failed && (it.origin != SourceOrigin.DEBRID || it.ready != false) } ?: return null
        return if (top.origin == SourceOrigin.DEBRID && top.id.startsWith(prefix)) top.id.removePrefix(prefix) else null
    }
}
