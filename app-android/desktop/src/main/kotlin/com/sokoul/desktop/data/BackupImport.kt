package com.sokoul.desktop.data

import com.sokoul.iptv.data.repo.BackupCrypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Lecture de la sauvegarde exportée par l'app Android (format JSON v1 à v3, chiffré ou non) : seuls les champs utiles
 * au PC sont décrits ici, le reste est ignoré (`ignoreUnknownKeys`). Le format de référence est `BackupManager` (:app).
 */
object BackupReader {
    const val APP = "SokoulTV"
    const val FORMAT_ENCRYPTED = "encrypted"
    const val MAX_VERSION = 3
    private const val MAX_BYTES = 5 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Serializable data class File(
        val app: String = "", val version: Int = 0, val withSecrets: Boolean = false,
        val playlists: List<PlaylistB> = emptyList(), val settings: SettingsB? = null,
    )

    @Serializable data class PlaylistB(
        val name: String, val type: String, val url: String, val username: String? = null, val password: String? = null,
        val epgUrl: String? = null, val userAgent: String? = null,
        val favorites: List<FavoriteB> = emptyList(), val groups: List<GroupB> = emptyList(), val progress: List<ProgressB> = emptyList(),
    )

    @Serializable data class FavoriteB(val type: String, val id: String, val addedAt: Long = 0, val profile: Long = 0)
    @Serializable data class GroupB(val type: String, val id: String, val hidden: Boolean = false, val locked: Boolean = false)
    @Serializable data class ProgressB(
        val type: String, val id: String, val title: String = "", val positionMs: Long = 0, val durationMs: Long = 0,
        val updatedAt: Long = 0, val finished: Boolean = false, val profile: Long = 0,
    )
    @Serializable data class SettingsB(val userAgent: String? = null, val parentalPin: String? = null, val tmdbApiKey: String? = null, val epgOffsetMinutes: Int = 0)

    @Serializable private data class Envelope(val iterations: Int, val salt: String, val iv: String, val data: String)

    class PassphraseException(message: String) : IOException(message)

    /** Vrai si le texte est une sauvegarde chiffrée (la phrase de passe sera demandée). */
    fun isEncrypted(text: String): Boolean = runCatching {
        (json.parseToJsonElement(text) as? JsonObject)?.get("format")?.jsonPrimitive?.content == FORMAT_ENCRYPTED
    }.getOrDefault(false)

    fun read(text: String, passphrase: String?): File {
        if (text.length > MAX_BYTES) throw IOException("Fichier trop volumineux pour une sauvegarde Sokoul TV")
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw IOException("Ce fichier n'est pas une sauvegarde Sokoul TV")
        if (root["app"]?.jsonPrimitive?.content != APP) throw IOException("Ce fichier n'est pas une sauvegarde Sokoul TV")
        val plain = if (root["format"]?.jsonPrimitive?.content == FORMAT_ENCRYPTED) {
            if (passphrase.isNullOrEmpty()) throw PassphraseException("Sauvegarde chiffrée : phrase de passe requise")
            val env = json.decodeFromJsonElement(Envelope.serializer(), root)
            try {
                BackupCrypto.open(BackupCrypto.Sealed(env.iterations, env.salt, env.iv, env.data), passphrase).decodeToString()
            } catch (e: com.sokoul.iptv.data.repo.BackupPassphraseException) {
                throw PassphraseException("Phrase de passe incorrecte")
            }
        } else text
        val file = runCatching { json.decodeFromString(File.serializer(), plain) }.getOrElse { throw IOException("Sauvegarde Sokoul TV invalide ou endommagée", it) }
        if (file.version !in 1..MAX_VERSION) throw IOException("Version de sauvegarde non prise en charge (${file.version})")
        return file
    }
}

data class ImportSummary(val playlists: Int, val favorites: Int, val progress: Int, val newPlaylistIds: List<Long>)

/** Écrit une sauvegarde lue par [BackupReader] dans la base du PC, sans rien supprimer de ce qui a été fait sur le PC. */
class DesktopImport(private val db: DesktopDb) {

    fun apply(file: BackupReader.File): ImportSummary = db.tx {
        var favs = 0
        var prog = 0
        val before = db.playlists().map { it.id }.toSet()
        val ids = file.playlists.mapNotNull { p ->
            val type = runCatching { PlaylistType.valueOf(p.type.uppercase()) }.getOrNull() ?: return@mapNotNull null
            val url = p.url.trim()
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@mapNotNull null
            val pid = db.upsertPlaylist(Playlist(0, p.name.take(80), type, url, p.username, p.password, p.epgUrl, p.userAgent))
            // favoris du profil principal (le PC n'a pas encore de profils)
            p.favorites.filter { it.profile == 0L }.forEach { db.addFavorite(pid, it.type, it.id, it.addedAt); favs++ }
            p.progress.filter { it.profile == 0L }.forEach {
                db.mergeProgress(pid, it.type, it.id, it.title, it.positionMs, it.durationMs, it.updatedAt, it.finished); prog++
            }
            // groupes masqués / verrouillés : appliqués à la prochaine synchronisation (catégories gardées par id)
            val flags = p.groups.filter { it.type == "LIVE" && (it.hidden || it.locked) }
            if (flags.isNotEmpty()) db.putSetting("pending_groups_$pid", flags.joinToString("\n") { "${it.id}\t${it.hidden}\t${it.locked}" })
            pid
        }
        file.settings?.let { s ->
            s.parentalPin?.takeIf { it.isNotBlank() }?.let { db.putSetting(Settings.PIN, it) }
            s.tmdbApiKey?.takeIf { it.isNotBlank() }?.let { db.putSetting(Settings.TMDB_KEY, it) }
            s.userAgent?.takeIf { it.isNotBlank() }?.let { db.putSetting(Settings.USER_AGENT, it) }
            db.putSetting(Settings.EPG_OFFSET, s.epgOffsetMinutes.toString())
        }
        if (db.setting(Settings.ACTIVE_PLAYLIST) == null) ids.firstOrNull()?.let { db.putSetting(Settings.ACTIVE_PLAYLIST, it.toString()) }
        ImportSummary(ids.size, favs, prog, ids.filter { it !in before })
    }
}

/** Clés des réglages du PC (table `settings`). */
object Settings {
    const val ACTIVE_PLAYLIST = "active_playlist"
    const val PIN = "parental_pin"
    const val TMDB_KEY = "tmdb_key"
    const val USER_AGENT = "user_agent"
    const val EPG_OFFSET = "epg_offset_minutes"
    const val WINDOW = "window_bounds"
    const val LAST_SCREEN = "last_screen"
    const val LAST_GROUP = "last_group"
    /** Même User-Agent que l'app Android : le fournisseur voit le même client. */
    const val DEFAULT_USER_AGENT = "SokoulTV/2.0 (Linux; Android) ExoPlayer"
}
