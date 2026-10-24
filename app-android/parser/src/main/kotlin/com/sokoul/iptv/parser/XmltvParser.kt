package com.sokoul.iptv.parser

import org.xmlpull.v1.XmlPullParser
import java.util.Calendar
import java.util.TimeZone

data class XmltvChannel(val id: String, val names: List<String>, val icon: String?)

data class XmltvProgramme(
    val channel: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
    val episode: String?,
    val icon: String?,
)

/**
 * Parser XMLTV en flux basé sur XmlPullParser : la mémoire reste constante
 * même pour des guides de plusieurs centaines de Mo.
 */
object XmltvParser {

    fun parse(
        p: XmlPullParser,
        preferredLang: String = "fr",
        onChannel: (XmltvChannel) -> Unit,
        onProgramme: (XmltvProgramme) -> Unit,
    ) {
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> readChannel(p)?.let(onChannel)
                    "programme" -> readProgramme(p, preferredLang)?.let(onProgramme)
                }
            }
            event = p.next()
        }
    }

    private fun readChannel(p: XmlPullParser): XmltvChannel? {
        val id = p.getAttributeValue(null, "id")?.trim()
        val names = ArrayList<String>(2)
        var icon: String? = null
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e != XmlPullParser.START_TAG) continue
            when (p.name) {
                "display-name" -> readText(p).trim().takeIf { it.isNotEmpty() }?.let(names::add)
                "icon" -> icon = p.getAttributeValue(null, "src")
                else -> skip(p)
            }
        }
        return if (id.isNullOrEmpty()) null else XmltvChannel(id, names, icon)
    }

    private fun readProgramme(p: XmlPullParser, lang: String): XmltvProgramme? {
        val channel = p.getAttributeValue(null, "channel")?.trim()
        val start = parseTime(p.getAttributeValue(null, "start"))
        val stop = parseTime(p.getAttributeValue(null, "stop"))
        var title: String? = null
        var titleLangMatch = false
        var subtitle: String? = null
        var desc: String? = null
        var descLangMatch = false
        var category: String? = null
        var episode: String? = null
        var icon: String? = null
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e != XmlPullParser.START_TAG) continue
            when (p.name) {
                "title" -> {
                    val match = p.getAttributeValue(null, "lang")?.startsWith(lang, true) == true
                    val text = readText(p).trim()
                    if (text.isNotEmpty() && (title == null || (match && !titleLangMatch))) {
                        title = text; titleLangMatch = match
                    }
                }
                "sub-title" -> subtitle = subtitle ?: readText(p).trim().ifEmpty { null }
                "desc" -> {
                    val match = p.getAttributeValue(null, "lang")?.startsWith(lang, true) == true
                    val text = readText(p).trim()
                    if (text.isNotEmpty() && (desc == null || (match && !descLangMatch))) {
                        desc = text; descLangMatch = match
                    }
                }
                "category" -> category = category ?: readText(p).trim().ifEmpty { null }
                "episode-num" -> {
                    val system = p.getAttributeValue(null, "system")
                    val text = readText(p).trim()
                    if (episode == null || system == "onscreen") episode = formatEpisode(system, text)
                }
                "icon" -> icon = icon ?: p.getAttributeValue(null, "src")
                else -> skip(p)
            }
        }
        if (channel.isNullOrEmpty() || start == null || stop == null || stop <= start) return null
        return XmltvProgramme(channel, start, stop, title ?: "Sans titre", subtitle, desc, category, episode, icon)
    }

    private fun formatEpisode(system: String?, text: String): String? {
        if (text.isEmpty()) return null
        if (system == "xmltv_ns") {
            // "saison.episode.partie" indexés à partir de 0
            val parts = text.split('.')
            val s = parts.getOrNull(0)?.substringBefore('/')?.trim()?.toIntOrNull()
            val e = parts.getOrNull(1)?.substringBefore('/')?.trim()?.toIntOrNull()
            if (s != null && e != null) return "S%02dE%02d".format(s + 1, e + 1)
            if (e != null) return "E%02d".format(e + 1)
            return null
        }
        return text
    }

    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.TEXT) sb.append(p.text)
        }
        return sb.toString()
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (p.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /**
     * Format XMLTV : `YYYYMMDDhhmmss +HHMM` (secondes, minutes et fuseau facultatifs).
     * Sans fuseau, l'heure est considérée en UTC.
     */
    fun parseTime(raw: String?): Long? {
        if (raw == null) return null
        val s = raw.trim()
        if (s.length < 8) return null
        val digits = s.takeWhile { it.isDigit() }
        if (digits.length < 8) return null
        fun part(from: Int, len: Int): Int = if (digits.length >= from + len) digits.substring(from, from + len).toInt() else 0
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(part(0, 4), part(4, 2) - 1, part(6, 2), part(8, 2), part(10, 2), part(12, 2))
        var millis = cal.timeInMillis
        val tz = s.substring(digits.length).trim()
        if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
            val h = tz.substring(1, 3).toIntOrNull() ?: 0
            val m = tz.substring(3, 5).toIntOrNull() ?: 0
            val offset = (h * 60 + m) * 60_000L
            millis -= if (tz[0] == '+') offset else -offset
        }
        return millis
    }
}
