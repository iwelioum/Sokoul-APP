package com.sokoul.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type

/**
 * En-tête commun des pages qui ne sont pas plein écran (Réglages, Enregistrements, Guide, catalogue…) :
 * titre de page, ligne d'infos facultative, actions à droite. Même taille et mêmes marges partout ;
 * le conteneur parent applique la marge latérale `LocalLayout.margin`.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Marge du haut imposée (page sous les onglets, sous la barre d'état) ; sinon marge standard. */
    top: androidx.compose.ui.unit.Dp? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val l = LocalLayout.current
    Row(
        modifier.fillMaxWidth().padding(top = top ?: if (l.isTv) l.safeTop else Space.m, bottom = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, Modifier.padding(top = Space.xs), style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/**
 * « Faux verre » sans flou temps réel (trop coûteux pour la box) : l'image de fond est décodée
 * à 32 px puis agrandie avec un filtrage lissé, sous un voile [SokoulColors.Panel] ; un reflet
 * de 1 dp en haut et à gauche simule l'arête d'une plaque de verre.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    image: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clip(Radius.Panel).glassEdge()) {
        if (image != null) {
            // PC : image de fond floutée (Skia), au lieu du décodage à 32 px de la tablette
            NetImage(image, null, Modifier.matchParentSize().graphicsLayer {
                alpha = 0.55f
                renderEffect = androidx.compose.ui.graphics.BlurEffect(40f, 40f, androidx.compose.ui.graphics.TileMode.Clamp)
            })
        }
        Box(Modifier.matchParentSize().background(SokoulColors.Panel.copy(alpha = if (image != null) 0.82f else 0.94f)))
        content()
    }
}

/** Arête lumineuse du verre : trait de 1 dp blanc à 10 %, plus marqué en haut à gauche. */
private fun Modifier.glassEdge(): Modifier = drawWithCache {
    val outline = Radius.Panel.createOutline(size, layoutDirection, this)
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(SokoulColors.white(0.10f), SokoulColors.white(0f)),
        start = Offset.Zero, end = Offset(size.width * 0.6f, size.height * 0.6f),
    )
    onDrawWithContent {
        drawContent()
        drawOutline(outline, brush, style = Stroke(1.dp.toPx() * 2))
    }
}
