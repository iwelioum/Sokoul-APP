package com.sokoul.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.net.TmdbItem
import kotlinx.coroutines.launch

/** Agrandissement au survol, commun aux cartes. */
@Composable
private fun hoverScale(active: Boolean, to: Float = 1.05f) = animateFloatAsState(if (active) to else 1f, tween(180)).value

/** Affiche 2:3 : image, contour au survol, titre et repères dessous ; [progress] = barre de reprise. */
@Composable
fun PosterCard(item: TmdbItem, onClick: () -> Unit, width: Dp = 168.dp, progress: Float? = null, modifier: Modifier = Modifier) {
    val h = rememberHover()
    val scale = hoverScale(h.active)
    Column(modifier.width(width).interactive(h, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(Radius.Card)
                .border(if (h.active) Edge.Focus else Edge.Hairline, if (h.active) SokoulColors.Focus else SokoulColors.Hairline, Radius.Card),
        ) {
            NetImage(item.poster, item.title, Modifier.fillMaxSize()) { Placeholder(item.title, Modifier.fillMaxSize()) }
            item.rating?.takeIf { it >= 7.5 }?.let { r ->
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp).background(SokoulColors.Scrim, Radius.Tiny).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, null, Modifier.width(12.dp), tint = SokoulColors.Gold)
                    Text(" %.1f".format(java.util.Locale.FRANCE, r), style = Type.Badge.copy(color = Color.White))
                }
            }
            progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter)) }
        }
        Text(
            item.title, Modifier.padding(top = 10.dp), style = Type.ItemTitle.copy(color = if (h.active) SokoulColors.TextPrimary else SokoulColors.TextPrimary.copy(alpha = 0.92f)),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(item.year?.toString(), if (item.mediaType == "tv") "Série" else null, item.genreIds.firstNotNullOfOrNull { com.sokoul.desktop.data.Catalog.genreName(it) })
                .joinToString(" · "),
            style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Vignette 16:9 : image de fond, titre dessous ; [progress] = barre de reprise. */
@Composable
fun LandscapeCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
    width: Dp = 300.dp,
    subtitle: String? = null,
    progress: Float? = null,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    val h = rememberHover()
    val scale = hoverScale(h.active, 1.04f)
    Column(modifier.width(width).interactive(h, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(Radius.Card)
                .border(if (h.active) Edge.Focus else Edge.Hairline, if (h.active) SokoulColors.Focus else SokoulColors.Hairline, Radius.Card),
        ) {
            NetImage(tmdbSize(image, "w780"), title, Modifier.fillMaxSize()) { Placeholder(title, Modifier.fillMaxSize()) }
            androidx.compose.animation.AnimatedVisibility(h.active, enter = fadeIn(tween(160)), exit = fadeOut(tween(160)), modifier = Modifier.align(Alignment.Center)) {
                RoundIconButton(Icons.Filled.PlayArrow, "Lecture", onClick, filled = true, size = 52.dp)
            }
            badge?.let { Badge(it, SokoulColors.Scrim, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) }
            progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter)) }
        }
        Text(title, Modifier.padding(top = 10.dp), style = Type.ItemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Carte du Top 10 : grand numéro détouré à gauche, affiche à droite. */
@Composable
fun Top10Card(rank: Int, item: TmdbItem, onClick: () -> Unit) {
    val h = rememberHover()
    val scale = hoverScale(h.active)
    val measurer = rememberTextMeasurer()
    val style = Type.Rank
    Row(Modifier.interactive(h, onClick = onClick), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.width(if (rank >= 10) 118.dp else 78.dp).height(226.dp)) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val layout = measurer.measure(rank.toString(), style)
                val x = size.width - layout.size.width + 14.dp.toPx()
                val y = size.height - layout.size.height + 22.dp.toPx()
                drawText(layout, color = SokoulColors.Background, topLeft = androidx.compose.ui.geometry.Offset(x, y))
                drawText(
                    measurer.measure(rank.toString(), style.copy(drawStyle = Stroke(width = 3.dp.toPx()))),
                    color = if (h.active) Color.White else SokoulColors.TextMuted, topLeft = androidx.compose.ui.geometry.Offset(x, y),
                )
            }
        }
        Box(
            Modifier.width(150.dp).aspectRatio(2f / 3f).graphicsLayer { scaleX = scale; scaleY = scale }.clip(Radius.Card)
                .border(if (h.active) Edge.Focus else Edge.Hairline, if (h.active) SokoulColors.Focus else SokoulColors.Hairline, Radius.Card),
        ) {
            NetImage(item.poster, item.title, Modifier.fillMaxSize()) { Placeholder(item.title, Modifier.fillMaxSize()) }
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(SokoulColors.Accent))
    }
}

/**
 * Rangée horizontale : titre, cartes, molette convertie en défilement horizontal au survol, flèches de bord au survol.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    spacing: Dp = 18.dp,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    Column(modifier.fillMaxWidth()) {
        if (title.isNotBlank()) SectionTitle(title, Modifier.padding(horizontal = Space.Gutter), kicker = kicker)
        Box(Modifier.fillMaxWidth().padding(top = if (title.isNotBlank()) 14.dp else 0.dp).hoverable(hoverSource)) {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxWidth().onPointerEvent(PointerEventType.Scroll) { e ->
                    val c = e.changes.firstOrNull() ?: return@onPointerEvent
                    val d = if (c.scrollDelta.x != 0f) c.scrollDelta.x else c.scrollDelta.y
                    if (d != 0f && (d > 0 && state.canScrollForward || d < 0 && state.canScrollBackward)) {
                        scope.launch { state.scrollBy(d * 90f) }
                        c.consume()
                    }
                },
                content = content,
            )
            val edge = Modifier.fillMaxHeight().width(Space.Gutter)
            androidx.compose.animation.AnimatedVisibility(hovered && state.canScrollBackward, Modifier.align(Alignment.CenterStart), enter = fadeIn(), exit = fadeOut()) {
                Box(edge.height(240.dp).background(Brush.horizontalGradient(listOf(SokoulColors.Background, Color.Transparent))), contentAlignment = Alignment.Center) {
                    RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Précédent", { scope.launch { state.animateScrollBy(-900f) } })
                }
            }
            androidx.compose.animation.AnimatedVisibility(hovered && state.canScrollForward, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
                Box(edge.height(240.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, SokoulColors.Background))), contentAlignment = Alignment.Center) {
                    RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Suivant", { scope.launch { state.animateScrollBy(900f) } })
                }
            }
        }
    }
}

/** Rangée en chargement : squelettes d'affiches. */
@Composable
fun SkeletonRow(landscape: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Skeleton(Modifier.padding(horizontal = Space.Gutter).width(220.dp).height(26.dp))
        Row(Modifier.padding(start = Space.Gutter, top = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(8) { Skeleton(if (landscape) Modifier.width(300.dp).aspectRatio(16f / 9f) else Modifier.width(168.dp).aspectRatio(2f / 3f)) }
        }
    }
}
