package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.tv.DeepLinks
import com.sokoul.iptv.tv.ResumeLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Liens « À suivre » : aller-retour exact, et tout lien douteux refusé (l'activité est exportée). */
class DeepLinksTest {
    private fun progress(type: ContentType, id: String, parent: String? = null) =
        ProgressEntity(7, type, id, parent, "Titre", null, null, 60_000, 600_000, 1, false)

    @Test fun `aller retour film, episode et reprise Real-Debrid`() {
        listOf(progress(ContentType.MOVIE, "1234"), progress(ContentType.SERIES, "ep-9", "s1"), progress(ContentType.MOVIE, "tmdb:m:603")).forEach { p ->
            assertEquals(ResumeLink(7, p.type, p.itemId), DeepLinks.parse(DeepLinks.of(p)))
        }
        assertEquals("sokoul://resume/MOVIE/7/tmdb%3Am%3A603", DeepLinks.of(progress(ContentType.MOVIE, "tmdb:m:603")))
    }

    @Test fun `liens refuses`() {
        listOf(
            null, "", "https://resume/MOVIE/7/1", "sokoul://resume/LIVE/7/1", "sokoul://resume/MOVIE/0/1",
            "sokoul://resume/MOVIE/x/1", "sokoul://resume/MOVIE/7", "sokoul://resume/MOVIE/7/a%2Fb", "sokoul://resume/MOVIE/7/" + "a".repeat(200),
            "sokoul://resume/MOVIE/7/%27%3B%20DROP",
        ).forEach { assertNull(it, DeepLinks.parse(it)) }
    }
}
