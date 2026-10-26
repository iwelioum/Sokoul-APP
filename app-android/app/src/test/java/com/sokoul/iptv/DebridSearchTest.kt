package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.debrid.DebridSearch
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.repo.Work
import org.junit.Assert.assertEquals
import org.junit.Test

/** Rangée « Aussi disponible via Real-Debrid » : seulement ce que l'abonnement n'a pas. */
class DebridSearchTest {
    private fun item(id: Int, title: String, type: String, votes: Int = 500, poster: String? = "p") =
        TmdbItem(id, title, null, 2020, poster, null, 7.0, null, voteCount = votes, mediaType = type)

    private fun have(title: String, type: ContentType, tmdbId: Int? = null) =
        Work(type, "x$title", "x$title", title, 2020, null, null, tmdbId = tmdbId)

    @Test fun `exclut ce que l abonnement a deja`() {
        val found = listOf(
            item(1, "Dune", "movie"), item(2, "Dune : Deuxième partie", "movie"),
            item(3, "Dune: Prophecy", "tv"), item(4, "Inconnu", "movie", votes = 2), item(5, "Sans affiche", "movie", poster = null),
        )
        val out = DebridSearch.extras(found, listOf(have("Dune", ContentType.MOVIE), have("Autre", ContentType.SERIES, tmdbId = 3)), movies = true, series = true)
        assertEquals(listOf("tmdb:2"), out.map { it.id })
        assertEquals(ContentType.MOVIE, out.single().type)
    }

    @Test fun `respecte le perimetre et la limite`() {
        val found = (1..30).map { item(it, "Titre $it", if (it % 2 == 0) "tv" else "movie") }
        val series = DebridSearch.extras(found, emptyList(), movies = false, series = true)
        assertEquals(DebridSearch.MAX, series.size)
        assert(series.all { it.type == ContentType.SERIES })
    }
}
