package com.sokoul.iptv.data.debrid

import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.PlaybackSources
import com.sokoul.iptv.domain.VideoQuality

/** Une façon de lire l'œuvre : version de l'abonnement IPTV ou version Real-Debrid. */
sealed interface Candidate {
    val source: PlaybackSource

    data class Iptv(val version: VodVersion, override val source: PlaybackSource) : Candidate
    data class Debrid(val stream: DebridStream, override val source: PlaybackSource) : Candidate
}

/**
 * Un seul classement pour l'IPTV et Real-Debrid (« Lecture » lance le premier), dans cet ordre :
 * 1. lisible ici (taille sous la limite du réseau, 4K seulement si la box la décode, IPTV libre) ;
 * 2. langue préférée (VF > MULTI > VOSTFR > VO par défaut) ;
 * 3. définition, puis HDR (Dolby Vision > HDR10) ;
 * 4. prêt tout de suite (IPTV, Real-Debrid déjà en cache) avant « à préparer », puis l'IPTV
 *    devant Real-Debrid (rien à préparer, reprise et sous-titres habituels) ;
 * 5. entre versions Real-Debrid : origine du fichier (Remux > Blu-ray > WEB), variante française
 *    (TRUEFRENCH, VFF), nombre de sources.
 */
object SourceRanker {
    data class Prefs(
        val languages: List<String> = com.sokoul.iptv.data.repo.VersionPicker.DEFAULT_LANGUAGES,
        val maxBytes: Long? = null,
        val supports4k: Boolean = true,
        /** Connexion IPTV prise (enregistrement) ou en pause après un refus : l'IPTV passe en dernier. */
        val iptvBlocked: Boolean = false,
    )

    /** [iptv] dans l'ordre de `VersionPicker` (choix mémorisé, échecs) : cet ordre départage les versions IPTV entre elles. */
    fun rank(iptv: List<VodVersion>, debrid: List<DebridStream>, prefs: Prefs): List<Candidate> {
        val all = iptv.map { Candidate.Iptv(it, PlaybackSources.fromIptv(it)) } + debrid.map { Candidate.Debrid(it, it.toSource()) }
        val order = all.withIndex().associate { (i, c) -> c to i }
        return all.sortedWith(
            compareBy<Candidate> { if (playable(it, prefs)) 0 else 1 }
                .thenBy { languageRank(it.source.language, prefs.languages) }
                .thenBy { qualityRank(effectiveQuality(it)) }
                .thenBy { hdrRank(it.source.hdr) }
                .thenBy { readyRank(it) }
                .thenBy { if (it is Candidate.Iptv) 0 else 1 }
                .thenBy { (it as? Candidate.Debrid)?.stream?.release?.source?.ordinal ?: RipSource.WEBDL.ordinal }
                .thenBy { frenchRank((it as? Candidate.Debrid)?.stream?.release?.frenchVariant) }
                .thenByDescending { (it as? Candidate.Debrid)?.stream?.release?.seeders ?: 0 }
                .thenBy { order.getValue(it) },
        )
    }

    fun playable(c: Candidate, p: Prefs): Boolean = when (c) {
        is Candidate.Iptv -> !p.iptvBlocked
        is Candidate.Debrid -> (p.maxBytes == null || (c.stream.release.sizeBytes ?: 0) <= p.maxBytes) &&
            (p.supports4k || c.source.quality != VideoQuality.UHD_4K)
    }

    /** Définition réelle quand elle a été mesurée (IPTV), sinon celle du nom. */
    private fun effectiveQuality(c: Candidate): VideoQuality? = when (c) {
        is Candidate.Iptv -> c.version.measured?.resolution?.let { r ->
            when (r) { "4K" -> VideoQuality.UHD_4K; "1080p" -> VideoQuality.FHD_1080; "720p" -> VideoQuality.HD_720; else -> VideoQuality.SD }
        } ?: c.source.quality
        is Candidate.Debrid -> c.source.quality
    }

    fun languageRank(l: AudioLanguage?, order: List<String>): Int =
        l?.let { order.indexOf(it.name).takeIf { i -> i >= 0 } } ?: order.indexOf("VO").takeIf { it >= 0 } ?: order.size

    /** Inconnue = comme du 720p (ni favorisée ni écartée). */
    private fun qualityRank(q: VideoQuality?) = when (q) {
        VideoQuality.UHD_4K -> 0; VideoQuality.FHD_1080 -> 1; VideoQuality.HD_720, null -> 2; VideoQuality.SD -> 3
    }

    private fun hdrRank(h: HdrFormat?) = when (h) { HdrFormat.DOLBY_VISION -> 0; HdrFormat.HDR10 -> 1; null -> 2 }

    private fun readyRank(c: Candidate) = when (c) {
        is Candidate.Iptv -> 0
        is Candidate.Debrid -> when (c.stream.release.cached) { true -> 0; null -> 1; false -> 2 }
    }

    private fun frenchRank(v: String?) = when (v) { "TRUEFRENCH", "VFF" -> 0; null -> 1; else -> 2 }
}
