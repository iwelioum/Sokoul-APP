package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.parser.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TasteDive : titres similaires (noms anglais). Les noms sont ensuite rapprochés du catalogue local ;
 * seuls les titres disponibles chez le fournisseur sont proposés.
 */
@Singleton
class TasteDiveClient @Inject constructor(private val api: ApiHttp, private val keys: ApiKeys) {

    suspend fun similar(title: String, isMovie: Boolean, limit: Int = 20): List<String> {
        val key = keys.key(ApiService.TASTEDIVE).ifEmpty { return emptyList() }
        val type = if (isMovie) "movie" else "show"
        val url = "$BASE/similar".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$type:$title").addQueryParameter("type", type)
            .addQueryParameter("limit", limit.toString()).addQueryParameter("k", key).build()
        // sans User-Agent d'application, la protection du site répond 403 (le client HTTP commun en ajoute un)
        val o = api.getJson(ApiService.TASTEDIVE, url, ttlMs = 7 * ApiHttp.DAY, secret = key) as? JsonObject ?: return emptyList()
        return parse(o)
    }

    companion object {
        const val BASE = "https://tastedive.com/api"

        /** Formats récent (`similar.results[].name`) et ancien (`Similar.Results[].Name`). */
        fun parse(o: JsonObject): List<String> {
            val root = (o["similar"] ?: o["Similar"]) as? JsonObject ?: return emptyList()
            val results = (root["results"] ?: root["Results"]) as? JsonArray ?: return emptyList()
            return results.mapNotNull { (it as? JsonObject)?.let { r -> r.str("name") ?: r.str("Name") } }.distinct()
        }
    }
}
