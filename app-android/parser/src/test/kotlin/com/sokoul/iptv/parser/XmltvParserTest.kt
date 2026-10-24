package com.sokoul.iptv.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class XmltvParserTest {

    @Test
    fun `parse channels and programmes with timezones and languages`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="TF1.fr"><display-name>TF1</display-name><display-name>TF1 HD</display-name><icon src="http://i/tf1.png"/></channel>
              <programme start="20240101200000 +0100" stop="20240101210000 +0100" channel="TF1.fr">
                <title lang="en">News</title>
                <title lang="fr">Le 20h</title>
                <desc lang="fr">Journal &amp; météo</desc>
                <category>Info</category>
                <episode-num system="xmltv_ns">1.4.</episode-num>
                <rating><value>TP</value></rating>
              </programme>
              <programme start="20240101210000" stop="20240101200000" channel="TF1.fr"><title>Invalide</title></programme>
            </tv>
        """.trimIndent()
        val parser = KXmlParser().apply { setInput(StringReader(xml)) }
        val channels = ArrayList<XmltvChannel>()
        val programmes = ArrayList<XmltvProgramme>()
        XmltvParser.parse(parser, "fr", channels::add, programmes::add)

        assertEquals(1, channels.size)
        assertEquals(listOf("TF1", "TF1 HD"), channels[0].names)
        assertEquals("http://i/tf1.png", channels[0].icon)

        assertEquals(1, programmes.size) // le programme avec stop < start est ignoré
        val p = programmes[0]
        assertEquals("Le 20h", p.title)
        assertEquals("Journal & météo", p.description)
        assertEquals("Info", p.category)
        assertEquals("S02E05", p.episode)
        assertEquals(1704135600000L, p.start) // 2024-01-01T19:00:00Z
        assertEquals(3_600_000L, p.stop - p.start)
    }

    @Test
    fun `time parsing variants`() {
        assertEquals(1704135600000L, XmltvParser.parseTime("20240101190000"))
        assertEquals(1704135600000L, XmltvParser.parseTime("20240101140000 -0500"))
        assertEquals(1704135600000L, XmltvParser.parseTime("202401011900"))
        assertNull(XmltvParser.parseTime("garbage"))
    }
}
