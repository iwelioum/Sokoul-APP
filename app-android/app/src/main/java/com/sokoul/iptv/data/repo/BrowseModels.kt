package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.db.TmdbMetaEntity
import com.sokoul.iptv.data.net.TmdbInfo

enum class BrowseKind { HOME, MOVIES, SERIES }

enum class RowStyle { POSTER, LANDSCAPE, TOP10, SAGAS, PLATFORMS, CONTINUE, CHANNELS, CATEGORIES }

/**
 * Une œuvre affichable (film ou série), toutes versions confondues : la version [id] ouvre la fiche.
 * Les champs TMDB (fond, logo, accroche…) sont ajoutés par [withMeta] quand ils sont connus.
 */
data class Work(
    val type: ContentType,
    val id: String,
    val key: String,
    val title: String,
    val year: Int?,
    val poster: String?,
    val backdrop: String? = null,
    /** Titre tel que publié par le fournisseur (recherche TMDB). */
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
    /** Reprise : progression 0..1 et entrée d'historique. */
    val progress: Float? = null,
    val resume: ProgressEntity? = null,
    val subtitle: String? = null,
) {
    /** Clé unique dans une liste mélangeant films et séries. */
    val uid get() = "${type.name}|$key"
}

data class SagaCard(val id: Int, val name: String, val backdrop: String?, val poster: String?, val count: Int)

data class PlatformTile(val platform: Platform, val type: ContentType?, val titles: Int)

data class ChannelTile(val channel: ChannelEntity, val now: ProgramEntity?)

data class BrowseRow(
    val id: String,
    val title: String,
    val tagline: String? = null,
    val style: RowStyle = RowStyle.POSTER,
    val works: List<Work> = emptyList(),
    val sagas: List<SagaCard> = emptyList(),
    val platforms: List<PlatformTile> = emptyList(),
    val channels: List<ChannelTile> = emptyList(),
    /** Catégories du fournisseur (identifiant → libellé lisible). */
    val chips: List<Pair<String, String>> = emptyList(),
    /** Plateforme mise en avant dans l'en-tête de la rangée (pastille de couleur). */
    val platform: Platform? = null,
    /** Grille « Tout voir » (voir [BrowseRepository.grid]). */
    val seeAll: String? = null,
) {
    val isEmpty get() = works.isEmpty() && sagas.isEmpty() && platforms.isEmpty() && channels.isEmpty() && chips.isEmpty()
}

data class BrowsePage(val hero: List<Work>, val rows: List<BrowseRow>)

/** En-tête d'une grille « Tout voir » (saga, plateforme). */
data class GridHeader(val title: String, val subtitle: String? = null, val overview: String? = null, val backdrop: String? = null, val platform: Platform? = null)

fun MovieEntity.toWork(versions: Int = 1, platform: Platform? = null) = Work(
    type = ContentType.MOVIE, id = id, key = keyOf(), title = displayTitle(title, versions, year), year = year, poster = poster,
    providerTitle = title, rating = rating?.takeIf { it > 0 }, versions = versions,
    quality = if (versions > 1) null else quality, platform = platform,
)

fun SeriesEntity.toWork(versions: Int = 1, platform: Platform? = null) = Work(
    type = ContentType.SERIES, id = id, key = keyOf(), title = displayTitle(title, versions, year), year = year, poster = poster,
    backdrop = backdrop, providerTitle = title, overview = plot?.takeIf { it.isNotBlank() },
    genres = genre?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    rating = rating?.takeIf { it > 0 }, versions = versions, platform = platform,
)

@Suppress("UNUSED_PARAMETER")
private fun displayTitle(title: String, versions: Int, year: Int? = null) = cleanDisplayTitle(title, year)

// accolades et crochets échappés : le moteur ICU d'Android les refuse nus (contrairement au JVM)
private val BRACKETS = Regex("""\[[^\]]*\]|\{[^\}]*\}""")
private val PAREN_TAG = Regex("""\((?:\d{4}|[A-Z0-9 .+-]{2,12})\)""")
private val TRAILING_YEAR = Regex("""\s+[-–|:]\s*(?:19|20)\d{2}\s*$""")
private val TRAILING_TAGS = Regex(
    """(?:\s*[-–|]?\s*\b(?:MULTI|VOSTFR|VOST|VF|VFF|VFQ|TRUEFRENCH|FRENCH|SUB|SUBS|DUBBED|4K|UHD|FHD|HD|SD|HDR|1080P|720P|HEVC|X265|H265)\b)+\s*$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Titre affiché quand TMDB n'en fournit pas : sans préfixe pays/langue, balises entre crochets,
 * année ni mentions techniques en fin de titre. « FR - The Musical - 2026 [ -SUB] » → « The Musical ».
 */
fun cleanDisplayTitle(raw: String, year: Int? = null): String {
    var s = VodGrouping.stripCountry(raw.trim())
    s = s.replace(BRACKETS, " ").replace(PAREN_TAG, " ")
    repeat(2) {
        s = s.replace(TRAILING_TAGS, "").replace(TRAILING_YEAR, "")
        // année nue en fin de titre : retirée seulement si c'est l'année de sortie (« Blade Runner 2049 » reste intact)
        if (year != null) s = s.trimEnd().removeSuffix(" $year")
    }
    s = s.replace(Regex("""\s{2,}"""), " ").trim(' ', '-', '–', '|', ':', '.', '_')
    return s.ifEmpty { raw.trim() }
}

/** Applique les métadonnées TMDB (titre français, affiche, fond, logo, accroche…). */
fun Work.withMeta(m: TmdbMetaEntity?): Work {
    if (m == null) return this
    return copy(
        title = m.title.ifBlank { title },
        poster = m.poster ?: poster,
        backdrop = m.backdrop ?: backdrop,
        logo = m.logo ?: logo,
        // résumé français TMDB d'abord ; un résumé anglais TMDB ne remplace pas celui du fournisseur
        overview = if (m.overviewLang == "fr") m.overview else (overview ?: m.overview),
        tagline = m.tagline ?: tagline,
        genres = m.genres?.split(", ")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: genres,
        rating = m.rating ?: rating,
        year = year ?: m.year,
        tmdbId = m.tmdbId,
        certification = m.certification ?: certification,
        runtimeMin = m.runtimeMin ?: runtimeMin,
        seasons = m.seasons ?: seasons,
    )
}

fun TmdbInfo.toMeta(type: ContentType, now: Long = System.currentTimeMillis()) = TmdbMetaEntity(
    type = type, tmdbId = id, title = title, originalTitle = originalTitle,
    overview = overview ?: overviewEn, overviewLang = when { overview != null -> "fr"; overviewEn != null -> "en"; else -> null },
    tagline = tagline, genres = genres.joinToString(", ").ifEmpty { null },
    poster = poster?.replace("/w500/", "/w342/"), backdrop = backdrop, logo = logo, rating = rating, year = year,
    runtimeMin = runtimeMin, seasons = seasons, collectionId = collectionId, collectionName = collectionName,
    certification = certification, fetchedAt = now,
)

/** Identifiant TMDB porté par une clé de regroupement « tmdb:123 ». */
fun tmdbIdOfKey(key: String): Int? = if (key.startsWith("tmdb:")) key.removePrefix("tmdb:").toIntOrNull() else null

/** Change la taille d'une image TMDB (« w1280 » → « w780 ») ; les autres URL sont rendues telles quelles. */
fun tmdbSized(url: String?, size: String): String? =
    url?.let { if (it.contains("image.tmdb.org/t/p/")) it.replace(Regex("/t/p/[a-z0-9]+/"), "/t/p/$size/") else it }

/** « [FR] FILMS 4K » → « Films 4K » ; « [MULTI-LANG] NETFLIX » → « Netflix · Multi ». */
fun prettyCategory(name: String): String {
    val base = stripLang(name).trim().trim('|', '-', ':', ' ').ifEmpty { name.trim() }
    val words = base.lowercase().split(Regex("\\s+")).joinToString(" ") { w ->
        if (w.length <= 3 && w.any(Char::isDigit) || w in setOf("4k", "uhd", "hd", "vf", "vostfr", "tv")) w.uppercase()
        else w.replaceFirstChar { it.titlecase() }
    }
    return when (val lang = categoryLang(name)) {
        null, "FR" -> words
        "MULTI" -> "$words · Multi"
        else -> "$words · $lang"
    }
}
private val FR_WORDS = setOf("le", "la", "les", "des", "une", "est", "et", "dans", "sur", "pour", "avec", "qui", "du", "son", "sa", "ses", "au", "aux", "mais", "elle", "il")
private val EN_WORDS = setOf("the", "and", "of", "is", "to", "with", "his", "her", "in", "a", "an", "when", "their", "who", "for", "on", "but", "she", "he")

/** Texte probablement en français (mots outils), pour ne pas afficher un résumé anglais du fournisseur. */
fun looksFrench(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }.take(80)
    val fr = words.count { it in FR_WORDS || it.startsWith("l'") || it.startsWith("d'") || it.startsWith("qu'") }
    val en = words.count { it in EN_WORDS }
    return fr > en
}

/** Résumé affichable sur les écrans de navigation : seulement en français. */
val Work.overviewFr: String? get() = overview?.takeIf { looksFrench(it) }