package com.sokoul.iptv

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.ui.screens.player.NextUpCard
import com.sokoul.iptv.ui.screens.player.PlayerAction
import com.sokoul.iptv.ui.screens.player.PlayerUi
import com.sokoul.iptv.ui.screens.player.TrackOption
import com.sokoul.iptv.ui.screens.player.TracksPanel
import com.sokoul.iptv.ui.screens.player.VodControls
import com.sokoul.iptv.ui.screens.player.clampSeek
import com.sokoul.iptv.ui.screens.player.seekDeltaLabel
import com.sokoul.iptv.ui.screens.player.vodPills
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.computeLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lecteur « icônes + texte » : ordre fixe des boutons, D-pad, panneau Audio et sous-titres, carte suivante. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class PlayerUiTest {
    @get:Rule val rule = createComposeRule()

    private val next = EpisodeEntity(1, "S", "S-1-4", 1, 4, "Show - S01E04 - Digging Deep", null, null, 1500, "mkv", null, null)

    @Composable
    private fun Tv(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLayout provides computeLayout(960, 540, isTv = true), LocalReducedMotion provides true) { content() }
    }

    @Test fun `ordre des pastilles identique pour tous les titres`() {
        val serie = vodPills(PlayerUi(nextEpisode = next), hasEpisodes = true, {}, {}, {}, {}).map { it.label }
        assertEquals(listOf("Épisodes", "Suivant", "Audio et sous-titres", "Plus"), serie)
        val film = vodPills(PlayerUi(), hasEpisodes = false, {}, {}, {}, {}).map { it.label }
        assertEquals(listOf("Audio et sous-titres", "Plus"), film)
    }

    @Test fun `nom de langue des pistes jamais de code brut`() {
        assertEquals("Français", com.sokoul.iptv.ui.screens.player.trackLanguageName("fre"))
        assertEquals("Anglais", com.sokoul.iptv.ui.screens.player.trackLanguageName("en"))
        assertEquals(null, com.sokoul.iptv.ui.screens.player.trackLanguageName("und"))
        assertEquals(null, com.sokoul.iptv.ui.screens.player.trackLanguageName(""))
        assertEquals(null, com.sokoul.iptv.ui.screens.player.trackLanguageName("xyz"))
    }

    @Test fun `libelle episode sans repetition`() {
        assertEquals("S1 · É3", com.sokoul.iptv.ui.screens.player.seLabel(1, 3, "Épisode 3"))
        assertEquals("S1 · É3 · Pilote", com.sokoul.iptv.ui.screens.player.seLabel(1, 3, "Pilote"))
    }

    @Test fun `saut borne au debut et une seconde avant la fin`() {
        assertEquals(0L, clampSeek(-5_000, 60_000))
        assertEquals(59_000L, clampSeek(90_000, 60_000))
        assertEquals(30_000L, clampSeek(30_000, 60_000))
        assertEquals("durée inconnue : pas de borne haute", 90_000L, clampSeek(90_000, 0))
        assertEquals("30 s", seekDeltaLabel(-30_000))
        assertEquals("1 min 30", seekDeltaLabel(90_000))
        assertEquals("2 min", seekDeltaLabel(120_000))
    }

    @Composable
    private fun Controls(clicks: MutableList<String>, scrubber: FocusRequester, play: FocusRequester) {
        VodControls(
            position = 600_000, duration = 2_700_000, preview = null, buffering = false, playing = true,
            scrubberFocus = scrubber, playFocus = play,
            onBack10 = { clicks += "-10" }, onPlayPause = { clicks += "pp" }, onFwd30 = { clicks += "+30" },
            pills = listOf(
                PlayerAction("Épisodes", Icons.Default.ClosedCaption) { clicks += "episodes" },
                PlayerAction("Suivant", Icons.Default.ClosedCaption) { clicks += "next" },
                PlayerAction("Audio et sous-titres", Icons.Default.ClosedCaption) { clicks += "tracks" },
                PlayerAction("Plus", Icons.Default.ClosedCaption) { clicks += "more" },
            ),
            onSeekStep = { clicks += "seek:$it" }, onSeekTo = {}, onInteract = {},
        )
    }

    @Test fun `barre puis bas va sur pause puis droite parcourt les boutons et haut revient a la barre`() {
        val clicks = mutableListOf<String>()
        lateinit var scrubber: FocusRequester
        rule.setContent {
            Tv {
                scrubber = remember { FocusRequester() }
                Controls(clicks, scrubber, remember { FocusRequester() })
            }
        }
        rule.runOnIdle { scrubber.requestFocus() }
        rule.onNodeWithContentDescription("Position de lecture", substring = true).assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.runOnIdle { assertEquals(listOf("seek:30000"), clicks) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Pause").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Avancer de 30 secondes").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Épisodes").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Audio et sous-titres").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertTrue(clicks.toString(), "tracks" in clicks) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithContentDescription("Position de lecture", substring = true).assertIsFocused()
    }

    @Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `TV tous les boutons tiennent sur une ligne sans depasser l'ecran`() {
        rule.setContent { Tv { Controls(mutableListOf(), remember { FocusRequester() }, remember { FocusRequester() }) } }
        rule.waitForIdle()
        val play = rule.onNodeWithContentDescription("Pause").fetchSemanticsNode().boundsInRoot
        listOf("Épisodes", "Suivant", "Audio et sous-titres", "Plus").forEach { label ->
            val b = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            with(rule.density) {
                assertTrue("$label dans l'écran : ${b.right.toDp()}", b.right.toDp() <= 960.dp)
                assertTrue("$label sur la même ligne que ⏯", kotlin.math.abs(b.center.y - play.center.y) < 30f)
                assertTrue("$label sur une ligne : ${b.height.toDp()}", b.height.toDp() < 50.dp)
            }
        }
    }

    @Test fun `panneau audio et sous-titres focus sur la piste choisie et colonnes au D-pad`() {
        val picks = mutableListOf<String>()
        rule.setContent {
            Tv {
                TracksPanel(
                    audio = listOf(TrackOption("Français · 5.1", null, 0, true), TrackOption("Anglais · stéréo", null, 1, false)),
                    text = listOf(TrackOption("Désactivés", null, -1, true), TrackOption("Français", null, 0, false), TrackOption("Anglais", null, 1, false)),
                ) { type, o -> picks += (if (type == C.TRACK_TYPE_TEXT) "text:" else "audio:") + o.label }
            }
        }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithContentDescription("Français · 5.1, sélectionné").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Anglais · stéréo").assertIsFocused()
        // ▶ : on arrive sur le sous-titre en cours, pas sur la ligne d'en face
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Désactivés, sélectionné").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf("text:Français"), picks) }
    }

    @Test fun `carte episode suivant compte a rebours et focus sur lire maintenant`() {
        val clicks = mutableListOf<String>()
        rule.setContent {
            Tv {
                NextUpCard(next, seconds = 12, fallbackImage = "https://example.org/backdrop.jpg", playFocus = remember { FocusRequester() }, modifier = androidx.compose.ui.Modifier, onPlay = { clicks += "play" }, onCancel = { clicks += "cancel" })
            }
        }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithContentDescription("Lire maintenant").assertIsFocused()
        rule.onNodeWithContentDescription("Vignette de l'épisode 4").assertExists()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf("play"), clicks) }
    }
}
