package com.sokoul.iptv.parser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Qualité réelle d'un fichier, telle que le panel Xtream l'a mesurée (bloc `info.video` / `info.audio`
 * de `get_vod_info` et des épisodes, issu de ffprobe). Tout est facultatif : beaucoup de panels n'en donnent qu'une partie.
 */
data class XtMedia(
    val width: Int? = null,
    val height: Int? = null,
    val videoCodec: String? = null,
    /** « HDR10 », « HLG » ou « Dolby Vision ». */
    val hdr: String? = null,
    val fps: Double? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val audioLanguage: String? = null,
    val bitrateKbps: Int? = null,
    val durationSecs: Int? = null,
)

object MediaInfo {
    fun parse(info: JsonObject?): XtMedia? {
        info ?: return null
        val v = info["video"] as? JsonObject
        val a = info["audio"] as? JsonObject
        val bitrate = info.int("bitrate")?.takeIf { it > 0 }
        if (v == null && a == null && bitrate == null) return null
        return XtMedia(
            width = v?.int("width")?.takeIf { it > 0 },
            height = v?.int("height")?.takeIf { it > 0 },
            videoCodec = v?.str("codec_name")?.lowercase(),
            hdr = v?.let(::hdrOf),
            fps = (v?.str("avg_frame_rate") ?: v?.str("r_frame_rate"))?.let(::rate),
            audioCodec = a?.str("codec_name")?.lowercase(),
            audioChannels = a?.int("channels")?.takeIf { it > 0 },
            audioLanguage = (a?.get("tags") as? JsonObject)?.str("language")?.lowercase(),
            // certains panels donnent des bits/s, la plupart des kb/s
            bitrateKbps = bitrate?.let { if (it > 200_000) it / 1000 else it },
            durationSecs = info.int("duration_secs") ?: info.str("duration")?.let(XtreamParse::parseDuration),
        )
    }

    private fun hdrOf(v: JsonObject): String? {
        val side = (v["side_data_list"] as? JsonArray)?.toString().orEmpty().lowercase()
        if ("dovi" in side || "dolby vision" in side || v.str("codec_tag_string")?.lowercase()?.startsWith("dv") == true) return "Dolby Vision"
        return when (v.str("color_transfer")?.lowercase()) {
            "smpte2084" -> "HDR10"
            "arib-std-b67" -> "HLG"
            else -> null
        }
    }

    private fun rate(s: String): Double? {
        val (n, d) = s.split('/').let { it[0].toDoubleOrNull() to (it.getOrNull(1)?.toDoubleOrNull() ?: 1.0) }
        if (n == null || d == 0.0) return null
        return (n / d).takeIf { it in 1.0..240.0 }
    }

    /** Hauteur utile : 1920 × 800 (scope) est du 1080p, 3840 × 1600 du 4K. */
    fun resolution(width: Int?, height: Int?): String? {
        val w = width ?: 0; val h = height ?: 0
        if (w == 0 && h == 0) return null
        return when {
            w >= 3200 || h >= 2000 -> "4K"
            w >= 1800 || h >= 1000 -> "1080p"
            w >= 1200 || h >= 700 -> "720p"
            else -> "SD"
        }
    }

    fun codec(c: String?): String? = when (c?.lowercase()) {
        null, "" -> null
        "h264", "avc", "avc1" -> "H.264"
        "hevc", "h265" -> "HEVC"
        "av1" -> "AV1"
        "vp9" -> "VP9"
        "mpeg2video" -> "MPEG-2"
        "mpeg4" -> "MPEG-4"
        else -> c.uppercase()
    }

    fun channels(n: Int?): String? = when (n) {
        null -> null
        1 -> "Mono"
        2 -> "Stéréo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$n canaux"
    }

    fun audioCodec(c: String?): String? = when (c?.lowercase()) {
        null, "" -> null
        "eac3" -> "Dolby Digital+"
        "ac3" -> "Dolby Digital"
        "truehd" -> "Dolby TrueHD"
        "dts" -> "DTS"
        "aac" -> "AAC"
        "mp3" -> "MP3"
        "opus" -> "Opus"
        "flac" -> "FLAC"
        else -> c.uppercase()
    }
}
