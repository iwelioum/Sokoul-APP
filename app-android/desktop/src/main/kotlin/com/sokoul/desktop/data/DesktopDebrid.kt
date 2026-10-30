package com.sokoul.desktop.data

import com.sokoul.iptv.data.debrid.DebridAddons
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.data.debrid.DebridStreams
import com.sokoul.iptv.data.debrid.RdUser
import com.sokoul.iptv.data.debrid.RipSource
import com.sokoul.iptv.data.debrid.SourceAddon
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.parser.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import com.sokoul.iptv.data.net.await
import java.util.concurrent.ConcurrentHashMap

/** Résultat d'une recherche de versions : les versions classées, ou la raison de l'absence. */
data class DebridResult(val streams: List<DebridStream>, val problem: String? = null)

/**
 * Real-Debrid sur PC : versions trouvées par Torrentio et Comet (format Stremio, jeton ajouté au moment de la requête,
 * jamais journalisé), puis classées par [DesktopRanker]. Résultats gardés 30 min en mémoire.
 */
class DesktopDebrid(private val db: DesktopDb, private val catalog: Catalog) {
    private val cache = ConcurrentHashMap<DebridQuery, Pair<Long, List<DebridStream>>>()

    fun token(): String? = ProviderConfig.key(db, "realdebrid")

    fun configured() = token() != null

    suspend fun user(): RdUser? = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext null
        runCatching {
            catalog.http.newCall(Request.Builder().url("https://api.real-debrid.com/rest/1.0/user").header("Authorization", "Bearer $t").build())
                .execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val o = Json.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject ?: return@use null
                    RdUser(o.str("username").orEmpty(), o.str("type") == "premium", o.str("expiration")?.let(::iso) ?: 0)
                }
        }.getOrNull()
    }

    /** Recherche hors du fil de l'interface (base, expressions régulières, interfaces réseau). */
    suspend fun find(q: DebridQuery, info: TmdbInfo?): DebridResult = withContext(Dispatchers.Default) {
        val token = withContext(Dispatchers.IO) { token() } ?: return@withContext DebridResult(emptyList(), "Real-Debrid n'est pas configuré (Réglages › Clés API)")
        cache[q]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.let { return@withContext DebridResult(it.second) }
        val imdb = info?.imdbId ?: catalog.imdbId(if (q.series) "tv" else "movie", q.tmdbId)
            ?: return@withContext DebridResult(emptyList(), "Identifiant IMDb introuvable pour ce titre")
        val urls = DebridAddons.urls(setOf(SourceAddon.TORRENTIO, SourceAddon.COMET), "", token)
        val path = q.streamPath(imdb)
        var answered = 0
        val results = coroutineScope {
            urls.map { base -> async { fetch("$base/$path")?.also { answered++ } } }.map { it.await().orEmpty() }
        }
        if (answered == 0) return@withContext DebridResult(emptyList(), "Les extensions de sources ne répondent pas, réessayez dans un instant")
        val titles = listOfNotNull(info?.title, info?.originalTitle)
        val kept = DebridStreams.combine(results)
            .filterNot { DebridStreams.otherWork(it.filename ?: it.title.lineSequence().firstOrNull().orEmpty(), titles, q.series) }
        val ranked = DesktopRanker.rank(DebridStreams.untagged(kept, info?.originalLanguage), withContext(Dispatchers.IO) { DesktopRanker.maxBytes() })
        if (ranked.isNotEmpty()) cache[q] = System.currentTimeMillis() to ranked
        DebridResult(ranked, if (ranked.isEmpty()) "Aucune version trouvée sur Real-Debrid" else null)
    }

    /**
     * Lien direct d'une version : l'extension répond par une redirection vers le fichier Real-Debrid (le torrent est
     * ajouté au compte s'il n'y est pas). Suivi ici, pour que le lecteur ne voie jamais l'adresse portant le jeton.
     * Torrentio renvoie une courte vidéo « téléchargement en cours » quand le fichier n'est pas prêt : signalée.
     */
    suspend fun resolve(s: DebridStream): Result<String> = withContext(Dispatchers.IO) {
        val url = s.url ?: return@withContext Result.failure(IllegalStateException("Version sans lien de lecture"))
        val noRedirect = catalog.http.newBuilder().followRedirects(false).followSslRedirects(false)
            .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS).build()
        var current = url
        repeat(6) {
            if (onRealDebrid(current)) return@withContext Result.success(current)
            // appel annulable : fermer le lecteur pendant la résolution coupe aussi la requête
            val r = runCatching { noRedirect.newCall(Request.Builder().url(current).header("User-Agent", "SokoulTV/2.0").build()).await() }
                .getOrElse { if (it is CancellationException) throw it; return@withContext Result.failure(IllegalStateException("Real-Debrid ne répond pas")) }
            r.use {
                when {
                    it.isRedirect -> {
                        val next = it.header("Location") ?: return@withContext Result.failure(IllegalStateException("Lien indisponible"))
                        current = it.request.url.resolve(next)?.toString() ?: next
                    }
                    // l'extension répond elle-même : sa vidéo d'attente (« Not ready yet », « downloading »…), jamais le film
                    it.isSuccessful -> return@withContext Result.failure(NotReady())
                    else -> return@withContext Result.failure(IllegalStateException("Version indisponible (HTTP ${it.code})"))
                }
            }
        }
        if (onRealDebrid(current)) Result.success(current) else Result.failure(NotReady())
    }

    /** Fichier servi par Real-Debrid (`*.download.real-debrid.com`, `real-debrid.com/d/`), et non une vidéo d'attente. */
    private fun onRealDebrid(u: String): Boolean {
        val host = u.toHttpUrlOrNull()?.host ?: return false
        return (host.endsWith(".real-debrid.com") || host == "real-debrid.com") && NOT_READY.none { u.contains(it) }
    }

    class NotReady : IllegalStateException("Cette version n'est pas encore prête sur Real-Debrid (en cours de préparation) : choisissez-en une autre ou réessayez dans quelques minutes")

    private suspend fun fetch(address: String): List<com.sokoul.iptv.data.debrid.DebridStream>? = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                // await : le délai de 12 s annule vraiment l'appel HTTP (execute() bloquant l'ignorerait)
                catalog.http.newCall(Request.Builder().url(address).header("User-Agent", "SokoulTV/2.0").build()).await().use { r ->
                    if (r.isSuccessful) DebridStreams.parse(r.body?.string().orEmpty()) else null
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }

    companion object {
        const val TTL_MS = 30 * 60_000L
        const val TIMEOUT_MS = 12_000L
        /** Vidéos d'attente de Torrentio (fichier pas encore prêt, erreur Real-Debrid). */
        private val NOT_READY = listOf("/videos/downloading", "/videos/failed", "/videos/access_denied", "/videos/limits_exceeded", "/videos/infringing")

        fun iso(s: String): Long = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0)
    }
}

/**
 * Classement des versions sur PC (même esprit que `SourceRanker` de la tablette) :
 * 1. lisible ici : taille sous la limite du réseau (25 Go en Wi-Fi, 60 Go en câble) ;
 * 2. langue entendue : VF et MULTI (qui contient le français) d'abord, puis VOSTFR, puis VO ;
 * 3. image SDR d'abord, puis HDR10, Dolby Vision en dernier : l'image passe par Compose (RV32), sans conversion
 *    HDR → SDR, une version HDR aurait des couleurs délavées ;
 * 4. définition ; 5. VF avant MULTI à image égale ; 6. Remux > Blu-ray > WEB ; 7. TRUEFRENCH/VFF ; 8. sources, taille.
 * Les échantillons (sous 50 Mio et sous le dixième de la plus grosse version) sont écartés.
 */
object DesktopRanker {
    private const val MIN_VIDEO_BYTES = 50L * 1024 * 1024

    fun tooSmall(bytes: Long?, largest: Long?): Boolean =
        bytes != null && bytes < MIN_VIDEO_BYTES && (largest == null || bytes < largest / 10)

    /** Limite de taille selon le réseau du moment : une interface filaire active = câble (calculé au plus toutes les 5 min). */
    fun maxBytes(): Long {
        val now = System.currentTimeMillis()
        if (now - wiredAt > 5 * 60_000L) { wiredCache = wired(); wiredAt = now }
        return if (wiredCache) 60_000_000_000L else 25_000_000_000L
    }
    @Volatile private var wiredCache = false
    @Volatile private var wiredAt = 0L

    private fun wired(): Boolean = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().any { n ->
            val name = (n.displayName + " " + n.name).lowercase()
            n.isUp && !n.isLoopback && !n.isVirtual && n.hardwareAddress != null &&
                ("ethernet" in name || name.startsWith("eth")) && listOf("virtual", "hyper-v", "vmware", "virtualbox", "vpn", "tap", "bluetooth").none { it in name }
        }
    }.getOrDefault(false)

    fun rank(streams: List<DebridStream>, maxBytes: Long = maxBytes()): List<DebridStream> {
        val largest = streams.mapNotNull { it.release.sizeBytes }.maxOrNull()
        return streams.filterNot { tooSmall(it.release.sizeBytes, largest) }.sortedWith(
            compareBy<DebridStream> { if ((it.release.sizeBytes ?: 0) > maxBytes) 1 else 0 }
                .thenBy { audioRank(it.release.language) }
                .thenBy { hdrRank(it.release.hdr) }
                .thenBy { qualityRank(it.release.quality) }
                // annoncée prête par l'extension (« [RD+] », « ⚡ ») : simple indice, Real-Debrid ne le confirme plus
                .thenBy { when (it.release.cached) { true -> 0; null -> 1; false -> 2 } }
                .thenBy { if (it.release.language == AudioLanguage.VF) 0 else 1 }
                .thenBy { it.release.source?.ordinal ?: RipSource.WEBDL.ordinal }
                .thenBy { when (it.release.frenchVariant) { "TRUEFRENCH", "VFF" -> 0; null -> 1; else -> 2 } }
                .thenByDescending { it.release.seeders ?: 0 }
                .thenByDescending { it.release.sizeBytes ?: 0 }
                .thenBy { it.key },
        )
    }

    fun audioRank(l: AudioLanguage?) = when (l) { AudioLanguage.VF, AudioLanguage.MULTI -> 0; AudioLanguage.VOSTFR -> 1; else -> 2 }
    private fun qualityRank(q: VideoQuality?) = when (q) { VideoQuality.UHD_4K -> 0; VideoQuality.FHD_1080 -> 1; VideoQuality.HD_720, null -> 2; VideoQuality.SD -> 3 }
    private fun hdrRank(h: HdrFormat?) = when (h) { null -> 0; HdrFormat.HDR10 -> 1; HdrFormat.DOLBY_VISION -> 2 }
}
