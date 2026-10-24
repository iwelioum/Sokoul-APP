package com.sokoul.iptv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.PinHasher
import com.sokoul.iptv.data.SecretBox
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryPrefEntity
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.FavoriteEntity
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.repo.BackupManager
import com.sokoul.iptv.data.repo.BackupPassphraseException
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.ui.ParentalGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Tests sur base Room en mémoire et DataStore réels (Robolectric, sans émulateur). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DataTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var gate: ParentalGate

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context)
        runBlocking {
            settings.update { it.copy(parentalPin = "", tmdbApiKey = "", activePlaylistId = 0) }
            settings.setPinAttempts(0, 0)
        }
        gate = ParentalGate(settings)
    }

    @After fun tearDown() = db.close()

    private fun channel(pid: Long, id: String, cat: String, name: String) =
        ChannelEntity(pid, id, id.toInt(), name, name.lowercase(), null, cat, null, null, name.lowercase(), null, null, null, 0, null, null, id.toInt(), 1)

    private fun movie(pid: Long, id: String, title: String) =
        MovieEntity(pid, id, title, title, title.lowercase(), null, null, null, null, null, "c", "mkv", null, 0, null, 0, 1)

    // --- Contrôle parental -----------------------------------------------------------------

    @Test fun `sans code tout est ouvert`() = runBlocking {
        assertTrue(gate.canAccessLocked())
    }

    @Test fun `code hache et deverrouillage`() = runBlocking {
        gate.setPin("2580")
        val stored = settings.current().parentalPin
        assertTrue(PinHasher.isHash(stored))
        assertFalse(gate.canAccessLocked())
        assertFalse(gate.tryUnlock("0000").ok)
        assertTrue(gate.tryUnlock("2580").ok)
        assertTrue(gate.canAccessLocked())
        gate.lock()
        assertFalse(gate.canAccessLocked())
    }

    @Test fun `expiration du deverrouillage`() = runBlocking {
        gate.setPin("2580")
        val t0 = 1_000_000L
        assertTrue(gate.tryUnlock("2580", t0).ok)
        assertTrue(gate.isOpen(t0 + 60_000))
        assertFalse(gate.isOpen(t0 + 60_000 + ParentalGate.UNLOCK_TTL_MS + 1))
    }

    @Test fun `blocage apres 5 essais meme avec le bon code`() = runBlocking {
        gate.setPin("2580")
        val t = System.currentTimeMillis()
        repeat(5) { assertFalse(gate.tryUnlock("1111", t).ok) }
        val blocked = gate.tryUnlock("2580", t + 1_000)
        assertFalse(blocked.ok)
        assertTrue(blocked.message!!.contains("Trop d'essais"))
        // le blocage survit à une nouvelle instance (redémarrage de l'app)
        assertFalse(ParentalGate(settings).tryUnlock("2580", t + 2_000).ok)
        assertTrue(gate.tryUnlock("2580", t + 31_000).ok)
        assertEquals(0, settings.pinAttempts().first)
    }

    @Test fun `ancien code en clair re-hache`() = runBlocking {
        settings.update { it.copy(parentalPin = "1357") }
        assertTrue(gate.tryUnlock("1357").ok)
        assertTrue(PinHasher.isHash(settings.current().parentalPin))
    }

    // --- Requêtes ---------------------------------------------------------------------------

    @Test fun `recherche avec jokers echappes et groupes verrouilles`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        db.channels().upsert(listOf(channel(pid, "1", "news", "Info 100%"), channel(pid, "2", "news", "Info 1000"), channel(pid, "3", "adult", "Info 100% Nuit")))
        db.movies().upsert(listOf(movie(pid, "10", "Le_Film"), movie(pid, "11", "LeXFilm")))
        db.categories().upsertPref(CategoryPrefEntity(pid, ContentType.LIVE, "adult", locked = true))
        gate.setPin("2580")
        val repo = CatalogRepository(db, playlistsStub(), xtreamStub(), tmdbStub(), settings, gate)
        val r = repo.search(pid, "100%")
        assertEquals(listOf("1"), r.channels.map { it.id }) // ni « 1000 », ni la chaîne verrouillée
        assertEquals(listOf("10"), repo.search(pid, "le_film").movies.map { it.id })
        gate.tryUnlock("2580")
        assertEquals(setOf("1", "3"), repo.search(pid, "100%").channels.map { it.id }.toSet())
    }

    @Test fun `listes transverses sans les groupes verrouilles`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        db.channels().upsert(listOf(channel(pid, "1", "news", "Info"), channel(pid, "2", "adult", "Nuit")))
        db.library().addFavorite(FavoriteEntity(pid, ContentType.LIVE, "1"))
        db.library().addFavorite(FavoriteEntity(pid, ContentType.LIVE, "2"))
        db.categories().upsertPref(CategoryPrefEntity(pid, ContentType.LIVE, "adult", locked = true))
        gate.setPin("2580")
        gate.lock()
        val repo = CatalogRepository(db, playlistsStub(), xtreamStub(), tmdbStub(), settings, gate)
        val fav = com.sokoul.iptv.data.repo.SpecialCategory.FAVORITES
        val all = com.sokoul.iptv.data.repo.SpecialCategory.ALL
        assertEquals(listOf("1"), repo.channels(pid, fav).first().map { it.id })
        assertEquals(listOf("1"), repo.channels(pid, all).first().map { it.id })
        gate.tryUnlock("2580")
        assertEquals(setOf("1", "2"), repo.channels(pid, fav).first().map { it.id }.toSet())
    }

    @Test fun `zapping favoris vide ne renvoie pas tout`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        db.channels().upsert(listOf(channel(pid, "1", "a", "A"), channel(pid, "2", "a", "B")))
        val repo = CatalogRepository(db, playlistsStub(), xtreamStub(), tmdbStub(), settings, gate)
        assertTrue(repo.channelList(pid, com.sokoul.iptv.data.repo.SpecialCategory.FAVORITES).isEmpty())
        db.library().addFavorite(FavoriteEntity(pid, ContentType.LIVE, "2"))
        assertEquals(listOf("2"), repo.channelList(pid, com.sokoul.iptv.data.repo.SpecialCategory.FAVORITES).map { it.id })
    }

    // --- Sauvegarde -------------------------------------------------------------------------

    private fun backup() = BackupManager(context, db, settings, SecretBox())

    @Test fun `export par defaut sans secrets`() = runBlocking {
        val secrets = SecretBox()
        db.playlists().insert(PlaylistEntity(name = "x", type = PlaylistType.XTREAM, url = "http://srv:8080", username = "jean", passwordEnc = secrets.encrypt("motdepasse")))
        db.playlists().insert(PlaylistEntity(name = "m", type = PlaylistType.M3U, url = "http://srv/get.php?username=jean&password=motdepasse"))
        gate.setPin("2580")
        settings.update { it.copy(tmdbApiKey = "abcdef123") }
        val (text, result) = backup().exportText(null)
        assertFalse(text.contains("motdepasse"))
        assertFalse(text.contains("abcdef123"))
        assertFalse(text.contains("pbkdf2"))
        assertEquals(1, result.playlists)
        assertEquals(1, result.skippedWithCredentials)
    }

    @Test fun `export chiffre puis restauration`() = runBlocking {
        val secrets = SecretBox()
        db.playlists().insert(PlaylistEntity(name = "x", type = PlaylistType.XTREAM, url = "http://srv:8080", username = "jean", passwordEnc = secrets.encrypt("motdepasse")))
        val (text, _) = backup().exportText("ma-phrase-secrete")
        assertFalse(text.contains("motdepasse"))
        assertFalse(text.contains("jean"))
        // restauration sur une base vide
        db.playlists().delete(db.playlists().getAll().first().id)
        try { backup().importText(text, null); fail() } catch (e: BackupPassphraseException) { /* phrase requise */ }
        try { backup().importText(text, "mauvaise-phrase"); fail() } catch (e: BackupPassphraseException) { /* refusée */ }
        val r = backup().importText(text, "ma-phrase-secrete")
        assertEquals(1, r.added.size)
        assertEquals(r.added, r.toSync)
        val p = db.playlists().getAll().single()
        assertEquals("motdepasse", secrets.decrypt(p.passwordEnc))
    }

    @Test fun `import piege refuse ou nettoye`() = runBlocking {
        val bad = """
            {"app":"SokoulTV","version":2,"playlists":[
              {"name":"a","type":"M3U","url":"file:///data/data/com.sokoul.iptv/databases/sokoul.db"},
              {"name":"b","type":"XTREAM","url":"javascript:alert(1)","username":"u"},
              {"name":"c","type":"XTREAM","url":"http://ok:80","username":"u","favorites":[{"type":"LIVE","id":"${"x".repeat(500)}","addedAt":1}]}
            ],"settings":{"bufferProfile":"???","liveFormat":"TS","decoderMode":"AUTO","userAgent":"","audioLanguage":"fr","subtitleLanguage":"",
              "epgOffsetMinutes":999999,"playlistRefreshHours":-5,"epgRefreshHours":0,"parentalPin":"0000"}}
        """.trimIndent()
        gate.setPin("2580")
        val r = backup().importText(bad, null)
        assertEquals(1, r.added.size)
        assertEquals(2, r.skipped)
        assertEquals(1, r.withoutPassword)
        assertTrue(r.toSync.isEmpty()) // Xtream sans mot de passe : pas de synchro vouée à l'échec
        assertTrue(db.library().allFavorites(r.added.first()).isEmpty())
        val s = settings.current()
        assertEquals(720, s.epgOffsetMinutes)
        assertEquals(1, s.playlistRefreshHours)
        // le code parental existant n'est pas remplacé par celui du fichier
        assertTrue(PinHasher.verify("2580", s.parentalPin))
        assertFalse(PinHasher.verify("0000", s.parentalPin))
    }

    @Test fun `fichiers invalides`() = runBlocking {
        for (t in listOf("", "{}", "[1,2]", """{"app":"Autre"}""", """{"app":"SokoulTV","version":99,"playlists":[],"settings":{}}""")) {
            try { backup().importText(t, null); fail("accepté : $t") } catch (e: IOException) { assertNotEquals("", e.message) }
        }
        try { backup().importText("x".repeat(BackupManager.MAX_BYTES + 1), null); fail() } catch (e: IOException) { /* trop gros */ }
    }

    // Dépendances réelles (aucun appel réseau dans les méthodes testées)
    private val http = okhttp3.OkHttpClient()
    private val json = com.sokoul.iptv.data.net.HttpModule.json()
    private fun xtreamStub() = com.sokoul.iptv.data.net.XtreamClient(http, json)
    private fun tmdbStub() = com.sokoul.iptv.data.net.TmdbClient(http, json, db.tmdb())
    private fun playlistsStub() = com.sokoul.iptv.data.repo.PlaylistRepository(
        context, db, SecretBox(), settings, xtreamStub(), http,
        com.sokoul.iptv.recording.RecordingScheduler(context, db), com.sokoul.iptv.ui.MultiviewSession(),
    )

    @Test fun `suppression de playlist en cascade`() = runBlocking {
        val repo = playlistsStub()
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        val other = db.playlists().insert(PlaylistEntity(name = "q", type = PlaylistType.M3U, url = "http://h/y.m3u"))
        db.channels().upsert(listOf(channel(pid, "1", "a", "A"), channel(other, "1", "a", "A")))
        db.library().addFavorite(FavoriteEntity(pid, ContentType.LIVE, "1"))
        db.recordings().insert(com.sokoul.iptv.data.db.RecordingEntity(playlistId = pid, channelId = "1", channelName = "A", title = "fini",
            startAt = 0, endAt = 1, status = com.sokoul.iptv.data.db.RecordingStatus.COMPLETED, filePath = "/x.ts"))
        settings.update { it.copy(activePlaylistId = pid) }
        repo.delete(pid)
        assertEquals(0, db.channels().count(pid))
        assertEquals(1, db.channels().count(other))
        assertTrue(db.library().allFavorites(pid).isEmpty())
        assertEquals(1, db.recordings().forPlaylist(pid).size) // l'enregistrement terminé reste lisible
        assertEquals(other, settings.current().activePlaylistId)
    }

    @Test fun `ajout M3U avec schema interdit`() = runBlocking {
        try { playlistsStub().addM3u("x", "file:///data/data/com.sokoul.iptv/shared_prefs/a.xml", null, null); fail() }
        catch (e: IllegalArgumentException) { /* refusé avant toute lecture */ }
    }

    @Test fun `conflits d'enregistrement`() = runBlocking {
        val sched = com.sokoul.iptv.recording.RecordingScheduler(context, db)
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.XTREAM, url = "http://h", username = "u", maxConnections = 1))
        val now = System.currentTimeMillis()
        val a = sched.schedule(pid, channel(pid, "1", "a", "A"), "Film", now + 3600_000, now + 7200_000)
        assertTrue(a.ok)
        val b = sched.schedule(pid, channel(pid, "2", "a", "B"), "Match", now + 5400_000, now + 9000_000)
        assertFalse(b.ok)
        assertTrue(b.message.contains("1 connexion"))
        val c = sched.schedule(pid, channel(pid, "2", "a", "B"), "Après", now + 7200_000, now + 9000_000)
        assertTrue(c.ok)
    }
}
