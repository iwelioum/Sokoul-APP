package com.sokoul.iptv.recording

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/** Refus du fournisseur (4xx) : jamais relancé tout de suite (une seule connexion autorisée). */
class StreamRefusedException(val code: Int) : IOException("HTTP $code")

/**
 * Lecture d'un flux en direct, octet par octet, pour l'enregistrement et la pause du direct : flux TS brut
 * (une connexion continue) ou HLS (segments successifs, la meilleure variante). Les octets vont à [sink] ;
 * la lecture s'arrête dès que [keepGoing] répond non. Les erreurs remontent à l'appelant (reconnexion, refus).
 */
class StreamPump(private val http: OkHttpClient) {

    suspend fun pump(url: String, ua: String, referrer: String?, keepGoing: () -> Boolean, sink: (ByteArray, Int) -> Unit) {
        if (url.substringBefore('?').endsWith(".m3u8", true)) pumpHls(url, ua, referrer, keepGoing, sink)
        else pumpRaw(url, ua, referrer, keepGoing, sink)
    }

    private fun request(url: String, ua: String, referrer: String?) =
        Request.Builder().url(url).header("User-Agent", ua).apply { referrer?.let { header("Referer", it) } }.build()

    fun pumpRaw(url: String, ua: String, referrer: String?, keepGoing: () -> Boolean, sink: (ByteArray, Int) -> Unit) {
        http.newCall(request(url, ua, referrer)).execute().use { resp ->
            if (resp.code in 400..499) throw StreamRefusedException(resp.code)
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val type = resp.header("Content-Type").orEmpty()
            if (type.contains("mpegurl", true)) throw IOException("Flux HLS inattendu")
            val input = resp.body?.byteStream() ?: throw IOException("Flux vide")
            val buf = ByteArray(64 * 1024)
            while (keepGoing()) {
                val n = input.read(buf)
                if (n < 0) throw IOException("Fin de flux inattendue")
                if (n > 0) sink(buf, n)
            }
        }
    }

    suspend fun pumpHls(playlistUrl: String, ua: String, referrer: String?, keepGoing: () -> Boolean, sink: (ByteArray, Int) -> Unit) {
        var mediaUrl = playlistUrl
        val seen = LinkedHashSet<String>()
        val buf = ByteArray(64 * 1024)
        while (keepGoing()) {
            val text = fetchText(mediaUrl, ua, referrer)
            if (text.contains("#EXT-X-STREAM-INF")) {
                mediaUrl = bestVariant(mediaUrl, text) ?: throw IOException("Aucune variante HLS")
                continue
            }
            if (text.contains("#EXT-X-KEY") && !text.contains("METHOD=NONE")) throw IOException("Flux HLS chiffré : enregistrement impossible")
            if (text.contains("#EXT-X-MAP")) throw IOException("Flux HLS fragmenté (fMP4) : enregistrement impossible")
            val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 6
            for (line in text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
                val seg = resolve(mediaUrl, line)
                if (!seen.add(seg)) continue
                if (seen.size > 500) seen.remove(seen.first())
                http.newCall(request(seg, ua, referrer)).execute().use { r ->
                    when {
                        r.isSuccessful -> r.body?.byteStream()?.let { input ->
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                if (n > 0) sink(buf, n)
                            }
                        }
                        r.code in 400..499 && r.code != 404 -> throw StreamRefusedException(r.code)
                        // segment expiré (404) ou erreur serveur ponctuelle : on passe au suivant
                        else -> Timber.w("Segment HLS ignoré (HTTP ${r.code})")
                    }
                }
                if (!keepGoing()) return
            }
            if (text.contains("#EXT-X-ENDLIST")) return
            delay((target * 1000 / 2).coerceIn(1000, 5000))
        }
    }

    private fun fetchText(url: String, ua: String, referrer: String?): String =
        http.newCall(request(url, ua, referrer)).execute().use { r ->
            if (r.code in 400..499) throw StreamRefusedException(r.code)
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }

    private fun bestVariant(base: String, master: String): String? {
        val lines = master.lines()
        var best: Pair<Long, String>? = null
        lines.forEachIndexed { i, l ->
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim() ?: return@forEachIndexed
                if (best == null || bw > best!!.first) best = bw to uri
            }
        }
        return best?.second?.let { resolve(base, it) }
    }

    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http")) ref else java.net.URI(base).resolve(ref).toString()
}
