package com.sokoul.iptv.data.repo

import android.content.Context
import android.net.Uri
import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.BufferProfile
import com.sokoul.iptv.data.DecoderMode
import com.sokoul.iptv.data.LiveFormat
import com.sokoul.iptv.data.SecretBox
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryPrefEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.FavoriteEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupFile(
    val app: String = "SokoulTV",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val playlists: List<PlaylistBackup>,
    val settings: SettingsBackup,
)

@Serializable
data class PlaylistBackup(
    val name: String,
    val type: String,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val favorites: List<FavoriteBackup> = emptyList(),
    val groups: List<GroupBackup> = emptyList(),
)

@Serializable data class FavoriteBackup(val type: String, val id: String, val addedAt: Long)
@Serializable data class GroupBackup(val type: String, val id: String, val hidden: Boolean, val locked: Boolean)

@Serializable
data class SettingsBackup(
    val bufferProfile: String, val liveFormat: String, val decoderMode: String, val userAgent: String,
    val audioLanguage: String, val subtitleLanguage: String, val epgOffsetMinutes: Int,
    val playlistRefreshHours: Int, val epgRefreshHours: Int, val parentalPin: String, val tmdbApiKey: String,
    val autoplayNextEpisode: Boolean,
)

/**
 * Sauvegarde / restauration en JSON : playlists (identifiants compris), favoris,
 * groupes masqués ou verrouillés et réglages. Le fichier contient les mots de passe en clair :
 * à conserver en lieu sûr.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val secrets: SecretBox,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val s = settings.current()
        val playlists = db.playlists().getAll().map { p ->
            PlaylistBackup(
                name = p.name, type = p.type.name, url = p.url, username = p.username, password = secrets.decrypt(p.passwordEnc),
                epgUrl = p.epgUrl, userAgent = p.userAgent,
                favorites = db.library().allFavorites(p.id).map { FavoriteBackup(it.type.name, it.itemId, it.addedAt) },
                groups = db.categories().allPrefs(p.id).map { GroupBackup(it.type.name, it.categoryId, it.hidden, it.locked) },
            )
        }
        val file = BackupFile(
            playlists = playlists,
            settings = SettingsBackup(
                s.bufferProfile.name, s.liveFormat.name, s.decoderMode.name, s.userAgent, s.audioLanguage, s.subtitleLanguage,
                s.epgOffsetMinutes, s.playlistRefreshHours, s.epgRefreshHours, s.parentalPin, s.tmdbApiKey, s.autoplayNextEpisode,
            ),
        )
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.encodeToString(file).toByteArray()) }
            ?: throw IOException("Impossible d'écrire le fichier")
    }

    /** Restaure le fichier ; renvoie les identifiants des playlists ajoutées (à synchroniser). */
    suspend fun import(uri: Uri): List<Long> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: throw IOException("Fichier illisible")
        val file = try { json.decodeFromString<BackupFile>(text) } catch (e: Exception) { throw IOException("Ce fichier n'est pas une sauvegarde Sokoul TV", e) }
        val existing = db.playlists().getAll()
        val added = ArrayList<Long>()
        for (pb in file.playlists) {
            val type = runCatching { PlaylistType.valueOf(pb.type) }.getOrNull() ?: continue
            if (existing.any { it.url == pb.url && it.username == pb.username }) continue
            val id = db.playlists().insert(
                PlaylistEntity(
                    name = pb.name, type = type, url = pb.url, username = pb.username,
                    passwordEnc = pb.password?.let(secrets::encrypt), epgUrl = pb.epgUrl, userAgent = pb.userAgent,
                )
            )
            pb.favorites.forEach { f ->
                runCatching { ContentType.valueOf(f.type) }.getOrNull()?.let { db.library().addFavorite(FavoriteEntity(id, it, f.id, f.addedAt)) }
            }
            pb.groups.forEach { g ->
                runCatching { ContentType.valueOf(g.type) }.getOrNull()?.let { db.categories().upsertPref(CategoryPrefEntity(id, it, g.id, g.hidden, g.locked)) }
            }
            added += id
        }
        val sb = file.settings
        settings.update { s ->
            s.copy(
                bufferProfile = enumOr(sb.bufferProfile, s.bufferProfile), liveFormat = enumOr(sb.liveFormat, s.liveFormat),
                decoderMode = enumOr(sb.decoderMode, s.decoderMode), userAgent = sb.userAgent.ifBlank { AppSettings.DEFAULT_USER_AGENT },
                audioLanguage = sb.audioLanguage, subtitleLanguage = sb.subtitleLanguage, epgOffsetMinutes = sb.epgOffsetMinutes,
                playlistRefreshHours = sb.playlistRefreshHours, epgRefreshHours = sb.epgRefreshHours,
                parentalPin = sb.parentalPin, tmdbApiKey = sb.tmdbApiKey, autoplayNextEpisode = sb.autoplayNextEpisode,
                activePlaylistId = if (s.activePlaylistId == 0L) added.firstOrNull() ?: 0 else s.activePlaylistId,
            )
        }
        added
    }

    private inline fun <reified T : Enum<T>> enumOr(v: String, d: T): T = runCatching { enumValueOf<T>(v) }.getOrDefault(d)
}
