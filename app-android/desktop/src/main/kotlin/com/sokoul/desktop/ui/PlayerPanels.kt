package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.desktop.player.PlayerStatus
import com.sokoul.desktop.tablet.VersionGroup
import com.sokoul.desktop.tablet.languageTitle
import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.components.SourceBadge
import com.sokoul.iptv.ui.components.SourceLabels
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.formatDuration

// Panneaux latéraux du lecteur de la tablette (`PlayerPanels.kt`) : la vidéo garde plus de la moitié de l'écran.

@Composable
fun SidePanel(title: String, icon: ImageVector, subtitle: List<String>, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    Column(
        modifier.fillMaxHeight().fillMaxWidth(0.4f).widthIn(max = 560.dp)
            .background(Brush.horizontalGradient(listOf(SokoulColors.Panel.copy(alpha = 0.92f), SokoulColors.Panel)))
            .padding(vertical = 24.dp),
    ) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(26.dp), tint = SokoulColors.Accent)
            Text(title, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        subtitle.forEachIndexed { i, s ->
            Text(
                s, Modifier.padding(horizontal = 22.dp).padding(top = if (i == 0) 4.dp else 1.dp),
                style = MaterialTheme.typography.bodyMedium, color = if (i == 0) SokoulColors.TextSecondary else SokoulColors.TextMuted,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f).padding(top = 10.dp)) {
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
            // d'autres lignes plus bas : le bas de la liste s'estompe (au lieu d'une ligne coupée net)
            if (state.canScrollForward) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp).background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.Panel))))
        }
    }
}

private fun LazyListScope.section(title: String, hint: String? = null) = item(key = "h-$title", contentType = "section") {
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
        Text(title.uppercase(), style = Type.Kicker)
        hint?.let { Text(it, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted) }
    }
}

/** Ligne choisissable : coche si sélectionnée, puis contenu libre. */
@Composable
private fun PanelRow(selected: Boolean, onClick: () -> Unit, indent: Boolean = false, content: @Composable (focused: Boolean) -> Unit) {
    FocusSurface(
        style = FocusStyle.Row, onClick = onClick, modifier = Modifier.fillMaxWidth().padding(start = if (indent) 26.dp else 0.dp),
        shape = Radius.Card, color = if (selected) SokoulColors.AccentTint else Color.Transparent,
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = SokoulColors.Accent) else Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) { content(focused) }
        }
    }
}

@Composable
private fun RowTitle(text: String, selected: Boolean, focused: Boolean) = Text(
    text, color = if (focused || selected) SokoulColors.TextPrimary else SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
    style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
)

@Composable
private fun RowNote(text: String, color: Color = SokoulColors.TextMuted, maxLines: Int = 1) =
    Text(text, Modifier.padding(top = 2.dp), color = color, style = MaterialTheme.typography.labelMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** « Blu-ray · 15 Go · prête » : origine du fichier, taille, état. */
private fun streamNote(s: DebridStream) = listOfNotNull(
    s.release.source?.label, SourceLabels.sizeOrNull(s.release.sizeBytes), if (s.release.cached == true || s.inAccount) "prête" else null,
).joinToString(" · ")

/** Nom lisible d'une piste libVLC, en français (« Piste 1 - [French] » → « Français »). */
fun shortTrack(label: String): String {
    val raw = Regex("""\[([^\]]+)]""").find(label)?.groupValues?.get(1) ?: label.substringAfter(" - ").ifBlank { label }
    return com.sokoul.desktop.player.AudioChoice.languageOf(raw)?.let(com.sokoul.desktop.data.DesktopPrefs::languageName) ?: raw
}

/**
 * Langue et qualité (tablette) : ce qu'on regarde, pistes du fichier (même image, changement immédiat), puis une
 * section par langue, une ligne par version avec la source lue, les autres sources dépliables et les échecs.
 * Changer de version reprend à la position actuelle.
 */
@Composable
fun LanguageQualityPanel(app: AppState, t: NowPlaying.Title, st: PlayerStatus, player: DesktopPlayer, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val result by produceState<com.sokoul.desktop.data.DebridResult?>(null, t.query) { value = app.debrid.find(t.query, app.catalog.details(t.kind, t.tmdbId)) }
    val groups = remember(result) { VersionGroup.of(result?.streams.orEmpty()) }
    val expanded = remember { mutableStateListOf<String>() }
    val current = t.stream
    val now = current?.let { c ->
        "Vous regardez : " + listOfNotNull(
            languageTitle(c.release.language), c.release.quality?.let(SourceLabels::quality), c.release.hdr?.let(SourceLabels::hdr), c.release.codec,
            "Real-Debrid", c.release.source?.label, SourceLabels.sizeOrNull(c.release.sizeBytes),
        ).joinToString(" · ")
    }
    val count = result?.streams?.size
    SidePanel(
        "Langue et qualité", Icons.Default.Audiotrack,
        listOfNotNull(now, count?.let { "$it sources trouvées : RD $it · changer reprend à ${formatDuration(st.timeMs)}" } ?: "Recherche des sources…"),
        modifier,
    ) {
        if (st.audio.size > 1) {
            section("Dans ce fichier", "Même image, changement immédiat")
            items(st.audio, key = { "t${it.first}" }) { (id, label) ->
                PanelRow(id == st.audioId, { player.setAudio(id) }) { f -> RowTitle(shortTrack(label), id == st.audioId, f) }
            }
        }
        groups.groupBy { it.language }.forEach { (lang, list) ->
            section(languageTitle(lang))
            list.forEach { g ->
                val key = "${g.language}|${g.quality}|${g.hdr}"
                val isCurrent = g.streams.any { it.key == current?.key }
                val shown = g.streams.firstOrNull { it.key == current?.key } ?: g.best
                val others = g.streams.filter { it.key != shown.key }
                val open = key in expanded
                item(key = "v-$key") {
                    PanelRow(isCurrent, { if (!isCurrent) { onClose(); app.playTitle(t, shown) } }) { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                g.title, Modifier.weight(1f, fill = false), color = if (f || isCurrent) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            )
                            if (isCurrent) Text("  EN COURS", style = Type.Badge, color = SokoulColors.Accent, maxLines = 1)
                            Row(Modifier.padding(start = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                SourceBadge("RD", filled = true)
                                shown.release.codec?.let { SourceBadge(it, filled = false) }
                            }
                        }
                        val fail = app.failures[shown.key]
                        RowNote(listOf(streamNote(shown)).plus(listOfNotNull(fail?.let { "a échoué : $it" })).filter { it.isNotBlank() }.joinToString(" · "), if (fail != null) SokoulColors.Warning else SokoulColors.TextSecondary, maxLines = 2)
                        if (others.isNotEmpty()) FocusSurface(
                            style = FocusStyle.Chip, onClick = { if (open) expanded -= key else expanded += key }, modifier = Modifier.padding(top = 4.dp),
                            color = Color.Transparent, shape = Radius.Tiny,
                        ) { hf ->
                            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (open) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null, Modifier.size(14.dp), tint = if (hf) SokoulColors.OnLight else SokoulColors.TextMuted)
                                Text(
                                    (if (others.size == 1) "1 autre source" else "${others.size} autres sources") + " (RD ${others.size})",
                                    Modifier.padding(start = 4.dp), color = if (hf) SokoulColors.OnLight else SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                if (open) items(others.take(30), key = { "a-${it.key}" }) { s ->
                    PanelRow(false, { onClose(); app.playTitle(t, s) }, indent = true) { f ->
                        Text(streamNote(s).ifEmpty { "Real-Debrid" }, color = if (f) SokoulColors.TextPrimary else SokoulColors.TextSecondary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        app.failures[s.key]?.let { RowNote("a échoué : $it", SokoulColors.Warning, 2) }
                        Text(s.title, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (result != null && groups.isEmpty()) item("none") { Text("Aucune autre source pour ce titre.", Modifier.padding(8.dp), color = SokoulColors.TextMuted) }
    }
}

/** Sous-titres (tablette) : désactivés, pistes du fichier, puis le décalage (± 0,5 s). */
@Composable
fun SubtitlePanel(st: PlayerStatus, player: DesktopPlayer, modifier: Modifier = Modifier) {
    SidePanel("Sous-titres", Icons.Default.ClosedCaption, emptyList(), modifier) {
        section("Choix")
        items(st.subtitles.ifEmpty { listOf(-1 to "Désactivés") }, key = { "s${it.first}" }) { (id, label) ->
            PanelRow(id == st.subtitleId, { player.setSubtitles(id) }) { f -> RowTitle(if (id < 0) "Désactivés" else shortTrack(label), id == st.subtitleId, f) }
        }
        if (st.subtitles.size > 1) {
            section("Décalage", "Si les sous-titres arrivent trop tôt ou trop tard")
            item("offset") {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SokoulButton("− 0,5 s", { player.setSubtitleDelay(st.subtitleDelayMs - 500) })
                    Text(
                        if (st.subtitleDelayMs == 0L) "Aucun" else "%+.1f s".format(java.util.Locale.FRANCE, st.subtitleDelayMs / 1000.0),
                        Modifier.widthIn(min = 80.dp), style = MaterialTheme.typography.titleMedium,
                    )
                    SokoulButton("+ 0,5 s", { player.setSubtitleDelay(st.subtitleDelayMs + 500) })
                }
            }
        }
    }
}

/** Vitesses du menu « … ». */
val PLAYBACK_RATES = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

fun rateLabel(r: Float) = if (r == 1f) "Normale" else "%s×".format(java.util.Locale.FRANCE, if (r % 1f == 0f) r.toInt().toString() else "%.2f".format(java.util.Locale.FRANCE, r).trimEnd('0'))
