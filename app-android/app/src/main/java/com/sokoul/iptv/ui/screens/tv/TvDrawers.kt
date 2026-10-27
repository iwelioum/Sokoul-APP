package com.sokoul.iptv.ui.screens.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import com.sokoul.iptv.ui.screens.player.LiveGroup
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.formatClock

/*
 * Tiroirs du direct : collés au bord de l'écran sur toute la hauteur, ils se fondent dans l'image par un dégradé
 * (pas de bulle flottante). En-tête : étiquette d'accent + titre à empattements ; légendes de touches en bas.
 */

/** Tiroir plein hauteur, à gauche ([start]) ou à droite ; [fade] = largeur du fondu côté image. */
@Composable
fun EdgeDrawer(start: Boolean, width: Dp, modifier: Modifier = Modifier, fade: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val solid = SokoulColors.bg(0.95f)
    val stops = if (!fade) arrayOf(0f to solid, 1f to solid)
    else if (start) arrayOf(0f to solid, 0.80f to SokoulColors.bg(0.90f), 1f to Color.Transparent)
    else arrayOf(0f to Color.Transparent, 0.20f to SokoulColors.bg(0.90f), 1f to solid)
    Column(
        modifier.fillMaxHeight().width(width).background(Brush.horizontalGradient(*stops))
            .padding(start = if (start) 0.dp else 28.dp, end = if (start && fade) 28.dp else 0.dp, top = 36.dp, bottom = 22.dp),
        content = content,
    )
}

@Composable
private fun DrawerHeader(kicker: String, title: String, meta: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = Space.l).padding(bottom = Space.m)) {
        Text(kicker, style = Type.Kicker, maxLines = 1)
        Text(title, Modifier.padding(top = 2.dp), style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (meta != null) Text(meta, style = Type.Meta, color = SokoulColors.TextMuted, maxLines = 1)
    }
}

/** Ligne surlignée : fond clair discret et barre d'accent à gauche (même langage que les listes de l'app). */
@Composable
private fun DrawerRow(highlighted: Boolean, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = 1.dp)
            .background(if (highlighted) SokoulColors.white(0.10f) else Color.Transparent, Radius.Card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(44.dp).background(if (highlighted) SokoulColors.Accent else Color.Transparent))
        Row(Modifier.weight(1f).padding(horizontal = Space.m, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Avancement d'un programme (0..1) à l'instant [now]. */
fun ProgramEntity.progressAt(now: Long): Float = ((now - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)

@Composable
private fun ThinProgress(f: Float, modifier: Modifier = Modifier) = com.sokoul.iptv.ui.components.ProgressLine(f, modifier, thickness = 2.dp, onImage = false)

/** Liste des chaînes de la catégorie en cours (◀) : numéro, logo, nom, programme en cours et son avancement. */
@Composable
fun ChannelOverlay(
    groupName: String,
    channels: List<ChannelEntity>,
    highlighted: Int,
    currentId: String?,
    now: Map<String, ProgramEntity>,
    clock: Long,
    withGroups: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 460.dp,
    /** Tactile : bouton « Catégories » dans l'en-tête (à la télécommande, c'est ◀). */
    onGroups: (() -> Unit)? = null,
    onPick: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(highlighted) { keepVisible(state, highlighted) }
    EdgeDrawer(start = true, width = width, modifier = modifier, fade = !withGroups) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawerHeader("CHAÎNES", groupName, "${channels.size} chaînes", Modifier.weight(1f))
            if (onGroups != null) com.sokoul.iptv.ui.components.SokoulButton(
                "Catégories", onGroups, Modifier.padding(end = Space.l, bottom = Space.m), Icons.AutoMirrored.Filled.List,
                com.sokoul.iptv.ui.components.ButtonKind.Glass,
            )
        }
        LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(bottom = Space.m)) {
            itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                val hl = i == highlighted && !withGroups
                val p = now[c.id]
                DrawerRow(hl, Modifier.pointerInput(i) { detectTapGestures { onPick(i) } }) {
                    Text(c.number.toString(), Modifier.width(48.dp), color = if (hl) SokoulColors.Accent else SokoulColors.TextMuted, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ChannelLogo(c.logo, c.name, Modifier.size(56.dp, 34.dp))
                    Column(Modifier.padding(start = Space.m).weight(1f)) {
                        Text(
                            c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (c.id == currentId || hl) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (hl || c.id == currentId) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                        )
                        if (p != null) {
                            Text(p.title, style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ThinProgress(p.progressAt(clock), Modifier.padding(top = 4.dp))
                        }
                    }
                    if (c.id == currentId) Icon(Icons.Default.PlayArrow, "En cours", Modifier.padding(start = Space.s).size(18.dp), tint = SokoulColors.Accent)
                }
            }
        }
        if (!withGroups) KeyHints(
            listOf(KeyHint("OK", "Regarder"), KeyHint("◀", "Catégories"), KeyHint("▶", "Programmes")),
            Modifier.padding(start = Space.l, top = Space.s),
        )
    }
}

/** Catégories (◀◀), en seconde colonne à gauche des chaînes : favoris en tête, puis toutes, puis celles du fournisseur. */
@Composable
fun GroupsPanel(groups: List<LiveGroup>, highlighted: Int, currentId: String?, modifier: Modifier = Modifier, width: Dp = 320.dp, onPick: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(highlighted) { keepVisible(state, highlighted) }
    EdgeDrawer(start = true, width = width, modifier = modifier, fade = false) {
        DrawerHeader("CATÉGORIES", "Bouquets", "${groups.size} catégories")
        LazyColumn(Modifier.weight(1f), state = state) {
            itemsIndexed(groups, key = { _, g -> g.id }) { i, g ->
                val hl = i == highlighted
                DrawerRow(hl, Modifier.pointerInput(i) { detectTapGestures { onPick(i) } }) {
                    if (g.id == SpecialCategory.FAVORITES) Icon(Icons.Default.Star, null, Modifier.padding(end = Space.s).size(18.dp), tint = SokoulColors.Warning)
                    Text(
                        g.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (g.id == currentId || hl) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (g.id == currentId && !hl) SokoulColors.Accent else if (hl) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                    )
                }
            }
        }
        KeyHints(listOf(KeyHint("OK", "Ouvrir"), KeyHint("▶", "Chaînes")), Modifier.padding(start = Space.l, top = Space.s))
    }
}

/**
 * Programmes de la chaîne (▶), à droite : toute la journée à venir, ▲▼ font défiler (sans zapper).
 * Le programme surligné montre son résumé ; celui en cours porte « EN DIRECT » et son avancement.
 */
@Composable
fun MiniGuide(
    c: ChannelEntity, programs: List<ProgramEntity>?, highlighted: Int, now: Long, modifier: Modifier = Modifier,
    width: Dp = 480.dp,
    /** Tactile : toucher un programme le surligne (et montre son résumé). */
    onPick: ((Int) -> Unit)? = null,
) {
    val state = rememberLazyListState()
    // tactile : le doigt fait défiler, la liste ne saute pas vers le programme touché
    val touch = onPick != null
    LaunchedEffect(highlighted) { if (!touch) keepVisible(state, highlighted) }
    EdgeDrawer(start = false, width = width, modifier = modifier) {
        DrawerHeader("PROGRAMMES", c.name, programs?.let { if (it.isEmpty()) null else "${it.size} programmes à venir" })
        when {
            programs == null -> Text("Chargement…", Modifier.padding(Space.l).weight(1f), color = SokoulColors.TextMuted)
            programs.isEmpty() -> Text("Pas d'information de programme pour cette chaîne.", Modifier.padding(Space.l).weight(1f), color = SokoulColors.TextMuted)
            else -> LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(bottom = Space.m)) {
                itemsIndexed(programs, key = { _, p -> p.start }) { i, p ->
                    val hl = i == highlighted
                    val live = p.start <= now && now < p.end
                    DrawerRow(hl, if (onPick != null) Modifier.pointerInput(i) { detectTapGestures { onPick(i) } } else Modifier) {
                        Column(Modifier.width(64.dp)) {
                            Text(formatClock(p.start), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (live) SokoulColors.Accent else SokoulColors.TextPrimary)
                            Text(formatClock(p.end), style = Type.Meta, color = SokoulColors.TextMuted)
                        }
                        Column(Modifier.weight(1f)) {
                            if (live) Text("EN DIRECT", style = Type.Kicker, maxLines = 1)
                            Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (hl) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (live) ThinProgress(p.progressAt(now), Modifier.padding(top = 5.dp))
                            if (hl) p.description?.let {
                                Text(it, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        KeyHints(listOf(KeyHint("▲▼", "Faire défiler"), KeyHint("◀", "Fermer"), KeyHint("Menu", "Guide complet")), Modifier.padding(start = Space.l, top = Space.s))
    }
}

private suspend fun keepVisible(state: LazyListState, i: Int) {
    val visible = state.layoutInfo.visibleItemsInfo
    val first = visible.firstOrNull()?.index ?: 0
    val last = visible.lastOrNull()?.index ?: 0
    if (i <= first || i >= last) state.scrollToItem((i - 3).coerceAtLeast(0))
}

/**
 * Largeurs des tiroirs du direct (dp), bornées à l'écran : TV et tablette gardent 320 / 460 / 480 ;
 * un écran étroit garde une marge pour toucher l'image et refermer.
 */
data class DrawerWidths(val groups: Int, val channels: Int, val channelsBeside: Int, val guide: Int) {
    companion object {
        fun of(widthDp: Int): DrawerWidths {
            val room = widthDp - 24
            val groups = minOf(320, (widthDp * 0.4f).toInt())
            return DrawerWidths(
                groups = groups,
                channels = minOf(460, room),
                channelsBeside = minOf(460, room - groups).coerceAtLeast(160),
                guide = minOf(480, room),
            )
        }
    }
}
