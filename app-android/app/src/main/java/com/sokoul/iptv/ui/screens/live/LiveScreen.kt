package com.sokoul.iptv.ui.screens.live

import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.nav.navigateOnce
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.NowNext
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.data.repo.StreamResolver
import com.sokoul.iptv.ui.components.CategoryChips
import com.sokoul.iptv.ui.components.CategoryColumn
import com.sokoul.iptv.ui.components.CategoryItem
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.PinDialog
import com.sokoul.iptv.ui.components.Tag
import com.sokoul.iptv.ui.components.buildCategoryItems
import com.sokoul.iptv.ui.nav.MultiviewRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.useSideRail
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.ui.util.rememberNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val epg: EpgRepository,
    private val resolver: StreamResolver,
    library: LibraryRepository,
    val actions: ChannelActions,
    private val settings: com.sokoul.iptv.data.SettingsStore,
    playerFactory: com.sokoul.iptv.player.PlayerFactory,
    private val gate: com.sokoul.iptv.player.ProviderGate,
) : ViewModel() {

    val playlist: StateFlow<PlaylistEntity?> = playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selected = MutableStateFlow(SpecialCategory.ALL)
    private val focused = MutableStateFlow<ChannelEntity?>(null)

    private val preview = LivePreview(playerFactory, gate, resolver, settings, viewModelScope)
    /** Aperçu en cours (chaîne et lecteur), null quand rien ne joue. */
    val previewing: StateFlow<LivePreview.Current?> = preview.current
    /** Écran Direct visible sur TV (posé par l'écran : ON_START / ON_STOP). */
    private val previewVisible = MutableStateFlow(false)

    fun setPreviewVisible(visible: Boolean) {
        previewVisible.value = visible
        if (!visible) preview.stop()
    }

    /** Avant le plein écran : l'aperçu rend la connexion (abonnement à une seule connexion). */
    fun stopPreview() = preview.stop()

    init {
        // Rouvre le dernier groupe consulté (avec des centaines de groupes, revenir à « Tout » est pénible)
        viewModelScope.launch {
            val p = playlist.filterNotNull().first()
            settings.lastGroup("live", p.id)?.let { selected.value = it }
            launch { actions.guardSelection(p.id, ContentType.LIVE, selected) }
            // changement de groupe (y compris re-verrouillage) : le panneau ne montre plus l'ancienne chaîne
            launch { selected.drop(1).collect { focused.value = null; lastPlayed = null } }
            selected.drop(1).collect { settings.setLastGroup("live", p.id, it) }
        }
        // Aperçu : le focus doit se poser ; changer de chaîne, masquer l'écran ou couper le réglage arrête le flux.
        viewModelScope.launch {
            combine(focused, previewVisible, settings.settings.map { it.livePreview }.distinctUntilChanged(), gate.recording) { c, v, on, rec ->
                c.takeIf { LivePreviewPolicy.canPlay(on, v, rec != null, 0) }
            }.distinctUntilChanged { a, b -> a?.id == b?.id }
                .onEach { c -> if (c?.id != preview.current.value?.channel?.id) preview.stop() }
                .debounce(LivePreviewPolicy.DEBOUNCE_MS)
                .collectLatest { c ->
                    val p = playlist.value
                    if (c == null || p == null || preview.busy()) preview.stop() else preview.show(p, c)
                }
        }
    }

    override fun onCleared() {
        preview.stop()
        super.onCleared()
    }

    val categories: StateFlow<List<CategoryItem>> = playlist.filterNotNull()
        .flatMapLatest { p -> combine(catalog.categories(p.id, ContentType.LIVE), catalog.channelCounts(p.id)) { cats, n -> buildCategoryItems(cats).map { it.copy(count = n[it.id]) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** null = chargement en cours. */
    val channels: StateFlow<List<ChannelEntity>?> = combine(playlist.filterNotNull(), selected) { p, c -> p.id to c }
        .flatMapLatest { (pid, cat) -> catalog.channels(pid, cat) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Programme en cours de chaque chaîne, rafraîchi chaque minute. */
    val nowPlaying: StateFlow<Map<String, ProgramEntity>> = combine(channels.filterNotNull(), ticker(60_000)) { list, _ -> list }
        .mapLatest { list -> playlist.value?.let { epg.nowFor(it.id, list) } ?: emptyMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val details: StateFlow<Pair<ChannelEntity, NowNext>?> = focused.debounce(200)
        .mapLatest { c -> c?.let { ch -> playlist.value?.let { ch to epg.nowNext(it.id, ch) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val favoriteIds: StateFlow<Set<String>> = playlist.filterNotNull()
        .flatMapLatest { library.favoriteIds(it.id, ContentType.LIVE) }.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun focus(c: ChannelEntity) { focused.value = c }

    /** Chaîne lancée depuis la liste : reprend le focus au retour du lecteur (même si la liste a été réordonnée). */
    var lastPlayed: String? = null

    fun supportsCatchup(c: ChannelEntity) = playlist.value?.let { resolver.supportsCatchup(it, c) } == true

    fun toggleFavorite(c: ChannelEntity) = launch { actions.toggleFavorite(it, c) }
    fun record(c: ChannelEntity, minutes: Int, done: (String) -> Unit) = launch { done(actions.recordNow(it, c, minutes)) }
    fun recordSeries(c: ChannelEntity, p: ProgramEntity, done: (String) -> Unit) = launch { done(actions.recordSeries(it, c, p)) }
    fun addToMultiview(c: ChannelEntity): String? = playlist.value?.let { actions.addToMultiview(it.id, c) }
    fun hideCategory(id: String) = launch { actions.setCategoryHidden(it, id, true) }
    fun setKids(id: String, v: Int) = launch { catalog.setCategoryKids(it, ContentType.LIVE, id, v) }
    fun setLocked(id: String, locked: Boolean) = launch { actions.setCategoryLocked(it, ContentType.LIVE, id, locked) }
    suspend fun hasPin() = actions.parental.hasPin()
    suspend fun unlock(pin: String) = actions.parental.tryUnlock(pin)
    fun isUnlocked() = actions.parental.isOpen()

    private fun launch(block: suspend (Long) -> Unit) {
        val pid = playlist.value?.id ?: return
        viewModelScope.launch { block(pid) }
    }

    private fun ticker(period: Long) = flow { while (true) { emit(Unit); delay(period) } }
}

@Composable
fun LiveScreen(nav: NavHostController) {
    val vm: LiveViewModel = hiltViewModel()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val wide = useSideRail()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var channelMenu by remember { mutableStateOf<ChannelEntity?>(null) }
    var categoryMenu by remember { mutableStateOf<CategoryItem?>(null) }
    var pinFor by remember { mutableStateOf<CategoryItem?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    val pid = playlist?.id ?: run { com.sokoul.iptv.ui.screens.browse.NoPlaylistState(nav); return }
    val selectCategory: (CategoryItem) -> Unit = { c ->
        if (c.locked && !vm.isUnlocked()) pinFor = c else vm.selected.value = c.id
    }
    val play: (ChannelEntity) -> Unit = { c ->
        vm.lastPlayed = c.id
        // l'aperçu rend la connexion avant que le plein écran ouvre la sienne
        vm.stopPreview()
        nav.navigateOnce(PlayerRoute(PlayKind.LIVE, pid, c.id, categoryId = selected))
    }
    val kidsProfile = com.sokoul.iptv.ui.screens.profiles.isKidsProfile()
    var kidsMenu by remember { mutableStateOf<CategoryItem?>(null) }
    // profil Enfant : pas de menu de groupe (masquer, verrouiller, autoriser)
    val openCategoryMenu: (CategoryItem) -> Unit = { if (!it.id.startsWith("__") && !kidsProfile) categoryMenu = it }

    // aperçu : seulement sur TV, tant que l'écran est visible (Accueil, veille ou autre écran → flux fermé)
    val isTv = com.sokoul.iptv.ui.theme.LocalLayout.current.isTv && wide
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, isTv) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> vm.setPreviewVisible(isTv)
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> vm.setPreviewVisible(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.setPreviewVisible(false)
        }
    }

    if (wide) {
        val l = com.sokoul.iptv.ui.theme.LocalLayout.current
        // panneau de détails : suit le focus de la télécommande ; au doigt un tap lance la chaîne, il resterait vide
        val showDetails = l.isTv
        val catWidth = if (l.widthDp < 840) 180.dp else 210.dp
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(catWidth).fillMaxHeight().background(SokoulColors.Surface.copy(alpha = 0.6f))) {
                // titre de la page, même rôle que les autres écrans
                Text("Direct", Modifier.padding(start = 20.dp, top = l.safeTop, end = 12.dp), style = com.sokoul.iptv.ui.theme.Type.ScreenTitle, maxLines = 1)
                CategoryColumn(categories, selected, selectCategory, Modifier.weight(1f).fillMaxWidth(), onLongClick = openCategoryMenu)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ChannelList(channels, nowPlaying, favorites, vm::focus, play, { channelMenu = it }, vm.lastPlayed) { vm.lastPlayed = null }
            }
            if (showDetails) {
                val previewing by vm.previewing.collectAsStateWithLifecycle()
                DetailsPanel(details, previewing, Modifier.width(if (l.isTv) 300.dp else 340.dp).fillMaxHeight(), vm::supportsCatchup)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            CategoryChips(categories, selected, selectCategory, onLongClick = openCategoryMenu)
            ChannelList(channels, nowPlaying, favorites, vm::focus, play, { channelMenu = it }, vm.lastPlayed) { vm.lastPlayed = null }
        }
    }

    channelMenu?.let { c ->
        val fav = c.id in favorites
        OptionsDialog(
            c.name,
            listOfNotNull(
                MenuOption(if (fav) "Retirer des favoris" else "Ajouter aux favoris", if (fav) Icons.Default.StarBorder else Icons.Default.Star) { vm.toggleFavorite(c) },
                MenuOption("Enregistrer 1 h", Icons.Default.FiberManualRecord) {
                    vm.record(c, 60) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                },
                MenuOption("Enregistrer 2 h", Icons.Default.FiberManualRecord) {
                    vm.record(c, 120) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                },
                // programme en cours d'après le guide : toutes ses diffusions sur cette chaîne
                nowPlaying[c.id]?.let { p ->
                    MenuOption("Enregistrer tous les épisodes de « ${p.title} »", Icons.Default.FiberManualRecord) {
                        vm.recordSeries(c, p) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    }
                },
                MenuOption("Ajouter au multi-écran", Icons.Default.GridView) {
                    vm.addToMultiview(c)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    nav.navigate(MultiviewRoute)
                },
            ),
            onDismiss = { channelMenu = null },
        )
    }
    kidsMenu?.let { cat ->
        OptionsDialog("« ${cat.name} » pour les enfants", com.sokoul.iptv.ui.screens.profiles.kidsAccessOptions { vm.setKids(cat.id, it) }, onDismiss = { kidsMenu = null })
    }
    categoryMenu?.let { cat ->
        OptionsDialog(
            cat.name,
            listOf(
                MenuOption("Masquer ce groupe", Icons.Default.VisibilityOff) { vm.hideCategory(cat.id) },
                MenuOption("Profil Enfant…", Icons.Default.ChildCare) { kidsMenu = cat },
                if (cat.locked) MenuOption("Déverrouiller", Icons.Default.LockOpen) {
                    if (vm.isUnlocked()) vm.setLocked(cat.id, false) else pinFor = cat.copy(name = "unlock:" + cat.id)
                } else MenuOption("Verrouiller (contrôle parental)", Icons.Default.Lock) {
                    scope.launch {
                        if (vm.hasPin()) vm.setLocked(cat.id, true)
                        else Toast.makeText(context, "Définissez d'abord un code dans Réglages › Contrôle parental", Toast.LENGTH_LONG).show()
                    }
                },
            ),
            subtitle = "Les groupes masqués se réaffichent depuis Réglages › Groupes",
            onDismiss = { categoryMenu = null },
        )
    }
    pinFor?.let { cat ->
        PinDialog(
            "Code parental",
            onResult = { pin ->
                scope.launch {
                    val r = vm.unlock(pin)
                    if (r.ok) {
                        if (cat.name.startsWith("unlock:")) vm.setLocked(cat.id, false) else vm.selected.value = cat.id
                        pinFor = null; pinError = null
                    } else pinError = r.message
                }
            },
            onDismiss = { pinFor = null; pinError = null },
            error = pinError,
        )
    }
}

@Composable
private fun ChannelList(
    channels: List<ChannelEntity>?,
    nowPlaying: Map<String, ProgramEntity>,
    favorites: Set<String>,
    onFocus: (ChannelEntity) -> Unit,
    onClick: (ChannelEntity) -> Unit,
    onLongClick: (ChannelEntity) -> Unit,
    restoreId: String? = null,
    onRestored: () -> Unit = {},
) {
    when {
        channels == null -> com.sokoul.iptv.ui.components.ChannelListSkeleton()
        channels.isEmpty() -> EmptyState(Icons.Default.LiveTv, "Aucune chaîne", if (com.sokoul.iptv.ui.theme.LocalLayout.current.isTouch) "Ce groupe est vide. Ajoutez des favoris avec un appui long sur une chaîne." else "Ce groupe est vide. Ajoutez des favoris avec un appui long (ou la touche Menu) sur une chaîne.")
        else -> {
            val now = rememberNow()
            val state = rememberLazyListState()
            val restore = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(channels.firstOrNull()?.id) {
                if (restoreId == null) state.scrollToItem(0)
            }
            LaunchedEffect(channels) {
                val index = restoreId?.let { id -> channels.indexOfFirst { it.id == id } } ?: -1
                if (index >= 0) {
                    state.scrollToItem(index)
                    androidx.compose.runtime.withFrameNanos { }
                    runCatching { restore.requestFocus() }
                    onRestored()
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().focusRestorer(),
                state = state,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { _, c ->
                    ChannelRow(
                        c, nowPlaying[c.id], c.id in favorites, now, { onFocus(c) }, { onClick(c) }, { onLongClick(c) },
                        if (c.id == restoreId) Modifier.focusRequester(restore) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    c: ChannelEntity,
    now: ProgramEntity?,
    favorite: Boolean,
    time: Long,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(style = FocusStyle.Row,
        onClick = onClick, onLongClick = onLongClick, onFocus = onFocus,
        modifier = modifier.fillMaxWidth(), color = Color.Transparent,
        shape = Radius.Card,
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.number.toString(), Modifier.width(44.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = if (focused) SokoulColors.Accent else SokoulColors.TextMuted,
            )
            ChannelLogo(c.logo, c.name, Modifier.size(64.dp, 40.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (favorite) Icon(Icons.Default.Star, null, Modifier.padding(start = 6.dp).size(14.dp), tint = SokoulColors.Warning)
                    if (c.catchupType != null) Icon(Icons.Default.History, "Replay", Modifier.padding(start = 6.dp).size(14.dp), tint = SokoulColors.TextMuted)
                }
                if (now != null) {
                    Text(now.title, style = com.sokoul.iptv.ui.theme.Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val f = ((time - now.start).toFloat() / (now.end - now.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    com.sokoul.iptv.ui.components.ProgressLine(f, Modifier.padding(top = 4.dp).fillMaxWidth(0.6f), thickness = 3.dp, onImage = false)
                }
            }
            if (now != null) Text(formatClock(now.end), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted)
        }
    }
}

@Composable
private fun DetailsPanel(
    details: Pair<ChannelEntity, NowNext>?,
    preview: LivePreview.Current?,
    modifier: Modifier,
    supportsCatchup: (ChannelEntity) -> Boolean,
) {
    val time = rememberNow()
    Column(modifier.background(Brush.horizontalGradient(listOf(SokoulColors.Surface.copy(alpha = 0.55f), SokoulColors.Surface))).padding(start = 26.dp, end = 30.dp, top = 30.dp, bottom = 20.dp)) {
        val (c, nn) = details ?: run {
            Text("Choisissez une chaîne", color = SokoulColors.TextMuted)
            return@Column
        }
        val live = preview?.takeIf { it.channel.id == c.id }
        if (live != null) {
            // aperçu sans le son ; le logo reprend sa place dès que l'aperçu s'arrête
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(com.sokoul.iptv.ui.theme.Radius.Card).background(SokoulColors.VideoBlack)) {
                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                            isFocusable = false
                            isFocusableInTouchMode = false
                        }
                    },
                    update = { it.player = live.player.player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            ChannelLogo(c.logo, c.name, Modifier.size(150.dp, 84.dp))
        }
        Text(c.name, Modifier.padding(top = 14.dp), style = com.sokoul.iptv.ui.theme.Type.Section, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("N° ${c.number}")
            if (supportsCatchup(c)) Tag("Replay ${if (c.catchupDays > 0) "${c.catchupDays} j" else ""}".trim(), SokoulColors.AccentSoft, SokoulColors.Accent)
        }
        Spacer(Modifier.height(20.dp))
        val now = nn.now
        if (now != null) {
            val art = now.icon?.takeIf(String::isNotBlank)
            if (art != null && live == null) {
                // image du programme fournie par le guide
                com.sokoul.iptv.ui.components.NetImage(art, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(com.sokoul.iptv.ui.theme.Radius.Card))
                Spacer(Modifier.height(14.dp))
            }
            Text("EN CE MOMENT", style = com.sokoul.iptv.ui.theme.Type.Kicker.copy(color = SokoulColors.Live))
            Text(now.title, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatRange(now.start, now.end) + (now.episode?.let { "  ·  $it" } ?: ""), style = com.sokoul.iptv.ui.theme.Type.Meta)
            com.sokoul.iptv.ui.components.ProgressLine(nn.progress(time), Modifier.padding(vertical = 8.dp), tone = com.sokoul.iptv.ui.components.ProgressTone.Live, onImage = false)
            now.description?.let { Text(it, color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = if (art != null) 3 else 7, overflow = TextOverflow.Ellipsis) }
        } else {
            Text("Pas d'information de programme", color = SokoulColors.TextMuted)
        }
        nn.next?.let { next ->
            Spacer(Modifier.height(18.dp))
            Text("ENSUITE · ${formatClock(next.start)}", style = com.sokoul.iptv.ui.theme.Type.Overline)
            Text(next.title, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        com.sokoul.iptv.ui.components.KeyHints(listOf(com.sokoul.iptv.ui.components.KeyHint("OK", "Regarder"), com.sokoul.iptv.ui.components.KeyHint("Menu", "Options")))
    }
}
