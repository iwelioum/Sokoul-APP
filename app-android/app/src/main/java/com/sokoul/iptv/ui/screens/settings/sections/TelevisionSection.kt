package com.sokoul.iptv.ui.screens.settings.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Update
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCtx
import com.sokoul.iptv.ui.screens.settings.offsetLabel
import com.sokoul.iptv.ui.screens.settings.subsection
import com.sokoul.iptv.ui.screens.settings.zapLabel

/** Télévision et guide : démarrage, zapping, décalage et mise à jour du guide. */
internal fun LazyListScope.televisionSection(c: SettingsCtx) {
    val s = c.s
    item("tv-start") {
        SettingRow("Au démarrage", if (s.resumeLastChannel) "Dernière chaîne regardée" else "Accueil", "Pour ceux qui allument la box pour regarder la télé", icon = Icons.Default.PowerSettingsNew) {
            c.choose("Au démarrage", listOf(false to "Accueil", true to "Dernière chaîne regardée").map { (v, label) ->
                MenuOption(label, selected = v == s.resumeLastChannel) { c.vm.update { it.copy(resumeLastChannel = v) } }
            })
        }
    }
    item("tv-zap") {
        SettingRow("Sens du zapping", zapLabel(s.zapInverted), icon = Icons.Default.SwapVert) {
            c.choose("Sens du zapping", listOf(false, true).map { v -> MenuOption(zapLabel(v), selected = v == s.zapInverted) { c.vm.update { it.copy(zapInverted = v) } } })
        }
    }
    subsection("Guide des programmes")
    item("tv-offset") {
        SettingRow("Décalage horaire du guide", offsetLabel(s.epgOffsetMinutes), "À ajuster si les programmes sont décalés", icon = Icons.Default.Schedule) {
            c.choose("Décalage du guide", (-12..12).map { h -> h * 60 }.map { m -> MenuOption(offsetLabel(m), selected = m == s.epgOffsetMinutes) { c.vm.update { it.copy(epgOffsetMinutes = m) } } })
        }
    }
    item("tv-epg") {
        SettingRow("Mise à jour du guide TV", "Toutes les ${s.epgRefreshHours} h", icon = Icons.Default.Update) {
            c.choose("Fréquence", listOf(6, 12, 24, 48).map { h -> MenuOption("Toutes les $h h", selected = h == s.epgRefreshHours) { c.vm.update { it.copy(epgRefreshHours = h) } } })
        }
    }
    item("tv-xmltv") {
        SettingRow("Guide TV xmltv.fr", if (s.epgXmltvFr) "Activé" else "Désactivé", "Chaînes françaises, belges et suisses en complément du guide du fournisseur (environ 8 Mo à chaque mise à jour)") {
            c.vm.update { it.copy(epgXmltvFr = !it.epgXmltvFr) }
        }
    }
}
