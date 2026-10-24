package com.sokoul.iptv.data.net

import com.sokoul.iptv.data.db.TmdbCacheEntity
import com.sokoul.iptv.data.db.TmdbDao
import com.sokoul.iptv.parser.double
import com.sokoul.iptv.parser.int
import com.sokoul.iptv.parser.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class TmdbPerson(val name: String, val role: String?, val photo: String?)

data class TmdbInfo(
    val id: Int,
    val title: String,
    val overview: String?,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val rating: Double?,
    val year: Int?,
    val runtimeMin: Int?,
    val genres: List<String>,
    val cast: List<TmdbPerson>,
    val director: String?,
    val trailerYoutubeKey: String?,
)

/**
 * Enrichissement facultatif des films/séries via TMDB (clé API saisie dans les réglages).
 * Portage du client de Sokoul (src/clients/tmdb.rs) : français par défaut, cache 7 jours.
 */
@Singleton
class TmdbClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val cache: TmdbDao,
) {
    private val img = "https://image.tmdb.org/t/p"

    suspend fun movie(apiKey: String, title: String, year: Int?, tmdbId: String?): TmdbInfo? =
        find(apiKey, "movie", title, year, tmdbId)

    suspend fun tv(apiKey: String, title: String, year: Int?, tmdbId: String?): TmdbInfo? =
        find(apiKey, "tv", title, year, tmdbId)

    private suspend fun find(apiKey: String, kind: String, title: String, year: Int?, tmdbId: String?): TmdbInfo? {
        if (apiKey.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val id = tmdbId?.toIntOrNull()?.takeIf { it > 0 } ?: search(apiKey, kind, title, year) ?: return@withContext null
                val o = get(apiKey, "/3/$kind/$id", mapOf("append_to_response" to "credits,videos,images", "include_image_language" to "fr,en,null"))
                    ?: return@withContext null
                parse(kind, o)
            } catch (e: Exception) {
                Timber.w(e, "TMDB indisponible")
                null
            }
        }
    }

    private suspend fun search(apiKey: String, kind: String, title: String, year: Int?): Int? {
        val params = buildMap {
            put("query", title)
            if (year != null) put(if (kind == "movie") "year" else "first_air_date_year", year.toString())
        }
        val results = (get(apiKey, "/3/search/$kind", params)?.get("results") as? JsonArray)
        val first = results?.firstOrNull() as? JsonObject
        if (first == null && year != null) return search(apiKey, kind, title, null)
        return first?.int("id")
    }

    private suspend fun get(apiKey: String, path: String, params: Map<String, String>): JsonObject? {
        val url = "https://api.themoviedb.org$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", apiKey)
            addQueryParameter("language", "fr-FR")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val key = url.toString().replace(apiKey, "")
        val now = System.currentTimeMillis()
        val cached = cache.get(key)
        if (cached != null && now - cached.fetchedAt < 7 * 24 * 3600_000L) {
            return json.parseToJsonElement(cached.json) as? JsonObject
        }
        val text = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }
        cache.put(TmdbCacheEntity(key, text, now))
        return json.parseToJsonElement(text) as? JsonObject
    }

    private fun parse(kind: String, o: JsonObject): TmdbInfo {
        val credits = o["credits"] as? JsonObject
        val cast = (credits?.get("cast") as? JsonArray).orEmpty().take(12).mapNotNull {
            val c = it as? JsonObject ?: return@mapNotNull null
            TmdbPerson(c.str("name") ?: return@mapNotNull null, c.str("character"), c.str("profile_path")?.let { p -> "$img/w185$p" })
        }
        val director = (credits?.get("crew") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }.firstOrNull { it.str("job") == "Director" }?.str("name")
            ?: (o["created_by"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.str("name") }
        val videos = (o["videos"] as? JsonObject)?.get("results") as? JsonArray
        val trailer = videos.orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("site") == "YouTube" && (it.str("type") == "Trailer" || it.str("type") == "Teaser") }?.str("key")
        val logos = ((o["images"] as? JsonObject)?.get("logos") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val logo = (logos.firstOrNull { it.str("iso_639_1") == "fr" } ?: logos.firstOrNull { it.str("iso_639_1") == "en" } ?: logos.firstOrNull())
            ?.str("file_path")?.let { "$img/w500$it" }
        val date = o.str(if (kind == "movie") "release_date" else "first_air_date")
        val runtime = o.int("runtime") ?: (o["episode_run_time"] as? JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
        return TmdbInfo(
            id = o.int("id") ?: 0,
            title = o.str("title") ?: o.str("name") ?: "",
            overview = o.str("overview"),
            poster = o.str("poster_path")?.let { "$img/w500$it" },
            backdrop = o.str("backdrop_path")?.let { "$img/w1280$it" },
            logo = logo,
            rating = o.double("vote_average")?.takeIf { it > 0 },
            year = date?.take(4)?.toIntOrNull(),
            runtimeMin = runtime,
            genres = (o["genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
            cast = cast,
            director = director,
            trailerYoutubeKey = trailer,
        )
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
