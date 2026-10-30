package com.sokoul.desktop.tablet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.data.DebridResult
import com.sokoul.desktop.data.Movie
import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.ui.components.DialogHeader
import com.sokoul.iptv.ui.components.DialogPlacement
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.SokoulDialog
import com.sokoul.iptv.ui.components.SokoulSpinner
import com.sokoul.iptv.ui.components.SourceBadge
import com.sokoul.iptv.ui.components.SourceLabels
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sources d'un titre, au format de la fenêtre des sources de la tablette (panneau latéral) : une section par langue,
 * une ligne par définition et HDR avec la source qui sera lue (la meilleure), « n autres sources » qui se déplient,
 * puis les versions de l'abonnement IPTV. Un clic sur une ligne lance cette source.
 */
@Composable
fun TabletSources(app: AppState, t: NowPlaying.Title) {
    val result by produceState<DebridResult?>(null, t.query) {
        value = withContext(Dispatchers.Default) { app.debrid.find(t.query, app.catalog.details(t.kind, t.tmdbId)) }
    }
    // versions de l'abonnement IPTV (films) : même œuvre par identifiant TMDB, ou titre normalisé et année à ±1 an
    val iptv by produceState(emptyList<Movie>(), t.tmdbId, app.playlist) {
        val p = app.playlist ?: return@produceState
        if (t.kind != "movie") return@produceState
        val year = t.subtitle?.toIntOrNull()
        val norm = com.sokoul.iptv.data.repo.VodGrouping.normalizeTitle(t.title, year)
        value = withContext(Dispatchers.IO) {
            app.db.movies(p.id, t.title.take(40), 80).filter { m ->
                m.tmdbId?.trim()?.trimStart('0') == t.tmdbId.toString() ||
                    com.sokoul.iptv.data.repo.VodGrouping.normalizeTitle(m.name, m.year) == norm && (year == null || m.year == null || kotlin.math.abs(m.year - year) <= 1)
            }
        }
    }
    val expanded = remember { mutableStateListOf<String>() }
    val playing = t.stream?.key
    val close = { app.sources = null }
    SokoulDialog(close, DialogPlacement.Side, maxWidth = 620.dp) {
        val r = result
        DialogHeader(
            "Langue et qualité",
            listOfNotNull(
                listOfNotNull(t.title, t.season?.let { "S$it · É${t.episode ?: 1}" }).joinToString(" · "),
                r?.let { if (it.streams.size > 1) "${it.streams.size} sources Real-Debrid" else null },
            ).joinToString(" · "),
        )
        LazyColumn(Modifier.weight(1f, fill = false).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                r == null -> item("wait") {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SokoulSpinner(size = 20.dp)
                        Text("Recherche des sources sur Torrentio et Comet…", Modifier.padding(start = 12.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                r.streams.isEmpty() -> item("none") { RowNoteItem(r.problem ?: "Aucune source Real-Debrid pour ce titre.") }
                else -> versions(VersionGroup.of(r.streams), playing, expanded) { s -> close(); app.playTitle(t, s) }
            }
            if (iptv.isNotEmpty()) {
                section("Abonnement IPTV", "Utilise la connexion de l'abonnement")
                items(iptv.take(6), key = { "iptv-${it.id}" }) { m ->
                    PanelRow(false, "Abonnement IPTV, ${m.name}", { close(); app.playMovie(m) }) { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, Modifier.weight(1f, fill = false), color = if (f) SokoulColors.TextPrimary else SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            SourceBadge(SourceLabels.originShort(com.sokoul.iptv.domain.SourceOrigin.IPTV), filled = true, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String, hint: String? = null) = item(key = "h-$title", contentType = "section") {
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
        Text(title.uppercase(), style = Type.Kicker)
        hint?.let { Text(it, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted) }
    }
}

/** Versions rangées par langue (MULTI, VF, VOSTFR, VO) : un intertitre par langue, puis ses versions. */
private fun LazyListScope.versions(groups: List<VersionGroup>, playing: String?, expanded: MutableList<String>, onPlay: (DebridStream) -> Unit) {
    groups.groupBy { it.language }.forEach { (lang, list) ->
        section(lang?.name ?: "Langue non précisée", VersionGroup.explain(lang))
        list.forEach { versionRows(it, playing, expanded, onPlay) }
    }
}

/** Une version (langue, définition, HDR) : sa meilleure source, puis les autres quand elle est dépliée. */
private fun LazyListScope.versionRows(g: VersionGroup, playing: String?, expanded: MutableList<String>, onPlay: (DebridStream) -> Unit) {
    val key = "${g.language}|${g.quality}|${g.hdr}"
    val open = key in expanded
    val others = g.streams.drop(1)
    item(key = "v-$key") {
        val s = g.best
        val current = s.key == playing
        PanelRow(current, "${g.title}, ${g.note.orEmpty()}", { if (!current) onPlay(s) }) { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    g.title, Modifier.weight(1f, fill = false), color = if (f || current) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                )
                if (current) Text("  EN COURS", style = Type.Badge, color = SokoulColors.Accent, maxLines = 1)
                Chips(s, Modifier.padding(start = 10.dp))
            }
            g.note?.let { RowNote(it) }
            Text(s.title, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (others.isNotEmpty()) FocusSurface(
                style = FocusStyle.Chip, onClick = { if (open) expanded -= key else expanded += key },
                modifier = Modifier.padding(top = 4.dp), color = Color.Transparent, shape = Radius.Tiny,
            ) { hf ->
                Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (open) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null, Modifier.size(14.dp), tint = if (hf) SokoulColors.OnLight else SokoulColors.TextMuted)
                    Text(
                        if (others.size == 1) "1 autre source" else "${others.size} autres sources", Modifier.padding(start = 4.dp),
                        color = if (hf) SokoulColors.OnLight else SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
    if (open) items(others, key = { "alt-${it.key}" }) { s ->
        val current = s.key == playing
        PanelRow(current, "Real-Debrid, ${s.title}", { if (!current) onPlay(s) }, indent = true) { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(s.release.source?.label, SourceLabels.sizeOrNull(s.release.sizeBytes), if (s.release.cached == true || s.inAccount) "prête" else null).joinToString(" · ").ifEmpty { "Real-Debrid" },
                    Modifier.weight(1f, fill = false), color = if (f || current) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                    maxLines = 1, style = MaterialTheme.typography.titleSmall,
                )
                Chips(s, Modifier.padding(start = 10.dp))
            }
            Text(s.title, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Puces : origine (pleine), puis langue et codec (contour), comme `OriginChips` de la tablette. */
@Composable
private fun Chips(s: DebridStream, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SourceBadge(SourceLabels.originShort(com.sokoul.iptv.domain.SourceOrigin.DEBRID), filled = true)
        listOfNotNull(s.release.language?.name, s.release.codec).forEach { SourceBadge(it, filled = false) }
    }
}

/** Ligne choisissable de la tablette : coche si sélectionnée, puis contenu libre. */
@Composable
private fun PanelRow(selected: Boolean, description: String, onClick: () -> Unit, indent: Boolean = false, content: @Composable (focused: Boolean) -> Unit) {
    FocusSurface(
        style = FocusStyle.Row, onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = if (indent) 26.dp else 0.dp).semantics { contentDescription = description + if (selected) ", sélectionné" else "" },
        shape = Radius.Card, color = if (selected) SokoulColors.AccentTint else Color.Transparent,
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = SokoulColors.Accent) else Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) { content(focused) }
        }
    }
}

@Composable
private fun RowNote(text: String) =
    Text(text, Modifier.padding(top = 2.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun RowNoteItem(text: String) = Text(text, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextMuted)
