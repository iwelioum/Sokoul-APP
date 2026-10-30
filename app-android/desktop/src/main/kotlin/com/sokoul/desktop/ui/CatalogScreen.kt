package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.BrowseSort
import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.iptv.data.net.TmdbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Films ou Séries : grille de découverte TMDB, genres et tri, page suivante en arrivant en bas. */
@Composable
fun BrowseScreen(app: AppState, kind: String) {
    val title = if (kind == "movie") "Films" else "Séries"
    val genres = if (kind == "movie") Catalog.movieGenres else Catalog.tvGenres
    var genre by rememberSaveable(kind) { mutableStateOf<Int?>(null) }
    var sort by rememberSaveable(kind) { mutableStateOf(BrowseSort.POPULAR) }
    var items by remember(kind, genre, sort) { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var page by remember(kind, genre, sort) { mutableIntStateOf(0) }
    var loading by remember(kind, genre, sort) { mutableStateOf(false) }
    var end by remember(kind, genre, sort) { mutableStateOf(false) }
    val grid = rememberLazyGridState()

    suspend fun more() {
        if (loading || end) return
        loading = true
        val next = app.catalog.browse(kind, genre, sort, page + 1)
        if (next.isEmpty()) end = true
        items = (items + next).distinctBy { it.id }
        page++
        loading = false
    }
    LaunchedEffect(kind, genre, sort) { grid.scrollToItem(0); more() }
    val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= grid.layoutInfo.totalItemsCount - 12 } ?: false } }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty()) more() }
    LaunchedEffect(Unit) { snapshotFlow { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 40 }.collect { app.navSolid = it } }

    LazyVerticalGrid(
        GridCells.Adaptive(176.dp), Modifier.fillMaxSize().background(SokoulColors.Background), state = grid,
        contentPadding = PaddingValues(start = Space.Gutter, end = Space.Gutter, top = 110.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(title, style = Type.ScreenTitle)
                    // tri en onglets texte : seul le genre est une pastille pleine
                    Row(Modifier.weight(1f).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.End)) {
                        BrowseSort.entries.forEach { s -> NavTab(s.label, s == sort) { sort = s } }
                    }
                }
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(top = 20.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip("Tous les genres", genre == null, { genre = null })
                    genres.forEach { g -> Chip(g.name, g.id == genre, { genre = g.id }) }
                }
            }
        }
        items(items, key = { it.id }) { item -> PosterCard(item, onClick = { app.openDetail(kind, item.id) }, width = 400.dp, modifier = Modifier.fillMaxWidth()) }
        if (loading) items(12) { Skeleton(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) }
        if (!loading && items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    if (app.catalog.hasKey()) "Rien à afficher" else "Branchez TMDB",
                    if (app.catalog.hasKey()) "TMDB n'a rien renvoyé pour ce filtre. Essayez un autre genre." else "Renseignez la clé TMDB dans Réglages › Clés API.",
                )
            }
        }
    }
}

/** Recherche : films et séries TMDB et chaînes IPTV, pendant la frappe (300 ms). */
@Composable
fun SearchScreen(app: AppState) {
    var query by rememberSaveable { mutableStateOf(com.sokoul.desktop.TrialRoute.searchQuery.orEmpty().also { com.sokoul.desktop.TrialRoute.searchQuery = null }) }
    var results by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { app.navSolid = true; runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); channels = emptyList(); return@LaunchedEffect }
        delay(300)
        searching = true
        channels = app.playlist?.let { p -> withContext(Dispatchers.IO) { app.db.searchChannels(p.id, LiveLogic.searchKey(q), 30) } }.orEmpty()
        results = app.catalog.search(q)
        searching = false
    }
    LazyVerticalGrid(
        GridCells.Adaptive(176.dp), Modifier.fillMaxSize().background(SokoulColors.Background),
        contentPadding = PaddingValues(start = Space.Gutter, end = Space.Gutter, top = 110.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // la case de grille impose sa largeur : le champ est borné à l'intérieur d'une boîte pleine largeur
            Box(Modifier.fillMaxWidth()) {
                SokoulTextField(
                    query, { query = it }, "Un film, une série, une chaîne…", Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    leading = Icons.Filled.Search, style = Type.SearchQuery, focusRequester = focus,
                )
            }
        }
        if (channels.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SectionTitle("Chaînes")
                LazyRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(channels.size) { i -> ChannelTile(channels[i]) { app.play(channels, i) } }
                }
            }
        }
        if (results.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Films et séries") }
        items(results, key = { "${it.mediaType}:${it.id}" }) { item ->
            PosterCard(item, onClick = { app.openDetail(item.mediaType ?: "movie", item.id) }, width = 400.dp, modifier = Modifier.fillMaxWidth())
        }
        if (searching && results.isEmpty()) items(12) { Skeleton(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) }
        if (!searching && query.trim().length >= 2 && results.isEmpty() && channels.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                EmptyState("Aucun résultat", "Rien ne correspond à « ${query.trim()} ». Vérifiez l'orthographe ou essayez le titre original.")
            }
        }
        if (query.isBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Astuce : appuyez sur / depuis n'importe quel écran pour chercher.", Modifier.padding(top = 8.dp), style = Type.Small)
        }
    }
}

/** Tuile de chaîne (logo sur fond clair-obscur, nom dessous). */
@Composable
fun ChannelTile(c: Channel, onClick: () -> Unit) {
    val h = rememberHover()
    Column(Modifier.widthIn(max = 180.dp).interactive(h, onClick = onClick)) {
        Box(
            Modifier.height(100.dp).aspectRatio(16f / 9f).background(if (h.active) SokoulColors.SurfaceHigher else SokoulColors.SurfaceHigh, Radius.Card)
                .focusRing(h).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            NetImage(c.logo, c.name, Modifier.fillMaxSize(), scale = androidx.compose.ui.layout.ContentScale.Fit) {
                Text(c.name.take(3).uppercase(), Modifier.align(Alignment.Center), style = Type.Section)
            }
        }
        Text(c.name, Modifier.padding(top = 8.dp), style = Type.ItemTitle, maxLines = 1)
    }
}
