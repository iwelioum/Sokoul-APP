package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.providers.FanartArt
import com.sokoul.iptv.data.net.providers.FanartClient
import com.sokoul.iptv.data.net.providers.NextEpisode
import com.sokoul.iptv.data.net.providers.OmdbClient
import com.sokoul.iptv.data.net.providers.Ratings
import com.sokoul.iptv.data.net.providers.StreamingClient
import com.sokoul.iptv.data.net.providers.StreamingOffer
import com.sokoul.iptv.data.net.providers.TasteDiveClient
import com.sokoul.iptv.data.net.providers.TvdbClient
import com.sokoul.iptv.data.net.providers.TvdbEpisode
import com.sokoul.iptv.data.net.providers.TvmazeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Compléments d'une fiche venant des sources secondaires ; tout est facultatif. */
data class DetailExtras(
    val art: FanartArt? = null,
    val ratings: Ratings? = null,
    val offers: List<StreamingOffer> = emptyList(),
    val nextEpisode: NextEpisode? = null,
) {
    val isEmpty get() = art == null && ratings == null && offers.isEmpty() && nextEpisode == null
}

/**
 * Orchestration des sources de données secondaires autour de TMDB (qui reste la source principale).
 * Chaque source est appelée en parallèle, bornée dans le temps ; une panne ou une clé absente donne
 * simplement « rien » pour cette source, sans jamais gêner les autres ni l'affichage de la fiche.
 */
@Singleton
class ExtrasRepository @Inject constructor(
    private val fanart: FanartClient,
    private val omdb: OmdbClient,
    private val streaming: StreamingClient,
    private val tvmaze: TvmazeClient,
    private val tvdb: TvdbClient,
    private val tastedive: TasteDiveClient,
) {
    suspend fun extras(isMovie: Boolean, tm: TmdbInfo): DetailExtras = coroutineScope {
        val art = async { quiet("fanart") { if (isMovie) fanart.movie(tm.id) else tm.tvdbId?.let { fanart.tv(it) } } }
        val ratings = async { quiet("omdb") { tm.imdbId?.let { omdb.ratings(it) }?.takeIf { !it.isEmpty } } }
        val offers = async { quiet("motn") { streaming.offers(isMovie, tm.id) }.orEmpty() }
        val next = async { if (isMovie || !tm.inProduction) null else quiet("tvmaze") { tvmaze.nextEpisode(tm.tvdbId, tm.imdbId) } }
        DetailExtras(art.await(), ratings.await(), offers.await(), next.await())
    }

    /** Épisodes TVDB en français (repli quand TMDB n'a ni titre ni résumé), par (saison, épisode). */
    suspend fun tvdbEpisodes(tm: TmdbInfo): Map<Pair<Int, Int>, TvdbEpisode> {
        val id = tm.tvdbId ?: return emptyMap()
        return quiet("tvdb", 20_000) { tvdb.episodes(id) }.orEmpty().associateBy { it.season to it.episode }
    }

    /** Noms d'œuvres similaires selon TasteDive (à rapprocher du catalogue). */
    suspend fun similarNames(isMovie: Boolean, tm: TmdbInfo): List<String> {
        val name = tm.originalTitle?.takeIf { it.isNotBlank() } ?: tm.title
        return quiet("tastedive") { tastedive.similar(name, isMovie) }.orEmpty()
    }

    private suspend fun <T> quiet(what: String, timeoutMs: Long = TIMEOUT_MS, block: suspend () -> T?): T? = try {
        withTimeoutOrNull(timeoutMs) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w("Source %s indisponible : %s", what, e.javaClass.simpleName)
        null
    }

    companion object {
        const val TIMEOUT_MS = 12_000L
        /** En dessous, « Vous aimerez aussi » est complété par TasteDive. */
        const val MIN_SIMILAR = 6
    }
}
