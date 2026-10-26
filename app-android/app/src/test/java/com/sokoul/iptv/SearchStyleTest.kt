package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.ui.screens.search.bestResults
import com.sokoul.iptv.ui.screens.search.chipLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recherche : meilleurs résultats (films et séries alternés) et pastilles de suggestion qui tiennent. */
class SearchStyleTest {
    private fun w(type: ContentType, id: String) = Work(type, id, "$type:$id", "Titre $id", null, null)

    @Test fun `meilleurs resultats alternent films et series, plafonnes`() {
        val movies = (1..10).map { w(ContentType.MOVIE, "m$it") }
        val series = (1..2).map { w(ContentType.SERIES, "s$it") }
        val best = bestResults(movies, series, n = 6)
        assertEquals(listOf("m1", "s1", "m2", "s2", "m3", "m4"), best.map { it.id })
        assertEquals(3, bestResults(movies.take(3), emptyList()).size)
    }

    @Test fun `pastille sans balises du fournisseur et jamais trop longue`() {
        assertEquals("The Past Is Never Bur…", chipLabel("The Past Is Never Buried - 2026 [ -SUB]"))
        assertEquals("Dune", chipLabel("Dune (2021)"))
        assertTrue(chipLabel("The Past Is Never Buried - 2026 [ -SUB]").length <= 22)
        assertEquals("Neandria", chipLabel("Neandria"))
    }
}
