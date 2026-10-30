package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.Screen
import com.sokoul.desktop.data.Feature
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.data.Shelf
import com.sokoul.desktop.data.ShelfStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Accueil chargé une fois par session (et à chaque changement de clé) : revenir à l'accueil est immédiat. */
private object HomeCache {
    var version = -1
    var features: List<Feature> = emptyList()
    var shelves: List<Shelf> = emptyList()
    var listState: LazyListState? = null
}

@Composable
fun HomeScreen(app: AppState) {
    val keyVersion = app.dataVersion
    var features by remember { mutableStateOf(HomeCache.features) }
    var shelves by remember { mutableStateOf(HomeCache.shelves) }
    var loading by remember { mutableStateOf(HomeCache.version < 0) }
    val resume by produceState(emptyList<Resume>(), keyVersion) { value = withContext(Dispatchers.IO) { app.db.resumeList() } }
    val list = HomeCache.listState ?: rememberLazyListState().also { HomeCache.listState = it }

    LaunchedEffect(keyVersion) {
        if (HomeCache.version == keyVersion && shelves.isNotEmpty()) return@LaunchedEffect
        if (!app.catalog.hasKey()) { loading = false; return@LaunchedEffect }
        loading = shelves.isEmpty()
        coroutineScope {
            val f = async { app.catalog.features() }
            val s = async { app.catalog.home() }
            features = f.await(); shelves = s.await()
        }
        HomeCache.features = features; HomeCache.shelves = shelves; HomeCache.version = keyVersion
        loading = false
    }
    val solid by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 420 } }
    LaunchedEffect(Unit) { snapshotFlow { solid }.collect { app.navSolid = it } }

    BoxWithConstraints(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        val heroHeight = maxOf(480.dp, maxHeight * 0.66f)
        if (!app.catalog.hasKey()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    "Branchez TMDB", "L'accueil, les films et les séries viennent de TMDB. Renseignez la clé dans Réglages › Clés API.",
                    action = "Ouvrir les réglages", onAction = { app.go(Screen.SETTINGS) },
                )
            }
            return@BoxWithConstraints
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(44.dp)) {
            item(key = "hero") {
                if (features.isEmpty()) Skeleton(Modifier.fillMaxWidth().height(heroHeight), Radius.Card)
                else HomeHero(
                    features, heroHeight,
                    onPlay = { f -> playFeature(app, f) },
                    onInfo = { f -> app.openDetail(f.item.mediaType ?: "movie", f.item.id) },
                )
            }
            if (resume.isNotEmpty()) item(key = "resume") {
                // la rangée remonte sur le bas du héros, comme sur la tablette
                Box(Modifier.offset(y = (-70).dp)) { ResumeRow(app, resume) }
            }
            if (app.playlist == null) item(key = "iptv") { IptvBanner(app) }
            if (loading) items(3) { SkeletonRow(landscape = it == 1) }
            items(shelves, key = { it.id }) { shelf -> ShelfRow(app, shelf) }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

private fun playFeature(app: AppState, f: Feature) {
    val kind = f.item.mediaType ?: "movie"
    if (kind == "tv") { app.openDetail(kind, f.item.id); return }
    app.playTitle(NowPlaying.Title(kind, f.item.id, f.info?.title ?: f.item.title, f.item.year?.toString(), f.info?.backdrop ?: f.item.backdrop))
}

@Composable
private fun ResumeRow(app: AppState, resume: List<Resume>) {
    CardRow("Reprendre") {
        items(resume, key = { it.key }) { r ->
            LandscapeCard(
                r.title, r.image,
                onClick = {
                    app.playTitle(NowPlaying.Title(r.kind, r.tmdbId, r.title, r.subtitle, r.image, r.season, r.episode))
                },
                subtitle = listOfNotNull(r.subtitle, "${timecode(r.durationMs - r.positionMs)} restantes").joinToString(" · "),
                progress = r.fraction,
                badge = if (r.season != null) "S${r.season} · É${r.episode}" else null,
            )
        }
    }
}

@Composable
private fun ShelfRow(app: AppState, shelf: Shelf) {
    when (shelf.style) {
        ShelfStyle.TOP10 -> CardRow(shelf.title, kicker = "Films et séries", spacing = 6.dp) {
            itemsIndexed(shelf.items, key = { _, it -> "${it.mediaType}:${it.id}" }) { i, item ->
                Top10Card(i + 1, item) { app.openDetail(item.mediaType ?: "movie", item.id) }
            }
        }
        ShelfStyle.LANDSCAPE -> CardRow(shelf.title) {
            items(shelf.items, key = { "${it.mediaType}:${it.id}" }) { item ->
                LandscapeCard(
                    item.title, item.backdrop ?: item.poster, onClick = { app.openDetail(item.mediaType ?: "tv", item.id) },
                    subtitle = listOfNotNull(item.year?.toString(), item.genreIds.firstNotNullOfOrNull(com.sokoul.desktop.data.Catalog::genreName)).joinToString(" · "),
                )
            }
        }
        ShelfStyle.POSTER -> CardRow(shelf.title) {
            items(shelf.items, key = { "${it.mediaType}:${it.id}" }) { item ->
                PosterCard(item, onClick = { app.openDetail(item.mediaType ?: "movie", item.id) })
            }
        }
    }
}

@Composable
private fun IptvBanner(app: AppState) {
    Row(
        Modifier.padding(horizontal = Space.Gutter).fillMaxWidth().background(SokoulColors.AccentTint, Radius.Panel)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, tint = SokoulColors.Accent)
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Ajoutez votre abonnement IPTV", style = Type.ItemTitle)
            Text("Chaînes en direct, guide TV et catalogue de votre fournisseur, à côté des films Real-Debrid.", style = Type.Small)
        }
        SokoulButton("Ajouter", { app.go(Screen.SETTINGS) }, style = ButtonStyle.Glass)
    }
}
