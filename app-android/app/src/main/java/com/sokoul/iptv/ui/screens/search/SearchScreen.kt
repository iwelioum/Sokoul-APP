package com.sokoul.iptv.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SearchResults
import com.sokoul.iptv.ui.components.ContentRow
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.FormField
import com.sokoul.iptv.ui.components.PosterCard
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.screens.home.ChannelCard
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    var playlistId: Long = 0
        private set

    val results: StateFlow<SearchResults?> = query.debounce(300).distinctUntilChanged().mapLatest { q ->
        val p = playlists.activeNow() ?: return@mapLatest null
        playlistId = p.id
        if (q.trim().length < 2) null else catalog.search(p.id, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun SearchScreen(nav: NavHostController) {
    val vm: SearchViewModel = hiltViewModel()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        FormField(
            "Rechercher une chaîne, un programme, un film, une série…", query, { vm.query.value = it },
            Modifier.padding(24.dp).focusRequester(focus), imeAction = ImeAction.Search,
        )
        val r = results
        val pid = vm.playlistId
        when {
            r == null -> EmptyState(Icons.Default.Search, "Recherche", "Tapez au moins 2 lettres. La recherche porte sur toute la playlist active, y compris le guide TV.")
            r.isEmpty -> EmptyState(Icons.Default.Search, "Aucun résultat pour « $query »")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    ContentRow("Chaînes", r.channels, key = { it.id }) { _, c ->
                        ChannelCard(c, null) { nav.navigate(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId)) }
                    }
                }
                item {
                    ContentRow("Au programme", r.programs, key = { "${it.first.channelKey}|${it.first.start}" }) { _, (p, c) ->
                        FocusSurface(onClick = {
                            if (c != null) nav.navigate(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId))
                        }, modifier = Modifier.width(260.dp)) { _ ->
                            Column(Modifier.padding(14.dp)) {
                                Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${dayLabel(p.start)} · ${formatRange(p.start, p.end)}", color = SokoulColors.Accent, style = MaterialTheme.typography.labelMedium)
                                Text(c?.name ?: "", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
                item {
                    ContentRow("Films", r.movies, key = { it.id }) { _, m ->
                        PosterCard(m.title, m.poster, { nav.navigate(MovieDetailRoute(m.id)) }, badge = m.quality, subtitle = m.year?.toString())
                    }
                }
                item {
                    ContentRow("Séries", r.series, key = { it.id }) { _, s ->
                        PosterCard(s.title, s.poster, { nav.navigate(SeriesDetailRoute(s.id)) }, subtitle = s.year?.toString())
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
