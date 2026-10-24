package com.sokoul.iptv.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class M3uParserTest {

    private fun parse(text: String): Pair<M3uHeader?, List<M3uEntry>> {
        var header: M3uHeader? = null
        val entries = ArrayList<M3uEntry>()
        M3uParser.parse(BufferedReader(StringReader(text)), { header = it }) { entries += it }
        return header to entries
    }

    @Test
    fun `parse header, attributes, names with commas and options`() {
        val (header, entries) = parse(
            """
            ﻿#EXTM3U url-tvg="http://epg.example/a.xml.gz,http://epg.example/b.xml" catchup="shift"
            #EXTINF:-1 tvg-id="TF1.fr" tvg-name="TF1" tvg-logo="http://l/tf1.png" group-title="France, TNT",TF1 HD, le direct
            #EXTVLCOPT:http-user-agent=MyAgent/1.0
            #EXTVLCOPT:http-referrer=http://ref.example/
            http://srv.example/live/u/p/1.ts

            #EXTINF:0,Sans attributs
            #EXTGRP:Divers
            http://srv.example/2.m3u8|User-Agent=Kodi%2F20&Referer=http://r
            #EXTINF:-1 tvg-chno=7 catchup="default" catchup-source="?utc={utc}" catchup-days="3",Arte
            http://srv.example/arte.m3u8
            """.trimIndent()
        )
        assertEquals(listOf("http://epg.example/a.xml.gz", "http://epg.example/b.xml"), header!!.epgUrls)
        assertEquals(3, entries.size)

        val tf1 = entries[0]
        assertEquals("TF1 HD, le direct", tf1.name)
        assertEquals("TF1.fr", tf1.tvgId)
        assertEquals("France, TNT", tf1.group)
        assertEquals("MyAgent/1.0", tf1.userAgent)
        assertEquals("http://ref.example/", tf1.referrer)

        val second = entries[1]
        assertEquals("Sans attributs", second.name)
        assertEquals("Divers", second.group)
        assertEquals("http://srv.example/2.m3u8", second.url)
        assertEquals("Kodi/20", second.userAgent)
        assertNull(second.tvgId)

        val arte = entries[2]
        assertEquals(7, arte.channelNumber)
        assertEquals("default", arte.catchup)
        assertEquals(3, arte.catchupDays)
        assertNull(arte.userAgent) // les options ne fuient pas d'une entrée à l'autre
    }

    @Test
    fun `server url normalisation and xtream detection`() {
        assertEquals("http://host.tv:8080", ServerUrl.normalize(" host.tv:8080/ "))
        assertEquals("https://host.tv", ServerUrl.normalize("https://host.tv/player_api.php?username=a"))
        assertEquals("http://h:80/sub", ServerUrl.normalize("http://h:80/sub/"))
        val creds = ServerUrl.extractXtream("http://h.tv:8080/get.php?username=jo%40e&password=p%26w&type=m3u_plus&output=ts")!!
        assertEquals(XtreamCredentials("http://h.tv:8080", "jo@e", "p&w"), creds)
        assertNull(ServerUrl.extractXtream("http://h.tv/playlist.m3u"))
    }
}
