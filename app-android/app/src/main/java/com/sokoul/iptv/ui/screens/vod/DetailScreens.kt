package com.sokoul.iptv.ui.screens.vod

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.db.SeriesDetailsEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbPerson
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.EpisodeMerge
import com.sokoul.iptv.data.repo.EpisodeView
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.MovieDetails
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.VersionPicker
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.keyOf
import com.sokoul.iptv.data.repo.looksFrench
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.data.repo.toVersion
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.HeroButton
import com.sokoul.iptv.ui.components.LoadingBox
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.ProgressLine
import com.sokoul.iptv.ui.components.RatingChip
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.certificationLabel
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.components.formatRuntime
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.browse.openWork
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Texte affichable et sa langue : TMDB français → fournisseur en français → TMDB anglais → fournisseur. */
data class Synopsis(val text: String, val french: Boolean)

fun pickSynopsis(tmdbFr: String?, provider: String?, tmdbEn: String?): Synopsis? {
    tmdbFr?.takeIf { it.isNotBlank() }?.let { return Synopsis(it, true) }
    provider?.takeIf { looksFrench(it) }?.let { return Synopsis(it, true) }
    tmdbEn?.takeIf { it.isNotBlank() }?.let { return Synopsis(it, false) }
    provider?.takeIf { it.isNotBlank() }?.let { return Synopsis(it, false) }
    return null
}

// --- Film ----------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val vod: VodRepository,
    private val enrich: EnrichmentRepository,
    private val browse: BrowseRepository,
) : ViewModel() {
    val details = MutableStateFlow<MovieDetails?>(null)
    val error = MutableStateFlow<String?>(null)
    /** Versions de l'œuvre dans l'ordre de lancement (la première est lancée par « Lecture »). */
    val versions = MutableStateFlow<List<MovieEntity>>(emptyList())
    val preferredId = MutableStateFlow<String?>(null)
    val similar = MutableStateFlow<List<Work>>(emptyList())
    val saga = MutableStateFlow<Pair<String, List<Work>>?>(null)
    var categoryNames: Map<String, String> = emptyMap()
        private set
    var playlist: PlaylistEntity? = null

    private val ids = versions.map { l -> l.map { it.id } }.distinctUntilChanged()
    val progress: StateFlow<ProgressEntity?> = ids.flatMapLatest { l ->
        val p = playlist
        if (p == null || l.isEmpty()) flowOf(null) else vod.observeLatestMovieProgress(p.id, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val favorite: StateFlow<Boolean> = ids.flatMapLatest { l ->
        val p = playlist
        if (p == null || l.isEmpty()) flowOf(false) else vod.observeAnyFavorite(p.id, ContentType.MOVIE, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun load(id: String) = viewModelScope.launch {
        if (details.value?.movie?.id == id) return@launch
        val p = playlists.activeNow() ?: return@launch
        playlist = p
        val m = catalog.movie(p.id, id) ?: run { error.value = "Film introuvable"; return@launch }
        details.value = MovieDetails(m, null, null)
        categoryNames = vod.categoryNames(p.id, ContentType.MOVIE)
        loadVersions(p, m)
        try {
            val hint = runCatching { enrich.known(p.id, ContentType.MOVIE, listOf(m.keyOf()))[m.keyOf()]?.tmdbId }.getOrNull()
            val d = catalog.movieDetails(p, m, hint)
            details.value = d
            // TMDB a reconnu l'œuvre : on la rattache aux autres versions (année identique exigée, pour éviter un homonyme)
            val tm = d.tmdb
            if (tm != null && m.year != null && tm.year != null && kotlin.math.abs(tm.year - m.year) <= 1) {
                vod.linkTmdb(p.id, ContentType.MOVIE, m.id, m.tmdbId, m.groupKey, tm.id)
                catalog.movie(p.id, m.id)?.let { fresh -> if (fresh.groupKey != m.groupKey) loadVersions(p, fresh) }
            }
            if (tm != null) {
                launch { similar.value = runCatching { browse.similar(p.id, ContentType.MOVIE, tm.id, m.keyOf()) }.getOrDefault(emptyList()) }
                tm.collectionId?.let { cid -> launch { saga.value = runCatching { browse.saga(p.id, cid) }.getOrNull()?.takeIf { it.second.size >= 2 } } }
            }
        } catch (e: Exception) {
            error.value = e.userMessage()
        }
    }

    private suspend fun loadVersions(p: PlaylistEntity, m: MovieEntity) {
        val all = vod.movieVersions(p.id, m)
        versions.value = vod.orderedMovies(p.id, all)
        preferredId.value = vod.choice(p.id, ContentType.MOVIE, m.groupKey)
    }

    fun prefer(v: MovieEntity) = viewModelScope.launch {
        val p = playlist ?: return@launch
        vod.setChoice(p.id, ContentType.MOVIE, v.groupKey, v.id)
        details.value?.movie?.let { loadVersions(p, it) }
    }

    fun toggleFavorite(shownId: String) = viewModelScope.launch {
        playlist?.let { vod.toggleFavorite(it.id, ContentType.MOVIE, versions.value.map { v -> v.id }.ifEmpty { listOf(shownId) }, shownId) }
    }
    fun clearProgress() = viewModelScope.launch {
        playlist?.let { p -> vod.clearMovieProgress(p.id, versions.value.map { it.id }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailScreen(nav: NavHostController, id: String) {
    val vm: MovieDetailViewModel = hiltViewModel()
    val d by vm.details.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val preferredId by vm.preferredId.collectAsStateWithLifecycle()
    val similar by vm.similar.collectAsStateWithLifecycle()
    val saga by vm.saga.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val l = LocalLayout.current
    val playFocus = remember { FocusRequester() }
    var versionMenu by remember { mutableStateOf<MovieEntity?>(null) }
    LaunchedEffect(id) { vm.load(id) }

    val details = d ?: run { LoadingBox(); return }
    val m = details.movie
    val tm = details.tmdb
    val xt = details.xtream
    val pid = vm.playlist?.id ?: return
    val synopsis = pickSynopsis(tm?.overview, xt?.plot, tm?.overviewEn)
    val durationMin = xt?.durationSecs?.div(60)?.takeIf { it > 0 } ?: tm?.runtimeMin
    val resume = progress?.takeIf { !it.finished && it.positionMs > 30_000 }
    // version lancée par « Lecture » : la meilleure selon les préférences (langue, qualité, choix manuel)
    val best = versions.firstOrNull() ?: m
    val multi = versions.size > 1
    val title = tm?.title?.takeIf { it.isNotBlank() } ?: com.sokoul.iptv.data.repo.cleanDisplayTitle(m.title, m.year)
    val play = { nav.navigate(PlayerRoute(PlayKind.MOVIE, pid, best.id)) }

    DetailScaffold(
        nav, backdrop = tm?.backdrop ?: xt?.backdrop, poster = tm?.poster ?: xt?.poster ?: m.poster, logo = tm?.logo, title = title,
        header = {
            tm?.tagline?.let { DetailTagline(it) }
            MetaLine(
                rating = tm?.rating ?: xt?.rating ?: m.rating,
                bits = listOfNotNull(
                    (m.year ?: tm?.year)?.toString(), formatRuntime(durationMin),
                    best.quality, best.language, if (multi) "${versions.size} versions" else null,
                ),
                certification = tm?.certification,
            )
            val genres = tm?.genres?.joinToString(", ")?.ifEmpty { null } ?: xt?.genre
            if (!genres.isNullOrBlank()) Text(genres, Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            DetailButtons {
                HeroButton(
                    (if (resume != null) "Reprendre à ${formatDuration(resume.positionMs)}" else "Lecture") + (if (multi) " · ${versionLabel(best)}" else ""),
                    Icons.Default.PlayArrow, primary = true, onClick = play,
                    modifier = Modifier.then(if (l.isCompact) Modifier.fillMaxWidth() else Modifier).focusRequesterSafe(playFocus),
                )
                if (resume != null) DetailAction("Depuis le début", Icons.Default.Replay) { vm.clearProgress(); play() }
                DetailAction(if (favorite) "Dans ma liste" else "Ma liste", if (favorite) Icons.Default.Check else Icons.Default.Add) { vm.toggleFavorite(m.id) }
                val trailer = tm?.trailerYoutubeKey ?: xt?.trailer
                if (!trailer.isNullOrBlank()) DetailAction("Bande-annonce", Icons.Default.Theaters) { openYoutube(context, trailer) }
            }
            if (resume != null && resume.durationMs > 0) {
                Box(Modifier.padding(top = 12.dp).widthIn(max = 360.dp).fillMaxWidth().clip(RoundedCornerShape(2.dp))) {
                    ProgressLine(resume.positionMs.toFloat() / resume.durationMs)
                }
            }
            synopsis?.let { Overview(it) }
            val director = tm?.director ?: xt?.director
            if (!director.isNullOrBlank()) Credit("Réalisation", director)
            if (tm?.cast.isNullOrEmpty() && !xt?.cast.isNullOrBlank()) Credit("Avec", xt?.cast ?: "")
        },
    ) {
        if (multi) {
            section("versions", "Versions disponibles", "OK pour lire · appui long pour toujours lancer celle-ci en premier") {
                FlowRow(Modifier.padding(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    versions.forEach { v ->
                        VersionChip(
                            label = versionLabel(v), detail = vm.categoryNames[v.categoryId],
                            preferred = v.id == preferredId, first = v.id == best.id,
                            onClick = { nav.navigate(PlayerRoute(PlayKind.MOVIE, pid, v.id)) }, onLongClick = { versionMenu = v },
                        )
                    }
                }
            }
        }
        castSection(tm?.cast.orEmpty())
        saga?.let { (name, works) ->
            section("saga", "Saga : ${name.replace(" - Saga", "").replace(" (Collection)", "")}", "Toute l'histoire, dans l'ordre de sortie.") {
                CardRow(works, { it.uid }) { _, w -> WorkCard(w, { if (w.id != m.id) nav.openWork(w) }, showPlatform = false) }
            }
        }
        if (similar.isNotEmpty()) {
            section("similar", "Vous aimerez aussi", "Des films dans la même veine, disponibles dans votre abonnement.") {
                CardRow(similar, { it.uid }) { _, w -> WorkCard(w, { nav.openWork(w) }) }
            }
        }
    }
    versionMenu?.let { v ->
        OptionsDialog(
            versionLabel(v),
            listOf(
                MenuOption("Lire cette version", Icons.Default.PlayArrow) { nav.navigate(PlayerRoute(PlayKind.MOVIE, pid, v.id)) },
                MenuOption("Toujours lancer cette version en premier", Icons.Default.Star) { vm.prefer(v) },
            ),
            subtitle = v.name,
            onDismiss = { versionMenu = null },
        )
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

// --- Série ---------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val vod: VodRepository,
    private val enrich: EnrichmentRepository,
    private val browse: BrowseRepository,
) : ViewModel() {
    private var openedId: String? = null
    private val pidFlow = MutableStateFlow<Long?>(null)
    var playlist: PlaylistEntity? = null
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)
    val tmdb = MutableStateFlow<TmdbInfo?>(null)
    /** Épisodes TMDB en français, par (saison, épisode). */
    val tmdbEpisodes = MutableStateFlow<Map<Pair<Int, Int>, TmdbEpisode>>(emptyMap())
    private val loadedSeasons = HashSet<Int>()
    val similar = MutableStateFlow<List<Work>>(emptyList())
    /** Versions de la série (VF, VOSTFR…), dans l'ordre de préférence. */
    val versions = MutableStateFlow<List<SeriesEntity>>(emptyList())
    /** Version principale : ses épisodes sont affichés, complétés par ceux des autres versions. */
    val primaryId = MutableStateFlow<String?>(null)
    val preferredId = MutableStateFlow<String?>(null)

    private val primaryKey = combine(pidFlow.filterNotNull(), primaryId.filterNotNull()) { p, s -> p to s }.distinctUntilChanged()
    private val versionIds = versions.map { l -> l.map { it.id } }.distinctUntilChanged()

    val series: StateFlow<SeriesEntity?> = primaryKey.flatMapLatest { (p, s) -> catalog.observeSeries(p, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val details: StateFlow<SeriesDetailsEntity?> = primaryKey.flatMapLatest { (p, s) -> catalog.seriesDetails(p, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** Épisodes bruts de toutes les versions (pour retrouver la saison/épisode d'une progression). */
    val allEpisodes: StateFlow<List<EpisodeEntity>> = combine(pidFlow.filterNotNull(), versionIds) { p, ids -> p to ids }
        .flatMapLatest { (p, ids) -> if (ids.isEmpty()) flowOf(emptyList()) else vod.observeEpisodes(p, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val episodes: StateFlow<List<EpisodeEntity>> = combine(allEpisodes, primaryId, versionIds) { all, primary, ids ->
        VodRepository.mergeEpisodes(all, listOfNotNull(primary) + ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progress: StateFlow<List<ProgressEntity>> = combine(pidFlow.filterNotNull(), versionIds) { p, ids -> p to ids }
        .flatMapLatest { (p, ids) -> if (ids.isEmpty()) flowOf(emptyList()) else vod.observeSeriesProgress(p, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorite: StateFlow<Boolean> = combine(pidFlow.filterNotNull(), versionIds) { p, ids -> p to ids }
        .flatMapLatest { (p, ids) -> if (ids.isEmpty()) flowOf(false) else vod.observeAnyFavorite(p, ContentType.SERIES, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun load(id: String, force: Boolean = false) = viewModelScope.launch {
        if (!force && openedId == id) return@launch
        val p = playlists.activeNow() ?: return@launch
        playlist = p
        openedId = id
        loading.value = true
        error.value = null
        val s = catalog.seriesItem(p.id, id) ?: run { error.value = "Série introuvable"; loading.value = false; return@launch }
        val ordered = vod.orderedSeries(p.id, vod.seriesVersions(p.id, s))
        versions.value = ordered
        preferredId.value = vod.choice(p.id, ContentType.SERIES, s.groupKey)
        val primary = ordered.firstOrNull()?.id ?: id
        primaryId.value = primary
        pidFlow.value = p.id
        // fiche TMDB en parallèle du chargement des épisodes (textes français, vignettes)
        launch {
            val hint = runCatching { enrich.known(p.id, ContentType.SERIES, listOf(s.keyOf()))[s.keyOf()]?.tmdbId }.getOrNull()
            val tm = catalog.seriesTmdb(s, hint)
            tmdb.value = tm
            if (tm != null) {
                launch { similar.value = runCatching { browse.similar(p.id, ContentType.SERIES, tm.id, s.keyOf()) }.getOrDefault(emptyList()) }
                if (s.year != null && tm.year != null && kotlin.math.abs(tm.year - s.year) <= 1) {
                    vod.linkTmdb(p.id, ContentType.SERIES, s.id, s.tmdbId, s.groupKey, tm.id)
                    catalog.seriesItem(p.id, s.id)?.let { fresh ->
                        if (fresh.groupKey != s.groupKey) {
                            val more = vod.orderedSeries(p.id, vod.seriesVersions(p.id, fresh))
                            if (more.size > versions.value.size) versions.value = more
                        }
                    }
                }
            }
        }
        try {
            catalog.ensureEpisodes(p, primary, force)
        } catch (e: Exception) {
            error.value = e.userMessage()
        } finally {
            loading.value = false
        }
        // autres versions : épisodes chargés une par une (cache 12 h) pour combler les trous
        ordered.filter { it.id != primary }.take(MAX_EXTRA_VERSIONS).forEach { v -> runCatching { catalog.ensureEpisodes(p, v.id, force) } }
    }

    /** Textes et vignettes TMDB d'une saison (une seule requête par saison, en cache 7 jours). */
    fun loadSeason(season: Int) {
        val tm = tmdb.value ?: return
        if (!loadedSeasons.add(season)) return
        viewModelScope.launch {
            val list = runCatching { catalog.tmdbSeason(tm.id, season) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) tmdbEpisodes.value = tmdbEpisodes.value + list.associateBy { it.season to it.episode }
        }
    }

    fun selectVersion(v: SeriesEntity) = viewModelScope.launch {
        val p = playlist ?: return@launch
        primaryId.value = v.id
        vod.setChoice(p.id, ContentType.SERIES, v.groupKey, v.id)
        preferredId.value = v.id
        runCatching { catalog.ensureEpisodes(p, v.id) }.onFailure { error.value = it.userMessage() }
    }

    fun toggleFavorite(shownId: String) = viewModelScope.launch {
        playlist?.let { vod.toggleFavorite(it.id, ContentType.SERIES, versions.value.map { v -> v.id }.ifEmpty { listOf(shownId) }, shownId) }
    }
    fun setWatched(e: EpisodeEntity, watched: Boolean) = viewModelScope.launch {
        playlist?.let { library.setWatched(it.id, ContentType.SERIES, e.id, e.seriesId, e.title, watched) }
    }
    fun setSeasonWatched(list: List<EpisodeEntity>, watched: Boolean) = viewModelScope.launch {
        playlist?.let { p -> list.forEach { e -> library.setWatched(p.id, ContentType.SERIES, e.id, e.seriesId, e.title, watched) } }
    }

    companion object {
        const val MAX_EXTRA_VERSIONS = 4
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesDetailScreen(nav: NavHostController, id: String) {
    val vm: SeriesDetailViewModel = hiltViewModel()
    val s by vm.series.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val tmdb by vm.tmdb.collectAsStateWithLifecycle()
    val tmdbEpisodes by vm.tmdbEpisodes.collectAsStateWithLifecycle()
    val similar by vm.similar.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val primaryId by vm.primaryId.collectAsStateWithLifecycle()
    val allEpisodes by vm.allEpisodes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val l = LocalLayout.current
    val playFocus = remember { FocusRequester() }
    var season by remember { mutableStateOf<Int?>(null) }
    var episodeMenu by remember { mutableStateOf<EpisodeEntity?>(null) }
    LaunchedEffect(id) { vm.load(id) }

    val series = s ?: run { LoadingBox(); return }
    val pid = vm.playlist?.id ?: return
    // progression rattachée à la saison/épisode : un épisode vu dans une version l'est dans toutes
    val seOf = allEpisodes.associate { it.id to (it.season to it.episode) }
    val progressBySe = progress.filter { seOf[it.itemId] != null }.groupBy { seOf.getValue(it.itemId) }.mapValues { (_, list) -> list.maxBy { it.updatedAt } }
    val progressById = episodes.mapNotNull { e -> progressBySe[e.season to e.episode]?.let { e.id to it } }.toMap()
    val seasons = episodes.map { it.season }.distinct().sorted()
    val last = progress.filter { seOf[it.itemId] != null }.maxByOrNull { it.updatedAt }
    val lastSe = last?.let { seOf[it.itemId] }
    val currentSeason = season ?: lastSe?.first ?: seasons.firstOrNull()
    LaunchedEffect(currentSeason, tmdb?.id) { currentSeason?.let { vm.loadSeason(it) } }

    // prochain épisode à regarder : celui en cours, sinon le suivant du dernier vu, sinon le premier
    val resumeEpisode = last?.let { lp ->
        val i = episodes.indexOfFirst { (it.season to it.episode) == lastSe }
        when {
            i < 0 -> null
            !lp.finished -> episodes[i]
            else -> episodes.getOrNull(i + 1)
        }
    } ?: episodes.firstOrNull()
    val resumeProgress = resumeEpisode?.let { progressById[it.id] }?.takeIf { !it.finished && it.positionMs > 30_000 }
    val backdrop = tmdb?.backdrop ?: details?.backdrop ?: series.backdrop
    val synopsis = pickSynopsis(tmdb?.overview, details?.plot ?: series.plot, tmdb?.overviewEn)
    val title = tmdb?.title?.takeIf { it.isNotBlank() } ?: com.sokoul.iptv.data.repo.cleanDisplayTitle(series.title, series.year)
    val seasonEpisodes = episodes.filter { it.season == currentSeason }.map { EpisodeMerge.merge(it, tmdbEpisodes[it.season to it.episode], backdrop) }

    DetailScaffold(
        nav, backdrop = backdrop, poster = tmdb?.poster ?: series.poster, logo = tmdb?.logo, title = title,
        header = {
            tmdb?.tagline?.let { DetailTagline(it) }
            MetaLine(
                rating = tmdb?.rating ?: series.rating,
                bits = listOfNotNull(
                    (series.year ?: tmdb?.year)?.toString(),
                    (seasons.size.takeIf { it > 0 } ?: tmdb?.seasons)?.let { if (it > 1) "$it saisons" else "1 saison" },
                    if (versions.size > 1) "${versions.size} versions" else null,
                ),
                certification = tmdb?.certification,
            )
            val genre = tmdb?.genres?.joinToString(", ")?.ifEmpty { null } ?: details?.genre ?: series.genre
            if (!genre.isNullOrBlank()) Text(genre, Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            DetailButtons {
                if (resumeEpisode != null) {
                    val label = "S${resumeEpisode.season} · É${resumeEpisode.episode}"
                    HeroButton(
                        if (resumeProgress != null) "Reprendre $label" else if (last != null) "Regarder $label" else "Lecture",
                        Icons.Default.PlayArrow, primary = true,
                        onClick = { nav.navigate(PlayerRoute(PlayKind.EPISODE, pid, resumeEpisode.id, parentId = resumeEpisode.seriesId)) },
                        modifier = Modifier.then(if (l.isCompact) Modifier.fillMaxWidth() else Modifier).focusRequesterSafe(playFocus),
                    )
                }
                DetailAction(if (favorite) "Dans ma liste" else "Ma liste", if (favorite) Icons.Default.Check else Icons.Default.Add) { vm.toggleFavorite(series.id) }
                val trailer = tmdb?.trailerYoutubeKey ?: details?.trailer
                if (!trailer.isNullOrBlank()) DetailAction("Bande-annonce", Icons.Default.Theaters) { openYoutube(context, trailer) }
                DetailAction("Actualiser", Icons.Default.Refresh) { vm.load(id, force = true) }
            }
            synopsis?.let { Overview(it) }
            val creator = tmdb?.director ?: details?.director
            if (!creator.isNullOrBlank()) Credit("Création", creator)
        },
    ) {
        if (versions.size > 1) {
            section("versions", "Versions", "Choisissez la langue : les épisodes manquants sont complétés par les autres versions.") {
                LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(versions, key = { it.id }) { v ->
                        Pill(seriesVersionLabel(v), selected = v.id == primaryId) { vm.selectVersion(v) }
                    }
                }
            }
        }
        if (loading && episodes.isEmpty()) item("loading") { Text("Chargement des épisodes…", Modifier.padding(horizontal = l.margin, vertical = 16.dp), color = SokoulColors.TextMuted) }
        error?.let { e -> item("error") { Text(e, Modifier.padding(horizontal = l.margin, vertical = 12.dp), color = SokoulColors.Live) } }
        if (seasons.isNotEmpty()) {
            section("seasons", "Épisodes", null) {
                LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seasons) { n ->
                        Pill(
                            "Saison $n", selected = n == currentSeason, onClick = { season = n },
                            onLongClick = { episodeMenu = EpisodeEntity(pid, series.id, "season:$n", n, 0, "Saison $n", null, null, null, null, null, null) },
                        )
                    }
                }
            }
            items(seasonEpisodes, key = { "ep:" + it.entity.id }) { ev ->
                EpisodeItem(ev, progressById[ev.entity.id], { nav.navigate(PlayerRoute(PlayKind.EPISODE, pid, ev.entity.id, parentId = ev.entity.seriesId)) }, { episodeMenu = ev.entity })
            }
        }
        castSection(tmdb?.cast.orEmpty())
        if (similar.isNotEmpty()) {
            section("similar", "Vous aimerez aussi", "Des séries dans la même veine, disponibles dans votre abonnement.") {
                CardRow(similar, { it.uid }) { _, w -> WorkCard(w, { nav.openWork(w) }) }
            }
        }
    }

    episodeMenu?.let { e ->
        if (e.id.startsWith("season:")) {
            val list = episodes.filter { it.season == e.season }
            OptionsDialog(
                "Saison ${e.season}",
                listOf(
                    MenuOption("Marquer la saison comme vue", Icons.Default.CheckCircle) { vm.setSeasonWatched(list, true) },
                    MenuOption("Marquer la saison comme non vue", Icons.Default.Replay) { vm.setSeasonWatched(list, false) },
                ),
                onDismiss = { episodeMenu = null },
            )
        } else {
            val watched = progressById[e.id]?.finished == true
            OptionsDialog(
                "S%02dE%02d · %s".format(e.season, e.episode, EpisodeMerge.cleanTitle(e.title, e.episode)),
                listOf(
                    MenuOption("Lecture", Icons.Default.PlayArrow) { nav.navigate(PlayerRoute(PlayKind.EPISODE, pid, e.id, parentId = e.seriesId)) },
                    MenuOption(if (watched) "Marquer comme non vu" else "Marquer comme vu", Icons.Default.CheckCircle) { vm.setWatched(e, !watched) },
                ),
                subtitle = e.plot,
                onDismiss = { episodeMenu = null },
            )
        }
    }
    LaunchedEffect(resumeEpisode != null) { if (resumeEpisode != null) runCatching { playFocus.requestFocus() } }
}

// --- composants des fiches -------------------------------------------------------------------------

/**
 * Mise en page des fiches. Grand écran/TV : fond plein cadre à droite (effet Ken Burns), texte à gauche.
 * Téléphone : image en haut, contenu en colonne dessous.
 */
@Composable
private fun DetailScaffold(
    nav: NavHostController,
    backdrop: String?,
    poster: String?,
    logo: String?,
    title: String,
    header: @Composable () -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    val l = LocalLayout.current
    val kb = remember { Animatable(1f) }
    LaunchedEffect(backdrop) { kb.snapTo(1f); kb.animateTo(1.07f, tween(22_000, easing = LinearEasing)) }
    val listState = rememberLazyListState()
    // le fond s'efface quand on descend dans la fiche, pour garder les sections lisibles
    val fade by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 0.18f
            else (1f - listState.firstVisibleItemScrollOffset / 900f).coerceIn(0.18f, 1f)
        }
    }
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        if (!l.isCompact) {
            val imgMod = Modifier.fillMaxWidth(0.80f).aspectRatio(16f / 9f).align(Alignment.TopEnd)
            if (backdrop != null) {
                NetImage(
                    tmdbSized(backdrop, "w1280"), null,
                    imgMod.clipToBounds().graphicsLayer { scaleX = kb.value; scaleY = kb.value; alpha = fade },
                )
            } else if (poster != null) {
                // pas de fond : l'affiche, en grand à droite
                NetImage(
                    tmdbSized(poster, "w500"), title,
                    Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = l.margin).height((l.heightDp * 0.6f).dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)),
                )
            }
            Box(imgMod.background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to SokoulColors.Background)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to SokoulColors.Background, 0.30f to Color(0xF20A0A0F), 0.62f to Color(0x330A0A0F), 1f to Color.Transparent)))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = if (l.isCompact) 0.dp else (l.heightDp * 0.14f).dp, bottom = 56.dp),
        ) {
            if (l.isCompact) {
                item("image") {
                    Box(Modifier.fillMaxWidth().aspectRatio(if (backdrop != null) 16f / 10f else 4f / 5f)) {
                        NetImage(tmdbSized(backdrop ?: poster, if (backdrop != null) "w780" else "w500"), title, Modifier.fillMaxSize().clipToBounds().graphicsLayer { scaleX = kb.value; scaleY = kb.value }, alignment = Alignment.TopCenter)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x80000000), 0.25f to Color.Transparent, 0.7f to Color.Transparent, 1f to SokoulColors.Background)))
                    }
                }
            }
            item("header") {
                Column(Modifier.padding(horizontal = l.margin).then(if (l.isCompact) Modifier else Modifier.widthIn(max = (l.widthDp * 0.52f).dp))) {
                    if (logo != null) {
                        NetImage(
                            tmdbSized(logo, "w500"), title, Modifier.heightIn(max = if (l.isCompact) 80.dp else 110.dp).widthIn(max = if (l.isCompact) 260.dp else 420.dp),
                            ContentScale.Fit, alignment = Alignment.BottomStart,
                        ) { DetailTitle(title) }
                    } else {
                        DetailTitle(title)
                    }
                    Spacer(Modifier.height(10.dp))
                    header()
                }
            }
            sections()
        }
        if (l.isTouch) {
            Box(
                Modifier.statusBarsPadding().padding(8.dp).size(44.dp).clip(CircleShape).background(Color(0x80000000)).dpadClickable({ nav.popBackStack() }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
        }
    }
}

@Composable
private fun DetailTitle(title: String) {
    val l = LocalLayout.current
    Text(
        title, style = if (l.isCompact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailTagline(text: String) {
    Text(text, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic, color = SokoulColors.TextPrimary.copy(alpha = 0.85f), maxLines = 2)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(rating: Double?, bits: List<String>, certification: String?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rating != null && rating > 0) RatingChip(rating)
        bits.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextSecondary) }
        if (certification != null) {
            Text(
                certificationLabel(certification), Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailButtons(content: @Composable () -> Unit) {
    FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** Action secondaire : pastille translucide (icône + libellé). */
@Composable
private fun DetailAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    HeroButton(text, icon, primary = false, onClick = onClick)
}

/** Résumé (dépliable au toucher), avec mention quand il n'existe qu'en anglais. */
@Composable
private fun Overview(s: Synopsis) {
    val l = LocalLayout.current
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 16.dp).animateContentSize()) {
        Text(
            s.text, Modifier.then(if (l.isTouch) Modifier.dpadClickable({ expanded = !expanded }) else Modifier),
            style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextPrimary.copy(alpha = 0.92f),
            maxLines = if (expanded) Int.MAX_VALUE else if (l.isTv) 5 else 4, overflow = TextOverflow.Ellipsis,
        )
        if (!s.french) Text("Résumé disponible en anglais uniquement", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted)
    }
}

@Composable
private fun Credit(label: String, value: String) {
    Text(
        "$label : $value", Modifier.padding(top = 10.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

/** Section de fiche : titre + accroche + contenu, marges adaptatives. */
private fun LazyListScope.section(key: String, title: String, tagline: String?, content: @Composable () -> Unit) {
    item(key) {
        val l = LocalLayout.current
        Column(Modifier.padding(top = 28.dp)) {
            Text(title, Modifier.padding(horizontal = l.margin), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (tagline != null) Text(tagline, Modifier.padding(horizontal = l.margin, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextMuted)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun LazyListScope.castSection(cast: List<TmdbPerson>) {
    if (cast.isEmpty()) return
    section("cast", "Distribution", null) {
        val l = LocalLayout.current
        LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cast) { person ->
                Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NetImage(person.photo, person.name, Modifier.size(78.dp).clip(CircleShape).background(SokoulColors.SurfaceHigher)) {
                        Text(com.sokoul.iptv.ui.components.initials(person.name), color = SokoulColors.TextSecondary, fontWeight = FontWeight.Bold)
                    }
                    Text(person.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    person.role?.let { Text(it, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(50), focusScale = 1.06f, borderWidth = 2.dp,
        color = if (selected) Color.White else SokoulColors.SurfaceHigh, focusedColor = if (selected) Color.White else SokoulColors.SurfaceHigher,
    ) { _ ->
        Text(
            text, Modifier.padding(horizontal = 18.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color(0xFF0A0A0F) else SokoulColors.TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun VersionChip(label: String, detail: String?, preferred: Boolean, first: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(12.dp), focusScale = 1.05f, borderWidth = 2.dp,
        color = if (first) SokoulColors.AccentSoft else SokoulColors.SurfaceHigh, focusedColor = SokoulColors.SurfaceHigher,
    ) { _ ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (preferred) Icons.Default.Star else Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = if (preferred) SokoulColors.Gold else SokoulColors.TextSecondary)
            Column(Modifier.padding(start = 8.dp).widthIn(max = 220.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EpisodeItem(ev: EpisodeView, progress: ProgressEntity?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val l = LocalLayout.current
    val thumbW = if (l.isCompact) 136.dp else (l.landscape * 0.9f)
    Box(Modifier.padding(horizontal = l.margin, vertical = 5.dp)) {
        FocusSurface(
            onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().widthIn(max = 1100.dp),
            color = Color.Transparent, focusedColor = SokoulColors.SurfaceHigh, focusScale = 1.015f, borderWidth = 2.dp, shape = RoundedCornerShape(14.dp),
        ) { focused ->
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(thumbW).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(SokoulColors.SurfaceHigher)) {
                        NetImage(tmdbSized(ev.image, "w500"), ev.title, Modifier.fillMaxSize()) {
                            Icon(Icons.Default.PlayArrow, null, tint = SokoulColors.TextMuted)
                        }
                        if (focused) {
                            Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                                }
                            }
                        }
                        if (progress != null && progress.durationMs > 0 && !progress.finished) {
                            ProgressLine(progress.positionMs.toFloat() / progress.durationMs, Modifier.align(Alignment.BottomStart))
                        }
                        if (progress?.finished == true) {
                            Icon(Icons.Default.CheckCircle, "Vu", Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp), tint = SokoulColors.Success)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("${ev.entity.episode}. ${ev.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ev.runtimeMin?.let { Text(formatRuntime(it) ?: "", Modifier.padding(top = 2.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium) }
                        if (!l.isCompact && ev.overview != null) {
                            Text(ev.overview, Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (l.isCompact && ev.overview != null) {
                    Text(ev.overview, Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun Modifier.focusRequesterSafe(r: FocusRequester): Modifier = this.focusRequester(r)

fun versionLabel(m: MovieEntity): String = VersionPicker.label(m.toVersion())
fun seriesVersionLabel(s: SeriesEntity): String = VersionPicker.label(s.toVersion()).takeIf { it != "Version" } ?: s.name.take(28)

private fun openYoutube(context: android.content.Context, key: String) {
    val id = key.substringAfter("v=").substringAfterLast('/')
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
