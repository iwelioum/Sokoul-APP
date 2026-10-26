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
        Regex("""\bCircleShape\b""") to "cercle en dur → Radius.Round",
        Regex("""FontFamily\(|FontFamily\.(Serif|SansSerif|Monospace|Cursive)""") to "police en dur → Manrope (thème)",
        Regex("""\bfocusScale\s*=""") to "agrandissement de focus en dur → FocusStyle",
        Regex("""\bborderWidth\s*=""") to "liseré de focus en dur → FocusStyle",
        Regex("""\d\.sp\b""") to "taille de texte en dur → Type / TitleSize",
        Regex("""\bColor\.(White|Black)\b""") to "blanc / noir en dur → SokoulColors.OnVideo / OnLight / VideoBlack",
        Regex("""\bOutlinedTextField\(""") to "champ Material → FormField",
        Regex("""\bLinearProgressIndicator\(""") to "barre Material → ProgressLine ou squelette",
    )

    private fun sources() = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }
        .associate { it.relativeTo(uiDir).invariantSeparatorsPath to it.readText() }

    @Test
    fun `une seule roue de chargement`() {
        val offenders = sources().filter { (path, text) -> path != "components/Controls.kt" && text.contains("CircularProgressIndicator(") }.keys
        assertTrue("Roue Material hors SokoulSpinner : $offenders", offenders.isEmpty())
    }

    @Test
    fun `un seul bouton, une seule barre, un seul cadre de fenetre`() {
        val s = sources()
        listOf("fun AppButton(" to "SokoulButton", "fun LoadingBox(" to "squelettes", "fun Pill(" to "SokoulChip").forEach { (sig, use) ->
            assertTrue("$sig ne doit plus exister ($use)", s.values.none { it.contains(sig) })
        }
        assertTrue("ProgressLine défini une seule fois", s.values.count { it.contains("fun ProgressLine(") } == 1)
        val rawDialogs = s.filter { (path, text) -> path != "components/Dialogs.kt" && Regex("""\bDialog\(onDismissRequest""").containsMatchIn(text) }.keys
        assertTrue("Fenêtres hors SokoulDialog : $rawDialogs", rawDialogs.isEmpty())
    }

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
