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
import com.sokoul.iptv.ui.screens.player.VersionPanel
import com.sokoul.iptv.ui.screens.player.PlayerSource
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
        val pills = vodPills("Français · 1080p · IPTV", subtitles = null, {}, {}, {})
        assertEquals(listOf("Langue et qualité", "Sous-titres", "Plus d'options"), pills.map { it.label })
        assertEquals(listOf("Français · 1080p · IPTV", "Désactivés", null), pills.map { it.text })
        // la pastille dit ce qu'on entend (piste choisie), la qualité et l'origine ; piste sans langue : langue de la source
        val cur = PlayerSource("rd:k", com.sokoul.iptv.domain.SourceOrigin.DEBRID, com.sokoul.iptv.domain.AudioLanguage.MULTI, quality = com.sokoul.iptv.domain.VideoQuality.UHD_4K, where = "x", current = true)
        assertEquals("Anglais · 4K · RD", com.sokoul.iptv.ui.screens.player.versionPillText(cur, "Anglais"))
        assertEquals("MULTI · 4K · RD", com.sokoul.iptv.ui.screens.player.versionPillText(cur, "Piste principale"))
        assertEquals("Langue et qualité", com.sokoul.iptv.ui.screens.player.versionPillText(null, null))
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
        // tap sur la barre tactile : piste rentrée de la moitié du repère de chaque côté
        assertEquals(0.5f, com.sokoul.iptv.ui.screens.player.scrubFraction(500f, 1000f, 20f), 0.001f)
        assertEquals(0f, com.sokoul.iptv.ui.screens.player.scrubFraction(5f, 1000f, 20f), 0.001f)
        assertEquals(1f, com.sokoul.iptv.ui.screens.player.scrubFraction(995f, 1000f, 20f), 0.001f)
        assertEquals(0f, com.sokoul.iptv.ui.screens.player.scrubFraction(10f, 30f, 20f), 0.001f)
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
                PlayerAction("Langue et qualité", Icons.Default.ClosedCaption, text = "Français · 1080p · IPTV") { clicks += "versions" },
                PlayerAction("Sous-titres", Icons.Default.ClosedCaption, text = "Désactivés") { clicks += "subs" },
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
        rule.onNodeWithContentDescription("Langue et qualité : Français · 1080p · IPTV").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertTrue(clicks.toString(), "versions" in clicks) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("Sous-titres : Désactivés").assertIsFocused()
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
        listOf("Épisodes", "Épisode suivant", "Langue et qualité : Français · 1080p · IPTV", "Sous-titres : Désactivés", "Plus d'options").forEach { label ->
            val b = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            with(rule.density) {
                assertTrue("$label dans l'écran : ${b.right.toDp()}", b.right.toDp() <= 960.dp)
                assertTrue("$label sur la même ligne que ⏯", kotlin.math.abs(b.center.y - play.center.y) < 30f)
                assertTrue("$label sur une ligne : ${b.height.toDp()}", b.height.toDp() < 50.dp)
            }
        }
    }

    @Test fun `panneau langue et qualite - une ligne par version, sources depliables, origine et qualite visibles`() {
        val picks = mutableListOf<String>()
        val iptv = PlayerSource(
            "m1", com.sokoul.iptv.domain.SourceOrigin.IPTV, com.sokoul.iptv.domain.AudioLanguage.VF, quality = com.sokoul.iptv.domain.VideoQuality.FHD_1080,
            where = "Abonnement IPTV · « FILMS FR »", basis = com.sokoul.iptv.ui.screens.player.QualityBasis.MEASURED, current = true,
        )
        val rdVf = PlayerSource("rd:a", com.sokoul.iptv.domain.SourceOrigin.DEBRID, com.sokoul.iptv.domain.AudioLanguage.VF, quality = com.sokoul.iptv.domain.VideoQuality.FHD_1080, where = "Real-Debrid · Remux · 60 Go · prête", fileName = "Dune.2021.1080p.REMUX")
        val rdVo = PlayerSource("rd:b", com.sokoul.iptv.domain.SourceOrigin.DEBRID, com.sokoul.iptv.domain.AudioLanguage.VO, quality = com.sokoul.iptv.domain.VideoQuality.UHD_4K, where = "Real-Debrid · WEB · 18 Go")
        rule.setContent {
            Tv {
                VersionPanel(
                    tracks = listOf(TrackOption("Français · 5.1", null, 0, true)), sources = listOf(rdVf, iptv, rdVo),
                    languages = listOf("VF", "MULTI", "VOSTFR", "VO"), search = com.sokoul.iptv.ui.screens.player.SourceSearch.DONE, resumeAt = "42:10",
                    onTrack = { picks += "track:" + it.label }, onSource = { picks += it.id },
                )
            }
        }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("Vous regardez : Français (VF) · 1080p (mesurée) · Abonnement IPTV · « FILMS FR »").assertExists()
        rule.onNodeWithText("Son : Français · 5.1").assertExists()
        rule.onNodeWithText("3 sources trouvées : IPTV 1 · RD 2 · changer reprend à 42:10").assertExists()
        // focus sur la version en cours ; la source Real-Debrid de la même version est repliée dessous
        val vf = "En cours, Français (VF) · 1080p, Abonnement IPTV · « FILMS FR », 1 autre source, sélectionné"
        rule.onNodeWithContentDescription(vf).assertIsFocused()
        rule.onNodeWithText("1 autre source (RD 1)").assertExists()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Dune.2021.1080p.REMUX").assertExists()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Real-Debrid, Real-Debrid · Remux · 60 Go · prête").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        // GAUCHE replie et revient sur la version
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithContentDescription(vf).assertIsFocused()
        rule.onNodeWithText("Dune.2021.1080p.REMUX").assertDoesNotExist()
        // autre langue : qualité et origine visibles avant de choisir
        rule.onNodeWithText("Image meilleure : 4K").assertExists()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Version originale (VO) · 4K, Real-Debrid · WEB · 18 Go").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(listOf("rd:a", "rd:b"), picks) }
    }

    @Test fun `panneau audio du direct - seulement les pistes`() {
        rule.setContent {
            Tv {
                VersionPanel(
                    tracks = listOf(TrackOption("Français", null, 0, true), TrackOption("Anglais", null, 1, false)), sources = emptyList(),
                    languages = listOf("VF"), search = com.sokoul.iptv.ui.screens.player.SourceSearch.DONE, resumeAt = "0:00", onTrack = {}, onSource = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("Audio").assertExists()
        rule.onNodeWithContentDescription("Français, sélectionné").assertIsFocused()
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
