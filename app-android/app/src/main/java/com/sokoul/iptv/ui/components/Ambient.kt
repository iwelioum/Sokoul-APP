package com.sokoul.iptv.ui.components

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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


/**
 * Fond d'ambiance « cuit » : image très atténuée, lueur et dégradé du haut peints une fois dans une petite image
 * (320 px de large), puis simplement étirée à l'écran. Un calque plein écran à pleine définition (image + dégradé
 * radial) coûtait trop cher au processeur graphique de la box à chaque changement d'œuvre (mesuré : 8 à 18 %
 * d'images saccadées sur l'accueil, contre 4 à 5 % sans lui). Étirée, l'image devient floue : c'est voulu.
 */
object AmbientBake {
    const val WIDTH = 320
    private val cache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(24)

    suspend fun bake(context: android.content.Context, url: String, glow: Color, background: Color, aspect: Float): androidx.compose.ui.graphics.ImageBitmap? {
        val h = (WIDTH / aspect.coerceIn(0.5f, 6f)).toInt().coerceAtLeast(8)
        val key = "$url|${glow.value}|$h"
        cache.get(key)?.let { return it }
        val request = ImageRequest.Builder(context).data(url).size(WIDTH, h).allowHardware(false).build()
        val src = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return null
        val out = withContext(Dispatchers.Default) {
            val bmp = android.graphics.Bitmap.createBitmap(WIDTH, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val w = WIDTH.toFloat(); val hf = h.toFloat()
            c.drawColor(background.toArgbInt())
            // lueur : même dégradé radial qu'avant, à basse définition
            val g = glow.toArgbInt()
            val glowPaint = android.graphics.Paint().apply {
                shader = android.graphics.RadialGradient(
                    w * 0.25f, hf * 0.3f, w * 0.75f,
                    intArrayOf(g, ColorUtils.setAlphaComponent(g, (255 * 0.35f).toInt()), 0), null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(0f, 0f, w, hf, glowPaint)
            // image recadrée (comme ContentScale.Crop) à 22 %
            val scale = maxOf(w / src.width, hf / src.height)
            val dw = src.width * scale; val dh = src.height * scale
            val dst = android.graphics.RectF((w - dw) / 2f, (hf - dh) / 2f, (w + dw) / 2f, (hf + dh) / 2f)
            c.drawBitmap(src, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { alpha = (255 * 0.22f).toInt() })
            // raccord avec le panneau d'aperçu au-dessus
            val bg = background.toArgbInt()
            c.drawRect(0f, 0f, w, hf * 0.45f, android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 0f, hf * 0.45f, bg, ColorUtils.setAlphaComponent(bg, 0), android.graphics.Shader.TileMode.CLAMP)
            })
            bmp.asImageBitmap()
        }
        cache.put(key, out)
        return out
    }

    private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
}
