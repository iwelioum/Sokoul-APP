package com.sokoul.iptv.parser

import java.io.BufferedReader

data class M3uHeader(val attrs: Map<String, String>) {
    /** URLs EPG déclarées dans l'en-tête (`url-tvg`, `x-tvg-url`), séparées par des virgules. */
    val epgUrls: List<String>
        get() = listOfNotNull(attrs["url-tvg"], attrs["x-tvg-url"], attrs["tvg-url"])
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    val catchup: String? get() = attrs["catchup"]
    val catchupSource: String? get() = attrs["catchup-source"]
    val catchupDays: Int? get() = attrs["catchup-days"]?.toIntOrNull()
}

data class M3uEntry(
    val name: String,
    val url: String,
    val attrs: Map<String, String>,
    val group: String?,
    val userAgent: String?,
    val referrer: String?,
    val headers: Map<String, String>,
) {
    val tvgId: String? get() = attrs["tvg-id"]?.trim()?.takeIf { it.isNotEmpty() }
    val tvgName: String? get() = attrs["tvg-name"]?.trim()?.takeIf { it.isNotEmpty() }
    val logo: String? get() = (attrs["tvg-logo"] ?: attrs["logo"])?.trim()?.takeIf { it.isNotEmpty() }
    val channelNumber: Int? get() = (attrs["tvg-chno"] ?: attrs["channel-number"])?.trim()?.toIntOrNull()
    val catchup: String? get() = attrs["catchup"] ?: attrs["catchup-type"]
    val catchupSource: String? get() = attrs["catchup-source"]
    val catchupDays: Int? get() = (attrs["catchup-days"] ?: attrs["tvg-rec"] ?: attrs["timeshift"])?.trim()?.toIntOrNull()
}

/**
 * Parser M3U/M3U8 en flux : il ne garde jamais toute la playlist en mémoire,
 * ce qui permet d'avaler des listes de plusieurs centaines de milliers d'entrées.
 */
object M3uParser {

    private val attrRegex = Regex("""([A-Za-z0-9_\-:.]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

    fun parse(
        reader: BufferedReader,
        onHeader: (M3uHeader) -> Unit = {},
        onEntry: (M3uEntry) -> Unit,
    ): Int {
        var count = 0
        var first = true
        var pendingInfo: String? = null
        var pendingGroup: String? = null
        var userAgent: String? = null
        var referrer: String? = null
        val headers = LinkedHashMap<String, String>()

        fun reset() {
            pendingInfo = null; pendingGroup = null; userAgent = null; referrer = null; headers.clear()
        }

        while (true) {
            var line = reader.readLine() ?: break
            if (first) {
                line = line.removePrefix("﻿")
                first = false
            }
            line = line.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> onHeader(M3uHeader(parseAttrs(line.substring(7))))
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    reset()
                    pendingInfo = line.substringAfter(':', "")
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> pendingGroup = line.substring(8).trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substring(11)
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> userAgent = value
                        "http-referrer", "http-referer" -> referrer = value
                    }
                }
                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val prop = line.substring(10)
                    if (prop.startsWith("inputstream.adaptive.stream_headers", true) ||
                        prop.startsWith("inputstream.adaptive.common_headers", true)
                    ) {
                        parseHeaderString(prop.substringAfter('=', "")).forEach { (k, v) -> headers[k] = v }
                    }
                }
                line.startsWith("#") -> Unit // commentaire ou directive inconnue
                else -> {
                    val info = pendingInfo
                    if (info != null) {
                        val (rawUrl, pipeHeaders) = splitPipeHeaders(line)
                        val allHeaders = LinkedHashMap(headers).apply { putAll(pipeHeaders) }
                        val ua = userAgent ?: allHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                        val ref = referrer ?: allHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value
                        val entry = buildEntry(info, rawUrl, pendingGroup, ua, ref, allHeaders)
                        if (entry != null) {
                            onEntry(entry)
                            count++
                        }
                    }
                    reset()
                }
            }
        }
        return count
    }

    private fun buildEntry(
        info: String,
        url: String,
        extGroup: String?,
        ua: String?,
        ref: String?,
        headers: Map<String, String>,
    ): M3uEntry? {
        if (url.isBlank()) return null
        val commaIndex = findNameSeparator(info)
        val attrPart = if (commaIndex >= 0) info.substring(0, commaIndex) else info
        var name = if (commaIndex >= 0) info.substring(commaIndex + 1).trim() else ""
        val attrs = parseAttrs(attrPart)
        if (name.isEmpty()) name = attrs["tvg-name"] ?: url.substringAfterLast('/')
        val group = attrs["group-title"]?.trim()?.takeIf { it.isNotEmpty() } ?: extGroup?.takeIf { it.isNotEmpty() }
        return M3uEntry(name, url.trim(), attrs, group, ua, ref, headers)
    }

    /** Première virgule hors guillemets : tout ce qui suit est le nom (qui peut contenir des virgules). */
    private fun findNameSeparator(info: String): Int {
        var quote: Char? = null
        for (i in info.indices) {
            val c = info[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                // un guillemet n'ouvre une valeur qu'après un '='
                if (i > 0 && info[i - 1] == '=') quote = c
            } else if (c == ',') {
                return i
            }
        }
        return -1
    }

    internal fun parseAttrs(s: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (m in attrRegex.findAll(s)) {
            val key = m.groupValues[1].lowercase()
            val value = m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            map[key] = value
        }
        return map
    }

    /** Syntaxe Kodi : `http://url|User-Agent=xxx&Referer=yyy`. */
    private fun splitPipeHeaders(line: String): Pair<String, Map<String, String>> {
        val idx = line.indexOf('|')
        if (idx < 0) return line to emptyMap()
        return line.substring(0, idx) to parseHeaderString(line.substring(idx + 1))
    }

    private fun parseHeaderString(s: String): Map<String, String> =
        s.split('&').mapNotNull {
            val k = it.substringBefore('=', "").trim()
            val v = it.substringAfter('=', "").trim()
            if (k.isEmpty()) null else k to java.net.URLDecoder.decode(v, "UTF-8")
        }.toMap()
}
