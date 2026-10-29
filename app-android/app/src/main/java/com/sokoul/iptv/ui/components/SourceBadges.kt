package com.sokoul.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.SourceState
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type

/** Libellés des sources, calculés ici à partir des valeurs typées (un seul endroit à traduire). */
object SourceLabels {
    /** Origine en abrégé (badges, bandeau) : « IPTV », « RD ». */
    fun origin(o: SourceOrigin) = originShort(o)
    fun originShort(o: SourceOrigin) = when (o) { SourceOrigin.IPTV -> "IPTV"; SourceOrigin.DEBRID -> "RD" }
    /** Origine en clair (panneaux, messages) : « Abonnement IPTV », « Real-Debrid ». Une nouvelle origine s'ajoute ici. */
    fun originName(o: SourceOrigin) = when (o) { SourceOrigin.IPTV -> "Abonnement IPTV"; SourceOrigin.DEBRID -> "Real-Debrid" }
    fun quality(q: VideoQuality) = when (q) {
        VideoQuality.UHD_4K -> "4K"; VideoQuality.FHD_1080 -> "1080p"; VideoQuality.HD_720 -> "720p"; VideoQuality.SD -> "SD"
    }
    /** Même nom partout (fiche, bouton, lecteur) : « HDR10 », « Dolby Vision ». */
    fun hdr(h: HdrFormat) = when (h) { HdrFormat.HDR10 -> "HDR10"; HdrFormat.DOLBY_VISION -> "Dolby Vision" }
    fun language(a: AudioLanguage) = a.name
    /** « 18 Go », « 700 Mo ». */
    fun size(bytes: Long): String = if (bytes >= 1_000_000_000L) "${(bytes / 1e8).toLong().let { if (it % 10 == 0L) "${it / 10}" else "${it / 10},${it % 10}" }} Go" else "${bytes / 1_000_000} Mo"
    fun state(s: SourceState) = when (s) {
        SourceState.AVAILABLE -> null; SourceState.CACHED_INSTANT -> "INSTANTANÉ"; SourceState.UNAVAILABLE -> "HORS CATALOGUE"
    }

    /** Étiquettes dans l'ordre d'affichage : langue (ou pays), définition, HDR. */
    fun tags(s: PlaybackSource): List<String> = listOfNotNull(
        s.language?.let(::language) ?: s.region,
        s.quality?.let(::quality),
        s.hdr?.let(::hdr),
        s.rip,
        s.sizeBytes?.let(::size),
    )

    /** Libellé court pour les menus et messages : « VF · 1080p », « AR · 4K », sinon « Version ». */
    fun text(s: PlaybackSource): String = tags(s).joinToString(" · ").ifEmpty { "Version" }

    /** Bouton « Lecture » : langue, définition et HDR seulement (« MULTI · 4K HDR ») ; rip et taille restent au détail. */
    fun short(s: PlaybackSource): String = listOfNotNull(
        s.language?.let(::language) ?: s.region,
        listOfNotNull(s.quality?.let(::quality), s.hdr?.let(::hdr)).joinToString(" ").ifEmpty { null },
    ).joinToString(" · ").ifEmpty { "Version" }
}

/**
 * Badges uniformes d'une source : [IPTV] [VF] [1080p] [HDR]. L'origine est pleine (on voit d'abord d'où vient le flux),
 * les caractéristiques sont en contour ; même rayon et même style de texte partout.
 */
@Composable
fun SourceBadges(source: PlaybackSource, modifier: Modifier = Modifier, showOrigin: Boolean = true, onLight: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showOrigin) SourceBadge(SourceLabels.origin(source.origin), filled = true, onLight = onLight)
        SourceLabels.tags(source).forEach { SourceBadge(it, filled = false, onLight = onLight) }
        SourceLabels.state(source.state)?.let { SourceBadge(it, filled = true, onLight = onLight) }
    }
}

@Composable
fun SourceBadge(text: String, filled: Boolean, modifier: Modifier = Modifier, onLight: Boolean = false) {
    val ink = if (onLight) SokoulColors.OnLight else SokoulColors.TextPrimary
    Text(
        text,
        modifier
            .then(
                if (filled) Modifier.background(if (onLight) SokoulColors.black(0.12f) else SokoulColors.GlassLight, Radius.Tiny)
                else Modifier.border(1.dp, if (onLight) SokoulColors.black(0.35f) else SokoulColors.TextMuted, Radius.Tiny)
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
        style = Type.Overline.copy(color = ink, letterSpacing = Type.Overline.letterSpacing * 0.5f),
        maxLines = 1,
    )
}
