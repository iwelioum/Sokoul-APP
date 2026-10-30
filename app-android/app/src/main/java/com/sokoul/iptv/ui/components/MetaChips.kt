package com.sokoul.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors

/** Type d'un repère d'œuvre : il fixe l'icône (le texte, lui, est déjà formaté). */
enum class MetaKind { YEAR, DURATION, ENDS, SEASONS, LANGUAGE }

data class MetaItem(val kind: MetaKind, val text: String)

private fun MetaKind.icon(): ImageVector = when (this) {
    MetaKind.YEAR -> Icons.Default.CalendarToday
    MetaKind.DURATION -> Icons.Default.Schedule
    MetaKind.ENDS -> Icons.Default.Alarm
    MetaKind.SEASONS -> Icons.Default.VideoLibrary
    MetaKind.LANGUAGE -> Icons.Default.Translate
}

/** Repère : icône + texte, d'un seul tenant (un retour à la ligne ne coupe jamais un repère). */
@Composable
fun MetaChip(item: MetaItem, color: Color = SokoulColors.TextSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(item.kind.icon(), null, Modifier.size(15.dp), tint = color.copy(alpha = 0.8f))
        Text(item.text, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/** Genre en puce discrète (contour fin), sans icône. */
@Composable
fun GenreChip(text: String) = Text(
    text,
    Modifier.border(1.dp, SokoulColors.white(0.22f), Radius.Pill).padding(horizontal = 8.dp, vertical = 2.dp),
    style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextSecondary, maxLines = 1,
)

/**
 * Ligne de repères qui passe à la ligne par éléments entiers : [lead] (note, badges) puis les repères,
 * le badge d'âge et les genres. Rien n'est focusable : la télécommande n'est pas concernée.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaRow(
    items: List<MetaItem>,
    modifier: Modifier = Modifier,
    certification: String? = null,
    genres: List<String> = emptyList(),
    centered: Boolean = false,
    lead: @Composable () -> Unit = {},
    trail: @Composable () -> Unit = {},
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        lead()
        items.forEach { MetaChip(it) }
        certification?.let { AgeBadge(it) }
        trail()
        genres.forEach { GenreChip(it) }
    }
}

/** Tranche d'âge d'une classification (France / TMDB). */
enum class AgeTone { ALL, TEN, TWELVE, SIXTEEN, EIGHTEEN, OTHER }

fun ageTone(cert: String): AgeTone {
    val c = cert.trim().trimEnd('+').removePrefix("-")
    return when {
        c.equals("TP", true) || c.equals("U", true) || c == "0" || c.equals("Tous publics", true) || c.equals("G", true) -> AgeTone.ALL
        c == "10" -> AgeTone.TEN
        c == "12" -> AgeTone.TWELVE
        c == "16" -> AgeTone.SIXTEEN
        c == "18" -> AgeTone.EIGHTEEN
        else -> AgeTone.OTHER
    }
}

/** Badge d'âge coloré selon la tranche : « Tous publics », « -12 »… */
@Composable
fun AgeBadge(cert: String, modifier: Modifier = Modifier) {
    val tone = ageTone(cert)
    val bg = when (tone) {
        AgeTone.ALL -> SokoulColors.AgeAll
        AgeTone.TEN -> SokoulColors.Age10
        AgeTone.TWELVE -> SokoulColors.Age12
        AgeTone.SIXTEEN -> SokoulColors.Age16
        AgeTone.EIGHTEEN -> SokoulColors.Age18
        AgeTone.OTHER -> SokoulColors.white(0.2f)
    }
    val label = com.sokoul.iptv.ui.components.certificationLabel(cert)
    Text(
        label,
        modifier.background(bg, Radius.Tiny)
            .then(if (tone == AgeTone.EIGHTEEN) Modifier.border(1.dp, SokoulColors.Live, Radius.Tiny) else Modifier)
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .semantics { contentDescription = "Classification : $label" },
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SokoulColors.OnVideo, maxLines = 1,
    )
}
