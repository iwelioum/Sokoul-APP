package com.sokoul.iptv

import com.sokoul.iptv.ui.screens.player.GestureZone
import com.sokoul.iptv.ui.screens.player.PinchDecision
import com.sokoul.iptv.ui.screens.player.gestureZone
import com.sokoul.iptv.ui.screens.player.levelAfterDrag
import com.sokoul.iptv.ui.screens.player.pinchDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchGesturesTest {
    @Test fun `zones de geste par tiers`() {
        assertEquals(GestureZone.LEFT, gestureZone(100f, 1500f))
        assertEquals(GestureZone.CENTER, gestureZone(750f, 1500f))
        assertEquals(GestureZone.RIGHT, gestureZone(1400f, 1500f))
        assertEquals(GestureZone.CENTER, gestureZone(499.9f + 0.2f, 1500f)) // 500 = début du centre
        assertEquals("largeur inconnue : centre (zapping seul)", GestureZone.CENTER, gestureZone(10f, 0f))
    }

    @Test fun `niveau apres glissement borne`() {
        // vers le haut sur la moitié de la hauteur : +50 %
        assertEquals(0.8f, levelAfterDrag(0.3f, -500f, 1000f), 0.001f)
        assertEquals(0.1f, levelAfterDrag(0.3f, 200f, 1000f), 0.001f)
        assertEquals(1f, levelAfterDrag(0.9f, -900f, 1000f), 0.001f)
        assertEquals(0f, levelAfterDrag(0.1f, 900f, 1000f), 0.001f)
        assertEquals(0.4f, levelAfterDrag(0.4f, 50f, 0f), 0.001f)
    }

    @Test fun `decision de pincement avec seuils`() {
        assertEquals(PinchDecision.FILL, pinchDecision(200f, 240f))
        assertEquals(PinchDecision.FIT, pinchDecision(200f, 170f))
        assertNull("petit mouvement : rien", pinchDecision(200f, 210f))
        assertNull(pinchDecision(0f, 100f))
    }
}
