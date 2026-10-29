package com.sokoul.iptv.data.debrid

import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.net.await
import com.sokoul.iptv.data.net.providers.ApiKeys
import com.sokoul.iptv.data.net.providers.ApiService
import com.sokoul.iptv.data.net.providers.ProviderHealth
import com.sokoul.iptv.parser.long
import com.sokoul.iptv.parser.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class RdUser(val username: String, val premium: Boolean, val expiresAtMs: Long) {
    fun daysLeft(now: Long): Int = if (!premium) 0 else ((expiresAtMs - now) / 86_400_000L).toInt().coerceAtLeast(0)
}

data class RdFile(val id: Int, val path: String, val bytes: Long, val selected: Boolean) {
    val name get() = path.substringAfterLast('/')
}

data class RdTorrent(
    val id: String, val filename: String, val hash: String = "", val status: String, val progress: Int,
    val bytes: Long, val files: List<RdFile>, val links: List<String>, val addedAt: Long,
) {
    val ready get() = status == "downloaded"
}

data class RdLink(val url: String, val filename: String, val bytes: Long, val mime: String?)

data class RdDownload(val id: String, val filename: String, val bytes: Long, val url: String, val generatedAt: Long)

data class RdDeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalS: Int, val expiresAtMs: Long)

class RdException(val code: Int, message: String) : IOException(message)

/**
 * Real-Debrid (api.real-debrid.com) : compte, torrents, liens directs, association par code.
 *
 * - Clé : Réglages, sinon `local.properties` (`sokoul.key.realdebrid`) ; ou jeton obtenu par code appareil
 *   (identifiant client open source, renouvelé seul). Jamais écrite dans un journal.
 * - Débit : une requête toutes les 260 ms au plus (limite RD : 250/min) ; 429 → attente `Retry-After`.
 */
@Singleton
class RealDebridClient @Inject constructor(
    private val http: OkHttpClient,
    private val keys: ApiKeys,
    private val settings: SettingsStore,
    private val health: ProviderHealth,
) : DebridService {
    override val providerName: String = "Real-Debrid"
    /** Remplaçables dans les tests (MockWebServer). */
    internal var base = BASE
    internal var oauth = OAUTH
    internal var clock: () -> Long = System::currentTimeMillis

    private val pace = Mutex()
    private var lastCall = 0L

    // --- Jeton ---------------------------------------------------------------------------------

    /** Jeton utilisable (null = Real-Debrid non configuré). */
    suspend fun token(): String? {
        val k = settings.current().apiKeys
        val refresh = k[K_REFRESH]
        if (refresh != null) {
            val exp = k[K_EXPIRY]?.toLongOrNull() ?: 0
            val access = k[K_ACCESS]
            if (access != null && exp > clock() + 60_000) return access
            return runCatching { refreshToken(k[K_CLIENT].orEmpty(), k[K_SECRET].orEmpty(), refresh) }.getOrNull()
        }
        return keys.key(ApiService.REALDEBRID).ifEmpty { null }
    }

    override suspend fun configured(): Boolean = token() != null

    // --- Compte, torrents, liens ---------------------------------------------------------------

    override suspend fun user(): RdUser {
        val o = call("GET", "user") as JsonObject
        return RdUser(
            username = o.str("username").orEmpty(),
            premium = o.str("type") == "premium",
            expiresAtMs = o.str("expiration")?.let { iso(it) } ?: 0,
        )
    }

    /** Ajoute un magnet (aucun téléchargement tant que les fichiers ne sont pas choisis). */
    override suspend fun addMagnet(magnet: String): String =
        (call("POST", "torrents/addMagnet", FormBody.Builder().add("magnet", magnet).build()) as JsonObject).str("id")
            ?: throw RdException(0, "Réponse Real-Debrid inattendue")

    override suspend fun torrentInfo(id: String): RdTorrent = parseTorrent(call("GET", "torrents/info/$id") as JsonObject)

    override suspend fun selectFiles(id: String, fileIds: List<Int>) {
        call("POST", "torrents/selectFiles/$id", FormBody.Builder().add("files", fileIds.joinToString(",").ifEmpty { "all" }).build())
    }

    override suspend fun deleteTorrent(id: String) { runCatching { call("DELETE", "torrents/delete/$id") } }

    /** Lien RD (hébergeur) → URL directe lisible par le lecteur. */
    override suspend fun unrestrict(link: String): RdLink {
        val o = call("POST", "unrestrict/link", FormBody.Builder().add("link", link).build()) as JsonObject
        return RdLink(o.str("download") ?: throw RdException(0, "Lien indisponible"), o.str("filename").orEmpty(), o.long("filesize") ?: 0, o.str("mimeType"))
    }

    override suspend fun torrents(limit: Int, page: Int): List<RdTorrent> =
        (call("GET", "torrents?limit=$limit" + (if (page > 1) "&page=$page" else "")) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseTorrent) }

    override suspend fun downloads(limit: Int): List<RdDownload> =
        (call("GET", "downloads?limit=$limit") as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            RdDownload(o.str("id") ?: return@mapNotNull null, o.str("filename").orEmpty(), o.long("filesize") ?: 0,
                o.str("download") ?: return@mapNotNull null, o.str("generated")?.let { iso(it) } ?: 0)
        }

    // --- Association par code (sans clavier) ---------------------------------------------------

    suspend fun deviceCode(): RdDeviceCode {
        val o = raw("GET", "$oauth/device/code?client_id=$OPEN_SOURCE_CLIENT&new_credentials=yes", null, null) as JsonObject
        return RdDeviceCode(
            o.str("device_code") ?: throw RdException(0, "Code indisponible"), o.str("user_code").orEmpty(),
            o.str("verification_url") ?: "https://real-debrid.com/device", (o.long("interval") ?: 5).toInt(),
            clock() + (o.long("expires_in") ?: 600) * 1000,
        )
    }

    /** Un essai : true quand l'utilisateur a validé le code sur real-debrid.com/device (jeton enregistré). */
    suspend fun pollDevice(code: RdDeviceCode): Boolean {
        val creds = try {
            raw("GET", "$oauth/device/credentials?client_id=$OPEN_SOURCE_CLIENT&code=${code.deviceCode}", null, null) as? JsonObject
        } catch (e: RdException) { return false } ?: return false
        val client = creds.str("client_id") ?: return false
        val secret = creds.str("client_secret") ?: return false
        val t = tokenRequest(client, secret, code.deviceCode)
        settings.update { s -> s.copy(apiKeys = s.apiKeys + mapOf(K_CLIENT to client, K_SECRET to secret) + t) }
        health.reset(ApiService.REALDEBRID)
        return true
    }

    /** Oublie l'association par code (la clé du fichier local reprend la main). */
    suspend fun unlink() = settings.update { s -> s.copy(apiKeys = s.apiKeys - setOf(K_CLIENT, K_SECRET, K_REFRESH, K_ACCESS, K_EXPIRY)) }

    suspend fun linkedByCode(): Boolean = settings.current().apiKeys[K_REFRESH] != null

    private suspend fun refreshToken(client: String, secret: String, refresh: String): String {
        val t = tokenRequest(client, secret, refresh)
        settings.update { s -> s.copy(apiKeys = s.apiKeys + t) }
        return t.getValue(K_ACCESS)
    }

    private suspend fun tokenRequest(client: String, secret: String, code: String): Map<String, String> {
        val body = FormBody.Builder().add("client_id", client).add("client_secret", secret).add("code", code)
            .add("grant_type", "http://oauth.net/grant_type/device/1.0").build()
        val o = raw("POST", "$oauth/token", body, null) as JsonObject
        val access = o.str("access_token") ?: throw RdException(0, "Association refusée")
        return mapOf(
            K_ACCESS to access,
            K_REFRESH to (o.str("refresh_token") ?: code),
            K_EXPIRY to (clock() + (o.long("expires_in") ?: 3600) * 1000).toString(),
        )
    }

    // --- HTTP ------------------------------------------------------------------------------------

    private suspend fun call(method: String, path: String, body: RequestBody? = null): JsonElement? {
        val token = token() ?: throw RdException(401, "Real-Debrid n'est pas configuré")
        if (health.isBlocked(ApiService.REALDEBRID)) throw RdException(401, "Clé Real-Debrid refusée : vérifiez-la dans Réglages")
        return raw(method, "$base/$path", body, token)
    }

    private suspend fun raw(method: String, url: String, body: RequestBody?, token: String?): JsonElement? {
        repeat(3) { attempt ->
            pace.withLock {
                val wait = lastCall + MIN_SPACING_MS - clock()
                if (wait > 0) delay(wait)
                lastCall = clock()
            }
            val req = Request.Builder().url(url.toHttpUrl())
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .method(method, body ?: if (method == "POST") FormBody.Builder().build() else null)
                .build()
            http.newCall(req).await().use { r ->
                val text = r.body?.string().orEmpty()
                when {
                    r.code == 429 && attempt < 2 -> {
                        delay((r.header("Retry-After")?.toLongOrNull() ?: 2) * 1000)
                        return@repeat
                    }
                    r.isSuccessful -> {
                        if (token != null) health.ok(ApiService.REALDEBRID)
                        return if (text.isBlank()) null else Json.parseToJsonElement(text)
                    }
                    else -> {
                        val err = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                        val code = err?.long("error_code")?.toInt() ?: 0
                        if (token != null && (r.code == 401 || r.code == 403 && code in setOf(8, 9))) health.failed(ApiService.REALDEBRID, r.code, "Clé refusée")
                        throw RdException(r.code, message(r.code, code, err?.str("error")))
                    }
                }
            }
        }
        throw RdException(429, "Real-Debrid est saturé, réessayez dans un instant")
    }

    companion object {
        const val BASE = "https://api.real-debrid.com/rest/1.0"
        const val OAUTH = "https://api.real-debrid.com/oauth/v2"
        /** Identifiant client public prévu par Real-Debrid pour les applications open source. */
        const val OPEN_SOURCE_CLIENT = "X245A4XAIBGVM"
        const val MIN_SPACING_MS = 260L

        // Jetons de l'association par code, rangés avec les clés (valeurs alphanumériques).
        const val K_ACCESS = "rdaccess"
        const val K_CLIENT = "rdclient"
        const val K_SECRET = "rdsecret"
        const val K_REFRESH = "rdrefresh"
        const val K_EXPIRY = "rdexpiry"

        fun iso(s: String): Long = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0)

        fun parseTorrent(o: JsonObject) = RdTorrent(
            id = o.str("id").orEmpty(), filename = o.str("filename").orEmpty(), hash = o.str("hash").orEmpty().lowercase(), status = o.str("status").orEmpty(),
            progress = (o.long("progress") ?: 0).toInt(), bytes = o.long("bytes") ?: 0,
            files = (o["files"] as? JsonArray).orEmpty().mapNotNull { f ->
                val fo = f as? JsonObject ?: return@mapNotNull null
                RdFile((fo.long("id") ?: return@mapNotNull null).toInt(), fo.str("path").orEmpty(), fo.long("bytes") ?: 0, fo.long("selected") == 1L)
            },
            links = (o["links"] as? JsonArray).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content },
            addedAt = o.str("added")?.let { iso(it) } ?: 0,
        )

        /** Messages lisibles (codes d'erreur documentés par Real-Debrid). */
        fun message(http: Int, code: Int, raw: String?): String = when {
            http == 401 || code == 8 -> "Clé Real-Debrid invalide ou expirée"
            code == 9 -> "Accès refusé par Real-Debrid"
            code == 20 || code == 21 -> "Abonnement Real-Debrid premium requis"
            code == 34 || http == 429 -> "Trop de requêtes Real-Debrid, réessayez dans un instant"
            code == 35 -> "Fichier refusé par Real-Debrid (contenu bloqué)"
            http == 503 || code == 19 || code == 24 -> "Fichier momentanément indisponible sur Real-Debrid"
            else -> "Real-Debrid : ${raw ?: "erreur $http"}"
        }
    }
}
