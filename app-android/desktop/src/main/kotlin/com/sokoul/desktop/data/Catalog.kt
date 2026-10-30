package com.sokoul.desktop.data

import com.sokoul.iptv.data.net.TmdbCache
import com.sokoul.iptv.data.net.TmdbCached
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Une rangée de l'accueil : titre, œuvres, et style (affiches, Top 10 numéroté, paysage). */
data class Shelf(val id: String, val title: String, val items: List<TmdbItem>, val style: ShelfStyle = ShelfStyle.POSTER)

enum class ShelfStyle { POSTER, TOP10, LANDSCAPE }

/** Œuvre du héros : l'item de liste et sa fiche (logo, repères). */
data class Feature(val item: TmdbItem, val info: TmdbInfo?)

/** Genres TMDB (identifiants fixes, libellés français). */
data class Genre(val id: Int, val name: String)

/**
 * Catalogue TMDB du PC : même client que l'Android (`:core`), cache dans la base SQLite.
 * Toutes les fonctions rendent une liste vide plutôt qu'une erreur (TMDB indisponible = rangée absente).
 */
class Catalog(private val db: DesktopDb) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val tmdb = TmdbClient(http, json, object : TmdbCache {
        override suspend fun get(key: String) = withContext(Dispatchers.IO) { db.tmdbGet(key)?.let { TmdbCached(it.first, it.second) } }
        override suspend fun put(key: String, json: String, fetchedAt: Long) = withContext(Dispatchers.IO) { db.tmdbPut(key, json, fetchedAt) }
    })

    /** Clé TMDB : saisie ou intégrée, sinon celle importée de la tablette. */
    fun key(): String = ProviderConfig.key(db, "tmdb") ?: db.setting(Settings.TMDB_KEY).orEmpty()

    fun hasKey(): Boolean = key().isNotBlank()

    /** Héros : tendances du jour (films et séries), avec image, dont on charge la fiche (logo, repères). */
    suspend fun features(count: Int = 6): List<Feature> = coroutineScope {
        val k = key()
        val day = (tmdb.trendingDay(k, "all").ifEmpty { tmdb.trendingDay(k, "movie") })
            .filter { it.backdrop != null && !it.overview.isNullOrBlank() }.take(count)
        day.map { item -> async { Feature(item, tmdb.details(k, item.mediaType ?: "movie", item.id, full = false)) } }.map { it.await() }
    }

    /** Rangées de l'accueil, chargées en parallèle ; une rangée vide est retirée. */
    suspend fun home(): List<Shelf> = coroutineScope {
        val k = key()
        fun platform(id: String, title: String, provider: Int, kind: String) = async {
            Shelf(id, title, tmdb.discover(k, kind, mapOf("with_watch_providers" to "$provider", "watch_region" to "FR", "sort_by" to "popularity.desc")))
        }
        listOf(
            async { Shelf("top10", "Top 10 aujourd'hui", tmdb.trendingDay(k, "all").filter { it.poster != null }.take(10), ShelfStyle.TOP10) },
            async { Shelf("movies", "Films populaires", tmdb.list(k, "movie", "popular")) },
            async { Shelf("series", "Séries du moment", tmdb.trending(k, "tv"), ShelfStyle.LANDSCAPE) },
            platform("netflix", "Sur Netflix", 8, "tv"),
            async { Shelf("toprated", "Les mieux notés", tmdb.list(k, "movie", "top_rated")) },
            platform("prime", "Sur Prime Video", 119, "movie"),
            platform("disney", "Sur Disney+", 337, "movie"),
            async { Shelf("tvtop", "Séries cultes", tmdb.list(k, "tv", "top_rated"), ShelfStyle.LANDSCAPE) },
            platform("apple", "Sur Apple TV+", 350, "tv"),
            async { Shelf("upcoming", "Bientôt au cinéma", tmdb.list(k, "movie", "upcoming")) },
        ).map { it.await() }.filter { it.items.isNotEmpty() }
    }

    /** Grille Films ou Séries : découverte TMDB par genre et tri (20 œuvres par page). */
    suspend fun browse(kind: String, genre: Int?, sort: BrowseSort, page: Int): List<TmdbItem> {
        val params = buildMap {
            put("sort_by", sort.paramFor(kind))
            put("vote_count.gte", if (sort == BrowseSort.TOP) "800" else "80")
            genre?.let { put("with_genres", it.toString()) }
            if (sort == BrowseSort.RECENT) {
                put(if (kind == "movie") "release_date.lte" else "first_air_date.lte", java.time.LocalDate.now().toString())
            }
        }
        return tmdb.discover(key(), kind, params, page).filter { it.poster != null }
    }

    suspend fun details(kind: String, id: Int): TmdbInfo? = tmdb.details(key(), kind, id, full = true)

    suspend fun season(tvId: Int, season: Int): List<TmdbEpisode> = tmdb.season(key(), tvId, season)

    suspend fun similar(kind: String, id: Int): List<TmdbItem> = tmdb.recommendations(key(), kind, id).filter { it.poster != null }

    suspend fun search(query: String): List<TmdbItem> = tmdb.searchMulti(key(), query).filter { it.poster != null || it.backdrop != null }

    suspend fun imdbId(kind: String, id: Int): String? = tmdb.imdbId(key(), kind, id)

    companion object {
        val movieGenres = listOf(
            Genre(28, "Action"), Genre(12, "Aventure"), Genre(16, "Animation"), Genre(35, "Comédie"), Genre(80, "Crime"),
            Genre(99, "Documentaire"), Genre(18, "Drame"), Genre(10751, "Famille"), Genre(14, "Fantastique"), Genre(36, "Histoire"),
            Genre(27, "Horreur"), Genre(9648, "Mystère"), Genre(10749, "Romance"), Genre(878, "Science-Fiction"), Genre(53, "Thriller"),
            Genre(10752, "Guerre"), Genre(37, "Western"),
        )
        val tvGenres = listOf(
            Genre(10759, "Action & Aventure"), Genre(16, "Animation"), Genre(35, "Comédie"), Genre(80, "Crime"), Genre(99, "Documentaire"),
            Genre(18, "Drame"), Genre(10751, "Famille"), Genre(10762, "Enfants"), Genre(9648, "Mystère"),
            Genre(10765, "Science-Fiction & Fantastique"), Genre(10768, "Guerre & Politique"), Genre(37, "Western"),
        )
        private val allGenres = (movieGenres + tvGenres).associate { it.id to it.name }
        fun genreName(id: Int): String? = allGenres[id]
    }
}

enum class BrowseSort(val label: String, val param: String) {
    POPULAR("Populaires", "popularity.desc"),
    TOP("Mieux notés", "vote_average.desc"),
    RECENT("Récents", "primary_release_date.desc"),
    ;

    /** TMDB : date de sortie des films, date de première diffusion des séries. */
    fun paramFor(kind: String) = if (this == RECENT && kind == "tv") "first_air_date.desc" else param
}
