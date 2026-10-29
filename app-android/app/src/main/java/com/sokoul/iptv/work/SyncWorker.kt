package com.sokoul.iptv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.repo.SyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Rafraîchissement en arrière-plan.
 * - mode "periodic" : met à jour les playlists et guides arrivés à échéance ;
 * - mode "playlist"/"epg" : synchronisation immédiate demandée par l'utilisateur.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncEngine,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val channelDb: com.sokoul.iptv.data.net.providers.ChannelDbClient,
    private val newEpisodes: NewEpisodeNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: MODE_PERIODIC
        val id = inputData.getLong(KEY_PLAYLIST, 0)
        return try {
            when (mode) {
                MODE_PLAYLIST -> {
                    sync.syncPlaylist(id)
                    notifyNew(id)
                    if (inputData.getBoolean(KEY_WITH_EPG, false)) runCatching { sync.syncEpg(id) }
                    fillLogos(id)
                    CatalogIndexWorker.start(applicationContext)
                }
                MODE_EPG -> { sync.syncEpg(id); fillLogos(id) }
                else -> periodic()
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Synchronisation $mode en échec")
            if (mode == MODE_PERIODIC && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    /** Séries suivies avec du nouveau : notification (une fois par mise à jour) ; jamais bloquant. */
    private suspend fun notifyNew(pid: Long) {
        runCatching { newEpisodes.check(pid) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else Timber.w(it, "Nouveaux épisodes") }
    }

    /** Logos de secours (guide TV, puis iptv-org) pour les chaînes livrées sans logo ; jamais bloquant. */
    private suspend fun fillLogos(pid: Long) {
        runCatching { channelDb.fillMissingLogos(pid) }
            .onSuccess { if (it > 0) Timber.i("%d logos de chaînes ajoutés", it) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    private suspend fun periodic() {
        val s = settings.current()
        val now = System.currentTimeMillis()
        db.programs().deleteOlderThan(now - 8 * 24 * 3600_000L)
        db.tmdb().purge(now - 14 * 24 * 3600_000L)
        for (p in db.playlists().getAll()) {
            if (now - p.lastSync > s.playlistRefreshHours * 3600_000L) runCatching { sync.syncPlaylist(p.id) }.onSuccess { notifyNew(p.id) }
            if (now - p.lastEpgSync > s.epgRefreshHours * 3600_000L) runCatching { sync.syncEpg(p.id) }
            fillLogos(p.id)
        }
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_PLAYLIST = "playlist"
        private const val KEY_WITH_EPG = "with_epg"
        private const val MODE_PERIODIC = "periodic"
        private const val MODE_PLAYLIST = "playlist"
        private const val MODE_EPG = "epg"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(network)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("periodic-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun syncNow(context: Context, playlistId: Long, withEpg: Boolean = true) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_MODE to MODE_PLAYLIST, KEY_PLAYLIST to playlistId, KEY_WITH_EPG to withEpg))
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-$playlistId", ExistingWorkPolicy.KEEP, req)
        }

        /** Annule les synchronisations en attente ou en cours d'une playlist (suppression). */
        fun cancelFor(context: Context, playlistId: Long) {
            runCatching {
                val wm = WorkManager.getInstance(context)
                wm.cancelUniqueWork("sync-$playlistId")
                wm.cancelUniqueWork("epg-$playlistId")
            }
        }

        fun epgNow(context: Context, playlistId: Long) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_MODE to MODE_EPG, KEY_PLAYLIST to playlistId))
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("epg-$playlistId", ExistingWorkPolicy.KEEP, req)
        }
    }
}
