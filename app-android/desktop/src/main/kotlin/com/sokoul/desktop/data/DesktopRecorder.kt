package com.sokoul.desktop.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Enregistrements du PC : un planificateur vérifie la table toutes les 20 s ; à l'heure, le flux de la chaîne est lu
 * en continu (une connexion) et écrit en `.ts` dans le dossier `recordings` jusqu'à l'heure de fin. L'app doit rester ouverte : un
 * enregistrement prévu app fermée est marqué manqué au lancement suivant. Un refus du fournisseur (4xx) n'est pas
 * relancé tout de suite ; une coupure réseau reprend le flux, à la suite du même fichier.
 */
class DesktopRecorder(private val db: DesktopDb, private val sync: DesktopSync, private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val _active = MutableStateFlow<Recording?>(null)
    /** Enregistrement en cours (il a la connexion), ou null. */
    val active: StateFlow<Recording?> = _active
    /** Incrémenté à chaque changement d'état (l'écran Enregistrements recharge). */
    val version = MutableStateFlow(0)
    private var job: Job? = null

    val dir: File get() = File(AppDirs.data, "recordings").apply { mkdirs() }

    fun start(playlist: () -> Playlist?) {
        scope.launch(Dispatchers.IO) {
            // au lancement : ce qui devait être enregistré pendant que l'app était fermée est manqué
            playlist()?.let { p ->
                val now = System.currentTimeMillis()
                db.recordings(p.id).filter { PcRecordingRules.missed(it, now) }.forEach { r ->
                    db.updateRecording(r.id, RecordingStatus.FAILED, error = if (r.status == RecordingStatus.RECORDING) "Interrompu (app fermée)" else "Manqué (app fermée)")
                }
                version.value++
            }
            while (isActive) {
                runCatching { tick(playlist()) }
                delay(20_000)
            }
        }
    }

    /** Programme l'enregistrement de [title] sur [c], de [start] à [end] (marges déjà comprises). */
    fun schedule(p: Playlist, c: Channel, title: String, start: Long, end: Long): PcRecordingRules.Verdict {
        val v = PcRecordingRules.check(c.id, start, end, db.recordings(p.id))
        if (v is PcRecordingRules.Verdict.Ok) {
            db.insertRecording(Recording(0, p.id, c.id, c.name, c.logo, title, start, end, RecordingStatus.SCHEDULED))
            version.value++
            scope.launch(Dispatchers.IO) { runCatching { tick(p) } }
        }
        return v
    }

    fun cancel(r: Recording) {
        if (_active.value?.id == r.id) job?.cancel()
        db.updateRecording(r.id, RecordingStatus.CANCELLED, error = "Annulé")
        version.value++
    }

    fun delete(r: Recording) {
        if (_active.value?.id == r.id) job?.cancel()
        r.path?.let { File(it).delete() }
        db.deleteRecording(r.id)
        version.value++
    }

    @Synchronized
    private fun tick(p: Playlist?) {
        p ?: return
        if (_active.value != null) return
        val now = System.currentTimeMillis()
        val due = db.recordings(p.id).firstOrNull { it.status == RecordingStatus.SCHEDULED && it.startAt <= now && it.endAt > now } ?: return
        val c = db.allChannels(p.id).firstOrNull { it.id == due.channelId }
        if (c == null) { db.updateRecording(due.id, RecordingStatus.FAILED, error = "Chaîne introuvable dans la playlist"); version.value++; return }
        record(p, c, due)
    }

    private fun record(p: Playlist, c: Channel, r: Recording) {
        val file = File(dir, PcRecordingRules.fileBase(r.channelName, r.title, r.startAt) + ".ts")
        db.updateRecording(r.id, RecordingStatus.RECORDING, path = file.absolutePath)
        _active.value = r.copy(status = RecordingStatus.RECORDING, path = file.absolutePath)
        version.value++
        job = scope.launch(Dispatchers.IO) {
            val req = LiveLogic.stream(p, c, sync.userAgent())
            var error: String? = null
            var refused = false
            try {
            file.outputStream().buffered().use { out ->
                while (isActive && System.currentTimeMillis() < r.endAt && !refused) {
                    try {
                        val call = http.newCall(Request.Builder().url(req.url).apply {
                            req.userAgent?.let { header("User-Agent", it) }
                            req.referrer?.let { header("Referer", it) }
                        }.build())
                        call.execute().use { resp ->
                            if (resp.code in 400..499) { refused = true; error = "Le fournisseur refuse la connexion (HTTP ${resp.code})"; return@use }
                            if (!resp.isSuccessful) { error = "Flux indisponible (HTTP ${resp.code})"; return@use }
                            val input = resp.body?.byteStream() ?: return@use
                            val buf = ByteArray(64 * 1024)
                            while (isActive && System.currentTimeMillis() < r.endAt) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    } catch (e: java.io.IOException) {
                        error = "Coupure du flux"
                    }
                    if (isActive && !refused && System.currentTimeMillis() < r.endAt) delay(3_000)
                }
            }
            } finally {
                // annulé ou terminé : l'état est toujours écrit, même si la coroutine a été annulée
                val cancelled = !isActive
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val bytes = file.length()
                    db.updateRecording(
                        r.id,
                        when {
                            cancelled -> RecordingStatus.CANCELLED
                            bytes > 0 -> RecordingStatus.COMPLETED
                            else -> RecordingStatus.FAILED
                        },
                        bytes = bytes, error = if (cancelled) "Annulé" else if (bytes > 0) null else error ?: "Rien n'a été reçu",
                    )
                    _active.value = null
                    version.value++
                }
            }
        }
    }
}
