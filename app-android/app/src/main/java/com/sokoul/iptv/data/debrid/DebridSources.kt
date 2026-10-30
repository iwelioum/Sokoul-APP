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

/**
 * Sources Real-Debrid d'une œuvre : TMDB → IMDb, puis extension au format Stremio
 * (`/stream/movie/tt….json`, `/stream/series/tt…:S:E.json`). Résultats gardés 30 min en mémoire.
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
        val info = runCatching { tmdb.details(settings.current().tmdbApiKey, if (q.series) "tv" else "movie", q.tmdbId, full = false) }
            .getOrElse { if (it is CancellationException) throw it; null }
        val titles = listOfNotNull(info?.title, info?.originalTitle)
        val addon = runCatching { addonStreams(q) }.getOrElse { if (it is CancellationException) throw it; emptyList() }
            .let { list ->
                // une extension renvoie parfois une autre œuvre (« Lupin Zero » pour « Lupin ») : écartée
                val (other, kept) = list.partition { otherWork(it.filename ?: it.title.lineSequence().firstOrNull().orEmpty(), titles, q.series) }
                if (other.isNotEmpty()) SourceLog.add("Recherche : ${other.size} versions d'une autre œuvre écartées (ex. « ${other.first().title.lineSequence().first().take(70)} »)")
                kept
            }
        val account = runCatching { info?.let { accountStreams(q, it) }.orEmpty() }.getOrElse { if (it is CancellationException) throw it; emptyList() }
            .filterNot { otherWork(it.filename ?: it.title.lineSequence().firstOrNull().orEmpty(), titles, q.series) }
        return untagged(merge(addon, account), info?.originalLanguage).also { last[q] = it }
    }

    /** Fichiers du compte qui sont cette œuvre (titres et année TMDB). */
    private suspend fun accountStreams(q: DebridQuery, info: com.sokoul.iptv.data.net.TmdbInfo): List<DebridStream> {
        val titles = listOfNotNull(info.title, info.originalTitle).distinct()
        return cloud.forWork(titles, info.year, q.season.takeIf { q.series }, q.episode.takeIf { q.series })
    }

    /** Adresses à interroger : Torrentio et Comet (jeton ajouté maintenant, jamais stocké), plus celles ajoutées à la main. */
    private suspend fun addonUrls(): List<String> {
        val s = settings.current()
        return DebridAddons.urls(DebridAddons.parse(s.debridAddons), s.debridAddon, rd.token())
    }

    /**
     * Toutes les extensions en parallèle (10 s chacune), résultats fusionnés : une même release renvoyée par deux
     * extensions n'apparaît qu'une fois. Gardé 30 min en mémoire (sauf réponse vide : on réessaiera).
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
                    if (r.isSuccessful) r.body?.string() else { SourceLog.add("Recherche ${url.host} : HTTP ${r.code}"); null }
                }
            } ?: run { SourceLog.add("Recherche ${url.host} : pas de réponse en ${TIMEOUT_MS / 1000} s"); null }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { SourceLog.add("Recherche ${url.host} : injoignable (${e.javaClass.simpleName})"); null }
        return text?.let { parse(it, json) }.orEmpty().also { if (text != null) SourceLog.add("Recherche ${url.host} : ${it.size} versions, ${it.count { s -> s.infoHash == null }} sans empreinte") }
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
            dolbyVision = com.sokoul.iptv.data.repo.DeviceCaps.dolbyVision,
            hdrDisplay = com.sokoul.iptv.data.repo.DeviceCaps.hdrDisplay,
            iptvBlocked = iptvBlocked,
            tieOrigin = com.sokoul.iptv.domain.SourceOrigin.entries.firstOrNull { it.name == s.sourceTie },
        )
    }

    private fun ethernet(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
    }.getOrDefault(false)

    companion object {
        const val TTL_MS = 30 * 60_000L
        const val TIMEOUT_MS = 10_000L

        // Lecture et tri des réponses : `DebridStreams` (module :core), partagé avec l'app PC.
        fun otherWork(name: String, titles: List<String>, series: Boolean = false): Boolean = DebridStreams.otherWork(name, titles, series)
        fun untagged(streams: List<DebridStream>, originalLanguage: String?): List<DebridStream> = DebridStreams.untagged(streams, originalLanguage)
        fun combine(results: List<List<DebridStream>>): List<DebridStream> = DebridStreams.combine(results)
        fun merge(addon: List<DebridStream>, account: List<DebridStream>): List<DebridStream> = DebridStreams.merge(addon, account)
        fun parse(text: String, json: Json = Json): List<DebridStream> = DebridStreams.parse(text, json)

        /** 0 = automatique : 25 Go en Wi-Fi, sans limite en câble ; 200 = sans limite. */
        fun maxBytes(s: AppSettings, ethernet: Boolean): Long? = when {
            s.debridMaxGb == 200 -> null
            s.debridMaxGb > 0 -> s.debridMaxGb * 1_000_000_000L
            ethernet -> null
            else -> 25_000_000_000L
        }
    }
}
