package com.sokoul.iptv.ui.screens.vod

import com.sokoul.iptv.ui.components.FocusStyle
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.repo.EpisodeResolver
import com.sokoul.iptv.data.repo.EpisodeView
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MIN_RATING
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.ProgressLine
import com.sokoul.iptv.ui.components.RatingChip
import com.sokoul.iptv.ui.components.certificationLabel
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.formatRuntime
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// =====================================================================================================
// Données affichées par l'en-tête (communes au film et à la série)
// =====================================================================================================

@Immutable
data class DetailHeroData(
    val title: String,
    val logo: String?,
    val backdrop: String?,
    val poster: String?,
    val rating: Double?,
    /** Année, durée ou saisons, nombre de versions… séparés par « · ». */
    val facts: List<String>,
    val certification: String?,
    /** Qualité principale (HD, 4K) affichée dans un cadre. */
    val quality: String?,
    val genres: String?,
    val tagline: String?,
    val synopsis: Synopsis?,
    /** « Réalisation : … » / « Création : … ». */
    val credit: String?,
    val watched: Boolean = false,
    /** Notes externes (OMDb) : « IMDb » → « 8,7 », « Rotten Tomatoes » → « 73 % ». */
    val externalRatings: List<Pair<String, String>> = emptyList(),
    /** Services de streaming en France (Movie of the Night) : nom et couleur de la marque. */
    val availability: List<Pair<String, Long?>> = emptyList(),
    /** « Prochain épisode : S3 · É4, mardi 12 novembre » (séries en cours, TVmaze). */
    val upcoming: String? = null,
)

/** Bouton principal : libellé, détail (« 18 min restantes ») et progression intégrée. */
@Immutable
data class PrimaryAction(val label: String, val detail: String?, val progress: Float?, val onClick: () -> Unit)

/** Action secondaire (icône + texte). [key] sert à la mémoire du focus. */
@Immutable
data class SecondaryAction(val key: String, val label: String, val icon: ImageVector, val onClick: () -> Unit)

// =====================================================================================================
// Mise en page
// =====================================================================================================

/**
 * Fiche « style Apple TV » sur TV / grand écran : le premier écran est le fond en plein cadre avec, en bas
 * à gauche, le titre, les infos et une seule rangée de boutons. En descendant, les sections (épisodes,
 * versions, distribution…) glissent par-dessus un fond qui s'assombrit et se floute.
 * Téléphone : image en haut, contenu en colonne.
 *
 * @param primary null pendant le chargement (emplacement en squelette, non focalisable).
 * @param hint indication sous les boutons (« Épisodes », « Plus d'infos »).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScaffold(
    nav: NavHostController,
    hero: DetailHeroData,
    primary: PrimaryAction?,
    primaryLoading: Boolean,
    actions: List<SecondaryAction>,
    hint: String?,
    memory: FocusMemory,
    onSynopsis: () -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    val l = LocalLayout.current
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // l'entrée animée ne se joue qu'une fois (pas au retour du lecteur, ni en remontant dans la fiche)
    val entered = rememberSaveable { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { delay(Motion.STAGGER * 8L + Motion.MEDIUM); entered.value = true }

    val heroPx = with(density) { l.heightDp.dp.toPx() }
    // 0 = premier écran, 1 = sections au premier plan (fond assombri)
    val depth by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / (heroPx * 0.5f)).coerceIn(0f, 1f) } }
    val deep by remember { derivedStateOf { depth > 0f } }

    // Ken Burns : lent zoom du fond, une seule fois ; en pause dès qu'on descend dans la fiche
    // (le fond est alors assombri et le défilement doit rester fluide sur les box)
    val kb = remember(hero.backdrop) { Animatable(1f) }
    LaunchedEffect(kb, reduced, deep) {
        if (reduced || deep || kb.value >= KB_MAX) return@LaunchedEffect
        val left = ((KB_MAX - kb.value) / (KB_MAX - 1f) * KB_MS).toInt().coerceAtLeast(1)
        kb.animateTo(KB_MAX, tween(left, easing = LinearEasing))
    }

    if (l.isCompact) {
        PhoneDetail(nav, hero, primary, primaryLoading, actions, memory, entered, kb, listState, onSynopsis, sections)
        return
    }

    // défilement piloté par le focus : en-tête → tout en haut ; sections → placées vers le haut de l'écran
    val heroFocused = remember { booleanArrayOf(true) }
    val spec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (heroFocused[0]) 0f else offset - containerSize * 0.16f
        }
    }
    val defaultSpec = LocalBringIntoViewSpec.current

    // Retour depuis les sections : on remonte d'abord en haut de la fiche (comme Apple TV), puis Retour quitte
    androidx.activity.compose.BackHandler(enabled = depth > 0f) {
        scope.launch {
            listState.animateScrollToItem(0)
            withFrameNanos { }
            runCatching { memory.requester("play").requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        DetailBackdrop(hero, kb, depthProvider = { depth }, reduced = reduced)
        androidx.compose.runtime.CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 72.dp)) {
                item("hero") {
                    androidx.compose.runtime.CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        Box(Modifier.fillMaxWidth().height(l.heightDp.dp)) {
                            Column(
                                Modifier.align(Alignment.BottomStart)
                                    .padding(start = l.margin, end = l.margin, bottom = 36.dp)
                                    .onFocusChanged { f ->
                                        heroFocused[0] = f.hasFocus
                                        if (f.hasFocus && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                                            scope.launch { listState.animateScrollToItem(0) }
                                        }
                                    },
                            ) {
                                HeroBlock(hero, primary, primaryLoading, actions, memory, entered, onSynopsis)
                                if (hint != null) ScrollHint(hint, Modifier.padding(top = 22.dp).graphicsLayer { alpha = 1f - depth }.entrance(6, entered))
                            }
                        }
                    }
                }
                sections()
            }
        }
    }
}

private const val KB_MAX = 1.08f
private const val KB_MS = 26_000

/** Fond plein cadre : Ken Burns, dégradés de lisibilité, assombri quand on descend. */
@Composable
private fun DetailBackdrop(hero: DetailHeroData, kb: Animatable<Float, *>, depthProvider: () -> Float, reduced: Boolean) {
    val img = hero.backdrop
    // Tout le fond (image + dégradés) est rendu une fois dans une texture (calque matériel) : au défilement,
    // seule l'opacité du calque change, sans redessiner l'image ni les dégradés plein écran (mesuré : c'était
    // la cause des images en retard sur Google TV Streamer). Le fond s'assombrit en laissant voir le noir derrière.
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = 1f - 0.7f * depthProvider()
            // Ken Burns appliqué à la texture entière (dégradés compris) : agrandir une texture prête ne la redessine pas
            scaleX = kb.value; scaleY = kb.value
        }
    ) {
        if (img != null) {
            Box(Modifier.fillMaxSize()) {
                // image de la carte (déjà en cache) d'abord ; dès que la haute définition est prête, la petite
                // n'est plus dessinée (une seule image plein écran à rendre)
                var hiReady by remember(img) { mutableStateOf(false) }
                if (!hiReady) NetImage(tmdbSized(img, "w780"), null, Modifier.fillMaxSize())
                coil.compose.AsyncImage(
                    tmdbSized(img, "w1280"), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    onSuccess = { hiReady = true },
                )
            }
        } else if (hero.poster != null) {
            // pas de fond : l'affiche floutée en plein cadre, puis l'affiche nette à droite
            NetImage(tmdbSized(hero.poster, "w342"), null, Modifier.fillMaxSize().graphicsLayer {
                alpha = 0.35f
                if (!reduced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) renderEffect = BlurEffect(60f, 60f, TileMode.Clamp)
            })
            val l = LocalLayout.current
            NetImage(
                tmdbSized(hero.poster, "w500"), hero.title,
                Modifier.align(Alignment.CenterEnd).padding(end = l.margin * 2).fillMaxHeight(0.72f).aspectRatio(2f / 3f).clip(Radius.Panel),
            )
        }
        // lisibilité du texte : dégradés à gauche et en bas (dans la même texture que l'image)
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val left = Brush.horizontalGradient(0f to SokoulColors.Background, 0.34f to SokoulColors.Background.copy(alpha = 0.86f), 0.68f to Color.Transparent)
                val bottom = Brush.verticalGradient(0.42f to Color.Transparent, 1f to SokoulColors.Background)
                onDrawBehind { drawRect(left); drawRect(bottom) }
            }
        )
    }
}

/** Logo/titre, infos, résumé et boutons, avec une apparition décalée élément par élément. */
@Composable
private fun HeroBlock(
    hero: DetailHeroData,
    primary: PrimaryAction?,
    primaryLoading: Boolean,
    actions: List<SecondaryAction>,
    memory: FocusMemory,
    entered: MutableState<Boolean>,
    onSynopsis: () -> Unit,
) {
    val l = LocalLayout.current
    val tw = if (l.isCompact) Modifier else Modifier.widthIn(max = (l.widthDp * 0.5f).dp)
    Box(tw.entrance(0, entered)) {
        if (hero.logo != null) {
            NetImage(
                tmdbSized(hero.logo, "w500"), hero.title,
                Modifier.heightIn(max = if (l.isCompact) 84.dp else 120.dp).widthIn(max = if (l.isCompact) 280.dp else 440.dp),
                ContentScale.Fit, alignment = Alignment.BottomStart,
            ) { DetailTitle(hero.title) }
        } else DetailTitle(hero.title)
    }
    hero.tagline?.let {
        Text(
            it, tw.padding(top = 10.dp).entrance(1, entered), style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic,
            color = SokoulColors.TextPrimary.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
    FactsLine(hero, tw.padding(top = 12.dp).entrance(2, entered))
    if (!hero.genres.isNullOrBlank()) {
        Text(hero.genres, tw.padding(top = 6.dp).entrance(2, entered), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    hero.upcoming?.let {
        Row(tw.padding(top = 8.dp).entrance(2, entered), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Event, null, Modifier.size(16.dp), tint = SokoulColors.Accent)
            Text(it, Modifier.padding(start = 6.dp), color = SokoulColors.TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    hero.synopsis?.let { s ->
        Column(tw.padding(top = 14.dp).entrance(3, entered)) {
            Text(
                s.text, Modifier.then(if (l.isTouch) Modifier.dpadClickable(onSynopsis) else Modifier),
                style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextPrimary.copy(alpha = 0.9f),
                maxLines = if (l.isCompact) 4 else 3, overflow = TextOverflow.Ellipsis,
            )
            if (!s.french) Text("Résumé disponible en anglais uniquement", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted)
        }
    }
    // une seule rangée : action principale, puis actions secondaires
    Row(
        Modifier.padding(top = 22.dp).entrance(4, entered).then(if (l.isCompact) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primary != null) {
            DetailPrimaryButton(primary, Modifier.focusMemory(memory, "play").then(if (l.isCompact) Modifier.weight(1f) else Modifier))
        } else if (primaryLoading) {
            Box(Modifier.size(if (l.isCompact) 160.dp else 220.dp, 52.dp).shimmer(Radius.Card).semantics { contentDescription = "Chargement" })
        }
        if (!l.isCompact) actions.forEach { a -> DetailActionButton(a, Modifier.focusMemory(memory, a.key)) }
    }
    if (l.isCompact && actions.isNotEmpty()) {
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().entrance(5, entered), horizontalArrangement = Arrangement.SpaceEvenly) {
            actions.forEach { a -> PhoneActionButton(a, Modifier.focusMemory(memory, a.key)) }
        }
    }
    hero.credit?.let {
        Text(it, tw.padding(top = 14.dp).entrance(5, entered), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (hero.availability.isNotEmpty()) AvailabilityLine(hero.availability, tw.padding(top = if (hero.credit != null) 8.dp else 14.dp).entrance(5, entered))
}

/** « Aussi sur  ● Netflix  ● Prime Video » : nom du service et pastille à sa couleur (pas de logo de marque). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailabilityLine(services: List<Pair<String, Long?>>, modifier: Modifier) {
    FlowRow(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Aussi sur " + services.joinToString(", ") { it.first } },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Aussi sur", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary)
        services.take(MAX_SERVICES).forEach { (name, color) ->
            Row(
                Modifier.background(SokoulColors.GlassLight.copy(alpha = 0.12f), Radius.Pill).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // contour clair : une couleur de marque sombre (Disney+) reste visible sur le fond
                Box(Modifier.size(9.dp).background(color?.let { Color(it) } ?: SokoulColors.TextSecondary, Radius.Round).border(1.dp, Color.White.copy(alpha = 0.55f), Radius.Round))
                Text(name, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextPrimary, maxLines = 1)
            }
        }
    }
}

private const val MAX_SERVICES = 5

/** Note externe : « IMDb 8,7 » (fond jaune IMDb), « RT 73 % », « MC 74 ». */
@Composable
private fun ExternalRating(source: String, value: String) {
    val imdb = source == "IMDb"
    Row(
        Modifier.then(if (imdb) Modifier.background(SokoulColors.Imdb, Radius.Tiny) else Modifier.border(1.dp, SokoulColors.TextMuted, Radius.Tiny))
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$source $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(source, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = if (imdb) Color.Black else SokoulColors.TextSecondary)
        Text(value, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (imdb) Color.Black else SokoulColors.TextPrimary)
    }
}

@Composable
private fun DetailTitle(title: String) {
    val l = LocalLayout.current
    Text(
        title, style = if (l.isCompact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

/** ★ 7,4 · 2026 · 1 h 50 · [-12] [4K] · ✓ Vu */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactsLine(hero: DetailHeroData, modifier: Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hero.rating != null && hero.rating >= MIN_RATING) RatingChip(hero.rating, Modifier.align(Alignment.CenterVertically))
        hero.externalRatings.forEach { (source, value) -> Box(Modifier.align(Alignment.CenterVertically)) { ExternalRating(source, value) } }
        val lead = (hero.rating != null && hero.rating >= MIN_RATING) || hero.externalRatings.isNotEmpty()
        hero.facts.forEachIndexed { i, f ->
            if (i > 0 || lead) Dot()
            Text(f, Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextSecondary)
        }
        hero.certification?.let { Badge(certificationLabel(it), filled = true) }
        hero.quality?.let { Badge(it, filled = false) }
        if (hero.watched) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = SokoulColors.Success)
                Text("Vu", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = SokoulColors.Success)
            }
        }
    }
}

@Composable
private fun Dot() = Text("·", style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextMuted)

@Composable
private fun Badge(text: String, filled: Boolean) = com.sokoul.iptv.ui.components.SourceBadge(text, filled)

// =====================================================================================================
// Boutons
// =====================================================================================================

/**
 * Bouton principal : « ▶ Reprendre S1 · É3 » avec la progression intégrée sous le libellé.
 * TV : blanc au focus seulement (le focus reste évident) ; tactile : toujours blanc.
 */
@Composable
fun DetailPrimaryButton(a: PrimaryAction, modifier: Modifier = Modifier) {
    val l = LocalLayout.current
    FocusSurface(style = FocusStyle.Chip,
        onClick = a.onClick, modifier = modifier.semantics { contentDescription = listOfNotNull(a.label, a.detail).joinToString(", ") },
        shape = Radius.Card, color = if (l.isTv) SokoulColors.GlassHeavy else Color.White, focusedColor = Color.White,
    ) { focused ->
        val c = if (!l.isTv || focused) SokoulColors.OnLight else Color.White
        Row(
            Modifier.height(if (l.isCompact) 50.dp else 54.dp).padding(start = 18.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (l.isCompact) Arrangement.Center else Arrangement.Start,
        ) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(28.dp), tint = c)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(a.label, color = c, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (a.progress != null || a.detail != null) {
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (a.progress != null) {
                            Box(Modifier.width(72.dp).height(3.dp).clip(Radius.Tiny).background(c.copy(alpha = 0.28f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(a.progress.coerceIn(0.03f, 1f)).background(if (focused || !l.isTv) SokoulColors.Accent else Color.White))
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        a.detail?.let { Text(it, color = c.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }
                }
            }
        }
    }
}

/**
 * Action secondaire TV / tablette : bouton rond en verre (icône seule) qui s'élargit au focus pour
 * afficher son libellé. La rangée reste compacte et le libellé apparaît là où se trouve le regard.
 */
@Composable
fun DetailActionButton(a: SecondaryAction, modifier: Modifier = Modifier) {
    FocusSurface(style = FocusStyle.Chip,
        onClick = a.onClick, modifier = modifier.semantics { contentDescription = a.label }, shape = Radius.Pill,
        color = SokoulColors.GlassLight, focusedColor = Color.White,
    ) { focused ->
        val c = if (focused) SokoulColors.OnLight else Color.White
        Row(
            Modifier.height(54.dp).widthIn(min = 54.dp).animateContentSize(Motion.enter()).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            MorphIcon(a.icon, c, 22)
            AnimatedVisibility(focused, enter = fadeIn(Motion.enter()) + expandHorizontally(Motion.enter()), exit = fadeOut(Motion.exit()) + shrinkHorizontally(Motion.exit())) {
                Text(a.label, Modifier.padding(start = 8.dp), color = c, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Action secondaire téléphone : icône au-dessus du libellé, comme les applis de streaming mobiles. */
@Composable
private fun PhoneActionButton(a: SecondaryAction, modifier: Modifier = Modifier) {
    FocusSurface(style = FocusStyle.Chip, onClick = a.onClick, modifier = modifier.widthIn(min = 72.dp), shape = Radius.Card, color = Color.Transparent, focusedColor = SokoulColors.SurfaceHigh) { _ ->
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MorphIcon(a.icon, Color.White, 26)
            Text(a.label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextSecondary, maxLines = 1)
        }
    }
}

/** Icône qui change en douceur (« + » → « ✓ » pour Ma liste). */
@Composable
private fun MorphIcon(icon: ImageVector, tint: Color, sizeDp: Int) {
    AnimatedContent(
        icon,
        transitionSpec = { (scaleIn(Motion.enter(), initialScale = 0.4f) + fadeIn(Motion.enter())) togetherWith (scaleOut(Motion.exit(), targetScale = 0.4f) + fadeOut(Motion.exit())) },
        label = "icon",
    ) { ic -> Icon(ic, null, Modifier.size(sizeDp.dp), tint = tint) }
}

/** « ▼ Épisodes » : invite à descendre, pulsation lente. */
@Composable
private fun ScrollHint(text: String, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "hint")
    val dy by t.animateFloat(0f, if (reduced) 0f else 5f, infiniteRepeatable(tween(900, easing = Motion.Emphasized), RepeatMode.Reverse), label = "dy")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(22.dp).graphicsLayer { translationY = dy }, tint = SokoulColors.TextSecondary)
        Text(text, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextSecondary)
    }
}

/** Apparition décalée (fondu + léger glissement vers le haut), jouée une seule fois par fiche. */
@Composable
fun Modifier.entrance(index: Int, entered: MutableState<Boolean>): Modifier {
    val a = remember { Animatable(if (entered.value) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (a.value < 1f) { delay(index * Motion.STAGGER.toLong()); a.animateTo(1f, Motion.enter()) }
    }
    val dy = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * dy }
}

// =====================================================================================================
// Téléphone
// =====================================================================================================

@Composable
private fun PhoneDetail(
    nav: NavHostController,
    hero: DetailHeroData,
    primary: PrimaryAction?,
    primaryLoading: Boolean,
    actions: List<SecondaryAction>,
    memory: FocusMemory,
    entered: MutableState<Boolean>,
    kb: Animatable<Float, *>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSynopsis: () -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    val l = LocalLayout.current
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 56.dp)) {
            item("image") {
                val img = hero.backdrop ?: hero.poster
                Box(Modifier.fillMaxWidth().aspectRatio(if (hero.backdrop != null) 16f / 10f else 4f / 5f)) {
                    NetImage(
                        tmdbSized(img, if (hero.backdrop != null) "w780" else "w500"), hero.title,
                        Modifier.fillMaxSize().clipToBounds().graphicsLayer { scaleX = kb.value; scaleY = kb.value }, alignment = Alignment.TopCenter,
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to SokoulColors.Scrim.copy(alpha = 0.5f), 0.25f to Color.Transparent, 0.65f to Color.Transparent, 1f to SokoulColors.Background)))
                }
            }
            item("hero") {
                Column(Modifier.padding(horizontal = l.margin).padding(top = 4.dp)) { HeroBlock(hero, primary, primaryLoading, actions, memory, entered, onSynopsis) }
            }
            sections()
        }
        Box(
            Modifier.statusBarsPadding().padding(8.dp).size(44.dp).clip(Radius.Round).background(SokoulColors.Scrim).dpadClickable({ nav.popBackStack() })
                .semantics { contentDescription = "Retour" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
    }
}

// =====================================================================================================
// Sections
// =====================================================================================================

/** Section de fiche : titre + accroche facultative + contenu. */
fun LazyListScope.detailSection(key: String, title: String?, tagline: String? = null, content: @Composable () -> Unit) {
    item(key) {
        val l = LocalLayout.current
        Column(Modifier.padding(top = if (l.isTv) 34.dp else 26.dp)) {
            if (title != null) Text(title, Modifier.padding(horizontal = l.margin), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (tagline != null) Text(tagline, Modifier.padding(horizontal = l.margin, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextMuted)
            if (title != null) Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Onglets de saison : soulignement qui s'étire sous la saison affichée. Passer sur un onglet l'affiche
 * (après un court délai, pour ne pas tout recharger quand on traverse les onglets).
 */
@Composable
fun SeasonTabs(
    seasons: List<Int>,
    current: Int?,
    seen: (Int) -> Boolean,
    memory: FocusMemory,
    onSelect: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedTab by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(focusedTab) {
        val f = focusedTab ?: return@LaunchedEffect
        delay(260)
        if (f != current) onSelect(f)
    }
    // l'onglet sélectionné reste composé (visible) : ▼ depuis les boutons y entre toujours, sans changer de saison
    val rowState = rememberLazyListState()
    LaunchedEffect(current) {
        val i = seasons.indexOf(current)
        if (i >= 0 && rowState.layoutInfo.visibleItemsInfo.none { it.index == i }) rowState.animateScrollToItem(i)
    }
    val enterTab = current?.takeIf { it in seasons }?.let { memory.requester("season:$it") }
    LazyRow(
        modifier.focusProperties { if (enterTab != null) enter = { enterTab } }.focusGroup(), state = rowState,
        horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = LocalLayout.current.margin),
    ) {
        itemsIndexed(seasons, key = { _, n -> n }) { _, n ->
            val selected = n == current
            val line by animateDpAsState(if (selected) 28.dp else 0.dp, Motion.enter(), label = "tab")
            FocusSurface(style = FocusStyle.Chip,
                onClick = { onSelect(n) }, onLongClick = { onLongClick(n) },
                modifier = Modifier.focusMemory(memory, "season:$n").onFocusChanged { if (it.isFocused) focusedTab = n },
                shape = Radius.Pill, color = Color.Transparent, focusedColor = Color.White,
            ) { focused ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Saison $n" + if (seen(n)) " ✓" else "",
                        style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = when { focused -> SokoulColors.OnLight; selected -> SokoulColors.TextPrimary; else -> SokoulColors.TextSecondary },
                    )
                    Box(Modifier.padding(top = 4.dp).width(line).height(3.dp).background(if (focused) SokoulColors.OnLight else SokoulColors.Accent, Radius.Tiny))
                }
            }
        }
    }
}

/**
 * Épisodes en rangée de cartes 16:9 (TV / tablette). La saison change en glissant ; l'épisode focalisé
 * affiche son résumé sous la rangée. ▼ depuis les onglets mène directement à l'épisode courant.
 */
@Composable
fun EpisodeShelf(
    season: Int?,
    episodes: List<EpisodeView>,
    progressOf: (EpisodeView) -> ProgressEntity?,
    currentKey: Pair<Int, Int>?,
    memory: FocusMemory,
    onPlay: (EpisodeView) -> Unit,
    onLongClick: (EpisodeView) -> Unit,
    fallbackImage: String? = null,
) {
    val l = LocalLayout.current
    var focusedEp by remember(season) { mutableStateOf<EpisodeView?>(null) }
    var previous by remember { mutableStateOf(season) }
    val forward = (season ?: 0) >= (previous ?: 0)
    LaunchedEffect(season) { previous = season }
    Column {
        AnimatedContent(
            season to episodes,
            contentKey = { it.first },
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(Motion.enter()) { it / 6 * dir } + fadeIn(Motion.enter())) togetherWith
                    (slideOutHorizontally(Motion.exit()) { -it / 6 * dir } + fadeOut(Motion.exit())) using SizeTransform(clip = false)
            },
            label = "season",
        ) { (s, eps) ->
            val rowState = rememberLazyListState()
            val currentIndex = eps.indexOfFirst { (it.entity.season to it.entity.episode) == currentKey }.takeIf { it >= 0 } ?: 0
            LaunchedEffect(s) { if (currentIndex > 0) rowState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }
            val target = eps.getOrNull(currentIndex)?.let { memory.requester("ep:${it.entity.season}:${it.entity.episode}") }
            LazyRow(
                Modifier.focusProperties { if (target != null) enter = { target } }.focusGroup(),
                state = rowState, contentPadding = PaddingValues(horizontal = l.margin, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(eps, key = { _, ev -> ev.entity.id }) { _, ev ->
                    EpisodeCard(
                        ev, progressOf(ev), current = (ev.entity.season to ev.entity.episode) == currentKey,
                        modifier = Modifier.focusMemory(memory, "ep:${ev.entity.season}:${ev.entity.episode}")
                            .onFocusChanged { if (it.isFocused) focusedEp = ev },
                        onClick = { onPlay(ev) }, onLongClick = { onLongClick(ev) }, fallbackImage = fallbackImage,
                    )
                }
            }
        }
        // résumé de l'épisode focalisé (hauteur fixe : rien ne saute pendant la navigation)
        Box(Modifier.padding(horizontal = l.margin).padding(top = 4.dp).height(74.dp).widthIn(max = 900.dp)) {
            AnimatedContent(focusedEp, transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) }, label = "epinfo") { ev ->
                if (ev != null) {
                    Column {
                        Text(
                            "S${ev.entity.season} · É${ev.entity.episode} — ${ev.title}", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        ev.overview?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

/** Carte d'épisode : vignette, progression, « Vu », repère de l'épisode courant ; lecture au focus. */
@Composable
fun EpisodeCard(ev: EpisodeView, progress: ProgressEntity?, current: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit, fallbackImage: String? = null) {
    val l = LocalLayout.current
    val w = l.landscape * 1.1f
    val watched = progress?.finished == true
    val resumable = progress != null && !progress.finished && progress.durationMs > 0 && LibraryRepository.resumeFrom(progress) > 0
    Column(Modifier.width(w)) {
        FocusSurface(
            onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).semantics { contentDescription = "Épisode ${ev.entity.episode} : ${ev.title}" + if (watched) ", vu" else "" },
            shape = Radius.Card, color = SokoulColors.SurfaceHigher,
        ) { focused ->
            Box(Modifier.fillMaxSize()) {
                NetImage(tmdbSized(ev.image, "w500"), null, Modifier.fillMaxSize().graphicsLayer { alpha = if (watched && !focused) 0.5f else 1f }) {
                    // image fournisseur absente ou cassée : fond de la série assombri + grand numéro
                    Box(Modifier.fillMaxSize()) {
                        if (fallbackImage != null && fallbackImage != ev.image) NetImage(tmdbSized(fallbackImage, "w780"), null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f })
                        Text(
                            "${ev.entity.episode}", Modifier.align(Alignment.BottomEnd).padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = focused, enter = fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.7f), exit = fadeOut(Motion.exit()), modifier = Modifier.align(Alignment.Center)) {
                    Box(Modifier.size(46.dp).background(Color.White, Radius.Round), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, tint = SokoulColors.OnLight)
                    }
                }
                if (current && !watched) {
                    Text(
                        if (resumable) "En cours" else "À suivre",
                        Modifier.align(Alignment.TopStart).padding(8.dp).background(SokoulColors.Accent, Radius.Card).padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
                if (watched) Icon(Icons.Default.CheckCircle, "Vu", Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp), tint = SokoulColors.Success)
                if (resumable) ProgressLine(progress!!.positionMs.toFloat() / progress.durationMs, Modifier.align(Alignment.BottomStart))
            }
        }
        Text(
            "${ev.entity.episode}. ${ev.title}", Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(ev.runtimeMin?.let { formatRuntime(it) }, progress?.takeIf { resumable }?.let { EpisodeResolver.remaining(it) }).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = if (current) SokoulColors.Accent else SokoulColors.TextMuted, maxLines = 1)
    }
}

/** Résumé complet (TV : depuis le menu ⋯ ; tactile : en touchant le résumé). */
@Composable
fun SynopsisDialog(title: String, text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val close = remember { FocusRequester() }
        Column(
            Modifier.fillMaxWidth(0.9f).widthIn(max = 760.dp).clip(Radius.Panel).background(SokoulColors.Panel).padding(28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text, Modifier.padding(top = 14.dp).heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextPrimary.copy(alpha = 0.92f),
            )
            Spacer(Modifier.height(20.dp))
            FocusSurface(style = FocusStyle.Chip, onClick = onDismiss, modifier = Modifier.focusRequester(close), shape = Radius.Card, color = SokoulColors.GlassLight, focusedColor = Color.White) { f ->
                Text("Fermer", Modifier.padding(horizontal = 22.dp, vertical = 12.dp), color = if (f) SokoulColors.OnLight else Color.White, fontWeight = FontWeight.Bold)
            }
        }
        LaunchedEffect(Unit) { delay(60); runCatching { close.requestFocus() } }
    }
}

/** Squelette fidèle à la fiche TV (bloc en bas à gauche) ou téléphone (image puis texte). */
@Composable
fun DetailSkeleton() {
    val l = LocalLayout.current
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        if (l.isCompact) Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).shimmer(Radius.None))
        Column(
            Modifier.align(if (l.isCompact) Alignment.TopStart else Alignment.BottomStart)
                .padding(start = l.margin, end = l.margin, bottom = 36.dp, top = if (l.isCompact) 280.dp else 0.dp)
                .widthIn(max = 640.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.62f).height(if (l.isCompact) 38.dp else 72.dp).shimmer(Radius.Card))
            Box(Modifier.padding(top = 16.dp).fillMaxWidth(0.45f).height(16.dp).shimmer(Radius.Card))
            repeat(3) { Box(Modifier.padding(top = 12.dp).fillMaxWidth(if (it == 2) 0.6f else 1f).height(14.dp).shimmer(Radius.Card)) }
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(220.dp, 54.dp).shimmer(Radius.Card))
                if (!l.isCompact) repeat(2) { Box(Modifier.size(150.dp, 54.dp).shimmer(Radius.Card)) }
            }
        }
    }
}

/** Squelette de la rangée d'épisodes pendant le chargement. */
@Composable
fun EpisodeShelfSkeleton() {
    val l = LocalLayout.current
    Row(Modifier.padding(horizontal = l.margin, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(if (l.isCompact) 1 else 4) {
            Column(Modifier.width(if (l.isCompact) 280.dp else l.landscape * 1.1f)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).shimmer(Radius.Card))
                Box(Modifier.padding(top = 10.dp).fillMaxWidth(0.7f).height(14.dp).shimmer(Radius.Card))
            }
        }
    }
}
