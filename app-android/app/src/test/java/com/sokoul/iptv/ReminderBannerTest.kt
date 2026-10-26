package com.sokoul.iptv

import android.app.Application
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.sokoul.iptv.recording.ReminderBus
import com.sokoul.iptv.ui.components.REMINDER_BANNER_MS
import com.sokoul.iptv.ui.components.ReminderBanner
import com.sokoul.iptv.ui.components.ReminderKeys
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.computeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bandeau de rappel : affiché par-dessus l'écran, ⏯ ouvre la chaîne, il s'efface tout seul. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class ReminderBannerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val r = ReminderBus.Reminder(1, "tf1", "TF1", "Le Journal", 0)

    @Test fun `affiché, ⏯ regarde, puis effacé`() {
        val watched = mutableListOf<ReminderBus.Reminder>()
        rule.setContent {
            CompositionLocalProvider(LocalLayout provides computeLayout(960, 540, isTv = true), LocalReducedMotion provides true) {
                ReminderBanner { watched += it }
            }
        }
        assertFalse(ReminderKeys.handle(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) // rien d'affiché : touche laissée à l'écran
        ReminderBus.post(r)
        rule.waitForIdle()
        rule.onNodeWithText("Le Journal").assertIsDisplayed()
        rule.onNodeWithText("DANS 1 MINUTE · TF1").assertIsDisplayed()
        assertTrue(ReminderKeys.handle(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(listOf(r), watched)
        assertNull(ReminderBus.current.value)
    }

    @Test fun `disparaît seul après 15 s`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLayout provides computeLayout(960, 540, isTv = true), LocalReducedMotion provides true) {
                ReminderBanner { }
            }
        }
        ReminderBus.post(r)
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, rule.onAllNodesWithText("Le Journal").fetchSemanticsNodes().size)
        rule.mainClock.advanceTimeBy(REMINDER_BANNER_MS + 2_000)
        assertNull(ReminderBus.current.value)
    }
}
