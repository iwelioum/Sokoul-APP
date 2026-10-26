package com.sokoul.iptv.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.db.CategoryWithPrefs
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.net.providers.ApiService
import com.sokoul.iptv.data.net.providers.ServiceState
import com.sokoul.iptv.data.net.providers.TestResult
import com.sokoul.iptv.data.repo.SyncStatus
import com.sokoul.iptv.ui.components.MenuOption

/**
 * Tout ce dont une rubrique a besoin : l'état courant et les gestes qui ouvrent les dialogues de l'écran
 * (les dialogues restent dans [SettingsScreen], une seule fois).
 */
internal class SettingsCtx(
    val vm: SettingsViewModel,
    val s: AppSettings,
    val nav: NavHostController,
    val context: Context,
    val playlists: List<PlaylistEntity>,
    val active: PlaylistEntity?,
    val status: (Long) -> SyncStatus?,
    val epgStatus: (Long) -> SyncStatus?,
    val flagged: List<CategoryWithPrefs>,
    val serviceState: (ApiService) -> ServiceState?,
    val testResult: (ApiService) -> TestResult?,
    val testing: ApiService?,
    val advanced: MutableState<Boolean>,
    val choose: (String, List<MenuOption>) -> Unit,
    val edit: (TextEdit) -> Unit,
    val playlistMenu: (PlaylistEntity) -> Unit,
    val pin: (PinStep) -> Unit,
    val sourceMenu: (ApiService) -> Unit,
    val exportMenu: () -> Unit,
    val restore: () -> Unit,
    val toast: (String) -> Unit,
    /** Real-Debrid associé par code appareil (jeton renouvelé seul). */
    val debridLinked: Boolean = false,
    val pairDebrid: () -> Unit = {},
)
