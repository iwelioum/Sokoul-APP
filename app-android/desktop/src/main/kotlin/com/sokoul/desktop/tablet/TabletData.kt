package com.sokoul.desktop.tablet

import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.Catalog
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.repo.BrowseKind
import com.sokoul.iptv.data.repo.BrowseRow
import com.sokoul.iptv.data.repo.Platform
import com.sokoul.iptv.data.repo.PlatformTile
import com.sokoul.iptv.data.repo.RowStyle
import com.sokoul.iptv.data.repo.SagaCard
import com.sokoul.iptv.data.repo.SagaFacts
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.parser.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** Page de navigation (accueil, films, séries) : héros et rangées, au format de l'app tablette. */
data class BrowsePage(val hero: List<Work>, val rows: List<BrowseRow>, val ranked: Boolean = true)

/**
 * Données des écrans « tablette » du PC. Sur la tablette, l'accueil vient du catalogue IPTV enrichi par TMDB
 * (`BrowseRepository`) ; sur PC, les mêmes rangées sont construites depuis TMDB (et la reprise locale).
 */
class TabletData(private val app: AppState) {
    private val cat get() = app.catalog
    private val pages = ConcurrentHashMap<BrowseKind, BrowsePage>()

    fun cached(kind: BrowseKind) = pages[kind]

    suspend fun page(kind: BrowseKind): BrowsePage = coroutineScope {
        val k = cat.key()
        val t = cat.tmdb
        fun works(items: List<TmdbItem>, kind: String? = null) = items.filter { it.poster != null }.map { it.toWork(kind ?: it.mediaType ?: "movie", Catalog::genreName) }
        fun discover(kind: String, params: Map<String, String>) = async { works(t.discover(k, kind, params), kind) }
        fun platform(p: Platform, provider: Int, kind: String) = async {
            BrowseRow(
                "plat-${p.name.lowercase()}-$kind", if (kind == "tv") "Séries ${p.label}" else "Films ${p.label}",
                p.tagline, works = works(t.discover(k, kind, mapOf("with_watch_providers" to "$provider", "watch_region" to "FR", "sort_by" to "popularity.desc")), kind),
                platform = p,
            )
        }
        val resume = withContext(Dispatchers.IO) { app.db.resumeList(20) }
        val continueRow = BrowseRow(
            "continue", "Reprendre la lecture", style = RowStyle.CONTINUE,
            works = resume.filter { kind == BrowseKind.HOME || (kind == BrowseKind.MOVIES) == (it.kind == "movie") }.map { r ->
                Work(
                    if (r.kind == "tv") ContentType.SERIES else ContentType.MOVIE, r.tmdbId.toString(), r.key, r.title, null, null, r.image,
                    tmdbId = r.tmdbId, progress = r.fraction, resume = r,
                    subtitle = listOfNotNull(r.season?.let { "S${"%02d".format(it)}E${"%02d".format(r.episode ?: 1)}" }, "reste ${remaining(r.durationMs - r.positionMs)}").joinToString(" · "),
                )
            },
        )
        val last = resume.firstOrNull()
        val because = last?.let { r -> async { r to works(t.recommendations(k, r.kind, r.tmdbId), r.kind) } }
        val sagas = if (kind == BrowseKind.SERIES) null else async { sagas() }
        val plats = listOf(Platform.NETFLIX to 8, Platform.PRIME to 119, Platform.DISNEY to 337, Platform.APPLE to 350, Platform.MAX to 1899, Platform.CANAL to 381, Platform.PARAMOUNT to 531)

        val heroItems = when (kind) {
            BrowseKind.HOME -> async { t.trending(k, "movie").take(5) + t.trending(k, "tv").take(3) }
            BrowseKind.MOVIES -> async { t.trending(k, "movie").take(8) }
            BrowseKind.SERIES -> async { t.trending(k, "tv").take(8) }
        }
        val rowsAsync: List<kotlinx.coroutines.Deferred<BrowseRow?>> = when (kind) {
            BrowseKind.HOME -> listOf(
                async { BrowseRow("top10-movie", "Top 10 des films cette semaine", style = RowStyle.TOP10, works = works(t.trending(k, "movie"), "movie").take(10)) },
                async { because?.await()?.let { (r, w) -> BrowseRow("byw-${r.tmdbId}", "Parce que vous avez regardé ${r.title}", "Dans la même veine, rien que pour vous.", works = w, emphasis = r.title) } },
                async { BrowseRow("popular-tv", "Les séries dont tout le monde parle", style = RowStyle.LANDSCAPE, works = works(t.trending(k, "tv"), "tv")) },
                async { BrowseRow("platforms", "Par plateforme", style = RowStyle.PLATFORMS, platforms = plats.map { PlatformTile(it.first, null, 0) }) },
                platform(Platform.NETFLIX, 8, "tv"),
                async { BrowseRow("new", "Tout juste sortis", "Les nouveautés du cinéma, à peine arrivées.", works = works(t.list(k, "movie", "now_playing"), "movie")) },
                async { sagas?.await()?.let { BrowseRow("sagas", "Les grandes sagas", "Une histoire, plusieurs films : à voir dans l'ordre.", style = RowStyle.SAGAS, sagas = it) } },
                platform(Platform.DISNEY, 337, "movie"),
                async { BrowseRow("toprated", "Les incontournables", "Les films les mieux notés de tous les temps.", works = works(t.list(k, "movie", "top_rated"), "movie")) },
                platform(Platform.PRIME, 119, "movie"),
                async { BrowseRow("spec-genre-16", "Votre dose d'animation", works = discover("movie", mapOf("with_genres" to "16", "sort_by" to "popularity.desc")).await()) },
                platform(Platform.APPLE, 350, "tv"),
            )
            BrowseKind.MOVIES -> listOf(
                async { BrowseRow("top10-movie", "Top 10 des films cette semaine", style = RowStyle.TOP10, works = works(t.trending(k, "movie"), "movie").take(10)) },
                async { BrowseRow("popular-movie", "Films populaires", works = works(t.list(k, "movie", "popular"), "movie")) },
                async { BrowseRow("new", "Tout juste sortis", "Les nouveautés du cinéma, à peine arrivées.", works = works(t.list(k, "movie", "now_playing"), "movie")) },
                async { sagas?.await()?.let { BrowseRow("sagas", "Les grandes sagas", "Une histoire, plusieurs films : à voir dans l'ordre.", style = RowStyle.SAGAS, sagas = it) } },
                async { BrowseRow("platforms", "Par plateforme", style = RowStyle.PLATFORMS, platforms = plats.map { PlatformTile(it.first, ContentType.MOVIE, 0) }) },
                async { BrowseRow("spec-genre-28", "Adrénaline", "Explosions, poursuites et héros sans peur.", works = discover("movie", mapOf("with_genres" to "28", "sort_by" to "popularity.desc")).await()) },
                async { BrowseRow("spec-genre-35", "Fous rires", "De quoi rire jusqu'au générique.", works = discover("movie", mapOf("with_genres" to "35", "sort_by" to "popularity.desc")).await()) },
                async { BrowseRow("spec-genre-878", "Autres mondes", "Science-fiction et fantastique, pour s'évader.", works = discover("movie", mapOf("with_genres" to "878", "sort_by" to "popularity.desc")).await()) },
                async { BrowseRow("spec-genre-27", "Frissons garantis", "À regarder les lumières allumées.", works = discover("movie", mapOf("with_genres" to "27", "sort_by" to "popularity.desc")).await()) },
                async { BrowseRow("spec-genre-10751", "En famille", "Pour petits et grands, bien installés sur le canapé.", works = discover("movie", mapOf("with_genres" to "10751", "sort_by" to "popularity.desc")).await()) },
                async { BrowseRow("toprated", "Les incontournables", "Les films les mieux notés de tous les temps.", works = works(t.list(k, "movie", "top_rated"), "movie")) },
                platform(Platform.NETFLIX, 8, "movie"),
                platform(Platform.PRIME, 119, "movie"),
            )
            BrowseKind.SERIES -> listOf(
                async { BrowseRow("top10-tv", "Top 10 des séries cette semaine", style = RowStyle.TOP10, works = works(t.trending(k, "tv"), "tv").take(10)) },
                async { BrowseRow("popular-tv", "Les séries dont tout le monde parle", style = RowStyle.LANDSCAPE, works = works(t.list(k, "tv", "popular"), "tv")) },
                async { BrowseRow("platforms", "Par plateforme", style = RowStyle.PLATFORMS, platforms = plats.map { PlatformTile(it.first, ContentType.SERIES, 0) }) },
                platform(Platform.NETFLIX, 8, "tv"),
                platform(Platform.MAX, 1899, "tv"),
                async { BrowseRow("spec-genre-80", "Enquêtes & suspense", "Le coupable est peut-être sous vos yeux.", works = discover("tv", mapOf("with_genres" to "80", "sort_by" to "popularity.desc")).await()) },
                async { BrowseRow("spec-genre-10765", "Autres mondes", "Science-fiction et fantastique, pour s'évader.", works = discover("tv", mapOf("with_genres" to "10765", "sort_by" to "popularity.desc")).await()) },
                platform(Platform.APPLE, 350, "tv"),
                async { BrowseRow("spec-genre-16", "Animés & mangas", "L'animation japonaise dans toute sa splendeur.", works = discover("tv", mapOf("with_genres" to "16", "with_original_language" to "ja", "sort_by" to "popularity.desc")).await()) },
                async { BrowseRow("toprated", "Séries cultes", "Les séries les mieux notées.", works = works(t.list(k, "tv", "top_rated"), "tv")) },
                platform(Platform.PRIME, 119, "tv"),
                platform(Platform.DISNEY, 337, "tv"),
            )
        }
        val hero = heroItems.await().filter { it.backdrop != null }.map { item ->
            async { t.details(k, item.mediaType ?: if (kind == BrowseKind.SERIES) "tv" else "movie", item.id, full = false)?.toWork(item.mediaType ?: if (kind == BrowseKind.SERIES) "tv" else "movie") ?: item.toWork(genreName = Catalog::genreName) }
        }.map { it.await() }
        val rows = listOf(continueRow) + rowsAsync.mapNotNull { it.await() }
        BrowsePage(hero, rows.filter { !it.isEmpty }).also { pages[kind] = it }
    }

    /** Grandes sagas (collections TMDB) : fond, nombre de films, années et note moyenne. */
    private suspend fun sagas(): List<SagaCard> = coroutineScope {
        SAGAS.map { id -> async { cat.tmdb.collection(cat.key(), id) } }.mapNotNull { it.await() }.map { c ->
            val released = c.parts.filter { it.year != null && it.year!! <= java.time.Year.now().value }
            SagaCard(
                c.id, c.name, c.backdrop, c.poster, c.parts.size,
                facts = SagaFacts(released.size, released.mapNotNull { it.year }.minOrNull(), released.mapNotNull { it.year }.maxOrNull(), released.mapNotNull { it.rating }.average().takeIf { !it.isNaN() }),
                minis = c.parts.mapNotNull { it.poster }.take(3).map { it.replace("/w342/", "/w154/") }, overview = c.overview,
            )
        }
    }

    // --- notes OMDb (IMDb, Rotten Tomatoes, Metacritic), comme la fiche de la tablette -----------------------

    private val ratings = ConcurrentHashMap<String, List<Pair<String, String>>>()

    suspend fun externalRatings(imdbId: String?): List<Pair<String, String>> {
        val id = imdbId ?: return emptyList()
        ratings[id]?.let { return it }
        val key = com.sokoul.desktop.data.ProviderConfig.key(app.db, "omdb") ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                cat.http.newCall(Request.Builder().url("https://www.omdbapi.com/?apikey=$key&i=$id").build()).execute().use { r ->
                    val o = Json.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject ?: return@use emptyList()
                    val list = (o["Ratings"] as? JsonArray).orEmpty().mapNotNull { e ->
                        val x = e as? JsonObject ?: return@mapNotNull null
                        val v = x.str("Value") ?: return@mapNotNull null
                        when (x.str("Source")) {
                            "Internet Movie Database" -> "IMDb" to v.substringBefore("/").replace('.', ',')
                            "Rotten Tomatoes" -> "RT" to v.replace("%", " %")
                            "Metacritic" -> "MC" to v.substringBefore("/")
                            else -> null
                        }
                    }
                    list
                }
            }.getOrDefault(emptyList()).also { ratings[id] = it }
        }
    }

    companion object {
        /** Sagas populaires (identifiants de collections TMDB). */
        val SAGAS = listOf(10, 1241, 119, 9485, 87359, 645, 86311, 263, 328, 2150, 10194, 8091, 131635, 748, 295)

        fun remaining(ms: Long): String {
            val m = (ms / 60_000).coerceAtLeast(1)
            return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"
        }
    }
}

/** Versions Real-Debrid rangées comme l'étagère de la tablette : par langue, une carte par définition et HDR. */
data class VersionGroup(val language: AudioLanguage?, val quality: VideoQuality?, val hdr: HdrFormat?, val streams: List<DebridStream>) {
    val best get() = streams.first()
    val title get() = listOfNotNull(
        quality?.let { com.sokoul.iptv.ui.components.SourceLabels.quality(it) },
        hdr?.let { com.sokoul.iptv.ui.components.SourceLabels.hdr(it) },
    ).joinToString(" · ").ifEmpty { "Version" }
    /** « WEB · 12,3 Go · prête » : origine du fichier, taille, état. */
    val note get() = listOfNotNull(
        best.release.source?.label,
        com.sokoul.iptv.ui.components.SourceLabels.sizeOrNull(best.release.sizeBytes),
        if (best.release.cached == true || best.inAccount) "prête" else null,
    ).joinToString(" · ").ifEmpty { null }

    companion object {
        private val ORDER = listOf(AudioLanguage.MULTI, AudioLanguage.VF, AudioLanguage.VOSTFR, AudioLanguage.VO)

        fun of(streams: List<DebridStream>): List<VersionGroup> =
            streams.groupBy { Triple(it.release.language, it.release.quality, it.release.hdr) }
                .map { (k, v) -> VersionGroup(k.first, k.second, k.third, v) }
                .sortedWith(compareBy<VersionGroup> { ORDER.indexOf(it.language).let { i -> if (i < 0) 9 else i } }.thenBy { it.quality?.ordinal ?: 9 }.thenBy { it.hdr?.ordinal ?: -1 })

        fun explain(l: AudioLanguage?): String = when (l) {
            AudioLanguage.VF -> "Doublage français"
            AudioLanguage.VOSTFR -> "VO + sous-titres français intégrés"
            AudioLanguage.VO -> "Version originale"
            AudioLanguage.MULTI -> "Plusieurs langues au choix dans le lecteur"
            null -> "Langue non précisée"
        }
    }
}
