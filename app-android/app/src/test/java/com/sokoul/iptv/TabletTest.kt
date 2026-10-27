package com.sokoul.iptv

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sokoul.iptv.player.timeshift.LiveTimeshift
import com.sokoul.iptv.ui.screens.player.SwipeZapDistance
import com.sokoul.iptv.ui.screens.player.TimeshiftBar
import com.sokoul.iptv.ui.screens.player.effectiveResizeMode
import com.sokoul.iptv.ui.screens.vod.DetailActionButton
import com.sokoul.iptv.ui.screens.vod.SecondaryAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.media3.ui.AspectRatioFrameLayout
import com.sokoul.iptv.ui.screens.player.swipeZapDelta
import com.sokoul.iptv.ui.screens.player.timeshiftTapDelta
import com.sokoul.iptv.ui.screens.tv.DrawerWidths
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.WindowClass
import com.sokoul.iptv.ui.theme.computeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tablette (Galaxy Tab S7 FE : 1280×800 dp) : tailles, zapping au doigt, tiroirs, pause du direct tactile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-land-xhdpi")
class TabletTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun Tablet(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLayout provides computeLayout(1280, 800, isTv = false), LocalReducedMotion provides true) { content() }
    }

    @Test fun `tablette - affiches plus grandes, environ 6 par rangee en paysage et 4 et demi en portrait`() {
        val land = computeLayout(1280, 800, isTv = false)
        assertEquals(WindowClass.EXPANDED, land.window)
        val content = 1280 - 88 - 40f
        val perRow = (content + 14) / (land.poster.value + 14)
        assertTrue("$perRow affiches", perRow in 5.8f..6.6f)
        assertTrue("grille alignée sur les affiches", land.gridCell.value in land.poster.value - 10..land.poster.value)
        val port = computeLayout(800, 1280, isTv = false)
        assertEquals(WindowClass.MEDIUM, port.window)
        val perRowPort = (800 - 88 - 24f + 12) / (port.poster.value + 12)
        assertTrue("$perRowPort affiches", perRowPort in 4.2f..5f)
        // la TV ne bouge pas
        assertEquals(computeLayout(960, 540, isTv = true).poster, computeLayout(960, 540, isTv = true).poster)
    }

    @Test fun `glisser vers le haut - chaine suivante, sens inverse par le reglage`() {
        assertEquals(1, swipeZapDelta(swipedUp = true, inverted = false))
        assertEquals(-1, swipeZapDelta(swipedUp = false, inverted = false))
        assertEquals(-1, swipeZapDelta(swipedUp = true, inverted = true))
        assertTrue(SwipeZapDistance.value >= 48f)
    }

    @Test fun `tiroirs du direct - TV et tablette inchanges, ecran etroit borne`() {
        assertEquals(DrawerWidths(320, 460, 460, 480), DrawerWidths.of(960))
        val portrait = DrawerWidths.of(800)
        assertTrue("catégories + chaînes tiennent en portrait", portrait.groups + portrait.channelsBeside <= 800 - 16)
        val phone = DrawerWidths.of(411)
        assertTrue(phone.channels <= 411 - 24 && phone.guide <= 411 - 24)
    }

    @Test fun `tap sur la barre de pause du direct - deplacement vers cet instant`() {
        assertEquals(-50_000L, timeshiftTapDelta(0.5f, start = 0, end = 100_000, position = 100_000))
        assertEquals(0L, timeshiftTapDelta(2f, start = 0, end = 100_000, position = 100_000))
        assertEquals(-100_000L, timeshiftTapDelta(-1f, start = 0, end = 100_000, position = 100_000))
    }

    @Test fun `pause du direct - boutons tactiles`() {
        val calls = mutableListOf<String>()
        val s = LiveTimeshift.State(active = true, paused = true, start = 0, end = 600_000, position = 300_000)
        rule.setContent {
            Tablet {
                TimeshiftBar(s, "Chaîne", onSeek = { calls += "seek $it" }, onToggle = { calls += "toggle" }, onLive = { calls += "live" })
            }
        }
        rule.onNodeWithText("+30 s").performClick()
        rule.onNodeWithText("−10 s").performClick()
        rule.onNodeWithText("Reprendre").performClick()
        rule.onNodeWithText("Retour au direct").performClick()
        assertEquals(listOf("seek 30000", "seek -10000", "toggle", "live"), calls)
    }

    @Test fun `portrait - la video n est jamais rognee, le reglage de cadrage vaut en paysage`() {
        for (i in 0..4) assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, effectiveResizeMode(i, portrait = true))
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, effectiveResizeMode(0, portrait = false))
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, effectiveResizeMode(1, portrait = false))
    }

    @Test fun `fiche - au doigt, les boutons ronds montrent leur libelle`() {
        var clicked = 0
        rule.setContent { Tablet { DetailActionButton(SecondaryAction("fav", "Ma liste", Icons.Default.Add) { clicked++ }) } }
        rule.onNodeWithText("Ma liste").performClick()
        assertEquals(1, clicked)
    }
}
