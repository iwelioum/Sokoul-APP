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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import com.sokoul.iptv.ui.components.SourceBadge
import com.sokoul.iptv.ui.components.SourceLabels
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import kotlinx.coroutines.delay

/** Panneau latéral ouvert depuis la barre du lecteur. */
enum class PlayerPanel { VERSIONS, SUBTITLES }

// --- Cadre commun ------------------------------------------------------------------------------------

/**
 * Tiroir à droite, sur toute la hauteur (même forme que « Épisodes ») : titre, lignes d'explication,
 * liste parcourue au D-pad. Le focus arrive sur la ligne [focusIndex] (le choix en cours).
 */
@Composable
private fun SidePanel(
    title: String, icon: ImageVector, subtitle: List<String>, entry: FocusRequester, focusIndex: Int, modifier: Modifier = Modifier,
    hints: List<KeyHint> = listOf(KeyHint("OK", "Choisir"), KeyHint("Retour", "Fermer")),
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
        subtitle.forEachIndexed { i, s ->
            Text(
                s, Modifier.padding(horizontal = 22.dp).padding(top = if (i == 0) 4.dp else 1.dp),
                style = MaterialTheme.typography.bodyMedium, color = if (i == 0) SokoulColors.TextSecondary else SokoulColors.TextMuted,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        LazyColumn(
            Modifier.weight(1f).padding(top = 10.dp), state = state,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
        KeyHints(hints, Modifier.padding(horizontal = 22.dp).padding(top = 10.dp))
    }
    LaunchedEffect(Unit) {
        // on garde quelques lignes au-dessus (titre de section visible)
        if (focusIndex > 3) state.scrollToItem(focusIndex - 3)
        delay(60)
        runCatching { entry.requestFocus() }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
        Text(title.uppercase(), style = Type.Kicker)
        hint?.let { Text(it, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted) }
    }
}

private fun LazyListScope.section(title: String, hint: String? = null) {
    item(contentType = "section") { SectionTitle(title, hint) }
}

/** Ligne choisissable : coche si sélectionnée, puis contenu libre. */
@Composable
private fun PanelRow(
    selected: Boolean, description: String, entry: FocusRequester?, onClick: () -> Unit,
    modifier: Modifier = Modifier, indent: Boolean = false, content: @Composable (focused: Boolean) -> Unit,
) {
    FocusSurface(style = FocusStyle.Row,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(start = if (indent) 26.dp else 0.dp).then(if (entry != null) Modifier.focusRequester(entry) else Modifier)
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
private fun RowNoteItem(text: String) = Text(text, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextMuted)

/** Puces : origine (pleine), puis HDR et codec (contour). */
@Composable
private fun OriginChips(s: PlayerSource, modifier: Modifier = Modifier, withQuality: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SourceBadge(SourceLabels.originShort(s.origin), filled = true)
        if (withQuality) s.quality?.let { SourceBadge(SourceLabels.quality(it), filled = false) }
        listOfNotNull(if (withQuality) s.hdr else null, s.codec).forEach { SourceBadge(it, filled = false) }
    }
}

/** Repère de qualité par rapport à la source en cours : « Image moins bonne : 720p » (orange) ou « Image meilleure : 4K » (vert). */
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
            (if (color == SokoulColors.Warning) "Image moins bonne : " else "Image meilleure : ") + SourceLabels.quality(q),
            Modifier.padding(start = 4.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        )
    }
}

// --- Langue et qualité ---------------------------------------------------------------------------------

/** Lignes du panneau « Langue et qualité », dans l'ordre d'affichage. */
internal sealed interface VersionLine {
    val key: String
    data class Header(val title: String, val hint: String? = null, override val key: String) : VersionLine
    data class Track(val option: TrackOption, val i: Int) : VersionLine { override val key get() = "t$i" }
    data class Version(val group: VersionGroup) : VersionLine { override val key get() = "v" + SourceCatalog.versionKey(group.best) }
    data class Alt(val source: PlayerSource, val groupKey: String) : VersionLine { override val key get() = "a" + source.id }
    data class Note(val text: String, override val key: String) : VersionLine
}

/** Titre d'une version : « 4K · HDR10 », « 1080p », « Qualité non indiquée ». */
internal fun versionTitle(g: VersionGroup): String =
    listOfNotNull(g.quality?.let(SourceLabels::quality), g.hdr).joinToString(" · ").ifEmpty { "Qualité non indiquée" }

/** Détail d'une source : « Abonnement IPTV · « FILMS 4K » · aussi dans « FR 4K » · qualité mesurée ». */
internal fun sourceDetail(s: PlayerSource): String = listOfNotNull(
    s.where,
    s.alsoIn.takeIf { it.isNotEmpty() }?.joinToString(", ") { "« $it »" }?.let { "aussi dans $it" },
    PlayerSources.basisLabel(s.basis),
    if (s.failed) "a échoué récemment" else null,
).joinToString(" · ")

/**
 * Lignes du panneau : pistes du fichier (si le choix existe), puis une section par langue, une ligne par version
 * (langue + qualité) avec la source qui sera lue, et ses autres sources quand la version est dépliée.
 */
internal fun versionLines(tracks: List<TrackOption>, groups: List<VersionGroup>, expanded: Set<String>, live: Boolean): List<VersionLine> = buildList {
    if (live || tracks.size > 1) {
        add(VersionLine.Header(if (live) "Pistes audio" else "Dans ce fichier", if (live) null else "Même image, changement immédiat", "h-tracks"))
        if (tracks.isEmpty()) add(VersionLine.Note("Pistes en cours de lecture…", "n-tracks"))
        tracks.forEachIndexed { i, t -> add(VersionLine.Track(t, i)) }
    }
    var lang: String? = null
    groups.forEach { g ->
        val k = SourceCatalog.languageKey(g.best)
        if (k != lang) {
            lang = k
            add(VersionLine.Header(PlayerSources.languageName(g.language, g.region), null, "h-$k"))
        }
        add(VersionLine.Version(g))
        val vk = SourceCatalog.versionKey(g.best)
        if (vk in expanded) g.others.forEach { add(VersionLine.Alt(it, vk)) }
    }
}

/** Compteur d'en-tête : « 9 sources trouvées : IPTV 4 · RD 5 » + état de la recherche hors abonnement. */
internal fun sourceCountLine(sources: List<PlayerSource>, search: SourceSearch): String {
    val counts = SourceCatalog.counts(sources)
    val total = counts.sumOf { it.second }
    val base = when (total) {
        0 -> "Aucune source trouvée"
        1 -> "1 source trouvée"
        else -> "$total sources trouvées"
    } + if (counts.size > 1) " : " + counts.joinToString(" · ") { (o, n) -> "${SourceLabels.originShort(o)} $n" } else ""
    val rd = SourceLabels.originName(com.sokoul.iptv.domain.SourceOrigin.DEBRID)
    return base + when (search) {
        SourceSearch.SEARCHING -> " · $rd : recherche…"
        SourceSearch.OFF -> " · $rd : recherche de sources non activée"
        SourceSearch.DONE -> ""
    }
}

/** « Vous regardez : Français (VF) · 1080p (mesurée) · H.264 · Abonnement IPTV · « FILMS FR » ». */
internal fun nowLine(cur: PlayerSource?): String? = cur?.let { s ->
    "Vous regardez : " + listOfNotNull(
        PlayerSources.languageName(s.language, s.region),
        s.quality?.let { SourceLabels.quality(it) + if (s.basis == QualityBasis.MEASURED) " (mesurée)" else "" },
        s.hdr, s.codec, s.where,
    ).joinToString(" · ")
}

/**
 * Langue et qualité : un seul endroit pour choisir ce qu'on regarde. Changer de langue change souvent de fichier
 * (donc d'image) : chaque version affiche sa qualité et d'où elle vient, toutes origines confondues.
 * - « Dans ce fichier » : pistes audio du fichier en cours (même image) ;
 * - une section par langue, une ligne par qualité : la source qui sera lue, et « Autres sources (n) » (DROITE pour les voir).
 * Sans sources (direct) : seulement les pistes audio.
 */
@Composable
fun VersionPanel(
    tracks: List<TrackOption>, sources: List<PlayerSource>, languages: List<String>, search: SourceSearch, resumeAt: String,
    modifier: Modifier = Modifier, onTrack: (TrackOption) -> Unit, onSource: (PlayerSource) -> Unit,
) {
    val live = sources.isEmpty() && search == SourceSearch.DONE
    val entry = remember { FocusRequester() }
    val expanded = remember { mutableStateListOf<String>() }
    val groups = remember(sources, languages) { SourceCatalog.group(sources, languages) }
    val current = sources.firstOrNull { it.current }
    val lines = versionLines(tracks, groups, expanded.toSet(), live)
    val groupFocus = remember(groups) { groups.associate { SourceCatalog.versionKey(it.best) to FocusRequester() } }
    val focusIndex = lines.indexOfFirst { it is VersionLine.Version && it.group.isCurrent }.takeIf { it >= 0 }
        ?: lines.indexOfFirst { it is VersionLine.Track && it.option.selected }.takeIf { it >= 0 }
        ?: lines.indexOfFirst { it is VersionLine.Version || it is VersionLine.Track }.coerceAtLeast(0)
    val subtitle = if (live) emptyList() else listOfNotNull(
        nowLine(current),
        tracks.singleOrNull()?.let { "Son : ${it.label}" },
        sourceCountLine(sources, search) + if (groups.size > 1) " · changer reprend à $resumeAt" else "",
    )
    val hints = buildList {
        add(KeyHint("OK", "Choisir"))
        if (groups.any { it.others.isNotEmpty() }) add(KeyHint("►", "Autres sources"))
        add(KeyHint("Retour", "Fermer"))
    }
    SidePanel(if (live) "Audio" else "Langue et qualité", Icons.Default.Audiotrack, subtitle, entry, focusIndex, modifier, hints) {
        if (!live && groups.isEmpty()) item(key = "none") { RowNoteItem("Aucune autre source pour ce titre.") }
        lines.forEachIndexed { i, line ->
            val e = entry.takeIf { i == focusIndex }
            when (line) {
                is VersionLine.Header -> item(key = line.key, contentType = "section") { SectionTitle(line.title, line.hint) }
                is VersionLine.Note -> item(key = line.key) { RowNoteItem(line.text) }
                is VersionLine.Track -> item(key = line.key) {
                    val t = line.option
                    PanelRow(t.selected, t.label, e, { onTrack(t) }) { f -> RowTitle(t.label, t.selected, f) }
                }
                is VersionLine.Version -> item(key = line.key) {
                    val g = line.group
                    val vk = SourceCatalog.versionKey(g.best)
                    val s = g.shown
                    val open = vk in expanded
                    val n = g.others.size
                    val lang = PlayerSources.languageName(g.language, g.region)
                    PanelRow(
                        g.isCurrent,
                        (if (g.isCurrent) "En cours, " else "") + "$lang · ${versionTitle(g)}, ${s.where}" + when (n) { 0 -> ""; 1 -> ", 1 autre source"; else -> ", $n autres sources" },
                        e, { if (!g.isCurrent) onSource(g.best) },
                        Modifier.focusRequester(groupFocus.getValue(vk)).onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown || n == 0) return@onPreviewKeyEvent false
                            when (ev.key) {
                                Key.DirectionRight -> { if (!open) expanded += vk; true }
                                Key.DirectionLeft -> if (open) { expanded -= vk; true } else false
                                else -> false
                            }
                        },
                    ) { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                versionTitle(g), Modifier.weight(1f, fill = false), color = if (f || g.isCurrent) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (g.isCurrent) FontWeight.Bold else FontWeight.Medium,
                            )
                            if (g.isCurrent) Text("  EN COURS", style = Type.Badge, color = SokoulColors.Accent, maxLines = 1)
                            OriginChips(s, Modifier.padding(start = 10.dp))
                        }
                        if (!g.isCurrent) QualityDelta(s, current)
                        RowNote(sourceDetail(s), SokoulColors.TextSecondary)
                        if (n > 0) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (open) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null, Modifier.size(14.dp), tint = SokoulColors.TextMuted)
                            Text(
                                (if (n == 1) "1 autre source" else "$n autres sources") + " : " +
                                    g.others.groupingBy { it.origin }.eachCount().entries.joinToString(" · ") { (o, c) -> "${SourceLabels.originShort(o)} $c" },
                                Modifier.padding(start = 4.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                is VersionLine.Alt -> item(key = line.key) {
                    val s = line.source
                    PanelRow(
                        s.current, (if (s.current) "En cours, " else "") + SourceLabels.originName(s.origin) + ", " + sourceDetail(s), e,
                        { if (!s.current) onSource(s) },
                        Modifier.onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                                expanded -= line.groupKey
                                runCatching { groupFocus[line.groupKey]?.requestFocus() }
                                true
                            } else false
                        },
                        indent = true,
                    ) { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                SourceLabels.originName(s.origin), Modifier.weight(1f, fill = false),
                                color = if (f || s.current) SokoulColors.TextPrimary else SokoulColors.TextSecondary, maxLines = 1,
                                style = MaterialTheme.typography.titleSmall, fontWeight = if (s.current) FontWeight.Bold else FontWeight.Medium,
                            )
                            if (s.current) Text("  EN COURS", style = Type.Badge, color = SokoulColors.Accent, maxLines = 1)
                            OriginChips(s, Modifier.padding(start = 10.dp), withQuality = true)
                        }
                        RowNote(sourceDetail(s), if (s.failed) SokoulColors.Warning else SokoulColors.TextSecondary)
                        s.fileName?.let { Text(it, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

// --- Sous-titres -----------------------------------------------------------------------------------------

/** Sous-titres : désactivés, ceux du fichier, ceux trouvés en ligne, puis le décalage. */
@Composable
fun SubtitlePanel(
    options: List<TrackOption>, offsetMs: Long?, delayOnly: Boolean, modifier: Modifier = Modifier,
    onShift: (Long) -> Unit, onPick: (TrackOption) -> Unit,
) {
    val entry = remember { FocusRequester() }
    val selected = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    SidePanel("Sous-titres", Icons.Default.ClosedCaption, emptyList(), entry, focusIndex = selected + 1, modifier = modifier) {
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
