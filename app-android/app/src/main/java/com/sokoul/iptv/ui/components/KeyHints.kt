package com.sokoul.iptv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors

/** Une légende de touche : la touche dessinée (« OK », « ◀ », « Menu ») et ce qu'elle fait. */
data class KeyHint(val key: String, val label: String)

/**
 * Légendes de touches, même rendu partout (direct, guide, recherche, lecteur) : petites touches dessinées
 * (cadre clair, lettre en gras) suivies de leur action. Masquées sur écran tactile (pas de télécommande).
 */
@Composable
fun KeyHints(hints: List<KeyHint>, modifier: Modifier = Modifier) {
    if (LocalLayout.current.isTouch || hints.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCap(h.key)
                Text(h.label, Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextSecondary, maxLines = 1)
            }
        }
    }
}

/** Touche de télécommande dessinée : fond translucide, filet clair, libellé centré. */
@Composable
fun KeyCap(key: String, modifier: Modifier = Modifier) {
    Box(
        modifier.widthIn(min = 26.dp)
            .background(SokoulColors.white(0.10f), Radius.Tiny)
            .border(1.dp, SokoulColors.white(0.32f), Radius.Tiny)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(key, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = SokoulColors.TextPrimary, textAlign = TextAlign.Center, maxLines = 1)
    }
}
