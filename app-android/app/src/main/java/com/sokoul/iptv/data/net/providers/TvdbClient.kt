package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.parser.int
import com.sokoul.iptv.parser.str
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Épisode TheTVDB (textes en français quand ils existent). */
data class TvdbEpisode(val season: Int, val episode: Int, val name: String?, val overview: String?, val image: String?, val runtimeMin: Int?)

/**
 * TheTVDB v4 : jeton obtenu par `POST /login` (valable environ un mois, gardé en cache 13 jours),
 * puis épisodes traduits en français (`/series/{id}/episodes/default/fra`).
 */
@Singleton
class TvdbClient @Inject constructor(private val api: ApiHttp, private val keys: ApiKeys) {
    private val loginLock = Mutex()

    private suspend fun token(key: String): String? = loginLock.withLock {
        val body = buildJsonObject { put("apikey", JsonPrimitive(key)) }.toString().toRequestBody(JSON)
        val o = api.getJson(ApiService.TVDB, "$BASE/login".toHttpUrl(), ttlMs = 13 * ApiHttp.DAY, secret = key, body = body) as? JsonObject
        (o?.get("data") as? JsonObject)?.str("token")
    }

    /** Épisodes de la série (toutes saisons, une page de 500 suffit pour la quasi-totalité des séries). */
    suspend fun episodes(tvdbId: Int, lang: String = "fra"): List<TvdbEpisode> {
        val key = keys.key(ApiService.TVDB).ifEmpty { return emptyList() }
        val tok = token(key) ?: return emptyList()
        val url = "$BASE/series/$tvdbId/episodes/default/$lang".toHttpUrl().newBuilder().addQueryParameter("page", "0").build()
        val o = api.getJson(ApiService.TVDB, url, ttlMs = 3 * ApiHttp.DAY, secret = tok, headers = mapOf("Authorization" to "Bearer $tok")) as? JsonObject
            ?: return emptyList()
        return parseEpisodes(o)
    }

    companion object {
        const val BASE = "https://api4.thetvdb.com/v4"
        private const val ART = "https://artworks.thetvdb.com"
        private val JSON = "application/json".toMediaType()

        fun parseEpisodes(o: JsonObject): List<TvdbEpisode> {
            val data = o["data"] as? JsonObject ?: return emptyList()
            return (data["episodes"] as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                val season = x.int("seasonNumber") ?: return@mapNotNull null
                val number = x.int("number") ?: return@mapNotNull null
                if (season <= 0 || number <= 0) return@mapNotNull null // épisodes spéciaux : hors catalogue IPTV
                TvdbEpisode(
                    season = season, episode = number,
                    name = x.str("name")?.trim()?.takeIf { it.isNotEmpty() },
                    overview = x.str("overview")?.trim()?.takeIf { it.isNotEmpty() },
                    image = x.str("image")?.let { if (it.startsWith("http")) it else ART + it },
                    runtimeMin = x.int("runtime")?.takeIf { it > 0 },
                )
            }
        }
    }
}
