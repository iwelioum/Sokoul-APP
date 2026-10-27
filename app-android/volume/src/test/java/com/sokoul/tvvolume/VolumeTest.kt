package com.sokoul.tvvolume

import android.view.KeyEvent
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeTest {
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    @Test fun `message setKey identique a celui de Magic Connect`() {
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><root><action name=\"setKey\" eventAction=\"TR_KEY_VOL_UP\" keyCode=\"TR_KEY_VOL_UP\" /></root>",
            String(TclProtocol.message(TvKey.VOL_UP), Charsets.UTF_8),
        )
        assertEquals(listOf("TR_KEY_VOL_UP", "TR_KEY_VOL_DOWN", "TR_KEY_MUTE", "TR_KEY_POWER"), TvKey.entries.map { it.code })
    }

    @Test fun `volume - chaque appui envoye, touche maintenue limitee`() {
        val p = KeyPolicy(repeatGapMs = 150)
        assertEquals(KeyDecision.Send(TvKey.VOL_UP), p.decide(KeyEvent.KEYCODE_VOLUME_UP, down, 0, 1_000))
        assertEquals(KeyDecision.Consume, p.decide(KeyEvent.KEYCODE_VOLUME_UP, down, 1, 1_050))
        assertEquals(KeyDecision.Send(TvKey.VOL_UP), p.decide(KeyEvent.KEYCODE_VOLUME_UP, down, 2, 1_160))
        assertEquals("relâchée : absorbée, rien n'est envoyé", KeyDecision.Consume, p.decide(KeyEvent.KEYCODE_VOLUME_UP, up, 0, 1_200))
        assertEquals("deux appuis rapides : les deux comptent", KeyDecision.Send(TvKey.VOL_UP), p.decide(KeyEvent.KEYCODE_VOLUME_UP, down, 0, 1_210))
        assertEquals(KeyDecision.Send(TvKey.VOL_DOWN), p.decide(KeyEvent.KEYCODE_VOLUME_DOWN, down, 0, 1_220))
    }

    @Test fun `muet - court pour couper le son, long pour la veille, une seule fois`() {
        val p = KeyPolicy(longPressMs = 800)
        assertEquals(KeyDecision.Consume, p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, down, 0, 0))
        assertEquals(KeyDecision.Send(TvKey.MUTE), p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, up, 0, 200))

        assertEquals(KeyDecision.Consume, p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, down, 0, 1_000))
        assertEquals(KeyDecision.Consume, p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, down, 1, 1_400))
        assertEquals(KeyDecision.Send(TvKey.POWER), p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, down, 2, 1_850))
        assertEquals(KeyDecision.Consume, p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, down, 3, 2_000))
        assertEquals("pas de Muet en plus au relâchement", KeyDecision.Consume, p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, up, 0, 2_100))

        // télécommande sans répétition : la durée est lue au relâchement
        p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, down, 0, 5_000)
        assertEquals(KeyDecision.Send(TvKey.POWER), p.decide(KeyEvent.KEYCODE_VOLUME_MUTE, up, 0, 6_000))
    }

    @Test fun `les autres touches passent au systeme`() {
        val p = KeyPolicy()
        for (k in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            assertEquals(KeyDecision.Pass, p.decide(k, down, 0, 0))
        }
    }

    @Test fun `envoi a une fausse TV, puis TV injoignable`() {
        ServerSocket(0).use { server ->
            val got = CompletableFuture.supplyAsync { server.accept().use { it.getInputStream().readBytes() } }
            assertTrue(TclRemote(port = server.localPort).send("127.0.0.1", TvKey.MUTE))
            assertArrayEquals(TclProtocol.message(TvKey.MUTE), got.get(3, TimeUnit.SECONDS))
        }
        val closed = ServerSocket(0).let { s -> s.localPort.also { s.close() } }
        assertFalse(TclRemote(port = closed, timeoutMs = 300).send("127.0.0.1", TvKey.VOL_UP))
    }

    @Test fun `reveil reseau et adresses`() {
        val pkt = TclProtocol.magicPacket("AA:bb:CC:00:11:22")!!
        assertEquals(102, pkt.size)
        assertTrue(pkt.take(6).all { it == 0xFF.toByte() })
        assertEquals(0xAA.toByte(), pkt[6]); assertEquals(0x22.toByte(), pkt[101])
        assertNull(TclProtocol.magicPacket("pas une mac"))
        assertTrue(TclProtocol.isPrivateIpv4("192.168.1.50"))
        assertFalse("jamais d'adresse publique", TclProtocol.isPrivateIpv4("8.8.8.8"))
        assertFalse(TclProtocol.isPrivateIpv4("192.168.0"))
        assertEquals(253, TvFinder.candidates("192.168.1.20").size)
        assertTrue(TvFinder.candidates("8.8.8.8").isEmpty())
    }
}
