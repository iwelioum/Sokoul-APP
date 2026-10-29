package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.VodProbeEntity
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.ui.screens.player.PlayerSource
import com.sokoul.iptv.ui.screens.player.PlayerSources
import com.sokoul.iptv.ui.screens.player.PlayerViewModel
import com.sokoul.iptv.ui.screens.player.SourceCatalog
import com.sokoul.iptv.ui.screens.player.SourceSearch
import com.sokoul.iptv.ui.screens.player.sourceCountLine
import com.sokoul.iptv.ui.screens.player.sourceDetail
import com.sokoul.iptv.ui.screens.player.versionLines
import com.sokoul.iptv.ui.screens.player.VersionLine
import com.sokoul.iptv.ui.screens.player.TrackOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Une seule liste de versions : toutes origines classées ensemble, doublons fusionnés, rien de perdu. */
class SourceCatalogTest {
    private val langs = listOf("VF", "MULTI", "VOSTFR", "VO")

    private fun src(
        id: String, lang: AudioLanguage?, q: VideoQuality?, origin: SourceOrigin = SourceOrigin.IPTV,
        current: Boolean = false, sameFile: String? = null, category: String? = null, failed: Boolean = false,
    ) = PlayerSource(id, origin, lang, quality = q, where = "x", current = current, sameFile = sameFile, category = category, failed = failed)

    @Test fun `meme fichier IPTV dans deux rubriques - une ligne, l autre rubrique et l identifiant gardes`() {
        val a = PlayerSources.fromIptv(VodVersion("m1", "FR - Dune (2021) 4K", "VF", "4K", "c1", categoryName = "FILMS 4K"), current = false)
        val b = PlayerSources.fromIptv(VodVersion("m2", "FR | Dune 2021 4K", "VF", "4K", "c2", categoryName = "FR 4K"), current = true)
        val merged = SourceCatalog.merge(listOf(a, b))
        assertEquals(1, merged.size)
        // la ligne en cours est gardée, l'autre rubrique devient « aussi dans »
        assertEquals("m2", merged[0].id)
        assertTrue(merged[0].current)
        assertEquals(listOf("FILMS 4K"), merged[0].alsoIn)
        assertEquals(listOf("m1"), merged[0].mergedIds)
        assertEquals("Abonnement IPTV · « FR 4K » · aussi dans « FILMS 4K » · qualité d'après le nom", sourceDetail(merged[0]))
    }

    @Test fun `meme release Real-Debrid renvoyee deux fois - une ligne, IPTV et RD jamais fusionnes`() {
        val r1 = src("rd:a", AudioLanguage.VF, VideoQuality.UHD_4K, SourceOrigin.DEBRID, sameFile = "dune 2021 2160p|60000")
        val r2 = src("rd:b", AudioLanguage.VF, VideoQuality.UHD_4K, SourceOrigin.DEBRID, sameFile = "dune 2021 2160p|60000")
        val iptv = src("m1", AudioLanguage.VF, VideoQuality.UHD_4K, sameFile = "dune 2021 2160p|60000")
        val merged = SourceCatalog.merge(listOf(r1, iptv, r2))
        assertEquals(listOf("rd:a", "m1"), merged.map { it.id })
        assertEquals(listOf("rd:b"), merged[0].mergedIds)
    }

    @Test fun `versions par langue puis qualite, toutes origines sous la meme version`() {
        val list = listOf(
            src("rd:vo4k", AudioLanguage.VO, VideoQuality.UHD_4K, SourceOrigin.DEBRID),
            src("vf720", AudioLanguage.VF, VideoQuality.HD_720, current = true),
            src("rd:vf1080", AudioLanguage.VF, VideoQuality.FHD_1080, SourceOrigin.DEBRID),
            src("vf1080", AudioLanguage.VF, VideoQuality.FHD_1080),
            src("it", null, VideoQuality.FHD_1080).copy(region = "IT"),
            src("unknown", null, null),
        )
        val g = SourceCatalog.group(list, langs)
        assertEquals(
            listOf("VF|FHD_1080", "VF|HD_720", "VO|UHD_4K", "R:IT|FHD_1080", "?|?"),
            g.map { SourceCatalog.versionKey(it.best) },
        )
        // VF 1080p : Real-Debrid et IPTV sous la même ligne, dans l'ordre reçu (classement neutre)
        assertEquals(listOf("rd:vf1080", "vf1080"), g[0].sources.map { it.id })
        assertEquals(listOf("vf1080"), g[0].others.map { it.id })
        assertTrue(g[1].isCurrent)
        assertEquals("vf720", g[1].shown.id)
        assertEquals(listOf(SourceOrigin.IPTV to 4, SourceOrigin.DEBRID to 2), SourceCatalog.counts(list))
        assertEquals("6 sources trouvées : IPTV 4 · RD 2 · Real-Debrid : recherche…", sourceCountLine(list, SourceSearch.SEARCHING))
    }

    @Test fun `secours - meme version d abord quelle que soit l origine, puis meme langue`() {
        val list = listOf(
            src("rd:vf1080", AudioLanguage.VF, VideoQuality.FHD_1080, SourceOrigin.DEBRID),
            src("vf1080", AudioLanguage.VF, VideoQuality.FHD_1080, current = true).copy(mergedIds = listOf("vf1080-bis")),
            src("vf720", AudioLanguage.VF, VideoQuality.HD_720),
            src("rd:vf4k", AudioLanguage.VF, VideoQuality.UHD_4K, SourceOrigin.DEBRID, failed = true),
            src("rd:vo4k", AudioLanguage.VO, VideoQuality.UHD_4K, SourceOrigin.DEBRID),
        )
        // la VF 1080p de l'abonnement tombe : même fichier ailleurs, puis RD même version, puis VF 720p ; jamais la VO
        assertEquals(listOf("vf1080-bis", "rd:vf1080", "vf720"), SourceCatalog.fallbackOrder(list, "vf1080", langs))
        // connexion IPTV prise : aucune source IPTV, toutes langues (la 4K VF a échoué récemment : écartée)
        assertEquals(listOf("rd:vf1080", "rd:vo4k"), SourceCatalog.fallbackOrder(list, "vf1080", langs, skip = setOf(SourceOrigin.IPTV), anyLanguage = true))
    }

    @Test fun `panneau - pistes du fichier seulement s il y a un choix, une section par langue, sources depliables`() {
        val list = listOf(
            src("rd:vf1080", AudioLanguage.VF, VideoQuality.FHD_1080, SourceOrigin.DEBRID),
            src("vf1080", AudioLanguage.VF, VideoQuality.FHD_1080, current = true),
            src("rd:vo4k", AudioLanguage.VO, VideoQuality.UHD_4K, SourceOrigin.DEBRID),
        )
        val g = SourceCatalog.group(list, langs)
        val one = listOf(TrackOption("Français · 5.1", null, 0, true))
        val closed = versionLines(one, g, emptySet(), live = false)
        assertEquals(listOf("h-VF", "vVF|FHD_1080", "h-VO", "vVO|UHD_4K"), closed.map { it.key })
        val open = versionLines(one + TrackOption("Anglais", null, 1, false), g, setOf("VF|FHD_1080"), live = false)
        assertEquals(listOf("h-tracks", "t0", "t1", "h-VF", "vVF|FHD_1080", "ard:vf1080", "h-VO", "vVO|UHD_4K"), open.map { it.key })
        // la version en cours montre la source en cours (IPTV), l'autre source est repliée dessous
        assertEquals("vf1080", (open[4] as VersionLine.Version).group.shown.id)
        // direct : seulement les pistes
        assertEquals(listOf("h-tracks", "t0"), versionLines(one, emptyList(), emptySet(), live = true).map { it.key })
    }

    @Test fun `panneau - une version depliee montre 8 sources, puis voir les autres`() {
        val list = (0 until 12).map { src("rd:$it", AudioLanguage.VF, VideoQuality.FHD_1080, SourceOrigin.DEBRID) }
        val g = SourceCatalog.group(list, langs)
        val open = versionLines(emptyList(), g, setOf("VF|FHD_1080"), live = false)
        assertEquals(com.sokoul.iptv.ui.screens.player.ALT_LIMIT, open.count { it is VersionLine.Alt })
        assertEquals(3, (open.last() as VersionLine.More).hidden)
        val all = versionLines(emptyList(), g, setOf("VF|FHD_1080"), live = false, showAll = setOf("VF|FHD_1080"))
        assertEquals(11, all.count { it is VersionLine.Alt })
        assertFalse(all.any { it is VersionLine.More })
    }

    @Test fun `ligne courte - l origine n est pas repetee, la rubrique brute IPTV n apparait pas`() {
        val rd = src("rd:a", AudioLanguage.VF, VideoQuality.UHD_4K, SourceOrigin.DEBRID).copy(where = "Real-Debrid · Remux · 58,3 Go · prête")
        assertEquals("Remux · 58,3 Go · prête", PlayerSources.facts(rd))
        val iptv = PlayerSources.fromIptv(VodVersion("m1", "EN - Dune", "VO", "4K", "c1", categoryName = "[EN] SCI-FI/FANTASY"), current = false)
        assertEquals(null, PlayerSources.facts(iptv))
        assertFalse(com.sokoul.iptv.ui.screens.player.sourceNote(iptv).orEmpty().contains("SCI-FI"))
    }

    @Test fun `mesures et echecs Real-Debrid relus depuis la base`() {
        val now = 1_000_000_000L
        val rows = listOf(
            VodProbeEntity(1, ContentType.MOVIE, PlayerViewModel.RD_MEASURE_PREFIX + "h:1", width = 1920, height = 1080, videoCodec = "hevc", fromPlayer = true, fetchedAt = now),
            VodProbeEntity(1, ContentType.MOVIE, PlayerViewModel.RD_FAIL_PREFIX + "h:2", fromPlayer = true, fetchedAt = now - 3_600_000),
            VodProbeEntity(1, ContentType.MOVIE, PlayerViewModel.RD_FAIL_PREFIX + "h:3", fromPlayer = true, fetchedAt = now - 25 * 3_600_000L),
        )
        val (failed, measured) = PlayerViewModel.readDebridMemory(rows, now)
        assertEquals(setOf("h:2"), failed)
        assertEquals("1080p", measured["h:1"]?.resolution)
        assertFalse("h:3" in failed)
    }
}
