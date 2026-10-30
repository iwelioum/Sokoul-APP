package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.Screen
import com.sokoul.desktop.data.BackupReader
import com.sokoul.desktop.data.DesktopImport
import com.sokoul.desktop.data.Playlist
import com.sokoul.desktop.data.PlaylistType
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.desktop.data.ProbeResult
import com.sokoul.desktop.data.ProviderProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Premier lancement : reprendre la configuration de la tablette, ou ajouter une playlist. */
@Composable
fun WelcomeScreen(app: AppState) {
    Box(Modifier.fillMaxSize().background(SokoulColors.Background), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 620.dp).padding(32.dp).verticalScroll(rememberScrollState())) {
            Text("Bienvenue dans Sokoul", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Reprenez la configuration de votre tablette (playlists, favoris, code parental) ou ajoutez une playlist.",
                Modifier.padding(top = 8.dp, bottom = 28.dp), color = SokoulColors.TextSecondary,
            )
            ImportPanel(app)
            Box(Modifier.padding(vertical = 22.dp).fillMaxWidth().background(SokoulColors.Divider).padding(0.5.dp))
            AddPlaylistPanel(app)
        }
    }
}

/** Import de la sauvegarde exportée depuis l'app Android (Réglages › Sauvegarde, de préférence chiffrée). */
@Composable
fun ImportPanel(app: AppState) {
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) }
    var pass by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(SokoulColors.Surface, Radius.Panel).padding(20.dp)) {
        Text("Importer depuis ma tablette", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Sur la tablette : Réglages › Sauvegarde › Exporter (avec une phrase de passe), puis copiez le fichier sur ce PC.",
            Modifier.padding(top = 4.dp, bottom = 14.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SokoulButton("Choisir le fichier…", { pickFile()?.let { file = it; message = null } }, primary = false, icon = Icons.Default.Tablet)
            Text(file?.name ?: "Aucun fichier choisi", color = SokoulColors.TextMuted)
        }
        val encrypted = remember(file) { file?.let { runCatching { BackupReader.isEncrypted(it.readText()) }.getOrDefault(false) } ?: false }
        if (encrypted) OutlinedTextField(
            pass, { pass = it }, Modifier.padding(top = 12.dp).fillMaxWidth(), singleLine = true,
            label = { Text("Phrase de passe de la sauvegarde") }, visualTransformation = PasswordVisualTransformation(),
        )
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
            message?.let { Text(it, Modifier.padding(start = 14.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
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
    Column(Modifier.fillMaxWidth().background(SokoulColors.Surface, Radius.Panel).padding(20.dp)) {
        Text("Ajouter une playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SokoulButton("Xtream Codes", { xtream = true }, primary = xtream)
            SokoulButton("Lien M3U", { xtream = false }, primary = !xtream)
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nom (facultatif)") })
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text(if (xtream) "Serveur (http://…:port)" else "Lien de la playlist M3U") })
        if (xtream) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it }, Modifier.weight(1f), singleLine = true, label = { Text("Identifiant") })
                OutlinedTextField(pass, { pass = it }, Modifier.weight(1f), singleLine = true, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation())
            }
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
            error?.let { Text(it, Modifier.padding(start = 14.dp), color = SokoulColors.Live) }
        }
    }
}

/** Réglages : playlists (choix, synchronisation), import, état de la synchronisation. */
@Composable
fun SettingsScreen(app: AppState) {
    val scope = rememberCoroutineScope()
    val sync by app.sync.state.collectAsState()
    val playlists = remember(app.dataVersion) { app.db.playlists() }
    var probes by remember { mutableStateOf<Map<String, ProbeResult>>(emptyMap()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        ScreenTitle("Réglages")
        Text("Playlists", Modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        playlists.forEach { p ->
            Row(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(vertical = 4.dp).background(SokoulColors.Surface, Radius.Card).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name + if (p.id == app.playlist?.id) "  · active" else "", fontWeight = FontWeight.SemiBold)
                    val counts = "${app.db.channelCount(p.id)} chaînes · ${app.db.programCount(p.id)} programmes · ${app.db.movieCount(p.id)} films · ${app.db.seriesCount(p.id)} séries"
                    val when_ = if (p.syncedAt > 0) "mise à jour ${dayLabel(p.syncedAt).lowercase()} à ${clock(p.syncedAt)}" else "jamais synchronisée"
                    Text("${if (p.type == PlaylistType.XTREAM) "Xtream Codes" else "M3U"} · $counts · $when_", color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (p.id != app.playlist?.id) SokoulButton("Utiliser", { app.selectPlaylist(p.id) }, primary = false)
                SokoulButton("Mettre à jour", { scope.launch { app.sync.sync(p); app.reloadPlaylist() } }, primary = false, icon = Icons.Default.Refresh, enabled = !sync.running)
            }
        }
        Text("Fournisseurs de métadonnées", Modifier.padding(top = 28.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Les clés locales sont détectées automatiquement sans être affichées. Tu peux ensuite ajouter une clé desktop dans la configuration de l'application.",
            Modifier.widthIn(max = 900.dp).padding(bottom = 10.dp), color = SokoulColors.TextSecondary,
        )
        ProviderConfig.statuses(app.db).forEach { provider ->
            Row(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(vertical = 3.dp).background(SokoulColors.Surface, Radius.Card).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(provider.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        probes[provider.id]?.message ?: provider.source ?: "Non configuré",
                        color = when {
                            probes[provider.id]?.ok == true -> SokoulColors.Success
                            probes[provider.id] != null -> SokoulColors.Warning
                            provider.configured -> SokoulColors.Success
                            else -> SokoulColors.TextMuted
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SokoulButton(
                    if (probes.containsKey(provider.id)) "Retester" else "Tester",
                    {
                        scope.launch {
                            probes = probes + (provider.id to ProviderProbe.check(app.db, provider.id))
                        }
                    },
                    primary = false,
                    enabled = provider.configured,
                )
            }
        }
        if (sync.running || sync.error != null || sync.step.isNotEmpty()) {
            Text(
                when {
                    sync.running -> "Synchronisation : ${sync.step}"
                    sync.error != null -> "Dernière synchronisation : ${sync.error}"
                    else -> "Synchronisation terminée : ${sync.channels} chaînes, ${sync.programs} programmes, ${sync.movies} films, ${sync.series} séries"
                },
                Modifier.padding(top = 8.dp), color = if (sync.error != null) SokoulColors.Warning else SokoulColors.TextSecondary,
            )
        }
        Box(Modifier.padding(top = 24.dp).widthIn(max = 900.dp)) { ImportPanel(app) }
        Box(Modifier.padding(top = 16.dp).widthIn(max = 900.dp)) { AddPlaylistPanel(app) }
        Text(
            "Une seule connexion à l'abonnement IPTV : ne regardez pas la même playlist sur la tablette et sur le PC en même temps.",
            Modifier.padding(top = 24.dp).width(900.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall,
        )
        SokoulButton("Retour au Direct", { app.screen = Screen.LIVE }, Modifier.padding(top = 16.dp), primary = false)
    }
}

private fun pickFile(): File? {
    val d = FileDialog(null as Frame?, "Sauvegarde Sokoul de la tablette", FileDialog.LOAD).apply { file = "*.json"; isVisible = true }
    return d.file?.let { File(d.directory, it) }
}
