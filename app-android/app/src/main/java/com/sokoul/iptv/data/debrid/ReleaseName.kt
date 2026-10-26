package com.sokoul.iptv.data.debrid

import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.VideoQuality

/** Origine du fichier, de la meilleure à la moins bonne. */
enum class RipSource(val label: String) { REMUX("Remux"), BLURAY("Blu-ray"), WEBDL("WEB"), WEBRIP("WEBRip"), HDTV("TV") }

/** Tout ce qu'on lit dans le nom d'une version (« Dune.2021.MULTi.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC… »). */
data class Release(
    val quality: VideoQuality? = null,
    val hdr: HdrFormat? = null,
    val language: AudioLanguage? = null,
    /** Variante française précise : « TRUEFRENCH », « VFF », « VFQ »… (pour départager deux VF). */
    val frenchVariant: String? = null,
    val source: RipSource? = null,
    val codec: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    /** Film filmé en salle (CAM, TS, TC, SCR) : jamais proposé. */
    val cam: Boolean = false,
    /** Déjà prêt chez Real-Debrid (« [RD+] », « ⚡ »), pas prêt (« [RD download] »), ou inconnu. */
    val cached: Boolean? = null,
    /** Épisodes contenus (pack de saison) : S02E05 → (2, 5) ; vide = inconnu ou film. */
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * Lecture des noms de versions (fichiers, extensions Stremio type Torrentio / Comet / MediaFusion).
 * Logique pure, testée sur de vrais noms. Inspiré de `legacy/GinBox/sokoul-backend/src/parser.rs`.
 */
object ReleaseName {
    private val tokenSep = Regex("[^A-Za-z0-9+]+")

    fun parse(vararg texts: String?): Release {
        // extension retirée : « .ts » n'est pas « TS » (telesync)
        val all = texts.filterNotNull().joinToString("\n").replace(Regex("(?im)\\.(mkv|mp4|avi|ts|m2ts|m4v|mov|webm)$"), " ")
        val tokens = all.split(tokenSep).filter { it.isNotEmpty() }.map { it.uppercase() }.toSet()
        val up = all.uppercase()
        return Release(
            quality = quality(up, tokens),
            hdr = hdr(up, tokens),
            language = language(all, up, tokens),
            frenchVariant = listOf("TRUEFRENCH", "VFF", "VFQ", "VFI", "VF2", "VFB").firstOrNull { it in tokens },
            source = source(up, tokens),
            codec = when {
                "AV1" in tokens -> "AV1"
                tokens.any { it in setOf("HEVC", "X265", "H265") } || "H.265" in up -> "HEVC"
                tokens.any { it in setOf("X264", "H264", "AVC") } || "H.264" in up -> "H.264"
                else -> null
            },
            sizeBytes = size(all),
            seeders = Regex("👤\\s*(\\d+)").find(all)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("(?i)seeders?:?\\s*(\\d+)").find(all)?.groupValues?.get(1)?.toIntOrNull(),
            cam = tokens.any { it in CAM } || Regex("(?i)\\bHD-?(CAM|TS|TC)\\b").containsMatchIn(all),
            cached = cached(all, up),
            season = episodeTag(all)?.first,
            episode = episodeTag(all)?.second,
        )
    }

    private val CAM = setOf("CAM", "CAMRIP", "HDCAM", "TS", "HDTS", "TELESYNC", "TC", "TELECINE", "SCR", "SCREENER", "DVDSCR", "HDTC")

    private fun quality(up: String, t: Set<String>): VideoQuality? = when {
        t.any { it in setOf("2160P", "4K", "UHD", "2160") } || "3840X2160" in up -> VideoQuality.UHD_4K
        t.any { it in setOf("1080P", "1080I", "1080", "FHD") } || "1920X1080" in up -> VideoQuality.FHD_1080
        t.any { it in setOf("720P", "720") } || "1280X720" in up -> VideoQuality.HD_720
        t.any { it in setOf("480P", "576P", "SD", "DVDRIP", "XVID") } -> VideoQuality.SD
        else -> null
    }

    private fun hdr(up: String, t: Set<String>): HdrFormat? = when {
        t.any { it in setOf("DV", "DOVI", "DOLBYVISION") } || "DOLBY VISION" in up || "DOLBY.VISION" in up -> HdrFormat.DOLBY_VISION
        t.any { it in setOf("HDR", "HDR10", "HDR10+", "HDR10PLUS") } -> HdrFormat.HDR10
        else -> null
    }

    private fun source(up: String, t: Set<String>): RipSource? = when {
        "REMUX" in t || "BDREMUX" in t -> RipSource.REMUX
        t.any { it in setOf("BLURAY", "BDRIP", "BRRIP", "BD") } || "BLU-RAY" in up -> RipSource.BLURAY
        t.any { it in setOf("WEBDL", "WEB") } || "WEB-DL" in up -> RipSource.WEBDL
        "WEBRIP" in t -> RipSource.WEBRIP
        t.any { it in setOf("HDTV", "PDTV", "DSR", "TVRIP") } -> RipSource.HDTV
        else -> null
    }

    private fun language(all: String, up: String, t: Set<String>): AudioLanguage? {
        val french = t.any { it in setOf("FRENCH", "TRUEFRENCH", "VFF", "VFQ", "VFI", "VF", "VF2", "VFB", "FR") } || "🇫🇷" in all
        val multi = "MULTI" in t || "MULTI AUDIO" in up || Regex("(?i)\\bdual\\b").containsMatchIn(all)
        return when {
            t.any { it in setOf("VOSTFR", "SUBFRENCH", "STFR") } -> AudioLanguage.VOSTFR
            multi && french -> AudioLanguage.MULTI
            french -> AudioLanguage.VF
            multi -> AudioLanguage.MULTI
            else -> null
        }
    }

    /** « 💾 18.3 GB », « 4.7 Go », « 700 MB ». */
    fun size(text: String): Long? {
        val m = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(TB|GB|GO|MB|MO|GIB|MIB)\\b").find(text) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2].uppercase()) { "TB" -> 1e12; "GB", "GO", "GIB" -> 1e9; else -> 1e6 }
        return (v * mult).toLong()
    }

    private fun cached(all: String, up: String): Boolean? = when {
        "[RD+]" in up || "RD+" in up || "⚡" in all || "[RD⚡]" in up || "INSTANT" in up -> true
        "[RD DOWNLOAD]" in up || "RD DOWNLOAD" in up || "⏳" in all -> false
        else -> null
    }

    /** S02E05, 2x05. */
    fun episodeTag(text: String): Pair<Int, Int>? {
        Regex("(?i)\\bS(\\d{1,2})[ ._-]?E(\\d{1,3})\\b").find(text)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        Regex("(?i)\\b(\\d{1,2})x(\\d{2,3})\\b").find(text)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        return null
    }

    private val VIDEO_EXT = setOf("mkv", "mp4", "avi", "m4v", "ts", "m2ts", "mov", "webm")

    fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in VIDEO_EXT && !Regex("(?i)\\bsample\\b").containsMatchIn(path)

    /**
     * Fichier à lire dans un torrent : l'épisode demandé dans un pack de saison, sinon la plus grosse vidéo.
     * Un « extra » (sample, bonus) n'est jamais choisi.
     */
    fun pickFile(files: List<RdFile>, season: Int?, episode: Int?): RdFile? {
        val videos = files.filter { isVideo(it.path) && !Regex("(?i)(/|\\b)(extras?|bonus|featurettes?)(/|\\b)").containsMatchIn(it.path) }
        if (season != null && episode != null) {
            return videos.filter { f -> episodeTag(f.name)?.let { it == season to episode } == true }.maxByOrNull { it.bytes }
                ?: videos.singleOrNull()
        }
        return videos.maxByOrNull { it.bytes }
    }
}
