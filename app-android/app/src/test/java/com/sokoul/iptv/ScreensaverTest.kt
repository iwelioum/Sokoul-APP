package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.TmdbMetaEntity
import com.sokoul.iptv.screensaver.ScreensaverPicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverTest {
    private fun meta(id: Int, backdrop: String?, logo: String? = null) = TmdbMetaEntity(
        type = ContentType.MOVIE, tmdbId = id, title = "Film $id", originalTitle = null, overview = null, overviewLang = null,
        tagline = null, genres = "Action,Aventure,Drame", poster = null, backdrop = backdrop, logo = logo, rating = 7.0, year = 2020,
        runtimeMin = null, seasons = null, collectionId = null, collectionName = null, certification = null, fetchedAt = 0,
    )

    @Test fun `un fond par œuvre, jamais sans image, logos d'abord, taille grand écran`() {
        val list = listOf(
            meta(1, null),
            meta(2, "https://image.tmdb.org/t/p/original/a.jpg"),
            meta(3, "https://image.tmdb.org/t/p/original/a.jpg"),
            meta(4, "https://image.tmdb.org/t/p/w780/b.jpg", logo = "https://image.tmdb.org/t/p/original/l.png"),
        )
        val all = ScreensaverPicks.slides(list)
        assertEquals(2, all.size)
        assertTrue(all.all { it.backdrop.contains("/w1280/") })
        assertEquals("2020  ·  Action, Aventure", all.first().meta)
        // n = 1 : l'œuvre avec logo passe avant les autres
        assertEquals("Film 4", ScreensaverPicks.slides(list, n = 1).single().title)
    }
}
