package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sokoul.desktop.data.Settings
import com.sokoul.iptv.data.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private enum class Section(val label: String, val icon: ImageVector) {
    PLAYLISTS("Playlists", Icons.Outlined.PlaylistPlay),
    KEYS("Clés API", Icons.Outlined.Key),
    PARENTAL("Contrôle parental", Icons.Outlined.Lock),
    ABOUT("À propos", Icons.Outlined.Info),
}

/** Réglages par sections : playlists (import de la tablette, ajout), clés API (état, test), code parental, à propos. */
@Composable
fun SettingsScreen(app: AppState) {
    LaunchedEffect(Unit) { app.navSolid = true }
    var section by remember { mutableStateOf(if (app.playlist == null) Section.PLAYLISTS else Section.KEYS) }
    Row(Modifier.fillMaxSize().background(SokoulColors.Background).padding(top = 76.dp)) {
        Column(Modifier.width(280.dp).fillMaxHeight().background(SokoulColors.Surface).padding(vertical = 22.dp, horizontal = 12.dp)) {
            Text("Réglages", Modifier.padding(start = 12.dp, bottom = 18.dp), style = Type.ScreenTitle)
            Section.entries.forEach { s ->
                val h = rememberHover()
                Row(
                    Modifier.fillMaxWidth().background(if (s == section) SokoulColors.AccentSoft else if (h.active) SokoulColors.SurfaceHigh else Color.Transparent, Radius.Card)
                        .interactive(h) { section = s }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(s.icon, null, Modifier.size(20.dp), tint = if (s == section) SokoulColors.Accent else SokoulColors.TextSecondary)
                    Text(s.label, Modifier.padding(start = 12.dp), style = Type.Nav.copy(color = if (s == section) SokoulColors.TextPrimary else SokoulColors.TextSecondary))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 30.dp)) {
            Column(Modifier.widthIn(max = 940.dp)) {
                when (section) {
                    Section.PLAYLISTS -> PlaylistsSection(app)
                    Section.KEYS -> KeysSection(app)
                    Section.PARENTAL -> ParentalSection(app)
                    Section.ABOUT -> AboutSection(app)
                }
            }
        }
    }
}

@Composable
private fun SectionHead(title: String, text: String) {
    Text(title, style = Type.Section)
    Text(text, Modifier.padding(top = 6.dp, bottom = 22.dp), style = Type.Body)
}

// --- playlists -------------------------------------------------------------------------------------------

@Composable
private fun PlaylistsSection(app: AppState) {
    val scope = rememberCoroutineScope()
    val sync by app.sync.state.collectAsState()
    val playlists = remember(app.dataVersion) { app.db.playlists() }
    SectionHead("Playlists", "Votre abonnement IPTV : chaînes, guide TV et catalogue. Une seule connexion à la fois : ne regardez pas la même playlist sur la tablette et sur le PC en même temps.")
    playlists.forEach { p ->
        Panel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = Type.ItemTitle)
                        if (p.id == app.playlist?.id) Badge("ACTIVE", SokoulColors.AccentSoft, modifier = Modifier.padding(start = 10.dp))
                    }
                    val counts = "${app.db.channelCount(p.id)} chaînes · ${app.db.programCount(p.id)} programmes · ${app.db.movieCount(p.id)} films · ${app.db.seriesCount(p.id)} séries"
                    val when_ = if (p.syncedAt > 0) "mise à jour ${dayLabel(p.syncedAt).lowercase()} à ${clock(p.syncedAt)}" else "jamais synchronisée"
                    Text("${if (p.type == PlaylistType.XTREAM) "Xtream Codes" else "M3U"} · $counts · $when_", Modifier.padding(top = 4.dp), style = Type.Small)
                }
                if (p.id != app.playlist?.id) { SokoulButton("Utiliser", { app.selectPlaylist(p.id) }, style = ButtonStyle.Ghost); HSpace(8.dp) }
                SokoulButton("Mettre à jour", { scope.launch { app.sync.sync(p); app.reloadPlaylist() } }, style = ButtonStyle.Glass, icon = Icons.Filled.Refresh, enabled = !sync.running)
            }
        }
    }
    if (sync.running || sync.error != null || sync.step.isNotEmpty()) {
        Text(
            when {
                sync.running -> "Synchronisation : ${sync.step}"
                sync.error != null -> "Dernière synchronisation : ${sync.error}"
                else -> "Synchronisation terminée : ${sync.channels} chaînes, ${sync.programs} programmes, ${sync.movies} films, ${sync.series} séries"
            },
            Modifier.padding(vertical = 8.dp), style = Type.Small.copy(color = if (sync.error != null) SokoulColors.Warning else SokoulColors.TextSecondary),
        )
    }
    VSpace(14.dp)
    ImportPanel(app)
    VSpace(14.dp)
    AddPlaylistPanel(app)
}

/** Import de la sauvegarde exportée depuis l'app Android (Réglages › Sauvegarde, de préférence chiffrée). */
@Composable
fun ImportPanel(app: AppState) {
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) }
    var pass by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Tablet, null, tint = SokoulColors.Accent)
            Text("Importer depuis ma tablette", Modifier.padding(start = 10.dp), style = Type.ItemTitle)
        }
        Text(
            "Sur la tablette : Réglages › Sauvegarde › Exporter (avec une phrase de passe), puis copiez le fichier sur ce PC. Playlists, favoris, reprises et code parental sont repris.",
            Modifier.padding(top = 6.dp, bottom = 16.dp), style = Type.Small,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SokoulButton("Choisir le fichier…", { pickFile()?.let { file = it; message = null } }, style = ButtonStyle.Glass)
            Text(file?.name ?: "Aucun fichier choisi", Modifier.padding(start = 14.dp), style = Type.Small)
        }
        val encrypted = remember(file) { file?.let { runCatching { BackupReader.isEncrypted(it.readText()) }.getOrDefault(false) } ?: false }
        if (encrypted) SokoulTextField(pass, { pass = it }, "Phrase de passe de la sauvegarde", Modifier.padding(top = 12.dp).fillMaxWidth(), password = true)
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SokoulButton(if (busy) "Import…" else "Importer", {
                val f = file ?: return@SokoulButton
                busy = true; message = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { DesktopImport(app.db).apply(BackupReader.read(f.readText(), pass.ifEmpty { null })) }
                    }
                    busy = false
                    result.onSuccess { s ->
                        message = "${s.playlists} playlist(s), ${s.favorites} favori(s) et ${s.progress} reprise(s) importés. Synchronisation des chaînes…"
                        pass = ""
                        app.reloadPlaylist()
                        app.playlist?.let { p -> launch { app.sync.sync(p); app.dataVersion++ } }
                    }.onFailure { message = it.message ?: "Import impossible" }
                }
            }, enabled = file != null && !busy && (!encrypted || pass.isNotEmpty()))
            message?.let { Text(it, Modifier.padding(start = 14.dp), style = Type.Small) }
        }
    }
}

@Composable
fun AddPlaylistPanel(app: AppState) {
    val scope = rememberCoroutineScope()
    var xtream by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Panel(Modifier.fillMaxWidth()) {
        Text("Ajouter une playlist", style = Type.ItemTitle)
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Xtream Codes", xtream, { xtream = true })
            Chip("Lien M3U", !xtream, { xtream = false })
        }
        SokoulTextField(name, { name = it }, "Nom (facultatif)", Modifier.fillMaxWidth())
        SokoulTextField(url, { url = it }, if (xtream) "Serveur (http://…:port)" else "Lien de la playlist M3U", Modifier.fillMaxWidth().padding(top = 10.dp))
        if (xtream) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SokoulTextField(user, { user = it }, "Identifiant", Modifier.weight(1f))
            SokoulTextField(pass, { pass = it }, "Mot de passe", Modifier.weight(1f), password = true)
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SokoulButton("Ajouter", {
                val u = url.trim()
                if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) { error = "Le lien doit commencer par http:// ou https://"; return@SokoulButton }
                if (xtream && (user.isBlank() || pass.isBlank())) { error = "Identifiant et mot de passe requis"; return@SokoulButton }
                val p = Playlist(0, name.ifBlank { if (xtream) "IPTV" else "Playlist M3U" }, if (xtream) PlaylistType.XTREAM else PlaylistType.M3U, u,
                    user.takeIf { xtream }, pass.takeIf { xtream }, null, null)
                val id = app.db.upsertPlaylist(p)
                app.selectPlaylist(id)
                app.playlist?.let { pl -> scope.launch { app.sync.sync(pl); app.dataVersion++ } }
                error = null; pass = ""
            })
            error?.let { Text(it, Modifier.padding(start = 14.dp), style = Type.Small.copy(color = SokoulColors.Live)) }
        }
    }
}

// --- clés API ------------------------------------------------------------------------------------------

@Composable
private fun KeysSection(app: AppState) {
    val scope = rememberCoroutineScope()
    var probes by remember { mutableStateOf<Map<String, ProbeResult>>(emptyMap()) }
    var testing by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val statuses = remember(app.dataVersion, editing) { ProviderConfig.statuses(app.db) }
    val rd by produceState<com.sokoul.iptv.data.debrid.RdUser?>(null, app.dataVersion) { value = app.debrid.user() }

    SectionHead("Clés API", "Les services qui enrichissent Sokoul. Les clés intégrées viennent de la configuration du projet ; une clé saisie ici les remplace. Seuls les 4 derniers caractères sont affichés.")
    rd?.let { u ->
        Panel(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Kicker("Compte Real-Debrid")
                    Text(u.username, Modifier.padding(top = 4.dp), style = Type.ItemTitle)
                }
                Badge(if (u.premium) "PREMIUM · ${u.daysLeft(System.currentTimeMillis())} JOURS RESTANTS" else "GRATUIT", if (u.premium) SokoulColors.Success else SokoulColors.Warning, SokoulColors.OnLight)
            }
        }
    }
    SokoulButton("Tout tester", {
        statuses.filter { it.configured }.forEach { s ->
            testing = testing + s.id
            scope.launch { probes = probes + (s.id to ProviderProbe.check(app.db, s.id)); testing = testing - s.id }
        }
    }, Modifier.padding(bottom = 14.dp), style = ButtonStyle.Glass)
    statuses.forEach { s ->
        Panel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val probe = probes[s.id]
                Icon(
                    if (probe?.ok == false || !s.configured) Icons.Outlined.ErrorOutline else Icons.Filled.CheckCircle, null, Modifier.size(20.dp),
                    tint = when { probe?.ok == true -> SokoulColors.Success; probe != null -> SokoulColors.Warning; s.configured -> SokoulColors.TextSecondary; else -> SokoulColors.TextMuted },
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(s.label, style = Type.ItemTitle)
                    Text(
                        listOfNotNull(
                            when { s.id in testing -> "Test en cours…"; probe != null -> probe.message; else -> null },
                            s.source?.let { src -> "$src ${s.hint.orEmpty()}" } ?: "Non configuré",
                        ).joinToString(" · "),
                        style = Type.Small.copy(color = when { probe?.ok == true -> SokoulColors.Success; probe != null -> SokoulColors.Warning; else -> SokoulColors.TextSecondary }),
                    )
                }
                SokoulButton("Modifier", { editing = if (editing == s.id) null else s.id; draft = "" }, style = ButtonStyle.Ghost)
                HSpace(8.dp)
                SokoulButton(if (probe != null) "Retester" else "Tester", {
                    testing = testing + s.id
                    scope.launch { probes = probes + (s.id to ProviderProbe.check(app.db, s.id)); testing = testing - s.id }
                }, style = ButtonStyle.Glass, enabled = s.configured && s.id !in testing)
            }
            if (editing == s.id) Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                SokoulTextField(draft, { draft = it.trim() }, "Nouvelle clé ${s.label}", Modifier.weight(1f), password = true)
                HSpace(10.dp)
                SokoulButton("Enregistrer", {
                    ProviderConfig.setKey(app.db, s.id, draft); editing = null; probes = probes - s.id; app.dataVersion++
                }, enabled = draft.isNotBlank())
                HSpace(8.dp)
                SokoulButton("Clé intégrée", { ProviderConfig.setKey(app.db, s.id, null); editing = null; probes = probes - s.id; app.dataVersion++ }, style = ButtonStyle.Ghost)
            }
        }
    }
}

// --- contrôle parental, à propos -----------------------------------------------------------------------

@Composable
private fun ParentalSection(app: AppState) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var has by remember { mutableStateOf(app.hasPin()) }
    SectionHead("Contrôle parental", "Le code protège les groupes verrouillés du Direct. Il est repris de la tablette à l'import ; vous pouvez en définir un ici.")
    Panel(Modifier.fillMaxWidth()) {
        Text(if (has) "Un code est défini." else "Aucun code défini.", style = Type.ItemTitle)
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SokoulTextField(pin, { v -> pin = v.filter(Char::isDigit).take(4) }, "Nouveau code (4 chiffres)", Modifier.width(260.dp), password = true, digits = true)
            HSpace(10.dp)
            SokoulButton("Enregistrer", {
                app.db.putSetting(Settings.PIN, PinHasher.hash(pin)); pin = ""; has = true; message = "Code enregistré."
            }, enabled = pin.length == 4)
            if (has) { HSpace(8.dp); SokoulButton("Supprimer", { app.db.putSetting(Settings.PIN, null); has = false; message = "Code supprimé." }, style = ButtonStyle.Ghost) }
        }
        message?.let { Text(it, Modifier.padding(top = 10.dp), style = Type.Small.copy(color = SokoulColors.Success)) }
    }
}

@Composable
private fun AboutSection(app: AppState) {
    SectionHead("À propos", "Sokoul pour Windows : même catalogue et mêmes réglages que l'app Android.")
    Panel(Modifier.fillMaxWidth()) {
        listOf(
            "Version" to "1.0.0",
            "Données" to AppDirs.data.absolutePath,
            "Lecteur" to if (app.player != null) "libVLC 3.0.21 (fourni)" else "libVLC introuvable",
            "Raccourcis" to "/ recherche · Échap retour · F11 plein écran · Espace pause · ← → ±10 s · ↑ ↓ volume · M muet · Page préc./suiv. zapping",
        ).forEach { (k, v) ->
            Row(Modifier.padding(vertical = 6.dp)) {
                Text(k, Modifier.width(140.dp), style = Type.Small.copy(color = SokoulColors.TextMuted))
                Text(v, style = Type.Small.copy(color = SokoulColors.TextPrimary))
            }
        }
    }
}

private fun pickFile(): File? {
    val d = FileDialog(null as Frame?, "Sauvegarde Sokoul de la tablette", FileDialog.LOAD).apply { file = "*.json"; isVisible = true }
    return d.file?.let { File(d.directory, it) }
}
