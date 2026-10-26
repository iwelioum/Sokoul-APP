package com.sokoul.iptv.ui.screens.settings.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.Modifier
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.nav.PlaylistEditRoute
import com.sokoul.iptv.ui.screens.settings.PlaylistRow
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCtx
import com.sokoul.iptv.ui.screens.settings.subsection

/** Compte et playlists : abonnements, ajout, fréquence de mise à jour. */
internal fun LazyListScope.accountSection(c: SettingsCtx) {
    items(c.playlists, key = { "pl-${it.id}" }) { p ->
        PlaylistRow(p, p.id == c.active?.id, c.status(p.id), c.epgStatus(p.id), Modifier) { c.playlistMenu(p) }
    }
    item("pl-add") { SettingRow("Ajouter une playlist", "Xtream Codes, lien M3U ou fichier local", icon = Icons.Default.Add) { c.nav.navigate(PlaylistEditRoute()) } }
    subsection("Mises à jour")
    item("pl-refresh") {
        SettingRow("Mise à jour des playlists", "Toutes les ${c.s.playlistRefreshHours} h", "Chaînes, films et séries de l'abonnement", icon = Icons.Default.Sync) {
            c.choose("Fréquence", listOf(6, 12, 24, 48, 168).map { h -> MenuOption("Toutes les $h h", selected = h == c.s.playlistRefreshHours) { c.vm.update { it.copy(playlistRefreshHours = h) } } })
        }
    }
}
