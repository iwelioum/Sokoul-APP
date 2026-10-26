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
        if (fav) db.library().removeFavorite(pid, type, id) else db.library().addFavorite(FavoriteEntity(pid, type, id, profileId = db.currentProfileId()))
        return !fav
    }

    fun continueWatching(pid: Long): Flow<List<ProgressEntity>> = db.library().observeContinueWatching(pid)
    fun seriesProgress(pid: Long, sid: String): Flow<List<ProgressEntity>> = db.library().observeSeriesProgress(pid, sid)

    suspend fun progress(pid: Long, type: ContentType, id: String) = db.library().progress(pid, type, id)
    suspend fun lastEpisode(pid: Long, sid: String) = db.library().lastEpisodeProgress(pid, sid)

    suspend fun saveProgress(
        pid: Long, type: ContentType, id: String, parentId: String?, title: String, subtitle: String?, image: String?,
        positionMs: Long, durationMs: Long, season: Int? = null, episode: Int? = null,
    ) {
        if (durationMs <= 0 || positionMs < 0) return
        val finished = isFinished(positionMs, durationMs)
        db.library().saveProgress(
            ProgressEntity(pid, type, id, parentId, title, subtitle, image, positionMs, durationMs, System.currentTimeMillis(), finished, season, episode, manual = false, profileId = db.currentProfileId())
        )
    }

    suspend fun markChannelWatched(pid: Long, channelId: String, name: String, logo: String?) {
        db.library().saveProgress(ProgressEntity(pid, ContentType.LIVE, channelId, null, name, null, logo, 0, 0, System.currentTimeMillis(), false, profileId = db.currentProfileId()))
    }

    suspend fun clearProgress(pid: Long, type: ContentType, id: String) = db.library().deleteProgress(pid, type, id)

    /** Marquage manuel : ne déplace pas la reprise (voir [EpisodeResolver.primaryAction]). */
    suspend fun setWatched(pid: Long, type: ContentType, id: String, parentId: String?, title: String, watched: Boolean, season: Int? = null, episode: Int? = null) {
        if (watched) {
            db.library().saveProgress(ProgressEntity(pid, type, id, parentId, title, null, null, 1, 1, System.currentTimeMillis(), true, season, episode, manual = true, profileId = db.currentProfileId()))
        } else db.library().deleteProgress(pid, type, id)
    }

    companion object {
        /** En dessous, on ne propose pas de reprendre (début de film). */
        const val RESUME_MIN_MS = 30_000L
        /** Recul appliqué à la reprise pour retrouver le contexte. */
        const val RESUME_REWIND_MS = 5_000L

        /** « Vu » : au-delà de 92 %, ou à moins de 3 min de la fin (génériques) pour un contenu de plus de 20 min. */
        fun isFinished(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0) return false
            val remaining = durationMs - positionMs
            return positionMs >= durationMs * 0.92 || (durationMs > 20 * 60_000L && remaining < 180_000L)
        }

        /** Position de reprise (0 = depuis le début). */
        fun resumeFrom(p: ProgressEntity?): Long =
            if (p == null || p.finished || p.positionMs <= RESUME_MIN_MS) 0 else p.positionMs - RESUME_REWIND_MS
    }
}
