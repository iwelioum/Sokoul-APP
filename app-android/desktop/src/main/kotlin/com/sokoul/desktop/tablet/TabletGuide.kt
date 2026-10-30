package com.sokoul.desktop.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.Screen
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.Program
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.data.SpecialGroup
import com.sokoul.iptv.ui.components.ButtonKind
import com.sokoul.iptv.ui.components.CategoryItem
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.ProgressLine
import com.sokoul.iptv.ui.components.ProgressTone
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.rememberNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val HALF_HOUR = 30 * 60_000L
private const val VISIBLE_MIN = 165
private fun align(t: Long) = t - Math.floorMod(t, HALF_HOUR)
private fun dayStart(t: Long): Long = Calendar.getInstance().run {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); timeInMillis
}

/**
 * Guide TV de la tablette : titre et heure, pastilles de groupes, « Maintenant » et jours, réglette des heures
 * (heures pleines à empattements), grille des chaînes × programmes, trait de l'heure actuelle. En-tête remplacé par le
 * programme choisi (clic). Molette horizontale ou Maj + molette : avancer ou reculer dans le temps.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TabletGuide(app: AppState) {
    val l = LocalLayout.current
    val p = app.playlist ?: run {
        EmptyState(Icons.Default.LiveTv, "Aucune playlist", "Ajoutez un abonnement dans les réglages pour commencer.") {
            SokoulButton("Réglages", { app.go(Screen.SETTINGS) }, kind = ButtonKind.Primary)
        }
        return
    }
    val now = rememberNow()
    var windowStart by remember { mutableLongStateOf(align(System.currentTimeMillis()) - HALF_HOUR) }
    val windowEnd = windowStart + VISIBLE_MIN * 60_000L
    val groups by produceState(emptyList<CategoryItem>(), p.id, app.dataVersion) { value = withContext(Dispatchers.IO) { liveGroups(app, p.id) } }
    var selected by remember(p.id) { mutableStateOf(app.db.setting(Settings.LAST_GROUP) ?: SpecialGroup.ALL) }
    val channels by produceState<List<Channel>?>(null, p.id, selected, app.dataVersion) { value = withContext(Dispatchers.IO) { app.db.channels(p.id, selected).take(400) } }
    val programs by produceState(emptyMap<String, List<Program>>(), channels, windowStart) {
        val list = channels ?: return@produceState
        value = withContext(Dispatchers.IO) { app.db.programs(p.id, list.mapNotNull { it.epgKey }.distinct(), windowStart - 4 * 3600_000L, windowEnd + 3600_000L).groupBy { it.channelKey } }
    }
    var picked by remember { mutableStateOf<Pair<Channel, Program>?>(null) }
    val side = l.margin
    val days = remember(now / 86_400_000L) { (0 until 7).map { dayStart(System.currentTimeMillis()) + it * 86_400_000L } }

    Column(Modifier.fillMaxSize()) {
        // en-tête : « Guide TV » et l'heure, ou le programme choisi
        Row(Modifier.fillMaxWidth().height(if (picked == null) 120.dp else 150.dp).padding(start = side, end = side, top = 22.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                val sel = picked
                if (sel == null) Text("Guide TV", style = Type.ScreenTitle)
                else {
                    val (c, pr) = sel
                    val live = pr.start <= now && pr.end > now
                    Text((if (live) "EN CE MOMENT" else dayLabel(pr.start, now).uppercase()) + " · " + c.name.uppercase(), style = Type.Kicker.copy(color = if (live) SokoulColors.Live else SokoulColors.Accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pr.title, Modifier.padding(top = 4.dp), style = Type.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatRange(pr.start, pr.end) + "  ·  " + "${(pr.end - pr.start) / 60_000} min", style = Type.Meta)
                        if (live) ProgressLine(((now - pr.start).toFloat() / (pr.end - pr.start)).coerceIn(0f, 1f), Modifier.padding(start = 14.dp).width(120.dp), tone = ProgressTone.Live, thickness = 3.dp, onImage = false)
                    }
                    pr.description?.let { Text(it, Modifier.padding(top = 6.dp).widthIn(max = 760.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatClock(now), style = Type.Section)
                picked?.let { (c, pr) ->
                    if (pr.start <= now && pr.end > now) SokoulButton("Regarder", { channels?.let { list -> app.play(list, list.indexOf(c).coerceAtLeast(0)) } }, Modifier.padding(top = 10.dp), Icons.Default.PlayArrow, ButtonKind.Primary)
                }
            }
        }
        // groupes
        LazyRow(contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups, key = { it.id }) { g ->
                GuideChip(g.name, g.id == selected) {
                    if (g.locked && !app.unlocked && app.hasPin()) return@GuideChip
                    selected = g.id; picked = null; app.db.putSetting(Settings.LAST_GROUP, g.id)
                }
            }
        }
        // Maintenant et jours
        Row(Modifier.padding(start = side, top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GuideChip("Maintenant", false, Icons.Default.Schedule) { windowStart = align(System.currentTimeMillis()) - HALF_HOUR }
            Box(Modifier.width(1.dp).height(24.dp).background(SokoulColors.white(0.12f)))
            days.forEachIndexed { i, d ->
                val shown = windowStart + HALF_HOUR in d until d + 86_400_000L
                GuideChip(if (i == 0) "Aujourd'hui" else dayLabel(d + 3600_000L, now), shown) {
                    windowStart = if (i == 0) align(System.currentTimeMillis()) - HALF_HOUR else d + 19 * 3600_000L + HALF_HOUR
                }
            }
        }
        val list = channels
        if (list != null && list.isEmpty()) { EmptyState(Icons.Default.LiveTv, "Aucune chaîne", "Ce groupe est vide."); return@Column }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(start = side).onPointerEvent(PointerEventType.Scroll) { e ->
                val c = e.changes.firstOrNull() ?: return@onPointerEvent
                // molette horizontale (ou Maj + molette) : le temps défile par demi-heures
                val dx = if (c.scrollDelta.x != 0f) c.scrollDelta.x else if ((e.nativeEvent as? java.awt.event.InputEvent)?.isShiftDown == true) c.scrollDelta.y else 0f
                if (dx != 0f) { windowStart += (if (dx > 0) 1 else -1) * HALF_HOUR; c.consume() }
            },
        ) {
            val channelCol = if (maxWidth < 900.dp) 170.dp else 220.dp
            val dpPerMin = (maxWidth - channelCol - 8.dp) / VISIBLE_MIN
            Column {
                TimeRuler(windowStart, channelCol, dpPerMin, now)
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(list.orEmpty(), key = { _, c -> c.id }) { i, c ->
                        GuideRow(
                            c, c.epgKey?.let { programs[it] }.orEmpty(), windowStart, windowEnd, channelCol, dpPerMin, now,
                            selectedRow = picked?.first?.id == c.id,
                            pickedStart = picked?.takeIf { it.first.id == c.id }?.second?.start,
                            onChannelClick = { app.play(list!!, i) },
                            onProgramClick = { pr -> if (pr == null) app.play(list!!, i) else if (picked?.second == pr && pr.start <= now && pr.end > now) app.play(list!!, i) else picked = c to pr },
                        )
                    }
                }
            }
        }
    }
}

/** Pastille de la barre du guide : teintée quand elle est le jour affiché, blanche au survol. */
@Composable
private fun GuideChip(text: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, style = FocusStyle.Chip, color = if (selected) SokoulColors.AccentTint else SokoulColors.white(0.06f)) { focused ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            val fg = when { focused -> SokoulColors.OnLight; selected -> SokoulColors.Accent; else -> SokoulColors.TextPrimary }
            if (icon != null) Icon(icon, null, Modifier.padding(end = 6.dp).size(16.dp), tint = fg)
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = fg, maxLines = 1)
        }
    }
}

/** Réglette des heures : heures pleines à empattements, demi-heures discrètes, trait « maintenant ». */
@Composable
private fun TimeRuler(windowStart: Long, channelCol: Dp, dpPerMin: Dp, now: Long) {
    Box(Modifier.fillMaxWidth().height(34.dp).clipToBounds()) {
        var t = align(windowStart)
        while (t < windowStart + VISIBLE_MIN * 60_000L) {
            if (t >= windowStart) {
                val x = channelCol + 3.dp + dpPerMin * ((t - windowStart) / 60_000f)
                val fullHour = Math.floorMod(t, 2 * HALF_HOUR) == 0L
                Box(Modifier.offset(x = x).align(Alignment.BottomStart).width(1.dp).height(if (fullHour) 12.dp else 7.dp).background(SokoulColors.white(0.28f)))
                Text(
                    formatClock(t), Modifier.offset(x = x + 7.dp).align(Alignment.BottomStart).padding(bottom = 2.dp),
                    style = if (fullHour) Type.Tab else MaterialTheme.typography.labelLarge, color = if (fullHour) SokoulColors.TextPrimary else SokoulColors.TextMuted, maxLines = 1,
                )
            }
            t += HALF_HOUR
        }
        if (now in windowStart until windowStart + VISIBLE_MIN * 60_000L) {
            val x = channelCol + 3.dp + dpPerMin * ((now - windowStart) / 60_000f)
            Box(Modifier.offset(x = x - 1.dp).align(Alignment.BottomStart).width(2.dp).height(34.dp).background(SokoulColors.Accent))
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel, programs: List<Program>, windowStart: Long, windowEnd: Long, channelCol: Dp, dpPerMin: Dp, now: Long,
    selectedRow: Boolean, pickedStart: Long?, onChannelClick: () -> Unit, onProgramClick: (Program?) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(78.dp).padding(vertical = 2.dp)) {
        Box(Modifier.width(channelCol).fillMaxHeight().background(if (selectedRow) SokoulColors.SurfaceHigher else SokoulColors.Surface.copy(alpha = 0.6f), Radius.Card).dpadClickable(onChannelClick)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (channelCol > 150.dp) Text(channel.number.toString(), Modifier.width(44.dp), color = if (selectedRow) SokoulColors.TextPrimary else SokoulColors.TextMuted, style = MaterialTheme.typography.labelLarge)
                ChannelLogo(channel.logo, channel.name, Modifier.size(56.dp, 36.dp))
                if (channelCol > 150.dp) Text(
                    channel.name, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selectedRow) FontWeight.Bold else FontWeight.Medium, color = if (selectedRow) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 3.dp).clipToBounds()) {
            val visible = programs.filter { it.end > windowStart && it.start < windowEnd }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(end = 2.dp).background(SokoulColors.Surface, Radius.Tiny).dpadClickable({ onProgramClick(null) }).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Pas d'information · cliquez pour regarder", color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            visible.forEach { p ->
                val start = maxOf(p.start, windowStart)
                val end = minOf(p.end, windowEnd)
                val x = dpPerMin * ((start - windowStart) / 60_000f)
                val w = dpPerMin * ((end - start) / 60_000f)
                val live = now >= p.start && now < p.end
                val past = p.end <= now
                val picked = pickedStart == p.start
                FocusSurface(
                    onClick = { onProgramClick(p) }, style = FocusStyle.Chip, shape = Radius.Tiny,
                    modifier = Modifier.offset(x = x).width((w - 3.dp).coerceAtLeast(1.dp)).fillMaxHeight(),
                    color = when { picked -> SokoulColors.AccentSoft; live -> SokoulColors.SurfaceHigher; past -> SokoulColors.Surface; else -> SokoulColors.SurfaceHigh },
                ) { focused ->
                    val fg = when { focused -> SokoulColors.OnLight; past -> SokoulColors.TextSecondary; else -> SokoulColors.TextPrimary }
                    Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Column {
                            Text(p.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg)
                            Text(formatRange(p.start, p.end), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (focused) SokoulColors.OnLight.copy(alpha = 0.7f) else SokoulColors.TextMuted)
                        }
                        if (live) {
                            val f = ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f)
                            Box(Modifier.align(Alignment.BottomStart).offset(y = 5.dp).fillMaxWidth(f).height(2.dp).background(if (focused) SokoulColors.Live else SokoulColors.Accent))
                        }
                    }
                }
            }
            if (now in windowStart until windowEnd) {
                val x = dpPerMin * ((now - windowStart) / 60_000f)
                Box(Modifier.offset(x = x - 1.dp).width(2.dp).fillMaxHeight().background(SokoulColors.Accent.copy(alpha = 0.85f)))
            }
        }
    }
}
