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

    @Test fun `configuration fournisseur est masquee et surchargeable`() {
        assertTrue(ProviderConfig.statuses(db).any { it.id == "tmdb" })
        db.putSetting("api_key_tmdb", "cle-test-locale")
        val tmdb = ProviderConfig.statuses(db).first { it.id == "tmdb" }
        assertTrue(tmdb.configured)
        assertEquals("clé desktop", tmdb.source)
        assertEquals("cle-test-locale", ProviderConfig.key(db, "tmdb"))
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
