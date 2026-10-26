package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.repo.BrowseRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeFilterTest {
    private fun p(type: ContentType, item: String, parent: String? = null) =
        ProgressEntity(1, type, item, parent, item, null, null, 10, 100, 0, false)

    @Test fun `reprise cachée si la catégorie est verrouillée ou masquée, gardée si inconnue`() {
        val lockedMovie = p(ContentType.MOVIE, "m1")
        val okMovie = p(ContentType.MOVIE, "m2")
        val unknownMovie = p(ContentType.MOVIE, "m3")
        val lockedEpisode = p(ContentType.SERIES, "e1", "s1")
        val okEpisode = p(ContentType.SERIES, "e2", "s2")
        val hidden = BrowseRepository.hiddenResumes(
            listOf(lockedMovie, okMovie, unknownMovie, lockedEpisode, okEpisode),
            movieCat = mapOf("m1" to "adult", "m2" to "films"),
            seriesCat = mapOf("s1" to "locked", "s2" to "series"),
            visibleMovies = setOf("films"), visibleSeries = setOf("series"),
        )
        assertEquals(setOf(lockedMovie, lockedEpisode), hidden)
    }
}
