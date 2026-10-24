package com.sokoul.iptv.recording

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sokoul.iptv.R
import com.sokoul.iptv.SokoulApp
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.RecordingEntity
import com.sokoul.iptv.data.db.RecordingStatus
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.StreamResolver
import com.sokoul.iptv.data.repo.userMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Enregistre un ou plusieurs flux en parallèle dans des fichiers .ts.
 * Flux MPEG-TS : copie directe des octets. Flux HLS : suivi de la playlist et
 * concaténation des segments. Reconnexion automatique jusqu'à l'heure de fin.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var resolver: StreamResolver
    @Inject lateinit var http: OkHttpClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification("Préparation de l'enregistrement…"))
        val id = intent?.getLongExtra(EXTRA_ID, 0) ?: 0
        when (intent?.action) {
            ACTION_START -> if (id > 0 && !jobs.containsKey(id)) start(id)
            ACTION_STOP -> jobs[id]?.cancel()
        }
        if (jobs.isEmpty()) stopSelfSafely()
        return START_NOT_STICKY
    }

    private fun start(id: Long) {
        acquireWakeLock()
        val job = scope.launch {
            val rec = db.recordings().get(id) ?: return@launch
            if (rec.status == RecordingStatus.CANCELLED || rec.endAt <= System.currentTimeMillis()) return@launch
            val file = rec.filePath?.let(::File) ?: outputFile(rec)
            var current = rec.copy(status = RecordingStatus.RECORDING, filePath = file.absolutePath, error = null)
            db.recordings().update(current)
            updateNotification()
            try {
                val p = playlists.get(rec.playlistId) ?: throw IOException("Playlist supprimée")
                val channel = db.channels().get(rec.playlistId, rec.channelId) ?: throw IOException("Chaîne introuvable")
                val stream = resolver.recordingUrl(p, channel)
                val url = stream.urls.firstOrNull() ?: throw IOException("URL de flux manquante")
                FileOutputStream(file, true).use { out ->
                    var failures = 0
                    while (coroutineContext.isActive && System.currentTimeMillis() < rec.endAt) {
                        try {
                            if (url.substringBefore('?').endsWith(".m3u8", true)) recordHls(url, stream.userAgent, out, rec.endAt)
                            else recordRaw(url, stream.userAgent, stream.referrer, out, rec.endAt)
                            failures = 0
                        } catch (e: IOException) {
                            failures++
                            Timber.w(e, "Enregistrement $id interrompu, reconnexion ($failures)")
                            if (failures > 30) throw e
                            delay(minOf(2_000L * failures, 15_000L))
                        }
                        current = current.copy(sizeBytes = file.length())
                        db.recordings().update(current)
                    }
                }
                db.recordings().update(current.copy(status = RecordingStatus.COMPLETED, sizeBytes = file.length()))
            } catch (e: kotlinx.coroutines.CancellationException) {
                db.recordings().update(current.copy(status = RecordingStatus.COMPLETED, sizeBytes = file.length(), error = "Arrêté manuellement"))
            } catch (e: Exception) {
                Timber.w(e, "Enregistrement $id en échec")
                db.recordings().update(current.copy(status = RecordingStatus.FAILED, sizeBytes = file.length(), error = e.userMessage()))
            }
        }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            scope.launch { updateNotification(); if (jobs.isEmpty()) stopSelfSafely() }
        }
    }

    private suspend fun recordRaw(url: String, ua: String, referrer: String?, out: OutputStream, endAt: Long) {
        val req = Request.Builder().url(url).header("User-Agent", ua).apply { referrer?.let { header("Referer", it) } }.build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val type = resp.header("Content-Type").orEmpty()
            if (type.contains("mpegurl", true)) throw IOException("Flux HLS inattendu")
            val input = resp.body?.byteStream() ?: throw IOException("Flux vide")
            val buf = ByteArray(64 * 1024)
            while (coroutineContext.isActive && System.currentTimeMillis() < endAt) {
                val n = input.read(buf)
                if (n < 0) throw IOException("Fin de flux inattendue")
                out.write(buf, 0, n)
            }
        }
    }

    private suspend fun recordHls(playlistUrl: String, ua: String, out: OutputStream, endAt: Long) {
        var mediaUrl = playlistUrl
        val seen = LinkedHashSet<String>()
        while (coroutineContext.isActive && System.currentTimeMillis() < endAt) {
            val text = fetchText(mediaUrl, ua)
            if (text.contains("#EXT-X-STREAM-INF")) {
                mediaUrl = bestVariant(mediaUrl, text) ?: throw IOException("Aucune variante HLS")
                continue
            }
            if (text.contains("#EXT-X-KEY") && !text.contains("METHOD=NONE")) throw IOException("Flux HLS chiffré : enregistrement impossible")
            val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 6
            for (line in text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
                val seg = resolve(mediaUrl, line)
                if (!seen.add(seg)) continue
                if (seen.size > 500) seen.remove(seen.first())
                http.newCall(Request.Builder().url(seg).header("User-Agent", ua).build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.byteStream()?.copyTo(out, 64 * 1024)
                }
                if (System.currentTimeMillis() >= endAt) return
            }
            if (text.contains("#EXT-X-ENDLIST")) return
            delay((target * 1000 / 2).coerceIn(1000, 5000))
        }
    }

    private fun fetchText(url: String, ua: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }

    private fun bestVariant(base: String, master: String): String? {
        val lines = master.lines()
        var best: Pair<Long, String>? = null
        lines.forEachIndexed { i, l ->
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim() ?: return@forEachIndexed
                if (best == null || bw > best!!.first) best = bw to uri
            }
        }
        return best?.second?.let { resolve(base, it) }
    }

    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http")) ref else java.net.URI(base).resolve(ref).toString()

    private fun outputFile(r: RecordingEntity): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir, "Enregistrements").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH'h'mm", Locale.FRANCE).format(Date(r.startAt))
        val safe = "${r.channelName}_${r.title}".replace(Regex("[^\\p{L}\\p{N}._ -]"), "").take(80).trim()
        return File(dir, "${stamp}_$safe.ts")
    }

    private suspend fun updateNotification() {
        val active = db.recordings().active()
        val text = when (active.size) {
            0 -> "Finalisation…"
            1 -> "● ${active[0].channelName} — ${active[0].title}"
            else -> "● ${active.size} enregistrements en cours"
        }
        startForegroundCompat(buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, SokoulApp.CHANNEL_RECORDINGS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sokoul TV — Enregistrement")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun startForegroundCompat(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type) }
            .onFailure { Timber.w(it, "startForeground impossible") }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sokoul:recording").apply { acquire(12 * 3600_000L) }
    }

    private fun stopSelfSafely() {
        wakeLock?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_ID = "id"
        private const val NOTIFICATION_ID = 4242
    }
}
