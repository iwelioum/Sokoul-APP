package com.sokoul.iptv.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.CatalogIndexer
import com.sokoul.iptv.data.repo.DurationBand
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.ExploreFilter
import com.sokoul.iptv.data.repo.GridSort
import com.sokoul.iptv.data.repo.IndexCoverage
import com.sokoul.iptv.data.repo.OriginalLanguage
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.TmdbGenres
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.ScreenHeader
import com.sokoul.iptv.ui.components.SokoulChip
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ExploreUi(
    val loading: Boolean = true,
    val works: List<Work> = emptyList(),
    val coverage: IndexCoverage? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val repo: BrowseRepository,
    private val indexer: CatalogIndexer,
    private val enrich: EnrichmentRepository,
) : ViewModel() {
    val type = MutableStateFlow<ContentType?>(null)
    val filter = MutableStateFlow(ExploreFilter())
    val sort = MutableStateFlow(GridSort.DEFAULT)
    val ui = MutableStateFlow(ExploreUi())

    init {
        viewModelScope.launch {
            val params = combine(type.filterNotNull(), filter, sort) { t, f, s -> Triple(t, f, s) }.distinctUntilChanged()
            // un changement de filtre annule la recherche en cours ; un nouveau lot indexé, non :
            // il est pris en compte une fois la recherche finie (au plus toutes les 1,5 s)
            params.collectLatest { (t, f, s) ->
                val p = playlists.activeNow() ?: run { ui.value = ExploreUi(loading = false); return@collectLatest }
                suspend fun load(refresh: Boolean) {
                    val works = try { repo.explore(p.id, t, f, s) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        Timber.w(e, "Explorer"); null
                    }
                    ui.value = ui.value.copy(loading = false, works = works ?: if (refresh) ui.value.works else emptyList())
                }
                load(false)
                enrich.updates.drop(1).debounce(1_500).conflate().collect { load(true) }
            }
        }
    }

    init {
        // avancement de l'analyse : compté toutes les 30 s seulement (requête lourde sur un gros catalogue)
        viewModelScope.launch {
            while (true) {
                val p = playlists.activeNow()
                val cov = p?.let { runCatching { indexer.coverage(it.id) }.getOrNull() }
                ui.value = ui.value.copy(coverage = cov)
                if (cov == null || cov.complete) break
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    fun start(t: ContentType) { if (type.value == null) type.value = t }
    fun set(f: ExploreFilter) { ui.value = ui.value.copy(loading = true); filter.value = f }

    /** « Surprends-moi » : null si aucun titre ne convient. */
    fun surprise(onResult: (Work?) -> Unit) {
        val t = type.value ?: return
        viewModelScope.launch {
            val p = playlists.activeNow() ?: return@launch onResult(null)
            onResult(runCatching { repo.surprise(p.id, t, filter.value) }.getOrNull())
        }
    }
}

private val Decades = listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960)
private val Ratings = listOf(8.0, 7.5, 7.0, 6.0)

private fun rating(r: Double) = "${r.toString().removeSuffix(".0").replace('.', ',')} et plus"

/** Explorer : tout le catalogue d'un type, filtré par genre, décennie, note, durée, langue et qualité. */
@Composable
fun ExploreScreen(nav: NavHostController, typeName: String) {
    val type = runCatching { ContentType.valueOf(typeName) }.getOrDefault(ContentType.MOVIE)
    val vm: ExploreViewModel = hiltViewModel()
    LaunchedEffect(type) { vm.start(type) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val f by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    val memory = rememberFocusMemory()
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    val movies = type == ContentType.MOVIE
    val genres = TmdbGenres.of(type)
    val context = androidx.compose.ui.platform.LocalContext.current
    // heures encore atteignables avec au moins 1 h de film
    val finishHours = remember { listOf(22, 23, 24).filter { com.sokoul.iptv.data.repo.ExploreQuery.minutesUntil(it, System.currentTimeMillis()) >= 60 } }

    fun update(nf: ExploreFilter) { vm.set(nf); scope.launch { grid.scrollToItem(0) } }

    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(l.gridCell),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = l.margin, end = l.margin, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(l.gutter),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("head", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    val cov = ui.coverage
                    ScreenHeader(
                        if (movies) "Explorer les films" else "Explorer les séries",
                        subtitle = when {
                            ui.loading -> "Recherche…"
                            cov != null && !cov.complete ->
                                "${ui.works.size} titres · Analyse du catalogue en cours (${cov.percent} %) · certains titres n'apparaissent pas encore"
                            else -> if (ui.works.size <= 1) "${ui.works.size} titre" else "${ui.works.size} titres"
                        },
                    )
                    // filtres : une pastille par critère, la valeur choisie s'y affiche
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s), contentPadding = PaddingValues(vertical = Space.xs)) {
                        item("surprise") {
                            SokoulChip("Surprends-moi", {
                                vm.surprise { w -> if (w != null) nav.openWork(w) else android.widget.Toast.makeText(context, "Aucun titre bien noté et pas encore vu avec ces filtres", android.widget.Toast.LENGTH_SHORT).show() }
                            }, Modifier.focusMemory(memory, "f:surprise"), icon = Icons.Default.Casino)
                        }
                        item("genre") { FilterChip("Genre", f.genre?.let { genres[it] }, memory, "f:genre") { dialog = "genre" } }
                        item("decade") { FilterChip("Décennie", f.decade?.let(::decadeLabel), memory, "f:decade") { dialog = "decade" } }
                        item("rating") { FilterChip("Note", f.minRating?.let(::rating), memory, "f:rating") { dialog = "rating" } }
                        if (movies) item("duration") { FilterChip("Durée", f.duration?.label, memory, "f:duration") { dialog = "duration" } }
                        if (movies && (f.finishBy != null || finishHours.isNotEmpty())) item("finish") { FilterChip("Fini avant", f.finishBy?.let(::hourLabel), memory, "f:finish") { dialog = "finish" } }
                        item("lang") { FilterChip("Langue", OriginalLanguage.entries.firstOrNull { it.code == f.language }?.label, memory, "f:lang") { dialog = "lang" } }
                        if (movies) item("uhd") {
                            SokoulChip("4K", { update(f.copy(uhd = !f.uhd)) }, Modifier.focusMemory(memory, "f:uhd"), selected = f.uhd)
                        }
                        if (!f.isEmpty) item("reset") {
                            SokoulChip("Tout effacer", { update(ExploreFilter()) }, Modifier.focusMemory(memory, "f:reset"), icon = Icons.Default.Close)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s), contentPadding = PaddingValues(top = Space.s, bottom = Space.xs)) {
                        items(GridSort.entries, key = { it.name }) { s ->
                            SokoulChip(s.label, { vm.sort.value = s; scope.launch { grid.scrollToItem(0) } }, Modifier.focusMemory(memory, "sort:" + s.name), selected = s == sort)
                        }
                    }
                }
            }
            when {
                ui.loading && ui.works.isEmpty() -> items(12) { Box(Modifier.fillMaxWidth().height(l.gridCell * 1.5f).shimmer()) }
                ui.works.isEmpty() -> item("empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Default.Tune, "Aucun titre ne correspond",
                        if (ui.coverage?.complete == false) "L'analyse du catalogue n'est pas terminée : réessayez plus tard, ou retirez un filtre."
                        else "Retirez un filtre pour élargir la sélection.",
                        Modifier.height(300.dp),
                    )
                }
                else -> items(ui.works, key = { it.uid }) { w ->
                    WorkCard(w, { nav.openWork(w) }, Modifier.fillMaxWidth().focusMemory(memory, w.uid), width = l.gridCell * 2)
                }
            }
        }
    }

    // TV : focus sur le premier filtre à l'ouverture, sur la carte d'où l'on vient au retour
    LaunchedEffect(Unit) {
        repeat(20) {
            withFrameNanos { }
            if (memory.restore()) return@LaunchedEffect
            if (memory.last == null && runCatching { memory.requester("f:genre").requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    when (dialog) {
        "genre" -> Choice("Genre", genres.entries.map { (id, name) -> name to id }, f.genre, { dialog = null }) { update(f.copy(genre = it)) }
        "decade" -> Choice("Décennie", Decades.map { decadeLabel(it) to it }, f.decade, { dialog = null }) { update(f.copy(decade = it)) }
        "rating" -> Choice("Note TMDB", Ratings.map { rating(it) to it }, f.minRating, { dialog = null }) { update(f.copy(minRating = it)) }
        "duration" -> Choice("Durée", DurationBand.entries.map { it.label to it }, f.duration, { dialog = null }) { update(f.copy(duration = it)) }
        "finish" -> Choice("Fini avant", finishHours.map { hourLabel(it) to it }, f.finishBy, { dialog = null }) { update(f.copy(finishBy = it)) }
        "lang" -> Choice("Langue originale", OriginalLanguage.entries.map { it.label to it.code }, f.language, { dialog = null }) { update(f.copy(language = it)) }
    }
}

internal fun hourLabel(h: Int) = if (h >= 24) "minuit" else "${h} h"

internal fun decadeLabel(d: Int) = if (d >= 2000) "Années $d" else "Années ${d % 100}"

@Composable
private fun FilterChip(label: String, value: String?, memory: com.sokoul.iptv.ui.components.FocusMemory, key: String, onClick: () -> Unit) {
    SokoulChip(if (value == null) label else "$label : $value", onClick, Modifier.focusMemory(memory, key), selected = value != null)
}

/** Choix d'une valeur de filtre ; « Tous » efface le critère. */
@Composable
private fun <T> Choice(title: String, options: List<Pair<String, T>>, current: T?, onDismiss: () -> Unit, onPick: (T?) -> Unit) {
    OptionsDialog(
        title,
        listOf(MenuOption("Tous", selected = current == null) { onPick(null) }) +
            options.map { (label, v) -> MenuOption(label, selected = v == current) { onPick(v) } },
        onDismiss = onDismiss,
    )
}
