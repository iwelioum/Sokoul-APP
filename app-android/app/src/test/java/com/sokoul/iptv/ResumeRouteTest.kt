package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.home.resumeRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/** « Reprendre » : chaque reprise rouvre le bon lecteur, y compris celles lues via Real-Debrid. */
class ResumeRouteTest {
    private fun p(type: ContentType, item: String, parent: String? = null, title: String = "Titre") =
        ProgressEntity(7, type, item, parent, title, null, null, 10, 100, 0, false)

    @Test fun `fichier du cloud Real-Debrid`() {
        val r = resumeRoute(7, p(ContentType.MOVIE, "rdc:t:ABC", title = "Dune")) as PlayerRoute
        assertEquals(PlayKind.CLOUD, r.kind)
        assertEquals("t:ABC", r.id)
        assertEquals("Dune", r.title)
    }

    @Test fun `film et épisode lus via Real-Debrid`() {
        val m = resumeRoute(7, p(ContentType.MOVIE, "tmdb:m:603")) as PlayerRoute
        assertEquals(PlayKind.DEBRID, m.kind)
        assertEquals("m:603", m.query)
        val e = resumeRoute(7, p(ContentType.SERIES, "tmdb:s:1399:1:2")) as PlayerRoute
        assertEquals(PlayKind.DEBRID, e.kind)
        assertEquals("s:1399:1:2", e.query)
    }

    @Test fun `reprises de l'abonnement inchangées`() {
        assertEquals(PlayerRoute(PlayKind.MOVIE, 7, "123"), resumeRoute(7, p(ContentType.MOVIE, "123")))
        assertEquals(PlayerRoute(PlayKind.EPISODE, 7, "e9", parentId = "s1"), resumeRoute(7, p(ContentType.SERIES, "e9", "s1")))
        // une favorite hors catalogue « tmdb:603 » n'est pas une lecture Real-Debrid
        assertEquals(PlayKind.MOVIE, (resumeRoute(7, p(ContentType.MOVIE, "tmdb:603")) as PlayerRoute).kind)
    }
}
