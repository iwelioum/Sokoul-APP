package com.sokoul.iptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.data.debrid.DebridSources
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebridOfferViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    debrid: DebridSources,
    private val browse: com.sokoul.iptv.data.repo.BrowseRepository,
) : ViewModel() {
    val enabled = MutableStateFlow(false)
    var pid = 0L
        private set

    init {
        viewModelScope.launch {
            pid = playlists.activeNow()?.id ?: 0
            enabled.value = pid != 0L && debrid.searchEnabled()
        }
    }

    /** Fiche de l'abonnement si le titre y est (toutes les sources : IPTV + Real-Debrid), sinon lecture Real-Debrid seule. */
    suspend fun target(w: Work): Any? {
        val id = w.tmdbId ?: return null
        val p = pid.takeIf { it != 0L } ?: playlists.activeNow()?.id ?: return null
        browse.inCatalog(p, w.type, id)?.let { found ->
            return if (found.type == ContentType.MOVIE) com.sokoul.iptv.ui.nav.MovieDetailRoute(found.id) else com.sokoul.iptv.ui.nav.SeriesDetailRoute(found.id)
        }
        return debridRoute(p, w)
    }
}

/** Route de lecture Real-Debrid d'une œuvre hors abonnement (série : saison 1, épisode 1). */
fun debridRoute(pid: Long, w: Work): PlayerRoute? {
    val id = w.tmdbId ?: return null
    val series = w.type == ContentType.SERIES
    val q = DebridQuery(id, series, if (series) 1 else null, if (series) 1 else null)
    return PlayerRoute(PlayKind.DEBRID, pid, q.encode(), query = q.encode(), title = w.title)
}

/**
 * « Lire via Real-Debrid » pour une œuvre hors abonnement (sagas, page d'un acteur, Ma liste).
 * Null tant que Real-Debrid n'est pas configuré (clé + extension de sources).
 */
@Composable
fun rememberDebridPlay(nav: NavHostController): ((Work) -> Unit)? {
    val vm: DebridOfferViewModel = hiltViewModel()
    val enabled by vm.enabled.collectAsState()
    if (!enabled) return null
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return { w -> scope.launch { vm.target(w)?.let { nav.navigateOnce(it) } } }
}
