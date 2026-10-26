package com.sokoul.iptv.data.trakt

import com.sokoul.iptv.data.AppScope
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.FavoriteEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.repo.ProfileRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'une synchronisation, affiché dans Réglages. */
data class TraktReport(val movies: Int, val episodes: Int, val listed: Int, val at: Long)

/**
 * Trakt pour le profil en cours :
 * - **scrobble** : le lecteur signale lecture, pause et arrêt (Trakt marque vu à partir de 80 %) ;
 * - **envoi** : titres marqués vus à la main et ajouts à Ma liste ;
 * - **import** ([sync]) : films et épisodes vus ailleurs (marqués vus ici) et liste « à voir » (ajoutée à Ma liste).
 * Toujours silencieux en cas d'échec : Trakt ne doit jamais gêner la lecture.
 */
@Singleton
class TraktSync @Inject constructor(
    private val trakt: TraktClient,
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
) {
    private val lock = Mutex()
    private var playing: TraktItem? = null
    private var started = false
    private var lastPct = 0f

    // --- Scrobble (lecteur) ------------------------------------------------------------------------

    /** Lecture en cours de [item] (appelé à chaque sauvegarde de position) ; null = titre sans identifiant TMDB. */
    fun playing(item: TraktItem?, pct: Float) = fire {
        if (item != playing) {
            playing?.takeIf { started }?.let { trakt.scrobble(it.profile(), "stop", it, lastPct) }
            playing = item; started = false
        }
        lastPct = pct
        if (item != null && !started) { trakt.scrobble(item.profile(), "start", item, pct); started = true }
    }

    fun paused(pct: Float?) = fire {
        val item = playing ?: return@fire
        if (pct != null) lastPct = pct
        if (started) { trakt.scrobble(item.profile(), "pause", item, lastPct); started = false }
    }

    /** Lecteur fermé : arrêt (Trakt ajoute à l'historique si [pct] ≥ 80). */
    fun stopped(pct: Float? = null) = fire {
        val item = playing ?: return@fire
        val p = pct ?: lastPct
        if (started || p >= 80f) trakt.scrobble(item.profile(), "stop", item, p)
        playing = null; started = false
    }

    // --- Envoi depuis les fiches ---------------------------------------------------------------------

    fun markedWatched(items: List<TraktItem>) = fire { trakt.addHistory(profiles.currentId(), items) }

    fun addedToList(item: TraktItem) = fire { trakt.addWatchlist(profiles.currentId(), listOf(item)) }

    private suspend fun TraktItem.profile() = profiles.currentId()

    private fun fire(block: suspend () -> Unit) {
        AppScope.launch {
            lock.withLock {
                if (!runCatching { trakt.linked(profiles.currentId()) }.getOrDefault(false)) return@withLock
                try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                    Timber.w("Trakt : %s", e.message)
                }
            }
        }
    }

    // --- Import ------------------------------------------------------------------------------------

    /** Importe pour chaque profil associé ; renvoie le bilan du profil en cours (null s'il n'est pas associé). */
    suspend fun syncAll(): TraktReport? {
        val current = profiles.currentId()
        var mine: TraktReport? = null
        for (p in db.profiles().all()) {
            if (!trakt.linked(p.id)) continue
            val r = runCatching { sync(p.id) }.onFailure { Timber.w("Trakt (profil %d) : %s", p.id, it.message) }.getOrNull()
            if (p.id == current) mine = r
        }
        return mine
    }

    /** Vus et liste Trakt → ce profil, dans chaque playlist (titres reconnus par leur identifiant TMDB). */
    suspend fun sync(profile: Long): TraktReport {
        val movies = trakt.watchedMovies(profile)
        val episodes = trakt.watchedEpisodes(profile)
        val list = trakt.watchlist(profile)
        var nm = 0; var ne = 0; var nl = 0
        val now = System.currentTimeMillis()
        for (pl in db.playlists().getAll()) {
            val pid = pl.id
            val progress = db.profiles().allProgress(pid).filter { it.profileId == profile }
            val seen = progress.filter { it.finished }.mapTo(HashSet()) { it.type to it.itemId }
            val add = ArrayList<ProgressEntity>()
            for (id in movies) {
                val local = movieFor(pid, id) ?: continue
                if ((ContentType.MOVIE to local.first) in seen) continue
                add += ProgressEntity(pid, ContentType.MOVIE, local.first, null, local.second, null, null, 1, 1, now, true, manual = true, profileId = profile)
                nm++
            }
            for ((show, eps) in episodes.groupBy { it.tmdbId }) {
                val sid = seriesFor(pid, show) ?: continue
                val bySe = db.vod().episodesIn(pid, listOf(sid)).associateBy { it.season to it.episode }
                for (e in eps) {
                    val ep = bySe[e.season to e.episode] ?: continue
                    if ((ContentType.SERIES to ep.id) in seen) continue
                    add += ProgressEntity(pid, ContentType.SERIES, ep.id, sid, ep.title, null, null, 1, 1, now, true, e.season, e.episode, manual = true, profileId = profile)
                    ne++
                }
            }
            if (add.isNotEmpty()) db.profiles().upsertProgress(add)
            val favs = db.library().allFavorites(pid).filter { it.profileId == profile }.mapTo(HashSet()) { it.type to it.itemId }
            val newFavs = list.mapNotNull { (isMovie, id) ->
                val type = if (isMovie) ContentType.MOVIE else ContentType.SERIES
                val local = (if (isMovie) movieFor(pid, id)?.first else seriesFor(pid, id)) ?: return@mapNotNull null
                if ((type to local) in favs) null else FavoriteEntity(pid, type, local, now, profileId = profile)
            }.distinctBy { it.type to it.itemId }
            if (newFavs.isNotEmpty()) db.profiles().upsertFavorites(newFavs)
            nl += newFavs.size
        }
        return TraktReport(nm, ne, nl, now)
    }

    /** Film de l'abonnement pour un identifiant TMDB : (id, titre). */
    private suspend fun movieFor(pid: Long, tmdbId: Int): Pair<String, String>? {
        val id = db.vod().movieWithTmdb(pid, tmdbId.toString())
            ?: db.browse().keysForTmdb(pid, ContentType.MOVIE, tmdbId).firstNotNullOfOrNull { k -> db.vod().movieVersions(pid, k).firstOrNull()?.id }
            ?: return null
        return id to (db.movies().get(pid, id)?.title ?: "")
    }

    private suspend fun seriesFor(pid: Long, tmdbId: Int): String? =
        db.vod().seriesWithTmdb(pid, tmdbId.toString())
            ?: db.browse().keysForTmdb(pid, ContentType.SERIES, tmdbId).firstNotNullOfOrNull { k -> db.vod().seriesVersions(pid, k).firstOrNull()?.id }
}
