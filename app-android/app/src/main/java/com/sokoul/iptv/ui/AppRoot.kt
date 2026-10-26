package com.sokoul.iptv.ui

import com.sokoul.iptv.ui.nav.navigateOnce
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.sokoul.iptv.ui.nav.AllCategoriesRoute
import com.sokoul.iptv.ui.nav.BrowseGridRoute
import com.sokoul.iptv.ui.nav.GuideRoute
import com.sokoul.iptv.ui.nav.HomeRoute
import com.sokoul.iptv.ui.nav.LiveRoute
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.MoviesRoute
import com.sokoul.iptv.ui.nav.MyListRoute
import com.sokoul.iptv.ui.nav.TvRoute
import com.sokoul.iptv.data.repo.GridSpec
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
    private val settings: com.sokoul.iptv.data.SettingsStore,
    private val catalog: com.sokoul.iptv.data.repo.CatalogRepository,
) : ViewModel() {
    /** null = chargement ; false = aucune playlist (premier lancement). */
    val hasPlaylist: StateFlow<Boolean?> = playlists.playlists.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Réglage « Au démarrage : dernière chaîne regardée ». */
    suspend fun startOnTv(): Boolean = settings.current().resumeLastChannel

    /** Demande externe : ignorée si la chaîne n'existe pas (le lecteur applique ensuite le contrôle parental). */
    suspend fun isPlayable(p: PlayIntent): Boolean = catalog.channel(p.playlistId, p.channelId) != null
}

@Composable
fun AppRoot(pendingPlay: StateFlow<PlayIntent?>, onPlayConsumed: () -> Unit) {
    val vm: RootViewModel = hiltViewModel()
    val hasPlaylist by vm.hasPlaylist.collectAsStateWithLifecycle()
    // pas de fond ici : celui de la fenêtre (même couleur) suffit — chaque fond plein écran en plus coûte une passe GPU
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            // bandeau de rappel affiché : ⏯ ouvre la chaîne, Retour l'ignore (appui et relâchement consommés)
            val code = e.nativeKeyEvent.keyCode
            if (com.sokoul.iptv.recording.ReminderBus.current.value == null) false
            else if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) com.sokoul.iptv.ui.components.ReminderKeys.handle(code)
            else code == android.view.KeyEvent.KEYCODE_BACK || code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY
        },
    ) {
        when (hasPlaylist) {
            null -> com.sokoul.iptv.ui.components.BrandSplash()
            else -> {
                val nav = rememberNavController()
                // destination de départ figée : l'ajout de la 1re playlist ne doit pas réinitialiser la navigation
                val start = remember { if (hasPlaylist == true) HomeRoute else PlaylistEditRoute(firstRun = true) }
                AppNavHost(nav, start = start)
                // TV : « Au démarrage : dernière chaîne » → la télé s'allume sur la chaîne (Retour = Accueil)
                val isTv = com.sokoul.iptv.ui.theme.LocalLayout.current.isTv
                LaunchedEffect(Unit) { if (isTv && start == HomeRoute && vm.startOnTv()) nav.navigateOnce(TvRoute) }
                val play by pendingPlay.collectAsStateWithLifecycle()
                LaunchedEffect(play) {
                    play?.let {
                        if (vm.isPlayable(it)) nav.navigateOnce(PlayerRoute(PlayKind.LIVE, it.playlistId, it.channelId))
                        onPlayConsumed()
                    }
                }
                com.sokoul.iptv.ui.components.ReminderBanner { r -> nav.navigateOnce(PlayerRoute(PlayKind.LIVE, r.playlistId, r.channelId)) }
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, start: Any) {
    // fondu court : la page suivante répond tout de suite et deux pages lourdes ne se superposent pas longtemps
    NavHost(
        nav, startDestination = start,
        enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(PAGE_FADE_MS)) },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(PAGE_FADE_MS)) },
    ) {
        composable<HomeRoute> { Shell(nav, Dest.HOME, edgeToEdge = true) { HomeScreen(nav) } }
        composable<LiveRoute> { Shell(nav, Dest.LIVE) { LiveScreen(nav) } }
        composable<GuideRoute> { Shell(nav, Dest.GUIDE) { GuideScreen(nav) } }
        composable<MoviesRoute> { Shell(nav, Dest.MOVIES, edgeToEdge = true) { BrowseScreen(nav, hiltViewModel<MoviesBrowseViewModel>(), "Films") } }
        composable<SeriesRoute> { Shell(nav, Dest.SERIES, edgeToEdge = true) { BrowseScreen(nav, hiltViewModel<SeriesBrowseViewModel>(), "Séries") } }
        composable<TvRoute> { com.sokoul.iptv.ui.screens.tv.TvLauncher(nav) }
        composable<MyListRoute> {
            Shell(nav, Dest.MY_LIST, edgeToEdge = true) {
                BrowseGridScreen(
                    nav, "Ma liste", remember { GridSpec.Favorites(null).encode() },
                    emptyTitle = "Votre liste est vide",
                    emptyText = "Sur la fiche d'un film ou d'une série, choisissez « Ma liste » pour le retrouver ici.",
                )
            }
        }
        composable<AllCategoriesRoute> {
            val movies = it.toRoute<AllCategoriesRoute>().type == "MOVIE"
            Shell(nav, if (movies) Dest.MOVIES else Dest.SERIES, bare = true) { if (movies) MoviesScreen(nav) else SeriesScreen(nav) }
        }
        composable<com.sokoul.iptv.ui.nav.SagasRoute> { Shell(nav, Dest.SAGAS, edgeToEdge = true) { com.sokoul.iptv.ui.screens.saga.SagasHubScreen(nav) } }
        composable<com.sokoul.iptv.ui.nav.SagaRoute> { com.sokoul.iptv.ui.screens.saga.SagaScreen(nav, it.toRoute<com.sokoul.iptv.ui.nav.SagaRoute>().id) }
        composable<com.sokoul.iptv.ui.nav.PersonRoute>(enterTransition = { detailEnter() }) { val r = it.toRoute<com.sokoul.iptv.ui.nav.PersonRoute>(); com.sokoul.iptv.ui.screens.person.PersonScreen(nav, r.id, r.name) }
        composable<BrowseGridRoute> { val r = it.toRoute<BrowseGridRoute>(); BrowseGridScreen(nav, r.title, r.spec) }
        composable<com.sokoul.iptv.ui.nav.ExploreRoute> { com.sokoul.iptv.ui.screens.browse.ExploreScreen(nav, it.toRoute<com.sokoul.iptv.ui.nav.ExploreRoute>().type) }
        composable<SearchRoute> { Shell(nav, Dest.SEARCH) { SearchScreen(nav, it.toRoute<SearchRoute>().origin) } }
        composable<RecordingsRoute> { Shell(nav, Dest.RECORDINGS) { RecordingsScreen(nav) } }
        composable<SettingsRoute> { Shell(nav, Dest.SETTINGS) { SettingsScreen(nav) } }
        composable<MovieDetailRoute>(enterTransition = { detailEnter() }) { MovieDetailScreen(nav, it.toRoute<MovieDetailRoute>().id) }
        composable<SeriesDetailRoute>(enterTransition = { detailEnter() }) { SeriesDetailScreen(nav, it.toRoute<SeriesDetailRoute>().id) }
        composable<PlayerRoute> { PlayerScreen(nav, it.toRoute()) }
        composable<MultiviewRoute> { MultiviewScreen(nav) }
        composable<PlaylistEditRoute> { val r = it.toRoute<PlaylistEditRoute>(); PlaylistEditScreen(nav, r.id, r.firstRun) }
        composable<SyncRoute> { SyncScreen(nav, it.toRoute<SyncRoute>().playlistId) }
    }
}

/** Durée du fondu entre deux pages. */
private const val PAGE_FADE_MS = 220

/** Fiche ou page acteur : elle « sort » de la carte choisie (fondu + léger agrandissement, 260 ms). */
private fun detailEnter() =
    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(PAGE_FADE_MS)) +
        androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = 0.96f)
