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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.Upcoming
import com.sokoul.iptv.data.repo.UpcomingRepository
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.ScreenHeader
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val upcoming: UpcomingRepository,
) : ViewModel() {
    private val _items = MutableStateFlow<List<Upcoming>?>(null)
    /** null : chargement en cours. */
    val items: StateFlow<List<Upcoming>?> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            val p = playlists.activeNow()
            _items.value = if (p == null) emptyList() else runCatching { upcoming.upcoming(p.id) }.getOrDefault(emptyList())
        }
    }
}

/** « À venir » : prochain épisode des séries suivies, jour par jour ; OK ouvre la fiche de la série. */
@Composable
fun CalendarScreen(nav: NavHostController) {
    val vm: CalendarViewModel = hiltViewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    val now = remember(items) { System.currentTimeMillis() }
    val sections = remember(items) { items.orEmpty().groupBy { dayLabel(it.airsAt, now) }.toList() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = l.margin, end = l.margin, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item("head") {
            ScreenHeader("À venir", subtitle = when (val i = items) {
                null -> "Recherche des prochains épisodes…"
                else -> if (i.isEmpty()) null else "Prochains épisodes des séries de Ma liste et de celles que vous regardez"
            })
        }
        when (val i = items) {
            null -> items(4) { Box(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 4.dp).clip(Radius.Card).background(SokoulColors.Surface)) }
            else -> if (i.isEmpty()) item("empty") {
                EmptyState(
                    Icons.Default.Event, "Aucun épisode annoncé",
                    "Ajoutez des séries en cours de diffusion à Ma liste (ou commencez-les) : leurs prochains épisodes s'afficheront ici, avec le jour et l'heure de sortie.",
                    Modifier.padding(top = 40.dp),
                )
            }
        }
        sections.forEach { (day, rows) ->
            item("d:$day") { Text(day.uppercase(), Modifier.padding(top = 18.dp, bottom = 4.dp), style = Type.Kicker) }
            items(rows, key = { "${it.seriesId}|${it.season}|${it.episode}" }) { u -> UpcomingRow(u) { nav.navigateOnce(SeriesDetailRoute(u.seriesId)) } }
        }
    }
}

@Composable
private fun UpcomingRow(u: Upcoming, onClick: () -> Unit) {
    val l = LocalLayout.current
    FocusSurface(style = FocusStyle.Row, onClick = onClick, modifier = Modifier.fillMaxWidth().widthIn(max = 1100.dp), color = Color.Transparent, shape = Radius.Card) { focused ->
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (l.isCompact) 112.dp else 160.dp).aspectRatio(16f / 9f).clip(Radius.Card).background(SokoulColors.SurfaceHigher)) {
                NetImage(com.sokoul.iptv.data.repo.tmdbSized(u.image, "w500"), null, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(u.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (focused) SokoulColors.TextPrimary else SokoulColors.TextSecondary)
                Text(
                    "S${u.season} · É${u.episode}" + (u.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("Sortie vers ${formatClock(u.airsAt)}", Modifier.padding(top = 2.dp), style = Type.Meta, color = SokoulColors.TextMuted)
            }
        }
    }
}
