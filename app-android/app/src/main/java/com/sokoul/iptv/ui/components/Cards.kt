package com.sokoul.iptv.ui.components

import androidx.compose.ui.unit.isSpecified
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Radius
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.LaunchedEffect
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
fun RowHeader(
    title: String, tagline: String?, platform: Platform? = null, onSeeAll: (() -> Unit)? = null,
    kicker: String? = null, emphasis: String? = null,
) {
    val l = LocalLayout.current
    Row(Modifier.fillMaxWidth().padding(start = l.margin, end = l.margin, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            // étiquette d'accent : pourquoi cette rangée est là
            if (kicker != null) Text(kicker, Modifier.padding(bottom = 2.dp), style = com.sokoul.iptv.ui.theme.Type.Kicker, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (platform != null) {
                    PlatformBadge(platform, Modifier.padding(end = 10.dp), large = true)
                }
                Text(
                    rowTitle(title, emphasis), Modifier.weight(4f, fill = false), style = com.sokoul.iptv.ui.theme.Type.Section,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // filet dégradé qui prolonge le titre
                Box(
                    Modifier.padding(start = 16.dp).weight(1f).height(1.dp).widthIn(min = 24.dp)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(SokoulColors.Accent.copy(alpha = 0.55f), androidx.compose.ui.graphics.Color.Transparent)))
                )
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
        // marges verticales : place pour la carte agrandie, soulevée, son ombre et son liseré
        contentPadding = PaddingValues(start = l.margin, end = l.margin, top = 16.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(l.gutter),
    ) {
        items.forEachIndexed { i, t -> item(key(t)) { Box(Modifier.focusOnTop()) { content(i, t) } } }
        if (trailing != null) item("__see_all") { Box(Modifier.focusOnTop()) { trailing() } }
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

/** Libellé court d'une œuvre : « 2024 · Action · 2 saisons » (plus de nombre de versions : souvent trompeur, la fiche détaille les versions). */
@Suppress("UNUSED_PARAMETER")
fun Work.metaLine(withVersions: Boolean = true): String = listOfNotNull(
    year?.toString(),
    genres.firstOrNull(),
    if (type == ContentType.SERIES) seasons?.let { if (it > 1) "$it saisons" else "1 saison" } else null,
).joinToString(" · ")

/** Repli d'une affiche absente : dégradé + titre. */
@Composable
private fun PosterFallback(title: String, genres: List<String> = emptyList(), style: com.sokoul.iptv.ui.theme.TitleStyle? = null) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SokoulColors.PlaceholderTop, SokoulColors.PlaceholderBottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (style != null) TitleArt(title, genres, 24.sp, Modifier.padding(12.dp), maxLines = 4, align = TextAlign.Center, style = style)
        else TitleArt(title, genres, 24.sp, Modifier.padding(12.dp), maxLines = 4, align = TextAlign.Center)
    }
}

// --- cartes -----------------------------------------------------------------------------------------

/**
 * Affiche d'une œuvre, sans légende : l'image parle d'elle-même. Au focus, [reveal] fait monter dans l'affiche
 * le logo (ou le titre) et « ★ note · année · durée » — à couper sur les pages qui ont déjà un panneau d'aperçu.
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
    reveal: Boolean = true,
) {
    // coin de l'affiche : état (hors catalogue), sinon 4K
    val src = com.sokoul.iptv.domain.PlaybackSources.summary(work)
    val corner = SourceLabels.state(src.state) ?: src.quality?.takeIf { it == com.sokoul.iptv.domain.VideoQuality.UHD_4K }?.let(SourceLabels::quality)
    PosterTile(
        title = work.title, image = tmdbSized(work.poster, "w342"), onClick = onClick, modifier = modifier, width = width,
        corner = corner, progress = work.progress,
        platform = work.platform.takeIf { showPlatform }, onFocus = onFocus, onLongClick = onLongClick,
        dimmed = !work.available,
        reveal = if (reveal) Reveal(work.title, tmdbSized(work.logo, "w300"), work.revealLine(), work.genres) else null,
    )
}

/** Ce qui monte dans l'affiche au focus. */
data class Reveal(val title: String, val logo: String?, val line: String, val genres: List<String> = emptyList())

/** « ★ 7,8 · 2021 · 2 h 10 » (film) ou « ★ 8,1 · 2019 · 3 saisons » (série). */
fun Work.revealLine(): String = listOfNotNull(
    rating?.takeIf { it >= 1.0 }?.let { "★ " + String.format(java.util.Locale.FRANCE, "%.1f", it) },
    year?.toString(),
    if (type == ContentType.SERIES) seasons?.let { if (it > 1) "$it saisons" else "1 saison" }
    else formatRuntime(runtimeMin),
).joinToString(" · ")

/**
 * La seule affiche 2:3 de l'app (rangées, grilles, catalogue, recherche) : image, repli titré, badges, progression.
 * Au focus, l'image « respire » (zoom lent) et le [reveal] éventuel monte du bas. Sur téléphone (pas de focus),
 * seul le titre reste sous l'affiche.
 */
@Composable
fun PosterTile(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = LocalLayout.current.poster,
    corner: String? = null,
    progress: Float? = null,
    platform: Platform? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** Œuvre hors catalogue : image en niveaux de gris, atténuée. */
    dimmed: Boolean = false,
    reveal: Reveal? = null,
) {
    val l = LocalLayout.current
    // focusOnTop : la carte agrandie passe devant ses voisines (rangée comme grille)
    Column(modifier.focusOnTop().then(if (width.isSpecified) Modifier.width(width) else Modifier.fillMaxWidth())) {
        FocusSurface(onClick = onClick, onLongClick = onLongClick, onFocus = onFocus) { focused ->
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                val zoom = rememberBreathing(focused)
                val img = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }
                NetImage(
                    image, title, if (dimmed) img.offCatalogLook() else img,
                    size = if (width.isSpecified) androidx.compose.ui.unit.DpSize(width, width * 1.5f) else null,
                ) { PosterFallback(title) }
                if (platform != null) PlatformBadge(platform, Modifier.align(Alignment.TopStart).padding(6.dp))
                if (corner != null) CornerBadge(corner, Modifier.align(Alignment.TopEnd).padding(6.dp))
                if (reveal != null && focused && !l.isTouch) RevealOverlay(reveal, Modifier.align(Alignment.BottomStart))
                if (progress != null && progress > 0f) ProgressLine(progress, Modifier.align(Alignment.BottomStart))
            }
        }
        if (l.isTouch) {
            Text(title, Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Zoom lent de l'image de la carte focalisée (1 → [to] en 2,4 s, après 350 ms) : la carte « respire ».
 * Valeur lue dans un graphicsLayer : aucune recomposition. Coupé si les animations sont désactivées.
 */
@Composable
fun rememberBreathing(focused: Boolean, to: Float = 1.05f): Animatable<Float, AnimationVector1D> {
    val reduced = LocalReducedMotion.current
    val zoom = remember { Animatable(1f) }
    LaunchedEffect(focused, reduced) {
        if (focused && !reduced) {
            delay(350)
            zoom.animateTo(to, tween(2400, easing = Motion.Decelerate))
        } else zoom.animateTo(1f, tween(Motion.FAST))
    }
    return zoom
}

/** Voile du bas de l'affiche focalisée : logo (ou titre) et ligne d'infos, qui montent en fondu. */
@Composable
private fun RevealOverlay(r: Reveal, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) { delay(120); p.animateTo(1f, tween(Motion.MEDIUM, easing = Motion.Decelerate)) } }
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 14.dp.toPx() }
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to SokoulColors.black(0.55f), 1f to SokoulColors.black(0.92f)))
            .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 9.dp),
    ) {
        if (r.logo != null) {
            NetImage(r.logo, r.title, Modifier.fillMaxWidth(0.9f).heightIn(max = 38.dp), ContentScale.Fit, alignment = Alignment.BottomStart) {
                RevealTitle(r.title, r.genres)
            }
        } else RevealTitle(r.title, r.genres)
        if (r.line.isNotEmpty()) {
            Text(r.line, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.white(0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RevealTitle(title: String, genres: List<String>) {
    TitleArt(title, genres, 20.sp, maxLines = 2)
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(SokoulColors.black(0.5f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0.02f, 1f)).background(SokoulColors.Accent))
    }
}

/**
 * Carte paysage 16:9 : fond TMDB + logo (ou titre) dans l'image, sans légende dessous.
 * [Work.subtitle] (reprise : « S02E05 · reste 20 min ») s'affiche dans l'image, sous le logo.
 */
@Composable
fun LandscapeCard(
    work: Work,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = LocalLayout.current.landscape,
    image: String? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    dense: Boolean = false,
) {
    Box(modifier.width(width)) {
        FocusSurface(onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, shape = Radius.Card) { focused ->
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                val zoom = rememberBreathing(focused, 1.04f)
                val imgMod = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }
                val img = image ?: tmdbSized(work.backdrop, "w780")
                if (img != null) NetImage(img, work.title, imgMod, size = androidx.compose.ui.unit.DpSize(width, width * 9f / 16f)) { PosterFallback("") }
                // sans image : simple dégradé, le titre est déjà écrit en bas de la carte
                else NetImage(tmdbSized(work.poster, "w342"), work.title, imgMod, alignment = Alignment.TopCenter) { PosterFallback("") }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to SokoulColors.black(0.92f))))
                Row(Modifier.align(Alignment.BottomStart).padding(if (dense) 6.dp else 10.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        if (work.logo != null) {
                            NetImage(
                                tmdbSized(work.logo, "w300"), work.title,
                                Modifier.heightIn(max = if (dense) 24.dp else 40.dp).widthIn(max = width * 0.6f), ContentScale.Fit,
                                alignment = Alignment.BottomStart,
                            )
                        } else {
                            TitleArt(work.title, work.genres, if (dense) 14.sp else 22.sp, Modifier.widthIn(max = width * 0.8f), maxLines = if (dense) 1 else 2)
                        }
                        if (!dense) work.subtitle?.let {
                            Text(it, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.white(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (work.resume != null) {
                        Box(
                            Modifier.size(30.dp).background(if (focused) Color.White else SokoulColors.black(0.6f), Radius.Round),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp), tint = if (focused) Color.Black else Color.White) }
                    }
                }
                if (work.platform != null) PlatformBadge(work.platform, Modifier.align(Alignment.TopStart).padding(8.dp))
                if (work.progress != null && work.progress > 0f) ProgressLine(work.progress, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/**
 * Top 10 en paysage (recherche) : grand chiffre à empattements, la carte 16:9 posée juste après.
 * Le titre est dans l'image (logo ou TitleArt) : rien n'est coupé, quelle que soit sa longueur.
 */
@Composable
fun RankLandscapeCard(rank: Int, work: Work, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 260.dp) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            rank.toString(), Modifier.offset(y = 14.dp).padding(end = 20.dp), maxLines = 1, softWrap = false,
            style = com.sokoul.iptv.ui.theme.Type.Display.copy(
                fontSize = (width.value * 0.46f).sp, lineHeight = (width.value * 0.46f).sp, letterSpacing = (-4).sp,
                brush = Brush.verticalGradient(listOf(Color.White, SokoulColors.white(0.12f))),
            ),
        )
        LandscapeCard(work, onClick, width = width)
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
                style = TextStyle(fontFamily = com.sokoul.iptv.ui.theme.Manrope, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = (-6).sp, drawStyle = Stroke(width = 5f)),
                color = SokoulColors.TextSecondary,
            )
        }
        WorkCard(work, onClick, width = l.poster, onFocus = onFocus, onLongClick = onLongClick, reveal = false)
    }
}

/**
 * Saga : fond de la collection, logo (ou nom), nombre de films et part déjà vue.
 * Au focus : le fond zoome lentement et 3 mini-affiches se déploient en éventail depuis le coin bas-droit
 * (translation et rotation lues dans un graphicsLayer : aucune recomposition pendant l'animation).
 */
@Composable
fun SagaTile(saga: SagaCard, onClick: () -> Unit, onFocus: (() -> Unit)? = null, modifier: Modifier = Modifier, width: Dp = LocalLayout.current.landscape) {
    val l = LocalLayout.current
    // fond transparent : l'image (ou son repli) couvre toute la tuile, inutile de peindre dessous
    FocusSurface(
        onClick = onClick, onFocus = onFocus, style = FocusStyle.Tile,
        modifier = (if (width.isSpecified) modifier.width(width) else modifier.fillMaxWidth()).focusOnTop(),
        shape = Radius.Card, color = Color.Transparent,
    ) { focused ->
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            val zoom = rememberBreathing(focused, to = 1.06f)
            NetImage(
                tmdbSized(saga.backdrop, "w780") ?: tmdbSized(saga.poster, "w342"), saga.name,
                Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value },
            ) { PosterFallback(sagaLabel(saga.name), style = com.sokoul.iptv.ui.theme.TitleStyle.GOLD) }
            // voile du bas (sous le texte) et du coin droit (sous l'éventail)
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.BottomStart).background(Brush.verticalGradient(0f to Color.Transparent, 1f to SokoulColors.black(0.94f))))
            if (!l.isTouch && saga.minis.isNotEmpty()) SagaFan(saga.minis, focused, maxHeight * 0.5f, Modifier.align(Alignment.BottomEnd))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 12.dp).fillMaxWidth(0.56f)) {
                val films = saga.facts?.films?.takeIf { it > 0 } ?: saga.count
                Text(
                    if (films > 1) "SAGA · $films FILMS" else "SAGA",
                    color = SokoulColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1,
                )
                if (saga.logo != null) {
                    NetImage(
                        tmdbSized(saga.logo, "w300"), saga.name, Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(max = 40.dp),
                        ContentScale.Fit, alignment = Alignment.BottomStart,
                    ) { SagaName(saga.name) }
                } else SagaName(saga.name)
                Text(
                    if (saga.count > 1) "${saga.count} au catalogue" else "1 au catalogue",
                    Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1,
                )
            }
            // part de la saga déjà vue
            val total = saga.facts?.films ?: 0
            if (saga.seen > 0 && total > 0) ProgressLine(saga.seen.toFloat() / total, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun SagaName(name: String) {
    TitleArt(sagaLabel(name), emptyList(), 22.sp, maxLines = 2, style = com.sokoul.iptv.ui.theme.TitleStyle.GOLD)
}

/** Nom de saga sans les suffixes du catalogue (« - Saga », « (Collection) »). */
fun sagaLabel(name: String) = name.replace(" - Saga", "").replace(" (Collection)", "")

/** 3 mini-affiches empilées dans le coin, qui s'ouvrent en éventail au focus. */
@Composable
private fun SagaFan(posters: List<String>, focused: Boolean, height: Dp, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(focused, reduced) {
        when {
            !focused -> p.animateTo(0f, tween(Motion.FAST))
            reduced -> p.snapTo(1f)
            else -> { delay(140); p.animateTo(1f, Motion.focusSpring()) }
        }
    }
    val w = height * (2f / 3f)
    Box(modifier.padding(end = 12.dp, bottom = 10.dp).width(w * 2f).height(height)) {
        val n = posters.size
        posters.forEachIndexed { i, url ->
            // de gauche (au fond) à droite (devant) : -14°, -4°, +6°
            val rank = i - (n - 1)
            NetImage(
                url, null,
                Modifier.align(Alignment.BottomEnd).width(w).height(height)
                    .graphicsLayer {
                        val v = p.value
                        alpha = v.coerceIn(0f, 1f)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        rotationZ = (rank * 10f + 6f) * v
                        translationX = rank * w.toPx() * 0.42f * v
                        translationY = (1f - v) * 18.dp.toPx()
                    }
                    .clip(Radius.Tiny),
            )
        }
    }
}

/** Tuile de plateforme (façon Disney+) : dégradé aux couleurs de la marque. */
@Composable
fun PlatformTileView(tile: PlatformTile, onClick: () -> Unit, onFocus: (() -> Unit)? = null) {
    val l = LocalLayout.current
    val w = if (l.isCompact) 132.dp else (l.landscape * 0.72f)
    FocusSurface(onClick = onClick, onFocus = onFocus, modifier = Modifier.width(w), shape = Radius.Card) { focused ->
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
        shape = Radius.Card, color = SokoulColors.SurfaceHigh, focusedColor = SokoulColors.SurfaceHigher, 
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

private val GrayPaint = androidx.compose.ui.graphics.Paint().apply {
    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })
    alpha = 0.55f
}

/** Œuvre hors catalogue : niveaux de gris et voile (calque logiciel, compatible avec toutes les versions d'Android). */
fun Modifier.offCatalogLook(): Modifier = drawWithContent {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), GrayPaint)
        drawContent()
        canvas.restore()
    }
}
