package com.sokoul.iptv.data.net.providers

import android.content.Context
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.net.await
import com.sokoul.iptv.parser.TitleTagger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class IoChannel(
    val id: String,
    val name: String,
    val alt_names: List<String> = emptyList(),
    val country: String? = null,
    val closed: String? = null,
    val is_nsfw: Boolean = false,
)

@Serializable
internal data class IoLogo(
    val channel: String,
    val feed: String? = null,
    val format: String? = null,
    val width: Int? = null,
    val url: String,
)

/**
 * Base de chaînes iptv-org (gratuite, sans clé) : logos de secours pour les chaînes que le fournisseur
 * livre sans logo. Les deux fichiers publics (~14 Mo) ne sont lus qu'en tâche de fond, au plus une fois
 * par mois, et réduits à un petit index local (identifiant EPG / nom normalisé → logo).
 */
@Singleton
class ChannelDbClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val db: AppDatabase,
    private val health: ProviderHealth,
) {
    private val lock = Mutex()
    @Volatile private var memory: Map<String, String>? = null
    private val file get() = File(context.filesDir, "iptvorg-logos.json")

    /**
     * Logos de secours d'une playlist : icône du guide TV d'abord, puis iptv-org. Ne télécharge la base
     * iptv-org que s'il reste des chaînes sans logo. Renvoie le nombre de logos ajoutés.
     */
    suspend fun fillMissingLogos(pid: Long): Int = withContext(Dispatchers.IO) {
        var added = runCatching { db.channels().fillLogosFromEpg(pid) }.getOrDefault(0)
        val missing = db.channels().withoutLogo(pid)
        if (missing.isEmpty()) return@withContext added
        val index = index() ?: return@withContext added
        for (c in missing) {
            val logo = lookup(index, c.tvgId, c.nameKey) ?: continue
            db.channels().setFallbackLogo(pid, c.id, logo)
            added++
        }
        added
    }

    /** Index local, rafraîchi si absent ou vieux de plus de 30 jours (sinon gardé tel quel, même périmé). */
    suspend fun index(): Map<String, String>? = lock.withLock {
        memory?.let { return@withLock it }
        val f = file
        val fresh = f.isFile && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS
        if (!fresh && !health.isBlocked(ApiService.IPTVORG)) {
            runCatching { download() }
                .onSuccess { idx -> f.writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), idx)); health.ok(ApiService.IPTVORG) }
                .onFailure { e -> if (e is CancellationException) throw e; Timber.w("iptv-org indisponible : %s", e.javaClass.simpleName); health.failed(ApiService.IPTVORG, null, "Téléchargement impossible") }
        }
        if (!f.isFile) return@withLock null
        runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), f.readText()) }.getOrNull()
            ?.also { memory = it }
    }

    private suspend fun download(): Map<String, String> {
        val channels = fetch("$BASE/channels.json") { json.decodeFromStream(ListSerializer(IoChannel.serializer()), it) }
        val logos = fetch("$BASE/logos.json") { json.decodeFromStream(ListSerializer(IoLogo.serializer()), it) }
        return buildIndex(channels, logos)
    }

    private suspend fun <T> fetch(url: String, read: (InputStream) -> T): T =
        http.newCall(Request.Builder().url(url).build()).await().use { resp ->
            if (!resp.isSuccessful) { health.failed(ApiService.IPTVORG, resp.code, "Erreur ${resp.code}"); error("HTTP ${resp.code}") }
            read(resp.body!!.byteStream())
        }

    companion object {
        const val BASE = "https://iptv-org.github.io/api"
        private const val MAX_AGE_MS = 30L * 24 * 3600_000

        /** Pays gardés, du plus au moins prioritaire en cas de nom identique (« TF1 » : la française). */
        private val COUNTRIES = listOf("FR", "BE", "CH", "LU", "MC", "CA", "MA", "DZ", "TN", "SN", "CI", "UK", "GB", "US", "DE", "ES", "IT", "PT", "NL")

        internal fun buildIndex(channels: List<IoChannel>, logos: List<IoLogo>): Map<String, String> {
            // un logo par chaîne : PNG/JPEG (Coil ne lit pas le SVG), logo principal (sans « feed »), le plus large
            val bestLogo = logos.asSequence()
                .filter { it.url.startsWith("https://") && it.format?.uppercase() != "SVG" && !it.url.endsWith(".svg", true) }
                .groupBy { it.channel }
                .mapValues { (_, l) -> l.sortedWith(compareBy<IoLogo> { it.feed != null }.thenByDescending { it.width ?: 0 }).first().url }
            val out = HashMap<String, String>()
            val rank = HashMap<String, Int>()
            channels.asSequence()
                .filter { !it.is_nsfw && it.closed == null && it.country in COUNTRIES }
                .forEach { c ->
                    val logo = bestLogo[c.id] ?: return@forEach
                    val r = COUNTRIES.indexOf(c.country)
                    out["id:" + c.id.lowercase()] = logo
                    (listOf(c.name) + c.alt_names).map(TitleTagger::channelKey).filter { it.length >= 2 }.forEach { k ->
                        if (r < (rank["n:$k"] ?: Int.MAX_VALUE)) { out["n:$k"] = logo; rank["n:$k"] = r }
                    }
                }
            return out
        }

        /** Identifiant EPG d'abord (« tf1.fr »), sinon nom normalisé (« tf1 »). */
        fun lookup(index: Map<String, String>, tvgId: String?, nameKey: String): String? =
            tvgId?.lowercase()?.let { index["id:$it"] } ?: nameKey.takeIf { it.length >= 2 }?.let { index["n:$it"] }
    }
}
