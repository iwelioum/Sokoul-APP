package com.sokoul.iptv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.overviewFr
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.SokoulColors
import kotlinx.coroutines.launch

/** Durée lisible : 135 → « 2 h 15 ». */
fun formatRuntime(min: Int?): String? = when {
    min == null || min <= 0 -> null
    min < 60 -> "$min min"
    else -> "${min / 60} h ${(min % 60).toString().padStart(2, '0')}"
}

/**
 * Héros animé façon plateformes : défilement automatique (8 s), effet Ken Burns, parallaxe,
 * indicateurs qui se remplissent. Les boutons sont hors du pager : le focus télécommande
 * n'est jamais perdu quand la page change.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HeroCarousel(
    works: List<Work>,
    onPlay: (Work) -> Unit,
    onInfo: (Work) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int, Work) -> String? = { _, _ -> null },
    playFocus: FocusRequester? = null,
    onFocused: () -> Unit = {},
    /** `false` quand le héros est hors écran ou masqué : le défilement automatique s'arrête. */
    active: Boolean = true,
) {
    if (works.isEmpty()) return
    val l = LocalLayout.current
    val n = works.size
    val virtual = if (n > 1) n * 400 else 1
    val state = rememberPagerState(initialPage = if (n > 1) n * 200 else 0) { virtual }
    val scope = rememberCoroutineScope()
    val dragged by state.interactionSource.collectIsDraggedAsState()
    val fill = remember { Animatable(0f) }
    val current = works[state.currentPage % n]

    // défilement automatique, mis en pause pendant un glissé au doigt
    // clé = page stabilisée : currentPage change à mi-animation et annulerait le défilement en cours
    LaunchedEffect(state.settledPage, dragged, n, active) {
        fill.snapTo(0f)
        if (n < 2 || dragged || !active) return@LaunchedEffect
        fill.animateTo(1f, tween(Motion.HERO_INTERVAL_MS.toInt(), easing = LinearEasing))
        state.animateScrollToPage(state.settledPage + 1, animationSpec = tween(1000, easing = Motion.Emphasized))
    }

    Box(modifier.fillMaxWidth().height(l.heroHeight)) {
        HorizontalPager(state, Modifier.fillMaxSize(), userScrollEnabled = l.isTouch && n > 1, beyondViewportPageCount = 1) { page ->
            val w = works[page % n]
            val offset = (state.currentPage - page) + state.currentPageOffsetFraction
            HeroPage(w, label(page % n, w), page == state.currentPage, offset)
        }
        // boutons et indicateurs (fixes)
        val compact = l.isCompact
        Column(
            Modifier.align(if (compact) Alignment.BottomCenter else Alignment.BottomStart)
                .padding(start = l.margin, end = l.margin, bottom = if (compact) 14.dp else 28.dp),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroButton(
                    if (current.resume != null) "Reprendre" else "Lecture", Icons.Default.PlayArrow, primary = true,
                    onClick = { onPlay(current) },
                    modifier = Modifier.then(if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocused() },
                )
                HeroButton(
                    "Plus d'infos", Icons.Outlined.Info, primary = false, onClick = { onInfo(current) },
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() }
                        // télécommande : → sur le dernier bouton passe à l'œuvre suivante
                        .onPreviewKeyEvent { e ->
                            if (e.key == Key.DirectionRight && n > 1) {
                                if (e.type == KeyEventType.KeyDown) scope.launch { state.animateScrollToPage(state.currentPage + 1, animationSpec = tween(700, easing = Motion.Emphasized)) }
                                true
                            } else false
                        },
                )
            }
            if (n > 1) {
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val activeDot = state.currentPage % n
                    repeat(n) { i ->
                        val width by animateDpAsState(if (i == activeDot) 28.dp else 8.dp, tween(Motion.MEDIUM), label = "dot")
                        Box(Modifier.width(width).height(4.dp).background(Color(0x55FFFFFF), RoundedCornerShape(2.dp))) {
                            if (i == activeDot) Box(Modifier.fillMaxHeight().fillMaxWidth(fill.value).background(Color.White, RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroPage(w: Work, label: String?, isCurrent: Boolean, offset: Float) {
    val l = LocalLayout.current
    val compact = l.isCompact
    val kb = remember { Animatable(1f) }
    LaunchedEffect(isCurrent) {
        if (isCurrent) { kb.snapTo(1f); kb.animateTo(1.12f, tween(11_000, easing = LinearEasing)) }
    }
    val textAlpha by animateFloatAsState(if (isCurrent) 1f else 0f, tween(Motion.SLOW), label = "heroText")
    Box(Modifier.fillMaxSize().graphicsLayer { clip = true }) {
        val image = if (compact) tmdbSized(w.poster, "w780") ?: tmdbSized(w.backdrop, "w1280")
        else tmdbSized(w.backdrop, "w1280") ?: tmdbSized(w.poster, "w780")
        // image + dégradés rendus une fois dans une texture (calque matériel) ; Ken Burns et parallaxe ne font
        // que déplacer/agrandir cette texture : plus aucun dégradé plein écran redessiné à chaque image
        // (mesuré sur Google TV Streamer : c'était la principale cause d'images en retard)
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                scaleX = kb.value; scaleY = kb.value
                // parallaxe : l'image glisse moins vite que la page
                translationX = offset * size.width * 0.35f
            }
        ) {
            NetImage(image, w.title, Modifier.fillMaxSize(), alignment = if (compact) Alignment.TopCenter else Alignment.TopEnd)
            if (compact) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.18f to Color.Transparent, 0.55f to Color.Transparent, 0.86f to Color(0xF20A0A0F), 1f to SokoulColors.Background)))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to SokoulColors.Background, 0.30f to Color(0xD90A0A0F), 0.62f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to SokoulColors.Background)))
            }
        }
        // texte
        Column(
            Modifier.align(if (compact) Alignment.BottomCenter else Alignment.BottomStart)
                .padding(start = l.margin, end = l.margin, bottom = if (compact) 92.dp else 108.dp)
                .widthIn(max = l.heroTextWidth)
                .graphicsLayer { alpha = textAlpha; translationY = (1f - textAlpha) * 24f },
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (label != null) {
                Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.background(SokoulColors.Live, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text("TOP", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextPrimary, fontWeight = FontWeight.Bold)
                }
            } else if (w.platform != null) {
                PlatformBadge(w.platform, Modifier.padding(bottom = 10.dp), large = true)
            }
            // téléphone : l'affiche porte déjà le titre de l'œuvre ; un logo par-dessus le répéterait
            if (compact && w.poster != null) {
                // rien : le titre est lu par l'image (contentDescription = titre)
            } else if (w.logo != null) {
                NetImage(
                    tmdbSized(w.logo, "w500"), w.title,
                    Modifier.heightIn(max = if (compact) 96.dp else (l.heroHeight * 0.26f).coerceAtMost(130.dp)).widthIn(max = if (compact) 260.dp else l.heroTextWidth * 0.8f),
                    ContentScale.Fit, alignment = if (compact) Alignment.BottomCenter else Alignment.BottomStart,
                ) { HeroTitle(w.title, compact) }
            } else {
                HeroTitle(w.title, compact)
            }
            if (!compact && w.tagline != null) {
                Text(
                    w.tagline, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic,
                    color = SokoulColors.TextPrimary.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val bits = heroMeta(w, compact)
            if (bits.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, if (compact) Alignment.CenterHorizontally else Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!compact && w.rating != null && w.rating >= MIN_RATING) RatingChip(w.rating)
                    bits.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextSecondary) }
                    if (w.certification != null && !compact) {
                        Text(
                            certificationLabel(w.certification),
                            Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            val overview = w.overviewFr
            if (!compact && overview != null) {
                Text(
                    overview, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium,
                    color = SokoulColors.TextSecondary, maxLines = if (l.isTv) 2 else 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun heroMeta(w: Work, compact: Boolean): List<String> =
    if (compact) w.genres.take(3).ifEmpty { listOfNotNull(w.year?.toString()) }.let { listOf(it.joinToString("  •  ")) }.filter { it.isNotBlank() }
    else listOfNotNull(
        w.year?.toString(),
        w.genres.take(2).joinToString(", ").ifEmpty { null },
        if (w.type == ContentType.SERIES) w.seasons?.let { if (it > 1) "$it saisons" else "1 saison" } else formatRuntime(w.runtimeMin),
        if (w.versions > 1) "${w.versions} versions" else null,
    )

/** Classification française : « 12 » → « -12 », « TP » → « Tous publics ». */
fun certificationLabel(c: String): String = when {
    c.equals("TP", true) || c.equals("U", true) || c == "0" || c == "0+" -> "Tous publics"
    c.trimEnd('+').all(Char::isDigit) -> "-" + c.trimEnd('+')
    else -> c
}

@Composable
private fun HeroTitle(title: String, compact: Boolean) {
    val l = LocalLayout.current
    Text(
        title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (compact) TextAlign.Center else TextAlign.Start,
        style = TextStyle(
            fontSize = l.heroTitle, lineHeight = l.heroTitle * 1.08f, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp,
            color = Color.White, shadow = Shadow(Color(0x99000000), blurRadius = 12f),
        ),
    )
}

/** Bouton du héros : blanc plein (lecture) ou translucide. */
@Composable
fun HeroButton(text: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val l = LocalLayout.current
    FocusSurface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(if (l.isTv) 10.dp else 8.dp),
        // TV : seul l'élément focalisé est blanc (sinon le bouton principal semble sélectionné à la place du focus)
        color = if (primary && !l.isTv) Color.White else if (primary) Color(0x73FFFFFF) else Color(0x59FFFFFF),
        focusedColor = if (primary || l.isTv) Color.White else Color(0x8CFFFFFF),
        focusScale = 1.08f, borderWidth = if (primary || l.isTv) 0.dp else 2.dp,
    ) { focused ->
        Row(
            Modifier.height(if (l.isCompact) 42.dp else 44.dp).padding(horizontal = if (l.isCompact) 18.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = if ((primary && !l.isTv) || (l.isTv && focused)) Color(0xFF0A0A0F) else Color.White
            Icon(icon, null, Modifier.size(22.dp), tint = c)
            Spacer(Modifier.width(8.dp))
            Text(text, color = c, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

