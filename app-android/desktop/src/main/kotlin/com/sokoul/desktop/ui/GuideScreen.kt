package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.Screen
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.data.Program
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.data.SpecialGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DP_PER_MIN = 5.dp
private const val HOURS = 24
private val CHANNEL_COL = 232.dp

/** Grille du guide : chaînes du groupe courant × 24 h à partir du jour choisi ; programme choisi en en-tête. */
@Composable
fun GuideScreen(app: AppState) {
    LaunchedEffect(Unit) { app.navSolid = true }
    val p = app.playlist ?: run { NoPlaylist(app, "Guide TV"); return }
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

    Column(Modifier.fillMaxSize().background(SokoulColors.Background).padding(start = Space.Gutter, top = 96.dp, end = Space.Gutter)) {
        // --- en-tête : programme choisi ---
        val sel = picked
        Row(Modifier.fillMaxWidth().height(132.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (sel == null) {
                    Text("Guide TV", style = Type.ScreenTitle)
                    Text("Cliquez sur un programme pour voir son détail, sur une chaîne pour la regarder.", Modifier.padding(top = 6.dp), style = Type.Small)
                } else {
                    val (c, pr) = sel
                    Kicker("${c.name} · ${dayLabel(pr.start)} ${clock(pr.start)} – ${clock(pr.end)}")
                    Text(pr.title, Modifier.padding(top = 6.dp), style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    pr.description?.let { Text(it, Modifier.padding(top = 6.dp).widthIn(max = 900.dp), style = Type.Body, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (sel != null && sel.second.start <= System.currentTimeMillis() && sel.second.end > System.currentTimeMillis()) {
                SokoulButton("Regarder", { app.play(channels, channels.indexOf(sel.first)) }, style = ButtonStyle.Primary, icon = Icons.Filled.PlayArrow)
            }
        }
        Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..6).forEach { d -> Chip(dayLabel(startOfDay(now) + d * 24 * 3600_000L + 3600_000L, now), d == dayOffset, { dayOffset = d; picked = null }) }
        }
        // --- règle des heures ---
        Row {
            Box(Modifier.width(CHANNEL_COL))
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(DP_PER_MIN * (HOURS * 60)).height(28.dp)) {
                    (0 until HOURS * 2).forEach { i ->
                        val t = from + i * 30 * 60_000L
                        Text(clock(t), Modifier.offset(x = DP_PER_MIN * (i * 30)).padding(start = 6.dp), style = Type.Meta.copy(color = if (i % 2 == 0) SokoulColors.TextPrimary else SokoulColors.TextMuted))
                    }
                }
            }
        }
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("Aucune chaîne", "Ce groupe est vide : choisissez un autre groupe dans Direct.") }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                Row(Modifier.height(64.dp)) {
                    val h = rememberHover()
                    Row(
                        Modifier.width(CHANNEL_COL).fillMaxHeight().background(if (h.active) SokoulColors.SurfaceHigher else SokoulColors.Surface, Radius.Card)
                            .interactive(h) { app.play(channels, i) }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.number.toString(), Modifier.width(34.dp), style = Type.ChannelNumber.copy(color = SokoulColors.TextMuted))
                        NetImage(c.logo, c.name, Modifier.size(54.dp, 34.dp), scale = androidx.compose.ui.layout.ContentScale.Fit)
                        Text(c.name, Modifier.padding(start = 10.dp), style = Type.Small.copy(color = SokoulColors.TextPrimary), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        Box(Modifier.width(DP_PER_MIN * (HOURS * 60)).fillMaxHeight().padding(start = 4.dp)) {
                            val list = c.epgKey?.let { programs[it] }.orEmpty()
                            if (list.isEmpty()) Text("Pas d'information", Modifier.padding(start = 12.dp).align(Alignment.CenterStart), style = Type.Small.copy(color = SokoulColors.TextMuted))
                            list.forEach { pr ->
                                val (s, e) = LiveLogic.span(pr, from, to)
                                if (e <= s) return@forEach
                                val x = DP_PER_MIN * ((s - from) / 60_000L).toInt()
                                val w = DP_PER_MIN * ((e - s) / 60_000L).toInt().coerceAtLeast(1)
                                val live = pr.start <= System.currentTimeMillis() && pr.end > System.currentTimeMillis()
                                val isPicked = picked?.second == pr && picked?.first?.id == c.id
                                val ph = rememberHover()
                                Column(
                                    Modifier.offset(x = x).width(w - 3.dp).fillMaxHeight()
                                        .background(
                                            when { isPicked -> SokoulColors.AccentSoft; ph.active -> SokoulColors.SurfaceHigher; live -> SokoulColors.SurfaceHigh; else -> SokoulColors.Surface },
                                            Radius.Card,
                                        )
                                        .then(if (isPicked) Modifier.border(Edge.Hairline, SokoulColors.Accent, Radius.Card) else Modifier)
                                        .interactive(ph) { picked = c to pr }.padding(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    Text(pr.title, style = Type.Small.copy(color = SokoulColors.TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${clock(pr.start)} – ${clock(pr.end)}", style = Type.Small.copy(color = if (live) SokoulColors.Live else SokoulColors.TextMuted), maxLines = 1)
                                }
                            }
                            // repère de l'heure actuelle
                            val t = System.currentTimeMillis()
                            if (t in from until to) Box(Modifier.offset(x = DP_PER_MIN * ((t - from) / 60_000L).toInt()).width(2.dp).fillMaxHeight().background(SokoulColors.Live))
                        }
                    }
                }
            }
        }
    }
}

/** Écran Direct ou Guide sans playlist : explication et accès à l'ajout. */
@Composable
fun NoPlaylist(app: AppState, title: String) {
    Box(Modifier.fillMaxSize().background(SokoulColors.Background), contentAlignment = Alignment.Center) {
        EmptyState(
            title, "Ajoutez votre abonnement IPTV (Xtream Codes ou lien M3U), ou importez la configuration de votre tablette, pour retrouver vos chaînes et le guide.",
            action = "Ajouter une playlist", onAction = { app.go(Screen.SETTINGS) },
        )
    }
}

private fun startOfDay(t: Long): Long {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
