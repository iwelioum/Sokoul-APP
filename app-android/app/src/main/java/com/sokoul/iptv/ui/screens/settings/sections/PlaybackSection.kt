package com.sokoul.iptv.ui.screens.settings.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.BufferProfile
import com.sokoul.iptv.data.DecoderMode
import com.sokoul.iptv.data.LiveFormat
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.screens.settings.AdvancedRow
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCtx
import com.sokoul.iptv.ui.screens.settings.TextEdit
import com.sokoul.iptv.ui.screens.settings.languageName
import com.sokoul.iptv.ui.screens.settings.languages

/** Lecture : langues et épisode suivant ; format, mémoire tampon, décodage et User-Agent sous « Avancé ». */
internal fun LazyListScope.playbackSection(c: SettingsCtx) {
    val s = c.s
    item("pb-audio") {
        SettingRow("Langue audio préférée", languageName(s.audioLanguage), icon = Icons.Default.RecordVoiceOver) {
            c.choose("Langue audio", languages.map { (code, name) -> MenuOption(name, selected = code == s.audioLanguage) { c.vm.update { it.copy(audioLanguage = code) } } })
        }
    }
    item("pb-subs") {
        SettingRow("Sous-titres par défaut", if (s.subtitleLanguage.isBlank()) "Désactivés" else languageName(s.subtitleLanguage), icon = Icons.Default.ClosedCaption) {
            c.choose("Sous-titres", (listOf("" to "Désactivés") + languages).map { (code, name) ->
                MenuOption(name, selected = code == s.subtitleLanguage) { c.vm.update { it.copy(subtitleLanguage = code) } }
            })
        }
    }
    item("pb-next") {
        SettingRow("Épisode suivant automatique", if (s.autoplayNextEpisode) "Activé" else "Désactivé", "Enchaîne l'épisode suivant à la fin du générique", icon = Icons.Default.SkipNext) {
            c.vm.update { it.copy(autoplayNextEpisode = !s.autoplayNextEpisode) }
        }
    }
    item("pb-rate") {
        SettingRow("Adapter la fréquence de l'écran", if (s.frameRateMatch) "Activé" else "Désactivé", "Films et séries : la TV passe en 24 ou 25 Hz pour des mouvements sans saccade", icon = Icons.Default.Tv) {
            c.vm.update { it.copy(frameRateMatch = !s.frameRateMatch) }
        }
    }
    item("pb-passthrough") {
        SettingRow("Son vers l'ampli (passthrough)", if (s.audioPassthrough) "Activé" else "Désactivé", "Dolby et DTS envoyés tels quels à l'ampli ou à la barre de son. Désactivez si le son est absent.", icon = Icons.Default.Speaker) {
            c.vm.update { it.copy(audioPassthrough = !s.audioPassthrough) }
        }
    }
    item("pb-advanced") { AdvancedRow(c.advanced.value, 4) { c.advanced.value = !c.advanced.value } }
    if (c.advanced.value) {
        item("pb-format") {
            SettingRow("Format des chaînes (Xtream)", s.liveFormat.label) {
                c.choose("Format des chaînes", LiveFormat.entries.map { f -> MenuOption(f.label, selected = f == s.liveFormat) { c.vm.update { it.copy(liveFormat = f) } } })
            }
        }
        item("pb-buffer") {
            SettingRow("Mémoire tampon", s.bufferProfile.label) {
                c.choose("Mémoire tampon", BufferProfile.entries.map { b -> MenuOption(b.label, selected = b == s.bufferProfile) { c.vm.update { it.copy(bufferProfile = b) } } })
            }
        }
        item("pb-decoder") {
            SettingRow("Décodage", s.decoderMode.label, "Passez en « Logiciel prioritaire » si le son ou l'image ne passe pas sur certaines chaînes") {
                c.choose("Décodage", DecoderMode.entries.map { d -> MenuOption(d.label, selected = d == s.decoderMode) { c.vm.update { it.copy(decoderMode = d) } } })
            }
        }
        item("pb-ua") {
            SettingRow("User-Agent", s.userAgent) {
                c.edit(TextEdit("User-Agent", s.userAgent) { v -> c.vm.update { it.copy(userAgent = v.ifBlank { AppSettings.DEFAULT_USER_AGENT }) } })
            }
        }
    }
}
