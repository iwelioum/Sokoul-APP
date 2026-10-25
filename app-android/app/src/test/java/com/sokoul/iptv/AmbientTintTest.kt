package com.sokoul.iptv

import android.app.Application
import androidx.core.graphics.ColorUtils
import com.sokoul.iptv.ui.components.AmbientTint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lueur d'ambiance : quelle que soit l'affiche, la teinte reste sombre, colorée et lisible derrière le texte. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AmbientTintTest {
    private fun hsl(argb: Int) = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }

    @Test fun `teinte bornee pour le blanc le noir le gris et les couleurs vives`() {
        val samples = listOf(0xFFFFFFFF, 0xFF000000, 0xFF808080, 0xFFFF0000, 0xFF00FFAA, 0xFF102040, 0xFFFFEE00).map { it.toInt() }
        samples.forEach { c ->
            val (_, s, l) = hsl(AmbientTint.clamp(c)).toList()
            // marge d'arrondi : la conversion passe par des entiers 8 bits
            assertTrue("luminosité de ${Integer.toHexString(c)} = $l", l in 0.11f..0.27f)
            if (c != 0xFFFFFFFF.toInt() && c != 0xFF000000.toInt() && c != 0xFF808080.toInt()) {
                assertTrue("saturation de ${Integer.toHexString(c)} = $s", s in 0.33f..0.82f)
            }
        }
    }

    @Test fun `la teinte conserve la couleur d'origine`() {
        val red = hsl(AmbientTint.clamp(0xFFE53935.toInt()))[0]
        val blue = hsl(AmbientTint.clamp(0xFF1E88E5.toInt()))[0]
        assertTrue(red < 15f || red > 345f)
        assertTrue(blue in 195f..225f)
    }
}
