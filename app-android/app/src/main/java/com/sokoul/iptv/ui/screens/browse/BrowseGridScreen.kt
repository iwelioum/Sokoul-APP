package com.sokoul.iptv.ui.screens.browse

import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import com.sokoul.iptv.ui.LocalTopBar
import com.sokoul.iptv.ui.TopTabsHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.GridHeader
import com.sokoul.iptv.data.repo.GridSort
import com.sokoul.iptv.data.repo.GridSpec
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.ui.components.metaLine
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PlatformBadge
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.screens.vod.Pill
import com.sokoul.iptv.ui.theme.LocalLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class GridUi(
    val loading: Boolean = true,
    val header: GridHeader? = null,
    /** Œuvres dans l'ordre éditorial (le tri est appliqué à l'affichage). */
    val works: List<Work> = emptyList(),
    val failed: Boolean = false,
    val sort: GridSort = GridSort.DEFAULT,
) {
    val sorted: List<Work> by lazy { sort.apply(works) }
    /** La sélection a été coupée à [BrowseRepository.GRID_LIMIT] titres. */
    val truncated get() = works.size >= BrowseRepository.GRID_LIMIT
}

@OptIn(FlowPreview::class)
@HiltViewModel
class BrowseGridViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val repo: BrowseRepository,
    private val enrich: EnrichmentRepository,
    private val actions: WorkActions,
) : ViewModel() {
    val ui = MutableStateFlow(GridUi())
    private var spec: String? = null
    private var job: Job? = null
    private var pid: Long? = null

    fun load(s: String, force: Boolean = false) {
        if (spec == s && !force) return
        // même grille rechargée (Ma liste au retour) : on garde l'affichage pendant la mise à jour
        val silent = spec == s && !ui.value.loading && !ui.value.failed
        spec = s
        job?.cancel()
        val sort = ui.value.sort
        if (!silent) ui.value = GridUi(sort = sort)
        job = viewModelScope.launch {
            val p = playlists.activeNow() ?: run { ui.value = GridUi(loading = false, sort = sort); return@launch }
            pid = p.id
            val gs = GridSpec.decode(s) ?: run { ui.value = GridUi(loading = false, sort = sort); return@launch }
            val (header, base) = try {
                repo.grid(p.id, gs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Grille $gs")
                ui.value = GridUi(loading = false, failed = true, sort = sort)
                return@launch
            }
            ui.value = ui.value.copy(loading = false, header = header, works = enrich.decorate(p.id, base))
            enrich.request(p.id, base.take(60))
            enrich.updates.drop(1).debounce(4_000).collect { ui.value = ui.value.copy(works = enrich.decorate(p.id, base)) }
        }
    }

    fun retry() { spec?.let { load(it, force = true) } }

    fun setSort(s: GridSort) { ui.value = ui.value.copy(sort = s) }

    /** Enrichissement au fil du défilement : les cartes autour de la position visible. */
    fun visibleFrom(index: Int) {
        val p = pid ?: return
        val list = ui.value.sorted
        if (list.isEmpty()) return
        enrich.request(p, list.drop((index - 6).coerceAtLeast(0)).take(48))
    }

    suspend fun playRoute(w: Work): Any = actions.playRoute(pid, w)

    fun toggleList(w: Work) = viewModelScope.launch {
        pid?.let { if (w.available) actions.toggleList(it, w) else repo.toggleOffCatalog(it, w) }
        if (spec?.startsWith("fav") == true) retry()
    }
}

/** Grille « Tout voir » : rangée complète, plateforme, saga, catégorie. */
@Composable
fun BrowseGridScreen(
    nav: NavHostController,
    title: String,
    spec: String,
    emptyTitle: String = "Rien à afficher",
    emptyText: String = "Aucun titre de cette sélection n'est disponible dans votre abonnement.",
) {
    val bar = LocalTopBar.current
    val vm: BrowseGridViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val memory = rememberFocusMemory()
    // ouverte depuis un onglet : le focus reste sur les onglets
    var initialFocusDone by rememberSaveable { mutableStateOf(bar?.arrivedByTab == true) }
    var menuFor by remember { mutableStateOf<Work?>(null) }
    val rdPlay = com.sokoul.iptv.ui.components.rememberDebridPlay(nav)
    // Ma liste change ailleurs (fiches) : rechargée à chaque retour sur la page
    LaunchedEffect(spec) { vm.load(spec, force = spec.startsWith("fav")) }
    val header = ui.header
    val works = ui.sorted
    // titre hors catalogue (retenu depuis une saga) : pas de fiche, seulement le retirer
    val open: (Work) -> Unit = { w -> if (w.available) nav.openWork(w) else menuFor = w }
    val play: (Work) -> Unit = { w -> scope.launch { nav.navigateOnce(vm.playRoute(w)) } }

    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(l.gridCell),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = l.margin, end = l.margin, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(l.gutter),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("banner", span = { GridItemSpan(maxLineSpan) }) { GridBanner(title, header, ui.works.size, ui.loading, ui.truncated, underTabs = bar != null) }
            // tri : inutile pour une saga (ordre chronologique) ou une liste trop courte
            val saga = header?.backdrop != null && header.platform == null
            if (!ui.loading && ui.works.size > 8 && !saga) {
                item("sort", span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(GridSort.entries, key = { it.name }) { s ->
                            Pill(s.label, selected = s == ui.sort, modifier = Modifier.focusMemory(memory, "sort:${s.name}")) {
                                vm.setSort(s)
                                scope.launch { grid.scrollToItem(0) }
                            }
                        }
                    }
                }
            }
            when {
                ui.loading -> items(18) { Box(Modifier.fillMaxWidth().padding(bottom = 30.dp).height(l.gridCell * 1.5f).shimmer()) }
                ui.failed -> item("failed", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.Refresh, "Impossible de charger cette sélection", "Vérifiez la connexion, puis réessayez.", Modifier.height(320.dp)) {
                        AppButton("Réessayer", vm::retry, primary = true, icon = Icons.Default.Refresh, modifier = Modifier.focusMemory(memory, "retry"))
                    }
                }
                works.isEmpty() -> item("empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.Movie, emptyTitle, emptyText, Modifier.height(320.dp))
                }
                else -> items(works, key = { it.uid }) { w ->
                    WorkCard(
                        w, { open(w) }, Modifier.fillMaxWidth().focusMemory(memory, w.uid), width = l.gridCell * 2,
                        showPlatform = header?.platform == null, onLongClick = { menuFor = w },
                    )
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

    menuFor?.let { w ->
        OptionsDialog(
            w.title,
            if (!w.available) listOfNotNull(
                rdPlay?.let { p -> MenuOption("Lire via Real-Debrid", Icons.Default.PlayArrow) { p(w) } },
                MenuOption("Retirer de Ma liste", Icons.Default.Remove) { vm.toggleList(w) },
            )
            else listOf(
                MenuOption(if (w.resume != null) "Reprendre" else "Lecture", Icons.Default.PlayArrow) { play(w) },
                MenuOption("Plus d'infos", Icons.Default.Info) { open(w) },
                MenuOption("Ajouter à / retirer de ma liste", Icons.Default.Add) { vm.toggleList(w) },
            ),
            subtitle = if (!w.available && rdPlay != null) "Hors abonnement : lisible via Real-Debrid si une version existe." else if (!w.available) "Hors catalogue : ce titre deviendra lisible dès qu'une source le proposera." else w.metaLine().ifEmpty { null },
            onDismiss = { menuFor = null },
        )
    }

    // sous les onglets : ils s'effacent dès que la grille défile ; Retour remonte en haut
    if (bar != null) {
        LaunchedEffect(grid) {
            snapshotFlow { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 48 }
                .distinctUntilChanged().collect { bar.deep = it }
        }
        // ▼ depuis les onglets : première carte
        bar.down = { works.firstOrNull()?.let { memory.requester(it.uid) } }
        bar.backToTop = {
            scope.launch {
                grid.scrollToItem(0)
                works.firstOrNull()?.let { runCatching { memory.requester(it.uid).requestFocus() } }
            }
            true
        }
    }
    // enrichissement TMDB des cartes qui défilent (pas seulement des 60 premières)
    LaunchedEffect(grid) {
        snapshotFlow { grid.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            delay(300)
            vm.visibleFrom((i - 2).coerceAtLeast(0))
        }
    }
    // TV : focus sur la première carte à l'ouverture, sur la carte d'où l'on vient au retour
    LaunchedEffect(ui.loading, ui.failed, works.isNotEmpty()) {
        if (!l.isTv || ui.loading) return@LaunchedEffect
        repeat(6) {
            delay(80)
            if (memory.restore()) { initialFocusDone = true; return@LaunchedEffect }
            if (!initialFocusDone) {
                val first = when {
                    ui.failed -> "retry"
                    works.isNotEmpty() -> works.first().uid
                    else -> null
                } ?: return@LaunchedEffect
                if (runCatching { memory.requester(first).requestFocus(FocusDirection.Enter) }.getOrDefault(false)) { initialFocusDone = true; return@LaunchedEffect }
            }
        }
    }
}

@Composable
private fun GridBanner(title: String, header: GridHeader?, count: Int, loading: Boolean, truncated: Boolean, underTabs: Boolean = false) {
    val countText = if (truncated) "Les $count premiers titres" else if (count <= 1) "$count titre" else "$count titres"
    val l = LocalLayout.current
    val rich = header?.backdrop != null || header?.platform != null
    if (!rich) {
        Column(Modifier.statusBarsPadding().padding(top = when { underTabs -> TopTabsHeight + l.safeTop; l.isTouch -> 56.dp; else -> 28.dp }, bottom = 8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = header?.subtitle ?: if (!loading) countText else null
            if (sub != null) Text(sub, Modifier.padding(top = 4.dp), color = SokoulColors.TextSecondary)
        }
        return
    }
    val h = (l.heightDp * if (l.isCompact) 0.36f else 0.42f).dp
    Box(Modifier.fillMaxWidth().height(h).padding(bottom = 8.dp).clip(if (l.isCompact) Radius.None else Radius.Panel)) {
        val p = header!!.platform
        if (p != null) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(p.color), Color(p.accent), SokoulColors.Background))))
        } else {
            NetImage(tmdbSized(header.backdrop, "w1280"), null, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to SokoulColors.bg(0.9f), 0.6f to SokoulColors.bg(0.4f), 1f to Color.Transparent)))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to SokoulColors.bg(0.8f))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp).widthIn(max = l.heroTextWidth)) {
            if (p != null) PlatformBadge(p, Modifier.padding(bottom = 10.dp), large = true)
            else Text("SAGA", color = SokoulColors.Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text(header.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp)) {
                Text(listOfNotNull(header.subtitle, if (!loading) (if (truncated) countText else "$count disponibles") else null).joinToString(" · "), color = SokoulColors.TextSecondary)
            }
            header.overview?.let {
                Text(it, Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
