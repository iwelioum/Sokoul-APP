package com.sokoul.iptv.parser

import java.text.Normalizer

data class TitleInfo(
    val clean: String,
    val year: Int?,
    val language: String?,
    val quality: String?,
    val prefix: String?,
)

data class EpisodeRef(val show: String, val season: Int, val episode: Int)

/**
 * Nettoie les noms « sales » des fournisseurs IPTV
 * (`FR| Le Parrain (1972) [VFF] 4K`) en titre exploitable + étiquettes.
 * Expressions reprises et simplifiées de GinBox/sokoul-backend/src/parser.rs.
 */
object TitleTagger {

    // Préfixe entre délimiteurs (|FR|, [FR], (VOD)) ou code connu suivi de ":", "|" ou " - "
    // (liste fermée pour ne pas couper "NCIS: Los Angeles" ou "CSI: Miami").
    private const val KNOWN_PREFIXES =
        "FR|FRA|EN|ENG|UK|US|USA|DE|ES|IT|PT|AR|TR|NL|BE|CH|CA|QC|MA|DZ|TN|AF|EU|LAT|PL|RO|AL|EXYU|EX-YU|VOD|4K|UHD|FHD|HD|SD|MULTI|VF|VFF|VOSTFR"
    private val prefixRegex = Regex(
        """^\s*(?:[|\[(]\s*([A-Z0-9]{2,5}(?:[- ][A-Z0-9]{2,5})?)\s*[|\])]\s*[:\-|]?|($KNOWN_PREFIXES)\s*(?::|\||\s-\s))\s*"""
    )
    private val yearParen = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""")
    private val yearTrail = Regex("""(?:\s[-–]\s|\s)((?:19|20)\d{2})\s*$""")
    private val qualityRegex = Regex("""(?i)(?<![\p{L}\d])(4K|UHD|2160p|FHD|FULL ?HD|1080[pi]?|HD|720p|SD|480p|HEVC|H\.?265|x265|HDR10\+?|HDR|DOLBY ?VISION|DV|3D|50 ?FPS|60 ?FPS)(?![\p{L}\d])""")
    private val languageRegex = Regex("""(?i)(?<![\p{L}\d])(TRUE[.\- ]?FRENCH|SUB[.\- ]?FRENCH|VFF|VFQ|VFI|VF2|VOSTFR|VOST|VF|VO|MULTI|FRENCH)(?![\p{L}\d])""")
    private val bracketJunk = Regex("""[\[(]\s*[)\]]""")
    private val separators = Regex("""\s*[|•]\s*""")
    private val spaces = Regex("""\s{2,}""")
    private val episodeRegex = Regex("""^(.*?)[\s._\-]*[Ss](\d{1,2})[\s._\-]*[Ee](\d{1,3})\b.*$""")
    private val episodeFrRegex = Regex("""(?i)^(.*?)[\s._\-]*saison\s*(\d{1,2})[\s._\-]*(?:é|e)pisode\s*(\d{1,3}).*$""")

    fun parse(raw: String): TitleInfo {
        var s = raw.trim()
        var prefix: String? = null
        prefixRegex.find(s)?.let { m ->
            prefix = m.groups[1]?.value ?: m.groups[2]?.value
            s = s.substring(m.range.last + 1)
        }
        val qualities = qualityRegex.findAll(s).map { normalizeQuality(it.value) }.toList()
        val languages = languageRegex.findAll(s).map { normalizeLanguage(it.value) }.toList()
        s = qualityRegex.replace(s, " ")
        s = languageRegex.replace(s, " ")
        s = bracketJunk.replace(s, " ").trim()

        var year: Int? = null
        yearParen.find(s)?.let { year = it.groupValues[1].toInt(); s = s.removeRange(it.range) }
        if (year == null) yearTrail.find(s)?.let {
            val y = it.groupValues[1].toInt()
            // évite de prendre "2012" pour une année quand c'est tout le titre
            if (s.length - it.value.length >= 2) { year = y; s = s.removeRange(it.range) }
        }
        s = bracketJunk.replace(s, " ")
        s = separators.replace(s, " ")
        s = s.trim(' ', '-', '_', ':', '|', '.', '–')
        s = spaces.replace(s, " ")
        if (s.isEmpty()) s = raw.trim()

        return TitleInfo(
            clean = s,
            year = year,
            language = languages.firstOrNull() ?: prefix?.let(::languageFromPrefix),
            quality = qualities.maxByOrNull(::qualityRank),
            prefix = prefix,
        )
    }

    fun episode(raw: String): EpisodeRef? {
        val m = episodeRegex.matchEntire(raw.trim()) ?: episodeFrRegex.matchEntire(raw.trim()) ?: return null
        val show = parse(m.groupValues[1]).clean.ifBlank { return null }
        return EpisodeRef(show, m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    private fun normalizeQuality(q: String): String {
        val u = q.uppercase().replace(" ", "").replace(".", "")
        return when {
            u == "4K" || u == "UHD" || u.startsWith("2160") -> "4K"
            u == "FHD" || u == "FULLHD" || u.startsWith("1080") -> "FHD"
            u == "HD" || u.startsWith("720") -> "HD"
            u == "SD" || u.startsWith("480") -> "SD"
            u == "HEVC" || u == "H265" || u == "X265" -> "HEVC"
            u.startsWith("HDR") || u == "DOLBYVISION" || u == "DV" -> "HDR"
            else -> u
        }
    }

    private fun qualityRank(q: String) = when (q) {
        "4K" -> 4; "FHD" -> 3; "HD" -> 2; "SD" -> 1; else -> 0
    }

    private fun normalizeLanguage(l: String): String {
        val u = l.uppercase().replace(Regex("[.\\- ]"), "")
        return when (u) {
            "TRUEFRENCH", "VFF", "VF", "VFQ", "VFI", "VF2", "FRENCH" -> "VF"
            "SUBFRENCH", "VOSTFR", "VOST" -> "VOSTFR"
            "MULTI" -> "MULTI"
            "VO" -> "VO"
            else -> u
        }
    }

    private fun languageFromPrefix(p: String): String? = when (p.uppercase()) {
        "FR", "FRA", "FRENCH" -> "VF"
        else -> null
    }

    /** Clé de rapprochement chaîne ↔ EPG quand `tvg-id` est absent : "TF1 HD" → "tf1". */
    fun channelKey(name: String): String {
        val withoutPrefix = prefixRegex.find(name)?.let { name.substring(it.range.last + 1) } ?: name
        val noQuality = qualityRegex.replace(withoutPrefix, " ")
        val ascii = Normalizer.normalize(noQuality, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return ascii.lowercase().replace(Regex("[^a-z0-9+]"), "")
    }
}
