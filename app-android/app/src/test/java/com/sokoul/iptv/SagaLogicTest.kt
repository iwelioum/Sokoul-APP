package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.repo.CuratedSagaParser
import com.sokoul.iptv.data.repo.SagaFacts
import com.sokoul.iptv.data.repo.SagaLogic
import com.sokoul.iptv.data.repo.Work
import org.junit.Assert.assertEquals
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

    @Test fun `sagas editorialisees - au moins 20, cles et collections uniques`() {
        val sagas = CuratedSagaParser.parse(File("src/main/assets/curated_sagas.json").readText())
        assertTrue("sagas : ${sagas.size}", sagas.size >= 20)
        assertEquals(sagas.size, sagas.map { it.key }.toSet().size)
        val cols = sagas.flatMap { it.collectionIds }
        assertEquals("une collection dans une seule saga", cols.size, cols.toSet().size)
    }
}
