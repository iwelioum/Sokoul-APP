package com.sokoul.iptv.ui.screens.search

import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.nav.navigateOnce
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.repo.BrowseKind
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.RowStyle
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.FormField
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.browse.openWork
import com.sokoul.iptv.ui.screens.home.ChannelCard
import com.sokoul.iptv.ui.theme.LocalLayout
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Résultats prêts à afficher : œuvres regroupées (une carte par œuvre) et enrichies par TMDB. */
data class SearchUi(
    val channels: List<ChannelEntity>,
    val programs: List<Pair<ProgramEntity, ChannelEntity?>>,
    val movies: List<Work>,
    val series: List<Work>,
) {
    val isEmpty get() = channels.isEmpty() && programs.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/** Suggestions affichées quand le champ est vide : tendances de la semaine présentes dans l'abonnement. */
data class Suggestion(val title: String, val tagline: String?, val works: List<Work>)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val vod: VodRepository,
    private val browse: BrowseRepository,
    private val enrich: EnrichmentRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    var playlistId: Long = 0
        private set
    val suggestions = MutableStateFlow<List<Suggestion>>(emptyList())

    init {
        viewModelScope.launch {
            val p = playlists.activeNow() ?: return@launch
            playlistId = p.id
            val page = runCatching { browse.page(p, BrowseKind.HOME) }.getOrNull() ?: return@launch
            suggestions.value = page.rows.filter { it.style == RowStyle.TOP10 && it.works.isNotEmpty() }
                .map { Suggestion(it.title, it.tagline, enrich.decorate(p.id, it.works)) }
        }
    }

    val results: StateFlow<SearchUi?> = query.debounce(300).distinctUntilChanged().mapLatest { q ->
        val p = playlists.activeNow() ?: return@mapLatest null
        playlistId = p.id
        if (q.trim().length < 2) return@mapLatest null
        val r = catalog.search(p.id, q)
        val movies = vod.groupMovies(p.id, r.movies).map { it.item.toWork(it.versions) }
        val series = vod.groupSeries(p.id, r.series).map { it.item.toWork(it.versions) }
        enrich.request(p.id, (movies + series).take(30))
        SearchUi(r.channels, r.programs, enrich.decorate(p.id, movies), enrich.decorate(p.id, series))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun SearchScreen(nav: NavHostController) {
    val vm: SearchViewModel = hiltViewModel()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        FormField(
            "Rechercher une chaîne, un programme, un film, une série…", query, { vm.query.value = it },
            Modifier.padding(horizontal = l.margin, vertical = if (l.isCompact) 12.dp else 24.dp).focusRequester(focus), imeAction = ImeAction.Search,
        )
        val r = results
        val pid = vm.playlistId
        when {
            r == null && suggestions.isEmpty() ->
                EmptyState(Icons.Default.Search, "Recherche", "Tapez au moins 2 lettres. La recherche porte sur toute la playlist active, y compris le guide TV.")
            r == null -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Tapez au moins 2 lettres : chaînes, programmes du guide, films et séries.",
                        Modifier.padding(horizontal = l.margin).padding(bottom = 18.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                suggestions.forEach { s ->
                    item(s.title) {
                        Column(Modifier.padding(bottom = l.rowSpacing)) {
                            RowHeader(s.title, s.tagline)
                            CardRow(s.works, { it.uid }) { _, w -> WorkCard(w, { nav.openWork(w) }) }
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
            r.isEmpty -> EmptyState(Icons.Default.Search, "Aucun résultat pour « $query »", "Vérifiez l'orthographe ou essayez le titre original.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (r.movies.isNotEmpty()) item("movies") {
                    Column(Modifier.padding(bottom = l.rowSpacing)) {
                        RowHeader("Films", "${r.movies.size} résultat${if (r.movies.size > 1) "s" else ""}")
                        CardRow(r.movies, { it.uid }) { _, w -> WorkCard(w, { nav.openWork(w) }) }
                    }
                }
                if (r.series.isNotEmpty()) item("series") {
                    Column(Modifier.padding(bottom = l.rowSpacing)) {
                        RowHeader("Séries", "${r.series.size} résultat${if (r.series.size > 1) "s" else ""}")
                        CardRow(r.series, { it.uid }) { _, w -> WorkCard(w, { nav.openWork(w) }) }
                    }
                }
                if (r.channels.isNotEmpty()) item("channels") {
                    Column(Modifier.padding(bottom = l.rowSpacing)) {
                        RowHeader("Chaînes", null)
                        CardRow(r.channels, { it.id }) { _, c ->
                            ChannelCard(c, null) { nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId)) }
                        }
                    }
                }
                if (r.programs.isNotEmpty()) item("programs") {
                    Column(Modifier.padding(bottom = l.rowSpacing)) {
                        RowHeader("Au programme", "Dans le guide TV des prochains jours")
                        CardRow(r.programs, { "${it.first.channelKey}|${it.first.start}" }) { _, (p, c) ->
                            FocusSurface(onClick = {
                                if (c != null) nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId))
                            }, modifier = Modifier.width(l.landscape)) { _ ->
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${dayLabel(p.start)} · ${formatRange(p.start, p.end)}", color = SokoulColors.Accent, style = MaterialTheme.typography.labelMedium)
                                    Text(c?.name ?: "", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
