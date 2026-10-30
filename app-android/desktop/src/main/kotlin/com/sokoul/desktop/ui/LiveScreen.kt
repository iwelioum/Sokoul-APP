package com.sokoul.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.data.SpecialGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Direct en trois colonnes : groupes, chaînes avec le programme en cours, aperçu de la chaîne survolée ou choisie
 * (programme, horaires, suite). Clic = regarder ; clic droit = menu (regarder, favori).
 */
@Composable
fun LiveScreen(app: AppState) {
    LaunchedEffect(Unit) { app.navSolid = true }
    val p = app.playlist ?: run { NoPlaylist(app, "Direct"); return }
    val version = app.dataVersion
    var favorites by remember(p.id, version) { mutableStateOf(app.db.favoriteIds(p.id)) }
    val groups = remember(p.id, version, favorites.isNotEmpty()) { LiveLogic.groups(app.db.categories(p.id), favorites.isNotEmpty()) }
    var selected by remember(p.id) { mutableStateOf(app.db.setting(Settings.LAST_GROUP) ?: groups.firstOrNull()?.id ?: SpecialGroup.ALL) }
    var query by remember { mutableStateOf("") }
    var askPin by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<Channel?>(null) }

    val channels by produceState(emptyList<Channel>(), p.id, selected, query, version, favorites) {
        value = withContext(Dispatchers.IO) {
            val key = LiveLogic.searchKey(query)
            if (key.length >= 2) app.db.searchChannels(p.id, key) else app.db.channels(p.id, selected)
        }
    }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
    val nowNext by produceState(emptyMap<String, LiveLogic.NowNext>(), channels, now) {
        value = withContext(Dispatchers.IO) {
            val keys = channels.mapNotNull { it.epgKey }.distinct()
            val progs = app.db.programs(p.id, keys, now - 6 * 3600_000L, now + 6 * 3600_000L).groupBy { it.channelKey }
            channels.associate { c -> c.id to LiveLogic.nowNext(c.epgKey?.let { progs[it] }.orEmpty(), now) }
        }
    }

    fun select(id: String, locked: Boolean) {
        if (locked && !app.unlocked && app.hasPin()) { askPin = id; return }
        selected = id; query = ""
        app.db.putSetting(Settings.LAST_GROUP, id)
    }

    Row(Modifier.fillMaxSize().background(SokoulColors.Background).padding(top = 0.dp)) {
        // --- groupes ---
        Column(Modifier.width(270.dp).fillMaxHeight().background(SokoulColors.Surface)) {
            Text("Direct", Modifier.padding(start = 24.dp, top = 22.dp), style = Type.ScreenTitle)
            Text(p.name, Modifier.padding(start = 24.dp, bottom = 14.dp), style = Type.Small)
            LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(groups, key = { _, g -> g.id }) { _, g ->
                    GroupRow(g.name, g.locked && !app.unlocked, g.id == selected && query.isBlank()) { select(g.id, g.locked) }
                }
            }
        }
        // --- chaînes ---
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp)) {
            SokoulTextField(query, { query = it }, "Rechercher une chaîne", Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), leading = Icons.Filled.Search)
            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        if (query.isNotBlank()) "Aucune chaîne" else if (app.db.channelCount(p.id) == 0) "Synchronisation…" else "Groupe vide",
                        if (query.isNotBlank()) "Aucune chaîne ne correspond à « $query »."
                        else if (app.db.channelCount(p.id) == 0) "Les chaînes arrivent : suivez la synchronisation dans Réglages."
                        else "Ce groupe ne contient aucune chaîne.",
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(selected) { listState.scrollToItem(0) }
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                        val fav = c.id in favorites
                        ContextMenuArea(items = {
                            listOf(
                                ContextMenuItem("Regarder") { app.play(channels, i) },
                                ContextMenuItem(if (fav) "Retirer des favoris" else "Ajouter aux favoris") {
                                    if (fav) app.db.removeFavorite(p.id, "LIVE", c.id) else app.db.addFavorite(p.id, "LIVE", c.id, System.currentTimeMillis())
                                    favorites = app.db.favoriteIds(p.id)
                                },
                            )
                        }) {
                            ChannelRow(c, nowNext[c.id], now, fav, focus?.id == c.id, onHover = { focus = c }) { app.play(channels, i) }
                        }
                    }
                }
            }
        }
        // --- aperçu ---
        val shown = focus?.takeIf { f -> channels.any { it.id == f.id } } ?: channels.firstOrNull()
        Column(Modifier.width(400.dp).fillMaxHeight().background(SokoulColors.Surface).padding(26.dp)) {
            if (shown != null) Preview(shown, nowNext[shown.id], now, shown.id in favorites, onWatch = { app.play(channels, channels.indexOf(shown)) }) {
                if (shown.id in favorites) app.db.removeFavorite(p.id, "LIVE", shown.id) else app.db.addFavorite(p.id, "LIVE", shown.id, System.currentTimeMillis())
                favorites = app.db.favoriteIds(p.id)
            }
        }
    }

    askPin?.let { id -> PinDialog(app::checkPin, onDismiss = { askPin = null }) { askPin = null; select(id, false) } }
}

@Composable
private fun GroupRow(name: String, locked: Boolean, selected: Boolean, onClick: () -> Unit) {
    val h = rememberHover()
    Row(
        Modifier.fillMaxWidth().background(if (selected) SokoulColors.AccentSoft else if (h.active) SokoulColors.SurfaceHigh else Color.Transparent, Radius.Card)
            .interactive(h, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1f), style = Type.Small.copy(color = if (selected) SokoulColors.TextPrimary else SokoulColors.TextSecondary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (locked) Icon(Icons.Filled.Lock, "Verrouillé", Modifier.size(14.dp), tint = SokoulColors.TextMuted)
    }
}

@Composable
private fun ChannelRow(c: Channel, nn: LiveLogic.NowNext?, now: Long, favorite: Boolean, focused: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val h = rememberHover()
    LaunchedEffect(h.active) { if (h.active) onHover() }
    Row(
        Modifier.fillMaxWidth().background(if (h.active || focused) SokoulColors.SurfaceHigh else Color.Transparent, Radius.Card)
            .interactive(h, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.number.toString(), Modifier.width(48.dp), style = Type.ChannelNumber.copy(color = if (h.active) SokoulColors.Accent else SokoulColors.TextMuted))
        Box(Modifier.size(78.dp, 46.dp).background(SokoulColors.SurfaceHigher, Radius.Card).padding(5.dp), contentAlignment = Alignment.Center) {
            NetImage(c.logo, c.name, Modifier.fillMaxSize(), scale = ContentScale.Fit) {
                Text(c.name.take(3).uppercase(), Modifier.align(Alignment.Center), style = Type.Badge.copy(color = SokoulColors.TextMuted))
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, Modifier.weight(1f, fill = false), style = Type.ItemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (favorite) Icon(Icons.Filled.Star, "Favori", Modifier.padding(start = 6.dp).size(14.dp), tint = SokoulColors.Gold)
            }
            nn?.now?.let { cur ->
                Text(cur.title, style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 5.dp).fillMaxWidth(0.55f).height(3.dp).background(SokoulColors.SurfaceHigher, Radius.Tiny)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(nn.progress(now)).background(SokoulColors.Accent, Radius.Tiny))
                }
            }
        }
        nn?.now?.let { Text(clock(it.end), style = Type.Meta.copy(color = SokoulColors.TextMuted)) }
    }
}

@Composable
private fun Preview(c: Channel, nn: LiveLogic.NowNext?, now: Long, favorite: Boolean, onWatch: () -> Unit, onFavorite: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(170.dp).background(SokoulColors.SurfaceHigh, Radius.Panel).padding(30.dp), contentAlignment = Alignment.Center) {
        NetImage(c.logo, c.name, Modifier.fillMaxSize(), scale = ContentScale.Fit) { Text(c.name, Modifier.align(Alignment.Center), style = Type.Section) }
    }
    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Badge("EN DIRECT", SokoulColors.Live, Color.White)
        Text("  ${c.number} · ${c.name}", style = Type.ItemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val cur = nn?.now
    if (cur != null) {
        Text(cur.title, Modifier.padding(top = 14.dp), style = Type.Section, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(clock(cur.start), style = Type.Meta)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(4.dp).background(SokoulColors.SurfaceHigher, Radius.Tiny)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(nn.progress(now)).background(SokoulColors.Live, Radius.Tiny))
            }
            Text(clock(cur.end), style = Type.Meta)
        }
        cur.description?.let { Text(it, Modifier.padding(top = 12.dp), style = Type.Body, maxLines = 6, overflow = TextOverflow.Ellipsis) }
    } else Text("Programme non communiqué par le fournisseur.", Modifier.padding(top = 14.dp), style = Type.Body)
    nn?.next?.let { n ->
        Text("ENSUITE", Modifier.padding(top = 20.dp), style = Type.Overline)
        Text("${clock(n.start)}  ${n.title}", Modifier.padding(top = 4.dp), style = Type.Small.copy(color = SokoulColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SokoulButton("Regarder", onWatch, style = ButtonStyle.Primary, icon = Icons.Filled.PlayArrow)
        SokoulButton(if (favorite) "Favori" else "Ajouter aux favoris", onFavorite, style = ButtonStyle.Glass, icon = if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline)
    }
}
