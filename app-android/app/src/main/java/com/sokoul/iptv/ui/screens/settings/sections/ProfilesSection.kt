package com.sokoul.iptv.ui.screens.settings.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import com.sokoul.iptv.data.db.ProfileEntity
import com.sokoul.iptv.data.repo.ProfileRepository
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCtx
import com.sokoul.iptv.ui.screens.settings.TextEdit
import com.sokoul.iptv.ui.screens.settings.note
import com.sokoul.iptv.ui.screens.settings.subsection

/** Profils : liste (OK = options), ajout d'un profil ou d'un profil Enfant, et règles du profil Enfant. */
internal fun LazyListScope.profilesSection(c: SettingsCtx) {
    items(c.profiles, key = { "pr-${it.id}" }) { p ->
        val current = p.id == c.currentProfileId
        SettingRow(
            p.name,
            listOfNotNull(if (current) "En cours" else null, if (p.kids) "Enfant" else null).joinToString(" · ").ifEmpty { null },
            icon = if (p.kids) Icons.Default.ChildCare else Icons.Default.Person,
        ) { c.choose(p.name, profileOptions(c, p, current)) }
    }
    if (c.profiles.size < ProfileRepository.MAX_PROFILES) {
        item("pr-add") { SettingRow("Ajouter un profil", null, icon = Icons.Default.Add) { askName(c, kids = false) } }
        item("pr-add-kids") {
            SettingRow("Ajouter un profil Enfant", null, "Seulement les groupes adaptés, sans réglages ni Real-Debrid", icon = Icons.Default.ChildCare) { askName(c, kids = true) }
        }
    }
    subsection("Profil Enfant")
    note(
        "pr-kids",
        "Groupes visibles : ceux dont le nom l'indique (enfants, jeunesse, animation, famille…), plus ceux que vous autorisez " +
            "avec un appui long sur le groupe. " +
            if (c.s.parentalPin.isBlank()) "Définissez un code parental (Contrôle parental) : sans lui, n'importe qui peut quitter le profil Enfant."
            else "Quitter un profil Enfant demande le code parental.",
    )
}

private fun askName(c: SettingsCtx, kids: Boolean) {
    c.edit(
        TextEdit(
            if (kids) "Nom du profil Enfant" else "Nom du profil", "",
            validate = { if (it.isBlank()) "Donnez un nom au profil" else null },
        ) { name -> c.vm.addProfile(name, kids); c.toast("Profil « ${name.trim()} » ajouté") },
    )
}

private fun profileOptions(c: SettingsCtx, p: ProfileEntity, current: Boolean): List<MenuOption> = buildList {
    if (!current) add(MenuOption("Utiliser ce profil", Icons.Default.Check) { c.vm.useProfile(p.id) })
    add(MenuOption("Renommer", Icons.Default.Edit) {
        c.edit(TextEdit("Nom du profil", p.name, validate = { if (it.isBlank()) "Donnez un nom au profil" else null }) { c.vm.updateProfile(p.copy(name = it)) })
    })
    add(MenuOption("Changer de couleur", Icons.Default.Palette) { c.vm.updateProfile(p.copy(color = (p.color + 1) % ProfileRepository.AVATAR_COLORS)) })
    if (p.id != ProfileEntity.MAIN) {
        add(MenuOption(if (p.kids) "Profil Enfant : oui" else "Profil Enfant : non", Icons.Default.ChildCare) { c.vm.updateProfile(p.copy(kids = !p.kids)) })
        add(MenuOption("Supprimer (liste et reprise comprises)", Icons.Default.Delete) { c.vm.deleteProfile(p.id); c.toast("Profil « ${p.name} » supprimé") })
    }
}
