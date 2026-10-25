package com.sokoul.iptv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.net.HttpModule
import com.sokoul.iptv.data.net.providers.ApiHttp
import com.sokoul.iptv.data.net.providers.ApiKeys
import com.sokoul.iptv.data.net.providers.ApiService
import com.sokoul.iptv.data.net.providers.ChannelDbClient
import com.sokoul.iptv.data.net.providers.FanartClient
import com.sokoul.iptv.data.net.providers.IoChannel
import com.sokoul.iptv.data.net.providers.IoLogo
import com.sokoul.iptv.data.net.providers.OfferType
import com.sokoul.iptv.data.net.providers.OmdbClient
import com.sokoul.iptv.data.net.providers.ProviderHealth
import com.sokoul.iptv.data.net.providers.ServiceState
import com.sokoul.iptv.data.net.providers.StreamingClient
import com.sokoul.iptv.data.net.providers.SubtitleFile
import com.sokoul.iptv.data.net.providers.SubtitlesClient
import com.sokoul.iptv.data.net.providers.TasteDiveClient
import com.sokoul.iptv.data.net.providers.TvdbClient
import com.sokoul.iptv.data.net.providers.TvmazeClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
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
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Sources de données externes (phase 8) : formats réels, cache, disjoncteur, aucune clé dans le cache ni les journaux. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProvidersTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val json = HttpModule.json()
    private fun obj(s: String) = json.parseToJsonElement(s).jsonObject

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    /** Faux serveur : compte les appels et répond avec [reply]. */
    private class FakeServer(var reply: (String) -> Pair<Int, String>) {
        val calls = mutableListOf<String>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            calls += url
            val (code, body) = reply(url)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
    }

    private fun api(server: FakeServer, health: ProviderHealth = ProviderHealth()) = ApiHttp(server.client, json, db.tmdb(), health)

    // --- Formats -------------------------------------------------------------------------------------

    @Test fun `fanart logo francais puis anglais le plus aime et fond sans texte`() {
        val o = obj(
            """{"hdmovielogo":[{"lang":"en","likes":"22","url":"https://a/en22.png"},{"lang":"fr","likes":"1","url":"https://a/fr.png"},{"lang":"ru","likes":"90","url":"https://a/ru.png"}],
               "moviebackground":[{"lang":"en","likes":"50","url":"https://a/bg-en.jpg"},{"lang":"","likes":"3","url":"https://a/bg-none.jpg"}]}""",
        )
        val art = FanartClient.parse(o, "hdmovielogo", "moviebackground", "hdmovieclearart")!!
        assertEquals("https://a/fr.png", art.logo)
        assertEquals("https://a/bg-none.jpg", art.background)
        assertNull(art.clearart)
        val en = FanartClient.best(obj("""{"l":[{"lang":"en","likes":"2","url":"https://a/2.png"},{"lang":"en","likes":"9","url":"https://a/9.png"}]}"""), "l", false)
        assertEquals("https://a/9.png", en)
        assertNull(FanartClient.parse(obj("{}"), "a", "b", "c"))
    }

    @Test fun `tvdb episodes francais sans les speciaux et images completes`() {
        val o = obj(
            """{"status":"success","data":{"id":81189,"episodes":[
               {"seasonNumber":0,"number":1,"name":"Goof","image":"/banners/e/1.jpg"},
               {"seasonNumber":1,"number":1,"name":"Chute libre","overview":"Walter apprend…","image":"/banners/e/2.jpg","runtime":58},
               {"seasonNumber":1,"number":2,"name":null,"overview":"  ","image":"https://x/3.jpg","runtime":0}]}}""",
        )
        val eps = TvdbClient.parseEpisodes(o)
        assertEquals(2, eps.size)
        assertEquals("Chute libre", eps[0].name)
        assertEquals("https://artworks.thetvdb.com/banners/e/2.jpg", eps[0].image)
        assertEquals(58, eps[0].runtimeMin)
        assertNull(eps[1].name)
        assertNull(eps[1].overview)
        assertNull(eps[1].runtimeMin)
    }

    @Test fun `tvmaze prochain episode`() {
        val o = obj("""{"id":1,"status":"Running","_embedded":{"nextepisode":{"season":3,"number":5,"name":"X","airstamp":"2026-11-12T02:00:00+00:00"}}}""")
        val n = TvmazeClient.parseNext(o)!!
        assertEquals(3, n.season)
        assertEquals(5, n.episode)
        assertEquals(1794448800000L, n.airsAt.toEpochMilli())
        assertNull(TvmazeClient.parseNext(obj("""{"id":1,"_links":{}}""")))
    }

    @Test fun `movie of the night une ligne par service la plus accessible d'abord`() {
        val o = obj(
            """{"streamingOptions":{"fr":[
               {"service":{"id":"apple","name":"Apple TV","themeColorCode":"#000000"},"type":"rent"},
               {"service":{"id":"prime","name":"Prime Video","themeColorCode":"#00A8E1"},"type":"addon","addon":{"id":"maxfr","name":"HBO Max","themeColorCode":"#000000"}},
               {"service":{"id":"prime","name":"Prime Video","themeColorCode":"#00A8E1"},"type":"buy"},
               {"service":{"id":"prime","name":"Prime Video","themeColorCode":"#00A8E1"},"type":"rent"},
               {"service":{"id":"netflix","name":"Netflix","themeColorCode":"#E50914"},"type":"subscription"},
               {"service":{"id":"hbo","name":"HBO Max"},"type":"subscription"}]}}""",
        )
        val offers = StreamingClient.parse(o, "fr")
        assertEquals("abonnement, option, puis location (ordre de la réponse)", listOf("HBO Max", "Netflix", "Apple TV", "Prime Video"), offers.map { it.service })
        assertEquals("HBO Max direct (abonnement) plutôt qu'en option", OfferType.SUBSCRIPTION, offers.first { it.service == "HBO Max" }.type)
        assertEquals(OfferType.SUBSCRIPTION, offers[0].type)
        assertEquals(OfferType.RENT, offers.first { it.service == "Prime Video" }.type)
        assertEquals(0xFFE50914, offers.first { it.service == "Netflix" }.color)
        assertTrue(StreamingClient.parse(o, "us").isEmpty())
    }

    @Test fun `tastedive formats recent et ancien`() {
        assertEquals(listOf("The Martian", "Inception"), TasteDiveClient.parse(obj("""{"similar":{"info":[{"name":"Interstellar"}],"results":[{"name":"The Martian"},{"name":"Inception"}]}}""")))
        assertEquals(listOf("Heat"), TasteDiveClient.parse(obj("""{"Similar":{"Info":[],"Results":[{"Name":"Heat","Type":"movie"}]}}""")))
        assertTrue(TasteDiveClient.parse(obj("""{"error":"x"}""")).isEmpty())
    }

    @Test fun `omdb notes au format francais`() {
        val r = OmdbClient.parse(
            obj("""{"Response":"True","imdbRating":"8.7","Metascore":"74","Ratings":[{"Source":"Internet Movie Database","Value":"8.7/10"},{"Source":"Rotten Tomatoes","Value":"73%"}]}"""),
        )!!
        assertEquals("8,7", r.imdb)
        assertEquals("73 %", r.rottenTomatoes)
        assertEquals("74", r.metacritic)
        assertNull(OmdbClient.parse(obj("""{"Response":"False","Error":"Movie not found!"}""")))
        assertNull(OmdbClient.parse(obj("""{"Response":"True","imdbRating":"N/A","Metascore":"N/A"}""")))
    }

    @Test fun `sous-titres opensubtitles et subdl`() {
        val os = SubtitlesClient.parseOpenSubtitles(
            obj("""{"data":[{"attributes":{"language":"fr","release":"Interstellar.2014.1080p","hearing_impaired":true,"files":[{"file_id":123,"file_name":"a.srt"}]}},{"attributes":{"files":[]}}]}"""),
        )
        assertEquals(1, os.size)
        assertEquals("123", os[0].ref)
        assertTrue(os[0].hearingImpaired)
        val sd = SubtitlesClient.parseSubdl(
            obj("""{"status":true,"subtitles":[{"release_name":"Interstellar.WEB","lang":"french","url":"/subtitle/1-2.zip"},{"url":"https://evil/x.zip"},{"url":"/../x.zip"}]}"""),
        )
        assertEquals(listOf("/subtitle/1-2.zip"), sd.map { it.ref })
        assertTrue(SubtitlesClient.parseSubdl(obj("""{"status":false,"error":"x"}""")).isEmpty())
    }

    @Test fun `fichier srt vtt zip et windows-1252`() {
        val srt = "1\r\n00:00:01,000 --> 00:00:03,500\r\n<i>Bonjour</i>\r\n\r\n2\r\n00:00:04,000 --> 00:00:06,000\r\nDeux\r\nlignes\r\n"
        val cues = SubtitleFile.parse(srt)
        assertEquals(2, cues.size)
        assertEquals("Bonjour", cues[0].text)
        assertEquals("Deux\nlignes", cues[1].text)
        assertEquals("Bonjour", SubtitleFile.at(cues, 2_000)?.text)
        assertNull(SubtitleFile.at(cues, 3_700))
        assertEquals("Deux\nlignes", SubtitleFile.at(cues, 5_999)?.text)
        assertNull(SubtitleFile.at(cues, 500))

        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000 align:start\nCourt\n\n01:00:00.000 --> 01:00:01.000\nLong\n"
        assertEquals(listOf(1_000L, 3_600_000L), SubtitleFile.parse(vtt).map { it.startMs })

        val zip = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z -> z.putNextEntry(ZipEntry("readme.txt")); z.write("x".toByteArray()); z.putNextEntry(ZipEntry("film.FR.srt")); z.write(srt.toByteArray()) }
        }.toByteArray()
        assertEquals(2, SubtitleFile.decode(zip).size)

        val latin = "1\n00:00:01,000 --> 00:00:02,000\nÉté à Noël\n".toByteArray(charset("windows-1252"))
        assertEquals("Été à Noël", SubtitleFile.decode(latin)[0].text)
    }

    @Test fun `iptv-org index logos png francais prioritaire et recherche par id puis nom`() {
        val channels = listOf(
            IoChannel("TF1.fr", "TF1", listOf("TF 1"), "FR"),
            IoChannel("TF1.be", "TF1", country = "BE"),
            IoChannel("Old.fr", "Old", country = "FR", closed = "2020-01-01"),
            IoChannel("Adult.fr", "Adult", country = "FR", is_nsfw = true),
            IoChannel("Far.jp", "Far", country = "JP"),
        )
        val logos = listOf(
            IoLogo("TF1.fr", null, "SVG", 500, "https://l/tf1.svg"),
            IoLogo("TF1.fr", null, "PNG", 300, "https://l/tf1-300.png"),
            IoLogo("TF1.fr", "feedHD", "PNG", 900, "https://l/tf1-feed.png"),
            IoLogo("TF1.be", null, "PNG", 300, "https://l/tf1-be.png"),
            IoLogo("Old.fr", null, "PNG", 300, "https://l/old.png"),
            IoLogo("Adult.fr", null, "PNG", 300, "https://l/a.png"),
            IoLogo("Far.jp", null, "PNG", 300, "https://l/far.png"),
        )
        val idx = ChannelDbClient.buildIndex(channels, logos)
        assertEquals("https://l/tf1-300.png", ChannelDbClient.lookup(idx, "tf1.fr", "zzz"))
        assertEquals("https://l/tf1-be.png", ChannelDbClient.lookup(idx, "TF1.be", "tf1"))
        assertEquals("par nom : la chaîne française", "https://l/tf1-300.png", ChannelDbClient.lookup(idx, null, "tf1"))
        assertNull(ChannelDbClient.lookup(idx, null, "old"))
        assertNull(ChannelDbClient.lookup(idx, null, "adult"))
        assertNull(ChannelDbClient.lookup(idx, null, "far"))
    }

    // --- Cache, disjoncteur, clés ---------------------------------------------------------------------

    @Test fun `cache sans la cle et aucun second appel tant qu'il est valide`() = runBlocking {
        val server = FakeServer { 200 to """{"ok":1}""" }
        val http = api(server)
        val url = "https://x.test/a?api_key=SECRET123&q=1".toHttpUrl()
        assertNotNull(http.getJson(ApiService.FANART, url, ttlMs = ApiHttp.DAY, secret = "SECRET123"))
        assertNotNull(http.getJson(ApiService.FANART, url, ttlMs = ApiHttp.DAY, secret = "SECRET123"))
        assertEquals(1, server.calls.size)
        val key = ApiHttp.cacheKey(ApiService.FANART, url, "SECRET123")
        assertFalse(key, "SECRET123" in key)
        assertTrue(key.startsWith("fanart:"))
        assertNotNull(db.tmdb().get(key))
    }

    @Test fun `401 coupe le service 24 h sans nouvel appel puis reprend`() = runBlocking {
        val server = FakeServer { 401 to """{"error":"bad key"}""" }
        val health = ProviderHealth()
        var now = 1_000_000L
        health.clock = { now }
        val http = api(server, health)
        val url = "https://x.test/b".toHttpUrl()
        assertNull(http.getJson(ApiService.OMDB, url, ttlMs = 0))
        assertTrue(health.state(ApiService.OMDB) is ServiceState.Blocked)
        repeat(5) { assertNull(http.getJson(ApiService.OMDB, url, ttlMs = 0)) }
        assertEquals("aucune rafale après un refus", 1, server.calls.size)
        // un autre service n'est pas touché
        server.reply = { 200 to "{}" }
        assertNotNull(http.getJson(ApiService.TVMAZE, url, ttlMs = 0))
        now += ProviderHealth.BLOCK_AUTH_MS + 1
        assertNotNull(http.getJson(ApiService.OMDB, url, ttlMs = 0))
        assertEquals(ServiceState.Ok, health.state(ApiService.OMDB))
    }

    @Test fun `429 coupe une heure et la derniere reponse connue reste servie`() = runBlocking {
        var code = 200
        val server = FakeServer { code to """{"v":1}""" }
        val health = ProviderHealth()
        var now = 0L
        health.clock = { now }
        val http = api(server, health)
        val url = "https://x.test/c".toHttpUrl()
        assertNotNull(http.getJson(ApiService.TVMAZE, url, ttlMs = 10))
        now = 100
        code = 429
        assertEquals("réponse périmée servie plutôt que rien", 1, (http.getJson(ApiService.TVMAZE, url, ttlMs = 10) as JsonObject).size)
        assertTrue(health.isBlocked(ApiService.TVMAZE))
        now += ProviderHealth.BLOCK_QUOTA_MS + 1
        assertFalse(health.isBlocked(ApiService.TVMAZE))
    }

    @Test fun `404 memorise comme absent`() = runBlocking {
        val server = FakeServer { 404 to "" }
        val http = api(server)
        val url = "https://x.test/d".toHttpUrl()
        assertNull(http.getJson(ApiService.FANART, url, ttlMs = ApiHttp.DAY))
        assertNull(http.getJson(ApiService.FANART, url, ttlMs = ApiHttp.DAY))
        assertEquals(1, server.calls.size)
    }

    @Test fun `sans cle aucun appel`() = runBlocking {
        val server = FakeServer { 200 to "{}" }
        val http = api(server)
        val keys = ApiKeys(SettingsStore(context))
        // clés de build vides dans les tests (pas de local.properties lu par les fixtures) : rien ne part
        if (ApiService.OMDB.buildKey.isEmpty()) assertNull(OmdbClient(http, keys).ratings("tt0816692"))
        if (ApiService.OPENSUBTITLES.buildKey.isEmpty() && ApiService.SUBDL.buildKey.isEmpty()) {
            assertFalse(SubtitlesClient(http, keys).available())
        }
        assertEquals(0, server.calls.size)
    }

    @Test fun `cle saisie dans reglages prioritaire sur celle du build`() {
        assertEquals("abc", ApiKeys.resolve(ApiService.FANART, mapOf("fanart" to "abc")))
        assertEquals(ApiService.FANART.buildKey, ApiKeys.resolve(ApiService.FANART, mapOf("fanart" to " ")))
        assertEquals("clé saisie", ApiKeys.origin(ApiService.OMDB, mapOf("omdb" to "k")))
        assertEquals("c16c…80be", ApiKeys.masked("c16c0000000080be"))
    }

    @Test fun `journaux sans cle ni jeton`() {
        val s = HttpModule.redact("GET https://x/api?api_key=AAA&apikey=BBB&k=CCC&q=ok X-API-Key: DDD Authorization: Bearer EEE")
        listOf("AAA", "BBB", "CCC", "DDD", "EEE").forEach { assertFalse("$it visible : $s", it in s) }
        assertTrue("q=ok" in s)
    }
}
