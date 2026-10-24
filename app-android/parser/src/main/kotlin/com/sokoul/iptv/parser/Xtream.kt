package com.sokoul.iptv.parser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64

data class XtAccount(
    val auth: Boolean,
    val status: String?,
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val isTrial: Boolean,
    val serverTimezone: String?,
    val message: String?,
) {
    val isActive: Boolean get() = auth && (status == null || status.equals("Active", true))
}

data class XtCategory(val id: String, val name: String)

data class XtLive(
    val streamId: Long,
    val num: Int?,
    val name: String,
    val icon: String?,
    val epgChannelId: String?,
    val categoryId: String?,
    val hasArchive: Boolean,
    val archiveDays: Int,
)

data class XtVod(
    val streamId: Long,
    val name: String,
    val icon: String?,
    val rating: Double?,
    val categoryId: String?,
    val extension: String?,
    val added: Long?,
    val year: Int?,
    val tmdbId: String?,
)

data class XtSeries(
    val seriesId: Long,
    val name: String,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: Double?,
    val backdrop: String?,
    val categoryId: String?,
    val lastModified: Long?,
    val trailer: String?,
    val tmdbId: String?,
)

data class XtEpisode(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val extension: String?,
    val durationSecs: Int?,
    val plot: String?,
    val image: String?,
    val rating: Double?,
)

data class XtSeriesInfo(val series: XtSeries?, val episodes: List<XtEpisode>, val seasonCovers: Map<Int, String>)

data class XtVodInfo(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val durationSecs: Int?,
    val rating: Double?,
    val backdrop: String?,
    val poster: String?,
    val trailer: String?,
    val tmdbId: String?,
    val extension: String?,
)

data class XtEpg(val start: Long, val stop: Long, val title: String, val description: String?)

/**
 * Conversion tolérante des réponses `player_api.php`.
 * Les panels Xtream renvoient des types incohérents (nombres en chaînes, `""`,
 * `null`, `[]` à la place d'objets…) : tout passe par des accesseurs défensifs.
 */
object XtreamParse {

    fun account(root: JsonElement): XtAccount {
        val o = root as? JsonObject ?: return XtAccount(false, null, null, null, null, false, null, "Réponse invalide")
        val u = o["user_info"] as? JsonObject
        val s = o["server_info"] as? JsonObject
        return XtAccount(
            auth = u?.int("auth") == 1,
            status = u?.str("status"),
            expiresAt = u?.long("exp_date")?.takeIf { it > 0 }?.times(1000),
            maxConnections = u?.int("max_connections"),
            activeConnections = u?.int("active_cons"),
            isTrial = u?.int("is_trial") == 1,
            serverTimezone = s?.str("timezone"),
            message = u?.str("message"),
        )
    }

    fun category(o: JsonObject): XtCategory? {
        val id = o.str("category_id") ?: return null
        return XtCategory(id, o.str("category_name") ?: "Sans nom")
    }

    fun live(o: JsonObject): XtLive? {
        val id = o.long("stream_id") ?: return null
        return XtLive(
            streamId = id,
            num = o.int("num"),
            name = o.str("name") ?: "Chaîne $id",
            icon = o.str("stream_icon"),
            epgChannelId = o.str("epg_channel_id"),
            categoryId = o.str("category_id") ?: o.firstOfArray("category_ids"),
            hasArchive = o.int("tv_archive") == 1,
            archiveDays = o.int("tv_archive_duration") ?: 0,
        )
    }

    fun vod(o: JsonObject): XtVod? {
        val id = o.long("stream_id") ?: return null
        val name = o.str("name") ?: o.str("title") ?: "Film $id"
        return XtVod(
            streamId = id,
            name = name,
            icon = o.str("stream_icon") ?: o.str("cover"),
            rating = o.double("rating")?.takeIf { it > 0 } ?: o.double("rating_5based")?.takeIf { it > 0 }?.times(2),
            categoryId = o.str("category_id") ?: o.firstOfArray("category_ids"),
            extension = o.str("container_extension"),
            added = o.long("added")?.times(1000),
            year = o.int("year") ?: TitleTagger.parse(name).year,
            tmdbId = o.str("tmdb") ?: o.str("tmdb_id"),
        )
    }

    fun series(o: JsonObject): XtSeries? {
        val id = o.long("series_id") ?: return null
        return XtSeries(
            seriesId = id,
            name = o.str("name") ?: o.str("title") ?: "Série $id",
            cover = o.str("cover"),
            plot = o.str("plot") ?: o.str("description"),
            cast = o.str("cast") ?: o.str("actors"),
            director = o.str("director"),
            genre = o.str("genre"),
            releaseDate = o.str("releaseDate") ?: o.str("release_date") ?: o.str("releasedate"),
            rating = o.double("rating")?.takeIf { it > 0 },
            backdrop = o.firstImage("backdrop_path"),
            categoryId = o.str("category_id") ?: o.firstOfArray("category_ids"),
            lastModified = o.long("last_modified")?.times(1000),
            trailer = o.str("youtube_trailer"),
            tmdbId = o.str("tmdb") ?: o.str("tmdb_id"),
        )
    }

    fun seriesInfo(root: JsonElement, seriesId: Long): XtSeriesInfo {
        val o = root as? JsonObject ?: return XtSeriesInfo(null, emptyList(), emptyMap())
        val info = (o["info"] as? JsonObject)?.let { series(JsonObject(it + ("series_id" to JsonPrimitive(seriesId)))) }
        val covers = HashMap<Int, String>()
        (o["seasons"] as? JsonArray)?.forEach { s ->
            val so = s as? JsonObject ?: return@forEach
            val n = so.int("season_number") ?: return@forEach
            (so.str("cover_big") ?: so.str("cover"))?.let { covers[n] = it }
        }
        val episodes = ArrayList<XtEpisode>()
        fun addEpisode(e: JsonElement, seasonHint: Int?) {
            val eo = e as? JsonObject ?: return
            val id = eo.str("id") ?: return
            val einfo = eo["info"] as? JsonObject
            episodes += XtEpisode(
                id = id,
                season = eo.int("season") ?: seasonHint ?: 1,
                episodeNum = eo.int("episode_num") ?: (episodes.size + 1),
                title = eo.str("title") ?: "Épisode",
                extension = eo.str("container_extension"),
                durationSecs = einfo?.int("duration_secs") ?: einfo?.str("duration")?.let(::parseDuration),
                plot = einfo?.str("plot"),
                image = einfo?.str("movie_image") ?: einfo?.str("cover_big"),
                rating = einfo?.double("rating")?.takeIf { it > 0 },
            )
        }
        when (val eps = o["episodes"]) {
            is JsonObject -> eps.forEach { (season, list) ->
                (list as? JsonArray)?.forEach { addEpisode(it, season.toIntOrNull()) }
            }
            is JsonArray -> eps.forEach { item ->
                if (item is JsonArray) item.forEach { addEpisode(it, null) } else addEpisode(item, null)
            }
            else -> Unit
        }
        episodes.sortWith(compareBy({ it.season }, { it.episodeNum }))
        return XtSeriesInfo(info, episodes, covers)
    }

    fun vodInfo(root: JsonElement): XtVodInfo? {
        val o = root as? JsonObject ?: return null
        val info = o["info"] as? JsonObject // peut être [] sur certains panels
        val movie = o["movie_data"] as? JsonObject
        return XtVodInfo(
            plot = info?.str("plot") ?: info?.str("description"),
            cast = info?.str("cast") ?: info?.str("actors"),
            director = info?.str("director"),
            genre = info?.str("genre"),
            releaseDate = info?.str("releasedate") ?: info?.str("release_date"),
            durationSecs = info?.int("duration_secs") ?: info?.str("duration")?.let(::parseDuration),
            rating = info?.double("rating")?.takeIf { it > 0 },
            backdrop = info?.firstImage("backdrop_path"),
            poster = info?.str("movie_image") ?: info?.str("cover_big"),
            trailer = info?.str("youtube_trailer"),
            tmdbId = info?.str("tmdb_id"),
            extension = movie?.str("container_extension"),
        )
    }

    fun shortEpg(root: JsonElement): List<XtEpg> {
        val list = ((root as? JsonObject)?.get("epg_listings") as? JsonArray) ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val start = o.long("start_timestamp")?.times(1000) ?: return@mapNotNull null
            val stop = o.long("stop_timestamp")?.times(1000) ?: return@mapNotNull null
            XtEpg(start, stop, decodeMaybeBase64(o.str("title")) ?: "Sans titre", decodeMaybeBase64(o.str("description")))
        }
    }

    /** "01:45:00" → 6300 s */
    fun parseDuration(s: String): Int? {
        val parts = s.trim().split(':').map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0] * 60
            else -> null
        }
    }

    /** Les titres d'EPG Xtream sont en base64, mais pas sur tous les panels. */
    fun decodeMaybeBase64(s: String?): String? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        if (t.length % 4 != 0 || !t.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) return t
        return try {
            val bytes = Base64.getDecoder().decode(t)
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            if (decoded.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) t else decoded.trim()
        } catch (_: IllegalArgumentException) {
            t
        } catch (_: CharacterCodingException) {
            t
        }
    }
}

// --- accesseurs défensifs ---------------------------------------------------

fun JsonElement?.stringOrNull(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content.trim().takeIf { it.isNotEmpty() && it != "null" }
    else -> null
}

fun JsonObject.str(key: String): String? = this[key].stringOrNull()
fun JsonObject.double(key: String): Double? = str(key)?.replace(',', '.')?.toDoubleOrNull()
fun JsonObject.long(key: String): Long? = str(key)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
fun JsonObject.int(key: String): Int? = long(key)?.toInt()

private fun JsonObject.firstOfArray(key: String): String? = (this[key] as? JsonArray)?.firstOrNull().stringOrNull()

private fun JsonObject.firstImage(key: String): String? = when (val v = this[key]) {
    is JsonArray -> v.firstNotNullOfOrNull { it.stringOrNull() }
    else -> v.stringOrNull()
}
