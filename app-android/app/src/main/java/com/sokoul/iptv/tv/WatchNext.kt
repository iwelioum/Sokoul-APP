package com.sokoul.iptv.tv

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.sokoul.iptv.data.AppScope
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.ProgressEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Reprise demandée de l'extérieur (rangée « À suivre » de Google TV). */
data class ResumeLink(val playlistId: Long, val type: ContentType, val itemId: String)

/** État de la rangée « À suivre », affiché dans Réglages › À propos (diagnostic sans journal). */
object WatchNextStatus {
    val text = MutableStateFlow<String?>(null)
}

/** Liens profonds `sokoul://resume/{type}/{playlist}/{id}` (règles pures, testées). */
object DeepLinks {
    const val SCHEME = "sokoul"
    private const val PREFIX = "$SCHEME://resume/"
    private val SAFE_ID = Regex("[A-Za-z0-9:._-]{1,128}")

    /** Reprise en attente, posée par l'activité et consommée par la racine de l'app. */
    val pending = MutableStateFlow<ResumeLink?>(null)

    fun of(p: ProgressEntity): String = PREFIX + p.type.name + "/" + p.playlistId + "/" + URLEncoder.encode(p.itemId, "UTF-8")

    /**
     * L'activité est exportée : n'importe quelle app peut envoyer un lien. Seuls un type VOD, une playlist
     * et un identifiant plausibles passent ; la racine vérifie ensuite que la reprise existe.
     */
    fun parse(uri: String?): ResumeLink? {
        if (uri == null || !uri.startsWith(PREFIX)) return null
        val parts = uri.removePrefix(PREFIX).split('/', limit = 3)
        if (parts.size != 3) return null
        val type = when (parts[0]) { "MOVIE" -> ContentType.MOVIE; "SERIES" -> ContentType.SERIES; else -> return null }
        val pid = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val id = runCatching { URLDecoder.decode(parts[2], "UTF-8") }.getOrNull()?.takeIf { SAFE_ID.matches(it) } ?: return null
        return ResumeLink(pid, type, id)
    }
}

/**
 * Rangée « À suivre » de l'écran d'accueil Android TV / Google TV : les reprises du profil Principal
 * (au plus 10), avec image, avancement et lien profond. Mise à jour à chaque position enregistrée,
 * retirée quand le titre est vu. Seulement sur un téléviseur (Android 8+).
 */
@Singleton
class WatchNextPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        if (!supported()) { WatchNextStatus.text.value = "Indisponible sur cet appareil"; return }
        AppScope.launch {
            db.library().observeMainResume().catch { }.distinctUntilChanged().debounce(3_000).collect { list ->
                runCatching { publish(list) }
                    .onSuccess { WatchNextStatus.text.value = if (list.isEmpty()) "Aucune reprise à publier" else "${list.size} reprise${if (list.size > 1) "s" else ""} publiée${if (list.size > 1) "s" else ""} (profil Principal)" }
                    .onFailure { WatchNextStatus.text.value = "Refusé par le système : ${it.javaClass.simpleName}"; Timber.w("À suivre : %s", it.message) }
            }
        }
    }

    private fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    @SuppressLint("RestrictedApi")
    private fun publish(list: List<ProgressEntity>) {
        val resolver = context.contentResolver
        // nos lignes déjà publiées (le fournisseur ne renvoie que celles de l'app), par clé interne
        val existing = HashMap<String, Long>()
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = WatchNextProgram.fromCursor(c)
                p.internalProviderId?.let { existing[it] = p.id }
            }
        }
        val wanted = list.associateBy { key(it) }
        for ((k, id) in existing) if (k !in wanted) resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
        for ((k, p) in wanted) {
            val values = program(p, k).toContentValues()
            val id = existing[k]
            if (id != null) resolver.update(TvContractCompat.buildWatchNextProgramUri(id), values, null, null)
            else resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
        }
    }

    private fun key(p: ProgressEntity) = "${p.playlistId}|${p.type}|${p.parentId ?: p.itemId}"

    @SuppressLint("RestrictedApi")
    private fun program(p: ProgressEntity, key: String): WatchNextProgram {
        val episode = p.type == ContentType.SERIES
        return WatchNextProgram.Builder()
            .setType(if (episode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(p.updatedAt)
            .setTitle(p.title)
            .apply { if (episode) p.subtitle?.let { setEpisodeTitle(it) } }
            .apply { p.season?.let { setSeasonNumber(it) }; p.episode?.let { setEpisodeNumber(it) } }
            .apply { p.image?.takeIf { it.startsWith("http") }?.let { setPosterArtUri(Uri.parse(it)) } }
            .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_2_3)
            .setLastPlaybackPositionMillis(p.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .setDurationMillis(p.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .setIntentUri(Uri.parse(DeepLinks.of(p)))
            .setInternalProviderId(key)
            .build()
    }
}
