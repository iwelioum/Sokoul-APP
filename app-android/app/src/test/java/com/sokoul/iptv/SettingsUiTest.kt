package com.sokoul.iptv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.screens.settings.SETTINGS_FOLLOW_MS
import com.sokoul.iptv.ui.screens.settings.SettingsCategory
import com.sokoul.iptv.ui.screens.settings.SettingsPanes
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.computeLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Réglages en deux volets (TV) : la rubrique suit le focus, ▶/OK entre dans le volet, ◀ et Retour reviennent sur la rubrique. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class SettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setUp() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLayout provides computeLayout(960, 540, isTv = true), LocalReducedMotion provides true) {
                // chaque volet : deux lignes nommées d'après la rubrique
                SettingsPanes { cat, modifier ->
                    Column(modifier) {
                        SokoulButton("Ligne 1 ${cat.name}", {})
                        SokoulButton("Ligne 2 ${cat.name}", {})
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(300)
    }

    /** La rubrique dans la liste (son nom figure aussi en titre du volet). */
    private fun cat(label: String) = rule.onNode(hasText(label) and hasClickAction())

    private fun key(k: Key, wait: Long = 80) {
        rule.onRoot().performKeyInput { pressKey(k) }
        rule.mainClock.advanceTimeBy(wait)
    }

    @Test fun `ouverture - focus sur la premiere rubrique, son volet affiche`() {
        setUp()
        cat(SettingsCategory.ACCOUNT.label).assertIsFocused()
        rule.onNodeWithText("Ligne 1 ACCOUNT").assertExists()
    }

    @Test fun `la rubrique suit le focus apres un court arret, pas pendant la traversee`() {
        setUp()
        key(Key.DirectionDown, wait = SETTINGS_FOLLOW_MS / 3)
        key(Key.DirectionDown, wait = SETTINGS_FOLLOW_MS / 3)
        // traversée rapide : le volet n'a pas encore changé
        rule.onNodeWithText("Ligne 1 ACCOUNT").assertExists()
        rule.mainClock.advanceTimeBy(SETTINGS_FOLLOW_MS + 100)
        cat(SettingsCategory.TV.label).assertIsFocused()
        rule.onNodeWithText("Ligne 1 TV").assertExists()
        rule.onNodeWithText("Ligne 1 ACCOUNT").assertDoesNotExist()
    }

    @Test fun `droite entre dans le volet, gauche revient sur la rubrique choisie`() {
        setUp()
        key(Key.DirectionDown, wait = SETTINGS_FOLLOW_MS + 100) // Lecture
        key(Key.DirectionRight)
        rule.onNodeWithText("Ligne 1 PLAYBACK").assertIsFocused()
        key(Key.DirectionDown)
        rule.onNodeWithText("Ligne 2 PLAYBACK").assertIsFocused()
        // depuis la 2e ligne, ◀ revient sur « Lecture », pas sur la rubrique la plus proche
        key(Key.DirectionLeft)
        cat(SettingsCategory.PLAYBACK.label).assertIsFocused()
    }

    @Test fun `OK sur une rubrique entre dans son volet, Retour revient sur la rubrique`() {
        setUp()
        repeat(5) { key(Key.DirectionDown, wait = 40) } // Contrôle parental (après Real-Debrid), sans attendre
        key(Key.DirectionCenter, wait = 200)
        rule.onNodeWithText("Ligne 1 PARENTAL").assertIsFocused()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(200)
        cat(SettingsCategory.PARENTAL.label).assertIsFocused()
        rule.onNodeWithText("Ligne 1 PARENTAL").assertExists()
    }
}
