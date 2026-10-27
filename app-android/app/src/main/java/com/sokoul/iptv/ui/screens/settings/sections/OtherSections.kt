package com.sokoul.iptv.ui.screens.settings.sections

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import com.sokoul.iptv.BuildConfig
import com.sokoul.iptv.data.net.providers.ApiKeys
import com.sokoul.iptv.data.net.providers.ApiService
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCtx
import com.sokoul.iptv.ui.screens.settings.note
import com.sokoul.iptv.ui.screens.settings.sourceStatus

/** Sources de données : un service par ligne (OK = tester, saisir ou effacer la clé). */
internal fun LazyListScope.sourcesSection(c: SettingsCtx) {
    note("sources", "Sans clé, un service est simplement ignoré. " + (if (c.isTv) "OK sur" else "Touchez") + " un service pour le tester ou saisir sa clé.")
    items(ApiService.entries.filter { it != ApiService.WATCHMODE && it != ApiService.REALDEBRID }, key = { "src-" + it.id }) { svc ->
        val key = ApiKeys.resolve(svc, c.s.apiKeys)
        SettingRow(
            svc.label,
            sourceStatus(svc, key, c.serviceState(svc), c.testResult(svc), c.testing == svc),
            svc.purpose + (if (svc.needsKey && key.isNotEmpty()) " · ${ApiKeys.origin(svc, c.s.apiKeys)} ${ApiKeys.masked(key)}" else ""),
        ) { c.sourceMenu(svc) }
    }
}

/** Sauvegarde : export (avec ou sans identifiants chiffrés) et restauration. */
internal fun LazyListScope.backupSection(c: SettingsCtx) {
    item("bk-export") { SettingRow("Exporter la configuration", null, "Playlists, favoris, groupes et réglages", icon = Icons.Default.Upload) { c.exportMenu() } }
    item("bk-restore") { SettingRow("Restaurer une sauvegarde", null, "Depuis un fichier, le dossier de l'app ou une adresse web", icon = Icons.Default.Download) { c.restore() } }
}

/** À propos. */
internal fun LazyListScope.aboutSection(@Suppress("UNUSED_PARAMETER") c: SettingsCtx) {
    item("ab-version") { SettingRow("Sokoul TV ${BuildConfig.VERSION_NAME}", null, "Lecteur IPTV pour Android TV, Fire TV et mobiles", icon = Icons.Default.CheckCircle) {} }
    item("ab-watchnext") {
        val st by com.sokoul.iptv.tv.WatchNextStatus.text.collectAsStateWithLifecycle()
        SettingRow("Rangée « À suivre » de l'accueil TV", st ?: "En attente", "Google TV ne l'affiche que pour certaines applis ; Android TV l'affiche toujours", icon = Icons.Default.CheckCircle) {}
    }
    note("fonts", "Police Manrope (licence SIL Open Font). Données de films et séries : TMDB.")
}
