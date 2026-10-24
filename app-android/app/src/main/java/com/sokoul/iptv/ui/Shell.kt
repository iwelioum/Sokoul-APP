package com.sokoul.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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
import com.sokoul.iptv.ui.nav.SeriesRoute
import com.sokoul.iptv.ui.nav.SettingsRoute
import com.sokoul.iptv.ui.theme.LocalIsTv
import com.sokoul.iptv.ui.theme.SokoulColors

enum class Dest(val label: String, val icon: ImageVector, val route: Any) {
    HOME("Accueil", Icons.Default.Home, HomeRoute),
    LIVE("Direct", Icons.Default.LiveTv, LiveRoute),
    GUIDE("Guide", Icons.Default.CalendarViewWeek, GuideRoute),
    MOVIES("Films", Icons.Default.Movie, MoviesRoute),
    SERIES("Séries", Icons.Default.VideoLibrary, SeriesRoute),
    SEARCH("Recherche", Icons.Default.Search, SearchRoute),
    RECORDINGS("Enregistr.", Icons.Default.FiberManualRecord, RecordingsRoute),
    SETTINGS("Réglages", Icons.Default.Settings, SettingsRoute),
}

fun NavHostController.goTo(dest: Dest) {
    navigate(dest.route) {
        popUpTo(HomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Grand écran ou TV : barre latérale. Téléphone en portrait : barre du bas. */
@Composable
fun useSideRail(): Boolean = LocalIsTv.current || LocalConfiguration.current.screenWidthDp >= 720

@Composable
fun Shell(nav: NavHostController, current: Dest, content: @Composable () -> Unit) {
    if (useSideRail()) {
        Row(Modifier.fillMaxSize().background(SokoulColors.Background)) {
            SideRail(current) { nav.goTo(it) }
            Box(Modifier.fillMaxSize().statusBarsPadding()) { content() }
        }
    } else {
        var more by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().background(SokoulColors.Background)) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) { content() }
            val main = listOf(Dest.HOME, Dest.LIVE, Dest.GUIDE, Dest.MOVIES, Dest.SERIES)
            NavigationBar(containerColor = SokoulColors.Surface, modifier = Modifier.navigationBarsPadding()) {
                main.forEach { d ->
                    NavigationBarItem(
                        selected = d == current,
                        onClick = { nav.goTo(d) },
                        icon = { Icon(d.icon, d.label) },
                        label = { Text(d.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = SokoulColors.AccentSoft, selectedIconColor = SokoulColors.Accent),
                    )
                }
                NavigationBarItem(
                    selected = current !in main,
                    onClick = { more = true },
                    icon = { Icon(Icons.Default.MoreHoriz, "Plus") },
                    label = { Text("Plus", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = SokoulColors.AccentSoft, selectedIconColor = SokoulColors.Accent),
                )
            }
        }
        if (more) {
            OptionsDialog(
                "Plus",
                listOf(Dest.SEARCH, Dest.RECORDINGS, Dest.SETTINGS).map { d -> MenuOption(d.label, d.icon, d == current) { nav.goTo(d) } },
                onDismiss = { more = false },
            )
        }
    }
}

@Composable
private fun SideRail(current: Dest, onSelect: (Dest) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(92.dp).background(SokoulColors.Surface).padding(vertical = 20.dp)
            .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("SOKOUL", color = SokoulColors.Accent, fontSize = 13.sp, letterSpacing = 2.sp, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.size(14.dp))
        Dest.entries.forEach { d ->
            val selected = d == current
            FocusSurface(
                onClick = { onSelect(d) },
                modifier = Modifier.size(76.dp, 64.dp),
                color = if (selected) SokoulColors.AccentSoft else Color.Transparent,
                focusedColor = SokoulColors.SurfaceHigher,
                shape = RoundedCornerShape(14.dp),
                focusScale = 1.04f,
                borderWidth = 2.dp,
            ) { focused ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(d.icon, d.label, Modifier.size(24.dp), tint = if (selected || focused) SokoulColors.Accent else SokoulColors.TextSecondary)
                    Text(
                        d.label, fontSize = 10.5.sp, maxLines = 1, textAlign = TextAlign.Center,
                        color = if (selected || focused) SokoulColors.TextPrimary else SokoulColors.TextMuted,
                    )
                }
            }
        }
    }
}
