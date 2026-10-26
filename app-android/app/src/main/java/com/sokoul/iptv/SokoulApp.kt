package com.sokoul.iptv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sokoul.iptv.recording.RecordingScheduler
import com.sokoul.iptv.work.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltAndroidApp
class SokoulApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var okHttp: OkHttpClient
    @Inject lateinit var recordingScheduler: RecordingScheduler
    @Inject lateinit var parentalGate: com.sokoul.iptv.ui.ParentalGate
    @Inject lateinit var vod: com.sokoul.iptv.data.repo.VodRepository
    @Inject lateinit var profiles: com.sokoul.iptv.data.repo.ProfileRepository
    @Inject lateinit var watchNext: com.sokoul.iptv.tv.WatchNextPublisher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        createChannels()
        profiles.start()
        watchNext.start()
        SyncWorker.schedulePeriodic(this)
        com.sokoul.iptv.work.TraktWorker.schedule(this)
        com.sokoul.iptv.work.CatalogIndexWorker.start(this)
        recordingScheduler.restoreAll()
        // Après la mise à jour de la base : calcule une fois les clés de regroupement des doublons.
        com.sokoul.iptv.data.AppScope.launch {
            kotlinx.coroutines.delay(3_000)
            runCatching { vod.regroupIfNeeded() }.onFailure { Timber.w(it, "Regroupement initial") }
        }
        // Contrôle parental : l'app passée en arrière-plan (touche Accueil, veille) se reverrouille.
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) = parentalGate.lock()
        })
    }

    /** Coil partage le client OkHttp (et son pool de connexions) ; cache disque pour les logos. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttp)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RECORDINGS, getString(R.string.channel_recordings), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH))
    }

    companion object {
        const val CHANNEL_RECORDINGS = "recordings"
        const val CHANNEL_REMINDERS = "reminders"
    }
}
