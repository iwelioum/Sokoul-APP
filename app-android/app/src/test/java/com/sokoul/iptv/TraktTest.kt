package com.sokoul.iptv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.Migrations
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.db.ProfileEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.repo.ProfileRepository
import com.sokoul.iptv.data.trakt.TraktClient
import com.sokoul.iptv.data.trakt.TraktException
import com.sokoul.iptv.data.trakt.TraktItem
import com.sokoul.iptv.data.trakt.TraktSync
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Trakt face à un faux serveur : association par code, scrobble, import des vus et de la liste par profil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TraktTest {
    private data class Call(val method: String, val path: String, val body: String, val auth: String?, val apiKey: String?)

    private val calls = mutableListOf<Call>()
    private var reply: (Call) -> Pair<Int, String> = { 200 to "{}" }
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val r = chain.request()
        val body = r.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
        val c = Call(r.method, r.url.encodedPath, body, r.header("Authorization"), r.header("trakt-api-key"))
        calls += c
        val (code, text) = reply(c)
        Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(text.toResponseBody("application/json".toMediaType())).build()
    }.build()

    private lateinit var settings: SettingsStore
    private lateinit var trakt: TraktClient
    private lateinit var db: AppDatabase

    @Before fun setUp() = runBlocking {
        settings = SettingsStore(ApplicationProvider.getApplicationContext())
        settings.update { it.copy(apiKeys = mapOf(TraktClient.K_ID to "CID123", TraktClient.K_SECRET to "SEC456")) }
        trakt = TraktClient(http, settings)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Migrations.seed(db)
            }).build()
    }

    @After fun tearDown() = db.close()

    private suspend fun link(profile: Long = ProfileEntity.MAIN) = settings.update {
        it.copy(apiKeys = it.apiKeys + mapOf(
            TraktClient.key(profile, "access") to "ACC", TraktClient.key(profile, "refresh") to "REF",
            TraktClient.key(profile, "expiry") to (System.currentTimeMillis() + 30L * 86_400_000).toString(),
        ))
    }

    @Test fun `association par code pour un profil`() = runBlocking {
        var polls = 0
        reply = { c ->
            when (c.path) {
                "/oauth/device/code" -> 200 to """{"device_code":"DEV","user_code":"AB12CD","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":5}"""
                "/oauth/device/token" -> if (++polls == 1) 400 to "" else 200 to """{"access_token":"ACC","refresh_token":"REF","expires_in":7776000}"""
                "/users/settings" -> 200 to """{"user":{"username":"lea"}}"""
                else -> 404 to ""
            }
        }
        val code = trakt.deviceCode()
        assertEquals("AB12CD", code.userCode)
        assertFalse("400 = pas encore validé", trakt.pollDevice(code, 2))
        assertTrue(trakt.pollDevice(code, 2))
        assertTrue(trakt.linked(2))
        assertFalse("jeton propre au profil", trakt.linked(ProfileEntity.MAIN))
        assertEquals("lea", trakt.username(2))
        assertTrue(calls.all { it.apiKey == "CID123" })
        assertTrue("secret envoyé dans le corps, jamais dans l'adresse", calls.none { "SEC456" in it.path })
        assertEquals("Bearer ACC", calls.last().auth)
        trakt.unlink(2)
        assertFalse(trakt.linked(2))
    }

    @Test fun `code refuse ou expire`() = runBlocking {
        reply = { 410 to "" }
        try {
            trakt.pollDevice(com.sokoul.iptv.data.trakt.TraktDeviceCode("D", "U", "u", 5, 0), 0)
            fail("410 doit arrêter l'attente")
        } catch (e: TraktException) {
            assertEquals(410, e.code)
        }
    }

    @Test fun `sans appli Trakt rien n est appele`() = runBlocking {
        settings.update { it.copy(apiKeys = emptyMap()) }
        assertFalse(trakt.appConfigured())
        trakt.scrobble(0, "start", TraktItem(1), 10f)
        assertTrue(calls.isEmpty())
    }

    @Test fun `scrobble d un episode et d un film`() = runBlocking {
        link()
        trakt.scrobble(0, "start", TraktItem(1396, 2, 5), 12.5f)
        trakt.scrobble(0, "stop", TraktItem(603), 91f)
        assertEquals("/scrobble/start", calls[0].path)
        assertTrue(calls[0].body, calls[0].body.contains(""""show":{"ids":{"tmdb":1396}}""") && calls[0].body.contains(""""episode":{"season":2,"number":5}"""))
        assertTrue(calls[1].body, calls[1].body.contains(""""movie":{"ids":{"tmdb":603}}""") && calls[1].body.contains(""""progress":91.0"""))
        assertTrue(calls.all { it.auth == "Bearer ACC" })
    }

    @Test fun `import des vus et de la liste dans le bon profil`() = runBlocking {
        val profiles = ProfileRepository(db)
        val lea = profiles.create("Léa", kids = false)
        link(lea)
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        db.movies().upsert(listOf(
            MovieEntity(pid, "m1", "Matrix", "Matrix", "matrix", 1999, null, null, null, null, "c", "mkv", null, 0, "603", 0, 1),
            MovieEntity(pid, "m2", "Dune", "Dune", "dune", 2021, null, null, null, null, "c", "mkv", null, 0, "438631", 1, 1),
        ))
        db.series().upsert(listOf(SeriesEntity(pid, "s1", "Breaking Bad", "Breaking Bad", "breaking bad", 2008, null, null, null, null, null, null, "s", 0, "1396", 0, 1)))
        db.series().upsertEpisodes(listOf(
            EpisodeEntity(pid, "s1", "e1", 1, 1, "Pilote", null, null, null, "mkv", null, null),
            EpisodeEntity(pid, "s1", "e2", 1, 2, "Le chat", null, null, null, "mkv", null, null),
        ))
        reply = { c ->
            when (c.path) {
                "/sync/watched/movies" -> 200 to """[{"plays":1,"movie":{"title":"Matrix","ids":{"trakt":1,"tmdb":603}}},{"movie":{"ids":{"tmdb":999}}}]"""
                "/sync/watched/shows" -> 200 to """[{"show":{"ids":{"tmdb":1396}},"seasons":[{"number":1,"episodes":[{"number":2}]}]}]"""
                "/sync/watchlist/movies" -> 200 to """[{"movie":{"ids":{"tmdb":438631}}}]"""
                "/sync/watchlist/shows" -> 200 to "[]"
                else -> 404 to ""
            }
        }
        val r = TraktSync(trakt, db, profiles).sync(lea)
        assertEquals(1, r.movies); assertEquals(1, r.episodes); assertEquals(1, r.listed)
        val prog = db.profiles().allProgress(pid)
        assertEquals(setOf("m1", "e2"), prog.filter { it.profileId == lea && it.finished }.map { it.itemId }.toSet())
        assertTrue("rien dans le profil Principal", prog.none { it.profileId == ProfileEntity.MAIN })
        assertEquals(listOf("m2"), db.library().allFavorites(pid).filter { it.profileId == lea && it.type == ContentType.MOVIE }.map { it.itemId })
        // deuxième passage : rien de neuf
        val again = TraktSync(trakt, db, profiles).sync(lea)
        assertEquals(0, again.movies + again.episodes + again.listed)
        assertNull(trakt.username(lea))
    }
}
