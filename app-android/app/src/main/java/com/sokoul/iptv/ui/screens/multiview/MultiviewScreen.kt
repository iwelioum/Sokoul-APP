package com.sokoul.iptv.ui.screens.multiview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.data.repo.StreamResolver
import com.sokoul.iptv.player.PlayerFactory
import com.sokoul.iptv.player.StreamPlayer
import com.sokoul.iptv.player.StreamState
import com.sokoul.iptv.ui.MultiviewSession
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.theme.SokoulColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SlotState(val channel: ChannelEntity?, val player: StreamPlayer?)

@HiltViewModel
class MultiviewViewModel @Inject constructor(
    private val session: MultiviewSession,
    private val factory: PlayerFactory,
    private val settings: SettingsStore,
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val resolver: StreamResolver,
) : ViewModel() {
    private val _slots = MutableStateFlow(List(4) { SlotState(null, null) })
    val slots: StateFlow<List<SlotState>> = _slots.asStateFlow()
    val channels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    private var audioSlot = 0

    init {
        viewModelScope.launch {
            val p = playlists.activeNow() ?: return@launch
            channels.value = (catalog.channelList(p.id, SpecialCategory.FAVORITES) + catalog.channelList(p.id, SpecialCategory.ALL)).distinctBy { it.id }
            session.slots.value.forEachIndexed { i, s -> if (s != null) catalog.channel(s.playlistId, s.channelId)?.let { assign(i, it) } }
        }
    }

    fun assign(index: Int, c: ChannelEntity) = viewModelScope.launch {
        val p = playlists.get(c.playlistId) ?: return@launch
        val current = _slots.value[index]
        val sp = current.player ?: StreamPlayer(factory.create(settings.current(), forMultiview = true), factory, viewModelScope)
        sp.player.volume = if (index == audioSlot) 1f else 0f
        _slots.value = _slots.value.toMutableList().also { it[index] = SlotState(c, sp) }
        session.set(index, MultiviewSession.Slot(c.playlistId, c.id))
        sp.play(resolver.live(p, c), c.name)
    }

    fun clear(index: Int) {
        _slots.value[index].player?.release()
        _slots.value = _slots.value.toMutableList().also { it[index] = SlotState(null, null) }
        session.set(index, null)
    }

    /** Libère les connexions quand l'écran n'est plus visible (limite de connexions simultanées). */
    fun stopAll() = _slots.value.forEach { it.player?.stop() }

    fun restartAll() = _slots.value.forEachIndexed { i, s ->
        if (s.channel != null && s.player?.state?.value == StreamState.Idle) assign(i, s.channel)
    }

    fun focusAudio(index: Int) {
        audioSlot = index
        _slots.value.forEachIndexed { i, s -> s.player?.player?.volume = if (i == index) 1f else 0f }
    }

    override fun onCleared() {
        _slots.value.forEach { it.player?.release() }
        super.onCleared()
    }
}

@Composable
fun MultiviewScreen(nav: NavHostController) {
    val vm: MultiviewViewModel = hiltViewModel()
    val slots by vm.slots.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    var pickerFor by remember { mutableStateOf<Int?>(null) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var audio by remember { mutableStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().background(Color.Black).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (rowIndex in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0..1) {
                    val i = rowIndex * 2 + col
                    val slot = slots[i]
                    FocusSurface(
                        onClick = { if (slot.channel == null) pickerFor = i else menuFor = i },
                        onLongClick = { menuFor = i },
                        onFocus = { audio = i; vm.focusAudio(i) },
                        modifier = Modifier.weight(1f).fillMaxSize().then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        color = SokoulColors.Surface,
                        shape = RoundedCornerShape(8.dp),
                        focusScale = 1f,
                        borderWidth = 3.dp,
                    ) { _ ->
                        SlotView(slot, audio == i)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.restartAll()
        onDispose { vm.stopAll() }
    }

    pickerFor?.let { i ->
        OptionsDialog(
            "Choisir une chaîne",
            channels.map { c -> MenuOption("${c.number}. ${c.name}") { vm.assign(i, c) } },
            onDismiss = { pickerFor = null },
        )
    }
    menuFor?.let { i ->
        val c = slots[i].channel
        OptionsDialog(
            c?.name ?: "Case ${i + 1}",
            buildList {
                add(MenuOption("Changer de chaîne", Icons.Default.SwapHoriz) { pickerFor = i })
                if (c != null) {
                    add(MenuOption("Plein écran", Icons.Default.Fullscreen) { nav.navigate(PlayerRoute(PlayKind.LIVE, c.playlistId, c.id, categoryId = c.categoryId)) })
                    add(MenuOption("Vider la case", Icons.Default.Delete) { vm.clear(i) })
                }
            },
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun SlotView(slot: SlotState, hasAudio: Boolean) {
    val sp = slot.player
    val c = slot.channel
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (sp == null || c == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Add, null, Modifier.size(42.dp), tint = SokoulColors.TextMuted)
                Text("Ajouter une chaîne", color = SokoulColors.TextMuted, textAlign = TextAlign.Center)
            }
            return@Box
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    isFocusable = false
                }
            },
            update = { it.player = sp.player },
            modifier = Modifier.fillMaxSize(),
        )
        val state by sp.state.collectAsState()
        when (val s = state) {
            is StreamState.Loading, is StreamState.Reconnecting -> CircularProgressIndicator(Modifier.size(32.dp), color = SokoulColors.Accent)
            is StreamState.Failed -> Text(s.message, Modifier.padding(16.dp), color = SokoulColors.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            else -> Unit
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasAudio) Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(14.dp).padding(end = 4.dp), tint = SokoulColors.Accent)
            Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
        }
    }
}
