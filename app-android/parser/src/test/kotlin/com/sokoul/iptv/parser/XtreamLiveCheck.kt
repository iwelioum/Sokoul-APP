package com.sokoul.iptv.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Vérification contre un vrai serveur Xtream. Identifiants lus dans l'environnement
 * (SOKOUL_XT_URL / SOKOUL_XT_USER / SOKOUL_XT_PASS), jamais dans le code.
 */
class XtreamLiveCheck {
    private val server = System.getenv("SOKOUL_XT_URL")?.let(ServerUrl::normalize)
    private val user = System.getenv("SOKOUL_XT_USER")
    private val pass = System.getenv("SOKOUL_XT_PASS")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun conn(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000; readTimeout = 180_000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", "SokoulTV-test")
    }

    private fun get(url: String): JsonElement = conn(url).inputStream.use { json.parseToJsonElement(it.readBytes().decodeToString()) }

    private fun api(action: String, extra: Map<String, String> = emptyMap()) =
        get(XtreamUrls.api(server!!, user!!, pass!!, action, extra))

    private fun hide(s: String) = s.replace(user!!, "<u>").replace(pass!!, "<p>")

    @Test
    fun fullCheck() {
        assumeTrue(server != null && user != null && pass != null)
        val acc = XtreamParse.account(get(XtreamUrls.api(server!!, user!!, pass!!)))
        println("Compte: actif=${acc.isActive} max=${acc.maxConnections} tz=${acc.serverTimezone}")
        check(acc.isActive)

        for ((label, catAction, listAction) in listOf(
            Triple("LIVE", "get_live_categories", "get_live_streams"),
            Triple("VOD", "get_vod_categories", "get_vod_streams"),
            Triple("SERIES", "get_series_categories", "get_series"),
        )) {
            val t0 = System.currentTimeMillis()
            val cats = (api(catAction) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(XtreamParse::category) }
            val raw = (api(listAction) as? JsonArray).orEmpty()
            val parsed = raw.mapNotNull { o ->
                val obj = o as? JsonObject ?: return@mapNotNull null
                when (label) { "LIVE" -> XtreamParse.live(obj); "VOD" -> XtreamParse.vod(obj); else -> XtreamParse.series(obj) }
            }
            val catIds = cats.map { it.id }.toSet()
            val orphan = parsed.count { p ->
                val c = when (p) { is XtLive -> p.categoryId; is XtVod -> p.categoryId; is XtSeries -> p.categoryId; else -> null }
                c == null || c !in catIds
            }
            println("$label: ${cats.size} catégories, ${raw.size} bruts, ${parsed.size} parsés, $orphan sans catégorie connue, ${System.currentTimeMillis() - t0} ms")
            println("  catégories ex.: " + cats.take(8).joinToString { it.name })
            when (label) {
                "LIVE" -> {
                    val lives = parsed.filterIsInstance<XtLive>()
                    println("  epg_channel_id renseigné: ${lives.count { !it.epgChannelId.isNullOrBlank() }} ; archive: ${lives.count { it.hasArchive }}")
                    println("  ex.: " + lives.take(5).joinToString { "${it.name} epg=${it.epgChannelId}" })
                    val ch = lives.firstOrNull { !it.epgChannelId.isNullOrBlank() } ?: lives.first()
                    val epg = XtreamParse.shortEpg(api("get_short_epg", mapOf("stream_id" to ch.streamId.toString(), "limit" to "4")))
                    println("  short_epg(${ch.name}): " + epg.joinToString { "${it.title} @${it.start}" })
                    // Premiers octets d'un flux live (connexion fermée aussitôt : 1 connexion max)
                    val c = conn(XtreamUrls.live(server, user, pass, ch.streamId, "ts"))
                    c.readTimeout = 15_000
                    try {
                        val code = c.responseCode
                        val head = c.inputStream.use { s -> ByteArray(376).also { readFully(s, it) } }
                        println("  flux TS: HTTP $code type=${c.contentType} sync=${head[0] == 0x47.toByte() && head[188] == 0x47.toByte()} final=${hide(c.url.toString())}")
                    } catch (e: Exception) {
                        println("  flux TS: échec ${e.javaClass.simpleName} ${e.message?.let(::hide)}")
                    } finally { c.disconnect() }
                    xmltv(lives)
                }
                "VOD" -> {
                    val v = parsed.filterIsInstance<XtVod>()
                    println("  extensions: " + v.groupingBy { it.extension }.eachCount())
                    println("  ex.: " + v.take(5).joinToString { "${it.name} → ${TitleTagger.parse(it.name)}" })
                    v.firstOrNull()?.let { println("  vod_info: " + XtreamParse.vodInfo(api("get_vod_info", mapOf("vod_id" to it.streamId.toString())))) }
                }
                else -> {
                    val s = parsed.filterIsInstance<XtSeries>()
                    s.firstOrNull()?.let { first ->
                        val info = XtreamParse.seriesInfo(api("get_series_info", mapOf("series_id" to first.seriesId.toString())), first.seriesId)
                        println("  series_info(${first.name}): ${info.episodes.size} épisodes, saisons=${info.episodes.map { it.season }.distinct()}, ex=${info.episodes.take(2)}")
                    }
                }
            }
        }

        // Lien M3U get.php
        val entries = ArrayList<M3uEntry>()
        val t0 = System.currentTimeMillis()
        conn("$server/get.php?username=$user&password=$pass&type=m3u_plus&output=mpegts").inputStream.bufferedReader().use { r ->
            M3uParser.parse(r, {}) { entries += it }
        }
        println("M3U: ${entries.size} entrées en ${System.currentTimeMillis() - t0} ms, types=" + entries.groupingBy { M3uClassifier.classify(it) }.eachCount())
        println("  extractXtream=" + (ServerUrl.extractXtream("$server/get.php?username=$user&password=$pass&type=m3u_plus") != null))
    }

    private fun xmltv(lives: List<XtLive>) {
        val ids = lives.mapNotNull { it.epgChannelId?.lowercase()?.substringBefore('@') }.toSet()
        val t0 = System.currentTimeMillis()
        val c = conn(XtreamUrls.xmltv(server!!, user!!, pass!!))
        var input: InputStream = c.inputStream.buffered()
        input.mark(2); val b0 = input.read(); val b1 = input.read(); input.reset()
        if (b0 == 0x1f && b1 == 0x8b) input = GZIPInputStream(input)
        val parser = KXmlParser().apply {
            setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
            setInput(input, null)
        }
        var channels = 0; var programmes = 0; var matched = 0
        val progChannels = HashSet<String>()
        XmltvParser.parse(parser, "fr", { ch -> channels++; if (ch.id.lowercase().substringBefore('@') in ids) matched++ }) { p ->
            programmes++; progChannels += p.channel.lowercase().substringBefore('@')
        }
        val covered = lives.count { it.epgChannelId?.lowercase()?.substringBefore('@') in progChannels }
        println("XMLTV: $channels chaînes, $programmes programmes, $matched chaînes rapprochées, $covered/${lives.size} chaînes live couvertes, ${System.currentTimeMillis() - t0} ms")
    }

    private fun readFully(s: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) { val n = s.read(buf, off, buf.size - off); if (n < 0) break; off += n }
    }
}
