package com.sokoul.desktop.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.Screen
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.BrowseKind
import com.sokoul.iptv.data.repo.BrowseRow
import com.sokoul.iptv.data.repo.RowKicker
import com.sokoul.iptv.data.repo.RowStyle
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.ui.components.ButtonKind
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.HeroCarousel
import com.sokoul.iptv.ui.components.LandscapeCard
import com.sokoul.iptv.ui.components.PlatformTileView
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.SagaTile
import com.sokoul.iptv.ui.components.SkeletonRow
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.components.SokoulMark
import com.sokoul.iptv.ui.components.Top10Card
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.TabletRailWidth
import kotlinx.coroutines.launch

/** Entrées du rail de la tablette (`Shell.kt`, `Dest`), dans le même ordre ; Enregistrements absents du PC. */
enum class Dest(val label: String, val icon: ImageVector, val screen: Screen, val short: String = label) {
    SEARCH("Recherche", Icons.Default.Search, Screen.SEARCH),
    HOME("Accueil", Icons.Default.Home, Screen.HOME),
    MOVIES("Films", Icons.Default.Movie, Screen.MOVIES),
    SERIES("Séries", Icons.Default.VideoLibrary, Screen.SERIES),
    MY_LIST("Ma liste", Icons.Default.BookmarkBorder, Screen.MY_LIST),
    SAGAS("Sagas", Icons.Default.CollectionsBookmark, Screen.SAGAS),
    LIVE("Direct", Icons.Default.LiveTv, Screen.LIVE),
    GUIDE("Guide TV", Icons.Default.CalendarViewWeek, Screen.GUIDE),
    SETTINGS("Réglages", Icons.Default.Settings, Screen.SETTINGS),
}

/** Rail fixe de la tablette : logo, entrées avec icône dans une pastille d'accent, Réglages en bas. */
@Composable
fun TabletRail(current: Screen, onSelect: (Screen) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(TabletRailWidth.dp).background(SokoulColors.Surface).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SokoulMark(Modifier.padding(bottom = 10.dp), size = 36.dp)
        Dest.entries.forEach { d ->
            val selected = d.screen == current
            if (d == Dest.SETTINGS) Spacer(Modifier.weight(1f))
            FocusSurface(style = FocusStyle.Row, onClick = { onSelect(d.screen) }, modifier = Modifier.size(76.dp, 58.dp), color = Color.Transparent, shape = Radius.Card) { focused ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(52.dp, 30.dp).background(if (selected) SokoulColors.AccentSoft else Color.Transparent, Radius.Card),
                        contentAlignment = Alignment.Center,
                    ) { Icon(d.icon, d.label, Modifier.size(22.dp), tint = if (selected || focused) SokoulColors.Accent else SokoulColors.TextSecondary) }
                    Text(
                        d.short, style = com.sokoul.iptv.ui.theme.Type.NavLabel, maxLines = 1, textAlign = TextAlign.Center,
                        color = if (selected || focused) SokoulColors.TextPrimary else SokoulColors.TextMuted,
                    )
                }
            }
        }
    }
}

/** Pages Accueil, Films et Séries de la tablette : héros animé, puis rangées (Reprendre, Top 10, plateformes, sagas…). */
@Composable
fun TabletBrowse(app: AppState, kind: BrowseKind, data: TabletData) {
    val l = LocalLayout.current
    var page by remember(kind) { mutableStateOf(data.cached(kind)) }
    var failed by remember(kind) { mutableStateOf(false) }
    LaunchedEffect(kind, app.dataVersion) {
        if (!app.catalog.hasKey()) return@LaunchedEffect
        failed = false
        runCatching { data.page(kind) }.onSuccess { page = it }.onFailure { failed = page == null }
    }
    if (!app.catalog.hasKey()) {
        EmptyState(Icons.Default.Settings, "Clé TMDB absente", "Renseignez la clé TMDB dans Réglages › Sources de données pour afficher le catalogue.") {
            SokoulButton("Réglages", { app.go(Screen.SETTINGS) }, kind = ButtonKind.Primary)
        }
        return
    }
    if (failed) {
        EmptyState(Icons.Default.Refresh, "Impossible d'afficher cette page", "Vérifiez la connexion internet du PC, puis réessayez.") {
            SokoulButton("Réessayer", { app.dataVersion++ }, kind = ButtonKind.Primary, icon = Icons.Default.Refresh)
        }
        return
    }
    val listState = remember(kind) { LazyListState() }
    // essais (SOKOUL_ROUTE home:<n>) : page défilée jusqu'à la rangée n
    LaunchedEffect(page != null) { if (page != null) com.sokoul.desktop.TrialRoute.scrollItem?.let { kotlinx.coroutines.delay(3_000); listState.scrollToItem(it) } }
    val density = LocalDensity.current
    val p = page
    val title = when (kind) { BrowseKind.HOME -> "Accueil"; BrowseKind.MOVIES -> "Films"; BrowseKind.SERIES -> "Séries" }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
            item("hero") {
                if (p == null) Box(Modifier.fillMaxWidth().height(l.heroHeight).shimmer(Radius.None))
                else if (p.hero.isNotEmpty()) HeroCarousel(
                    works = p.hero, onPlay = { w -> play(app, w) }, onInfo = { w -> open(app, w) },
                    active = listState.firstVisibleItemIndex == 0,
                    label = { i, w -> if (p.ranked && i < 10) "N° ${i + 1} des ${if (w.type == ContentType.MOVIE) "films" else "séries"} cette semaine" else null },
                )
            }
            if (p == null) items(3) { SkeletonRow(landscape = it == 1) }
            else p.rows.forEach { row -> item(row.id) { RowView(app, row) } }
        }
        TopBar(title, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, l.heroHeight.value * density.density) { app.go(Screen.SEARCH) }
    }
}

fun open(app: AppState, w: Work) { app.openDetail(if (w.type == ContentType.SERIES) "tv" else "movie", w.tmdbId ?: w.id.toIntOrNull() ?: return) }

fun play(app: AppState, w: Work) {
    val id = w.tmdbId ?: w.id.toIntOrNull() ?: return
    // une série s'ouvre sur sa fiche (choix de l'épisode) ; un film se lance directement
    if (w.type == ContentType.SERIES && w.resume == null) { open(app, w); return }
    val r = w.resume
    app.playTitle(NowPlaying.Title(if (w.type == ContentType.SERIES) "tv" else "movie", id, w.title, r?.subtitle ?: w.year?.toString(), w.backdrop, r?.season, r?.episode))
}

/** Rangée de la tablette : en-tête (kicker, titre, filet, « Tout voir »), cartes selon le style ; molette horizontale. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RowView(app: AppState, row: BrowseRow) {
    val l = LocalLayout.current
    Column(Modifier.padding(top = l.rowSpacing / 2, bottom = l.rowSpacing / 2)) {
        if (row.style != RowStyle.PLATFORMS) RowHeader(row.title, row.tagline, row.platform, null, kicker = RowKicker.of(row.id, row.title), emphasis = row.emphasis)
        else RowHeader(row.title, null, kicker = "PLATEFORMES")
        run {
            when (row.style) {
                RowStyle.POSTER -> CardRow(row.works, { it.uid }) { _, w -> WorkCard(w, { open(app, w) }, showPlatform = row.platform == null, reveal = true) }
                RowStyle.LANDSCAPE -> CardRow(row.works, { it.uid }) { _, w -> LandscapeCard(w.copy(logo = w.logo), { open(app, w) }) }
                RowStyle.CONTINUE -> CardRow(row.works, { it.uid }) { _, w -> LandscapeCard(w, { play(app, w) }) }
                RowStyle.TOP10 -> CardRow(row.works, { it.uid }) { i, w -> Top10Card(i + 1, w, { open(app, w) }) }
                RowStyle.SAGAS -> CardRow(row.sagas, { it.id }) { _, s -> SagaTile(s, { app.openSaga(s.id) }) }
                RowStyle.PLATFORMS -> CardRow(row.platforms, { it.platform.name }) { _, t ->
                    PlatformTileView(t, { app.openPlatform(t.platform, if (t.type == ContentType.SERIES) "tv" else "movie") })
                }
                else -> Unit
            }
        }
    }
}

/** Barre du haut de la tablette : transparente sur le héros, opaque en défilant ; titre de la page et recherche. */
@Composable
private fun TopBar(title: String, firstIndex: Int, offset: Int, heroPx: Float, onSearch: () -> Unit) {
    val l = LocalLayout.current
    val solid = if (firstIndex > 0) 1f else (offset / (heroPx * 0.4f)).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth()
            .background(SokoulColors.Background.copy(alpha = solid))
            .background(Brush.verticalGradient(listOf(SokoulColors.black(0.6f).copy(alpha = 0.6f * (1f - solid)), Color.Transparent)))
            .padding(horizontal = l.margin, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        FocusSurface(style = FocusStyle.Chip, onClick = onSearch, modifier = Modifier.size(44.dp), color = Color.Transparent, shape = Radius.Pill) { focused ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Search, "Recherche", Modifier.size(24.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.OnVideo)
            }
        }
    }
}
