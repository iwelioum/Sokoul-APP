package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.Screen
import com.sokoul.desktop.data.PlaylistType
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(app: AppState) {
    val playlist = app.playlist ?: return
    val sync by app.sync.state.collectAsState()
    val scope = rememberCoroutineScope()
    val channels = app.db.channelCount(playlist.id)
    val programs = app.db.programCount(playlist.id)
    val groups = app.db.categories(playlist.id).count { !it.hidden }
    val movies = app.db.movieCount(playlist.id)
    val series = app.db.seriesCount(playlist.id)

    Column(
        Modifier.fillMaxSize().background(SokoulColors.Background).verticalScroll(rememberScrollState()).padding(34.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bonjour", color = SokoulColors.TextSecondary)
                Text("Votre télévision, autrement.", color = SokoulColors.TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
            }
            SokoulButton("Réglages", { app.screen = Screen.SETTINGS }, primary = false, icon = Icons.Default.Settings)
        }

        HeroBanner(
        title = "Retrouvez vos chaînes et programmes",
        eyebrow = "Contenu en direct",
        description = "Une expérience plus claire pour lancer le Direct, parcourir le guide et retrouver votre installation.",
        onPrimary = { app.screen = Screen.LIVE },
        onSecondary = { app.screen = Screen.GUIDE },
        )

        Text("Votre installation", color = SokoulColors.TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeStat("Chaînes", channels.toString(), Icons.Default.LiveTv)
            HomeStat("Groupes", groups.toString(), Icons.Default.CalendarMonth)
            HomeStat("Programmes", programs.toString(), Icons.Default.Refresh)
            HomeStat("VOD", "${movies + series}", Icons.Default.Movie)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SokoulButton("Parcourir les films", { app.screen = Screen.MOVIES }, primary = false)
            SokoulButton("Parcourir les séries", { app.screen = Screen.SERIES }, primary = false)
        }

        Row(Modifier.fillMaxWidth().background(SokoulColors.Surface, Radius.Panel).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(playlist.name, color = SokoulColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "${if (playlist.type == PlaylistType.XTREAM) "Xtream Codes" else "Playlist M3U"} · ${
                        if (sync.running) sync.step else if (playlist.syncedAt > 0) "Synchronisée ${clock(playlist.syncedAt)}" else "À synchroniser"
                    }",
                    color = if (sync.error != null) SokoulColors.Warning else SokoulColors.TextSecondary,
                )
            }
            SokoulButton("Mettre à jour", { scope.launch { app.sync.sync(playlist); app.reloadPlaylist() } }, primary = false, icon = Icons.Default.Refresh, enabled = !sync.running)
        }
    }
}

@Composable
private fun HomeStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier.width(170.dp).background(SokoulColors.Surface, Radius.Panel).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = SokoulColors.Accent)
        Text(value, color = SokoulColors.TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text(label, color = SokoulColors.TextSecondary)
    }
}
