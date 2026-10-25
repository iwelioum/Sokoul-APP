package com.sokoul.iptv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.db.TmdbMatchEntity
import com.sokoul.iptv.data.db.TmdbMetaEntity
import com.sokoul.iptv.data.net.HttpModule
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.data.repo.CatalogIndexer
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.ExploreFilter
import com.sokoul.iptv.data.repo.ExploreQuery
import com.sokoul.iptv.data.repo.GridSort
import com.sokoul.iptv.data.repo.MicroGenre
import com.sokoul.iptv.data.repo.Mood
import com.sokoul.iptv.data.repo.TmdbGenres
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.packIds
import com.sokoul.iptv.data.repo.packKeywords
import com.sokoul.iptv.ui.ParentalGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Explorer, ambiances, micro-genres et indexation du catalogue (base Room réelle, TMDB simulé). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExploreTest {
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

    private fun movie(id: String, title: String, added: Long = 0, quality: String? = null, groupKey: String? = "k$id") =
        MovieEntity(pid, id, title, title, title.lowercase(), 2020, null, quality, null, null, "c", "mkv", null, added, null, 0, 1, groupKey)

    private fun meta(id: Int, title: String, genres: List<Int>, rating: Double = 7.0, votes: Int = 5_000, runtime: Int = 100,
                     keywords: List<String> = emptyList(), year: Int = 2020, lang: String = "en", cert: String? = null) =
        TmdbMetaEntity(
            ContentType.MOVIE, id, title, null, null, null, null, null, null, null, null, rating, year, runtime, null, null, null, cert, 1,
            originalLanguage = lang, genreIds = packIds(genres), voteCount = votes, keywords = packKeywords(keywords),
        )

    /** Films « kN » reliés à leur fiche TMDB N. */
    private suspend fun catalog(vararg m: Pair<MovieEntity, TmdbMetaEntity>) {
        db.movies().upsert(m.map { it.first })
        db.browse().putMetas(m.map { it.second })
        db.browse().putMatches(m.map { (mv, t) -> TmdbMatchEntity(pid, ContentType.MOVIE, mv.groupKey!!, t.tmdbId, 1) })
    }

    private suspend fun titles(f: ExploreFilter, sort: GridSort = GridSort.DEFAULT) =
        db.browse().explore(ExploreQuery.build(pid, ContentType.MOVIE, f, sort, 50)).map { it.meta.title }

    // --- genres -------------------------------------------------------------------------------------

    @Test fun `genres - identifiants releves sur TMDB, separes films et series`() {
        // relevé themoviedb.org/movie et /tv (25/10/2026)
        assertEquals(setOf(28, 16, 12, 99, 18, 10751, 14, 36, 27, 35, 80, 10402, 9648, 10752, 10749, 878, 10770, 53, 37), TmdbGenres.movie.keys)
        assertEquals(setOf(10759, 16, 99, 18, 10751, 10762, 35, 80, 9648, 10763, 10764, 10765, 10766, 10767, 10768, 37), TmdbGenres.tv.keys)
        for (t in listOf(ContentType.MOVIE, ContentType.SERIES)) {
            val known = TmdbGenres.of(t).keys
            val rules = Mood.entries.map { it.rule(t) } + MicroGenre.entries.map { it.rule(t) }
            rules.forEach { r ->
                assertTrue("$t : genre inconnu dans $r", known.containsAll(r.anyGenre + r.noGenre))
                assertTrue("mots-clés en minuscules", r.keywords.all { it == it.lowercase() })
            }
        }
    }

    // --- Explorer et ambiances ---------------------------------------------------------------------------

    @Test fun `ambiances - genres, exclusions, mots-cles requis et duree`() = runBlocking {
        catalog(
            movie("1", "Peur") to meta(1, "Peur", listOf(TmdbGenres.HORROR), keywords = listOf("suspense")),
            movie("2", "Peur sans mot-clé") to meta(2, "Peur sans mot-clé", listOf(TmdbGenres.HORROR), rating = 8.0),
            movie("3", "Comédie") to meta(3, "Comédie", listOf(TmdbGenres.COMEDY), runtime = 95),
            movie("4", "Comédie dramatique") to meta(4, "Comédie dramatique", listOf(TmdbGenres.COMEDY, TmdbGenres.DRAMA)),
            movie("5", "Comédie fleuve") to meta(5, "Comédie fleuve", listOf(TmdbGenres.COMEDY), runtime = 150),
            movie("6", "Énigme") to meta(6, "Énigme", listOf(TmdbGenres.SCIFI), rating = 7.5, keywords = listOf("time loop")),
            movie("7", "SF banale") to meta(7, "SF banale", listOf(TmdbGenres.SCIFI), rating = 8.5),
        )
        // mot-clé recherché en tête, même avec une note plus basse
        assertEquals(listOf("Peur", "Peur sans mot-clé"), titles(ExploreFilter(mood = Mood.FRISSONS)))
        assertEquals(listOf("Comédie"), titles(ExploreFilter(mood = Mood.LEGER)))
        // « Puzzle mental » exige un mot-clé
        assertEquals(listOf("Énigme"), titles(ExploreFilter(mood = Mood.PUZZLE)))
        assertEquals(listOf("Énigme"), titles(ExploreFilter(micro = MicroGenre.TIME)))
    }

    @Test fun `explorer - decennie, note, duree, langue, 4K, pepites, cle tmdb directe`() = runBlocking {
        catalog(
            movie("1", "Années 90") to meta(1, "Années 90", listOf(TmdbGenres.DRAMA), year = 1994, lang = "fr", runtime = 85),
            movie("2", "Récent") to meta(2, "Récent", listOf(TmdbGenres.DRAMA), rating = 8.1, votes = 900, runtime = 130),
            movie("3", "4K", quality = "4K UHD") to meta(3, "4K", listOf(TmdbGenres.ACTION), rating = 6.5),
        )
        // version à clé « tmdb:4 » : fiche trouvée sans correspondance enregistrée
        db.movies().upsert(listOf(movie("9", "Direct", groupKey = "tmdb:4")))
        db.browse().putMetas(listOf(meta(4, "Direct", listOf(TmdbGenres.WESTERN))))

        assertEquals(listOf("Années 90"), titles(ExploreFilter(decade = 1990)))
        assertEquals(listOf("Années 90"), titles(ExploreFilter(language = "fr")))
        assertEquals(listOf("Récent"), titles(ExploreFilter(minRating = 8.0)))
        assertEquals(listOf("Années 90"), titles(ExploreFilter(duration = com.sokoul.iptv.data.repo.DurationBand.SHORT)))
        assertEquals(listOf("Récent"), titles(ExploreFilter(duration = com.sokoul.iptv.data.repo.DurationBand.LONG)))
        assertEquals(listOf("4K"), titles(ExploreFilter(uhd = true)))
        assertEquals(listOf("4K"), titles(ExploreFilter(micro = MicroGenre.SPECTACLE)))
        assertEquals(listOf("Récent"), titles(ExploreFilter(micro = MicroGenre.GEMS)))
        assertEquals(listOf("Direct"), titles(ExploreFilter(genre = TmdbGenres.WESTERN)))
        assertEquals(listOf("4K", "Années 90", "Direct", "Récent"), titles(ExploreFilter(), GridSort.TITLE))
    }

    @Test fun `explorer - une seule carte par fiche TMDB, la plus fournie en versions`() = runBlocking {
        db.categories().upsert(listOf(com.sokoul.iptv.data.db.CategoryEntity(pid, ContentType.MOVIE, "c", "[FR] ACTION", 0, 1)))
        // même film : un groupe par titre (relié par l'app) et deux versions sous la clé « tmdb:7 »
        catalog(movie("1", "Batman Begins", groupKey = "t:batman begins|2005") to meta(7, "Batman Begins", listOf(TmdbGenres.ACTION)))
        db.movies().upsert(listOf(movie("2", "Batman Begins", groupKey = "tmdb:7"), movie("3", "Batman Begins 4K", groupKey = "tmdb:7")))
        val (ix, enrich) = indexer()
        val repo = com.sokoul.iptv.data.repo.BrowseRepository(db, VodRepository(db, settings, gate), enrich, TmdbClient(fakeTmdb(), HttpModule.json(), db.tmdb()), gate, settings)
        val works = repo.explore(pid, ContentType.MOVIE, ExploreFilter(genre = TmdbGenres.ACTION))
        assertEquals(1, works.size)
        assertEquals("tmdb:7", works.single().key)
        assertEquals(2, works.single().versions)
        assertTrue(ix.coverage(pid).complete)
    }

    // --- indexation -----------------------------------------------------------------------------------

    private val requests = AtomicInteger()

    /** TMDB simulé : « Film N » ↔ fiche 1000+N, horreur, mot-clé « suspense ». */
    private fun fakeTmdb() = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        val url = chain.request().url
        val body = when {
            url.encodedPath.startsWith("/3/search/movie") -> {
                val q = url.queryParameter("query")!!
                val n = q.removePrefix("Film ").toInt()
                """{"results":[{"id":${1000 + n},"title":"$q","release_date":"2020-05-01"}]}"""
            }
            url.encodedPath.startsWith("/3/movie/") -> {
                val id = url.pathSegments.last().toInt()
                """{"id":$id,"title":"Film ${id - 1000}","release_date":"2020-05-01","vote_average":7.0,"vote_count":321,"original_language":"en",
                   "genres":[{"id":27,"name":"Horreur"}],"keywords":{"keywords":[{"id":288394,"name":"Suspense"}]}}"""
            }
            else -> """{"results":[]}"""
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody()).build()
    }.build()

    private suspend fun indexer(): Pair<CatalogIndexer, EnrichmentRepository> {
        settings.update { it.copy(tmdbApiKey = "test") }
        val tmdb = TmdbClient(fakeTmdb(), HttpModule.json(), db.tmdb())
        val vod = VodRepository(db, settings, gate)
        val enrich = EnrichmentRepository(db, tmdb, settings, vod)
        return CatalogIndexer(db, tmdb, settings, enrich) to enrich
    }

    @Test fun `indexation - tout le catalogue, ecrit par lots de 45, sans cache TMDB`() = runBlocking(Dispatchers.IO) {
        db.movies().upsert((1..100).map { movie("$it", "Film $it", added = it.toLong()) })
        // deuxième version de « Film 100 » : une seule œuvre, indexée une seule fois
        db.movies().upsert(listOf(movie("100b", "Film 100", added = 100, groupKey = "k100")))
        val (ix, enrich) = indexer()
        assertEquals(0, ix.coverage(pid).percent)
        assertTrue(ix.run(pid))
        val cov = ix.coverage(pid)
        assertEquals(100, cov.total); assertTrue(cov.complete)
        // 100 titres = 3 écritures (45 + 45 + 10), donc 3 rafraîchissements d'écran seulement
        assertEquals(3L, enrich.updates.value)
        assertEquals(100, db.browse().explore(ExploreQuery.build(pid, ContentType.MOVIE, ExploreFilter(mood = Mood.FRISSONS), GridSort.DEFAULT, 500)).size)
        // l'indexation n'encombre pas le cache des réponses TMDB
        assertEquals(0, db.query("SELECT COUNT(*) FROM tmdb_cache", null).use { it.moveToFirst(); it.getInt(0) })
        // relancée : plus rien à faire, aucune requête
        val before = requests.get()
        assertTrue(ix.run(pid))
        assertEquals(before, requests.get())
    }

    @Test fun `indexation - en pause tant qu'un lecteur tourne`() = runBlocking(Dispatchers.IO) {
        db.movies().upsert((1..5).map { movie("$it", "Film $it") })
        val (ix, _) = indexer()
        val playing = CompletableDeferred<Unit>()
        ix.awaitIdle = { playing.await() }
        val job = async { ix.run(pid) }
        delay(400)
        assertEquals("aucune requête pendant la lecture", 0, requests.get())
        playing.complete(Unit)
        assertTrue(job.await())
        assertTrue(ix.coverage(pid).complete)
    }
}
