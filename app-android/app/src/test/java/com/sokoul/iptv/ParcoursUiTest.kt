package com.sokoul.iptv

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sokoul.iptv.player.StreamState
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.screens.player.PlayerAction
import com.sokoul.iptv.ui.screens.player.PlayerUi
import com.sokoul.iptv.ui.screens.player.StatusOverlay
import com.sokoul.iptv.ui.screens.player.VodControls
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@Serializable object Home
@Serializable object Detail

/** Interface (Compose sous Robolectric, mise en page TV) : tout est atteignable à la télécommande, sans touche Menu. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ParcoursUiTest {
    @get:Rule val rule = createComposeRule()

    @Before fun clock() {
        // sortir de la fenêtre anti-double-appui laissée par un test précédent
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
    }

    // --- lecteur ------------------------------------------------------------------------------------

    @Test fun `erreur de lecture le bouton Reessayer prend le focus`() {
        var retried = 0
        rule.setContent {
            StatusOverlay(StreamState.Failed("Serveur injoignable"), null, { 0L }, remember { FocusRequester() }, { retried++ }, {})
        }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Réessayer").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(1, retried)
    }

    @Test fun `refus du fournisseur Reessayer bloque pendant le compte a rebours`() {
        var retried = 0
        val until = System.currentTimeMillis() + 30_000
        rule.setContent {
            StatusOverlay(StreamState.Failed("Refus 403", refused = true, retryAtMs = until), null, { 30_000L }, remember { FocusRequester() }, { retried++ }, {})
        }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Le fournisseur refuse la connexion").assertExists()
        val button = rule.onNodeWithText("Réessayer dans", substring = true)
        button.assertIsFocused()
        button.performClick()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals("aucune requête avant la fin de la pause", 0, retried)
        // Retour reste accessible à droite
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("Retour").assertIsFocused()
    }

    @Test fun `barre de lecture boutons atteignables au D-pad depuis la progression`() {
        val clicked = mutableListOf<String>()
        val seeks = mutableListOf<Long>()
        val scrubber = FocusRequester()
        rule.setContent {
            VodControls(
                ui = PlayerUi(title = "Film", subtitle = "S1 · É3"), position = 60_000, duration = 600_000, preview = null, buffering = false,
                scrubberFocus = scrubber, firstButtonFocus = remember { FocusRequester() },
                actions = listOf(
                    PlayerAction("Pause", Icons.Default.PlayArrow, primary = true) { clicked += "Pause" },
                    PlayerAction("Sous-titres", Icons.Default.Subtitles) { clicked += "Sous-titres" },
                ),
                onSeekStep = { seeks += it }, onSeekTo = {}, onPlayPause = { clicked += "OK" }, onInteract = {},
            )
        }
        rule.runOnIdle { scrubber.requestFocus() }
        rule.onNodeWithContentDescription("Position de lecture").assertIsFocused()
        // ◀ ▶ sur la barre = recul / avance
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.runOnIdle { assertEquals(listOf(30_000L, -10_000L), seeks) }
        // ▼ : rangée de boutons ; ▶ : bouton suivant ; OK : action
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Pause").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Sous-titres").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf("Sous-titres"), clicked) }
        // ▲ : retour sur la barre, OK = lecture/pause
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithContentDescription("Position de lecture").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf("Sous-titres", "OK"), clicked) }
    }

    @Composable
    private fun NextUp(onPlay: () -> Unit = {}) {
        androidx.compose.foundation.layout.Box(Modifier.size(960.dp, 540.dp)) {
            com.sokoul.iptv.ui.screens.player.NextUpCard(
                com.sokoul.iptv.data.db.EpisodeEntity(1, "s", "e2", 1, 2, "The Fan", null, null, null, "mkv", null, null),
                seconds = 10, fallbackImage = null, playFocus = remember { FocusRequester() },
                modifier = Modifier, onPlay = onPlay, onCancel = {},
            )
        }
    }

    @Test fun `carte episode suivant bouton Lire focalise et actif au D-pad`() {
        var played = 0
        rule.setContent { NextUp { played++ } }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Lire maintenant").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(1, played) }
    }

    /** Mesure réelle du texte (rendu natif) : la carte occupe la moitié de l'écran TV, rien ne passe à la ligne. */
    @Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `carte episode suivant lisible sur TV`() {
        rule.setContent { NextUp() }
        rule.waitForIdle()
        val cancel = rule.onNodeWithText("Annuler").fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithText("Épisode suivant dans 10 s").fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            assertTrue("Annuler : ${cancel.width.toDp()} x ${cancel.height.toDp()}", cancel.height.toDp() < 64.dp && cancel.width.toDp() > 80.dp)
            assertTrue("titre : ${title.width.toDp()} x ${title.height.toDp()}", title.height.toDp() < 30.dp)
        }
    }

    // --- navigation -------------------------------------------------------------------------------------

    private fun navHost(): NavHostController {
        lateinit var nav: NavHostController
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Home) {
                composable<Home> {}
                composable<Detail> {}
                composable<PlayerRoute> {}
            }
        }
        rule.waitForIdle()
        return nav
    }

    private fun NavHostController.depth() = currentBackStack.value.count { it.destination.route != null && it.destination !is androidx.navigation.NavGraph }

    @Test fun `double appui une seule fiche ouverte`() {
        val nav = navHost()
        rule.runOnIdle {
            nav.navigateOnce(Detail)
            nav.navigateOnce(Detail)
        }
        rule.runOnIdle { assertEquals(2, nav.depth()) }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        rule.runOnIdle { nav.navigateOnce(Detail) }
        rule.runOnIdle { assertEquals("appui volontaire plus tard : accepté", 3, nav.depth()) }
    }

    @Test fun `jamais deux lecteurs empiles donc jamais deux connexions`() {
        val nav = navHost()
        rule.runOnIdle { nav.navigateOnce(PlayerRoute(PlayKind.MOVIE, 1, "10")) }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        rule.runOnIdle { nav.navigateOnce(PlayerRoute(PlayKind.MOVIE, 1, "11")) }
        rule.runOnIdle {
            assertEquals(2, nav.depth())
            assertEquals("10", nav.currentBackStackEntry!!.arguments?.getString("id"))
        }
    }

    // --- mémoire du focus -----------------------------------------------------------------------------

    @Test fun `retour sur un ecran le focus revient sur l'element d'origine`() {
        val restorer = StateRestorationTester(rule)
        var memory: FocusMemory? = null
        var visible by mutableStateOf(true)
        restorer.setContent {
            val m = rememberFocusMemory().also { memory = it }
            if (visible) Column {
                AppButton("Lecture", {}, Modifier.focusMemory(m, "play"))
                AppButton("Épisode 3", {}, Modifier.focusMemory(m, "ep:1:3"))
            }
        }
        rule.runOnIdle { memory!!.requester("ep:1:3").requestFocus() }
        rule.onNodeWithText("Épisode 3").assertIsFocused()
        // l'écran est détruit puis recréé (navigation vers le lecteur et retour)
        restorer.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithText("Épisode 3").assertIsNotFocused()
        rule.runOnIdle { assertEquals("ep:1:3", memory!!.last); memory!!.restore() }
        rule.onNodeWithText("Épisode 3").assertIsFocused()
        rule.onNodeWithText("Lecture").assertIsNotFocused()
    }

    @Test fun `carte remplacee apres la lecture le focus reste dans la meme rangee`() {
        var memory: FocusMemory? = null
        var keys by mutableStateOf(listOf("A", "B"))
        var visible by mutableStateOf(true)
        rule.setContent {
            val m = rememberFocusMemory().also { memory = it }
            Column {
                AppButton("Menu", {}) // menu latéral : hors mémoire, comme dans l'app
                if (visible) keys.forEach { k -> androidx.compose.runtime.key(k) { AppButton("Carte $k", {}, Modifier.focusMemory(m, "continue|$k")) } }
            }
        }
        rule.runOnIdle { memory!!.requester("continue|A").requestFocus() }
        rule.onNodeWithText("Carte A").assertIsFocused()
        // départ vers le lecteur : la rangée disparaît ; au retour la carte A est devenue A2
        rule.runOnIdle { visible = false }
        rule.runOnIdle { keys = listOf("A2", "B"); visible = true }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("continue|A", memory!!.last)
            assertFalse("carte disparue : pas de faux succès", memory!!.restore())
            assertTrue(memory!!.restore(sameGroup = true))
        }
        rule.onNodeWithText("Menu").assertIsNotFocused()
        rule.onNodeWithText("Carte B").assertIsFocused()
    }
}
