package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.PlaybackSources
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.SourceState
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.ui.components.SourceLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sources de lecture typées : adaptateur IPTV, libellés calculés par l'interface, état des cartes. */
class PlaybackSourceTest {
    private fun v(quality: String?, language: String?, name: String = "Film") = VodVersion("1", name, language, quality, "c", "[FR] FILMS")

    @Test fun `adaptateur IPTV - etiquettes du parseur en valeurs typees`() {
        val s = PlaybackSources.fromIptv(v("FHD", "VF"))
        assertEquals(SourceOrigin.IPTV, s.origin)
        assertEquals(VideoQuality.FHD_1080, s.quality)
        assertEquals(AudioLanguage.VF, s.language)
        assertEquals("[FR] FILMS", s.group)
        assertEquals(SourceState.AVAILABLE, s.state)

        assertEquals(VideoQuality.UHD_4K, PlaybackSources.fromIptv(v("4K", "MULTI")).quality)
        val hdr = PlaybackSources.fromIptv(v("HDR", "VOSTFR"))
        assertEquals(HdrFormat.HDR10, hdr.hdr)
        assertNull(hdr.quality)
        // un codec n'est pas une définition
        assertNull(PlaybackSources.fromIptv(v("HEVC", null)).quality)
        // langue inconnue du parseur : pas d'exception, aucune langue inventée
        assertNull(PlaybackSources.fromIptv(v(null, "XX")).language)
    }

    @Test fun `libelles - calcules a partir des valeurs typees`() {
        assertEquals("VF · 1080p", SourceLabels.text(PlaybackSources.fromIptv(v("FHD", "VF"))))
        assertEquals("MULTI · 4K", SourceLabels.text(PlaybackSources.fromIptv(v("4K", "MULTI"))))
        assertEquals("VOSTFR · HDR", SourceLabels.text(PlaybackSources.fromIptv(v("HDR", "VOSTFR"))))
        assertEquals("Version", SourceLabels.text(PlaybackSource("x", SourceOrigin.IPTV)))
        // langue inconnue : le pays du fournisseur
        assertEquals("AR · 720p", SourceLabels.text(PlaybackSources.fromIptv(v("HD", null, name = "AR - Le Film"))))
        assertEquals("IPTV", SourceLabels.origin(SourceOrigin.IPTV))
        assertEquals("RD", SourceLabels.origin(SourceOrigin.DEBRID))
        assertEquals("Dolby Vision", SourceLabels.hdr(HdrFormat.DOLBY_VISION))
        // chaque valeur a un libellé (un ajout d'enum sans libellé casserait la compilation, un libellé vide ce test)
        VideoQuality.entries.forEach { assertTrue(SourceLabels.quality(it).isNotBlank()) }
        assertNull(SourceLabels.state(SourceState.AVAILABLE))
        assertEquals("HORS CATALOGUE", SourceLabels.state(SourceState.UNAVAILABLE))
    }

    @Test fun `cartes - une oeuvre sans source est indisponible`() {
        val w = Work(ContentType.MOVIE, "1", "k", "Film", 2020, null, quality = "4K")
        assertEquals(SourceState.AVAILABLE, PlaybackSources.summary(w).state)
        assertEquals(VideoQuality.UHD_4K, PlaybackSources.summary(w).quality)
        assertEquals(SourceState.UNAVAILABLE, PlaybackSources.summary(w.copy(available = false)).state)
    }

    @Test fun `aucun libelle de source code en dur hors de SourceBadges`() {
        // les écrans passent par SourceLabels : « HORS CATALOGUE » et « 1080p » ne s'écrivent qu'à un seul endroit
        val ui = File("src/main/java/com/sokoul/iptv/ui")
        val offenders = ui.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "SourceBadges.kt" }
            .filter { f -> f.readText().let { "\"HORS CATALOGUE\"" in it || "\"1080p\"" in it } }.map { it.name }.toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
