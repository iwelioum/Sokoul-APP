package com.sokoul.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Chargeur d'images du bureau (Compose Desktop n'en a pas) : cache mémoire borné en octets décodés (300 Mo),
 * cache disque dans `%LOCALAPPDATA%\Sokoul\images` (affiches et fonds TMDB réutilisés d'un lancement à l'autre).
 */
object ImageLoader {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val gate = Semaphore(8)
    private const val MAX_BYTES = 300L * 1024 * 1024
    private var bytes = 0L
    private val cache = LinkedHashMap<String, ImageBitmap>(256, 0.75f, true)
    private val failed = HashSet<String>()

    /** Remplaçable par les rendus sans fenêtre (images d'exemple locales). */
    @Volatile var dir: File? = runCatching {
        File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "Sokoul/images").apply { mkdirs() }
    }.getOrNull()

    fun cached(url: String): ImageBitmap? = synchronized(cache) { cache[url] }

    private fun put(url: String, img: ImageBitmap) = synchronized(cache) {
        cache.put(url, img)?.let { bytes -= weight(it) }
        bytes += weight(img)
        val it = cache.entries.iterator()
        while (bytes > MAX_BYTES && it.hasNext()) { val e = it.next(); if (e.key == url) continue; bytes -= weight(e.value); it.remove() }
    }

    private fun weight(i: ImageBitmap) = i.width.toLong() * i.height * 4

    private fun fileFor(url: String): File? = dir?.let { d ->
        val h = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        File(d, "$h.img")
    }

    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it }
        if (synchronized(failed) { url in failed }) return null
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                cached(url)?.let { return@withContext it }
                runCatching {
                    val file = fileFor(url)
                    val data = if (url.startsWith("file:")) File(java.net.URI(url)).readBytes()
                    else file?.takeIf { it.isFile && it.length() > 0 }?.readBytes() ?: http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        r.body?.bytes()?.takeIf { r.isSuccessful && it.size < 8_000_000 }?.also { b -> runCatching { file?.writeBytes(b) } }
                    }
                    data?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
                }.getOrNull().also { img ->
                    if (img != null) put(url, img) else synchronized(failed) { failed += url }
                }
            }
        }
    }
}

/** TMDB : même image dans une taille plus légère (« w1280 » → « w780 ») pour les vignettes. */
fun tmdbSize(url: String?, size: String): String? = url?.replace(Regex("/t/p/w\\d+/|/t/p/original/"), "/t/p/$size/")

/** Image distante avec fondu à l'apparition ; [fallback] tant qu'elle n'est pas là (ou si elle manque). */
@Composable
fun NetImage(
    url: String?,
    description: String?,
    modifier: Modifier,
    scale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    fallback: @Composable () -> Unit = {},
) {
    var img by remember(url) { mutableStateOf(url?.let(ImageLoader::cached)) }
    val alpha = remember(url) { Animatable(if (img != null) 1f else 0f) }
    LaunchedEffect(url) {
        if (img == null && !url.isNullOrBlank() && (url.startsWith("http", true) || url.startsWith("file:"))) {
            img = ImageLoader.load(url)
            if (img != null) alpha.animateTo(1f, tween(260))
        }
    }
    val bmp = img
    Box(modifier) {
        if (bmp == null || alpha.value < 1f) fallback()
        if (bmp != null) Image(bmp, description, Modifier.matchParentSize().graphicsLayer { this.alpha = alpha.value }, contentScale = scale, alignment = alignment)
    }
}
