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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Dp
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
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import com.sokoul.iptv.ui.components.LandscapeCard
import com.sokoul.iptv.ui.components.RankLandscapeCard
import com.sokoul.iptv.ui.components.focusOnTop
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
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Résultats prêts à afficher : œuvres regroupées (une carte par œuvre) et enrichies par TMDB. */
data class SearchUi(
    val channels: List<ChannelEntity>,
    val programs: List<Pair<ProgramEntity, ChannelEntity?>>,
    val movies: List<Work>,
    val series: List<Work>,
    /** « Aussi disponible via Real-Debrid ». */
    val debrid: List<Work> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && programs.isEmpty() && movies.isEmpty() && series.isEmpty() && debrid.isEmpty()
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
    private val debrid: com.sokoul.iptv.data.debrid.DebridSources,
    private val tmdb: com.sokoul.iptv.data.net.TmdbClient,
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
        .transformLatest { (q, s) ->
            val p = playlists.activeNow() ?: return@transformLatest emit(null)
            playlistId = p.id
            if (q.length < 2) return@transformLatest emit(null)
            val r = catalog.search(p.id, q, s)
            val movies = if (r.movies.isEmpty()) emptyList() else vod.groupMovies(p.id, r.movies).map { it.item.toWork(it.versions) }
            val series = if (r.series.isEmpty()) emptyList() else vod.groupSeries(p.id, r.series).map { it.item.toWork(it.versions) }
            enrich.request(p.id, (movies + series).take(30))
            val ui = SearchUi(r.channels, r.programs, enrich.decorate(p.id, movies), enrich.decorate(p.id, series))
            emit(ui)
            // puis ce que Real-Debrid peut lire en plus (titres TMDB absents de l'abonnement)
            val extra = debridExtras(q, s, movies + series)
            if (extra.isNotEmpty()) emit(ui.copy(debrid = extra))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Titres TMDB que l'abonnement n'a pas, lisibles via Real-Debrid (au plus 15 ; rien sans Real-Debrid ou en profil Enfant). */
    private suspend fun debridExtras(q: String, s: Set<SearchKind>, have: List<Work>): List<Work> {
        if (SearchKind.MOVIES !in s && SearchKind.SERIES !in s) return emptyList()
        if (!runCatching { debrid.searchEnabled() }.getOrDefault(false)) return emptyList()
        val key = runCatching { settings.current().tmdbApiKey }.getOrDefault("")
        val found = runCatching { tmdb.searchMulti(key, q) }.getOrDefault(emptyList())
        return com.sokoul.iptv.data.debrid.DebridSearch.extras(found, have, movies = SearchKind.MOVIES in s, series = SearchKind.SERIES in s)
    }

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
    val rawResults by vm.results.collectAsStateWithLifecycle()
    val rawTrending by vm.trending.collectAsStateWithLifecycle()
    // profil Enfant : sans les titres 12 ans et plus, dans les résultats comme dans les tendances
    val kids = com.sokoul.iptv.ui.screens.profiles.isKidsProfile()
    val results = remember(rawResults, kids) {
        rawResults?.let { r -> if (!kids) r else r.copy(movies = r.movies.filter(com.sokoul.iptv.data.repo.KidsRating::allowed), series = r.series.filter(com.sokoul.iptv.data.repo.KidsRating::allowed), debrid = emptyList()) }
    }
    val trending = remember(rawTrending, kids) {
        if (!kids) rawTrending else rawTrending.map { it.copy(works = it.works.filter(com.sokoul.iptv.data.repo.KidsRating::allowed)) }.filter { it.works.isNotEmpty() }
    }
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

    val content: @Composable (Modifier, Boolean) -> Unit = { m, keyboardOpen ->
        SearchResults(
            results, query, trendingInScope, kinds, vm.playlistId, nav, memory, m, keyboardOpen = keyboardOpen,
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
            content(Modifier.weight(1f), false)
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
    content: @Composable (Modifier, Boolean) -> Unit,
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
                    // suggestions courtes : la ligne tient toujours dans la largeur (périmètre jamais coupé)
                    completions.take(3).forEach { s -> SokoulChip(chipLabel(s), { onQuery(s) }, icon = Icons.Default.Search) }
                    if (completions.isNotEmpty()) Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(SokoulColors.white(0.18f)))
                    SearchKind.entries.forEach { k -> SokoulChip(k.label, { onToggle(k) }, selected = k in kinds) }
                }
                TvKeyboard(query, onQuery, firstKey = entry)
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp)
                .onFocusChanged { if (it.hasFocus) collapsed = true }
                // ▲ en haut des résultats : le clavier revient (le champ seul ne prenait pas toujours le focus)
                .focusProperties { exit = { dir -> if (dir == FocusDirection.Up) { collapsed = false; reopen++; FocusRequester.Cancel } else FocusRequester.Default } }
                .focusGroup(),
        ) { content(Modifier.fillMaxSize(), !collapsed) }
        // légendes des touches, toujours au même endroit en bas de l'écran
        KeyHints(
            if (collapsed) listOf(KeyHint("OK", "Ouvrir"), KeyHint("▲", "Clavier"), KeyHint("Retour", "Quitter"))
            else listOf(KeyHint("⏯", "Espace"), KeyHint("◀◀", "Effacer"), KeyHint("▼", "Résultats")),
            Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
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
                style = com.sokoul.iptv.ui.theme.Type.SearchQuery,
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
    keyboardOpen: Boolean = false,
) {
    val l = LocalLayout.current
    val state = rememberLazyListState()
    val open: (Work) -> Unit = { w -> onPicked(); nav.openWork(w) }
    // titre trouvé via TMDB : sa fiche de l'abonnement s'il y est (IPTV + Real-Debrid), sinon Real-Debrid seul
    val offer: com.sokoul.iptv.ui.components.DebridOfferViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val rdScope = androidx.compose.runtime.rememberCoroutineScope()
    val openRd: (Work) -> Unit = { w ->
        onPicked()
        rdScope.launch { (offer.target(w) ?: com.sokoul.iptv.ui.components.debridRoute(pid, w))?.let { nav.navigateOnce(it) } }
    }
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
            trending.forEach { s -> rankRow(s.title, s.tagline, s.works, "t:${s.title}", memory, open) }
            item { Spacer(Modifier.height(40.dp)) }
        }
        r.isEmpty -> EmptyState(
            Icons.Default.Search, "Aucun résultat pour « $query »",
            if (kinds != SearchKind.ALL) "Vérifiez l'orthographe, ou cochez d'autres types ci-dessus." else "Vérifiez l'orthographe ou essayez le titre original.",
            modifier,
        )
        else -> BoxWithConstraints(modifier.fillMaxWidth()) {
          val best = remember(r) { bestResults(r.movies, r.series) }
          val bestIds = remember(best) { best.mapTo(HashSet()) { it.uid } }
          // clavier ouvert : un aperçu (une rangée) taillé pour la hauteur qui reste, jamais coupé
          val peekHeight = maxHeight
          LazyColumn(Modifier.fillMaxSize(), state) {
            fun count(n: Int) = "$n résultat${if (n > 1) "s" else ""}"
            val total = r.movies.size + r.series.size
            if (best.isNotEmpty()) {
                item("best") { BestRow(best, total, if (keyboardOpen) peekHeight else null, memory, open) }
            }
            if (!keyboardOpen || best.isEmpty()) {
            val movies = r.movies.filterNot { it.uid in bestIds }
            val series = r.series.filterNot { it.uid in bestIds }
            if (movies.isNotEmpty()) landscapeRow("Films", null, movies, "movies", memory, open)
            if (series.isNotEmpty()) landscapeRow("Séries", null, series, "series", memory, open)
            if (r.debrid.isNotEmpty()) landscapeRow("Aussi disponible via Real-Debrid", if (l.isTouch) "Hors abonnement · touchez pour lancer la meilleure version" else "Hors abonnement · OK pour lancer la meilleure version", r.debrid, "rd", memory, openRd)
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
            }
            item { Spacer(Modifier.height(40.dp)) }
          }
        }
    }
}

/** Suggestion en pastille : sans balises du fournisseur ([SUB], année), 22 caractères au plus. */
internal fun chipLabel(s: String): String {
    val clean = s.replace(Regex("""\s*[\[(][^\])]*[\])]"""), "").replace(Regex("""\s+-\s+\d{4}\s*$"""), "").trim().ifEmpty { s.trim() }
    return if (clean.length <= 22) clean else clean.take(21).trimEnd() + "…"
}

/** Meilleurs résultats : films et séries alternés (les plus pertinents de chaque liste d'abord). */
internal fun bestResults(movies: List<Work>, series: List<Work>, n: Int = 12): List<Work> {
    val out = ArrayList<Work>(n)
    var i = 0
    while (out.size < n && (i < movies.size || i < series.size)) {
        movies.getOrNull(i)?.let(out::add)
        if (out.size < n) series.getOrNull(i)?.let(out::add)
        i++
    }
    return out
}

/**
 * Meilleurs résultats. Clavier ouvert ([peekHeight] connu) : un aperçu taillé pour la hauteur qui reste ;
 * clavier replié : grandes cartes paysage. Une seule et même rangée dans les deux cas (mêmes clés) :
 * le focus ne se perd pas quand le clavier se replie.
 */
@Composable
private fun BestRow(works: List<Work>, total: Int, peekHeight: Dp?, memory: FocusMemory, open: (Work) -> Unit) {
    val l = LocalLayout.current
    val label = 18.dp
    val pad = if (peekHeight != null) 10.dp else 16.dp
    val cardH = peekHeight?.let { (it - label - pad * 2).coerceAtLeast(48.dp) }
    Column(Modifier.padding(bottom = if (peekHeight == null) l.rowSpacing else 0.dp)) {
        Text(
            "MEILLEURS RÉSULTATS · $total", Modifier.padding(horizontal = l.margin).height(label),
            style = Type.Kicker, maxLines = 1,
        )
        if (peekHeight == null) Text("Les plus proches de votre saisie", Modifier.padding(horizontal = l.margin), style = Type.Section, maxLines = 1)
        LazyRow(
            contentPadding = PaddingValues(start = l.margin, end = l.margin, top = pad, bottom = if (peekHeight != null) pad else 26.dp),
            horizontalArrangement = Arrangement.spacedBy(l.gutter),
        ) {
            items(works, key = { "best|${it.uid}" }) { w ->
                Box(Modifier.focusOnTop()) {
                    LandscapeCard(
                        w, { open(w) }, Modifier.focusMemory(memory, "best|${w.uid}"),
                        width = cardH?.let { it * 16f / 9f } ?: 260.dp, dense = cardH != null && cardH < 120.dp,
                    )
                }
            }
        }
    }
}

/** Rangée de cartes paysage, assez larges (260 dp) pour que le titre tienne dans l'image. */
private fun LazyListScope.landscapeRow(title: String, tagline: String?, works: List<Work>, key: String, memory: FocusMemory, open: (Work) -> Unit) {
    item(key) {
        Column(Modifier.padding(bottom = LocalLayout.current.rowSpacing)) {
            RowHeader(title, tagline)
            CardRow(works, { it.uid }) { _, w -> LandscapeCard(w, { open(w) }, Modifier.focusMemory(memory, "$key|${w.uid}"), width = 260.dp) }
        }
    }
}

/** Top 10 de la semaine : grand rang à empattements et carte paysage. */
private fun LazyListScope.rankRow(title: String, tagline: String?, works: List<Work>, key: String, memory: FocusMemory, open: (Work) -> Unit) {
    item(key) {
        Column(Modifier.padding(bottom = LocalLayout.current.rowSpacing)) {
            RowHeader(title, tagline, kicker = "TENDANCE")
            CardRow(works.take(10), { it.uid }) { i, w -> RankLandscapeCard(i + 1, w, { open(w) }, Modifier.focusMemory(memory, "$key|${w.uid}"), width = 240.dp) }
        }
    }
}
