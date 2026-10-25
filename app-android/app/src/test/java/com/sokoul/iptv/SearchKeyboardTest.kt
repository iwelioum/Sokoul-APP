package com.sokoul.iptv

import android.app.Application
import androidx.compose.ui.input.key.KeyEvent
import com.sokoul.iptv.ui.components.KeyInput
import com.sokoul.iptv.ui.components.applyKey
import com.sokoul.iptv.ui.components.remoteKeyInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.KeyEvent as AKey

/** Clavier de recherche TV : saisie et raccourcis de la télécommande. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SearchKeyboardTest {
    private fun down(code: Int) = KeyEvent(AKey(AKey.ACTION_DOWN, code))

    @Test fun `saisie - pas d'espace en tete ni double, effacer, tout effacer, longueur bornee`() {
        assertEquals("", applyKey("", KeyInput.Space))
        assertEquals("le ", applyKey("le", KeyInput.Space))
        assertEquals("le ", applyKey("le ", KeyInput.Space))
        assertEquals("l", applyKey("le", KeyInput.Delete))
        assertEquals("", applyKey("", KeyInput.Delete))
        assertEquals("", applyKey("matrix", KeyInput.Clear))
        assertEquals("ab", applyKey("ab", KeyInput.Char('c'), max = 2))
    }

    @Test fun `telecommande - Lecture = espace, Retour rapide = effacer, lettres tapees`() {
        assertEquals(KeyInput.Space, remoteKeyInput(down(AKey.KEYCODE_MEDIA_PLAY_PAUSE)))
        assertEquals(KeyInput.Delete, remoteKeyInput(down(AKey.KEYCODE_MEDIA_REWIND)))
        assertEquals(KeyInput.Char('m'), remoteKeyInput(down(AKey.KEYCODE_M)))
        assertEquals(KeyInput.Char('7'), remoteKeyInput(down(AKey.KEYCODE_7)))
        // les flèches et OK gardent leur rôle de navigation
        assertNull(remoteKeyInput(down(AKey.KEYCODE_DPAD_CENTER)))
        assertNull(remoteKeyInput(down(AKey.KEYCODE_DPAD_RIGHT)))
        assertNull(remoteKeyInput(KeyEvent(AKey(AKey.ACTION_UP, AKey.KEYCODE_MEDIA_REWIND))))
    }
}
