package com.sokoul.iptv.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.BuildConfig
import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.BufferProfile
import com.sokoul.iptv.data.DecoderMode
import com.sokoul.iptv.data.LiveFormat
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryWithPrefs
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.repo.BackupManager
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SyncEngine
import com.sokoul.iptv.data.repo.SyncStatus
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.ui.ParentalGate
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.ConfirmDialog
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.FormField
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PinDialog
import com.sokoul.iptv.ui.nav.PlaylistEditRoute
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatDate
import com.sokoul.iptv.ui.util.formatDateTime
import com.sokoul.iptv.work.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val backup: BackupManager,
    private val parental: ParentalGate,
    sync: SyncEngine,
    db: AppDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val playlistList: StateFlow<List<PlaylistEntity>> = playlists.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val active: StateFlow<PlaylistEntity?> = playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val playlistStatus = sync.playlistStatus
    val epgStatus = sync.epgStatus
    val flaggedGroups: StateFlow<List<CategoryWithPrefs>> = active.filterNotNull().flatMapLatest { db.categories().observeFlagged(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { store.update(t) }
    fun activate(p: PlaylistEntity) = viewModelScope.launch { playlists.setActive(p.id) }
    fun sync(p: PlaylistEntity) = SyncWorker.syncNow(context, p.id, withEpg = true)
    fun syncEpg(p: PlaylistEntity) = SyncWorker.epgNow(context, p.id)
    fun delete(p: PlaylistEntity) = viewModelScope.launch { playlists.delete(p.id) }
    fun unflag(g: CategoryWithPrefs) = viewModelScope.launch { catalog.setCategoryPref(g.playlistId, g.type, g.categoryId, hidden = false, locked = false) }
    fun lockNow() = parental.lock()

    fun export(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        done(runCatching { backup.export(uri) }.fold({ "Sauvegarde exportée" }, { it.userMessage() }))
    }

    fun import(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        done(runCatching { backup.import(uri) }.fold({ ids -> ids.forEach { SyncWorker.syncNow(context, it) }; "${ids.size} playlist(s) restaurée(s)" }, { it.userMessage() }))
    }
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val vm: SettingsViewModel = hiltViewModel()
    val s by vm.settings.collectAsStateWithLifecycle()
    val playlists by vm.playlistList.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val status by vm.playlistStatus.collectAsStateWithLifecycle()
    val epgStatus by vm.epgStatus.collectAsStateWithLifecycle()
    val flagged by vm.flaggedGroups.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var choice by remember { mutableStateOf<Pair<String, List<MenuOption>>?>(null) }
    var textEdit by remember { mutableStateOf<TextEdit?>(null) }
    var playlistMenu by remember { mutableStateOf<PlaylistEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var pinStep by remember { mutableStateOf<PinStep?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Réglages", style = MaterialTheme.typography.headlineMedium) }

        section("Playlists")
        items(playlists, key = { it.id }) { p ->
            PlaylistRow(p, p.id == active?.id, status[p.id], epgStatus[p.id], Modifier.then(if (p == playlists.first()) Modifier.focusRequester(firstFocus) else Modifier)) { playlistMenu = p }
        }
        item { SettingRow("Ajouter une playlist", "Xtream Codes, lien M3U ou fichier local", icon = Icons.Default.Add) { nav.navigate(PlaylistEditRoute()) } }

        section("Lecteur")
        item {
            SettingRow("Format des chaînes (Xtream)", s.liveFormat.label) {
                choice = "Format des chaînes" to LiveFormat.entries.map { f -> MenuOption(f.label, selected = f == s.liveFormat) { vm.update { it.copy(liveFormat = f) } } }
            }
        }
        item {
            SettingRow("Mémoire tampon", s.bufferProfile.label) {
                choice = "Mémoire tampon" to BufferProfile.entries.map { b -> MenuOption(b.label, selected = b == s.bufferProfile) { vm.update { it.copy(bufferProfile = b) } } }
            }
        }
        item {
            SettingRow("Décodage", s.decoderMode.label, "Passez en « Logiciel prioritaire » si le son ou l'image ne passe pas sur certaines chaînes") {
                choice = "Décodage" to DecoderMode.entries.map { d -> MenuOption(d.label, selected = d == s.decoderMode) { vm.update { it.copy(decoderMode = d) } } }
            }
        }
        item {
            SettingRow("Langue audio préférée", languageName(s.audioLanguage)) {
                choice = "Langue audio" to languages.map { (code, name) -> MenuOption(name, selected = code == s.audioLanguage) { vm.update { it.copy(audioLanguage = code) } } }
            }
        }
        item {
            SettingRow("Sous-titres par défaut", if (s.subtitleLanguage.isBlank()) "Désactivés" else languageName(s.subtitleLanguage)) {
                choice = "Sous-titres" to (listOf("" to "Désactivés") + languages).map { (code, name) ->
                    MenuOption(name, selected = code == s.subtitleLanguage) { vm.update { it.copy(subtitleLanguage = code) } }
                }
            }
        }
        item { ToggleRow("Épisode suivant automatique", s.autoplayNextEpisode) { v -> vm.update { it.copy(autoplayNextEpisode = v) } } }
        item {
            SettingRow("User-Agent", s.userAgent) {
                textEdit = TextEdit("User-Agent", s.userAgent) { v -> vm.update { it.copy(userAgent = v.ifBlank { AppSettings.DEFAULT_USER_AGENT }) } }
            }
        }

        section("Guide TV et mises à jour")
        item {
            SettingRow("Décalage horaire du guide", offsetLabel(s.epgOffsetMinutes), "À ajuster si les programmes sont décalés") {
                choice = "Décalage du guide" to (-12..12).map { h -> h * 60 }.map { m ->
                    MenuOption(offsetLabel(m), selected = m == s.epgOffsetMinutes) { vm.update { it.copy(epgOffsetMinutes = m) } }
                }
            }
        }
        item {
            SettingRow("Mise à jour des playlists", "Toutes les ${s.playlistRefreshHours} h") {
                choice = "Fréquence" to listOf(6, 12, 24, 48, 168).map { h -> MenuOption("Toutes les $h h", selected = h == s.playlistRefreshHours) { vm.update { it.copy(playlistRefreshHours = h) } } }
            }
        }
        item {
            SettingRow("Mise à jour du guide TV", "Toutes les ${s.epgRefreshHours} h") {
                choice = "Fréquence" to listOf(6, 12, 24, 48).map { h -> MenuOption("Toutes les $h h", selected = h == s.epgRefreshHours) { vm.update { it.copy(epgRefreshHours = h) } } }
            }
        }

        section("Groupes masqués et verrouillés")
        if (flagged.isEmpty()) item { Text("Aucun. Appui long (ou touche Menu) sur un groupe pour le masquer ou le verrouiller.", Modifier.padding(8.dp), color = SokoulColors.TextMuted) }
        items(flagged, key = { "${it.type}|${it.categoryId}" }) { g ->
            SettingRow(
                g.name,
                listOfNotNull(typeLabel(g.type), if (g.hidden) "masqué" else null, if (g.locked) "verrouillé" else null).joinToString(" · "),
                icon = if (g.locked) Icons.Default.Lock else Icons.Default.Visibility,
            ) { vm.unflag(g); Toast.makeText(context, "« ${g.name} » rétabli", Toast.LENGTH_SHORT).show() }
        }

        section("Contrôle parental")
        item {
            SettingRow(if (s.parentalPin.isBlank()) "Définir un code parental" else "Modifier le code parental", "Code à 4 chiffres pour les groupes verrouillés", icon = Icons.Default.Lock) {
                pinStep = if (s.parentalPin.isBlank()) PinStep.NEW else PinStep.CURRENT
            }
        }
        if (s.parentalPin.isNotBlank()) {
            item { SettingRow("Reverrouiller maintenant", "Masque à nouveau les groupes verrouillés", icon = Icons.Default.LockOpen) { vm.lockNow(); Toast.makeText(context, "Groupes verrouillés", Toast.LENGTH_SHORT).show() } }
            item { SettingRow("Supprimer le code", null, icon = Icons.Default.Delete) { pinStep = PinStep.REMOVE } }
        }

        section("Fiches films et séries")
        item {
            SettingRow("Clé API TMDB", if (s.tmdbApiKey.isBlank()) "Non configurée — affiches HD, résumés, casting et bandes-annonces" else "Configurée") {
                textEdit = TextEdit("Clé API TMDB (gratuite sur themoviedb.org)", s.tmdbApiKey) { v -> vm.update { it.copy(tmdbApiKey = v.trim()) } }
            }
        }

        section("Sauvegarde")
        item { SettingRow("Exporter la configuration", "Playlists, favoris, groupes et réglages (contient vos mots de passe)") { exportLauncher.launch("sokoul-sauvegarde.json") } }
        item { SettingRow("Restaurer une sauvegarde", null) { importLauncher.launch(arrayOf("application/json", "*/*")) } }

        section("À propos")
        item { SettingRow("Sokoul TV ${BuildConfig.VERSION_NAME}", "Lecteur IPTV pour Android TV, Fire TV et mobiles", icon = Icons.Default.CheckCircle) {} }
    }
    LaunchedEffect(playlists.isNotEmpty()) { if (playlists.isNotEmpty()) runCatching { firstFocus.requestFocus() } }

    choice?.let { (title, options) -> OptionsDialog(title, options, onDismiss = { choice = null }) }
    textEdit?.let { t -> TextEditDialog(t) { textEdit = null } }
    playlistMenu?.let { p ->
        OptionsDialog(
            p.name,
            buildList {
                if (p.id != active?.id) add(MenuOption("Utiliser cette playlist", Icons.Default.CheckCircle) { vm.activate(p) })
                add(MenuOption("Synchroniser maintenant", Icons.Default.Refresh) { vm.sync(p); Toast.makeText(context, "Synchronisation lancée", Toast.LENGTH_SHORT).show() })
                add(MenuOption("Mettre à jour le guide TV", Icons.Default.Schedule) { vm.syncEpg(p); Toast.makeText(context, "Mise à jour du guide lancée", Toast.LENGTH_SHORT).show() })
                add(MenuOption("Modifier", Icons.Default.Edit) { nav.navigate(PlaylistEditRoute(p.id)) })
                add(MenuOption("Supprimer", Icons.Default.Delete) { confirmDelete = p })
            },
            onDismiss = { playlistMenu = null },
        )
    }
    confirmDelete?.let { p ->
        ConfirmDialog("Supprimer « ${p.name} » ?", "Chaînes, favoris, reprises et guide de cette playlist seront supprimés.", "Supprimer", { vm.delete(p) }, { confirmDelete = null })
    }
    pinStep?.let { step ->
        PinDialog(
            when (step) { PinStep.CURRENT, PinStep.REMOVE -> "Code actuel"; PinStep.NEW -> "Nouveau code"; is PinStep.CONFIRM -> "Confirmez le code" },
            onResult = { pin ->
                when (step) {
                    PinStep.CURRENT -> if (pin == s.parentalPin) { pinStep = PinStep.NEW; pinError = null } else pinError = "Code incorrect"
                    PinStep.REMOVE -> if (pin == s.parentalPin) { vm.update { it.copy(parentalPin = "") }; pinStep = null; pinError = null } else pinError = "Code incorrect"
                    PinStep.NEW -> { pinStep = PinStep.CONFIRM(pin); pinError = null }
                    is PinStep.CONFIRM -> if (pin == step.first) {
                        vm.update { it.copy(parentalPin = pin) }; pinStep = null; pinError = null
                        Toast.makeText(context, "Code parental enregistré", Toast.LENGTH_SHORT).show()
                    } else { pinStep = PinStep.NEW; pinError = "Les codes ne correspondent pas" }
                }
            },
            onDismiss = { pinStep = null; pinError = null },
            error = pinError,
        )
    }
}

private sealed interface PinStep {
    data object CURRENT : PinStep
    data object NEW : PinStep
    data object REMOVE : PinStep
    data class CONFIRM(val first: String) : PinStep
}

private data class TextEdit(val title: String, val value: String, val onSave: (String) -> Unit)

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String) = item(key = "section-$title") {
    Text(title.uppercase(), Modifier.padding(top = 22.dp, bottom = 4.dp, start = 4.dp), color = SokoulColors.Accent, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun SettingRow(
    title: String,
    value: String?,
    hint: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth().widthIn(max = 900.dp), color = SokoulColors.Surface, focusScale = 1.01f) { _ ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, Modifier.padding(end = 14.dp).size(22.dp), tint = SokoulColors.TextSecondary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (hint != null) Text(hint, color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (value != null) Text(value, Modifier.padding(start = 16.dp).widthIn(max = 380.dp), color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(title, if (value) "Activé" else "Désactivé") { onChange(!value) }

@Composable
private fun PlaylistRow(p: PlaylistEntity, active: Boolean, status: SyncStatus?, epg: SyncStatus?, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth().widthIn(max = 900.dp), color = if (active) SokoulColors.AccentSoft else SokoulColors.Surface, focusScale = 1.01f) { _ ->
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(if (p.type == PlaylistType.XTREAM) "Xtream" else "M3U", color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                if (active) Text("  ·  ACTIVE", color = SokoulColors.Accent, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${p.liveCount} chaînes · ${p.movieCount} films · ${p.seriesCount} séries" +
                    (if (p.lastSync > 0) " · synchronisée ${formatDateTime(p.lastSync)}" else ""),
                color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            p.expiresAt?.let { Text("Abonnement valable jusqu'au ${formatDate(it)}" + (p.maxConnections?.let { n -> " · $n connexion${if (n > 1) "s" else ""} simultanée${if (n > 1) "s" else ""}" } ?: ""), color = if (it < System.currentTimeMillis() + 7 * 86_400_000L) SokoulColors.Warning else SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            statusLine("Playlist", status)?.let { Text(it, color = if (status?.error != null) SokoulColors.Live else SokoulColors.Accent, style = MaterialTheme.typography.bodySmall) }
            statusLine("Guide", epg)?.let { Text(it, color = if (epg?.error != null) SokoulColors.Live else SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun statusLine(label: String, s: SyncStatus?): String? = when {
    s == null -> null
    s.running -> "$label : ${s.step} ${if (s.count > 0) s.count.toString() else ""}".trim()
    s.error != null -> "$label : ${s.error}"
    s.warnings.isNotEmpty() -> "$label : ${s.warnings.joinToString(" ; ")}"
    s.finishedAt > 0 -> "$label : à jour (${s.count})"
    else -> null
}

@Composable
private fun TextEditDialog(t: TextEdit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(t.value) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(min = 360.dp, max = 560.dp).background(SokoulColors.SurfaceHigh, RoundedCornerShape(18.dp)).padding(24.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleLarge)
            FormField("Valeur", value, { value = it }, Modifier.padding(top = 14.dp).focusRequester(focus), autoEdit = true, onDone = { t.onSave(value); onDismiss() })
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("Annuler", onDismiss)
                AppButton("Enregistrer", { t.onSave(value); onDismiss() }, primary = true)
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

private val languages = listOf(
    "fr" to "Français", "en" to "Anglais", "ar" to "Arabe", "es" to "Espagnol", "de" to "Allemand",
    "it" to "Italien", "pt" to "Portugais", "tr" to "Turc", "nl" to "Néerlandais",
)

private fun languageName(code: String) = languages.firstOrNull { it.first == code }?.second ?: code.ifBlank { "Automatique" }

private fun offsetLabel(min: Int) = when {
    min == 0 -> "Aucun"
    min > 0 -> "+${min / 60} h"
    else -> "−${-min / 60} h"
}

private fun typeLabel(t: ContentType) = when (t) {
    ContentType.LIVE -> "Direct"
    ContentType.MOVIE -> "Films"
    ContentType.SERIES -> "Séries"
}
