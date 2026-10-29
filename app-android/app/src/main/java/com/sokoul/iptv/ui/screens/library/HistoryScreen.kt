package com.sokoul.iptv.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.ui.components.ConfirmDialog
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.ScreenHeader
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Règles de l'historique (pures, testées) : où mène une ligne, regroupement par jour, ligne d'état. */
object HistoryRules {
    /** Destination d'une ligne : fiche du film ou de la série, chaîne, ou relance d'un titre Real-Debrid. */
    fun route(p: ProgressEntity): Any? = when {
        p.type == ContentType.LIVE -> PlayerRoute(PlayKind.LIVE, p.playlistId, p.itemId)
        p.itemId.startsWith(CLOUD) -> PlayerRoute(PlayKind.CLOUD, p.playlistId, p.itemId.removePrefix(CLOUD), title = p.title)
        p.type == ContentType.MOVIE && p.itemId.startsWith(DEBRID) -> PlayerRoute(PlayKind.DEBRID, p.playlistId, p.itemId.removePrefix(DEBRID), title = p.title)
        p.type == ContentType.MOVIE -> MovieDetailRoute(p.itemId)
        p.parentId?.startsWith(DEBRID) == true || p.itemId.startsWith(DEBRID) ->
            PlayerRoute(PlayKind.DEBRID, p.playlistId, p.itemId.removePrefix(DEBRID), title = p.title)
        p.parentId != null -> SeriesDetailRoute(p.parentId)
        else -> null
    }

    /** Sections « Aujourd'hui », « Hier », « Lun. 27 oct. », dans l'ordre reçu (le plus récent d'abord). */
    fun byDay(list: List<ProgressEntity>, now: Long, label: (Long, Long) -> String): List<Pair<String, List<ProgressEntity>>> =
        list.groupBy { label(it.updatedAt, now) }.toList()

    /** « 20:30 · vu », « 20:30 · 45 % », « 20:30 » (chaîne). */
    fun status(p: ProgressEntity, clock: String): String = when {
        p.type == ContentType.LIVE -> clock
        p.finished -> "$clock · vu"
        p.durationMs > 0 -> "$clock · ${(p.positionMs * 100 / p.durationMs).coerceIn(1, 99)} %"
        else -> clock
    }

    private const val DEBRID = "tmdb:"
    private val CLOUD = com.sokoul.iptv.data.debrid.DebridCloud.KEY_PREFIX
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val pid = kotlinx.coroutines.flow.flow { emit(playlists.activeNow()?.id) }
    val items: StateFlow<List<ProgressEntity>?> = pid.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else library.history(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun remove(p: ProgressEntity) = viewModelScope.launch { library.clearProgress(p.playlistId, p.type, p.itemId) }
    fun clearAll() = viewModelScope.launch { playlists.activeNow()?.id?.let { library.clearHistory(it) } }
}

@Composable
fun HistoryScreen(nav: NavHostController) {
    val vm: HistoryViewModel = hiltViewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    var menu by remember { mutableStateOf<ProgressEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = items ?: return
    val now = remember(list) { System.currentTimeMillis() }
    val sections = remember(list) { HistoryRules.byDay(list, now, ::dayLabel) }
    val open: (ProgressEntity) -> Unit = { p -> HistoryRules.route(p)?.let { nav.navigateOnce(it) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = l.margin, end = l.margin, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item("head") {
            ScreenHeader("Historique", subtitle = if (list.isEmpty()) null else "${list.size} titre${if (list.size > 1) "s" else ""} regardé${if (list.size > 1) "s" else ""}") {
                if (list.isNotEmpty()) SokoulButton("Tout effacer", { confirmClear = true }, icon = Icons.Default.DeleteSweep)
            }
        }
        if (list.isEmpty()) item("empty") {
            EmptyState(Icons.Default.History, "Rien pour l'instant", "Les films, épisodes et chaînes que vous regardez apparaîtront ici, du plus récent au plus ancien.", Modifier.padding(top = 40.dp))
        }
        sections.forEach { (day, rows) ->
            item("d:$day") { Text(day.uppercase(), Modifier.padding(top = 18.dp, bottom = 4.dp), style = Type.Kicker) }
            items(rows, key = { "${it.type}|${it.itemId}" }) { p ->
                HistoryRow(p, onClick = { if (HistoryRules.route(p) != null) open(p) else menu = p }, onLongClick = { menu = p })
            }
        }
    }

    menu?.let { p ->
        OptionsDialog(
            p.title,
            listOfNotNull(
                HistoryRules.route(p)?.let { MenuOption(if (p.type == ContentType.LIVE) "Regarder" else "Ouvrir", if (p.type == ContentType.LIVE) Icons.Default.PlayArrow else Icons.Default.Info) { open(p) } },
                MenuOption("Retirer de l'historique", Icons.Default.Remove) { vm.remove(p) },
            ),
            subtitle = if (p.type == ContentType.LIVE) null else "Retirer efface aussi le point de reprise de ce titre.",
            onDismiss = { menu = null },
        )
    }
    if (confirmClear) ConfirmDialog(
        "Effacer l'historique ?", "Tout l'historique de ce profil et ses points de reprise sont effacés. Les titres marqués « vu » à la main le restent.",
        "Tout effacer", onConfirm = { vm.clearAll(); confirmClear = false }, onDismiss = { confirmClear = false },
    )
}

@Composable
private fun HistoryRow(p: ProgressEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    val l = LocalLayout.current
    FocusSurface(style = FocusStyle.Row, onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().widthIn(max = 1100.dp), color = Color.Transparent, shape = Radius.Card) { focused ->
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (l.isCompact) 112.dp else 160.dp).aspectRatio(16f / 9f).clip(Radius.Card).background(SokoulColors.SurfaceHigher)) {
                NetImage(p.image, null, Modifier.fillMaxSize(), contentScale = if (p.type == ContentType.LIVE) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (focused) SokoulColors.TextPrimary else SokoulColors.TextSecondary)
                p.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(HistoryRules.status(p, formatClock(p.updatedAt)), Modifier.padding(top = 2.dp), style = Type.Meta, color = SokoulColors.TextMuted)
            }
            if (p.finished) Icon(Icons.Default.CheckCircle, "Vu", tint = SokoulColors.Success)
        }
    }
}
