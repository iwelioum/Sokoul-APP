package com.sokoul.iptv.player.timeshift

import com.sokoul.iptv.recording.StreamRefusedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

/** Règles de la pause du direct (pures, testées). */
object TimeshiftPolicy {
    const val MIN_FREE_BYTES = 3_000L * 1024 * 1024
    const val MAX_BYTES = 2_500L * 1024 * 1024
    const val MAX_MS = 30 * 60_000L
    /** Marge derrière l'écriture : le lecteur ne colle jamais au dernier octet écrit. */
    const val LIVE_MARGIN_MS = 3_000L
    /** En deçà de ce retard, on considère être « en direct ». */
    const val AT_LIVE_MS = 8_000L

    /** Appareil modeste (Fire TV Stick : 8 Go, souvent moins de 2 Go libres) : tampon plus court mais disponible. */
    const val LOW_MIN_FREE_BYTES = 1_200L * 1024 * 1024
    const val LOW_MAX_BYTES = 1_024L * 1024 * 1024

    fun canStart(freeBytes: Long, low: Boolean = false) = freeBytes >= if (low) LOW_MIN_FREE_BYTES else MIN_FREE_BYTES

    /** Plafond sur disque : 2,5 Go (1 Go sur appareil modeste), et toujours 1 Go (512 Mo) laissé libre à Android. */
    fun capBytes(freeBytes: Long, low: Boolean = false): Long {
        val reserve = if (low) 512L * 1024 * 1024 else 1_024L * 1024 * 1024
        return minOf(if (low) LOW_MAX_BYTES else MAX_BYTES, (freeBytes - reserve).coerceAtLeast(64L * 1024 * 1024))
    }

    fun clampSeek(target: Long, start: Long, end: Long) = target.coerceIn(start, maxOf(start, end - LIVE_MARGIN_MS))

    fun isAtLive(position: Long, end: Long) = end - position < AT_LIVE_MS
}

/**
 * Pause du direct : un écrivain tient l'unique connexion au fournisseur et remplit le [TimeshiftBuffer] ;
 * le lecteur lit le tampon. Cette classe tient l'écrivain (reconnexion, refus, fin) et l'horloge du tampon ;
 * l'écran du lecteur s'occupe d'ExoPlayer.
 */
class LiveTimeshift(
    private val scope: CoroutineScope,
    private val newBuffer: () -> TimeshiftBuffer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * @param start/end fenêtre disponible (heures d'écriture) ; [position] = heure du contenu affiché ;
     * [error] = l'écriture s'est arrêtée (refus, disque, coupures répétées).
     */
    data class State(
        val active: Boolean = false, val paused: Boolean = false,
        val start: Long = 0, val end: Long = 0, val position: Long = 0, val error: String? = null,
    ) {
        val behindMs get() = (end - position).coerceAtLeast(0)
        val atLive get() = TimeshiftPolicy.isAtLive(position, end)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    var buffer: TimeshiftBuffer? = null
        private set
    private var writer: Job? = null
    /** Heure du contenu au début de la source locale en cours (position du lecteur = 0). */
    private var sourceStart = 0L

    val active get() = buffer != null

    /**
     * Démarre l'écriture avec [feed] (lit le flux, envoie les octets, s'arrête quand on le lui dit). Reconnexion
     * après une coupure (au plus [MAX_FAILURES]) ; un refus (4xx) arrête tout, sans nouvel essai.
     */
    fun start(onRefused: (Int) -> Unit, feed: suspend (keepGoing: () -> Boolean, sink: (ByteArray, Int) -> Unit) -> Unit) {
        stop()
        val buf = newBuffer()
        buffer = buf
        val now = clock()
        sourceStart = now
        _state.value = State(active = true, paused = true, start = now, end = now, position = now)
        writer = scope.launch(io) {
            var failures = 0
            var error: String? = null
            try {
                while (isActive) {
                    try {
                        feed({ isActive }) { b, n -> buf.append(b, n); failures = 0 }
                    } catch (e: StreamRefusedException) {
                        onRefused(e.code)
                        error = "Le fournisseur a coupé le flux (${e.code})"
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        failures++
                        Timber.w(e, "Pause du direct : écriture interrompue ($failures)")
                        if (failures > MAX_FAILURES) { error = "Flux coupé : pause du direct arrêtée"; break }
                        delay(minOf(1_000L * failures, 8_000L))
                    } catch (e: IllegalStateException) {
                        break // tampon fermé
                    }
                }
            } finally {
                buf.finish()
                if (error != null) _state.update { it.copy(error = error) }
            }
        }
    }

    /** Arrête l'écriture et efface le tampon (changement de chaîne, retour au flux réseau, sortie). */
    fun stop() {
        writer?.cancel(); writer = null
        buffer?.clear(); buffer = null
        _state.value = State()
    }

    /** Position du tampon (octets) et heure du contenu pour lire à partir de l'heure [time]. */
    fun sourceAt(time: Long): Long? {
        val buf = buffer ?: return null
        val t = TimeshiftPolicy.clampSeek(time, buf.startTime, buf.endTime)
        val offset = buf.offsetAt(t)
        sourceStart = buf.timeAt(offset)
        return offset
    }

    /** Heure du contenu affiché, d'après la position du lecteur dans la source locale. */
    fun positionOf(playerPositionMs: Long) = sourceStart + playerPositionMs.coerceAtLeast(0)

    /** Met à jour la fenêtre et la position (appelé deux fois par seconde par l'écran). */
    fun tick(playerPositionMs: Long?, paused: Boolean) {
        val buf = buffer ?: return
        _state.update {
            it.copy(
                start = buf.startTime, end = buf.endTime, paused = paused,
                position = playerPositionMs?.let(::positionOf) ?: it.position,
            )
        }
    }

    /** Heure visée par un saut de [deltaMs] depuis la position affichée, bornée à la fenêtre. */
    fun seekTarget(deltaMs: Long): Long {
        val s = _state.value
        return TimeshiftPolicy.clampSeek(s.position + deltaMs, s.start, s.end)
    }

    companion object {
        const val MAX_FAILURES = 10
    }
}
