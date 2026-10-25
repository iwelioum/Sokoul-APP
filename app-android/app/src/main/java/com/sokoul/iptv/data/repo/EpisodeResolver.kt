package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.ProgressEntity

/** Saison et numéro d'épisode. */
typealias Se = Pair<Int, Int>

/** Ce que propose le bouton principal d'une fiche série. */
sealed interface SeriesAction {
    val episode: EpisodeEntity

    /** Épisode commencé : reprise à la position enregistrée. */
    data class Resume(override val episode: EpisodeEntity, val progress: ProgressEntity) : SeriesAction
    /** Suite logique du dernier épisode regardé. */
    data class Next(override val episode: EpisodeEntity) : SeriesAction
    /** Rien de regardé : premier épisode (non vu) de la série. */
    data class Start(override val episode: EpisodeEntity) : SeriesAction
    /** Tout est vu : on propose de revoir depuis le début. */
    data class Rewatch(override val episode: EpisodeEntity) : SeriesAction
}

/**
 * Reprise et enchaînement des épisodes sur la liste fusionnée de toutes les versions
 * ([VodRepository.mergeEpisodes]) : un épisode est identifié par sa saison/numéro, pas par l'id
 * de la version dans laquelle il a été regardé.
 */
object EpisodeResolver {

    fun EpisodeEntity.se(): Se = season to episode

    /** Saison/épisode d'une progression : colonnes v4, sinon retrouvées par l'id d'épisode. */
    fun seOf(p: ProgressEntity, byId: Map<String, Se>): Se? {
        val s = p.season
        val e = p.episode
        return if (s != null && e != null) s to e else byId[p.itemId]
    }

    /** Dernière progression par saison/épisode, toutes versions confondues. */
    fun progressBySe(progress: List<ProgressEntity>, allEpisodes: List<EpisodeEntity>): Map<Se, ProgressEntity> {
        val byId = allEpisodes.associate { it.id to it.se() }
        val out = HashMap<Se, ProgressEntity>()
        for (p in progress) {
            val k = seOf(p, byId) ?: continue
            val cur = out[k]
            if (cur == null || p.updatedAt > cur.updatedAt) out[k] = p
        }
        return out
    }

    /** Épisode suivant dans la liste fusionnée (les trous d'une version sont comblés par les autres). */
    fun nextAfter(merged: List<EpisodeEntity>, se: Se): EpisodeEntity? =
        merged.asSequence().filter { compare(it.se(), se) > 0 }.minWithOrNull(compareBy({ it.season }, { it.episode }))

    private fun compare(a: Se, b: Se) = if (a.first != b.first) a.first.compareTo(b.first) else a.second.compareTo(b.second)

    /**
     * Le bouton principal se fonde sur le dernier épisode **réellement regardé** : un « marquer comme vu »
     * fait à la main ne déplace pas la reprise, il fait seulement sauter les épisodes déjà vus.
     */
    fun primaryAction(merged: List<EpisodeEntity>, bySe: Map<Se, ProgressEntity>): SeriesAction? {
        if (merged.isEmpty()) return null
        val sorted = merged.sortedWith(compareBy({ it.season }, { it.episode }))
        fun watched(e: EpisodeEntity) = bySe[e.se()]?.finished == true
        val last = bySe.entries.filter { !it.value.manual }.maxByOrNull { it.value.updatedAt }
        if (last == null) {
            val first = sorted.firstOrNull { !watched(it) } ?: return SeriesAction.Rewatch(sorted.first())
            return SeriesAction.Start(first)
        }
        val (se, p) = last
        val current = sorted.firstOrNull { it.se() == se }
        if (current != null && !p.finished) {
            return if (LibraryRepository.resumeFrom(p) > 0) SeriesAction.Resume(current, p) else SeriesAction.Next(current)
        }
        // terminé : le prochain épisode pas encore vu
        val next = sorted.firstOrNull { compare(it.se(), se) > 0 && !watched(it) }
        if (next != null) return SeriesAction.Next(next)
        val firstUnwatched = sorted.firstOrNull { !watched(it) }
        return if (firstUnwatched != null) SeriesAction.Next(firstUnwatched) else SeriesAction.Rewatch(sorted.first())
    }

    /** Libellé du bouton principal. */
    fun label(a: SeriesAction): String {
        val se = "S${a.episode.season} · É${a.episode.episode}"
        return when (a) {
            is SeriesAction.Resume -> "Reprendre $se"
            is SeriesAction.Next -> "Regarder $se"
            is SeriesAction.Start -> if (a.episode.season <= 1 && a.episode.episode <= 1) "Lecture" else "Regarder $se"
            is SeriesAction.Rewatch -> "Revoir depuis le début"
        }
    }

    /** Ligne d'aide sous le bouton : « 18 min restantes », « Épisode suivant »… */
    fun hint(a: SeriesAction): String? = when (a) {
        is SeriesAction.Resume -> remaining(a.progress)
        is SeriesAction.Next -> "Épisode suivant · " + EpisodeMerge.cleanTitle(a.episode.title, a.episode.episode)
        is SeriesAction.Start -> null
        is SeriesAction.Rewatch -> "Vous avez tout vu"
    }

    fun remaining(p: ProgressEntity): String? {
        if (p.durationMs <= 0) return null
        val left = ((p.durationMs - p.positionMs) / 60_000).coerceAtLeast(1)
        return if (left >= 60) "Reste ${left / 60} h ${(left % 60).toString().padStart(2, '0')}" else "Reste $left min"
    }
}
