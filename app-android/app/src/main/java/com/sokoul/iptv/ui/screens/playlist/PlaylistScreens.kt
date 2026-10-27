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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
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

    /** Une playlist existe (restaurée depuis une sauvegarde pendant le premier lancement). */
    val hasAny = repo.playlists.map { it.isNotEmpty() }

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
    // premier lancement : « Restaurer une sauvegarde » ouvre Réglages ; au retour, la playlist restaurée mène à l'accueil
    var restoring by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val restored by vm.hasAny.collectAsStateWithLifecycle(false)
    LaunchedEffect(restoring, restored) {
        if (firstRun && restoring && restored) nav.navigate(HomeRoute) { popUpTo(nav.graph.id) { inclusive = true } }
    }

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

    val l = com.sokoul.iptv.ui.theme.LocalLayout.current
    // deux colonnes en paysage (TV, tablette couchée) ; empilé en portrait, sinon le formulaire est écrasé
    val wide = l.isTv || (!l.isCompact && l.widthDp > l.heightDp)
    val welcome = firstRun || existing == null
    val intro: @Composable () -> Unit = {
        if (firstRun) {
            SetupSteps(1)
            Text("BIENVENUE", Modifier.padding(top = 28.dp), style = com.sokoul.iptv.ui.theme.Type.Kicker)
            Text("Sokoul TV", style = com.sokoul.iptv.ui.theme.Type.Display)
            Text(
                "La télé en direct, les films et les séries de votre abonnement, réunis dans une seule app.",
                Modifier.padding(top = 10.dp).widthIn(max = 520.dp), style = MaterialTheme.typography.titleMedium, color = SokoulColors.TextSecondary,
            )
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Feature(Icons.Default.LiveTv, "Guide TV, replay et pause du direct")
                Feature(Icons.Default.Movie, "Fiches complètes, sagas et reprise de lecture")
                Feature(Icons.Default.Bolt, "Real-Debrid en option pour les meilleures versions")
            }
        } else {
            if (existing == null) SetupSteps(1)
            Text(if (existing != null) "Modifier la playlist" else "Nouvelle playlist", Modifier.padding(top = 16.dp), style = com.sokoul.iptv.ui.theme.Type.ScreenTitle)
        }
    }
    val form: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (existing == null) {
                Text("TYPE D'ABONNEMENT", style = com.sokoul.iptv.ui.theme.Type.Overline)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.sokoul.iptv.ui.components.SokoulChip("Xtream Codes", { type = PlaylistType.XTREAM }, Modifier.focusRequester(firstFocus), icon = Icons.Default.Dns, selected = type == PlaylistType.XTREAM)
                    com.sokoul.iptv.ui.components.SokoulChip("Lien M3U / fichier", { type = PlaylistType.M3U }, icon = Icons.Default.Link, selected = type == PlaylistType.M3U)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = SokoulColors.Success)
                        Text("Lien Xtream détecté : le compte sera ajouté en mode Xtream (guide, replay et fiches complètes).", Modifier.padding(start = 8.dp), color = SokoulColors.Success, style = MaterialTheme.typography.bodyMedium)
                    }
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

            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    icon = if (p != null) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    kind = ButtonKind.Primary,
                    enabled = !busy,
                    focusRequester = if (p != null) firstFocus else null,
                )
                if (busy) com.sokoul.iptv.ui.components.SokoulSpinner(size = 28.dp)
                if (p != null) {
                    SokoulButton("Supprimer", { confirmDelete = true }, icon = Icons.Default.Delete)
                }
                if (!firstRun && p == null) SokoulButton("Annuler", { nav.popBackStack() })
                if (firstRun && p == null) SokoulButton("Restaurer", { restoring = true; nav.navigate(com.sokoul.iptv.ui.nav.SettingsRoute) }, icon = Icons.Default.Restore)
            }
            Text(
                "Vos identifiants restent sur cet appareil. Une seule connexion est utilisée à la fois.",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextMuted,
            )
        }
    }

    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        if (welcome) com.sokoul.iptv.ui.components.PosterMosaic()
        if (wide) {
            // TV / tablette : présentation à gauche sur le mur d'affiches, formulaire dans un panneau à droite
            Row(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Column(Modifier.weight(0.9f).fillMaxHeight().padding(start = l.margin, end = 32.dp), verticalArrangement = Arrangement.Center) { intro() }
                com.sokoul.iptv.ui.components.GlassPanel(Modifier.padding(top = 32.dp, bottom = 32.dp, end = l.margin).weight(1.1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) { form() }
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                intro()
                Spacer(Modifier.height(8.dp))
                form()
                Spacer(Modifier.height(40.dp))
            }
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
    // compteurs par section (chaînes, films, séries) : le plus grand nombre vu pour chacune
    var counters by remember { mutableStateOf(SyncCounters.State()) }
    LaunchedEffect(status) { counters = SyncCounters.update(counters, status?.step, status?.count ?: 0) }

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
                    Text("Synchronisation en cours", Modifier.padding(top = 28.dp), style = com.sokoul.iptv.ui.theme.Type.Section)
                    Text(s?.step?.ifBlank { null } ?: "En attente du réseau…", Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary)
                    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SyncCounters.sections(counters).forEach { (label, value, state) -> CounterTile(label, value, state) }
                    }
                    Box(Modifier.padding(top = 24.dp).fillMaxWidth().height(4.dp).shimmer(Radius.Tiny))
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
                if (done) Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = SokoulColors.OnVideo)
                else Text("$n", style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = if (active) SokoulColors.OnVideo else SokoulColors.TextMuted)
            }
            // libellé seulement pour l'étape en cours : la frise reste lisible même dans une colonne étroite
            if (active) Text(
                label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge,
                color = SokoulColors.TextPrimary, maxLines = 1,
            )
        }
    }
}

/** Atout de l'app sur l'écran de bienvenue : icône dans une pastille teintée et phrase courte. */
@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(SokoulColors.AccentSoft, Radius.Card), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = SokoulColors.Accent)
        }
        Text(text, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleMedium, color = SokoulColors.TextPrimary)
    }
}

/** Compteurs de l'écran de synchronisation, déduits de l'étape en cours (« Chaînes… », « Films… »…). */
object SyncCounters {
    enum class Phase { WAITING, RUNNING, DONE }
    data class State(val counts: Map<String, Int> = emptyMap(), val current: String? = null)

    private val XTREAM = listOf("Chaînes", "Films", "Séries")
    private const val M3U = "Éléments"

    fun keyOf(step: String?): String? = when {
        step == null -> null
        step.startsWith("Lecture de la playlist") -> M3U
        else -> XTREAM.firstOrNull { step.startsWith(it) }
    }

    fun update(s: State, step: String?, count: Int): State {
        val k = keyOf(step) ?: return s.copy(current = null)
        return State(s.counts + (k to maxOf(count, s.counts[k] ?: 0)), k)
    }

    /** (libellé, valeur, état) à afficher : les trois sections Xtream, ou un seul compteur pour une M3U. */
    fun sections(s: State): List<Triple<String, Int, Phase>> {
        if (M3U in s.counts) return listOf(Triple(M3U, s.counts.getValue(M3U), Phase.RUNNING))
        val at = XTREAM.indexOf(s.current)
        return XTREAM.mapIndexed { i, k ->
            val phase = when {
                k == s.current -> Phase.RUNNING
                k in s.counts && (at < 0 || i < at) -> Phase.DONE
                else -> Phase.WAITING
            }
            Triple(k, s.counts[k] ?: 0, phase)
        }
    }
}

/** Tuile de compteur : le nombre défile jusqu'à sa valeur ; coche une fois la section terminée. */
@Composable
private fun CounterTile(label: String, value: Int, phase: SyncCounters.Phase) {
    val shown by androidx.compose.animation.core.animateIntAsState(value, androidx.compose.animation.core.tween(600), label = "count")
    val active = phase == SyncCounters.Phase.RUNNING
    Column(
        Modifier.width(150.dp).background(if (active) SokoulColors.AccentTint else SokoulColors.SurfaceHigh, Radius.Card).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (phase == SyncCounters.Phase.DONE) Icon(Icons.Default.Check, null, Modifier.padding(end = 4.dp).size(16.dp), tint = SokoulColors.Success)
            Text(label.uppercase(), style = com.sokoul.iptv.ui.theme.Type.Overline, color = if (active) SokoulColors.Accent else SokoulColors.TextMuted)
        }
        Text(
            if (phase == SyncCounters.Phase.WAITING && value == 0) "—" else "%,d".format(java.util.Locale.FRANCE, shown),
            Modifier.padding(top = 6.dp), style = com.sokoul.iptv.ui.theme.Type.Section,
            color = if (phase == SyncCounters.Phase.WAITING) SokoulColors.TextMuted else SokoulColors.TextPrimary,
        )
    }
}
