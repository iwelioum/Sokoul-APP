package com.sokoul.iptv.ui.screens.live

import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.recording.RecordingScheduler
import com.sokoul.iptv.ui.MultiviewSession
import com.sokoul.iptv.ui.ParentalGate
import javax.inject.Inject
import javax.inject.Singleton

/** Actions communes sur les chaînes et programmes (écran Direct, Guide, lecteur). */
@Singleton
class ChannelActions @Inject constructor(
    private val library: LibraryRepository,
    private val recordings: RecordingScheduler,
    private val catalog: CatalogRepository,
    val multiview: MultiviewSession,
    val parental: ParentalGate,
) {
    suspend fun toggleFavorite(pid: Long, c: ChannelEntity) = library.toggleFavorite(pid, ContentType.LIVE, c.id)

    suspend fun recordNow(pid: Long, c: ChannelEntity, minutes: Int, title: String? = null) {
        val now = System.currentTimeMillis()
        recordings.schedule(pid, c, title ?: c.name, now, now + minutes * 60_000L)
    }

    suspend fun recordProgram(pid: Long, c: ChannelEntity, p: ProgramEntity) {
        // marge de 2 min avant / 5 min après : les horaires EPG sont rarement exacts
        recordings.schedule(pid, c, p.title, maxOf(p.start - 120_000, System.currentTimeMillis()), p.end + 300_000)
    }

    suspend fun remind(pid: Long, c: ChannelEntity, p: ProgramEntity) = recordings.addReminder(pid, c, p.start, p.title)

    suspend fun setCategoryHidden(pid: Long, catId: String, hidden: Boolean) =
        catalog.setCategoryPref(pid, ContentType.LIVE, catId, hidden = hidden)

    suspend fun setCategoryLocked(pid: Long, type: ContentType, catId: String, locked: Boolean) =
        catalog.setCategoryPref(pid, type, catId, locked = locked)
}
