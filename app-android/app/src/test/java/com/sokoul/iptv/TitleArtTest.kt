package com.sokoul.iptv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.ui.components.TitleArt
import com.sokoul.iptv.ui.components.fitTitle
import com.sokoul.iptv.ui.theme.TitleArtSpecs
import com.sokoul.iptv.ui.theme.TitleStyle
import com.sokoul.iptv.ui.theme.TitleStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Titres générés : style choisi par le genre, et jamais de texte coupé. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class TitleArtTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `style selon le genre le plus marque`() {
        assertEquals(TitleStyle.HORROR, TitleStyles.of(listOf("Drame", "Horreur")))
        assertEquals(TitleStyle.NEON, TitleStyles.of(listOf("Action", "Science-Fiction")))
        assertEquals(TitleStyle.NEON, TitleStyles.of(listOf("Science-Fiction & Fantastique")))
        assertEquals(TitleStyle.PLAYFUL, TitleStyles.of(listOf("Animation", "Aventure")))
        assertEquals(TitleStyle.STAMP, TitleStyles.of(listOf("Crime", "Drame")))
        assertEquals(TitleStyle.STAMP, TitleStyles.of(listOf("Mystère")))
        assertEquals(TitleStyle.METAL, TitleStyles.of(listOf("Action")))
        assertEquals(TitleStyle.GOLD, TitleStyles.of(listOf("Guerre", "Drame")))
        assertEquals(TitleStyle.WESTERN, TitleStyles.of(listOf("Western")))
        assertEquals(TitleStyle.DOC, TitleStyles.of(listOf("Documentaire")))
        assertEquals(TitleStyle.ELEGANT, TitleStyles.of(listOf("Drame", "Romance")))
        assertEquals(TitleStyle.ELEGANT, TitleStyles.of(emptyList()))
    }

    @Test fun `un titre long tient sans debordement, chaque style`() {
        val title = "Les Aventures extraordinaires d'Adèle Blanc-Sec"
        var measurer: TextMeasurer? = null
        rule.setContent { measurer = rememberTextMeasurer() }
        rule.waitForIdle()
        val m = measurer!!
        listOf(280, 520).forEach { width ->
            TitleStyle.entries.forEach { style ->
                val fit = fitTitle(m, title, TitleArtSpecs.of(style), 46f, width, maxLines = 2)
                val r = m.measure(fit.text, fit.style, maxLines = fit.lines, constraints = Constraints(maxWidth = width))
                assertFalse("$style $width px déborde", r.hasVisualOverflow)
                assertTrue("$style : au plus 3 lignes", fit.lines <= 3)
            }
        }
        // titre court, place large : pleine taille
        val short = fitTitle(m, "Dune", TitleArtSpecs.of(TitleStyle.ELEGANT), 46f, 520, maxLines = 2)
        assertEquals(46f * 1.05f, short.style.fontSize.value, 0.01f)
        assertEquals(2, short.lines)
    }

    @Test fun `le titre reste lisible par son nom, pas en capitales`() {
        rule.setContent { TitleArt("Someone, Someday", listOf("Mystère"), 40.sp, Modifier.width(400.dp)) }
        rule.onNodeWithText("Someone, Someday").assertExists()
    }
}
