package com.sokoul.iptv.ui

import com.sokoul.iptv.ui.components.SokoulMark
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.theme.Radius
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.nav.GuideRoute
import com.sokoul.iptv.ui.nav.HomeRoute
import com.sokoul.iptv.ui.nav.LiveRoute
import com.sokoul.iptv.ui.nav.MoviesRoute
import com.sokoul.iptv.ui.nav.RecordingsRoute
import com.sokoul.iptv.ui.nav.SearchRoute
import com.sokoul.iptv.ui.nav.navigateOnce
import com.sokoul.iptv.ui.nav.SeriesRoute
import com.sokoul.iptv.ui.nav.MyListRoute
import androidx.compose.material.icons.filled.BookmarkBorder
import com.sokoul.iptv.ui.nav.SettingsRoute
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.WindowClass

/** Rubriques principales, dans l'ordre du menu (recherche en tête, comme sur les plateformes). */
enum class Dest(val label: String, val icon: ImageVector, val route: Any) {
    SEARCH("Recherche", Icons.Default.Search, SearchRoute()),
    HOME("Accueil", Icons.Default.Home, HomeRoute),
    MOVIES("Films", Icons.Default.Movie, MoviesRoute),
    SERIES("Séries", Icons.Default.VideoLibrary, SeriesRoute),
    MY_LIST("Ma liste", Icons.Default.BookmarkBorder, MyListRoute),
    LIVE("Direct", Icons.Default.LiveTv, LiveRoute),
    GUIDE("Guide TV", Icons.Default.CalendarViewWeek, GuideRoute),
    RECORDINGS("Enregistrements", Icons.Default.FiberManualRecord, RecordingsRoute),
    SETTINGS("Réglages", Icons.Default.Settings, SettingsRoute),
}

/** Page d'origine de la recherche, pour son périmètre de départ. */
fun Dest.searchOrigin(): String = when (this) {
    Dest.HOME, Dest.MY_LIST -> com.sokoul.iptv.data.repo.SearchOrigin.HOME
    Dest.MOVIES -> com.sokoul.iptv.data.repo.SearchOrigin.MOVIES
    Dest.SERIES -> com.sokoul.iptv.data.repo.SearchOrigin.SERIES
    Dest.LIVE, Dest.GUIDE, Dest.RECORDINGS -> com.sokoul.iptv.data.repo.SearchOrigin.TV
    else -> com.sokoul.iptv.data.repo.SearchOrigin.ALL
}

/** Recherche contextuelle : empilée au-dessus de la page d'origine (Retour y ramène, focus compris). */
fun NavHostController.openSearch(origin: String) = navigateOnce(SearchRoute(origin))

fun NavHostController.goTo(dest: Dest) {
    navigate(dest.route) {
        popUpTo(HomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Grand écran ou TV : barre latérale. Téléphone : barre du bas. */
@Composable
fun useSideRail(): Boolean = LocalLayout.current.window != WindowClass.COMPACT

/** [bare] : page secondaire d'une rubrique (catégories du fournisseur) — sur TV, ni onglets ni menu. */
@Composable
fun Shell(nav: NavHostController, current: Dest, edgeToEdge: Boolean = false, bare: Boolean = false, content: @Composable () -> Unit) {
    val layout = LocalLayout.current
    when (layout.window) {
        // TV : univers Streaming = onglets en haut ; recherche et réglages = plein écran, Retour ramène
        WindowClass.TV -> when (current) {
            in StreamingDests -> if (bare) Box(Modifier.fillMaxSize()) { content() } else StreamingShell(nav, current, content)
            // recherche, réglages et univers TV (guide, enregistrements) : plein écran, Retour ramène
            else -> Box(Modifier.fillMaxSize()) { content() }
        }
        WindowClass.MEDIUM, WindowClass.EXPANDED -> Row(Modifier.fillMaxSize()) {
            TabletRail(current) { nav.goTo(it) }
            Box(Modifier.fillMaxSize().then(if (edgeToEdge) Modifier else Modifier.statusBarsPadding())) { content() }
        }
        WindowClass.COMPACT -> PhoneShell(nav, current, edgeToEdge, content)
    }
}

/** Tablette : rail fixe avec libellés. */
@Composable
private fun TabletRail(current: Dest, onSelect: (Dest) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(88.dp).background(SokoulColors.Surface).statusBarsPadding().navigationBarsPadding().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SokoulMark(Modifier.padding(bottom = 10.dp), size = 36.dp)
        Dest.entries.forEach { d ->
            val selected = d == current
            if (d == Dest.RECORDINGS) Spacer(Modifier.weight(1f))
            FocusSurface(style = FocusStyle.Row,
                onClick = { onSelect(d) },
                modifier = Modifier.size(76.dp, 58.dp),
                color = Color.Transparent,
                shape = Radius.Card,
            ) { focused ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(52.dp, 30.dp).background(if (selected) SokoulColors.AccentSoft else Color.Transparent, Radius.Card),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(d.icon, d.label, Modifier.size(22.dp), tint = if (selected || focused) SokoulColors.Accent else SokoulColors.TextSecondary)
                    }
                    Text(
                        d.label, fontSize = 10.5.sp, maxLines = 1, textAlign = TextAlign.Center,
                        color = if (selected || focused) SokoulColors.TextPrimary else SokoulColors.TextMuted,
                    )
                }
            }
        }
    }
}

/** Téléphone : barre du bas (Accueil, Films, Séries, Direct, Plus). */
@Composable
private fun PhoneShell(nav: NavHostController, current: Dest, edgeToEdge: Boolean, content: @Composable () -> Unit) {
    var more by remember { mutableStateOf(false) }
    val main = listOf(Dest.HOME, Dest.MOVIES, Dest.SERIES, Dest.LIVE)
    Column(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        // écrans à héros : bord à bord, l'image passe sous la barre d'état
        Box(Modifier.weight(1f).fillMaxWidth().then(if (edgeToEdge) Modifier else Modifier.statusBarsPadding())) { content() }
        NavigationBar(containerColor = SokoulColors.Surface, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding().height(64.dp)) {
            val colors = NavigationBarItemDefaults.colors(
                indicatorColor = SokoulColors.AccentSoft, selectedIconColor = SokoulColors.TextPrimary, selectedTextColor = SokoulColors.TextPrimary,
                unselectedIconColor = SokoulColors.TextMuted, unselectedTextColor = SokoulColors.TextMuted,
            )
            main.forEach { d ->
                NavigationBarItem(
                    selected = d == current, onClick = { nav.goTo(d) },
                    icon = { Icon(d.icon, d.label, Modifier.size(22.dp)) },
                    label = { Text(d.label, fontSize = 10.5.sp, maxLines = 1) }, colors = colors,
                )
            }
            NavigationBarItem(
                selected = current !in main, onClick = { more = true },
                icon = { Icon(Icons.Default.MoreHoriz, "Plus", Modifier.size(22.dp)) },
                label = { Text("Plus", fontSize = 10.5.sp) }, colors = colors,
            )
        }
    }
    if (more) {
        OptionsDialog(
            "Plus",
            listOf(Dest.SEARCH, Dest.GUIDE, Dest.RECORDINGS, Dest.SETTINGS).map { d -> MenuOption(d.label, d.icon, d == current) { nav.goTo(d) } },
            onDismiss = { more = false },
        )
    }
}
