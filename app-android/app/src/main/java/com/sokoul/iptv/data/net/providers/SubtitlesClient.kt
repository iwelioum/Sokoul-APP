package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.parser.int
import com.sokoul.iptv.parser.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Sous-titre trouvé en ligne, pas encore téléchargé. */
data class SubtitleResult(
    val service: ApiService,
    /** OpenSubtitles : identifiant du fichier ; Subdl : chemin du .zip. */
    val ref: String,
    val language: String,
    val release: String?,
    val hearingImpaired: Boolean,
)

/** Ce qu'on cherche : film (id TMDB) ou épisode (id TMDB de la série, saison, épisode). */
data class SubtitleQuery(val tmdbId: Int, val isMovie: Boolean, val season: Int? = null, val episode: Int? = null, val language: String = "fr")

/**
 * Sous-titres en ligne : OpenSubtitles puis Subdl. **Recherche à l'ouverture du panneau, téléchargement
 * seulement quand l'utilisateur choisit** (quotas quotidiens). Inactif sans clé.
 */
@Singleton
class SubtitlesClient @Inject constructor(private val api: ApiHttp, private val keys: ApiKeys) {

    suspend fun available(): Boolean = keys.usable(ApiService.OPENSUBTITLES) || keys.usable(ApiService.SUBDL)

    suspend fun search(q: SubtitleQuery): List<SubtitleResult> {
        val os = runCatching { searchOpenSubtitles(q) }.getOrDefault(emptyList())
        val sd = if (os.size >= 5) emptyList() else runCatching { searchSubdl(q) }.getOrDefault(emptyList())
        return (os + sd).take(MAX_RESULTS)
    }

    suspend fun download(r: SubtitleResult): List<Cue> = when (r.service) {
        ApiService.OPENSUBTITLES -> downloadOpenSubtitles(r)
        ApiService.SUBDL -> api.getBytes(ApiService.SUBDL, "$SUBDL_DL${r.ref}".toHttpUrl())?.let(SubtitleFile::decode).orEmpty()
        else -> emptyList()
    }

    // --- OpenSubtitles (api.opensubtitles.com, en-tête Api-Key + User-Agent d'application obligatoire) ---

    private fun osHeaders(key: String) = mapOf("Api-Key" to key, "User-Agent" to OS_UA, "Accept" to "application/json")

    internal suspend fun searchOpenSubtitles(q: SubtitleQuery): List<SubtitleResult> {
        val key = keys.key(ApiService.OPENSUBTITLES).ifEmpty { return emptyList() }
        val url = "$OS_BASE/subtitles".toHttpUrl().newBuilder().apply {
            if (q.isMovie) addQueryParameter("tmdb_id", q.tmdbId.toString())
            else {
                addQueryParameter("parent_tmdb_id", q.tmdbId.toString())
                q.season?.let { addQueryParameter("season_number", it.toString()) }
                q.episode?.let { addQueryParameter("episode_number", it.toString()) }
            }
            addQueryParameter("languages", q.language)
            addQueryParameter("order_by", "download_count")
        }.build()
        val o = api.getJson(ApiService.OPENSUBTITLES, url, ttlMs = ApiHttp.DAY, secret = key, headers = osHeaders(key)) as? JsonObject
            ?: return emptyList()
        return parseOpenSubtitles(o)
    }

    private suspend fun downloadOpenSubtitles(r: SubtitleResult): List<Cue> {
        val key = keys.key(ApiService.OPENSUBTITLES).ifEmpty { return emptyList() }
        val body = buildJsonObject { put("file_id", JsonPrimitive(r.ref.toLongOrNull() ?: return emptyList())) }.toString()
            .toRequestBody("application/json".toMediaType())
        // le lien de téléchargement est valable quelques heures : pas de cache long
        val o = api.getJson(ApiService.OPENSUBTITLES, "$OS_BASE/download".toHttpUrl(), ttlMs = 3 * ApiHttp.HOUR, secret = key, headers = osHeaders(key), body = body) as? JsonObject
            ?: return emptyList()
        val link = o.str("link")?.takeIf { it.startsWith("https://") } ?: return emptyList()
        return api.getBytes(ApiService.OPENSUBTITLES, link.toHttpUrl())?.let(SubtitleFile::decode).orEmpty()
    }

    // --- Subdl (api.subdl.com, clé en paramètre ; fichiers .zip sur dl.subdl.com) ---

    internal suspend fun searchSubdl(q: SubtitleQuery): List<SubtitleResult> {
        val key = keys.key(ApiService.SUBDL).ifEmpty { return emptyList() }
        val url = "$SUBDL_BASE/subtitles".toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", key)
            addQueryParameter("tmdb_id", q.tmdbId.toString())
            addQueryParameter("type", if (q.isMovie) "movie" else "tv")
            if (!q.isMovie) {
                q.season?.let { addQueryParameter("season_number", it.toString()) }
                q.episode?.let { addQueryParameter("episode_number", it.toString()) }
            }
            addQueryParameter("languages", q.language.uppercase())
            addQueryParameter("subs_per_page", "10")
        }.build()
        val o = api.getJson(ApiService.SUBDL, url, ttlMs = ApiHttp.DAY, secret = key) as? JsonObject ?: return emptyList()
        return parseSubdl(o)
    }

    companion object {
        const val OS_BASE = "https://api.opensubtitles.com/api/v1"
        const val SUBDL_BASE = "https://api.subdl.com/api/v1"
        const val SUBDL_DL = "https://dl.subdl.com"
        const val OS_UA = "SokoulTV v2.0"
        const val MAX_RESULTS = 8

        fun parseOpenSubtitles(o: JsonObject): List<SubtitleResult> =
            (o["data"] as? JsonArray).orEmpty().mapNotNull { e ->
                val a = (e as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
                val file = (a["files"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@mapNotNull null
                SubtitleResult(
                    ApiService.OPENSUBTITLES, ref = (file.int("file_id") ?: return@mapNotNull null).toString(),
                    language = a.str("language") ?: "fr", release = a.str("release")?.take(80),
                    hearingImpaired = (a["hearing_impaired"] as? JsonPrimitive)?.booleanOrNull == true,
                )
            }

        fun parseSubdl(o: JsonObject): List<SubtitleResult> {
            if ((o["status"] as? JsonPrimitive)?.booleanOrNull == false) return emptyList()
            return (o["subtitles"] as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                // le lien fourni porte la clé en paramètre (« ?api_key=… ») : on ne garde que le chemin public du .zip
                val path = x.str("url")?.substringBefore('?')?.takeIf { it.startsWith("/") && ".." !in it } ?: return@mapNotNull null
                SubtitleResult(
                    ApiService.SUBDL, ref = path, language = (x.str("language") ?: x.str("lang") ?: "fr").lowercase(),
                    release = (x.str("release_name") ?: x.str("name"))?.take(80),
                    hearingImpaired = (x["hi"] as? JsonPrimitive)?.booleanOrNull == true,
                )
            }
        }
    }
}
