package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.data.net.await
import com.sokoul.iptv.data.repo.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat du bouton « Tester » d'un service. */
data class TestResult(val ok: Boolean, val message: String)

/**
 * « Tester » dans Réglages : **un seul appel** au service, sans passer par le cache (une clé refusée ne doit
 * pas paraître valide), avec une œuvre connue (Interstellar, Breaking Bad). Le disjoncteur est levé avant,
 * pour qu'une nouvelle clé puisse être essayée tout de suite.
 */
@Singleton
class DataSourceTester @Inject constructor(
    private val api: ApiHttp,
    private val keys: ApiKeys,
    private val fanart: FanartClient,
    private val tvdb: TvdbClient,
    private val streaming: StreamingClient,
    private val tastedive: TasteDiveClient,
    private val omdb: OmdbClient,
    private val subtitles: SubtitlesClient,
    private val channels: ChannelDbClient,
    private val http: OkHttpClient,
) {
    suspend fun test(s: ApiService): TestResult {
        if (s == ApiService.WATCHMODE) return TestResult(false, "Non utilisé : l'offre gratuite n'inclut pas la France")
        if (s.needsKey && keys.key(s).isEmpty()) return TestResult(false, "Aucune clé : inscription gratuite sur ${s.signupUrl?.removePrefix("https://")?.substringBefore('/')}")
        api.health.reset(s)
        val detail = try {
            withTimeoutOrNull(TIMEOUT_MS) { freshCall { probe(s) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            api.health.failed(s, null, "Réseau indisponible")
            null
        }
        return when (val st = api.health.state(s)) {
            is ServiceState.Blocked -> TestResult(false, if (st.code == 429) "Quota atteint, réessayez plus tard" else "Clé refusée (erreur ${st.code})")
            is ServiceState.Failing -> TestResult(false, st.message)
            ServiceState.Ok -> if (detail != null) TestResult(true, "OK · $detail") else TestResult(false, "Le service répond, mais sans résultat")
            ServiceState.Unknown -> if (detail != null) TestResult(true, "OK · $detail") else TestResult(false, "Pas de réponse (délai dépassé)")
        }
    }

    private suspend fun probe(s: ApiService): String? = when (s) {
        ApiService.TVDB -> tvdb.episodes(BB_TVDB).takeIf { it.isNotEmpty() }?.let { "${it.size} épisodes de Breaking Bad" }
        ApiService.FANART -> fanart.movie(INTERSTELLAR)?.let { a -> listOfNotNull(a.logo?.let { "logo" }, a.background?.let { "fond" }).joinToString(" et ").ifEmpty { null } }
        ApiService.TVMAZE -> (api.getJson(ApiService.TVMAZE, "${TvmazeClient.BASE}/lookup/shows?thetvdb=$BB_TVDB".toHttpUrl(), ttlMs = 0) as? JsonObject)
            ?.let { "fiche « ${(it["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "?"} » trouvée" }
        ApiService.MOTN -> streaming.offers(true, INTERSTELLAR).takeIf { it.isNotEmpty() }?.let { o -> "Interstellar : " + o.take(3).joinToString { it.service } }
        ApiService.TASTEDIVE -> tastedive.similar("Interstellar", true, 5).takeIf { it.isNotEmpty() }?.let { "${it.size} titres similaires" }
        ApiService.OMDB -> omdb.ratings("tt0816692")?.imdb?.let { "IMDb $it pour Interstellar" }
        ApiService.OPENSUBTITLES -> subtitles.searchOpenSubtitles(SubtitleQuery(INTERSTELLAR, true)).takeIf { it.isNotEmpty() }?.let { "${it.size} sous-titres français" }
        ApiService.SUBDL -> subtitles.searchSubdl(SubtitleQuery(INTERSTELLAR, true)).takeIf { it.isNotEmpty() }?.let { "${it.size} sous-titres français" }
        ApiService.IPTVORG -> channels.index()?.takeIf { it.isNotEmpty() }?.let { idx -> "${idx.keys.count { it.startsWith("id:") }} logos de chaînes" }
            ?.also { api.health.ok(s) }
        ApiService.XMLTVFR -> http.newCall(Request.Builder().url(SyncEngine.XMLTVFR_URL).head().build()).await().use { r ->
            if (r.isSuccessful) { api.health.ok(s); r.header("Content-Length")?.toLongOrNull()?.let { "guide disponible (${it / 1_000_000} Mo)" } ?: "guide disponible" }
            else { api.health.failed(s, r.code, "Erreur ${r.code}"); null }
        }
        ApiService.WATCHMODE -> null
    }

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val INTERSTELLAR = 157336
        private const val BB_TVDB = 81189
    }
}
