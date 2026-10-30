package com.sokoul.iptv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.net.HttpModule
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.CuratedSagaParser
import com.sokoul.iptv.data.repo.CuratedSagaStore
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.GridSpec
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.ui.ParentalGate
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Hub des sagas, page « musée », titres hors catalogue dans Ma liste (base Room réelle, TMDB simulé). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SagaTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private var pid = 0L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context)
        runBlocking {
            settings.update { it.copy(parentalPin = "", tmdbApiKey = "test", activePlaylistId = 0) }
            pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
            db.categories().upsert(listOf(CategoryEntity(pid, ContentType.MOVIE, "c", "[FR] FILMS", 0, 1)))
        }
    }

    @After fun tearDown() = db.close()

    private fun movie(id: String, title: String, groupKey: String) =
        MovieEntity(pid, id, title, title, title.lowercase(), 2001, null, null, null, null, "c", "mkv", null, 0, null, 0, 1, groupKey)

    private fun part(id: Int, title: String, year: Int) = """{"id":$id,"title":"$title","release_date":"$year-11-16"}"""
    private fun cast(vararg p: Pair<Int, String>) = p.withIndex().joinToString(",") { (i, x) -> """{"id":${x.first},"name":"${x.second}","order":$i}""" }
    private fun crew(vararg p: Pair<String, String>) = p.withIndex().joinToString(",") { (i, x) -> """{"id":${500 + i + x.second.length},"name":"${x.second}","job":"${x.first}"}""" }

    /** Harry Potter (3 films) + Les Animaux fantastiques (1 film) : le monde des sorciers éditorialisé. */
    private fun fakeTmdb() = OkHttpClient.Builder().addInterceptor { chain ->
        val path = chain.request().url.encodedPath
        val radcliffe = 10980 to "Daniel Radcliffe"
        val williams = "Original Music Composer" to "John Williams"
        val body = when (path) {
            "/3/collection/1241" -> """{"id":1241,"name":"Harry Potter - Saga","overview":"Le jeune sorcier.","backdrop_path":"/hp.jpg",
                "parts":[${part(673, "Le Prisonnier d'Azkaban", 2004)},${part(671, "À l'école des sorciers", 2001)},${part(672, "La Chambre des secrets", 2002)}]}"""
            "/3/collection/435259" -> """{"id":435259,"name":"Les Animaux fantastiques - Saga","parts":[${part(259316, "Les Animaux fantastiques", 2016)}]}"""
            "/3/movie/671/credits" -> """{"cast":[${cast(radcliffe)}],"crew":[${crew("Director" to "Chris Columbus", williams)}]}"""
            "/3/movie/672/credits" -> """{"cast":[${cast(radcliffe)}],"crew":[${crew("Director" to "Chris Columbus", williams)}]}"""
            "/3/movie/673/credits" -> """{"cast":[${cast(radcliffe)}],"crew":[${crew("Director" to "Alfonso Cuarón", williams)}]}"""
            "/3/movie/259316/credits" -> """{"cast":[${cast(37632 to "Eddie Redmayne")}],"crew":[${crew("Director" to "David Yates")}]}"""
            else -> """{"results":[]}"""
        }
        val code = if (body.startsWith("""{"results"""") && path.startsWith("/3/collection")) 404 else 200
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("OK").body(body.toResponseBody()).build()
    }.build()

    private fun repo(): BrowseRepository {
        val tmdb = TmdbClient(fakeTmdb(), HttpModule.json(), com.sokoul.iptv.data.db.DatabaseModule.tmdbCache(db.tmdb()))
        val gate = ParentalGate(settings, com.sokoul.iptv.data.repo.ProfileRepository(db))
        val vod = VodRepository(db, settings, gate)
        return BrowseRepository(db, vod, EnrichmentRepository(db, tmdb, settings, vod), tmdb, gate, settings, CuratedSagaStore(context))
    }

    @Test fun `saga - frise complete, hors catalogue, ordre du recit, visages et coulisses`() = runBlocking {
        db.movies().upsert(listOf(movie("1", "Harry Potter 1", "tmdb:671"), movie("3", "Harry Potter 3", "tmdb:673")))
        val page = repo().sagaPage(pid, 1241)!!

        assertEquals("Le monde des sorciers", page.name)
        assertEquals(listOf(671, 672, 673, 259316), page.films.map { it.tmdbId })
        assertEquals(listOf(true, false, true, false), page.films.map { it.available })
        assertEquals("tmdb:672", page.films[1].key)
        assertEquals(2, page.inCatalog)
        // ordre du récit éditorialisé : Les Animaux fantastiques d'abord
        assertEquals(listOf(259316, 671, 672, 673), page.story!!.map { it.tmdbId })
        // visages : présents dans au moins 2 films
        assertEquals(listOf("Daniel Radcliffe"), page.faces.map { it.name })
        assertEquals(3, page.faces.single().films)
        // coulisses : le plus fidèle d'abord
        val crew = page.crew.associate { it.label to it.names }
        assertEquals(listOf("Chris Columbus", "Alfonso Cuarón", "David Yates"), crew["RÉALISATION"])
        assertEquals(listOf("John Williams"), crew["MUSIQUE ORIGINALE"])
        assertFalse("PRODUCTION" in crew)
    }

    @Test fun `saga - titre publie sans annee retrouve par son nom exact`() = runBlocking {
        db.movies().upsert(listOf(
            MovieEntity(pid, "2", "La Chambre des secrets", "La Chambre des secrets", "la chambre des secrets", null, null, null, null, null, "c", "mkv", null, 0, null, 0, 1, null),
        ))
        val page = repo().sagaPage(pid, 1241)!!
        val hp2 = page.films.first { it.tmdbId == 672 }
        assertTrue(hp2.available)
        assertEquals("2", hp2.id)
    }

    @Test fun `hub - grande franchise des qu'un film est au catalogue`() = runBlocking {
        db.movies().upsert(listOf(movie("3", "Harry Potter 3", "tmdb:673")))
        val hub = repo().sagaHub(pid)
        val hp = hub.single()
        assertEquals(1241, hp.id)
        assertEquals("Le monde des sorciers", hp.name)
        assertEquals(1, hp.count)
        assertTrue(hp.curated)
    }

    @Test fun `ma liste - titre hors catalogue retenu, puis lisible des son arrivee`() = runBlocking {
        db.movies().upsert(listOf(movie("1", "Harry Potter 1", "tmdb:671")))
        val r = repo()
        val missing = r.sagaPage(pid, 1241)!!.films.first { it.tmdbId == 672 }
        assertTrue(r.toggleOffCatalog(pid, missing))

        val before = r.grid(pid, GridSpec.Favorites(null)).second
        assertEquals(listOf("tmdb:672"), before.map { it.key })
        assertFalse(before.single().available)
        assertEquals("La Chambre des secrets", before.single().title)

        // le film arrive au catalogue : même entrée de Ma liste, désormais lisible
        db.movies().upsert(listOf(movie("2", "Harry Potter 2", "tmdb:672")))
        val after = r.grid(pid, GridSpec.Favorites(null)).second.single()
        assertTrue(after.available)
        assertEquals("2", after.id)

        // retirer
        assertFalse(r.toggleOffCatalog(pid, missing))
        assertTrue(r.grid(pid, GridSpec.Favorites(null)).second.isEmpty())
    }

    @Test fun `sagas editorialisees - format, taille, aucun doublon`() {
        val file = File("src/main/assets/curated_sagas.json")
        assertTrue("fichier trop lourd", file.length() < 15_000)
        val sagas = CuratedSagaParser.parse(file.readText())
        assertTrue("sagas : ${sagas.size}", sagas.size in 20..40)
        assertEquals(sagas.size, sagas.map { it.key }.toSet().size)
        val cols = sagas.flatMap { it.collectionIds }
        assertEquals("collection dans deux sagas", cols.size, cols.toSet().size)
        for (s in sagas) {
            assertTrue(s.key, s.collectionIds.isNotEmpty())
            assertEquals(s.key, s.chronologicalOrderIds.size, s.chronologicalOrderIds.toSet().size)
            assertEquals(s.key, s.relatedSeriesTmdbIds.size, s.relatedSeriesTmdbIds.toSet().size)
            // un film ajouté hors collection doit trouver sa place dans l'ordre du récit
            if (s.chronologicalOrderIds.isNotEmpty()) assertTrue(s.key, s.chronologicalOrderIds.containsAll(s.extraMovieIds))
        }
    }
}
