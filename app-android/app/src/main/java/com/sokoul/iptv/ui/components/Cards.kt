package com.sokoul.iptv.ui.components

import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Radius
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.Platform
import com.sokoul.iptv.data.repo.PlatformTile
import com.sokoul.iptv.data.repo.SagaCard
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.SokoulColors

// --- chargement -------------------------------------------------------------------------------

/** Reflet animé des squelettes de chargement. */
fun Modifier.shimmer(shape: RoundedCornerShape = Radius.Card): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    this.clip(shape).drawBehind {
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(SokoulColors.Skeleton, SokoulColors.SkeletonShine, SokoulColors.Skeleton),
                start = Offset(w * x - w, 0f), end = Offset(w * x, size.height),
            )
        )
    }
}

/** Rangée fantôme affichée pendant la construction de la page. */
@Composable
fun SkeletonRow(landscape: Boolean = false) {
    val l = LocalLayout.current
    Column(Modifier.padding(vertical = l.rowSpacing / 2)) {
        Box(Modifier.padding(start = l.margin, bottom = 12.dp).size(180.dp, 20.dp).shimmer(Radius.Tiny))
        Row(Modifier.padding(start = l.margin), horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
            repeat(8) {
                val w = if (landscape) l.landscape else l.poster
                Box(Modifier.width(w).aspectRatio(if (landscape) 16f / 9f else 2f / 3f).shimmer())
            }
        }
    }
}

// --- en-tête de rangée ---------------------------------------------------------------------------

@Composable
fun RowHeader(title: String, tagline: String?, platform: Platform? = null, onSeeAll: (() -> Unit)? = null) {
    val l = LocalLayout.current
    Row(Modifier.fillMaxWidth().padding(start = l.margin, end = l.margin, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (platform != null) {
                    PlatformBadge(platform, Modifier.padding(end = 10.dp), large = true)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!tagline.isNullOrBlank()) {
                Text(tagline, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // sur TV, « Tout voir » est une carte en fin de rangée (atteignable à la télécommande)
        if (onSeeAll != null && l.isTouch) {
            Row(
                Modifier.clip(Radius.Pill).dpadClickable(onSeeAll).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tout voir", style = MaterialTheme.typography.labelLarge, color = SokoulColors.Accent)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = SokoulColors.Accent)
            }
        }
    }
}

/** Rangée horizontale avec marges adaptatives. */
@Composable
fun <T> CardRow(items: List<T>, key: (T) -> Any, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, content: @Composable (Int, T) -> Unit) {
    val l = LocalLayout.current
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = l.margin, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(l.gutter),
    ) {
        items.forEachIndexed { i, t -> item(key(t)) { content(i, t) } }
        if (trailing != null) item("__see_all") { trailing() }
    }
}

// --- pastilles ---------------------------------------------------------------------------------------

/** Pastille de plateforme aux couleurs de la marque (« NETFLIX », « Disney+ »…). */
@Composable
fun PlatformBadge(p: Platform, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        p.label.uppercase(),
        modifier.background(Color(p.color), Radius.Tiny).padding(horizontal = if (large) 7.dp else 5.dp, vertical = if (large) 2.dp else 1.dp),
        color = if (p == Platform.HULU) Color.Black else Color.White,
        fontSize = if (large) 11.sp else 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, maxLines = 1,
    )
}

@Composable
private fun CornerBadge(text: String, modifier: Modifier = Modifier, color: Color = SokoulColors.black(0.85f)) {
    Text(
        text, modifier.background(color, Radius.Tiny).padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

/** Libellé court d'une œuvre : « 2024 · Action · 3 versions ». */
fun Work.metaLine(withVersions: Boolean = true): String = listOfNotNull(
    year?.toString(),
    genres.firstOrNull(),
    if (type == ContentType.SERIES) seasons?.let { if (it > 1) "$it saisons" else "1 saison" } else null,
    if (withVersions && versions > 1) "$versions versions" else null,
).joinToString(" · ")

/** Repli d'une affiche absente : dégradé + titre. */
@Composable
private fun PosterFallback(title: String) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SokoulColors.PlaceholderTop, SokoulColors.PlaceholderBottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title, Modifier.padding(10.dp), textAlign = TextAlign.Center, color = SokoulColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 5, overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- cartes -----------------------------------------------------------------------------------------

/**
 * Affiche d'une œuvre. TV : le titre apparaît sous l'affiche au focus (sans décaler la rangée) ;
 * tactile : titre toujours visible.
 */
@Composable
fun WorkCard(
    work: Work,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = LocalLayout.current.poster,
    showPlatform: Boolean = true,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val l = LocalLayout.current
    var focusedState by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick, onLongClick = onLongClick, onFocus = onFocus,
            modifier = Modifier.onFocusChanged { focusedState = it.isFocused },
            shape = Radius.Card, focusScale = 1.08f, borderWidth = 2.dp, color = SokoulColors.SurfaceHigh,
        ) { _ ->
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                NetImage(tmdbSized(work.poster, "w342"), work.title, Modifier.fillMaxSize()) { PosterFallback(work.title) }
                if (showPlatform && work.platform != null) PlatformBadge(work.platform, Modifier.align(Alignment.TopStart).padding(6.dp))
                val corner = work.quality?.takeIf { it.contains("4K", true) || it.contains("UHD", true) }?.let { "4K" }
                    ?: if (work.versions > 1) "${work.versions} VERSIONS" else null
                if (corner != null) CornerBadge(corner, Modifier.align(Alignment.TopEnd).padding(6.dp))
                if (work.progress != null && work.progress > 0f) ProgressLine(work.progress, Modifier.align(Alignment.BottomStart))
            }
        }
        val alpha by animateFloatAsState(if (l.isTouch || focusedState) 1f else 0f, tween(Motion.FAST), label = "title")
        Column(Modifier.graphicsLayer { this.alpha = alpha }.padding(top = 7.dp, start = 2.dp, end = 2.dp)) {
            Text(work.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = work.subtitle ?: work.metaLine()
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(SokoulColors.black(0.5f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0.02f, 1f)).background(SokoulColors.Accent))
    }
}

/** Carte paysage 16:9 : fond TMDB + logo (ou titre), progression éventuelle. */
@Composable
fun LandscapeCard(
    work: Work,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = LocalLayout.current.landscape,
    image: String? = null,
    caption: String? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(width)) {
        FocusSurface(onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, shape = Radius.Card, focusScale = 1.06f, borderWidth = 2.dp) { focused ->
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                val img = image ?: tmdbSized(work.backdrop, "w780")
                if (img != null) NetImage(img, work.title, Modifier.fillMaxSize()) { PosterFallback(work.title) }
                else NetImage(tmdbSized(work.poster, "w342"), work.title, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) { PosterFallback(work.title) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to SokoulColors.black(0.9f))))
                Row(Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    if (work.logo != null) {
                        NetImage(
                            tmdbSized(work.logo, "w300"), work.title,
                            Modifier.weight(1f, fill = false).heightIn(max = 42.dp).widthIn(max = width * 0.6f), ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                        )
                    } else {
                        Text(work.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.weight(1f))
                    if (work.resume != null) {
                        Box(
                            Modifier.size(30.dp).background(if (focused) Color.White else SokoulColors.black(0.6f), Radius.Pill),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp), tint = if (focused) Color.Black else Color.White) }
                    }
                }
                if (work.platform != null) PlatformBadge(work.platform, Modifier.align(Alignment.TopStart).padding(8.dp))
                if (work.progress != null && work.progress > 0f) ProgressLine(work.progress, Modifier.align(Alignment.BottomStart))
            }
        }
        val sub = caption ?: work.subtitle ?: work.metaLine()
        if (sub.isNotEmpty()) {
            Text(sub, Modifier.padding(top = 7.dp, start = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Carte du Top 10 : grand chiffre détouré, affiche par-dessus. */
@Composable
fun Top10Card(rank: Int, work: Work, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    val l = LocalLayout.current
    val posterH = l.poster * 1.5f
    Row(modifier.width(l.poster * 1.62f), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.width(l.poster * 0.62f).height(posterH), contentAlignment = Alignment.BottomEnd) {
            val size = (posterH.value * 0.78f).sp
            Text(
                rank.toString(), Modifier.offset(x = 14.dp, y = 10.dp), maxLines = 1, softWrap = false,
                style = TextStyle(fontSize = size, fontWeight = FontWeight.Black, letterSpacing = (-6).sp, drawStyle = Stroke(width = 5f)),
                color = SokoulColors.TextSecondary,
            )
        }
        WorkCard(work, onClick, width = l.poster, onFocus = onFocus, onLongClick = onLongClick)
    }
}

/** Saga : fond de la collection, nom et nombre de films disponibles. */
@Composable
fun SagaTile(saga: SagaCard, onClick: () -> Unit, onFocus: (() -> Unit)? = null) {
    val l = LocalLayout.current
    FocusSurface(onClick = onClick, onFocus = onFocus, modifier = Modifier.width(l.landscape), shape = Radius.Card, focusScale = 1.06f, borderWidth = 2.dp) { _ ->
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            NetImage(tmdbSized(saga.backdrop, "w780") ?: tmdbSized(saga.poster, "w342"), saga.name, Modifier.fillMaxSize()) { PosterFallback(saga.name) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to SokoulColors.black(0.13f), 1f to SokoulColors.black(0.94f))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text("SAGA", color = SokoulColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(saga.name.replace(" - Saga", "").replace(" (Collection)", ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${saga.count} films disponibles", style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary)
            }
        }
    }
}

/** Tuile de plateforme (façon Disney+) : dégradé aux couleurs de la marque. */
@Composable
fun PlatformTileView(tile: PlatformTile, onClick: () -> Unit, onFocus: (() -> Unit)? = null) {
    val l = LocalLayout.current
    val w = if (l.isCompact) 132.dp else (l.landscape * 0.72f)
    FocusSurface(onClick = onClick, onFocus = onFocus, modifier = Modifier.width(w), shape = Radius.Card, focusScale = 1.07f, borderWidth = 2.dp) { focused ->
        val p = tile.platform
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(Brush.linearGradient(listOf(Color(p.color), Color(p.accent)))),
            contentAlignment = Alignment.Center,
        ) {
            // reflet
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(if (focused) 0x40FFFFFF else 0x22FFFFFF), Color.Transparent))))
            Text(
                p.label, color = if (p == Platform.HULU) SokoulColors.OnLight else Color.White,
                fontSize = if (l.isCompact) 17.sp else 21.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, maxLines = 1,
            )
        }
    }
}

/** Dernière carte d'une rangée sur TV : « Tout voir ». */
@Composable
fun SeeAllCard(onClick: () -> Unit, landscape: Boolean = false) {
    val l = LocalLayout.current
    FocusSurface(
        onClick = onClick, modifier = Modifier.width(if (landscape) l.landscape * 0.6f else l.poster),
        shape = Radius.Card, color = SokoulColors.SurfaceHigh, focusedColor = SokoulColors.SurfaceHigher, focusScale = 1.08f, borderWidth = 2.dp,
    ) { focused ->
        Column(
            Modifier.fillMaxWidth().aspectRatio(if (landscape) 1.2f else 2f / 3f),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(44.dp).background(if (focused) SokoulColors.Accent else SokoulColors.SurfaceHigher, Radius.Pill),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White) }
            Text("Tout voir", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** En dessous, la note TMDB repose sur trop peu de votes pour être affichée (« 0,5 » paraît cassé). */
const val MIN_RATING = 1.0

/** Note TMDB : ★ 7,8 */
@Composable
fun RatingChip(rating: Double, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, null, Modifier.size(15.dp), tint = SokoulColors.Gold)
        Text(String.format(java.util.Locale.FRANCE, "%.1f", rating), Modifier.padding(start = 3.dp), style = MaterialTheme.typography.labelLarge)
    }
}
