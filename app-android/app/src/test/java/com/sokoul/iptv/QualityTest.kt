package com.sokoul.iptv

import com.sokoul.iptv.data.repo.BestVersions
import com.sokoul.iptv.data.repo.VersionPicker
import com.sokoul.iptv.data.repo.VodQuality
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.ui.screens.vod.VersionGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Vraie qualité des versions : affichage, classement, regroupement par langue. */
class QualityTest {
    private val uhd = VodQuality(3840, 2160, "hevc", "HDR10", "eac3", 6, "fre", 18000, 7920)
    private val fhd = VodQuality(1920, 1080, "h264", null, "aac", 2, "fre", 5000, 7920)

    @Test fun `ligne de qualite lisible`() {
        assertEquals("4K · HDR10 · HEVC · 5.1 Dolby Digital+ · 2 h 12", uhd.line())
        assertEquals("1080p · H.264", fhd.imageLine())
        assertEquals("Stéréo AAC", fhd.soundLine())
        assertEquals("45 min", VodQuality.duration(2700))
    }

    @Test fun `la qualite mesuree prime sur l etiquette du nom`() {
        // nom « 4K » mais mesuré en 1080p ; nom sans étiquette mais mesuré en 4K
        val a = VodVersion("a", "Dune 4K", "VF", "4K", "c", measured = fhd)
        val b = VodVersion("b", "Dune", "VF", null, "c", measured = uhd)
        assertEquals(listOf("b", "a"), VersionPicker.order(listOf(a, b)).map { it.id })
        // la langue préférée reste le premier critère
        val vo = VodVersion("c", "Dune", "VO", null, "c", measured = uhd.copy(hdr = "Dolby Vision"))
        assertEquals("b", VersionPicker.order(listOf(vo, a, b)).first().id)
    }

    @Test fun `meilleure image et meilleur son seulement s ils se distinguent`() {
        assertEquals("u", BestVersions.image(mapOf("u" to uhd, "f" to fhd)))
        assertEquals("u", BestVersions.sound(mapOf("u" to uhd, "f" to fhd)))
        assertNull(BestVersions.image(mapOf("u" to uhd)))
        assertNull(BestVersions.image(mapOf("u" to uhd, "v" to uhd)))
    }

    @Test fun `explication des langues sous le titre de section`() {
        assertEquals("VO + sous-titres français intégrés", VersionGroups.explain("VOSTFR"))
        assertEquals("Version italienne, sans sous-titres français", VersionGroups.explain("IT"))
    }
}
