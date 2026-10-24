package com.sokoul.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sokoul.iptv.PlayIntent
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.ui.components.LoadingBox
import com.sokoul.iptv.ui.nav.AllCategoriesRoute
import com.sokoul.iptv.ui.nav.BrowseGridRoute
import com.sokoul.iptv.ui.nav.GuideRoute
import com.sokoul.iptv.ui.nav.HomeRoute
import com.sokoul.iptv.ui.nav.LiveRoute
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.MoviesRoute
import com.sokoul.iptv.ui.nav.MultiviewRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.PlaylistEditRoute
import com.sokoul.iptv.ui.nav.RecordingsRoute
import com.sokoul.iptv.ui.nav.SearchRoute
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.nav.SeriesRoute
import com.sokoul.iptv.ui.nav.SettingsRoute
import com.sokoul.iptv.ui.nav.SyncRoute
import com.sokoul.iptv.ui.screens.browse.BrowseGridScreen
import com.sokoul.iptv.ui.screens.browse.BrowseScreen
import com.sokoul.iptv.ui.screens.browse.MoviesBrowseViewModel
import com.sokoul.iptv.ui.screens.browse.SeriesBrowseViewModel
import com.sokoul.iptv.ui.screens.guide.GuideScreen
import com.sokoul.iptv.ui.screens.home.HomeScreen
import com.sokoul.iptv.ui.screens.live.LiveScreen
import com.sokoul.iptv.ui.screens.multiview.MultiviewScreen
import com.sokoul.iptv.ui.screens.player.PlayerScreen
import com.sokoul.iptv.ui.screens.playlist.PlaylistEditScreen
import com.sokoul.iptv.ui.screens.playlist.SyncScreen
import com.sokoul.iptv.ui.screens.recordings.RecordingsScreen
import com.sokoul.iptv.ui.screens.search.SearchScreen
import com.sokoul.iptv.ui.screens.settings.SettingsScreen
import com.sokoul.iptv.ui.screens.vod.MovieDetailScreen
import com.sokoul.iptv.ui.screens.vod.MoviesScreen
import com.sokoul.iptv.ui.screens.vod.SeriesDetailScreen
import com.sokoul.iptv.ui.screens.vod.SeriesScreen
import com.sokoul.iptv.ui.theme.SokoulColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    playlists: PlaylistRepository,
    private val catalog: com.sokoul.iptv.data.repo.CatalogRepository,
) : ViewModel() {
    /** null = chargement ; false = aucune playlist (premier lancement). */
    val hasPlaylist: StateFlow<Boolean?> = playlists.playlists.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Demande externe : ignorée si la chaîne n'existe pas (le lecteur applique ensuite le contrôle parental). */
    suspend fun isPlayable(p: PlayIntent): Boolean = catalog.channel(p.playlistId, p.channelId) != null
}

@Composable
fun AppRoot(pendingPlay: StateFlow<PlayIntent?>, onPlayConsumed: () -> Unit) {
    val vm: RootViewModel = hiltViewModel()
    val hasPlaylist by vm.hasPlaylist.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        when (hasPlaylist) {
            null -> LoadingBox()
            else -> {
                val nav = rememberNavController()
                // destination de départ figée : l'ajout de la 1re playlist ne doit pas réinitialiser la navigation
                val start = remember { if (hasPlaylist == true) HomeRoute else PlaylistEditRoute(firstRun = true) }
                AppNavHost(nav, start = start)
                val play by pendingPlay.collectAsStateWithLifecycle()
                LaunchedEffect(play) {
                    play?.let {
                        if (vm.isPlayable(it)) nav.navigate(PlayerRoute(PlayKind.LIVE, it.playlistId, it.channelId))
                        onPlayConsumed()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, start: Any) {
    NavHost(nav, startDestination = start) {
        composable<HomeRoute> { Shell(nav, Dest.HOME, edgeToEdge = true) { HomeScreen(nav) } }
        composable<LiveRoute> { Shell(nav, Dest.LIVE) { LiveScreen(nav) } }
        composable<GuideRoute> { Shell(nav, Dest.GUIDE) { GuideScreen(nav) } }
        composable<MoviesRoute> { Shell(nav, Dest.MOVIES, edgeToEdge = true) { BrowseScreen(nav, hiltViewModel<MoviesBrowseViewModel>(), "Films") } }
        composable<SeriesRoute> { Shell(nav, Dest.SERIES, edgeToEdge = true) { BrowseScreen(nav, hiltViewModel<SeriesBrowseViewModel>(), "Séries") } }
        composable<AllCategoriesRoute> {
            val movies = it.toRoute<AllCategoriesRoute>().type == "MOVIE"
            Shell(nav, if (movies) Dest.MOVIES else Dest.SERIES) { if (movies) MoviesScreen(nav) else SeriesScreen(nav) }
        }
        composable<BrowseGridRoute> { val r = it.toRoute<BrowseGridRoute>(); BrowseGridScreen(nav, r.title, r.spec) }
        composable<SearchRoute> { Shell(nav, Dest.SEARCH) { SearchScreen(nav) } }
        composable<RecordingsRoute> { Shell(nav, Dest.RECORDINGS) { RecordingsScreen(nav) } }
        composable<SettingsRoute> { Shell(nav, Dest.SETTINGS) { SettingsScreen(nav) } }
        composable<MovieDetailRoute> { MovieDetailScreen(nav, it.toRoute<MovieDetailRoute>().id) }
        composable<SeriesDetailRoute> { SeriesDetailScreen(nav, it.toRoute<SeriesDetailRoute>().id) }
        composable<PlayerRoute> { PlayerScreen(nav, it.toRoute()) }
        composable<MultiviewRoute> { MultiviewScreen(nav) }
        composable<PlaylistEditRoute> { val r = it.toRoute<PlaylistEditRoute>(); PlaylistEditScreen(nav, r.id, r.firstRun) }
        composable<SyncRoute> { SyncScreen(nav, it.toRoute<SyncRoute>().playlistId) }
    }
}
