package com.sokoul.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LiveScreen(app: AppState) {
    val p = app.playlist ?: return
    val version = app.dataVersion
    var favorites by remember(p.id, version) { mutableStateOf(app.db.favoriteIds(p.id)) }
    val groups = remember(p.id, version, favorites.isNotEmpty()) { LiveLogic.groups(app.db.categories(p.id), favorites.isNotEmpty()) }
    var selected by remember(p.id) { mutableStateOf(app.db.setting(Settings.LAST_GROUP) ?: groups.firstOrNull()?.id ?: SpecialGroup.ALL) }
    var query by remember { mutableStateOf("") }
    var askPin by remember { mutableStateOf<String?>(null) }

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

    Row(Modifier.fillMaxSize()) {
        // --- groupes ---
        Column(Modifier.width(260.dp).fillMaxHeight().background(SokoulColors.Surface.copy(alpha = 0.6f))) {
            ScreenTitle("Direct", p.name, Modifier.padding(start = 20.dp, top = 22.dp, end = 12.dp, bottom = 10.dp))
            val groupState = rememberLazyListState()
            LazyColumn(state = groupState, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(groups, key = { _, g -> g.id }) { _, g ->
                    GroupRow(g.name, g.locked && !app.unlocked, g.id == selected && query.isBlank()) { select(g.id, g.locked) }
                }
            }
        }
        // --- chaînes ---
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp)) {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), singleLine = true,
                placeholder = { Text("Rechercher une chaîne") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            )
            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isNotBlank()) "Aucune chaîne ne correspond à « $query »"
                        else if (app.db.channelCount(p.id) == 0) "Synchronisation des chaînes en cours… (Réglages pour suivre)"
                        else "Ce groupe est vide",
                        color = SokoulColors.TextMuted,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(selected) { listState.scrollToItem(0) }
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            ChannelRow(c, nowNext[c.id], now, fav) { app.play(channels, i) }
                        }
                    }
                }
            }
        }
    }

    askPin?.let { id ->
        PinDialog(app::checkPin, onDismiss = { askPin = null }) { askPin = null; select(id, false) }
    }
}

@Composable
private fun GroupRow(name: String, locked: Boolean, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().background(if (selected) SokoulColors.AccentSoft else if (hovered) SokoulColors.SurfaceHigh else SokoulColors.Surface.copy(alpha = 0f), Radius.Card)
            .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1f), color = if (selected) SokoulColors.Accent else SokoulColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (locked) Icon(Icons.Default.Lock, "Verrouillé", Modifier.size(14.dp), tint = SokoulColors.TextMuted)
    }
}

@Composable
private fun ChannelRow(c: Channel, nn: LiveLogic.NowNext?, now: Long, favorite: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().background(if (hovered) SokoulColors.SurfaceHigh else SokoulColors.Background, Radius.Card)
            .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.number.toString(), Modifier.width(52.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (hovered) SokoulColors.Accent else SokoulColors.TextMuted)
        Box(Modifier.size(72.dp, 44.dp).background(SokoulColors.SurfaceHigh, Radius.Card).padding(4.dp), contentAlignment = Alignment.Center) {
            NetImage(c.logo, c.name, Modifier.fillMaxSize()) {
                Text(c.name.take(3), Modifier.align(Alignment.Center), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (favorite) Icon(Icons.Default.Star, "Favori", Modifier.padding(start = 6.dp).size(14.dp), tint = SokoulColors.Warning)
            }
            val cur: Program? = nn?.now
            if (cur != null) {
                Text(cur.title, style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { nn.progress(now) }, Modifier.padding(top = 4.dp).fillMaxWidth(0.5f).height(3.dp),
                    color = SokoulColors.Accent, trackColor = SokoulColors.SurfaceHigher,
                )
            }
        }
        nn?.now?.let { Text(clock(it.end), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted) }
    }
}
