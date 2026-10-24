package com.sokoul.iptv.ui

import com.sokoul.iptv.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Contrôle parental : les groupes verrouillés restent accessibles jusqu'à la fermeture de l'app une fois le code saisi. */
@Singleton
class ParentalGate @Inject constructor(private val settings: SettingsStore) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    suspend fun hasPin(): Boolean = settings.current().parentalPin.length == 4

    suspend fun tryUnlock(pin: String): Boolean {
        val ok = pin == settings.current().parentalPin
        if (ok) _unlocked.value = true
        return ok
    }

    fun lock() { _unlocked.value = false }
}

/** Chaînes choisies pour le multi-écran (4 au maximum). */
@Singleton
class MultiviewSession @Inject constructor() {
    data class Slot(val playlistId: Long, val channelId: String)

    private val _slots = MutableStateFlow<List<Slot?>>(List(4) { null })
    val slots: StateFlow<List<Slot?>> = _slots.asStateFlow()

    fun add(playlistId: Long, channelId: String) = _slots.update { list ->
        if (list.any { it?.channelId == channelId && it.playlistId == playlistId }) return@update list
        val i = list.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 3
        list.toMutableList().also { it[i] = Slot(playlistId, channelId) }
    }

    fun set(index: Int, slot: Slot?) = _slots.update { it.toMutableList().also { l -> l[index] = slot } }
}
