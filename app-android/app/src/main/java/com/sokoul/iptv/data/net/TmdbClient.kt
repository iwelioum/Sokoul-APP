package com.sokoul.iptv.data.net

import com.sokoul.iptv.data.db.TmdbCacheEntity
import com.sokoul.iptv.data.db.TmdbDao
import com.sokoul.iptv.data.repo.VodGrouping
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
    val tagline: String? = null,
    val originalTitle: String? = null,
    /** Résumé anglais, seulement quand TMDB n'a pas de résumé français. */
    val overviewEn: String? = null,
    val collectionId: Int? = null,
    val collectionName: String? = null,
    val certification: String? = null,
    val seasons: Int? = null,
)

/** Résultat de liste TMDB (tendances, recommandations, collection). */
data class TmdbItem(
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val poster: String?,
    val backdrop: String?,
    val rating: Double?,
    val overview: String?,
)

data class TmdbEpisode(val season: Int, val episode: Int, val name: String?, val overview: String?, val still: String?, val runtimeMin: Int?)

data class TmdbCollection(val id: Int, val name: String, val overview: String?, val poster: String?, val backdrop: String?, val parts: List<TmdbItem>)

/**
 * Enrichissement des films/séries via TMDB (clé API saisie dans les réglages), en français.
 * Toutes les réponses sont mises en cache en base (`tmdb_cache`).
 */
@Singleton
class TmdbClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val cache: TmdbDao,
) {
    suspend fun movie(apiKey: String, title: String, year: Int?, tmdbId: String?): TmdbInfo? =
        find(apiKey, "movie", title, year, tmdbId)

    suspend fun tv(apiKey: String, title: String, year: Int?, tmdbId: String?): TmdbInfo? =
        find(apiKey, "tv", title, year, tmdbId)

    private suspend fun find(apiKey: String, kind: String, title: String, year: Int?, tmdbId: String?): TmdbInfo? {
        if (apiKey.isBlank()) return null
        return safe {
            val id = tmdbId?.toIntOrNull()?.takeIf { it > 0 } ?: search(apiKey, kind, title, year) ?: return@safe null
            details(apiKey, kind, id, full = true)
        }
    }

    /**
     * Identifiant TMDB d'un titre du catalogue, seulement si la correspondance est sûre :
     * titre identique (titre français ou original, normalisé) et année à ±1 an.
     * Sans année : accepté uniquement si un seul résultat porte exactement ce titre.
     */
    suspend fun match(apiKey: String, kind: String, title: String, year: Int?): Int? {
        if (apiKey.isBlank()) return null
        return safe {
            val norm = VodGrouping.normalizeTitle(title, year)
            if (norm.length < 2) return@safe null
            val params = buildMap {
                put("query", VodGrouping.stripCountry(title))
                if (year != null) put(if (kind == "movie") "primary_release_year" else "first_air_date_year", year.toString())
            }
            var results = items(get(apiKey, "/3/search/$kind", params), kind)
            if (results.isEmpty() && year != null) {
                results = items(get(apiKey, "/3/search/$kind", mapOf("query" to VodGrouping.stripCountry(title))), kind)
            }
            val exact = results.filter { r ->
                VodGrouping.normalizeTitle(r.title, r.year) == norm || r.originalTitle?.let { VodGrouping.normalizeTitle(it, r.year) } == norm
            }
            if (year != null) exact.firstOrNull { r -> r.year != null && kotlin.math.abs(r.year - year) <= 1 }?.id
            else exact.singleOrNull()?.id
        }
    }

    /** Fiche TMDB ; `full` ajoute casting et vidéos (fiche détaillée), sinon images seulement (enrichissement des rangées). */
    suspend fun details(apiKey: String, kind: String, id: Int, full: Boolean): TmdbInfo? {
        if (apiKey.isBlank()) return null
        return safe {
            val ratings = if (kind == "movie") "release_dates" else "content_ratings"
            val append = if (full) "credits,videos,images,$ratings" else "images,$ratings"
            val o = get(apiKey, "/3/$kind/$id", mapOf("append_to_response" to append, "include_image_language" to "fr,en,null"))
                ?: return@safe null
            var info = parse(kind, o)
            if (info.overview.isNullOrBlank()) {
                val en = get(apiKey, "/3/$kind/$id", emptyMap(), language = "en-US")
                info = info.copy(overviewEn = en?.str("overview")?.takeIf { it.isNotBlank() })
            }
            info
        }
    }

    suspend fun trending(apiKey: String, kind: String): List<TmdbItem> {
        if (apiKey.isBlank()) return emptyList()
        return safe {
            (1..2).flatMap { page -> items(get(apiKey, "/3/trending/$kind/week", mapOf("page" to page.toString()), ttlMs = 12 * HOUR), kind) }
        } ?: emptyList()
    }

    /** Listes TMDB : « top_rated », « popular »… (2 pages, cache 1 jour). */
    suspend fun list(apiKey: String, kind: String, name: String): List<TmdbItem> {
        if (apiKey.isBlank()) return emptyList()
        return safe {
            (1..2).flatMap { page -> items(get(apiKey, "/3/$kind/$name", mapOf("page" to page.toString()), ttlMs = 24 * HOUR), kind) }
        } ?: emptyList()
    }

    suspend fun recommendations(apiKey: String, kind: String, id: Int): List<TmdbItem> {
        if (apiKey.isBlank()) return emptyList()
        return safe {
            val rec = items(get(apiKey, "/3/$kind/$id/recommendations", emptyMap()), kind)
            rec.ifEmpty { items(get(apiKey, "/3/$kind/$id/similar", emptyMap()), kind) }
        } ?: emptyList()
    }

    suspend fun collection(apiKey: String, id: Int): TmdbCollection? {
        if (apiKey.isBlank()) return null
        return safe {
            val o = get(apiKey, "/3/collection/$id", emptyMap()) ?: return@safe null
            TmdbCollection(
                id = id, name = o.str("name") ?: return@safe null, overview = o.str("overview"),
                poster = o.str("poster_path")?.let { "$IMG/w500$it" }, backdrop = o.str("backdrop_path")?.let { "$IMG/w1280$it" },
                parts = items(o, "movie", "parts").sortedBy { it.year ?: 9999 },
            )
        }
    }

    suspend fun season(apiKey: String, tvId: Int, season: Int): List<TmdbEpisode> {
        if (apiKey.isBlank()) return emptyList()
        return safe {
            val o = get(apiKey, "/3/tv/$tvId/season/$season", emptyMap()) ?: return@safe emptyList()
            (o["episodes"] as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                TmdbEpisode(
                    season = x.int("season_number") ?: season, episode = x.int("episode_number") ?: return@mapNotNull null,
                    name = x.str("name")?.takeIf { it.isNotBlank() }, overview = x.str("overview")?.takeIf { it.isNotBlank() },
                    still = x.str("still_path")?.let { "$IMG/w500$it" }, runtimeMin = x.int("runtime"),
                )
            }
        } ?: emptyList()
    }

    private suspend fun search(apiKey: String, kind: String, title: String, year: Int?): Int? =
        match(apiKey, kind, title, year) ?: run {
            // repli historique : premier résultat (fiche ouverte par l'utilisateur, pas de regroupement automatique)
            val params = buildMap {
                put("query", VodGrouping.stripCountry(title))
                if (year != null) put(if (kind == "movie") "year" else "first_air_date_year", year.toString())
            }
            (get(apiKey, "/3/search/$kind", params)?.get("results") as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.int("id") }
        }

    private suspend fun <T> safe(block: suspend () -> T): T? = withContext(Dispatchers.IO) {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Timber.w(e, "TMDB indisponible"); null }
    }

    private suspend fun get(apiKey: String, path: String, params: Map<String, String>, language: String = "fr-FR", ttlMs: Long = 7 * 24 * HOUR): JsonObject? {
        val url = "https://api.themoviedb.org$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", apiKey)
            addQueryParameter("language", language)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val key = url.toString().replace(apiKey, "")
        val now = System.currentTimeMillis()
        val cached = cache.get(key)
        if (cached != null && now - cached.fetchedAt < ttlMs) {
            return json.parseToJsonElement(cached.json) as? JsonObject
        }
        // appel annulable : une page qui n'attend plus TMDB coupe aussi la requête (sinon jusqu'à 60 s de blocage)
        val text = http.newCall(Request.Builder().url(url).build()).await().use { resp ->
            if (!resp.isSuccessful) return cached?.let { json.parseToJsonElement(it.json) as? JsonObject }
            resp.body?.string() ?: return null
        }
        cache.put(TmdbCacheEntity(key, text, now))
        return json.parseToJsonElement(text) as? JsonObject
    }

    private fun items(o: JsonObject?, kind: String, field: String = "results"): List<TmdbItem> =
        (o?.get(field) as? JsonArray).orEmpty().mapNotNull { e ->
            val x = e as? JsonObject ?: return@mapNotNull null
            val k = x.str("media_type") ?: kind
            if (k != "movie" && k != "tv") return@mapNotNull null
            TmdbItem(
                id = x.int("id") ?: return@mapNotNull null,
                title = x.str("title") ?: x.str("name") ?: return@mapNotNull null,
                originalTitle = x.str("original_title") ?: x.str("original_name"),
                year = (x.str("release_date") ?: x.str("first_air_date"))?.take(4)?.toIntOrNull(),
                poster = x.str("poster_path")?.let { "$IMG/w342$it" },
                backdrop = x.str("backdrop_path")?.let { "$IMG/w1280$it" },
                rating = x.double("vote_average")?.takeIf { it > 0 },
                overview = x.str("overview")?.takeIf { it.isNotBlank() },
            )
        }

    private fun parse(kind: String, o: JsonObject): TmdbInfo {
        val credits = o["credits"] as? JsonObject
        val cast = (credits?.get("cast") as? JsonArray).orEmpty().take(15).mapNotNull {
            val c = it as? JsonObject ?: return@mapNotNull null
            TmdbPerson(c.str("name") ?: return@mapNotNull null, c.str("character"), c.str("profile_path")?.let { p -> "$IMG/w185$p" })
        }
        val director = (credits?.get("crew") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }.firstOrNull { it.str("job") == "Director" }?.str("name")
            ?: (o["created_by"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.str("name") }
        val videos = ((o["videos"] as? JsonObject)?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val trailer = (videos.firstOrNull { it.str("site") == "YouTube" && it.str("type") == "Trailer" && it.str("iso_639_1") == "fr" }
            ?: videos.firstOrNull { it.str("site") == "YouTube" && (it.str("type") == "Trailer" || it.str("type") == "Teaser") })?.str("key")
        val logos = ((o["images"] as? JsonObject)?.get("logos") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val logo = (logos.firstOrNull { it.str("iso_639_1") == "fr" } ?: logos.firstOrNull { it.str("iso_639_1") == "en" } ?: logos.firstOrNull())
            ?.str("file_path")?.let { "$IMG/w500$it" }
        val backdrops = ((o["images"] as? JsonObject)?.get("backdrops") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        // fond sans texte incrusté de préférence (le logo est affiché par-dessus)
        val backdrop = (backdrops.firstOrNull { it.str("iso_639_1") == null }?.str("file_path") ?: o.str("backdrop_path"))?.let { "$IMG/w1280$it" }
        val date = o.str(if (kind == "movie") "release_date" else "first_air_date")
        val runtime = o.int("runtime") ?: (o["episode_run_time"] as? JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
        val collection = o["belongs_to_collection"] as? JsonObject
        return TmdbInfo(
            id = o.int("id") ?: 0,
            title = o.str("title") ?: o.str("name") ?: "",
            overview = o.str("overview")?.takeIf { it.isNotBlank() },
            poster = o.str("poster_path")?.let { "$IMG/w500$it" },
            backdrop = backdrop,
            logo = logo,
            rating = o.double("vote_average")?.takeIf { it > 0 },
            year = date?.take(4)?.toIntOrNull(),
            runtimeMin = runtime,
            genres = (o["genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
            cast = cast,
            director = director,
            trailerYoutubeKey = trailer,
            tagline = o.str("tagline")?.takeIf { it.isNotBlank() },
            originalTitle = o.str("original_title") ?: o.str("original_name"),
            collectionId = collection?.int("id"),
            collectionName = collection?.str("name"),
            certification = certification(kind, o),
            seasons = o.int("number_of_seasons"),
        )
    }

    /** Classification française (« 12 », « 16 »…) si TMDB la connaît. */
    private fun certification(kind: String, o: JsonObject): String? {
        return if (kind == "movie") {
            val fr = ((o["release_dates"] as? JsonObject)?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .firstOrNull { it.str("iso_3166_1") == "FR" } ?: return null
            (fr["release_dates"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("certification") }.firstOrNull { it.isNotBlank() }
        } else {
            ((o["content_ratings"] as? JsonObject)?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .firstOrNull { it.str("iso_3166_1") == "FR" }?.str("rating")?.takeIf { it.isNotBlank() }
        }
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

    companion object {
        const val IMG = "https://image.tmdb.org/t/p"
        private const val HOUR = 3600_000L
    }
}

/** Exécute la requête sans bloquer de thread ; l'annulation de la coroutine annule l'appel HTTP. */
internal suspend fun okhttp3.Call.await(): okhttp3.Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { if (cont.isActive) cont.resumeWith(Result.failure(e)) }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            if (cont.isActive) cont.resumeWith(Result.success(response)) else response.close()
        }
    })
}
