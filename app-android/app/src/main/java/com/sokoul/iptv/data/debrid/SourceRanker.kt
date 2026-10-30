package com.sokoul.iptv.data.debrid

import com.sokoul.iptv.data.repo.VodQuality
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.PlaybackSources
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.VideoQuality

/** Une façon de lire l'œuvre : version de l'abonnement IPTV ou version Real-Debrid. */
sealed interface Candidate {
    val source: PlaybackSource

    data class Iptv(val version: VodVersion, override val source: PlaybackSource) : Candidate
    data class Debrid(val stream: DebridStream, override val source: PlaybackSource) : Candidate
}

/**
 * Un seul classement pour toutes les origines (« Lecture » lance le premier) :
 * 1. lisible ici (taille sous la limite du réseau, 4K seulement si la box la décode, IPTV libre) ;
 * 2. langue préférée (VF > MULTI > VOSTFR > VO par défaut), VF et MULTI comptant comme « français » ;
 * 3. définition (mesurée si le fichier a déjà été lu), puis HDR (Dolby Vision > HDR10), puis VF avant MULTI ;
 * 4. prête tout de suite avant « à préparer », puis pas d'échec récent ;
 * 5. origine prioritaire (Réglages ; Real-Debrid par défaut) : à langue et image égales, elle passe devant ;
 * 6. qualité mesurée avant qualité lue dans le nom ;
 * 7. rang dans sa propre origine (IPTV : ordre de `VersionPicker` ; Real-Debrid : Remux > Blu-ray > WEB,
 *    TRUEFRENCH/VFF, nombre de sources, taille).
 */
object SourceRanker {
    data class Prefs(
        val languages: List<String> = com.sokoul.iptv.data.repo.VersionPicker.DEFAULT_LANGUAGES,
        val maxBytes: Long? = null,
        val supports4k: Boolean = true,
        /** L'appareil décode le Dolby Vision. */
        val dolbyVision: Boolean = true,
        /** La télé affiche le HDR ; sinon la SDR passe devant la HDR à définition égale. */
        val hdrDisplay: Boolean = true,
        /** Connexion IPTV prise (enregistrement) ou en pause après un refus : l'IPTV passe en dernier. */
        val iptvBlocked: Boolean = false,
        /** Origine prioritaire à langue et image égales (Réglages › Bibliothèque) ; null = aucune. */
        val tieOrigin: SourceOrigin? = null,
        /** Qualité mesurée lors d'une lecture précédente, par clé de flux Real-Debrid. */
        val debridMeasured: Map<String, VodQuality> = emptyMap(),
        /** Sources en échec récent (id IPTV ou clé Real-Debrid) : gardées, mais après les autres de même qualité. */
        val failed: Set<String> = emptySet(),
    )

    /**
     * Fichier trop petit pour être le film ou l'épisode (échantillon, bonus, fichier vide) : sous 50 Mio **et** sous
     * le dixième de la plus grosse source du titre. Un dessin animé de 6 min dont toutes les sources font 35 Mo reste
     * donc proposé ; un échantillon de 12 Mo à côté d'un film de 9 Go ne l'est pas.
     */
    fun tooSmall(bytes: Long?, largest: Long?): Boolean =
        bytes != null && bytes < MIN_VIDEO_BYTES && (largest == null || bytes < largest / 10)

    const val MIN_VIDEO_BYTES = 50L * 1024 * 1024

    /**
     * [iptv] dans l'ordre de `VersionPicker` (choix mémorisé, échecs) : cet ordre départage les versions IPTV entre elles.
     * [keep] : clé Real-Debrid de la source en cours de lecture, jamais écartée (même petite).
     */
    fun rank(iptv: List<VodVersion>, debrid: List<DebridStream>, prefs: Prefs, keep: String? = null): List<Candidate> {
        val iptvC = iptv.map { Candidate.Iptv(it, PlaybackSources.fromIptv(it)) }
        val largest = debrid.mapNotNull { it.release.sizeBytes }.maxOrNull()
        val debridC = debrid.filter { it.key == keep || it.inAccount || !tooSmall(it.release.sizeBytes, largest) }
            .map { Candidate.Debrid(it, heard(it, prefs)) }
        // rang de chaque candidat parmi ceux de sa propre origine (jamais comparé d'une origine à l'autre)
        val inner = HashMap<Candidate, Int>()
        iptvC.forEachIndexed { i, c -> inner[c] = i }
        debridC.withIndex().sortedWith(
            compareBy<IndexedValue<Candidate.Debrid>> { it.value.stream.release.source?.ordinal ?: RipSource.WEBDL.ordinal }
                .thenBy { frenchRank(it.value.stream.release.frenchVariant) }
                .thenByDescending { it.value.stream.release.seeders ?: 0 }
                .thenByDescending { it.value.stream.release.sizeBytes ?: 0 }
                // ordre stable d'une recherche à l'autre : la clé, pas l'ordre d'arrivée des extensions
                .thenBy { it.value.stream.key }
                .thenBy { it.index },
        ).forEachIndexed { i, c -> inner[c.value] = i }
        val all: List<Candidate> = iptvC + debridC
        val order = all.withIndex().associate { (i, c) -> c to i }
        return all.sortedWith(
            compareBy<Candidate> { if (playable(it, prefs)) 0 else 1 }
                // VF et MULTI (qui contient le français) au même rang : la meilleure image l'emporte, la VF seulement à image égale
                .thenBy { audioRank(it.source.language, prefs.languages) }
                .thenBy { qualityRank(effectiveQuality(it, prefs)) }
                .thenBy { hdrRank(it.source.hdr, prefs.dolbyVision, prefs.hdrDisplay) }
                .thenBy { languageRank(it.source.language, prefs.languages) }
                .thenBy { readyRank(it) }
                .thenBy { if (id(it) in prefs.failed) 1 else 0 }
                // même langue, même image, prête : l'origine prioritaire (Real-Debrid par défaut) passe devant
                .thenBy { if (prefs.tieOrigin == null || it.source.origin == prefs.tieOrigin) 0 else 1 }
                .thenBy { if (measured(it, prefs) != null) 0 else 1 }
                .thenBy { inner.getValue(it) }
                .thenBy { order.getValue(it) },
        )
    }

    /** Source Real-Debrid avec la langue entendue lors d'une lecture précédente (sinon celle du nom). */
    private fun heard(s: DebridStream, p: Prefs): PlaybackSource {
        val src = s.toSource()
        val m = p.debridMeasured[s.key] ?: return src
        val heard = com.sokoul.iptv.domain.SpokenLanguages.parse(m.audioLanguage)
        if (heard.isEmpty()) return src
        val lang = com.sokoul.iptv.domain.SpokenLanguages.language(src.language, heard, com.sokoul.iptv.domain.SpokenLanguages.complete(m.audioLanguage, m.fromPlayer))
        return src.copy(language = lang, region = com.sokoul.iptv.domain.SpokenLanguages.region(lang, heard))
    }

    /** Identifiant de la source : id de la version IPTV, ou clé du flux Real-Debrid. */
    fun id(c: Candidate): String = when (c) { is Candidate.Iptv -> c.version.id; is Candidate.Debrid -> c.stream.key }

    fun playable(c: Candidate, p: Prefs): Boolean = when (c) {
        is Candidate.Iptv -> !p.iptvBlocked
        is Candidate.Debrid -> (p.maxBytes == null || (c.stream.release.sizeBytes ?: 0) <= p.maxBytes) &&
            (p.supports4k || effectiveQuality(c, p) != VideoQuality.UHD_4K)
    }

    private fun measured(c: Candidate, p: Prefs): VodQuality? = when (c) {
        is Candidate.Iptv -> c.version.measured
        is Candidate.Debrid -> p.debridMeasured[c.stream.key]
    }?.takeIf { it.hasImage }

    /** Définition réelle quand elle a été mesurée, sinon celle du nom. */
    private fun effectiveQuality(c: Candidate, p: Prefs): VideoQuality? =
        measured(c, p)?.resolution?.let(::fromResolution) ?: c.source.quality

    fun fromResolution(r: String): VideoQuality = when (r) {
        "4K" -> VideoQuality.UHD_4K; "1080p" -> VideoQuality.FHD_1080; "720p" -> VideoQuality.HD_720; else -> VideoQuality.SD
    }

    /**
     * Rang « ce qu'on entend » : une release MULTI contient le français et la version originale, elle a donc
     * le meilleur rang des deux (une MULTI 4K passe devant une VF 1080p).
     */
    fun audioRank(l: AudioLanguage?, order: List<String>): Int {
        val r = languageRank(l, order)
        if (l != AudioLanguage.MULTI) return r
        // MULTI = français + version originale
        return listOf(AudioLanguage.VF, AudioLanguage.VO).map { order.indexOf(it.name) }.filter { it >= 0 }.fold(r) { a, b -> minOf(a, b) }
    }

    fun languageRank(l: AudioLanguage?, order: List<String>): Int =
        l?.let { order.indexOf(it.name).takeIf { i -> i >= 0 } } ?: order.indexOf("VO").takeIf { it >= 0 } ?: order.size

    /** Inconnue = comme du 720p (ni favorisée ni écartée). */
    private fun qualityRank(q: VideoQuality?) = when (q) {
        VideoQuality.UHD_4K -> 0; VideoQuality.FHD_1080 -> 1; VideoQuality.HD_720, null -> 2; VideoQuality.SD -> 3
    }

    /**
     * Sans décodeur Dolby Vision, la HDR10 passe devant la DV (certaines DV ne s'affichent pas bien sans lui).
     * Télé SDR : la SDR d'abord, puis la HDR10, la DV en dernier (conversion de la box, couleurs fausses).
     */
    private fun hdrRank(h: HdrFormat?, dv: Boolean, hdrDisplay: Boolean = true) = if (!hdrDisplay) when (h) {
        null -> 0
        HdrFormat.HDR10 -> 1
        HdrFormat.DOLBY_VISION -> 2
    } else when (h) {
        HdrFormat.DOLBY_VISION -> if (dv) 0 else 1
        HdrFormat.HDR10 -> if (dv) 1 else 0
        null -> 2
    }

    /** IPTV : lisible tout de suite ; Real-Debrid : déjà en cache, inconnu, à télécharger. */
    private fun readyRank(c: Candidate) = when (c) {
        is Candidate.Iptv -> 0
        is Candidate.Debrid -> when (c.stream.readyNow) { true -> 0; null -> 1; false -> 2 }
    }

    private fun frenchRank(v: String?) = when (v) { "TRUEFRENCH", "VFF" -> 0; null -> 1; else -> 2 }
}
