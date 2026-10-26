package com.sokoul.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.SokoulMark
import com.sokoul.iptv.ui.components.onChip
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.rememberNow
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import kotlinx.coroutines.delay
import com.sokoul.iptv.ui.nav.navigateOnce
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope

/** Hauteur réservée en haut des pages de l'univers Streaming quand les onglets sont visibles. */
val TopTabsHeight = 76.dp

/** Délai d'arrêt sur un onglet de contenu avant d'ouvrir sa page (traverser la barre ne charge rien). */
const val TAB_SETTLE_MS = 320L

/** Pages de l'univers Streaming (TV) : elles ont les onglets en haut. */
val StreamingDests = listOf(Dest.HOME, Dest.MOVIES, Dest.SERIES, Dest.MY_LIST, Dest.SAGAS)

/**
 * Lien entre les onglets (Shell) et la page affichée :
 * - [deep] : la page est descendue dans ses rangées → les onglets s'effacent ;
 * - [backToTop] : ce que fait Retour depuis les rangées (remonter au héros) ;
 * - [arrivedByTab] : la page vient d'être ouverte depuis un onglet → elle ne prend pas le focus.
 */
@Stable
class TopBarState(val arrivedByTab: Boolean) {
    var deep by mutableStateOf(false)
    var tabsFocused by mutableStateOf(false)
    var backToTop: (() -> Boolean)? = null
    /**
     * Cible de ▼ depuis les onglets. Nécessaire : la page plein écran commence sous les onglets
     * (en haut de l'écran), la recherche de focus « vers le bas » ne la trouverait pas.
     * null = page sans élément focalisable pour l'instant.
     */
    var down: (() -> FocusRequester?)? = null
}

val LocalTopBar = staticCompositionLocalOf<TopBarState?> { null }

/** Passe l'information « ouverte depuis un onglet » d'une page à la suivante (chaque page a son Shell). */
internal object TabHandoff {
    @Volatile var pending = false
    fun consume(): Boolean = pending.also { pending = false }
}

/**
 * Univers Streaming sur TV : contenu plein écran, onglets fins en haut qui s'effacent quand on
 * descend dans les rangées. Retour : rangées → héros → onglets → Accueil → sortie de l'app.
 */
@Composable
fun StreamingShell(nav: NavHostController, current: Dest, content: @Composable () -> Unit) {
    val bar = remember { TopBarState(arrivedByTab = TabHandoff.consume()) }
    val tabsFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalTopBar provides bar) { content() }
        TopTabs(
            current = current,
            visible = bar.tabsFocused || !bar.deep,
            selectedFocus = tabsFocus,
            onFocusChanged = { bar.tabsFocused = it },
            onTab = { d -> if (d != current) { TabHandoff.pending = true; nav.goTo(d) } },
            // 📺 TV : l'univers TV s'ouvre sur la dernière chaîne, en plein écran
            onAction = { d ->
                when (d) {
                    Dest.LIVE -> nav.navigateOnce(com.sokoul.iptv.ui.nav.TvRoute)
                    Dest.SEARCH -> nav.openSearch(current.searchOrigin())
                    else -> nav.goTo(d)
                }
            },
            downTarget = { bar.down?.invoke() ?: FocusRequester.Default },
        )
    }
    if (bar.arrivedByTab) LaunchedEffect(Unit) {
        // la page précédente (et ses onglets) vient d'être remplacée : le focus reste sur l'onglet
        repeat(20) { withFrameNanos { }; if (runCatching { tabsFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    BackHandler(enabled = !bar.tabsFocused || current != Dest.HOME) {
        when {
            bar.tabsFocused -> nav.goTo(Dest.HOME)
            bar.deep && bar.backToTop?.invoke() == true -> Unit
            // onglets masqués : on les réaffiche, puis focus à l'image suivante (ils redeviennent focalisables)
            else -> scope.launch { bar.deep = false; withFrameNanos { }; withFrameNanos { }; runCatching { tabsFocus.requestFocus() } }
        }
    }
}

@Composable
private fun TopTabs(
    current: Dest,
    visible: Boolean,
    selectedFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onTab: (Dest) -> Unit,
    onAction: (Dest) -> Unit,
    downTarget: () -> FocusRequester,
) {
    val l = LocalLayout.current
    val reduced = LocalReducedMotion.current
    // lu dans les focusProperties : un état observé, sinon Compose ne réévalue pas canFocus
    val open = rememberUpdatedState(visible)
    // ▼ depuis n'importe quel onglet : la cible fournie par la page
    val toContent = Modifier.focusProperties { down = downTarget() }
    val shown by animateFloatAsState(if (visible) 1f else 0f, if (reduced) snap() else tween(Motion.MEDIUM, easing = Motion.Emphasized), label = "tabs")
    // onglet sous le focus : la page ne s'ouvre qu'une fois le curseur posé
    var settling by remember { mutableStateOf<Dest?>(null) }
    LaunchedEffect(settling) {
        val d = settling ?: return@LaunchedEffect
        delay(TAB_SETTLE_MS)
        if (settling == d) onTab(d)
    }
    // effacement terminé : plus rien à focaliser ni à dessiner
    if (!visible && shown == 0f) return
    Box(
        Modifier.fillMaxWidth().height(TopTabsHeight + Space.l)
            .graphicsLayer { alpha = shown; translationY = -(1f - shown) * 24.dp.toPx() }
            // voile de lisibilité au-dessus du héros
            .background(Brush.verticalGradient(listOf(SokoulColors.bg(0.85f), SokoulColors.bg(0.45f), Color.Transparent)))
    ) {
        // trois zones : marque à gauche, rubriques centrées dans l'espace libre (jamais sous les actions), actions et heure à droite
        Row(
            Modifier.fillMaxWidth().padding(start = l.margin, end = l.margin, top = Space.l)
                .onFocusChanged { onFocusChanged(it.hasFocus); if (!it.hasFocus) settling = null }
                // onglets masqués (rangées) : ▲ ne doit pas y entrer
                .focusProperties { enter = { if (open.value) selectedFocus else FocusRequester.Cancel } }
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SokoulMark(size = 30.dp)
            Row(Modifier.weight(1f).padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterHorizontally)) {
                StreamingDests.forEach { d ->
                    TextTab(
                        d, selected = d == current, visible = open,
                        modifier = toContent.then(if (d == current) Modifier.focusRequester(selectedFocus) else Modifier),
                        onFocus = { settling = d },
                        onClick = { settling = null; onTab(d) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                IconTab(Icons.Default.Search, "Recherche", open, toContent, onFocus = { settling = null }) { onAction(Dest.SEARCH) }
                TvSwitch(open, toContent, onFocus = { settling = null }) { onAction(Dest.LIVE) }
                IconTab(Icons.Default.Settings, "Réglages", open, toContent, onFocus = { settling = null }) { onAction(Dest.SETTINGS) }
                Text(
                    formatClock(rememberNow(15_000)), Modifier.padding(start = Space.s),
                    style = MaterialTheme.typography.titleMedium, color = SokoulColors.TextSecondary, maxLines = 1,
                )
            }
        }
    }
}

/**
 * Onglet de contenu : texte seul. L'onglet de la page affichée est souligné d'un trait d'accent rectangulaire
 * (même signe que le liseré sous la carte focalisée) ; au focus, fond blanc et texte sombre.
 */
@Composable
private fun TextTab(d: Dest, selected: Boolean, visible: State<Boolean>, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, onFocus = onFocus, style = FocusStyle.Chip, color = Color.Transparent,
        modifier = modifier.focusProperties { canFocus = visible.value },
    ) { focused ->
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                d.label, maxLines = 1, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, letterSpacing = 0.2.sp),
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
                color = when { focused -> SokoulColors.OnLight; selected -> SokoulColors.TextPrimary; else -> SokoulColors.TextSecondary },
            )
            Box(
                Modifier.padding(top = 4.dp).size(20.dp, 2.dp)
                    .background(if (selected && !focused) SokoulColors.Accent else Color.Transparent, Radius.None)
            )
        }
    }
}

/** Action (recherche, réglages) : il faut appuyer sur OK, le simple passage ne fait rien. */
@Composable
private fun IconTab(icon: ImageVector, label: String, visible: State<Boolean>, extra: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, onFocus = onFocus, style = FocusStyle.Chip, color = Color.Transparent,
        modifier = extra.size(44.dp).focusProperties { canFocus = visible.value }.semantics { contentDescription = label },
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.TextSecondary)
        }
    }
}

/** Passage à l'univers TV : bouton distinct (OK obligatoire), jamais déclenché au simple passage. */
@Composable
private fun TvSwitch(visible: State<Boolean>, extra: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, onFocus = onFocus, style = FocusStyle.Chip, color = SokoulColors.GlassLight,
        modifier = extra.padding(end = Space.s).focusProperties { canFocus = visible.value }.semantics { contentDescription = "Passer à la TV en direct" },
    ) { focused ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(20.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.Live)
            Text("TV", Modifier.padding(start = Space.s), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = onChip(focused))
        }
    }
}
