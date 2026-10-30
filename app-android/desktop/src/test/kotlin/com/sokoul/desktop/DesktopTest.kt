package com.sokoul.desktop

import com.sokoul.desktop.data.BackupReader
import com.sokoul.desktop.data.Category
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopImport
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.data.Playlist
import com.sokoul.desktop.data.PlaylistType
import com.sokoul.desktop.data.Program
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.desktop.data.Movie
import com.sokoul.desktop.data.Series
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.data.SpecialGroup
import com.sokoul.iptv.data.repo.BackupCrypto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class DesktopTest {
    private lateinit var file: File
    private lateinit var db: DesktopDb

    @Before fun open() {
        file = File.createTempFile("sokoul-test", ".db")
        db = DesktopDb(file)
    }

    @After fun close() {
        db.close()
        file.delete()
    }

    private fun backup(favs: String = """{"type":"LIVE","id":"101","addedAt":5}""") = """
        {"app":"SokoulTV","version":3,"withSecrets":true,"createdAt":1,
         "playlists":[{"name":"Mon IPTV","type":"XTREAM","url":"http://exemple.test:8080/","username":"u","password":"p",
            "favorites":[$favs],"groups":[{"type":"LIVE","id":"7","hidden":true,"locked":false}],
            "progress":[{"type":"MOVIE","id":"m1","title":"Film","positionMs":1000,"durationMs":5000,"updatedAt":10,"finished":false}],
            "recordingRules":[]}],
         "settings":{"bufferProfile":"BALANCED","liveFormat":"TS","decoderMode":"AUTO","userAgent":"UA","audioLanguage":"fr",
            "subtitleLanguage":"fr","epgOffsetMinutes":0,"playlistRefreshHours":24,"epgRefreshHours":24,"parentalPin":"1234"},
         "profiles":[]}
    """.trimIndent()

    private fun encrypt(plain: String, pass: String): String {
        val s = BackupCrypto.seal(plain.toByteArray(), pass)
        return """{"app":"SokoulTV","format":"encrypted","version":3,"iterations":${s.iterations},"salt":"${s.salt}","iv":"${s.iv}","data":"${s.data}"}"""
    }

    @Test fun `sauvegarde chiffree de la tablette importee`() {
        val text = encrypt(backup(), "phrase-test")
        assertTrue(BackupReader.isEncrypted(text))
        val f = BackupReader.read(text, "phrase-test")
        val s = DesktopImport(db).apply(f)
        assertEquals(1, s.playlists)
        val p = db.playlists().single()
        assertEquals(PlaylistType.XTREAM, p.type)
        assertEquals("p", p.password)
        assertEquals(setOf("101"), db.favoriteIds(p.id))
        assertEquals(1, db.progressCount(p.id))
        assertEquals("1234", db.setting(Settings.PIN))
        assertEquals(p.id.toString(), db.setting(Settings.ACTIVE_PLAYLIST))
    }

    @Test fun `mauvaise phrase refusee, rien importe`() {
        val text = encrypt(backup(), "bonne-phrase")
        try {
            BackupReader.read(text, "mauvaise-phrase"); fail("phrase acceptée")
        } catch (e: BackupReader.PassphraseException) {
            assertTrue(e.message!!.contains("incorrecte"))
        }
        assertTrue(db.playlists().isEmpty())
    }

    @Test fun `second import sans perte de ce qui a ete fait sur le PC`() {
        DesktopImport(db).apply(BackupReader.read(backup(), null))
        val pid = db.playlists().single().id
        db.addFavorite(pid, "LIVE", "202", 99) // ajouté sur le PC
        DesktopImport(db).apply(BackupReader.read(backup("""{"type":"LIVE","id":"303","addedAt":7}"""), null))
        assertEquals("même playlist reconnue (serveur et identifiant)", 1, db.playlists().size)
        assertEquals(setOf("101", "202", "303"), db.favoriteIds(pid))
    }

    @Test fun `fichier etranger refuse`() {
        try { BackupReader.read("""{"app":"Autre"}""", null); fail() } catch (e: java.io.IOException) { assertTrue(e.message!!.contains("pas une sauvegarde")) }
    }

    @Test fun `groupes masques, verrouilles, favoris et recherche`() {
        val pid = db.upsertPlaylist(Playlist(0, "P", PlaylistType.M3U, "http://x/list.m3u", null, null, null, null))
        val cats = listOf(Category(pid, "a", "France", 0), Category(pid, "b", "Adultes", 1), Category(pid, "c", "Masqué", 2))
        fun ch(id: String, cat: String, name: String, n: Int) = Channel(pid, id, n, name, null, cat, null, LiveLogic.searchKey(name), "http://x/$id.ts", null, null, n)
        db.replaceLive(pid, cats, listOf(ch("1", "a", "TF1 HD", 1), ch("2", "a", "France 2", 2), ch("3", "b", "X", 3), ch("4", "c", "Caché", 4)))
        db.setCategoryFlags(pid, "b", hidden = false, locked = true)
        db.setCategoryFlags(pid, "c", hidden = true, locked = false)
        // « Toutes » : ni verrouillé, ni masqué
        assertEquals(listOf("1", "2"), db.channels(pid, SpecialGroup.ALL).map { it.id })
        val groups = LiveLogic.groups(db.categories(pid), hasFavorites = false)
        assertEquals(listOf(SpecialGroup.ALL, "a", "b"), groups.map { it.id })
        assertTrue(groups.first { it.id == "b" }.locked)
        assertEquals(listOf("2"), db.searchChannels(pid, LiveLogic.searchKey("france")).map { it.id })
        db.addFavorite(pid, "LIVE", "2", 1)
        assertEquals(listOf("2"), db.channels(pid, SpecialGroup.FAVORITES).map { it.id })
        // une nouvelle synchronisation garde les drapeaux posés
        db.replaceLive(pid, cats, listOf(ch("1", "a", "TF1 HD", 1)))
        assertTrue(db.categories(pid).first { it.id == "b" }.locked)
    }

    @Test fun `guide, programme en cours et zapping en boucle`() {
        val pid = db.upsertPlaylist(Playlist(0, "P", PlaylistType.XTREAM, "http://s:80", "u", "p", null, null))
        val h = 3600_000L
        val now = 1_700_000_000_000L
        db.replacePrograms(pid, sequenceOf(
            Program("tf1.fr", now - 2 * h, now - h, "Ancien", null),
            Program("tf1.fr", now - h / 2, now + h / 2, "En cours", "résumé"),
            Program("tf1.fr", now + h / 2, now + h, "Ensuite", null),
        ), keepFrom = now - 3 * h)
        val progs = db.programs(pid, listOf("tf1.fr"), now - 3 * h, now + 3 * h)
        val nn = LiveLogic.nowNext(progs, now)
        assertEquals("En cours", nn.now?.title)
        assertEquals("Ensuite", nn.next?.title)
        assertEquals(0.5f, nn.progress(now), 0.01f)
        assertEquals(0, LiveLogic.zap(3, 2, 1))
        assertEquals(2, LiveLogic.zap(3, 0, -1))
        val c = Channel(pid, "123", 1, "TF1", null, "a", "tf1.fr", "tf1", null, null, null, 0)
        val req = LiveLogic.stream(db.playlists().single(), c, "UA")
        assertTrue(req.url.endsWith("/live/u/p/123.ts"))
        assertEquals("UA", req.userAgent)
        assertNotNull(db.programs(pid, listOf("tf1.fr"), now, now + h).firstOrNull())
        assertFalse(LiveLogic.guideStart(now) > now)
    }

    @Test fun `reprendre - libelles de la tablette`() {
        val d = com.sokoul.desktop.tablet.TabletData
        assertEquals("Reste 1 h 05", d.remaining(65 * 60_000L))
        assertEquals("Reste 1 min", d.remaining(59_000L))
        assertEquals("Reste 1 h 49", d.continueCaption(null, null, 109 * 60_000L))
        assertEquals("S1 · É3 · Reste 18 min", d.continueCaption(1, 3, 18 * 60_000L))
    }

    @Test fun `piste audio - francais, sinon anglais, jamais la premiere au hasard`() {
        val pick = com.sokoul.desktop.player.AudioChoice::pick
        // pistes réelles d'une version « MULTI » sans français : l'anglais plutôt que le turc
        assertEquals(2, pick(listOf(1 to "HDM UHD Bluray AUDIO - [Turkish]", 2 to "HDM - [English]")))
        assertEquals(3, pick(listOf(1 to "Piste 1 - [English]", 3 to "TrueFrench AC3 - [Français]")))
        assertEquals(5, pick(listOf(4 to "Track 1 - [eng]", 5 to "VFF 5.1")))
        assertEquals(null, pick(listOf(1 to "Piste 1 - [Japanese]", 2 to "Piste 2 - [German]")))
    }

    @Test fun `recherche au programme - pas fini, chaine visible, sans casse`() {
        val pid = db.upsertPlaylist(Playlist(0, "P", PlaylistType.M3U, "http://x/list.m3u", null, null, null, null))
        val h = 3600_000L
        val now = 1_700_000_000_000L
        db.replaceLive(pid, listOf(Category(pid, "a", "France", 0), Category(pid, "c", "Masqué", 1)), listOf(
            Channel(pid, "1", 1, "TF1", null, "a", "tf1.fr", "tf1", null, null, null, 0),
            Channel(pid, "2", 2, "Caché", null, "c", "cache.fr", "cache", null, null, null, 1),
        ))
        db.setCategoryFlags(pid, "c", hidden = true, locked = false)
        db.replacePrograms(pid, sequenceOf(
            Program("tf1.fr", now - 2 * h, now - h, "Journal de 13h", null),
            Program("tf1.fr", now + h, now + 2 * h, "Le Journal de 20h", null),
            Program("cache.fr", now + h, now + 2 * h, "Journal caché", null),
        ), keepFrom = now - 3 * h)
        val found = db.searchPrograms(pid, "JOURNAL", now)
        assertEquals(listOf("Le Journal de 20h"), found.map { it.first.title })
        assertEquals("1", found.single().second.id)
    }

    @Test fun `cle saisie l'emporte sur la cle integree, jamais affichee en entier`() {
        val before = ProviderConfig.builtIn
        ProviderConfig.builtIn = { mapOf("tmdb" to "integree-1234") }
        try {
            assertEquals("integree-1234", ProviderConfig.key(db, "tmdb"))
            assertEquals("Clé intégrée", ProviderConfig.statuses(db).first { it.id == "tmdb" }.source)
            ProviderConfig.setKey(db, "tmdb", " cle-test-locale ")
            val tmdb = ProviderConfig.statuses(db).first { it.id == "tmdb" }
            assertEquals("Clé saisie", tmdb.source)
            assertEquals("•••• cale", tmdb.hint)
            assertEquals("cle-test-locale", ProviderConfig.key(db, "tmdb"))
            ProviderConfig.setKey(db, "tmdb", "")
            assertEquals("integree-1234", ProviderConfig.key(db, "tmdb"))
            assertFalse(ProviderConfig.statuses(db).first { it.id == "omdb" }.configured)
        } finally { ProviderConfig.builtIn = before }
    }

    @Test fun `reprise enregistree, une seule ligne par serie, terminee exclue`() {
        fun r(key: String, kind: String, id: Int, s: Int?, e: Int?, pos: Long, at: Long, done: Boolean = false) =
            com.sokoul.desktop.data.Resume(key, kind, id, s, e, "T$id", null, null, pos, 3_600_000, at, done)
        db.saveResume(r("movie:1", "movie", 1, null, null, 600_000, 10))
        db.saveResume(r("tv:2:1:1", "tv", 2, 1, 1, 900_000, 20))
        db.saveResume(r("tv:2:1:2", "tv", 2, 1, 2, 300_000, 30))
        db.saveResume(r("movie:3", "movie", 3, null, null, 3_500_000, 40, done = true))
        val list = db.resumeList()
        assertEquals(listOf("tv:2:1:2", "movie:1"), list.map { it.key })
        assertEquals("tv:2:1:2", com.sokoul.desktop.data.Resume.key("tv", 2, 1, 2))
        assertEquals("movie:5", com.sokoul.desktop.data.Resume.key("movie", 5, null, null))
        assertEquals(600_000L, db.resume("movie:1")?.positionMs)
        // série : point de départ de « Reprendre », épisode terminé compris
        db.saveResume(r("tv:2:1:3", "tv", 2, 1, 3, 3_500_000, 50, done = true))
        assertEquals("tv:2:1:3", db.lastOf("tv", 2)?.key)
    }

    @Test fun `classement PC - langue, SDR avant HDR, taille lisible, echantillons ecartes`() {
        var n = 0
        fun s(name: String, gb: Double) = com.sokoul.iptv.data.debrid.DebridStreams.parse(
            """{"streams":[{"name":"Torrentio","title":"$name\n💾 $gb GB","url":"https://t.example/resolve/realdebrid/x/${"a".repeat(39)}${n++}/1/f.mkv"}]}""",
        ).single()
        val vostfr4k = s("Film.2021.VOSTFR.2160p.WEB-DL.x265", 15.0)
        val multiHdr = s("Film.2021.MULTi.2160p.UHD.BluRay.HDR10.x265", 20.0)
        val multiSdr = s("Film.2021.MULTi.1080p.BluRay.x264", 10.0)
        val tooBig = s("Film.2021.MULTi.1080p.BluRay.REMUX.AVC", 40.0)
        val sample = s("Film.2021.MULTi.1080p.WEB.sample", 0.02)
        val ranked = com.sokoul.desktop.data.DesktopRanker.rank(listOf(vostfr4k, multiHdr, tooBig, multiSdr, sample), maxBytes = 25_000_000_000L)
        assertEquals(listOf(multiSdr.key, multiHdr.key, vostfr4k.key, tooBig.key), ranked.map { it.key })
    }

    @Test fun `catalogue vod est isole par playlist et filtrable`() {
        val first = db.upsertPlaylist(Playlist(0, "P1", PlaylistType.XTREAM, "http://one", "u", "p", null, null))
        val second = db.upsertPlaylist(Playlist(0, "P2", PlaylistType.XTREAM, "http://two", "u", "p", null, null))
        db.replaceVod(
            first,
            listOf(Movie(first, "10", "Inception", 2010, "https://image.test/inception.jpg", 8.8, "1", "mp4", null, "27205", "https://stream.test/inception.mp4")),
            listOf(Series(first, "20", "Dark", 2017, "https://image.test/dark.jpg", null, "Mystère", 8.7, "2", null, null)),
        )
        db.replaceVod(second, listOf(Movie(second, "11", "Autre film", null, null, null, null, null, null, null)), emptyList())
        assertEquals(listOf("Inception"), db.movies(first, "incept").map { it.name })
        assertEquals(listOf("Dark"), db.series(first).map { it.name })
        assertTrue(db.movies(second).none { it.name == "Inception" })
    }
}
