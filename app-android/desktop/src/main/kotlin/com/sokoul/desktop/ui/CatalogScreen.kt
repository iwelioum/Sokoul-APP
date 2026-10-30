package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.Movie
import com.sokoul.desktop.data.Series
import kotlinx.coroutines.launch

@Composable
fun CatalogScreen(app: AppState, title: String) {
    var query by remember(title) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val playlist = app.playlist
    val movies = remember(app.dataVersion, query, playlist?.id) {
        playlist?.let { app.db.movies(it.id, query) }.orEmpty()
    }
    val series = remember(app.dataVersion, query, playlist?.id) {
        playlist?.let { app.db.series(it.id, query) }.orEmpty()
    }
    val isSeries = title == "Séries"
    val isSearch = title == "Recherche"
    val visibleMovies = if (isSeries) emptyList() else movies
    val visibleSeries = if (isSeries || isSearch) series else emptyList()

    Column(
        Modifier.fillMaxSize().background(SokoulColors.Background).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                ScreenTitle(title, "Catalogue IPTV synchronisé, enrichi quand les fournisseurs sont disponibles")
            }
            SokoulButton(
                "Synchroniser",
                { playlist?.let { p -> scope.launch { app.sync.sync(p); app.reloadPlaylist() } } },
                primary = false,
                icon = Icons.Default.Refresh,
                enabled = playlist != null,
            )
        }
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (isSearch) "Rechercher films et séries" else "Filtrer $title") },
        )
        if (playlist == null) {
            Text("Ajoutez ou importez une playlist depuis Réglages pour charger le catalogue.", color = SokoulColors.TextSecondary)
        } else if (visibleMovies.isEmpty() && visibleSeries.isEmpty()) {
            Text(
                if (playlist.type.name == "M3U") "Cette playlist ne contient pas encore de catalogue VOD exploitable."
                else "Aucun élément trouvé. Lancez une synchronisation ou vérifiez les catégories du serveur.",
                color = SokoulColors.TextSecondary,
            )
        } else {
            if (visibleMovies.isNotEmpty()) {
                Text("Films · ${visibleMovies.size}", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(visibleMovies, key = { it.id }) { movie -> MovieCard(movie) { app.playMovie(movie) } }
                }
            }
            if (visibleSeries.isNotEmpty()) {
                Text("Séries · ${visibleSeries.size}", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(visibleSeries, key = { it.id }) { item -> SeriesCard(item) {} }
                }
            }
        }
    }
}

@Composable
private fun MovieCard(movie: Movie, onClick: () -> Unit) {
    PosterCard(movie.name, movie.year?.toString() ?: "Film IPTV", movie.poster, onClick)
}

@Composable
private fun SeriesCard(item: Series, onClick: () -> Unit) {
    PosterCard(item.name, item.year?.toString() ?: "Série IPTV", item.poster, onClick)
}
