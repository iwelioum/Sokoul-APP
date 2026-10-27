package com.sokoul.iptv.ui.screens.player

import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.data.repo.VersionPicker
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.parser.MediaInfo
import com.sokoul.iptv.ui.components.SourceLabels

/** D'où vient la qualité affichée : mesurée en lecture (sûre) ou lue dans le nom du fichier (annoncée). */
enum class QualityBasis { MEASURED, NAME, UNKNOWN }

/**
 * Une source jouable de l'œuvre en cours, telle qu'affichée dans le lecteur (panneaux Sources et Audio).
 * Tout est déjà calculé : l'interface n'analyse aucun nom.
 */
data class PlayerSource(
    /** Identifiant passé à `switchVersion` : id du film, « rd:clé » ou « ep:série|épisode ». */
    val id: String,
    val origin: SourceOrigin,
    val language: AudioLanguage? = null,
    /** Pays affiché par le fournisseur quand la langue est inconnue (« AR », « EN »). */
    val region: String? = null,
    val quality: VideoQuality? = null,
    /** « HDR10 », « Dolby Vision », « HLG ». */
    val hdr: String? = null,
    /** « HEVC », « H.264 », « AV1 ». */
    val codec: String? = null,
    /** Où elle a été trouvée, en clair : « Abonnement IPTV · « FILMS 4K FR » », « Real-Debrid · Remux · 12 Go · prête ». */
    val where: String,
    /** Nom exact du fichier ou de la release (petit texte). */
    val fileName: String? = null,
    val basis: QualityBasis = QualityBasis.UNKNOWN,
    val current: Boolean = false,
)

object PlayerSources {
    const val EP_PREFIX = "ep:"

    fun episodeId(seriesId: String, episodeId: String) = "$EP_PREFIX$seriesId|$episodeId"

    /** « ep:série|épisode » → (série, épisode). */
    fun parseEpisodeId(id: String): Pair<String, String>? =
        id.takeIf { it.startsWith(EP_PREFIX) }?.removePrefix(EP_PREFIX)?.split('|', limit = 2)?.takeIf { it.size == 2 }?.let { it[0] to it[1] }

    /** Version de l'abonnement : la mesure (si le fichier a déjà été lu) prime sur le nom. */
    fun fromIptv(v: VodVersion, current: Boolean, id: String = v.id): PlayerSource {
        val m = v.measured?.takeIf { it.hasImage }
        val nameCodec = if (v.quality == "HEVC" || Regex("(?i)\\b(hevc|x265|h\\.?265)\\b").containsMatchIn(v.name)) "HEVC" else null
        return PlayerSource(
            id = id,
            origin = SourceOrigin.IPTV,
            language = v.language?.let { runCatching { AudioLanguage.valueOf(it) }.getOrNull() },
            region = if (v.language == null) VersionPicker.region(v.name) else null,
            quality = if (m != null) qualityOf(m.resolution) else com.sokoul.iptv.domain.PlaybackSources.quality(v.quality),
            hdr = m?.hdr ?: if (v.quality == "HDR") "HDR10" else null,
            codec = m?.videoCodec?.let(MediaInfo::codec) ?: nameCodec,
            where = "Abonnement IPTV" + (v.categoryName?.takeIf { it.isNotBlank() }?.let { " · « ${it.trim()} »" } ?: ""),
            fileName = v.name,
            basis = when {
                m != null -> QualityBasis.MEASURED
                v.quality != null -> QualityBasis.NAME
                else -> QualityBasis.UNKNOWN
            },
            current = current,
        )
    }

    /** Version Real-Debrid : tout vient du nom de la release. */
    fun fromDebrid(s: DebridStream, current: Boolean, id: String): PlayerSource {
        val r = s.release
        val state = when (r.cached) { true -> "prête"; false -> "à télécharger"; null -> null }
        return PlayerSource(
            id = id,
            origin = SourceOrigin.DEBRID,
            language = r.language,
            quality = r.quality,
            hdr = r.hdr?.let(SourceLabels::hdr)?.let { if (it == "HDR") "HDR10" else it },
            codec = r.codec,
            where = listOfNotNull("Real-Debrid", r.source?.label, r.sizeBytes?.let(SourceLabels::size), state).joinToString(" · "),
            fileName = (s.filename ?: s.title.lineSequence().firstOrNull())?.trim()?.takeIf { it.isNotEmpty() },
            basis = if (r.quality != null) QualityBasis.NAME else QualityBasis.UNKNOWN,
            current = current,
        )
    }

    /** « 4K », « 1080p »… (mesure) → définition ; mêmes libellés que les badges. */
    /** Fichier de « Mes fichiers Real-Debrid » : décrit par le nom du fichier. */
    fun fromCloud(item: com.sokoul.iptv.data.debrid.CloudItem?, title: String?): PlayerSource {
        val r = item?.release
        return PlayerSource(
            id = "cloud", origin = SourceOrigin.DEBRID, language = r?.language, quality = r?.quality,
            hdr = r?.hdr?.let(SourceLabels::hdr)?.let { if (it == "HDR") "HDR10" else it }, codec = r?.codec,
            where = listOfNotNull("Real-Debrid · Mes fichiers", item?.sizeBytes?.takeIf { it > 0 }?.let(SourceLabels::size)).joinToString(" · "),
            fileName = item?.title ?: title, basis = if (r?.quality != null) QualityBasis.NAME else QualityBasis.UNKNOWN, current = true,
        )
    }

    fun qualityOf(resolution: String?): VideoQuality? = VideoQuality.entries.firstOrNull { SourceLabels.quality(it) == resolution }

    /** Nom de langue lisible : « Français (VF) », « Version originale (VO) ». */
    fun languageName(lang: AudioLanguage?, region: String? = null): String = when (lang) {
        AudioLanguage.VF -> "Français (VF)"
        AudioLanguage.MULTI -> "Plusieurs langues (MULTI)"
        AudioLanguage.VOSTFR -> "VO sous-titrée français (VOSTFR)"
        AudioLanguage.VO -> "Version originale (VO)"
        null -> region?.let { "Langue : $it" } ?: "Langue non indiquée"
    }

    /** Puces d'une source : langue, définition, HDR, codec. */
    fun chips(s: PlayerSource): List<String> = listOfNotNull(
        s.language?.name ?: s.region,
        s.quality?.let(SourceLabels::quality),
        s.hdr,
        s.codec,
    )

    /** Libellé court (pastille du lecteur) : « VF · 1080p », sinon « Source ». */
    fun short(s: PlayerSource?): String =
        s?.let { listOfNotNull(it.language?.name ?: it.region, it.quality?.let(SourceLabels::quality)).joinToString(" · ") }?.ifEmpty { null } ?: "Source"

    fun basisLabel(b: QualityBasis): String? = when (b) {
        QualityBasis.MEASURED -> "qualité mesurée"
        QualityBasis.NAME -> "qualité d'après le nom"
        QualityBasis.UNKNOWN -> null
    }

    /** Clé de langue : deux sources de même clé ont le même son. */
    private fun langKey(s: PlayerSource): String? = s.language?.name ?: s.region?.let { "R:$it" }

    private val best = compareBy<PlayerSource>({ it.quality?.ordinal ?: 9 }, { if (it.hdr != null) 0 else 1 }, { it.origin.ordinal }, { if (it.basis == QualityBasis.MEASURED) 0 else 1 })

    /**
     * Section « Autres langues » du panneau Audio : la meilleure source de chaque langue absente du fichier en cours
     * (définition, puis HDR, puis IPTV avant Real-Debrid). Langue inconnue : pas de ligne (rien à promettre).
     */
    fun otherLanguages(sources: List<PlayerSource>): List<PlayerSource> {
        val current = sources.firstOrNull { it.current }
        val currentKey = current?.let(::langKey)
        return sources.filter { !it.current && langKey(it) != null && langKey(it) != currentKey }
            .groupBy { langKey(it)!! }.values.map { group -> group.sortedWith(best).first() }
            .sortedWith(compareBy { langOrder(it) })
    }

    private fun langOrder(s: PlayerSource) = s.language?.ordinal ?: 10

    /** La source [other] a une image moins bonne que [current] (les deux connues). */
    fun qualityDrop(other: PlayerSource, current: PlayerSource?): Boolean {
        val a = other.quality ?: return false
        val b = current?.quality ?: return false
        return a.ordinal > b.ordinal
    }

    /** La source [other] a une image meilleure que [current]. */
    fun qualityGain(other: PlayerSource, current: PlayerSource?): Boolean {
        val a = other.quality ?: return false
        val b = current?.quality ?: return false
        return a.ordinal < b.ordinal
    }

    /**
     * Bandeau « ce que vous regardez » : d'abord la mesure du flux en cours (définition, codec, HDR),
     * puis la langue et l'origine de la source. Ex. « 1080p · HEVC · HDR10 · VF · IPTV ».
     */
    fun banner(width: Int?, height: Int?, codec: String?, hdr: String?, source: PlayerSource?): String? {
        val res = MediaInfo.resolution(width, height)
        val parts = listOfNotNull(
            res ?: source?.quality?.let(SourceLabels::quality),
            if (res != null) codec else source?.codec,
            if (res != null) hdr else source?.hdr,
            source?.let { it.language?.name ?: it.region },
            source?.origin?.let { if (it == SourceOrigin.IPTV) "IPTV" else "Real-Debrid" },
        )
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** HDR d'après la courbe de transfert de l'image (Media3). */
    fun hdrOf(colorTransfer: Int?, dolbyVision: Boolean): String? = when (colorTransfer) {
        androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> if (dolbyVision) "Dolby Vision" else "HDR10"
        androidx.media3.common.C.COLOR_TRANSFER_HLG -> "HLG"
        else -> null
    }
}
