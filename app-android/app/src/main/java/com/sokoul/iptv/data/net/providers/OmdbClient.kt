package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.parser.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/** Notes externes : IMDb « 8,7 », Rotten Tomatoes « 73 % », Metacritic « 74 ». */
data class Ratings(val imdb: String?, val rottenTomatoes: String?, val metacritic: String?) {
    val isEmpty get() = imdb == null && rottenTomatoes == null && metacritic == null
}

/** OMDb : notes IMDb / Rotten Tomatoes / Metacritic par identifiant IMDb. Inactif sans clé. */
@Singleton
class OmdbClient @Inject constructor(private val api: ApiHttp, private val keys: ApiKeys) {

    suspend fun ratings(imdbId: String): Ratings? {
        val key = keys.key(ApiService.OMDB).ifEmpty { return null }
        val url = BASE.toHttpUrl().newBuilder().addQueryParameter("i", imdbId).addQueryParameter("apikey", key).build()
        val o = api.getJson(ApiService.OMDB, url, ttlMs = 7 * ApiHttp.DAY, secret = key) as? JsonObject ?: return null
        return parse(o)
    }

    companion object {
        const val BASE = "https://www.omdbapi.com/"

        fun parse(o: JsonObject): Ratings? {
            if (o.str("Response") == "False") return null
            val list = (o["Ratings"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            fun src(name: String) = list.firstOrNull { it.str("Source") == name }?.str("Value")
            val imdb = (o.str("imdbRating") ?: src("Internet Movie Database")?.substringBefore('/'))
                ?.takeIf { it != "N/A" }?.replace('.', ',')
            val rt = src("Rotten Tomatoes")?.replace("%", " %")
            val mc = (o.str("Metascore") ?: src("Metacritic")?.substringBefore('/'))?.takeIf { it != "N/A" }
            return Ratings(imdb, rt, mc).takeIf { !it.isEmpty }
        }
    }
}
