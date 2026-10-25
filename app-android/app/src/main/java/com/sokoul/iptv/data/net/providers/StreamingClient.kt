package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.parser.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/** Offre d'un service de streaming en France : « Netflix · abonnement ». */
data class StreamingOffer(val serviceId: String, val service: String, val type: OfferType, val color: Long?)

enum class OfferType(val label: String, val rank: Int) {
    SUBSCRIPTION("abonnement", 0), FREE("gratuit", 1), ADDON("option", 2), RENT("location", 3), BUY("achat", 4);
}

/** Movie of the Night (Streaming Availability) v4 : où regarder un titre en France. */
@Singleton
class StreamingClient @Inject constructor(private val api: ApiHttp, private val keys: ApiKeys) {

    suspend fun offers(isMovie: Boolean, tmdbId: Int, country: String = "fr"): List<StreamingOffer> {
        val key = keys.key(ApiService.MOTN).ifEmpty { return emptyList() }
        val url = "$BASE/shows/${if (isMovie) "movie" else "tv"}/$tmdbId".toHttpUrl().newBuilder()
            .addQueryParameter("country", country).addQueryParameter("output_language", "fr").build()
        val o = api.getJson(ApiService.MOTN, url, ttlMs = 2 * ApiHttp.DAY, secret = key, headers = mapOf("X-API-Key" to key)) as? JsonObject
            ?: return emptyList()
        return parse(o, country)
    }

    companion object {
        const val BASE = "https://api.movieofthenight.com/v4"

        /** Une ligne par service, l'offre la plus accessible d'abord (abonnement avant location, avant achat). */
        fun parse(o: JsonObject, country: String): List<StreamingOffer> {
            val list = ((o["streamingOptions"] as? JsonObject)?.get(country) as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                val s = x["service"] as? JsonObject ?: return@mapNotNull null
                val type = when (x.str("type")) {
                    "subscription" -> OfferType.SUBSCRIPTION
                    "free" -> OfferType.FREE
                    "addon" -> OfferType.ADDON
                    "rent" -> OfferType.RENT
                    "buy" -> OfferType.BUY
                    else -> return@mapNotNull null
                }
                // option payante d'un service (« HBO Max » via Prime Video) : on affiche le nom de l'option
                val addon = (x["addon"] as? JsonObject)?.takeIf { type == OfferType.ADDON }
                val src = addon ?: s
                StreamingOffer(
                    serviceId = src.str("id") ?: return@mapNotNull null,
                    service = src.str("name") ?: return@mapNotNull null,
                    type = type,
                    color = src.str("themeColorCode")?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it },
                )
            }
            // même service sous deux identifiants (« HBO Max » direct et en option Prime Video) : une seule ligne
            return list.groupBy { it.service.lowercase() }.values.map { offers -> offers.minBy { it.type.rank } }.sortedBy { it.type.rank }
        }
    }
}
