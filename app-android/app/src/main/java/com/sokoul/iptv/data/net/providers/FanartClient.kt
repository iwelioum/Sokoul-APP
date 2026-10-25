package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.parser.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/** Visuels fanart.tv : logo transparent HD, fond sans texte, « clearart ». */
data class FanartArt(val logo: String?, val background: String?, val clearart: String?)

/**
 * fanart.tv : films par identifiant TMDB, séries par identifiant TheTVDB.
 * Langue préférée : français, puis anglais, puis sans texte, puis la plus aimée.
 */
@Singleton
class FanartClient @Inject constructor(private val api: ApiHttp, private val keys: ApiKeys) {

    suspend fun movie(tmdbId: Int): FanartArt? = fetch("movies/$tmdbId", "hdmovielogo", "moviebackground", "hdmovieclearart")

    suspend fun tv(tvdbId: Int): FanartArt? = fetch("tv/$tvdbId", "hdtvlogo", "showbackground", "hdclearart")

    private suspend fun fetch(path: String, logoField: String, bgField: String, clearField: String): FanartArt? {
        val key = keys.key(ApiService.FANART).ifEmpty { return null }
        val url = "$BASE/$path".toHttpUrl().newBuilder().addQueryParameter("api_key", key).build()
        val o = api.getJson(ApiService.FANART, url, ttlMs = 7 * ApiHttp.DAY, secret = key) as? JsonObject ?: return null
        return parse(o, logoField, bgField, clearField)
    }

    companion object {
        const val BASE = "https://webservice.fanart.tv/v3"

        fun parse(o: JsonObject, logoField: String, bgField: String, clearField: String): FanartArt? {
            val art = FanartArt(
                logo = best(o, logoField, textless = false),
                background = best(o, bgField, textless = true),
                clearart = best(o, clearField, textless = false),
            )
            return art.takeIf { it.logo != null || it.background != null || it.clearart != null }
        }

        /** Meilleure image d'un type : fr > en > sans langue (fonds) > autre, puis la plus aimée. */
        fun best(o: JsonObject, field: String, textless: Boolean): String? {
            val list = (o[field] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            if (list.isEmpty()) return null
            fun rank(lang: String?): Int = when (lang) {
                "fr" -> 0
                "00", "", null -> if (textless) 0 else 2
                "en" -> 1
                else -> 3
            }
            return list.sortedWith(compareBy<JsonObject> { rank(it.str("lang")) }.thenByDescending { it.str("likes")?.toIntOrNull() ?: 0 })
                .firstNotNullOfOrNull { it.str("url")?.takeIf { u -> u.startsWith("https://") }?.replace(" ", "%20") }
        }
    }
}
