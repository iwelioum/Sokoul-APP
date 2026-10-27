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
import kotlinx.coroutines.flow.flow
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
    /** Rangées suivantes en cours de chargement (fin de page). */
    val loadingMore: Boolean = false,
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
    private val cloud: com.sokoul.iptv.data.debrid.DebridCloud,
    private val profileRepo: com.sokoul.iptv.data.repo.ProfileRepository,
) : ViewModel() {
    /** Profil en cours : en changer reconstruit la page (liste, reprise, profil Enfant). */
    private val profileId = profileRepo.current.map { it?.id }.distinctUntilChanged()
    private val types: Set<ContentType> = when (kind) {
        BrowseKind.HOME -> setOf(ContentType.MOVIE, ContentType.SERIES)
        BrowseKind.MOVIES -> setOf(ContentType.MOVIE)
        BrowseKind.SERIES -> setOf(ContentType.SERIES)
    }

    private val page = MutableStateFlow<BrowsePage?>(null)
    private val playlist: StateFlow<PlaylistEntity?> = playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val resolved = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    /** Rangées chargées au fil du défilement, et ce qui reste à charger. */
    private val extra = MutableStateFlow<List<BrowseRow>>(emptyList())
    private val loadingMore = MutableStateFlow(false)
    @Volatile private var more: List<String> = emptyList()

    private val live: Flow<List<BrowseRow>> = combine(playlist, profileId) { p, _ -> p }.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else liveRows(p.id) }

    private val pageRows = combine(page, extra, loadingMore) { pg, ex, lm -> Triple(pg, ex, lm) }

    val ui: StateFlow<BrowseUi> = combine(playlist, pageRows, live, resolved, failed) { p, (pg, ex, lm), lv, done, err ->
        BrowseUi(
            loading = pg == null && !err && (p != null || !done),
            failed = err && pg == null,
            playlist = p,
            hero = pg?.hero.orEmpty(),
            heroRanked = pg?.hero?.firstOrNull()?.let { h -> pg.rows.any { it.style == RowStyle.TOP10 && it.works.firstOrNull()?.uid == h.uid } } == true,
            rows = lv + withExtra(pg?.rows.orEmpty(), ex),
            loadingMore = lm,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrowseUi())

    /** Relance manuelle (« Réessayer ») : reconstruit la page sans passer par le cache. */
    private val reload = MutableStateFlow(0)

    fun retry() { reload.value++ }

    /** Page affichée (avant décoration) : base des redécorations TMDB et du recalcul au retour. */
    @Volatile private var current: BrowsePage? = null
    private var refreshing: kotlinx.coroutines.Job? = null

    /**
     * Retour sur l'écran : si la page n'est plus juste (30 min, nouveau jour, une lecture, Ma liste modifiée), elle est
     * recalculée en arrière-plan et remplacée sans écran vide (rangées gardées par leur clé : le focus reste).
     */
    fun refreshIfStale() {
        val p = playlist.value ?: return
        if (current == null || refreshing?.isActive == true) return
        refreshing = viewModelScope.launch {
            val why = runCatching { repo.staleReason(p, kind) }.getOrNull() ?: return@launch
            Timber.i("Page $kind recalculée : $why")
            val built = try {
                repo.page(p, kind, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Recalcul $kind"); return@launch
            }
            if (playlist.value?.id != p.id || built.partial) return@launch
            current = built
            page.value = decorate(p.id, built)
            enrich.request(p.id, priority(built))
        }
    }

    init {
        viewModelScope.launch {
            var shownFor: Pair<Long?, Long?>? = null
            combine(playlists.active, parental.unlocked, reload, profileId) { p, u, r, who -> Quad(p, u, r, who) }
                .distinctUntilChanged { a, b ->
                    a.p?.id == b.p?.id && a.p?.lastSync == b.p?.lastSync && a.unlocked == b.unlocked && a.reload == b.reload && a.profile == b.profile
                }
                .collectLatest { (p, _, r, who) ->
                    resolved.value = true
                    failed.value = false
                    // même playlist (synchro, contrôle parental) : l'ancienne page reste affichée jusqu'à la nouvelle
                    // autre playlist ou autre profil : on n'affiche pas la page précédente
                    if ((p?.id to who) != shownFor) { page.value = null; extra.value = emptyList() }
                    shownFor = p?.id to who
                    if (p == null) return@collectLatest
                    val built = try {
                        repo.page(p, kind, force = r > 0, onPartial = { partial -> page.value = decorate(p.id, partial) })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Page $kind")
                        // une page déjà affichée vaut mieux qu'un écran d'erreur
                        if (page.value == null) failed.value = true
                        return@collectLatest
                    }
                    current = built
                    page.value = decorate(p.id, built)
                    // nouvelle page : les rangées chargées au défilement repartent de zéro
                    extra.value = emptyList()
                    more = built.more
                    enrich.request(p.id, priority(built))
                    var lastSagas = System.currentTimeMillis()
                    // nouvelles métadonnées TMDB → cartes redécorées (fonds, logos, titres français)
                    enrich.updates.drop(1).debounce(4_000).collect {
                        var base = current ?: return@collect
                        if (kind != BrowseKind.SERIES && System.currentTimeMillis() - lastSagas > 300_000) {
                            lastSagas = System.currentTimeMillis()
                            val sagas = runCatching { repo.sagas(p.id) }.getOrDefault(emptyList())
                            if (sagas.isNotEmpty()) { base = base.copy(rows = base.rows.map { if (it.style == RowStyle.SAGAS) it.copy(sagas = sagas) else it }); current = base }
                        }
                        page.value = decorate(p.id, base)
                        if (extra.value.isNotEmpty()) extra.value = decorateRows(p.id, extra.value)
                    }
                }
        }
    }

    /**
     * Fin de page atteinte : les [BrowseRepository.MORE_BATCH] rangées éditoriales suivantes (une seule demande à la fois).
     * Les œuvres déjà montrées deux fois ne reviennent pas.
     */
    fun loadMore() {
        val pid = playlist.value?.id ?: return
        if (loadingMore.value || more.isEmpty()) return
        loadingMore.value = true
        viewModelScope.launch {
            try {
                val shown = (page.value?.rows.orEmpty() + extra.value).flatMap { r -> r.works.map { it.uid } }
                val (rows, rest) = repo.moreRows(pid, more, shown)
                more = rest
                if (rows.isNotEmpty()) {
                    extra.value = extra.value + decorateRows(pid, rows)
                    enrich.request(pid, rows.flatMap { it.works.take(8) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Rangées suivantes")
            } finally {
                loadingMore.value = false
            }
        }
    }

    /** Rangées chargées au défilement : avant « Explorer par catégorie », qui reste la dernière. */
    private fun withExtra(rows: List<BrowseRow>, ex: List<BrowseRow>): List<BrowseRow> {
        if (ex.isEmpty()) return rows
        val cats = rows.indexOfFirst { it.style == RowStyle.CATEGORIES }
        return if (cats < 0) rows + ex else rows.subList(0, cats) + ex + rows.subList(cats, rows.size)
    }

    private suspend fun decorateRows(pid: Long, rows: List<BrowseRow>): List<BrowseRow> {
        val byUid = enrich.decorate(pid, rows.flatMap { it.works }.distinctBy { it.uid }).associateBy { it.uid }
        return rows.map { r -> r.copy(works = r.works.map { byUid[it.uid] ?: it }) }.kidsOnly()
    }

    /** Profil Enfant : sans les œuvres classées 12 ans et plus (rangées vidées retirées). */
    private suspend fun List<BrowseRow>.kidsOnly(): List<BrowseRow> =
        if (!profileRepo.isKids()) this
        else map { r -> r.copy(works = r.works.filter(com.sokoul.iptv.data.repo.KidsRating::allowed)) }.filter { it.works.isNotEmpty() || it.channels.isNotEmpty() || it.sagas.isNotEmpty() || it.platforms.isNotEmpty() }

    /** Ordre d'enrichissement : héros, puis les premières cartes de chaque rangée. */
    private fun priority(pg: BrowsePage): List<Work> =
        pg.hero + pg.rows.flatMap { it.works.take(8) } + pg.rows.flatMap { it.works.drop(8).take(8) }

    private suspend fun decorate(pid: Long, pg: BrowsePage): BrowsePage {
        val all = (pg.hero + pg.rows.flatMap { it.works }).distinctBy { it.uid }
        val byUid = enrich.decorate(pid, all).associateBy { it.uid }
        fun List<Work>.d() = map { byUid[it.uid] ?: it }
        val kids = profileRepo.isKids()
        return pg.copy(
            hero = pg.hero.d().let { h -> if (kids) h.filter(com.sokoul.iptv.data.repo.KidsRating::allowed) else h },
            rows = pg.rows.map { it.copy(works = it.works.d()) }.kidsOnly(),
        )
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
        val myList: Flow<BrowseRow?> = combine(favMovies, favSeries, enrich.updates.debounce(4_000).onStart { emit(0) }) { m, s, _ -> m + s }
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

        // pas de rangée « Mes fichiers Real-Debrid » : les fichiers du compte apparaissent comme versions de leur fiche
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
    cloud: com.sokoul.iptv.data.debrid.DebridCloud, profiles: com.sokoul.iptv.data.repo.ProfileRepository,
) : BrowseViewModel(BrowseKind.HOME, playlists, repo, enrich, vod, library, catalog, epg, parental, cloud, profiles)

@HiltViewModel
class MoviesBrowseViewModel @Inject constructor(
    playlists: PlaylistRepository, repo: BrowseRepository, enrich: EnrichmentRepository, vod: VodRepository,
    library: LibraryRepository, catalog: CatalogRepository, epg: EpgRepository, parental: ParentalGate,
    cloud: com.sokoul.iptv.data.debrid.DebridCloud, profiles: com.sokoul.iptv.data.repo.ProfileRepository,
) : BrowseViewModel(BrowseKind.MOVIES, playlists, repo, enrich, vod, library, catalog, epg, parental, cloud, profiles)

@HiltViewModel
class SeriesBrowseViewModel @Inject constructor(
    playlists: PlaylistRepository, repo: BrowseRepository, enrich: EnrichmentRepository, vod: VodRepository,
    library: LibraryRepository, catalog: CatalogRepository, epg: EpgRepository, parental: ParentalGate,
    cloud: com.sokoul.iptv.data.debrid.DebridCloud, profiles: com.sokoul.iptv.data.repo.ProfileRepository,
) : BrowseViewModel(BrowseKind.SERIES, playlists, repo, enrich, vod, library, catalog, epg, parental, cloud, profiles)

private data class Quad(val p: PlaylistEntity?, val unlocked: Boolean, val reload: Int, val profile: Long?)
