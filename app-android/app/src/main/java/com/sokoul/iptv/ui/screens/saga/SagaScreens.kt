package com.sokoul.iptv.ui.screens.saga

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import com.sokoul.iptv.data.repo.SagaLogic
import com.sokoul.iptv.data.repo.SagaPage
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.LocalTopBar
import com.sokoul.iptv.ui.TopTabsHeight
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.SagaTile
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.focusOnTop
import com.sokoul.iptv.ui.components.rememberBreathing
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.components.revealLine
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.nav.SagaRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.screens.browse.openWork
import com.sokoul.iptv.ui.screens.vod.Pill
import com.sokoul.iptv.ui.screens.vod.entrance
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Motion
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
            // logos ensuite : le hub s'affiche sans les attendre
            if (list.any { it.logo == null && it.logoOf != null }) {
                val withLogos = try { repo.sagaLogos(p.id, list) } catch (e: CancellationException) { throw e } catch (e: Exception) { Timber.w(e, "Logos des sagas"); list }
                if (withLogos !== list) ui.value = ui.value.copy(sagas = withLogos)
            }
        }
    }
}

/**
 * Onglet « Sagas » : en haut, une bannière sur la saga focalisée (fond, logo, faits, progression) ;
 * dessous, toutes les sagas du catalogue, les grandes franchises d'abord.
 */
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
    // saga mise en avant : lue seulement par la bannière (le focus ne recompose pas la grille)
    val spotId = remember { mutableStateOf<Int?>(null) }
    val banner = l.isTv && ui.sagas.isNotEmpty()
    val bannerHeight = (l.heightDp * 0.46f).dp

    Column(Modifier.fillMaxSize()) {
        if (banner) {
            SagaBanner(spotId, ui.sagas, bannerHeight)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(l.landscape),
            state = grid,
            // pas de fond : celui de la fenêtre suffit (une passe plein écran de moins à chaque image)
            modifier = Modifier.fillMaxWidth().weight(1f),
            // marge du haut : la tuile agrandie, son halo et son liseré ne sont jamais coupés
            contentPadding = PaddingValues(start = l.margin, end = l.margin, top = if (banner) 18.dp else 0.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(l.gutter),
            verticalArrangement = Arrangement.spacedBy(l.gutter + 6.dp),
        ) {
            if (!banner) item("head", span = { GridItemSpan(maxLineSpan) }) {
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
                        s, { nav.navigateOnce(SagaRoute(s.id)) }, onFocus = { spotId.value = s.id },
                        modifier = Modifier.focusMemory(memory, "s:${s.id}"), width = Dp.Unspecified,
                    )
                }
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

/**
 * Bannière du hub : fond de la saga focalisée à droite, logo (ou nom), faits et progression à gauche.
 * Change 250 ms après que le curseur s'est posé (pas à chaque tuile traversée), en fondu dans une texture en cache.
 */
@Composable
private fun SagaBanner(spotId: State<Int?>, sagas: List<SagaCard>, height: Dp) {
    val l = LocalLayout.current
    var shown by remember { mutableStateOf(sagas.first()) }
    LaunchedEffect(sagas) {
        snapshotFlow { spotId.value }.collect { id ->
            val s = sagas.firstOrNull { it.id == id } ?: sagas.first()
            if (s.id != shown.id) delay(250)
            shown = s
        }
    }
    Box(Modifier.fillMaxWidth().height(height).background(SokoulColors.Background)) {
        AnimatedContent(shown, transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.MEDIUM)) }, contentKey = { it.id }, label = "saga-banner") { s ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    NetImage(tmdbSized(s.backdrop, "w1280"), null, Modifier.fillMaxWidth(0.66f).fillMaxHeight().align(Alignment.TopEnd), alignment = Alignment.TopCenter)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.30f to SokoulColors.Background, 0.55f to SokoulColors.bg(0.7f), 0.80f to Color.Transparent)))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to SokoulColors.Background)))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = l.margin, bottom = 8.dp).widthIn(max = l.heroTextWidth)) {
                    Text("SAGA", style = Type.Overline, color = SokoulColors.Gold)
                    // bannière basse : logo contenu, faits et progression sur une ligne (rien ne remonte sous les onglets)
                    SagaTitle(s.name, s.logo, Modifier.padding(top = 4.dp), maxLogo = 60.dp)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val facts = listOfNotNull(s.facts?.line()?.takeIf { it.isNotEmpty() }, "${s.count} au catalogue")
                        Text(facts.joinToString(" · "), style = Type.Meta, color = SokoulColors.TextSecondary, maxLines = 1)
                        val total = s.facts?.films ?: 0
                        if (s.seen > 0) Text(
                            if (total > 0 && s.seen >= total) "  ·  Saga terminée" else "  ·  Vu${if (s.seen > 1) "s" else ""} : ${s.seen}" + if (total > 0) "/$total" else "",
                            style = Type.Meta, color = SokoulColors.Accent, fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                    }
                    s.overview?.let {
                        Text(it, Modifier.padding(top = 8.dp).widthIn(max = 560.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Logo de la saga, sinon son nom en grand. */
@Composable
private fun SagaTitle(name: String, logo: String?, modifier: Modifier = Modifier, maxLogo: Dp = 100.dp) {
    if (logo != null) {
        NetImage(
            tmdbSized(logo, "w500"), name, modifier.widthIn(max = 440.dp).heightIn(max = maxLogo), ContentScale.Fit, alignment = Alignment.BottomStart,
        ) { Text(name, style = Type.ScreenTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    } else Text(name, modifier, style = Type.ScreenTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
 * Page « musée » d'une saga :
 * - entrée animée (une fois) : le fond apparaît en dézoomant, puis le logo, les faits et la frise ;
 * - le fond suit le film focalisé ;
 * - « Commencer » / « Continuer la saga » ouvre le prochain film non vu ;
 * - vraie frise : chapitres datés reliés par une ligne, point plein = vu, anneau = à voir, gris = hors catalogue ;
 * - séries du même univers, visages récurrents et coulisses.
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
    // film focalisé : lu seulement par le fond et le panneau d'infos
    val focusedFilm = remember { mutableStateOf<Work?>(null) }
    // l'entrée ne se joue qu'une fois (pas au retour d'une fiche)
    val entered = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(page != null) { if (page != null) { delay(1200); entered.value = true } }

    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        if (page != null) SagaBackdrop(page, focusedFilm, entered)
        when {
            ui.loading -> SagaSkeleton()
            ui.noKey -> EmptyState(Icons.Default.Key, "Clé TMDB nécessaire", "Les sagas s'appuient sur TheMovieDB : ajoutez une clé dans Réglages.")
            page == null -> EmptyState(Icons.Default.Refresh, "Impossible de charger cette saga", "Vérifiez la connexion, puis réessayez.") {
                AppButton("Réessayer", vm::retry, primary = true, icon = Icons.Default.Refresh, modifier = Modifier.focusMemory(memory, "retry"))
            }
            else -> {
                val films = if (story) page.story ?: page.films else page.films
                val next = SagaLogic.next(films, page.seen)
                LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 56.dp)) {
                    item("head") {
                        SagaHeader(page, story, next, memory, entered, onStory = { vm.story.value = it }, onNext = { w -> nav.openWork(w) })
                    }
                    item("films") {
                        // changement d'ordre : les cartes glissent à leur nouvelle place, la frise repart du début
                        val row = rememberLazyListState()
                        LaunchedEffect(story) { row.scrollToItem(0) }
                        Column(Modifier.entrance(3, entered)) {
                            FilmInfo(focusedFilm, page)
                            LazyRow(
                                state = row,
                                contentPadding = PaddingValues(start = l.margin, end = l.margin, top = 4.dp, bottom = 26.dp),
                                horizontalArrangement = Arrangement.spacedBy(l.gutter),
                            ) {
                                itemsIndexed(films, key = { _, w -> w.uid }) { i, w ->
                                    TimelineStop(
                                        w, i, last = i == films.lastIndex, seen = w.uid in page.seen, gutter = l.gutter,
                                        modifier = Modifier.animateItem().entrance(4 + minOf(i, 6), entered),
                                    ) {
                                        WorkCard(
                                            w, { if (w.available) nav.openWork(w) else offFor = w },
                                            Modifier.focusMemory(memory, "f:${w.uid}"), showPlatform = false, reveal = false,
                                            onFocus = { focusedFilm.value = w },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (page.series.isNotEmpty()) item("series") {
                        Section("Séries du même univers", null)
                        LazyRow(contentPadding = PaddingValues(start = l.margin, end = l.margin, top = 8.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
                            items(page.series, key = { it.uid }) { w ->
                                WorkCard(w, { if (w.available) nav.openWork(w) else offFor = w }, Modifier.focusMemory(memory, "s:${w.uid}"), showPlatform = false)
                            }
                        }
                    }
                    if (page.faces.isNotEmpty()) item("faces") {
                        Section("Visages de la saga", "Les acteurs qui reviennent d'un film à l'autre")
                        LazyRow(contentPadding = PaddingValues(start = l.margin, end = l.margin, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
                            items(page.faces, key = { it.id }) { f -> FaceCard(f, Modifier.focusMemory(memory, "p:${f.id}")) }
                        }
                    }
                    if (page.crew.isNotEmpty()) item("crew") {
                        Section("Les coulisses", null)
                        LazyRow(contentPadding = PaddingValues(start = l.margin, end = l.margin, top = 8.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
                            items(page.crew, key = { it.label }) { c -> CrewCard(c, Modifier.focusMemory(memory, "c:${c.label}")) }
                        }
                    }
                }
            }
        }
        if (l.isTouch) {
            Box(
                Modifier.statusBarsPadding().padding(8.dp).size(44.dp).clip(Radius.Round).background(SokoulColors.black(0.5f))
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

    // TV : focus sur « Continuer la saga » (sinon le premier film du catalogue) ; au retour, sur l'élément d'où l'on vient
    LaunchedEffect(ui.loading, page?.id) {
        if (!l.isTv || ui.loading) return@LaunchedEffect
        repeat(8) {
            delay(80)
            if (memory.restore()) return@LaunchedEffect
            val first = page?.let { p ->
                if (SagaLogic.next(p.films, p.seen) != null) "go"
                else (p.films.firstOrNull { it.available } ?: p.films.firstOrNull())?.let { "f:${it.uid}" }
            } ?: if (ui.failed) "retry" else return@LaunchedEffect
            if (runCatching { memory.requester(first).requestFocus(FocusDirection.Enter) }.getOrDefault(false)) {
                delay(16); list.scrollToItem(0)
                return@LaunchedEffect
            }
        }
    }
}

/**
 * Fond de la page : l'image du film focalisé (sinon celle de la saga), à droite, sous deux dégradés.
 * Entrée : fondu et lent dézoom (1,08 → 1, 1,2 s). Changement de film : fondu de 300 ms, 300 ms après que le
 * curseur s'est posé, dans une texture en cache (pas de calque plein écran recalculé à chaque image).
 */
@Composable
private fun SagaBackdrop(page: SagaPage, focused: State<Work?>, entered: MutableState<Boolean>) {
    val reduced = LocalReducedMotion.current
    val intro = remember { Animatable(if (entered.value || reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (intro.value < 1f) intro.animateTo(1f, tween(1200, easing = Motion.Decelerate)) }
    var image by remember { mutableStateOf(page.backdrop) }
    LaunchedEffect(page.id) {
        snapshotFlow { focused.value?.backdrop ?: page.backdrop }.distinctUntilChanged().collect { b ->
            if (b != image) delay(300)
            image = b
        }
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        val v = intro.value
        alpha = v
        val s = 1.08f - 0.08f * v
        scaleX = s; scaleY = s
    }) {
        AnimatedContent(image, transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.MEDIUM)) }, label = "saga-bg") { img ->
            Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                // w780 : fond sous deux dégradés, changé à chaque film de la frise (décodage d'un w1280 trop coûteux)
                if (img != null) NetImage(tmdbSized(img, "w780"), null, Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.78f).align(Alignment.TopEnd), alignment = Alignment.TopCenter)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.22f to SokoulColors.Background, 0.5f to SokoulColors.bg(0.72f), 0.85f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.30f to Color.Transparent, 0.72f to SokoulColors.Background)))
            }
        }
    }
}

@Composable
private fun SagaHeader(
    page: SagaPage, story: Boolean, next: Work?, memory: FocusMemory, entered: MutableState<Boolean>,
    onStory: (Boolean) -> Unit, onNext: (Work) -> Unit,
) {
    val l = LocalLayout.current
    Column(
        Modifier.statusBarsPadding().padding(start = l.margin, end = l.margin, top = l.safeTop + 28.dp),
    ) {
        Text("SAGA", Modifier.entrance(0, entered), style = Type.Overline, color = SokoulColors.Gold)
        SagaTitle(page.name, page.logo, Modifier.padding(top = 6.dp).entrance(0, entered), maxLogo = 110.dp)
        Column(Modifier.entrance(1, entered).widthIn(max = l.heroTextWidth)) {
            val facts = listOfNotNull(
                page.facts.line().takeIf { it.isNotEmpty() },
                "${page.inCatalog} au catalogue",
                page.series.takeIf { it.isNotEmpty() }?.let { if (it.size > 1) "${it.size} séries" else "1 série" },
            )
            Text(facts.joinToString(" · "), Modifier.padding(top = 12.dp), color = SokoulColors.TextSecondary, style = Type.Meta)
            if (page.seen.isNotEmpty()) {
                Text(
                    if (next == null) "Vous avez vu tous les films disponibles" else "Vous en avez vu ${page.seen.size}",
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.Accent, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            Modifier.padding(top = 16.dp).entrance(2, entered),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (next != null) {
                AppButton(
                    if (page.seen.isEmpty()) "Commencer la saga" else "Continuer la saga", { onNext(next) },
                    Modifier.focusMemory(memory, "go"), icon = Icons.Default.PlayArrow, primary = true,
                )
            }
            if (page.story != null) {
                Pill("Ordre de sortie", selected = !story, modifier = Modifier.focusMemory(memory, "o:release")) { onStory(false) }
                Pill("Ordre du récit", selected = story, modifier = Modifier.focusMemory(memory, "o:story")) { onStory(true) }
            }
            if (next != null) {
                Text(
                    "Prochain : ${next.title}" + (next.year?.let { " ($it)" } ?: ""), Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Panneau du film focalisé, au-dessus de la frise (sinon le résumé de la saga). Hauteur fixe : la frise ne saute pas. */
@Composable
private fun FilmInfo(focused: State<Work?>, page: SagaPage) {
    val l = LocalLayout.current
    val w = focused.value
    Column(Modifier.padding(start = l.margin, end = l.margin, top = 26.dp).height(92.dp).widthIn(max = 720.dp)) {
        if (w == null) {
            Text("Les films", style = Type.Section)
            page.overview?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.title, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                val line = w.revealLine()
                if (line.isNotEmpty()) Text("  ·  $line", style = Type.Meta, color = SokoulColors.TextSecondary, maxLines = 1)
                if (!w.available) Text("  ·  HORS CATALOGUE", style = Type.Overline, color = SokoulColors.TextMuted)
            }
            (w.overview ?: "").takeIf { it.isNotBlank() }?.let {
                Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Étape de la frise : « CHAPITRE 3 · 1983 » au-dessus d'un point relié aux voisins par une ligne,
 * puis l'affiche. Point plein (accent) = vu ; anneau = à voir ; gris = hors catalogue.
 */
@Composable
private fun TimelineStop(w: Work, index: Int, last: Boolean, seen: Boolean, gutter: Dp, modifier: Modifier = Modifier, card: @Composable () -> Unit) {
    val l = LocalLayout.current
    Column(modifier.width(l.poster).focusOnTop()) {
        // « 03  1983 » : numéro de chapitre (accent) et année, sur une ligne qui tient dans la largeur d'une affiche
        Row(Modifier.padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                (index + 1).toString().padStart(2, '0'), style = Type.Overline,
                color = if (w.available) SokoulColors.Gold else SokoulColors.TextMuted, maxLines = 1,
            )
            Text(
                w.year?.toString() ?: "À venir", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = if (w.available) SokoulColors.TextPrimary else SokoulColors.TextMuted, maxLines = 1,
            )
        }
        val lineColor = SokoulColors.white(0.18f)
        val dot = when {
            seen -> SokoulColors.Accent
            w.available -> Color.White
            else -> SokoulColors.white(0.3f)
        }
        Box(
            Modifier.fillMaxWidth().height(14.dp).padding(bottom = 4.dp).drawBehind {
                val y = size.height / 2f
                val r = 5.dp.toPx()
                val cx = r + 2.dp.toPx()
                // ligne vers le chapitre suivant (traverse la gouttière)
                if (!last) drawLine(lineColor, Offset(cx, y), Offset(size.width + gutter.toPx() + cx, y), strokeWidth = 2.dp.toPx())
                if (seen) drawCircle(dot, r, Offset(cx, y))
                else {
                    drawCircle(SokoulColors.Background, r, Offset(cx, y))
                    drawCircle(dot, r - 1.dp.toPx(), Offset(cx, y), style = Stroke(2.dp.toPx()))
                }
            },
        )
        card()
    }
}

@Composable
private fun Section(title: String, subtitle: String?) {
    val l = LocalLayout.current
    Column(Modifier.padding(start = l.margin, end = l.margin, top = 20.dp, bottom = 4.dp)) {
        Text(title, style = Type.Section)
        if (subtitle != null) Text(subtitle, Modifier.padding(top = 2.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FaceCard(f: SagaFace, modifier: Modifier = Modifier) {
    val size = 112.dp
    Column(modifier.width(size + 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // focalisable pour faire défiler la rangée à la télécommande ; pas d'action. Visage : seule forme ronde.
        FocusSurface(onClick = {}, shape = Radius.Round) { focused ->
            val zoom = rememberBreathing(focused, to = 1.08f)
            NetImage(tmdbSized(f.photo, "w185"), f.name, Modifier.size(size).graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }) {
                Box(Modifier.size(size).background(SokoulColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Text(f.name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), style = Type.Section)
                }
            }
        }
        Text(f.name, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text("Apparaît dans ${f.films} films", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CrewCard(c: SagaCrew, modifier: Modifier = Modifier) {
    FocusSurface(onClick = {}, modifier = modifier.width(260.dp), style = FocusStyle.Card) { _ ->
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(c.label, style = Type.Overline, color = SokoulColors.Gold)
            Spacer(Modifier.height(8.dp))
            c.names.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun SagaSkeleton() {
    val l = LocalLayout.current
    Column(Modifier.fillMaxSize().padding(start = l.margin, top = l.safeTop + 28.dp)) {
        Box(Modifier.width(320.dp).height(64.dp).clip(Radius.Tiny).shimmer())
        Spacer(Modifier.height(14.dp))
        Box(Modifier.width(520.dp).height(40.dp).clip(Radius.Tiny).shimmer())
        Spacer(Modifier.height(60.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
            repeat(6) { Box(Modifier.width(l.poster).height(l.poster * 1.5f).clip(Radius.Card).shimmer()) }
        }
    }
}
