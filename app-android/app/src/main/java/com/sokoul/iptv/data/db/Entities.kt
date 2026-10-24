package com.sokoul.iptv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlaylistType { XTREAM, M3U }

enum class ContentType { LIVE, MOVIE, SERIES }

enum class RecordingStatus { SCHEDULED, RECORDING, COMPLETED, FAILED, CANCELLED }

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    /** Serveur Xtream normalisé ou URL de la playlist M3U (ou `file://`). */
    val url: String,
    val username: String? = null,
    /** Mot de passe chiffré via Android Keystore (voir SecretBox). */
    val passwordEnc: String? = null,
    /** EPG XMLTV saisi par l'utilisateur ; prioritaire sur celui du fournisseur. */
    val epgUrl: String? = null,
    /** EPG annoncé par l'en-tête M3U (`url-tvg`). */
    val providerEpgUrl: String? = null,
    val userAgent: String? = null,
    val serverTimezone: String? = null,
    val expiresAt: Long? = null,
    val maxConnections: Int? = null,
    val lastSync: Long = 0,
    val lastEpgSync: Long = 0,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories", primaryKeys = ["playlistId", "type", "categoryId"])
data class CategoryEntity(
    val playlistId: Long,
    val type: ContentType,
    val categoryId: String,
    val name: String,
    val position: Int,
    val gen: Long,
)

/** Préférences utilisateur par groupe : conservées entre deux synchronisations. */
@Entity(tableName = "category_prefs", primaryKeys = ["playlistId", "type", "categoryId"])
data class CategoryPrefEntity(
    val playlistId: Long,
    val type: ContentType,
    val categoryId: String,
    val hidden: Boolean = false,
    val locked: Boolean = false,
)

@Entity(
    tableName = "channels",
    primaryKeys = ["playlistId", "id"],
    indices = [
        Index("playlistId", "categoryId", "position"),
        Index("playlistId", "epgKey"),
        Index("playlistId", "number"),
    ],
)
data class ChannelEntity(
    val playlistId: Long,
    val id: String,
    val number: Int,
    val name: String,
    val searchName: String,
    val logo: String?,
    val categoryId: String,
    /** Identifiant EPG d'origine (tvg-id / epg_channel_id), en minuscules. */
    val tvgId: String?,
    /** Identifiant EPG résolu (tvg-id ou rapprochement par nom), en minuscules. */
    val epgKey: String?,
    /** Nom normalisé pour le rapprochement EPG ("tf1"). */
    val nameKey: String,
    /** URL directe (M3U). Null pour Xtream : construite à la lecture. */
    val streamUrl: String?,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int,
    val userAgent: String?,
    val referrer: String?,
    val position: Int,
    val gen: Long,
)

@Entity(
    tableName = "movies",
    primaryKeys = ["playlistId", "id"],
    indices = [Index("playlistId", "categoryId", "position"), Index("playlistId", "added"), Index("playlistId", "groupKey")],
)
data class MovieEntity(
    val playlistId: Long,
    val id: String,
    val name: String,
    val title: String,
    val searchName: String,
    val year: Int?,
    val language: String?,
    val quality: String?,
    val poster: String?,
    val rating: Double?,
    val categoryId: String,
    val extension: String?,
    val streamUrl: String?,
    val added: Long,
    val tmdbId: String?,
    val position: Int,
    val gen: Long,
    /** Clé de regroupement des doublons (même œuvre) ; null = pas encore calculée. */
    val groupKey: String? = null,
)

@Entity(
    tableName = "series",
    primaryKeys = ["playlistId", "id"],
    indices = [Index("playlistId", "categoryId", "position"), Index("playlistId", "lastModified"), Index("playlistId", "groupKey")],
)
data class SeriesEntity(
    val playlistId: Long,
    val id: String,
    val name: String,
    val title: String,
    val searchName: String,
    val year: Int?,
    val poster: String?,
    val backdrop: String?,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val rating: Double?,
    val categoryId: String,
    val lastModified: Long,
    val tmdbId: String?,
    val position: Int,
    val gen: Long,
    /** Clé de regroupement des doublons (même œuvre) ; null = pas encore calculée. */
    val groupKey: String? = null,
)

@Entity(
    tableName = "episodes",
    primaryKeys = ["playlistId", "seriesId", "id"],
    indices = [Index("playlistId", "seriesId", "season", "episode")],
)
data class EpisodeEntity(
    val playlistId: Long,
    val seriesId: String,
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val plot: String?,
    val image: String?,
    val durationSecs: Int?,
    val extension: String?,
    val streamUrl: String?,
    val rating: Double?,
)

/** Détails Xtream d'une série, mis en cache pour éviter un appel à chaque ouverture. */
@Entity(tableName = "series_details", primaryKeys = ["playlistId", "seriesId"])
data class SeriesDetailsEntity(
    val playlistId: Long,
    val seriesId: String,
    val fetchedAt: Long,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val backdrop: String?,
    val trailer: String?,
)

@Entity(
    tableName = "programs",
    primaryKeys = ["playlistId", "channelKey", "start"],
    indices = [Index("playlistId", "end")],
)
data class ProgramEntity(
    val playlistId: Long,
    val channelKey: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val episode: String?,
    val icon: String?,
    val gen: Long,
)

@Entity(tableName = "epg_channels", primaryKeys = ["playlistId", "id", "nameKey"], indices = [Index("playlistId", "nameKey")])
data class EpgChannelEntity(
    val playlistId: Long,
    val id: String,
    val nameKey: String,
    val icon: String?,
)

@Entity(tableName = "favorites", primaryKeys = ["playlistId", "type", "itemId"])
data class FavoriteEntity(
    val playlistId: Long,
    val type: ContentType,
    val itemId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Reprise de lecture (films, épisodes) et historique des chaînes (LIVE).
 * Pour un épisode : itemId = id d'épisode, parentId = id de la série.
 */
@Entity(
    tableName = "progress",
    primaryKeys = ["playlistId", "type", "itemId"],
    indices = [Index("playlistId", "type", "updatedAt"), Index("playlistId", "parentId")],
)
data class ProgressEntity(
    val playlistId: Long,
    val type: ContentType,
    val itemId: String,
    val parentId: String?,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val finished: Boolean,
)

@Entity(tableName = "recordings", indices = [Index("status"), Index("startAt")])
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val channelId: String,
    val channelName: String,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val status: RecordingStatus,
    val filePath: String? = null,
    val sizeBytes: Long = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reminders", primaryKeys = ["playlistId", "channelId", "start"])
data class ReminderEntity(
    val playlistId: Long,
    val channelId: String,
    val channelName: String,
    val start: Long,
    val title: String,
)

@Entity(tableName = "tmdb_cache")
data class TmdbCacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Long,
)

/**
 * Identifiant TMDB trouvé par l'app (ouverture d'une fiche) pour un titre que le fournisseur n'a pas identifié.
 * Conservé à part : les lignes du fournisseur ne sont jamais réécrites, et le lien survit aux synchros.
 */
@Entity(tableName = "tmdb_links", primaryKeys = ["playlistId", "type", "itemId"])
data class TmdbLinkEntity(
    val playlistId: Long,
    val type: ContentType,
    val itemId: String,
    val tmdbId: String,
)

/**
 * Métadonnées TMDB en français d'une œuvre (affiche, fond, logo, accroche, saga…), utilisées par les
 * rangées éditoriales et les fiches. Partagées entre playlists (clé = type + identifiant TMDB).
 */
@Entity(tableName = "tmdb_meta", primaryKeys = ["type", "tmdbId"], indices = [Index("collectionId")])
data class TmdbMetaEntity(
    val type: ContentType,
    val tmdbId: Int,
    val title: String,
    val originalTitle: String?,
    /** Résumé français ; à défaut, résumé anglais (overviewLang = "en"). */
    val overview: String?,
    val overviewLang: String?,
    val tagline: String?,
    /** Genres séparés par des virgules. */
    val genres: String?,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val rating: Double?,
    val year: Int?,
    val runtimeMin: Int?,
    val seasons: Int?,
    val collectionId: Int?,
    val collectionName: String?,
    val certification: String?,
    val fetchedAt: Long,
)

/**
 * Correspondance œuvre du catalogue (clé de regroupement) → identifiant TMDB.
 * tmdbId null = recherche faite sans résultat sûr (pas de nouvelle tentative avant `checkedAt` + 30 j).
 */
@Entity(tableName = "tmdb_match", primaryKeys = ["playlistId", "type", "workKey"], indices = [Index("type", "tmdbId")])
data class TmdbMatchEntity(
    val playlistId: Long,
    val type: ContentType,
    val workKey: String,
    val tmdbId: Int?,
    val checkedAt: Long,
)

/** Version choisie manuellement pour une œuvre (lancée en premier la fois suivante). */
@Entity(tableName = "vod_choice", primaryKeys = ["playlistId", "type", "groupKey"])
data class VodChoiceEntity(
    val playlistId: Long,
    val type: ContentType,
    val groupKey: String,
    val itemId: String,
)
