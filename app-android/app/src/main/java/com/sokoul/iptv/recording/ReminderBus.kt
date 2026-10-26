package com.sokoul.iptv.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rappels arrivés pendant que l'app est ouverte : affichés en bandeau dans l'app (les notifications sont
 * peu visibles sur Google TV). Le récepteur publie ; l'interface affiche puis efface.
 */
object ReminderBus {
    data class Reminder(val playlistId: Long, val channelId: String, val channel: String, val title: String, val start: Long)

    private val _current = MutableStateFlow<Reminder?>(null)
    val current: StateFlow<Reminder?> = _current

    fun post(r: Reminder) { _current.value = r }
    fun dismiss(r: Reminder) { _current.compareAndSet(r, null) }
}
