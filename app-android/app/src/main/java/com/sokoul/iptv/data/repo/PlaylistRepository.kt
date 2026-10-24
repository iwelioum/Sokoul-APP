package com.sokoul.iptv.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sokoul.iptv.data.SecretBox
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.net.XtreamAuth
import com.sokoul.iptv.data.net.XtreamClient
import com.sokoul.iptv.data.net.openStream
import com.sokoul.iptv.parser.ServerUrl
import com.sokoul.iptv.recording.RecordingScheduler
import com.sokoul.iptv.ui.MultiviewSession
import com.sokoul.iptv.work.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Sources de playlist M3U acceptées. */
enum class PlaylistSource {
    HTTP, CONTENT, INVALID;

    companion object {
        fun of(url: String): PlaylistSource {
            val u = url.trim()
            val scheme = u.substringBefore("://", "").lowercase()
            return when {
                scheme == "content" && u.length > "content://".length -> CONTENT
                BackupRules.isHttpUrl(u) -> HTTP
                else -> INVALID
            }
        }
    }
}

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val secrets: SecretBox,
    private val settings: SettingsStore,
    private val xtream: XtreamClient,
    private val http: OkHttpClient,
    private val recordings: RecordingScheduler,
    private val multiview: MultiviewSession,
) {
    val playlists: Flow<List<PlaylistEntity>> = db.playlists().observeAll()

    /** Playlist active : celle choisie dans les réglages, sinon la première. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val active: Flow<PlaylistEntity?> = combine(playlists, settings.settings) { list, s ->
        list.firstOrNull { it.id == s.activePlaylistId } ?: list.firstOrNull()
    }.distinctUntilChanged()

    suspend fun get(id: Long) = db.playlists().get(id)

    suspend fun activeNow(): PlaylistEntity? {
        val list = db.playlists().getAll()
        val id = settings.current().activePlaylistId
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    suspend fun setActive(id: Long) = settings.update { it.copy(activePlaylistId = id) }

    fun auth(p: PlaylistEntity): XtreamAuth? {
        if (p.type != PlaylistType.XTREAM) return null
        val pass = secrets.decrypt(p.passwordEnc) ?: return null
        return XtreamAuth(p.url, p.username.orEmpty(), pass, p.userAgent)
    }

    /** Vérifie les identifiants, enregistre la playlist et la rend active. */
    suspend fun addXtream(name: String, server: String, username: String, password: String, epgUrl: String?, userAgent: String?): Long {
        val url = ServerUrl.normalize(server)
        require(BackupRules.hasValidHost(url)) { "Adresse du serveur invalide" }
        require(username.isNotBlank() && password.isNotEmpty()) { "Identifiant et mot de passe obligatoires" }
        epgUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { e ->
            require(e.split(',', '\n', ' ').filter { it.isNotBlank() }.all { PlaylistSource.of(it) != PlaylistSource.INVALID }) { "Adresse du guide TV invalide (http/https attendu)" }
        }
        val existing = db.playlists().getAll().firstOrNull { it.type == PlaylistType.XTREAM && it.url == url && it.username == username.trim() }
        require(existing == null) { "Cette playlist est déjà ajoutée (« ${existing?.name} »)" }
        val auth = XtreamAuth(url, username.trim(), password, userAgent?.trim()?.take(256)?.takeIf { it.isNotBlank() })
        val account = xtream.account(auth)
        if (!account.auth) throw IOException(account.message ?: "Identifiants refusés par le serveur")
        if (!account.isActive) throw IOException("Abonnement inactif (statut : ${account.status})")
        val id = db.playlists().insert(
            PlaylistEntity(
                name = name.ifBlank { url.substringAfter("://").substringBefore(':').substringBefore('/') },
                type = PlaylistType.XTREAM,
                url = url,
                username = auth.username,
                passwordEnc = secrets.encrypt(password),
                epgUrl = epgUrl?.trim()?.takeIf { it.isNotEmpty() },
                userAgent = auth.userAgent,
                serverTimezone = account.serverTimezone,
                expiresAt = account.expiresAt,
                maxConnections = account.maxConnections,
            )
        )
        setActive(id)
        return id
    }

    suspend fun addM3u(name: String, url: String, epgUrl: String?, userAgent: String?): Long {
        val clean = url.trim()
        require(clean.isNotEmpty()) { "L'URL de la playlist est vide" }
        require(PlaylistSource.of(clean) != PlaylistSource.INVALID) { "Adresse invalide : utilisez un lien http(s) ou choisissez un fichier" }
        epgUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { e ->
            require(e.split(',', '\n', ' ').filter { it.isNotBlank() }.all { PlaylistSource.of(it) != PlaylistSource.INVALID }) { "Adresse du guide TV invalide (http/https attendu)" }
        }
        withContext(Dispatchers.IO) { checkM3u(clean, userAgent) }
        val id = db.playlists().insert(
            PlaylistEntity(
                name = name.ifBlank { "Playlist M3U" },
                type = PlaylistType.M3U,
                url = clean,
                epgUrl = epgUrl?.trim()?.takeIf { it.isNotEmpty() },
                userAgent = userAgent?.takeIf { it.isNotBlank() },
            )
        )
        setActive(id)
        return id
    }

    suspend fun updateDetails(p: PlaylistEntity, name: String, epgUrl: String?, userAgent: String?, newPassword: String?) {
        epgUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { e ->
            require(e.split(',', '\n', ' ').filter { it.isNotBlank() }.all { PlaylistSource.of(it) != PlaylistSource.INVALID }) { "Adresse du guide TV invalide (http/https attendu)" }
        }
        db.playlists().update(
            p.copy(
                name = name.ifBlank { p.name },
                epgUrl = epgUrl?.trim()?.takeIf { it.isNotEmpty() },
                userAgent = userAgent?.trim()?.takeIf { it.isNotEmpty() },
                passwordEnc = if (!newPassword.isNullOrEmpty()) secrets.encrypt(newPassword) else p.passwordEnc,
            )
        )
    }

    /**
     * Supprime une playlist et tout ce qui en dépend : synchronisations en cours, enregistrements programmés
     * ou en cours, rappels, multi-écran, catalogue, guide, favoris et reprises.
     * Les enregistrements terminés (fichiers vidéo) sont conservés et restent lisibles.
     */
    suspend fun delete(id: Long) {
        SyncWorker.cancelFor(context, id)
        recordings.cancelForPlaylist(id)
        multiview.removePlaylist(id)
        db.withTransaction {
            purgeData(id)
            db.playlists().delete(id)
        }
        val remaining = db.playlists().getAll()
        if (settings.current().activePlaylistId == id) settings.update { it.copy(activePlaylistId = remaining.firstOrNull()?.id ?: 0) }
    }

    /** Efface les données d'une playlist (aussi appelé si une synchronisation se termine après sa suppression). */
    fun purgeData(id: Long) {
        db.openHelper.writableDatabase.apply {
            for (table in PLAYLIST_TABLES) execSQL("DELETE FROM $table WHERE playlistId = ?", arrayOf(id))
        }
    }

    companion object {
        val PLAYLIST_TABLES = listOf(
            "categories", "category_prefs", "channels", "movies", "series", "episodes", "series_details",
            "programs", "epg_channels", "favorites", "progress", "reminders", "tmdb_links", "vod_choice", "tmdb_match",
        )
    }

    /**
     * Ouvre une playlist distante (http/https) ou un fichier choisi avec le sélecteur système (content://).
     * Les chemins file:// et autres schémas sont refusés : ils permettraient de lire les fichiers privés de l'app.
     */
    fun openPlaylistStream(url: String, userAgent: String?): Pair<AutoCloseable, InputStream> {
        val uri = Uri.parse(url.trim())
        return when (PlaylistSource.of(url)) {
            PlaylistSource.CONTENT -> {
                if (uri.authority == context.packageName || uri.authority?.startsWith(context.packageName + ".") == true) {
                    throw IOException("Source de playlist non autorisée")
                }
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Fichier illisible")
                val buffered = input.buffered(64 * 1024)
                buffered.mark(2)
                val gz = buffered.read() == 0x1f && buffered.read() == 0x8b
                buffered.reset()
                val stream = if (gz) java.util.zip.GZIPInputStream(buffered).buffered() else buffered
                stream to stream
            }
            PlaylistSource.HTTP -> {
                val (resp, stream) = http.openStream(url.trim(), userAgent)
                resp to stream
            }
            PlaylistSource.INVALID -> throw IOException("Adresse de playlist invalide : utilisez un lien http(s) ou choisissez un fichier")
        }
    }

    private fun checkM3u(url: String, userAgent: String?) {
        val (closeable, stream) = openPlaylistStream(url, userAgent)
        closeable.use {
            val reader = stream.bufferedReader()
            var lines = 0
            while (lines < 50) {
                val line = reader.readLine() ?: break
                val t = line.trim().removePrefix("\uFEFF")
                if (t.isEmpty()) continue
                if (t.startsWith("#EXTM3U", true) || t.startsWith("#EXTINF", true)) return
                lines++
            }
        }
        throw IOException("Le fichier ne ressemble pas à une playlist M3U")
    }
}
