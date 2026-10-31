package com.sokoul.desktop.data

import com.sokoul.iptv.data.debrid.DebridStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Ordre des essais de lecture (logique pure, testée). Vu sur Breaking Bad : les premières versions venaient toutes de
 * Comet, annoncées prêtes, mais Comet renvoyait sa vidéo d'erreur (fichier refusé par Real-Debrid) ; les trois essais
 * tombaient sur la même extension. Après un échec, on essaie d'abord une version d'une autre extension.
 */
object PlayAttempts {
    /** Extension d'une version : hôte de son lien (comet.elfhosted.com, torrentio.strem.fun…). */
    fun host(s: DebridStream): String? = s.url?.toHttpUrlOrNull()?.host

    /**
     * Prochaine version à essayer parmi [remaining] (déjà classées) : la mieux classée d'une extension qui n'a pas
     * encore échoué, sinon la mieux classée tout court ; [readyOnly] = seulement celles annoncées prêtes.
     */
    fun next(remaining: List<DebridStream>, failedHosts: Set<String>, readyOnly: Boolean): DebridStream? {
        val pool = if (readyOnly) remaining.filter { it.release.cached == true || it.inAccount } else remaining
        return pool.firstOrNull { host(it) !in failedHosts } ?: pool.firstOrNull()
    }
}
