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
/** Stockage presque plein : l'enregistrement s'arrête proprement. */
private class NoSpaceException : IOException("Espace de stockage insuffisant : enregistrement arrêté")

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var resolver: StreamResolver
    @Inject lateinit var http: OkHttpClient
    @Inject lateinit var gate: com.sokoul.iptv.player.ProviderGate

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
                // L'enregistrement prend la connexion unique : le lecteur IPTV s'arrête d'abord (une seule connexion à la fois).
                if (stream.provider) { gate.holdForRecording(id, rec.channelName); delay(1_500) }
                checkSpace(file)
                FileOutputStream(file, true).use { out ->
                    var failures = 0
                    var refusals = 0
                    while (coroutineContext.isActive && System.currentTimeMillis() < rec.endAt) {
                        try {
                            val ctx = coroutineContext
                            var reads = 0
                            checkSpace(file)
                            StreamPump(http).pump(url, stream.userAgent, stream.referrer, keepGoing = { ctx.isActive && System.currentTimeMillis() < rec.endAt }) { b, n ->
                                out.write(b, 0, n)
                                if (++reads % 1024 == 0) checkSpace(file) // ~ tous les 64 Mo
                            }
                            failures = 0
                            refusals = 0
                        } catch (e: NoSpaceException) {
                            throw e
                        } catch (e: StreamRefusedException) {
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
                    // fin réelle : la carte affiche « 08:34 – 09:01 » et non l'horaire prévu
                    db.recordings().update(current.copy(
                        status = RecordingStatus.COMPLETED, sizeBytes = file.length(), error = reason,
                        endAt = minOf(current.endAt, System.currentTimeMillis()),
                    ))
                }
            } catch (e: Exception) {
                Timber.w(e, "Enregistrement $id en échec")
                db.recordings().update(current.copy(status = RecordingStatus.FAILED, sizeBytes = file.length(), error = e.userMessage()))
            }
        }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            gate.releaseRecording(id)
            scope.launch { updateNotification(); if (jobs.isEmpty()) stopSelfSafely() }
        }
    }

    /** Arrêt propre quand le stockage est presque plein (sinon fichier corrompu et système instable). */
    private fun checkSpace(file: File) {
        val dir = file.parentFile ?: return
        if (dir.usableSpace in 1 until RecordingRules.MIN_FREE_BYTES) throw NoSpaceException()
    }

    private fun outputFile(r: RecordingEntity): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir, "Enregistrements").apply { mkdirs() }
        val base = RecordingRules.fileBase(r.channelName, r.title, r.startAt)
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
