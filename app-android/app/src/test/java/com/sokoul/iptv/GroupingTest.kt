package com.sokoul.iptv

import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.repo.VersionPicker
import com.sokoul.iptv.data.repo.VodGrouping
import com.sokoul.iptv.data.repo.VodGrouping.Row
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.parser.TitleTagger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Règles de regroupement des doublons et de choix de version (fonctions pures). */
class GroupingTest {

    /** Ligne construite comme à la synchro : titre nettoyé et année extraits du nom fournisseur. */
    private fun raw(id: String, name: String, tmdb: String? = null): Row {
        val info = TitleTagger.parse(name)
        return Row(id, info.clean, info.year, tmdb)
    }

    @Test fun `versions VF MULTI 4K d'un meme titre reunies`() {
        val rows = listOf(
            raw("1", "FR| Clayface (2025) [VF]"),
            raw("2", "Clayface (2025) MULTI"),
            raw("3", "[4K] Clayface 2025"),
        )
        val keys = VodGrouping.assign(rows)
        assertEquals(1, keys.values.toSet().size)
    }

    @Test fun `meme TMDB regroupe meme sans annee`() {
        val keys = VodGrouping.assign(listOf(Row("1", "Dune", null, "438631"), Row("2", "Dune Part One", 2021, "438631")))
        assertEquals("tmdb:438631", keys["1"])
        assertEquals("tmdb:438631", keys["2"])
    }

    @Test fun `remakes separes par l'annee`() {
        val keys = VodGrouping.assign(listOf(Row("1", "Dune", 1984, null), Row("2", "Dune", 2021, null)))
        assertNotEquals(keys["1"], keys["2"])
    }

    @Test fun `sans annee ni TMDB jamais regroupe`() {
        val keys = VodGrouping.assign(listOf(Row("1", "Dune", null, null), Row("2", "Dune", null, null)))
        assertEquals("id:1", keys["1"])
        assertEquals("id:2", keys["2"])
    }

    @Test fun `titres generiques ou trop courts uniquement par TMDB`() {
        assertNull(VodGrouping.titleKey("Episode", 2020))
        assertNull(VodGrouping.titleKey("Up", 2009))
        assertNull(VodGrouping.titleKey("1917", 2019))
        val keys = VodGrouping.assign(listOf(Row("1", "Film", 2020, null), Row("2", "Film", 2020, null)))
        assertNotEquals(keys["1"], keys["2"])
    }

    @Test fun `pont entre version TMDB et version sans TMDB`() {
        val keys = VodGrouping.assign(listOf(Row("1", "Le Comte de Monte-Cristo", 2024, "1084736"), Row("2", "Comte de Monte Cristo", 2024, null)))
        assertEquals("tmdb:1084736", keys["1"])
        assertEquals("tmdb:1084736", keys["2"])
    }

    @Test fun `homonymes TMDB differents non fusionnes`() {
        val keys = VodGrouping.assign(
            listOf(Row("1", "The Ring", 2002, "565"), Row("2", "The Ring", 2002, "999"), Row("3", "The Ring", 2002, null))
        )
        assertEquals("tmdb:565", keys["1"])
        assertEquals("tmdb:999", keys["2"])
        assertNotEquals(keys["1"], keys["2"])
    }

    @Test fun `code pays colle au titre ignore`() {
        val keys = VodGrouping.assign(
            listOf(Row("1", "Chernobyl", 2019, null), Row("2", "HU Chernobyl", 2019, null), Row("3", "IN Chernobyl", 2019, null), Row("4", "ALB Chernobyl", 2019, null))
        )
        assertEquals(1, keys.values.toSet().size)
        // vrais titres non touchés : minuscules, mot seul, sigle hors liste
        assertEquals("It Follows", VodGrouping.stripCountry("It Follows"))
        assertEquals("US", VodGrouping.stripCountry("US"))
        assertEquals("FBI Most Wanted", VodGrouping.stripCountry("FBI Most Wanted"))
        assertEquals("NCIS Los Angeles", VodGrouping.stripCountry("NCIS Los Angeles"))
    }

    @Test fun `chaque ligne a exactement une cle`() {
        val rows = (1..500).map { i -> Row("$i", "Titre ${i % 37}", if (i % 5 == 0) null else 2000 + i % 3, if (i % 7 == 0) "${i % 11 + 1}" else null) }
        val keys = VodGrouping.assign(rows)
        assertEquals(rows.size, keys.size)
        assertEquals(rows.map { it.id }.toSet(), keys.keys)
    }

    // --- choix de version -------------------------------------------------------------------

    private val vf = VodVersion("vf", "Film VF", "VF", "HD", "c")
    private val multi4k = VodVersion("multi", "Film MULTI 4K", "MULTI", "4K", "c")
    private val vostfr = VodVersion("vost", "Film VOSTFR", "VOSTFR", "FHD", "c")
    private val vf4k = VodVersion("vf4k", "Film VF 4K", "VF", "4K", "c")
    private val unknown = VodVersion("x", "Film", null, null, "c")

    @Test fun `francais d'abord (VF ou MULTI) puis qualite, VF avant MULTI a image egale`() {
        val order = VersionPicker.order(listOf(unknown, vostfr, multi4k, vf, vf4k)).map { it.id }
        assertEquals(listOf("vf4k", "multi", "vf", "vost", "x"), order)
    }

    @Test fun `4K evitee si l'appareil ne la decode pas`() {
        val order = VersionPicker.order(listOf(vf4k, vf), supports4k = false).map { it.id }
        assertEquals(listOf("vf", "vf4k"), order)
    }

    @Test fun `choix manuel prioritaire et echecs en dernier`() {
        assertEquals("vost", VersionPicker.order(listOf(vf, vostfr), preferredId = "vost").first().id)
        assertEquals(listOf("vost", "vf"), VersionPicker.order(listOf(vf, vostfr), failed = setOf("vf")).map { it.id })
    }

    @Test fun `ordre VOSTFR d'abord`() {
        val langs = VersionPicker.parseOrder("VOSTFR,VO,MULTI,VF")
        assertEquals("vost", VersionPicker.order(listOf(vf, multi4k, vostfr), langs).first().id)
        assertEquals(VersionPicker.DEFAULT_LANGUAGES, VersionPicker.parseOrder("n'importe quoi"))
    }

    // --- épisodes fusionnés -----------------------------------------------------------------

    private fun ep(sid: String, id: String, s: Int, e: Int) = EpisodeEntity(1, sid, id, s, e, "E$e", null, null, null, null, null, null)

    @Test fun `episodes completes depuis les autres versions sans doublon`() {
        val all = listOf(
            ep("vf", "a1", 1, 1), ep("vf", "a2", 1, 2),
            ep("vost", "b1", 1, 1), ep("vost", "b2", 1, 2), ep("vost", "b3", 1, 3), ep("vost", "b4", 2, 1),
        )
        val merged = VodRepository.mergeEpisodes(all, listOf("vf", "vost"))
        assertEquals(listOf("a1", "a2", "b3", "b4"), merged.map { it.id })
    }

    @Test fun `episodes de meme numero dans une version tous conserves`() {
        val all = listOf(ep("vf", "a1", 1, 0), ep("vf", "a2", 1, 0), ep("vost", "b1", 1, 0))
        assertEquals(listOf("a1", "a2"), VodRepository.mergeEpisodes(all, listOf("vf", "vost")).map { it.id })
    }

    @Test fun `sans version dans les langues reglees - l anglais avant les autres doublages`() {
        val it = VodVersion("it", "IT| Lupin", null, "FHD", "c")
        val pl = VodVersion("pl", "PL| Lupin", null, "FHD", "c")
        val en = VodVersion("en", "EN| Lupin", null, "HD", "c")
        assertEquals(listOf("en", "it", "pl"), VersionPicker.order(listOf(it, pl, en)).map { v -> v.id })
        // une langue réglée passe toujours devant
        assertEquals("vf", VersionPicker.order(listOf(en, vf)).first().id)
    }
}
