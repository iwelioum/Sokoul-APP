package com.sokoul.iptv.ui.components

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lueur d'ambiance : la teinte d'une image, assez sombre pour rester derrière le texte.
 * Calculée sur une miniature logicielle de 64 px (Palette ne lit pas les images matérielles),
 * jamais sur l'image entière ; mise en cache par adresse.
 */
object AmbientTint {
    private val cache = LruCache<String, Int>(200)

    /** Bornes HSL : ni noir (L ≥ 0,12) ni criard (L ≤ 0,26, S ≤ 0,80), ni gris (S ≥ 0,35). */
    fun clamp(argb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[1] = hsl[1].coerceIn(0.35f, 0.80f)
        hsl[2] = hsl[2].coerceIn(0.12f, 0.26f)
        return ColorUtils.HSLToColor(hsl)
    }

    /** Ordre de préférence des nuances : sombre vive → vive → atténuée → dominante. */
    fun pick(p: Palette): Int? =
        (p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch)?.rgb

    fun cached(url: String): Int? = cache.get(url)

    internal fun put(url: String, argb: Int) { cache.put(url, argb) }
}

/** Teinte de [url] (null si l'image ne charge pas) : cache, sinon miniature de 64 px analysée hors du fil principal. */
suspend fun AmbientTint.of(context: android.content.Context, url: String): Color? {
    cached(url)?.let { return Color(it) }
    val request = ImageRequest.Builder(context).data(url).size(64).allowHardware(false).build()
    val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable ?: return null
    val argb = withContext(Dispatchers.Default) {
        pick(Palette.from(bitmap.bitmap).maximumColorCount(12).generate())?.let(::clamp)
    } ?: return null
    put(url, argb)
    return Color(argb)
}

/**
 * Image d'ambiance et sa teinte, publiées ensemble : le fond (une texture en cache) n'est recalculé
 * qu'une fois par œuvre, pas une fois pour l'image puis une autre pour la teinte.
 */
@Composable
fun rememberAmbient(image: String?, tintUrl: (String) -> String, fallback: Color): State<Pair<String?, Color>> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Pair<String?, Color>>(null to fallback) }
    LaunchedEffect(image) {
        if (image == null) { state.value = null to state.value.second; return@LaunchedEffect }
        val tint = AmbientTint.of(context, tintUrl(image)) ?: state.value.second
        state.value = image to tint
    }
    return state
}

