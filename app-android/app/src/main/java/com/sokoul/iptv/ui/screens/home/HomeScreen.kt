package com.sokoul.iptv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.browse.BrowseScreen
import com.sokoul.iptv.ui.screens.browse.HomeBrowseViewModel
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors

/** Accueil : héros du moment, reprise, chaînes favorites, Top 10, nouveautés, sagas… */
@Composable
fun HomeScreen(nav: NavHostController) {
    BrowseScreen(nav, hiltViewModel<HomeBrowseViewModel>(), "Accueil")
}

fun resumeRoute(pid: Long, p: ProgressEntity): Any = when (p.type) {
    ContentType.MOVIE -> PlayerRoute(PlayKind.MOVIE, pid, p.itemId)
    ContentType.SERIES -> PlayerRoute(PlayKind.EPISODE, pid, p.itemId, parentId = p.parentId)
    ContentType.LIVE -> PlayerRoute(PlayKind.LIVE, pid, p.itemId)
}

@Composable
fun ChannelCard(c: ChannelEntity, now: ProgramEntity?, onClick: () -> Unit) {
    val w = (LocalLayout.current.landscape * 0.72f)
    Column(Modifier.width(w)) {
        FocusSurface(onClick = onClick, focusScale = 1.06f, borderWidth = 2.dp) { _ ->
            Column {
                Box(Modifier.fillMaxWidth().height(w * 0.5f).background(SokoulColors.SurfaceHigher), contentAlignment = Alignment.Center) {
                    ChannelLogo(c.logo, c.name, Modifier.size(w * 0.7f, w * 0.36f))
                }
                if (now != null) {
                    val t = System.currentTimeMillis()
                    val f = ((t - now.start).toFloat() / (now.end - now.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(SokoulColors.Live))
                    }
                }
            }
        }
        Text(c.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(now?.title ?: "—", style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
