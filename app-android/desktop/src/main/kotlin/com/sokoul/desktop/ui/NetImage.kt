package com.sokoul.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Chargement des logos et images par HTTP, avec cache mémoire borné (Compose Desktop n'a pas de chargeur d'images). */
object ImageLoader {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val gate = Semaphore(6)
    private val cache = object : LinkedHashMap<String, ImageBitmap>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 400
    }
    private val failed = HashSet<String>()

    fun cached(url: String): ImageBitmap? = synchronized(cache) { cache[url] }

    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it }
        if (synchronized(failed) { url in failed }) return null
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        val bytes = r.body?.bytes()?.takeIf { r.isSuccessful && it.size < 4_000_000 } ?: return@use null
                        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    }
                }.getOrNull().also { img ->
                    if (img != null) synchronized(cache) { cache[url] = img } else synchronized(failed) { failed += url }
                }
            }
        }
    }
}

@Composable
fun NetImage(url: String?, description: String?, modifier: Modifier, scale: ContentScale = ContentScale.Fit, fallback: @Composable () -> Unit = {}) {
    var img by remember(url) { mutableStateOf(url?.let(ImageLoader::cached)) }
    LaunchedEffect(url) { if (img == null && !url.isNullOrBlank() && url.startsWith("http", true)) img = ImageLoader.load(url) }
    val bmp = img
    if (bmp != null) Image(bmp, description, modifier, contentScale = scale) else Box(modifier) { fallback() }
}
