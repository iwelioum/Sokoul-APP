package com.sokoul.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ProbeResult(val ok: Boolean, val message: String)

object ProviderProbe {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(db: DesktopDb, id: String): ProbeResult = withContext(Dispatchers.IO) {
        val key = ProviderConfig.key(db, id) ?: return@withContext ProbeResult(false, "Clé absente")
        val request = when (id) {
            "tmdb" -> Request.Builder().url("https://api.themoviedb.org/3/configuration?api_key=$key").get().build()
            "realdebrid" -> Request.Builder().url("https://api.real-debrid.com/rest/1.0/user").header("Authorization", "Bearer $key").get().build()
            "omdb" -> Request.Builder().url("https://www.omdbapi.com/?apikey=$key&t=Inception").get().build()
            "tastedive" -> Request.Builder().url("https://tastedive.com/api/similar?key=$key&q=Inception&limit=1").get().build()
            "tvmaze" -> Request.Builder().url("https://api.tvmaze.com/search/shows?q=Inception").get().build()
            "fanart" -> Request.Builder().url("https://webservice.fanart.tv/v3/movies/tt1375666?api_key=$key").get().build()
            "tvdb" -> Request.Builder().url("https://api4.thetvdb.com/v4/login")
                .post("""{"apikey":"$key"}""".toRequestBody("application/json".toMediaType())).build()
            "motn" -> Request.Builder().url("https://api.movieofthenight.com/v4/shows/movie/27205?country=fr&output_language=fr")
                .header("X-API-Key", key).get().build()
            "watchmode" -> Request.Builder().url("https://api.watchmode.com/v1/list-titles/?apiKey=$key&limit=1").get().build()
            "opensubtitles" -> Request.Builder().url("https://api.opensubtitles.com/api/v1/subtitles?tmdb_id=27205&languages=fr")
                .header("Api-Key", key).header("User-Agent", "SokoulTV v2.0").get().build()
            "subdl" -> Request.Builder().url("https://api.subdl.com/api/v1/subtitles?api_key=$key&tmdb_id=27205&type=movie&languages=FR&subs_per_page=1").get().build()
            else -> return@withContext ProbeResult(true, "Clé détectée, test dédié à l'utilisation")
        }
        runCatching {
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) ProbeResult(true, "Connexion réussie")
                else ProbeResult(false, "HTTP ${response.code}")
            }
        }.getOrElse { ProbeResult(false, "Réseau indisponible") }
    }
}
