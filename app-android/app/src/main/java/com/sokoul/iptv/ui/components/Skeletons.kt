package com.sokoul.iptv.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.Space

/**
 * Squelettes de chargement : la forme de l'écran apparaît tout de suite, le contenu la remplace.
 * Plus de roue qui tourne au milieu d'une page vide.
 */

/** Liste de chaînes (Direct, tiroirs) : numéro, logo, deux lignes de texte. */
@Composable
fun ChannelListSkeleton(modifier: Modifier = Modifier, rows: Int = 8) {
    Column(modifier.fillMaxSize().padding(Space.s).semantics { contentDescription = "Chargement" }, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        repeat(rows) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp, 14.dp).shimmer(Radius.Tiny))
                Spacer(Modifier.width(Space.m))
                Box(Modifier.size(64.dp, 40.dp).shimmer())
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.45f else 0.6f).height(14.dp).shimmer(Radius.Tiny))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(11.dp).shimmer(Radius.Tiny))
                }
            }
        }
    }
}

/** Grille du guide : colonne des chaînes et cases de programmes de largeurs variées. */
@Composable
fun GuideSkeleton(modifier: Modifier = Modifier, rows: Int = 7) {
    val widths = listOf(0.22f, 0.35f, 0.18f, 0.25f)
    Column(modifier.fillMaxSize().padding(top = Space.s).semantics { contentDescription = "Chargement" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(236.dp).fillMaxHeight().shimmer())
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    widths.indices.forEach { i ->
                        val w = widths[(i + r) % widths.size]
                        Box(Modifier.weight(w).fillMaxHeight().shimmer())
                    }
                }
            }
        }
    }
}

/** Grille d'affiches (catalogue, Ma liste). */
@Composable
fun PosterGridSkeleton(modifier: Modifier = Modifier, rows: Int = 2) {
    val l = LocalLayout.current
    Column(modifier.fillMaxSize().padding(horizontal = l.margin, vertical = Space.s).semantics { contentDescription = "Chargement" }, verticalArrangement = Arrangement.spacedBy(l.gutter)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
                repeat(8) { Box(Modifier.width(l.poster).aspectRatio(2f / 3f).shimmer()) }
            }
        }
    }
}

/** Démarrage : la marque respire doucement le temps de lire la base. */
@Composable
fun BrandSplash(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "splash")
    val a by t.animateFloat(0.55f, if (reduced) 0.55f else 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.fillMaxSize().semantics { contentDescription = "Chargement" }, contentAlignment = Alignment.Center) {
        SokoulMark(Modifier.graphicsLayer { alpha = a }, size = 64.dp)
    }
}
