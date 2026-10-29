package com.sokoul.iptv.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Retarde le son décodé (PCM 16 bits) de [delayMs] : chaque octet reçu ressort [delayMs] plus tard, du silence
 * comblant le début. Autant d'octets sortent qu'il en entre : l'horloge du lecteur (qui cale l'image sur le son)
 * n'est pas faussée, seul ce qu'on entend est décalé. Sans effet sur le son envoyé tel quel à l'ampli (passthrough).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioDelayProcessor : BaseAudioProcessor() {
    /** Retard voulu ; un changement repart d'un tampon de silence (court blanc, sans désynchroniser l'image). */
    @Volatile var delayMs: Long = 0

    private var ring = ByteArray(0)
    private var pos = 0
    private var ringFor = -1L

    /** Autre format (float, passthrough) : processeur inactif, le son passe sans retard ni erreur. */
    override fun onConfigure(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (input.encoding == C.ENCODING_PCM_16BIT) input else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        val want = delayMs
        if (want != ringFor) resize(want)
        val out = replaceOutputBuffer(n)
        if (ring.isEmpty()) {
            out.put(inputBuffer)
        } else {
            // ligne à retard : on sort l'octet le plus ancien et on range le nouveau à sa place
            for (i in 0 until n) {
                out.put(ring[pos])
                ring[pos] = inputBuffer.get()
                pos = if (pos + 1 == ring.size) 0 else pos + 1
            }
        }
        out.flip()
    }

    private fun resize(ms: Long) {
        ringFor = ms
        pos = 0
        val frame = inputAudioFormat.bytesPerFrame.coerceAtLeast(1)
        val bytes = ringBytes(ms, inputAudioFormat.sampleRate, frame)
        ring = ByteArray(bytes)
    }

    override fun onFlush() { ringFor = -1L } // saut : on repart d'un tampon vide (pas d'ancien son qui ressurgit)

    override fun onReset() { ring = ByteArray(0); pos = 0; ringFor = -1L }

    companion object {
        /** Taille de la ligne à retard en octets, alignée sur une trame (tous les canaux d'un échantillon). */
        fun ringBytes(ms: Long, sampleRate: Int, bytesPerFrame: Int): Int {
            if (ms <= 0 || sampleRate <= 0) return 0
            val frames = (sampleRate.toLong() * ms / 1000L).coerceAtMost(sampleRate.toLong() * 5)
            return (frames * bytesPerFrame).toInt()
        }
    }
}
