package com.sokoul.iptv.player

import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Protège l'abonnement du fournisseur (souvent limité à une connexion, et qui bloque
 * temporairement les comptes qui insistent après un refus).
 *
 * - Après un refus HTTP (401/403/406/429…), plus aucune requête de flux n'est envoyée
 *   pendant [COOLDOWN_MS], quel que soit l'écran qui la demande.
 * - Un seul lecteur principal à la fois : en démarrer un arrête le précédent immédiatement.
 */
@Singleton
class ProviderGate @Inject constructor() {
    /** Horloge remplaçable dans les tests. */
    var clock: () -> Long = System::currentTimeMillis

    @Volatile private var refusedAt = 0L
    private var active: WeakReference<StreamPlayer>? = null

    fun noteRefusal() { refusedAt = clock() }

    /** Temps d'attente restant avant la prochaine requête autorisée (0 = libre). */
    fun remainingMs(): Long = if (refusedAt == 0L) 0 else (refusedAt + COOLDOWN_MS - clock()).coerceAtLeast(0)

    fun availableAt(): Long = clock() + remainingMs()

    @Synchronized
    fun acquire(player: StreamPlayer) {
        val previous = active?.get()
        if (previous != null && previous !== player) previous.stop()
        active = WeakReference(player)
    }

    @Synchronized
    fun release(player: StreamPlayer) {
        if (active?.get() === player) active = null
    }

    @Synchronized
    fun isActive(player: StreamPlayer) = active?.get() === player

    // --- Enregistrements : ils tiennent la connexion unique tant qu'ils tournent ----------------

    private val holds = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val _recording = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Chaîne en cours d'enregistrement (null = connexion libre). Les lecteurs IPTV s'effacent devant elle. */
    val recording: kotlinx.coroutines.flow.StateFlow<String?> = _recording

    fun holdForRecording(id: Long, channel: String) {
        holds[id] = channel
        _recording.value = channel
    }

    fun releaseRecording(id: Long) {
        holds.remove(id)
        _recording.value = holds.values.firstOrNull()
    }

    companion object {
        const val COOLDOWN_MS = 30_000L
    }
}

/** Ce que le lecteur doit faire après une erreur de lecture (logique pure, testée). */
sealed interface ErrorDecision {
    /** Essayer l'URL suivante (autre format) tout de suite : le serveur n'a pas refusé l'accès. */
    data object NextCandidate : ErrorDecision
    /** Refus du fournisseur : on attend la fin de la pause imposée par [ProviderGate]. */
    data class Refused(val message: String) : ErrorDecision
    /** Erreur réseau : nouvel essai après [delayMs]. */
    data class Retry(val delayMs: Long) : ErrorDecision
    data class Fail(val message: String) : ErrorDecision
}

object ErrorPolicy {
    const val MAX_NETWORK_RETRIES_VOD = 8
    const val MAX_NETWORK_RETRIES_LIVE = 40

    /**
     * @param http code HTTP de la réponse s'il y en a un
     * @param network erreur de connexion (coupure, délai dépassé)
     * @param format format non reconnu par l'extracteur
     * @param hasNextCandidate une autre URL (TS ↔ HLS) reste à essayer
     * @param retries nombre d'essais réseau déjà faits pour ce flux
     */
    fun decide(http: Int?, network: Boolean, format: Boolean, live: Boolean, hasNextCandidate: Boolean, retries: Int, describe: String): ErrorDecision = when {
        http == 404 || http == 410 ->
            if (hasNextCandidate) ErrorDecision.NextCandidate
            else ErrorDecision.Fail("Flux introuvable sur le serveur (chaîne ou film retiré)")
        // Tout autre refus HTTP : on ne relance rien tout de suite, même une autre URL du même serveur.
        http != null && http in 400..499 -> ErrorDecision.Refused(refusal(http))
        format && hasNextCandidate -> ErrorDecision.NextCandidate
        network || live -> {
            val max = if (live) MAX_NETWORK_RETRIES_LIVE else MAX_NETWORK_RETRIES_VOD
            if (retries >= max) ErrorDecision.Fail(describe) else ErrorDecision.Retry(backoff(retries + 1))
        }
        hasNextCandidate -> ErrorDecision.NextCandidate
        else -> ErrorDecision.Fail(describe)
    }

    /** Refus d'un lien hors abonnement (Real-Debrid, extension de sources). */
    fun linkRefusal(code: Int) = when (code) {
        401, 403 -> "Lien refusé ($code) : clé Real-Debrid refusée ou lien expiré"
        404, 410 -> "Fichier introuvable ($code) : retiré de Real-Debrid ou lien expiré"
        429 -> "Limite atteinte (429) : trop de demandes, réessayez dans un instant"
        else -> "Lien refusé (erreur $code)"
    }

    fun backoff(attempt: Int): Long = minOf(500L * (1L shl minOf(attempt - 1, 5)), 15_000L)

    fun refusal(code: Int) = when (code) {
        401, 403 -> "Le fournisseur refuse la connexion ($code) : flux déjà ouvert sur un autre appareil, abonnement expiré ou blocage temporaire."
        406, 429, 458 -> "Le fournisseur limite les connexions ($code) : fermez l'autre appareil, puis réessayez."
        else -> "Le serveur refuse le flux (erreur $code)."
    }
}
