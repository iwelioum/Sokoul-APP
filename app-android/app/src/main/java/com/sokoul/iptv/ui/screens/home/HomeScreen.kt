package com.sokoul.iptv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.EpgRepository
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.data.repo.VodRepository
import kotlinx.coroutines.flow.map
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.BottomFade
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.ContentRow
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.LeftFade
import com.sokoul.iptv.ui.components.LoadingBox
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.PosterCard
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.nav.SettingsRoute
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.rememberNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val playlist: PlaylistEntity? = null,
    val continueWatching: List<ProgressEntity> = emptyList(),
    val favoriteChannels: List<ChannelEntity> = emptyList(),
    val recentChannels: List<ChannelEntity> = emptyList(),
    val nowPlaying: Map<String, ProgramEntity> = emptyMap(),
    val recentMovies: List<MovieEntity> = emptyList(),
    val recentSeries: List<SeriesEntity> = emptyList(),
    val favoriteMovies: List<MovieEntity> = emptyList(),
    val favoriteSeries: List<SeriesEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    playlists: PlaylistRepository,
    catalog: CatalogRepository,
    library: LibraryRepository,
    private val epg: EpgRepository,
    private val vodRepo: VodRepository,
) : ViewModel() {

    val state: StateFlow<HomeState> = playlists.active.flatMapLatest { p ->
        if (p == null) return@flatMapLatest flowOf(HomeState(loading = false))
        val pid = p.id
        val channels: Flow<Pair<List<ChannelEntity>, List<ChannelEntity>>> =
            combine(catalog.channels(pid, SpecialCategory.FAVORITES), catalog.channels(pid, SpecialCategory.RECENT)) { f, r -> f to r }
        // une carte par œuvre (doublons VF/MULTI/4K regroupés si le réglage est actif)
        fun movies(f: Flow<List<MovieEntity>>) = vodRepo.movieCards(pid, f).map { l -> l.map { it.item } }
        fun series(f: Flow<List<SeriesEntity>>) = vodRepo.seriesCards(pid, f).map { l -> l.map { it.item } }
        val cwFlow = library.continueWatching(pid).mapLatest { l -> vodRepo.dedupeProgress(pid, l, { it.type }, { if (it.type == ContentType.SERIES) it.parentId ?: it.itemId else it.itemId }) }
        val vod = combine(movies(catalog.recentMovies(pid)), series(catalog.recentSeries(pid)), movies(catalog.favoriteMovies(pid)), series(catalog.favoriteSeries(pid))) { a, b, c, d ->
            listOf(a, b, c, d)
        }
        combine(cwFlow, channels, vod) { cw, ch, v ->
            @Suppress("UNCHECKED_CAST")
            HomeState(
                loading = false, playlist = p, continueWatching = cw,
                favoriteChannels = ch.first, recentChannels = ch.second.take(15),
                recentMovies = v[0] as List<MovieEntity>, recentSeries = v[1] as List<SeriesEntity>,
                favoriteMovies = v[2] as List<MovieEntity>, favoriteSeries = v[3] as List<SeriesEntity>,
            )
        }.mapLatest { s -> s.copy(nowPlaying = epg.nowFor(pid, (s.favoriteChannels + s.recentChannels).distinctBy { it.id })) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@Composable
fun HomeScreen(nav: NavHostController) {
    val vm: HomeViewModel = hiltViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val p = s.playlist
    if (s.loading) { LoadingBox(); return }
    if (p == null) {
        EmptyState(Icons.Default.LiveTv, "Aucune playlist", "Ajoutez un abonnement dans les réglages.") {
            AppButton("Réglages", { nav.navigate(SettingsRoute) }, primary = true)
        }
        return
    }
    val pid = p.id
    val hero = s.continueWatching.firstOrNull()
    val heroMovie = s.recentMovies.firstOrNull()
    val firstFocus = remember { FocusRequester() }
    val empty = p.liveCount + p.movieCount + p.seriesCount == 0

    LazyColumn(Modifier.fillMaxSize()) {
        item("hero") {
            Hero(
                playlist = p,
                title = hero?.title ?: heroMovie?.title ?: p.name,
                subtitle = hero?.subtitle ?: heroMovie?.let { listOfNotNull(it.year?.toString(), it.quality, it.language).joinToString(" · ") },
                image = hero?.image ?: heroMovie?.poster,
                actionLabel = when {
                    hero != null -> "Reprendre"
                    heroMovie != null -> "Voir le film"
                    else -> null
                },
                onAction = {
                    when {
                        hero != null -> nav.navigate(resumeRoute(pid, hero))
                        heroMovie != null -> nav.navigate(MovieDetailRoute(heroMovie.id))
                    }
                },
                focusRequester = firstFocus,
            )
        }
        if (empty) {
            item("empty") {
                Text(
                    "La playlist est vide ou pas encore synchronisée. Lancez une synchronisation depuis Réglages › Playlists.",
                    Modifier.padding(24.dp), color = SokoulColors.TextSecondary,
                )
            }
        }
        item("continue") {
            ContentRow("Reprendre la lecture", s.continueWatching, key = { "${it.type}:${it.itemId}" }) { _, it ->
                PosterCard(
                    title = it.title, image = it.image, width = 240.dp, landscape = true, subtitle = it.subtitle,
                    progress = if (it.durationMs > 0) it.positionMs.toFloat() / it.durationMs else null,
                    onClick = { nav.navigate(resumeRoute(pid, it)) },
                )
            }
        }
        item("fav-ch") {
            ContentRow("Chaînes favorites", s.favoriteChannels, key = { it.id }) { _, c ->
                ChannelCard(c, s.nowPlaying[c.id]) { nav.navigate(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = SpecialCategory.FAVORITES)) }
            }
        }
        item("recent-ch") {
            ContentRow("Regardées récemment", s.recentChannels, key = { it.id }) { _, c ->
                ChannelCard(c, s.nowPlaying[c.id]) { nav.navigate(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = c.categoryId)) }
            }
        }
        item("movies") {
            ContentRow("Films ajoutés récemment", s.recentMovies, key = { it.id }) { _, m ->
                PosterCard(m.title, m.poster, { nav.navigate(MovieDetailRoute(m.id)) }, badge = m.quality, subtitle = m.year?.toString())
            }
        }
        item("series") {
            ContentRow("Séries mises à jour", s.recentSeries, key = { it.id }) { _, x ->
                PosterCard(x.title, x.poster, { nav.navigate(SeriesDetailRoute(x.id)) }, subtitle = x.year?.toString())
            }
        }
        item("fav-movies") {
            ContentRow("Ma liste — films", s.favoriteMovies, key = { it.id }) { _, m ->
                PosterCard(m.title, m.poster, { nav.navigate(MovieDetailRoute(m.id)) }, badge = m.quality)
            }
        }
        item("fav-series") {
            ContentRow("Ma liste — séries", s.favoriteSeries, key = { it.id }) { _, x ->
                PosterCard(x.title, x.poster, { nav.navigate(SeriesDetailRoute(x.id)) })
            }
        }
        item("bottom") { Spacer(Modifier.height(48.dp)) }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

fun resumeRoute(pid: Long, p: ProgressEntity): Any = when (p.type) {
    ContentType.MOVIE -> PlayerRoute(PlayKind.MOVIE, pid, p.itemId)
    ContentType.SERIES -> PlayerRoute(PlayKind.EPISODE, pid, p.itemId, parentId = p.parentId)
    ContentType.LIVE -> PlayerRoute(PlayKind.LIVE, pid, p.itemId)
}

@Composable
private fun Hero(
    playlist: PlaylistEntity,
    title: String,
    subtitle: String?,
    image: String?,
    actionLabel: String?,
    onAction: () -> Unit,
    focusRequester: FocusRequester,
) {
    val now = rememberNow()
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        NetImage(image, null, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(LeftFade))
        Box(Modifier.fillMaxSize().background(BottomFade))
        Row(Modifier.align(Alignment.TopEnd).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            val exp = playlist.expiresAt
            if (exp != null && exp - now < 7 * 24 * 3600_000L) {
                Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = SokoulColors.Warning)
                Text(
                    if (exp < now) "Abonnement expiré" else "Abonnement : expire dans ${((exp - now) / (24 * 3600_000L)).coerceAtLeast(0)} j",
                    Modifier.padding(start = 6.dp, end = 16.dp), color = SokoulColors.Warning, style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(formatClock(now), style = MaterialTheme.typography.titleLarge, color = SokoulColors.TextPrimary)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp).widthIn(max = 640.dp)) {
            Text(playlist.name.uppercase(), color = SokoulColors.Accent, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null) {
                AppButton(actionLabel, onAction, Modifier.padding(top = 14.dp), icon = Icons.Default.PlayArrow, primary = true, focusRequester = focusRequester)
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}

@Composable
fun ChannelCard(c: ChannelEntity, now: ProgramEntity?, onClick: () -> Unit) {
    Column(Modifier.width(190.dp)) {
        FocusSurface(onClick = onClick) { _ ->
            Column {
                Box(Modifier.fillMaxWidth().height(96.dp).background(SokoulColors.SurfaceHigher), contentAlignment = Alignment.Center) {
                    ChannelLogo(c.logo, c.name, Modifier.size(140.dp, 72.dp))
                }
                if (now != null) {
                    val t = System.currentTimeMillis()
                    val f = ((t - now.start).toFloat() / (now.end - now.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(SokoulColors.Accent))
                    }
                }
            }
        }
        Text(c.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(now?.title ?: "—", style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
