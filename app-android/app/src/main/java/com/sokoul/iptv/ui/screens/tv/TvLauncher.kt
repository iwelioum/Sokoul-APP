package com.sokoul.iptv.ui.screens.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.ui.components.LoadingBox
import com.sokoul.iptv.ui.nav.LiveRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.TvRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TvLauncherViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val settings: SettingsStore,
    private val db: AppDatabase,
) : ViewModel() {
    /** Chaîne d'arrivée dans l'univers TV : la dernière regardée, sinon la première de la liste. */
    suspend fun target(): PlayerRoute? {
        val p = playlists.activeNow() ?: return null
        val last = settings.current().lastChannelId.takeIf { it.isNotBlank() }?.let { db.channels().get(p.id, it) }
        val c = last ?: db.channels().listAllVisible(p.id).firstOrNull() ?: return null
        return PlayerRoute(PlayKind.LIVE, p.id, c.id, categoryId = c.categoryId)
    }
}

/**
 * Entrée de l'univers TV : la télé s'allume directement sur une chaîne, en plein écran, sans menu.
 * Cet écran ne reste pas dans l'historique : Retour depuis la chaîne revient au Streaming.
 */
@Composable
fun TvLauncher(nav: NavHostController) {
    val vm: TvLauncherViewModel = hiltViewModel()
    LoadingBox()
    LaunchedEffect(Unit) {
        val to: Any = vm.target() ?: LiveRoute
        nav.navigate(to) { popUpTo<TvRoute> { inclusive = true } }
    }
}
