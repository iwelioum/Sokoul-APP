package com.sokoul.iptv.data.debrid

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.repo.VodGrouping
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.offCatalog

/** Rangée « Aussi disponible via Real-Debrid » de la recherche (règles pures, testées). */
object DebridSearch {
    const val MAX = 15
    /** Sous ce nombre de votes TMDB, le titre est trop obscur pour avoir des versions en cache. */
    const val MIN_VOTES = 10

    /**
     * Résultats TMDB absents de l'abonnement ([have] : même identifiant TMDB ou même titre),
     * avec une affiche, dans l'ordre de pertinence TMDB.
     */
    fun extras(found: List<TmdbItem>, have: List<Work>, movies: Boolean, series: Boolean): List<Work> {
        val ids = have.mapNotNullTo(HashSet()) { w -> w.tmdbId?.let { (if (w.type == ContentType.SERIES) "tv:" else "movie:") + it } }
        val titles = have.mapTo(HashSet()) { VodGrouping.normalizeTitle(it.title, null) }
        return found.asSequence()
            .filter { (it.mediaType == "movie" && movies) || (it.mediaType == "tv" && series) }
            .filter { it.poster != null && (it.voteCount ?: 0) >= MIN_VOTES }
            .filter { "${it.mediaType}:${it.id}" !in ids && VodGrouping.normalizeTitle(it.title, null) !in titles }
            .distinctBy { "${it.mediaType}:${it.id}" }
            .take(MAX)
            .map { it.offCatalog(if (it.mediaType == "tv") ContentType.SERIES else ContentType.MOVIE) }
            .toList()
    }
}
