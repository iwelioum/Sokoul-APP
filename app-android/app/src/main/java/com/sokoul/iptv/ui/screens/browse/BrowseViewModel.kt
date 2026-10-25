package com.sokoul.iptv.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.repo.BrowseKind
import com.sokoul.iptv.data.repo.BrowsePage
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.BrowseRow
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.ChannelTile
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.EpgRepository
import com.sokoul.iptv.data.repo.GridSpec
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.RowStyle
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.ui.ParentalGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BrowseUi(
    val loading: Boolean = true,
    val playlist: PlaylistEntity? = null,
    val hero: List<Work> = emptyList(),
    /** Le héros vient du classement de la semaine (« N° 1 des films… »). */
    val heroRanked: Boolean = false,
    val rows: List<BrowseRow> = emptyList(),
    /** La page n'a pas pu être construite (aucune page précédente à montrer). */
    val failed: Boolean = false,
)

/**
 * Écran éditorial (Accueil, Films, Séries) : page construite par [BrowseRepository] (mise en cache),
 * rangées personnelles en direct (reprise, ma liste, chaînes), métadonnées TMDB ajoutées au fil de l'eau.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
abstract class BrowseViewModel(
    val kind: BrowseKind,
    private val playlists: PlaylistRepository,
    private val repo: BrowseRepository,
    private val enrich: EnrichmentRepository,
    private val vod: VodRepository,
    private val library: LibraryRepository,
    private val catalog: CatalogRepository,
    private val epg: EpgRepository,
    private val parental: ParentalGate,
) : ViewModel() {
    private val types: Set<ContentType> = when (kind) {
        BrowseKind.HOME -> setOf(ContentType.MOVIE, ContentType.SERIES)
        BrowseKind.MOVIES -> setOf(ContentType.MOVIE)
        BrowseKind.SERIES -> setOf(ContentType.SERIES)
    }

    private val page = MutableStateFlow<BrowsePage?>(null)
    private val playlist: StateFlow<PlaylistEntity?> = playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val resolved = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)

    private val live: Flow<List<BrowseRow>> = playlist.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else liveRows(p.id) }

    val ui: StateFlow<BrowseUi> = combine(playlist, page, live, resolved, failed) { p, pg, lv, done, err ->
        BrowseUi(
            loading = pg == null && !err && (p != null || !done),
            failed = err && pg == null,
            playlist = p,
            hero = pg?.hero.orEmpty(),
            heroRanked = pg?.hero?.firstOrNull()?.let { h -> pg.rows.any { it.style == RowStyle.TOP10 && it.works.firstOrNull()?.uid == h.uid } } == true,
            rows = lv + pg?.rows.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrowseUi())

    /** Relance manuelle (« Réessayer ») : reconstruit la page sans passer par le cache. */
    private val reload = MutableStateFlow(0)

    fun retry() { reload.value++ }

    init {
        viewModelScope.launch {
            var shownFor: Long? = null
            combine(playlists.active, parental.unlocked, reload) { p, u, r -> Triple(p, u, r) }
                .distinctUntilChanged { a, b ->
                    a.first?.id == b.first?.id && a.first?.lastSync == b.first?.lastSync && a.second == b.second && a.third == b.third
                }
                .collectLatest { (p, _, r) ->
                    resolved.value = true
                    failed.value = false
                    // même playlist (synchro, contrôle parental) : l'ancienne page reste affichée jusqu'à la nouvelle
                    if (p?.id != shownFor) page.value = null
                    shownFor = p?.id
                    if (p == null) return@collectLatest
                    var base = try {
                        repo.page(p, kind, force = r > 0, onPartial = { partial -> page.value = decorate(p.id, partial) })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Page $kind")
                        // une page déjà affichée vaut mieux qu'un écran d'erreur
                        if (page.value == null) failed.value = true
                        return@collectLatest
                    }
                    page.value = decorate(p.id, base)
                    enrich.request(p.id, priority(base))
                    var lastSagas = System.currentTimeMillis()
                    // nouvelles métadonnées TMDB → cartes redécorées (fonds, logos, titres français)
                    enrich.updates.drop(1).debounce(900).collect {
                        if (kind != BrowseKind.SERIES && System.currentTimeMillis() - lastSagas > 45_000) {
                            lastSagas = System.currentTimeMillis()
                            val sagas = runCatching { repo.sagas(p.id) }.getOrDefault(emptyList())
                            if (sagas.isNotEmpty()) base = base.copy(rows = base.rows.map { if (it.style == RowStyle.SAGAS) it.copy(sagas = sagas) else it })
                        }
                        page.value = decorate(p.id, base)
                    }
                }
        }
    }

    /** Ordre d'enrichissement : héros, puis les premières cartes de chaque rangée. */
    private fun priority(pg: BrowsePage): List<Work> =
        pg.hero + pg.rows.flatMap { it.works.take(8) } + pg.rows.flatMap { it.works.drop(8).take(8) }

    private suspend fun decorate(pid: Long, pg: BrowsePage): BrowsePage {
        val all = (pg.hero + pg.rows.flatMap { it.works }).distinctBy { it.uid }
        val byUid = enrich.decorate(pid, all).associateBy { it.uid }
        fun List<Work>.d() = map { byUid[it.uid] ?: it }
        return pg.copy(hero = pg.hero.d(), rows = pg.rows.map { it.copy(works = it.works.d()) })
    }

    // --- rangées personnelles -----------------------------------------------------------------

    private fun liveRows(pid: Long): Flow<List<BrowseRow>> {
        val cw: Flow<BrowseRow?> = library.continueWatching(pid).mapLatest { list ->
            val mine = vod.dedupeProgress(pid, list.filter { it.type in types }, { it.type }, { if (it.type == ContentType.SERIES) it.parentId ?: it.itemId else it.itemId })
            val works = repo.continueWorks(pid, mine)
            enrich.request(pid, works)
            if (works.isEmpty()) null else BrowseRow("continue", "Reprendre la lecture", "Là où vous vous étiez arrêté.", RowStyle.CONTINUE, works)
        }.onStart { emit(null) }

        val favMovies: Flow<List<Work>> = if (ContentType.MOVIE in types) vod.movieCards(pid, catalog.favoriteMovies(pid)).map { l -> l.map { it.item.toWork(it.versions) } } else flowOf(emptyList())
        val favSeries: Flow<List<Work>> = if (ContentType.SERIES in types) vod.seriesCards(pid, catalog.favoriteSeries(pid)).map { l -> l.map { it.item.toWork(it.versions) } } else flowOf(emptyList())
        val myList: Flow<BrowseRow?> = combine(favMovies, favSeries, enrich.updates.debounce(900).onStart { emit(0) }) { m, s, _ -> m + s }
            .mapLatest { works ->
                if (works.isEmpty()) return@mapLatest null
                enrich.request(pid, works)
                val type = types.singleOrNull()
                BrowseRow(
                    "mylist", "Ma liste", "Les titres que vous avez mis de côté.", RowStyle.POSTER, enrich.decorate(pid, works),
                    seeAll = type?.let { GridSpec.Favorites(it).encode() },
                )
            }.onStart { emit(null) }

        val channels: Flow<BrowseRow?> = if (kind != BrowseKind.HOME) flowOf(null) else
            catalog.channels(pid, SpecialCategory.FAVORITES).mapLatest { list ->
                if (list.isEmpty()) return@mapLatest null
                val now = epg.nowFor(pid, list)
                BrowseRow("channels", "Vos chaînes en direct", "Ce qui passe en ce moment sur vos favorites.", RowStyle.CHANNELS,
                    channels = list.take(30).map { ChannelTile(it, now[it.id]) })
            }.onStart { emit(null) }

        return combine(cw, channels, myList) { a, b, c -> listOfNotNull(a, b, c) }
    }

    // --- actions ---------------------------------------------------------------------------------

    private val actions = WorkActions(catalog, vod)

    /** « Lecture » : meilleure version d'un film, ou reprise de la série (sinon sa fiche). */
    suspend fun playRoute(w: Work): Any = actions.playRoute(playlist.value?.id, w)

    fun toggleList(w: Work) = viewModelScope.launch {
        val pid = playlist.value?.id ?: return@launch
        actions.toggleList(pid, w)
    }
}

@HiltViewModel
class HomeBrowseViewModel @Inject constructor(
    playlists: PlaylistRepository, repo: BrowseRepository, enrich: EnrichmentRepository, vod: VodRepository,
    library: LibraryRepository, catalog: CatalogRepository, epg: EpgRepository, parental: ParentalGate,
) : BrowseViewModel(BrowseKind.HOME, playlists, repo, enrich, vod, library, catalog, epg, parental)

@HiltViewModel
class MoviesBrowseViewModel @Inject constructor(
    playlists: PlaylistRepository, repo: BrowseRepository, enrich: EnrichmentRepository, vod: VodRepository,
    library: LibraryRepository, catalog: CatalogRepository, epg: EpgRepository, parental: ParentalGate,
) : BrowseViewModel(BrowseKind.MOVIES, playlists, repo, enrich, vod, library, catalog, epg, parental)

@HiltViewModel
class SeriesBrowseViewModel @Inject constructor(
    playlists: PlaylistRepository, repo: BrowseRepository, enrich: EnrichmentRepository, vod: VodRepository,
    library: LibraryRepository, catalog: CatalogRepository, epg: EpgRepository, parental: ParentalGate,
) : BrowseViewModel(BrowseKind.SERIES, playlists, repo, enrich, vod, library, catalog, epg, parental)
