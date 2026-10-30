package com.sokoul.iptv.ui.screens.guide

import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.nav.navigateOnce
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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.sokoul.iptv.ui.components.CategoryChips
import com.sokoul.iptv.ui.components.CategoryPickerButton
import com.sokoul.iptv.ui.components.CategoryItem
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PinDialog
import com.sokoul.iptv.ui.components.buildCategoryItems
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.live.ChannelActions
import com.sokoul.iptv.ui.theme.LocalIsTv
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.rememberNow
import com.sokoul.iptv.work.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sokoul.iptv.ui.components.AmbientBake
import com.sokoul.iptv.ui.components.AmbientTint
import com.sokoul.iptv.ui.components.of
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.theme.Edge

private const val HALF_HOUR = GuideTime.HALF_HOUR
private const val LOAD_SPAN = 12 * 3600_000L
/** Délai avant de mettre à jour l'aperçu du Guide (le temps de relâcher la flèche). */
private const val PREVIEW_SETTLE_MS = 140L

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
    fun recordSeries(c: ChannelEntity, p: ProgramEntity, done: (String) -> Unit) = launch { done(actions.recordSeries(it, c, p)) }
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
    val layout = LocalLayout.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val pid = playlist?.id ?: run { com.sokoul.iptv.ui.screens.browse.NoPlaylistState(nav); return }

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
    val side = if (layout.isCompact) 16.dp else 48.dp
    Column(Modifier.fillMaxSize()) {
        // --- aperçu : programme focalisé ---
        // mis à jour 140 ms après le dernier déplacement : en défilement rapide, la grille reste fluide
        var preview by remember { mutableStateOf(row to focusTime) }
        LaunchedEffect(row, focusTime) { if (preview != row to focusTime) { delay(PREVIEW_SETTLE_MS); preview = row to focusTime } }
        val currentChannel = list?.getOrNull(preview.first)
        val currentProgram = currentChannel?.let { c -> programs[c.id]?.firstOrNull { preview.second >= it.start && preview.second < it.end } }
        GuidePreview(
            currentChannel, currentProgram, now, windowStart,
            catchup = currentChannel?.let { vm.supportsCatchup(it) } == true,
            reminded = currentChannel != null && currentProgram != null && "${currentChannel.id}|${currentProgram.start}" in reminders,
            compact = layout.isCompact, side = side,
        )

        val pickCategory: (CategoryItem) -> Unit = { c ->
            if (c.locked && !vm.isUnlocked()) pinFor = c else {
                vm.selected.value = c.id; row = 0; vm.reset()
            }
        }
        if (!isTv) CategoryChips(categories, selected, pickCategory)

        // --- barre : groupe, jours, maintenant ---
        val today = GuideTime.dayStart(now)
        val focusDay = GuideTime.dayStart(focusTime)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = side, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isTv) CategoryPickerButton(categories, selected, pickCategory)
            GuideChip("Maintenant", selected = false, icon = Icons.Default.Schedule) {
                windowStart = GuideTime.defaultWindow(System.currentTimeMillis()); focusTime = System.currentTimeMillis()
            }
            Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(SokoulColors.Divider))
            GuideTime.days(now).forEach { d ->
                GuideChip(dayLabel(d, now), selected = d == focusDay) {
                    val (w, f) = GuideTime.jumpTo(d, System.currentTimeMillis(), focusTime)
                    windowStart = w; focusTime = f
                }
            }
            val st = epgStatus[pid]
            val status = when {
                st?.running == true -> "Mise à jour du guide… ${st.count}"
                st?.error != null -> "Guide : ${st.error}"
                else -> null
            }
            if (status != null) Text(status, Modifier.padding(start = 12.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }

        when {
            list == null -> com.sokoul.iptv.ui.components.GuideSkeleton()
            list.isEmpty() -> if (selected == SpecialCategory.FAVORITES) EmptyState(Icons.Default.Star, "Aucune chaîne favorite", "Dans le Direct, ouvrez le menu d'une chaîne et choisissez « Ajouter aux favoris ».")
                else EmptyState(Icons.Default.LiveTv, "Aucune chaîne dans ce groupe", "Choisissez un autre groupe dans la liste.")
            else -> BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                // colonne des chaînes : logo + nom sur TV ; logos seuls sur écran étroit
                val channelCol = if (isTv || maxWidth > 700.dp) 236.dp else 84.dp
                val dpPerMin = if (isTv) 6.5.dp else 4.5.dp
                val gridStart = if (layout.isCompact) 8.dp else side - 8.dp
                val visibleMin = ((maxWidth - channelCol - gridStart) / dpPerMin).toInt().coerceAtLeast(60)
                val windowEnd = windowStart + visibleMin * 60_000L
                val rowHeight = if (layout.isCompact) 56.dp else 60.dp
                val density = LocalDensity.current

                fun act(c: ChannelEntity, p: ProgramEntity?) {
                    val t = System.currentTimeMillis()
                    when {
                        p == null || (p.start <= t && p.end > t) ->
                            nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = selected))
                        p.end <= t && vm.supportsCatchup(c) && StreamResolver.inCatchupWindow(c, p.start, t) ->
                            nav.navigateOnce(PlayerRoute(PlayKind.CATCHUP, pid, c.id, categoryId = selected, start = p.start, end = p.end))
                        p.end <= t && vm.supportsCatchup(c) ->
                            Toast.makeText(context, "Replay disponible sur ${c.catchupDays} jour(s) seulement pour cette chaîne", Toast.LENGTH_LONG).show()
                        else -> menu = c to p
                    }
                }

                val pickRef = rememberUpdatedState { i: Int, c: ChannelEntity, p: ProgramEntity? -> row = i; focusTime = p?.start ?: focusTime; act(c, p) }
                val openRef = rememberUpdatedState { c: ChannelEntity -> nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = selected)) }
                Column(
                    Modifier.fillMaxSize().padding(start = gridStart)
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
                                    if (focusTime + 30 * 60_000L > windowEnd) windowStart = GuideTime.align(focusTime - HALF_HOUR)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    val cur = rowPrograms.getOrNull(idx)
                                    val atLeftEdge = (cur?.start ?: focusTime) <= windowStart
                                    val t = System.currentTimeMillis()
                                    val onNow = cur == null || (cur.start <= t && cur.end > t)
                                    val atDefaultWindow = windowStart >= vm.alignedNow() - HALF_HOUR
                                    // Sur le programme en cours (ou sans guide), gauche ouvre le menu comme sur TiviMate ;
                                    // le passé reste accessible via les chaînes en replay et la touche ⏪.
                                    if (atDefaultWindow && (atLeftEdge || (onNow && !vm.supportsCatchup(c)) || rowPrograms.isEmpty())) {
                                        focusTime = t
                                        return@onKeyEvent false
                                    }
                                    val prev = if (idx > 0) rowPrograms[idx - 1] else rowPrograms.lastOrNull { it.end <= focusTime }
                                    focusTime = prev?.start?.coerceAtLeast(windowStart - 6 * HALF_HOUR) ?: (focusTime - HALF_HOUR)
                                    if (focusTime < windowStart) windowStart = GuideTime.align(focusTime)
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_REWIND -> { GuideTime.shift(windowStart, -GuideTime.JUMP).let { (w, f) -> windowStart = w; focusTime = f }; true }
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { GuideTime.shift(windowStart, GuideTime.JUMP).let { (w, f) -> windowStart = w; focusTime = f }; true }
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
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                                selectedRow = i == row,
                                focusedTime = if (i == row && gridFocused) focusTime else null,
                                catchup = vm.supportsCatchup(c),
                                reminders = reminders,
                                // rappels stables : une touche ne recompose que la ligne focalisée et l'aperçu
                                onProgramClick = remember(i, c.id) { { p: ProgramEntity? -> pickRef.value(i, c, p) } },
                                onChannelClick = remember(c.id) { { openRef.value(c) } },
                                onProgramLongClick = remember(c.id) { { p: ProgramEntity? -> menu = c to p } },
                            )
                        }
                    }
                    KeyHints(
                        listOf(
                            KeyHint("OK", "Regarder"),
                            KeyHint("Menu", "Rappel, enregistrement…"),
                            KeyHint("⏪ ⏩", "−2 h / +2 h"),
                            KeyHint("▲", "Jours et groupes"),
                        ),
                        Modifier.padding(start = channelCol, top = 6.dp, bottom = 10.dp),
                    )
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
                nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = selected))
            })
            if (p != null && p.end <= t && vm.supportsCatchup(c) && StreamResolver.inCatchupWindow(c, p.start, t)) add(MenuOption("Revoir (replay)", Icons.Default.History) {
                nav.navigateOnce(PlayerRoute(PlayKind.CATCHUP, pid, c.id, categoryId = selected, start = p.start, end = p.end))
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
            if (p != null && p.end > t) add(MenuOption("Enregistrer tous les épisodes", Icons.Default.FiberManualRecord) {
                vm.recordSeries(c, p) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
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

/**
 * Aperçu du programme focalisé, en haut du guide : fond « cuit » tiré de l'image du programme (s'il y en a une),
 * étiquette (EN CE MOMENT, DEMAIN · 20:50…), titre à empattements, horaires, résumé, et l'heure.
 */
@Composable
private fun GuidePreview(
    c: ChannelEntity?, p: ProgramEntity?, now: Long, windowStart: Long,
    catchup: Boolean, reminded: Boolean, compact: Boolean, side: Dp,
) {
    val context = LocalContext.current
    var bg by remember { mutableStateOf<ImageBitmap?>(null) }
    val icon = p?.icon?.takeIf { it.isNotBlank() }
    LaunchedEffect(icon) {
        bg = icon?.let { url ->
            val tint = AmbientTint.of(context, url) ?: SokoulColors.AccentSoft
            AmbientBake.bake(context, url, tint, SokoulColors.Background, aspect = 4.5f)
        }
    }
    Box(Modifier.fillMaxWidth().height(if (compact) 116.dp else 176.dp)) {
        bg?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        // fondu vers la grille en bas
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(48.dp).background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.Background))))
        Row(
            Modifier.fillMaxSize().padding(start = side, end = side, top = if (compact) 10.dp else 22.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                val live = p != null && p.start <= now && p.end > now
                Text(
                    buildString {
                        append(p?.let { GuideTime.kicker(it, now, catchup) } ?: "GUIDE")
                        c?.let { append(" · ").append(it.name.uppercase()) }
                    },
                    style = Type.Kicker.copy(color = if (live) SokoulColors.Live else SokoulColors.Accent), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    p?.title ?: "Pas de programme",
                    Modifier.padding(top = 4.dp),
                    style = if (compact) Type.Section else Type.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (p != null) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val meta = listOfNotNull(formatRange(p.start, p.end), GuideTime.duration(p), p.episode, p.category?.takeIf { it.isNotBlank() })
                        Text(meta.joinToString("  ·  "), style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (live) {
                            val f = ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f)
                            com.sokoul.iptv.ui.components.ProgressLine(f, Modifier.padding(start = 14.dp).width(120.dp), tone = com.sokoul.iptv.ui.components.ProgressTone.Live, thickness = 3.dp, onImage = false)
                            Text(GuideTime.remaining(p, now), Modifier.padding(start = 10.dp), style = Type.Meta, maxLines = 1)
                        }
                        if (reminded) {
                            Icon(Icons.Default.NotificationsActive, null, Modifier.padding(start = 14.dp).size(16.dp), tint = SokoulColors.Warning)
                            Text("Rappel prévu", Modifier.padding(start = 4.dp), style = Type.Meta.copy(color = SokoulColors.Warning), maxLines = 1)
                        }
                    }
                    if (!compact) p.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it, Modifier.padding(top = 8.dp).widthIn(max = 760.dp), color = SokoulColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(Modifier.padding(start = 24.dp), horizontalAlignment = Alignment.End) {
                Text(formatClock(now), style = Type.Section)
                Text(dayLabel(windowStart, now).uppercase(), style = Type.Overline, maxLines = 1)
                if (!compact) {
                    Box(Modifier.padding(top = 8.dp).size(160.dp, 90.dp).clip(Radius.Card).background(SokoulColors.SurfaceHigh)) {
                        if (icon != null) NetImage(icon, p?.title, Modifier.fillMaxSize()) { c?.let { ChannelLogo(it.logo, it.name, Modifier.fillMaxSize()) } }
                        else c?.let { ChannelLogo(it.logo, it.name, Modifier.fillMaxSize().padding(18.dp)) }
                    }
                }
            }
        }
    }
}

/** Pastille de la barre du guide (jour, Maintenant) : blanche au focus, teintée quand elle est le jour affiché. */
@Composable
private fun GuideChip(text: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, style = FocusStyle.Chip,
        color = if (selected) SokoulColors.AccentTint else SokoulColors.white(0.06f),
    ) { focused ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            val fg = when {
                focused -> SokoulColors.OnLight
                selected -> SokoulColors.Accent
                else -> SokoulColors.TextPrimary
            }
            if (icon != null) Icon(icon, null, Modifier.padding(end = 6.dp).size(16.dp), tint = fg)
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = fg, maxLines = 1)
        }
    }
}

/** Réglette des heures : heures pleines à empattements, demi-heures discrètes, pastille « maintenant ». */
@Composable
private fun TimeRuler(windowStart: Long, visibleMin: Int, channelCol: Dp, dpPerMin: Dp, now: Long) {
    Box(Modifier.fillMaxWidth().height(34.dp).clipToBounds()) {
        var t = GuideTime.align(windowStart)
        while (t < windowStart + visibleMin * 60_000L) {
            if (t >= windowStart) {
                val x = channelCol + 3.dp + dpPerMin * ((t - windowStart) / 60_000f)
                val fullHour = Math.floorMod(t, 2 * HALF_HOUR) == 0L
                Box(Modifier.offset(x = x).align(Alignment.BottomStart).width(1.dp).height(if (fullHour) 12.dp else 7.dp).background(SokoulColors.white(0.28f)))
                Text(
                    formatClock(t), Modifier.offset(x = x + 7.dp).align(Alignment.BottomStart).padding(bottom = 2.dp),
                    style = if (fullHour) Type.Tab else MaterialTheme.typography.labelLarge,
                    color = if (fullHour) SokoulColors.TextPrimary else SokoulColors.TextMuted, maxLines = 1,
                )
            }
            t += HALF_HOUR
        }
        if (now in windowStart until windowStart + visibleMin * 60_000L) {
            val x = channelCol + 3.dp + dpPerMin * ((now - windowStart) / 60_000f)
            Box(Modifier.offset(x = x - 1.dp).align(Alignment.BottomStart).width(2.dp).height(34.dp).background(SokoulColors.Accent))
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
    selectedRow: Boolean,
    focusedTime: Long?,
    catchup: Boolean,
    reminders: Set<String>,
    onProgramClick: (ProgramEntity?) -> Unit,
    onChannelClick: () -> Unit,
    /** Tactile : appui long = menu du programme (rappel, enregistrement…), comme la touche Menu. */
    onProgramLongClick: (ProgramEntity?) -> Unit = {},
) {
    val rowFocused = focusedTime != null
    Row(Modifier.fillMaxWidth().height(height).padding(vertical = 2.dp)) {
        // chaîne : collée à gauche, éclairée et marquée d'un trait d'accent sur la ligne active
        Box(
            Modifier.width(channelCol).fillMaxHeight()
                .background(if (selectedRow) SokoulColors.SurfaceHigher else Color.Transparent, Radius.Card)
                .tap(onTap = onChannelClick),
        ) {
            if (rowFocused) Box(Modifier.width(Edge.Accent).fillMaxHeight().padding(vertical = 8.dp).background(SokoulColors.Accent, Radius.Tiny))
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (channelCol > 150.dp) Text(
                    channel.number.toString(), Modifier.width(36.dp),
                    color = if (selectedRow) SokoulColors.TextPrimary else SokoulColors.TextMuted, style = MaterialTheme.typography.labelLarge,
                )
                ChannelLogo(channel.logo, channel.name, Modifier.size(56.dp, 36.dp))
                if (channelCol > 150.dp) {
                    Text(
                        channel.name, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, fontWeight = if (selectedRow) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedRow) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 3.dp).clipToBounds()) {
            val visible = programs.orEmpty().filter { it.end > windowStart && it.start < windowEnd }
            if (programs != null && visible.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(end = 2.dp)
                        .background(if (rowFocused) SokoulColors.Focus else SokoulColors.Surface, Radius.Tiny)
                        .tap(onLongPress = { onProgramLongClick(null) }) { onProgramClick(null) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(if (LocalLayout.current.isTouch) "Pas d'information · touchez pour regarder" else "Pas d'information · OK pour regarder", color = if (rowFocused) SokoulColors.OnLight else SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
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
                    focused -> SokoulColors.Focus
                    live -> SokoulColors.SurfaceHigher
                    past -> SokoulColors.Surface
                    else -> SokoulColors.SurfaceHigh
                }
                val fg = when {
                    focused -> SokoulColors.OnLight
                    past -> SokoulColors.TextSecondary
                    else -> SokoulColors.TextPrimary
                }
                Box(
                    Modifier.offset(x = x).width(w - 3.dp).fillMaxHeight()
                        .background(bg, Radius.Tiny)
                        .tap(onLongPress = { onProgramLongClick(p) }) { onProgramClick(p) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (past && catchup) Icon(Icons.Default.History, null, Modifier.size(13.dp).padding(end = 3.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.TextMuted)
                            if ("${channel.id}|${p.start}" in reminders) Icon(Icons.Default.NotificationsActive, null, Modifier.size(13.dp).padding(end = 3.dp), tint = SokoulColors.Warning)
                            Text(
                                p.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = fg,
                            )
                        }
                        Text(
                            formatRange(p.start, p.end), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = if (focused) SokoulColors.OnLight.copy(alpha = 0.7f) else SokoulColors.TextMuted,
                        )
                    }
                    if (live) {
                        val f = ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f)
                        Box(Modifier.align(Alignment.BottomStart).offset(y = 5.dp).fillMaxWidth(f).height(2.dp).background(if (focused) SokoulColors.Live else SokoulColors.Accent))
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

/** Toucher sans rendre l'élément focalisable : la grille gère elle-même la télécommande. */
private fun Modifier.tap(onLongPress: (() -> Unit)? = null, onTap: () -> Unit): Modifier = pointerInput(onTap, onLongPress) {
    detectTapGestures(onLongPress = onLongPress?.let { long -> { long() } }, onTap = { onTap() })
}
