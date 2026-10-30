package com.sokoul.iptv.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space

/**
 * La seule pastille de l'app (catégories, raccourcis, ambiances, filtres, périmètre de recherche).
 * Repos : fond discret, ou violet léger si [selected]. Focus : fond blanc, texte sombre.
 */
@Composable
fun SokoulChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick, modifier = modifier, style = FocusStyle.Chip, onLongClick = onLongClick, onFocus = onFocus,
        color = if (selected) SokoulColors.AccentSoft else SokoulColors.SurfaceHigh,
    ) { focused ->
        Row(Modifier.padding(horizontal = Space.m, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(
                icon, null, Modifier.padding(end = Space.s).size(18.dp),
                tint = if (focused) SokoulColors.OnLight else if (selected) SokoulColors.TextPrimary else SokoulColors.Accent,
            )
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = onChip(focused))
        }
    }
}
