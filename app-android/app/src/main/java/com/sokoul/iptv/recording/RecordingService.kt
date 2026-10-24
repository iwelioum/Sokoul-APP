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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
/** Le serveur a répondu 4xx : inutile d'insister rapidement. */
private class RefusedException(val code: Int) : IOException("HTTP $code")

/** Stockage presque plein : l'enregistrement s'arrête proprement. */
private class NoSpaceException : IOException("Espace de stockage insuffisant : enregistrement arrêté")

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
                checkSpace(file)
                FileOutputStream(file, true).use { out ->
                    var failures = 0
                    var refusals = 0
                    while (coroutineContext.isActive && System.currentTimeMillis() < rec.endAt) {
                        try {
                            if (url.substringBefore('?').endsWith(".m3u8", true)) recordHls(url, stream.userAgent, stream.referrer, out, rec.endAt, file)
                            else recordRaw(url, stream.userAgent, stream.referrer, out, rec.endAt, file)
                            failures = 0
                            refusals = 0
                        } catch (e: NoSpaceException) {
                            throw e
                        } catch (e: RefusedException) {
                            // Connexion refusée (souvent : la seule connexion autorisée est déjà utilisée).
                            // On réessaie lentement pour ne pas se faire bannir par le fournisseur.
                            refusals++
                            Timber.w("Enregistrement $id refusé (HTTP ${e.code}), essai $refusals")
                            if (refusals > 6) throw IOException("Le fournisseur refuse le flux (HTTP ${e.code}) : la connexion autorisée est sans doute déjà utilisée par un autre appareil ou une autre chaîne")
                            delay(30_000)
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
                val reason = if (timedOut) "Arrêté par Android (limite de 6 h d'enregistrement par jour)" else "Arrêté manuellement"
                withContext(NonCancellable) {
                    db.recordings().update(current.copy(status = RecordingStatus.COMPLETED, sizeBytes = file.length(), error = reason))
                }
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

    private fun request(url: String, ua: String, referrer: String?) =
        Request.Builder().url(url).header("User-Agent", ua).apply { referrer?.let { header("Referer", it) } }.build()

    /** Arrêt propre quand le stockage est presque plein (sinon fichier corrompu et système instable). */
    private fun checkSpace(file: File) {
        val dir = file.parentFile ?: return
        if (dir.usableSpace in 1 until RecordingRules.MIN_FREE_BYTES) throw NoSpaceException()
    }

    private suspend fun recordRaw(url: String, ua: String, referrer: String?, out: OutputStream, endAt: Long, file: File) {
        http.newCall(request(url, ua, referrer)).execute().use { resp ->
            if (resp.code in 400..499) throw RefusedException(resp.code)
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val type = resp.header("Content-Type").orEmpty()
            if (type.contains("mpegurl", true)) throw IOException("Flux HLS inattendu")
            val input = resp.body?.byteStream() ?: throw IOException("Flux vide")
            val buf = ByteArray(64 * 1024)
            var reads = 0
            while (coroutineContext.isActive && System.currentTimeMillis() < endAt) {
                val n = input.read(buf)
                if (n < 0) throw IOException("Fin de flux inattendue")
                out.write(buf, 0, n)
                if (++reads % 1024 == 0) checkSpace(file) // ~ tous les 64 Mo
            }
        }
    }

    private suspend fun recordHls(playlistUrl: String, ua: String, referrer: String?, out: OutputStream, endAt: Long, file: File) {
        var mediaUrl = playlistUrl
        val seen = LinkedHashSet<String>()
        while (coroutineContext.isActive && System.currentTimeMillis() < endAt) {
            checkSpace(file)
            val text = fetchText(mediaUrl, ua, referrer)
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
                http.newCall(request(seg, ua, referrer)).execute().use { r ->
                    when {
                        r.isSuccessful -> r.body?.byteStream()?.copyTo(out, 64 * 1024)
                        r.code in 400..499 && r.code != 404 -> throw RefusedException(r.code)
                        // segment expiré (404) ou erreur serveur ponctuelle : on passe au suivant
                        else -> Timber.w("Segment HLS ignoré (HTTP ${r.code})")
                    }
                }
                if (System.currentTimeMillis() >= endAt) return
            }
            if (text.contains("#EXT-X-ENDLIST")) return
            delay((target * 1000 / 2).coerceIn(1000, 5000))
        }
    }

    private fun fetchText(url: String, ua: String, referrer: String?): String =
        http.newCall(request(url, ua, referrer)).execute().use { r ->
            if (r.code in 400..499) throw RefusedException(r.code)
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
        val base = "${stamp}_${RecordingRules.safeFileName(r.channelName, r.title)}"
        var f = File(dir, "$base.ts")
        var n = 2
        while (f.exists()) f = File(dir, "${base}_${n++}.ts")
        return f
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

    @Volatile private var timedOut = false

    /** Android 15 limite les services « dataSync » à 6 h par jour : on arrête proprement au lieu de planter. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        timedOut = true
        jobs.values.forEach { it.cancel() }
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
