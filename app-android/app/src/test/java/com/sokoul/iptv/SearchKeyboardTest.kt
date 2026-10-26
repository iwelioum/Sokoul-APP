package com.sokoul.iptv

import android.app.Application
import androidx.compose.ui.input.key.KeyEvent
import com.sokoul.iptv.data.RecentSearches
import com.sokoul.iptv.ui.components.KeyAction
import com.sokoul.iptv.ui.components.KeyInput
import com.sokoul.iptv.ui.components.KeyboardLayout
import com.sokoul.iptv.ui.components.applyKey
import com.sokoul.iptv.ui.components.remoteKeyInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun `disposition - AZERTY sur 3 lignes, puis Espace et Dicter, 10 unites par ligne`() {
        val rows = KeyboardLayout.rows(digits = false, voice = true)
        assertEquals(4, rows.size)
        assertEquals("azertyuiop", rows[0].mapNotNull { it.char }.joinToString(""))
        assertEquals("qsdfghjklm", rows[1].mapNotNull { it.char }.joinToString(""))
        assertEquals("wxcvbn", rows[2].mapNotNull { it.char }.joinToString(""))
        assertEquals(listOf(KeyAction.SPACE, KeyAction.VOICE), rows[3].map { it.action })
        for (digits in listOf(false, true)) for (voice in listOf(false, true)) {
            KeyboardLayout.rows(digits, voice).forEach { r -> assertEquals(KeyboardLayout.UNITS, r.sumOf { it.span }) }
        }
        // sans dictée : Espace occupe toute la ligne
        assertEquals(listOf(KeyAction.SPACE), KeyboardLayout.rows(false, voice = false)[3].map { it.action })
    }

    @Test fun `disposition - 123 bascule vers chiffres et accents, ABC revient, meme place`() {
        val letters = KeyboardLayout.rows(digits = false, voice = false)
        val digits = KeyboardLayout.rows(digits = true, voice = false)
        assertEquals("123", letters[2].first().label)
        assertEquals("ABC", digits[2].first().label)
        assertEquals(KeyAction.TOGGLE, digits[2].first().action)
        assertEquals("1234567890", digits[0].mapNotNull { it.char }.joinToString(""))
        assertTrue('é' in digits[1].mapNotNull { it.char })
        assertEquals(KeyAction.DELETE, digits[2].last().action)
    }

    @Test fun `recherches recentes - en tete, sans doublon, 8 au plus, 2 lettres minimum`() {
        var l = emptyList<String>()
        l = RecentSearches.add(l, "matrix")
        l = RecentSearches.add(l, "  Dune   2 ")
        l = RecentSearches.add(l, "MATRIX")
        assertEquals(listOf("MATRIX", "Dune 2"), l)
        assertEquals(l, RecentSearches.add(l, "a"))
        (1..12).forEach { l = RecentSearches.add(l, "titre $it") }
        assertEquals(RecentSearches.MAX, l.size)
        assertEquals("titre 12", l.first())
    }
}
