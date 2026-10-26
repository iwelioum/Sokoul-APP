package com.sokoul.iptv.ui.screens.multiview

import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.nav.navigateOnce
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.player.PlayerFactory
import com.sokoul.iptv.player.StreamPlayer
import com.sokoul.iptv.player.StreamState
import com.sokoul.iptv.ui.MultiviewSession
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val parental: com.sokoul.iptv.ui.ParentalGate,
    private val gate: com.sokoul.iptv.player.ProviderGate,
) : ViewModel() {
    private val _slots = MutableStateFlow(List(4) { SlotState(null, null) })
    val slots: StateFlow<List<SlotState>> = _slots.asStateFlow()
    /** Groupes de chaînes pour le sélecteur en deux étapes (groupe puis chaîne). */
    val groups = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val notice = MutableStateFlow<String?>(null)
    val maxConnections = MutableStateFlow<Int?>(null)
    private var audioSlot = 0
    private var pid = 0L

    init {
        viewModelScope.launch {
            val p = playlists.activeNow() ?: return@launch
            pid = p.id
            maxConnections.value = p.maxConnections?.takeIf { it > 0 }
            groups.value = listOf(SpecialCategory.FAVORITES to "★ Favoris") +
                catalog.categories(p.id, com.sokoul.iptv.data.db.ContentType.LIVE).first()
                    .filter { !it.hidden && !it.locked }.map { it.categoryId to it.name }
            session.slots.value.forEachIndexed { i, s -> if (s != null) catalog.channel(s.playlistId, s.channelId)?.let { assign(i, it) } }
        }
    }

    suspend fun channelsOf(categoryId: String): List<ChannelEntity> = catalog.channelList(pid, categoryId)

    fun assign(index: Int, c: ChannelEntity) = viewModelScope.launch {
        val p = playlists.get(c.playlistId) ?: return@launch
        // Chaque case ouvre une connexion : on respecte la limite de l'abonnement plutôt que d'échouer en boucle
        val max = p.maxConnections?.takeIf { it > 0 }
        val others = _slots.value.withIndex().count { (j, s) -> j != index && s.channel?.playlistId == c.playlistId && s.player?.state?.value.let { it is StreamState.Playing || it is StreamState.Buffering || it is StreamState.Loading || it is StreamState.Reconnecting } }
        if (max != null && others >= max) {
            notice.value = "Votre abonnement n'autorise que $max connexion${if (max > 1) "s" else ""} simultanée${if (max > 1) "s" else ""} : videz une case avant d'en lancer une autre."
            return@launch
        }
        if (catalog.isCategoryLocked(c.playlistId, com.sokoul.iptv.data.db.ContentType.LIVE, c.categoryId) && !parental.canAccessLocked()) {
            notice.value = "« ${c.name} » appartient à un groupe verrouillé : déverrouillez-le depuis l'écran Direct."
            return@launch
        }
        val stream = try { resolver.live(p, c) } catch (e: Exception) {
            notice.value = e.userMessage()
            return@launch
        }
        notice.value = null
        val current = _slots.value[index]
        val sp = current.player ?: StreamPlayer(factory.create(settings.current(), forMultiview = true), factory, viewModelScope, gate, exclusive = false)
        sp.player.volume = if (index == audioSlot) 1f else 0f
        _slots.value = _slots.value.toMutableList().also { it[index] = SlotState(c, sp) }
        session.set(index, MultiviewSession.Slot(c.playlistId, c.id))
        sp.play(stream, c.name)
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
    val groups by vm.groups.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val maxConn by vm.maxConnections.collectAsStateWithLifecycle()
    var pickerFor by remember { mutableStateOf<Int?>(null) }
    var groupFor by remember { mutableStateOf<Pair<Int, Pair<String, String>>?>(null) }
    var groupChannels by remember { mutableStateOf<List<ChannelEntity>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var audio by remember { mutableStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    // Une seule connexion : le multi-écran ne peut rien montrer de plus que le plein écran. On l'explique au lieu
    // d'afficher quatre cases dont trois resteraient noires.
    if (maxConn == 1 && slots.all { it.channel == null }) {
        SingleConnectionNotice(onBack = { nav.popBackStack() })
        return
    }

    Column(Modifier.fillMaxSize().background(SokoulColors.VideoBlack).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val banner = notice ?: maxConn?.takeIf { it == 1 }?.let { "Votre abonnement n'autorise qu'une connexion : une seule case peut être lue à la fois." }
        if (banner != null) {
            Text(
                banner, Modifier.fillMaxWidth().background(SokoulColors.Warning.copy(alpha = 0.18f), Radius.Card).padding(horizontal = 14.dp, vertical = 8.dp),
                color = SokoulColors.TextPrimary, style = MaterialTheme.typography.bodyMedium,
            )
        }
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
                        shape = Radius.Card,
                        
                        
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
            "Choisir un groupe",
            groups.map { g -> MenuOption(g.second) {
                groupFor = i to g
                groupChannels = null
                scope.launch { groupChannels = vm.channelsOf(g.first) }
            } },
            onDismiss = { pickerFor = null },
        )
    }
    groupFor?.let { (i, g) ->
        val list = groupChannels
        if (list != null) OptionsDialog(
            g.second,
            if (list.isEmpty()) listOf(MenuOption("Aucune chaîne dans ce groupe") {})
            else list.map { c -> MenuOption("${c.number}. ${c.name}") { vm.assign(i, c) } },
            onDismiss = { groupFor = null; groupChannels = null },
        )
    }
    menuFor?.let { i ->
        val c = slots[i].channel
        OptionsDialog(
            c?.name ?: "Case ${i + 1}",
            buildList {
                add(MenuOption("Changer de chaîne", Icons.Default.SwapHoriz) { pickerFor = i })
                if (c != null) {
                    add(MenuOption("Plein écran", Icons.Default.Fullscreen) { nav.navigateOnce(PlayerRoute(PlayKind.LIVE, c.playlistId, c.id, categoryId = c.categoryId)) })
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
    Box(Modifier.fillMaxSize().background(SokoulColors.VideoBlack), contentAlignment = Alignment.Center) {
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
            is StreamState.Loading, is StreamState.Buffering, is StreamState.Reconnecting, is StreamState.Waiting -> com.sokoul.iptv.ui.components.SokoulSpinner(size = 32.dp)
            is StreamState.Failed -> Text(s.message, Modifier.padding(16.dp), color = SokoulColors.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            is StreamState.Busy -> Text("Connexion utilisée par l'enregistrement de ${s.channel}", Modifier.padding(16.dp), color = SokoulColors.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            else -> Unit
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(8.dp).background(SokoulColors.black(0.6f), Radius.Tiny).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasAudio) Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(14.dp).padding(end = 4.dp), tint = SokoulColors.Accent)
            Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = SokoulColors.OnVideo)
        }
    }
}

/** Écran d'explication quand l'abonnement n'autorise qu'une connexion (le multi-écran en demande une par case). */
@Composable
private fun SingleConnectionNotice(onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(SokoulColors.Background), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp).widthIn(max = 720.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // quatre cases dessinées, une seule allumée
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0..1) Box(
                        Modifier.size(96.dp, 54.dp).background(if (r == 0 && c == 0) SokoulColors.Accent else SokoulColors.SurfaceHigh, Radius.Card),
                    )
                }
            }
            Text("MULTI-ÉCRAN", Modifier.padding(top = 28.dp), style = com.sokoul.iptv.ui.theme.Type.Kicker)
            Text(
                "Votre abonnement autorise 1 connexion", Modifier.padding(top = 4.dp),
                style = com.sokoul.iptv.ui.theme.Type.ScreenTitle, textAlign = TextAlign.Center,
            )
            Text(
                "Chaque case du multi-écran ouvre sa propre connexion au fournisseur. Avec une seule connexion, " +
                    "une deuxième chaîne serait refusée, voire bloquerait l'abonnement quelques minutes. " +
                    "Le multi-écran s'activera tout seul avec un abonnement à plusieurs connexions.",
                Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextSecondary, textAlign = TextAlign.Center,
            )
            com.sokoul.iptv.ui.components.SokoulButton("Retour", onBack, Modifier.padding(top = 28.dp).focusRequester(focus), kind = com.sokoul.iptv.ui.components.ButtonKind.Primary)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
