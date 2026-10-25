package com.sokoul.iptv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.net.HttpModule
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.providers.ApiHttp
import com.sokoul.iptv.data.net.providers.ApiKeys
import com.sokoul.iptv.data.net.providers.FanartClient
import com.sokoul.iptv.data.net.providers.NextEpisode
import com.sokoul.iptv.data.net.providers.OfferType
import com.sokoul.iptv.data.net.providers.OmdbClient
import com.sokoul.iptv.data.net.providers.ProviderHealth
import com.sokoul.iptv.data.net.providers.Ratings
import com.sokoul.iptv.data.net.providers.StreamingClient
import com.sokoul.iptv.data.net.providers.StreamingOffer
import com.sokoul.iptv.data.net.providers.TasteDiveClient
import com.sokoul.iptv.data.net.providers.TvdbClient
import com.sokoul.iptv.data.net.providers.TvdbEpisode
import com.sokoul.iptv.data.net.providers.TvmazeClient
import com.sokoul.iptv.data.net.providers.ApiService
import com.sokoul.iptv.data.net.providers.ChannelDbClient
import com.sokoul.iptv.data.net.providers.DataSourceTester
import com.sokoul.iptv.data.net.providers.SubtitlesClient
import com.sokoul.iptv.ui.screens.settings.sourceStatus
import com.sokoul.iptv.data.repo.EpisodeMerge
import com.sokoul.iptv.data.repo.ExtrasRepository
import com.sokoul.iptv.ui.screens.vod.availabilityPills
import com.sokoul.iptv.ui.screens.vod.ratingBadges
import com.sokoul.iptv.ui.screens.vod.upcomingLabel
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** Intégration des sources secondaires sur la fiche : indépendance des sources, ordre des replis, libellés. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExtrasTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val json = HttpModule.json()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun info(inProduction: Boolean = false) = TmdbInfo(
        id = 157336, title = "Interstellar", overview = null, poster = null, backdrop = null, logo = null, rating = 8.4, year = 2014,
        runtimeMin = 169, genres = emptyList(), cast = emptyList(), director = null, trailerYoutubeKey = null,
        imdbId = "tt0816692", tvdbId = 81189, inProduction = inProduction,
    )

    /** Faux serveur aiguillé par hôte ; compte les appels. */
    private fun repo(calls: MutableList<String>, route: (String) -> Pair<Int, String>): ExtrasRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            synchronized(calls) { calls += url }
            val (code, body) = route(url)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val settings = SettingsStore(context)
        runBlocking { settings.update { it.copy(apiKeys = mapOf("fanart" to "f", "motn" to "m", "omdb" to "", "tvdb" to "", "tastedive" to "")) } }
        val api = ApiHttp(client, json, db.tmdb(), ProviderHealth())
        val keys = ApiKeys(settings)
        return ExtrasRepository(FanartClient(api, keys), OmdbClient(api, keys), StreamingClient(api, keys), TvmazeClient(api), TvdbClient(api, keys), TasteDiveClient(api, keys))
    }

    private val motn = """{"streamingOptions":{"fr":[{"service":{"id":"netflix","name":"Netflix","themeColorCode":"#E50914"},"type":"subscription"}]}}"""

    @Test fun `une source en panne ne bloque pas les autres et la fiche garde TMDB`() = runBlocking {
        val calls = mutableListOf<String>()
        val r = repo(calls) { url ->
            when {
                "fanart.tv" in url -> 500 to "boom"
                "movieofthenight" in url -> 200 to motn
                else -> 404 to ""
            }
        }
        val x = r.extras(true, info())
        assertNull("fanart en panne : rien", x.art)
        assertEquals(listOf("Netflix"), x.offers.map { it.service })
        assertNull("OMDb sans clé : aucune note", x.ratings)
        assertNull("film : pas de prochain épisode", x.nextEpisode)
        assertTrue("aucun appel OMDb sans clé", calls.none { "omdbapi" in it })
        assertTrue("aucun appel TVmaze pour un film", calls.none { "tvmaze" in it })
    }

    @Test fun `prochain episode seulement pour une serie en cours`() = runBlocking {
        val calls = mutableListOf<String>()
        val r = repo(calls) { url ->
            when {
                "lookup/shows" in url -> 200 to """{"id":1,"status":"Running"}"""
                "shows/1" in url -> 200 to """{"id":1,"_embedded":{"nextepisode":{"season":3,"number":4,"name":"X","airstamp":"2026-11-10T02:00:00+00:00"}}}"""
                else -> 404 to ""
            }
        }
        assertNull(r.extras(false, info(inProduction = false)).nextEpisode)
        assertTrue("série terminée : TVmaze n'est pas appelé", calls.none { "tvmaze" in it })
        val n = r.extras(false, info(inProduction = true)).nextEpisode
        assertEquals(3 to 4, n?.season to n?.episode)
    }

    @Test fun `episode textes TMDB puis TVDB francais puis fournisseur`() {
        val e = EpisodeEntity(1, "s", "e1", 1, 1, "Show - S01E01", "An English plot from the provider.", "http://prov/img.jpg", null, "mkv", null, null)
        val tvdbFr = TvdbEpisode(1, 1, "Chute libre", "Une femme obsédée par sa popularité sur les réseaux sociaux.", "https://artworks.thetvdb.com/x.jpg", 63)
        val tm = TmdbEpisode(1, 1, "Chute libre (TMDB)", "Résumé TMDB.", "https://image.tmdb.org/t/p/w500/a.jpg", 60)

        val withTmdb = EpisodeMerge.merge(e, tm, null, tvdbFr)
        assertEquals("Chute libre (TMDB)", withTmdb.title)
        assertEquals("Résumé TMDB.", withTmdb.overview)
        assertTrue(withTmdb.image!!.contains("tmdb"))

        val withTvdb = EpisodeMerge.merge(e, null, "fond", tvdbFr)
        assertEquals("Chute libre", withTvdb.title)
        assertTrue(withTvdb.overviewFr)
        assertEquals("https://artworks.thetvdb.com/x.jpg", withTvdb.image)
        assertEquals(63, withTvdb.runtimeMin)

        // résumé TVDB non traduit (anglais) : on garde celui du fournisseur, signalé comme non français
        val tvdbEn = tvdbFr.copy(overview = "A woman obsessed with her social media rating.", image = null)
        val v = EpisodeMerge.merge(e, null, "fond", tvdbEn)
        assertEquals("An English plot from the provider.", v.overview)
        assertEquals(false, v.overviewFr)
        assertEquals("http://prov/img.jpg", v.image)

        // sans rien : comportement d'avant (titre nettoyé, fond de la série)
        val bare = EpisodeMerge.merge(e.copy(image = null), null, "fond")
        assertEquals("Épisode 1", bare.title)
        assertEquals("fond", bare.image)
    }

    @Test fun `libelles notes services et prochain episode`() {
        assertEquals(listOf("IMDb" to "8,7", "RT" to "73 %"), ratingBadges(Ratings("8,7", "73 %", null)))
        assertEquals(emptyList<Pair<String, String>>(), ratingBadges(null))

        val offers = listOf(
            StreamingOffer("netflix", "Netflix", OfferType.SUBSCRIPTION, 0xFFE50914),
            StreamingOffer("apple", "Apple TV", OfferType.RENT, null),
            StreamingOffer("hbo", "HBO Max", OfferType.ADDON, null),
        )
        assertEquals(listOf("Netflix", "HBO Max"), availabilityPills(offers).map { it.first })

        val zone = ZoneId.of("Europe/Paris")
        val now = Instant.parse("2026-10-25T10:00:00Z")
        fun at(iso: String) = NextEpisode(3, 4, null, Instant.parse(iso))
        assertEquals("Prochain épisode : S3 · É4 aujourd'hui", upcomingLabel(at("2026-10-25T20:00:00Z"), now, zone))
        assertEquals("Prochain épisode : S3 · É4 demain", upcomingLabel(at("2026-10-26T20:00:00Z"), now, zone))
        assertEquals("Prochain épisode : S3 · É4 le mardi 10 novembre", upcomingLabel(at("2026-11-10T12:00:00Z"), now, zone))
        assertEquals("Prochain épisode : S3 · É4 le vendredi 8 janvier 2027", upcomingLabel(at("2027-01-08T12:00:00Z"), now, zone))
        assertNull("date passée : rien", upcomingLabel(at("2026-10-20T12:00:00Z"), now, zone))
    }

    @Test fun `tester ignore le cache et signale une cle refusee`() = runBlocking {
        var code = 200
        val calls = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            synchronized(calls) { calls += chain.request().url.toString() }
            val body = if (code == 200) """{"hdmovielogo":[{"url":"https://x/l.png","lang":"fr","likes":"1"}]}""" else """{"status":"error"}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val settings = SettingsStore(context)
        settings.update { it.copy(apiKeys = mapOf("fanart" to "cle-valide-1234")) }
        val api = ApiHttp(client, json, db.tmdb(), ProviderHealth())
        val keys = ApiKeys(settings)
        val tester = DataSourceTester(
            api, keys, FanartClient(api, keys), TvdbClient(api, keys), StreamingClient(api, keys), TasteDiveClient(api, keys),
            OmdbClient(api, keys), SubtitlesClient(api, keys), ChannelDbClient(context, client, json, db, api.health), client,
        )
        val ok = tester.test(ApiService.FANART)
        assertTrue(ok.message, ok.ok)
        // la clé est remplacée par une mauvaise : la réponse en cache ne doit pas faire croire qu'elle marche
        code = 401
        val bad = tester.test(ApiService.FANART)
        assertEquals(false, bad.ok)
        assertTrue(bad.message, "refusée" in bad.message)
        assertEquals("un appel par test", 2, calls.size)
        assertEquals("Clé refusée (pause 24 h)", sourceStatus(ApiService.FANART, "k", api.health.state(ApiService.FANART), null, false))
    }

    @Test fun `tester sans cle ne fait aucun appel`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain -> calls += chain.request().url.toString(); error("aucun appel attendu") }.build()
        val settings = SettingsStore(context)
        val api = ApiHttp(client, json, db.tmdb(), ProviderHealth())
        val keys = ApiKeys(settings)
        val tester = DataSourceTester(
            api, keys, FanartClient(api, keys), TvdbClient(api, keys), StreamingClient(api, keys), TasteDiveClient(api, keys),
            OmdbClient(api, keys), SubtitlesClient(api, keys), ChannelDbClient(context, client, json, db, api.health), client,
        )
        if (ApiService.OMDB.buildKey.isEmpty()) {
            val r = tester.test(ApiService.OMDB)
            assertTrue(r.message, r.message.startsWith("Aucune clé"))
        }
        assertEquals("Non utilisé : l'offre gratuite n'inclut pas la France", tester.test(ApiService.WATCHMODE).message)
        assertEquals(0, calls.size)
        assertEquals("Clé manquante", sourceStatus(ApiService.OMDB, "", null, null, false))
    }
}
