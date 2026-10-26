package com.sokoul.iptv.data.trakt

import com.sokoul.iptv.BuildConfig
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.net.await
import com.sokoul.iptv.parser.int
import com.sokoul.iptv.parser.long
import com.sokoul.iptv.parser.str
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class TraktDeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalS: Int, val expiresAtMs: Long)

/** Ce que l'on envoie ou reçoit de Trakt : un film, ou un épisode d'une série (identifiants TMDB). */
data class TraktItem(val tmdbId: Int, val season: Int? = null, val episode: Int? = null) {
    val isEpisode get() = season != null && episode != null
}

class TraktException(val code: Int, message: String) : IOException(message)

/**
 * Trakt (api.trakt.tv) : association par code appareil, scrobble, historique et liste.
 *
 * - Identifiant et secret de l'appli Trakt de l'utilisateur : Réglages, sinon `local.properties`
 *   (`sokoul.key.trakt`, `sokoul.key.traktsecret`). Sans eux, rien n'est appelé.
 * - Jetons rangés **par profil** avec les clés ; jamais écrits dans un journal.
 */
@Singleton
class TraktClient @Inject constructor(
    private val http: OkHttpClient,
    private val settings: SettingsStore,
) {
    /** Remplaçables dans les tests (MockWebServer). */
    internal var base = BASE
    internal var clock: () -> Long = System::currentTimeMillis

    suspend fun clientId(): String = settings.current().apiKeys[K_ID]?.takeIf { it.isNotBlank() } ?: BuildConfig.KEY_TRAKT
    private suspend fun clientSecret(): String = settings.current().apiKeys[K_SECRET]?.takeIf { it.isNotBlank() } ?: BuildConfig.KEY_TRAKTSECRET

    /** L'appli Trakt est renseignée (identifiant et secret). */
    suspend fun appConfigured(): Boolean = clientId().isNotBlank() && clientSecret().isNotBlank()

    // --- Jetons (par profil) ---------------------------------------------------------------------

    suspend fun linked(profile: Long): Boolean = settings.current().apiKeys[key(profile, "refresh")] != null

    suspend fun username(profile: Long): String? = settings.current().apiKeys[key(profile, "user")]

    /** Jeton utilisable du profil (renouvelé s'il expire), null s'il n'est pas associé. */
    suspend fun token(profile: Long): String? {
        val k = settings.current().apiKeys
        val refresh = k[key(profile, "refresh")] ?: return null
        val access = k[key(profile, "access")]
        val exp = k[key(profile, "expiry")]?.toLongOrNull() ?: 0
        if (access != null && exp > clock() + 3_600_000L) return access
        val o = post("/oauth/token", buildJsonObject {
            put("refresh_token", refresh); put("client_id", clientId()); put("client_secret", clientSecret())
            put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob"); put("grant_type", "refresh_token")
        }, token = null) as? JsonObject ?: return null
        return store(profile, o)
    }

    suspend fun unlink(profile: Long) = settings.update { s ->
        s.copy(apiKeys = s.apiKeys - listOf("access", "refresh", "expiry", "user").map { key(profile, it) }.toSet())
    }

    // --- Association par code --------------------------------------------------------------------

    suspend fun deviceCode(): TraktDeviceCode {
        if (!appConfigured()) throw TraktException(0, "Renseignez d'abord l'identifiant et le secret de votre appli Trakt")
        val o = post("/oauth/device/code", buildJsonObject { put("client_id", clientId()) }, token = null) as? JsonObject
            ?: throw TraktException(0, "Code indisponible")
        return TraktDeviceCode(
            o.str("device_code") ?: throw TraktException(0, "Code indisponible"), o.str("user_code").orEmpty(),
            o.str("verification_url") ?: "https://trakt.tv/activate", (o.long("interval") ?: 5).toInt(),
            clock() + (o.long("expires_in") ?: 600) * 1000,
        )
    }

    /**
     * Un essai : true quand le code a été validé sur trakt.tv/activate (jeton enregistré pour [profile]).
     * 400 = pas encore validé ; 404/409/410/418 = code inutilisable (exception).
     */
    suspend fun pollDevice(code: TraktDeviceCode, profile: Long): Boolean {
        val o = try {
            post("/oauth/device/token", buildJsonObject {
                put("code", code.deviceCode); put("client_id", clientId()); put("client_secret", clientSecret())
            }, token = null) as? JsonObject
        } catch (e: TraktException) {
            if (e.code == 400 || e.code == 429) return false
            throw e
        } ?: return false
        val token = store(profile, o)
        runCatching { (get("/users/settings", token) as? JsonObject)?.let { (it["user"] as? JsonObject)?.str("username") } }.getOrNull()?.let { u ->
            settings.update { s -> s.copy(apiKeys = s.apiKeys + (key(profile, "user") to u)) }
        }
        return true
    }

    private suspend fun store(profile: Long, o: JsonObject): String {
        val access = o.str("access_token") ?: throw TraktException(0, "Association refusée")
        val t = mapOf(
            key(profile, "access") to access,
            key(profile, "refresh") to (o.str("refresh_token") ?: ""),
            key(profile, "expiry") to (clock() + (o.long("expires_in") ?: 7_776_000L) * 1000).toString(),
        )
        settings.update { s -> s.copy(apiKeys = s.apiKeys + t) }
        return access
    }

    // --- Scrobble, historique, liste -------------------------------------------------------------

    /** [action] : « start », « pause » ou « stop » ; [progress] en pourcentage (Trakt marque vu à partir de 80 %). */
    suspend fun scrobble(profile: Long, action: String, item: TraktItem, progress: Float) {
        val token = token(profile) ?: return
        post("/scrobble/$action", buildJsonObject {
            itemJson(item)
            put("progress", progress.coerceIn(0f, 100f))
        }, token)
    }

    /** Ajoute des titres vus à l'historique Trakt. */
    suspend fun addHistory(profile: Long, items: List<TraktItem>) {
        if (items.isEmpty()) return
        val token = token(profile) ?: return
        post("/sync/history", syncBody(items), token)
    }

    suspend fun addWatchlist(profile: Long, items: List<TraktItem>) {
        if (items.isEmpty()) return
        val token = token(profile) ?: return
        post("/sync/watchlist", syncBody(items), token)
    }

    /** Films vus sur Trakt (identifiants TMDB). */
    suspend fun watchedMovies(profile: Long): List<Int> {
        val token = token(profile) ?: return emptyList()
        return (get("/sync/watched/movies", token) as? JsonArray).orEmpty().mapNotNull { e ->
            ((e as? JsonObject)?.get("movie") as? JsonObject)?.let(::tmdbOf)
        }
    }

    /** Épisodes vus sur Trakt : (TMDB de la série, saison, épisode). */
    suspend fun watchedEpisodes(profile: Long): List<TraktItem> {
        val token = token(profile) ?: return emptyList()
        return (get("/sync/watched/shows", token) as? JsonArray).orEmpty().flatMap { e ->
            val o = e as? JsonObject ?: return@flatMap emptyList()
            val show = (o["show"] as? JsonObject)?.let(::tmdbOf) ?: return@flatMap emptyList()
            (o["seasons"] as? JsonArray).orEmpty().flatMap { s ->
                val so = s as? JsonObject ?: return@flatMap emptyList()
                val n = so.int("number") ?: return@flatMap emptyList()
                (so["episodes"] as? JsonArray).orEmpty().mapNotNull { ep -> (ep as? JsonObject)?.int("number")?.let { TraktItem(show, n, it) } }
            }
        }
    }

    /** Liste « à voir » Trakt : (film ?, identifiant TMDB). */
    suspend fun watchlist(profile: Long): List<Pair<Boolean, Int>> {
        val token = token(profile) ?: return emptyList()
        val movies = (get("/sync/watchlist/movies", token) as? JsonArray).orEmpty().mapNotNull { e ->
            ((e as? JsonObject)?.get("movie") as? JsonObject)?.let(::tmdbOf)?.let { true to it }
        }
        val shows = (get("/sync/watchlist/shows", token) as? JsonArray).orEmpty().mapNotNull { e ->
            ((e as? JsonObject)?.get("show") as? JsonObject)?.let(::tmdbOf)?.let { false to it }
        }
        return movies + shows
    }

    private fun tmdbOf(o: JsonObject): Int? = (o["ids"] as? JsonObject)?.int("tmdb")

    private fun kotlinx.serialization.json.JsonObjectBuilder.itemJson(item: TraktItem) {
        if (item.isEpisode) {
            putJsonObject("show") { putJsonObject("ids") { put("tmdb", item.tmdbId) } }
            putJsonObject("episode") { put("season", item.season); put("number", item.episode) }
        } else putJsonObject("movie") { putJsonObject("ids") { put("tmdb", item.tmdbId) } }
    }

    private fun syncBody(items: List<TraktItem>): JsonObject = buildJsonObject {
        put("movies", buildJsonArray {
            items.filter { !it.isEpisode }.forEach { add(buildJsonObject { putJsonObject("ids") { put("tmdb", it.tmdbId) } }) }
        })
        put("shows", buildJsonArray {
            items.filter { it.isEpisode }.groupBy { it.tmdbId }.forEach { (show, eps) ->
                add(buildJsonObject {
                    putJsonObject("ids") { put("tmdb", show) }
                    put("seasons", buildJsonArray {
                        eps.groupBy { it.season!! }.forEach { (s, list) ->
                            add(buildJsonObject {
                                put("number", s)
                                put("episodes", buildJsonArray { list.forEach { add(buildJsonObject { put("number", it.episode) }) } })
                            })
                        }
                    })
                })
            }
        })
    }

    // --- HTTP --------------------------------------------------------------------------------------

    private suspend fun get(path: String, token: String?): JsonElement? = call("GET", path, null, token)
    private suspend fun post(path: String, body: JsonObject, token: String?): JsonElement? = call("POST", path, body, token)

    private suspend fun call(method: String, path: String, body: JsonObject?, token: String?): JsonElement? {
        repeat(2) { attempt ->
            val req = Request.Builder().url(base + path)
                .header("Content-Type", "application/json")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", clientId())
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .method(method, body?.toString()?.toRequestBody(JSON))
                .build()
            http.newCall(req).await().use { r ->
                val text = r.body?.string().orEmpty()
                when {
                    // une seule nouvelle tentative, après le délai demandé (jamais en boucle)
                    r.code == 429 && attempt == 0 && path != "/oauth/device/token" -> {
                        delay(((r.header("Retry-After")?.toLongOrNull() ?: 2).coerceAtMost(30)) * 1000)
                        return@repeat
                    }
                    r.isSuccessful -> return if (text.isBlank()) null else Json.parseToJsonElement(text)
                    else -> throw TraktException(r.code, message(r.code))
                }
            }
        }
        throw TraktException(429, message(429))
    }

    companion object {
        const val BASE = "https://api.trakt.tv"
        const val K_ID = "trakt"
        const val K_SECRET = "traktsecret"
        private val JSON = "application/json".toMediaType()

        fun key(profile: Long, what: String) = "trakt$what$profile"

        fun message(http: Int): String = when (http) {
            401, 403 -> "Trakt a refusé l'accès : associez la box à nouveau"
            404 -> "Code Trakt invalide"
            409 -> "Code Trakt déjà utilisé"
            410 -> "Code Trakt expiré : recommencez"
            418 -> "Association refusée sur Trakt"
            429 -> "Trakt est saturé, réessayez dans un instant"
            else -> "Trakt : erreur $http"
        }
    }
}
