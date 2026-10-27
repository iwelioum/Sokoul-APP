package com.sokoul.iptv.data.debrid

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.data.net.await
import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.SourceState
import com.sokoul.iptv.parser.long
import com.sokoul.iptv.parser.str
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Une version proposée par l'extension de sources, lisible via Real-Debrid. */
data class DebridStream(
    /** Clé stable : « hash:fichier », ou empreinte du lien de l'extension. */
    val key: String,
    /** Nom brut de la version (affiché en détail, jamais analysé ailleurs). */
    val title: String,
    /** Lien déjà résolu par l'extension (extension configurée avec Real-Debrid). */
    val url: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val filename: String?,
    val release: Release,
    /** Déjà dans le compte Real-Debrid de l'utilisateur, prêt (« Mes fichiers ») : lecture immédiate. */
    val inAccount: Boolean = false,
    /** En cours de téléchargement dans le compte (pourcentage), null sinon. */
    val preparing: Int? = null,
) {
    val magnet get() = infoHash?.let { "magnet:?xt=urn:btih:$it" }

    /** Prête tout de suite : dans le compte, ou annoncée en cache par l'extension. */
    val readyNow: Boolean? get() = if (inAccount) true else if (preparing != null) false else release.cached

    fun toSource(): PlaybackSource = PlaybackSource(
        id = key, origin = SourceOrigin.DEBRID, quality = release.quality, hdr = release.hdr, language = release.language,
        state = if (readyNow == true) SourceState.CACHED_INSTANT else SourceState.AVAILABLE,
        sizeBytes = release.sizeBytes, rip = release.source?.label,
    )
}

/** Œuvre à chercher : film, ou épisode d'une série (identifiants TMDB). */
data class DebridQuery(val tmdbId: Int, val series: Boolean, val season: Int? = null, val episode: Int? = null) {
    /** Forme sérialisable pour une route : « m:157336 », « s:1396:2:5 ». */
    fun encode() = if (series) "s:$tmdbId:$season:$episode" else "m:$tmdbId"

    companion object {
        fun decode(s: String?): DebridQuery? {
            val p = s?.split(':') ?: return null
            return when (p.firstOrNull()) {
                "m" -> p.getOrNull(1)?.toIntOrNull()?.let { DebridQuery(it, false) }
                "s" -> {
                    val id = p.getOrNull(1)?.toIntOrNull() ?: return null
                    DebridQuery(id, true, p.getOrNull(2)?.toIntOrNull(), p.getOrNull(3)?.toIntOrNull())
                }
                else -> null
            }
        }
    }
}

/**
 * Sources Real-Debrid d'une œuvre : TMDB → IMDb, puis extension au format Stremio
 * (`/stream/movie/tt….json`, `/stream/series/tt…:S:E.json`). Résultats gardés 6 h en mémoire.
 * L'adresse de l'extension peut contenir une clé : elle n'est jamais écrite dans un journal.
 */
@Singleton
class DebridSources @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: SettingsStore,
    private val tmdb: TmdbClient,
    private val rd: RealDebridClient,
    @ApplicationContext private val context: Context,
    private val profiles: com.sokoul.iptv.data.repo.ProfileRepository,
    private val cloud: DebridCloud,
) {
    private data class Entry(val at: Long, val streams: List<DebridStream>)
    private val cache = ConcurrentHashMap<DebridQuery, Entry>()
    /** Dernière liste complète (extension + compte) par œuvre. */
    private val last = ConcurrentHashMap<DebridQuery, List<DebridStream>>()

    /** Real-Debrid utilisable : clé (ou association), hors profil Enfant. Les fichiers du compte sont alors des sources. */
    suspend fun enabled(): Boolean = rd.configured() && !profiles.isKids()

    /** Recherche de nouvelles versions active : Real-Debrid utilisable et extension de sources renseignée. */
    suspend fun searchEnabled(): Boolean = enabled() && addonUrls().isNotEmpty()

    /**
     * Versions Real-Debrid de l'œuvre : celles de l'extension de sources (si elle est renseignée) et les fichiers
     * déjà dans le compte. Une même empreinte n'apparaît qu'une fois, marquée « dans votre compte ».
     */
    suspend fun find(q: DebridQuery): List<DebridStream> {
        if (!enabled()) return emptyList()
        val addon = runCatching { addonStreams(q) }.getOrElse { if (it is CancellationException) throw it; emptyList() }
        val account = runCatching { accountStreams(q) }.getOrElse { if (it is CancellationException) throw it; emptyList() }
        return merge(addon, account).also { last[q] = it }
    }

    /** Fichiers du compte qui sont cette œuvre (titres et année TMDB). */
    private suspend fun accountStreams(q: DebridQuery): List<DebridStream> {
        val key = settings.current().tmdbApiKey
        val info = tmdb.details(key, if (q.series) "tv" else "movie", q.tmdbId, full = false) ?: return emptyList()
        val titles = listOfNotNull(info.title, info.originalTitle).distinct()
        return cloud.forWork(titles, info.year, q.season.takeIf { q.series }, q.episode.takeIf { q.series })
    }

    /** Adresses à interroger : Torrentio et Comet (jeton ajouté maintenant, jamais stocké), plus celles ajoutées à la main. */
    private suspend fun addonUrls(): List<String> {
        val s = settings.current()
        return DebridAddons.urls(DebridAddons.parse(s.debridAddons), s.debridAddon, rd.token())
    }

    /**
     * Toutes les extensions en parallèle (15 s chacune), résultats fusionnés : une même release renvoyée par deux
     * extensions n'apparaît qu'une fois. Gardé 6 h en mémoire (sauf réponse vide : on réessaiera).
     */
    private suspend fun addonStreams(q: DebridQuery): List<DebridStream> {
        cache[q]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }?.let { return it.streams }
        val urls = addonUrls()
        if (urls.isEmpty()) return emptyList()
        val s = settings.current()
        val imdb = tmdb.imdbId(s.tmdbApiKey, if (q.series) "tv" else "movie", q.tmdbId) ?: return emptyList()
        val path = if (q.series) "stream/series/$imdb:${q.season}:${q.episode}.json" else "stream/movie/$imdb.json"
        val results = kotlinx.coroutines.coroutineScope {
            urls.map { base -> async { fetch("$base/$path") } }.map { it.await() }
        }
        val streams = combine(results)
        if (streams.isNotEmpty()) cache[q] = Entry(System.currentTimeMillis(), streams)
        return streams
    }

    private suspend fun fetch(address: String): List<DebridStream> {
        val url = address.toHttpUrlOrNull() ?: return emptyList()
        val text = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                http.newCall(Request.Builder().url(url).header("User-Agent", "SokoulTV/2.0").build()).await().use { r ->
                    // jamais l'adresse dans le journal : elle contient le jeton Real-Debrid
                    if (r.isSuccessful) r.body?.string() else { Timber.w("Extension de sources (${url.host}) : HTTP ${r.code}"); null }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Timber.w("Extension de sources injoignable (${url.host})"); null }
        return text?.let { parse(it, json) }.orEmpty()
    }

    /** Une version déjà trouvée (lecteur relancé avec la même clé). */
    fun cached(q: DebridQuery, key: String): DebridStream? = (last[q] ?: cache[q]?.streams)?.firstOrNull { it.key == key }

    /** Préférences de classement selon les réglages et le réseau du moment. */
    suspend fun prefs(iptvBlocked: Boolean = false): SourceRanker.Prefs {
        val s = settings.current()
        return SourceRanker.Prefs(
            languages = com.sokoul.iptv.data.repo.VersionPicker.parseOrder(s.vodLanguages),
            maxBytes = maxBytes(s, ethernet()),
            supports4k = com.sokoul.iptv.data.repo.DeviceCaps.supports4k,
            iptvBlocked = iptvBlocked,
            tieOrigin = com.sokoul.iptv.domain.SourceOrigin.entries.firstOrNull { it.name == s.sourceTie },
        )
    }

    private fun ethernet(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
    }.getOrDefault(false)

    companion object {
        const val TTL_MS = 6 * 3600_000L
        const val TIMEOUT_MS = 15_000L

        /** 0 = automatique : 25 Go en Wi-Fi, sans limite en câble ; 200 = sans limite. */
        fun maxBytes(s: AppSettings, ethernet: Boolean): Long? = when {
            s.debridMaxGb == 200 -> null
            s.debridMaxGb > 0 -> s.debridMaxGb * 1_000_000_000L
            ethernet -> null
            else -> 25_000_000_000L
        }

        /**
         * Résultats de plusieurs extensions → une liste sans doublon : même clé, même empreinte de torrent (et fichier),
         * ou même nom de fichier et même taille. L'ordre de la première extension est gardé.
         */
        fun combine(results: List<List<DebridStream>>): List<DebridStream> {
            val seen = HashSet<String>()
            val out = mutableListOf<DebridStream>()
            for (list in results) for (s in list) {
                val name = (s.filename ?: s.title.lineSequence().firstOrNull())?.lowercase()?.filter { it.isLetterOrDigit() }
                val ids = listOfNotNull(
                    "k:" + s.key,
                    s.infoHash?.let { "h:$it:" + (s.fileIdx ?: -1) },
                    if (!name.isNullOrEmpty() && s.release.sizeBytes != null) "n:$name|" + s.release.sizeBytes / 50_000_000 else null,
                )
                if (ids.any { it in seen }) continue
                seen += ids
                out += s
            }
            return out
        }

        /**
         * Versions de l'extension + fichiers du compte : une version de l'extension déjà dans le compte (même empreinte)
         * est marquée « dans votre compte » (ou « en préparation ») ; les autres fichiers du compte s'ajoutent à la fin.
         */
        fun merge(addon: List<DebridStream>, account: List<DebridStream>): List<DebridStream> {
            val byHash = account.filter { it.infoHash != null }.associateBy { it.infoHash!!.lowercase() }
            val used = HashSet<String>()
            val out = addon.map { a ->
                val c = a.infoHash?.lowercase()?.let(byHash::get) ?: return@map a
                used += c.key
                a.copy(inAccount = c.inAccount, preparing = c.preparing)
            }
            return out + account.filter { it.key !in used }
        }

        /** Réponse Stremio `{"streams":[…]}` → versions lisibles (films filmés en salle écartés). */
        fun parse(text: String, json: Json = Json): List<DebridStream> {
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
            return (root["streams"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val hints = o["behaviorHints"] as? JsonObject
                val url = o.str("url")?.takeIf { it.startsWith("http") }
                val hash = o.str("infoHash")?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{40}")) }
                    // extension réglée avec Real-Debrid : l'empreinte est dans le lien (« /resolve/realdebrid/…/<hash>/… »)
                    ?: url?.let { u -> Regex("[/=]([0-9a-fA-F]{40})(?=[/?]|$)").find(u)?.groupValues?.get(1)?.lowercase() }
                if (url == null && hash == null) return@mapNotNull null
                val name = o.str("name").orEmpty()
                val title = o.str("title") ?: o.str("description").orEmpty()
                val filename = hints?.str("filename")
                val release = ReleaseName.parse(name, title, filename).let { r ->
                    if (r.sizeBytes == null) r.copy(sizeBytes = hints?.long("videoSize")) else r
                }
                if (release.cam) return@mapNotNull null
                val fileIdx = o.long("fileIdx")?.toInt()
                DebridStream(
                    key = hash?.let { "$it:${fileIdx ?: -1}" } ?: ("u" + url.hashCode().toUInt().toString(16)),
                    title = (filename ?: title.lineSequence().firstOrNull() ?: name).trim(),
                    url = url, infoHash = hash, fileIdx = fileIdx, filename = filename, release = release,
                )
            }.distinctBy { it.key }
        }
    }
}
