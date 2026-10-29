package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.repo.KidsRating
import com.sokoul.iptv.data.repo.Upcoming
import com.sokoul.iptv.data.repo.UpcomingRepository
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.screens.library.HistoryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Séries et bibliothèque : calendrier « À venir », historique, filtre du profil Enfant. */
class SeriesLibraryTest {
    private fun p(type: ContentType, id: String, parent: String? = null, pos: Long = 600_000, dur: Long = 1_800_000, finished: Boolean = false, at: Long = 0) =
        ProgressEntity(1, type, id, parent, "Titre", null, null, pos, dur, at, finished)

    @Test fun `calendrier - series suivies sans doublon, a venir seulement, la plus proche d abord`() {
        assertEquals(listOf("a", "b", "c"), UpcomingRepository.Calendar.followed(listOf("a", "b"), listOf("b", "c")))
        assertEquals(2, UpcomingRepository.Calendar.followed(listOf("a", "b", "c"), emptyList(), max = 2).size)
        val now = 1_000_000_000_000L
        val h = 3_600_000L
        fun u(id: String, at: Long) = Upcoming(id, id, null, 1, 1, null, at)
        val list = listOf(u("loin", now + 90 * 24 * h), u("demain", now + 24 * h), u("passe", now - 12 * h), u("ce soir", now + 3 * h), u("tout juste", now - 2 * h))
        assertEquals(listOf("tout juste", "ce soir", "demain"), UpcomingRepository.Calendar.sorted(list, now).map { it.seriesId })
    }

    @Test fun `historique - chaque ligne mene a la bonne page`() {
        assertEquals(MovieDetailRoute("123"), HistoryRules.route(p(ContentType.MOVIE, "123")))
        assertEquals(SeriesDetailRoute("s9"), HistoryRules.route(p(ContentType.SERIES, "e1", parent = "s9")))
        assertEquals(PlayKind.LIVE, (HistoryRules.route(p(ContentType.LIVE, "ch1")) as PlayerRoute).kind)
        val rd = HistoryRules.route(p(ContentType.MOVIE, "tmdb:550")) as PlayerRoute
        assertEquals(PlayKind.DEBRID, rd.kind); assertEquals("550", rd.id)
        val cloud = HistoryRules.route(p(ContentType.MOVIE, "rdc:t:42")) as PlayerRoute
        assertEquals(PlayKind.CLOUD, cloud.kind); assertEquals("t:42", cloud.id)
    }

    @Test fun `historique - etat lisible et regroupement par jour dans l ordre`() {
        assertEquals("20:30 · 33 %", HistoryRules.status(p(ContentType.MOVIE, "1"), "20:30"))
        assertEquals("20:30 · vu", HistoryRules.status(p(ContentType.MOVIE, "1", finished = true), "20:30"))
        assertEquals("20:30", HistoryRules.status(p(ContentType.LIVE, "c", dur = 0), "20:30"))
        val rows = listOf(p(ContentType.MOVIE, "a", at = 3), p(ContentType.MOVIE, "b", at = 2), p(ContentType.MOVIE, "c", at = 1))
        val days = HistoryRules.byDay(rows, 10) { t, _ -> if (t >= 2) "Aujourd'hui" else "Hier" }
        assertEquals(listOf("Aujourd'hui", "Hier"), days.map { it.first })
        assertEquals(listOf("a", "b"), days[0].second.map { it.itemId })
    }

    @Test fun `nouvel episode - une seule notification par mise a jour, libelles`() {
        val r = com.sokoul.iptv.work.NewEpisodeNotifier.Rules
        val fresh = listOf("lupin" to 100L, "from" to 200L)
        assertEquals(fresh, r.toNotify(fresh, emptySet()))
        val done = r.remember(emptySet(), fresh)
        assertTrue(r.toNotify(fresh, done).isEmpty())
        // nouvelle mise à jour de Lupin : signalée à nouveau
        assertEquals(listOf("lupin" to 300L), r.toNotify(listOf("lupin" to 300L, "from" to 200L), done))
        assertEquals("Nouvel épisode : Lupin", r.title(listOf("Lupin")))
        assertEquals("Nouveaux épisodes dans 2 séries", r.title(listOf("Lupin", "FROM")))
        assertEquals(200, r.remember((1..300).map { "s$it@1" }.toSet(), emptyList()).size)
    }

    @Test fun `profil enfant - 12 ans et plus, genres adultes ecartes, sans classement autorise`() {
        assertTrue(KidsRating.allowed("U"))
        assertTrue(KidsRating.allowed(null))
        assertFalse(KidsRating.allowed("-12"))
        assertFalse(KidsRating.allowed("PG-13"))
        assertFalse(KidsRating.allowed(null, listOf("Horreur")))
    }
}
