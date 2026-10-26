package com.sokoul.iptv.ui.screens.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.GlassPanel
import com.sokoul.iptv.ui.screens.player.LiveGroup
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.formatClock

/**
 * Bandeau de zapping : la chaîne visée s'affiche dès l'appui. La jauge du bas se remplit pendant
 * [ZAP_SETTLE_MS] : l'utilisateur voit que l'appui est pris en compte avant l'ouverture du flux.
 */
@Composable
fun ZapBanner(c: ChannelEntity, number: String?, modifier: Modifier = Modifier) {
    val fill = remember(c.id, number) { Animatable(0f) }
    LaunchedEffect(c.id, number) {
        fill.animateTo(1f, tween(if (number != null) NUMBER_SETTLE_MS.toInt() else ZAP_SETTLE_MS.toInt(), easing = LinearEasing))
    }
    GlassPanel(modifier.widthIn(min = 360.dp, max = 560.dp)) {
        Column {
            Row(Modifier.padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    number ?: c.number.toString(), Modifier.widthIn(min = 64.dp),
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (number != null) SokoulColors.Accent else SokoulColors.TextPrimary,
                )
                ChannelLogo(c.logo, c.name, Modifier.padding(start = Space.s).size(84.dp, 50.dp))
                Text(
                    c.name, Modifier.padding(start = Space.m).weight(1f, fill = false),
                    style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // jauge de 2 dp : appui pris en compte, ouverture imminente
            Box(Modifier.fillMaxWidth().height(2.dp).background(SokoulColors.white(0.12f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth().graphicsLayer {
                    scaleX = fill.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }.background(SokoulColors.Accent))
            }
        }
    }
}

