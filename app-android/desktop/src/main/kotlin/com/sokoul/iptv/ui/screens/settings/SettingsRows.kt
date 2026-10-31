package com.sokoul.iptv.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type

/** Intertitre dans un volet (« Avancé », « Groupes masqués »…). */
fun LazyListScope.subsection(title: String) = item(key = "sub-$title") {
    Text(title.uppercase(), Modifier.padding(top = 18.dp, bottom = 4.dp, start = 4.dp), color = SokoulColors.Accent, style = Type.Overline)
}

/** Paragraphe d'explication dans un volet. */
fun LazyListScope.note(key: String, text: String) = item(key = "note-$key") {
    Text(text, Modifier.padding(horizontal = 6.dp, vertical = 6.dp).widthIn(max = 760.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
}

/** Ligne de réglage : icône, nom et aide à gauche, valeur alignée à droite. */
@Composable
fun SettingRow(
    title: String,
    value: String?,
    hint: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    valueColor: Color = SokoulColors.TextSecondary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    FocusSurface(style = FocusStyle.Row, onClick = onClick, modifier = modifier.fillMaxWidth(), color = SokoulColors.Surface) { _ ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, Modifier.padding(end = 14.dp).size(22.dp), tint = SokoulColors.TextSecondary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (hint != null) Text(hint, color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (value != null) Text(
                value, Modifier.padding(start = 16.dp).widthIn(max = if (LocalLayout.current.isCompact) 140.dp else 300.dp),
                color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
            )
            if (trailing != null) Box(Modifier.padding(start = 16.dp)) { trailing() }
        }
    }
}

/** Section « Avancé » repliable : réglages techniques qu'on touche rarement. */
@Composable
fun AdvancedRow(open: Boolean, count: Int, onToggle: () -> Unit) {
    FocusSurface(style = FocusStyle.Row, onClick = onToggle, modifier = Modifier.fillMaxWidth(), color = SokoulColors.Surface) { _ ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(androidx.compose.material.icons.Icons.Default.Tune, null, Modifier.padding(end = 14.dp).size(22.dp), tint = SokoulColors.TextSecondary)
            Column(Modifier.weight(1f)) {
                Text("Avancé", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(if (open) "Réglages techniques" else "$count réglages techniques, à ne changer qu'en cas de problème", color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                if (open) androidx.compose.material.icons.Icons.Default.ExpandLess else androidx.compose.material.icons.Icons.Default.ExpandMore,
                if (open) "Replier" else "Déplier", tint = SokoulColors.TextSecondary,
            )
        }
    }
}

@Composable
fun ToggleRow(title: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) =
    SettingRow(title, null, hint, trailing = { com.sokoul.iptv.ui.components.SokoulSwitch(value) }) { onChange(!value) }
