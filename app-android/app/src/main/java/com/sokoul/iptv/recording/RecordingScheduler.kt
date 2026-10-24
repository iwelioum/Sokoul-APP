package com.sokoul.iptv.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.RecordingEntity
import com.sokoul.iptv.data.db.RecordingStatus
import com.sokoul.iptv.data.db.ReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'une demande d'enregistrement, avec le message à afficher. */
data class ScheduleOutcome(val id: Long?, val message: String) {
    val ok get() = id != null
}

@Singleton
class RecordingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    /** Évite que deux demandes simultanées passent toutes les deux le contrôle de conflit. */
    private val mutex = Mutex()

    fun recordings() = db.recordings().observeAll()
    fun reminders(pid: Long) = db.recordings().observeRemindersFor(pid)

    /** Enregistrement immédiat ou programmé, refusé s'il est incohérent ou en conflit. */
    suspend fun schedule(pid: Long, channel: ChannelEntity, title: String, start: Long, end: Long): ScheduleOutcome = mutex.withLock {
        val now = System.currentTimeMillis()
        val playlist = db.playlists().get(pid) ?: return@withLock ScheduleOutcome(null, "Playlist introuvable")
        val effectiveStart = maxOf(start, now)
        when (val v = RecordingRules.check(channel.id, effectiveStart, end, db.recordings().forPlaylist(pid), playlist.maxConnections, now)) {
            is RecordingRules.Verdict.Refused -> ScheduleOutcome(null, v.reason)
            is RecordingRules.Verdict.Ok -> {
                val id = db.recordings().insert(
                    RecordingEntity(
                        playlistId = pid, channelId = channel.id, channelName = channel.name, title = title.take(200),
                        startAt = effectiveStart, endAt = end, status = RecordingStatus.SCHEDULED,
                    )
                )
                arm(id, effectiveStart)
                val base = if (effectiveStart <= now + 2_000) "Enregistrement démarré" else "Enregistrement programmé"
                ScheduleOutcome(id, listOfNotNull(base, v.warning).joinToString(" — "))
            }
        }
    }

    suspend fun cancel(id: Long) {
        val r = db.recordings().get(id) ?: return
        alarms.cancel(pending(id))
        if (r.status == RecordingStatus.RECORDING) {
            context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP).putExtra(RecordingService.EXTRA_ID, id))
        } else if (r.status == RecordingStatus.SCHEDULED) {
            db.recordings().update(r.copy(status = RecordingStatus.CANCELLED))
        }
    }

    suspend fun delete(id: Long) {
        val r = db.recordings().get(id) ?: return
        if (r.status == RecordingStatus.RECORDING || r.status == RecordingStatus.SCHEDULED) cancel(id)
        r.filePath?.let { java.io.File(it).delete() }
        db.recordings().delete(r)
    }

    /**
     * Suppression d'une playlist : annule les enregistrements à venir ou en cours et les rappels.
     * Les enregistrements terminés (fichiers) sont conservés.
     */
    suspend fun cancelForPlaylist(pid: Long) {
        for (r in db.recordings().forPlaylist(pid)) {
            if (r.status == RecordingStatus.SCHEDULED || r.status == RecordingStatus.RECORDING) cancel(r.id)
        }
        for (rem in db.recordings().remindersFor(pid)) {
            alarms.cancel(reminderPending(rem.playlistId, rem.channelId, rem.start))
            db.recordings().deleteReminder(rem.playlistId, rem.channelId, rem.start)
        }
    }

    suspend fun addReminder(pid: Long, channel: ChannelEntity, start: Long, title: String): String {
        if (start - 60_000 <= System.currentTimeMillis()) return "Ce programme commence dans moins d'une minute"
        db.recordings().upsertReminder(ReminderEntity(pid, channel.id, channel.name, start, title.take(200)))
        armReminder(pid, channel.id, start)
        return "Rappel programmé"
    }

    suspend fun removeReminder(pid: Long, channelId: String, start: Long) {
        db.recordings().deleteReminder(pid, channelId, start)
        alarms.cancel(reminderPending(pid, channelId, start))
    }

    private fun arm(id: Long, start: Long) {
        if (start <= System.currentTimeMillis() + 2_000) {
            ContextCompat.startForegroundService(
                context, Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START).putExtra(RecordingService.EXTRA_ID, id)
            )
        } else {
            setExact(start, pending(id))
        }
    }

    private fun armReminder(pid: Long, channelId: String, start: Long) {
        val at = start - 60_000
        if (at > System.currentTimeMillis()) setExact(at, reminderPending(pid, channelId, start))
    }

    private fun setExact(at: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (canExact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pending(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, RecordingRules.recordRequestCode(id),
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_RECORD).putExtra(RecordingService.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun reminderPending(pid: Long, channelId: String, start: Long): PendingIntent = PendingIntent.getBroadcast(
        context, RecordingRules.reminderRequestCode(pid, channelId, start),
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_REMINDER)
            .putExtra(EXTRA_PLAYLIST, pid).putExtra(EXTRA_CHANNEL, channelId).putExtra(EXTRA_START, start),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Après un redémarrage de l'appareil ou de l'application : réarme les alarmes. */
    fun restoreAll() {
        scope.launch {
            val now = System.currentTimeMillis()
            for (r in db.recordings().active()) {
                // le processus a été tué pendant l'enregistrement : on reprend si c'est encore l'heure
                if (r.endAt > now) arm(r.id, now)
                else db.recordings().update(r.copy(status = if (r.sizeBytes > 0) RecordingStatus.COMPLETED else RecordingStatus.FAILED, error = r.error ?: "Interrompu (appareil ou application arrêtés)"))
            }
            for (r in db.recordings().scheduled()) {
                if (r.endAt <= now) db.recordings().update(r.copy(status = RecordingStatus.FAILED, error = "Appareil éteint à l'heure prévue"))
                else arm(r.id, r.startAt)
            }
            for (rem in db.recordings().upcomingReminders(now)) armReminder(rem.playlistId, rem.channelId, rem.start)
        }
    }

    companion object {
        const val ACTION_RECORD = "com.sokoul.iptv.RECORD"
        const val ACTION_REMINDER = "com.sokoul.iptv.REMINDER"
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_START = "start"
    }
}
