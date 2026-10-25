package com.sokoul.iptv

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.repo.EpisodeView
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.screens.vod.DetailHeroData
import com.sokoul.iptv.ui.screens.vod.DetailScaffold
import com.sokoul.iptv.ui.screens.vod.EpisodeShelf
import com.sokoul.iptv.ui.screens.vod.PrimaryAction
import com.sokoul.iptv.ui.screens.vod.SeasonTabs
import com.sokoul.iptv.ui.screens.vod.SecondaryAction
import com.sokoul.iptv.ui.screens.vod.Synopsis
import com.sokoul.iptv.ui.screens.vod.detailSection
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
import org.robolectric.annotation.GraphicsMode

/** Fiche « style Apple TV » : boutons, focus à la télécommande, saisons/épisodes, mise en page TV et téléphone. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class DetailUiTest {
    @get:Rule val rule = createComposeRule()

    private val hero = DetailHeroData(
        title = "Someone, Someday", logo = null, backdrop = null, poster = null, rating = 7.4,
        facts = listOf("2026", "1 saison", "2 versions"), certification = "12", quality = "HD",
        genres = "Mystère, Drame", tagline = null,
        synopsis = Synopsis("Une patronne d'application de rencontre se lie d'amitié avec quelqu'un qui veut se venger. " .repeat(4), true),
        credit = "Création : Chad Vidanes",
    )

    private fun ep(e: Int, s: Int = 1) = EpisodeView(EpisodeEntity(1, "S", "S-$s-$e", s, e, "Épisode $e", "Résumé de l'épisode $e", null, 2700, "mkv", null, null), "Épisode $e", "Résumé de l'épisode $e", true, null, 45).copy(title = "Titre S$s-$e")

    @Composable
    private fun Page(clicks: MutableList<String>, tv: Boolean = true, memoryOut: (FocusMemory) -> Unit = {}) {
        val layout = if (tv) computeLayout(960, 540, isTv = true) else computeLayout(411, 891, isTv = false)
        CompositionLocalProvider(LocalLayout provides layout, LocalReducedMotion provides true) {
            val memory = rememberFocusMemory().also(memoryOut)
            DetailScaffold(
                rememberNavController(), hero,
                PrimaryAction("Reprendre S1 · É3", "Reste 18 min", 0.4f) { clicks += "play" },
                primaryLoading = false,
                actions = listOf(
                    SecondaryAction("fav", "Ma liste", Icons.Default.Add) { clicks += "fav" },
                    SecondaryAction("trailer", "Bande-annonce", Icons.Default.Theaters) { clicks += "trailer" },
                    SecondaryAction("more", "Plus", Icons.Default.MoreHoriz) { clicks += "more" },
                ),
                hint = "Épisodes", memory = memory, onSynopsis = {},
            ) {
                detailSection("episodes", null) {
                    var season by androidx.compose.runtime.remember { mutableStateOf(1) }
                    Column {
                        SeasonTabs(listOf(1, 2), season, { false }, memory, onSelect = { season = it; clicks += "season:$it" }, onLongClick = {})
                        EpisodeShelf(
                            season, if (season == 1) (1..6).map { ep(it) } else (1..4).map { ep(it, 2) }, { null },
                            currentKey = 1 to 3, memory = memory, onPlay = { clicks += "ep:${it.entity.season}:${it.entity.episode}" }, onLongClick = {},
                        )
                    }
                }
            }
        }
    }

    @Test fun `boutons dans l'ordre et navigables a la telecommande`() {
        val clicks = mutableListOf<String>()
        var memory: FocusMemory? = null
        rule.setContent { Page(clicks) { memory = it } }
        rule.runOnIdle { memory!!.requester("play").requestFocus() }
        rule.onNodeWithContentDescription("Reprendre S1 · É3, Reste 18 min").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Ma liste").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Bande-annonce").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Plus").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf("play", "more"), clicks) }
    }

    @Test fun `bas depuis les boutons puis les onglets mene a l'episode courant`() {
        val clicks = mutableListOf<String>()
        var memory: FocusMemory? = null
        rule.setContent { Page(clicks) { memory = it } }
        rule.runOnIdle { memory!!.requester("play").requestFocus() }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("Saison 1").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithContentDescription("Épisode 3 :", substring = true).assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertTrue(clicks.toString(), "ep:1:3" in clicks) }
        // ▲▲ : retour aux onglets puis au bouton principal
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Reprendre S1 · É3, Reste 18 min").assertIsFocused()
    }

    @Test fun `passer sur un onglet affiche la saison`() {
        val clicks = mutableListOf<String>()
        var memory: FocusMemory? = null
        rule.setContent { Page(clicks) { memory = it } }
        rule.runOnIdle { memory!!.requester("play").requestFocus() }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("Saison 1").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("Saison 2").assertIsFocused()
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle { assertTrue(clicks.toString(), "season:2" in clicks) }
        rule.onNodeWithContentDescription("Épisode 1 : Titre S2-1").assertExists()
        rule.onAllNodesWithContentDescription("Titre S1-", substring = true).assertCountEquals(0)
    }

    private fun assertFirstScreen(heightDp: Int) {
        val play = rule.onNodeWithContentDescription("Reprendre S1 · É3, Reste 18 min").fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithText("Reprendre S1 · É3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            assertTrue("bouton principal visible sans défiler : bas à ${play.bottom.toDp()}", play.bottom.toDp() <= heightDp.dp)
            assertTrue("libellé sur une ligne : ${label.height.toDp()}", label.height.toDp() < 26.dp)
        }
    }

    @Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `TV le premier ecran montre le titre et les boutons sans rien couper`() {
        rule.setContent { Page(mutableListOf()) }
        rule.waitForIdle()
        assertFirstScreen(540)
        val title = rule.onNodeWithText("Someone, Someday").fetchSemanticsNode().boundsInRoot
        with(rule.density) { assertTrue("titre visible : ${title.top.toDp()}", title.top.toDp() >= 0.dp) }
    }

    @Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `telephone bouton principal pleine largeur et actions lisibles`() {
        rule.setContent { Page(mutableListOf(), tv = false) }
        rule.waitForIdle()
        assertFirstScreen(891)
        val play = rule.onNodeWithContentDescription("Reprendre S1 · É3, Reste 18 min").fetchSemanticsNode().boundsInRoot
        with(rule.density) { assertTrue("pleine largeur : ${play.width.toDp()}", play.width.toDp() > 300.dp) }
        rule.onNodeWithText("Bande-annonce").assertExists()
    }
}
