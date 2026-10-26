package com.sokoul.iptv.ui.screens.settings

import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.components.ScreenHeader
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.theme.Radius
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
import androidx.compose.runtime.saveable.rememberSaveable
import com.sokoul.iptv.ui.screens.settings.sections.aboutSection
import com.sokoul.iptv.ui.screens.settings.sections.accountSection
import com.sokoul.iptv.ui.screens.settings.sections.backupSection
import com.sokoul.iptv.ui.screens.settings.sections.debridSection
import com.sokoul.iptv.ui.screens.settings.sections.librarySection
import com.sokoul.iptv.ui.screens.settings.sections.parentalSection
import com.sokoul.iptv.ui.screens.settings.sections.playbackSection
import com.sokoul.iptv.ui.screens.settings.sections.sourcesSection
import com.sokoul.iptv.ui.screens.settings.sections.televisionSection
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
import com.sokoul.iptv.data.repo.BackupPassphraseException
import com.sokoul.iptv.ui.ParentalGate
import com.sokoul.iptv.ui.PinCheck
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.components.ButtonKind
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
import com.sokoul.iptv.ui.util.hasDocumentPicker
import com.sokoul.iptv.work.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sokoul.iptv.data.net.providers.ApiKeys
import com.sokoul.iptv.data.net.providers.ApiService
import com.sokoul.iptv.data.net.providers.ServiceState

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
    private val http: okhttp3.OkHttpClient,
    @ApplicationContext private val context: Context,
    private val vod: com.sokoul.iptv.data.repo.VodRepository,
    private val health: com.sokoul.iptv.data.net.providers.ProviderHealth,
    private val tester: com.sokoul.iptv.data.net.providers.DataSourceTester,
    private val indexer: com.sokoul.iptv.data.repo.CatalogIndexer,
    private val rd: com.sokoul.iptv.data.debrid.RealDebridClient,
) : ViewModel() {
    // --- Real-Debrid : association par code ------------------------------------------------
    data class Pairing(val code: String?, val message: String)
    val pairing = MutableStateFlow<Pairing?>(null)
    val debridLinked = MutableStateFlow(false)
    private var pairJob: kotlinx.coroutines.Job? = null

    init { viewModelScope.launch { debridLinked.value = rd.linkedByCode() } }

    /** Affiche un code à saisir sur real-debrid.com/device puis attend la validation (10 min au plus). */
    fun startPairing() {
        pairJob?.cancel()
        pairing.value = Pairing(null, "Demande d'un code…")
        pairJob = viewModelScope.launch {
            try {
                val code = rd.deviceCode()
                pairing.value = Pairing(code.userCode, "Sur votre téléphone, ouvrez ${code.verificationUrl.removePrefix("https://")} et saisissez ce code. Cette fenêtre se fermera seule.")
                while (System.currentTimeMillis() < code.expiresAtMs) {
                    kotlinx.coroutines.delay(code.intervalS.coerceAtLeast(5) * 1000L)
                    if (rd.pollDevice(code)) {
                        pairing.value = null
                        debridLinked.value = true
                        testResults.value = testResults.value - ApiService.REALDEBRID
                        test(ApiService.REALDEBRID)
                        return@launch
                    }
                }
                pairing.value = Pairing(null, "Code expiré : recommencez.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                pairing.value = Pairing(null, e.message ?: "Association impossible")
            }
        }
    }

    fun cancelPairing() { pairJob?.cancel(); pairing.value = null }

    fun unlinkDebrid() = viewModelScope.launch {
        rd.unlink(); debridLinked.value = false
        testResults.value = testResults.value - ApiService.REALDEBRID
    }

    /** Part du catalogue dont la fiche est complète (Explorer, ambiances). */
    val coverage = kotlinx.coroutines.flow.MutableStateFlow<com.sokoul.iptv.data.repo.IndexCoverage?>(null)

    fun refreshCoverage() = viewModelScope.launch {
        val p = playlists.activeNow() ?: return@launch
        coverage.value = runCatching { indexer.coverage(p.id) }.getOrNull()
    }

    fun setTmdbKey(v: String) = viewModelScope.launch {
        store.update { it.copy(tmdbApiKey = v.trim()) }
        if (v.isNotBlank()) com.sokoul.iptv.work.CatalogIndexWorker.start(context)
    }
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
    /** Rétablit un groupe ; un groupe verrouillé ne se déverrouille qu'avec le code (sinon `needsPin`). */
    fun unflag(g: CategoryWithPrefs, done: (needsPin: Boolean) -> Unit) = viewModelScope.launch {
        if (g.locked && !parental.canAccessLocked()) { done(true); return@launch }
        catalog.setCategoryPref(g.playlistId, g.type, g.categoryId, hidden = false, locked = false)
        done(false)
    }
    // --- sources de données -----------------------------------------------------------------
    val serviceStates = health.states
    val testResults = MutableStateFlow<Map<ApiService, com.sokoul.iptv.data.net.providers.TestResult>>(emptyMap())
    val testing = MutableStateFlow<ApiService?>(null)

    /** Un seul test à la fois (un appel au service). */
    fun test(s: ApiService) {
        if (testing.value != null) return
        testing.value = s
        viewModelScope.launch {
            try { testResults.value = testResults.value + (s to tester.test(s)) } finally { testing.value = null }
        }
    }

    /** Nouvelle clé (vide = revenir à celle du fichier local) : disjoncteur levé, ancien résultat oublié. */
    fun setKey(s: ApiService, value: String) = viewModelScope.launch {
        store.update { it.copy(apiKeys = it.apiKeys + (s.id to AppSettings.cleanKey(value))) }
        health.reset(s)
        testResults.value = testResults.value - s
    }

    fun lockNow() = parental.lock()
    fun stats(done: (com.sokoul.iptv.data.repo.CatalogStats?) -> Unit) = viewModelScope.launch {
        done(active.value?.let { p -> runCatching { vod.stats(p.id) }.getOrNull() })
    }
    fun checkPin(pin: String, done: (PinCheck) -> Unit) = viewModelScope.launch { done(parental.tryUnlock(pin)) }
    fun setPin(pin: String) = viewModelScope.launch { parental.setPin(pin) }
    fun removePin() = viewModelScope.launch { parental.removePin() }

    fun export(uri: Uri, passphrase: String?, done: (String) -> Unit) = viewModelScope.launch {
        done(runCatching { backup.export(uri, passphrase) }.fold({ r ->
            buildString {
                append(if (passphrase != null) "Sauvegarde chiffrée exportée" else "Sauvegarde exportée (sans mots de passe)")
                if (r.skippedWithCredentials > 0) append(" — ${r.skippedWithCredentials} playlist(s) M3U avec identifiants non incluse(s) : utilisez l'export chiffré")
            }
        }, { it.userMessage() }))
    }

    /** Sans sélecteur de fichiers (TV) : export dans le dossier de l'app. */
    fun exportToFolder(passphrase: String?, done: (String) -> Unit) = viewModelScope.launch {
        done(runCatching { backup.exportToFolder(passphrase) }.fold({ (f, r) ->
            "Sauvegarde enregistrée : ${f.absolutePath}" +
                (if (r.skippedWithCredentials > 0) " — ${r.skippedWithCredentials} playlist(s) M3U avec identifiants non incluse(s)" else "")
        }, { it.userMessage() }))
    }

    fun localBackups(): List<java.io.File> = backup.localBackups()
    fun backupFolder(): String = backup.backupDir().absolutePath

    fun import(uri: Uri, passphrase: String?, needPassphrase: (String?) -> Unit, done: (String) -> Unit) =
        runImport(passphrase, needPassphrase, done) { backup.import(uri, it) }

    fun importFile(file: java.io.File, passphrase: String?, needPassphrase: (String?) -> Unit, done: (String) -> Unit) =
        runImport(passphrase, needPassphrase, done) { backup.importFile(file, it) }

    fun importUrl(url: String, passphrase: String?, needPassphrase: (String?) -> Unit, done: (String) -> Unit) =
        runImport(passphrase, needPassphrase, done) { backup.importUrl(url, it, http) }

    /** [needPassphrase] est appelé quand le fichier est chiffré et qu'aucune phrase (ou une mauvaise) n'a été fournie. */
    private fun runImport(
        passphrase: String?, needPassphrase: (String?) -> Unit, done: (String) -> Unit,
        block: suspend (String?) -> com.sokoul.iptv.data.repo.ImportResult,
    ) = viewModelScope.launch {
        try {
            val r = block(passphrase)
            r.toSync.forEach { SyncWorker.syncNow(context, it) }
            done(buildString {
                append("${r.added.size} playlist(s) restaurée(s)")
                if (r.skipped > 0) append(", ${r.skipped} ignorée(s)")
                if (r.withoutPassword > 0) append(" — ${r.withoutPassword} sans mot de passe : à saisir via « Modifier »")
            })
        } catch (e: BackupPassphraseException) {
            needPassphrase(if (passphrase != null) "Phrase de passe incorrecte" else null)
        } catch (e: Exception) {
            done(e.userMessage())
        }
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
    val serviceStates by vm.serviceStates.collectAsStateWithLifecycle()
    val testResults by vm.testResults.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    var sourceMenu by remember { mutableStateOf<ApiService?>(null) }
    val debridLinked by vm.debridLinked.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var choice by remember { mutableStateOf<Pair<String, List<MenuOption>>?>(null) }
    var textEdit by remember { mutableStateOf<TextEdit?>(null) }
    var playlistMenu by remember { mutableStateOf<PlaylistEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var pinStep by remember { mutableStateOf<PinStep?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val advanced = rememberSaveable { mutableStateOf(false) }

    var exportPass by remember { mutableStateOf<String?>(null) }
    var exportMenu by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri, exportPass, toast)
        exportPass = null
    }
    // Lance un import ; redemande la phrase de passe si la sauvegarde est chiffrée.
    fun runImport(start: (String?, (String?) -> Unit) -> Unit) {
        lateinit var attempt: (String?) -> Unit
        attempt = { pass ->
            start(pass) { err -> textEdit = TextEdit("Phrase de passe de la sauvegarde", "", password = true, message = err) { p -> attempt(p) } }
        }
        attempt(null)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport { pass, need -> vm.import(uri, pass, need, toast) }
    }
    // Android TV : pas de sélecteur de fichiers → dossier de l'app ou adresse web
    val hasPicker = remember { context.hasDocumentPicker() }
    var restoreMenu by remember { mutableStateOf(false) }
    val doExport: (String?) -> Unit = { pass ->
        if (hasPicker) { exportPass = pass; exportLauncher.launch(if (pass != null) "sokoul-sauvegarde-chiffree.json" else "sokoul-sauvegarde.json") }
        else vm.exportToFolder(pass, toast)
    }

    val ctx = SettingsCtx(
        vm = vm, s = s, nav = nav, context = context, playlists = playlists, active = active,
        status = { status[it] }, epgStatus = { epgStatus[it] }, flagged = flagged,
        serviceState = { serviceStates[it] }, testResult = { testResults[it] }, testing = testing,
        advanced = advanced,
        choose = { title, options -> choice = title to options },
        edit = { textEdit = it },
        playlistMenu = { playlistMenu = it },
        pin = { pinStep = it },
        sourceMenu = { sourceMenu = it },
        exportMenu = { exportMenu = true },
        restore = { if (hasPicker) importLauncher.launch(arrayOf("application/json", "*/*")) else restoreMenu = true },
        toast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        debridLinked = debridLinked,
        pairDebrid = { vm.startPairing() },
    )

    SettingsPanes { cat, modifier ->
        // une liste par rubrique : chacune repart en haut
        androidx.compose.runtime.key(cat) {
            LazyColumn(
                modifier,
                contentPadding = PaddingValues(start = if (LocalLayout.current.isTouch) LocalLayout.current.margin else 0.dp, end = if (LocalLayout.current.isTouch) LocalLayout.current.margin else 0.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (cat) {
                    SettingsCategory.ACCOUNT -> accountSection(ctx)
                    SettingsCategory.PLAYBACK -> playbackSection(ctx)
                    SettingsCategory.TV -> televisionSection(ctx)
                    SettingsCategory.LIBRARY -> librarySection(ctx)
                    SettingsCategory.DEBRID -> debridSection(ctx)
                    SettingsCategory.PARENTAL -> parentalSection(ctx)
                    SettingsCategory.SOURCES -> sourcesSection(ctx)
                    SettingsCategory.BACKUP -> backupSection(ctx)
                    SettingsCategory.ABOUT -> aboutSection(ctx)
                }
            }
        }
    }

    choice?.let { (title, options) -> OptionsDialog(title, options, onDismiss = { choice = null }) }
    pairing?.let { p ->
        OptionsDialog(
            p.code?.let { "Code : $it" } ?: "Association Real-Debrid",
            listOf(MenuOption("Annuler", Icons.Default.Delete) { vm.cancelPairing() }),
            subtitle = p.message,
            onDismiss = { vm.cancelPairing() },
        )
    }
    sourceMenu?.let { svc ->
        val saved = !s.apiKeys[svc.id].isNullOrBlank()
        OptionsDialog(
            svc.label,
            buildList {
                add(MenuOption(if (testing == svc) "Test en cours…" else "Tester (un appel)", Icons.Default.CheckCircle) { vm.test(svc) })
                if (svc.needsKey) add(MenuOption(if (saved) "Remplacer la clé…" else "Saisir une clé…", Icons.Default.Lock) {
                    textEdit = TextEdit(
                        "Clé ${svc.label}", "", password = true,
                        message = ApiKeys.resolve(svc, s.apiKeys).takeIf { it.isNotEmpty() }?.let { "Clé actuelle : ${ApiKeys.masked(it)}" },
                        validate = { if (AppSettings.cleanKey(it).length < 8) "Clé trop courte" else null },
                    ) { v -> vm.setKey(svc, v) }
                })
                if (saved) add(MenuOption(if (svc.buildKey.isNotEmpty()) "Revenir à la clé du fichier local" else "Effacer la clé", Icons.Default.Delete) { vm.setKey(svc, "") })
            },
            subtitle = svc.purpose + (svc.signupUrl?.let { " — clé gratuite : ${it.removePrefix("https://")}" } ?: " — sans clé"),
            onDismiss = { sourceMenu = null },
        )
    }
    if (exportMenu) {
        OptionsDialog(
            "Exporter la configuration",
            listOf(
                MenuOption("Sans mots de passe") { doExport(null) },
                MenuOption("Avec les identifiants (chiffrée)", Icons.Default.Lock) {
                    textEdit = TextEdit(
                        "Choisissez une phrase de passe (8 caractères minimum)", "", password = true,
                        validate = { if (it.length < 8) "8 caractères minimum" else null },
                    ) { p -> doExport(p) }
                },
            ),
            subtitle = "Sans mots de passe, il faudra les ressaisir après la restauration. La version chiffrée demande la phrase de passe pour être restaurée.",
            onDismiss = { exportMenu = false },
        )
    }
    if (restoreMenu) {
        val files = remember { vm.localBackups() }
        OptionsDialog(
            "Restaurer une sauvegarde",
            files.take(20).map { f ->
                MenuOption(f.name) { runImport { pass, need -> vm.importFile(f, pass, need, toast) } }
            } + MenuOption("Depuis une adresse web…") {
                textEdit = TextEdit("Adresse de la sauvegarde (http/https)", "http://",
                    validate = { if (com.sokoul.iptv.data.repo.BackupRules.isHttpUrl(it)) null else "Adresse invalide" }) { url ->
                    runImport { pass, need -> vm.importUrl(url, pass, need, toast) }
                }
            },
            subtitle = if (files.isEmpty()) "Aucune sauvegarde dans ${vm.backupFolder()}" else "Sauvegardes de ${vm.backupFolder()}",
            onDismiss = { restoreMenu = false },
        )
    }
    textEdit?.let { t -> androidx.compose.runtime.key(t) { TextEditDialog(t) { if (textEdit === t) textEdit = null } } }
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
    // key(step) : chaque étape repart d'une saisie vide (sinon « Confirmez » affichait déjà 4 chiffres)
    pinStep?.let { step -> androidx.compose.runtime.key(step) {
        PinDialog(
            when (step) {
                PinStep.CURRENT, PinStep.REMOVE -> "Code actuel"; PinStep.NEW -> "Nouveau code"
                is PinStep.CONFIRM -> "Confirmez le code"; is PinStep.UNFLAG -> "Code parental"
            },
            onResult = { pin ->
                when (step) {
                    is PinStep.UNFLAG -> vm.checkPin(pin) { r ->
                        if (r.ok) {
                            pinStep = null; pinError = null
                            vm.unflag(step.group) { Toast.makeText(context, "« ${step.group.name} » rétabli", Toast.LENGTH_SHORT).show() }
                        } else pinError = r.message
                    }
                    PinStep.CURRENT -> vm.checkPin(pin) { r -> if (r.ok) { pinStep = PinStep.NEW; pinError = null } else pinError = r.message }
                    PinStep.REMOVE -> vm.checkPin(pin) { r ->
                        if (r.ok) { vm.removePin(); pinStep = null; pinError = null; Toast.makeText(context, "Code parental supprimé", Toast.LENGTH_SHORT).show() }
                        else pinError = r.message
                    }
                    PinStep.NEW -> { pinStep = PinStep.CONFIRM(pin); pinError = null }
                    is PinStep.CONFIRM -> if (pin == step.first) {
                        vm.setPin(pin); pinStep = null; pinError = null
                        Toast.makeText(context, "Code parental enregistré", Toast.LENGTH_SHORT).show()
                    } else { pinStep = PinStep.NEW; pinError = "Les codes ne correspondent pas" }
                }
            },
            onDismiss = { pinStep = null; pinError = null },
            error = pinError,
        )
    } }
}

internal sealed interface PinStep {
    data object CURRENT : PinStep
    data object NEW : PinStep
    data object REMOVE : PinStep
    data class CONFIRM(val first: String) : PinStep
    data class UNFLAG(val group: CategoryWithPrefs) : PinStep
}

internal data class TextEdit(
    val title: String,
    val value: String,
    val password: Boolean = false,
    val message: String? = null,
    /** Renvoie un message d'erreur pour refuser la valeur. */
    val validate: (String) -> String? = { null },
    val onSave: (String) -> Unit,
)

@Composable
private fun TextEditDialog(t: TextEdit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(t.value) }
    var error by remember { mutableStateOf(t.message) }
    val focus = remember { FocusRequester() }
    val save = {
        val e = t.validate(value)
        if (e != null) error = e else { t.onSave(value); onDismiss() }
    }
    com.sokoul.iptv.ui.components.SokoulDialog(onDismiss) {
        run {
            com.sokoul.iptv.ui.components.DialogHeader(t.title)
            FormField("Valeur", value, { value = it; error = null }, Modifier.padding(top = 14.dp).focusRequester(focus), password = t.password, autoEdit = true, onDone = save)
            error?.let { Text(it, Modifier.padding(top = 8.dp), color = SokoulColors.Live, style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SokoulButton("Annuler", onDismiss)
                SokoulButton("Valider", save, kind = ButtonKind.Primary)
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

internal val languages = listOf(
    "fr" to "Français", "en" to "Anglais", "ar" to "Arabe", "es" to "Espagnol", "de" to "Allemand",
    "it" to "Italien", "pt" to "Portugais", "tr" to "Turc", "nl" to "Néerlandais",
)

internal fun languageName(code: String) = languages.firstOrNull { it.first == code }?.second ?: code.ifBlank { "Automatique" }

internal fun offsetLabel(min: Int) = when {
    min == 0 -> "Aucun"
    min > 0 -> "+${min / 60} h"
    else -> "−${-min / 60} h"
}

internal fun typeLabel(t: ContentType) = when (t) {
    ContentType.LIVE -> "Direct"
    ContentType.MOVIE -> "Films"
    ContentType.SERIES -> "Séries"
}

/** État affiché d'un service : résultat du dernier test, sinon état du disjoncteur, sinon présence de la clé. */
internal fun sourceStatus(
    svc: ApiService, key: String, state: ServiceState?, result: com.sokoul.iptv.data.net.providers.TestResult?, testing: Boolean,
): String = when {
    testing -> "Test en cours…"
    result != null -> (if (result.ok) "✓ " else "✕ ") + result.message
    svc.needsKey && key.isEmpty() -> "Clé manquante"
    state is ServiceState.Blocked -> if (state.code == 429) "Quota atteint (pause 1 h)" else "Clé refusée (pause 24 h)"
    state is ServiceState.Failing -> state.message
    state == ServiceState.Ok -> "Actif"
    svc == ApiService.XMLTVFR -> "Facultatif"
    else -> "Prêt"
}

/** ▲ = chaîne précédente dans la liste (numéro plus petit), comme sur la plupart des box. */
internal fun zapLabel(inverted: Boolean) = if (inverted) "▲ chaîne suivante · ▼ précédente" else "▲ chaîne précédente · ▼ suivante"
