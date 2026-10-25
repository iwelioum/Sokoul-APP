package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.EpisodeMerge
import com.sokoul.iptv.data.repo.GridSpec
import com.sokoul.iptv.data.repo.Platform
import com.sokoul.iptv.data.repo.Theme
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.categoryLang
import com.sokoul.iptv.data.repo.cleanDisplayTitle
import com.sokoul.iptv.data.repo.langRank
import com.sokoul.iptv.data.repo.looksFrench
import com.sokoul.iptv.ui.screens.vod.pickSynopsis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navigation éditorialisée : plateformes, thèmes, textes français, épisodes, grilles. */
class BrowseTest {

    // --- plateformes et thèmes, sur des noms de catégories réels du fournisseur ---

    @Test fun `plateformes reconnues dans les noms de categories`() {
        assertEquals(Platform.NETFLIX, Platform.detect("[MULTI-LANG] NETFLIX"))
        assertEquals(Platform.PRIME, Platform.detect("[EN] AMAZON PRIME+"))
        assertEquals(Platform.APPLE, Platform.detect("[IT] APPLETV"))
        assertEquals(Platform.DISNEY, Platform.detect("FR - DISNEY+"))
        assertEquals(Platform.MAX, Platform.detect("[FR] HBO MAX"))
        assertEquals(Platform.CANAL, Platform.detect("[FR] CANAL+ SERIES"))
        assertNull(Platform.detect("[FR] RECEMMENT AJOUTER"))
        assertNull(Platform.detect("[EN] SKYFALL COLLECTION"))
    }

    @Test fun `themes reconnus`() {
        assertTrue(Theme.HORROR in Theme.detect("[FR] HORREUR"))
        assertTrue(Theme.NEW in Theme.detect("[FR] RECEMMENT AJOUTER"))
        assertTrue(Theme.NEW in Theme.detect("[EN] NEW RELEASES"))
        assertTrue(Theme.SUPERHERO in Theme.detect("[MULTI] SUPER-HEROS"))
        assertTrue(Theme.detect("[FR] DIVERS").isEmpty())
    }

    @Test fun `langue des categories et priorite francaise`() {
        assertEquals("FR", categoryLang("[FR] ACTION"))
        assertEquals("MULTI", categoryLang("[MULTI-LANG] NETFLIX"))
        assertEquals("EN", categoryLang("[UK] DRAMA"))
        assertNull(categoryLang("ACTION"))
        assertTrue(langRank("[FR] ACTION") < langRank("[MULTI-LANG] ACTION"))
        assertTrue(langRank("[MULTI-LANG] ACTION") < langRank("[EN] ACTION"))
        assertTrue(langRank("[EN] ACTION") < langRank("[DE] ACTION"))
    }

    // --- textes ---

    @Test fun `detection du francais`() {
        assertTrue(looksFrench("Une enseignante enferme par inadvertance son élève le plus indiscipliné chez elle."))
        assertFalse(looksFrench("A teacher accidentally locks her most unruly student in her home and the class thrives."))
        assertFalse(looksFrench(null))
        assertFalse(looksFrench(""))
    }

    @Test fun `ordre des resumes TMDB fr puis fournisseur fr puis anglais`() {
        val fr = "Une famille part en vacances et tout tourne mal dans la maison."
        val en = "A family goes on holiday and everything goes wrong in the house."
        assertEquals(fr, pickSynopsis("$fr", en, en)!!.text)
        assertEquals(fr, pickSynopsis(null, fr, en)!!.text)
        val s = pickSynopsis(null, en, "TMDB english")!!
        assertEquals("TMDB english", s.text)
        assertFalse(s.french)
        assertFalse(pickSynopsis(null, en, null)!!.french)
        assertNull(pickSynopsis(null, null, null))
    }

    @Test fun `titres affiches nettoyes`() {
        assertEquals("The Musical", cleanDisplayTitle("The Musical - 2026 [ -SUB]", 2026))
        assertEquals("Masterplan", cleanDisplayTitle("FR - Masterplan (2026)", 2026))
        assertEquals("Blade Runner 2049", cleanDisplayTitle("Blade Runner 2049", 2017))
        assertEquals("1917", cleanDisplayTitle("1917", 2019))
        assertEquals("Dune", cleanDisplayTitle("Dune 2021 MULTI 4K", 2021))
        assertEquals("Spider-Man", cleanDisplayTitle("Spider-Man", null))
    }

    // --- épisodes ---

    private fun ep(title: String, plot: String? = null, image: String? = null, episode: Int = 1) =
        EpisodeEntity(1, "s", "e$episode", 1, episode, title, plot, image, 3000, "mkv", null, null)

    @Test fun `titre propre d'un episode du fournisseur`() {
        assertEquals("Pilot", EpisodeMerge.cleanTitle("PH| Someone, Someday - S01E01 - Pilot", 1))
        assertEquals("Épisode 3", EpisodeMerge.cleanTitle("Chernobyl S01E03", 3))
        assertEquals("Épisode 2", EpisodeMerge.cleanTitle("Show - S01E02 - Episode 2", 2))
        assertEquals("Le début", EpisodeMerge.cleanTitle("Le début", 1))
        // nom de release du fournisseur : jamais affiché comme titre
        assertEquals("Épisode 1", EpisodeMerge.cleanTitle("Trese - S01E01 - 720p.NF.WEBRip.x264-GalaxyTV", 1))
        assertEquals("Épisode 4", EpisodeMerge.cleanTitle("Show.S01E04.1080p.WEB-DL.H264", 4))
        assertEquals("Le 720 Club", EpisodeMerge.cleanTitle("Show - S01E05 - Le 720 Club", 5))
        assertEquals("S1 · É1 · Pilot", EpisodeMerge.caption("S01E01 · PH| Someone, Someday - S01E01 - Pilot"))
        assertEquals("S2 · É5", EpisodeMerge.caption("Show S02E05"))
        assertNull(EpisodeMerge.caption(null))
    }

    @Test fun `fusion episode TMDB et fournisseur`() {
        val tm = TmdbEpisode(1, 1, "En enfer", "Quarante ans après la catastrophe…", "/still.jpg", 53)
        val v = EpisodeMerge.merge(ep("Tchernobyl S01E01", "English plot of the episode", "http://p/img.jpg"), tm, "/bd.jpg")
        assertEquals("En enfer", v.title)
        assertEquals("Quarante ans après la catastrophe…", v.overview)
        assertTrue(v.overviewFr)
        assertEquals("/still.jpg", v.image)
        assertEquals(50, v.runtimeMin) // durée du fichier fournisseur prioritaire
        // sans TMDB : image du fournisseur, puis fond de la série
        assertEquals("http://p/img.jpg", EpisodeMerge.merge(ep("x S01E01", image = "http://p/img.jpg"), null, "/bd.jpg").image)
        assertEquals("/bd.jpg", EpisodeMerge.merge(ep("x S01E01"), null, "/bd.jpg").image)
        // nom générique TMDB (« Épisode 4 ») : on garde le titre du fournisseur s'il est meilleur
        val generic = TmdbEpisode(1, 4, "Épisode 4", null, null, null)
        assertEquals("Le procès", EpisodeMerge.merge(ep("Show S01E04 - Le procès", episode = 4), generic, null).title)
    }

    // --- correspondance TMDB ↔ catalogue et rangées ---

    @Test fun `cles candidates d'un resultat TMDB`() {
        val keys = BrowseRepository.candidateKeys(TmdbItem(603, "Matrix", "The Matrix", 1999, null, null, 8.2, null))
        assertEquals("tmdb:603", keys.first())
        assertTrue("t:matrix|1999" in keys)
        assertTrue("t:matrix|2000" in keys)
        assertTrue(keys.any { it.startsWith("t:") && it.endsWith("|1998") })
        assertEquals(listOf("tmdb:1"), BrowseRepository.candidateKeys(TmdbItem(1, "Sans date", null, null, null, null, null, null)))
    }

    @Test fun `une seule carte par titre dans une rangee`() {
        fun w(id: String, title: String, year: Int?) = Work(ContentType.MOVIE, id, "id:$id", title, year, null)
        val out = BrowseRepository.distinctTitles(listOf(w("1", "FR - Dune", 2021), w("2", "Dune", 2021), w("3", "Dune", 1984), w("4", "Arrival", 2016)))
        assertEquals(listOf("1", "3", "4"), out.map { it.id })
    }

    @Test fun `routes de grille encodees et decodees`() {
        val specs = listOf(
            GridSpec.Cats(ContentType.MOVIE, listOf("12", "34"), true),
            GridSpec.Recent(ContentType.SERIES),
            GridSpec.Ids(ContentType.MOVIE, listOf("a", "b")),
            GridSpec.Plat(null, Platform.NETFLIX),
            GridSpec.Plat(ContentType.SERIES, Platform.DISNEY),
            GridSpec.Themed(ContentType.MOVIE, Theme.HORROR),
            GridSpec.Saga(10),
            GridSpec.Favorites(ContentType.SERIES),
        )
        specs.forEach { assertEquals(it, GridSpec.decode(it.encode())) }
        assertNull(GridSpec.decode("inconnu"))
        assertNull(GridSpec.decode("theme\u001FMOVIE\u001FNOPE"))
    }
}
