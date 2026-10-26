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
}
