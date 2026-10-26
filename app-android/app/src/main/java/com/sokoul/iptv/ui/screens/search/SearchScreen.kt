package com.sokoul.iptv.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.TvKeyboardWidth
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.Radius
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
    private val settings: com.sokoul.iptv.data.SettingsStore,
) : ViewModel() {
    val query = MutableStateFlow("")
    /** Recherches récentes (8 dernières), proposées quand le champ est vide. */
    val recents: StateFlow<List<String>> = settings.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Un résultat a été ouvert : la saisie rejoint les recherches récentes. */
    fun picked() {
        val q = query.value
        if (q.trim().length >= 2) viewModelScope.launch { settings.addRecentSearch(q) }
    }

    fun clearRecents() = viewModelScope.launch { settings.clearRecentSearches() }
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
    val recents by vm.recents.collectAsStateWithLifecycle()
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
        SearchResults(
            results, query, trendingInScope, kinds, vm.playlistId, nav, memory, m,
            recents = recents, onRecent = { vm.query.value = it }, onPicked = vm::picked,
        )
    }
    val chips: @Composable () -> Unit = {
        LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            items(SearchKind.entries) { k -> SokoulChip(k.label, { vm.toggle(k) }, selected = k in kinds) }
        }
    }

    if (l.isTv) {
        TvSearch(query, { vm.query.value = it }, searchPlaceholder(kinds), completions, kinds, vm::toggle, entry, content)
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

/**
 * Recherche TV, centrée : grand champ, suggestions et périmètre en pastilles, clavier AZERTY compact, puis les résultats
 * en rangées. ▼ vers les résultats : le clavier se replie et les résultats prennent l'écran ; ▲ jusqu'au champ le rouvre.
 * Entrée : la page descend en fondu (300 ms).
 */
@Composable
private fun TvSearch(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    completions: List<String>,
    kinds: Set<SearchKind>,
    onToggle: (SearchKind) -> Unit,
    entry: FocusRequester,
    content: @Composable (Modifier) -> Unit,
) {
    val l = LocalLayout.current
    val reduced = LocalReducedMotion.current
    var collapsed by remember { mutableStateOf(false) }
    var reopen by remember { mutableStateOf(0) }
    val intro = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(Motion.MEDIUM, easing = Motion.Decelerate)) }
    // champ atteint par ▲ depuis les résultats : le clavier revient et reprend le focus
    LaunchedEffect(reopen) {
        if (reopen == 0) return@LaunchedEffect
        repeat(10) { withFrameNanos { }; if (runCatching { entry.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    Column(
        Modifier.fillMaxSize().padding(top = l.safeTop + 22.dp)
            .graphicsLayer { alpha = intro.value; translationY = -(1f - intro.value) * 24.dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchField(
            query, placeholder, focusable = collapsed,
            onFocus = { collapsed = false; reopen++ },
            modifier = Modifier.fillMaxWidth(0.6f).widthIn(min = TvKeyboardWidth),
        )
        AnimatedVisibility(
            !collapsed,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(250)) + fadeOut(tween(200)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // suggestions (dès 2 lettres) puis périmètre, sur une ligne centrée ; place réservée : rien ne saute
                Row(
                    Modifier.padding(top = 12.dp, bottom = 14.dp).height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically,
                ) {
                    completions.take(3).forEach { s -> SokoulChip(s, { onQuery(s) }, icon = Icons.Default.Search) }
                    if (completions.isNotEmpty()) Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(SokoulColors.white(0.18f)))
                    SearchKind.entries.forEach { k -> SokoulChip(k.label, { onToggle(k) }, selected = k in kinds) }
                }
                TvKeyboard(query, onQuery, firstKey = entry)
                Text(
                    "Lecture : espace  ·  Retour rapide : effacer  ·  ▼ résultats", Modifier.padding(top = 10.dp, bottom = 6.dp),
                    style = Type.Meta, color = SokoulColors.TextMuted,
                )
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp)
                .onFocusChanged { if (it.hasFocus) collapsed = true },
        ) { content(Modifier.fillMaxSize()) }
    }
}

/** Champ de la recherche TV : grande saisie avec curseur. Focalisable seulement quand le clavier est replié (▲ le rouvre). */
@Composable
private fun SearchField(query: String, placeholder: String, focusable: Boolean, onFocus: () -> Unit, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val blink = if (reduced) null else rememberInfiniteTransition(label = "caret").animateFloat(
        1f, 0f, infiniteRepeatable(tween(530, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Reverse), label = "caret",
    )
    FocusSurface(
        onClick = onFocus, onFocus = onFocus, style = FocusStyle.Chip, shape = Radius.Card,
        color = SokoulColors.SurfaceHigh, focusedColor = SokoulColors.SurfaceHigher,
        modifier = modifier.focusProperties { canFocus = focusable },
    ) { _ ->
        Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, Modifier.size(28.dp), tint = SokoulColors.Accent)
            Spacer(Modifier.width(14.dp))
            Text(
                query.ifEmpty { placeholder }, Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (query.isEmpty()) SokoulColors.TextMuted else SokoulColors.TextPrimary,
            )
            // curseur : opacité lue au dessin (aucune recomposition)
            Box(Modifier.padding(start = 3.dp).size(2.dp, 30.dp).graphicsLayer { alpha = blink?.value ?: 1f }.background(SokoulColors.Accent))
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
    recents: List<String> = emptyList(),
    onRecent: (String) -> Unit = {},
    onPicked: () -> Unit = {},
) {
    val l = LocalLayout.current
    val state = rememberLazyListState()
    val open: (Work) -> Unit = { w -> onPicked(); nav.openWork(w) }
    when {
        r == null && trending.isEmpty() && recents.isEmpty() ->
            EmptyState(Icons.Default.Search, "Recherche", "Tapez au moins 2 lettres.", modifier)
        r == null -> LazyColumn(modifier.fillMaxWidth(), state) {
            if (recents.isNotEmpty()) item("recents") {
                Column(Modifier.padding(bottom = l.rowSpacing)) {
                    RowHeader("Recherches récentes", null)
                    LazyRow(contentPadding = PaddingValues(start = l.margin, end = l.margin, top = 6.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        items(recents, key = { it }) { q ->
                            Box(Modifier.focusMemory(memory, "recents|$q")) { SokoulChip(q, { onRecent(q) }, icon = Icons.Default.History) }
                        }
                    }
                }
            }
            if (trending.isNotEmpty()) item("hint") {
                Text(
                    "Tendances de la semaine",
                    Modifier.padding(horizontal = l.margin).padding(bottom = 6.dp), color = SokoulColors.TextMuted, style = Type.Overline,
                )
            }
            trending.forEach { s -> workRow(s.title, s.tagline, s.works, "t:${s.title}", memory, open) }
            item { Spacer(Modifier.height(40.dp)) }
        }
        r.isEmpty -> EmptyState(
            Icons.Default.Search, "Aucun résultat pour « $query »",
            if (kinds != SearchKind.ALL) "Vérifiez l'orthographe, ou cochez d'autres types ci-dessus." else "Vérifiez l'orthographe ou essayez le titre original.",
            modifier,
        )
        else -> LazyColumn(modifier.fillMaxWidth(), state) {
            fun count(n: Int) = "$n résultat${if (n > 1) "s" else ""}"
            if (r.movies.isNotEmpty()) workRow("Films", count(r.movies.size), r.movies, "movies", memory, open)
            if (r.series.isNotEmpty()) workRow("Séries", count(r.series.size), r.series, "series", memory, open)
            if (r.channels.isNotEmpty()) item("channels") {
                Column(Modifier.padding(bottom = l.rowSpacing)) {
                    RowHeader("Chaînes", count(r.channels.size))
                    CardRow(r.channels, { it.id }) { _, c ->
                        Box(Modifier.focusMemory(memory, "channels|${c.id}")) {
                            ChannelCard(c, null) { onPicked(); nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId)) }
                        }
                    }
                }
            }
            if (r.programs.isNotEmpty()) item("programs") {
                Column(Modifier.padding(bottom = l.rowSpacing)) {
                    RowHeader("Au programme", "Dans le guide TV des prochains jours")
                    CardRow(r.programs, { "${it.first.channelKey}|${it.first.start}" }) { _, (p, c) ->
                        FocusSurface(onClick = {
                            if (c != null) { onPicked(); nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId)) }
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

private fun LazyListScope.workRow(title: String, tagline: String?, works: List<Work>, key: String, memory: FocusMemory, open: (Work) -> Unit) {
    item(key) {
        Column(Modifier.padding(bottom = LocalLayout.current.rowSpacing)) {
            RowHeader(title, tagline)
            CardRow(works, { it.uid }) { _, w -> WorkCard(w, { open(w) }, Modifier.focusMemory(memory, "$key|${w.uid}")) }
        }
    }
}
