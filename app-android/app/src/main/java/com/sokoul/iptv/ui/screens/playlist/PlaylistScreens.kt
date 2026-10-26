package com.sokoul.iptv.ui.screens.playlist

import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.components.shimmer
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SyncEngine
import com.sokoul.iptv.data.repo.SyncStatus
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.parser.ServerUrl
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.components.ButtonKind
import com.sokoul.iptv.ui.components.ConfirmDialog
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.FormField
import com.sokoul.iptv.ui.nav.HomeRoute
import com.sokoul.iptv.ui.nav.PlaylistEditRoute
import com.sokoul.iptv.ui.nav.SyncRoute
import com.sokoul.iptv.ui.util.hasDocumentPicker
import com.sokoul.iptv.work.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistEditViewModel @Inject constructor(
    private val repo: PlaylistRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    suspend fun load(id: Long): PlaylistEntity? = repo.get(id)

    fun addXtream(name: String, server: String, user: String, pass: String, epg: String, ua: String, onDone: (Long) -> Unit) = run {
        require(server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) { "Serveur, identifiant et mot de passe sont obligatoires" }
        repo.addXtream(name, server, user, pass, epg, ua).also(onDone)
    }

    fun addM3u(name: String, url: String, epg: String, ua: String, onDone: (Long) -> Unit) = run {
        // URL get.php d'un panel Xtream : on bascule en mode Xtream, bien plus complet
        val xt = ServerUrl.extractXtream(url)
        if (xt != null) repo.addXtream(name, xt.server, xt.username, xt.password, epg, ua).also(onDone)
        else repo.addM3u(name, url, epg, ua).also(onDone)
    }

    fun update(p: PlaylistEntity, name: String, epg: String, ua: String, pass: String, onDone: () -> Unit) = run {
        repo.updateDetails(p, name, epg, ua, pass.ifBlank { null })
        onDone()
    }

    fun delete(id: Long, onDone: () -> Unit) = run { repo.delete(id); onDone() }

    fun startSync(id: Long) = SyncWorker.syncNow(context, id, withEpg = true)

    private fun run(block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            try {
                block()
            } catch (e: Exception) {
                error.value = e.userMessage()
            } finally {
                busy.value = false
            }
        }
    }
}

@Composable
fun PlaylistEditScreen(nav: NavHostController, id: Long, firstRun: Boolean) {
    val vm: PlaylistEditViewModel = hiltViewModel()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var existing by remember { mutableStateOf<PlaylistEntity?>(null) }
    var type by remember { mutableStateOf(PlaylistType.XTREAM) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var ua by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val context = LocalContext.current

    LaunchedEffect(id) {
        if (id > 0) vm.load(id)?.let { p ->
            existing = p; type = p.type; name = p.name; epg = p.epgUrl.orEmpty(); ua = p.userAgent.orEmpty()
            if (p.type == PlaylistType.XTREAM) { server = p.url; user = p.username.orEmpty() } else m3u = p.url
        }
        runCatching { firstFocus.requestFocus() }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            m3u = uri.toString()
            if (name.isBlank()) name = "Playlist locale"
        }
    }

    val onSaved: (Long) -> Unit = { newId ->
        vm.startSync(newId)
        nav.navigate(SyncRoute(newId)) { popUpTo(nav.graph.id) { inclusive = true } }
    }

    Box(Modifier.fillMaxSize().background(SokoulColors.Background).statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (firstRun) {
                SetupSteps(1)
                Text("BIENVENUE", style = com.sokoul.iptv.ui.theme.Type.Kicker)
                Text("Sokoul TV", style = com.sokoul.iptv.ui.theme.Type.Display)
                Text(
                    "Ajoutez votre abonnement IPTV pour commencer. Les comptes Xtream Codes donnent accès au guide TV complet, au replay et aux fiches détaillées des films et séries.",
                    color = SokoulColors.TextSecondary,
                )
            } else {
                if (existing == null) SetupSteps(1)
                Text(if (existing != null) "Modifier la playlist" else "Nouvelle playlist", style = com.sokoul.iptv.ui.theme.Type.ScreenTitle)
            }

            if (existing == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TypeChoice("Xtream Codes", type == PlaylistType.XTREAM, Modifier.weight(1f).focusRequester(firstFocus)) { type = PlaylistType.XTREAM }
                    TypeChoice("Lien M3U / fichier", type == PlaylistType.M3U, Modifier.weight(1f)) { type = PlaylistType.M3U }
                }
            }

            FormField("Nom (facultatif)", name, { name = it }, placeholder = "Mon abonnement")
            if (type == PlaylistType.XTREAM) {
                if (existing == null) {
                    FormField("Adresse du serveur", server, { server = it }, placeholder = "http://exemple.com:8080", keyboardType = KeyboardType.Uri)
                    FormField("Nom d'utilisateur", user, { user = it })
                }
                FormField(if (existing != null) "Nouveau mot de passe (laisser vide pour garder)" else "Mot de passe", pass, { pass = it }, password = true)
            } else if (existing == null) {
                FormField("URL de la playlist M3U", m3u, { m3u = it }, placeholder = "http://…/playlist.m3u", keyboardType = KeyboardType.Uri)
                if (ServerUrl.extractXtream(m3u) != null) {
                    Text("✓ Lien Xtream détecté : le compte sera ajouté en mode Xtream (guide, replay et fiches complètes).", color = SokoulColors.Success, style = MaterialTheme.typography.bodyMedium)
                }
                // Masqué sur Android TV : le sélecteur de fichiers y est factice (rien ne s'ouvrirait)
                if (remember { context.hasDocumentPicker() }) {
                    SokoulButton("Choisir un fichier sur l'appareil", { filePicker.launch(arrayOf("*/*")) }, icon = Icons.Default.FolderOpen)
                }
            }
            FormField("Guide TV XMLTV (facultatif)", epg, { epg = it }, placeholder = "Fourni par le serveur si vide", keyboardType = KeyboardType.Uri)
            FormField("User-Agent (facultatif)", ua, { ua = it }, placeholder = "Seulement si votre fournisseur l'exige", imeAction = ImeAction.Done)

            if (error != null) {
                Row(
                    Modifier.fillMaxWidth().background(SokoulColors.Live.copy(alpha = 0.15f), Radius.Card).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = SokoulColors.Live)
                    Text(error!!, Modifier.padding(start = 10.dp), color = SokoulColors.TextPrimary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val p = existing
                SokoulButton(
                    text = if (busy) "Vérification…" else if (p != null) "Enregistrer" else "Ajouter et synchroniser",
                    onClick = {
                        when {
                            p != null -> vm.update(p, name, epg, ua, pass) { nav.popBackStack() }
                            type == PlaylistType.XTREAM -> vm.addXtream(name, server, user, pass, epg, ua, onSaved)
                            else -> vm.addM3u(name, m3u, epg, ua, onSaved)
                        }
                    },
                    kind = ButtonKind.Primary,
                    enabled = !busy,
                    focusRequester = if (p != null) firstFocus else null,
                )
                if (busy) com.sokoul.iptv.ui.components.SokoulSpinner(size = 28.dp)
                if (p != null) {
                    SokoulButton("Supprimer", { confirmDelete = true }, icon = Icons.Default.Delete)
                }
                if (!firstRun && p == null) SokoulButton("Annuler", { nav.popBackStack() })
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmDelete) {
        existing?.let { p ->
            ConfirmDialog(
                "Supprimer « ${p.name} » ?",
                "Les chaînes, favoris, reprises de lecture et le guide de cette playlist seront supprimés de l'appareil.",
                "Supprimer",
                onConfirm = { vm.delete(p.id) { nav.popBackStack() } },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun TypeChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(style = FocusStyle.Row,
        onClick = onClick,
        modifier = modifier,
        color = if (selected) SokoulColors.AccentTint else SokoulColors.Surface,
    ) { _ ->
        Text(
            label, Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
            color = if (selected) SokoulColors.Accent else SokoulColors.TextPrimary, style = MaterialTheme.typography.titleMedium,
        )
    }
}

// --- Synchronisation initiale ------------------------------------------------------------

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val engine: SyncEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    fun status(id: Long): StateFlow<SyncStatus?> = engine.playlistStatus.map { it[id] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), engine.playlistStatus.value[id])
    fun epgStatus(id: Long): StateFlow<SyncStatus?> = engine.epgStatus.map { it[id] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), engine.epgStatus.value[id])
    fun retry(id: Long) = SyncWorker.syncNow(context, id, withEpg = true)
}

@Composable
fun SyncScreen(nav: NavHostController, playlistId: Long) {
    val vm: SyncViewModel = hiltViewModel()
    val status by remember(playlistId) { vm.status(playlistId) }.collectAsStateWithLifecycle()
    val epg by remember(playlistId) { vm.epgStatus(playlistId) }.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val done = status?.let { !it.running && it.error == null && it.finishedAt > 0 } == true

    Box(Modifier.fillMaxSize().background(SokoulColors.Background), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val s = status
            when {
                s?.error != null -> {
                    Icon(Icons.Default.ErrorOutline, null, Modifier.size(56.dp), tint = SokoulColors.Live)
                    Text("La synchronisation a échoué", Modifier.padding(top = 16.dp), style = com.sokoul.iptv.ui.theme.Type.Section)
                    Text(s.error, Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary, textAlign = TextAlign.Center)
                    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SokoulButton("Réessayer", { vm.retry(playlistId) }, icon = Icons.Default.Refresh, kind = ButtonKind.Primary, focusRequester = focus)
                        SokoulButton("Modifier", { nav.navigate(PlaylistEditRoute(playlistId)) })
                        SokoulButton("Continuer", { nav.navigate(HomeRoute) { popUpTo(nav.graph.id) { inclusive = true } } })
                    }
                }
                done -> {
                    SetupSteps(3)
                    Icon(Icons.Default.CheckCircle, null, Modifier.padding(top = 28.dp).size(56.dp), tint = SokoulColors.Success)
                    Text("Playlist prête", Modifier.padding(top = 16.dp), style = com.sokoul.iptv.ui.theme.Type.ScreenTitle)
                    Text("${s?.count ?: 0} éléments importés", Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary)
                    s?.warnings?.forEach { Text("⚠ $it", Modifier.padding(top = 6.dp), color = SokoulColors.Warning, textAlign = TextAlign.Center) }
                    val e = epg
                    Text(
                        when {
                            e == null || e.running -> "Le guide TV se télécharge en arrière-plan…"
                            e.error != null -> "Guide TV : ${e.error}"
                            else -> "Guide TV : ${e.count} programmes"
                        },
                        Modifier.padding(top = 10.dp), color = SokoulColors.TextMuted, textAlign = TextAlign.Center,
                    )
                    SokoulButton(
                        "C'est parti", { nav.navigate(HomeRoute) { popUpTo(nav.graph.id) { inclusive = true } } },
                        Modifier.padding(top = 24.dp), kind = ButtonKind.Primary, focusRequester = focus,
                    )
                }
                else -> {
                    SetupSteps(2)
                    com.sokoul.iptv.ui.components.SokoulSpinner(Modifier.padding(top = 28.dp))
                    Text("Synchronisation en cours", Modifier.padding(top = 20.dp), style = com.sokoul.iptv.ui.theme.Type.Section)
                    Text(s?.step ?: "En attente du réseau…", Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary)
                    if ((s?.count ?: 0) > 0) Text("${s?.count} éléments", color = SokoulColors.TextMuted)
                    Box(Modifier.padding(top = 20.dp).fillMaxWidth().height(4.dp).shimmer(Radius.Tiny))
                    Text(
                        "Les grosses playlists peuvent prendre une à deux minutes. Les mises à jour suivantes se font en arrière-plan.",
                        Modifier.padding(top = 16.dp), color = SokoulColors.TextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    LaunchedEffect(done, status?.error) { runCatching { focus.requestFocus() } }
}

/** Avancement de l'ajout d'un abonnement : 1 Abonnement, 2 Synchronisation, 3 C'est prêt. */
@Composable
internal fun SetupSteps(current: Int, modifier: Modifier = Modifier) {
    val steps = listOf("Abonnement", "Synchronisation", "C'est prêt")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, label ->
            val n = i + 1
            val done = n < current
            val active = n == current
            if (i > 0) Box(Modifier.padding(horizontal = 10.dp).size(28.dp, 1.dp).background(if (n <= current) SokoulColors.Accent else SokoulColors.Divider))
            Box(
                Modifier.size(22.dp).background(if (done || active) SokoulColors.Accent else SokoulColors.SurfaceHigher, Radius.Round),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (done) "✓" else "$n", style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = if (done || active) SokoulColors.OnVideo else SokoulColors.TextMuted)
            }
            Text(
                label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge,
                color = if (active) SokoulColors.TextPrimary else SokoulColors.TextMuted, maxLines = 1,
            )
        }
    }
}
