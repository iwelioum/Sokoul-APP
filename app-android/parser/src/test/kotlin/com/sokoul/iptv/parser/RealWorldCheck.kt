package com.sokoul.iptv.parser

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Vérification ponctuelle sur des sources publiques réelles (lancée avec -DrealWorld=true). */
class RealWorldCheck {

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 120_000; setRequestProperty("User-Agent", "SokoulTV-test")
    }.inputStream

    @Test
    fun realM3uAndXmltv() {
        assumeTrue(System.getProperty("realWorld") == "true")
        var header: M3uHeader? = null
        val entries = ArrayList<M3uEntry>()
        open("https://iptv-org.github.io/iptv/countries/fr.m3u").bufferedReader().use { r ->
            M3uParser.parse(r, { header = it }) { entries += it }
        }
        val kinds = entries.groupingBy { M3uClassifier.classify(it) }.eachCount()
        println("M3U: ${entries.size} entrées, types=$kinds, epg=${header?.epgUrls}")
        println("Exemples: " + entries.take(5).joinToString { "${it.name} [${it.group}] tvg=${it.tvgId} key=${TitleTagger.channelKey(it.name)}" })
        check(entries.size > 50)

        val keys = entries.map { TitleTagger.channelKey(it.tvgName ?: it.name) }.toSet()
        var channels = 0; var programmes = 0; var matchedByName = 0
        val stream = open("https://xmltvfr.fr/xmltv/xmltv.xml.gz").buffered()
        val parser = KXmlParser().apply {
            setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
            setInput(GZIPInputStream(stream), null)
        }
        var first: XmltvProgramme? = null
        XmltvParser.parse(parser, "fr", { ch ->
            channels++
            if (ch.names.any { TitleTagger.channelKey(it) in keys }) matchedByName++
        }) { p -> programmes++; if (first == null) first = p }
        println("XMLTV: $channels chaînes, $programmes programmes, $matchedByName chaînes rapprochées par nom; 1er=${first?.title} ${first?.episode}")
        check(programmes > 1000)
    }
}
