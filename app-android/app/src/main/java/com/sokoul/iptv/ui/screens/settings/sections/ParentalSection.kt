package com.sokoul.iptv.ui.screens.settings.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import com.sokoul.iptv.ui.screens.settings.PinStep
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCtx
import com.sokoul.iptv.ui.screens.settings.note
import com.sokoul.iptv.ui.screens.settings.subsection
import com.sokoul.iptv.ui.screens.settings.typeLabel

/** Contrôle parental : code, reverrouillage, puis groupes masqués et verrouillés (OK = rétablir). */
internal fun LazyListScope.parentalSection(c: SettingsCtx) {
    val s = c.s
    item("pa-pin") {
        SettingRow(if (s.parentalPin.isBlank()) "Définir un code parental" else "Modifier le code parental", if (s.parentalPin.isBlank()) "Aucun" else "Actif", "Code à 4 chiffres pour les groupes verrouillés", icon = Icons.Default.Lock) {
            c.pin(if (s.parentalPin.isBlank()) PinStep.NEW else PinStep.CURRENT)
        }
    }
    if (s.parentalPin.isNotBlank()) {
        item("pa-lock") { SettingRow("Reverrouiller maintenant", null, "Masque à nouveau les groupes verrouillés", icon = Icons.Default.LockOpen) { c.vm.lockNow(); c.toast("Groupes verrouillés") } }
        item("pa-remove") { SettingRow("Supprimer le code", null, icon = Icons.Default.Delete) { c.pin(PinStep.REMOVE) } }
    }
    subsection("Groupes masqués et verrouillés")
    if (c.flagged.isEmpty()) note("flagged", "Aucun. Appui long (ou touche Menu) sur un groupe pour le masquer ou le verrouiller.")
    else note("flagged", "OK sur un groupe pour le rétablir.")
    items(c.flagged, key = { "pa-${it.type}|${it.categoryId}" }) { g ->
        SettingRow(
            g.name,
            listOfNotNull(typeLabel(g.type), if (g.hidden) "masqué" else null, if (g.locked) "verrouillé" else null).joinToString(" · "),
            icon = if (g.locked) Icons.Default.Lock else Icons.Default.Visibility,
        ) {
            c.vm.unflag(g) { needsPin -> if (needsPin) c.pin(PinStep.UNFLAG(g)) else c.toast("« ${g.name} » rétabli") }
        }
    }
}
