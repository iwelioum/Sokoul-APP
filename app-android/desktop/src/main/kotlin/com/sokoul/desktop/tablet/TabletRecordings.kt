package com.sokoul.desktop.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.PcRecordingRules
import com.sokoul.desktop.data.Recording
import com.sokoul.desktop.data.RecordingStatus
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.ProgressLine
import com.sokoul.iptv.ui.components.ProgressTone
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.TitleArt
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.formatSize
import com.sokoul.iptv.ui.util.rememberNow

/**
 * Enregistrements (`RecordingsScreen` de la tablette) : « n terminés · x utilisés · y libres », rangées En cours,
 * Programmés, Terminés, Échecs et annulés ; clic = lire (terminé ou en cours), clic droit = arrêter, annuler, supprimer.
 */
@Composable
fun TabletRecordings(app: AppState) {
    val l = LocalLayout.current
    val version by app.recorder.version.collectAsState()
    val recordings = remember(version, app.dataVersion, app.playlist?.id) { app.playlist?.let { app.db.recordings(it.id) }.orEmpty() }
    var menu by remember { mutableStateOf<Recording?>(null) }
    val now = rememberNow(10_000)
    if (recordings.isEmpty()) {
        EmptyState(
            Icons.Default.FiberManualRecord, "Aucun enregistrement",
            "Depuis le guide TV, clic droit sur un programme à venir pour le programmer, ou cliquez « Enregistrer » pendant la lecture d'une chaîne. L'app doit rester ouverte pendant l'enregistrement.",
        )
        return
    }
    val sections = remember(recordings) { PcRecordingRules.sections(recordings) }
    val free = remember(recordings.size) { app.recorder.dir.usableSpace }
    val used = recordings.sumOf { it.bytes }
    val open: (Recording) -> Unit = { r ->
        when (r.status) {
            RecordingStatus.COMPLETED -> app.playRecording(r)
            RecordingStatus.RECORDING -> app.playRecording(r, live = true)
            else -> menu = r
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item {
            Column(Modifier.padding(horizontal = l.margin).padding(top = 24.dp)) {
                Text("Enregistrements", style = Type.ScreenTitle)
                val line = buildList {
                    val done = recordings.count { it.status == RecordingStatus.COMPLETED }
                    if (done > 0) add("$done terminé" + if (done > 1) "s" else "")
                    if (used > 0) add("${formatSize(used)} utilisés")
                    if (free > 0) add("${formatSize(free)} libres")
                }.joinToString("  ·  ")
                if (line.isNotEmpty()) Text(line, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, color = SokoulColors.TextSecondary)
            }
        }
        sections.forEach { (section, list) ->
            item(key = section.name) {
                Column {
                    RowHeader(section.title, "${list.size} enregistrement" + if (list.size > 1) "s" else "", kicker = section.kicker)
                    LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(list, key = { it.id }) { r -> RecordingCard(r, now, onClick = { open(r) }, onLongClick = { menu = r }) }
                    }
                }
            }
        }
    }
    menu?.let { r ->
        OptionsDialog(
            r.title,
            buildList {
                if (r.status == RecordingStatus.COMPLETED || r.status == RecordingStatus.RECORDING) add(MenuOption("Lire", Icons.Default.PlayArrow) { open(r) })
                if (r.status == RecordingStatus.RECORDING) add(MenuOption("Arrêter l'enregistrement", Icons.Default.Stop) { app.recorder.cancel(r) })
                if (r.status == RecordingStatus.SCHEDULED) add(MenuOption("Annuler la programmation", Icons.Default.Cancel) { app.recorder.cancel(r) })
                add(MenuOption("Supprimer", Icons.Default.Delete) { app.recorder.delete(r) })
            },
            subtitle = listOfNotNull(
                "${PcRecordingRules.channelLabel(r.channelName)} · ${dayLabel(r.startAt)} ${formatRange(r.startAt, r.endAt)}",
                r.error, r.path?.substringAfterLast('\\')?.substringAfterLast('/'),
            ).joinToString("\n"),
            onDismiss = { menu = null },
        )
    }
}

/** Carte d'enregistrement de la tablette (16:9) : titre dessiné, logo, pastille d'état, infos, avancement. */
@Composable
private fun RecordingCard(r: Recording, now: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, style = FocusStyle.Card, modifier = Modifier.width(LocalLayout.current.landscape).aspectRatio(16f / 9f)) { _ ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SokoulColors.PlaceholderTop, SokoulColors.PlaceholderBottom)))) {
            val dim = r.status == RecordingStatus.FAILED || r.status == RecordingStatus.CANCELLED
            TitleArt(
                r.title, emptyList(), com.sokoul.iptv.ui.theme.TitleSize.Recording,
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp),
                maxLines = 2, align = TextAlign.Center,
            )
            ChannelLogo(r.channelLogo, r.channelName, Modifier.align(Alignment.TopEnd).padding(10.dp).size(54.dp, 32.dp))
            StatusBadge(r, now, Modifier.align(Alignment.TopStart).padding(10.dp))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.black(0.85f))))
                    .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 9.dp),
            ) {
                Text(
                    PcRecordingRules.channelLabel(r.channelName) + " · " + dayLabel(r.startAt, now) + " " + formatRange(r.startAt, r.endAt),
                    style = MaterialTheme.typography.labelMedium, color = if (dim) SokoulColors.TextMuted else SokoulColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val extra = when {
                    r.status == RecordingStatus.FAILED -> r.error ?: "Échec"
                    r.bytes > 0 -> formatSize(r.bytes)
                    else -> null
                }
                if (extra != null) Text(extra, style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (r.status == RecordingStatus.RECORDING) {
                val f = ((now - r.startAt).toFloat() / (r.endAt - r.startAt).coerceAtLeast(1)).coerceIn(0f, 1f)
                ProgressLine(f, Modifier.align(Alignment.BottomStart), tone = ProgressTone.Live, thickness = 3.dp)
            }
        }
    }
}

@Composable
private fun StatusBadge(r: Recording, now: Long, modifier: Modifier) {
    val (text, bg, fg) = when (r.status) {
        RecordingStatus.RECORDING -> Triple("● REC", SokoulColors.Live, SokoulColors.OnVideo)
        RecordingStatus.SCHEDULED -> Triple("${dayLabel(r.startAt, now)} · ${formatClock(r.startAt)}", SokoulColors.Accent, SokoulColors.OnVideo)
        RecordingStatus.COMPLETED -> return
        RecordingStatus.FAILED -> Triple("Échec", SokoulColors.black(0.7f), SokoulColors.Live)
        RecordingStatus.CANCELLED -> Triple("Annulé", SokoulColors.black(0.7f), SokoulColors.TextMuted)
    }
    Text(
        text, modifier.background(bg, Radius.Tiny).padding(horizontal = 7.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
    )
}
