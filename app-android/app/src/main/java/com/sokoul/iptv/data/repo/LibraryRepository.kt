package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.FavoriteEntity
import com.sokoul.iptv.data.db.ProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(private val db: AppDatabase) {

    fun isFavorite(pid: Long, type: ContentType, id: String): Flow<Boolean> = db.library().observeIsFavorite(pid, type, id)
    fun favoriteIds(pid: Long, type: ContentType): Flow<List<String>> = db.library().observeFavoriteIds(pid, type)

    suspend fun toggleFavorite(pid: Long, type: ContentType, id: String): Boolean {
        val fav = db.library().observeIsFavorite(pid, type, id).first()
        if (fav) db.library().removeFavorite(pid, type, id) else db.library().addFavorite(FavoriteEntity(pid, type, id))
        return !fav
    }

    fun continueWatching(pid: Long): Flow<List<ProgressEntity>> = db.library().observeContinueWatching(pid)
    fun seriesProgress(pid: Long, sid: String): Flow<List<ProgressEntity>> = db.library().observeSeriesProgress(pid, sid)

    suspend fun progress(pid: Long, type: ContentType, id: String) = db.library().progress(pid, type, id)
    suspend fun lastEpisode(pid: Long, sid: String) = db.library().lastEpisodeProgress(pid, sid)

    suspend fun saveProgress(
        pid: Long, type: ContentType, id: String, parentId: String?, title: String, subtitle: String?, image: String?,
        positionMs: Long, durationMs: Long,
    ) {
        // « vu » au-delà de 92 % ou à moins de 3 minutes de la fin (génériques)
        val finished = durationMs > 0 && (positionMs >= durationMs * 0.92 || durationMs - positionMs < 180_000)
        db.library().saveProgress(
            ProgressEntity(pid, type, id, parentId, title, subtitle, image, positionMs, durationMs, System.currentTimeMillis(), finished)
        )
    }

    suspend fun markChannelWatched(pid: Long, channelId: String, name: String, logo: String?) {
        db.library().saveProgress(ProgressEntity(pid, ContentType.LIVE, channelId, null, name, null, logo, 0, 0, System.currentTimeMillis(), false))
    }

    suspend fun clearProgress(pid: Long, type: ContentType, id: String) = db.library().deleteProgress(pid, type, id)

    suspend fun setWatched(pid: Long, type: ContentType, id: String, parentId: String?, title: String, watched: Boolean) {
        if (watched) {
            db.library().saveProgress(ProgressEntity(pid, type, id, parentId, title, null, null, 1, 1, System.currentTimeMillis(), true))
        } else db.library().deleteProgress(pid, type, id)
    }
}
