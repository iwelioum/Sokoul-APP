package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.parser.int
import com.sokoul.iptv.parser.str
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Prochain épisode d'une série en cours (TVmaze). */
data class NextEpisode(val season: Int, val episode: Int, val name: String?, val airsAt: Instant)

/** TVmaze, API publique sans clé : prochain épisode d'une série, retrouvée par son identifiant TheTVDB ou IMDb. */
@Singleton
class TvmazeClient @Inject constructor(private val api: ApiHttp) {

    suspend fun nextEpisode(tvdbId: Int?, imdbId: String?): NextEpisode? {
        val lookup = when {
            tvdbId != null -> "$BASE/lookup/shows".toHttpUrl().newBuilder().addQueryParameter("thetvdb", tvdbId.toString()).build()
            imdbId != null -> "$BASE/lookup/shows".toHttpUrl().newBuilder().addQueryParameter("imdb", imdbId).build()
            else -> return null
        }
        val show = api.getJson(ApiService.TVMAZE, lookup, ttlMs = 7 * ApiHttp.DAY) as? JsonObject ?: return null
        if (show.str("status") != "Running") return null
        val id = show.int("id") ?: return null
        val url = "$BASE/shows/$id".toHttpUrl().newBuilder().addQueryParameter("embed", "nextepisode").build()
        val o = api.getJson(ApiService.TVMAZE, url, ttlMs = 12 * ApiHttp.HOUR) as? JsonObject ?: return null
        return parseNext(o)
    }

    companion object {
        const val BASE = "https://api.tvmaze.com"

        fun parseNext(o: JsonObject): NextEpisode? {
            val e = ((o["_embedded"] as? JsonObject)?.get("nextepisode") as? JsonObject) ?: return null
            val at = e.str("airstamp")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
            return NextEpisode(e.int("season") ?: return null, e.int("number") ?: return null, e.str("name"), at)
        }
    }
}
