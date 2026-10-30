package com.sokoul.iptv.domain

import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.data.repo.Work

// Types des sources (SourceOrigin, VideoQuality, HdrFormat, AudioLanguage, SourceState, PlaybackSource) : module :core.

object PlaybackSources {
    /** Adaptateur : une version IPTV (étiquettes du parseur : 4K/FHD/HD/SD/HDR/HEVC, VF/MULTI/VOSTFR/VO). */
    fun fromIptv(v: VodVersion, region: String? = null): PlaybackSource = PlaybackSource(
        id = v.id,
        origin = SourceOrigin.IPTV,
        quality = quality(v.quality),
        hdr = if (v.quality == "HDR") HdrFormat.HDR10 else null,
        language = v.spokenLanguage?.let { runCatching { AudioLanguage.valueOf(it) }.getOrNull() },
        region = region ?: if (v.spokenLanguage == null) SpokenLanguages.region(null, v.heard) ?: com.sokoul.iptv.data.repo.VersionPicker.region(v.name) else null,
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
