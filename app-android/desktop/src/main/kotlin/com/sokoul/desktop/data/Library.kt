package com.sokoul.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.time.Instant

/** Prochain épisode annoncé d'une série suivie (« À venir », comme la tablette). */
data class Upcoming(val tvId: Int, val title: String, val image: String?, val season: Int, val episode: Int, val name: String?, val airsAt: Long)

/** Règles de l'historique et d'« À venir » (pures, testées). */
object LibraryRules {
    /** Sections « Aujourd'hui », « Hier », « Lun. 27 oct. », dans l'ordre reçu (le plus récent d'abord). */
    fun <T> byDay(list: List<T>, at: (T) -> Long, now: Long, label: (Long, Long) -> String): List<Pair<String, List<T>>> =
        list.groupBy { label(at(it), now) }.toList()

    /** « 20:30 · vu », « 20:30 · 45 % ». */
    fun status(r: Resume, clock: String): String = when {
        r.finished -> "$clock · vu"
        r.durationMs > 0 -> "$clock · ${(r.positionMs * 100 / r.durationMs).coerceIn(1, 99)} %"
        else -> clock
    }

    /** « À venir » : par date de sortie, une ligne par série (le plus proche d'abord), passés écartés. */
    fun upcoming(list: List<Upcoming>, now: Long): List<Upcoming> =
        list.filter { it.airsAt >= now - 6 * 3_600_000L }.sortedBy { it.airsAt }.distinctBy { it.tvId }

    /** Prochain épisode dans une réponse TVmaze `shows/{id}?embed=nextepisode` (null si rien d'annoncé). */
    fun parseTvmazeNext(o: JsonObject): Triple<Int, Int, Pair<String?, Long>>? {
        val e = (o["_embedded"] as? JsonObject)?.get("nextepisode") as? JsonObject ?: return null
        val at = e["airstamp"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return null
        val s = e["season"]?.jsonPrimitive?.intOrNull ?: return null
        val n = e["number"]?.jsonPrimitive?.intOrNull ?: return null
        return Triple(s, n, e["name"]?.jsonPrimitive?.contentOrNull to at)
    }
}

/**
 * « À venir » du PC : séries de Ma liste et séries commencées, prochain épisode lu sur TVmaze (API publique sans
 * clé, retrouvée par l'identifiant IMDb de TMDB). Réponses gardées 12 h dans le cache de la base.
 */
class DesktopUpcoming(private val db: DesktopDb, private val catalog: Catalog) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun getJson(url: String, ttlMs: Long): JsonObject? = withContext(Dispatchers.IO) {
        val key = "tvmaze:$url"
        db.tmdbGet(key)?.takeIf { System.currentTimeMillis() - it.second < ttlMs }?.let { return@withContext json.parseToJsonElement(it.first) as? JsonObject }
        runCatching {
            catalog.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val body = r.body?.string() ?: return@use null
                db.tmdbPut(key, body, System.currentTimeMillis())
                json.parseToJsonElement(body) as? JsonObject
            }
        }.getOrNull()
    }

    suspend fun load(seriesIds: Collection<Int>): List<Upcoming> = coroutineScope {
        val k = catalog.key()
        seriesIds.distinct().map { id ->
            async {
                val imdb = catalog.imdbId("tv", id) ?: return@async null
                val show = getJson("https://api.tvmaze.com/lookup/shows".toHttpUrl().newBuilder().addQueryParameter("imdb", imdb).build().toString(), 7 * 86_400_000L) ?: return@async null
                if (show["status"]?.jsonPrimitive?.contentOrNull != "Running") return@async null
                val showId = show["id"]?.jsonPrimitive?.intOrNull ?: return@async null
                val full = getJson("https://api.tvmaze.com/shows/$showId?embed=nextepisode", 12 * 3_600_000L) ?: return@async null
                val (s, e, rest) = LibraryRules.parseTvmazeNext(full) ?: return@async null
                val info = catalog.tmdb.details(k, "tv", id, full = false)
                Upcoming(id, info?.title ?: show["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), info?.backdrop ?: info?.poster, s, e, rest.first, rest.second)
            }
        }.mapNotNull { it.await() }.let { LibraryRules.upcoming(it, System.currentTimeMillis()) }
    }
}
