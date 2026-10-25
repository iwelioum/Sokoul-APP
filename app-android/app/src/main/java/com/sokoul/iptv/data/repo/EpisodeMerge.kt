package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.providers.TvdbEpisode

/** Épisode prêt à afficher : textes français de TMDB quand ils existent, sinon ceux du fournisseur. */
data class EpisodeView(
    val entity: EpisodeEntity,
    val title: String,
    val overview: String?,
    /** Le résumé est en français (sinon : résumé anglais du fournisseur, signalé à l'écran). */
    val overviewFr: Boolean,
    val image: String?,
    val runtimeMin: Int?,
)

object EpisodeMerge {
    private val GENERIC = Regex("""^(é|e)pisode\s*\d+$""", RegexOption.IGNORE_CASE)
    private val SE = Regex("""S\d{1,2}\s*[._-]?\s*E\d{1,3}""", RegexOption.IGNORE_CASE)
    /** Nom de fichier de release (« 720p.NF.WEBRip.x264-GalaxyTV ») : ce n'est pas un titre. */
    private val RELEASE = Regex(
        """(^|[ ._\-\[(])(\d{3,4}p|web-?dl|web-?rip|hdtv|blu-?ray|brrip|bdrip|dvdrip|x26[45]|h\.?26[45]|hevc|10bit)([ ._\-\])]|$)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Titre lisible d'un épisode du fournisseur : « PH| Someone, Someday - S01E01 - Pilot » → « Pilot ».
     * Sans titre propre après le code saison/épisode : « Épisode N ».
     */
    fun cleanTitle(raw: String, episode: Int): String {
        val m = SE.findAll(raw).lastOrNull()
        val after = m?.let { raw.substring(it.range.last + 1) }?.trim(' ', '-', ':', '|', '.', '–')
        return when {
            m == null -> raw.trim().takeUnless { it.isEmpty() || RELEASE.containsMatchIn(it) } ?: "Épisode $episode"
            !after.isNullOrBlank() && !GENERIC.matches(after) && !RELEASE.containsMatchIn(after) -> after
            else -> "Épisode $episode"
        }
    }

    private val SE_NUM = Regex("""S(\d{1,2})\s*[._-]?\s*E(\d{1,3})""", RegexOption.IGNORE_CASE)

    /** Légende courte d'un épisode à partir du libellé fournisseur : « S01E01 · PH| Show - S01E01 - Pilot » → « S1 · É1 · Pilot ». */
    fun caption(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val m = SE_NUM.find(raw) ?: return raw.takeIf { it.length <= 40 }
        val s = m.groupValues[1].toInt()
        val e = m.groupValues[2].toInt()
        val t = cleanTitle(raw, e)
        return if (t == "Épisode $e" || t.length > 40) "S$s · É$e" else "S$s · É$e · $t"
    }

    /**
     * Ordre des textes : TMDB français → TheTVDB français → fournisseur (s'il est en français) → fournisseur (autre langue).
     * Vignette : TMDB → TheTVDB → image du fournisseur → fond de la série.
     */
    fun merge(e: EpisodeEntity, tm: TmdbEpisode?, seriesBackdrop: String?, tvdb: TvdbEpisode? = null): EpisodeView {
        val tmName = tm?.name?.takeIf { !GENERIC.matches(it.trim()) }
        val tvdbName = tvdb?.name?.takeIf { it.isNotBlank() && !GENERIC.matches(it.trim()) }
        val tvdbPlot = tvdb?.overview?.takeIf { it.isNotBlank() && looksFrench(it) }
        val providerPlot = e.plot?.takeIf { it.isNotBlank() }
        val overview = tm?.overview ?: tvdbPlot ?: providerPlot?.takeIf { looksFrench(it) } ?: providerPlot
        return EpisodeView(
            entity = e,
            title = tmName ?: tvdbName ?: cleanTitle(e.title, e.episode),
            overview = overview,
            overviewFr = tm?.overview != null || tvdbPlot != null || looksFrench(e.plot),
            image = tm?.still ?: tvdb?.image ?: e.image ?: seriesBackdrop,
            runtimeMin = e.durationSecs?.takeIf { it > 0 }?.div(60) ?: tm?.runtimeMin ?: tvdb?.runtimeMin,
        )
    }
}
