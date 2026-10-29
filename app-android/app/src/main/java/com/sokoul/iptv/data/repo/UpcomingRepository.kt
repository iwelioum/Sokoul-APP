package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.net.providers.TvmazeClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** Prochain épisode d'une série suivie, pour le calendrier « À venir ». */
data class Upcoming(
    val seriesId: String,
    val title: String,
    val image: String?,
    val season: Int,
    val episode: Int,
    val name: String?,
    val airsAt: Long,
)

/**
 * Calendrier des séries suivies (Ma liste + séries commencées) : prochain épisode annoncé par TVmaze, pour les
 * séries encore en production d'après TMDB. Réponses en cache (fiche TMDB 7 j, TVmaze 12 h) : quelques appels au plus.
 */
@Singleton
class UpcomingRepository @Inject constructor(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val tvmaze: TvmazeClient,
) {
    suspend fun upcoming(pid: Long, now: Long = System.currentTimeMillis()): List<Upcoming> = coroutineScope {
        val favorites = runCatching { catalog.favoriteSeries(pid).first().map { it.id } }.getOrDefault(emptyList())
        val started = runCatching { library.continueWatching(pid).first().filter { it.type == ContentType.SERIES }.mapNotNull { it.parentId } }.getOrDefault(emptyList())
        val ids = Calendar.followed(favorites, started)
        val gate = Semaphore(4)
        ids.map { sid ->
            async {
                gate.withPermit {
                    runCatching {
                        val s = catalog.seriesItem(pid, sid) ?: return@runCatching null.also { timber.log.Timber.d("À venir : série $sid absente du catalogue") }
                        val tm = catalog.seriesTmdb(s) ?: return@runCatching null.also { timber.log.Timber.d("À venir : « ${s.title} » sans fiche TMDB") }
                        if (!tm.inProduction) return@runCatching null.also { timber.log.Timber.d("À venir : « ${tm.title} » terminée") }
                        // la fiche légère n'a pas les identifiants externes : IMDb demandé à part (en cache)
                        val imdb = tm.imdbId ?: catalog.seriesImdb(tm.id)
                        val next = tvmaze.nextEpisode(tm.tvdbId, imdb) ?: return@runCatching null.also { timber.log.Timber.d("À venir : « ${tm.title} » ($imdb) sans prochain épisode annoncé") }
                        Upcoming(sid, tm.title.ifBlank { cleanDisplayTitle(s.title, s.year) }, tm.backdrop ?: s.backdrop ?: tm.poster ?: s.poster,
                            next.season, next.episode, next.name, next.airsAt.toEpochMilli())
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()
            // une série présente en plusieurs versions (VF, MULTI…) n'apparaît qu'une fois
            .distinctBy { "${it.title}|${it.season}|${it.episode}" }
            .let { Calendar.sorted(it, now) }
    }

    /** Règles pures (testées). */
    object Calendar {
        /** Séries suivies : Ma liste d'abord, puis les séries commencées, sans doublon, 40 au plus. */
        fun followed(favorites: List<String>, started: List<String>, max: Int = 40): List<String> = (favorites + started).distinct().take(max)

        /** À venir uniquement (jusqu'à 6 h après la diffusion, le temps qu'il arrive), le plus proche d'abord, 60 jours au plus. */
        fun sorted(list: List<Upcoming>, now: Long): List<Upcoming> =
            list.filter { it.airsAt >= now - 6 * HOUR && it.airsAt <= now + 60 * DAY }.sortedBy { it.airsAt }

        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR
    }
}
