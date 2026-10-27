package com.sokoul.iptv

import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.data.debrid.Release
import com.sokoul.iptv.data.debrid.RipSource
import com.sokoul.iptv.data.repo.VodQuality
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.ui.screens.player.PlayerSource
import com.sokoul.iptv.ui.screens.player.PlayerSources
import com.sokoul.iptv.ui.screens.player.QualityBasis
import com.sokoul.iptv.ui.screens.player.audioPillText
import com.sokoul.iptv.ui.screens.player.subtitlePillText
import com.sokoul.iptv.ui.screens.player.TrackOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sources du lecteur : origine en clair, qualité mesurée ou annoncée, langues des autres fichiers. */
class PlayerSourcesTest {
    private fun src(id: String, lang: AudioLanguage?, q: VideoQuality?, origin: SourceOrigin = SourceOrigin.IPTV, current: Boolean = false, hdr: String? = null) =
        PlayerSource(id, origin, lang, quality = q, hdr = hdr, where = "x", current = current)

    @Test fun `version IPTV avec categorie et qualite d apres le nom`() {
        val s = PlayerSources.fromIptv(VodVersion("m1", "FR - Dune (2021) 4K HEVC", "VF", "4K", "c1", categoryName = " FILMS 4K FR "), current = true)
        assertEquals(AudioLanguage.VF, s.language)
        assertEquals(VideoQuality.UHD_4K, s.quality)
        assertEquals("HEVC", s.codec)
        assertEquals("Abonnement IPTV · « FILMS 4K FR »", s.where)
        assertEquals(QualityBasis.NAME, s.basis)
        assertEquals("FR - Dune (2021) 4K HEVC", s.fileName)
        assertTrue(s.current)
    }

    @Test fun `la mesure prime sur le nom`() {
        val measured = VodQuality(width = 1280, height = 720, videoCodec = "h264", hdr = null)
        val s = PlayerSources.fromIptv(VodVersion("m1", "Dune 4K", "VF", "4K", "c1", measured = measured), current = false)
        assertEquals("annoncée 4K, mesurée 720p", VideoQuality.HD_720, s.quality)
        assertEquals("H.264", s.codec)
        assertEquals(QualityBasis.MEASURED, s.basis)
        assertEquals("Abonnement IPTV", s.where)
    }

    @Test fun `version Real-Debrid avec release taille et etat`() {
        val r = Release(quality = VideoQuality.UHD_4K, hdr = HdrFormat.DOLBY_VISION, language = AudioLanguage.MULTI, source = RipSource.REMUX, codec = "HEVC", sizeBytes = 61_200_000_000, cached = true)
        val s = PlayerSources.fromDebrid(DebridStream("k", "Dune.2021.MULTi.2160p.REMUX.DV\n👤 12", null, "h", 0, null, r), current = false, id = "rd:k")
        assertEquals("Real-Debrid · Remux · 61,2 Go · prête", s.where)
        assertEquals("Dolby Vision", s.hdr)
        assertEquals("Dune.2021.MULTi.2160p.REMUX.DV", s.fileName)
        assertEquals(listOf("MULTI", "4K", "Dolby Vision", "HEVC"), PlayerSources.chips(s))
        assertEquals(QualityBasis.NAME, s.basis)
    }

    @Test fun `autres langues - la meilleure source de chaque langue absente du fichier en cours`() {
        val list = listOf(
            src("a", AudioLanguage.VF, VideoQuality.HD_720, current = true),
            src("b", AudioLanguage.VF, VideoQuality.UHD_4K),
            src("c", AudioLanguage.VO, VideoQuality.FHD_1080),
            src("rd:d", AudioLanguage.VO, VideoQuality.UHD_4K, SourceOrigin.DEBRID, hdr = "HDR10"),
            src("e", AudioLanguage.VOSTFR, VideoQuality.SD),
            src("f", null, VideoQuality.UHD_4K),
        )
        val others = PlayerSources.otherLanguages(list)
        assertEquals("VF déjà dans le fichier en cours ; langue inconnue ignorée", listOf("e", "rd:d"), others.map { it.id })
        val cur = list.first()
        assertTrue(PlayerSources.qualityDrop(list[4], cur))
        assertTrue(PlayerSources.qualityGain(list[3], cur))
        assertFalse(PlayerSources.qualityDrop(list[5].copy(quality = null), cur))
    }

    @Test fun `bandeau - mesure du flux puis langue et origine`() {
        val cur = src("a", AudioLanguage.VF, VideoQuality.UHD_4K, current = true)
        assertEquals("1080p · HEVC · HDR10 · VF · IPTV", PlayerSources.banner(1920, 1080, "HEVC", "HDR10", cur))
        assertEquals("avant la première image : la source", "4K · VF · IPTV", PlayerSources.banner(null, null, null, null, cur))
        assertNull(PlayerSources.banner(null, null, null, null, null))
    }

    @Test fun `libelles courts et identifiants d episode`() {
        assertEquals("VF · 1080p", PlayerSources.short(src("a", AudioLanguage.VF, VideoQuality.FHD_1080)))
        assertEquals("Source", PlayerSources.short(null))
        assertEquals("Français (VF)", PlayerSources.languageName(AudioLanguage.VF))
        assertEquals("Arabe (AR)", PlayerSources.languageName(null, "AR"))
        assertEquals("Italien (IT)", PlayerSources.languageName(null, "IT"))
        assertEquals("Langue : XX", PlayerSources.languageName(null, "XX"))
        val id = PlayerSources.episodeId("s9", "e|1")
        assertEquals("s9" to "e|1", PlayerSources.parseEpisodeId(id))
        assertNull(PlayerSources.parseEpisodeId("rd:x"))
    }

    @Test fun `pastilles audio et sous-titres`() {
        assertEquals("Français", audioPillText(listOf(TrackOption("Français · 5.1 · Dolby Digital+", null, 0, true), TrackOption("Anglais", null, 1, false))))
        assertEquals("piste unique non marquée", "Anglais", audioPillText(listOf(TrackOption("Anglais · stéréo", null, 0, false))))
        assertNull(audioPillText(emptyList()))
        assertEquals("piste sans langue : langue de la source", "EN", audioPillText(listOf(TrackOption("Piste principale · stéréo · AAC", null, 0, true)), "EN"))
        assertEquals("Désactivés", subtitlePillText(listOf(TrackOption("Désactivés", null, -1, true), TrackOption("Français", null, 0, false))))
        assertEquals("Français", subtitlePillText(listOf(TrackOption("Désactivés", null, -1, false), TrackOption("Français · SDH", null, 0, true))))
    }
}
