package com.sokoul.iptv

import com.sokoul.iptv.player.timeshift.LiveTimeshift
import com.sokoul.iptv.player.timeshift.TimeshiftBuffer
import com.sokoul.iptv.player.timeshift.TimeshiftPolicy
import com.sokoul.iptv.recording.StreamRefusedException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Pause du direct : tampon sur disque (segments, plafonds, index temps → octet, lecture qui attend), écrivain. */
class TimeshiftTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000_000L
    private fun buffer(maxMs: Long = 30 * 60_000L, maxBytes: Long = Long.MAX_VALUE) =
        TimeshiftBuffer(tmp.newFolder(), segmentMs = 60_000, maxMs = maxMs, maxBytes = maxBytes, clock = { now })

    private fun chunk(seed: Int, size: Int = 188 * 10) = ByteArray(size) { (seed + it).toByte() }

    @Test fun `ecriture puis lecture a travers plusieurs segments`() {
        val b = buffer()
        val written = mutableListOf<Byte>()
        repeat(5) { m ->
            val c = chunk(m)
            b.append(c, c.size); written += c.toList()
            now += 61_000 // un segment par minute
        }
        assertEquals(written.size.toLong(), b.endOffset)
        val out = ByteArray(written.size)
        var pos = 0L
        while (pos < b.endOffset) {
            val n = b.read(pos, out, pos.toInt(), (out.size - pos).toInt(), waitMs = 0)
            assertTrue(n > 0)
            pos += n
        }
        assertArrayEquals(written.toByteArray(), out)
    }

    @Test fun `plafond de duree - les plus vieux segments partent, les positions restent globales`() {
        val b = buffer(maxMs = 3 * 60_000L)
        repeat(10) { m -> val c = chunk(m); b.append(c, c.size); now += 61_000 }
        assertTrue("début de fenêtre avancé", b.startOffset > 0)
        assertTrue("au plus 4 segments", b.bytesOnDisk <= 4L * 1880)
        // une position supprimée est ramenée au début de la fenêtre
        assertEquals(b.startOffset, b.clampRead(0))
        assertEquals(b.endOffset, 10L * 1880)
    }

    @Test fun `plafond de taille`() {
        val b = buffer(maxBytes = 3L * 1880)
        repeat(8) { m -> val c = chunk(m); b.append(c, c.size); now += 61_000 }
        assertTrue(b.bytesOnDisk <= 4L * 1880)
    }

    @Test fun `index temps vers octet aligne sur un paquet TS`() {
        val b = buffer()
        val t0 = now
        repeat(30) { s -> val c = chunk(s, 1000); b.append(c, c.size); now += 1_000 }
        val o10 = b.offsetAt(t0 + 10_000)
        val o20 = b.offsetAt(t0 + 20_000)
        assertEquals(0L, o10 % TimeshiftBuffer.TS_PACKET)
        assertTrue(o20 > o10)
        // bornes : avant le début → début ; après la fin → dernière marque
        assertEquals(0L, b.offsetAt(t0 - 99_000))
        assertTrue(b.offsetAt(now + 99_000) <= b.endOffset)
        assertEquals(t0 + 10_000, b.timeAt(10_000))
    }

    @Test fun `la lecture attend l ecriture, puis rend la fin quand le flux s arrete`() {
        val b = buffer()
        val got = AtomicInteger(-2)
        val reader = thread { got.set(b.read(0, ByteArray(100), 0, 100, waitMs = 5_000)) }
        Thread.sleep(150)
        assertEquals(-2, got.get()) // toujours en attente
        b.append(chunk(1, 50), 50)
        reader.join(2_000)
        assertEquals(50, got.get())
        // rien de plus à lire et écriture finie : fin des données
        b.finish()
        assertEquals(-1, b.read(50, ByteArray(10), 0, 10, waitMs = 1_000))
    }

    @Test fun `effacer supprime les fichiers`() {
        val dir = tmp.newFolder()
        val b = TimeshiftBuffer(dir, clock = { now })
        b.append(chunk(1), 1880)
        b.clear()
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun `regles - espace libre, plafond, sauts bornes, direct`() {
        val gb = 1_024L * 1024 * 1024
        assertFalse(TimeshiftPolicy.canStart(2 * gb))
        assertTrue(TimeshiftPolicy.canStart(4 * gb))
        assertEquals(TimeshiftPolicy.MAX_BYTES, TimeshiftPolicy.capBytes(20 * gb))
        assertEquals(2 * gb, TimeshiftPolicy.capBytes(3 * gb)) // 1 Go toujours laissé libre
        // Fire TV Stick : 1,7 Go libres suffisent, tampon de 1 Go au plus, 512 Mo laissés à Android
        val freeFire = 1_740L * 1024 * 1024
        assertTrue(TimeshiftPolicy.canStart(freeFire, low = true))
        assertFalse(TimeshiftPolicy.canStart(freeFire))
        assertEquals(TimeshiftPolicy.LOW_MAX_BYTES, TimeshiftPolicy.capBytes(freeFire, low = true))
        assertEquals(gb / 2, TimeshiftPolicy.capBytes(gb, low = true))
        assertEquals(1_000L, TimeshiftPolicy.clampSeek(0, 1_000, 60_000))
        assertEquals(57_000L, TimeshiftPolicy.clampSeek(99_000, 1_000, 60_000))
        assertTrue(TimeshiftPolicy.isAtLive(55_000, 60_000))
        assertFalse(TimeshiftPolicy.isAtLive(30_000, 60_000))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `ecrivain - un refus arrete tout sans nouvel essai`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val ts = LiveTimeshift(backgroundScope, newBuffer = { buffer() }, clock = { now }, io = dispatcher)
        var calls = 0
        var refused = 0
        ts.start(onRefused = { refused = it }) { _, sink ->
            calls++
            sink(chunk(1), 1880)
            throw StreamRefusedException(403)
        }
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(403, refused)
        assertNotNull(ts.state.value.error)
        assertTrue(ts.buffer!!.finished)
        ts.stop()
        assertFalse(ts.active)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `ecrivain - reconnexion apres une coupure, position et retard`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val ts = LiveTimeshift(backgroundScope, newBuffer = { buffer() }, clock = { now }, io = dispatcher)
        var calls = 0
        ts.start(onRefused = {}) { keepGoing, sink ->
            calls++
            if (calls == 1) throw IOException("coupure")
            repeat(20) { if (keepGoing()) { sink(chunk(it), 1880); now += 1_000 } }
            throw IOException("fin du test")
        }
        // les attentes de l'écrivain (arrière-plan) ne sont pas jouées par advanceUntilIdle : on avance l'horloge
        advanceTimeBy(120_000); runCurrent()
        assertTrue("reconnecté", calls >= 2)
        ts.tick(playerPositionMs = null, paused = true)
        val s = ts.state.value
        assertTrue(s.end > s.start)
        // reprise au début de la pause : 20 s de retard environ
        assertTrue(s.behindMs >= 15_000)
        assertNotNull(ts.sourceAt(s.position))
        assertEquals(s.position - 10_000 < s.start, ts.seekTarget(-10_000) == s.start)
        ts.stop()
    }
}
