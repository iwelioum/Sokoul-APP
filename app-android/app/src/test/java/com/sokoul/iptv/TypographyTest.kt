package com.sokoul.iptv

import com.sokoul.iptv.data.repo.RowKicker
import com.sokoul.iptv.ui.components.rowTitle
import com.sokoul.iptv.ui.theme.SERIF_MIN_SP
import com.sokoul.iptv.ui.theme.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Typographie « cinéma » : la police à empattements reste lisible à 3 m, les rangées portent une étiquette. */
class TypographyTest {

    private val uiDir = listOf("src/main/java/com/sokoul/iptv/ui", "app/src/main/java/com/sokoul/iptv/ui")
        .map(::File).first { it.isDirectory }

    @Test fun `la police a empattements n est jamais sous 20 sp`() {
        listOf(Type.Display, Type.ScreenTitle, Type.Section, Type.Tab, Type.PlayerTitle).forEach {
            assertTrue("${it.fontSize} < $SERIF_MIN_SP sp", it.fontSize.value >= SERIF_MIN_SP)
        }
    }

    @Test fun `la police a empattements ne sert que via Type`() {
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/ui/theme/") }
            .filter { it.readText().contains("SerifDisplay") }.map { it.name }.toList()
        assertTrue("SerifDisplay utilisée hors du thème : $offenders", offenders.isEmpty())
    }

    @Test fun `etiquettes des rangees`() {
        assertNull(RowKicker.of("continue", "Reprendre la lecture")) // redondant
        assertEquals("REPRENDRE", RowKicker.of("continue", "Continuer"))
        assertEquals("POUR VOUS", RowKicker.of("byw-12", "Parce que vous avez regardé Dune"))
        assertEquals("TENDANCE", RowKicker.of("top10-movie", "Top 10"))
        assertEquals("ÉPOQUE", RowKicker.of("year-1990", "Les années 90"))
        assertNull(RowKicker.of("inconnue", "Rangée"))
    }

    @Test fun `le nom de l oeuvre passe en italique`() {
        val t = rowTitle("Parce que vous avez regardé Dune", "Dune")
        assertEquals("Parce que vous avez regardé Dune", t.text)
        assertEquals(1, t.spanStyles.size)
        assertEquals(28, t.spanStyles[0].start)
        // nom absent du titre : texte simple
        assertEquals(0, rowTitle("Top 10", "Dune").spanStyles.size)
    }
}
