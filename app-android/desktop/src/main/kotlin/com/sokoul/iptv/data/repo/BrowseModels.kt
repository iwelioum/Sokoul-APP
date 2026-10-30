package com.sokoul.iptv.data.repo

import androidx.compose.runtime.Immutable
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem

// Modèles d'affichage de l'app tablette (`app/.../data/repo/BrowseModels.kt`), repris pour l'app PC : les composants
// copiés de la tablette (cartes, héros, fiche) les lisent tels quels. Ici sans Room : alimentés par TMDB et l'IPTV.

enum class BrowseKind { HOME, MOVIES, SERIES }

enum class RowStyle { POSTER, LANDSCAPE, TOP10, SAGAS, PLATFORMS, CONTINUE, CHANNELS, CATEGORIES, MOODS }

/** Une œuvre affichable (film ou série). [id] = identifiant TMDB (« 27205 ») sur PC. */
@Immutable
data class Work(
    val type: ContentType,
    val id: String,
    val key: String,
    val title: String,
    val year: Int?,
    val poster: String?,
    val backdrop: String? = null,
    val providerTitle: String = title,
    val logo: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val versions: Int = 1,
    val quality: String? = null,
    val platform: Platform? = null,
    val tmdbId: Int? = null,
    val certification: String? = null,
    val runtimeMin: Int? = null,
    val seasons: Int? = null,
    /** Reprise : progression 0..1 et position enregistrée. */
    val progress: Float? = null,
    val resume: com.sokoul.desktop.data.Resume? = null,
    val subtitle: String? = null,
    val available: Boolean = true,
) {
    val uid get() = "${type.name}|$key"
}

@Immutable
data class SagaCard(
    val id: Int, val name: String, val backdrop: String?, val poster: String?, val count: Int, val curated: Boolean = false,
    val facts: SagaFacts? = null,
    val seen: Int = 0,
    val minis: List<String> = emptyList(),
    val logo: String? = null,
    val logoOf: Int? = null,
    val logoEn: Boolean = false,
    val overview: String? = null,
)

@Immutable
data class SagaFacts(val films: Int, val from: Int?, val to: Int?, val rating: Double?) {
    fun line(): String = listOfNotNull(
        if (films > 1) "$films films" else "1 film",
        if (from != null && to != null && to > from) "$from → $to" else from?.toString(),
        rating?.let { "★ " + String.format(java.util.Locale.FRANCE, "%.1f", it) + " de moyenne" },
    ).joinToString(" · ")
}

@Immutable
data class PlatformTile(val platform: Platform, val type: ContentType?, val titles: Int)

@Immutable
data class BrowseRow(
    val id: String,
    val title: String,
    val tagline: String? = null,
    val style: RowStyle = RowStyle.POSTER,
    val works: List<Work> = emptyList(),
    val sagas: List<SagaCard> = emptyList(),
    val platforms: List<PlatformTile> = emptyList(),
    val platform: Platform? = null,
    val seeAll: String? = null,
    val emphasis: String? = null,
) {
    val isEmpty get() = works.isEmpty() && sagas.isEmpty() && platforms.isEmpty()
}

/** Œuvre TMDB → carte (genres en clair, type selon `media_type`). */
fun TmdbItem.toWork(kind: String = mediaType ?: "movie", genreName: (Int) -> String? = { null }): Work {
    val type = if (kind == "tv") ContentType.SERIES else ContentType.MOVIE
    return Work(
        type, id.toString(), "tmdb:$id", title, year, poster, backdrop, providerTitle = title, overview = overview,
        genres = genreIds.mapNotNull(genreName), rating = rating, tmdbId = id,
    )
}

/** Fiche TMDB complète → carte enrichie (logo, accroche, durée, classification). */
fun TmdbInfo.toWork(kind: String): Work = Work(
    if (kind == "tv") ContentType.SERIES else ContentType.MOVIE, id.toString(), "tmdb:$id", title, year, poster, backdrop,
    logo = logo, overview = overview ?: overviewEn, tagline = tagline, genres = genres, rating = rating, tmdbId = id,
    certification = certification, runtimeMin = runtimeMin, seasons = seasons,
)

/** Change la taille d'une image TMDB (« w1280 » → « w780 ») ; les autres URL sont rendues telles quelles. */
fun tmdbSized(url: String?, size: String): String? =
    url?.let { if (it.contains("image.tmdb.org/t/p/")) it.replace(Regex("/t/p/[a-z0-9]+/"), "/t/p/$size/") else it }

private val FR_WORDS = setOf("le", "la", "les", "des", "une", "est", "et", "dans", "sur", "pour", "avec", "qui", "du", "son", "sa", "ses", "au", "aux", "mais", "elle", "il")
private val EN_WORDS = setOf("the", "and", "of", "is", "to", "with", "his", "her", "in", "a", "an", "when", "their", "who", "for", "on", "but", "she", "he")

fun looksFrench(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }.take(80)
    val fr = words.count { it in FR_WORDS || it.startsWith("l'") || it.startsWith("d'") || it.startsWith("qu'") }
    val en = words.count { it in EN_WORDS }
    return fr > en
}

val Work.overviewFr: String? get() = overview?.takeIf { looksFrench(it) }
