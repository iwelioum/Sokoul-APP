package com.sokoul.iptv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sokoul.iptv.data.repo.CatalogIndexer
import com.sokoul.iptv.data.repo.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Indexation du catalogue en tâche de fond (TMDB uniquement). Une tâche dure au plus ~8 min
 * (limite Android) ; tant que le catalogue n'est pas fini, elle se reprogramme.
 */
@HiltWorker
class CatalogIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val indexer: CatalogIndexer,
    private val playlists: PlaylistRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val p = playlists.activeNow() ?: return Result.success()
        return try {
            if (indexer.run(p.id)) Result.success() else Result.retry()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Indexation du catalogue")
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "catalog-index"

        /** Lance l'indexation si elle ne tourne pas déjà (démarrage de l'app, fin de synchronisation). */
        fun start(context: Context) {
            val req = OneTimeWorkRequestBuilder<CatalogIndexWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 60, TimeUnit.SECONDS)
                .build()
            runCatching { WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req) }
        }
    }
}
