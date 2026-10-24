package com.sokoul.iptv.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.ui.ParentalGate
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.CategoryChips
import com.sokoul.iptv.ui.components.CategoryColumn
import com.sokoul.iptv.ui.components.CategoryItem
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.LoadingBox
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PinDialog
import com.sokoul.iptv.ui.components.PosterCard
import com.sokoul.iptv.ui.components.buildCategoryItems
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.useSideRail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortMode(val label: String) { DEFAULT("Ordre du fournisseur"), RECENT("Ajouts récents"), AZ("Titre A → Z"), RATING("Mieux notés"), YEAR("Année (récent)") }

/** Élément affichable dans une grille (film ou série). */
data class GridItem(val id: String, val title: String, val poster: String?, val badge: String?, val subtitle: String?, val rating: Double?, val year: Int?, val added: Long)

@OptIn(ExperimentalCoroutinesApi::class)
abstract class CatalogViewModel(
    playlists: PlaylistRepository,
    protected val catalog: CatalogRepository,
    private val parental: ParentalGate,
    private val type: ContentType,
) : ViewModel() {
    val playlist: StateFlow<PlaylistEntity?> = playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selected = MutableStateFlow(SpecialCategory.RECENT)
    val sort = MutableStateFlow(SortMode.DEFAULT)

    val categories: StateFlow<List<CategoryItem>> = playlist.filterNotNull()
        .flatMapLatest { catalog.categories(it.id, type) }
        .map { list ->
            buildCategoryItems(list, withRecent = false).toMutableList().apply {
                add(1, CategoryItem(SpecialCategory.RECENT, "Récemment ajoutés"))
                removeAll { it.id == SpecialCategory.ALL }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    protected abstract fun source(pid: Long, cat: String): kotlinx.coroutines.flow.Flow<List<GridItem>>

    val items: StateFlow<List<GridItem>?> = combine(playlist.filterNotNull(), selected, sort) { p, c, s -> Triple(p.id, c, s) }
        .flatMapLatest { (pid, cat, s) -> source(pid, cat).map { sortItems(it, s) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun sortItems(list: List<GridItem>, s: SortMode) = when (s) {
        SortMode.DEFAULT -> list
        SortMode.RECENT -> list.sortedByDescending { it.added }
        SortMode.AZ -> list.sortedBy { it.title.lowercase() }
        SortMode.RATING -> list.sortedByDescending { it.rating ?: 0.0 }
        SortMode.YEAR -> list.sortedByDescending { it.year ?: 0 }
    }

    // Position conservée en revenant d'une fiche (focus sur l'affiche ouverte, défilement intact)
    var lastOpened: String? = null
    var gridIndex = 0
    var gridOffset = 0
    var listKey: Pair<String, SortMode>? = null

    fun hide(cat: String) = playlist.value?.let { p -> viewModelScope.launch { catalog.setCategoryPref(p.id, type, cat, hidden = true) } }
    fun lock(cat: String, locked: Boolean) = playlist.value?.let { p ->
        viewModelScope.launch {
            catalog.setCategoryPref(p.id, type, cat, locked = locked)
            // le groupe affiché vient d'être verrouillé alors que le code n'a pas été saisi → on le quitte
            if (locked && selected.value == cat && !parental.canAccessLocked()) selected.value = SpecialCategory.RECENT
        }
    }
    suspend fun unlock(pin: String) = parental.tryUnlock(pin)
    suspend fun hasPin() = parental.hasPin()
    fun isUnlocked() = parental.isOpen()

    init {
        // Re-verrouillage pendant l'affichage d'un groupe verrouillé → retour sur « Récemment ajoutés »
        viewModelScope.launch {
            combine(playlist.filterNotNull(), selected, parental.unlocked) { p, cat, _ -> p.id to cat }.collect { (pid, cat) ->
                if (catalog.isCategoryLocked(pid, type, cat) && !parental.canAccessLocked()) selected.value = SpecialCategory.RECENT
            }
        }
    }
}

@HiltViewModel
class MoviesViewModel @Inject constructor(playlists: PlaylistRepository, catalog: CatalogRepository, parental: ParentalGate) :
    CatalogViewModel(playlists, catalog, parental, ContentType.MOVIE) {
    override fun source(pid: Long, cat: String) = catalog.movies(pid, cat).map { list -> list.map(MovieEntity::toGrid) }
}

@HiltViewModel
class SeriesViewModel @Inject constructor(playlists: PlaylistRepository, catalog: CatalogRepository, parental: ParentalGate) :
    CatalogViewModel(playlists, catalog, parental, ContentType.SERIES) {
    override fun source(pid: Long, cat: String) = catalog.series(pid, cat).map { list -> list.map(SeriesEntity::toGrid) }
}

fun MovieEntity.toGrid() = GridItem(id, title, poster, quality, listOfNotNull(year?.toString(), language).joinToString(" · ").ifEmpty { null }, rating, year, added)
fun SeriesEntity.toGrid() = GridItem(id, title, poster, null, listOfNotNull(year?.toString(), genre?.substringBefore(',')).joinToString(" · ").ifEmpty { null }, rating, year, lastModified)

@Composable
fun MoviesScreen(nav: NavHostController) {
    val vm: MoviesViewModel = hiltViewModel()
    CatalogGrid(vm, "Films") { nav.navigate(MovieDetailRoute(it.id)) }
}

@Composable
fun SeriesScreen(nav: NavHostController) {
    val vm: SeriesViewModel = hiltViewModel()
    CatalogGrid(vm, "Séries") { nav.navigate(SeriesDetailRoute(it.id)) }
}

@Composable
private fun CatalogGrid(vm: CatalogViewModel, title: String, onOpen: (GridItem) -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val wide = useSideRail()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sortMenu by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf<CategoryItem?>(null) }
    var pinFor by remember { mutableStateOf<CategoryItem?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    val select: (CategoryItem) -> Unit = { c -> if (c.locked && !vm.isUnlocked()) pinFor = c else vm.selected.value = c.id }
    val longClick: (CategoryItem) -> Unit = { if (!it.id.startsWith("__")) catMenu = it }

    val grid: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(categories.firstOrNull { it.id == selected }?.name ?: title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                items?.let { Text("${it.size} titres", Modifier.padding(end = 12.dp), color = SokoulColors.TextMuted) }
                AppButton(sort.label, { sortMenu = true }, icon = Icons.AutoMirrored.Filled.Sort)
            }
            val list = items
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(Icons.Default.Movie, "Rien ici pour le moment", if (selected == SpecialCategory.FAVORITES) "Ajoutez des titres à « Ma liste » depuis leur fiche." else null)
                else -> {
                    val state = rememberLazyGridState(vm.gridIndex, vm.gridOffset)
                    val restore = remember { FocusRequester() }
                    LaunchedEffect(selected, sort) {
                        val key = selected to sort
                        if (vm.listKey != null && vm.listKey != key) { state.scrollToItem(0); vm.lastOpened = null }
                        vm.listKey = key
                    }
                    LaunchedEffect(state) {
                        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
                            .collect { (i, o) -> vm.gridIndex = i; vm.gridOffset = o }
                    }
                    LaunchedEffect(list) {
                        if (vm.lastOpened != null && list.any { it.id == vm.lastOpened }) {
                            withFrameNanos { }
                            runCatching { restore.requestFocus() }
                            vm.lastOpened = null
                        }
                    }
                    LazyVerticalGrid(
                        // ~5 affiches par ligne sur TV (comme TiviMate), 3 sur téléphone
                        columns = GridCells.Adaptive(if (wide) 104.dp else 104.dp),
                        state = state,
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(list, key = { it.id }) { item ->
                            PosterCard(
                                item.title, item.poster, { vm.lastOpened = item.id; onOpen(item) },
                                Modifier.fillMaxWidth().then(if (item.id == vm.lastOpened) Modifier.focusRequester(restore) else Modifier),
                                badge = item.badge, subtitle = item.subtitle,
                            )
                        }
                    }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            CategoryColumn(categories, selected, select, Modifier.width(240.dp).fillMaxHeight().background(SokoulColors.Surface.copy(alpha = 0.6f)), onLongClick = longClick)
            Box(Modifier.weight(1f)) { grid(Modifier.fillMaxSize()) }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            CategoryChips(categories, selected, select, onLongClick = longClick)
            grid(Modifier.fillMaxSize())
        }
    }

    if (sortMenu) {
        OptionsDialog("Trier par", SortMode.entries.map { m -> MenuOption(m.label, selected = m == sort) { vm.sort.value = m } }, onDismiss = { sortMenu = false })
    }
    catMenu?.let { c ->
        OptionsDialog(
            c.name,
            listOf(
                MenuOption("Masquer ce groupe", Icons.Default.VisibilityOff) { vm.hide(c.id) },
                MenuOption(if (c.locked) "Déverrouiller" else "Verrouiller (contrôle parental)", Icons.Default.Lock) {
                    scope.launch {
                        when {
                            c.locked && vm.isUnlocked() -> vm.lock(c.id, false)
                            c.locked -> pinFor = c.copy(name = "unlock:" + c.id)
                            vm.hasPin() -> vm.lock(c.id, true)
                            else -> android.widget.Toast.makeText(context, "Définissez d'abord un code dans Réglages › Contrôle parental", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
            ),
            onDismiss = { catMenu = null },
        )
    }
    pinFor?.let { c ->
        PinDialog("Code parental", onResult = { pin ->
            scope.launch {
                val r = vm.unlock(pin)
                if (r.ok) {
                    if (c.name.startsWith("unlock:")) vm.lock(c.id, false) else vm.selected.value = c.id
                    pinFor = null; pinError = null
                } else pinError = r.message
            }
        }, onDismiss = { pinFor = null; pinError = null }, error = pinError)
    }
}
