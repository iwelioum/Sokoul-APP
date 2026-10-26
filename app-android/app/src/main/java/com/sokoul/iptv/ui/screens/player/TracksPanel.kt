package com.sokoul.iptv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import com.sokoul.iptv.ui.theme.Edge
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import kotlinx.coroutines.delay

/** Une colonne d'audio n'apparaît que s'il y a vraiment un choix (au moins deux pistes). */
internal fun showAudioColumn(audio: List<TrackOption>) = audio.count { it.group != null } > 1

/**
 * Carte compacte « Audio et sous-titres », en bas à droite au-dessus des pastilles : hauteur de son contenu
 * (au plus 60 % de l'écran), ◀ ▶ entre les colonnes, OK pour choisir. Reste ouverte après un choix
 * (Retour pour fermer), pour régler audio puis sous-titres d'un seul geste.
 */
@Composable
fun TracksPanel(audio: List<TrackOption>, text: List<TrackOption>, modifier: Modifier = Modifier, onPick: (type: Int, TrackOption) -> Unit) {
    val l = LocalLayout.current
    val audioEnter = remember { FocusRequester() }
    val textEnter = remember { FocusRequester() }
    val withAudio = showAudioColumn(audio)
    val maxList = (LocalConfiguration.current.screenHeightDp * 0.6f).dp - 110.dp
    val colWidth = if (l.isCompact) 260.dp else 330.dp
    Column(
        modifier
            .background(SokoulColors.Panel, Radius.Panel)
            .border(Edge.Hairline, SokoulColors.white(0.08f), Radius.Panel)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("Audio et sous-titres", style = Type.Section)
        // piste audio unique : simple rappel, sans colonne à parcourir
        if (!withAudio) {
            val only = audio.firstOrNull { it.selected } ?: audio.firstOrNull()
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Audiotrack, null, Modifier.size(15.dp), tint = SokoulColors.TextMuted)
                Text(only?.label ?: "Piste audio unique", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (withAudio) {
                TrackColumn("Audio", Icons.Default.Audiotrack, audio, audioEnter, maxList, Modifier.width(colWidth)) { if (it.group != null) onPick(C.TRACK_TYPE_AUDIO, it) }
            }
            TrackColumn("Sous-titres", Icons.Default.ClosedCaption, text, textEnter, maxList, Modifier.width(colWidth)) { onPick(C.TRACK_TYPE_TEXT, it) }
        }
        KeyHints(
            listOfNotNull(if (withAudio) KeyHint("◀ ▶", "Colonne") else null, KeyHint("OK", "Choisir"), KeyHint("Retour", "Fermer")),
            Modifier.padding(top = 12.dp),
        )
    }
    // ouverture : focus sur la piste en cours
    LaunchedEffect(withAudio) { delay(60); runCatching { (if (withAudio) audioEnter else textEnter).requestFocus() } }
}

@Composable
private fun TrackColumn(title: String, icon: ImageVector, options: List<TrackOption>, entry: FocusRequester, maxHeight: Dp, modifier: Modifier, onPick: (TrackOption) -> Unit) {
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = SokoulColors.Accent)
            Text(title.uppercase(), Modifier.padding(start = 8.dp), style = Type.Kicker)
        }
        Spacer(Modifier.size(8.dp))
        // ◀ ▶ entre les colonnes : on arrive toujours sur la piste sélectionnée, pas sur la plus proche
        LazyColumn(
            Modifier.heightIn(max = maxHeight.coerceAtLeast(120.dp)).focusProperties { enter = { entry } }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(options) { i, o ->
                FocusSurface(style = FocusStyle.Row,
                    onClick = { onPick(o) },
                    modifier = Modifier.fillMaxWidth().then(if (i == selectedIndex) Modifier.focusRequester(entry) else Modifier)
                        .semantics { contentDescription = o.label + if (o.selected) ", sélectionné" else "" },
                    shape = Radius.Card, color = Color.Transparent,
                ) { focused ->
                    val fg = when {
                        o.info -> SokoulColors.TextMuted
                        focused || o.selected -> SokoulColors.TextPrimary
                        else -> SokoulColors.TextSecondary
                    }
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (o.selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = SokoulColors.Accent)
                        else Spacer(Modifier.width(18.dp))
                        Text(
                            o.label, Modifier.padding(start = 10.dp), color = fg, maxLines = if (o.online != null) 2 else 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = if (o.selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
