package com.sokoul.iptv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sokoul.iptv.ui.Dest
import com.sokoul.iptv.ui.LocalTopBar
import com.sokoul.iptv.ui.Shell
import com.sokoul.iptv.ui.TAB_SETTLE_MS
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.nav.HomeRoute
import com.sokoul.iptv.ui.nav.MoviesRoute
import com.sokoul.iptv.ui.nav.MyListRoute
import com.sokoul.iptv.ui.nav.SearchRoute
import com.sokoul.iptv.ui.nav.SeriesRoute
import com.sokoul.iptv.ui.nav.SettingsRoute
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.computeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Univers Streaming sur TV : onglets du haut, ouverture au focus après arrêt, boutons à OK, chaîne de Retour. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class ShellUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Nombre d'ouvertures de chaque page. */
    private val opened = mutableMapOf<Dest, Int>()
    private var lastBar: com.sokoul.iptv.ui.TopBarState? = null

    @Composable
    private fun Page(d: Dest) {
        LaunchedEffect(Unit) { opened[d] = (opened[d] ?: 0) + 1 }
        val bar = LocalTopBar.current
        lastBar = bar
        val play = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
        bar?.down = { play }
        // comme les vraies pages : un groupe plein écran qui commence sous les onglets (en haut de l'écran)
        Box(Modifier.fillMaxSize().focusGroup()) {
            Box(Modifier.padding(top = 200.dp, start = 48.dp)) {
                // OK simule la descente dans les rangées
                SokoulButton("Lecture ${d.label}", { bar?.deep = true }, modifier = Modifier.focusRequester(play))
            }
        }
    }

    private fun setUp() {
        // sortir de la fenêtre anti-double-appui de navigateOnce laissée par un test précédent
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2))
        com.sokoul.iptv.ui.nav.resetNavigationDebounce()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLayout provides computeLayout(960, 540, isTv = true), LocalReducedMotion provides true) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = HomeRoute) {
                    composable<HomeRoute> { Shell(nav, Dest.HOME) { Page(Dest.HOME) } }
                    composable<MoviesRoute> { Shell(nav, Dest.MOVIES) { Page(Dest.MOVIES) } }
                    composable<SeriesRoute> { Shell(nav, Dest.SERIES) { Page(Dest.SERIES) } }
                    composable<MyListRoute> { Shell(nav, Dest.MY_LIST) { Page(Dest.MY_LIST) } }
                    composable<SearchRoute> { Shell(nav, Dest.SEARCH) { Text("Page recherche") } }
                    composable<SettingsRoute> { Shell(nav, Dest.SETTINGS) { Text("Page réglages") } }
                    composable<com.sokoul.iptv.ui.nav.TvRoute> { LaunchedEffect(Unit) { opened[Dest.LIVE] = (opened[Dest.LIVE] ?: 0) + 1 }; Text("Page TV") }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun key(k: Key, wait: Long = 60) {
        rule.onRoot().performKeyInput { pressKey(k) }
        rule.mainClock.advanceTimeBy(wait)
    }

    /** Depuis le contenu d'Accueil, ▲ amène sur l'onglet actif. */
    private fun focusTabsFromHome() {
        rule.onNodeWithText("Lecture Accueil").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.mainClock.advanceTimeBy(100)
        key(Key.DirectionUp)
        rule.onNodeWithText("Accueil").assertIsFocused()
    }

    @Test fun `traverser la barre jusqu'aux reglages n'ouvre aucune page intermediaire`() {
        setUp()
        focusTabsFromHome()
        // Films, Séries, Ma liste, Sagas, Recherche, Guide, Enregistrements, TV, Réglages : appuis plus rapides que le délai d’arrêt
        repeat(9) { key(Key.DirectionRight, wait = TAB_SETTLE_MS / 3) }
        rule.onNodeWithContentDescription("Réglages").assertIsFocused()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("aucune page traversée ne s'ouvre", null, opened[Dest.MOVIES] ?: opened[Dest.SERIES] ?: opened[Dest.MY_LIST] ?: opened[Dest.SAGAS])
        rule.onNodeWithText("Page réglages").assertDoesNotExist()
        // ⚙ est un bouton : OK l'ouvre
        key(Key.DirectionCenter, wait = 1_200)
        rule.onNodeWithText("Page réglages").assertExists()
    }

    @Test fun `s'arreter sur un onglet de contenu ouvre sa page et garde le focus sur l'onglet`() {
        setUp()
        focusTabsFromHome()
        key(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(TAB_SETTLE_MS + 1_200)
        assertEquals(1, opened[Dest.MOVIES])
        rule.onNodeWithText("Lecture Films").assertExists()
        rule.onNodeWithText("Films").assertIsFocused()
        // ▼ : on descend dans la page
        key(Key.DirectionDown)
        rule.onNodeWithText("Lecture Films").assertIsFocused()
    }

    @Test fun `le bouton TV exige OK`() {
        setUp()
        focusTabsFromHome()
        repeat(8) { key(Key.DirectionRight, wait = TAB_SETTLE_MS / 3) }
        rule.onNodeWithContentDescription("Passer à la TV en direct").assertIsFocused()
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals("jamais de bascule en TV au simple passage", null, opened[Dest.LIVE])
        key(Key.DirectionCenter, wait = 1_200)
        assertEquals(1, opened[Dest.LIVE])
    }

    @Test fun `Retour depuis les onglets d'une autre rubrique ramene a l'Accueil`() {
        setUp()
        focusTabsFromHome()
        key(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(TAB_SETTLE_MS + 1_200)
        rule.onNodeWithText("Films").assertIsFocused()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(1_200)
        rule.onNodeWithText("Lecture Accueil").assertExists()
    }

    @Test fun `onglets effaces dans les rangees puis Retour les ramene`() {
        setUp()
        rule.onNodeWithText("Lecture Accueil").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.mainClock.advanceTimeBy(100)
        // OK sur le contenu = « descendu dans les rangées » : les onglets ne sont plus atteignables
        key(Key.DirectionCenter)
        // horloge figée : on transmet à Compose l'état modifié par le clic (fait par la boucle d'images sur la box)
        rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeBy(400)
        key(Key.DirectionUp)
        rule.onNodeWithText("Lecture Accueil").assertIsFocused()
        // Retour (pas de remontée fournie par la page) : focus sur les onglets, qui réapparaissent
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("Accueil").assertIsFocused()
        assertTrue(opened[Dest.HOME] == 1)
    }
}
