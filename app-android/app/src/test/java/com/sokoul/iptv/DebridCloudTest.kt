package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.debrid.CloudItem
import com.sokoul.iptv.data.debrid.DebridCloud
import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.ui.components.debridRoute
import com.sokoul.iptv.ui.nav.PlayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebridCloudTest {
    @Test fun titresPropres() {
        assertEquals("The Batman", DebridCloud.cleanTitle("The.Batman.2022.2160p.WEB-DL.DV.HDR.mkv"))
        assertEquals("Dune Part Two", DebridCloud.cleanTitle("Dune.Part.Two.(2024).MULTI.1080p.mkv"))
        assertEquals("The Bear", DebridCloud.cleanTitle("The.Bear.S03E02.FRENCH.1080p.WEB.mkv"))
        assertEquals("Le Bureau des Légendes", DebridCloud.cleanTitle("Le_Bureau_des_Légendes_S01_MULTI_1080p"))
        assertEquals("Interview with the Vampire - The Vampire Lestat", DebridCloud.cleanTitle("Interview with the Vampire - The Vampire Lestat - S03E05 - 1080p.mkv"))
        assertEquals(2022, DebridCloud.year("The.Batman.2022.2160p.mkv"))
        assertNull(DebridCloud.year("The.Bear.S03E02.1080p.mkv"))
    }

    private fun item(id: String, name: String, at: Long) =
        CloudItem(id, DebridCloud.cleanTitle(name), DebridCloud.year(name), ReleaseName.parse(name), 1_000_000_000L, at)

    @Test fun memeEpisodeLaMeilleureDefinitionReste() {
        val sd = item("t:1", "Interview.With.The.Vampire.S03E05.XviD.avi", 300)
        val hd = item("t:2", "Interview.with.the.Vampire.S03E05.720p.mkv", 100)
        val out = DebridCloud.merge(listOf(sd, hd), emptyList())
        assertEquals(listOf("t:2"), out.map { it.id })
        assertEquals(300, out[0].addedAt)
    }

    @Test fun fusionSansDoublonDuPlusRecent() {
        val t = item("t:1", "The.Batman.2022.2160p.mkv", 100)
        val d = item("d:9", "The Batman 2022 2160p.mkv", 200)
        val ep = item("d:3", "The.Bear.S03E02.1080p.mkv", 50)
        val out = DebridCloud.merge(listOf(t), listOf(d, ep))
        assertEquals(listOf("d:9", "d:3"), out.map { it.id })
        val w = DebridCloud.toWork(out[1])
        assertEquals(ContentType.SERIES, w.type)
        assertEquals("The Bear · S03E02", w.title)
        assertEquals("rdc:d:3", w.key)
    }

    @Test fun repriseViaRealDebrid() {
        val w = Work(type = ContentType.MOVIE, id = "tmdb:m:603", key = "tmdb:m:603", title = "Matrix", year = null, poster = null)
        val r = com.sokoul.iptv.ui.screens.browse.debridResumeRoute(3, w)!!
        assertEquals(PlayKind.DEBRID, r.kind)
        assertEquals("m:603", r.query)
        assertNull(com.sokoul.iptv.ui.screens.browse.debridResumeRoute(3, w.copy(key = "tmdb:603"))) // œuvre hors catalogue : sa fiche
    }

    @Test fun routeHorsAbonnement() {
        val film = Work(type = ContentType.MOVIE, id = "tmdb:42", key = "tmdb:42", title = "X", year = null, poster = null, tmdbId = 42, available = false)
        val r = debridRoute(7, film)!!
        assertEquals(PlayKind.DEBRID, r.kind)
        assertEquals("m:42", r.query)
        val serie = film.copy(type = ContentType.SERIES)
        assertEquals("s:42:1:1", debridRoute(7, serie)!!.query)
        assertNull(debridRoute(7, film.copy(tmdbId = null)))
    }
}
