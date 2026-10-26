package com.sokoul.iptv.player.timeshift

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/**
 * Source locale du lecteur pendant la pause du direct : lit le [TimeshiftBuffer] à partir de la position
 * demandée et attend quand elle rattrape l'écriture (comme un flux réseau un peu lent). Fin des données
 * seulement quand l'écriture est arrêtée.
 */
@OptIn(UnstableApi::class)
class TimeshiftDataSource(private val buffer: TimeshiftBuffer, private val startOffset: Long = 0) : BaseDataSource(/* isNetwork = */ false) {
    private var uri: Uri? = null
    private var position = 0L
    @Volatile private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        position = buffer.clampRead(startOffset + dataSpec.position)
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        // attente par tranches courtes : le lecteur peut fermer la source entre-temps (changement de position)
        while (opened) {
            position = this.buffer.clampRead(position)
            val n = this.buffer.read(position, buffer, offset, length, waitMs = 500)
            if (n < 0) return C.RESULT_END_OF_INPUT
            if (n > 0) {
                position += n
                bytesTransferred(n)
                return n
            }
        }
        throw IOException("Source fermée")
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) { opened = false; transferEnded() }
        uri = null
    }

    /** [startOffset] = position du tampon où commence la lecture (reculer, avancer, revenir au direct). */
    class Factory(private val buffer: TimeshiftBuffer, private val startOffset: Long) : DataSource.Factory {
        override fun createDataSource(): DataSource = TimeshiftDataSource(buffer, startOffset)
    }

    companion object {
        val URI: Uri = Uri.parse("timeshift://local/live.ts")
    }
}
