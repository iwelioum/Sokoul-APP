package com.sokoul.iptv.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.repo.SyncStatus
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.formatDate
import com.sokoul.iptv.ui.util.formatDateTime

/** Intertitre dans un volet (« Avancé », « Groupes masqués »…). */
internal fun LazyListScope.subsection(title: String) = item(key = "sub-$title") {
    Text(title.uppercase(), Modifier.padding(top = 18.dp, bottom = 4.dp, start = 4.dp), color = SokoulColors.Accent, style = Type.Overline)
}

/** Paragraphe d'explication dans un volet. */
internal fun LazyListScope.note(key: String, text: String) = item(key = "note-$key") {
    Text(text, Modifier.padding(horizontal = 6.dp, vertical = 6.dp).widthIn(max = 760.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
}

/** Ligne de réglage : icône, nom et aide à gauche, valeur alignée à droite. */
@Composable
internal fun SettingRow(
    title: String,
    value: String?,
    hint: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
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
                color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
            )
            if (trailing != null) Box(Modifier.padding(start = 16.dp)) { trailing() }
        }
    }
}

@Composable
internal fun ToggleRow(title: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) =
    SettingRow(title, null, hint, trailing = { com.sokoul.iptv.ui.components.SokoulSwitch(value) }) { onChange(!value) }

/** Section « Avancé » repliable : réglages techniques qu'on touche rarement. */
@Composable
internal fun AdvancedRow(open: Boolean, count: Int, onToggle: () -> Unit) {
    FocusSurface(style = FocusStyle.Row, onClick = onToggle, modifier = Modifier.fillMaxWidth(), color = SokoulColors.Surface) { _ ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, null, Modifier.padding(end = 14.dp).size(22.dp), tint = SokoulColors.TextSecondary)
            Column(Modifier.weight(1f)) {
                Text("Avancé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (open) "Réglages techniques" else "$count réglages techniques, à ne changer qu'en cas de problème", color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Replier" else "Déplier", tint = SokoulColors.TextSecondary)
        }
    }
}

@Composable
internal fun PlaylistRow(p: PlaylistEntity, active: Boolean, status: SyncStatus?, epg: SyncStatus?, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(style = FocusStyle.Row, onClick = onClick, modifier = modifier.fillMaxWidth(), color = if (active) SokoulColors.AccentTint else SokoulColors.Surface) { _ ->
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(if (p.type == PlaylistType.XTREAM) "Xtream" else "M3U", color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                if (active) Text("  ·  ACTIVE", color = SokoulColors.Accent, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${p.liveCount} chaînes · ${p.movieCount} films · ${p.seriesCount} séries" +
                    (if (p.lastSync > 0) " · synchronisée ${formatDateTime(p.lastSync)}" else ""),
                color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            p.expiresAt?.let { Text("Abonnement valable jusqu'au ${formatDate(it)}" + (p.maxConnections?.let { n -> " · $n connexion${if (n > 1) "s" else ""} simultanée${if (n > 1) "s" else ""}" } ?: ""), color = if (it < System.currentTimeMillis() + 7 * 86_400_000L) SokoulColors.Warning else SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            statusLine("Playlist", status)?.let { Text(it, color = if (status?.error != null) SokoulColors.Live else SokoulColors.Accent, style = MaterialTheme.typography.bodySmall) }
            statusLine("Guide", epg)?.let { Text(it, color = if (epg?.error != null) SokoulColors.Live else SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun statusLine(label: String, s: SyncStatus?): String? = when {
    s == null -> null
    s.running -> "$label : ${s.step} ${if (s.count > 0) s.count.toString() else ""}".trim()
    s.error != null -> "$label : ${s.error}"
    s.warnings.isNotEmpty() -> "$label : ${s.warnings.joinToString(" ; ")}"
    s.finishedAt > 0 -> "$label : à jour (${s.count})"
    else -> null
}
