package com.sokoul.desktop.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.Catalog
import com.sokoul.iptv.data.repo.Platform
import com.sokoul.iptv.data.repo.SagaCard
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.PlatformBadge
import com.sokoul.iptv.ui.components.SagaTile
import com.sokoul.iptv.ui.components.TouchTopBar
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Grille d'affiches de la tablette (« Tout voir », Ma liste, saga, plateforme) : en-tête puis cartes. */
@Composable
private fun WorkGrid(app: AppState, works: List<Work>?, header: @Composable () -> Unit, empty: (@Composable () -> Unit)? = null, back: Boolean = false) {
    val l = LocalLayout.current
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(l.gridCell), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = l.margin, end = l.margin, top = if (back) 72.dp else 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(l.gutter), verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
            when {
                works == null -> items(18) { Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).shimmer()) }
                works.isEmpty() && empty != null -> item(span = { GridItemSpan(maxLineSpan) }) { empty() }
                else -> items(works, key = { it.uid }) { w -> WorkCard(w, { open(app, w) }, width = Dp.Unspecified) }
            }
        }
        if (back) TouchTopBar(onBack = { app.back() }, scrimProvider = { 1f })
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, style = Type.ScreenTitle)
        subtitle?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary) }
    }
}

/** Ma liste : films et séries ajoutés depuis les fiches (bouton « Ma liste »). */
@Composable
fun TabletMyList(app: AppState) {
    val works by produceState<List<Work>?>(null, app.dataVersion) {
        val ids = withContext(Dispatchers.IO) { app.myList() }
        value = coroutineScope { ids.map { (k, id) -> async { app.catalog.details(k, id)?.toWork(k) } }.mapNotNull { it.await() } }
    }
    WorkGrid(app, works, { PageTitle("Ma liste", works?.let { "${it.size} titre" + if (it.size > 1) "s" else "" }) }, empty = {
        EmptyState(Icons.Default.BookmarkBorder, "Votre liste est vide", "Ajoutez un film ou une série avec le bouton « Ma liste » de sa fiche.")
    })
}

/** Sagas : toutes les collections, en grandes tuiles. */
@Composable
fun TabletSagas(app: AppState, data: TabletData) {
    val l = LocalLayout.current
    val sagas by produceState<List<SagaCard>?>(null) { value = data.page(com.sokoul.iptv.data.repo.BrowseKind.MOVIES).rows.firstOrNull { it.id == "sagas" }?.sagas.orEmpty() }
    LazyVerticalGrid(
        GridCells.Adaptive(l.landscape), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = l.margin, end = l.margin, top = 24.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(l.gutter), verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { PageTitle("Sagas", "Une histoire, plusieurs films : à voir dans l'ordre.") }
        val s = sagas
        if (s == null) items(9) { Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).shimmer()) }
        else if (s.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Default.CollectionsBookmark, "Aucune saga", "TMDB ne répond pas pour le moment.") }
        else items(s, key = { it.id }) { saga -> SagaTile(saga, { app.openSaga(saga.id) }, width = Dp.Unspecified) }
    }
}

/** Page d'une saga : fond, nom, résumé, puis les films dans l'ordre de sortie. */
@Composable
fun TabletSaga(app: AppState, id: Int) {
    val c by produceState<com.sokoul.iptv.data.net.TmdbCollection?>(null, id) { value = app.catalog.tmdb.collection(app.catalog.key(), id) }
    val works = c?.parts?.map { it.toWork("movie", Catalog::genreName) }
    WorkGrid(app, works, {
        val col = c
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            if (col?.backdrop != null) NetImage(col.backdrop, null, Modifier.fillMaxSize())
            // voile du bas : le texte reste lisible sur le fond de la saga
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.2f to androidx.compose.ui.graphics.Color.Transparent, 1f to SokoulColors.Background)))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("SAGA", style = Type.SagaKicker)
                Text(col?.name?.let { com.sokoul.iptv.ui.components.sagaLabel(it) } ?: "", style = Type.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                col?.overview?.let { Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }, back = true)
}

/** Page d'une plateforme : ses films ou séries les plus populaires en France. */
@Composable
fun TabletPlatform(app: AppState, p: Platform, kind: String) {
    val provider = when (p) { Platform.NETFLIX -> 8; Platform.PRIME -> 119; Platform.DISNEY -> 337; Platform.APPLE -> 350; Platform.MAX -> 1899; Platform.CANAL -> 381; Platform.PARAMOUNT -> 531; else -> 0 }
    val works by produceState<List<Work>?>(null, p, kind) {
        value = coroutineScope {
            (1..3).map { page -> async { app.catalog.tmdb.discover(app.catalog.key(), kind, mapOf("with_watch_providers" to "$provider", "watch_region" to "FR", "sort_by" to "popularity.desc"), page) } }
                .flatMap { it.await() }.filter { it.poster != null }.distinctBy { it.id }.map { it.toWork(kind, Catalog::genreName) }
        }
    }
    WorkGrid(app, works, {
        Column(Modifier.padding(bottom = 8.dp)) {
            PlatformBadge(p, Modifier.padding(bottom = 10.dp), large = true)
            PageTitle(if (kind == "tv") "Séries ${p.label}" else "Films ${p.label}", p.tagline)
        }
    }, back = true)
}
