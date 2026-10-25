package com.sokoul.iptv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.net.HttpModule
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.data.repo.BrowseKind
import com.sokoul.iptv.data.repo.BrowsePage
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.ui.ParentalGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Parcours films/séries (phase 6) sur base Room réelle : « Reprendre la lecture », pages avec TMDB lent ou en panne. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ParcoursDataTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var gate: ParentalGate
    private var pid = 0L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context)
        runBlocking {
            settings.update { it.copy(parentalPin = "", tmdbApiKey = "", activePlaylistId = 0) }
            pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        }
        gate = ParentalGate(settings)
    }

    @After fun tearDown() = db.close()

    private fun movie(id: String, title: String, cat: String = "c", pos: Int = 0) =
        MovieEntity(pid, id, title, title, title.lowercase(), 2020, null, null, null, null, cat, "mkv", null, 1_000L + pos, null, pos, 1)

    private fun series(id: String, title: String) =
        SeriesEntity(pid, id, title, title, title.lowercase(), 2020, null, null, null, null, null, null, "s", 0, null, 0, 1)

    private fun ep(sid: String, s: Int, e: Int) = EpisodeEntity(pid, sid, "$sid-$s-$e", s, e, "Épisode $e", null, null, null, "mkv", null, null)

    private fun progress(type: ContentType, id: String, parent: String?, pos: Long, dur: Long, at: Long, s: Int? = null, e: Int? = null, manual: Boolean = false) =
        ProgressEntity(pid, type, id, parent, "Titre", null, null, pos, dur, at, manual || LibraryRepository.isFinished(pos, dur), s, e, manual)

    private fun browse(http: OkHttpClient = OkHttpClient()): BrowseRepository {
        val tmdb = TmdbClient(http, HttpModule.json(), db.tmdb())
        val vod = VodRepository(db, settings, gate)
        return BrowseRepository(db, vod, EnrichmentRepository(db, tmdb, settings, vod), tmdb, gate, settings)
    }

    // --- « Reprendre la lecture » -------------------------------------------------------------------

    @Test fun `reprendre ne garde que ce qui se reprend vraiment`() = runBlocking {
        val min = 60_000L
        listOf(
            progress(ContentType.MOVIE, "m-debut", null, 20_000, 100 * min, at = 1), // < 30 s : rien à reprendre
            progress(ContentType.MOVIE, "m-seuil", null, 30_000, 100 * min, at = 2), // pile 30 s : reprise à 0, donc absent
            progress(ContentType.MOVIE, "m-cours", null, 40 * min, 100 * min, at = 3),
            progress(ContentType.MOVIE, "m-fini", null, 99 * min, 100 * min, at = 4),
            // série A : dernier épisode regardé en cours
            progress(ContentType.SERIES, "A-1-1", "A", 39 * min, 40 * min, at = 5, s = 1, e = 1),
            progress(ContentType.SERIES, "A-1-2", "A", 5 * min, 40 * min, at = 6, s = 1, e = 2),
            // série B : dernier épisode terminé, gardé pour proposer la suite
            progress(ContentType.SERIES, "B-1-1", "B", 39 * min, 40 * min, at = 7, s = 1, e = 1),
            // série C : seulement un « marquer comme vu » à la main
            progress(ContentType.SERIES, "C-1-1", "C", 1, 1, at = 8, s = 1, e = 1, manual = true),
            // série A : marquage manuel plus récent, ignoré
            progress(ContentType.SERIES, "A-1-5", "A", 1, 1, at = 9, s = 1, e = 5, manual = true),
        ).forEach { db.library().saveProgress(it) }
        val rows = db.library().observeContinueWatching(pid).first()
        assertEquals(listOf("B-1-1", "A-1-2", "m-cours"), rows.map { it.itemId })
    }

    @Test fun `serie terminee propose l'episode suivant depuis le debut ou sort de la rangee`() = runBlocking {
        val min = 60_000L
        db.series().upsert(listOf(series("B", "Série B"), series("Z", "Série Z")))
        db.series().upsertEpisodes(listOf(ep("B", 1, 1), ep("B", 1, 2), ep("Z", 1, 1)))
        val b = progress(ContentType.SERIES, "B-1-1", "B", 39 * min, 40 * min, at = 7, s = 1, e = 1)
        val z = progress(ContentType.SERIES, "Z-1-1", "Z", 39 * min, 40 * min, at = 6, s = 1, e = 1)
        val works = browse().continueWorks(pid, listOf(b, z))
        val w = works.single()
        assertEquals("B", w.id)
        val r = w.resume!!
        assertEquals("B-1-2", r.itemId)
        assertEquals(0, r.positionMs)
        assertFalse(r.finished)
        assertEquals(1 to 2, r.season to r.episode)
        assertEquals("Épisode suivant · S1 · É2", w.subtitle)
        assertNull(w.progress)
    }

    @Test fun `episodes de toutes les versions et progression par saison`() = runBlocking {
        db.series().upsertEpisodes(listOf(ep("A", 1, 1), ep("B", 1, 1), ep("B", 1, 2), ep("X", 1, 1)))
        db.library().saveProgress(progress(ContentType.SERIES, "B-1-2", "B", 1, 1, at = 1, s = 1, e = 2, manual = true))
        db.library().saveProgress(progress(ContentType.SERIES, "X-1-1", "X", 1, 1, at = 1, s = 1, e = 1, manual = true))
        assertEquals(setOf("A-1-1", "B-1-1", "B-1-2"), db.vod().episodesIn(pid, listOf("A", "B")).map { it.id }.toSet())
        assertEquals(listOf("B-1-2"), db.vod().seriesProgressIn(pid, listOf("A", "B")).map { it.itemId })
    }

    // --- pages éditoriales : TMDB ne bloque jamais l'affichage -------------------------------------------

    private suspend fun catalog() {
        db.categories().upsert(listOf(CategoryEntity(pid, ContentType.MOVIE, "c", "[FR] ACTION", 0, 1)))
        db.movies().upsert((1..12).map { movie("$it", "Film $it", pos = it) })
        settings.update { it.copy(tmdbApiKey = "cle-de-test") }
    }

    @Test fun `TMDB tres lent la page locale arrive d'abord puis la page finale dans le delai`() = runBlocking {
        catalog()
        val calls = AtomicInteger()
        val slow = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Thread.sleep(8_000) // TMDB qui ne répond pas (jamais de vrai appel réseau dans ce test)
            throw java.io.IOException("délai dépassé")
        }.build()
        val repo = browse(slow).apply { quickMs = 300; tmdbBudgetMs = 1_200 }
        var partial: BrowsePage? = null
        var partialAt = 0L
        val start = System.currentTimeMillis()
        val page = repo.page(db.playlists().getAll().first(), BrowseKind.MOVIES, onPartial = { partial = it; partialAt = System.currentTimeMillis() - start })
        val total = System.currentTimeMillis() - start
        assertTrue("TMDB a bien été sollicité", calls.get() > 0)
        assertNotNull("page locale envoyée sans attendre TMDB", partial)
        assertTrue("page locale en $partialAt ms", partialAt < 3_000)
        assertTrue(partial!!.rows.any { it.id == "new" && it.works.size == 12 })
        assertTrue("page finale en $total ms", total < 6_000)
        assertTrue("page incomplète signalée", page.partial)
        assertTrue(page.rows.any { it.id == "new" && it.works.isNotEmpty() })
    }

    @Test fun `TMDB en panne la page se construit sans attente et sans rangees vides`() = runBlocking {
        catalog()
        val down = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(500).message("KO").body("".toResponseBody()).build()
        }.build()
        val repo = browse(down)
        var partialCalled = false
        val start = System.currentTimeMillis()
        val page = repo.page(db.playlists().getAll().first(), BrowseKind.MOVIES, onPartial = { partialCalled = true })
        val total = System.currentTimeMillis() - start
        assertTrue("page en $total ms", total < BrowseRepository.QUICK_MS + 2_000)
        assertFalse(page.partial)
        assertFalse(partialCalled)
        assertTrue(page.rows.any { it.id == "new" && it.works.size == 12 })
        // rangées TMDB vides : masquées par l'écran (isEmpty)
        assertTrue(page.rows.filter { it.id == "top10" || it.id == "toprated" }.all { it.isEmpty })
    }

    @Test fun `accueil sans cle TMDB`() = runBlocking {
        catalog()
        settings.update { it.copy(tmdbApiKey = "") }
        val page = browse().page(db.playlists().getAll().first(), BrowseKind.HOME)
        assertFalse(page.partial)
        assertTrue(page.rows.any { it.id == "new-m" && it.works.size == 12 })
    }
}
