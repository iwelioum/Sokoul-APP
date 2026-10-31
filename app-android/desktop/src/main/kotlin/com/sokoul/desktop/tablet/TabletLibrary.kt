package com.sokoul.desktop.tablet

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Event
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.data.LibraryRules
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.data.Upcoming
import com.sokoul.iptv.ui.components.ConfirmDialog
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.components.TouchTopBar
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** En-tête de page de la tablette : titre, sous-titre, action à droite. */
@Composable
private fun LibraryHeader(title: String, subtitle: String?, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 72.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.ScreenTitle)
            subtitle?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary) }
        }
        action?.invoke()
    }
}

/** Vignette 16:9 et trois lignes de texte : la ligne de « À venir » et de l'Historique de la tablette. */
@Composable
private fun LibraryRow(image: String?, title: String, line: String?, meta: String, done: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    FocusSurface(style = FocusStyle.Row, onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().widthIn(max = 1100.dp), color = Color.Transparent, shape = Radius.Card) { focused ->
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(Radius.Card).background(SokoulColors.SurfaceHigher)) {
                NetImage(com.sokoul.iptv.data.repo.tmdbSized(image, "w500"), null, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (focused) SokoulColors.TextPrimary else SokoulColors.TextSecondary)
                line?.takeIf { it.isNotBlank() }?.let { Text(it, style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(meta, Modifier.padding(top = 2.dp), style = Type.Meta, color = SokoulColors.TextMuted)
            }
            if (done) Icon(Icons.Default.CheckCircle, "Vu", tint = SokoulColors.Success)
        }
    }
}

/** « À venir » (`CalendarScreen` de la tablette) : prochains épisodes des séries suivies, groupés par jour. */
@Composable
fun TabletUpcoming(app: AppState) {
    val l = LocalLayout.current
    val items by produceState<List<Upcoming>?>(null) {
        val ids = withContext(Dispatchers.IO) { app.myList().filter { it.first == "tv" }.map { it.second } + app.db.startedSeries() }
        value = app.upcoming.load(ids)
    }
    val now = remember(items) { System.currentTimeMillis() }
    val sections = remember(items) { LibraryRules.byDay(items.orEmpty(), { it.airsAt }, now, ::dayLabel) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = l.margin, end = l.margin, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item("head") {
                LibraryHeader("À venir", when (val i = items) {
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
                items(rows, key = { "${it.tvId}|${it.season}|${it.episode}" }) { u ->
                    LibraryRow(u.image, u.title, "S${u.season} · É${u.episode}" + (u.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), "Sortie vers ${formatClock(u.airsAt)}", false, { app.openDetail("tv", u.tvId) })
                }
            }
        }
        TouchTopBar(onBack = { app.back() }, scrimProvider = { 1f })
    }
}

/** Historique (`HistoryScreen` de la tablette) : tout ce qui a été regardé, groupé par jour ; retirer ou tout effacer. */
@Composable
fun TabletHistory(app: AppState) {
    val l = LocalLayout.current
    val list by produceState<List<Resume>?>(null, app.dataVersion) { value = withContext(Dispatchers.IO) { app.db.history() } }
    var menu by remember { mutableStateOf<Resume?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val now = remember(list) { System.currentTimeMillis() }
    val sections = remember(list) { LibraryRules.byDay(list.orEmpty(), { it.updatedAt }, now, ::dayLabel) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = l.margin, end = l.margin, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val all = list.orEmpty()
            item("head") {
                LibraryHeader("Historique", if (all.isEmpty()) null else "${all.size} titre${if (all.size > 1) "s" else ""} regardé${if (all.size > 1) "s" else ""}") {
                    if (all.isNotEmpty()) SokoulButton("Tout effacer", { confirmClear = true }, icon = Icons.Default.DeleteSweep)
                }
            }
            if (list != null && all.isEmpty()) item("empty") {
                EmptyState(Icons.Default.History, "Rien pour l'instant", "Les films et épisodes que vous regardez apparaîtront ici, du plus récent au plus ancien.", Modifier.padding(top = 40.dp))
            }
            sections.forEach { (day, rows) ->
                item("d:$day") { Text(day.uppercase(), Modifier.padding(top = 18.dp, bottom = 4.dp), style = Type.Kicker) }
                items(rows, key = { it.key }) { r ->
                    LibraryRow(r.image, r.title, r.subtitle, LibraryRules.status(r, formatClock(r.updatedAt)), r.finished, { app.openDetail(r.kind, r.tmdbId) }, { menu = r })
                }
            }
        }
        TouchTopBar(onBack = { app.back() }, scrimProvider = { 1f })
    }
    menu?.let { r ->
        OptionsDialog(
            r.title,
            listOf(
                MenuOption("Ouvrir", Icons.Default.Info) { app.openDetail(r.kind, r.tmdbId) },
                MenuOption(if (r.finished) "Revoir" else "Reprendre", Icons.Default.PlayArrow) {
                    app.playTitle(NowPlaying.Title(r.kind, r.tmdbId, r.title, r.subtitle, r.image, r.season, r.episode))
                },
                MenuOption("Retirer de l'historique", Icons.Default.Remove) { app.db.deleteResume(r.key); app.dataVersion++ },
            ),
            subtitle = "Retirer efface aussi le point de reprise de ce titre.",
            onDismiss = { menu = null },
        )
    }
    if (confirmClear) ConfirmDialog(
        "Effacer l'historique ?", "Tout l'historique et les points de reprise de ce PC sont effacés.",
        "Tout effacer", onConfirm = { app.db.clearHistory(); app.dataVersion++; confirmClear = false }, onDismiss = { confirmClear = false },
    )
}
