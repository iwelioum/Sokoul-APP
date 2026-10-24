package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.LiveFormat
import com.sokoul.iptv.parser.Catchup
import com.sokoul.iptv.parser.XtreamUrls
import java.io.IOException
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private fun ChannelEntity.streamId(): Long = id.toLongOrNull() ?: throw IOException("Identifiant de chaîne invalide")

/** Flux prêt à lire : URL principale, solutions de repli et en-têtes HTTP. */
data class ResolvedStream(
    val urls: List<String>,
    val userAgent: String,
    val referrer: String?,
    val isLive: Boolean,
)

@Singleton
class StreamResolver @Inject constructor(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
    private val settings: SettingsStore,
) {
    private suspend fun ua(p: PlaylistEntity, override: String? = null) =
        override?.takeIf { it.isNotBlank() } ?: p.userAgent?.takeIf { it.isNotBlank() } ?: settings.current().userAgent

    suspend fun live(p: PlaylistEntity, c: ChannelEntity): ResolvedStream {
        val urls = when (p.type) {
            PlaylistType.XTREAM -> {
                val a = playlists.auth(p) ?: throw IOException("Identifiants indisponibles")
                val preferred = settings.current().liveFormat
                val other = if (preferred == LiveFormat.TS) LiveFormat.HLS else LiveFormat.TS
                listOf(
                    XtreamUrls.live(a.server, a.username, a.password, c.streamId(), preferred.ext),
                    XtreamUrls.live(a.server, a.username, a.password, c.streamId(), other.ext),
                )
            }
            PlaylistType.M3U -> listOfNotNull(c.streamUrl)
        }
        return ResolvedStream(urls, ua(p, c.userAgent), c.referrer, isLive = true)
    }

    fun supportsCatchup(p: PlaylistEntity, c: ChannelEntity): Boolean = when {
        c.catchupType == SyncEngine.CATCHUP_XTREAM -> p.type == PlaylistType.XTREAM
        !c.catchupType.isNullOrBlank() -> true
        else -> false
    }

    suspend fun catchup(p: PlaylistEntity, c: ChannelEntity, start: Long, end: Long): ResolvedStream {
        if (!supportsCatchup(p, c)) throw IOException("Le replay n'est pas disponible pour cette chaîne")
        if (end <= start) throw IOException("Plage de replay invalide")
        if (!inCatchupWindow(c, start)) throw IOException("Ce programme n'est plus disponible en replay (${c.catchupDays} jour(s) d'archive)")
        val url = when {
            c.catchupType == SyncEngine.CATCHUP_XTREAM -> {
                val a = playlists.auth(p) ?: throw IOException("Identifiants indisponibles")
                val tz = p.serverTimezone?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
                val minutes = ((end - start) / 60_000L).toInt().coerceAtLeast(1) + 1
                XtreamUrls.timeshift(a.server, a.username, a.password, c.streamId(), start, minutes, tz)
            }
            else -> Catchup.build(c.catchupType, c.catchupSource, c.streamUrl ?: "", start, end)
                ?: throw IOException("Le replay n'est pas disponible pour cette chaîne")
        }
        return ResolvedStream(listOf(url), ua(p, c.userAgent), c.referrer, isLive = false)
    }

    suspend fun movie(p: PlaylistEntity, m: MovieEntity): ResolvedStream {
        val url = when (p.type) {
            PlaylistType.XTREAM -> {
                val a = playlists.auth(p) ?: throw IOException("Identifiants indisponibles")
                XtreamUrls.movie(a.server, a.username, a.password, m.id.toLongOrNull() ?: throw IOException("Identifiant de film invalide"), m.extension)
            }
            PlaylistType.M3U -> m.streamUrl ?: throw IOException("URL du film manquante")
        }
        return ResolvedStream(listOf(url), ua(p), null, isLive = false)
    }

    suspend fun episode(p: PlaylistEntity, e: EpisodeEntity): ResolvedStream {
        val url = when (p.type) {
            PlaylistType.XTREAM -> {
                val a = playlists.auth(p) ?: throw IOException("Identifiants indisponibles")
                XtreamUrls.episode(a.server, a.username, a.password, e.id, e.extension)
            }
            PlaylistType.M3U -> e.streamUrl ?: throw IOException("URL de l'épisode manquante")
        }
        return ResolvedStream(listOf(url), ua(p), null, isLive = false)
    }

    companion object {
        /** Le début du programme est-il encore dans la fenêtre d'archive de la chaîne ? (0 jour = inconnu : on tente) */
        fun inCatchupWindow(c: ChannelEntity, start: Long, now: Long = System.currentTimeMillis()): Boolean =
            c.catchupDays <= 0 || start >= now - c.catchupDays * 86_400_000L - 3600_000L
    }

    /** URL brute d'enregistrement : toujours en MPEG-TS quand c'est possible (copie directe). */
    suspend fun recordingUrl(p: PlaylistEntity, c: ChannelEntity): ResolvedStream {
        val urls = when (p.type) {
            PlaylistType.XTREAM -> {
                val a = playlists.auth(p) ?: throw IOException("Identifiants indisponibles")
                listOf(XtreamUrls.live(a.server, a.username, a.password, c.streamId(), "ts"))
            }
            PlaylistType.M3U -> listOfNotNull(c.streamUrl)
        }
        return ResolvedStream(urls, ua(p, c.userAgent), c.referrer, isLive = true)
    }
}
