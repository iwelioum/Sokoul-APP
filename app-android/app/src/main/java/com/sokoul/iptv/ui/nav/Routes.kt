package com.sokoul.iptv.ui.nav

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object LiveRoute
@Serializable object GuideRoute
@Serializable object MoviesRoute
@Serializable object SeriesRoute
@Serializable object SearchRoute
@Serializable object RecordingsRoute
@Serializable object SettingsRoute
@Serializable object MultiviewRoute

@Serializable data class PlaylistEditRoute(val id: Long = 0, val firstRun: Boolean = false)
@Serializable data class SyncRoute(val playlistId: Long)
@Serializable data class MovieDetailRoute(val id: String)
@Serializable data class SeriesDetailRoute(val id: String)

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
)

object PlayKind {
    const val LIVE = "live"
    const val CATCHUP = "catchup"
    const val MOVIE = "movie"
    const val EPISODE = "episode"
    const val RECORDING = "recording"
}
