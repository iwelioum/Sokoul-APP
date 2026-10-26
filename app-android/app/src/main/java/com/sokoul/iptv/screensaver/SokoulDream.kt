package com.sokoul.iptv.screensaver

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.sokoul.iptv.R
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.TmdbMetaEntity
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.tmdbSized
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Choix des diapositives de l'économiseur (pur, testé) : un fond par œuvre, celles avec logo d'abord. */
object ScreensaverPicks {
    data class Slide(val backdrop: String, val logo: String?, val title: String, val meta: String)

    fun slides(metas: List<TmdbMetaEntity>, n: Int = 30): List<Slide> = metas
        .filter { !it.backdrop.isNullOrBlank() }
        .distinctBy { it.backdrop }
        .sortedByDescending { it.logo != null }
        .take(n)
        .map { m ->
            Slide(
                backdrop = tmdbSized(m.backdrop, "w1280")!!,
                logo = tmdbSized(m.logo, "w500"),
                title = m.title,
                meta = listOfNotNull(m.year?.toString(), m.genres?.split(',')?.take(2)?.joinToString(", ")?.ifBlank { null }).joinToString("  ·  "),
            )
        }
        .shuffled()
}

/**
 * Économiseur d'écran Sokoul : les fonds des films et séries de l'abonnement, en fondu lent avec un léger
 * zoom, le logo (ou le titre) en bas à gauche et l'heure en haut à droite. Vues Android simples
 * (une image plein écran à la fois) : rien de lourd pour la puce graphique de la box.
 */
@AndroidEntryPoint
class SokoulDream : DreamService() {
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var playlists: PlaylistRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        val serif = runCatching { ResourcesCompat.getFont(this, R.font.dm_serif_display) }.getOrNull()
        val sans = runCatching { ResourcesCompat.getFont(this, R.font.manrope) }.getOrNull()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(10, 10, 15)) }
        val images = List(2) { ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f } }
        images.forEach { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        // voile bas-gauche pour la lisibilité du titre
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(Color.argb(230, 10, 10, 15), Color.argb(90, 10, 10, 15), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(-1, -1))
        val caption = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val logo = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_START }
        val title = TextView(this).apply { typeface = serif; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f) }
        val meta = TextView(this).apply { typeface = sans; setTextColor(Color.argb(200, 242, 242, 247)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) }
        caption.addView(logo, LinearLayout.LayoutParams(dp(420), dp(120)))
        caption.addView(title)
        caption.addView(meta)
        root.addView(caption, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(64), 0, 0, dp(56)) })
        val clock = TextView(this).apply { typeface = serif; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f) }
        root.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(36), dp(56), 0) })
        setContentView(root)

        val fmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
        scope.launch {
            val pid = playlists.activeNow()?.id ?: return@launch
            val slides = ScreensaverPicks.slides(runCatching { db.browse().screensaverPicks(pid, 60) }.getOrDefault(emptyList()))
            if (slides.isEmpty()) { title.text = "Sokoul TV"; return@launch }
            var i = 0
            while (isActive) {
                val s = slides[i % slides.size]
                val front = images[i % 2]; val back = images[(i + 1) % 2]
                val loaded = load(s.backdrop, front)
                clock.text = fmt.format(Date())
                if (loaded) {
                    showCaption(s, logo, title, meta)
                    front.scaleX = 1f; front.scaleY = 1f
                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(front, View.ALPHA, 0f, 1f).setDuration(1_600),
                            ObjectAnimator.ofFloat(back, View.ALPHA, back.alpha, 0f).setDuration(1_600),
                            ObjectAnimator.ofFloat(front, View.SCALE_X, 1f, 1.06f).setDuration(SLIDE_MS),
                            ObjectAnimator.ofFloat(front, View.SCALE_Y, 1f, 1.06f).setDuration(SLIDE_MS),
                        )
                        start()
                    }
                    delay(SLIDE_MS)
                }
                i++
            }
        }
    }

    private suspend fun load(url: String, into: ImageView): Boolean {
        val r = imageLoader.execute(ImageRequest.Builder(this).data(url).build())
        val d = r.drawable ?: return false
        into.setImageDrawable(d)
        return true
    }

    private suspend fun showCaption(s: ScreensaverPicks.Slide, logo: ImageView, title: TextView, meta: TextView) {
        val logoDrawable = s.logo?.let { imageLoader.execute(ImageRequest.Builder(this).data(it).build()).drawable }
        logo.setImageDrawable(logoDrawable)
        logo.visibility = if (logoDrawable != null) View.VISIBLE else View.GONE
        title.text = s.title
        title.visibility = if (logoDrawable == null) View.VISIBLE else View.GONE
        meta.text = s.meta
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /** Durée d'une diapositive. */
        const val SLIDE_MS = 12_000L
    }
}
