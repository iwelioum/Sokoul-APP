package com.sokoul.iptv.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sokoul.iptv.MainActivity
import com.sokoul.iptv.R
import com.sokoul.iptv.SokoulApp
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.HomeFreshness
import com.sokoul.iptv.data.repo.ProfileRepository
import com.sokoul.iptv.data.repo.cleanDisplayTitle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Après une synchronisation : une notification « Nouvel épisode » pour les séries suivies qui ont reçu du nouveau depuis
 * le dernier visionnage (même règle que la rangée de l'accueil). Chaque mise à jour n'est signalée qu'une fois.
 */
@Singleton
class NewEpisodeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
) {
    private val prefs by lazy { context.getSharedPreferences("new_episodes", Context.MODE_PRIVATE) }

    suspend fun check(pid: Long) {
        if (profiles.isKids()) return
        val watched = db.browse().lastWatched(pid, 300)
            .filter { it.type == ContentType.SERIES && it.parentId != null }
            .groupBy { it.parentId!! }.mapValues { (_, e) -> e.maxOf { it.updatedAt } }
        if (watched.isEmpty()) return
        val series = watched.keys.toList().chunked(500).flatMap { db.browse().seriesByIds(pid, it) }
        val fresh = HomeFreshness.newEpisodes(series, { it.id }, { it.lastModified }, watched)
        val already = prefs.getStringSet(KEY, emptySet()).orEmpty()
        val todo = Rules.toNotify(fresh.map { it.id to it.lastModified }, already)
        if (todo.isEmpty()) return
        val byId = fresh.associateBy { it.id }
        val titles = todo.mapNotNull { (id, _) -> byId[id]?.let { cleanDisplayTitle(it.title, it.year) } }.distinct()
        notify(pid, titles, todo.first().first)
        prefs.edit().putStringSet(KEY, Rules.remember(already, todo)).apply()
    }

    private fun notify(pid: Long, titles: List<String>, firstSeries: String) {
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!allowed || titles.isEmpty()) return
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_SERIES_PLAYLIST, pid).putExtra(MainActivity.EXTRA_OPEN_SERIES, firstSeries),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, SokoulApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(Rules.title(titles))
            .setContentText(Rules.text(titles))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    /** Règles pures (testées). */
    object Rules {
        /** Séries à signaler : mise à jour (série + date) jamais signalée. */
        fun toNotify(fresh: List<Pair<String, Long>>, already: Set<String>): List<Pair<String, Long>> =
            fresh.filter { (id, modified) -> "$id@$modified" !in already }

        /** Mises à jour signalées, les 200 dernières gardées. */
        fun remember(already: Set<String>, done: List<Pair<String, Long>>): Set<String> =
            (already + done.map { (id, m) -> "$id@$m" }).toList().takeLast(200).toSet()

        fun title(titles: List<String>) = if (titles.size == 1) "Nouvel épisode : ${titles[0]}" else "Nouveaux épisodes dans ${titles.size} séries"

        fun text(titles: List<String>) = if (titles.size == 1) "Disponible dans votre abonnement" else titles.take(4).joinToString(", ") + if (titles.size > 4) "…" else ""
    }

    private companion object {
        const val KEY = "notified"
        const val NOTIFICATION_ID = 7_301
    }
}
