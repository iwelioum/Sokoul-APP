package com.sokoul.iptv.ui.nav

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object LiveRoute
@Serializable object GuideRoute
@Serializable object MoviesRoute
@Serializable object SeriesRoute
@Serializable object MyListRoute
/** Hub des sagas (onglet de l'univers Streaming). */
@Serializable object SagasRoute
/** Page « musée » d'une saga (identifiant de collection TMDB). */
@Serializable data class SagaRoute(val id: Int)
/** Univers TV : ouvre la dernière chaîne en plein écran (écran de passage, hors historique). */
@Serializable object TvRoute
/** Recherche ; [origin] (SearchOrigin) fixe le périmètre proposé au départ. */
@Serializable data class SearchRoute(val origin: String = "all")
@Serializable object RecordingsRoute
@Serializable object SettingsRoute
@Serializable object MultiviewRoute

@Serializable data class PlaylistEditRoute(val id: Long = 0, val firstRun: Boolean = false)
@Serializable data class SyncRoute(val playlistId: Long)
@Serializable data class MovieDetailRoute(val id: String)
@Serializable data class SeriesDetailRoute(val id: String)

/** Grille « Tout voir » d'une rangée (spec = GridSpec encodée). */
@Serializable data class BrowseGridRoute(val title: String, val spec: String)

/** Explorer : tout le catalogue d'un type (MOVIE | SERIES), filtré par les fiches TMDB. */
@Serializable data class ExploreRoute(val type: String)

/** Ancienne navigation par catégories du fournisseur (type = MOVIE | SERIES). */
@Serializable data class AllCategoriesRoute(val type: String)

/**
 * Lecture. kind : live | catchup | movie | episode | recording.
 * - live : id = chaîne, categoryId = groupe pour le zapping
 * - catchup : id = chaîne, start/end = programme
 * - episode : id = épisode, parentId = série
 * - recording : id = identifiant d'enregistrement
 */
@Serializable
data class PlayerRoute(
    val kind: String,
    val playlistId: Long,
    val id: String,
    val parentId: String? = null,
    val categoryId: String? = null,
    val start: Long = 0,
    val end: Long = 0,
    /** Film / épisode : ignorer la reprise et lire depuis le début. */
    val fromStart: Boolean = false,
)

object PlayKind {
    const val LIVE = "live"
    const val CATCHUP = "catchup"
    const val MOVIE = "movie"
    const val EPISODE = "episode"
    const val RECORDING = "recording"
}
