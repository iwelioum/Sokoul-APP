package com.sokoul.iptv.ui.screens.browse

import com.sokoul.iptv.ui.components.SokoulMark
import com.sokoul.iptv.ui.components.SokoulChip
import com.sokoul.iptv.ui.components.onChip
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.nav.navigateOnce
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.State
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import com.sokoul.iptv.ui.LocalTopBar
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.BrowseKind
import com.sokoul.iptv.data.repo.SearchOrigin
import com.sokoul.iptv.data.repo.BrowseRow
import com.sokoul.iptv.data.repo.GridSpec
import com.sokoul.iptv.data.repo.Platform
import com.sokoul.iptv.data.repo.RowStyle
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.overviewFr
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.components.rememberAmbient
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.rememberFocusMemory
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.focus.focusRequester
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.HeroCarousel
import com.sokoul.iptv.ui.components.LandscapeCard
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PlatformBadge
import com.sokoul.iptv.ui.components.PlatformTileView
import com.sokoul.iptv.ui.components.RatingChip
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.SagaTile
import com.sokoul.iptv.ui.components.SeeAllCard
import com.sokoul.iptv.ui.components.SkeletonRow
import com.sokoul.iptv.ui.components.Top10Card
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.components.metaLine
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.nav.AllCategoriesRoute
import com.sokoul.iptv.ui.nav.BrowseGridRoute
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.SearchRoute
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.nav.SettingsRoute
import com.sokoul.iptv.ui.nav.SyncRoute
import com.sokoul.iptv.ui.screens.home.ChannelCard
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.rememberNow
import kotlinx.coroutines.launch

/** Ouvre la fiche d'une œuvre. */
fun NavHostController.openWork(w: Work) {
    navigateOnce(if (w.type == ContentType.MOVIE) MovieDetailRoute(w.id) else SeriesDetailRoute(w.id))
}

// délai avant de changer le panneau d'aperçu et le fond : pas de rafale d'images quand on maintient le D-pad
private const val SPOT_DEBOUNCE_MS = 220L
/** Délai sous lequel une perte de focus vient de la télécommande (◀ vers le menu) et doit être respectée. */
private const val FOCUS_KEY_WINDOW_MS = 600L

/** Panneau d'information de la carte focalisée (TV). */
private data class Spot(
    val title: String,
    val logo: String? = null,
    val meta: String? = null,
    val overview: String? = null,
    val image: String? = null,
    val platform: Platform? = null,
    val rating: Double? = null,
    val tagline: String? = null,
    /** Détails affichés une fois le curseur posé (genres, qualité, versions). */
    val details: String? = null,
)

private fun Work.toSpot(): Spot {
    val meta = metaLine()
    // détails = seulement ce que la ligne d'infos ne dit pas déjà
    val extra = (genres.take(3) + listOfNotNull(quality)).filter { !meta.contains(it, ignoreCase = true) }
    return Spot(
        title, logo, meta.ifEmpty { null }, overviewFr, backdrop ?: poster, platform, rating, tagline,
        details = extra.joinToString(" · ").ifEmpty { null },
    )
}

/** Délai d'arrêt avant d'afficher les détails du panneau d'aperçu. */
private const val SPOT_DETAILS_MS = 700L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(nav: NavHostController, vm: BrowseViewModel, title: String) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    val p = ui.playlist
    val retryFocus = remember { FocusRequester() }
    if (!ui.loading && p == null) {
        EmptyState(Icons.Default.LiveTv, "Aucune playlist", "Ajoutez un abonnement dans les réglages pour commencer.") {
            AppButton("Réglages", { nav.navigate(SettingsRoute) }, primary = true)
        }
        return
    }
    if (p != null && p.liveCount + p.movieCount + p.seriesCount == 0 && !ui.loading && ui.rows.all { it.isEmpty }) {
        EmptyState(Icons.Default.Sync, "Catalogue vide", "La playlist n'est pas encore synchronisée.") {
            AppButton("Synchroniser", { nav.navigate(SyncRoute(p.id)) }, primary = true, icon = Icons.Default.Sync)
        }
        return
    }
    if (ui.failed && ui.rows.all { it.isEmpty }) {
        EmptyState(Icons.Default.Refresh, "Impossible d'afficher cette page", "Vérifiez la connexion internet de l'appareil, puis réessayez.") {
            AppButton("Réessayer", vm::retry, primary = true, icon = Icons.Default.Refresh, modifier = Modifier.focusRequester(retryFocus))
        }
        LaunchedEffect(Unit) { delay(100); runCatching { retryFocus.requestFocus() } }
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playFocus = remember { FocusRequester() }
    // états lus seulement par des composables enfants : un déplacement du focus ne recompose pas tout l'écran
    val spot = remember { mutableStateOf<Spot?>(null) }
    var inRows by remember { mutableStateOf(false) }
    // TV : onglets du haut (univers Streaming) — effacés dans les rangées, Retour remonte au héros
    val bar = LocalTopBar.current
    if (bar != null) {
        LaunchedEffect(inRows) { bar.deep = inRows }
        bar.backToTop = {
            // le héros, hors écran, n'est pas composé : on remonte d'abord, puis focus sur « Lecture »
            scope.launch {
                listState.scrollToItem(0)
                repeat(8) {
                    androidx.compose.runtime.withFrameNanos { }
                    if (runCatching { playFocus.requestFocus() }.getOrDefault(false)) return@launch
                }
            }
            true
        }
        // focus sur les onglets : la page quitte l'état « rangées » (plus de panneau d'aperçu dessous)
        LaunchedEffect(bar.tabsFocused) {
            if (bar.tabsFocused && inRows) { inRows = false; listState.scrollToItem(0) }
        }
    }
    var menuFor by remember { mutableStateOf<Work?>(null) }
    // carte d'où l'on est parti (fiche, grille, lecteur) : le focus y revient au retour
    val memory = rememberFocusMemory()
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    // images d'ambiance et panneau d'aperçu : mis à jour quand le défilement au D-pad se pose
    val shownSpot = remember { mutableStateOf<Spot?>(null) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { spot.value }.collectLatest { s -> if (shownSpot.value != null) delay(SPOT_DEBOUNCE_MS); shownSpot.value = s }
    }
    // panneau d'aperçu (TV) : opaque une fois affiché ; le héros dessous n'est alors plus dessiné
    val spotShown = animateFloatAsState(if (inRows && l.isTv) 1f else 0f, tween(Motion.MEDIUM), label = "spot")
    val spotHeight = (l.heightDp * 0.44f).dp
    val spotPx = with(density) { spotHeight.roundToPx() }

    val hero = remember(ui.hero, l.isCompact) {
        val withImage = ui.hero.filter { if (l.isCompact) it.poster != null else it.backdrop != null }
        if (withImage.size >= 3) withImage else ui.hero.filter { it.backdrop != null || it.poster != null }
    }
    val rows = ui.rows.filter { !it.isEmpty }
    // ▼ depuis les onglets : « Lecture » du héros (s'il est affiché)
    bar?.down = { if (hero.isNotEmpty() && listState.firstVisibleItemIndex == 0) playFocus else null }

    val play: (Work) -> Unit = { w -> scope.launch { nav.navigateOnce(vm.playRoute(w)) } }
    val open: (Work) -> Unit = { w -> nav.openWork(w) }
    val seeAll: (BrowseRow) -> Unit = { r -> r.seeAll?.let { nav.navigateOnce(BrowseGridRoute(r.title, it)) } }

    // TV : le défilement est piloté par le focus (rangée focalisée sous le panneau d'information)
    val defaultSpec = LocalBringIntoViewSpec.current
    val noAutoScroll = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
        }
    }

    // TV : si la carte focalisée disparaît (rangée mise à jour après la lecture), Compose envoie le focus
    // au menu latéral. Sans appui de touche récent, on le ramène dans les rangées.
    var lastKeyAt by remember { mutableStateOf(0L) }
    var contentFocused by remember { mutableStateOf(false) }
    var focusLost by remember { mutableStateOf(0) }
    LaunchedEffect(focusLost) {
        if (focusLost == 0 || !l.isTv || menuFor != null) return@LaunchedEffect
        delay(120)
        if (contentFocused || android.os.SystemClock.uptimeMillis() - lastKeyAt < FOCUS_KEY_WINDOW_MS) return@LaunchedEffect
        if (!memory.restore(sameGroup = true)) runCatching { playFocus.requestFocus(FocusDirection.Enter) }
    }

    Box(
        // pas de fond : celui de la fenêtre (même couleur) suffit, une passe GPU plein écran de moins par image
        Modifier.fillMaxSize()
            .onPreviewKeyEvent { lastKeyAt = android.os.SystemClock.uptimeMillis(); false }
            .onFocusChanged { f ->
                if (contentFocused && !f.hasFocus) focusLost++
                contentFocused = f.hasFocus
            },
    ) {
        // fond d'ambiance (TV) : l'œuvre focalisée, très atténuée, derrière les rangées
        if (l.isTv) AmbientBackdrop(shownSpot, inRows, spotHeight)
        CompositionLocalProvider(LocalBringIntoViewSpec provides if (l.isTv) noAutoScroll else defaultSpec) {
            LazyColumn(
                Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(bottom = if (l.isTv) spotHeight + 40.dp else 40.dp),
            ) {
                item("hero") {
                    if (ui.loading && hero.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(l.heroHeight).shimmer(Radius.None))
                    } else if (hero.isNotEmpty()) {
                        HeroCarousel(
                            modifier = Modifier.graphicsLayer { alpha = if (spotShown.value >= 1f) 0f else 1f },
                            works = hero, onPlay = play, onInfo = open, playFocus = playFocus,
                            active = !inRows && listState.firstVisibleItemIndex == 0,
                            label = { i, w -> if (ui.heroRanked && i < 10) "N° ${i + 1} des ${if (w.type == ContentType.MOVIE) "films" else "séries"} cette semaine" else null },
                            onFocused = {
                                inRows = false
                                memory.forget()
                                if (l.isTv) scope.launch { listState.animateScrollToItem(0) }
                            },
                        )
                    } else {
                        Spacer(Modifier.height(if (l.isCompact) 96.dp else 24.dp))
                    }
                }
                p?.expiresAt?.let { exp ->
                    item("expiry") { ExpiryBanner(exp) }
                }
                rows.forEachIndexed { index, row ->
                    item(row.id) {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                            Box(
                                Modifier.onFocusChanged {
                                    if (it.hasFocus && l.isTv) {
                                        inRows = true
                                        // +1 : le héros, +1 éventuel : bandeau d'abonnement
                                        val i = listState.layoutInfo.visibleItemsInfo.firstOrNull { v -> v.key == row.id }?.index
                                            ?: (index + 1 + if (p?.expiresAt != null) 1 else 0)
                                        scope.launch { listState.animateScrollToItem(i, -spotPx + with(density) { 6.dp.roundToPx() }) }
                                    }
                                }
                            ) {
                                RowView(
                                    row, nav, vm,
                                    onOpen = open, onSeeAll = { seeAll(row) },
                                    onSpot = { spot.value = it },
                                    onMenu = { menuFor = it },
                                    memory = memory,
                                )
                            }
                        }
                    }
                }
                if (ui.loading) items(3) { SkeletonRow(landscape = it == 1) }
                if (ui.failed) item("failed") {
                    Row(Modifier.padding(horizontal = l.margin, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Une partie de la page n'a pas pu se charger.", Modifier.weight(1f, fill = false), color = SokoulColors.TextSecondary)
                        Spacer(Modifier.width(12.dp))
                        AppButton("Réessayer", vm::retry, icon = Icons.Default.Refresh)
                    }
                }
            }
        }

        // TV : panneau d'information de la carte focalisée, au-dessus des rangées
        if (l.isTv) {
            SpotLayer(shownSpot, spotShown, spotHeight)
        } else {
            TopBar(title, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, l.heroHeight.value * density.density, vm.kind, nav)
        }
    }

    menuFor?.let { w ->
        OptionsDialog(
            w.title,
            listOf(
                MenuOption(if (w.resume != null) "Reprendre" else "Lecture", Icons.Default.PlayArrow) { play(w) },
                MenuOption("Plus d'infos", Icons.Default.Info) { open(w) },
                MenuOption("Ajouter à / retirer de ma liste", Icons.Default.Add) { vm.toggleList(w) },
            ),
            subtitle = w.metaLine().ifEmpty { null },
            onDismiss = { menuFor = null },
        )
    }
    // TV : focus initial sur « Lecture » une seule fois ; au retour, sur la carte d'où l'on vient
    LaunchedEffect(hero.isNotEmpty(), rows.isNotEmpty()) {
        if (!l.isTv) return@LaunchedEffect
        // ouverte depuis un onglet : le focus reste sur les onglets
        if (bar?.arrivedByTab == true) return@LaunchedEffect
        if (memory.last != null) {
            // la rangée peut se recomposer juste après le retour (progression mise à jour) : on insiste,
            // puis on se rabat sur une autre carte de la même rangée plutôt que de laisser le menu latéral
            repeat(16) { i ->
                delay(80)
                if (memory.restore(sameGroup = i >= 12)) { initialFocusDone = true; return@LaunchedEffect }
            }
        }
        // premier affichage, ou retour d'une fiche ouverte depuis le héros : « Lecture » (jamais le menu latéral)
        if (hero.isEmpty()) return@LaunchedEffect
        repeat(5) { i ->
            delay(80)
            if (i == 1 && listState.firstVisibleItemIndex > 0) listState.scrollToItem(0) // héros hors écran : on remonte
            if (runCatching { playFocus.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) { initialFocusDone = true; return@LaunchedEffect }
        }
    }
}

@Composable
private fun RowView(
    row: BrowseRow,
    nav: NavHostController,
    vm: BrowseViewModel,
    onOpen: (Work) -> Unit,
    onSeeAll: () -> Unit,
    onSpot: (Spot) -> Unit,
    onMenu: (Work) -> Unit,
    memory: FocusMemory,
) {
    val l = LocalLayout.current
    fun m(key: Any) = Modifier.focusMemory(memory, "${row.id}|$key")
    val pid = vm.ui.value.playlist?.id ?: 0
    val seeAllAction = if (row.seeAll != null) onSeeAll else null
    Column(Modifier.padding(top = l.rowSpacing / 2, bottom = l.rowSpacing / 2)) {
        if (row.style != RowStyle.PLATFORMS) RowHeader(row.title, row.tagline, row.platform, seeAllAction)
        val tvSeeAll: (@Composable () -> Unit)? = if (l.isTv && seeAllAction != null) {
            { Box(m("__see_all")) { SeeAllCard(seeAllAction, landscape = row.style == RowStyle.LANDSCAPE || row.style == RowStyle.CONTINUE) } }
        } else null
        when (row.style) {
            RowStyle.POSTER -> CardRow(row.works, { it.uid }, trailing = tvSeeAll) { _, w ->
                WorkCard(w, { onOpen(w) }, m(w.uid), showPlatform = row.platform == null, onFocus = { onSpot(w.toSpot()) }, onLongClick = { onMenu(w) })
            }
            RowStyle.LANDSCAPE -> CardRow(row.works, { it.uid }, trailing = tvSeeAll) { _, w ->
                LandscapeCard(w, { onOpen(w) }, m(w.uid), onFocus = { onSpot(w.toSpot()) }, onLongClick = { onMenu(w) })
            }
            RowStyle.CONTINUE -> CardRow(row.works, { it.uid }) { _, w ->
                LandscapeCard(
                    w, { w.resume?.let { r -> nav.navigateOnce(com.sokoul.iptv.ui.screens.home.resumeRoute(pid, r)) } ?: onOpen(w) },
                    m(w.uid),
                    onFocus = { onSpot(w.toSpot().copy(meta = w.subtitle)) }, onLongClick = { onMenu(w) },
                )
            }
            RowStyle.TOP10 -> CardRow(row.works, { it.uid }, trailing = tvSeeAll) { i, w ->
                Top10Card(
                    i + 1, w, { onOpen(w) }, m(w.uid),
                    onFocus = { onSpot(w.toSpot().copy(meta = "N° ${i + 1} cette semaine · " + w.metaLine())) }, onLongClick = { onMenu(w) },
                )
            }
            RowStyle.SAGAS -> CardRow(row.sagas, { it.id }) { _, s ->
                Box(m(s.id)) { SagaTile(
                    s, { nav.navigateOnce(BrowseGridRoute(s.name, GridSpec.Saga(s.id).encode())) },
                    onFocus = { onSpot(Spot(s.name, meta = "Saga · ${s.count} films disponibles", image = s.backdrop)) },
                ) }
            }
            RowStyle.PLATFORMS -> CardRow(row.platforms, { it.platform.name }) { _, t ->
                Box(m(t.platform.name)) { PlatformTileView(
                    t, { nav.navigateOnce(BrowseGridRoute(t.platform.label, GridSpec.Plat(t.type, t.platform).encode())) },
                    onFocus = { onSpot(Spot(t.platform.label, meta = "Plateforme", overview = t.platform.tagline, platform = t.platform)) },
                ) }
            }
            RowStyle.CHANNELS -> CardRow(row.channels, { it.channel.id }) { _, c ->
                Box(m(c.channel.id).onFocusChanged { if (it.hasFocus) onSpot(Spot(c.channel.name, meta = "En direct", overview = c.now?.title)) }) {
                    ChannelCard(c.channel, c.now) { nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.channel.id, categoryId = SpecialCategory.FAVORITES)) }
                }
            }
            RowStyle.CATEGORIES -> {
                val type = if (vm.kind == BrowseKind.SERIES) ContentType.SERIES else ContentType.MOVIE
                LazyRow(
                    contentPadding = PaddingValues(horizontal = l.margin, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item("all") {
                        Box(m("all")) { Chip("Toutes les catégories", Icons.AutoMirrored.Filled.List, accent = true, onFocus = { onSpot(Spot("Toutes les catégories", overview = row.tagline)) }) {
                            nav.navigate(AllCategoriesRoute(type.name))
                        } }
                    }
                    items(row.chips, key = { it.first }) { (id, label) ->
                        Box(m(id)) { Chip(label, onFocus = { onSpot(Spot(label, meta = "Catégorie")) }) {
                            nav.navigateOnce(BrowseGridRoute(label, GridSpec.Cats(type, listOf(id), byPosition = true).encode()))
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, accent: Boolean = false, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    SokoulChip(text, onClick, icon = icon, selected = accent, onFocus = onFocus)
}

/** Panneau TV : fond de l'œuvre à droite, logo/titre, méta et résumé à gauche. */
/**
 * Fond d'ambiance (TV) : l'œuvre focalisée, très atténuée, derrière les rangées.
 * Composable à part : le changement d'œuvre et le fondu ne recomposent pas tout l'écran.
 */
@Composable
private fun AmbientBackdrop(spot: State<Spot?>, inRows: Boolean, spotHeight: androidx.compose.ui.unit.Dp) {
    // image et teinte de l'œuvre publiées ensemble, une fois le curseur posé (pas de fondu de couleur :
    // chaque image intermédiaire redessinerait le fond plein écran — mesuré : +12 points d'images saccadées)
    val amb = rememberAmbient(spot.value?.image, { tmdbSized(it, "w780") ?: it }, SokoulColors.Background)
    val image = amb.value.first
    val ambient = animateFloatAsState(if (inRows && image != null) 1f else 0f, tween(Motion.SLOW), label = "ambient")
    if (ambient.value <= 0f) return
    // une seule image (pas de fondu enchaîné entre deux images plein écran : mesuré, c'était ~5 points
    // d'images saccadées sur la box) ; à 22 % d'opacité derrière les rangées, le changement reste doux
    // lueur dans la même texture en cache que l'image : un seul calque plein écran à composer,
    // recalculé seulement quand l'œuvre (ou sa teinte) change
    Box(Modifier.fillMaxSize().padding(top = spotHeight - 1.dp).graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen; alpha = ambient.value }) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                val glow = amb.value.second
                drawRect(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.25f, size.height * 0.3f), radius = size.width * 0.75f))
            }
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f }) {
            NetImage(tmdbSized(image, "w780"), null, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to SokoulColors.Background, 0.45f to Color.Transparent)))
    }
}

/** Panneau d'aperçu (TV) : lit l'œuvre et l'opacité ici, pas dans l'écran parent. */
@Composable
private fun SpotLayer(spot: State<Spot?>, shown: State<Float>, height: androidx.compose.ui.unit.Dp) {
    if (shown.value > 0f) SpotPanel(spot.value, height, Modifier.graphicsLayer { alpha = shown.value })
}

@Composable
private fun SpotPanel(spot: Spot?, height: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val l = LocalLayout.current
    Box(modifier.fillMaxWidth().height(height).background(SokoulColors.Background)) {
        AnimatedContent(spot, transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.MEDIUM)) }, label = "spot") { s ->
            Box(Modifier.fillMaxSize()) {
                // image + dégradés dans une texture en cache : le fondu enchaîné ne redessine rien, il change l'opacité
                Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
                if (s?.image != null) {
                    NetImage(
                        tmdbSized(s.image, "w1280"), null, Modifier.fillMaxWidth(0.68f).height(height).align(Alignment.TopEnd),
                        alignment = Alignment.TopCenter,
                    )
                } else if (s?.platform != null) {
                    Box(Modifier.fillMaxWidth(0.68f).height(height).align(Alignment.TopEnd).background(Brush.linearGradient(listOf(Color(s.platform.color), Color(s.platform.accent)))))
                }
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.30f to SokoulColors.Background, 0.55f to SokoulColors.bg(0.7f), 0.80f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.65f to Color.Transparent, 1f to SokoulColors.Background)))
                }
                if (s != null) {
                    Column(Modifier.align(Alignment.BottomStart).padding(start = l.margin, bottom = 18.dp).widthIn(max = l.heroTextWidth)) {
                        if (s.platform != null && s.platform.label != s.title) PlatformBadge(s.platform, Modifier.padding(bottom = 8.dp), large = true)
                        if (s.logo != null) {
                            NetImage(tmdbSized(s.logo, "w500"), s.title, Modifier.heightIn(max = 70.dp).widthIn(max = 300.dp), ContentScale.Fit, alignment = Alignment.BottomStart) {
                                SpotTitle(s.title)
                            }
                        } else SpotTitle(s.title)
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (s.rating != null && s.rating >= com.sokoul.iptv.ui.components.MIN_RATING) RatingChip(s.rating)
                            if (s.meta != null) Text(s.meta, style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextSecondary, maxLines = 1)
                        }
                        if (s.details != null) SpotDetails(s.details)
                        val text = s.overview ?: s.tagline
                        if (text != null) {
                            Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotTitle(t: String) {
    Text(t, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Barre du haut (téléphone/tablette) : transparente sur le héros, opaque en défilant. */
@Composable
private fun TopBar(title: String, firstIndex: Int, offset: Int, heroPx: Float, kind: BrowseKind, nav: NavHostController) {
    val l = LocalLayout.current
    // opaque dès qu'on a quitté le haut du héros : rien ne doit apparaître en transparence derrière le titre
    val solid = if (firstIndex > 0) 1f else (offset / (heroPx * 0.4f)).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth()
            .background(SokoulColors.Background.copy(alpha = solid))
            .background(Brush.verticalGradient(listOf(SokoulColors.black(0.6f).copy(alpha = 0.6f * (1f - solid)), Color.Transparent)))
            .statusBarsPadding().padding(horizontal = l.margin, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // le rail latéral (tablette) porte déjà le logo
        if (l.isCompact) SokoulMark(Modifier.padding(end = 12.dp), size = 30.dp)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (kind != BrowseKind.HOME) {
            val type = if (kind == BrowseKind.SERIES) ContentType.SERIES else ContentType.MOVIE
            TopIcon(Icons.AutoMirrored.Filled.List, "Catégories") { nav.navigate(AllCategoriesRoute(type.name)) }
        }
        TopIcon(Icons.Default.Search, "Recherche") {
            nav.navigate(SearchRoute(when (kind) { BrowseKind.MOVIES -> SearchOrigin.MOVIES; BrowseKind.SERIES -> SearchOrigin.SERIES; else -> SearchOrigin.HOME }))
        }
    }
}

@Composable
private fun TopIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(Radius.Pill).dpadClickable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(24.dp), tint = Color.White)
    }
}

@Composable
private fun ExpiryBanner(expiresAt: Long) {
    val now = rememberNow()
    val l = LocalLayout.current
    if (expiresAt - now > 7 * 24 * 3600_000L) return
    Row(
        Modifier.padding(horizontal = l.margin, vertical = 6.dp).background(SokoulColors.Warning.copy(alpha = 0.14f), Radius.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = SokoulColors.Warning)
        Text(
            if (expiresAt < now) "Votre abonnement a expiré." else "Votre abonnement expire dans ${((expiresAt - now) / (24 * 3600_000L)).coerceAtLeast(0)} jour(s).",
            Modifier.padding(start = 10.dp), color = SokoulColors.Warning, style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Ligne de détails : apparaît en fondu une fois le curseur posé, pour ne rien charger en traversant. */
@Composable
private fun SpotDetails(text: String) {
    var shown by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) { delay(SPOT_DETAILS_MS); shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(Motion.MEDIUM), label = "details")
    Text(
        text, Modifier.padding(top = 6.dp).graphicsLayer { this.alpha = alpha },
        style = com.sokoul.iptv.ui.theme.Type.Meta, color = SokoulColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}
