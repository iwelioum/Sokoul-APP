package com.sokoul.iptv.ui.screens.home

import com.sokoul.iptv.ui.theme.SokoulColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.debrid.DebridCloud
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.browse.BrowseScreen
import com.sokoul.iptv.ui.screens.browse.HomeBrowseViewModel
import com.sokoul.iptv.ui.theme.LocalLayout

/** Accueil : héros du moment, reprise, chaînes favorites, Top 10, nouveautés, sagas… */
@Composable
fun HomeScreen(nav: NavHostController) {
    BrowseScreen(nav, hiltViewModel<HomeBrowseViewModel>(), "Accueil")
}

fun resumeRoute(pid: Long, p: ProgressEntity): Any {
    // reprises Real-Debrid : fichier du cloud (« rdc:ID ») ou titre lu via RD (« tmdb:m:603 », « tmdb:s:1399:1:2 »)
    if (p.itemId.startsWith(DebridCloud.KEY_PREFIX))
        return PlayerRoute(PlayKind.CLOUD, pid, p.itemId.removePrefix(DebridCloud.KEY_PREFIX), title = p.title)
    if (p.itemId.startsWith("tmdb:")) DebridQuery.decode(p.itemId.removePrefix("tmdb:"))?.let { q ->
        return PlayerRoute(PlayKind.DEBRID, pid, q.encode(), query = q.encode(), title = p.title)
    }
    return when (p.type) {
        ContentType.MOVIE -> PlayerRoute(PlayKind.MOVIE, pid, p.itemId)
        ContentType.SERIES -> PlayerRoute(PlayKind.EPISODE, pid, p.itemId, parentId = p.parentId)
        ContentType.LIVE -> PlayerRoute(PlayKind.LIVE, pid, p.itemId)
    }
}

@Composable
fun ChannelCard(c: ChannelEntity, now: ProgramEntity?, onClick: () -> Unit) {
    val w = (LocalLayout.current.landscape * 0.72f)
    Column(Modifier.width(w)) {
        FocusSurface(onClick = onClick) { _ ->
            Column {
                val art = now?.icon?.takeIf(String::isNotBlank)
                Box(
                    Modifier.fillMaxWidth().height(w * 0.5f)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(SokoulColors.SurfaceHigher, SokoulColors.PlaceholderBottom))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) {
                        // image du programme en cours (guide), logo de la chaîne en petit dans le coin
                        com.sokoul.iptv.ui.components.NetImage(art, null, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.45f to androidx.compose.ui.graphics.Color.Transparent, 1f to SokoulColors.Scrim)))
                        ChannelLogo(c.logo, c.name, Modifier.align(Alignment.BottomStart).padding(8.dp).size(w * 0.26f, w * 0.13f))
                    } else {
                        ChannelLogo(c.logo, c.name, Modifier.size(w * 0.7f, w * 0.36f))
                    }
                }
                if (now != null) {
                    val t = System.currentTimeMillis()
                    val f = ((t - now.start).toFloat() / (now.end - now.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    com.sokoul.iptv.ui.components.ProgressLine(f, tone = com.sokoul.iptv.ui.components.ProgressTone.Live, thickness = 3.dp, track = SokoulColors.white(0.2f))
                }
            }
        }
        Text(c.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(now?.title ?: "—", style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
