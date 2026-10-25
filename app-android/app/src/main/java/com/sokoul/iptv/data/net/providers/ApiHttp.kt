package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.data.db.TmdbCacheEntity
import com.sokoul.iptv.data.db.TmdbDao
import com.sokoul.iptv.data.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** État d'un service, affiché dans Réglages. */
sealed interface ServiceState {
    data object Unknown : ServiceState
    data object Ok : ServiceState
    /** Clé refusée (401/403) ou quota atteint (429) : plus aucun appel jusqu'à [until]. */
    data class Blocked(val code: Int, val until: Long) : ServiceState
    /** Dernier appel en échec réseau ou serveur (aucun blocage : le prochain appel réessaie). */
    data class Failing(val message: String) : ServiceState
}

/**
 * Disjoncteur par service : un refus de clé coupe le service 24 h, un quota (429) 1 h. Aucune rafale
 * de requêtes vers un service qui refuse (même logique que la porte du fournisseur IPTV).
 */
@Singleton
class ProviderHealth @Inject constructor() {
    private val _states = MutableStateFlow<Map<ApiService, ServiceState>>(emptyMap())
    val states: StateFlow<Map<ApiService, ServiceState>> = _states.asStateFlow()

    /** Horloge remplaçable dans les tests. */
    var clock: () -> Long = System::currentTimeMillis

    fun state(s: ApiService): ServiceState = _states.value[s] ?: ServiceState.Unknown

    fun isBlocked(s: ApiService): Boolean {
        val st = state(s) as? ServiceState.Blocked ?: return false
        if (clock() < st.until) return true
        _states.update { it - s }
        return false
    }

    fun ok(s: ApiService) = _states.update { it + (s to ServiceState.Ok) }

    fun failed(s: ApiService, code: Int?, message: String) {
        val block = when (code) {
            401, 403 -> BLOCK_AUTH_MS
            429 -> BLOCK_QUOTA_MS
            else -> 0L
        }
        _states.update { it + (s to if (block > 0) ServiceState.Blocked(code!!, clock() + block) else ServiceState.Failing(message)) }
    }

    /** Nouvelle clé saisie : on lève le blocage pour pouvoir la tester. */
    fun reset(s: ApiService) = _states.update { it - s }

    companion object {
        const val BLOCK_AUTH_MS = 24 * 3600_000L
        const val BLOCK_QUOTA_MS = 3600_000L
    }
}

/**
 * Appels HTTP communs aux services de données : cache en base (table `tmdb_cache`, préfixe par service,
 * **sans la clé** dans la clé de cache), au plus 2 requêtes simultanées par service, disjoncteur.
 * Toute erreur renvoie null (ou la dernière réponse connue) : un service en panne ne casse jamais l'écran.
 */
@Singleton
class ApiHttp @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val cache: TmdbDao,
    val health: ProviderHealth,
) {
    private val gates = ApiService.entries.associateWith { Semaphore(2) }

    /**
     * GET JSON. [secret] (clé) est retiré de la clé de cache. Réponse 404 mise en cache comme « rien »
     * (pas de nouvel appel avant [ttlMs]).
     */
    suspend fun getJson(
        service: ApiService, url: HttpUrl, ttlMs: Long, secret: String? = null,
        headers: Map<String, String> = emptyMap(), body: RequestBody? = null,
    ): JsonElement? = withContext(Dispatchers.IO) {
        val key = cacheKey(service, url, secret, body)
        val now = health.clock()
        val cached = runCatching { cache.get(key) }.getOrNull()
        val fresh = coroutineContext[FreshCall] != null
        if (!fresh && cached != null && now - cached.fetchedAt < ttlMs) return@withContext parse(cached.json)
        if (health.isBlocked(service)) return@withContext cached?.let { parse(it.json) }
        try {
            gates.getValue(service).withPermit {
                val req = Request.Builder().url(url).apply {
                    headers.forEach { (k, v) -> header(k, v) }
                    if (body != null) post(body)
                }.build()
                http.newCall(req).await().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val text = resp.body?.string().orEmpty()
                            health.ok(service)
                            runCatching { cache.put(TmdbCacheEntity(key, text, now)) }
                            parse(text)
                        }
                        resp.code == 404 -> {
                            health.ok(service)
                            runCatching { cache.put(TmdbCacheEntity(key, "null", now)) }
                            null
                        }
                        else -> {
                            health.failed(service, resp.code, "Erreur ${resp.code}")
                            cached?.let { parse(it.json) }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("%s indisponible : %s", service.label, e.javaClass.simpleName)
            health.failed(service, null, "Réseau indisponible")
            cached?.let { parse(it.json) }
        }
    }

    /** Téléchargement d'un fichier (sous-titres), sans cache en base ; null si refusé, absent ou trop gros. */
    suspend fun getBytes(
        service: ApiService, url: HttpUrl, headers: Map<String, String> = emptyMap(), maxBytes: Long = 5L * 1024 * 1024,
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (health.isBlocked(service)) return@withContext null
        try {
            gates.getValue(service).withPermit {
                val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
                http.newCall(req).await().use { resp ->
                    if (!resp.isSuccessful) { health.failed(service, resp.code, "Erreur ${resp.code}"); return@use null }
                    val body = resp.body ?: return@use null
                    if (body.contentLength() > maxBytes) return@use null
                    body.byteStream().use { s -> s.readNBytesCompat(maxBytes + 1) }.takeIf { it.size <= maxBytes }
                        ?.also { health.ok(service) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            health.failed(service, null, "Réseau indisponible")
            null
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (total < limit) {
            val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
            if (n < 0) break
            out.write(buf, 0, n); total += n
        }
        return out.toByteArray()
    }

    private fun parse(text: String): JsonElement? =
        runCatching { json.parseToJsonElement(text) }.getOrNull()?.takeIf { it !is JsonNull }

    companion object {
        /** Clé de cache : « fanart:https://…?… » sans la clé d'API (ni dans l'URL, ni dans le corps). */
        fun cacheKey(service: ApiService, url: HttpUrl, secret: String?, body: RequestBody? = null): String {
            var u = url.toString()
            if (!secret.isNullOrEmpty()) u = u.replace(secret, "")
            val b = body?.let { rb -> okio.Buffer().also { rb.writeTo(it) }.readUtf8() }?.let { if (!secret.isNullOrEmpty()) it.replace(secret, "") else it }
            return service.id + ":" + u + (b?.let { "#$it" } ?: "")
        }

        const val HOUR = 3600_000L
        const val DAY = 24 * HOUR
    }
}

/**
 * Appel « frais » : le cache n'est pas lu (la réponse y est quand même enregistrée). Sert au bouton
 * « Tester » des Réglages, pour qu'une clé refusée ne paraisse pas valide grâce à une réponse en cache.
 */
class FreshCall : AbstractCoroutineContextElement(FreshCall) {
    companion object Key : CoroutineContext.Key<FreshCall>

}

suspend fun <T> freshCall(block: suspend () -> T): T = withContext(FreshCall()) { block() }
