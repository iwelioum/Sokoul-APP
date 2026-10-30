package com.sokoul.iptv.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.PinHasher
import com.sokoul.iptv.data.SecretBox
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryPrefEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.FavoriteEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.db.ProfileEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.db.RecordingRuleEntity
import com.sokoul.iptv.parser.ServerUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupFile(
    val app: String = BackupManager.APP,
    val version: Int = BackupManager.VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    /** Vrai si le fichier contient des secrets (uniquement dans une enveloppe chiffrée). */
    val withSecrets: Boolean = false,
    val playlists: List<PlaylistBackup>,
    val settings: SettingsBackup,
    /** Profils (v3) ; le profil 0 « Principal » est toujours présent sur l'appareil. */
    val profiles: List<ProfileBackup> = emptyList(),
)

@Serializable data class ProfileBackup(val id: Long, val name: String, val color: Int = 0, val kids: Boolean = false)

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
    /** Reprise de lecture et vus (v3). */
    val progress: List<ProgressBackup> = emptyList(),
    /** Règles « tous les épisodes » (titre + chaîne). */
    val recordingRules: List<RecordingRuleBackup> = emptyList(),
)

@Serializable data class FavoriteBackup(val type: String, val id: String, val addedAt: Long, val profile: Long = 0)
@Serializable data class RecordingRuleBackup(val channelId: String, val channelName: String, val title: String, val createdAt: Long = 0)
@Serializable data class GroupBackup(val type: String, val id: String, val hidden: Boolean, val locked: Boolean, val kids: Int = 0)
@Serializable data class ProgressBackup(
    val type: String, val id: String, val parentId: String? = null, val title: String, val subtitle: String? = null, val image: String? = null,
    val positionMs: Long, val durationMs: Long, val updatedAt: Long, val finished: Boolean, val season: Int? = null, val episode: Int? = null,
    val manual: Boolean = false, val profile: Long = 0,
)

@Serializable
data class SettingsBackup(
    val bufferProfile: String, val liveFormat: String, val decoderMode: String, val userAgent: String,
    val audioLanguage: String, val subtitleLanguage: String, val epgOffsetMinutes: Int,
    val playlistRefreshHours: Int, val epgRefreshHours: Int,
    /** Hash du code parental (jamais le code en clair) ; absent d'une sauvegarde sans secrets. */
    val parentalPin: String? = null,
    val tmdbApiKey: String? = null,
    val autoplayNextEpisode: Boolean = true,
    val groupDuplicates: Boolean = true,
    val vodLanguages: String? = null,
    val livePreview: Boolean = true,
)

/** Enveloppe d'une sauvegarde chiffrée (voir [BackupCrypto]). */
@Serializable
data class EncryptedBackup(
    val app: String = BackupManager.APP,
    val format: String = BackupManager.FORMAT_ENCRYPTED,
    val version: Int = BackupManager.VERSION,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
)

data class ExportResult(val playlists: Int, val skippedWithCredentials: Int)
data class ImportResult(val added: List<Long>, val toSync: List<Long>, val skipped: Int, val withoutPassword: Int)

/**
 * Sauvegarde / restauration en JSON.
 * - Par défaut, aucun secret : ni mot de passe, ni code parental, ni clé TMDB. Les playlists M3U dont le lien
 *   contient des identifiants ne sont pas exportées dans ce mode.
 * - Avec une phrase de passe : tout est inclus, dans une enveloppe chiffrée (PBKDF2 + AES-GCM).
 * - À l'import, tout est validé (taille, schémas d'URL, bornes) et écrit en une seule transaction.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val secrets: SecretBox,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri, passphrase: String?): ExportResult = withContext(Dispatchers.IO) {
        val (text, result) = exportText(passphrase)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw IOException("Impossible d'écrire le fichier")
        result
    }

    suspend fun import(uri: Uri, passphrase: String?): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { readLimited(it) } ?: throw IOException("Fichier illisible")
        importText(text, passphrase)
    }

    /** Dossier des sauvegardes quand le sélecteur de fichiers système est absent (Android TV). */
    fun backupDir(): java.io.File =
        java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "Sauvegardes").apply { mkdirs() }

    fun localBackups(): List<java.io.File> =
        backupDir().listFiles { f -> f.isFile && f.name.endsWith(".json", true) }.orEmpty().sortedByDescending { it.lastModified() }

    suspend fun exportToFolder(passphrase: String?): Pair<java.io.File, ExportResult> = withContext(Dispatchers.IO) {
        val (text, result) = exportText(passphrase)
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH'h'mm", java.util.Locale.FRANCE).format(java.util.Date())
        val f = java.io.File(backupDir(), "sokoul-sauvegarde${if (passphrase != null) "-chiffree" else ""}_$stamp.json")
        f.writeText(text)
        f to result
    }

    suspend fun importFile(file: java.io.File, passphrase: String?): ImportResult = withContext(Dispatchers.IO) {
        // uniquement les fichiers de notre dossier de sauvegardes
        if (file.canonicalFile.parentFile != backupDir().canonicalFile) throw IOException("Fichier non autorisé")
        importText(file.inputStream().use(::readLimited), passphrase)
    }

    /** Restauration depuis une adresse web (http/https), pratique sur TV. */
    suspend fun importUrl(url: String, passphrase: String?, http: okhttp3.OkHttpClient): ImportResult = withContext(Dispatchers.IO) {
        if (!BackupRules.isHttpUrl(url)) throw IOException("Adresse invalide (http/https attendu)")
        val text = http.newCall(okhttp3.Request.Builder().url(url.trim()).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException(com.sokoul.iptv.data.net.httpMessage(r.code))
            r.body?.byteStream()?.use(::readLimited) ?: throw IOException("Réponse vide")
        }
        importText(text, passphrase)
    }

    suspend fun exportText(passphrase: String?): Pair<String, ExportResult> {
        val withSecrets = passphrase != null
        val s = settings.current()
        var skipped = 0
        val playlists = db.playlists().getAll().mapNotNull { p ->
            if (!withSecrets && p.type == PlaylistType.M3U && BackupRules.hasCredentials(p.url)) { skipped++; return@mapNotNull null }
            PlaylistBackup(
                name = p.name, type = p.type.name, url = p.url, username = p.username,
                password = if (withSecrets) secrets.decrypt(p.passwordEnc) else null,
                epgUrl = p.epgUrl?.takeIf { withSecrets || !BackupRules.hasCredentials(it) },
                userAgent = p.userAgent,
                favorites = db.library().allFavorites(p.id).map { FavoriteBackup(it.type.name, it.itemId, it.addedAt, it.profileId) },
                groups = db.categories().allPrefs(p.id).map { GroupBackup(it.type.name, it.categoryId, it.hidden, it.locked, it.kids) },
                progress = db.profiles().allProgress(p.id).sortedByDescending { it.updatedAt }.take(MAX_ITEMS).map {
                    ProgressBackup(it.type.name, it.itemId, it.parentId, it.title, it.subtitle, it.image, it.positionMs, it.durationMs, it.updatedAt, it.finished, it.season, it.episode, it.manual, it.profileId)
                },
                recordingRules = db.recordings().allRules().filter { it.playlistId == p.id }
                    .map { RecordingRuleBackup(it.channelId, it.channelName, it.title, it.createdAt) },
            )
        }
        val file = BackupFile(
            withSecrets = withSecrets,
            playlists = playlists,
            profiles = db.profiles().all().map { ProfileBackup(it.id, it.name, it.color, it.kids) },
            settings = SettingsBackup(
                s.bufferProfile.name, s.liveFormat.name, s.decoderMode.name, s.userAgent, s.audioLanguage, s.subtitleLanguage,
                s.epgOffsetMinutes, s.playlistRefreshHours, s.epgRefreshHours,
                parentalPin = s.parentalPin.takeIf { withSecrets && it.isNotEmpty() },
                tmdbApiKey = s.tmdbApiKey.takeIf { withSecrets && it.isNotEmpty() },
                autoplayNextEpisode = s.autoplayNextEpisode,
                groupDuplicates = s.groupDuplicates,
                vodLanguages = s.vodLanguages,
                livePreview = s.livePreview,
            ),
        )
        val plain = json.encodeToString(file)
        val text = if (passphrase == null) plain else {
            val sealed = BackupCrypto.seal(plain.toByteArray(), passphrase)
            json.encodeToString(EncryptedBackup(iterations = sealed.iterations, salt = sealed.salt, iv = sealed.iv, data = sealed.data))
        }
        return text to ExportResult(playlists.size, skipped)
    }

    suspend fun importText(text: String, passphrase: String?): ImportResult {
        if (text.length > MAX_BYTES) throw IOException("Fichier trop volumineux pour une sauvegarde Sokoul TV")
        val root = try { json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null }
            ?: throw IOException("Ce fichier n'est pas une sauvegarde Sokoul TV")
        if (root["app"]?.jsonPrimitive?.content != APP) throw IOException("Ce fichier n'est pas une sauvegarde Sokoul TV")
        val plain = if (root["format"]?.jsonPrimitive?.content == FORMAT_ENCRYPTED) {
            if (passphrase.isNullOrEmpty()) throw BackupPassphraseException("Sauvegarde chiffrée : phrase de passe requise")
            val env = decode<EncryptedBackup>(text)
            BackupCrypto.open(BackupCrypto.Sealed(env.iterations, env.salt, env.iv, env.data), passphrase).decodeToString()
        } else text
        val file = decode<BackupFile>(plain)
        if (file.version !in 1..VERSION) throw IOException("Version de sauvegarde non prise en charge (${file.version})")
        if (file.playlists.size > MAX_PLAYLISTS) throw IOException("Sauvegarde invalide : trop de playlists")

        val existing = db.playlists().getAll()
        val added = ArrayList<Long>()
        val toSync = ArrayList<Long>()
        var skipped = 0
        var withoutPassword = 0
        // profils de la sauvegarde → profils de l'appareil (même nom = même profil ; sinon créé)
        val profileMap = HashMap<Long, Long>().apply { put(ProfileEntity.MAIN, ProfileEntity.MAIN) }
        val local = db.profiles().all()
        for (pr in file.profiles.take(ProfileRepository.MAX_PROFILES)) {
            if (pr.id == ProfileEntity.MAIN) continue
            val name = pr.name.trim().take(ProfileRepository.MAX_NAME)
            if (name.isEmpty()) continue
            val same = local.firstOrNull { it.name.equals(name, ignoreCase = true) }
            profileMap[pr.id] = same?.id ?: run {
                if (db.profiles().all().size >= ProfileRepository.MAX_PROFILES) return@run ProfileEntity.MAIN
                val id = (db.profiles().all().maxOfOrNull { it.id } ?: 0) + 1
                db.profiles().upsert(ProfileEntity(id, name, pr.color.coerceIn(0, ProfileRepository.AVATAR_COLORS - 1), pr.kids, id.toInt()))
                id
            }
        }
        db.withTransaction {
            for (pb in file.playlists) {
                val p = BackupRules.validPlaylist(pb)
                if (p == null) { skipped++; continue }
                if (existing.any { it.url == p.url && it.username == p.username }) { skipped++; continue }
                val password = pb.password?.takeIf { it.isNotEmpty() && it.length <= 256 }
                if (p.type == PlaylistType.XTREAM && password == null) withoutPassword++
                val id = db.playlists().insert(p.copy(passwordEnc = password?.let(secrets::encrypt)))
                pb.favorites.take(MAX_ITEMS).forEach { f ->
                    val t = enumOrNull<ContentType>(f.type) ?: return@forEach
                    if (f.id.isBlank() || f.id.length > 64) return@forEach
                    db.library().addFavorite(FavoriteEntity(id, t, f.id, f.addedAt.coerceAtLeast(0), profileId = profileMap[f.profile] ?: ProfileEntity.MAIN))
                }
                pb.groups.take(MAX_ITEMS).forEach { g ->
                    val t = enumOrNull<ContentType>(g.type) ?: return@forEach
                    if (g.id.isBlank() || g.id.length > 128) return@forEach
                    db.categories().upsertPref(CategoryPrefEntity(id, t, g.id, g.hidden, g.locked, g.kids.coerceIn(0, 2)))
                }
                db.profiles().upsertProgress(pb.progress.take(MAX_ITEMS).mapNotNull { r ->
                    val t = enumOrNull<ContentType>(r.type) ?: return@mapNotNull null
                    if (r.id.isBlank() || r.id.length > 64 || r.positionMs < 0 || r.durationMs < 0) return@mapNotNull null
                    ProgressEntity(
                        id, t, r.id, r.parentId?.take(64), r.title.take(300), r.subtitle?.take(300), r.image?.take(1000), r.positionMs, r.durationMs,
                        r.updatedAt.coerceAtLeast(0), r.finished, r.season, r.episode, r.manual, profileMap[r.profile] ?: ProfileEntity.MAIN,
                    )
                })
                pb.recordingRules.take(MAX_RULES).forEach { r ->
                    val title = r.title.trim().take(200)
                    if (title.isEmpty() || r.channelId.isBlank() || r.channelId.length > 64) return@forEach
                    db.recordings().insertRule(
                        RecordingRuleEntity(
                            playlistId = id, channelId = r.channelId, channelName = r.channelName.take(200), title = title,
                            ruleKey = com.sokoul.iptv.recording.RecordingRules.seriesKey(r.channelName, title),
                            createdAt = r.createdAt.coerceAtLeast(0),
                        )
                    )
                }
                added += id
                if (p.type == PlaylistType.M3U || password != null) toSync += id
            }
        }
        val sb = file.settings
        settings.update { s ->
            s.copy(
                bufferProfile = enumOrNull<com.sokoul.iptv.data.BufferProfile>(sb.bufferProfile) ?: s.bufferProfile,
                liveFormat = enumOrNull<com.sokoul.iptv.data.LiveFormat>(sb.liveFormat) ?: s.liveFormat,
                decoderMode = enumOrNull<com.sokoul.iptv.data.DecoderMode>(sb.decoderMode) ?: s.decoderMode,
                userAgent = sb.userAgent.ifBlank { AppSettings.DEFAULT_USER_AGENT },
                audioLanguage = sb.audioLanguage, subtitleLanguage = sb.subtitleLanguage, epgOffsetMinutes = sb.epgOffsetMinutes,
                playlistRefreshHours = sb.playlistRefreshHours, epgRefreshHours = sb.epgRefreshHours,
                // Un import ne remplace jamais un code parental existant (sinon il suffirait d'importer pour le contourner).
                parentalPin = if (s.parentalPin.isEmpty()) BackupRules.importedPin(sb.parentalPin) else s.parentalPin,
                tmdbApiKey = sb.tmdbApiKey?.takeIf { it.isNotBlank() } ?: s.tmdbApiKey,
                autoplayNextEpisode = sb.autoplayNextEpisode,
                groupDuplicates = sb.groupDuplicates,
                vodLanguages = sb.vodLanguages ?: s.vodLanguages,
                livePreview = sb.livePreview,
                activePlaylistId = if (s.activePlaylistId == 0L) added.firstOrNull() ?: 0 else s.activePlaylistId,
            )
        }
        return ImportResult(added, toSync, skipped, withoutPassword)
    }

    private inline fun <reified T> decode(text: String): T =
        try { json.decodeFromString<T>(text) } catch (e: Exception) { throw IOException("Sauvegarde Sokoul TV invalide ou endommagée", e) }

    private fun readLimited(input: InputStream): String {
        val bytes = input.readNBytesCompat(MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) throw IOException("Fichier trop volumineux pour une sauvegarde Sokoul TV")
        return bytes.decodeToString()
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val APP = "SokoulTV"
        const val VERSION = 3
        const val FORMAT_ENCRYPTED = "encrypted"
        const val MAX_BYTES = 5 * 1024 * 1024
        const val MAX_PLAYLISTS = 50
        const val MAX_ITEMS = 20_000
        const val MAX_RULES = 200
    }
}

/** Règles de validation des sauvegardes (pures, testées unitairement). */
object BackupRules {
    private val credentialParams = Regex("[?&](username|password|pass|token|auth|key)=", RegexOption.IGNORE_CASE)

    /** Le lien contient-il des identifiants (paramètres ou `user:pass@`) ? */
    fun hasCredentials(url: String): Boolean =
        credentialParams.containsMatchIn(url) || Regex("^[a-z]+://[^/@]+:[^/@]*@", RegexOption.IGNORE_CASE).containsMatchIn(url)

    fun isHttpUrl(url: String): Boolean {
        val u = url.trim()
        val scheme = u.substringBefore("://", "").lowercase()
        return (scheme == "http" || scheme == "https") && u.length <= 2048 && u.substringAfter("://").let { it.isNotBlank() && !it.startsWith("/") } &&
            u.none { it.isWhitespace() || it < ' ' }
    }

    /** Hôte et port exploitables (refuse par ex. « javascript:alert(1) » devenu « http://javascript:alert(1) »). */
    fun hasValidHost(url: String): Boolean = runCatching {
        val u = java.net.URI(url)
        !u.host.isNullOrBlank() && (u.port == -1 || u.port in 1..65535)
    }.getOrDefault(false)

    /** Convertit une entrée de sauvegarde en playlist, ou null si elle est invalide. */
    fun validPlaylist(pb: PlaylistBackup): PlaylistEntity? {
        val type = runCatching { PlaylistType.valueOf(pb.type) }.getOrNull() ?: return null
        val url = when (type) {
            PlaylistType.XTREAM -> runCatching { ServerUrl.normalize(pb.url) }.getOrNull()?.takeIf(::hasValidHost) ?: return null
            PlaylistType.M3U -> pb.url.trim().takeIf(::isHttpUrl) ?: return null
        }
        val username = pb.username?.trim()?.take(256)
        if (type == PlaylistType.XTREAM && username.isNullOrEmpty()) return null
        return PlaylistEntity(
            name = pb.name.trim().take(100).ifBlank { url.substringAfter("://").substringBefore('/').take(100) },
            type = type,
            url = url,
            username = username,
            epgUrl = pb.epgUrl?.trim()?.takeIf(::isHttpUrl),
            userAgent = pb.userAgent?.trim()?.take(AppSettings.MAX_USER_AGENT)?.takeIf { it.isNotEmpty() && it.none { c -> c < ' ' } },
        )
    }

    /** Code parental importé : hash valide, ou ancien code en clair re-haché ; sinon aucun. */
    fun importedPin(stored: String?): String = when {
        stored.isNullOrEmpty() -> ""
        PinHasher.isHash(stored) && PinHasher.isValidStored(stored) -> stored
        PinHasher.isValidPin(stored) -> PinHasher.hash(stored)
        else -> ""
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(v: String): T? = runCatching { enumValueOf<T>(v) }.getOrNull()
