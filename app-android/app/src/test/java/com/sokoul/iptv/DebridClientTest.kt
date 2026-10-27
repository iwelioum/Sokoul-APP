package com.sokoul.iptv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.debrid.RdException
import com.sokoul.iptv.data.debrid.RealDebridClient
import com.sokoul.iptv.data.net.providers.ApiKeys
import com.sokoul.iptv.data.net.providers.ApiService
import com.sokoul.iptv.data.net.providers.ProviderHealth
import com.sokoul.iptv.data.net.providers.ServiceState
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Client Real-Debrid face à un faux serveur : formats réels, 429, clé refusée, jeton jamais dans l'URL. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DebridClientTest {
    private data class Call(val method: String, val url: String, val body: String, val auth: String?)

    private val calls = mutableListOf<Call>()
    private var reply: (Call) -> Triple<Int, String, Map<String, String>> = { Triple(200, "{}", emptyMap()) }
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val r = chain.request()
        val body = r.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
        val c = Call(r.method, r.url.toString(), body, r.header("Authorization"))
        calls += c
        val (code, text, headers) = reply(c)
        Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .body(text.toResponseBody("application/json".toMediaType())).build()
    }.build()

    private val settings = SettingsStore(ApplicationProvider.getApplicationContext())
    private val health = ProviderHealth()
    private val rd = RealDebridClient(http, ApiKeys(settings), settings, health)

    private fun withKey() = runBlocking { settings.update { it.copy(apiKeys = mapOf("realdebrid" to "TESTKEY123")) } }

    @Test fun `compte, magnet, choix du fichier, lien direct`() = runBlocking {
        withKey()
        reply = { c ->
            when {
                c.url.endsWith("/user") -> Triple(200, """{"username":"oumba","type":"premium","expiration":"2027-01-10T12:00:00.000Z"}""", emptyMap())
                c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"T1","uri":"x"}""", emptyMap())
                c.url.endsWith("/torrents/info/T1") -> Triple(200, """{"id":"T1","filename":"Show.S02","status":"downloaded","progress":100,"bytes":5,
                    "files":[{"id":1,"path":"/Show.S02E05.mkv","bytes":3,"selected":1}],"links":["https://real-debrid.com/d/ABC"]}""", emptyMap())
                c.url.endsWith("/torrents/selectFiles/T1") -> Triple(204, "", emptyMap())
                c.url.endsWith("/unrestrict/link") -> Triple(200, """{"download":"https://dl.rd/abc/Show.S02E05.mkv","filename":"Show.S02E05.mkv","filesize":3,"mimeType":"video/x-matroska"}""", emptyMap())
                else -> Triple(404, """{"error":"unknown"}""", emptyMap())
            }
        }
        val u = rd.user()
        assertTrue(u.premium)
        assertEquals(76, u.daysLeft(java.time.Instant.parse("2026-10-26T12:00:00Z").toEpochMilli()))
        assertEquals("T1", rd.addMagnet("magnet:?xt=urn:btih:abc"))
        rd.selectFiles("T1", listOf(1))
        val t = rd.torrentInfo("T1")
        assertTrue(t.ready)
        assertEquals("Show.S02E05.mkv", t.files.single().name)
        assertEquals("https://dl.rd/abc/Show.S02E05.mkv", rd.unrestrict(t.links.single()).url)
        assertEquals("files=1", calls.first { it.url.contains("selectFiles") }.body)
        assertTrue("jeton en en-tête, jamais dans l'URL", calls.all { it.auth == "Bearer TESTKEY123" && "TESTKEY123" !in it.url })
        assertEquals(ServiceState.Ok, health.state(ApiService.REALDEBRID))
    }

    @Test fun `429 attend puis reessaie une fois`() = runBlocking {
        withKey()
        var n = 0
        reply = { if (n++ == 0) Triple(429, """{"error":"too_many_requests","error_code":34}""", mapOf("Retry-After" to "0")) else Triple(200, """{"username":"a","type":"free"}""", emptyMap()) }
        val u = rd.user()
        assertFalse(u.premium)
        assertEquals(2, calls.size)
    }

    @Test fun `cle refusee service bloque et message clair`() = runBlocking {
        withKey()
        reply = { Triple(401, """{"error":"bad_token","error_code":8}""", emptyMap()) }
        try { rd.user(); fail() } catch (e: RdException) { assertEquals("Clé Real-Debrid invalide ou expirée", e.message) }
        assertTrue(health.state(ApiService.REALDEBRID) is ServiceState.Blocked)
        // plus aucun appel tant que la clé n'est pas changée
        try { rd.user(); fail() } catch (_: RdException) {}
        assertEquals(1, calls.size)
    }

    @Test fun `sans cle aucun appel`() = runBlocking {
        org.junit.Assume.assumeTrue("clé présente dans local.properties", ApiService.REALDEBRID.buildKey.isEmpty())
        assertFalse(rd.configured())
        try { rd.user(); fail() } catch (_: RdException) {}
        assertTrue(calls.isEmpty())
    }

    // --- Résolution d'une version : pack de saison, version pas prête, torrent déjà dans le compte ---

    private val hash = "abcdef0123456789abcdef0123456789abcdef01"
    private fun packStream() = com.sokoul.iptv.data.debrid.DebridStream("$hash:-1", "Show.S02.1080p", null, hash, null, null, com.sokoul.iptv.data.debrid.Release())
    private val episode = com.sokoul.iptv.data.debrid.DebridQuery(1396, true, 2, 5)
    private val packFiles = """[{"id":1,"path":"/Show.S02E04.mkv","bytes":2000,"selected":0},{"id":2,"path":"/Show.S02E05.mkv","bytes":2100,"selected":0},{"id":3,"path":"/Show.S02E05.sample.mkv","bytes":10,"selected":0}]"""

    @Test fun `pack de saison seul l'episode est choisi puis lien direct`() = runBlocking {
        withKey()
        var ready = false
        reply = { c ->
            when {
                c.url.contains("/torrents?") -> Triple(200, "[]", emptyMap())
                c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"T9"}""", emptyMap())
                c.url.endsWith("/torrents/selectFiles/T9") -> { ready = true; Triple(204, "", emptyMap()) }
                c.url.endsWith("/torrents/info/T9") -> Triple(200,
                    if (!ready) """{"id":"T9","status":"waiting_files_selection","files":$packFiles,"links":[]}"""
                    else """{"id":"T9","status":"downloaded","files":${packFiles.replace("\"id\":2,\"path\":\"/Show.S02E05.mkv\",\"bytes\":2100,\"selected\":0", "\"id\":2,\"path\":\"/Show.S02E05.mkv\",\"bytes\":2100,\"selected\":1")},"links":["https://real-debrid.com/d/E05"]}""", emptyMap())
                c.url.endsWith("/unrestrict/link") -> Triple(200, """{"download":"https://dl.rd/E05.mkv","filename":"Show.S02E05.mkv","filesize":2100}""", emptyMap())
                else -> Triple(404, "{}", emptyMap())
            }
        }
        val r = com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }
        assertEquals("https://dl.rd/E05.mkv", r.resolve(packStream(), episode))
        assertEquals("files=2", calls.single { it.url.contains("selectFiles") }.body)
        assertTrue("lien de l'épisode demandé", calls.single { it.url.endsWith("unrestrict/link") }.body.contains("E05"))
    }

    @Test fun `attend la conversion du magnet avant de choisir le fichier`() = runBlocking {
        withKey()
        var infoCalls = 0
        reply = { c ->
            when {
                c.url.contains("/torrents?") -> Triple(200, "[]", emptyMap())
                c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"META"}""", emptyMap())
                c.url.endsWith("/torrents/info/META") -> {
                    infoCalls++
                    if (infoCalls < 3) Triple(200, """{"id":"META","status":"magnet_conversion","files":[],"links":[]}""", emptyMap())
                    else Triple(200, """{"id":"META","status":"downloaded","files":[{"id":1,"path":"/Film.2024.mkv","bytes":1000,"selected":1}],"links":["https://real-debrid.com/d/META"]}""", emptyMap())
                }
                c.url.endsWith("/unrestrict/link") -> Triple(200, """{"download":"https://dl.rd/meta.mkv"}""", emptyMap())
                else -> Triple(204, "", emptyMap())
            }
        }
        val r = com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }
        assertEquals("https://dl.rd/meta.mkv", r.resolve(movieStream(addonLink), movie))
        assertEquals(4, infoCalls)
        assertTrue(calls.any { it.url.endsWith("/torrents/selectFiles/META") })
    }

    private fun neverConverted(): (Call) -> Triple<Int, String, Map<String, String>> = { c ->
        when {
            c.url.contains("/torrents?") -> Triple(200, "[]", emptyMap())
            c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"NC"}""", emptyMap())
            c.url.endsWith("/torrents/info/NC") -> Triple(200, """{"id":"NC","status":"magnet_conversion","progress":0,"files":[],"links":[]}""", emptyMap())
            else -> Triple(204, "", emptyMap())
        }
    }

    @Test fun `magnet jamais converti - pas en cache, torrent supprime, pas une erreur`() = runBlocking {
        withKey()
        reply = neverConverted()
        val r = com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }
        try { r.resolve(movieStream(addonLink), movie); fail() } catch (e: com.sokoul.iptv.data.debrid.DebridNotCached) {}
        assertTrue(calls.none { it.url.contains("selectFiles") })
        assertTrue(calls.any { it.method == "DELETE" && it.url.endsWith("/torrents/delete/NC") })
        assertEquals("~4 s au plus", 1 + com.sokoul.iptv.data.debrid.DebridResolver.METADATA_POLLS, calls.count { it.url.endsWith("/torrents/info/NC") })
    }

    @Test fun `magnet jamais converti, choisi a la main - garde pour se telecharger`() = runBlocking {
        withKey()
        reply = neverConverted()
        val r = com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }
        try { r.resolve(movieStream(addonLink), movie, keep = true); fail() } catch (e: com.sokoul.iptv.data.debrid.DebridPreparing) {
            assertEquals(0, e.progress)
        }
        assertTrue(calls.none { it.method == "DELETE" })
        assertEquals("files=all", calls.single { it.url.contains("selectFiles") }.body)
    }

    @Test fun `fausse version qui ne contient qu un programme - refusee et supprimee, meme choisie a la main`() = runBlocking {
        withKey()
        reply = { c ->
            when {
                c.url.contains("/torrents?") -> Triple(200, "[]", emptyMap())
                c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"FAKE"}""", emptyMap())
                c.url.endsWith("/torrents/info/FAKE") -> Triple(200, """{"id":"FAKE","status":"waiting_files_selection","files":[{"id":1,"path":"/Clayface 2026 1080p WEB H264-CAKES.exe","bytes":2000000,"selected":0}],"links":[]}""", emptyMap())
                else -> Triple(204, "", emptyMap())
            }
        }
        try { com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }.resolve(movieStream(addonLink), movie, keep = true); fail() } catch (e: com.sokoul.iptv.data.debrid.DebridNotReady) {
            assertEquals("Fausse version : contient un programme (.exe), pas de vidéo", e.message)
        }
        assertTrue("jamais gardé dans le compte", calls.any { it.method == "DELETE" && it.url.endsWith("/torrents/delete/FAKE") })
        assertTrue(calls.none { it.url.contains("selectFiles") })
    }

    @Test fun `empreinte deja dans le compte en page 2 - aucun nouvel ajout`() = runBlocking {
        withKey()
        val filler = (1..100).joinToString(",", "[", "]") { """{"id":"F$it","hash":"${"%040x".format(it)}","status":"downloaded","files":[]}""" }
        reply = { c ->
            when {
                c.url.contains("/torrents?") && c.url.contains("page=2") -> Triple(200, """[{"id":"OLD","hash":"$hash","status":"downloaded","files":[]}]""", emptyMap())
                c.url.contains("/torrents?") -> Triple(200, filler, emptyMap())
                c.url.endsWith("/torrents/info/OLD") -> Triple(200, """{"id":"OLD","status":"downloaded","files":[{"id":1,"path":"/Film.mkv","bytes":9,"selected":1}],"links":["https://real-debrid.com/d/OLD"]}""", emptyMap())
                c.url.endsWith("/unrestrict/link") -> Triple(200, """{"download":"https://dl.rd/old-film.mkv"}""", emptyMap())
                else -> Triple(204, "", emptyMap())
            }
        }
        assertEquals("https://dl.rd/old-film.mkv", com.sokoul.iptv.data.debrid.DebridResolver(rd).resolve(movieStream(addonLink), movie))
        assertTrue(calls.none { it.url.contains("addMagnet") })
    }

    @Test fun `version pas prete torrent supprime et version suivante`() = runBlocking {
        withKey()
        reply = { c ->
            when {
                c.url.contains("/torrents?") -> Triple(200, "[]", emptyMap())
                c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"T7"}""", emptyMap())
                c.url.endsWith("/torrents/info/T7") -> Triple(200, """{"id":"T7","status":"downloading","progress":3,"files":$packFiles,"links":[]}""", emptyMap())
                c.method == "DELETE" -> Triple(204, "", emptyMap())
                else -> Triple(204, "", emptyMap())
            }
        }
        val r = com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }
        try { r.resolve(packStream(), episode); fail() } catch (e: com.sokoul.iptv.data.debrid.DebridNotReady) {
            assertEquals("Version pas encore prête sur Real-Debrid", e.message)
        }
        assertTrue("torrent ajouté puis supprimé", calls.any { it.method == "DELETE" && it.url.endsWith("/torrents/delete/T7") })
    }

    @Test fun `torrent deja pret dans le compte reutilise sans ajout`() = runBlocking {
        withKey()
        val selected = packFiles.replace("\"id\":2,\"path\":\"/Show.S02E05.mkv\",\"bytes\":2100,\"selected\":0", "\"id\":2,\"path\":\"/Show.S02E05.mkv\",\"bytes\":2100,\"selected\":1")
        reply = { c ->
            when {
                c.url.contains("/torrents?") -> Triple(200, """[{"id":"OLD","hash":"${hash.uppercase()}","status":"downloaded","files":[]}]""", emptyMap())
                c.url.endsWith("/torrents/info/OLD") -> Triple(200, """{"id":"OLD","status":"downloaded","files":$selected,"links":["https://real-debrid.com/d/OLD"]}""", emptyMap())
                c.url.endsWith("/unrestrict/link") -> Triple(200, """{"download":"https://dl.rd/old.mkv"}""", emptyMap())
                else -> Triple(500, "{}", emptyMap())
            }
        }
        assertEquals("https://dl.rd/old.mkv", com.sokoul.iptv.data.debrid.DebridResolver(rd).resolve(packStream(), episode))
        assertTrue(calls.none { it.url.contains("addMagnet") })
    }

    @Test fun `version choisie a la main pas prete - gardee dans le compte, en preparation, pas un echec`() = runBlocking {
        withKey()
        reply = { c ->
            when {
                c.url.contains("/torrents?") -> Triple(200, "[]", emptyMap())
                c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"T8"}""", emptyMap())
                c.url.endsWith("/torrents/info/T8") -> Triple(200, """{"id":"T8","status":"downloading","progress":12,"files":$packFiles,"links":[]}""", emptyMap())
                else -> Triple(204, "", emptyMap())
            }
        }
        val r = com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }
        try { r.resolve(packStream(), episode, keep = true); fail() } catch (e: com.sokoul.iptv.data.debrid.DebridPreparing) {
            assertEquals(12, e.progress)
        }
        assertTrue("torrent gardé pour se télécharger", calls.none { it.method == "DELETE" })
        // essai automatique : pas en cache, écarté sans être compté comme une panne
        calls.clear()
        try { r.resolve(packStream(), episode); fail() } catch (e: com.sokoul.iptv.data.debrid.DebridNotCached) {}
        assertTrue(calls.any { it.method == "DELETE" })
    }

    @Test fun `torrent deja en telechargement dans le compte - rien n est ajoute`() = runBlocking {
        withKey()
        reply = { c ->
            when {
                c.url.contains("/torrents?") -> Triple(200, """[{"id":"DL","hash":"$hash","status":"downloading","progress":45,"files":[]}]""", emptyMap())
                else -> Triple(500, "{}", emptyMap())
            }
        }
        try { com.sokoul.iptv.data.debrid.DebridResolver(rd).resolve(packStream(), episode, keep = true); fail() } catch (e: com.sokoul.iptv.data.debrid.DebridPreparing) {
            assertEquals(45, e.progress)
        }
        assertTrue(calls.none { it.url.contains("addMagnet") })
    }

    // --- Lecture par le compte plutôt que par le lien de l'extension ---

    private fun movieStream(url: String?, h: String? = hash) =
        com.sokoul.iptv.data.debrid.DebridStream("k", "Film.2024.FRENCH.1080p", url, h, null, null, com.sokoul.iptv.data.debrid.Release())
    private val movie = com.sokoul.iptv.data.debrid.DebridQuery(603, false)
    private val addonLink = "https://comet.example/playback/opaque"

    private fun accountReplies(): (Call) -> Triple<Int, String, Map<String, String>> = { c ->
        when {
            c.url.contains("/torrents?") -> Triple(200, "[]", emptyMap())
            c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"M1"}""", emptyMap())
            c.url.endsWith("/torrents/info/M1") -> Triple(200, """{"id":"M1","status":"downloaded","files":[{"id":1,"path":"/Film.mkv","bytes":9,"selected":1}],"links":["https://real-debrid.com/d/F"]}""", emptyMap())
            c.url.endsWith("/unrestrict/link") -> Triple(200, """{"download":"https://dl.rd/film.mkv"}""", emptyMap())
            else -> Triple(204, "", emptyMap())
        }
    }

    @Test fun `empreinte connue - lecture par le compte, jamais par le lien de l extension`() = runBlocking {
        withKey()
        reply = accountReplies()
        val r = com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }
        assertEquals("https://dl.rd/film.mkv", r.resolve(movieStream(addonLink), movie))
        assertTrue(calls.any { it.url.endsWith("/torrents/addMagnet") })
    }

    @Test fun `sans empreinte - lien de l extension, aucun appel au compte`() = runBlocking {
        withKey()
        reply = accountReplies()
        assertEquals(addonLink, com.sokoul.iptv.data.debrid.DebridResolver(rd).resolve(movieStream(addonLink, h = null), movie))
        assertTrue(calls.isEmpty())
    }

    @Test fun `compte en panne - lien de l extension en secours, cle refusee - jamais`() = runBlocking {
        withKey()
        reply = { Triple(503, "{}", emptyMap()) }
        assertEquals(addonLink, com.sokoul.iptv.data.debrid.DebridResolver(rd).resolve(movieStream(addonLink), movie))
        reply = { Triple(401, """{"error":"bad_token","error_code":8}""", emptyMap()) }
        try { com.sokoul.iptv.data.debrid.DebridResolver(rd).resolve(movieStream(addonLink), movie); fail() } catch (e: RdException) { assertEquals(401, e.code) }
    }

    @Test fun `pack de saison sans l episode demande - aucune autre video choisie`() = runBlocking {
        withKey()
        val other = """[{"id":1,"path":"/Show.S02E04.mkv","bytes":2000,"selected":0},{"id":2,"path":"/Show.S02E06.mkv","bytes":2100,"selected":0}]"""
        reply = { c ->
            when {
                c.url.contains("/torrents?") -> Triple(200, "[]", emptyMap())
                c.url.endsWith("/torrents/addMagnet") -> Triple(201, """{"id":"P1"}""", emptyMap())
                c.url.endsWith("/torrents/info/P1") -> Triple(200, """{"id":"P1","status":"waiting_files_selection","files":$other,"links":[]}""", emptyMap())
                else -> Triple(204, "", emptyMap())
            }
        }
        try { com.sokoul.iptv.data.debrid.DebridResolver(rd).apply { pollDelayMs = 0 }.resolve(packStream(), episode); fail() } catch (e: com.sokoul.iptv.data.debrid.DebridNotReady) {
            assertEquals("L'épisode S02E05 n'est pas dans cette version", e.message)
        }
        assertTrue("rien choisi, torrent supprimé", calls.none { it.url.contains("selectFiles") } && calls.any { it.method == "DELETE" })
    }
}
