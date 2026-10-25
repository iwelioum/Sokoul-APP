package com.sokoul.iptv.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Lecture en cours (VOD, TV, multi-écran) : les tâches de fond lourdes (indexation du catalogue)
 * se mettent en pause tant qu'un lecteur existe, pour laisser le réseau et le processeur à la vidéo.
 */
object PlaybackActivity {
    private val players = MutableStateFlow(0)
    val active: StateFlow<Int> = players

    fun started() = players.update { it + 1 }
    fun ended() = players.update { (it - 1).coerceAtLeast(0) }

    val isPlaying get() = players.value > 0

    /** Attend qu'aucun lecteur ne tourne. */
    suspend fun awaitIdle() { players.map { it == 0 }.first { it } }
}
