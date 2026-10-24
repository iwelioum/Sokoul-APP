package com.sokoul.iptv.ui.screens.guide

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.EpgRepository
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.data.repo.StreamResolver
import com.sokoul.iptv.data.repo.SyncEngine
import com.sokoul.iptv.recording.RecordingScheduler
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.CategoryChips
import com.sokoul.iptv.ui.components.CategoryPickerButton
import com.sokoul.iptv.ui.components.CategoryItem
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.LoadingBox
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PinDialog
import com.sokoul.iptv.ui.components.buildCategoryItems
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.live.ChannelActions
import com.sokoul.iptv.ui.theme.LocalIsTv
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.rememberNow
import com.sokoul.iptv.work.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.roundToInt

private const val HALF_HOUR = 30 * 60_000L
private const val LOAD_SPAN = 12 * 3600_000L

private data class Loaded(val from: Long, val to: Long, val programs: List<ProgramEntity>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    playlists: PlaylistRepository,
    catalog: CatalogRepository,
    private val epg: EpgRepository,
    private val resolver: StreamResolver,
    private val sync: SyncEngine,
    private val recordings: RecordingScheduler,
    val actions: ChannelActions,
    @ApplicationContext private val context: android.content.Context,
    private val settings: com.sokoul.iptv.data.SettingsStore,
) : ViewModel() {
    val playlist: StateFlow<PlaylistEntity?> = playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selected = MutableStateFlow(SpecialCategory.ALL)

    init {
        // Même groupe que l'écran Direct, comme sur TiviMate
        viewModelScope.launch {
            val p = playlist.filterNotNull().first()
            settings.lastGroup("live", p.id)?.takeIf { it != SpecialCategory.RECENT }?.let { selected.value = it }
            launch { actions.guardSelection(p.id, ContentType.LIVE, selected) }
            selected.drop(1).collect { settings.setLastGroup("live", p.id, it) }
        }
    }

    val categories: StateFlow<List<CategoryItem>> = playlist.filterNotNull()
        .flatMapLatest { catalog.categories(it.id, ContentType.LIVE) }
        .map { buildCategoryItems(it, withRecent = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channels: StateFlow<List<ChannelEntity>?> = combine(playlist.filterNotNull(), selected) { p, c -> p.id to c }
        .flatMapLatest { (pid, cat) -> catalog.channels(pid, cat) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val epgStatus = sync.epgStatus

    private val cache = ConcurrentHashMap<String, Loaded>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val _programs = MutableStateFlow<Map<String, List<ProgramEntity>>>(emptyMap())
    val programs: StateFlow<Map<String, List<ProgramEntity>>> = _programs

    val reminders: StateFlow<Set<String>> = playlist.filterNotNull()
        .flatMapLatest { recordings.reminders(it.id) }
        .map { list -> list.map { "${it.channelId}|${it.start}" }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // position de navigation, conservée en revenant du lecteur
    var windowStart = alignedNow() - HALF_HOUR
    var row = 0
    var focusTime = System.currentTimeMillis()

    fun alignedNow() = System.currentTimeMillis().let { it - it % HALF_HOUR }

    /** Charge les programmes d'une chaîne autour de la fenêtre visible (une seule requête à la fois). */
    fun request(c: ChannelEntity, from: Long, to: Long) {
        val pid = playlist.value?.id ?: return
        val l = cache[c.id]
        if (l != null && l.from <= from && l.to >= to) return
        if (!pending.add(c.id)) return
        viewModelScope.launch {
            try {
                val start = from - LOAD_SPAN / 2
                val end = to + LOAD_SPAN
                val list = epg.range(pid, c, start, end)
                cache[c.id] = Loaded(start, end, list)
                _programs.update { it + (c.id to list) }
            } finally {
                pending.remove(c.id)
            }
        }
    }

    fun reset() {
        cache.clear()
        _programs.value = emptyMap()
    }

    fun supportsCatchup(c: ChannelEntity) = playlist.value?.let { resolver.supportsCatchup(it, c) } == true
    fun refreshEpg() = playlist.value?.let { SyncWorker.epgNow(context, it.id) }

    fun remind(c: ChannelEntity, p: ProgramEntity, done: (String) -> Unit) = launch { done(actions.remind(it, c, p)) }
    fun removeReminder(c: ChannelEntity, p: ProgramEntity) = launch { recordings.removeReminder(it, c.id, p.start) }
    fun record(c: ChannelEntity, p: ProgramEntity, done: (String) -> Unit) = launch { done(actions.recordProgram(it, c, p)) }
    fun toggleFavorite(c: ChannelEntity) = launch { actions.toggleFavorite(it, c) }
    suspend fun unlock(pin: String) = actions.parental.tryUnlock(pin)
    fun isUnlocked() = actions.parental.isOpen()

    private fun launch(block: suspend (Long) -> Unit) {
        val pid = playlist.value?.id ?: return
        viewModelScope.launch { block(pid) }
    }
}

@Composable
fun GuideScreen(nav: NavHostController) {
    val vm: GuideViewModel = hiltViewModel()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val epgStatus by vm.epgStatus.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val pid = playlist?.id ?: return

    var windowStart by remember { mutableStateOf(vm.windowStart) }
    var row by remember { mutableStateOf(vm.row) }
    var focusTime by remember { mutableStateOf(vm.focusTime) }
    var gridFocused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Pair<ChannelEntity, ProgramEntity?>?>(null) }
    var pinFor by remember { mutableStateOf<CategoryItem?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val gridFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(windowStart, row, focusTime) { vm.windowStart = windowStart; vm.row = row; vm.focusTime = focusTime }

    val list = channels
    Column(Modifier.fillMaxSize()) {
        // --- en-tête : programme sélectionné ---
        val currentChannel = list?.getOrNull(row)
        val currentProgram = currentChannel?.let { c -> programs[c.id]?.firstOrNull { focusTime >= it.start && focusTime < it.end } }
        GuideHeader(currentChannel, currentProgram, now, windowStart)

        val pickCategory: (CategoryItem) -> Unit = { c ->
            if (c.locked && !vm.isUnlocked()) pinFor = c else {
                vm.selected.value = c.id; row = 0; vm.reset()
            }
        }
        if (!isTv) CategoryChips(categories, selected, pickCategory)

        Row(Modifier.padding(horizontal = 16.dp, vertical = if (isTv) 8.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isTv) CategoryPickerButton(categories, selected, pickCategory)
            AppButton("−3 h", { windowStart -= 6 * HALF_HOUR; focusTime = windowStart + HALF_HOUR }, icon = Icons.Default.ChevronLeft)
            AppButton("Maintenant", { windowStart = vm.alignedNow() - HALF_HOUR; focusTime = System.currentTimeMillis() }, icon = Icons.Default.Schedule)
            AppButton("+3 h", { windowStart += 6 * HALF_HOUR; focusTime = windowStart + HALF_HOUR }, icon = Icons.Default.ChevronRight)
            val st = epgStatus[pid]
            Text(
                when {
                    st?.running == true -> "Mise à jour du guide… ${st.count}"
                    st?.error != null -> "Guide : ${st.error}"
                    else -> ""
                },
                Modifier.padding(start = 12.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
        }

        when {
            list == null -> LoadingBox()
            list.isEmpty() -> EmptyState(Icons.Default.Star, "Aucune chaîne dans ce groupe")
            else -> BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp)) {
                val channelCol = if (isTv || maxWidth > 700.dp) 220.dp else 120.dp
                val dpPerMin = if (isTv) 6.5.dp else 4.5.dp
                val visibleMin = ((maxWidth - channelCol) / dpPerMin).toInt().coerceAtLeast(60)
                val windowEnd = windowStart + visibleMin * 60_000L
                val rowHeight = 64.dp
                val density = LocalDensity.current

                fun act(c: ChannelEntity, p: ProgramEntity?) {
                    val t = System.currentTimeMillis()
                    when {
                        p == null || (p.start <= t && p.end > t) ->
                            nav.navigate(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = selected))
                        p.end <= t && vm.supportsCatchup(c) && StreamResolver.inCatchupWindow(c, p.start, t) ->
                            nav.navigate(PlayerRoute(PlayKind.CATCHUP, pid, c.id, categoryId = selected, start = p.start, end = p.end))
                        p.end <= t && vm.supportsCatchup(c) ->
                            Toast.makeText(context, "Replay disponible sur ${c.catchupDays} jour(s) seulement pour cette chaîne", Toast.LENGTH_LONG).show()
                        else -> menu = c to p
                    }
                }

                Column(
                    Modifier.fillMaxSize()
                        .focusRequester(gridFocus)
                        .onFocusChanged { gridFocused = it.hasFocus }
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val c = list.getOrNull(row) ?: return@onKeyEvent false
                            val rowPrograms = programs[c.id].orEmpty()
                            val idx = rowPrograms.indexOfFirst { focusTime >= it.start && focusTime < it.end }
                            when (e.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (row == 0) return@onKeyEvent false
                                    row--; true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (row < list.lastIndex) row++; true
                                }
                                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { row = (row - 8).coerceAtLeast(0); true }
                                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { row = (row + 8).coerceAtMost(list.lastIndex); true }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    val next = if (idx >= 0) rowPrograms.getOrNull(idx + 1) else rowPrograms.firstOrNull { it.start > focusTime }
                                    focusTime = next?.start ?: (focusTime + HALF_HOUR)
                                    if (focusTime + 30 * 60_000L > windowEnd) windowStart = (focusTime - HALF_HOUR).let { it - it % HALF_HOUR }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    val cur = rowPrograms.getOrNull(idx)
                                    val atLeftEdge = (cur?.start ?: focusTime) <= windowStart
                                    val t = System.currentTimeMillis()
                                    val onNow = cur == null || (cur.start <= t && cur.end > t)
                                    val atDefaultWindow = windowStart >= vm.alignedNow() - HALF_HOUR
                                    // Sur le programme en cours (ou sans guide), gauche ouvre le menu comme sur TiviMate ;
                                    // le passé reste accessible via les chaînes en replay et les boutons −3 h.
                                    if (atDefaultWindow && (atLeftEdge || (onNow && !vm.supportsCatchup(c)) || rowPrograms.isEmpty())) {
                                        focusTime = t
                                        return@onKeyEvent false
                                    }
                                    val prev = if (idx > 0) rowPrograms[idx - 1] else rowPrograms.lastOrNull { it.end <= focusTime }
                                    focusTime = prev?.start?.coerceAtLeast(windowStart - 6 * HALF_HOUR) ?: (focusTime - HALF_HOUR)
                                    if (focusTime < windowStart) windowStart = focusTime - focusTime % HALF_HOUR
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_REWIND -> { windowStart -= 6 * HALF_HOUR; focusTime = windowStart + HALF_HOUR; true }
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { windowStart += 6 * HALF_HOUR; focusTime = windowStart + HALF_HOUR; true }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    if (e.nativeKeyEvent.repeatCount == 0) act(c, rowPrograms.getOrNull(idx)); true
                                }
                                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { menu = c to rowPrograms.getOrNull(idx); true }
                                else -> false
                            }
                        }
                        .focusable()
                        .pointerInput(dpPerMin) {
                            val pxPerMin = with(density) { dpPerMin.toPx() }
                            var acc = 0f
                            detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { _, dx ->
                                acc += dx
                                val minutes = (acc / pxPerMin).roundToInt()
                                if (kotlin.math.abs(minutes) >= 10) {
                                    windowStart -= minutes * 60_000L
                                    acc -= minutes * pxPerMin
                                }
                            }
                        }
                ) {
                    TimeRuler(windowStart, visibleMin, channelCol, dpPerMin, now)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                            LaunchedEffect(c.id, windowStart) { vm.request(c, windowStart, windowEnd) }
                            GuideRow(
                                channel = c,
                                programs = programs[c.id],
                                windowStart = windowStart,
                                windowEnd = windowEnd,
                                channelCol = channelCol,
                                dpPerMin = dpPerMin,
                                height = rowHeight,
                                now = now,
                                focusedTime = if (i == row && gridFocused) focusTime else null,
                                catchup = vm.supportsCatchup(c),
                                reminders = reminders,
                                onProgramClick = { p ->
                                    row = i; focusTime = p?.start ?: focusTime
                                    act(c, p)
                                },
                                onChannelClick = { nav.navigate(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = selected)) },
                            )
                        }
                    }
                }

                // garde la ligne focalisée visible
                LaunchedEffect(row) {
                    val info = listState.layoutInfo
                    val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    if (row < first + 1 || row > last - 1) listState.animateScrollToItem((row - 2).coerceAtLeast(0))
                }
                LaunchedEffect(Unit) { if (isTv) runCatching { gridFocus.requestFocus() } }
            }
        }
    }

    menu?.let { (c, p) ->
        val t = System.currentTimeMillis()
        val reminderKey = p?.let { "${c.id}|${it.start}" }
        val options = buildList {
            if (p == null || (p.start <= t && p.end > t)) add(MenuOption("Regarder en direct", Icons.Default.PlayArrow) {
                nav.navigate(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = selected))
            })
            if (p != null && p.end <= t && vm.supportsCatchup(c) && StreamResolver.inCatchupWindow(c, p.start, t)) add(MenuOption("Revoir (replay)", Icons.Default.History) {
                nav.navigate(PlayerRoute(PlayKind.CATCHUP, pid, c.id, categoryId = selected, start = p.start, end = p.end))
            })
            if (p != null && p.start > t) {
                if (reminderKey in reminders) add(MenuOption("Annuler le rappel", Icons.Default.NotificationsActive) { vm.removeReminder(c, p) })
                else add(MenuOption("Me rappeler", Icons.Default.NotificationsActive) {
                    vm.remind(c, p) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                })
            }
            if (p != null && p.end > t) add(MenuOption(if (p.start > t) "Programmer l'enregistrement" else "Enregistrer jusqu'à la fin", Icons.Default.FiberManualRecord) {
                vm.record(c, p) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            })
            add(MenuOption("Favori (ajouter / retirer)", Icons.Default.Star) { vm.toggleFavorite(c) })
            add(MenuOption("Mettre à jour le guide", Icons.Default.Schedule) { vm.refreshEpg() })
        }
        OptionsDialog(
            p?.title ?: c.name,
            options,
            subtitle = p?.let { "${c.name} · ${dayLabel(it.start)} ${formatRange(it.start, it.end)}" + (it.description?.let { d -> "\n$d" } ?: "") },
            onDismiss = { menu = null },
        )
    }
    pinFor?.let { cat ->
        PinDialog("Code parental", onResult = { pin ->
            scope.launch {
                val r = vm.unlock(pin)
                if (r.ok) { vm.selected.value = cat.id; row = 0; vm.reset(); pinFor = null; pinError = null } else pinError = r.message
            }
        }, onDismiss = { pinFor = null; pinError = null }, error = pinError)
    }
}

@Composable
private fun GuideHeader(c: ChannelEntity?, p: ProgramEntity?, now: Long, windowStart: Long) {
    Row(Modifier.fillMaxWidth().height(118.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (c != null) ChannelLogo(c.logo, c.name, Modifier.size(110.dp, 66.dp))
        Column(Modifier.weight(1f).padding(start = 18.dp)) {
            Text(
                "${dayLabel(windowStart, now)}  ·  ${c?.name ?: ""}", color = SokoulColors.Accent,
                style = MaterialTheme.typography.labelLarge, maxLines = 1,
            )
            Text(p?.title ?: "Pas de programme", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p != null) {
                Text(
                    formatRange(p.start, p.end) + (p.episode?.let { "  ·  $it" } ?: "") + (p.category?.let { "  ·  $it" } ?: ""),
                    color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                )
                p.description?.let { Text(it, color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        Text(formatClock(now), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun TimeRuler(windowStart: Long, visibleMin: Int, channelCol: Dp, dpPerMin: Dp, now: Long) {
    Box(Modifier.fillMaxWidth().height(28.dp).clipToBounds()) {
        var t = windowStart - windowStart % HALF_HOUR
        while (t < windowStart + visibleMin * 60_000L) {
            if (t >= windowStart) {
                val x = channelCol + dpPerMin * ((t - windowStart) / 60_000f)
                Text(formatClock(t), Modifier.offset(x = x + 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Box(Modifier.offset(x = x).width(1.dp).height(28.dp).background(SokoulColors.Divider))
            }
            t += HALF_HOUR
        }
        if (now in windowStart until windowStart + visibleMin * 60_000L) {
            val x = channelCol + dpPerMin * ((now - windowStart) / 60_000f)
            Box(Modifier.offset(x = x - 1.dp).width(2.dp).height(28.dp).background(SokoulColors.Live))
        }
    }
}

@Composable
private fun GuideRow(
    channel: ChannelEntity,
    programs: List<ProgramEntity>?,
    windowStart: Long,
    windowEnd: Long,
    channelCol: Dp,
    dpPerMin: Dp,
    height: Dp,
    now: Long,
    focusedTime: Long?,
    catchup: Boolean,
    reminders: Set<String>,
    onProgramClick: (ProgramEntity?) -> Unit,
    onChannelClick: () -> Unit,
) {
    val rowFocused = focusedTime != null
    Row(Modifier.fillMaxWidth().height(height).padding(vertical = 2.dp)) {
        Row(
            Modifier.width(channelCol).fillMaxHeight()
                .background(if (rowFocused) SokoulColors.SurfaceHigher else SokoulColors.Surface, RoundedCornerShape(8.dp))
                .tap(onChannelClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(channel.number.toString(), Modifier.width(34.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            ChannelLogo(channel.logo, channel.name, Modifier.size(52.dp, 34.dp))
            if (channelCol > 150.dp) {
                Text(
                    channel.name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 3.dp).clipToBounds()) {
            val visible = programs.orEmpty().filter { it.end > windowStart && it.start < windowEnd }
            if (programs != null && visible.isEmpty()) {
                val focused = rowFocused
                Box(
                    Modifier.fillMaxSize().padding(end = 2.dp)
                        .background(if (focused) SokoulColors.Accent else SokoulColors.SurfaceHigh, RoundedCornerShape(6.dp))
                        .tap { onProgramClick(null) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text("Pas d'information", color = if (focused) Color.White else SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            }
            visible.forEach { p ->
                val start = maxOf(p.start, windowStart)
                val end = minOf(p.end, windowEnd)
                val x = dpPerMin * ((start - windowStart) / 60_000f)
                val w = dpPerMin * ((end - start) / 60_000f)
                val focused = focusedTime != null && focusedTime >= p.start && focusedTime < p.end
                val live = now >= p.start && now < p.end
                val past = p.end <= now
                val bg = when {
                    focused -> SokoulColors.Accent
                    live -> SokoulColors.SurfaceHigher
                    past -> SokoulColors.Surface
                    else -> SokoulColors.SurfaceHigh
                }
                Box(
                    Modifier.offset(x = x).width(w - 2.dp).fillMaxHeight()
                        .background(bg, RoundedCornerShape(6.dp))
                        .then(if (focused) Modifier.border(2.dp, SokoulColors.Focus, RoundedCornerShape(6.dp)) else Modifier)
                        .tap { onProgramClick(p) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (past && catchup) Icon(Icons.Default.History, null, Modifier.size(12.dp).padding(end = 2.dp), tint = if (focused) Color.White else SokoulColors.TextMuted)
                            if ("${channel.id}|${p.start}" in reminders) Icon(Icons.Default.NotificationsActive, null, Modifier.size(12.dp).padding(end = 2.dp), tint = SokoulColors.Warning)
                            Text(
                                p.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = if (focused) Color.White else if (past) SokoulColors.TextSecondary else SokoulColors.TextPrimary,
                            )
                        }
                        Text(
                            formatRange(p.start, p.end), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = if (focused) Color(0xDDFFFFFF) else SokoulColors.TextMuted,
                        )
                    }
                    if (live) {
                        val f = ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f)
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(f).height(2.dp).background(if (focused) Color.White else SokoulColors.Accent))
                    }
                }
            }
            if (now in windowStart until windowEnd) {
                val x = dpPerMin * ((now - windowStart) / 60_000f)
                Box(Modifier.offset(x = x - 1.dp).width(2.dp).fillMaxHeight().background(SokoulColors.Live.copy(alpha = 0.8f)))
            }
        }
    }
}

/** Toucher sans rendre l'élément focalisable : la grille gère elle-même la télécommande. */
private fun Modifier.tap(onTap: () -> Unit): Modifier = pointerInput(onTap) { detectTapGestures { onTap() } }
