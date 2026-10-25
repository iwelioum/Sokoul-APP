package com.sokoul.iptv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import kotlinx.coroutines.delay

/**
 * Panneau unique « Audio et sous-titres » (glisse depuis la droite) : deux colonnes, ◀ ▶ pour passer
 * de l'une à l'autre, OK pour choisir. Le panneau reste ouvert après un choix (Retour pour fermer),
 * pour régler audio puis sous-titres d'un seul geste.
 */
@Composable
fun TracksPanel(audio: List<TrackOption>, text: List<TrackOption>, modifier: Modifier = Modifier, onPick: (type: Int, TrackOption) -> Unit) {
    val l = LocalLayout.current
    val audioEnter = remember { FocusRequester() }
    val textEnter = remember { FocusRequester() }
    val audioList = audio.ifEmpty { listOf(TrackOption("Piste unique", null, -1, true)) }
    Column(
        modifier.fillMaxHeight().fillMaxWidth(if (l.isCompact) 1f else 0.55f).widthIn(max = 760.dp)
            .background(Brush.horizontalGradient(listOf(SokoulColors.Panel.copy(alpha = 0.9f), SokoulColors.Panel)))
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Text("Audio et sous-titres", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 18.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TrackColumn("Audio", Icons.Default.Audiotrack, audioList, audioEnter, Modifier.weight(1f)) { if (it.group != null) onPick(C.TRACK_TYPE_AUDIO, it) }
            TrackColumn("Sous-titres", Icons.Default.ClosedCaption, text, textEnter, Modifier.weight(1f)) { onPick(C.TRACK_TYPE_TEXT, it) }
        }
        if (l.isTv) {
            Text("◀ ▶ changer de colonne  ·  OK choisir  ·  Retour fermer", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted)
        }
    }
    // ouverture : focus sur la piste audio en cours
    LaunchedEffect(Unit) { delay(60); runCatching { audioEnter.requestFocus() } }
}

@Composable
private fun TrackColumn(title: String, icon: ImageVector, options: List<TrackOption>, entry: FocusRequester, modifier: Modifier, onPick: (TrackOption) -> Unit) {
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = SokoulColors.TextSecondary)
            Text(title.uppercase(), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextSecondary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        // ◀ ▶ entre les colonnes : on arrive toujours sur la piste sélectionnée, pas sur la plus proche
        LazyColumn(
            Modifier.focusProperties { enter = { entry } }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(options) { i, o ->
                FocusSurface(
                    onClick = { onPick(o) },
                    modifier = Modifier.fillMaxWidth().then(if (i == selectedIndex) Modifier.focusRequester(entry) else Modifier)
                        .semantics { contentDescription = o.label + if (o.selected) ", sélectionné" else "" },
                    shape = Radius.Medium, color = Color.Transparent, focusedColor = Color.White, focusScale = 1.03f, borderWidth = 0.dp,
                ) { focused ->
                    val fg = if (focused) SokoulColors.OnLight else if (o.selected) SokoulColors.TextPrimary else SokoulColors.TextSecondary
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (o.selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.Accent)
                        else Spacer(Modifier.width(20.dp))
                        Text(
                            o.label, Modifier.padding(start = 10.dp), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall, fontWeight = if (o.selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
