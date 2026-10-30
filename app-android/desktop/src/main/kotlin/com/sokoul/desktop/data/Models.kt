package com.sokoul.desktop.data

/** Playlist IPTV (même modèle que l'app Android : Xtream Codes ou M3U). */
data class Playlist(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val url: String,
    val username: String?,
    val password: String?,
    val epgUrl: String?,
    val userAgent: String?,
    val syncedAt: Long = 0,
)

enum class PlaylistType { XTREAM, M3U }

data class Category(val playlistId: Long, val id: String, val name: String, val position: Int, val hidden: Boolean = false, val locked: Boolean = false)

data class Channel(
    val playlistId: Long,
    val id: String,
    val number: Int,
    val name: String,
    val logo: String?,
    val categoryId: String,
    /** Identifiant du guide (tvg-id / epg_channel_id), en minuscules. */
    val epgKey: String?,
    /** Nom normalisé pour rapprocher la chaîne du guide quand l'identifiant manque. */
    val nameKey: String,
    /** Lien direct (M3U) ; null pour Xtream (construit à la lecture). */
    val url: String?,
    val userAgent: String?,
    val referrer: String?,
    val position: Int,
)

data class Program(val channelKey: String, val start: Long, val end: Long, val title: String, val description: String?)

data class Movie(
    val playlistId: Long,
    val id: String,
    val name: String,
    val year: Int?,
    val poster: String?,
    val rating: Double?,
    val categoryId: String?,
    val extension: String?,
    val added: Long?,
    val tmdbId: String?,
    val streamUrl: String? = null,
)

data class Series(
    val playlistId: Long,
    val id: String,
    val name: String,
    val year: Int?,
    val poster: String?,
    val backdrop: String?,
    val plot: String?,
    val rating: Double?,
    val categoryId: String?,
    val lastModified: Long?,
    val tmdbId: String?,
)

/** Groupes spéciaux en tête de la liste (comme sur la tablette). */
object SpecialGroup {
    const val ALL = "__all"
    const val FAVORITES = "__fav"
}
