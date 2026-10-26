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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
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
import com.sokoul.iptv.data.repo.BestVersions
import com.sokoul.iptv.data.repo.VodQuality
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.SourceBadge
import com.sokoul.iptv.ui.components.SourceLabels
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors

/** Une version affichée dans l'étagère : sa source (langue, étiquettes du nom) et sa qualité mesurée. */
data class VersionItem(val id: String, val source: PlaybackSource, val quality: VodQuality?, val first: Boolean, val preferred: Boolean)

/** Un groupe de langue : « VF », « VOSTFR »… et ses versions, la meilleure image en tête. */
data class VersionGroup(val key: String, val title: String, val explain: String, val items: List<VersionItem>)

object VersionGroups {
    /** Préfixes de pays des fournisseurs → langue (adjectif). */
    private val REGIONS = mapOf(
        "EN" to "anglaise", "UK" to "anglaise", "US" to "américaine", "AR" to "arabe", "ES" to "espagnole", "DE" to "allemande",
        "IT" to "italienne", "PT" to "portugaise", "BR" to "brésilienne", "TR" to "turque", "NL" to "néerlandaise", "PL" to "polonaise",
        "RU" to "russe", "HI" to "hindi", "IN" to "indienne", "KR" to "coréenne", "JP" to "japonaise", "CN" to "chinoise",
    )

    fun languageKey(s: PlaybackSource) = s.language?.name ?: s.region ?: "AUTRE"

    fun explain(key: String): String = when (key) {
        AudioLanguage.VF.name -> "Doublage français"
        AudioLanguage.VOSTFR.name -> "VO + sous-titres français intégrés"
        AudioLanguage.VO.name -> "Version originale"
        AudioLanguage.MULTI.name -> "Plusieurs langues au choix dans le lecteur"
        "AUTRE" -> "Langue non précisée par le fournisseur"
        else -> REGIONS[key]?.let { "Version $it, sans sous-titres français" } ?: "Version marquée « $key » par le fournisseur"
    }

    /**
     * Groupes dans l'ordre de lancement (la langue préférée d'abord) ; dans un groupe, la meilleure image mesurée
     * passe devant, sinon l'ordre de lancement est gardé.
     */
    fun group(items: List<VersionItem>): List<VersionGroup> =
        items.groupBy { languageKey(it.source) }.map { (k, list) ->
            val sorted = list.withIndex().sortedWith(compareBy({ -(it.value.quality?.imageScore ?: 0) }, { it.index })).map { it.value }
            VersionGroup(k, if (k == "AUTRE") "Autre" else k, explain(k), sorted)
        }
}

/**
 * Étagère des versions : groupes par langue, avec la qualité réelle de chaque version (définition, HDR, codec, son,
 * durée) et les repères « Meilleure image » / « Meilleur son ». Une version non encore mesurée affiche l'étiquette
 * lue dans son nom, signalée comme estimée.
 */
@Composable
fun VersionsShelf(
    items: List<VersionItem>,
    memory: FocusMemory,
    below: FocusRequester?,
    onPlay: (String) -> Unit,
    onMenu: (String) -> Unit,
) {
    val l = LocalLayout.current
    val qualities = items.mapNotNull { i -> i.quality?.let { i.id to it } }.toMap()
    val bestImage = BestVersions.image(qualities)
    val bestSound = BestVersions.sound(qualities)
    val groups = VersionGroups.group(items)
    LazyRow(contentPadding = PaddingValues(horizontal = l.margin, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.forEachIndexed { gi, g ->
            item(key = "g:${g.key}") { GroupHeader(g, first = gi == 0) }
            items(g.items, key = { it.id }) { v ->
                VersionCard(
                    v, bestImage = v.id == bestImage, bestSound = v.id == bestSound,
                    modifier = Modifier.focusMemory(memory, "ver:${v.id}").focusProperties { if (below != null) down = below },
                    onClick = { onPlay(v.id) }, onLongClick = { onMenu(v.id) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(g: VersionGroup, first: Boolean) {
    Row(Modifier.height(VersionCardHeight), verticalAlignment = Alignment.CenterVertically) {
        // filet vertical entre deux langues
        if (!first) androidx.compose.foundation.layout.Box(
            Modifier.padding(end = 14.dp).width(1.dp).fillMaxHeight().padding(vertical = 10.dp)
                .background(SokoulColors.white(0.12f))
        )
        Column(Modifier.width(118.dp)) {
            Text(g.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(g.explain, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 3)
        }
    }
}

private val VersionCardHeight = 118.dp

@Composable
private fun VersionCard(v: VersionItem, bestImage: Boolean, bestSound: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    FocusSurface(
        style = FocusStyle.Chip, onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(236.dp).height(VersionCardHeight),
        shape = Radius.Card, color = if (v.first) SokoulColors.AccentSoft else SokoulColors.SurfaceHigh,
    ) { focused ->
        val ink = if (focused) SokoulColors.OnLight else SokoulColors.TextPrimary
        val soft = if (focused) SokoulColors.OnLight.copy(alpha = 0.72f) else SokoulColors.TextSecondary
        val rd = v.source.origin == com.sokoul.iptv.domain.SourceOrigin.DEBRID
        val q = v.quality
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HighQuality, null, Modifier.size(18.dp), tint = soft)
                // image : mesurée, sinon l'étiquette du nom (estimée)
                val image = q?.imageLine()?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(v.source.quality?.let(SourceLabels::quality), v.source.hdr?.let(SourceLabels::hdr)).joinToString(" · ").ifEmpty { null }
                        ?.let { if (rd) it else "$it (estimé)" }
                    ?: "Image non mesurée"
                Text(image, Modifier.padding(start = 6.dp).weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (v.preferred) Icon(Icons.Default.Star, "Lancée en premier", Modifier.size(16.dp), tint = SokoulColors.Gold)
                else if (v.first) Icon(Icons.Default.PlayArrow, "Lancée par Lecture", Modifier.size(18.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.Accent)
            }
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, Modifier.size(18.dp), tint = soft)
                val sound = if (rd) listOfNotNull("Real-Debrid", v.source.sizeBytes?.let(SourceLabels::size), v.source.rip).joinToString(" · ")
                    else listOfNotNull(q?.soundLine()?.ifEmpty { null }, q?.durationSecs?.let(VodQuality::duration)).joinToString(" · ").ifEmpty { "Son non mesuré" }
                Text(sound, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            // un seul repère de qualité (jamais coupé), puis la version lancée par « Lecture »
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val best = when {
                    bestImage && bestSound -> "MEILLEURE IMAGE ET SON"
                    bestImage -> "MEILLEURE IMAGE"
                    bestSound -> "MEILLEUR SON"
                    else -> null
                }
                best?.let { SourceBadge(it, filled = false, onLight = focused) }
                if (v.first && best == null) SourceBadge("LANCÉE PAR LECTURE", filled = true, onLight = focused)
                if (v.source.state == com.sokoul.iptv.domain.SourceState.CACHED_INSTANT && !v.first) SourceBadge("INSTANTANÉ", filled = true, onLight = focused)
            }
        }
    }
}
