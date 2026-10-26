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
import com.sokoul.iptv.data.repo.SearchKind
import com.sokoul.iptv.data.repo.SearchOrigin
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
        val repo = CatalogRepository(db, playlistsStub(), xtreamStub(), tmdbStub(), settings, gate, com.sokoul.iptv.player.ProviderGate())
        val r = repo.search(pid, "100%")
        assertEquals(listOf("1"), r.channels.map { it.id }) // ni « 1000 », ni la chaîne verrouillée
        assertEquals(listOf("10"), repo.search(pid, "le_film").movies.map { it.id })
        gate.tryUnlock("2580")
        assertEquals(setOf("1", "3"), repo.search(pid, "100%").channels.map { it.id }.toSet())
    }

    @Test fun `recherche contextuelle - depuis Films ni chaines ni programmes`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        db.channels().upsert(listOf(channel(pid, "1", "a", "Matrix TV")))
        db.movies().upsert(listOf(movie(pid, "10", "Matrix")))
        val repo = CatalogRepository(db, playlistsStub(), xtreamStub(), tmdbStub(), settings, gate, com.sokoul.iptv.player.ProviderGate())
        val films = repo.search(pid, "matrix", SearchKind.forOrigin(SearchOrigin.MOVIES))
        assertEquals(listOf("10"), films.movies.map { it.id })
        assertTrue(films.channels.isEmpty() && films.programs.isEmpty() && films.series.isEmpty())
        val tv = repo.search(pid, "matrix", SearchKind.forOrigin(SearchOrigin.TV))
        assertEquals(listOf("1"), tv.channels.map { it.id })
        assertTrue(tv.movies.isEmpty())
        assertEquals(SearchKind.ALL, SearchKind.forOrigin(SearchOrigin.ALL))
    }

    @Test fun `suggestions par prefixe de mot - 3 au plus, debut de titre puis plus recents, sans verrouilles`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        db.movies().upsert(listOf(
            movie(pid, "1", "Le Parrain II").copy(added = 1), movie(pid, "2", "Le Parrain").copy(added = 4), movie(pid, "3", "Parasite").copy(added = 5),
            movie(pid, "4", "Paris, Texas").copy(added = 3), movie(pid, "5", "Comparaison").copy(added = 9), movie(pid, "6", "Le Parrain").copy(added = 2),
        ))
        db.channels().upsert(listOf(channel(pid, "7", "adult", "Paradise")))
        db.categories().upsertPref(CategoryPrefEntity(pid, ContentType.LIVE, "adult", locked = true))
        gate.setPin("2580")
        val repo = CatalogRepository(db, playlistsStub(), xtreamStub(), tmdbStub(), settings, gate, com.sokoul.iptv.player.ProviderGate())
        val s = repo.suggest(pid, "par", SearchKind.ALL)
        // « Comparaison » : « par » au milieu d'un mot, pas proposé ; doublons regroupés ; chaîne verrouillée absente
        assertEquals(listOf("Parasite", "Paris, Texas", "Le Parrain"), s)
        assertTrue(repo.suggest(pid, "p", SearchKind.ALL).isEmpty())
        assertTrue(repo.suggest(pid, "par", setOf(SearchKind.SERIES)).isEmpty())
    }

    @Test fun `listes transverses sans les groupes verrouilles`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        db.channels().upsert(listOf(channel(pid, "1", "news", "Info"), channel(pid, "2", "adult", "Nuit")))
        db.library().addFavorite(FavoriteEntity(pid, ContentType.LIVE, "1"))
        db.library().addFavorite(FavoriteEntity(pid, ContentType.LIVE, "2"))
        db.categories().upsertPref(CategoryPrefEntity(pid, ContentType.LIVE, "adult", locked = true))
        gate.setPin("2580")
        gate.lock()
        val repo = CatalogRepository(db, playlistsStub(), xtreamStub(), tmdbStub(), settings, gate, com.sokoul.iptv.player.ProviderGate())
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
        val repo = CatalogRepository(db, playlistsStub(), xtreamStub(), tmdbStub(), settings, gate, com.sokoul.iptv.player.ProviderGate())
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

    // --- Doublons regroupés : rien ne se perd ------------------------------------------------

    private fun vmovie(pid: Long, id: String, name: String, cat: String = "c", tmdb: String? = null, pos: Int = 0): MovieEntity {
        val info = com.sokoul.iptv.parser.TitleTagger.parse(name)
        return MovieEntity(pid, id, name, info.clean, info.clean.lowercase(), info.year, info.language, info.quality, null, null, cat, "mkv", null, pos.toLong(), tmdb, pos, 1)
    }

    private fun seedDuplicates(pid: Long) = runBlocking {
        db.movies().upsert(
            listOf(
                vmovie(pid, "1", "Clayface (2025) VF", "top", pos = 0),
                vmovie(pid, "2", "Clayface (2025) MULTI", "netflix", pos = 1),
                vmovie(pid, "3", "Clayface (2025) 4K", "adult", pos = 2),
                vmovie(pid, "4", "Dune (1984)", "top", pos = 3),
                vmovie(pid, "5", "Dune (2021)", "top", pos = 4),
                vmovie(pid, "6", "Sans Annee", "top", pos = 5),
                vmovie(pid, "7", "Sans Annee", "netflix", pos = 6),
                vmovie(pid, "8", "Autre titre (2019)", "netflix", tmdb = "42", pos = 7),
                vmovie(pid, "9", "Autre Titre (2019) VOSTFR", "top", pos = 8),
            )
        )
    }

    private fun vodRepo() = com.sokoul.iptv.data.repo.VodRepository(db, settings, gate)

    @Test fun `invariant chaque version dans exactement une oeuvre`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        seedDuplicates(pid)
        val vod = vodRepo()
        vod.regroup(pid, ContentType.MOVIE)
        val rows = db.vod().movieRows(pid)
        assertTrue(rows.all { it.groupKey != null })
        val byKey = rows.groupBy { it.groupKey!! }
        // la somme des versions de toutes les œuvres = le nombre de lignes du fournisseur
        assertEquals(9, byKey.values.sumOf { it.size })
        assertEquals(9, db.movies().count(pid))
        assertEquals(6, byKey.size) // Clayface ×3, Dune 1984, Dune 2021, Sans Année ×2 séparés, Autre titre ×2
        assertEquals(setOf("1", "2", "3"), byKey.values.first { g -> g.any { it.id == "1" } }.map { it.id }.toSet())
        assertEquals("tmdb:42", rows.first { it.id == "9" }.groupKey) // pont TMDB ↔ titre
        // un second passage ne réécrit rien
        assertEquals(0, vod.regroup(pid, ContentType.MOVIE))
        // chaque version reste accessible depuis la fiche de son œuvre
        for (r in rows) {
            val m = db.movies().get(pid, r.id)!!
            assertTrue(vod.movieVersions(pid, m).any { it.id == r.id })
        }
    }

    @Test fun `grilles une carte par oeuvre et presentes dans chaque categorie`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        seedDuplicates(pid)
        val vod = vodRepo()
        vod.regroup(pid, ContentType.MOVIE)
        val all = db.movies().observeRecent(pid, 300).first()
        val cards = vod.groupMovies(pid, all)
        assertEquals(6, cards.size)
        assertEquals(9, cards.sumOf { it.versions })
        // l'œuvre apparaît dans « netflix » même si sa version « top » est celle mise en avant ailleurs
        val netflix = vod.movieCards(pid, db.movies().observeByCategory(pid, "netflix")).first()
        assertEquals(setOf("2", "7", "8"), netflix.map { it.item.id }.toSet())
        // regroupement désactivé : listes identiques à l'affichage d'origine
        settings.update { it.copy(groupDuplicates = false) }
        assertEquals(all.map { it.id }, vod.groupMovies(pid, all).map { it.item.id })
        assertEquals(all.size, vod.movieCards(pid, db.movies().observeRecent(pid, 300)).first().size)
        settings.update { it.copy(groupDuplicates = true) }
    }

    @Test fun `versions verrouillees cachees sans cacher l'oeuvre`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        seedDuplicates(pid)
        val vod = vodRepo()
        vod.regroup(pid, ContentType.MOVIE)
        db.categories().upsertPref(CategoryPrefEntity(pid, ContentType.MOVIE, "adult", locked = true))
        gate.setPin("2580")
        gate.lock()
        val m = db.movies().get(pid, "1")!!
        assertEquals(setOf("1", "2"), vod.movieVersions(pid, m).map { it.id }.toSet())
        gate.tryUnlock("2580")
        assertEquals(setOf("1", "2", "3"), vod.movieVersions(pid, m).map { it.id }.toSet())
    }

    @Test fun `lien TMDB trouve a l'ouverture fusionne les groupes`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        db.movies().upsert(listOf(vmovie(pid, "1", "Le Film (2020)", tmdb = "77"), vmovie(pid, "2", "The Movie (2020)")))
        val vod = vodRepo()
        vod.regroup(pid, ContentType.MOVIE)
        val m2 = db.movies().get(pid, "2")!!
        assertNotEquals(db.movies().get(pid, "1")!!.groupKey, m2.groupKey)
        vod.linkTmdb(pid, ContentType.MOVIE, "2", null, m2.groupKey, 77)
        assertEquals("tmdb:77", db.movies().get(pid, "2")!!.groupKey)
        // le lien survit à un nouveau calcul complet (synchro)
        vod.regroup(pid, ContentType.MOVIE)
        assertEquals("tmdb:77", db.movies().get(pid, "2")!!.groupKey)
    }

    @Test fun `reprise partagee et favori sur l'oeuvre`() = runBlocking {
        val pid = db.playlists().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U, url = "http://h/x.m3u"))
        seedDuplicates(pid)
        val vod = vodRepo()
        vod.regroup(pid, ContentType.MOVIE)
        val lib = com.sokoul.iptv.data.repo.LibraryRepository(db)
        lib.saveProgress(pid, ContentType.MOVIE, "2", null, "Clayface", null, null, 600_000, 6_000_000)
        val ids = listOf("1", "2", "3")
        assertEquals(600_000L, vod.latestMovieProgress(pid, ids)?.positionMs)
        assertFalse(vod.observeAnyFavorite(pid, ContentType.MOVIE, ids).first())
        vod.toggleFavorite(pid, ContentType.MOVIE, ids, "1")
        assertTrue(vod.observeAnyFavorite(pid, ContentType.MOVIE, ids).first())
        db.library().addFavorite(FavoriteEntity(pid, ContentType.MOVIE, "3"))
        vod.toggleFavorite(pid, ContentType.MOVIE, ids, "1")
        assertFalse(vod.observeAnyFavorite(pid, ContentType.MOVIE, ids).first())
        // la progression de chaque version reste enregistrée à part
        assertEquals(600_000L, lib.progress(pid, ContentType.MOVIE, "2")?.positionMs)
        assertEquals(null, lib.progress(pid, ContentType.MOVIE, "1"))
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
