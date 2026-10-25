package com.sokoul.iptv.ui.screens.saga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SagaCard
import com.sokoul.iptv.data.repo.SagaCrew
import com.sokoul.iptv.data.repo.SagaFace
import com.sokoul.iptv.data.repo.SagaPage
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.LocalTopBar
import com.sokoul.iptv.ui.TopTabsHeight
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.SagaTile
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.nav.SagaRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.screens.browse.openWork
import com.sokoul.iptv.ui.screens.vod.Pill
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// --- hub ------------------------------------------------------------------------------------------

data class SagasHubUi(val loading: Boolean = true, val sagas: List<SagaCard> = emptyList(), val noKey: Boolean = false)

@HiltViewModel
class SagasHubViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val repo: BrowseRepository,
    private val enrich: EnrichmentRepository,
) : ViewModel() {
    val ui = MutableStateFlow(SagasHubUi())

    init {
        viewModelScope.launch {
            val p = playlists.activeNow() ?: run { ui.value = SagasHubUi(loading = false); return@launch }
            if (enrich.apiKey().isBlank()) { ui.value = SagasHubUi(loading = false, noKey = true); return@launch }
            val list = try { repo.sagaHub(p.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { Timber.w(e, "Sagas"); emptyList() }
            ui.value = SagasHubUi(loading = false, sagas = list)
        }
    }
}

/** Onglet « Sagas » : toutes les sagas du catalogue, les grandes franchises d'abord. */
@Composable
fun SagasHubScreen(nav: NavHostController) {
    val vm: SagasHubViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val bar = LocalTopBar.current
    val l = LocalLayout.current
    val grid = rememberLazyGridState()
    val memory = rememberFocusMemory()
    val scope = rememberCoroutineScope()
    var initialFocusDone by rememberSaveable { mutableStateOf(bar?.arrivedByTab == true) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(l.landscape),
        state = grid,
        modifier = Modifier.fillMaxSize().background(SokoulColors.Background),
        contentPadding = PaddingValues(start = l.margin, end = l.margin, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(l.gutter),
        verticalArrangement = Arrangement.spacedBy(l.gutter),
    ) {
        item("head", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = if (bar != null) TopTabsHeight + l.safeTop else if (l.isTouch) 56.dp else 28.dp, bottom = 8.dp)) {
                Text("Sagas", style = Type.ScreenTitle)
                Text(
                    "Les grandes histoires du catalogue, film après film.", Modifier.padding(top = 4.dp),
                    color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        when {
            ui.loading -> items(8) { Box(Modifier.fillMaxWidth().height(l.landscape * 9f / 16f).clip(Radius.Card).shimmer()) }
            ui.noKey -> item("nokey", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Default.Key, "Clé TMDB nécessaire", "Les sagas s'appuient sur TheMovieDB : ajoutez une clé dans Réglages.", Modifier.height(320.dp))
            }
            ui.sagas.isEmpty() -> item("empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Default.CollectionsBookmark, "Aucune saga pour l'instant",
                    "Les sagas apparaissent au fil de l'analyse du catalogue. Revenez un peu plus tard.", Modifier.height(320.dp),
                )
            }
            else -> items(ui.sagas, key = { it.id }) { s ->
                SagaTile(
                    s, { nav.navigateOnce(SagaRoute(s.id)) },
                    modifier = Modifier.focusMemory(memory, "s:${s.id}"), width = Dp.Unspecified,
                )
            }
        }
    }

    if (bar != null) {
        LaunchedEffect(grid) {
            snapshotFlow { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 48 }
                .distinctUntilChanged().collect { bar.deep = it }
        }
        bar.down = { ui.sagas.firstOrNull()?.let { memory.requester("s:${it.id}") } }
        bar.backToTop = {
            scope.launch {
                grid.scrollToItem(0)
                ui.sagas.firstOrNull()?.let { runCatching { memory.requester("s:${it.id}").requestFocus() } }
            }
            true
        }
    }
    LaunchedEffect(ui.loading) {
        if (!l.isTv || ui.loading) return@LaunchedEffect
        repeat(6) {
            delay(80)
            if (memory.restore()) { initialFocusDone = true; return@LaunchedEffect }
            if (!initialFocusDone) {
                val first = ui.sagas.firstOrNull() ?: return@LaunchedEffect
                if (runCatching { memory.requester("s:${first.id}").requestFocus(FocusDirection.Enter) }.getOrDefault(false)) {
                    initialFocusDone = true; return@LaunchedEffect
                }
            }
        }
    }
}

// --- page « musée » --------------------------------------------------------------------------------

data class SagaUi(val loading: Boolean = true, val page: SagaPage? = null, val failed: Boolean = false, val noKey: Boolean = false)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SagaViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val repo: BrowseRepository,
    private val enrich: EnrichmentRepository,
    library: LibraryRepository,
) : ViewModel() {
    val ui = MutableStateFlow(SagaUi())
    /** Ordre du récit plutôt que de sortie. */
    val story = MutableStateFlow(false)
    private val pid = MutableStateFlow<Long?>(null)
    private var id: Int? = null

    /** Titres hors catalogue déjà dans Ma liste (« MOVIE|tmdb:N »). */
    val inList = pid.flatMapLatest { p ->
        if (p == null) flowOf(emptySet())
        else combine(library.favoriteIds(p, ContentType.MOVIE), library.favoriteIds(p, ContentType.SERIES)) { m, s ->
            (m.filter { it.startsWith("tmdb:") }.map { "MOVIE|$it" } + s.filter { it.startsWith("tmdb:") }.map { "SERIES|$it" }).toSet()
        }
    }

    fun load(collectionId: Int, force: Boolean = false) {
        if (id == collectionId && !force) return
        id = collectionId
        ui.value = SagaUi()
        viewModelScope.launch {
            val p = playlists.activeNow() ?: run { ui.value = SagaUi(loading = false, failed = true); return@launch }
            pid.value = p.id
            if (enrich.apiKey().isBlank()) { ui.value = SagaUi(loading = false, noKey = true); return@launch }
            val page = try { repo.sagaPage(p.id, collectionId) { partial -> ui.value = SagaUi(loading = false, page = partial) } } catch (e: CancellationException) { throw e } catch (e: Exception) { Timber.w(e, "Saga"); null }
            ui.value = SagaUi(loading = false, page = page, failed = page == null)
        }
    }

    fun retry() { id?.let { load(it, force = true) } }

    fun toggleList(w: Work) = viewModelScope.launch { pid.value?.let { repo.toggleOffCatalog(it, w) } }
}

/**
 * Page « musée » d'une saga : fond plein écran, résumé de l'univers, frise des films (ordre de sortie ou du récit),
 * séries du même univers, visages récurrents et coulisses. Les films absents du catalogue sont grisés
 * (« HORS CATALOGUE ») et peuvent être ajoutés à Ma liste.
 */
@Composable
fun SagaScreen(nav: NavHostController, collectionId: Int) {
    val vm: SagaViewModel = hiltViewModel()
    LaunchedEffect(collectionId) { vm.load(collectionId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val story by vm.story.collectAsStateWithLifecycle()
    val inList by vm.inList.collectAsStateWithLifecycle(emptySet())
    val l = LocalLayout.current
    val memory = rememberFocusMemory()
    val list = rememberLazyListState()
    var offFor by remember { mutableStateOf<Work?>(null) }
    val page = ui.page

    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        if (page?.backdrop != null) {
            NetImage(tmdbSized(page.backdrop, "w1280"), null, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to SokoulColors.bg(0.95f), 0.55f to SokoulColors.bg(0.6f), 1f to SokoulColors.bg(0.25f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 0.62f to SokoulColors.bg(0.9f), 1f to SokoulColors.Background)))
        }
        when {
            ui.loading -> SagaSkeleton()
            ui.noKey -> EmptyState(Icons.Default.Key, "Clé TMDB nécessaire", "Les sagas s'appuient sur TheMovieDB : ajoutez une clé dans Réglages.")
            page == null -> EmptyState(Icons.Default.Refresh, "Impossible de charger cette saga", "Vérifiez la connexion, puis réessayez.") {
                AppButton("Réessayer", vm::retry, primary = true, icon = Icons.Default.Refresh, modifier = Modifier.focusMemory(memory, "retry"))
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 56.dp)) {
                item("head") { SagaHeader(page, story, memory) { vm.story.value = it } }
                item("films") {
                    val films = if (story) page.story ?: page.films else page.films
                    // changement d'ordre : la frise repart du début
                    val row = rememberLazyListState()
                    LaunchedEffect(story) { row.scrollToItem(0) }
                    Section(
                        "Les films",
                        if (story && page.story != null) "Dans l'ordre du récit" else "Par date de sortie · ${page.inCatalog} sur ${page.films.size} au catalogue",
                    )
                    LazyRow(state = row, contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
                        items(films, key = { it.uid }) { w ->
                            Column(Modifier.width(l.poster)) {
                                Text(w.year?.toString() ?: "À venir", Modifier.padding(bottom = 6.dp, start = 2.dp), style = Type.Overline)
                                WorkCard(
                                    w, { if (w.available) nav.openWork(w) else offFor = w },
                                    Modifier.focusMemory(memory, "f:${w.uid}"), showPlatform = false,
                                )
                            }
                        }
                    }
                }
                if (page.series.isNotEmpty()) item("series") {
                    Section("Séries du même univers", null)
                    LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
                        items(page.series, key = { it.uid }) { w ->
                            WorkCard(w, { if (w.available) nav.openWork(w) else offFor = w }, Modifier.focusMemory(memory, "s:${w.uid}"), showPlatform = false)
                        }
                    }
                }
                if (page.faces.isNotEmpty()) item("faces") {
                    Section("Visages de la saga", "Les acteurs qui reviennent d'un film à l'autre")
                    LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
                        items(page.faces, key = { it.id }) { f -> FaceCard(f, Modifier.focusMemory(memory, "p:${f.id}")) }
                    }
                }
                if (page.crew.isNotEmpty()) item("crew") {
                    Section("Les coulisses", null)
                    LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
                        items(page.crew, key = { it.label }) { c -> CrewCard(c, Modifier.focusMemory(memory, "c:${c.label}")) }
                    }
                }
            }
        }
        if (l.isTouch) {
            Box(
                Modifier.statusBarsPadding().padding(8.dp).size(44.dp).clip(Radius.Pill).background(SokoulColors.black(0.5f))
                    .dpadClickable({ nav.popBackStack() }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
        }
    }

    offFor?.let { w ->
        val there = "${w.type.name}|tmdb:${w.tmdbId}" in inList
        OptionsDialog(
            w.title,
            listOf(
                if (there) MenuOption("Retirer de Ma liste", Icons.Default.Remove) { vm.toggleList(w); offFor = null }
                else MenuOption("Ajouter à Ma liste", Icons.Default.Add) { vm.toggleList(w); offFor = null },
            ),
            subtitle = "Hors catalogue : ce titre deviendra lisible dès qu'une source le proposera.",
            onDismiss = { offFor = null },
        )
    }

    // TV : focus sur le premier film du catalogue (sinon le premier de la frise) ; au retour, sur la carte d'où l'on vient
    LaunchedEffect(ui.loading, page?.id) {
        if (!l.isTv || ui.loading) return@LaunchedEffect
        repeat(8) {
            delay(80)
            if (memory.restore()) return@LaunchedEffect
            val first = page?.let { p -> (p.films.firstOrNull { it.available } ?: p.films.firstOrNull())?.let { "f:${it.uid}" } }
                ?: if (ui.failed) "retry" else return@LaunchedEffect
            if (runCatching { memory.requester(first).requestFocus(FocusDirection.Enter) }.getOrDefault(false)) {
                // le titre de la saga reste visible au-dessus de la frise
                delay(16); list.scrollToItem(0)
                return@LaunchedEffect
            }
        }
    }
}

@Composable
private fun SagaHeader(page: SagaPage, story: Boolean, memory: com.sokoul.iptv.ui.components.FocusMemory, onStory: (Boolean) -> Unit) {
    val l = LocalLayout.current
    Column(
        Modifier.statusBarsPadding().padding(start = l.margin, end = l.margin, top = l.safeTop + 20.dp, bottom = 0.dp)
            .widthIn(max = l.heroTextWidth),
    ) {
        Text("SAGA", style = Type.Overline, color = SokoulColors.Gold)
        Text(page.name, style = Type.ScreenTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val facts = listOfNotNull(
            "${page.films.size} films",
            "${page.inCatalog} au catalogue",
            page.series.takeIf { it.isNotEmpty() }?.let { if (it.size > 1) "${it.size} séries" else "1 série" },
        )
        Text(facts.joinToString(" · "), Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = Type.Meta)
        page.overview?.let {
            Text(
                it, Modifier.padding(top = 8.dp).widthIn(max = 600.dp), color = SokoulColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        if (page.story != null) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Ordre de sortie", selected = !story, modifier = Modifier.focusMemory(memory, "o:release")) { onStory(false) }
                Pill("Ordre du récit", selected = story, modifier = Modifier.focusMemory(memory, "o:story")) { onStory(true) }
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String?) {
    val l = LocalLayout.current
    Column(Modifier.padding(start = l.margin, end = l.margin, top = 28.dp, bottom = 12.dp)) {
        Text(title, style = Type.Section)
        if (subtitle != null) Text(subtitle, Modifier.padding(top = 2.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FaceCard(f: SagaFace, modifier: Modifier = Modifier) {
    val size = 112.dp
    Column(modifier.width(size + 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // focalisable pour faire défiler la rangée à la télécommande ; pas d'action
        FocusSurface(onClick = {}, shape = CircleShape) { _ ->
            NetImage(tmdbSized(f.photo, "w185"), f.name, Modifier.size(size)) {
                Box(Modifier.size(size).background(SokoulColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Text(f.name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), style = Type.Section)
                }
            }
        }
        Text(f.name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text("Apparaît dans ${f.films} films", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CrewCard(c: SagaCrew, modifier: Modifier = Modifier) {
    FocusSurface(onClick = {}, modifier = modifier.width(260.dp), style = FocusStyle.Card) { _ ->
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(c.label, style = Type.Overline)
            Spacer(Modifier.height(8.dp))
            c.names.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun SagaSkeleton() {
    val l = LocalLayout.current
    Column(Modifier.fillMaxSize().padding(start = l.margin, top = l.safeTop + 20.dp)) {
        Box(Modifier.width(320.dp).height(44.dp).clip(Radius.Tiny).shimmer())
        Spacer(Modifier.height(14.dp))
        Box(Modifier.width(520.dp).height(64.dp).clip(Radius.Tiny).shimmer())
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
            repeat(6) { Box(Modifier.width(l.poster).height(l.poster * 1.5f).clip(Radius.Card).shimmer()) }
        }
    }
}
