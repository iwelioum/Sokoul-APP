package com.sokoul.desktop.tablet

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.Screen
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.data.SpecialGroup
import com.sokoul.iptv.ui.components.ButtonKind
import com.sokoul.iptv.ui.components.CategoryColumn
import com.sokoul.iptv.ui.components.CategoryItem
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PinDialog
import com.sokoul.iptv.ui.components.ProgressLine
import com.sokoul.iptv.ui.components.ProgressTone
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.rememberNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Groupes du Direct au format de la tablette (Favoris, Tout, puis les groupes visibles, avec leur nombre de chaînes). */
internal fun liveGroups(app: AppState, pid: Long): List<CategoryItem> {
    val favorites = app.db.favoriteIds(pid).size
    val counts = app.db.allChannels(pid).groupingBy { it.categoryId }.eachCount()
    return buildList {
        if (favorites > 0) add(CategoryItem(SpecialGroup.FAVORITES, "Favoris", count = favorites, icon = Icons.Default.Star))
        add(CategoryItem(SpecialGroup.ALL, "Tout", count = counts.values.sum(), icon = Icons.Default.Apps))
        app.db.categories(pid).filter { !it.hidden }.forEach { add(CategoryItem(it.id, it.name, it.locked, counts[it.id])) }
    }
}

/**
 * Direct de la tablette en paysage : titre et colonne des groupes, liste des chaînes (numéro, logo, programme,
 * progression, heure de fin), panneau de la chaîne choisie. Un premier clic choisit, le second (ou « Regarder ») lance.
 */
@Composable
fun TabletLive(app: AppState) {
    val l = LocalLayout.current
    val p = app.playlist ?: run {
        EmptyState(Icons.Default.LiveTv, "Aucune playlist", "Ajoutez un abonnement dans les réglages pour commencer.") {
            SokoulButton("Réglages", { app.go(Screen.SETTINGS) }, kind = ButtonKind.Primary)
        }
        return
    }
    val version = app.dataVersion
    var favorites by remember(p.id, version) { mutableStateOf(app.db.favoriteIds(p.id)) }
    val groups by produceState(emptyList<CategoryItem>(), p.id, version, favorites.size) { value = withContext(Dispatchers.IO) { liveGroups(app, p.id) } }
    var selected by remember(p.id) { mutableStateOf(app.db.setting(Settings.LAST_GROUP) ?: SpecialGroup.ALL) }
    val channels by produceState<List<Channel>?>(null, p.id, selected, version, favorites) { value = withContext(Dispatchers.IO) { app.db.channels(p.id, selected) } }
    val now = rememberNow()
    val nowNext by produceState(emptyMap<String, LiveLogic.NowNext>(), channels, now / 60_000) {
        val list = channels ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val progs = app.db.programs(p.id, list.mapNotNull { it.epgKey }.distinct(), now - 6 * 3600_000L, now + 6 * 3600_000L).groupBy { it.channelKey }
            list.associate { c -> c.id to LiveLogic.nowNext(c.epgKey?.let { progs[it] }.orEmpty(), now) }
        }
    }
    var picked by remember(p.id) { mutableStateOf<Channel?>(null) }
    var menu by remember { mutableStateOf<Channel?>(null) }
    var pinFor by remember { mutableStateOf<CategoryItem?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val list = channels
    fun play(c: Channel) { val all = list ?: return; app.play(all, all.indexOf(c).coerceAtLeast(0)) }
    fun select(c: CategoryItem) {
        if (c.locked && !app.unlocked && app.hasPin()) { pinFor = c; return }
        selected = c.id; picked = null
        app.db.putSetting(Settings.LAST_GROUP, c.id)
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(if (l.widthDp < 840) 180.dp else 210.dp).fillMaxHeight().background(SokoulColors.Surface.copy(alpha = 0.6f))) {
            Text("Direct", Modifier.padding(start = 20.dp, top = 12.dp, end = 12.dp), style = Type.ScreenTitle, maxLines = 1)
            CategoryColumn(groups, selected, ::select, Modifier.weight(1f).fillMaxWidth())
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(Icons.Default.LiveTv, "Aucune chaîne", "Ce groupe est vide. Ajoutez des favoris d'un clic droit sur une chaîne.")
                else -> {
                    val state = rememberLazyListState()
                    LaunchedEffect(selected) { state.scrollToItem(0) }
                    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(list, key = { _, c -> c.id }) { _, c ->
                            ChannelRow(c, nowNext[c.id], c.id in favorites, now, selected = picked?.id == c.id, onClick = {
                                // tablette en paysage : premier appui = choisir, second = regarder
                                if (picked?.id == c.id) play(c) else picked = c
                            }, onLongClick = { menu = c })
                        }
                    }
                }
            }
        }
        DetailsPanel(picked?.let { it to nowNext[it.id] }, now, Modifier.width(340.dp).fillMaxHeight()) { play(it) }
    }

    menu?.let { c ->
        val fav = c.id in favorites
        OptionsDialog(
            c.name,
            listOf(
                MenuOption("Regarder", Icons.Default.PlayArrow) { play(c) },
                MenuOption(if (fav) "Retirer des favoris" else "Ajouter aux favoris", if (fav) Icons.Default.StarBorder else Icons.Default.Star) {
                    if (fav) app.db.removeFavorite(p.id, "LIVE", c.id) else app.db.addFavorite(p.id, "LIVE", c.id, System.currentTimeMillis())
                    favorites = app.db.favoriteIds(p.id)
                },
            ),
            onDismiss = { menu = null },
        )
    }
    pinFor?.let { cat ->
        PinDialog("Code parental", onResult = { pin ->
            if (app.checkPin(pin)) { pinFor = null; pinError = null; selected = cat.id } else pinError = "Code incorrect"
        }, onDismiss = { pinFor = null; pinError = null }, error = pinError)
    }
}

@Composable
private fun ChannelRow(c: Channel, nn: LiveLogic.NowNext?, favorite: Boolean, time: Long, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    FocusSurface(style = FocusStyle.Row, onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth(), color = if (selected) SokoulColors.AccentSoft else Color.Transparent, shape = Radius.Card) { hasFocus ->
        val focused = hasFocus || selected
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.number.toString(), Modifier.width(44.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (focused) SokoulColors.Accent else SokoulColors.TextMuted)
            ChannelLogo(c.logo, c.name, Modifier.size(64.dp, 40.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (favorite) Icon(Icons.Default.Star, null, Modifier.padding(start = 6.dp).size(14.dp), tint = SokoulColors.Warning)
                }
                nn?.now?.let { now ->
                    Text(now.title, style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressLine(nn.progress(time), Modifier.padding(top = 4.dp).fillMaxWidth(0.6f), thickness = 3.dp, onImage = false)
                }
            }
            nn?.now?.let { Text(formatClock(it.end), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted) }
        }
    }
}

/** Panneau de la chaîne choisie : logo, nom, numéro, programme en cours (horaires, progression, résumé), ensuite, Regarder. */
@Composable
private fun DetailsPanel(details: Pair<Channel, LiveLogic.NowNext?>?, time: Long, modifier: Modifier, onWatch: (Channel) -> Unit) {
    Column(modifier.background(Brush.horizontalGradient(listOf(SokoulColors.Surface.copy(alpha = 0.55f), SokoulColors.Surface))).padding(start = 26.dp, end = 30.dp, top = 30.dp, bottom = 20.dp)) {
        val (c, nn) = details ?: run { Text("Choisissez une chaîne", color = SokoulColors.TextMuted); return@Column }
        ChannelLogo(c.logo, c.name, Modifier.size(150.dp, 84.dp))
        Text(c.name, Modifier.padding(top = 14.dp), style = Type.Section, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 6.dp)) {
            Text("N° ${c.number}", Modifier.background(SokoulColors.SurfaceHigher, Radius.Tiny).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextSecondary)
        }
        Spacer(Modifier.height(20.dp))
        val now = nn?.now
        if (now != null) {
            Text("EN CE MOMENT", style = Type.Kicker.copy(color = SokoulColors.Live))
            Text(now.title, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatRange(now.start, now.end), style = Type.Meta)
            ProgressLine(nn.progress(time), Modifier.padding(vertical = 8.dp), tone = ProgressTone.Live, onImage = false)
            now.description?.let { Text(it, color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 7, overflow = TextOverflow.Ellipsis) }
        } else Text("Pas d'information de programme", color = SokoulColors.TextMuted)
        nn?.next?.let { next ->
            Spacer(Modifier.height(18.dp))
            Text("ENSUITE · ${formatClock(next.start)}", style = Type.Overline)
            Text(next.title, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        SokoulButton("Regarder", { onWatch(c) }, Modifier.fillMaxWidth(), Icons.Default.PlayArrow, ButtonKind.Primary)
    }
}
