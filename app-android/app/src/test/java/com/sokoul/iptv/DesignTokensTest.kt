package com.sokoul.iptv

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde-fou du système de design : les écrans n'écrivent ni couleur, ni arrondi, ni effet de
 * focus « à la main ». Tout passe par `ui/theme` (SokoulColors, Radius, Space, Type) et par
 * les trois styles de `FocusStyle`. Une incohérence visuelle ne peut donc pas revenir en douce.
 */
class DesignTokensTest {

    private val uiDir = listOf("src/main/java/com/sokoul/iptv/ui", "app/src/main/java/com/sokoul/iptv/ui")
        .map(::File).first { it.isDirectory }

    private val forbidden = listOf(
        Regex("""Color\(0x""") to "couleur en dur → SokoulColors",
        Regex("""RoundedCornerShape\(""") to "arrondi en dur → Radius",
        Regex("""\bfocusScale\s*=""") to "agrandissement de focus en dur → FocusStyle",
        Regex("""\bborderWidth\s*=""") to "liseré de focus en dur → FocusStyle",
    )

    @Test
    fun `aucune valeur visuelle en dur hors du theme`() {
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/ui/theme/") }
            .flatMap { f ->
                f.readLines().withIndex().flatMap { (i, line) ->
                    forbidden.filter { (re, _) -> re.containsMatchIn(line) }
                        .map { (_, why) -> "${f.relativeTo(uiDir).invariantSeparatorsPath}:${i + 1} — $why" }
                }
            }
            .toList()
        assertTrue("Valeurs visuelles en dur :\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `une seule affiche et une seule pastille`() {
        val sources = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }.toList()
        assertTrue("PosterCard ne doit plus exister (utiliser PosterTile / WorkCard)", sources.none { it.contains("fun PosterCard(") })
        assertTrue("ContentRow ne doit plus exister (utiliser CardRow)", sources.none { it.contains("fun <T> ContentRow(") })
        assertTrue("Pastille privée interdite (utiliser SokoulChip)", sources.none { Regex("""private fun Chip\(""").containsMatchIn(it) && !it.contains("SokoulChip(") })
    }
}
