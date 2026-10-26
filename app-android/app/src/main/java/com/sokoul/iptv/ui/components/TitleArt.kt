package com.sokoul.iptv.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.ui.theme.TitleArtSpec
import com.sokoul.iptv.ui.theme.TitleArtSpecs
import com.sokoul.iptv.ui.theme.TitleStyle
import com.sokoul.iptv.ui.theme.TitleStyles

/**
 * Titre « logo » généré pour une œuvre sans logo officiel : police et effet selon le genre ([TitleStyles]).
 * La taille baisse jusqu'à ce que tout tienne sur [maxLines] lignes sans couper un mot.
 */
@Composable
fun TitleArt(
    title: String,
    genres: List<String>,
    size: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    align: TextAlign = TextAlign.Start,
    style: TitleStyle = remember(genres) { TitleStyles.of(genres) },
) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val fit = remember(title, style, size, maxW, maxLines) {
            fitTitle(measurer, title, TitleArtSpecs.of(style), size.value, maxW, maxLines)
        }
        // lecteurs d'écran et recherche de texte : le titre tel quel (pas en capitales)
        Text(
            fit.text, Modifier.clearAndSetSemantics { text = AnnotatedString(title) },
            style = fit.style, maxLines = fit.lines, overflow = TextOverflow.Ellipsis, textAlign = align,
        )
    }
}

/** Titre ajusté : texte affiché (capitales selon le style), style à la bonne taille, nombre de lignes. */
internal data class FittedTitle(val text: String, val style: TextStyle, val lines: Int)

/**
 * Plus grande taille (pas de 2 sp, jusqu'à la moitié) où le titre tient sur [maxLines] lignes sans débordement ni mot
 * coupé ; si même la plus petite taille déborde, une ligne de plus est permise (mieux qu'un titre tronqué).
 */
internal fun fitTitle(measurer: TextMeasurer, title: String, spec: TitleArtSpec, maxSp: Float, maxW: Int, maxLines: Int): FittedTitle {
    val start = maxSp * spec.scale
    val min = start * 0.5f
    fun specFor(sp: Float) = if (sp < TitleArtSpecs.DECORATIVE_MIN_SP) TitleArtSpecs.small(spec) else spec
    fun text(sp: Float) = if (specFor(sp).upper) title.uppercase() else title
    if (maxW == Constraints.Infinity) return FittedTitle(text(start), specFor(start).style(start.sp), maxLines)
    fun fits(t: String, st: TextStyle, lines: Int): Boolean {
        val r = measurer.measure(t, st, maxLines = lines, constraints = Constraints(maxWidth = maxW))
        val longest = t.split(' ').maxByOrNull { it.length }.orEmpty()
        return !r.hasVisualOverflow && measurer.measure(longest, st, softWrap = false, maxLines = 1).size.width <= maxW
    }
    var s = start
    while (s > min) {
        val t = text(s)
        val st = specFor(s).style(s.sp)
        if (fits(t, st, maxLines)) return FittedTitle(t, st, maxLines)
        s -= 2f
    }
    val t = text(min)
    val st = specFor(min).style(min.sp)
    return FittedTitle(t, st, if (fits(t, st, maxLines)) maxLines else maxLines + 1)
}
