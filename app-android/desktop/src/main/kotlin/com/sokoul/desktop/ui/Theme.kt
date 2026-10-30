package com.sokoul.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Même palette que l'app Android (`ui/theme/Theme.kt`, SokoulColors). */
object SokoulColors {
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF111118)
    val SurfaceHigh = Color(0xFF1A1A24)
    val SurfaceHigher = Color(0xFF242433)
    val Accent = Color(0xFF7C6CFF)
    val AccentSoft = Color(0x337C6CFF)
    val AccentTint = Color(0x147C6CFF)
    val AccentEnd = Color(0xFFB36CFF)
    val Live = Color(0xFFFF4D5E)
    val Success = Color(0xFF3DDC97)
    val Warning = Color(0xFFFFB547)
    val TextPrimary = Color(0xFFF2F2F7)
    val TextSecondary = Color(0xFFA0A0B4)
    val TextMuted = Color(0xFF6B6B80)
    val Focus = Color(0xFFFFFFFF)
    val Divider = Color(0x1FFFFFFF)
    val Hairline = Color(0x14FFFFFF)
    val Skeleton = Color(0xFF1C1C27)
    val SkeletonShine = Color(0xFF2A2A3A)
    val Gold = Color(0xFFFFC94D)
    val Scrim = Color(0xB3000000)
    val ScrimStrong = Color(0xF2000000)
    val GlassLight = Color(0x26FFFFFF)
    val GlassHeavy = Color(0x47FFFFFF)
    val Panel = Color(0xF2111118)
    val OnLight = Color(0xFF0A0A0F)
    val Imdb = Color(0xFFF5C518)
    val AgeAll = Color(0xFF1E8E4E)
    val Age10 = Color(0xFF1F6FB8)
    val Age12 = Color(0xFFC46A0A)
    val Age16 = Color(0xFFC62835)
    val Age18 = Color(0xFF141414)
    val PlaceholderTop = Color(0xFF262638)
    val PlaceholderBottom = Color(0xFF15151F)
    val VideoBlack = Color.Black
}

/** Arrondis « nets » de l'app Android : coins presque droits ; le rond seulement pour ce qui est rond. */
object Radius {
    val Tiny = RoundedCornerShape(2.dp)
    val Card = RoundedCornerShape(4.dp)
    val Panel = RoundedCornerShape(6.dp)
    val Pill = RoundedCornerShape(4.dp)
    val Round = RoundedCornerShape(50)
}

object Edge {
    val Hairline = 1.dp
    val Focus = 2.dp
    val Accent = 3.dp
}

/** Espacements : multiples de 4 ; [Gutter] = marge latérale des écrans. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val sm = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 40.dp
    val Gutter = 56.dp
}

object Fonts {
    val Manrope = FontFamily(
        Font("font/manrope_w300.ttf", FontWeight.Light),
        Font("font/manrope_w400.ttf", FontWeight.Normal),
        Font("font/manrope_w500.ttf", FontWeight.Medium),
        Font("font/manrope_w600.ttf", FontWeight.SemiBold),
        Font("font/manrope_w700.ttf", FontWeight.Bold),
        Font("font/manrope_w800.ttf", FontWeight.ExtraBold),
        Font("font/manrope_w800.ttf", FontWeight.Black),
    )
    val Serif = FontFamily(
        Font("font/dm_serif_display.ttf", FontWeight.Normal),
        Font("font/dm_serif_display_italic.ttf", FontWeight.Normal, FontStyle.Italic),
    )
}

/** Rôles de texte du bureau (même logique que `Type` de l'Android, tailles adaptées à l'écran d'un PC). */
object Type {
    private fun serif(size: Int, spacing: Float = 0f, line: Int? = null) = TextStyle(
        fontFamily = Fonts.Serif, fontWeight = FontWeight.Normal, fontSize = size.sp, letterSpacing = spacing.sp,
        lineHeight = line?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified, color = SokoulColors.TextPrimary,
    )
    private fun sans(size: Int, weight: FontWeight = FontWeight.Normal, line: Int? = null, spacing: Float = 0f, color: Color = SokoulColors.TextPrimary) = TextStyle(
        fontFamily = Fonts.Manrope, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp, color = color,
        lineHeight = line?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
    )

    /** Titre d'une œuvre sans logo (héros, fiche). */
    val Display = serif(60, -0.6f, 64)
    /** Titre de page (Films, Séries, Réglages). */
    val ScreenTitle = serif(40, -0.3f, 46)
    /** Titre de rangée ou de section. */
    val Section = serif(25, 0.1f, 30)
    /** Titre dans le lecteur. */
    val PlayerTitle = serif(28, -0.2f)
    /** Titre d'un élément de liste (épisode, chaîne, réglage). */
    val ItemTitle = sans(15, FontWeight.Bold, 20)
    /** Texte courant (résumés). */
    val Body = sans(15, FontWeight.Normal, 23, color = SokoulColors.TextSecondary)
    val BodyStrong = sans(16, FontWeight.Medium, 25, color = SokoulColors.TextPrimary)
    val Small = sans(13, FontWeight.Medium, 18, color = SokoulColors.TextSecondary)
    /** Repères (année · durée · genre). */
    val Meta = sans(14, FontWeight.SemiBold, 18, color = SokoulColors.TextSecondary)
    /** Petites capitales espacées au-dessus d'un titre (« TENDANCE », « SÉRIE »). */
    val Kicker = sans(12, FontWeight.ExtraBold, 16, 2.2f, SokoulColors.Accent)
    val Overline = sans(11, FontWeight.Bold, 14, 1.2f, SokoulColors.TextMuted)
    val Button = sans(15, FontWeight.Bold, 18)
    val Badge = sans(11, FontWeight.ExtraBold, 13, 0.6f)
    val Nav = sans(15, FontWeight.SemiBold, 18)
    /** Grand chiffre du Top 10. */
    val Rank = TextStyle(fontFamily = Fonts.Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 120.sp, letterSpacing = (-8).sp, lineHeight = 120.sp)
    val ChannelNumber = sans(15, FontWeight.ExtraBold, 18).copy(fontFeatureSettings = "tnum")
    val Input = sans(16, FontWeight.Medium, 22)
    val SearchQuery = sans(30, FontWeight.SemiBold, 36)
}

private val sokoulTypography = Typography().let { t ->
    fun TextStyle.m() = copy(fontFamily = Fonts.Manrope)
    Typography(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m(), titleMedium = t.titleMedium.m(), titleSmall = t.titleSmall.m(),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(), labelMedium = t.labelMedium.m(), labelSmall = t.labelSmall.m(),
    )
}

private val scheme = darkColorScheme(
    primary = SokoulColors.Accent,
    onPrimary = Color.White,
    secondary = SokoulColors.Accent,
    background = SokoulColors.Background,
    onBackground = SokoulColors.TextPrimary,
    surface = SokoulColors.Surface,
    onSurface = SokoulColors.TextPrimary,
    surfaceVariant = SokoulColors.SurfaceHigh,
    onSurfaceVariant = SokoulColors.TextSecondary,
    surfaceContainerHigh = SokoulColors.SurfaceHigh,
    outline = SokoulColors.Divider,
    error = SokoulColors.Live,
)

@Composable
fun SokoulTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = scheme,
    typography = sokoulTypography,
    content = content,
)
