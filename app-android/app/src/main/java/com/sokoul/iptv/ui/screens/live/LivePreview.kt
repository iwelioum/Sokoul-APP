package com.sokoul.iptv.ui.screens.live

import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.repo.StreamResolver
import com.sokoul.iptv.player.PlayerFactory
import com.sokoul.iptv.player.ProviderGate
import com.sokoul.iptv.player.StreamPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Quand l'aperçu de l'écran Direct peut ouvrir un flux (logique pure, testée). */
object LivePreviewPolicy {
    /** Le focus doit rester ce temps sur une chaîne : faire défiler la liste n'ouvre aucun flux. */
    const val DEBOUNCE_MS = 800L

    /**
     * @param enabled réglage « Aperçu dans l'écran Direct »
     * @param visible écran Direct affiché sur TV (pas en arrière-plan, pas au doigt)
     * @param recording un enregistrement tient la connexion du fournisseur
     * @param cooldownMs pause imposée après un refus du fournisseur
     */
    fun canPlay(enabled: Boolean, visible: Boolean, recording: Boolean, cooldownMs: Long): Boolean =
        enabled && visible && !recording && cooldownMs <= 0
}

/**
 * Aperçu de la chaîne sélectionnée : un seul lecteur exclusif (une connexion à la fois), son coupé, 720p au plus.
 * [stop] ferme le flux et libère le décodeur : appelé avant le plein écran et quand l'écran n'est plus visible.
 */
class LivePreview(
    private val factory: PlayerFactory,
    private val gate: ProviderGate,
    private val resolver: StreamResolver,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    data class Current(val channel: ChannelEntity, val player: StreamPlayer)

    private val _current = MutableStateFlow<Current?>(null)
    val current: StateFlow<Current?> = _current

    fun busy(): Boolean = gate.recording.value != null || gate.remainingMs() > 0

    suspend fun show(p: PlaylistEntity, c: ChannelEntity) {
        if (_current.value?.channel?.id == c.id) return
        val stream = runCatching { resolver.live(p, c) }.getOrNull() ?: return stop()
        val sp = _current.value?.player?.also { it.stop() }
            ?: StreamPlayer(factory.create(settings.current(), forMultiview = true), factory, scope, gate, exclusive = true)
        sp.player.volume = 0f
        _current.value = Current(c, sp)
        sp.play(stream, c.name)
    }

    fun stop() {
        val cur = _current.value ?: return
        _current.value = null
        cur.player.stop()
        cur.player.release()
    }
}
