package com.sokoul.iptv

import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.ui.screens.tv.ZAP_SETTLE_MS
import com.sokoul.iptv.ui.screens.tv.Zapper
import com.sokoul.iptv.ui.screens.tv.zapDelta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zapping « vraie box » : un seul flux par série d'appuis, ouvert après l'arrêt, le précédent fermé avant. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvZapTest {
    private fun ch(n: Int) = ChannelEntity(1, "c$n", n, "Chaîne $n", "chaine $n", null, "g", null, null, "chaine$n", null, null, null, 0, null, null, n, 1)
    private val list = (1..10).map(::ch)

    @Test fun `cinq appuis rapides ouvrent un seul flux apres l'arret et apres fermeture du precedent`() = runTest {
        val events = mutableListOf<String>()
        val z = Zapper(backgroundScope, onStart = { events += "stop" }, onCommit = { events += "open ${it.number}" })
        repeat(5) {
            z.step(list, list[2], +1)
            advanceTimeBy(ZAP_SETTLE_MS / 3)
        }
        assertEquals("bandeau sur la chaîne visée", 8, z.pending.value?.number)
        assertEquals("flux fermé au premier appui, rien d'ouvert pendant la série", listOf("stop"), events)
        advanceTimeBy(ZAP_SETTLE_MS); runCurrent()
        assertEquals(listOf("stop", "open 8"), events)
        assertNull(z.pending.value)
    }

    @Test fun `la liste boucle aux deux bouts`() = runTest {
        var opened: ChannelEntity? = null
        val z = Zapper(backgroundScope, onStart = {}, onCommit = { opened = it })
        z.step(list, list[0], -1)
        advanceTimeBy(ZAP_SETTLE_MS + 1); runCurrent()
        assertEquals(10, opened?.number)
        z.step(list, list[9], +1)
        advanceTimeBy(ZAP_SETTLE_MS + 1); runCurrent()
        assertEquals(1, opened?.number)
    }

    @Test fun `Retour pendant le zapping n'ouvre rien`() = runTest {
        val events = mutableListOf<String>()
        val z = Zapper(backgroundScope, onStart = { events += "stop" }, onCommit = { events += "open" })
        z.step(list, list[0], +1)
        assertTrue(z.cancel())
        advanceTimeBy(ZAP_SETTLE_MS * 2); runCurrent()
        assertEquals(listOf("stop"), events)
    }

    @Test fun `sens du zapping par defaut et inverse`() {
        assertEquals(-1, zapDelta(up = true, inverted = false))
        assertEquals(1, zapDelta(up = false, inverted = false))
        assertEquals(1, zapDelta(up = true, inverted = true))
        assertEquals(-1, zapDelta(up = false, inverted = true))
    }
}
