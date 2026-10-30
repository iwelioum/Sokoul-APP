package com.sokoul.iptv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.platform.Font
import com.sokoul.iptv.R

/**
 * Rendu d'un titre généré : police (licences OFL/Apache dans `assets/licenses/`), dégradé, lueur, casse.
 * [scale] compense la largeur de la police (Bebas est étroite, Orbitron très large).
 */
class TitleArtSpec(
    val family: FontFamily, val weight: FontWeight = FontWeight.Normal, val italic: Boolean = false,
    val upper: Boolean = false, val spacing: Float = 0f, val scale: Float = 1f,
    val colors: List<Color>, val glow: Color = Color.Black.copy(alpha = 0.6f), val glowRadius: Float = 12f,
) {
    fun style(size: TextUnit): TextStyle = TextStyle(
        fontFamily = family, fontWeight = weight, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = size, lineHeight = (size.value * 1.06f).sp, letterSpacing = spacing.em,
        brush = if (colors.size > 1) Brush.verticalGradient(colors) else null,
        shadow = Shadow(glow, blurRadius = glowRadius),
    ).let { if (colors.size > 1) it else it.copy(color = colors.first()) }
}

object TitleArtSpecs {
    private val Bebas = FontFamily(Font(R.font.bebas_neue))
    private val Cinzel = FontFamily(Font(R.font.cinzel, FontWeight.Bold))
    private val Orbitron = FontFamily(Font(R.font.orbitron, FontWeight.Bold))
    private val Creepster = FontFamily(Font(R.font.creepster))
    private val SpecialElite = FontFamily(Font(R.font.special_elite))
    private val Fredoka = FontFamily(Font(R.font.fredoka, FontWeight.Bold))
    private val Rye = FontFamily(Font(R.font.rye))

    /** Sous cette taille, les polices décoratives se lisent mal à 3 m : même couleur, mais en Manrope. */
    const val DECORATIVE_MIN_SP = 18f

    fun of(s: TitleStyle): TitleArtSpec = when (s) {
        TitleStyle.METAL -> TitleArtSpec(
            Bebas, upper = true, spacing = 0.03f, scale = 1.18f,
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFD5DCE4), Color(0xFF8E9AA8)),
        )
        TitleStyle.GOLD -> TitleArtSpec(
            Cinzel, FontWeight.Bold, upper = true, spacing = 0.06f, scale = 0.86f,
            colors = listOf(Color(0xFFFFF3C8), Color(0xFFE6BC5C), Color(0xFFA27A2C)),
        )
        TitleStyle.NEON -> TitleArtSpec(
            Orbitron, FontWeight.Bold, upper = true, spacing = 0.08f, scale = 0.74f,
            colors = listOf(Color(0xFFF0FDFF), Color(0xFF8BE9FF)), glow = Color(0xBF22C8FF), glowRadius = 22f,
        )
        TitleStyle.HORROR -> TitleArtSpec(
            Creepster, spacing = 0.04f, scale = 1.05f,
            colors = listOf(Color(0xFFFF5A4E), Color(0xFF9E0B0B)), glow = Color(0xB3B00000), glowRadius = 18f,
        )
        TitleStyle.STAMP -> TitleArtSpec(SpecialElite, upper = true, spacing = 0.04f, scale = 0.86f, colors = listOf(Color(0xFFF1EADA)))
        TitleStyle.ELEGANT -> TitleArtSpec(SerifDisplay, italic = true, scale = 1.05f, colors = listOf(Color.White))
        TitleStyle.PLAYFUL -> TitleArtSpec(
            Fredoka, FontWeight.Bold, scale = 0.98f,
            colors = listOf(Color(0xFFFFE67A), Color(0xFFFF9A5C)), glow = Color(0xB33A1A00), glowRadius = 6f,
        )
        TitleStyle.WESTERN -> TitleArtSpec(Rye, scale = 0.84f, colors = listOf(Color(0xFFF7E2B8), Color(0xFFC08A4A)))
        TitleStyle.DOC -> TitleArtSpec(Manrope, FontWeight.ExtraBold, upper = true, spacing = 0.08f, scale = 0.82f, colors = listOf(Color.White))
    }

    /** Variante lisible en petit : mêmes couleurs, Manrope ExtraBold. */
    fun small(spec: TitleArtSpec) = TitleArtSpec(
        Manrope, FontWeight.ExtraBold, upper = spec.upper, spacing = spec.spacing.coerceAtMost(0.03f),
        colors = spec.colors, glowRadius = 8f,
    )
}
