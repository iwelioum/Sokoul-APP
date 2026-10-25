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

/**
 * Écran en cours de chargement (page d'accueil, saga…) : l'indexation du catalogue attend la fin du chargement
 * avant de lancer de nouvelles requêtes, pour laisser la base et le processeur à ce que l'utilisateur regarde.
 */
object ScreenLoading {
    private val loads = MutableStateFlow(0)

    suspend fun <T> track(block: suspend () -> T): T {
        loads.update { it + 1 }
        try { return block() } finally { loads.update { (it - 1).coerceAtLeast(0) } }
    }

    suspend fun awaitIdle() { loads.map { it == 0 }.first { it } }
}
