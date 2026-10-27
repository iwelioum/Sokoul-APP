package com.sokoul.iptv.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.SourceBadge
import com.sokoul.iptv.ui.components.SourceLabels
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.screens.player.QualityBasis
import com.sokoul.iptv.ui.screens.player.SourceCatalog
import com.sokoul.iptv.ui.screens.player.VersionGroup
import com.sokoul.iptv.ui.screens.player.versionTitle
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors

/** Langues : titre court et explication sous le titre de section. */
object VersionGroups {
    /** Préfixes de pays des fournisseurs → langue (adjectif). */
    private val REGIONS = mapOf(
        "EN" to "anglaise", "UK" to "anglaise", "US" to "américaine", "AR" to "arabe", "ES" to "espagnole", "DE" to "allemande",
        "IT" to "italienne", "PT" to "portugaise", "BR" to "brésilienne", "TR" to "turque", "NL" to "néerlandaise", "PL" to "polonaise",
        "RU" to "russe", "HI" to "hindi", "IN" to "indienne", "KR" to "coréenne", "JP" to "japonaise", "CN" to "chinoise",
    )

    fun languageKey(g: VersionGroup) = g.language?.name ?: g.region ?: "AUTRE"

    fun explain(key: String): String = when (key) {
        AudioLanguage.VF.name -> "Doublage français"
        AudioLanguage.VOSTFR.name -> "VO + sous-titres français intégrés"
        AudioLanguage.VO.name -> "Version originale"
        AudioLanguage.MULTI.name -> "Plusieurs langues au choix dans le lecteur"
        "AUTRE" -> "Langue non précisée par le fournisseur"
        else -> REGIONS[key]?.let { "Version $it, sans sous-titres français" } ?: "Version marquée « $key » par le fournisseur"
    }
}

/**
 * Étagère des versions de la fiche : une carte par version (langue + qualité), quelle que soit l'origine.
 * La carte montre la source qui sera lue (origine en clair, qualité mesurée ou estimée) et combien d'autres sources
 * proposent la même version ; appui long : toutes ces sources, à lire une par une.
 */
@Composable
fun VersionsShelf(
    groups: List<VersionGroup>,
    firstId: String?,
    preferredId: String?,
    memory: FocusMemory,
    below: FocusRequester?,
    onPlay: (VersionGroup) -> Unit,
    onMenu: (VersionGroup) -> Unit,
) {
    val l = LocalLayout.current
    val byLanguage = groups.groupBy(VersionGroups::languageKey)
    LazyRow(contentPadding = PaddingValues(horizontal = l.margin, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        byLanguage.entries.forEachIndexed { gi, (key, list) ->
            item(key = "g:$key") { LanguageHeader(if (key == "AUTRE") "Autre" else key, VersionGroups.explain(key), first = gi == 0) }
            items(list, key = { SourceCatalog.versionKey(it.best) }) { g ->
                val ids = g.sources.flatMap { listOf(it.id) + it.mergedIds }
                VersionCard(
                    g, first = firstId != null && firstId in ids, preferred = preferredId != null && preferredId in ids,
                    modifier = Modifier.focusMemory(memory, "ver:${SourceCatalog.versionKey(g.best)}").focusProperties { if (below != null) down = below },
                    onClick = { onPlay(g) }, onLongClick = { onMenu(g) },
                )
            }
        }
    }
}

@Composable
private fun LanguageHeader(title: String, explain: String, first: Boolean) {
    Row(Modifier.height(VersionCardHeight), verticalAlignment = Alignment.CenterVertically) {
        // filet vertical entre deux langues
        if (!first) androidx.compose.foundation.layout.Box(
            Modifier.padding(end = 14.dp).width(1.dp).fillMaxHeight().padding(vertical = 10.dp)
                .background(SokoulColors.white(0.12f))
        )
        Column(Modifier.width(118.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(explain, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 3)
        }
    }
}

private val VersionCardHeight = 118.dp

@Composable
private fun VersionCard(g: VersionGroup, first: Boolean, preferred: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val s = g.best
    FocusSurface(
        style = FocusStyle.Chip, onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(256.dp).height(VersionCardHeight),
        shape = Radius.Card, color = if (first) SokoulColors.AccentSoft else SokoulColors.SurfaceHigh,
    ) { focused ->
        val ink = if (focused) SokoulColors.OnLight else SokoulColors.TextPrimary
        val soft = if (focused) SokoulColors.OnLight.copy(alpha = 0.72f) else SokoulColors.TextSecondary
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HighQuality, null, Modifier.size(18.dp), tint = soft)
                // image : mesurée, sinon l'étiquette du nom (estimée)
                val image = listOfNotNull(versionTitle(g), s.codec).joinToString(" · ") + if (s.basis == QualityBasis.NAME) " (estimée)" else ""
                Text(image, Modifier.padding(start = 6.dp).weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (preferred) Icon(Icons.Default.Star, "Lancée en premier", Modifier.size(16.dp), tint = SokoulColors.Gold)
                else if (first) Icon(Icons.Default.PlayArrow, "Lancée par Lecture", Modifier.size(18.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.Accent)
            }
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Layers, null, Modifier.size(18.dp), tint = soft)
                Text(s.where, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SourceBadge(SourceLabels.originShort(s.origin), filled = true, onLight = focused)
                val more = g.sources.size - 1
                if (more > 0) SourceBadge(if (more == 1) "+1 SOURCE" else "+$more SOURCES", filled = false, onLight = focused)
                if (first) SourceBadge("LANCÉE PAR LECTURE", filled = true, onLight = focused)
                else if (s.ready == true && s.origin != com.sokoul.iptv.domain.SourceOrigin.IPTV) SourceBadge("INSTANTANÉ", filled = true, onLight = focused)
            }
        }
    }
}
