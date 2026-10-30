package com.sokoul.iptv.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

class HttpException(val code: Int, message: String) : IOException(message)

/** Exécute une requête GET et renvoie le corps, décompressé si c'est du gzip. */
fun OkHttpClient.openStream(url: String, userAgent: String? = null): Pair<Response, InputStream> {
    val req = Request.Builder().url(url).apply { if (!userAgent.isNullOrBlank()) header("User-Agent", userAgent) }.build()
    val resp = newCall(req).execute()
    if (!resp.isSuccessful) {
        val code = resp.code
        resp.close()
        throw HttpException(code, httpMessage(code))
    }
    val body = resp.body ?: run { resp.close(); throw IOException("Réponse vide") }
    val raw = BufferedInputStream(body.byteStream(), 64 * 1024)
    raw.mark(2)
    val b1 = raw.read()
    val b2 = raw.read()
    raw.reset()
    val stream = if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(raw, 64 * 1024), 64 * 1024) else raw
    return resp to stream
}

fun httpMessage(code: Int): String = when (code) {
    401, 403 -> "Accès refusé par le serveur (identifiants incorrects ou abonnement expiré)"
    404 -> "Adresse introuvable sur le serveur (404)"
    429 -> "Trop de requêtes : le serveur limite les connexions"
    in 500..599 -> "Le serveur IPTV rencontre une erreur ($code)"
    else -> "Erreur HTTP $code"
}

/** Masque identifiants et clés dans un texte (URL, en-têtes) avant tout journal. Partagé Android / PC. */
object Redact {
    fun text(s: String): String = s
        .replace(Regex("(password=)[^&\\s]+"), "$1***")
        .replace(Regex("(username=)[^&\\s]+"), "$1***")
        .replace(Regex("(/(?:live|movie|series|timeshift)/)[^/]+/[^/]+/"), "$1***/***/")
        // clés des services de données (TMDB, OMDb, fanart.tv, TasteDive, Watchmode…), en paramètre ou en en-tête
        .replace(Regex("((?:api_key|apikey|apiKey|api-key|k|key|token)=)[^&\\s]+"), "$1***")
        // extensions Real-Debrid : jeton dans le chemin (Torrentio « realdebrid=… », « /resolve/realdebrid/… », configuration Comet)
        .replace(Regex("(realdebrid[=/])[^/|%&\\s]+"), "$1***")
        .replace(Regex("(comet[^/\\s]*/)[A-Za-z0-9+_=-]{40,}"), "$1***")
        .replace(Regex("((?:X-API-Key|Api-Key|Authorization|api-key):\\s*)\\S.*", RegexOption.IGNORE_CASE), "$1***")
}
