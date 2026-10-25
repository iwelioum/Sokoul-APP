package com.sokoul.iptv.ui.screens.recordings

import com.sokoul.iptv.ui.nav.navigateOnce
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.RecordingEntity
import com.sokoul.iptv.data.db.RecordingStatus
import com.sokoul.iptv.data.db.ReminderEntity
import com.sokoul.iptv.recording.RecordingScheduler
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatDateTime
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.formatSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val scheduler: RecordingScheduler,
    db: AppDatabase,
) : ViewModel() {
    val recordings: StateFlow<List<RecordingEntity>> = scheduler.recordings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders: StateFlow<List<ReminderEntity>> = db.recordings().observeReminders(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancel(r: RecordingEntity) = viewModelScope.launch { scheduler.cancel(r.id) }
    fun delete(r: RecordingEntity) = viewModelScope.launch { scheduler.delete(r.id) }
    fun removeReminder(r: ReminderEntity) = viewModelScope.launch { scheduler.removeReminder(r.playlistId, r.channelId, r.start) }
}

@Composable
fun RecordingsScreen(nav: NavHostController) {
    val vm: RecordingsViewModel = hiltViewModel()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<RecordingEntity?>(null) }
    var reminderMenu by remember { mutableStateOf<ReminderEntity?>(null) }

    if (recordings.isEmpty() && reminders.isEmpty()) {
        EmptyState(
            Icons.Default.FiberManualRecord, "Aucun enregistrement",
            "Depuis le guide TV, ouvrez un programme à venir pour le programmer, ou utilisez Menu › Enregistrer pendant la lecture d'une chaîne.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Enregistrements", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp)) }
        items(recordings, key = { it.id }) { r ->
            FocusSurface(
                onClick = {
                    if (r.status == RecordingStatus.COMPLETED || r.status == RecordingStatus.RECORDING) nav.navigateOnce(PlayerRoute(PlayKind.RECORDING, r.playlistId, r.id.toString()))
                    else menu = r
                },
                onLongClick = { menu = r },
                modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp),
                color = SokoulColors.Surface, focusScale = 1.01f,
            ) { _ ->
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint, label) = when (r.status) {
                        RecordingStatus.SCHEDULED -> Triple(Icons.Default.Schedule, SokoulColors.Accent, "Programmé")
                        RecordingStatus.RECORDING -> Triple(Icons.Default.FiberManualRecord, SokoulColors.Live, "En cours")
                        RecordingStatus.COMPLETED -> Triple(Icons.Default.CheckCircle, SokoulColors.Success, "Terminé")
                        RecordingStatus.FAILED -> Triple(Icons.Default.ErrorOutline, SokoulColors.Live, "Échec")
                        RecordingStatus.CANCELLED -> Triple(Icons.Default.Cancel, SokoulColors.TextMuted, "Annulé")
                    }
                    Icon(icon, label, Modifier.size(26.dp), tint = tint)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.channelName} · ${formatDateTime(r.startAt)} (${formatRange(r.startAt, r.endAt)})", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        r.error?.let { Text(it, color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
                        if (r.sizeBytes > 0) Text(formatSize(r.sizeBytes), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (reminders.isNotEmpty()) {
            item { Text("Rappels", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
            items(reminders, key = { "${it.playlistId}|${it.channelId}|${it.start}" }) { r ->
                FocusSurface(onClick = { reminderMenu = r }, modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp), color = SokoulColors.Surface, focusScale = 1.01f) { _ ->
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = SokoulColors.Warning)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium)
                            Text("${r.channelName} · ${formatDateTime(r.start)}", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
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
            subtitle = r.filePath,
            onDismiss = { menu = null },
        )
    }
    reminderMenu?.let { r ->
        OptionsDialog(r.title, listOf(MenuOption("Supprimer le rappel", Icons.Default.Delete) { vm.removeReminder(r) }), onDismiss = { reminderMenu = null })
    }
}
