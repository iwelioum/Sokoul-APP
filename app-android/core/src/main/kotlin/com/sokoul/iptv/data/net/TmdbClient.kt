package com.sokoul.iptv.data.net

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
import javax.inject.Inject
import javax.inject.Singleton

/** Personne de la distribution d'une fiche ; [id] mène à sa page (filmographie). */
data class TmdbPerson(val name: String, val role: String?, val photo: String?, val id: Int? = null)

/**
 * Page d'une personne : identité et filmographie ([credits] = rôles, [directed] = réalisation/création),
 * les plus connus d'abord. [gender] : 1 femme, 2 homme, 0 inconnu (TMDB).
 */
data class TmdbPersonInfo(
    val id: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val department: String?,
    val gender: Int,
    val photo: String?,
    val credits: List<TmdbItem>,
    val directed: List<TmdbItem>,
)

/** Personne d'un générique : [role] = personnage (distribution) ou poste (« Director »…), [order] = rang au générique. */
data class TmdbCredit(val id: Int, val name: String, val role: String?, val photo: String?, val order: Int)
data class TmdbCredits(val movieId: Int, val cast: List<TmdbCredit>, val crew: List<TmdbCredit>)

data class TmdbInfo(
    val id: Int,
    val title: String,
    val overview: String?,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    /** Logo anglais (sagas : celui du film en VO porte le nom de la franchise sans sous-titre français). */
    val logoEn: String? = null,
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
    /** Identifiants IMDb (« tt0816692 ») et TheTVDB : ponts vers les autres sources de données. */
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    /** Série en cours de diffusion (TMDB « Returning Series »). */
    val inProduction: Boolean = false,
    /** Langue originale (« fr », « en », « ko »…). */
    val originalLanguage: String? = null,
    /** Identifiants de genres TMDB (différents pour les films et les séries). */
    val genreIds: List<Int> = emptyList(),
    val voteCount: Int? = null,
    /** Noms des mots-clés TMDB, en minuscules (« heist », « time travel »). */
    val keywords: List<String>? = null,
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
    val genreIds: List<Int> = emptyList(),
    val originalLanguage: String? = null,
    val voteCount: Int? = null,
    /** « movie » ou « tv » quand la liste mélange les deux (filmographie). */
    val mediaType: String? = null,
)

data class TmdbEpisode(val season: Int, val episode: Int, val name: String?, val overview: String?, val still: String?, val runtimeMin: Int?)

data class TmdbCollection(val id: Int, val name: String, val overview: String?, val poster: String?, val backdrop: String?, val parts: List<TmdbItem>)

/**
 * Enrichissement des films/séries via TMDB (clé API saisie dans les réglages), en français.
 * Toutes les réponses sont mises en cache ([TmdbCache] : Room sur Android, SQLite sur PC).
 */
@Singleton
class TmdbClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val cache: TmdbCache,
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
    suspend fun match(apiKey: String, kind: String, title: String, year: Int?, store: Boolean = true): Int? {
        if (apiKey.isBlank()) return null
        return safe {
            val norm = VodGrouping.normalizeTitle(title, year)
            if (norm.length < 2) return@safe null
            val params = buildMap {
                put("query", VodGrouping.stripCountry(title))
                if (year != null) put(if (kind == "movie") "primary_release_year" else "first_air_date_year", year.toString())
            }
            var results = items(get(apiKey, "/3/search/$kind", params, store = store), kind)
            if (results.isEmpty() && year != null) {
                results = items(get(apiKey, "/3/search/$kind", mapOf("query" to VodGrouping.stripCountry(title)), store = store), kind)
            }
            val exact = results.filter { r ->
                VodGrouping.normalizeTitle(r.title, r.year) == norm || r.originalTitle?.let { VodGrouping.normalizeTitle(it, r.year) } == norm
            }
            if (year != null) exact.firstOrNull { r -> r.year != null && kotlin.math.abs(r.year - year) <= 1 }?.id
            else exact.singleOrNull()?.id
        }
    }

    /** Fiche TMDB ; `full` ajoute casting et vidéos (fiche détaillée), sinon images seulement (enrichissement des rangées). */
    /**
     * [store] faux : réponse non gardée dans le cache (indexation de tout le catalogue : des dizaines
     * de milliers de fiches, que le cache ferait gonfler pour rien).
     */
    suspend fun details(apiKey: String, kind: String, id: Int, full: Boolean, store: Boolean = true): TmdbInfo? {
        if (apiKey.isBlank()) return null
        return safe {
            val ratings = if (kind == "movie") "release_dates" else "content_ratings"
            val append = if (full) "credits,videos,images,external_ids,keywords,$ratings" else "images,keywords,$ratings"
            val o = get(apiKey, "/3/$kind/$id", mapOf("append_to_response" to append, "include_image_language" to "fr,en,null"), store = store)
                ?: return@safe null
            var info = parse(kind, o)
            if (info.overview.isNullOrBlank()) {
                val en = get(apiKey, "/3/$kind/$id", emptyMap(), language = "en-US", store = store)
                info = info.copy(overviewEn = en?.str("overview")?.takeIf { it.isNotBlank() })
            }
            info
        }
    }

    /** Identifiant IMDb (« tt0816692 ») d'une œuvre TMDB, pour les extensions de sources. Cache 30 jours. */
    suspend fun imdbId(apiKey: String, kind: String, id: Int): String? {
        if (apiKey.isBlank()) return null
        return safe { get(apiKey, "/3/$kind/$id/external_ids", emptyMap(), ttlMs = 30 * 24 * HOUR)?.str("imdb_id")?.takeIf { it.startsWith("tt") } }
    }

    suspend fun trending(apiKey: String, kind: String): List<TmdbItem> {
        if (apiKey.isBlank()) return emptyList()
        return safe {
            (1..2).flatMap { page -> items(get(apiKey, "/3/trending/$kind/week", mapOf("page" to page.toString()), ttlMs = 12 * HOUR), kind) }
        } ?: emptyList()
    }

    /** Tendances du jour (une page, cache 3 h) : « Ce qui buzze aujourd'hui » et le héros de l'accueil. */
    suspend fun trendingDay(apiKey: String, kind: String): List<TmdbItem> {
        if (apiKey.isBlank()) return emptyList()
        return safe { items(get(apiKey, "/3/trending/$kind/day", emptyMap(), ttlMs = 3 * HOUR), kind) } ?: emptyList()
    }

    /** Listes TMDB : « top_rated », « popular »… (2 pages, cache 1 jour). */
    suspend fun list(apiKey: String, kind: String, name: String): List<TmdbItem> {
        if (apiKey.isBlank()) return emptyList()
        return safe {
            (1..2).flatMap { page -> items(get(apiKey, "/3/$kind/$name", mapOf("page" to page.toString()), ttlMs = 24 * HOUR), kind) }
        } ?: emptyList()
    }

    /** Découverte TMDB (genre, plateforme, tri) : une page, cache 1 jour. Sert aux rangées et grilles de l'app PC. */
    suspend fun discover(apiKey: String, kind: String, params: Map<String, String>, page: Int = 1): List<TmdbItem> {
        if (apiKey.isBlank()) return emptyList()
        return safe { items(get(apiKey, "/3/discover/$kind", params + ("page" to page.toString()), ttlMs = 24 * HOUR), kind) } ?: emptyList()
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

    /** Générique complet d'un film (sagas : visages récurrents, réalisation, musique, production). Cache 7 jours. */
    suspend fun credits(apiKey: String, movieId: Int): TmdbCredits? {
        if (apiKey.isBlank()) return null
        return safe {
            val o = get(apiKey, "/3/movie/$movieId/credits", emptyMap()) ?: return@safe null
            val cast = (o["cast"] as? JsonArray).orEmpty().mapNotNull { e ->
                val c = e as? JsonObject ?: return@mapNotNull null
                TmdbCredit(c.int("id") ?: return@mapNotNull null, c.str("name") ?: return@mapNotNull null, c.str("character"),
                    c.str("profile_path")?.let { "$IMG/w185$it" }, c.int("order") ?: 99)
            }
            val crew = (o["crew"] as? JsonArray).orEmpty().mapNotNull { e ->
                val c = e as? JsonObject ?: return@mapNotNull null
                TmdbCredit(c.int("id") ?: return@mapNotNull null, c.str("name") ?: return@mapNotNull null, c.str("job"),
                    c.str("profile_path")?.let { "$IMG/w185$it" }, 99)
            }
            TmdbCredits(movieId, cast, crew)
        }
    }

    /** Œuvre portant exactement ce titre (français ou original), la plus populaire : sert à rapprocher un nom (TasteDive). */
    /** Recherche films et séries ensemble (une page, cache 1 jour) : rangée Real-Debrid de la recherche. */
    suspend fun searchMulti(apiKey: String, query: String): List<TmdbItem> {
        if (apiKey.isBlank() || query.trim().length < 2) return emptyList()
        return safe { items(get(apiKey, "/3/search/multi", mapOf("query" to query.trim()), ttlMs = 24 * HOUR), "movie") } ?: emptyList()
    }

    suspend fun findByName(apiKey: String, kind: String, title: String): TmdbItem? {
        if (apiKey.isBlank()) return null
        return safe {
            val norm = VodGrouping.normalizeTitle(title, null)
            if (norm.length < 2) return@safe null
            items(get(apiKey, "/3/search/$kind", mapOf("query" to title)), kind).firstOrNull { r ->
                VodGrouping.normalizeTitle(r.title, r.year) == VodGrouping.normalizeTitle(title, r.year) ||
                    r.originalTitle?.let { VodGrouping.normalizeTitle(it, r.year) == VodGrouping.normalizeTitle(title, r.year) } == true
            }
        }
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
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { CoreLog.warn("TMDB indisponible", e); null }
    }

    private suspend fun get(apiKey: String, path: String, params: Map<String, String>, language: String = "fr-FR", ttlMs: Long = 7 * 24 * HOUR, store: Boolean = true): JsonObject? {
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
        if (store) cache.put(key, text, now)
        return json.parseToJsonElement(text) as? JsonObject
    }

    /**
     * Page d'une personne, avec sa filmographie (`combined_credits`), en français ; biographie anglaise à défaut.
     * Émissions de plateau, d'actualité et de téléréalité écartées (apparitions « dans son propre rôle »). Cache 7 jours.
     */
    suspend fun person(apiKey: String, id: Int): TmdbPersonInfo? {
        if (apiKey.isBlank()) return null
        return safe {
            val o = get(apiKey, "/3/person/$id", mapOf("append_to_response" to "combined_credits")) ?: return@safe null
            val bioFr = o.str("biography")?.takeIf { it.isNotBlank() }
            val bio = bioFr ?: get(apiKey, "/3/person/$id", emptyMap(), language = "en-US")?.str("biography")?.takeIf { it.isNotBlank() }
            val cc = o["combined_credits"] as? JsonObject
            fun keep(item: TmdbItem) = item.mediaType != "tv" || item.genreIds.none { it in NOT_FICTION_TV }
            val cast = (cc?.get("cast") as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                itemOf(x, "movie")?.takeIf { keep(it) }
            }
            val directed = (cc?.get("crew") as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                if (x.str("job") !in DIRECTING_JOBS) return@mapNotNull null
                itemOf(x, "movie")?.takeIf { keep(it) }
            }
            fun List<TmdbItem>.ranked() = distinctBy { "${it.mediaType}:${it.id}" }.sortedByDescending { it.voteCount ?: 0 }
            TmdbPersonInfo(
                id = id, name = o.str("name") ?: return@safe null, biography = bio,
                birthday = o.str("birthday"), deathday = o.str("deathday"), placeOfBirth = o.str("place_of_birth"),
                department = o.str("known_for_department"), gender = o.int("gender") ?: 0,
                photo = o.str("profile_path")?.let { "$IMG/w342$it" },
                credits = cast.ranked(), directed = directed.ranked(),
            )
        }
    }

    private fun items(o: JsonObject?, kind: String, field: String = "results"): List<TmdbItem> =
        (o?.get(field) as? JsonArray).orEmpty().mapNotNull { e -> (e as? JsonObject)?.let { itemOf(it, kind) } }

    private fun itemOf(x: JsonObject, kind: String): TmdbItem? {
            val k = x.str("media_type") ?: kind
            if (k != "movie" && k != "tv") return null
            return TmdbItem(
                id = x.int("id") ?: return null,
                title = x.str("title") ?: x.str("name") ?: return null,
                originalTitle = x.str("original_title") ?: x.str("original_name"),
                year = (x.str("release_date") ?: x.str("first_air_date"))?.take(4)?.toIntOrNull(),
                poster = x.str("poster_path")?.let { "$IMG/w342$it" },
                backdrop = x.str("backdrop_path")?.let { "$IMG/w1280$it" },
                rating = x.double("vote_average")?.takeIf { it > 0 },
                overview = x.str("overview")?.takeIf { it.isNotBlank() },
                genreIds = (x["genre_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() },
                originalLanguage = x.str("original_language"),
                voteCount = x.int("vote_count"),
                mediaType = k,
            )
    }

    private fun parse(kind: String, o: JsonObject): TmdbInfo {
        val credits = o["credits"] as? JsonObject
        val cast = (credits?.get("cast") as? JsonArray).orEmpty().take(15).mapNotNull {
            val c = it as? JsonObject ?: return@mapNotNull null
            TmdbPerson(c.str("name") ?: return@mapNotNull null, c.str("character"), c.str("profile_path")?.let { p -> "$IMG/w185$p" }, c.int("id"))
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
            logoEn = logos.firstOrNull { it.str("iso_639_1") == "en" }?.str("file_path")?.let { "$IMG/w500$it" },
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
            imdbId = (o.str("imdb_id") ?: (o["external_ids"] as? JsonObject)?.str("imdb_id"))?.takeIf { it.startsWith("tt") },
            tvdbId = (o["external_ids"] as? JsonObject)?.int("tvdb_id"),
            inProduction = o["in_production"]?.toString() == "true",
            originalLanguage = o.str("original_language"),
            genreIds = (o["genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.int("id") },
            voteCount = o.int("vote_count"),
            keywords = (o["keywords"] as? JsonObject)?.let { k ->
                // films : « keywords », séries : « results »
                ((k["keywords"] ?: k["results"]) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name")?.lowercase()?.trim() }
            },
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
        /** Genres TV écartés des filmographies : actualité, téléréalité, talk-show. */
        private val NOT_FICTION_TV = setOf(10763, 10764, 10767)
        private val DIRECTING_JOBS = setOf("Director", "Creator")
        private const val HOUR = 3600_000L
    }
}

/** Exécute la requête sans bloquer de thread ; l'annulation de la coroutine annule l'appel HTTP. */
suspend fun okhttp3.Call.await(): okhttp3.Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { if (cont.isActive) cont.resumeWith(Result.failure(e)) }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            if (cont.isActive) cont.resumeWith(Result.success(response)) else response.close()
        }
    })
}
