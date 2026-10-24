package com.sokoul.iptv.recording

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sokoul.iptv.MainActivity
import com.sokoul.iptv.R
import com.sokoul.iptv.SokoulApp
import com.sokoul.iptv.data.db.AppDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var db: AppDatabase

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RecordingScheduler.ACTION_RECORD -> {
                val id = intent.getLongExtra(RecordingService.EXTRA_ID, 0)
                ContextCompat.startForegroundService(
                    context, Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START).putExtra(RecordingService.EXTRA_ID, id)
                )
            }
            RecordingScheduler.ACTION_REMINDER -> {
                val pid = intent.getLongExtra(RecordingScheduler.EXTRA_PLAYLIST, 0)
                val cid = intent.getStringExtra(RecordingScheduler.EXTRA_CHANNEL) ?: return
                val start = intent.getLongExtra(RecordingScheduler.EXTRA_START, 0)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val r = db.recordings().upcomingReminders(start - 1).firstOrNull { it.playlistId == pid && it.channelId == cid && it.start == start }
                        if (r != null) notifyReminder(context, pid, cid, r.channelName, r.title, start)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun notifyReminder(context: Context, pid: Long, cid: String, channel: String, title: String, start: Long) {
        val open = PendingIntent.getActivity(
            context, (pid.toString() + cid + start).hashCode(),
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_PLAY_PLAYLIST, pid)
                .putExtra(MainActivity.EXTRA_PLAY_CHANNEL, cid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, SokoulApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Dans 1 minute sur $channel")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Regarder", open)
            .build()
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (allowed) runCatching { NotificationManagerCompat.from(context).notify((cid + start).hashCode(), n) }
    }
}

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: RecordingScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) scheduler.restoreAll()
    }
}
