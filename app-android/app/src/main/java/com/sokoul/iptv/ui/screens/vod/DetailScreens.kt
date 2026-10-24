package com.sokoul.iptv.ui.screens.vod

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.db.SeriesDetailsEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.MovieDetails
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.data.repo.VersionPicker
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.toVersion
import com.sokoul.iptv.data.db.MovieEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.BottomFade
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.LeftFade
import com.sokoul.iptv.ui.components.LoadingBox
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.Tag
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatDuration
import com.sokoul.iptv.ui.util.formatMinutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Film ----------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val vod: VodRepository,
) : ViewModel() {
    val details = MutableStateFlow<MovieDetails?>(null)
    val error = MutableStateFlow<String?>(null)
    /** Versions de l'œuvre dans l'ordre de lancement (la première est lancée par « Lecture »). */
    val versions = MutableStateFlow<List<MovieEntity>>(emptyList())
    val preferredId = MutableStateFlow<String?>(null)
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
            val d = catalog.movieDetails(p, m)
            details.value = d
            // TMDB a reconnu l'œuvre : on la rattache aux autres versions (année identique exigée, pour éviter un homonyme)
            val tm = d.tmdb
            if (tm != null && m.year != null && tm.year != null && kotlin.math.abs(tm.year - m.year) <= 1) {
                vod.linkTmdb(p.id, ContentType.MOVIE, m.id, m.tmdbId, m.groupKey, tm.id)
                catalog.movie(p.id, m.id)?.let { fresh -> if (fresh.groupKey != m.groupKey) loadVersions(p, fresh) }
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
@Composable
fun MovieDetailScreen(nav: NavHostController, id: String) {
    val vm: MovieDetailViewModel = hiltViewModel()
    val d by vm.details.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val preferredId by vm.preferredId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    var versionMenu by remember { mutableStateOf<MovieEntity?>(null) }
    LaunchedEffect(id) { vm.load(id) }

    val details = d ?: run { LoadingBox(); return }
    val m = details.movie
    val tm = details.tmdb
    val xt = details.xtream
    val pid = vm.playlist?.id ?: return
    val backdrop = tm?.backdrop ?: xt?.backdrop ?: tm?.poster ?: xt?.poster ?: m.poster
    val plot = xt?.plot ?: tm?.overview
    val durationMin = xt?.durationSecs?.div(60) ?: tm?.runtimeMin
    val resume = progress?.takeIf { !it.finished && it.positionMs > 30_000 }
    // version lancée par « Lecture » : la meilleure selon les préférences (langue, qualité, choix manuel)
    val best = versions.firstOrNull() ?: m
    val multi = versions.size > 1

    DetailLayout(backdrop = backdrop, poster = tm?.poster ?: m.poster, logo = tm?.logo, title = workTitle(m.title, versions.size)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            (m.year ?: tm?.year)?.let { Tag(it.toString()) }
            durationMin?.takeIf { it > 0 }?.let { Tag(formatMinutes(it)) }
            (tm?.rating ?: xt?.rating ?: m.rating)?.let { Tag("★ %.1f".format(it), SokoulColors.Warning.copy(alpha = 0.2f), SokoulColors.Warning) }
            best.quality?.let { Tag(it, SokoulColors.AccentSoft, SokoulColors.Accent) }
            best.language?.let { Tag(it) }
            if (multi) Tag("${versions.size} versions")
        }
        val genres = tm?.genres?.joinToString(", ") ?: xt?.genre
        if (!genres.isNullOrBlank()) Text(genres, Modifier.padding(top = 10.dp), color = SokoulColors.TextSecondary)
        if (!plot.isNullOrBlank()) Text(plot, Modifier.padding(top = 12.dp).widthIn(max = 760.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
        val director = tm?.director ?: xt?.director
        if (!director.isNullOrBlank()) Text("Réalisation : $director", Modifier.padding(top = 10.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)

        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(
                (if (resume != null) "Reprendre à ${formatDuration(resume.positionMs)}" else "Lecture") + (if (multi) " · ${versionLabel(best)}" else ""),
                { nav.navigate(PlayerRoute(PlayKind.MOVIE, pid, best.id)) }, icon = Icons.Default.PlayArrow, primary = true, focusRequester = playFocus,
            )
            if (resume != null) AppButton("Depuis le début", { vm.clearProgress(); nav.navigate(PlayerRoute(PlayKind.MOVIE, pid, best.id)) }, icon = Icons.Default.Replay)
            AppButton(if (favorite) "Dans ma liste" else "Ma liste", { vm.toggleFavorite(m.id) }, icon = if (favorite) Icons.Default.Check else Icons.Default.Add)
            val trailer = tm?.trailerYoutubeKey ?: xt?.trailer
            if (!trailer.isNullOrBlank()) AppButton("Bande-annonce", { openYoutube(context, trailer) }, icon = Icons.Default.Theaters)
        }
        if (resume != null && resume.durationMs > 0) {
            Box(Modifier.padding(top = 12.dp).width(320.dp).height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(resume.positionMs.toFloat() / resume.durationMs).background(SokoulColors.Accent, RoundedCornerShape(2.dp)))
            }
        }
        if (multi) {
            Text("Versions", Modifier.padding(top = 26.dp, bottom = 4.dp), style = MaterialTheme.typography.titleLarge)
            Text("OK pour lire une version · appui long (ou Menu) pour la lancer en premier", color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                versions.forEach { v ->
                    VersionRow(
                        label = versionLabel(v), detail = listOfNotNull(vm.categoryNames[v.categoryId], v.name).joinToString(" · "),
                        preferred = v.id == preferredId, first = v.id == best.id,
                        onClick = { nav.navigate(PlayerRoute(PlayKind.MOVIE, pid, v.id)) }, onLongClick = { versionMenu = v },
                    )
                }
            }
        }
        if (!tm?.cast.isNullOrEmpty()) {
            Text("Distribution", Modifier.padding(top = 26.dp, bottom = 10.dp), style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(tm!!.cast) { person ->
                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        NetImage(person.photo, person.name, Modifier.size(80.dp).clip(CircleShape).background(SokoulColors.SurfaceHigher))
                        Text(person.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                        person.role?.let { Text(it, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        } else if (!xt?.cast.isNullOrBlank()) {
            Text("Avec : ${xt?.cast}", Modifier.padding(top = 16.dp), color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
) : ViewModel() {
    private var openedId: String? = null
    private val pidFlow = MutableStateFlow<Long?>(null)
    var playlist: PlaylistEntity? = null
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)
    val tmdb = MutableStateFlow<TmdbInfo?>(null)
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
        try {
            catalog.ensureEpisodes(p, primary, force)
        } catch (e: Exception) {
            error.value = e.userMessage()
        } finally {
            loading.value = false
        }
        // autres versions : épisodes chargés une par une (cache 12 h) pour combler les trous
        ordered.filter { it.id != primary }.take(MAX_EXTRA_VERSIONS).forEach { v -> runCatching { catalog.ensureEpisodes(p, v.id, force) } }
        val tm = catalog.seriesTmdb(s)
        tmdb.value = tm
        if (tm != null && s.year != null && tm.year != null && kotlin.math.abs(tm.year - s.year) <= 1) {
            vod.linkTmdb(p.id, ContentType.SERIES, s.id, s.tmdbId, s.groupKey, tm.id)
            catalog.seriesItem(p.id, s.id)?.let { fresh ->
                if (fresh.groupKey != s.groupKey) {
                    val more = vod.orderedSeries(p.id, vod.seriesVersions(p.id, fresh))
                    if (more.size > versions.value.size) versions.value = more
                }
            }
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
    val versions by vm.versions.collectAsStateWithLifecycle()
    val primaryId by vm.primaryId.collectAsStateWithLifecycle()
    val allEpisodes by vm.allEpisodes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    var season by remember { mutableStateOf<Int?>(null) }
    var episodeMenu by remember { mutableStateOf<EpisodeEntity?>(null) }
    LaunchedEffect(id) { vm.load(id) }

    val series = s ?: run { LoadingBox(); return }
    val pid = vm.playlist?.id ?: return
    // progression rattachée à la saison/épisode : un épisode vu dans une version l'est dans toutes
    val seOf = allEpisodes.associate { it.id to (it.season to it.episode) }
    val progressBySe = progress.filter { seOf[it.itemId] != null }.groupBy { seOf.getValue(it.itemId) }.mapValues { (_, l) -> l.maxBy { it.updatedAt } }
    val progressById = episodes.mapNotNull { e -> progressBySe[e.season to e.episode]?.let { e.id to it } }.toMap()
    val seasons = episodes.map { it.season }.distinct().sorted()
    val last = progress.filter { seOf[it.itemId] != null }.maxByOrNull { it.updatedAt }
    val lastSe = last?.let { seOf[it.itemId] }
    val currentSeason = season ?: lastSe?.first ?: seasons.firstOrNull()

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

    DetailLayout(
        backdrop = tmdb?.backdrop ?: details?.backdrop ?: series.backdrop ?: series.poster,
        poster = tmdb?.poster ?: series.poster, logo = tmdb?.logo, title = workTitle(series.title, versions.size),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (series.year ?: tmdb?.year)?.let { Tag(it.toString()) }
            if (seasons.isNotEmpty()) Tag("${seasons.size} saison${if (seasons.size > 1) "s" else ""}")
            (tmdb?.rating ?: series.rating)?.let { Tag("★ %.1f".format(it), SokoulColors.Warning.copy(alpha = 0.2f), SokoulColors.Warning) }
        }
        val genre = tmdb?.genres?.joinToString(", ") ?: details?.genre ?: series.genre
        if (!genre.isNullOrBlank()) Text(genre, Modifier.padding(top = 10.dp), color = SokoulColors.TextSecondary)
        val plot = details?.plot ?: series.plot ?: tmdb?.overview
        if (!plot.isNullOrBlank()) Text(plot, Modifier.padding(top = 12.dp).widthIn(max = 760.dp), maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)

        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (resumeEpisode != null) {
                val label = "S%02dE%02d".format(resumeEpisode.season, resumeEpisode.episode)
                AppButton(
                    if (resumeProgress != null) "Reprendre $label" else if (last != null) "Regarder $label" else "Lecture",
                    { nav.navigate(PlayerRoute(PlayKind.EPISODE, pid, resumeEpisode.id, parentId = resumeEpisode.seriesId)) },
                    icon = Icons.Default.PlayArrow, primary = true, focusRequester = playFocus,
                )
            }
            AppButton(if (favorite) "Dans ma liste" else "Ma liste", { vm.toggleFavorite(series.id) }, icon = if (favorite) Icons.Default.Check else Icons.Default.Add)
            val trailer = tmdb?.trailerYoutubeKey ?: details?.trailer
            if (!trailer.isNullOrBlank()) AppButton("Bande-annonce", { openYoutube(context, trailer) }, icon = Icons.Default.Theaters)
            AppButton("Actualiser", { vm.load(id, force = true) }, icon = Icons.Default.Refresh)
        }
        if (versions.size > 1) {
            Text("Versions", Modifier.padding(top = 22.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(versions, key = { it.id }) { v ->
                    val sel = v.id == primaryId
                    FocusSurface(
                        onClick = { vm.selectVersion(v) },
                        color = if (sel) SokoulColors.Accent else SokoulColors.SurfaceHigh,
                        shape = RoundedCornerShape(50), focusScale = 1.05f,
                    ) { _ ->
                        Text(seriesVersionLabel(v), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (loading && episodes.isEmpty()) Text("Chargement des épisodes…", Modifier.padding(top = 20.dp), color = SokoulColors.TextMuted)
        error?.let { Text(it, Modifier.padding(top = 16.dp), color = SokoulColors.Live) }

        if (seasons.isNotEmpty()) {
            LazyRow(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons) { n ->
                    val sel = n == currentSeason
                    FocusSurface(
                        onClick = { season = n },
                        onLongClick = { episodeMenu = EpisodeEntity(pid, series.id, "season:$n", n, 0, "Saison $n", null, null, null, null, null, null) },
                        color = if (sel) SokoulColors.Accent else SokoulColors.SurfaceHigh,
                        shape = RoundedCornerShape(50), focusScale = 1.05f,
                    ) { _ ->
                        Text("Saison $n", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                episodes.filter { it.season == currentSeason }.forEach { e ->
                    EpisodeRow(e, progressById[e.id], { nav.navigate(PlayerRoute(PlayKind.EPISODE, pid, e.id, parentId = e.seriesId)) }, { episodeMenu = e })
                }
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
                "S%02dE%02d · %s".format(e.season, e.episode, e.title),
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

@Composable
private fun EpisodeRow(e: EpisodeEntity, progress: ProgressEntity?, onClick: () -> Unit, onLongClick: () -> Unit) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp), color = SokoulColors.Surface, focusScale = 1.01f) { _ ->
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(176.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(SokoulColors.SurfaceHigher)) {
                NetImage(e.image, e.title, Modifier.fillMaxSize()) {
                    Icon(Icons.Default.PlayArrow, null, tint = SokoulColors.TextMuted)
                }
                if (progress != null && progress.durationMs > 0 && !progress.finished) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.positionMs.toFloat() / progress.durationMs).background(SokoulColors.Accent))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.episode}. ${e.title}", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (progress?.finished == true) Icon(Icons.Default.CheckCircle, "Vu", Modifier.padding(start = 8.dp).size(18.dp), tint = SokoulColors.Success)
                }
                e.durationSecs?.takeIf { it > 0 }?.let { Text(formatMinutes(it / 60), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium) }
                e.plot?.let { Text(it, Modifier.padding(top = 4.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

fun versionLabel(m: MovieEntity): String = VersionPicker.label(m.toVersion())
fun seriesVersionLabel(s: SeriesEntity): String = VersionPicker.label(s.toVersion()).takeIf { it != "Version" } ?: s.name.take(28)

@Composable
private fun VersionRow(label: String, detail: String, preferred: Boolean, first: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp), color = SokoulColors.Surface, focusScale = 1.01f) { _ ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (preferred) Icons.Default.Star else Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = if (preferred) SokoulColors.Warning else SokoulColors.TextMuted)
            Text(label, Modifier.padding(start = 10.dp).width(140.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(detail, Modifier.weight(1f), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (first) Text("lancée en premier", Modifier.padding(start = 8.dp), color = SokoulColors.Accent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Mise en page commune des fiches : fond flouté, affiche, logo ou titre, contenu défilant. */
@Composable
private fun DetailLayout(backdrop: String?, poster: String?, logo: String?, title: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        NetImage(backdrop, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.TopEnd), ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(LeftFade))
        Box(Modifier.fillMaxSize().background(BottomFade))
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 48.dp)) {
            item {
                Row {
                    NetImage(poster, title, Modifier.width(170.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.padding(start = 28.dp)) {
                        if (logo != null) {
                            NetImage(logo, title, Modifier.height(90.dp).widthIn(max = 420.dp), ContentScale.Fit) {
                                Text(title, style = MaterialTheme.typography.displaySmall)
                            }
                        } else {
                            Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(12.dp))
                        content()
                    }
                }
            }
        }
    }
}

private fun openYoutube(context: android.content.Context, key: String) {
    val id = key.substringAfter("v=").substringAfterLast('/')
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
