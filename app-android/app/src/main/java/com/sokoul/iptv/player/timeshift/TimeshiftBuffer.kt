package com.sokoul.iptv.player.timeshift

import java.io.File
import java.io.RandomAccessFile

/**
 * Tampon de la pause du direct : le flux (MPEG-TS) est écrit dans des segments d'une minute, sur disque.
 * Chaque octet a une position « globale » qui ne change jamais ; un index (une marque par seconde) relie
 * l'heure d'écriture à une position, pour reculer ou avancer sans réseau.
 *
 * - plafonds : [maxMs] de contenu et [maxBytes] sur disque — le plus vieux segment est supprimé d'abord ;
 * - la lecture ([read]) attend les octets pas encore écrits (le lecteur rattrape l'écriture) ;
 * - les positions rendues par [offsetAt] sont alignées sur un paquet TS (188 octets).
 *
 * Écriture d'un côté (un seul écrivain), lecture de l'autre : tout passe par le verrou de l'objet.
 */
class TimeshiftBuffer(
    private val dir: File,
    private val segmentMs: Long = 60_000,
    private val maxMs: Long = 30 * 60_000L,
    private val maxBytes: Long = 2_500L * 1024 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Segment(val file: File, val start: Long, val startTime: Long) {
        var length = 0L
        val end get() = start + length
    }

    /** Une marque : à l'heure [time], [offset] octets avaient été écrits. */
    data class Mark(val time: Long, val offset: Long)

    private val lock = Object()
    private val segments = ArrayDeque<Segment>()
    private val marks = ArrayDeque<Mark>()
    private var written = 0L
    private var nextIndex = 0
    private var out: RandomAccessFile? = null

    /** Plus d'écriture à venir (flux arrêté) : une lecture au bout rend la fin des données. */
    @Volatile var finished = false
        private set

    init {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Première position encore disponible (les plus vieux segments ont pu être supprimés). */
    val startOffset: Long get() = synchronized(lock) { segments.firstOrNull()?.start ?: written }
    val endOffset: Long get() = synchronized(lock) { written }
    val startTime: Long get() = synchronized(lock) { marks.firstOrNull()?.time ?: clock() }
    val endTime: Long get() = synchronized(lock) { marks.lastOrNull()?.time ?: clock() }
    val bytesOnDisk: Long get() = synchronized(lock) { segments.sumOf { it.length } }

    fun append(buf: ByteArray, len: Int) {
        if (len <= 0) return
        synchronized(lock) {
            check(!finished) { "Tampon fermé" }
            val now = clock()
            var seg = segments.lastOrNull()
            if (seg == null || now - seg.startTime >= segmentMs) {
                out?.close()
                seg = Segment(File(dir, "seg-${nextIndex++}.ts"), written, now)
                segments.addLast(seg)
                out = RandomAccessFile(seg.file, "rw")
            }
            if (marks.isEmpty() || now - marks.last().time >= MARK_EVERY_MS) marks.addLast(Mark(now, written))
            out!!.seek(seg.length)
            out!!.write(buf, 0, len)
            seg.length += len
            written += len
            trim(now)
            lock.notifyAll()
        }
    }

    /** Supprime les plus vieux segments au-delà des plafonds (jamais celui en cours d'écriture). */
    private fun trim(now: Long) {
        while (segments.size > 1) {
            val oldest = segments.first()
            val tooOld = now - segments[1].startTime > maxMs
            val tooBig = segments.sumOf { it.length } > maxBytes
            if (!tooOld && !tooBig) break
            segments.removeFirst()
            oldest.file.delete()
            val first = segments.first().start
            while (marks.size > 1 && marks[1].offset <= first) marks.removeFirst()
            if (marks.isNotEmpty() && marks.first().offset < first) marks[0] = Mark(marks.first().time, first)
        }
    }

    /** Position (alignée sur 188 octets) du contenu écrit à [time], bornée à la fenêtre disponible. */
    fun offsetAt(time: Long): Long = synchronized(lock) {
        if (marks.isEmpty()) return written
        val m = marks.lastOrNull { it.time <= time } ?: marks.first()
        val start = segments.firstOrNull()?.start ?: 0L
        align(m.offset.coerceIn(start, written), start)
    }

    /** Heure d'écriture approchée de la position [offset]. */
    fun timeAt(offset: Long): Long = synchronized(lock) {
        (marks.lastOrNull { it.offset <= offset } ?: marks.firstOrNull())?.time ?: clock()
    }

    private fun align(offset: Long, base: Long) = base + ((offset - base) / TS_PACKET) * TS_PACKET

    /**
     * Lit à partir de [offset] ; attend jusqu'à [waitMs] que des octets arrivent. Rend le nombre d'octets lus,
     * 0 si rien n'est arrivé à temps, -1 si le flux est terminé et tout a été lu. Une position déjà supprimée
     * est ramenée au début de la fenêtre : utiliser [clampRead] pour connaître la position effective.
     */
    fun read(offset: Long, dst: ByteArray, dstOff: Int, len: Int, waitMs: Long): Int {
        synchronized(lock) {
            val deadline = clock() + waitMs
            while (offset >= written) {
                if (finished) return -1
                val left = deadline - clock()
                if (left <= 0) return 0
                // le lecteur annule un chargement en interrompant son fil : on le signale comme une E/S interrompue
                try { lock.wait(left.coerceAtMost(250)) } catch (e: InterruptedException) { throw java.io.InterruptedIOException() }
            }
            val pos = clampRead(offset)
            val seg = segments.lastOrNull { it.start <= pos } ?: return 0
            val n = minOf(len.toLong(), seg.end - pos).toInt()
            if (n <= 0) return 0
            RandomAccessFile(seg.file, "r").use { f -> f.seek(pos - seg.start); f.readFully(dst, dstOff, n) }
            return n
        }
    }

    /** Position de lecture effective : une position supprimée (trop vieille) passe au début de la fenêtre. */
    fun clampRead(offset: Long): Long = synchronized(lock) { offset.coerceAtLeast(segments.firstOrNull()?.start ?: 0L) }

    /** Fin de l'écriture : les lecteurs en attente rendent la fin des données. */
    fun finish() = synchronized(lock) {
        finished = true
        out?.close(); out = null
        lock.notifyAll()
    }

    /** Ferme et efface tout (changement de chaîne, sortie du lecteur). */
    fun clear() = synchronized(lock) {
        finish()
        segments.forEach { it.file.delete() }
        segments.clear(); marks.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val TS_PACKET = 188L
        const val MARK_EVERY_MS = 1_000L
    }
}
