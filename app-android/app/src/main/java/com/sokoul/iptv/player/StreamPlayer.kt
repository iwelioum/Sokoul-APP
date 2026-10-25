package com.sokoul.iptv.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sokoul.iptv.data.repo.ResolvedStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface StreamState {
    data object Idle : StreamState
    data object Loading : StreamState
    data object Playing : StreamState
    /** Lecture à la demande en attente de données (après une avance rapide, réseau lent). */
    data object Buffering : StreamState
    data class Reconnecting(val attempt: Int) : StreamState
    /** Pause imposée après un refus du fournisseur : nouvel essai automatique à [untilMs]. */
    data class Waiting(val untilMs: Long, val message: String) : StreamState
    /** @param refused refus du fournisseur : « Réessayer » reste bloqué jusqu'à [retryAtMs]. */
    data class Failed(val message: String, val refused: Boolean = false, val retryAtMs: Long = 0) : StreamState
    data object Ended : StreamState
}

/**
 * Pilote un ExoPlayer pour l'IPTV : essaie les URL de repli (TS ↔ HLS, HLS forcé),
 * se reconnecte automatiquement en cas de coupure réseau et relance un flux direct
 * bloqué en mise en mémoire tampon trop longtemps.
 *
 * Un refus du fournisseur (4xx) n'est jamais relancé tout de suite : la porte [gate]
 * impose une pause, suivie d'un seul nouvel essai automatique.
 *
 * @param exclusive lecteur principal : en démarrer un arrête l'autre (une connexion à la fois).
 */
class StreamPlayer(
    val player: ExoPlayer,
    private val factory: PlayerFactory,
    private val scope: CoroutineScope,
    private val gate: ProviderGate,
    private val exclusive: Boolean = true,
) {
    private val _state = MutableStateFlow<StreamState>(StreamState.Idle)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var stream: ResolvedStream? = null
    private var title = ""
    private var candidates: List<Pair<String, Boolean>> = emptyList() // (url, forceHls)
    private var candidateIndex = 0
    private var retries = 0
    private var refusalRetries = 0
    private var retryJob: Job? = null
    private var watchdog: Job? = null
    private var startPositionMs = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    retries = 0
                    refusalRetries = 0
                    _state.value = StreamState.Playing
                    watchdog?.cancel()
                }
                Player.STATE_BUFFERING -> {
                    if (stream?.isLive == true) armWatchdog()
                    else if (_state.value == StreamState.Playing) _state.value = StreamState.Buffering
                }
                Player.STATE_ENDED -> {
                    if (stream?.isLive == true) network("Flux terminé") else _state.value = StreamState.Ended
                }
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.w(error, "Erreur de lecture ${error.errorCodeName}")
            val s = stream ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            val http = httpStatus(error)
            val decision = ErrorPolicy.decide(
                http = http, network = isNetworkError(error) || (http != null && http >= 500), format = isFormatError(error), live = s.isLive,
                hasNextCandidate = candidateIndex < candidates.lastIndex, retries = retries, describe = describe(error),
            )
            when (decision) {
                ErrorDecision.NextCandidate -> { candidateIndex++; load(keepPosition = true) }
                is ErrorDecision.Refused -> refused(decision.message)
                is ErrorDecision.Retry -> network(describe(error), decision.delayMs)
                is ErrorDecision.Fail -> _state.value = StreamState.Failed(decision.message)
            }
        }
    }

    private val released = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        player.addListener(listener)
        PlaybackActivity.started()
    }

    fun play(stream: ResolvedStream, title: String, startPositionMs: Long = 0) {
        if (exclusive) gate.acquire(this)
        this.stream = stream
        this.title = title
        this.startPositionMs = startPositionMs
        candidates = buildList {
            stream.urls.forEach { add(it to false) }
            // URL sans extension : on retente en forçant HLS
            stream.urls.firstOrNull()?.let { u ->
                val path = u.substringBefore('?').substringAfterLast('/')
                if (!path.contains('.')) add(u to true)
            }
        }
        candidateIndex = 0
        retries = 0
        refusalRetries = 0
        loadWhenAllowed(keepPosition = false)
    }

    /** « Réessayer » : respecte toujours la pause imposée par le fournisseur. */
    fun retryNow() {
        if (stream == null) return
        if (exclusive) gate.acquire(this)
        retries = 0
        candidateIndex = 0
        loadWhenAllowed(keepPosition = true)
    }

    private fun loadWhenAllowed(keepPosition: Boolean) {
        retryJob?.cancel()
        val wait = gate.remainingMs()
        if (wait <= 0) { load(keepPosition); return }
        player.stop()
        _state.value = StreamState.Waiting(gate.availableAt(), "Le fournisseur a refusé une connexion il y a quelques instants.")
        retryJob = scope.launch {
            delay(wait)
            load(keepPosition)
        }
    }

    private fun load(keepPosition: Boolean) {
        val s = stream ?: return
        val (url, forceHls) = candidates.getOrNull(candidateIndex) ?: return
        retryJob?.cancel()
        val position = if (keepPosition && !s.isLive) player.currentPosition.takeIf { it > 0 } ?: startPositionMs else startPositionMs
        _state.value = if (retries > 0) StreamState.Reconnecting(retries) else StreamState.Loading
        val source = factory.mediaSourceFactory(s.userAgent, s.referrer)
            .createMediaSource(factory.mediaItem(url, title, forceHls, s.isLive))
        player.setMediaSource(source, if (s.isLive) 0 else position)
        player.prepare()
        player.playWhenReady = true
    }

    /** Refus du fournisseur : pause imposée, puis un seul nouvel essai automatique. */
    private fun refused(message: String) {
        retryJob?.cancel()
        watchdog?.cancel()
        gate.noteRefusal()
        player.stop()
        if (refusalRetries >= MAX_AUTO_RETRIES_AFTER_REFUSAL) {
            _state.value = StreamState.Failed(message, refused = true, retryAtMs = gate.availableAt())
            return
        }
        refusalRetries++
        val wait = gate.remainingMs()
        _state.value = StreamState.Waiting(gate.availableAt(), message)
        retryJob = scope.launch {
            delay(wait)
            candidateIndex = 0
            load(keepPosition = true)
        }
    }

    private fun network(reason: String, delayMs: Long = ErrorPolicy.backoff(retries + 1)) {
        if (retryJob?.isActive == true) return
        val max = if (stream?.isLive == true) ErrorPolicy.MAX_NETWORK_RETRIES_LIVE else ErrorPolicy.MAX_NETWORK_RETRIES_VOD
        retries++
        if (retries > max) {
            _state.value = StreamState.Failed(reason)
            return
        }
        _state.value = StreamState.Reconnecting(retries)
        retryJob = scope.launch {
            delay(delayMs)
            // en direct, on alterne entre les formats disponibles (TS / HLS)
            if (stream?.isLive == true && candidates.size > 1 && retries % 3 == 0) {
                candidateIndex = (candidateIndex + 1) % candidates.size
            }
            if (gate.remainingMs() > 0) loadWhenAllowed(keepPosition = true) else load(keepPosition = true)
        }
    }

    /** Un direct qui reste en BUFFERING plus de 12 s est relancé. */
    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(12_000)
            if (isActive && player.playbackState == Player.STATE_BUFFERING) {
                Timber.i("Flux bloqué, relance")
                network("Flux bloqué")
            }
        }
    }

    /** Arrête le flux (connexion fermée). Appelé aussi quand un autre lecteur prend la main. */
    fun stop() {
        retryJob?.cancel()
        watchdog?.cancel()
        player.stop()
        _state.value = StreamState.Idle
    }

    fun release() {
        if (released.compareAndSet(false, true)) PlaybackActivity.ended()
        stop()
        gate.release(this)
        player.removeListener(listener)
        player.release()
    }

    private fun httpStatus(e: PlaybackException): Int? {
        var c: Throwable? = e.cause
        while (c != null) {
            if (c is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return c.responseCode
            c = c.cause
        }
        return null
    }

    private fun isNetworkError(e: PlaybackException) = e.errorCode in 2000..2999 && e.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    private fun isFormatError(e: PlaybackException) = e.errorCode in 3000..3999

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Le serveur refuse le flux (abonnement, connexions simultanées ou chaîne hors ligne)"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connexion au flux impossible : vérifiez la connexion internet de l'appareil."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Flux introuvable"
        in 3000..3999 -> "Format de flux non reconnu"
        in 4000..4999 -> "Le décodeur de l'appareil ne prend pas en charge ce flux (essayez le mode logiciel dans Réglages › Lecteur)"
        else -> e.errorCodeName
    }

    companion object {
        const val MAX_AUTO_RETRIES_AFTER_REFUSAL = 1
    }
}
