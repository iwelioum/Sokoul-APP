package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.repo.EpisodeResolver
import com.sokoul.iptv.data.repo.GridSort
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.SeriesAction
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.player.ErrorDecision
import com.sokoul.iptv.player.ErrorPolicy
import com.sokoul.iptv.player.ProviderGate
import com.sokoul.iptv.ui.screens.vod.youtubeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parcours films/séries (phase 6) : protection du fournisseur, reprise, épisode suivant, tris. */
class ParcoursLogicTest {

    // --- erreurs de lecture et fournisseur -------------------------------------------------------

    private fun decide(http: Int? = null, network: Boolean = false, format: Boolean = false, live: Boolean = false, next: Boolean = false, retries: Int = 0) =
        ErrorPolicy.decide(http, network, format, live, next, retries, "erreur")

    @Test fun `refus du fournisseur jamais relance tout de suite meme avec une autre URL`() {
        for (code in listOf(401, 403, 406, 429, 458)) {
            assertTrue("$code", decide(http = code) is ErrorDecision.Refused)
            assertTrue("$code + autre URL", decide(http = code, next = true) is ErrorDecision.Refused)
            assertTrue("$code en direct", decide(http = code, live = true, next = true) is ErrorDecision.Refused)
        }
        assertTrue((decide(http = 403) as ErrorDecision.Refused).message.contains("refuse"))
    }

    @Test fun `flux introuvable essaie l'autre format puis abandonne`() {
        assertEquals(ErrorDecision.NextCandidate, decide(http = 404, next = true))
        assertTrue(decide(http = 404) is ErrorDecision.Fail)
        assertTrue(decide(http = 410) is ErrorDecision.Fail)
    }

    @Test fun `erreurs reseau avec attente croissante puis abandon en VOD`() {
        assertEquals(ErrorDecision.Retry(500), decide(network = true))
        assertEquals(ErrorDecision.Retry(1000), decide(network = true, retries = 1))
        assertTrue(decide(network = true, retries = ErrorPolicy.MAX_NETWORK_RETRIES_VOD) is ErrorDecision.Fail)
        // le direct insiste plus longtemps (coupures courtes)
        assertTrue(decide(live = true, retries = ErrorPolicy.MAX_NETWORK_RETRIES_LIVE - 1) is ErrorDecision.Retry)
        assertTrue(decide(live = true, retries = ErrorPolicy.MAX_NETWORK_RETRIES_LIVE) is ErrorDecision.Fail)
    }

    @Test fun `format illisible essaie l'autre URL`() {
        assertEquals(ErrorDecision.NextCandidate, decide(format = true, next = true))
        assertTrue(decide(format = true) is ErrorDecision.Fail)
    }

    @Test fun `attente reseau plafonnee a 15 s`() {
        assertEquals(500, ErrorPolicy.backoff(1))
        assertEquals(8000, ErrorPolicy.backoff(5))
        assertEquals(15_000, ErrorPolicy.backoff(6))
        assertEquals(15_000, ErrorPolicy.backoff(50))
    }

    @Test fun `pause de 30 s apres un refus pour tous les ecrans`() {
        var now = 1_000L
        val gate = ProviderGate().apply { clock = { now } }
        assertEquals(0, gate.remainingMs())
        gate.noteRefusal()
        assertEquals(ProviderGate.COOLDOWN_MS, gate.remainingMs())
        now += 19_000
        assertEquals(11_000, gate.remainingMs())
        assertEquals(now + 11_000, gate.availableAt())
        now += 11_000
        assertEquals(0, gate.remainingMs())
        // un nouveau refus relance la pause complète
        gate.noteRefusal()
        assertEquals(ProviderGate.COOLDOWN_MS, gate.remainingMs())
    }

    // --- reprise et épisode suivant -----------------------------------------------------------------

    private fun ep(sid: String, s: Int, e: Int) = EpisodeEntity(1, sid, "$sid-$s-$e", s, e, "Épisode $e", null, null, null, "mkv", null, null)

    private fun prog(id: String, s: Int?, e: Int?, pos: Long, dur: Long = 40 * 60_000L, at: Long, manual: Boolean = false) = ProgressEntity(
        1, ContentType.SERIES, id, id.substringBefore('-'), "Série", null, null, pos, dur, at,
        LibraryRepository.isFinished(pos, dur) || manual, s, e, manual,
    )

    /** Version A (VF) : S1E1-E4 ; version B (VOSTFR) : S1E1-E5 et S2E1. */
    private val a = listOf(ep("A", 1, 1), ep("A", 1, 2), ep("A", 1, 3), ep("A", 1, 4))
    private val b = listOf(ep("B", 1, 1), ep("B", 1, 2), ep("B", 1, 3), ep("B", 1, 4), ep("B", 1, 5), ep("B", 2, 1))
    private val all = a + b
    private val merged = VodRepository.mergeEpisodes(all, listOf("A", "B"))

    private fun action(vararg p: ProgressEntity) = EpisodeResolver.primaryAction(merged, EpisodeResolver.progressBySe(p.toList(), all))

    @Test fun `liste fusionnee comble les trous de la version preferee`() {
        assertEquals(listOf(1 to 1, 1 to 2, 1 to 3, 1 to 4, 1 to 5, 2 to 1), merged.map { it.season to it.episode })
        assertEquals("A", merged.first().seriesId)
        assertEquals("B", merged.last().seriesId)
    }

    @Test fun `rien de regarde premier episode et libelle Lecture`() {
        val act = action()
        assertTrue(act is SeriesAction.Start)
        assertEquals(1 to 1, act!!.episode.season to act.episode.episode)
        assertEquals("Lecture", EpisodeResolver.label(act))
    }

    @Test fun `reprise retrouvee meme si l'episode a ete vu dans une autre version`() {
        val act = action(prog("B-1-3", 1, 3, pos = 22 * 60_000L, at = 5))
        assertTrue(act is SeriesAction.Resume)
        assertEquals(1 to 3, act!!.episode.season to act.episode.episode)
        assertEquals("Reprendre S1 · É3", EpisodeResolver.label(act))
        assertEquals("Reste 18 min", EpisodeResolver.hint(act))
    }

    @Test fun `ancienne progression sans saison retrouvee par l'id d'episode`() {
        val act = action(prog("B-1-2", null, null, pos = 10 * 60_000L, at = 5))
        assertTrue(act is SeriesAction.Resume)
        assertEquals(1 to 2, act!!.episode.season to act.episode.episode)
    }

    @Test fun `episode termine propose le suivant meme s'il n'existe que dans une autre version`() {
        val act = action(prog("A-1-4", 1, 4, pos = 39 * 60_000L, at = 5))
        assertTrue(act is SeriesAction.Next)
        assertEquals("B", act!!.episode.seriesId)
        assertEquals(1 to 5, act.episode.season to act.episode.episode)
        assertEquals("Regarder S1 · É5", EpisodeResolver.label(act))
        // fin de saison : la saison suivante
        val next = action(prog("B-1-5", 1, 5, pos = 39 * 60_000L, at = 6))
        assertEquals(2 to 1, next!!.episode.season to next.episode.episode)
    }

    @Test fun `marquage manuel ignore pour la reprise`() {
        // regardé S1E3 jusqu'à 12 min, puis « marquer comme vu » sur S1E5 à la main
        val act = action(prog("A-1-3", 1, 3, pos = 12 * 60_000L, at = 5), prog("B-1-5", 1, 5, pos = 1, dur = 1, at = 9, manual = true))
        assertTrue(act is SeriesAction.Resume)
        assertEquals(1 to 3, act!!.episode.season to act.episode.episode)
    }

    @Test fun `episodes marques vus a la main sautes par la suite`() {
        val act = action(prog("A-1-1", 1, 1, pos = 39 * 60_000L, at = 5), prog("A-1-2", 1, 2, pos = 1, dur = 1, at = 9, manual = true))
        assertEquals(1 to 3, act!!.episode.season to act.episode.episode)
    }

    @Test fun `debut d'episode de moins de 30 s relance l'episode depuis le debut`() {
        val act = action(prog("A-1-3", 1, 3, pos = 20_000, at = 5))
        assertTrue(act is SeriesAction.Next)
        assertEquals(1 to 3, act!!.episode.season to act.episode.episode)
    }

    @Test fun `dernier episode vu avec des trous propose le premier non vu puis revoir`() {
        val act = action(prog("B-2-1", 2, 1, pos = 39 * 60_000L, at = 5))
        assertTrue(act is SeriesAction.Next)
        assertEquals(1 to 1, act!!.episode.season to act.episode.episode)
        val everything = merged.mapIndexed { i, e -> prog(e.id, e.season, e.episode, pos = 39 * 60_000L, at = i.toLong()) }
        val done = action(*everything.toTypedArray())
        assertTrue(done is SeriesAction.Rewatch)
        assertEquals("Revoir depuis le début", EpisodeResolver.label(done!!))
        assertEquals("Vous avez tout vu", EpisodeResolver.hint(done))
    }

    @Test fun `episode suivant et liste vide`() {
        assertEquals(1 to 5, EpisodeResolver.nextAfter(merged, 1 to 4)!!.let { it.season to it.episode })
        assertNull(EpisodeResolver.nextAfter(merged, 2 to 1))
        assertNull(EpisodeResolver.primaryAction(emptyList(), emptyMap()))
    }

    @Test fun `temps restant lisible`() {
        assertEquals("Reste 1 h 05", EpisodeResolver.remaining(prog("A-1-1", 1, 1, pos = 55 * 60_000L, dur = 120 * 60_000L, at = 1)))
        assertEquals("Reste 1 min", EpisodeResolver.remaining(prog("A-1-1", 1, 1, pos = 39 * 60_000L + 59_000, at = 1)))
        assertNull(EpisodeResolver.remaining(prog("A-1-1", 1, 1, pos = 0, dur = 0, at = 1)))
    }

    // --- seuils de progression -------------------------------------------------------------------

    @Test fun `seuils de reprise et de fin coherents`() {
        fun p(pos: Long, dur: Long = 100 * 60_000L) = ProgressEntity(1, ContentType.MOVIE, "m", null, "Film", null, null, pos, dur, 1, LibraryRepository.isFinished(pos, dur))
        assertEquals(0, LibraryRepository.resumeFrom(p(30_000)))
        assertEquals(25_001, LibraryRepository.resumeFrom(p(30_001)))
        assertEquals(0, LibraryRepository.resumeFrom(p(99 * 60_000L)))
        assertEquals(0, LibraryRepository.resumeFrom(null))
        assertTrue(LibraryRepository.isFinished(92, 100))
        assertFalse(LibraryRepository.isFinished(80, 100))
        // générique : à moins de 3 min de la fin d'un long contenu
        assertTrue(LibraryRepository.isFinished(118 * 60_000L, 120 * 60_000L))
        // court contenu : 3 min restantes sur 10 ne suffit pas
        assertFalse(LibraryRepository.isFinished(7 * 60_000L, 10 * 60_000L))
        assertFalse(LibraryRepository.isFinished(10, 0))
    }

    // --- grilles et bande-annonce -----------------------------------------------------------------

    private fun work(title: String, year: Int? = null, rating: Double? = null) =
        Work(ContentType.MOVIE, title, title, title, year, null, rating = rating)

    @Test fun `tris de la grille`() {
        val list = listOf(work("Zorro", 1998, 6.1), work("Écho", null, 0.0), work("avatar", 2009, 7.6), work("Batman", 2022, null))
        assertEquals(list, GridSort.DEFAULT.apply(list))
        assertEquals(listOf("avatar", "Batman", "Écho", "Zorro"), GridSort.TITLE.apply(list).map { it.title })
        assertEquals(listOf("Batman", "avatar", "Zorro", "Écho"), GridSort.YEAR.apply(list).map { it.title })
        // note 0 = inconnue, en fin de liste
        assertEquals(listOf("avatar", "Zorro", "Écho", "Batman"), GridSort.RATING.apply(list).map { it.title })
        assertEquals(GridSort.YEAR, GridSort.from("YEAR"))
        assertEquals(GridSort.DEFAULT, GridSort.from("???"))
    }

    @Test fun `identifiant de bande-annonce`() {
        assertEquals("dQw4w9WgXcQ", youtubeId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", youtubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30"))
        assertEquals("dQw4w9WgXcQ", youtubeId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", youtubeId("https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1"))
        assertEquals("dQw4w9WgXcQ", youtubeId("  dQw4w9WgXcQ  "))
        assertNull(youtubeId(""))
        assertNull(youtubeId("pas une vidéo !"))
        assertNull(youtubeId("javascript:alert(1)"))
    }
}
