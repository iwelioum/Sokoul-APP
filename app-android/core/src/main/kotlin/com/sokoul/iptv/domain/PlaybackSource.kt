package com.sokoul.iptv.domain

/** D'où vient le flux : abonnement IPTV ou compte Real-Debrid. */
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
    /** Taille du fichier (Real-Debrid) : « 18 Go ». */
    val sizeBytes: Long? = null,
    /** Origine du fichier (Real-Debrid) : « Remux », « Blu-ray », « WEB ». */
    val rip: String? = null,
)
