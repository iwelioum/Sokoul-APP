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
import androidx.compose.ui.test.onNodeWithText
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
import com.sokoul.iptv.ui.screens.player.AudioPanel
import com.sokoul.iptv.ui.screens.player.PlayerSource
import com.sokoul.iptv.ui.screens.player.SourcesPanel
import com.sokoul.iptv.ui.screens.player.vodTools
import com.sokoul.iptv.ui.screens.player.VodControls
import com.sokoul.iptv.ui.screens.player.clampSeek
import com.sokoul.iptv.ui.screens.player.seekDeltaLabel
import com.sokoul.iptv.ui.screens.player.vodPills
import com.sokoul.iptv.ui.screens.player.trackLabel
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
        val pills = vodPills(PlayerUi(), audio = "Français", subtitles = null, {}, {}, {}, {})
        assertEquals(listOf("Audio", "Sous-titres", "Source", "Plus d'options"), pills.map { it.label })
        assertEquals(listOf("Français", "Désactivés", "Source", null), pills.map { it.text })
        assertEquals(listOf("Épisodes", "Épisode suivant"), vodTools(PlayerUi(nextEpisode = next), hasEpisodes = true, {}, {}).map { it.label })
        assertEquals(emptyList<String>(), vodTools(PlayerUi(), hasEpisodes = false, {}, {}).map { it.label })
    }

    @Test fun `nom de langue des pistes jamais de code brut`() {
        assertEquals("Français", com.sokoul.iptv.ui.screens.player.trackLanguageName("fre"))
        assertEquals("Anglais", com.sokoul.iptv.ui.screens.player.trackLanguageName("en"))
        assertEquals(null, com.sokoul.iptv.ui.screens.player.trackLanguageName("und"))
        assertEquals(null, com.sokoul.iptv.ui.screens.player.trackLanguageName(""))
        assertEquals(null, com.sokoul.iptv.ui.screens.player.trackLanguageName("xyz"))
    }

    @Test fun `libelle de piste lisible jamais de nom d'encodeur ni de format interne`() {
        val label = ::trackLabel
        assertEquals("Piste principale · stéréo · AAC", label("und", "GalaxyTV - small excellence!", 2, "audio/mp4a-latm", true))
        assertEquals("Français · 5.1 · Dolby Digital+", label("fre", null, 6, "audio/eac3", true))
        assertEquals("Anglais · SDH", label("en", "English [SDH]", 0, null, false))
        assertEquals("Français · forcés", label("fr", "Forced", 0, null, false))
        assertEquals("Sous-titres", label(null, "X-MEDIA3-CUES", 0, null, false))
        assertEquals("Chinois · traditionnel", label("zh", "Chinese Traditional", 0, null, false))
        assertEquals("Portugais · Brésil", label("pt", "Portuguese (Brazil)", 0, null, false))
        val dup = com.sokoul.iptv.ui.screens.player.numberDuplicates(
            listOf(TrackOption("Anglais", null, 0, false), TrackOption("Arabe", null, 1, false), TrackOption("Anglais", null, 2, false)),
        ).map { it.label }
        assertEquals(listOf("Anglais (1)", "Arabe", "Anglais (2)"), dup)
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
            tools = listOf(
                PlayerAction("Épisodes", Icons.Default.ClosedCaption) { clicks += "episodes" },
                PlayerAction("Épisode suivant", Icons.Default.ClosedCaption) { clicks += "next" },
            ),
            pills = listOf(
                PlayerAction("Audio", Icons.Default.ClosedCaption, text = "Français") { clicks += "audio" },
                PlayerAction("Sous-titres", Icons.Default.ClosedCaption, text = "Désactivés") { clicks += "subs" },
                PlayerAction("Source", Icons.Default.ClosedCaption, text = "VOSTFR · 1080p") { clicks += "sources" },
                PlayerAction("Plus d'options", Icons.Default.ClosedCaption, iconOnly = true) { clicks += "more" },
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
        // bouton rond : son nom s'affiche au focus
        rule.onNodeWithText("Épisodes").assertExists()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Épisode suivant").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Audio : Français").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Sous-titres : Désactivés").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Source : VOSTFR · 1080p").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertTrue(clicks.toString(), "sources" in clicks) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Plus d'options").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithContentDescription("Position de lecture", substring = true).assertIsFocused()
    }

    @Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `TV tous les boutons tiennent sur une ligne sans depasser l'ecran`() {
        rule.setContent { Tv { Controls(mutableListOf(), remember { FocusRequester() }, remember { FocusRequester() }) } }
        rule.waitForIdle()
        val play = rule.onNodeWithContentDescription("Pause").fetchSemanticsNode().boundsInRoot
        listOf("Épisodes", "Épisode suivant", "Audio : Français", "Sous-titres : Désactivés", "Source : VOSTFR · 1080p", "Plus d'options").forEach { label ->
            val b = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            with(rule.density) {
                assertTrue("$label dans l'écran : ${b.right.toDp()}", b.right.toDp() <= 960.dp)
                assertTrue("$label sur la même ligne que ⏯", kotlin.math.abs(b.center.y - play.center.y) < 30f)
                assertTrue("$label sur une ligne : ${b.height.toDp()}", b.height.toDp() < 50.dp)
            }
        }
    }

    @Test fun `panneau audio pistes du fichier puis autres langues avec qualite`() {
        val picks = mutableListOf<String>()
        val vf = PlayerSource("m1", com.sokoul.iptv.domain.SourceOrigin.IPTV, com.sokoul.iptv.domain.AudioLanguage.VF, quality = com.sokoul.iptv.domain.VideoQuality.HD_720, where = "Abonnement IPTV", current = true)
        val vo = PlayerSource("rd:x", com.sokoul.iptv.domain.SourceOrigin.DEBRID, com.sokoul.iptv.domain.AudioLanguage.VO, quality = com.sokoul.iptv.domain.VideoQuality.UHD_4K, where = "Real-Debrid · Remux")
        rule.setContent {
            Tv {
                AudioPanel(
                    tracks = listOf(TrackOption("Français · 5.1", null, 0, true), TrackOption("Anglais · stéréo", null, 1, false)),
                    sources = listOf(vf, vo), resumeAt = "42:10",
                    onTrack = { picks += "track:" + it.label }, onSource = { picks += "source:" + it.id },
                )
            }
        }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithContentDescription("Français · 5.1, sélectionné").assertIsFocused()
        rule.onNodeWithText("Le lecteur ouvre un autre fichier et reprend à 42:10 : l'image peut changer").assertExists()
        rule.onNodeWithText("Image meilleure : 4K").assertExists()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Anglais · stéréo").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Version originale (VO), autre fichier, VO · 4K").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf("track:Anglais · stéréo", "source:rd:x"), picks) }
    }

    @Test fun `panneau sources focus sur la source en cours et origine lisible`() {
        val picks = mutableListOf<String>()
        val a = PlayerSource("m1", com.sokoul.iptv.domain.SourceOrigin.IPTV, com.sokoul.iptv.domain.AudioLanguage.VF, quality = com.sokoul.iptv.domain.VideoQuality.FHD_1080, where = "Abonnement IPTV · « FILMS FR »", fileName = "FR - Dune (2021) FHD", basis = com.sokoul.iptv.ui.screens.player.QualityBasis.MEASURED)
        val b = a.copy(id = "m2", quality = com.sokoul.iptv.domain.VideoQuality.UHD_4K, where = "Abonnement IPTV · « FILMS 4K »", fileName = "Dune 4K", basis = com.sokoul.iptv.ui.screens.player.QualityBasis.NAME, current = true)
        val c = PlayerSource("rd:k", com.sokoul.iptv.domain.SourceOrigin.DEBRID, com.sokoul.iptv.domain.AudioLanguage.MULTI, quality = com.sokoul.iptv.domain.VideoQuality.UHD_4K, where = "Real-Debrid · Remux · 60 Go · prête")
        rule.setContent { Tv { SourcesPanel(listOf(a, b, c), resumeAt = "1:02:00") { picks += it.id } } }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("3 sources trouvées · changer reprend à 1:02:00").assertExists()
        rule.onNodeWithText("ABONNEMENT IPTV (2)").assertExists()
        rule.onNodeWithText("Abonnement IPTV · « FILMS FR » · qualité mesurée").assertExists()
        rule.onNodeWithText("Image moins bonne : 1080p").assertExists()
        rule.onNodeWithContentDescription("En cours, Français (VF) · 4K, Abonnement IPTV · « FILMS 4K », sélectionné").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Plusieurs langues (MULTI) · 4K, Real-Debrid · Remux · 60 Go · prête").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf("rd:k"), picks) }
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
