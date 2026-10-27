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
 * Étagère des versions de la fiche, rangée comme le panneau du lecteur :
 * - une section par langue des Réglages (VF, MULTI, VOSTFR, VO), une carte par définition (4K, 1080p…) ;
 * - toutes les autres langues réunies dans une seule carte « Autres langues » (OK : la liste) ;
 * - chaque carte montre la source qui sera lue et combien d'autres proposent la même version (appui long : toutes).
 */
@Composable
fun VersionsShelf(
    groups: List<VersionGroup>,
    languages: List<String>,
    firstId: String?,
    preferredId: String?,
    memory: FocusMemory,
    below: FocusRequester?,
    onPlay: (VersionGroup) -> Unit,
    onMenu: (VersionGroup) -> Unit,
    onOthers: (List<VersionGroup>) -> Unit,
) {
    val l = LocalLayout.current
    val (main, others) = groups.partition { SourceCatalog.mainLanguage(it, languages) }
    val byLanguage = main.groupBy(VersionGroups::languageKey)
    LazyRow(contentPadding = PaddingValues(horizontal = l.margin, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        byLanguage.entries.forEachIndexed { gi, (key, list) ->
            item(key = "g:$key") { LanguageHeader(key, VersionGroups.explain(key), first = gi == 0) }
            items(list, key = { SourceCatalog.versionKey(it.best) }) { g ->
                val ids = g.sources.flatMap { listOf(it.id) + it.mergedIds }
                VersionCard(
                    g, first = firstId != null && firstId in ids, preferred = preferredId != null && preferredId in ids,
                    modifier = Modifier.focusMemory(memory, "ver:${SourceCatalog.versionKey(g.best)}").focusProperties { if (below != null) down = below },
                    onClick = { onPlay(g) }, onLongClick = { onMenu(g) },
                )
            }
        }
        if (others.isNotEmpty()) {
            item(key = "g:other") { LanguageHeader("Autres", "Autres langues et langue non indiquée", first = byLanguage.isEmpty()) }
            item(key = "other") {
                OthersCard(
                    others, Modifier.focusMemory(memory, "ver:other").focusProperties { if (below != null) down = below },
                ) { onOthers(others) }
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

/** Carte d'une version : la définition en grand, d'où vient la source lue, puis l'origine et le nombre d'autres sources. */
@Composable
private fun VersionCard(g: VersionGroup, first: Boolean, preferred: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val s = g.best
    FocusSurface(
        style = FocusStyle.Chip, onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(244.dp).height(VersionCardHeight),
        shape = Radius.Card, color = if (first) SokoulColors.AccentSoft else SokoulColors.SurfaceHigh,
    ) { focused ->
        val ink = if (focused) SokoulColors.OnLight else SokoulColors.TextPrimary
        val soft = if (focused) SokoulColors.OnLight.copy(alpha = 0.72f) else SokoulColors.TextSecondary
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(versionTitle(g), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (preferred) Icon(Icons.Default.Star, "Lancée en premier", Modifier.size(18.dp), tint = SokoulColors.Gold)
                else if (first) Icon(Icons.Default.PlayArrow, "Lancée par Lecture", Modifier.size(20.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.Accent)
            }
            // « WEB-DL · 2,7 Go · prête » : l'origine est dans la puce, pas répétée ici
            Text(
                s.where.removePrefix(SourceLabels.originName(s.origin)).removePrefix(" · ").ifEmpty { s.category ?: "" },
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(SourceLabels.originShort(s.origin), filled = true, onLight = focused)
                // les autres origines de cette version restent visibles (l'IPTV n'est jamais caché derrière « +n »)
                g.sources.map { it.origin }.distinct().filter { it != s.origin }.forEach {
                    SourceBadge(SourceLabels.originShort(it), filled = false, onLight = focused)
                }
                s.codec?.let { SourceBadge(it, filled = false, onLight = focused) }
                val more = g.sources.size - 1
                if (more > 0) Text("+$more", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = soft)
            }
        }
    }
}

/** Carte « Autres langues » : les langues présentes et le nombre de versions ; OK ouvre la liste. */
@Composable
private fun OthersCard(groups: List<VersionGroup>, modifier: Modifier, onClick: () -> Unit) {
    val names = groups.map { com.sokoul.iptv.ui.screens.player.PlayerSources.languageName(it.language, it.region).substringBefore(" (") }.distinct()
    FocusSurface(
        style = FocusStyle.Chip, onClick = onClick, modifier = modifier.width(244.dp).height(VersionCardHeight),
        shape = Radius.Card, color = SokoulColors.SurfaceHigh,
    ) { focused ->
        val ink = if (focused) SokoulColors.OnLight else SokoulColors.TextPrimary
        val soft = if (focused) SokoulColors.OnLight.copy(alpha = 0.72f) else SokoulColors.TextSecondary
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(if (groups.size == 1) "1 version" else "${groups.size} versions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = ink, maxLines = 1)
            Text(names.joinToString(", "), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(if (LocalLayout.current.isTouch) "Touchez pour voir la liste" else "OK : voir la liste", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = soft)
        }
    }
}
