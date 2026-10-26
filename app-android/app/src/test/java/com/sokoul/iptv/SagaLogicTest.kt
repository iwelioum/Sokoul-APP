package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.repo.CuratedSagaParser
import com.sokoul.iptv.data.repo.SagaFacts
import com.sokoul.iptv.data.repo.SagaLogic
import com.sokoul.iptv.data.repo.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sagas spectaculaires : logo, faits, prochain film, catalogue éditorialisé. */
class SagaLogicTest {
    private fun item(id: Int, title: String, year: Int?, original: String? = null, rating: Double? = null) =
        TmdbItem(id, title, original, year, null, null, rating, null)

    private fun film(id: String, available: Boolean = true) = Work(ContentType.MOVIE, id, id, "Film $id", 2000, null, available = available)

    @Test fun `logo - le film qui porte le nom de la saga, titre francais ou original`() {
        val parts = listOf(item(1, "Alien, le huitième passager", 1979, original = "Alien"), item(2, "Aliens, le retour", 1986))
        assertEquals(1, SagaLogic.logoPart("Alien - Saga", parts))
        assertEquals(10, SagaLogic.logoPart("Mission : Impossible", listOf(item(10, "Mission: Impossible", 1996))))
        // aucun épisode ne s'appelle comme la saga : pas de logo trompeur
        assertNull(SagaLogic.logoPart("Le monde des sorciers", listOf(item(3, "Harry Potter à l'école des sorciers", 2001))))
        assertNull(SagaLogic.logoPart("Harry Potter - Saga", listOf(item(3, "Harry Potter à l'école des sorciers", 2001))))
    }

    @Test fun `logo - sinon le premier film « Nom - sous-titre »`() {
        val pirates = listOf(
            item(22, "Pirates des Caraïbes : La Malédiction du Black Pearl", 2003),
            item(58, "Pirates des Caraïbes : Le Secret du coffre maudit", 2006),
        )
        assertEquals(22, SagaLogic.logoPart("Pirates des Caraïbes - Saga", pirates))
        // titre original seulement, tiret long
        assertEquals(120, SagaLogic.logoPart("The Lord of the Rings", listOf(item(120, "La Communauté de l'anneau", 2001, original = "The Lord of the Rings – The Fellowship"))))
        // nom contenant déjà « : »
        // logo anglais seulement quand le titre français ne porte pas le nom de la saga
        val sw = listOf(item(11, "La Guerre des étoiles", 1977, original = "Star Wars"))
        assertEquals(11, SagaLogic.logoPart("Star Wars - Saga", sw))
        assertTrue(SagaLogic.englishLogo("Star Wars - Saga", sw, 11))
        assertFalse(SagaLogic.englishLogo("Pirates des Caraïbes - Saga", pirates, 22))
        assertEquals(7, SagaLogic.logoPart("Mission : Impossible", listOf(item(7, "Mission: Impossible - Fallout", 2018))))
    }

    @Test fun `faits - films sortis seulement, annees et note moyenne`() {
        val f = SagaLogic.facts(listOf(1977 to 8.2, 1980 to 8.4, 2019 to 6.4, null to null, 2031 to null), thisYear = 2026)
        assertEquals(SagaFacts(3, 1977, 2019, (8.2 + 8.4 + 6.4) / 3), f)
        assertEquals("3 films · 1977 → 2019 · ★ 7,7 de moyenne", f.line())
        assertEquals("1 film · 2001", SagaFacts(1, 2001, 2001, null).line())
    }

    @Test fun `prochain film - reprend apres le dernier vu, saute les hors catalogue`() {
        val films = listOf(film("a"), film("b"), film("c", available = false), film("d"), film("e"))
        assertEquals("a", SagaLogic.next(films, emptySet())?.id)
        assertEquals("d", SagaLogic.next(films, setOf("MOVIE|a", "MOVIE|b"))?.id)
        // vu dans le désordre : on reprend après le dernier vu, puis on revient au premier oublié
        assertEquals("e", SagaLogic.next(films, setOf("MOVIE|d"))?.id)
        assertEquals("a", SagaLogic.next(films, setOf("MOVIE|e"))?.id)
        assertNull(SagaLogic.next(films, setOf("MOVIE|a", "MOVIE|b", "MOVIE|d", "MOVIE|e")))
    }

    @Test fun `commencer la saga - suit l'ordre choisi (sortie ou recit)`() {
        // Star Wars : sortie 4-5-6-1-2-3, récit 1-2-3-4-5-6
        val release = listOf(4, 5, 6, 1, 2, 3).map { Work(ContentType.MOVIE, "e$it", "e$it", "Episode $it", null, null, tmdbId = it) }
        val story = SagaLogic.storyOrder(release, listOf(1, 2, 3, 4, 5, 6))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), story.map { it.tmdbId })
        assertEquals(4, SagaLogic.next(release, emptySet())?.tmdbId)
        assertEquals(1, SagaLogic.next(story, emptySet())?.tmdbId)
        // épisodes 1 et 2 vus : on continue au 3 dans le récit, au 3 aussi dans la sortie (après le dernier vu)
        val seen = setOf("MOVIE|e1", "MOVIE|e2")
        assertEquals(3, SagaLogic.next(story, seen)?.tmdbId)
        assertEquals(3, SagaLogic.next(release, seen)?.tmdbId)
    }

    @Test fun `sagas editorialisees - au moins 20, cles et collections uniques`() {
        val sagas = CuratedSagaParser.parse(File("src/main/assets/curated_sagas.json").readText())
        assertTrue("sagas : ${sagas.size}", sagas.size >= 20)
        assertEquals(sagas.size, sagas.map { it.key }.toSet().size)
        val cols = sagas.flatMap { it.collectionIds }
        assertEquals("une collection dans une seule saga", cols.size, cols.toSet().size)
    }
}
