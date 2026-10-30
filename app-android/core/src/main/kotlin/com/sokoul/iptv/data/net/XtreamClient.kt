package com.sokoul.iptv.data.net

import com.sokoul.iptv.parser.XtAccount
import com.sokoul.iptv.parser.XtEpg
import com.sokoul.iptv.parser.XtSeriesInfo
import com.sokoul.iptv.parser.XtVodInfo
import com.sokoul.iptv.parser.XtreamParse
import com.sokoul.iptv.parser.XtreamUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeToSequence
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class XtreamAuth(val server: String, val username: String, val password: String, val userAgent: String?)

@Singleton
class XtreamClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun account(a: XtreamAuth): XtAccount = withContext(Dispatchers.IO) {
        val el = getJson(a, null)
        if (el !is JsonObject || el["user_info"] == null) {
            throw IOException("Ce serveur ne répond pas comme un serveur Xtream Codes")
        }
        XtreamParse.account(el)
    }

    suspend fun categories(a: XtreamAuth, action: String) = withContext(Dispatchers.IO) {
        val list = ArrayList<com.sokoul.iptv.parser.XtCategory>()
        streamArray(a, action) { o -> XtreamParse.category(o)?.let(list::add) }
        list
    }

    /** Parcourt une grosse liste (`get_live_streams`, `get_vod_streams`…) sans la charger entièrement. */
    suspend fun streamArray(a: XtreamAuth, action: String, onItem: suspend (JsonObject) -> Unit) = withContext(Dispatchers.IO) {
        val url = XtreamUrls.api(a.server, a.username, a.password, action)
        val (resp, input) = http.openStream(url, a.userAgent)
        resp.use {
            // Certains panels renvoient {} ou [] mal formés quand une liste est vide
            val buffered = input.buffered()
            buffered.mark(64 * 1024)
            val first = generateSequence { buffered.read().takeIf { it >= 0 } }.firstOrNull { !it.toChar().isWhitespace() }
            if (first != '['.code) return@withContext
            buffered.reset()
            val seq = json.decodeToSequence(buffered, JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
            for (el in seq) {
                coroutineContext.ensureActive()
                (el as? JsonObject)?.let { onItem(it) }
            }
        }
    }

    suspend fun seriesInfo(a: XtreamAuth, seriesId: Long): XtSeriesInfo = withContext(Dispatchers.IO) {
        XtreamParse.seriesInfo(getJson(a, "get_series_info", mapOf("series_id" to seriesId.toString())), seriesId)
    }

    suspend fun vodInfo(a: XtreamAuth, vodId: Long): XtVodInfo? = withContext(Dispatchers.IO) {
        XtreamParse.vodInfo(getJson(a, "get_vod_info", mapOf("vod_id" to vodId.toString())))
    }

    suspend fun shortEpg(a: XtreamAuth, streamId: Long, limit: Int = 12): List<XtEpg> = withContext(Dispatchers.IO) {
        XtreamParse.shortEpg(getJson(a, "get_short_epg", mapOf("stream_id" to streamId.toString(), "limit" to limit.toString())))
    }

    private fun getJson(a: XtreamAuth, action: String?, extra: Map<String, String> = emptyMap()): JsonElement {
        val url = XtreamUrls.api(a.server, a.username, a.password, action, extra)
        val (resp, input) = http.openStream(url, a.userAgent)
        resp.use {
            val text = input.bufferedReader().readText()
            if (text.isBlank()) throw IOException("Réponse vide du serveur")
            return try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw IOException("Réponse illisible du serveur (ce n'est peut-être pas un serveur Xtream)", e)
            }
        }
    }
}
