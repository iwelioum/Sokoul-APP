package com.sokoul.iptv.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleTaggerTest {

    @Test
    fun `clean typical provider names`() {
        val a = TitleTagger.parse("FR| Le Parrain (1972) [VFF] 4K")
        assertEquals("Le Parrain", a.clean)
        assertEquals(1972, a.year)
        assertEquals("VF", a.language)
        assertEquals("4K", a.quality)
        assertEquals("FR", a.prefix)

        val b = TitleTagger.parse("|EN| Dune: Part Two - 2024 MULTI FHD")
        assertEquals("Dune: Part Two", b.clean)
        assertEquals(2024, b.year)
        assertEquals("MULTI", b.language)
        assertEquals("FHD", b.quality)
    }

    @Test
    fun `does not damage legitimate titles`() {
        assertEquals("NCIS: Los Angeles", TitleTagger.parse("NCIS: Los Angeles").clean)
        assertEquals("CSI: Miami", TitleTagger.parse("CSI: Miami").clean)
        val y = TitleTagger.parse("2012")
        assertEquals("2012", y.clean)
        assertNull(y.year)
        assertEquals("Hard Day", TitleTagger.parse("Hard Day").clean)
    }

    @Test
    fun `episode references`() {
        assertEquals(EpisodeRef("Breaking Bad", 2, 5), TitleTagger.episode("FR - Breaking Bad S02 E05"))
        assertEquals(EpisodeRef("Lupin", 1, 3), TitleTagger.episode("Lupin.S01E03.VF"))
        assertNull(TitleTagger.episode("TF1 HD"))
    }

    @Test
    fun `channel keys for epg matching`() {
        assertEquals("tf1", TitleTagger.channelKey("FR| TF1 HD"))
        assertEquals("france2", TitleTagger.channelKey("France 2 FHD"))
        assertEquals("canal+", TitleTagger.channelKey("Canal+ 4K"))
        assertEquals("arte", TitleTagger.channelKey("Arté"))
    }
}
