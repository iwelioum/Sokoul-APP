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

    /** Renvoie le message à afficher (succès, avertissement ou refus). */
    suspend fun recordNow(pid: Long, c: ChannelEntity, minutes: Int, title: String? = null): String {
        val now = System.currentTimeMillis()
        return recordings.schedule(pid, c, title ?: c.name, now, now + minutes * 60_000L).message
    }

    suspend fun recordProgram(pid: Long, c: ChannelEntity, p: ProgramEntity): String =
        // marge de 2 min avant / 5 min après : les horaires EPG sont rarement exacts
        recordings.schedule(pid, c, p.title, maxOf(p.start - 120_000, System.currentTimeMillis()), p.end + 300_000).message

    suspend fun recordSeries(pid: Long, c: ChannelEntity, p: ProgramEntity): String =
        recordings.addSeriesRule(pid, c, p.title).message

    suspend fun scheduleSeriesOccurrence(pid: Long, c: ChannelEntity, p: ProgramEntity) =
        recordings.scheduleSeriesOccurrence(pid, c, p)

    suspend fun remind(pid: Long, c: ChannelEntity, p: ProgramEntity): String = recordings.addReminder(pid, c, p.start, p.title)

    /** Ajout au multi-écran ; renvoie un message si la chaîne n'a pas pu être ajoutée. */
    fun addToMultiview(pid: Long, c: ChannelEntity): String? = when (multiview.add(pid, c.id)) {
        MultiviewSession.AddResult.ADDED -> null
        MultiviewSession.AddResult.ALREADY_PRESENT -> "« ${c.name} » est déjà dans le multi-écran"
        MultiviewSession.AddResult.FULL -> "Multi-écran complet : choisissez une case à remplacer"
    }

    /** Un groupe verrouillé ne s'ouvre pas sans code (y compris quand il est restauré au démarrage). */
    suspend fun canOpenCategory(pid: Long, type: ContentType, catId: String): Boolean =
        !catalog.isCategoryLocked(pid, type, catId) || parental.canAccessLocked()

    /**
     * Garde le groupe affiché cohérent avec le contrôle parental : groupe verrouillé restauré
     * ou re-verrouillage pendant l'affichage → retour sur « Tout ».
     */
    suspend fun guardSelection(pid: Long, type: ContentType, selected: kotlinx.coroutines.flow.MutableStateFlow<String>) {
        kotlinx.coroutines.flow.combine(selected, parental.unlocked, lockChanges) { cat, _, _ -> cat }.collect { cat ->
            if (!canOpenCategory(pid, type, cat)) selected.value = com.sokoul.iptv.data.repo.SpecialCategory.ALL
        }
    }

    /** Incrémenté à chaque (dé)verrouillage : le groupe affiché est re-vérifié aussitôt. */
    private val lockChanges = kotlinx.coroutines.flow.MutableStateFlow(0)

    suspend fun setCategoryHidden(pid: Long, catId: String, hidden: Boolean) =
        catalog.setCategoryPref(pid, ContentType.LIVE, catId, hidden = hidden)

    suspend fun setCategoryLocked(pid: Long, type: ContentType, catId: String, locked: Boolean) {
        catalog.setCategoryPref(pid, type, catId, locked = locked)
        lockChanges.value++
    }
}
