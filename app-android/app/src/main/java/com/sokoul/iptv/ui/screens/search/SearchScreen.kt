package com.sokoul.iptv.ui.screens.search

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import com.sokoul.iptv.data.repo.SearchKind
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.FormField
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.SokoulChip
import com.sokoul.iptv.ui.components.TvKeyboard
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.screens.browse.openWork
import com.sokoul.iptv.ui.screens.home.ChannelCard
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
data class Suggestion(val title: String, val tagline: String?, val works: List<Work>, val series: Boolean)

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
    /** Périmètre : fixé une fois par la page d'origine, puis modifiable par les pastilles. */
    val scope = MutableStateFlow<Set<SearchKind>?>(null)
    var playlistId: Long = 0
        private set
    val trending = MutableStateFlow<List<Suggestion>>(emptyList())

    fun start(initial: Set<SearchKind>) {
        if (scope.value == null) scope.value = initial
    }

    /** Une pastille de périmètre ; on garde toujours au moins un type coché. */
    fun toggle(k: SearchKind) {
        val s = scope.value ?: return
        scope.value = if (k in s) (s - k).ifEmpty { s } else s + k
    }

    init {
        viewModelScope.launch {
            val p = playlists.activeNow() ?: return@launch
            playlistId = p.id
            val page = runCatching { browse.page(p, BrowseKind.HOME) }.getOrNull() ?: return@launch
            trending.value = page.rows.filter { it.style == RowStyle.TOP10 && it.works.isNotEmpty() }
                .map { r -> Suggestion(r.title, r.tagline, enrich.decorate(p.id, r.works), r.works.first().type == com.sokoul.iptv.data.db.ContentType.SERIES) }
        }
    }

    val results: StateFlow<SearchUi?> = combine(query.debounce(300), scope.filterNotNull()) { q, s -> q.trim() to s }
        .distinctUntilChanged()
        .mapLatest { (q, s) ->
            val p = playlists.activeNow() ?: return@mapLatest null
            playlistId = p.id
            if (q.length < 2) return@mapLatest null
            val r = catalog.search(p.id, q, s)
            val movies = if (r.movies.isEmpty()) emptyList() else vod.groupMovies(p.id, r.movies).map { it.item.toWork(it.versions) }
            val series = if (r.series.isEmpty()) emptyList() else vod.groupSeries(p.id, r.series).map { it.item.toWork(it.versions) }
            enrich.request(p.id, (movies + series).take(30))
            SearchUi(r.channels, r.programs, enrich.decorate(p.id, movies), enrich.decorate(p.id, series))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Complétion : 3 titres du catalogue dont un mot commence par la saisie. */
    val completions: StateFlow<List<String>> = combine(query.debounce(150), scope.filterNotNull()) { q, s -> q.trim() to s }
        .distinctUntilChanged()
        .mapLatest { (q, s) ->
            val p = playlists.activeNow() ?: return@mapLatest emptyList()
            runCatching { catalog.suggest(p.id, q, s) }.getOrDefault(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Ce que le champ annonce, selon le périmètre. */
internal fun searchPlaceholder(scope: Set<SearchKind>): String = when (scope) {
    setOf(SearchKind.MOVIES) -> "Rechercher un film"
    setOf(SearchKind.SERIES) -> "Rechercher une série"
    setOf(SearchKind.MOVIES, SearchKind.SERIES) -> "Rechercher un film, une série"
    setOf(SearchKind.CHANNELS, SearchKind.PROGRAMS), setOf(SearchKind.CHANNELS) -> "Rechercher une chaîne, un programme"
    else -> "Rechercher un titre, une chaîne"
}

@Composable
fun SearchScreen(nav: NavHostController, origin: String = com.sokoul.iptv.data.repo.SearchOrigin.ALL) {
    val vm: SearchViewModel = hiltViewModel()
    LaunchedEffect(origin) { vm.start(SearchKind.forOrigin(origin)) }
    val query by vm.query.collectAsStateWithLifecycle()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    val memory = rememberFocusMemory()
    val entry = remember { FocusRequester() }
    val kinds = scope ?: SearchKind.forOrigin(origin)
    val trendingInScope = trending.filter { if (it.series) SearchKind.SERIES in kinds else SearchKind.MOVIES in kinds }

    // Au retour d'une fiche : la carte d'où l'on vient ; sinon le clavier (TV) ou le champ
    LaunchedEffect(Unit) {
        repeat(20) {
            withFrameNanos { }
            if (memory.restore(sameGroup = true)) return@LaunchedEffect
            if (memory.last == null && runCatching { entry.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    val content: @Composable (Modifier) -> Unit = { m ->
        SearchResults(results, query, trendingInScope, kinds, vm.playlistId, nav, memory, m)
    }
    val chips: @Composable () -> Unit = {
        LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            items(SearchKind.entries) { k -> SokoulChip(k.label, { vm.toggle(k) }, selected = k in kinds) }
        }
    }

    if (l.isTv) {
        Row(Modifier.fillMaxSize().padding(top = l.safeTop)) {
            Column(Modifier.fillMaxHeight().padding(start = l.margin, top = Space.m)) {
                TvKeyboard(query, { vm.query.value = it }, completions, searchPlaceholder(kinds), firstKey = entry)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = Space.m)) {
                Text("Chercher dans", Modifier.padding(horizontal = l.margin), style = Type.Overline)
                Box(Modifier.padding(top = Space.s, bottom = Space.m)) { chips() }
                content(Modifier.weight(1f))
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            FormField(
                searchPlaceholder(kinds), query, { vm.query.value = it },
                Modifier.padding(horizontal = l.margin, vertical = if (l.isCompact) 12.dp else 24.dp).focusRequester(entry), imeAction = ImeAction.Search,
            )
            Box(Modifier.padding(bottom = Space.m)) { chips() }
            content(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchResults(
    r: SearchUi?,
    query: String,
    trending: List<Suggestion>,
    kinds: Set<SearchKind>,
    pid: Long,
    nav: NavHostController,
    memory: FocusMemory,
    modifier: Modifier,
) {
    val l = LocalLayout.current
    val state = rememberLazyListState()
    when {
        r == null && trending.isEmpty() ->
            EmptyState(Icons.Default.Search, "Recherche", "Tapez au moins 2 lettres.", modifier)
        r == null -> LazyColumn(modifier.fillMaxWidth(), state) {
            item {
                Text(
                    "Tapez au moins 2 lettres. En attendant, les tendances de la semaine :",
                    Modifier.padding(horizontal = l.margin).padding(bottom = 18.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodyMedium,
                )
            }
            trending.forEach { s -> workRow(s.title, s.tagline, s.works, "t:${s.title}", nav, memory) }
            item { Spacer(Modifier.height(40.dp)) }
        }
        r.isEmpty -> EmptyState(
            Icons.Default.Search, "Aucun résultat pour « $query »",
            if (kinds != SearchKind.ALL) "Vérifiez l'orthographe, ou cochez d'autres types ci-dessus." else "Vérifiez l'orthographe ou essayez le titre original.",
            modifier,
        )
        else -> LazyColumn(modifier.fillMaxWidth(), state) {
            fun count(n: Int) = "$n résultat${if (n > 1) "s" else ""}"
            if (r.movies.isNotEmpty()) workRow("Films", count(r.movies.size), r.movies, "movies", nav, memory)
            if (r.series.isNotEmpty()) workRow("Séries", count(r.series.size), r.series, "series", nav, memory)
            if (r.channels.isNotEmpty()) item("channels") {
                Column(Modifier.padding(bottom = l.rowSpacing)) {
                    RowHeader("Chaînes", count(r.channels.size))
                    CardRow(r.channels, { it.id }) { _, c ->
                        Box(Modifier.focusMemory(memory, "channels|${c.id}")) {
                            ChannelCard(c, null) { nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId)) }
                        }
                    }
                }
            }
            if (r.programs.isNotEmpty()) item("programs") {
                Column(Modifier.padding(bottom = l.rowSpacing)) {
                    RowHeader("Au programme", "Dans le guide TV des prochains jours")
                    CardRow(r.programs, { "${it.first.channelKey}|${it.first.start}" }) { _, (p, c) ->
                        FocusSurface(onClick = {
                            if (c != null) nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId))
                        }, modifier = Modifier.width(l.landscape).focusMemory(memory, "programs|${p.channelKey}:${p.start}")) { _ ->
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

private fun LazyListScope.workRow(title: String, tagline: String?, works: List<Work>, key: String, nav: NavHostController, memory: FocusMemory) {
    item(key) {
        Column(Modifier.padding(bottom = LocalLayout.current.rowSpacing)) {
            RowHeader(title, tagline)
            CardRow(works, { it.uid }) { _, w -> WorkCard(w, { nav.openWork(w) }, Modifier.focusMemory(memory, "$key|${w.uid}")) }
        }
    }
}
