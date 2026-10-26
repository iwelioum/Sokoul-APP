package com.sokoul.iptv.ui.screens.browse

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.EpisodeResolver
import com.sokoul.iptv.data.repo.SeriesAction
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.ui.nav.MovieDetailRoute
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.nav.SeriesDetailRoute
import com.sokoul.iptv.ui.screens.home.resumeRoute
import javax.inject.Inject

/** Actions communes à toutes les cartes d'œuvre (accueil, grilles) : lecture directe, « Ma liste ». */
class WorkActions @Inject constructor(
    private val catalog: CatalogRepository,
    private val vod: VodRepository,
) {
    /** « Lecture » : meilleure version d'un film, ou reprise de la série (sinon sa fiche). */
    suspend fun playRoute(pid: Long?, w: Work): Any {
        if (w.key.startsWith(com.sokoul.iptv.data.debrid.DebridCloud.KEY_PREFIX))
            return PlayerRoute(PlayKind.CLOUD, pid ?: 0, w.key.removePrefix(com.sokoul.iptv.data.debrid.DebridCloud.KEY_PREFIX), title = w.title)
        com.sokoul.iptv.ui.screens.browse.debridResumeRoute(pid ?: 0, w)?.let { return it }
        if (pid == null) return detailRoute(w)
        w.resume?.let { return resumeRoute(pid, it) }
        return when (w.type) {
            ContentType.MOVIE -> {
                val m = catalog.movie(pid, w.id) ?: return detailRoute(w)
                val best = vod.orderedMovies(pid, vod.movieVersions(pid, m)).firstOrNull() ?: m
                PlayerRoute(PlayKind.MOVIE, pid, best.id)
            }
            else -> {
                // même logique que le bouton de la fiche : reprise, sinon épisode suivant (liste en cache uniquement)
                val s = catalog.seriesItem(pid, w.id) ?: return detailRoute(w)
                val ids = vod.orderedSeries(pid, vod.seriesVersions(pid, s)).map { it.id }
                val all = vod.episodesIn(pid, ids)
                val merged = VodRepository.mergeEpisodes(all, ids)
                val action = EpisodeResolver.primaryAction(merged, EpisodeResolver.progressBySe(vod.seriesProgressIn(pid, ids), all))
                when (action) {
                    null, is SeriesAction.Start, is SeriesAction.Rewatch -> detailRoute(w)
                    else -> PlayerRoute(PlayKind.EPISODE, pid, action.episode.id, parentId = action.episode.seriesId)
                }
            }
        }
    }

    fun detailRoute(w: Work): Any = if (w.type == ContentType.MOVIE) MovieDetailRoute(w.id) else SeriesDetailRoute(w.id)

    /** Ajoute ou retire l'œuvre (toutes ses versions) de « Ma liste ». */
    suspend fun toggleList(pid: Long, w: Work) {
        val ids = when (w.type) {
            ContentType.MOVIE -> catalog.movie(pid, w.id)?.let { m -> vod.movieVersions(pid, m).map { it.id } }
            else -> catalog.seriesItem(pid, w.id)?.let { s -> vod.seriesVersions(pid, s).map { it.id } }
        } ?: listOf(w.id)
        vod.toggleFavorite(pid, w.type, ids, w.id)
    }
}

/** Carte « Reprendre » d'un titre lu uniquement via Real-Debrid (« tmdb:m:603 ») : route du lecteur, sinon null. */
fun debridResumeRoute(pid: Long, w: Work): PlayerRoute? {
    if (!w.key.startsWith("tmdb:")) return null
    val q = com.sokoul.iptv.data.debrid.DebridQuery.decode(w.key.removePrefix("tmdb:")) ?: return null
    return PlayerRoute(PlayKind.DEBRID, pid, q.encode(), query = q.encode(), title = w.title)
}
