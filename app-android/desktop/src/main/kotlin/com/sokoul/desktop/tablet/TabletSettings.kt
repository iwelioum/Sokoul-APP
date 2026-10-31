package com.sokoul.desktop.tablet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.AppDirs
import com.sokoul.desktop.data.BackupReader
import com.sokoul.desktop.data.DesktopImport
import com.sokoul.desktop.data.Playlist
import com.sokoul.desktop.data.PlaylistType
import com.sokoul.desktop.data.ProbeResult
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.desktop.data.ProviderProbe
import com.sokoul.desktop.data.BufferProfile
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.data.SubtitleBackground
import com.sokoul.desktop.data.SubtitleColor
import com.sokoul.desktop.data.SubtitlePosition
import com.sokoul.desktop.data.SubtitleSize
import com.sokoul.iptv.ui.screens.settings.AdvancedRow
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VerticalAlignBottom
import com.sokoul.iptv.data.PinHasher
import com.sokoul.iptv.ui.components.ButtonKind
import com.sokoul.iptv.ui.components.ConfirmDialog
import com.sokoul.iptv.ui.components.DialogHeader
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.FormField
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PinDialog
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.components.SokoulChip
import com.sokoul.iptv.ui.components.SokoulDialog
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCategory
import com.sokoul.iptv.ui.screens.settings.SettingsPanes
import com.sokoul.iptv.ui.screens.settings.note
import com.sokoul.iptv.ui.screens.settings.subsection
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.util.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Fenêtre ouverte depuis un volet des réglages. */
private sealed interface SettingsDialog {
    data class PlaylistMenu(val p: Playlist) : SettingsDialog
    data class DeletePlaylist(val p: Playlist) : SettingsDialog
    data object AddPlaylist : SettingsDialog
    data object Import : SettingsDialog
    data class Key(val id: String, val label: String) : SettingsDialog
    data object SetPin : SettingsDialog
    /** Changer de code : l'ancien d'abord (sinon n'importe qui le remplace). */
    data object VerifyPin : SettingsDialog
    data object RemovePin : SettingsDialog
    /** Choix d'une valeur (comme `c.choose` de la tablette). */
    data class Choose(val title: String, val options: List<MenuOption>) : SettingsDialog
    data object UserAgent : SettingsDialog
}

/**
 * Réglages de la tablette (`SettingsPanes`, rubriques et lignes de la tablette). Chaque ligne décrit ce que fait
 * vraiment le PC : un réglage que le PC n'a pas encore n'est pas affiché comme modifiable.
 */
@Composable
fun TabletSettings(app: AppState) {
    val scope = rememberCoroutineScope()
    val sync by app.sync.state.collectAsState()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var probes by remember { mutableStateOf<Map<String, ProbeResult>>(emptyMap()) }
    var testing by remember { mutableStateOf(setOf<String>()) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    fun test(id: String) {
        testing = testing + id
        scope.launch { probes = probes + (id to ProviderProbe.check(app.db, id)); testing = testing - id }
    }
    fun syncNow(p: Playlist) = scope.launch { app.sync.sync(p); app.reloadPlaylist(); app.dataVersion++ }

    val initial = remember {
        com.sokoul.desktop.TrialRoute.settingsPane.also { com.sokoul.desktop.TrialRoute.settingsPane = null }
            ?.let { n -> SettingsCategory.entries.firstOrNull { it.name == n } } ?: SettingsCategory.ACCOUNT
    }
    SettingsPanes(initial = initial) { cat, modifier ->
        val version = app.dataVersion
        val playlists = remember(version, sync.running) { app.db.playlists() }
        val statuses = remember(version) { ProviderConfig.statuses(app.db) }
        LazyColumn(modifier, contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (cat) {
                SettingsCategory.ACCOUNT -> {
                    items(playlists, key = { "pl-${it.id}" }) { p ->
                        PlaylistRow(app, p, p.id == app.playlist?.id, if (p.id == app.playlist?.id || sync.running) syncLine(sync) else null, version) { dialog = SettingsDialog.PlaylistMenu(p) }
                    }
                    item("pl-add") { SettingRow("Ajouter une playlist", "Xtream Codes ou lien M3U", icon = Icons.Default.Add) { dialog = SettingsDialog.AddPlaylist } }
                    subsection("Mises à jour")
                    item("pl-refresh") {
                        SettingRow(
                            "Mettre à jour maintenant", if (sync.running) sync.step else null, "Chaînes, guide TV, films et séries de l'abonnement actif",
                            icon = Icons.Default.Sync,
                        ) { app.playlist?.takeIf { !sync.running }?.let(::syncNow) }
                    }
                    note("one", "Une seule connexion à la fois : ne regardez pas la même playlist sur la tablette et sur le PC en même temps.")
                }
                SettingsCategory.PLAYBACK -> {
                    val pr = app.prefs
                    fun set(block: () -> Unit) { block(); app.dataVersion++ }
                    item("pb-audio") {
                        SettingRow("Langue audio préférée", DesktopPrefs.languageName(pr.audioLanguage), icon = Icons.Default.RecordVoiceOver) {
                            dialog = SettingsDialog.Choose("Langue audio", DesktopPrefs.languages.map { (code, name) -> MenuOption(name, selected = code == pr.audioLanguage) { set { pr.audioLanguage = code } } })
                        }
                    }
                    item("pb-subs") {
                        SettingRow("Sous-titres par défaut", if (pr.subtitleLanguage.isBlank()) "Désactivés" else DesktopPrefs.languageName(pr.subtitleLanguage), icon = Icons.Default.ClosedCaption) {
                            dialog = SettingsDialog.Choose("Sous-titres", (listOf("" to "Désactivés") + DesktopPrefs.languages).map { (code, name) ->
                                MenuOption(name, selected = code == pr.subtitleLanguage) { set { pr.subtitleLanguage = code } }
                            })
                        }
                    }
                    // apparence : options du média, appliquées dès le titre suivant
                    item("pb-sub-size") {
                        SettingRow("Taille des sous-titres", pr.subtitleSize.label, icon = Icons.Default.FormatSize) {
                            dialog = SettingsDialog.Choose("Taille des sous-titres", SubtitleSize.entries.map { v -> MenuOption(v.label, selected = v == pr.subtitleSize) { set { pr.subtitleSize = v } } })
                        }
                    }
                    item("pb-sub-color") {
                        SettingRow("Couleur des sous-titres", pr.subtitleColor.label, icon = Icons.Default.Palette) {
                            dialog = SettingsDialog.Choose("Couleur des sous-titres", SubtitleColor.entries.map { v -> MenuOption(v.label, selected = v == pr.subtitleColor) { set { pr.subtitleColor = v } } })
                        }
                    }
                    item("pb-sub-bg") {
                        SettingRow("Lisibilité des sous-titres", pr.subtitleBackground.label, "Contour noir, fond noir semi-transparent ou simple ombre", icon = Icons.Default.Contrast) {
                            dialog = SettingsDialog.Choose("Lisibilité des sous-titres", SubtitleBackground.entries.map { v -> MenuOption(v.label, selected = v == pr.subtitleBackground) { set { pr.subtitleBackground = v } } })
                        }
                    }
                    item("pb-sub-pos") {
                        SettingRow("Position des sous-titres", pr.subtitlePosition.label, "« Plus haut » quand un bandeau ou le bas de l'écran les cache", icon = Icons.Default.VerticalAlignBottom) {
                            dialog = SettingsDialog.Choose("Position des sous-titres", SubtitlePosition.entries.map { v -> MenuOption(v.label, selected = v == pr.subtitlePosition) { set { pr.subtitlePosition = v } } })
                        }
                    }
                    item("pb-next") {
                        SettingRow("Épisode suivant automatique", null, "Enchaîne l'épisode suivant à la fin du générique", icon = Icons.Default.SkipNext, trailing = { com.sokoul.iptv.ui.components.SokoulSwitch(pr.autoNext) }) {
                            set { pr.autoNext = !pr.autoNext }
                        }
                    }
                    item("pb-advanced") { AdvancedRow(advanced, 3) { advanced = !advanced } }
                    if (advanced) {
                        item("pb-buffer") {
                            SettingRow("Mémoire tampon", pr.buffer.label) {
                                dialog = SettingsDialog.Choose("Mémoire tampon", BufferProfile.entries.map { b -> MenuOption(b.label, selected = b == pr.buffer) { set { pr.buffer = b } } })
                            }
                        }
                        item("pb-ua") { SettingRow("User-Agent", pr.userAgent, "Le même que l'app Android : le fournisseur voit le même client") { dialog = SettingsDialog.UserAgent } }
                        item("pb-player") { SettingRow("Lecteur vidéo", if (app.player != null) "libVLC 3.0.21" else "Introuvable", "Fourni avec l'application", icon = Icons.Default.CheckCircle, valueColor = if (app.player != null) SokoulColors.TextSecondary else SokoulColors.Warning) {} }
                    }
                }
                SettingsCategory.TV -> {
                    val pr = app.prefs
                    fun set(block: () -> Unit) { block(); app.dataVersion++ }
                    item("tv-start") {
                        SettingRow("Au démarrage", if (pr.resumeLastChannel) "Dernière chaîne regardée" else "Dernier écran ouvert", "Pour ceux qui allument le PC pour regarder la télé", icon = Icons.Default.PowerSettingsNew) {
                            dialog = SettingsDialog.Choose("Au démarrage", listOf(false to "Dernier écran ouvert", true to "Dernière chaîne regardée").map { (v, label) ->
                                MenuOption(label, selected = v == pr.resumeLastChannel) { set { pr.resumeLastChannel = v } }
                            })
                        }
                    }
                    item("tv-zap") {
                        SettingRow("Sens du zapping", DesktopPrefs.zapLabel(pr.zapInverted), icon = Icons.Default.SwapVert) {
                            dialog = SettingsDialog.Choose("Sens du zapping", listOf(false, true).map { v -> MenuOption(DesktopPrefs.zapLabel(v), selected = v == pr.zapInverted) { set { pr.zapInverted = v } } })
                        }
                    }
                    subsection("Guide des programmes")
                    item("tv-offset") {
                        SettingRow("Décalage horaire du guide", DesktopPrefs.offsetLabel(pr.epgOffsetMinutes), "À ajuster si les programmes sont décalés (le guide est rechargé)", icon = Icons.Default.Schedule) {
                            dialog = SettingsDialog.Choose("Décalage du guide", (-12..12).map { h -> h * 60 }.map { m ->
                                MenuOption(DesktopPrefs.offsetLabel(m), selected = m == pr.epgOffsetMinutes) {
                                    if (m != pr.epgOffsetMinutes) { set { pr.epgOffsetMinutes = m }; app.playlist?.takeIf { !sync.running }?.let(::syncNow) }
                                }
                            })
                        }
                    }
                    item("tv-epg") {
                        val n = remember(version, sync.running) { app.playlist?.let { app.db.programCount(it.id) } ?: 0 }
                        SettingRow("Guide TV", if (n > 0) "$n programmes" else "Vide", "Mis à jour avec la playlist", icon = Icons.Default.Sync) {}
                    }
                }
                SettingsCategory.LIBRARY -> {
                    val tmdb = statuses.firstOrNull { it.id == "tmdb" }
                    item("lib-tmdb") {
                        SettingRow("Clé API TMDB", if (tmdb?.configured == true) "Configurée" else "Non configurée", "Affiches HD, résumés, casting, bandes-annonces et sagas", icon = Icons.Default.Key) {
                            dialog = SettingsDialog.Key("tmdb", tmdb?.label ?: "TMDB")
                        }
                    }
                    item("lib-order") { SettingRow("Version lancée en premier", "Français d'abord", "MULTI › VF › VOSTFR › VO, puis la meilleure qualité lisible", icon = Icons.Default.Language) {} }
                    item("lib-count") {
                        val counts = remember(version, sync.running) { app.playlist?.let { app.db.movieCount(it.id) to app.db.seriesCount(it.id) } }
                        SettingRow("Catalogue de l'abonnement", counts?.let { "${it.first} films · ${it.second} séries" } ?: "Aucune playlist", icon = Icons.Default.Folder) {}
                    }
                }
                SettingsCategory.DEBRID -> {
                    note("rd-intro", "Real-Debrid lit les films et séries depuis ses serveurs : la connexion IPTV reste libre (enregistrements, direct) et la 4K HDR devient possible.")
                    item("rd-account") {
                        val user by produceState<com.sokoul.iptv.data.debrid.RdUser?>(null, version) { value = app.debrid.user() }
                        val rd = statuses.firstOrNull { it.id == "realdebrid" }
                        val u = user
                        SettingRow(
                            "Compte Real-Debrid",
                            when { u == null && rd?.configured != true -> "Non associé"; u == null -> "Vérification…"; u.premium -> "Premium · ${u.daysLeft(System.currentTimeMillis())} jours"; else -> "Gratuit" },
                            u?.username ?: "Clé API du compte (real-debrid.com/apitoken)", icon = Icons.Default.Person,
                            valueColor = if (u?.premium == true) SokoulColors.Success else SokoulColors.TextSecondary,
                        ) { dialog = SettingsDialog.Key("realdebrid", rd?.label ?: "Real-Debrid") }
                    }
                    item("rd-size") { SettingRow("Taille maximale d'un fichier", "Automatique", "25 Go en Wi-Fi, 60 Go en câble : les gros fichiers (Remux 4K) demandent un débit élevé", icon = Icons.Default.SdStorage) {} }
                }
                SettingsCategory.PARENTAL -> {
                    val has = app.hasPin()
                    item("pa-pin") {
                        SettingRow(if (!has) "Définir un code parental" else "Modifier le code parental", if (!has) "Aucun" else "Actif", "Code à 4 chiffres pour les groupes verrouillés", icon = Icons.Default.Lock) {
                            pinError = null; dialog = if (has) SettingsDialog.VerifyPin else SettingsDialog.SetPin
                        }
                    }
                    if (has) item("pa-remove") { SettingRow("Supprimer le code", null, icon = Icons.Default.Delete) { dialog = SettingsDialog.RemovePin } }
                    subsection("Groupes masqués et verrouillés")
                    val flagged = app.playlist?.let { p -> app.db.categories(p.id).filter { it.hidden || it.locked } }.orEmpty()
                    if (flagged.isEmpty()) note("flagged", "Aucun. Clic droit sur un groupe du Direct pour le masquer ou le verrouiller.")
                    else note("flagged", "Cliquez sur un groupe pour le rétablir.")
                    items(flagged, key = { "fl-${it.id}" }) { g ->
                        SettingRow(g.name, if (g.locked) "Verrouillé" else "Masqué", icon = if (g.locked) Icons.Default.LockOpen else Icons.Default.Visibility) {
                            app.db.setCategoryFlags(g.playlistId, g.id, hidden = false, locked = false); app.dataVersion++
                        }
                    }
                }
                SettingsCategory.PROFILES -> {
                    item("pr-one") { SettingRow("Profil principal", "Actif", "Sur ce PC, un seul profil : sa liste et sa reprise viennent de l'import de la tablette", icon = Icons.Default.Person) {} }
                }
                SettingsCategory.SOURCES -> {
                    note("sources", "Sans clé, un service est simplement ignoré. Cliquez sur un service pour le tester ou saisir sa clé. Seuls les 4 derniers caractères d'une clé sont affichés.")
                    items(statuses.filter { it.id != "realdebrid" }, key = { "src-${it.id}" }) { s ->
                        val probe = probes[s.id]
                        SettingRow(
                            s.label,
                            when { s.id in testing -> "Test en cours…"; probe != null -> probe.message; s.configured -> "Configuré"; else -> "Non configuré" },
                            s.source?.let { "$it ${s.hint.orEmpty()}" },
                            valueColor = when { probe?.ok == true -> SokoulColors.Success; probe != null -> SokoulColors.Warning; else -> SokoulColors.TextSecondary },
                        ) { dialog = SettingsDialog.Key(s.id, s.label) }
                    }
                }
                SettingsCategory.BACKUP -> {
                    item("bk-restore") { SettingRow("Importer depuis ma tablette", null, "Playlists, favoris, reprises et code parental", icon = Icons.Default.Download) { dialog = SettingsDialog.Import } }
                    note("bk-how", "Sur la tablette : Réglages › Sauvegarde › Exporter (avec une phrase de passe), puis copiez le fichier sur ce PC et supprimez-le après l'import.")
                }
                SettingsCategory.ABOUT -> {
                    item("ab-version") { SettingRow("Sokoul pour Windows 1.0.0", null, "Même catalogue et mêmes réglages que l'app Android", icon = Icons.Default.CheckCircle) {} }
                    item("ab-data") { SettingRow("Données", null, AppDirs.data.absolutePath, icon = Icons.Default.Folder) {} }
                    item("ab-keys") { SettingRow("Raccourcis clavier", null, "/ recherche · Échap retour · F11 plein écran · Espace pause · ← → ±10 s · ↑ ↓ volume · M muet · Page préc./suiv. zapping", icon = Icons.Default.Keyboard) {} }
                    note("fonts", "Police Manrope (licence SIL Open Font). Données de films et séries : TMDB.")
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is SettingsDialog.PlaylistMenu -> OptionsDialog(d.p.name, listOfNotNull(
            if (d.p.id != app.playlist?.id) MenuOption("Utiliser cette playlist") { app.selectPlaylist(d.p.id); app.dataVersion++; dialog = null } else null,
            MenuOption("Mettre à jour", enabled = !sync.running) { syncNow(d.p); dialog = null },
            MenuOption("Supprimer", icon = Icons.Default.Delete) { dialog = SettingsDialog.DeletePlaylist(d.p) },
        ), { if (dialog is SettingsDialog.PlaylistMenu) dialog = null }) // l'option choisie peut ouvrir une autre fenêtre
        is SettingsDialog.DeletePlaylist -> ConfirmDialog(
            "Supprimer « ${d.p.name} » ?", "Ses chaînes, son guide, ses favoris et ses reprises sont retirés de ce PC. La tablette n'est pas touchée.", "Supprimer",
            { app.db.deletePlaylist(d.p.id); app.reloadPlaylist(); app.dataVersion++; dialog = null }, { dialog = null },
        )
        SettingsDialog.AddPlaylist -> AddPlaylistDialog(app, { dialog = null }) { p -> dialog = null; syncNow(p) }
        SettingsDialog.Import -> ImportDialog(app) { dialog = null }
        is SettingsDialog.Key -> KeyDialog(app, d.id, d.label, probes[d.id], d.id in testing, { test(d.id) }) { changed ->
            if (changed) { probes = probes - d.id; app.dataVersion++ }
            dialog = null
        }
        SettingsDialog.SetPin -> PinDialog("Nouveau code parental", { pin ->
            if (pin.length == 4 && pin.all(Char::isDigit)) { app.db.putSetting(Settings.PIN, PinHasher.hash(pin)); app.dataVersion++; dialog = null }
            else pinError = "4 chiffres"
        }, { dialog = null }, pinError)
        is SettingsDialog.Choose -> OptionsDialog(d.title, d.options, { if (dialog === d) dialog = null })
        SettingsDialog.UserAgent -> {
            var v by remember { mutableStateOf(app.prefs.userAgent) }
            SokoulDialog({ dialog = null }, maxWidth = 620.dp) {
                DialogHeader("User-Agent", "Laissé vide : celui de l'app Android")
                FormField("User-Agent", v, { v = it }, Modifier.padding(top = Space.m).fillMaxWidth())
                Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    SokoulButton("Enregistrer", { app.prefs.userAgent = v; app.dataVersion++; dialog = null }, kind = ButtonKind.Primary)
                    SokoulButton("Par défaut", { app.prefs.userAgent = ""; app.dataVersion++; dialog = null })
                }
            }
        }
        SettingsDialog.VerifyPin -> PinDialog("Code actuel", { pin ->
            if (app.checkPin(pin)) { pinError = null; dialog = SettingsDialog.SetPin } else pinError = "Code incorrect"
        }, { dialog = null }, pinError)
        SettingsDialog.RemovePin -> PinDialog("Code actuel", { pin ->
            if (app.checkPin(pin)) { app.db.putSetting(Settings.PIN, null); app.dataVersion++; dialog = null } else pinError = "Code incorrect"
        }, { dialog = null }, pinError)
    }
}

private fun syncLine(s: com.sokoul.desktop.data.SyncState): String? = when {
    s.running -> "Playlist : ${s.step}"
    s.error != null -> "Playlist : ${s.error}"
    s.step.isNotEmpty() -> "Playlist : à jour (${s.channels})"
    else -> null
}

/** `PlaylistRow` de la tablette, avec les compteurs de la base du PC. */
@Composable
private fun PlaylistRow(app: AppState, p: Playlist, active: Boolean, status: String?, version: Int, onClick: () -> Unit) {
    val counts = remember(p.id, p.syncedAt, version) { Triple(app.db.channelCount(p.id), app.db.movieCount(p.id), app.db.seriesCount(p.id)) }
    FocusSurface(style = FocusStyle.Row, onClick = onClick, modifier = Modifier.fillMaxWidth(), color = if (active) SokoulColors.AccentTint else SokoulColors.Surface) { _ ->
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(if (p.type == PlaylistType.XTREAM) "Xtream" else "M3U", color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                if (active) Text("  ·  ACTIVE", color = SokoulColors.Accent, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${counts.first} chaînes · ${counts.second} films · ${counts.third} séries" + (if (p.syncedAt > 0) " · synchronisée ${formatDateTime(p.syncedAt)}" else ""),
                color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            status?.let { Text(it, color = if (it.contains("erreur", true)) SokoulColors.Live else SokoulColors.Accent, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun AddPlaylistDialog(app: AppState, onDismiss: () -> Unit, onAdded: (Playlist) -> Unit) {
    var xtream by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    SokoulDialog(onDismiss, maxWidth = 620.dp) {
        DialogHeader("Ajouter une playlist", "Xtream Codes ou lien M3U")
        Row(Modifier.padding(vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SokoulChip("Xtream Codes", { xtream = true }, selected = xtream)
            SokoulChip("Lien M3U", { xtream = false }, selected = !xtream)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField("Nom (facultatif)", name, { name = it }, Modifier.fillMaxWidth())
            FormField(if (xtream) "Serveur" else "Lien de la playlist M3U", url, { url = it }, Modifier.fillMaxWidth(), placeholder = if (xtream) "http://serveur:port" else "http://…")
            if (xtream) {
                FormField("Identifiant", user, { user = it }, Modifier.fillMaxWidth())
                FormField("Mot de passe", pass, { pass = it }, Modifier.fillMaxWidth(), password = true)
            }
        }
        error?.let { Text(it, Modifier.padding(top = 10.dp), color = SokoulColors.Live, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SokoulButton("Ajouter", {
                val u = url.trim()
                if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) { error = "Le lien doit commencer par http:// ou https://"; return@SokoulButton }
                if (xtream && (user.isBlank() || pass.isBlank())) { error = "Identifiant et mot de passe requis"; return@SokoulButton }
                val id = app.db.upsertPlaylist(Playlist(0, name.ifBlank { if (xtream) "IPTV" else "Playlist M3U" }, if (xtream) PlaylistType.XTREAM else PlaylistType.M3U, u, user.takeIf { xtream }, pass.takeIf { xtream }, null, null))
                app.selectPlaylist(id)
                app.playlist?.let(onAdded)
            }, kind = ButtonKind.Primary)
            SokoulButton("Annuler", onDismiss)
        }
    }
}

@Composable
private fun ImportDialog(app: AppState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) }
    var pass by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val encrypted = remember(file) { file?.let { runCatching { BackupReader.isEncrypted(it.readText()) }.getOrDefault(false) } ?: false }
    SokoulDialog(onDismiss, maxWidth = 620.dp) {
        DialogHeader("Importer depuis ma tablette", "Playlists, favoris, reprises et code parental sont repris.")
        Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
            SokoulButton("Choisir le fichier…", { pickFile()?.let { file = it; message = null } })
            Text(file?.name ?: "Aucun fichier choisi", Modifier.padding(start = 14.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (encrypted) FormField("Phrase de passe de la sauvegarde", pass, { pass = it }, Modifier.padding(top = 12.dp).fillMaxWidth(), password = true)
        message?.let { Text(it, Modifier.padding(top = 10.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SokoulButton(if (busy) "Import…" else "Importer", {
                val f = file ?: return@SokoulButton
                busy = true; message = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { DesktopImport(app.db).apply(BackupReader.read(f.readText(), pass.ifEmpty { null })) } }
                    busy = false
                    result.onSuccess { s ->
                        message = "${s.playlists} playlist(s), ${s.favorites} favori(s) et ${s.progress} reprise(s) importés. Synchronisation des chaînes…"
                        pass = ""
                        app.reloadPlaylist()
                        app.playlist?.let { p -> launch { app.sync.sync(p); app.dataVersion++ } }
                    }.onFailure { message = it.message ?: "Import impossible" }
                }
            }, kind = ButtonKind.Primary, enabled = file != null && !busy && (!encrypted || pass.isNotEmpty()))
            SokoulButton("Fermer", onDismiss)
        }
    }
}

/** Clé d'un service : tester, saisir une clé (masquée) ou revenir à la clé intégrée. */
@Composable
private fun KeyDialog(app: AppState, id: String, label: String, probe: ProbeResult?, testing: Boolean, onTest: () -> Unit, onClose: (changed: Boolean) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val status = remember(id) { ProviderConfig.statuses(app.db).firstOrNull { it.id == id } }
    SokoulDialog({ onClose(false) }, maxWidth = 620.dp) {
        DialogHeader(label, status?.source?.let { "$it ${status.hint.orEmpty()}" } ?: "Non configuré")
        Text(
            when { testing -> "Test en cours…"; probe != null -> probe.message; else -> "Pas encore testé" },
            Modifier.padding(top = Space.s), style = MaterialTheme.typography.bodyMedium,
            color = when { probe?.ok == true -> SokoulColors.Success; probe != null -> SokoulColors.Warning; else -> SokoulColors.TextMuted },
        )
        FormField("Nouvelle clé", draft, { draft = it.trim() }, Modifier.padding(top = Space.m).fillMaxWidth(), password = true)
        Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SokoulButton("Enregistrer", { ProviderConfig.setKey(app.db, id, draft); onClose(true) }, kind = ButtonKind.Primary, enabled = draft.isNotBlank())
            SokoulButton(if (probe != null) "Retester" else "Tester", onTest, enabled = status?.configured == true && !testing)
            SokoulButton("Clé intégrée", { ProviderConfig.setKey(app.db, id, null); onClose(true) })
        }
    }
}

private fun pickFile(): File? {
    val d = FileDialog(null as Frame?, "Sauvegarde Sokoul de la tablette", FileDialog.LOAD).apply { file = "*.json"; isVisible = true }
    return d.file?.let { File(d.directory, it) }
}
