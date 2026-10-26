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
/** Page d'un acteur ou d'un réalisateur (identifiant TMDB) ; [name] s'affiche pendant le chargement. */
@Serializable data class PersonRoute(val id: Int, val name: String = "")
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
 * - debrid : titre hors abonnement, lu via Real-Debrid (query, title) ; id = query
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
    /** Film / épisode : version Real-Debrid choisie sur la fiche (clé de `DebridStream`). */
    val source: String? = null,
    /** Œuvre pour Real-Debrid (`DebridQuery.encode()`) ; seule pour `debrid` (titre absent de l'abonnement). */
    val query: String? = null,
    /** Titre affiché pour `debrid` (pas de fiche IPTV). */
    val title: String? = null,
)

object PlayKind {
    const val LIVE = "live"
    const val CATCHUP = "catchup"
    const val MOVIE = "movie"
    const val EPISODE = "episode"
    const val RECORDING = "recording"
    const val DEBRID = "debrid"
}
