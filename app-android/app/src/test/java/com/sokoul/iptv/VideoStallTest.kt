package com.sokoul.iptv

import com.sokoul.iptv.player.VideoStallDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Image figée alors que le son continue : réparation après 4 s, au plus 2 fois par flux. */
class VideoStallTest {
    @Test fun `images qui avancent - jamais de reparation`() {
        val d = VideoStallDetector()
        for (s in 0..20) assertFalse(d.sample(s * 1000L, s * 25, playing = true, hasVideo = true))
    }

    @Test fun `images figees 4 s pendant la lecture - reparation`() {
        val d = VideoStallDetector()
        assertFalse(d.sample(0, 10, true, true))
        assertFalse(d.sample(1000, 11, true, true))
        assertFalse(d.sample(3000, 11, true, true))
        assertTrue(d.sample(5000, 11, true, true))
        assertEquals(1, d.recoveries)
        // laisse le temps à la réparation avant de recompter
        assertFalse(d.sample(6000, 11, true, true))
    }

    @Test fun `pause ou flux sans video - rien`() {
        val d = VideoStallDetector()
        for (s in 0..10) assertFalse(d.sample(s * 1000L, 5, playing = false, hasVideo = true))
        for (s in 0..10) assertFalse(d.sample(s * 1000L, 5, playing = true, hasVideo = false))
    }

    @Test fun `chaine image fixe - deux reparations puis on laisse faire`() {
        val d = VideoStallDetector()
        var n = 0
        for (s in 0..60) if (d.sample(s * 1000L, 3, true, true)) n++
        assertEquals(2, n)
        d.reset()
        assertEquals(0, d.recoveries)
    }
}
