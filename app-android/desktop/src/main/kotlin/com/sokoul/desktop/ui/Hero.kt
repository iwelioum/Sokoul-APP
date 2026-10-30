package com.sokoul.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.Feature
import kotlinx.coroutines.delay

/** Fond plein cadre d'une œuvre : image calée en haut, voiles à gauche (texte) et en bas (fondu vers la page). */
@Composable
fun Backdrop(url: String?, modifier: Modifier = Modifier, leftVeil: Float = 0.92f) {
    Box(modifier) {
        NetImage(url, null, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SokoulColors.PlaceholderTop, SokoulColors.Background))))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to SokoulColors.Background.copy(alpha = leftVeil), 0.40f to SokoulColors.Background.copy(alpha = 0.78f), 0.62f to SokoulColors.Background.copy(alpha = 0.35f), 0.85f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.18f to Color.Transparent, 0.62f to Color.Transparent, 1f to SokoulColors.Background),
            ),
        )
    }
}

/** Logo du titre (TMDB), sinon le titre en DM Serif. */
@Composable
fun TitleArt(title: String, logo: String?, modifier: Modifier = Modifier, maxWidth: Dp = 520.dp, maxHeight: Dp = 170.dp) {
    if (logo != null) {
        NetImage(
            logo, title, modifier.widthIn(max = maxWidth).heightIn(max = maxHeight).width(maxWidth).height(maxHeight),
            scale = ContentScale.Fit, alignment = Alignment.BottomStart,
        ) { Text(title, style = Type.Display, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    } else {
        Text(title, modifier.widthIn(max = 760.dp), style = Type.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Héros de l'accueil : tendances du jour en plein cadre, une toutes les 9 s (arrêt au survol),
 * points cliquables, boutons Lecture et Plus d'infos.
 */
@Composable
fun HomeHero(features: List<Feature>, height: Dp, onPlay: (Feature) -> Unit, onInfo: (Feature) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    LaunchedEffect(features.size, hovered, index) {
        if (features.size > 1 && !hovered) { delay(9_000); index = (index + 1) % features.size }
    }
    Box(Modifier.fillMaxWidth().hoverable(hover)) {
        Box(Modifier.fillMaxWidth().height(height)) {
            val f = features.getOrNull(index)
            Crossfade(f, animationSpec = tween(700)) { cur ->
                Box(Modifier.fillMaxSize()) {
                    Backdrop(cur?.info?.backdrop ?: cur?.item?.backdrop, Modifier.fillMaxSize())
                    if (cur != null) HeroText(cur, onPlay, onInfo, Modifier.align(Alignment.BottomStart))
                }
            }
            if (features.size > 1) Row(
                Modifier.align(Alignment.BottomEnd).padding(end = Space.Gutter, bottom = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                features.indices.forEach { i ->
                    val h = rememberHover()
                    Box(
                        Modifier.size(if (i == index) 26.dp else 10.dp, 4.dp)
                            .background(if (i == index) Color.White else if (h.active) SokoulColors.GlassHeavy else SokoulColors.GlassLight, Radius.Tiny)
                            .interactive(h) { index = i },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroText(f: Feature, onPlay: (Feature) -> Unit, onInfo: (Feature) -> Unit, modifier: Modifier) {
    val info = f.info
    val tv = (f.item.mediaType ?: "movie") == "tv"
    Column(modifier.padding(start = Space.Gutter, bottom = 64.dp).widthIn(max = 640.dp)) {
        Kicker(if (tv) "Série · Tendance du jour" else "Film · Tendance du jour")
        TitleArt(info?.title ?: f.item.title, info?.logo, Modifier.padding(top = 14.dp), maxWidth = 440.dp, maxHeight = 130.dp)
        MetaRow(
            year = info?.year ?: f.item.year, runtimeMin = info?.runtimeMin?.takeIf { !tv }, rating = info?.rating ?: f.item.rating,
            seasons = info?.seasons?.takeIf { tv }, certification = info?.certification,
            genres = info?.genres ?: f.item.genreIds.mapNotNull(Catalog::genreName),
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            info?.overview ?: f.item.overview.orEmpty(), Modifier.padding(top = 14.dp).widthIn(max = 580.dp),
            style = Type.BodyStrong.copy(color = SokoulColors.TextPrimary.copy(alpha = 0.86f)), maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SokoulButton("Lecture", { onPlay(f) }, style = ButtonStyle.Primary, icon = Icons.Filled.PlayArrow, large = true)
            SokoulButton("Plus d'infos", { onInfo(f) }, style = ButtonStyle.Glass, icon = Icons.Outlined.Info, large = true)
        }
    }
}
