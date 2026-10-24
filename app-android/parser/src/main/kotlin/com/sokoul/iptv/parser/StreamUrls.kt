package com.sokoul.iptv.parser

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Construction des URLs de lecture au format Xtream Codes. */
object XtreamUrls {
    private fun e(s: String) = ServerUrl.encodeSegment(s)

    fun api(server: String, user: String, pass: String, action: String? = null, extra: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder("$server/player_api.php?username=${ServerUrl.encodeQuery(user)}&password=${ServerUrl.encodeQuery(pass)}")
        if (action != null) sb.append("&action=").append(action)
        extra.forEach { (k, v) -> sb.append('&').append(k).append('=').append(ServerUrl.encodeQuery(v)) }
        return sb.toString()
    }

    fun xmltv(server: String, user: String, pass: String) =
        "$server/xmltv.php?username=${ServerUrl.encodeQuery(user)}&password=${ServerUrl.encodeQuery(pass)}"

    fun live(server: String, user: String, pass: String, streamId: Long, ext: String = "ts") =
        "$server/live/${e(user)}/${e(pass)}/$streamId.$ext"

    fun movie(server: String, user: String, pass: String, streamId: Long, ext: String?) =
        "$server/movie/${e(user)}/${e(pass)}/$streamId.${ext?.takeIf { it.isNotBlank() } ?: "mp4"}"

    fun episode(server: String, user: String, pass: String, episodeId: String, ext: String?) =
        "$server/series/${e(user)}/${e(pass)}/$episodeId.${ext?.takeIf { it.isNotBlank() } ?: "mp4"}"

    /**
     * Replay Xtream : `/timeshift/u/p/{durée en min}/{AAAA-MM-JJ:HH-MM}/{id}.ts`.
     * L'heure de début doit être exprimée dans le fuseau du serveur.
     */
    fun timeshift(
        server: String, user: String, pass: String, streamId: Long,
        startMs: Long, durationMin: Int, serverTz: TimeZone = TimeZone.getDefault(),
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = serverTz }
        return "$server/timeshift/${e(user)}/${e(pass)}/$durationMin/${fmt.format(startMs)}/$streamId.ts"
    }
}

/** Catch-up des playlists M3U (attributs `catchup`, `catchup-source`), compatible Kodi/TiviMate. */
object Catchup {

    private val xtreamLive = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.(\w+))?$""")
    private val flussonicHls = Regex("""^(.*)/(video|index|mono)(?:-[^/]*)?\.m3u8(\?.*)?$""")
    private val flussonicTs = Regex("""^(.*)/mpegts(\?.*)?$""")

    fun build(type: String?, source: String?, url: String, startMs: Long, endMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        val t = type?.trim()?.lowercase()
        val template: String = when (t) {
            null, "" -> if (source.isNullOrBlank()) return null else defaultTemplate(url, source)
            "default" -> if (source.isNullOrBlank()) return null else defaultTemplate(url, source)
            "append" -> url + (source ?: return null)
            "shift", "timeshift" -> url + (if (url.contains('?')) "&" else "?") + "utc={utc}&lutc={lutc}"
            "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> flussonic(url) ?: return null
            "xc" -> xc(url) ?: return null
            else -> return null
        }
        return fill(template, startMs, endMs, nowMs)
    }

    private fun defaultTemplate(url: String, source: String): String = when {
        source.startsWith("http", true) -> source
        source.startsWith("?") || source.startsWith("&") ->
            url + (if (url.contains('?')) "&" + source.drop(1) else "?" + source.drop(1))
        else -> url + source
    }

    private fun flussonic(url: String): String? {
        flussonicHls.matchEntire(url)?.let { m ->
            return "${m.groupValues[1]}/${m.groupValues[2]}-{utc}-{duration}.m3u8${m.groupValues[3]}"
        }
        flussonicTs.matchEntire(url)?.let { m ->
            return "${m.groupValues[1]}/timeshift_abs-{utc}.ts${m.groupValues[2]}"
        }
        return null
    }

    private fun xc(url: String): String? {
        val m = xtreamLive.matchEntire(url) ?: return null
        val (host, user, pass, id) = m.destructured
        return "$host/timeshift/$user/$pass/{duration:60}/{Y}-{m}-{d}:{H}-{M}/$id.ts"
    }

    /** Remplace les variables `{utc}`, `${start}`, `{Y}`… par leurs valeurs. */
    fun fill(template: String, startMs: Long, endMs: Long, nowMs: Long): String {
        val start = startMs / 1000
        val end = endMs / 1000
        val now = nowMs / 1000
        val duration = (end - start).coerceAtLeast(0)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = startMs }
        fun two(v: Int) = v.toString().padStart(2, '0')
        var out = template
        val simple = mapOf(
            "utc" to start, "start" to start, "timestamp" to now, "lutc" to now, "now" to now,
            "utcend" to end, "end" to end, "duration" to duration,
        )
        simple.forEach { (k, v) ->
            out = out.replace("{$k}", v.toString()).replace("\${$k}", v.toString())
        }
        out = out.replace("{Y}", cal.get(java.util.Calendar.YEAR).toString())
            .replace("{m}", two(cal.get(java.util.Calendar.MONTH) + 1))
            .replace("{d}", two(cal.get(java.util.Calendar.DAY_OF_MONTH)))
            .replace("{H}", two(cal.get(java.util.Calendar.HOUR_OF_DAY)))
            .replace("{M}", two(cal.get(java.util.Calendar.MINUTE)))
            .replace("{S}", two(cal.get(java.util.Calendar.SECOND)))
        out = Regex("""\{duration:(\d+)\}""").replace(out) { (duration / it.groupValues[1].toLong().coerceAtLeast(1)).toString() }
        out = Regex("""\{offset:(\d+)\}""").replace(out) { ((now - start) / it.groupValues[1].toLong().coerceAtLeast(1)).toString() }
        return out
    }
}
