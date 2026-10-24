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
    data class Reconnecting(val attempt: Int) : StreamState
    data class Failed(val message: String) : StreamState
    data object Ended : StreamState
}

/**
 * Pilote un ExoPlayer pour l'IPTV : essaie les URL de repli (TS ↔ HLS, HLS forcé),
 * se reconnecte automatiquement en cas de coupure réseau et relance un flux direct
 * bloqué en mise en mémoire tampon trop longtemps.
 */
class StreamPlayer(
    val player: ExoPlayer,
    private val factory: PlayerFactory,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<StreamState>(StreamState.Idle)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var stream: ResolvedStream? = null
    private var title = ""
    private var candidates: List<Pair<String, Boolean>> = emptyList() // (url, forceHls)
    private var candidateIndex = 0
    private var retries = 0
    private var retryJob: Job? = null
    private var watchdog: Job? = null
    private var startPositionMs = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    retries = 0
                    _state.value = StreamState.Playing
                    watchdog?.cancel()
                }
                Player.STATE_BUFFERING -> armWatchdog()
                Player.STATE_ENDED -> {
                    if (stream?.isLive == true) scheduleRetry("Flux terminé") else _state.value = StreamState.Ended
                }
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.w(error, "Erreur de lecture ${error.errorCodeName}")
            val s = stream ?: return
            val http = httpStatus(error)
            when {
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    player.seekToDefaultPosition()
                    player.prepare()
                }
                // Refus du serveur : autre format si disponible, sinon quelques essais espacés puis abandon
                http != null && http in 400..499 -> when {
                    candidateIndex < candidates.lastIndex && http != 401 -> { candidateIndex++; load(keepPosition = true) }
                    http == 404 || http == 410 -> _state.value = StreamState.Failed("Flux introuvable sur le serveur (chaîne ou film retiré)")
                    else -> scheduleRetry(refusal(http), refused = true)
                }
                isFormatError(error) && candidateIndex < candidates.lastIndex -> {
                    candidateIndex++
                    load(keepPosition = false)
                }
                isNetworkError(error) || s.isLive -> scheduleRetry(describe(error))
                candidateIndex < candidates.lastIndex -> {
                    candidateIndex++
                    load(keepPosition = true)
                }
                else -> _state.value = StreamState.Failed(describe(error))
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun play(stream: ResolvedStream, title: String, startPositionMs: Long = 0) {
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
        load(keepPosition = false)
    }

    fun retryNow() {
        retries = 0
        candidateIndex = 0
        load(keepPosition = true)
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

    private fun scheduleRetry(reason: String, refused: Boolean = false) {
        if (retryJob?.isActive == true) return
        retries++
        val maxRetries = when {
            refused -> 2 // la connexion se libère parfois quelques secondes après la chaîne précédente
            stream?.isLive == true -> 40
            else -> 8
        }
        if (retries > maxRetries) {
            _state.value = StreamState.Failed(reason)
            return
        }
        _state.value = StreamState.Reconnecting(retries)
        retryJob = scope.launch {
            delay(if (refused) 4_000L * retries else minOf(500L * (1L shl minOf(retries - 1, 5)), 15_000L))
            // en direct, on alterne entre les formats disponibles (TS / HLS)
            if (stream?.isLive == true && candidates.size > 1 && retries % 3 == 0) {
                candidateIndex = (candidateIndex + 1) % candidates.size
            }
            load(keepPosition = true)
        }
    }

    /** Un direct qui reste en BUFFERING plus de 12 s est relancé. */
    private fun armWatchdog() {
        if (stream?.isLive != true) return
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(12_000)
            if (isActive && player.playbackState == Player.STATE_BUFFERING) {
                Timber.i("Flux bloqué, relance")
                scheduleRetry("Flux bloqué")
            }
        }
    }

    fun stop() {
        retryJob?.cancel()
        watchdog?.cancel()
        player.stop()
        _state.value = StreamState.Idle
    }

    fun release() {
        stop()
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

    private fun refusal(code: Int) = when (code) {
        401, 403 -> "Le fournisseur refuse le flux ($code) : connexion déjà utilisée ailleurs, abonnement expiré ou accès bloqué temporairement"
        406, 429, 458 -> "Le fournisseur limite les connexions ($code) : fermez l'autre appareil ou réessayez dans quelques minutes"
        else -> "Le serveur refuse le flux (erreur $code)"
    }

    private fun isNetworkError(e: PlaybackException) = e.errorCode in 2000..2999
    private fun isFormatError(e: PlaybackException) = e.errorCode in 3000..3999 ||
        e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && candidates.size > 1

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Le serveur refuse le flux (abonnement, connexions simultanées ou chaîne hors ligne)"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connexion au flux impossible"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Flux introuvable"
        in 3000..3999 -> "Format de flux non reconnu"
        in 4000..4999 -> "Le décodeur de l'appareil ne prend pas en charge ce flux (essayez le mode logiciel dans Réglages › Lecteur)"
        else -> e.errorCodeName
    }
}
