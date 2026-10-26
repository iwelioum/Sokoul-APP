package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.VodProbeEntity
import com.sokoul.iptv.parser.MediaInfo
import com.sokoul.iptv.parser.XtMedia

/**
 * Qualité réelle d'une version (mesurée par le panel ou le lecteur), prête à afficher et à comparer.
 * Remplace les étiquettes devinées dans le nom (« 4K », « FHD ») quand elle est connue.
 */
data class VodQuality(
    val width: Int? = null,
    val height: Int? = null,
    val videoCodec: String? = null,
    val hdr: String? = null,
    val audioCodec: String? = null,
    val channels: Int? = null,
    val audioLanguage: String? = null,
    val bitrateKbps: Int? = null,
    val durationSecs: Int? = null,
    val fromPlayer: Boolean = false,
) {
    /** « 4K », « 1080p », « 720p », « SD », ou null si l'image n'a pas été mesurée. */
    val resolution: String? get() = MediaInfo.resolution(width, height)

    val hasImage get() = resolution != null
    val hasSound get() = channels != null || audioCodec != null

    /** Image : « 4K · HDR10 · HEVC ». */
    fun imageLine(): String = listOfNotNull(resolution, hdr, MediaInfo.codec(videoCodec)).joinToString(" · ")

    /** Son : « 5.1 Dolby Digital+ ». */
    fun soundLine(): String = listOfNotNull(MediaInfo.channels(channels), MediaInfo.audioCodec(audioCodec)).joinToString(" ")

    /** « 4K · HDR10 · HEVC · 5.1 Dolby Digital+ · 2 h 12 ». */
    fun line(): String = listOf(imageLine(), soundLine(), durationSecs?.let(::duration).orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")

    /** Note d'image : définition d'abord, puis HDR, puis débit (départage). */
    val imageScore: Int
        get() {
            val res = when (resolution) { "4K" -> 4; "1080p" -> 3; "720p" -> 2; "SD" -> 1; else -> 0 }
            val hdrBonus = when (hdr) { "Dolby Vision" -> 3; "HDR10" -> 2; "HLG" -> 1; else -> 0 }
            return res * 1000 + hdrBonus * 100 + ((bitrateKbps ?: 0) / 500).coerceAtMost(99)
        }

    /** Note de son : nombre de canaux, puis codec. */
    val soundScore: Int
        get() {
            val codec = when (audioCodec) { "truehd" -> 6; "eac3" -> 5; "dts" -> 5; "ac3" -> 4; "flac" -> 4; "aac" -> 2; else -> if (audioCodec == null) 0 else 1 }
            return (channels ?: 0) * 10 + codec
        }

    companion object {
        fun duration(secs: Int): String {
            val m = secs / 60
            return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"
        }
    }
}

fun VodProbeEntity.toQuality(): VodQuality? =
    VodQuality(width, height, videoCodec, hdr, audioCodec, audioChannels, audioLanguage, bitrateKbps, durationSecs, fromPlayer)
        .takeIf { it.hasImage || it.hasSound }

fun XtMedia.toProbe(pid: Long, type: ContentType, itemId: String, now: Long) = VodProbeEntity(
    pid, type, itemId, width, height, videoCodec, hdr, fps, audioCodec, audioChannels, audioLanguage, bitrateKbps, durationSecs, false, now,
)

/** Meilleures versions d'un ensemble (identifiant), pour les badges « Meilleure image » / « Meilleur son ». */
object BestVersions {
    fun image(q: Map<String, VodQuality>): String? =
        q.filterValues { it.hasImage }.takeIf { it.size >= 2 }?.maxByOrNull { it.value.imageScore }
            ?.takeIf { best -> q.values.count { it.imageScore == best.value.imageScore } == 1 }?.key

    fun sound(q: Map<String, VodQuality>): String? =
        q.filterValues { it.hasSound }.takeIf { it.size >= 2 }?.maxByOrNull { it.value.soundScore }
            ?.takeIf { best -> q.values.count { it.soundScore == best.value.soundScore } == 1 }?.key
}
