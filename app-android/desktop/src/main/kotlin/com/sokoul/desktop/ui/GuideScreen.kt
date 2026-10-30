package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.data.Program
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.data.SpecialGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DP_PER_MIN = 5.dp
private const val HOURS = 24

/** Grille du guide : chaînes du groupe courant × 24 h à partir du jour choisi ; détail d'un programme en haut. */
@Composable
fun GuideScreen(app: AppState) {
    val p = app.playlist ?: return
    val now = remember { System.currentTimeMillis() }
    var dayOffset by remember { mutableStateOf(0) }
    val group = app.db.setting(Settings.LAST_GROUP)?.takeIf { it != SpecialGroup.FAVORITES || app.db.favoriteIds(p.id).isNotEmpty() } ?: SpecialGroup.ALL
    val from = remember(dayOffset) { if (dayOffset == 0) LiveLogic.guideStart(now) else startOfDay(now) + dayOffset * 24 * 3600_000L }
    val to = from + HOURS * 3600_000L
    val channels by produceState(emptyList<Channel>(), p.id, group, app.dataVersion) {
        value = withContext(Dispatchers.IO) { app.db.channels(p.id, group).take(300) }
    }
    val programs by produceState(emptyMap<String, List<Program>>(), channels, from) {
        value = withContext(Dispatchers.IO) { app.db.programs(p.id, channels.mapNotNull { it.epgKey }.distinct(), from, to).groupBy { it.channelKey } }
    }
    var picked by remember { mutableStateOf<Pair<Channel, Program>?>(null) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(from) {
        // maintenant (ou 20 h pour un autre jour) au début de l'écran
        val target = if (dayOffset == 0) (now - from) / 60_000L - 30 else 20 * 60L
        scroll.scrollTo(with(density) { (DP_PER_MIN * target.toInt().coerceAtLeast(0)).roundToPx() })
    }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, end = 20.dp)) {
        // --- en-tête : programme choisi, jours ---
        val sel = picked
        Column(Modifier.fillMaxWidth().height(110.dp)) {
            if (sel == null) ScreenTitle("Guide TV", "Cliquez sur un programme pour voir son détail")
            else {
                val (c, pr) = sel
                Text("${c.name.uppercase()} · ${dayLabel(pr.start)} ${clock(pr.start)} – ${clock(pr.end)}", style = MaterialTheme.typography.labelMedium, color = SokoulColors.Accent)
                Text(pr.title, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                pr.description?.let { Text(it, Modifier.padding(top = 4.dp), color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            (0..6).forEach { d ->
                SokoulButton(dayLabel(startOfDay(now) + d * 24 * 3600_000L + 3600_000L, now), { dayOffset = d; picked = null }, primary = d == dayOffset)
            }
            if (sel != null && sel.second.start <= System.currentTimeMillis() && sel.second.end > System.currentTimeMillis()) {
                SokoulButton("Regarder", { app.play(channels, channels.indexOf(sel.first)) })
            }
        }
        // --- règle des heures ---
        Row {
            Box(Modifier.width(220.dp))
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(DP_PER_MIN * (HOURS * 60)).height(28.dp)) {
                    (0 until HOURS * 2).forEach { i ->
                        val t = from + i * 30 * 60_000L
                        Text(clock(t), Modifier.offset(x = DP_PER_MIN * (i * 30)).padding(start = 4.dp), color = if (i % 2 == 0) SokoulColors.TextPrimary else SokoulColors.TextMuted, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Aucune chaîne dans ce groupe (choisissez un groupe dans Direct)", color = SokoulColors.TextMuted) }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                Row(Modifier.height(58.dp)) {
                    Row(
                        Modifier.width(220.dp).fillMaxHeight().background(SokoulColors.Surface, Radius.Card).clickable { app.play(channels, i) }
                            .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NetImage(c.logo, c.name, Modifier.size(56.dp, 34.dp))
                        Text(c.name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        Box(Modifier.width(DP_PER_MIN * (HOURS * 60)).fillMaxHeight()) {
                            val list = c.epgKey?.let { programs[it] }.orEmpty()
                            if (list.isEmpty()) {
                                Text("Pas d'information", Modifier.padding(start = 12.dp).align(Alignment.CenterStart), color = SokoulColors.TextMuted)
                            }
                            list.forEach { pr ->
                                val (s, e) = LiveLogic.span(pr, from, to)
                                if (e <= s) return@forEach
                                val x = DP_PER_MIN * ((s - from) / 60_000L).toInt()
                                val w = DP_PER_MIN * ((e - s) / 60_000L).toInt().coerceAtLeast(1)
                                val live = pr.start <= System.currentTimeMillis() && pr.end > System.currentTimeMillis()
                                val isPicked = picked?.second == pr && picked?.first?.id == c.id
                                Column(
                                    Modifier.offset(x = x).width(w - 2.dp).fillMaxHeight()
                                        .background(if (isPicked) SokoulColors.AccentSoft else if (live) SokoulColors.SurfaceHigher else SokoulColors.SurfaceHigh, Radius.Tiny)
                                        .then(if (isPicked) Modifier.border(1.dp, SokoulColors.Accent, Radius.Tiny) else Modifier)
                                        .clickable { picked = c to pr }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(pr.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${clock(pr.start)} – ${clock(pr.end)}", style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted, maxLines = 1)
                                }
                            }
                            // repère de l'heure actuelle
                            val t = System.currentTimeMillis()
                            if (t in from until to) Box(Modifier.offset(x = DP_PER_MIN * ((t - from) / 60_000L).toInt()).width(2.dp).fillMaxHeight().background(SokoulColors.Accent))
                        }
                    }
                }
            }
        }
    }
}

private fun startOfDay(t: Long): Long {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
