package com.sokoul.iptv.ui.screens.tv

import com.sokoul.iptv.data.db.ChannelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Délai sans nouvelle touche avant d'ouvrir le flux de la chaîne affichée sur le bandeau. */
const val ZAP_SETTLE_MS = 600L

/** Saisie au pavé numérique : délai après le dernier chiffre avant de zapper. */
const val NUMBER_SETTLE_MS = 1_200L

/**
 * Zapping d'une vraie box : chaque appui affiche aussitôt la chaîne visée sur le bandeau, mais le flux
 * n'est ouvert qu'après [settleMs] sans nouvel appui. Cinq appuis rapides = un seul flux ouvert.
 * Le flux en cours est fermé dès le premier appui : l'abonnement n'accepte souvent qu'une connexion.
 */
class Zapper(
    private val scope: CoroutineScope,
    private val settleMs: Long = ZAP_SETTLE_MS,
    /** Premier appui d'une série : fermer le flux en cours. */
    private val onStart: () -> Unit,
    /** Fin de la série : ouvrir la chaîne choisie. */
    private val onCommit: (ChannelEntity) -> Unit,
) {
    private val _pending = MutableStateFlow<ChannelEntity?>(null)
    /** Chaîne affichée sur le bandeau, pas encore ouverte. */
    val pending: StateFlow<ChannelEntity?> = _pending.asStateFlow()
    private var job: Job? = null

    /** ▲▼ / CH+ CH− : [delta] places dans [list] à partir de la chaîne visée (ou de [current]). */
    fun step(list: List<ChannelEntity>, current: ChannelEntity?, delta: Int) {
        if (list.isEmpty()) return
        val from = _pending.value ?: current
        val i = from?.let { f -> list.indexOfFirst { it.id == f.id } } ?: -1
        select(list[((if (i < 0) 0 else i) + delta).mod(list.size)])
    }

    /** Chaîne choisie directement (pavé numérique, chaîne précédente) : même délai, même bandeau. */
    fun select(c: ChannelEntity) {
        if (_pending.value == null) onStart()
        _pending.value = c
        job?.cancel()
        job = scope.launch {
            delay(settleMs)
            _pending.value = null
            onCommit(c)
        }
    }

    /** Abandon (Retour pendant le zapping) : rien n'est ouvert. */
    fun cancel(): Boolean {
        val had = _pending.value != null
        job?.cancel()
        _pending.value = null
        return had
    }
}

/** Sens du zapping : par défaut ▲ = chaîne précédente dans la liste ; inversé sur demande. */
fun zapDelta(up: Boolean, inverted: Boolean): Int = if (up != inverted) -1 else 1
