package com.sokoul.iptv.data.repo

import android.util.Xml
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryEntity
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpgChannelEntity
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.net.XtreamAuth
import com.sokoul.iptv.data.net.XtreamClient
import com.sokoul.iptv.data.net.openStream
import com.sokoul.iptv.parser.M3uClassifier
import com.sokoul.iptv.parser.M3uKind
import com.sokoul.iptv.parser.M3uParser
import com.sokoul.iptv.parser.TitleTagger
import com.sokoul.iptv.parser.XtreamParse
import com.sokoul.iptv.parser.XtreamUrls
import com.sokoul.iptv.parser.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class SyncStatus(
    val running: Boolean = false,
    val step: String = "",
    val count: Int = 0,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
    val finishedAt: Long = 0,
)

/**
 * Synchronise playlists et guides TV dans Room.
 *
 * Stratégie « génération » : chaque passage écrit ses lignes avec un numéro de génération
 * puis supprime les lignes d'une génération antérieure. L'interface reste utilisable
 * pendant toute la synchronisation, et un échec réseau ne vide jamais la base.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
    private val xtream: XtreamClient,
    private val http: OkHttpClient,
    private val settings: SettingsStore,
    private val vod: VodRepository,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val _playlistStatus = MutableStateFlow<Map<Long, SyncStatus>>(emptyMap())
    private val _epgStatus = MutableStateFlow<Map<Long, SyncStatus>>(emptyMap())
    val playlistStatus: StateFlow<Map<Long, SyncStatus>> = _playlistStatus.asStateFlow()
    val epgStatus: StateFlow<Map<Long, SyncStatus>> = _epgStatus.asStateFlow()

    private fun lock(id: Long) = locks.getOrPut(id) { Mutex() }

    private fun setStatus(flow: MutableStateFlow<Map<Long, SyncStatus>>, id: Long, s: SyncStatus) =
        flow.update { it + (id to s) }

    suspend fun syncPlaylist(id: Long) = lock(id).withLock {
        val p = playlists.get(id) ?: return@withLock
        setStatus(_playlistStatus, id, SyncStatus(running = true, step = "Connexion…"))
        try {
            val warnings = withContext(Dispatchers.IO) {
                when (p.type) {
                    PlaylistType.XTREAM -> syncXtream(p)
                    PlaylistType.M3U -> syncM3u(p)
                }
            }
            if (playlists.get(id) == null) {
                // Playlist supprimée pendant la synchronisation : on ne laisse pas de données orphelines.
                playlists.purgeData(id)
                setStatus(_playlistStatus, id, SyncStatus(error = "Playlist supprimée"))
                return@withLock
            }
            db.channels().resetEpgKeys(id)
            db.channels().matchEpgByName(id)
            // Doublons : relie les versions TMDB et sans TMDB d'une même œuvre (ne bloque jamais la synchro)
            setStatus(_playlistStatus, id, SyncStatus(running = true, step = "Regroupement des doublons…"))
            withContext(Dispatchers.Default) {
                for (t in listOf(ContentType.MOVIE, ContentType.SERIES)) runCatching { vod.regroup(id, t) }.onFailure { Timber.w(it, "Regroupement") }
            }
            val counts = Triple(db.channels().count(id), db.movies().count(id), db.series().count(id))
            db.playlists().markSynced(id, System.currentTimeMillis(), counts.first, counts.second, counts.third)
            setStatus(_playlistStatus, id, SyncStatus(step = "Terminé", count = counts.first + counts.second + counts.third, warnings = warnings, finishedAt = System.currentTimeMillis()))
        } catch (e: kotlinx.coroutines.CancellationException) {
            setStatus(_playlistStatus, id, SyncStatus(error = "Synchronisation annulée"))
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Échec de synchronisation de la playlist $id")
            setStatus(_playlistStatus, id, SyncStatus(error = e.userMessage(), finishedAt = System.currentTimeMillis()))
            throw e
        }
    }

    // --- Xtream -----------------------------------------------------------------------------

    private suspend fun syncXtream(p: PlaylistEntity): List<String> {
        val auth = playlists.auth(p) ?: throw IOException("Mot de passe manquant ou illisible : modifiez la playlist pour le saisir")
        val account = xtream.account(auth)
        if (!account.auth) throw IOException("Identifiants refusés par le serveur")
        db.playlists().update(
            p.copy(expiresAt = account.expiresAt, maxConnections = account.maxConnections, serverTimezone = account.serverTimezone ?: p.serverTimezone)
        )
        // Compte expiré : on garde le catalogue existant et on explique pourquoi rien ne se met à jour.
        if (!account.isActive) throw IOException(
            "Abonnement inactif ou expiré (statut : ${account.status ?: "inconnu"})" +
                (account.expiresAt?.let { " — échéance ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE).format(java.util.Date(it))}" } ?: "")
        )
        val warnings = ArrayList<String>()
        val gen = System.currentTimeMillis()
        val pid = p.id

        runSection("Chaînes", warnings) { syncXtreamLive(pid, auth, gen) }
        runSection("Films", warnings) { syncXtreamMovies(pid, auth, gen) }
        runSection("Séries", warnings) { syncXtreamSeries(pid, auth, gen) }
        return warnings
    }

    private suspend fun runSection(name: String, warnings: MutableList<String>, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Section $name en échec")
            warnings += "$name : ${e.userMessage()}"
        }
    }

    private suspend fun syncXtreamLive(pid: Long, auth: XtreamAuth, gen: Long) {
        status(pid, "Catégories des chaînes…")
        val cats = xtream.categories(auth, "get_live_categories")
        val known = cats.map { it.id }.toHashSet()
        db.categories().upsert(cats.mapIndexed { i, c -> CategoryEntity(pid, ContentType.LIVE, c.id, c.name, i, gen) })
        var usedUnknown = false
        var position = 0
        val batch = ArrayList<ChannelEntity>(BATCH)
        xtream.streamArray(auth, "get_live_streams") { o ->
            val s = XtreamParse.live(o) ?: return@streamArray
            val cat = s.categoryId?.takeIf { it in known } ?: UNCATEGORIZED.also { usedUnknown = true }
            val tvg = epgId(s.epgChannelId)
            batch += ChannelEntity(
                playlistId = pid, id = s.streamId.toString(), number = s.num ?: (position + 1), name = s.name,
                searchName = searchable(s.name), logo = s.icon, categoryId = cat, tvgId = tvg, epgKey = tvg,
                nameKey = TitleTagger.channelKey(s.name), streamUrl = null,
                catchupType = if (s.hasArchive) CATCHUP_XTREAM else null, catchupSource = null,
                catchupDays = s.archiveDays, userAgent = null, referrer = null, position = position++, gen = gen,
            )
            if (batch.size >= BATCH) {
                db.channels().upsert(batch.toList()); batch.clear(); status(pid, "Chaînes…", position)
            }
        }
        if (batch.isNotEmpty()) db.channels().upsert(batch)
        if (usedUnknown) db.categories().upsert(listOf(CategoryEntity(pid, ContentType.LIVE, UNCATEGORIZED, "Non classées", cats.size, gen)))
        keepIfEmpty(position, db.channels().count(pid), "aucune chaîne")
        db.channels().deleteStale(pid, gen)
        db.categories().deleteStale(pid, ContentType.LIVE, gen)
    }

    private suspend fun syncXtreamMovies(pid: Long, auth: XtreamAuth, gen: Long) {
        status(pid, "Catégories des films…")
        val cats = xtream.categories(auth, "get_vod_categories")
        val known = cats.map { it.id }.toHashSet()
        db.categories().upsert(cats.mapIndexed { i, c -> CategoryEntity(pid, ContentType.MOVIE, c.id, c.name, i, gen) })
        var usedUnknown = false
        var position = 0
        val batch = ArrayList<MovieEntity>(BATCH)
        val links = db.vod().links(pid, ContentType.MOVIE).associate { it.itemId to it.tmdbId }
        xtream.streamArray(auth, "get_vod_streams") { o ->
            val v = XtreamParse.vod(o) ?: return@streamArray
            val info = TitleTagger.parse(v.name)
            val cat = v.categoryId?.takeIf { it in known } ?: UNCATEGORIZED.also { usedUnknown = true }
            batch += MovieEntity(
                playlistId = pid, id = v.streamId.toString(), name = v.name, title = info.clean, searchName = searchable(info.clean),
                year = v.year ?: info.year, language = info.language, quality = info.quality, poster = v.icon, rating = v.rating,
                categoryId = cat, extension = v.extension, streamUrl = null, added = v.added ?: 0, tmdbId = v.tmdbId,
                position = position++, gen = gen,
                groupKey = groupKey(v.streamId.toString(), info.clean, v.year ?: info.year, v.tmdbId, links),
            )
            if (batch.size >= BATCH) {
                db.movies().upsert(batch.toList()); batch.clear(); status(pid, "Films…", position)
            }
        }
        if (batch.isNotEmpty()) db.movies().upsert(batch)
        if (usedUnknown) db.categories().upsert(listOf(CategoryEntity(pid, ContentType.MOVIE, UNCATEGORIZED, "Non classés", cats.size, gen)))
        keepIfEmpty(position, db.movies().count(pid), "aucun film")
        db.movies().deleteStale(pid, gen)
        db.categories().deleteStale(pid, ContentType.MOVIE, gen)
    }

    private suspend fun syncXtreamSeries(pid: Long, auth: XtreamAuth, gen: Long) {
        status(pid, "Catégories des séries…")
        val cats = xtream.categories(auth, "get_series_categories")
        val known = cats.map { it.id }.toHashSet()
        db.categories().upsert(cats.mapIndexed { i, c -> CategoryEntity(pid, ContentType.SERIES, c.id, c.name, i, gen) })
        var usedUnknown = false
        var position = 0
        val batch = ArrayList<SeriesEntity>(BATCH)
        val links = db.vod().links(pid, ContentType.SERIES).associate { it.itemId to it.tmdbId }
        xtream.streamArray(auth, "get_series") { o ->
            val s = XtreamParse.series(o) ?: return@streamArray
            val info = TitleTagger.parse(s.name)
            val cat = s.categoryId?.takeIf { it in known } ?: UNCATEGORIZED.also { usedUnknown = true }
            batch += SeriesEntity(
                playlistId = pid, id = s.seriesId.toString(), name = s.name, title = info.clean, searchName = searchable(info.clean),
                year = info.year ?: s.releaseDate?.take(4)?.toIntOrNull(), poster = s.cover, backdrop = s.backdrop, plot = s.plot,
                genre = s.genre, cast = s.cast, rating = s.rating, categoryId = cat, lastModified = s.lastModified ?: 0,
                tmdbId = s.tmdbId, position = position++, gen = gen,
                groupKey = groupKey(s.seriesId.toString(), info.clean, info.year ?: s.releaseDate?.take(4)?.toIntOrNull(), s.tmdbId, links),
            )
            if (batch.size >= BATCH) {
                db.series().upsert(batch.toList()); batch.clear(); status(pid, "Séries…", position)
            }
        }
        if (batch.isNotEmpty()) db.series().upsert(batch)
        if (usedUnknown) db.categories().upsert(listOf(CategoryEntity(pid, ContentType.SERIES, UNCATEGORIZED, "Non classées", cats.size, gen)))
        keepIfEmpty(position, db.series().count(pid), "aucune série")
        db.series().deleteStale(pid, gen)
        db.series().deleteOrphanEpisodes(pid)
        db.categories().deleteStale(pid, ContentType.SERIES, gen)
    }

    // --- M3U --------------------------------------------------------------------------------

    private suspend fun syncM3u(p: PlaylistEntity): List<String> {
        val pid = p.id
        val gen = System.currentTimeMillis()
        val ctx = coroutineContext
        status(pid, "Téléchargement de la playlist…")

        val categories = LinkedHashMap<Pair<ContentType, String>, CategoryEntity>()
        fun categoryFor(type: ContentType, group: String?): String {
            val name = group?.trim()?.takeIf { it.isNotEmpty() } ?: "Non classé"
            val key = type to name
            categories.getOrPut(key) { CategoryEntity(pid, type, name, name, categories.size, gen) }
            return name
        }

        val channels = ArrayList<ChannelEntity>(BATCH)
        val movies = ArrayList<MovieEntity>(BATCH)
        val episodes = ArrayList<EpisodeEntity>(BATCH)
        val seriesMap = LinkedHashMap<String, SeriesEntity>()
        val idCounts = HashMap<String, Int>()
        val movieLinks = db.vod().links(pid, ContentType.MOVIE).associate { it.itemId to it.tmdbId }
        val seriesLinks = db.vod().links(pid, ContentType.SERIES).associate { it.itemId to it.tmdbId }
        var liveCount = 0
        var movieCount = 0
        var headerEpg: String? = null

        fun stableId(vararg parts: String?): String {
            val base = sha1(parts.joinToString("|") { it.orEmpty() })
            val n = idCounts.merge(base, 1, Int::plus)!!
            return if (n == 1) base else "$base-$n"
        }

        fun flush() {
            if (channels.isEmpty() && movies.isEmpty() && episodes.isEmpty()) return
            db.runInTransaction {
                if (channels.isNotEmpty()) db.channels().upsertNow(channels)
                if (movies.isNotEmpty()) db.movies().upsertNow(movies)
                if (episodes.isNotEmpty()) db.series().upsertEpisodesNow(episodes)
            }
            channels.clear(); movies.clear(); episodes.clear()
            setStatus(_playlistStatus, pid, SyncStatus(running = true, step = "Lecture de la playlist…", count = liveCount + movieCount + seriesMap.size))
        }

        val (closeable, stream) = playlists.openPlaylistStream(p.url, p.userAgent)
        closeable.use {
            var headerCatchup: String? = null
            var headerCatchupSource: String? = null
            var headerCatchupDays = 0
            M3uParser.parse(stream.bufferedReader(), onHeader = { h ->
                headerEpg = h.epgUrls.joinToString(",").ifEmpty { null }
                headerCatchup = h.catchup
                headerCatchupSource = h.catchupSource
                headerCatchupDays = h.catchupDays ?: 0
            }) { e ->
                ctx.ensureActive()
                when (M3uClassifier.classify(e)) {
                    M3uKind.LIVE -> {
                        val cat = categoryFor(ContentType.LIVE, e.group)
                        val tvg = epgId(e.tvgId)
                        channels += ChannelEntity(
                            playlistId = pid, id = stableId(e.tvgId, e.name, cat), number = e.channelNumber ?: (liveCount + 1),
                            name = e.name, searchName = searchable(e.name), logo = e.logo, categoryId = cat, tvgId = tvg, epgKey = tvg,
                            nameKey = TitleTagger.channelKey(e.tvgName ?: e.name), streamUrl = e.url,
                            catchupType = e.catchup ?: headerCatchup, catchupSource = e.catchupSource ?: headerCatchupSource,
                            catchupDays = e.catchupDays ?: headerCatchupDays,
                            userAgent = e.userAgent, referrer = e.referrer, position = liveCount++, gen = gen,
                        )
                    }
                    M3uKind.MOVIE -> {
                        val cat = categoryFor(ContentType.MOVIE, e.group)
                        val info = TitleTagger.parse(e.name)
                        movies += MovieEntity(
                            playlistId = pid, id = stableId(e.name, cat), name = e.name, title = info.clean,
                            searchName = searchable(info.clean), year = info.year, language = info.language, quality = info.quality,
                            poster = e.logo, rating = null, categoryId = cat, extension = e.url.substringBefore('?').substringAfterLast('.', "").take(5).ifEmpty { null },
                            streamUrl = e.url, added = gen - movieCount, tmdbId = null, position = movieCount++, gen = gen,
                        ).let { m -> m.copy(groupKey = groupKey(m.id, m.title, m.year, null, movieLinks)) }
                    }
                    M3uKind.EPISODE -> {
                        val ref = TitleTagger.episode(e.name) ?: return@parse
                        val cat = categoryFor(ContentType.SERIES, e.group)
                        val seriesId = sha1("$cat|${ref.show.lowercase()}").take(16)
                        val series = seriesMap.getOrPut(seriesId) {
                            SeriesEntity(
                                playlistId = pid, id = seriesId, name = ref.show, title = ref.show, searchName = searchable(ref.show),
                                year = null, poster = e.logo, backdrop = null, plot = null, genre = null, cast = null, rating = null,
                                categoryId = cat, lastModified = gen - seriesMap.size, tmdbId = null, position = seriesMap.size, gen = gen,
                            )
                        }
                        episodes += EpisodeEntity(
                            playlistId = pid, seriesId = series.id, id = stableId(series.id, ref.season.toString(), ref.episode.toString()),
                            season = ref.season, episode = ref.episode, title = e.name, plot = null, image = e.logo, durationSecs = null,
                            extension = e.url.substringBefore('?').substringAfterLast('.', "").take(5).ifEmpty { null },
                            streamUrl = e.url, rating = null,
                        )
                    }
                }
                if (channels.size + movies.size + episodes.size >= BATCH) flush()
            }
        }
        flush()
        db.runInTransaction {
            db.categories().upsertNow(categories.values.toList())
            seriesMap.values.map { s -> s.copy(groupKey = groupKey(s.id, s.title, s.year, null, seriesLinks)) }
                .chunked(BATCH).forEach { db.series().upsertNow(it) }
        }
        db.channels().deleteStale(pid, gen)
        db.movies().deleteStale(pid, gen)
        db.series().deleteStale(pid, gen)
        db.series().deleteOrphanEpisodes(pid)
        for (t in ContentType.entries) db.categories().deleteStale(pid, t, gen)
        playlists.get(pid)?.let { db.playlists().update(it.copy(providerEpgUrl = headerEpg)) }
        if (liveCount + movieCount + seriesMap.size == 0) throw IOException("La playlist est vide")
        return emptyList()
    }

    // --- EPG --------------------------------------------------------------------------------

    fun epgSources(p: PlaylistEntity): List<String> {
        val custom = p.epgUrl?.split(',', '\n', ' ')?.map { it.trim() }?.filter { PlaylistSource.of(it) != PlaylistSource.INVALID }
        if (!custom.isNullOrEmpty()) return custom
        return when (p.type) {
            PlaylistType.XTREAM -> playlists.auth(p)?.let { listOf(XtreamUrls.xmltv(it.server, it.username, it.password)) } ?: emptyList()
            PlaylistType.M3U -> p.providerEpgUrl?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        }
    }

    suspend fun syncEpg(id: Long) = lock(-id - 1).withLock {
        val p = playlists.get(id) ?: return@withLock
        val sources = epgSources(p)
        if (sources.isEmpty()) {
            setStatus(_epgStatus, id, SyncStatus(error = "Aucune source EPG : ajoutez une URL XMLTV dans la playlist", finishedAt = System.currentTimeMillis()))
            return@withLock
        }
        setStatus(_epgStatus, id, SyncStatus(running = true, step = "Téléchargement du guide…"))
        try {
            val total = withContext(Dispatchers.IO) { importEpg(p, sources) }
            if (playlists.get(id) == null) { playlists.purgeData(id); return@withLock }
            setStatus(_epgStatus, id, SyncStatus(step = "Terminé", count = total, finishedAt = System.currentTimeMillis()))
        } catch (e: kotlinx.coroutines.CancellationException) {
            setStatus(_epgStatus, id, SyncStatus(error = "Mise à jour du guide annulée"))
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Échec EPG $id")
            setStatus(_epgStatus, id, SyncStatus(error = e.userMessage(), finishedAt = System.currentTimeMillis()))
            throw e
        }
    }

    private suspend fun importEpg(p: PlaylistEntity, sources: List<String>): Int {
        val pid = p.id
        val ctx = coroutineContext
        val gen = System.currentTimeMillis()
        val offset = settings.current().epgOffsetMinutes * 60_000L
        val now = System.currentTimeMillis()
        val keepFrom = now - 8 * DAY
        val keepTo = now + 10 * DAY

        val refs = db.channels().epgRefs(pid)
        val epgIds = refs.mapNotNullTo(HashSet()) { it.tvgId }
        val nameKeys = refs.mapTo(HashSet()) { it.nameKey }
        val wanted = HashSet<String>(epgIds)

        val programs = ArrayList<ProgramEntity>(EPG_BATCH)
        val epgChannels = ArrayList<EpgChannelEntity>(512)
        var total = 0
        var okSources = 0
        val errors = ArrayList<String>()

        fun flush() {
            if (programs.isEmpty() && epgChannels.isEmpty()) return
            db.runInTransaction {
                if (epgChannels.isNotEmpty()) db.programs().upsertEpgChannelsNow(epgChannels)
                if (programs.isNotEmpty()) db.programs().upsertNow(programs)
            }
            programs.clear(); epgChannels.clear()
            setStatus(_epgStatus, pid, SyncStatus(running = true, step = "Import du guide…", count = total))
        }

        db.programs().clearEpgChannels(pid)
        for (url in sources) {
            try {
                val (closeable, stream) = playlists.openPlaylistStream(url, p.userAgent)
                closeable.use {
                    val parser = Xml.newPullParser().apply {
                        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        // mode tolérant : entités HTML (&nbsp;…) et petites erreurs fréquentes dans les XMLTV
                        runCatching { setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
                        setInput(stream, null)
                    }
                    XmltvParser.parse(
                        parser,
                        onChannel = { ch ->
                            val idL = epgId(ch.id) ?: return@parse
                            val keys = ch.names.map(TitleTagger::channelKey).filter { it.isNotEmpty() }.toSet()
                            val matches = idL in epgIds || keys.any { it in nameKeys }
                            if (matches) {
                                wanted += idL
                                keys.forEach { epgChannels += EpgChannelEntity(pid, idL, it, ch.icon) }
                                if (keys.isEmpty()) epgChannels += EpgChannelEntity(pid, idL, "", ch.icon)
                            }
                        },
                        onProgramme = { pr ->
                            ctx.ensureActive()
                            val key = epgId(pr.channel) ?: return@parse
                            if (key in wanted) {
                                val start = pr.start + offset
                                val end = pr.stop + offset
                                if (end > keepFrom && start < keepTo) {
                                    programs += ProgramEntity(pid, key, start, end, pr.title, pr.description, pr.category, pr.episode ?: pr.subtitle, pr.icon, gen)
                                    total++
                                    if (programs.size >= EPG_BATCH) flush()
                                }
                            }
                        },
                    )
                }
                flush()
                okSources++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Source EPG en échec")
                errors += e.userMessage()
            }
        }
        if (okSources == 0) throw IOException(errors.firstOrNull() ?: "Guide TV indisponible")
        db.programs().deleteStaleGen(pid, gen)
        db.programs().deleteOlderThan(keepFrom)
        db.channels().resetEpgKeys(pid)
        db.channels().matchEpgByName(pid)
        db.playlists().markEpgSynced(pid, System.currentTimeMillis())
        return total
    }

    /**
     * Réponse vide alors que la base contient déjà des éléments : souvent un incident passager du serveur.
     * On n'efface rien et on le signale.
     */
    private fun keepIfEmpty(received: Int, stored: Int, what: String) {
        if (shouldKeepExisting(received, stored)) throw IOException("Le serveur n'a renvoyé $what : liste actuelle conservée")
    }

    private fun status(pid: Long, step: String, count: Int = 0) =
        setStatus(_playlistStatus, pid, SyncStatus(running = true, step = step, count = count))

    companion object {
        const val UNCATEGORIZED = "__none"
        const val CATCHUP_XTREAM = "xtream"
        private const val BATCH = 1000
        private const val EPG_BATCH = 3000
        private const val DAY = 24 * 3600_000L

        /** Identifiant EPG comparable : minuscules, sans suffixe de flux iptv-org (TF1.fr@HD → tf1.fr). */
        /** [stored] inclut les lignes de la génération en cours : vide côté serveur = 0 reçu. */
        fun shouldKeepExisting(received: Int, stored: Int) = received == 0 && stored > 0

        fun epgId(raw: String?): String? = raw?.trim()?.substringBefore('@')?.lowercase()?.takeIf { it.isNotEmpty() }

        /** Clé provisoire calculée à l'insertion ; les ponts TMDB ↔ titre sont faits par VodRepository.regroup. */
        fun groupKey(id: String, title: String, year: Int?, tmdbId: String?, links: Map<String, String>): String =
            VodGrouping.provisionalKey(VodGrouping.Row(id, title, year, tmdbId?.takeIf { it.isNotBlank() } ?: links[id]))

        fun searchable(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

        private fun sha1(s: String): String {
            val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            return d.joinToString("") { "%02x".format(it) }.take(20)
        }
    }
}

/** Message d'erreur affichable. Toujours expurgé : une URL Xtream contient l'identifiant et le mot de passe. */
fun Throwable.userMessage(): String = com.sokoul.iptv.data.net.HttpModule.redact(
    when (this) {
        is java.net.UnknownHostException -> "Serveur introuvable : vérifiez l'adresse et la connexion Internet"
        is java.net.SocketTimeoutException -> "Le serveur met trop de temps à répondre"
        is java.net.ConnectException -> "Connexion refusée par le serveur"
        is javax.net.ssl.SSLException -> "Erreur de sécurité SSL : essayez l'adresse en http://"
        is IllegalArgumentException -> message ?: "Valeur invalide"
        is IOException -> message ?: "Erreur réseau"
        else -> message ?: javaClass.simpleName
    }
)
