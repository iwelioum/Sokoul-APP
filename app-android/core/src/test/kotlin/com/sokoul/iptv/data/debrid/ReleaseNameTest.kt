package com.sokoul.iptv.data.debrid

import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Langue et définition lues dans de vrais noms de versions (spec `release-name-parsing`). */
class ReleaseNameTest {
    @Test fun `DUAL sans francais - langue inconnue (version turque et anglaise)`() {
        // nom réel classé « MULTI 4K » sur le PC : le film démarrait en turc
        val r = ReleaseName.parse("Inception.2010.UHD.Bluray.1080p.DUAL.x264-HDM.mkv")
        assertNull(r.language)
        assertEquals(VideoQuality.FHD_1080, r.quality)
    }

    @Test fun `DUAL avec le francais - MULTI`() {
        assertEquals(AudioLanguage.MULTI, ReleaseName.parse("Film.2020.1080p.DUAL.TRUEFRENCH.x264.mkv").language)
    }

    @Test fun `resolution ecrite avant UHD, UHD seul vaut 4K`() {
        assertEquals(VideoQuality.FHD_1080, ReleaseName.parse("Film.2020.UHD.BluRay.1080p.x265.mkv").quality)
        assertEquals(VideoQuality.UHD_4K, ReleaseName.parse("Film.2020.MULTi.UHD.BluRay.x265.mkv").quality)
        assertEquals(VideoQuality.UHD_4K, ReleaseName.parse("Dune.2021.MULTi.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.mkv").quality)
        assertEquals(VideoQuality.HD_720, ReleaseName.parse("Film.2020.4K.Upscale.720p.WEB.mkv").quality)
    }

    @Test fun `FRE et FRA comptent comme du francais`() {
        assertEquals(
            AudioLanguage.MULTI,
            ReleaseName.parse("Inception.2010.1080p.BluRay.ENG.LATINO.CASTELLANO.HINDI.ITA.GER.FRE.x265.mkv").language,
        )
        assertEquals(AudioLanguage.VF, ReleaseName.parse("Film.2020.FRA.1080p.WEB.x264.mkv").language)
        // vraies MULTI et VF : inchangées
        assertEquals(AudioLanguage.MULTI, ReleaseName.parse("Inception.2010.MULTi.VFF.2160p.BluRay.HDR10.x265-QTZ.mkv").language)
        assertEquals(AudioLanguage.VF, ReleaseName.parse("Film.2020.TRUEFRENCH.1080p.BluRay.x264.mkv").language)
    }
}
