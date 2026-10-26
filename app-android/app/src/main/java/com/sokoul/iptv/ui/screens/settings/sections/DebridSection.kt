package com.sokoul.iptv.ui.screens.settings.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.runtime.LaunchedEffect
import com.sokoul.iptv.data.net.providers.ApiKeys
import com.sokoul.iptv.data.net.providers.ApiService
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCtx
import com.sokoul.iptv.ui.screens.settings.TextEdit
import com.sokoul.iptv.ui.screens.settings.note
import com.sokoul.iptv.ui.screens.settings.sourceStatus

/** Libellé de la taille maximale d'un fichier Real-Debrid (0 = automatique). */
internal fun debridSizeLabel(gb: Int) = when (gb) {
    0 -> "Automatique (25 Go en Wi-Fi, sans limite en câble)"
    200 -> "Sans limite"
    else -> "$gb Go"
}

/**
 * Real-Debrid : compte (testé à l'ouverture, un appel), association par code sans clavier,
 * clé API, extension de recherche de sources et taille maximale des fichiers.
 */
internal fun LazyListScope.debridSection(c: SettingsCtx) {
    val svc = ApiService.REALDEBRID
    val s = c.s
    note("rd-intro", "Real-Debrid lit les films et séries depuis ses serveurs : la connexion IPTV reste libre (enregistrements, direct) et la 4K HDR devient possible.")
    item("rd-account") {
        LaunchedEffect(Unit) { if (c.testResult(svc) == null && c.testing == null) c.vm.test(svc) }
        val key = ApiKeys.resolve(svc, s.apiKeys)
        SettingRow(
            "Compte Real-Debrid",
            sourceStatus(svc, key.ifEmpty { if (c.debridLinked) "code" else "" }, c.serviceState(svc), c.testResult(svc), c.testing == svc),
            "OK pour tester à nouveau",
            icon = Icons.Default.AccountCircle,
        ) { c.vm.test(svc) }
    }
    item("rd-pair") {
        SettingRow(
            if (c.debridLinked) "Box associée par code" else "Associer la box par code",
            if (c.debridLinked) "Dissocier" else null,
            "Sans clavier : un code à saisir sur real-debrid.com/device depuis le téléphone",
            icon = Icons.Default.Link,
        ) { if (c.debridLinked) c.vm.unlinkDebrid() else c.pairDebrid() }
    }
    item("rd-key") {
        val key = ApiKeys.resolve(svc, s.apiKeys)
        SettingRow(
            "Clé API",
            if (key.isEmpty()) "Aucune" else "${ApiKeys.origin(svc, s.apiKeys)} ${ApiKeys.masked(key)}",
            "Ou collez la clé de real-debrid.com/apitoken",
            icon = Icons.Default.Key,
        ) { c.sourceMenu(svc) }
    }
    item("rd-addon") {
        SettingRow(
            "Extension de recherche de sources",
            s.debridAddon.removePrefix("https://").substringBefore('/').ifEmpty { "Aucune" },
            "Adresse d'une extension au format Stremio (…/manifest.json) : elle liste les versions d'un film ou d'un épisode",
            icon = Icons.Default.Extension,
        ) {
            c.edit(TextEdit(
                "Adresse de l'extension (https://…/manifest.json)", s.debridAddon,
                validate = { v -> if (v.isBlank() || com.sokoul.iptv.data.AppSettings.cleanAddon(v).isNotEmpty()) null else "Adresse https:// attendue" },
            ) { v -> c.vm.update { it.copy(debridAddon = v) } })
        }
    }
    item("rd-size") {
        SettingRow("Taille maximale d'un fichier", debridSizeLabel(s.debridMaxGb), "Les gros fichiers (Remux 4K) demandent un débit élevé", icon = Icons.Default.SdStorage) {
            c.choose("Taille maximale", listOf(0, 10, 25, 50, 200).map { gb ->
                MenuOption(debridSizeLabel(gb), selected = gb == s.debridMaxGb) { c.vm.update { it.copy(debridMaxGb = gb) } }
            })
        }
    }
}
