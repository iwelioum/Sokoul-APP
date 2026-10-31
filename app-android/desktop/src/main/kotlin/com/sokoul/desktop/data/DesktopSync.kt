package com.sokoul.desktop.data

import com.sokoul.iptv.data.net.XtreamAuth
import com.sokoul.iptv.data.net.XtreamClient
import com.sokoul.iptv.data.net.openStream
import com.sokoul.iptv.parser.M3uClassifier
import com.sokoul.iptv.parser.M3uKind
import com.sokoul.iptv.parser.M3uParser
import com.sokoul.iptv.parser.TitleTagger
import com.sokoul.iptv.parser.XmltvParser
import com.sokoul.iptv.parser.XtreamParse
import com.sokoul.iptv.parser.XtreamUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.xmlpull.v1.XmlPullParser
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class SyncState(
    val running: Boolean = false,
    val step: String = "",
    val error: String? = null,
    val channels: Int = 0,
    val programs: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
)

private data class VodSyncResult(
    val movies: Int = 0,
    val series: Int = 0,
    val errors: List<String> = emptyList(),
)

/**
 * Synchronisation des chaînes et du guide sur le PC, avec les parseurs partagés (:parser) et le client Xtream (:core).
 * Au lancement si les données ont plus de [STALE_MS], et à la demande depuis Réglages.
 */
class DesktopSync(private val db: DesktopDb) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val req = chain.request()
            if (req.header("User-Agent") == null) chain.proceed(req.newBuilder().header("User-Agent", userAgent()).build()) else chain.proceed(req)
        }
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }
    private val xtream = XtreamClient(http, json)

    fun userAgent(): String = db.setting(Settings.USER_AGENT) ?: Settings.DEFAULT_USER_AGENT

    fun auth(p: Playlist) = XtreamAuth(p.url.trimEnd('/'), p.username.orEmpty(), p.password.orEmpty(), p.userAgent)

    fun isStale(p: Playlist, now: Long = System.currentTimeMillis()) = now - p.syncedAt > STALE_MS || db.channelCount(p.id) == 0

    suspend fun syncIfStale(p: Playlist) { if (isStale(p)) sync(p) }

    suspend fun sync(p: Playlist) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                _state.value = SyncState(true, "Chaînes…")
                val n = when (p.type) {
                    PlaylistType.XTREAM -> syncXtream(p)
                    PlaylistType.M3U -> syncM3u(p)
                }
                applyPendingGroups(p.id)
                _state.value = _state.value.copy(running = true, step = "Guide TV…", channels = n)
                val programs = runCatching { syncEpg(p) }.getOrElse { e ->
                    _state.value = _state.value.copy(error = "Guide : " + com.sokoul.iptv.data.net.Redact.text(e.message ?: e.javaClass.simpleName))
                    0
                }
                db.markSynced(p.id, System.currentTimeMillis())
                _state.value = _state.value.copy(running = false, step = "Terminé", channels = n, programs = programs)
            } catch (e: Exception) {
                _state.value = SyncState(false, "", com.sokoul.iptv.data.net.Redact.text(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private suspend fun syncXtream(p: Playlist): Int {
        val a = auth(p)
        val cats = xtream.categories(a, "get_live_categories").mapIndexed { i, c -> Category(p.id, c.id, c.name, i) }
        val known = cats.map { it.id }.toSet()
        val channels = ArrayList<Channel>(4096)
        xtream.streamArray(a, "get_live_streams") { o ->
            val s = XtreamParse.live(o) ?: return@streamArray
            val cat = s.categoryId?.takeIf { it in known } ?: UNCATEGORIZED
            channels += Channel(
                p.id, s.streamId.toString(), s.num ?: (channels.size + 1), s.name, s.icon, cat,
                epgId(s.epgChannelId), TitleTagger.channelKey(s.name), null, null, null, channels.size,
            )
        }
        val all = if (channels.any { it.categoryId == UNCATEGORIZED }) cats + Category(p.id, UNCATEGORIZED, "Non classées", cats.size) else cats
        db.replaceLive(p.id, all, channels)
        _state.value = _state.value.copy(step = "Films et séries…", channels = channels.size)
        val vod = syncVod(p, a)
        val vodError = vod.errors.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        _state.value = _state.value.copy(
            error = vodError,
            movies = vod.movies,
            series = vod.series,
        )
        return channels.size
    }

    private suspend fun syncVod(p: Playlist, a: XtreamAuth): VodSyncResult {
        val movies = ArrayList<Movie>()
        val series = ArrayList<Series>()
        val errors = ArrayList<String>(2)
        try {
            xtream.streamArray(a, "get_vod_streams") { o ->
                XtreamParse.vod(o)?.let { item ->
                    movies += Movie(p.id, item.streamId.toString(), item.name, item.year, item.icon, item.rating, item.categoryId, item.extension, item.added, item.tmdbId)
                }
            }
        } catch (e: Exception) {
            errors += "Films : ${com.sokoul.iptv.data.net.Redact.text(e.message ?: e.javaClass.simpleName)}"
        }
        try {
            xtream.streamArray(a, "get_series") { o ->
                XtreamParse.series(o)?.let { item ->
                    series += Series(p.id, item.seriesId.toString(), item.name, item.releaseDate?.take(4)?.toIntOrNull(),
                        item.cover, item.backdrop, item.plot, item.rating, item.categoryId, item.lastModified, item.tmdbId)
                }
            }
        } catch (e: Exception) {
            errors += "Séries : ${com.sokoul.iptv.data.net.Redact.text(e.message ?: e.javaClass.simpleName)}"
        }
        if (errors.isEmpty()) db.replaceVod(p.id, movies, series)
        return VodSyncResult(movies.size, series.size, errors)
    }

    private fun syncM3u(p: Playlist): Int {
        val cats = LinkedHashMap<String, Category>()
        val channels = ArrayList<Channel>(4096)
        val movies = ArrayList<Movie>()
        val series = LinkedHashMap<String, Series>()
        val ids = HashMap<String, Int>()
        var headerEpg: String? = null
        val (resp, input) = http.openStream(p.url, p.userAgent)
        resp.use {
            M3uParser.parse(input.bufferedReader(), onHeader = { h -> headerEpg = h.epgUrls.joinToString(",").ifEmpty { null } }) { e ->
                when (M3uClassifier.classify(e)) {
                    M3uKind.MOVIE -> {
                        val info = TitleTagger.parse(e.name)
                        val id = sha1("${e.url}|${e.name}")
                        movies += Movie(p.id, id, info.clean, info.year, e.logo, null, e.group, e.url.substringAfterLast('.', "").takeIf { it.length in 2..5 }, null, null, e.url)
                        return@parse
                    }
                    M3uKind.EPISODE -> {
                        val ref = TitleTagger.episode(e.name) ?: return@parse
                        val key = sha1(ref.show.lowercase())
                        series.putIfAbsent(key, Series(p.id, key, ref.show, TitleTagger.parse(ref.show).year, e.logo, null, null, null, e.group, null, null))
                        return@parse
                    }
                    M3uKind.LIVE -> Unit
                }
                val group = e.group?.trim()?.ifEmpty { null } ?: "Non classées"
                val cat = cats.getOrPut(group) { Category(p.id, sha1(group), group, cats.size) }
                val base = sha1(listOf(e.tvgId, e.name, cat.id).joinToString("|") { it.orEmpty() })
                val n = ids.merge(base, 1, Int::plus)!!
                channels += Channel(
                    p.id, if (n == 1) base else "$base-$n", e.channelNumber ?: (channels.size + 1), e.name, e.logo, cat.id,
                    epgId(e.tvgId), TitleTagger.channelKey(e.tvgName ?: e.name), e.url, e.userAgent, e.referrer, channels.size,
                )
            }
        }
        if (p.epgUrl.isNullOrBlank() && headerEpg != null) db.putSetting("provider_epg_${p.id}", headerEpg)
        db.replaceLive(p.id, cats.values.toList(), channels)
        db.replaceVod(p.id, movies, series.values.toList())
        return channels.size
    }

    /** Sources du guide : URL XMLTV de la playlist, sinon celle du fournisseur (Xtream ou en-tête M3U). */
    fun epgSources(p: Playlist): List<String> {
        val custom = p.epgUrl?.split(',', '\n', ' ')?.map { it.trim() }?.filter { it.startsWith("http", true) }
        if (!custom.isNullOrEmpty()) return custom
        return when (p.type) {
            PlaylistType.XTREAM -> listOf(XtreamUrls.xmltv(p.url.trimEnd('/'), p.username.orEmpty(), p.password.orEmpty()))
            PlaylistType.M3U -> db.setting("provider_epg_${p.id}")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        }
    }

    private fun syncEpg(p: Playlist): Int {
        val now = System.currentTimeMillis()
        val offset = db.setting(Settings.EPG_OFFSET)?.toIntOrNull() ?: 0
        val chans = db.allChannels(p.id)
        val epgIds = chans.mapNotNullTo(HashSet()) { it.epgKey }
        val nameKeys = chans.mapTo(HashSet()) { it.nameKey }
        val wanted = HashSet<String>(epgIds)
        val byName = HashMap<String, String>()
        val programs = ArrayList<Program>(100_000)
        for (url in epgSources(p)) {
            val (resp, input) = http.openStream(url, p.userAgent)
            resp.use {
                val parser = org.kxml2.io.KXmlParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    runCatching { setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
                    setInput(input, null)
                }
                XmltvParser.parse(
                    parser,
                    onChannel = { ch ->
                        val id = epgId(ch.id) ?: return@parse
                        val keys = ch.names.map(TitleTagger::channelKey).filter { it.isNotEmpty() }
                        if (id in epgIds || keys.any { it in nameKeys }) {
                            wanted += id
                            keys.forEach { byName.putIfAbsent(it, id) }
                        }
                    },
                    onProgramme = { pr ->
                        val key = epgId(pr.channel) ?: return@parse
                        if (key in wanted) {
                            val start = DesktopPrefs.shiftGuide(pr.start, offset)
                            val end = DesktopPrefs.shiftGuide(pr.stop, offset)
                            if (end > now - DAY && start < now + 8 * DAY) programs += Program(key, start, end, pr.title, pr.description)
                        }
                    },
                )
            }
        }
        // chaînes sans identifiant de guide reconnu : rapprochées par nom, comme sur Android
        val found = programs.mapTo(HashSet()) { it.channelKey }
        val links = chans.filter { it.epgKey == null || it.epgKey !in found }.mapNotNull { c -> byName[c.nameKey]?.let { c.id to it } }
        db.assignEpgKeys(p.id, links)
        return db.replacePrograms(p.id, programs.asSequence(), now - DAY)
    }

    private fun applyPendingGroups(pid: Long) {
        val raw = db.setting("pending_groups_$pid") ?: return
        raw.lineSequence().forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 3) db.setCategoryFlags(pid, parts[0], parts[1].toBoolean(), parts[2].toBoolean())
        }
        db.putSetting("pending_groups_$pid", null)
    }

    companion object {
        const val STALE_MS = 12 * 3600_000L
        private const val DAY = 24 * 3600_000L
        const val UNCATEGORIZED = "__none"

        fun epgId(raw: String?): String? = raw?.trim()?.substringBefore('@')?.lowercase()?.takeIf { it.isNotEmpty() }

        private fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
