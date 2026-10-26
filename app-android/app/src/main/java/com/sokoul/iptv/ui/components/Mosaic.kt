package com.sokoul.iptv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.SokoulColors

/**
 * Décor de l'accueil d'installation : un mur d'affiches abstraites (dégradés aux couleurs de l'app, aucune
 * image protégée) qui défile très lentement en biais, sous un voile qui laisse lire le texte à gauche.
 * Dessiné une fois (cache), seule la translation est animée : coût négligeable sur la box.
 */
@Composable
fun PosterMosaic(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "mosaic")
    val shift by t.animateFloat(0f, if (reduced) 0f else 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "shift")
    Box(modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    // une période = la hauteur d'une affiche + l'espace : la boucle ne se voit pas
                    val period = (TileW.toPx() * 1.5f + Gap.toPx())
                    translationY = -shift * period
                    rotationZ = -8f; scaleX = 1.25f; scaleY = 1.25f
                }
                .drawWithCache {
                    val w = TileW.toPx(); val h = w * 1.5f; val gap = Gap.toPx()
                    val cols = (size.width / (w + gap)).toInt() + 3
                    val rows = (size.height / (h + gap)).toInt() + 4
                    val corner = CornerRadius(4.dp.toPx())
                    val tiles = buildList {
                        for (c in 0 until cols) for (r in 0 until rows) {
                            // colonnes décalées d'une demi-affiche, couleurs pseudo-aléatoires mais stables
                            val x = c * (w + gap) - w
                            val y = r * (h + gap) - h + if (c % 2 == 1) h / 2 else 0f
                            val i = (c * 7 + r * 3) % Palette.size
                            add(Triple(Offset(x, y), Palette[i], Palette[(i + 2) % Palette.size]))
                        }
                    }
                    onDrawBehind {
                        tiles.forEach { (o, a, b) ->
                            drawRoundRect(Brush.linearGradient(listOf(a, b), start = o, end = Offset(o.x + w, o.y + h)), o, Size(w, h), corner)
                        }
                    }
                }
        )
        // voile : plein à gauche (texte), plus léger à droite où le mur reste visible
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to SokoulColors.bg(0.97f), 0.45f to SokoulColors.bg(0.86f), 1f to SokoulColors.bg(0.62f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to SokoulColors.bg(0.9f))))
    }
}

private val TileW = 150.dp
private val Gap = 14.dp
private val Palette = listOf(
    SokoulColors.Accent.copy(alpha = 0.55f), SokoulColors.PlaceholderTop, SokoulColors.AccentEnd.copy(alpha = 0.45f),
    SokoulColors.SurfaceHigher, SokoulColors.Live.copy(alpha = 0.35f), SokoulColors.PlaceholderBottom,
    SokoulColors.Gold.copy(alpha = 0.25f), SokoulColors.SurfaceHigh,
)
