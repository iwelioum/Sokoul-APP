package com.sokoul.iptv.ui.screens.recordings

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.RecordingEntity
import com.sokoul.iptv.data.db.RecordingStatus
import com.sokoul.iptv.data.db.ReminderEntity
import com.sokoul.iptv.recording.RecordingRules
import com.sokoul.iptv.recording.RecordingScheduler
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.ScreenHeader
import com.sokoul.iptv.ui.components.TitleArt
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.formatSize
import com.sokoul.iptv.ui.util.rememberNow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val scheduler: RecordingScheduler,
    private val db: AppDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val recordings: StateFlow<List<RecordingEntity>> = scheduler.recordings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders: StateFlow<List<ReminderEntity>> = db.recordings().observeReminders(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Logos des chaînes enregistrées (clé « playlist|chaîne »), pour illustrer les cartes. */
    val logos: StateFlow<Map<String, String?>> = scheduler.recordings().map { list ->
        list.map { it.playlistId to it.channelId }.distinct().associate { (pid, cid) ->
            "$pid|$cid" to runCatching { db.channels().get(pid, cid)?.logo }.getOrNull()
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Espace libre là où les enregistrements sont écrits. */
    fun freeBytes(): Long = runCatching {
        (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir).let { File(it, "Enregistrements").apply { mkdirs() } }.usableSpace
    }.getOrDefault(0L)

    fun cancel(r: RecordingEntity) = viewModelScope.launch { scheduler.cancel(r.id) }
    fun delete(r: RecordingEntity) = viewModelScope.launch { scheduler.delete(r.id) }
    fun removeReminder(r: ReminderEntity) = viewModelScope.launch { scheduler.removeReminder(r.playlistId, r.channelId, r.start) }
}

@Composable
fun RecordingsScreen(nav: NavHostController) {
    val vm: RecordingsViewModel = hiltViewModel()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val logos by vm.logos.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<RecordingEntity?>(null) }
    var reminderMenu by remember { mutableStateOf<ReminderEntity?>(null) }
    val l = LocalLayout.current
    val now = rememberNow(10_000)

    if (recordings.isEmpty() && reminders.isEmpty()) {
        EmptyState(
            Icons.Default.FiberManualRecord, "Aucun enregistrement",
            "Depuis le guide TV, ouvrez un programme à venir pour le programmer, ou utilisez Menu › Enregistrer pendant la lecture d'une chaîne.",
        )
        return
    }
    val sections = remember(recordings) { RecordingRules.sections(recordings) }
    val free = remember(recordings.size) { vm.freeBytes() }
    val used = recordings.sumOf { it.sizeBytes }
    val open: (RecordingEntity) -> Unit = { r ->
        if (r.status == RecordingStatus.COMPLETED || r.status == RecordingStatus.RECORDING) nav.navigateOnce(PlayerRoute(PlayKind.RECORDING, r.playlistId, r.id.toString()))
        else menu = r
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            item {
                ScreenHeader(
                    "Enregistrements",
                    Modifier.padding(horizontal = l.margin),
                    subtitle = buildList {
                        val done = recordings.count { it.status == RecordingStatus.COMPLETED }
                        if (done > 0) add("$done terminé" + if (done > 1) "s" else "")
                        if (used > 0) add("${formatSize(used)} utilisés")
                        if (free > 0) add("${formatSize(free)} libres")
                    }.joinToString("  ·  ").ifEmpty { null },
                )
            }
            sections.forEach { (section, list) ->
                item(key = section.name) {
                    Column {
                        com.sokoul.iptv.ui.components.RowHeader(section.title, countLabel(list.size, "enregistrement"), kicker = section.kicker)
                        LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(list, key = { it.id }) { r ->
                                RecordingCard(r, logos["${r.playlistId}|${r.channelId}"], now, onClick = { open(r) }, onLongClick = { menu = r })
                            }
                        }
                    }
                }
            }
            if (reminders.isNotEmpty()) item(key = "reminders") {
                Column {
                    com.sokoul.iptv.ui.components.RowHeader("Programmes à ne pas manquer", countLabel(reminders.size, "rappel"), kicker = "RAPPELS")
                    LazyRow(contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(reminders, key = { "${it.playlistId}|${it.channelId}|${it.start}" }) { r ->
                            FocusSurface(onClick = { reminderMenu = r }, style = FocusStyle.Row, color = SokoulColors.Surface, modifier = Modifier.width(320.dp)) { _ ->
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.NotificationsActive, null, Modifier.size(22.dp), tint = SokoulColors.Warning)
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${RecordingRules.channelLabel(r.channelName)} · ${dayLabel(r.start, now)} ${formatClock(r.start)}", style = Type.Meta, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        KeyHints(
            listOf(KeyHint("OK", "Lire"), KeyHint("Menu", "Arrêter, supprimer…")),
            Modifier.padding(start = l.margin, bottom = 14.dp, top = 6.dp),
        )
    }

    menu?.let { r ->
        OptionsDialog(
            r.title,
            buildList {
                if (r.status == RecordingStatus.COMPLETED || r.status == RecordingStatus.RECORDING) add(MenuOption("Lire", Icons.Default.PlayArrow) {
                    nav.navigateOnce(PlayerRoute(PlayKind.RECORDING, r.playlistId, r.id.toString()))
                })
                if (r.status == RecordingStatus.RECORDING) add(MenuOption("Arrêter l'enregistrement", Icons.Default.Stop) { vm.cancel(r) })
                if (r.status == RecordingStatus.SCHEDULED) add(MenuOption("Annuler la programmation", Icons.Default.Cancel) { vm.cancel(r) })
                add(MenuOption("Supprimer", Icons.Default.Delete) { vm.delete(r) })
            },
            subtitle = listOfNotNull(
                "${RecordingRules.channelLabel(r.channelName)} · ${dayLabel(r.startAt)} ${formatRange(r.startAt, r.endAt)}",
                r.error, r.filePath?.substringAfterLast('/'),
            ).joinToString("\n"),
            onDismiss = { menu = null },
        )
    }
    reminderMenu?.let { r ->
        OptionsDialog(r.title, listOf(MenuOption("Supprimer le rappel", Icons.Default.Delete) { vm.removeReminder(r) }), onDismiss = { reminderMenu = null })
    }
}

private fun countLabel(n: Int, word: String) = "$n $word" + if (n > 1) "s" else ""

/**
 * Carte d'enregistrement (16:9) : titre généré sur un fond sombre, logo de la chaîne, pastille d'état
 * (● REC, heure de début, Échec), barre d'avancement pendant l'enregistrement, infos dans la carte.
 */
@Composable
private fun RecordingCard(r: RecordingEntity, logo: String?, now: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, style = FocusStyle.Card, modifier = Modifier.width(320.dp).aspectRatio(16f / 9f)) { _ ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SokoulColors.PlaceholderTop, SokoulColors.PlaceholderBottom)))) {
            val dim = r.status == RecordingStatus.FAILED || r.status == RecordingStatus.CANCELLED
            TitleArt(
                r.title, emptyList(), com.sokoul.iptv.ui.theme.TitleSize.Recording,
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp),
                maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ChannelLogo(logo, r.channelName, Modifier.align(Alignment.TopEnd).padding(10.dp).size(54.dp, 32.dp))
            StatusBadge(r, now, Modifier.align(Alignment.TopStart).padding(10.dp))
            // bandeau d'infos en bas de la carte
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.black(0.85f))))
                    .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 9.dp),
            ) {
                Text(
                    RecordingRules.channelLabel(r.channelName) + " · " + dayLabel(r.startAt, now) + " " + formatRange(r.startAt, r.endAt),
                    style = MaterialTheme.typography.labelMedium, color = if (dim) SokoulColors.TextMuted else SokoulColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val extra = when {
                    r.status == RecordingStatus.FAILED -> r.error ?: "Échec"
                    r.sizeBytes > 0 -> formatSize(r.sizeBytes)
                    else -> null
                }
                if (extra != null) Text(extra, style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (r.status == RecordingStatus.RECORDING) {
                val f = ((now - r.startAt).toFloat() / (r.endAt - r.startAt).coerceAtLeast(1)).coerceIn(0f, 1f)
                com.sokoul.iptv.ui.components.ProgressLine(f, Modifier.align(Alignment.BottomStart), tone = com.sokoul.iptv.ui.components.ProgressTone.Live, thickness = 3.dp)
            }
        }
    }
}

@Composable
private fun StatusBadge(r: RecordingEntity, now: Long, modifier: Modifier) {
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
