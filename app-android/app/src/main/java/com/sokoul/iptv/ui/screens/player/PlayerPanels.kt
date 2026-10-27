package com.sokoul.iptv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import com.sokoul.iptv.ui.components.SourceBadge
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import kotlinx.coroutines.delay

/** Panneau latéral ouvert depuis la barre du lecteur. */
enum class PlayerPanel { AUDIO, SUBTITLES, SOURCES }

// --- Cadre commun ------------------------------------------------------------------------------------

/**
 * Tiroir à droite, sur toute la hauteur (même forme que « Épisodes ») : titre, sous-titre explicatif,
 * liste parcourue au D-pad. Le focus arrive sur la ligne marquée [focusKey] (le choix en cours).
 */
@Composable
private fun SidePanel(
    title: String, icon: ImageVector, subtitle: String?, entry: FocusRequester, focusIndex: Int, modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val l = LocalLayout.current
    val state = rememberLazyListState()
    Column(
        modifier.fillMaxHeight().fillMaxWidth(if (l.isCompact) 1f else 0.46f).widthIn(max = 620.dp)
            .background(Brush.horizontalGradient(listOf(SokoulColors.Panel.copy(alpha = 0.92f), SokoulColors.Panel)))
            .padding(vertical = 24.dp),
    ) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(26.dp), tint = SokoulColors.Accent)
            Text(title, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        subtitle?.let {
            Text(it, Modifier.padding(horizontal = 22.dp).padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary)
        }
        LazyColumn(
            Modifier.weight(1f).padding(top = 10.dp), state = state,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
        KeyHints(listOf(KeyHint("OK", "Choisir"), KeyHint("Retour", "Fermer")), Modifier.padding(horizontal = 22.dp).padding(top = 10.dp))
    }
    LaunchedEffect(Unit) {
        // on garde quelques lignes au-dessus (titre de section visible)
        if (focusIndex > 3) state.scrollToItem(focusIndex - 3)
        delay(60)
        runCatching { entry.requestFocus() }
    }
}

private fun LazyListScope.section(title: String, hint: String? = null) {
    item(contentType = "section") {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(title.uppercase(), style = Type.Kicker)
            hint?.let { Text(it, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted) }
        }
    }
}

/** Ligne choisissable : coche si sélectionnée, puis contenu libre. */
@Composable
private fun PanelRow(selected: Boolean, description: String, entry: FocusRequester?, onClick: () -> Unit, content: @Composable (focused: Boolean) -> Unit) {
    FocusSurface(style = FocusStyle.Row,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(if (entry != null) Modifier.focusRequester(entry) else Modifier)
            .semantics { contentDescription = description + if (selected) ", sélectionné" else "" },
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
private fun RowNote(text: String, color: Color = SokoulColors.TextMuted) =
    Text(text, Modifier.padding(top = 2.dp), color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Chips(s: PlayerSource, modifier: Modifier = Modifier) {
    val chips = PlayerSources.chips(s)
    if (chips.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SourceBadge(if (s.origin == SourceOrigin.IPTV) "IPTV" else "RD", filled = true)
        chips.forEach { SourceBadge(it, filled = false) }
    }
}

/** Repère de qualité par rapport à la source en cours : « ↓ 720p » (orange) ou « ↑ 4K » (vert). */
@Composable
private fun QualityDelta(s: PlayerSource, current: PlayerSource?) {
    val q = s.quality ?: return
    val (icon, color) = when {
        PlayerSources.qualityDrop(s, current) -> Icons.Default.ArrowDownward to SokoulColors.Warning
        PlayerSources.qualityGain(s, current) -> Icons.Default.ArrowUpward to SokoulColors.Success
        else -> return
    }
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Text(
            (if (color == SokoulColors.Warning) "Image moins bonne : " else "Image meilleure : ") + com.sokoul.iptv.ui.components.SourceLabels.quality(q),
            Modifier.padding(start = 4.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        )
    }
}

// --- Audio ---------------------------------------------------------------------------------------------

/**
 * Audio : les pistes du fichier en cours (changement immédiat, même image), puis les langues disponibles
 * dans d'autres fichiers — chaque ligne dit clairement que l'image peut changer et à quelle qualité.
 */
@Composable
fun AudioPanel(
    tracks: List<TrackOption>, sources: List<PlayerSource>, resumeAt: String,
    modifier: Modifier = Modifier, onTrack: (TrackOption) -> Unit, onSource: (PlayerSource) -> Unit,
) {
    val entry = remember { FocusRequester() }
    val current = sources.firstOrNull { it.current }
    val others = remember(sources) { PlayerSources.otherLanguages(sources) }
    val selected = tracks.indexOfFirst { it.selected }.coerceAtLeast(0)
    SidePanel(
        "Audio", Icons.Default.Audiotrack,
        current?.let { "Fichier en cours : ${PlayerSources.languageName(it.language, it.region)}" },
        entry, focusIndex = selected + 1, modifier = modifier,
    ) {
        section("Dans ce fichier", "Changement immédiat, même image")
        if (tracks.isEmpty()) item { RowNoteItem("Pistes en cours de lecture…") }
        tracks.forEachIndexed { i, t ->
            item(key = "t$i") {
                PanelRow(t.selected, t.label, if (i == selected) entry else null, { onTrack(t) }) { f -> RowTitle(t.label, t.selected, f) }
            }
        }
        if (others.isNotEmpty()) {
            section("Autres langues · autre fichier", "Le lecteur ouvre un autre fichier et reprend à $resumeAt : l'image peut changer")
            others.forEach { s ->
                item(key = "s" + s.id) {
                    val lang = PlayerSources.languageName(s.language, s.region)
                    PanelRow(false, "$lang, autre fichier, ${PlayerSources.short(s)}", null, { onSource(s) }) { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(lang, Modifier.weight(1f, fill = false), color = if (f) SokoulColors.TextPrimary else SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Chips(s, Modifier.padding(start = 10.dp))
                        }
                        QualityDelta(s, current)
                        RowNote(s.where)
                    }
                }
            }
        } else if (sources.size > 1) {
            item { RowNoteItem("Les autres sources trouvées sont dans la même langue (voir Sources).") }
        }
    }
}

@Composable
private fun RowNoteItem(text: String) = Text(text, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextMuted)

// --- Sous-titres -----------------------------------------------------------------------------------------

/** Sous-titres : désactivés, ceux du fichier, ceux trouvés en ligne, puis le décalage. */
@Composable
fun SubtitlePanel(
    options: List<TrackOption>, offsetMs: Long?, delayOnly: Boolean, modifier: Modifier = Modifier,
    onShift: (Long) -> Unit, onPick: (TrackOption) -> Unit,
) {
    val entry = remember { FocusRequester() }
    val selected = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    SidePanel("Sous-titres", Icons.Default.ClosedCaption, null, entry, focusIndex = selected + 1, modifier = modifier) {
        section("Choix")
        options.forEachIndexed { i, o ->
            item(key = "o$i") {
                if (o.info) RowNoteItem(o.label)
                else PanelRow(o.selected, o.label, if (i == selected) entry else null, { onPick(o) }) { f ->
                    RowTitle(o.label, o.selected, f)
                    if (o.online != null) RowNote("Trouvé en ligne, téléchargé si choisi")
                }
            }
        }
        if (offsetMs != null) item(key = "offset") { SubtitleOffsetRow(offsetMs, delayOnly, onShift) }
    }
}

// --- Sources ---------------------------------------------------------------------------------------------

/**
 * Toutes les sources trouvées, par origine : langue, définition, HDR, codec, puis où elles ont été trouvées
 * et si la qualité est mesurée ou annoncée par le nom. La source en cours est marquée.
 */
@Composable
fun SourcesPanel(sources: List<PlayerSource>, resumeAt: String, modifier: Modifier = Modifier, onPick: (PlayerSource) -> Unit) {
    val entry = remember { FocusRequester() }
    val current = sources.firstOrNull { it.current }
    val iptv = sources.filter { it.origin == SourceOrigin.IPTV }
    val rd = sources.filter { it.origin == SourceOrigin.DEBRID }
    val n = sources.size
    // index de la ligne en cours dans la liste (titres de section compris)
    val idx = when {
        current == null -> 1
        current.origin == SourceOrigin.IPTV -> 1 + iptv.indexOf(current)
        else -> (if (iptv.isEmpty()) 0 else iptv.size + 1) + 1 + rd.indexOf(current)
    }
    SidePanel(
        "Sources", Icons.Default.Layers,
        if (n <= 1) "Une seule source trouvée pour ce titre" else "$n sources trouvées · changer reprend à $resumeAt",
        entry, focusIndex = idx, modifier = modifier,
    ) {
        if (sources.isEmpty()) item { RowNoteItem("Aucune autre source trouvée.") }
        if (iptv.isNotEmpty()) section("Abonnement IPTV (${iptv.size})")
        iptv.forEach { s -> sourceItem(s, current, entry.takeIf { s == current || (current == null && s == iptv.first()) }, onPick) }
        if (rd.isNotEmpty()) section("Real-Debrid (${rd.size})")
        rd.forEach { s -> sourceItem(s, current, entry.takeIf { s == current || (current == null && iptv.isEmpty() && s == rd.first()) }, onPick) }
    }
}

private fun LazyListScope.sourceItem(s: PlayerSource, current: PlayerSource?, entry: FocusRequester?, onPick: (PlayerSource) -> Unit) {
    item(key = "src" + s.id) {
        val title = PlayerSources.languageName(s.language, s.region) + (s.quality?.let { " · " + com.sokoul.iptv.ui.components.SourceLabels.quality(it) } ?: "")
        PanelRow(s.current, (if (s.current) "En cours, " else "") + title + ", " + s.where, entry, { if (!s.current) onPick(s) }) { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, Modifier.weight(1f, fill = false), color = if (f || s.current) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (s.current) FontWeight.Bold else FontWeight.Medium,
                )
                if (s.current) Text("  EN COURS", style = Type.Badge, color = SokoulColors.Accent, maxLines = 1)
                Chips(s, Modifier.padding(start = 10.dp))
            }
            if (!s.current) QualityDelta(s, current)
            RowNote(listOfNotNull(s.where, PlayerSources.basisLabel(s.basis)).joinToString(" · "), SokoulColors.TextSecondary)
            s.fileName?.let { Text(it, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

