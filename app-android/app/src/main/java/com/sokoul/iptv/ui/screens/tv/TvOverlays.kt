package com.sokoul.iptv.ui.screens.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.GlassPanel
import com.sokoul.iptv.ui.screens.player.LiveGroup
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.formatClock

/**
 * Bandeau de zapping : la chaîne visée s'affiche dès l'appui. La jauge du bas se remplit pendant
 * [ZAP_SETTLE_MS] : l'utilisateur voit que l'appui est pris en compte avant l'ouverture du flux.
 */
@Composable
fun ZapBanner(c: ChannelEntity, number: String?, modifier: Modifier = Modifier) {
    val fill = remember(c.id, number) { Animatable(0f) }
    LaunchedEffect(c.id, number) {
        fill.animateTo(1f, tween(if (number != null) NUMBER_SETTLE_MS.toInt() else ZAP_SETTLE_MS.toInt(), easing = LinearEasing))
    }
    GlassPanel(modifier.widthIn(min = 360.dp, max = 560.dp)) {
        Column {
            Row(Modifier.padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    number ?: c.number.toString(), Modifier.widthIn(min = 64.dp),
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (number != null) SokoulColors.Accent else SokoulColors.TextPrimary,
                )
                ChannelLogo(c.logo, c.name, Modifier.padding(start = Space.s).size(84.dp, 50.dp))
                Text(
                    c.name, Modifier.padding(start = Space.m).weight(1f, fill = false),
                    style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // jauge de 2 dp : appui pris en compte, ouverture imminente
            Box(Modifier.fillMaxWidth().height(2.dp).background(SokoulColors.white(0.12f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth().graphicsLayer {
                    scaleX = fill.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }.background(SokoulColors.Accent))
            }
        }
    }
}

/** Liste des chaînes du groupe en cours, en surimpression (OK pour l'ouvrir, ◀ pour les groupes). */
@Composable
fun ChannelOverlay(
    groupName: String,
    channels: List<ChannelEntity>,
    highlighted: Int,
    currentId: String?,
    now: Map<String, ProgramEntity>,
    modifier: Modifier = Modifier,
    onPick: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(highlighted) { keepVisible(state, highlighted) }
    GlassPanel(modifier.fillMaxHeight().width(440.dp).padding(vertical = Space.l)) {
        Column(Modifier.padding(top = Space.m)) {
            PanelHeader(groupName, "${channels.size} chaînes", "◀ groupes · ▶ guide · OK regarder")
            LazyColumn(state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.m)) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                    val hl = i == highlighted
                    PanelRow(hl, Modifier.pointerInput(i) { detectTapGestures { onPick(i) } }) {
                        Text(c.number.toString(), Modifier.width(44.dp), color = if (hl) SokoulColors.Accent else SokoulColors.TextMuted, style = MaterialTheme.typography.titleSmall)
                        ChannelLogo(c.logo, c.name, Modifier.size(52.dp, 32.dp))
                        Column(Modifier.padding(start = Space.m).weight(1f)) {
                            Text(
                                c.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (c.id == currentId || hl) FontWeight.Bold else FontWeight.Normal,
                            )
                            now[c.id]?.let { p -> Text(p.title, style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (c.id == currentId) Icon(Icons.Default.PlayArrow, "En cours", Modifier.size(18.dp), tint = SokoulColors.Accent)
                    }
                }
            }
        }
    }
}

/** Groupes de chaînes : favoris en tête, puis toutes les chaînes, puis ceux du fournisseur. */
@Composable
fun GroupsPanel(groups: List<LiveGroup>, highlighted: Int, currentId: String?, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(highlighted) { keepVisible(state, highlighted) }
    GlassPanel(modifier.fillMaxHeight().width(340.dp).padding(vertical = Space.l)) {
        Column(Modifier.padding(top = Space.m)) {
            PanelHeader("Groupes", "${groups.size}", "OK ouvrir · ▶ chaînes")
            LazyColumn(state = state) {
                itemsIndexed(groups, key = { _, g -> g.id }) { i, g ->
                    val hl = i == highlighted
                    PanelRow(hl, Modifier.pointerInput(i) { detectTapGestures { onPick(i) } }) {
                        if (g.id == SpecialCategory.FAVORITES) Icon(Icons.Default.Star, null, Modifier.padding(end = Space.s).size(18.dp), tint = SokoulColors.Warning)
                        Text(
                            g.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (g.id == currentId || hl) FontWeight.Bold else FontWeight.Normal,
                            color = if (g.id == currentId && !hl) SokoulColors.Accent else SokoulColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

/** Mini-guide (▶) : la suite des programmes de la chaîne regardée. */
@Composable
fun MiniGuide(c: ChannelEntity, programs: List<ProgramEntity>?, now: Long, modifier: Modifier = Modifier) {
    GlassPanel(modifier.fillMaxHeight().width(420.dp).padding(vertical = Space.l)) {
        Column(Modifier.padding(top = Space.m)) {
            PanelHeader(c.name, "Programme", "◀ fermer · Menu guide complet")
            when {
                programs == null -> Text("Chargement…", Modifier.padding(Space.l), color = SokoulColors.TextMuted)
                programs.isEmpty() -> Text("Pas d'information de programme pour cette chaîne.", Modifier.padding(Space.l), color = SokoulColors.TextMuted)
                else -> Column(Modifier.padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    programs.forEach { p ->
                        val live = p.start <= now && now < p.end
                        Column {
                            Text(
                                if (live) "EN CE MOMENT · ${formatClock(p.start)} – ${formatClock(p.end)}" else "${formatClock(p.start)} – ${formatClock(p.end)}",
                                style = Type.Overline, color = if (live) SokoulColors.Accent else SokoulColors.TextMuted,
                            )
                            Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (live) {
                                Box(Modifier.padding(top = Space.xs).fillMaxWidth().height(3.dp).background(SokoulColors.white(0.15f), Radius.Tiny)) {
                                    val f = ((now - p.start).toFloat() / (p.end - p.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(SokoulColors.Accent, Radius.Tiny))
                                }
                                p.description?.let { Text(it, Modifier.padding(top = Space.xs), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, count: String, hint: String) {
    Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
        Text(title, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count  ·  $hint", style = Type.Meta, color = SokoulColors.TextMuted, maxLines = 1)
    }
}

/** Ligne de panneau, même langage que les listes de l'app (style « Row » : fond teinté + barre d'accent). */
@Composable
private fun PanelRow(highlighted: Boolean, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = 1.dp)
            .background(if (highlighted) SokoulColors.SurfaceHigher else Color.Transparent, Radius.Card)
            .then(if (highlighted) Modifier.background(SokoulColors.AccentTint, Radius.Card) else Modifier)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private suspend fun keepVisible(state: androidx.compose.foundation.lazy.LazyListState, i: Int) {
    val visible = state.layoutInfo.visibleItemsInfo
    val first = visible.firstOrNull()?.index ?: 0
    val last = visible.lastOrNull()?.index ?: 0
    if (i <= first || i >= last) state.scrollToItem((i - 4).coerceAtLeast(0))
}
