package com.sokoul.iptv.ui.screens.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type

/** « Décalage » : − 0,25 s · valeur (OK = remise à zéro) · + 0,25 s, parcouru avec ◀ ▶. */
@Composable
internal fun SubtitleOffsetRow(offsetMs: Long, delayOnly: Boolean, onShift: (Long) -> Unit) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("DÉCALAGE", style = Type.Kicker)
        Spacer(Modifier.width(12.dp))
        OffsetButton("− 0,25 s", "Sous-titres plus tôt", enabled = !(delayOnly && offsetMs <= 0)) { onShift(-SubtitleOffset.STEP_MS) }
        OffsetButton(SubtitleOffset.label(offsetMs), "Remettre à zéro le décalage", enabled = true, strong = true) { onShift(0) }
        OffsetButton("+ 0,25 s", "Sous-titres plus tard", enabled = true) { onShift(SubtitleOffset.STEP_MS) }
        if (delayOnly) Text("retard seulement", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted, maxLines = 1)
    }
}

@Composable
private fun OffsetButton(label: String, description: String, enabled: Boolean, strong: Boolean = false, onClick: () -> Unit) {
    FocusSurface(style = FocusStyle.Row,
        onClick = { if (enabled) onClick() },
        modifier = Modifier.padding(horizontal = 2.dp).semantics { contentDescription = description },
        shape = Radius.Card, color = Color.Transparent,
    ) { focused ->
        Text(
            label, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), maxLines = 1,
            color = when { !enabled -> SokoulColors.TextMuted; focused || strong -> SokoulColors.TextPrimary; else -> SokoulColors.TextSecondary },
            style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
