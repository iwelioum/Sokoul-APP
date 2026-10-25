package com.sokoul.iptv.domain

import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.data.repo.Work

/** D'où vient le flux. DEBRID arrivera avec Real-Debrid. */
enum class SourceOrigin { IPTV, DEBRID }

/** Définition de l'image, de la meilleure à la moins bonne. */
enum class VideoQuality { UHD_4K, FHD_1080, HD_720, SD }

/** Plage dynamique étendue, indépendante de la définition. */
enum class HdrFormat { HDR10, DOLBY_VISION }

/** Langue de la piste audio principale. */
enum class AudioLanguage { VF, MULTI, VOSTFR, VO }

enum class SourceState {
    /** Lisible tout de suite. */
    AVAILABLE,
    /** Debrid : déjà en cache, lancement immédiat. */
    CACHED_INSTANT,
    /** Connue (TMDB) mais aucune source : grisée, non lisible. */
    UNAVAILABLE,
}

/**
 * Une façon de lire une œuvre, décrite par des valeurs typées : aucun texte à analyser ni à traduire ici,
 * l'interface calcule ses libellés et ses badges (`ui/components/SourceBadges.kt`).
 */
data class PlaybackSource(
    val id: String,
    val origin: SourceOrigin,
    val quality: VideoQuality? = null,
    val hdr: HdrFormat? = null,
    val language: AudioLanguage? = null,
    /** Pays affiché par le fournisseur quand la langue est inconnue (« AR », « EN »). */
    val region: String? = null,
    /** Rubrique du fournisseur (« [FR] FILMS 4K »). */
    val group: String? = null,
    val state: SourceState = SourceState.AVAILABLE,
)

object PlaybackSources {
    /** Adaptateur : une version IPTV (étiquettes du parseur : 4K/FHD/HD/SD/HDR/HEVC, VF/MULTI/VOSTFR/VO). */
    fun fromIptv(v: VodVersion, region: String? = null): PlaybackSource = PlaybackSource(
        id = v.id,
        origin = SourceOrigin.IPTV,
        quality = quality(v.quality),
        hdr = if (v.quality == "HDR") HdrFormat.HDR10 else null,
        language = v.language?.let { runCatching { AudioLanguage.valueOf(it) }.getOrNull() },
        region = region ?: if (v.language == null) com.sokoul.iptv.data.repo.VersionPicker.region(v.name) else null,
        group = v.categoryName,
    )

    fun quality(tag: String?): VideoQuality? = when (tag?.uppercase()) {
        "4K", "UHD" -> VideoQuality.UHD_4K
        "FHD" -> VideoQuality.FHD_1080
        "HD" -> VideoQuality.HD_720
        "SD" -> VideoQuality.SD
        else -> null
    }

    /** État d'une carte : une œuvre sans source est grisée (sagas, résultats TMDB, Ma liste). */
    fun state(w: Work): SourceState = if (w.available) SourceState.AVAILABLE else SourceState.UNAVAILABLE

    /** Résumé d'une carte : la meilleure définition connue de l'œuvre. */
    fun summary(w: Work): PlaybackSource =
        PlaybackSource(id = w.key, origin = SourceOrigin.IPTV, quality = quality(w.quality), hdr = if (w.quality == "HDR") HdrFormat.HDR10 else null, state = state(w))
}
