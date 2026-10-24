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

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    val details = MutableStateFlow<MovieDetails?>(null)
    val error = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow<ProgressEntity?>(null)
    val favorite = MutableStateFlow(false)
    var playlist: PlaylistEntity? = null

    fun load(id: String) = viewModelScope.launch {
        if (details.value?.movie?.id == id) return@launch
        val p = playlists.activeNow() ?: return@launch
        playlist = p
        val m = catalog.movie(p.id, id) ?: run { error.value = "Film introuvable"; return@launch }
        details.value = MovieDetails(m, null, null)
        launch { library.isFavorite(p.id, ContentType.MOVIE, id).collect { favorite.value = it } }
        progress.value = library.progress(p.id, ContentType.MOVIE, id)
        try {
            details.value = catalog.movieDetails(p, m)
        } catch (e: Exception) {
            error.value = e.userMessage()
        }
    }

    fun refreshProgress(id: String) = viewModelScope.launch { playlist?.let { progress.value = library.progress(it.id, ContentType.MOVIE, id) } }
    fun toggleFavorite(id: String) = viewModelScope.launch { playlist?.let { library.toggleFavorite(it.id, ContentType.MOVIE, id) } }
    fun clearProgress(id: String) = viewModelScope.launch { playlist?.let { library.clearProgress(it.id, ContentType.MOVIE, id); progress.value = null } }
}

@Composable
fun MovieDetailScreen(nav: NavHostController, id: String) {
    val vm: MovieDetailViewModel = hiltViewModel()
    val d by vm.details.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(Unit) { vm.refreshProgress(id) }

    val details = d ?: run { LoadingBox(); return }
    val m = details.movie
    val tm = details.tmdb
    val xt = details.xtream
    val pid = vm.playlist?.id ?: return
    val backdrop = tm?.backdrop ?: xt?.backdrop ?: tm?.poster ?: xt?.poster ?: m.poster
    val plot = xt?.plot ?: tm?.overview
    val durationMin = xt?.durationSecs?.div(60) ?: tm?.runtimeMin
    val resume = progress?.takeIf { !it.finished && it.positionMs > 30_000 }

    DetailLayout(backdrop = backdrop, poster = tm?.poster ?: m.poster, logo = tm?.logo, title = m.title) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            (m.year ?: tm?.year)?.let { Tag(it.toString()) }
            durationMin?.takeIf { it > 0 }?.let { Tag(formatMinutes(it)) }
            (tm?.rating ?: xt?.rating ?: m.rating)?.let { Tag("★ %.1f".format(it), SokoulColors.Warning.copy(alpha = 0.2f), SokoulColors.Warning) }
            m.quality?.let { Tag(it, SokoulColors.AccentSoft, SokoulColors.Accent) }
            m.language?.let { Tag(it) }
        }
        val genres = tm?.genres?.joinToString(", ") ?: xt?.genre
        if (!genres.isNullOrBlank()) Text(genres, Modifier.padding(top = 10.dp), color = SokoulColors.TextSecondary)
        if (!plot.isNullOrBlank()) Text(plot, Modifier.padding(top = 12.dp).widthIn(max = 760.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
        val director = tm?.director ?: xt?.director
        if (!director.isNullOrBlank()) Text("Réalisation : $director", Modifier.padding(top = 10.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)

        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(
                if (resume != null) "Reprendre à ${formatDuration(resume.positionMs)}" else "Lecture",
                { nav.navigate(PlayerRoute(PlayKind.MOVIE, pid, m.id)) }, icon = Icons.Default.PlayArrow, primary = true, focusRequester = playFocus,
            )
            if (resume != null) AppButton("Depuis le début", { vm.clearProgress(m.id); nav.navigate(PlayerRoute(PlayKind.MOVIE, pid, m.id)) }, icon = Icons.Default.Replay)
            AppButton(if (favorite) "Dans ma liste" else "Ma liste", { vm.toggleFavorite(m.id) }, icon = if (favorite) Icons.Default.Check else Icons.Default.Add)
            val trailer = tm?.trailerYoutubeKey ?: xt?.trailer
            if (!trailer.isNullOrBlank()) AppButton("Bande-annonce", { openYoutube(context, trailer) }, icon = Icons.Default.Theaters)
        }
        if (resume != null && resume.durationMs > 0) {
            Box(Modifier.padding(top = 12.dp).width(320.dp).height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(resume.positionMs.toFloat() / resume.durationMs).background(SokoulColors.Accent, RoundedCornerShape(2.dp)))
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
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

// --- Série ---------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val key = MutableStateFlow<Pair<Long, String>?>(null)
    var playlist: PlaylistEntity? = null
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)
    val tmdb = MutableStateFlow<TmdbInfo?>(null)

    val series: StateFlow<SeriesEntity?> = key.filterNotNull().flatMapLatest { (p, s) -> catalog.observeSeries(p, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val details: StateFlow<SeriesDetailsEntity?> = key.filterNotNull().flatMapLatest { (p, s) -> catalog.seriesDetails(p, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val episodes: StateFlow<List<EpisodeEntity>> = key.filterNotNull().flatMapLatest { (p, s) -> catalog.episodes(p, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progress: StateFlow<List<ProgressEntity>> = key.filterNotNull().flatMapLatest { (p, s) -> library.seriesProgress(p, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorite: StateFlow<Boolean> = key.filterNotNull().flatMapLatest { (p, s) -> library.isFavorite(p, ContentType.SERIES, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun load(id: String, force: Boolean = false) = viewModelScope.launch {
        if (!force && key.value?.second == id) return@launch
        val p = playlists.activeNow() ?: return@launch
        playlist = p
        key.value = p.id to id
        loading.value = true
        error.value = null
        try {
            catalog.ensureEpisodes(p, id, force)
        } catch (e: Exception) {
            error.value = e.userMessage()
        } finally {
            loading.value = false
        }
        catalog.seriesItem(p.id, id)?.let { tmdb.value = catalog.seriesTmdb(it) }
    }

    fun toggleFavorite(id: String) = viewModelScope.launch { playlist?.let { library.toggleFavorite(it.id, ContentType.SERIES, id) } }
    fun setWatched(e: EpisodeEntity, watched: Boolean) = viewModelScope.launch {
        playlist?.let { library.setWatched(it.id, ContentType.SERIES, e.id, e.seriesId, e.title, watched) }
    }
    fun setSeasonWatched(list: List<EpisodeEntity>, watched: Boolean) = viewModelScope.launch {
        playlist?.let { p -> list.forEach { e -> library.setWatched(p.id, ContentType.SERIES, e.id, e.seriesId, e.title, watched) } }
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
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    var season by remember { mutableStateOf<Int?>(null) }
    var episodeMenu by remember { mutableStateOf<EpisodeEntity?>(null) }
    LaunchedEffect(id) { vm.load(id) }

    val series = s ?: run { LoadingBox(); return }
    val pid = vm.playlist?.id ?: return
    val progressById = progress.associateBy { it.itemId }
    val seasons = episodes.map { it.season }.distinct().sorted()
    val currentSeason = season ?: progress.maxByOrNull { it.updatedAt }?.let { last -> episodes.firstOrNull { it.id == last.itemId }?.season } ?: seasons.firstOrNull()

    // prochain épisode à regarder : celui en cours, sinon le suivant du dernier vu, sinon le premier
    val last = progress.maxByOrNull { it.updatedAt }
    val resumeEpisode = last?.let { lp ->
        val i = episodes.indexOfFirst { it.id == lp.itemId }
        when {
            i < 0 -> null
            !lp.finished -> episodes[i]
            else -> episodes.getOrNull(i + 1)
        }
    } ?: episodes.firstOrNull()
    val resumeProgress = resumeEpisode?.let { progressById[it.id] }?.takeIf { !it.finished && it.positionMs > 30_000 }

    DetailLayout(
        backdrop = tmdb?.backdrop ?: details?.backdrop ?: series.backdrop ?: series.poster,
        poster = tmdb?.poster ?: series.poster, logo = tmdb?.logo, title = series.title,
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
                    { nav.navigate(PlayerRoute(PlayKind.EPISODE, pid, resumeEpisode.id, parentId = series.id)) },
                    icon = Icons.Default.PlayArrow, primary = true, focusRequester = playFocus,
                )
            }
            AppButton(if (favorite) "Dans ma liste" else "Ma liste", { vm.toggleFavorite(series.id) }, icon = if (favorite) Icons.Default.Check else Icons.Default.Add)
            val trailer = tmdb?.trailerYoutubeKey ?: details?.trailer
            if (!trailer.isNullOrBlank()) AppButton("Bande-annonce", { openYoutube(context, trailer) }, icon = Icons.Default.Theaters)
            AppButton("Actualiser", { vm.load(series.id, force = true) }, icon = Icons.Default.Refresh)
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
                    EpisodeRow(e, progressById[e.id], { nav.navigate(PlayerRoute(PlayKind.EPISODE, pid, e.id, parentId = series.id)) }, { episodeMenu = e })
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
                    MenuOption("Lecture", Icons.Default.PlayArrow) { nav.navigate(PlayerRoute(PlayKind.EPISODE, pid, e.id, parentId = series.id)) },
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
