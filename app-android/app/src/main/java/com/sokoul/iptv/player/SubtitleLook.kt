package com.sokoul.iptv.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.SubtitleBackground
import com.sokoul.iptv.data.SubtitleColor

/**
 * Apparence des sous-titres choisie dans Réglages › Lecture, appliquée à toutes les vues de sous-titres du lecteur
 * (sous-titres du fichier, en ligne, décalés). Le style système n'est pas réglable sur toutes les box (Fire TV).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object SubtitleLook {
    /** Fond semi-transparent du mode « Fond noir » (75 % d'opacité). */
    private val BOX = Color.argb(191, 0, 0, 0)

    fun style(s: AppSettings): CaptionStyleCompat {
        val text = if (s.subtitleColor == SubtitleColor.YELLOW) Color.rgb(255, 230, 0) else Color.WHITE
        return when (s.subtitleBackground) {
            SubtitleBackground.OUTLINE -> CaptionStyleCompat(text, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, Typeface.DEFAULT_BOLD)
            SubtitleBackground.BOX -> CaptionStyleCompat(text, BOX, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.TRANSPARENT, Typeface.DEFAULT)
            SubtitleBackground.NONE -> CaptionStyleCompat(text, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, Typeface.DEFAULT)
        }
    }

    fun apply(view: SubtitleView, s: AppSettings) {
        // le style du fichier (couleurs d'un .ass) est ignoré : seul le texte compte, lisible à 3 m
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        view.setStyle(style(s))
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * s.subtitleSize.scale)
        view.setBottomPaddingFraction(s.subtitlePosition.bottomFraction)
    }
}
