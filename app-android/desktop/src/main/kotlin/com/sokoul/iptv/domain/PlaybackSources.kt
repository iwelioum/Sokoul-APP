package com.sokoul.iptv.domain

import com.sokoul.iptv.data.repo.Work

/** Même résumé de carte que l'app tablette (`domain/PlaybackSource.kt`) : la meilleure définition connue de l'œuvre. */
object PlaybackSources {
    fun quality(tag: String?): VideoQuality? = when (tag?.uppercase()) {
        "4K", "UHD" -> VideoQuality.UHD_4K
        "FHD" -> VideoQuality.FHD_1080
        "HD" -> VideoQuality.HD_720
        "SD" -> VideoQuality.SD
        else -> null
    }

    fun state(w: Work): SourceState = if (w.available) SourceState.AVAILABLE else SourceState.UNAVAILABLE

    fun summary(w: Work): PlaybackSource =
        PlaybackSource(id = w.key, origin = SourceOrigin.IPTV, quality = quality(w.quality), hdr = if (w.quality == "HDR") HdrFormat.HDR10 else null, state = state(w))
}
