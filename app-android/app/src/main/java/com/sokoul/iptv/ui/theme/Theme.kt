package com.sokoul.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette sombre reprise des design tokens de GinBox (sokoul-desktop/src/styles/tokens.css). */
object SokoulColors {
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF111118)
    val SurfaceHigh = Color(0xFF1A1A24)
    val SurfaceHigher = Color(0xFF242433)
    val Accent = Color(0xFF7C6CFF)
    val AccentSoft = Color(0x337C6CFF)
    val Live = Color(0xFFFF4D5E)
    val Success = Color(0xFF3DDC97)
    val Warning = Color(0xFFFFB547)
    val TextPrimary = Color(0xFFF2F2F7)
    val TextSecondary = Color(0xFFA0A0B4)
    val TextMuted = Color(0xFF6B6B80)
    val Focus = Color(0xFFFFFFFF)
    val Divider = Color(0x1FFFFFFF)
    /** Fond des squelettes de chargement et reflet animé. */
    val Skeleton = Color(0xFF1C1C27)
    val SkeletonShine = Color(0xFF2A2A3A)
    val Gold = Color(0xFFFFC94D)

    // --- calques sur image / vidéo (voiles, verre dépoli, texte sur fond clair) ---
    /** Voile léger (dégradés de lisibilité). */
    val Scrim = Color(0xB3000000)
    /** Voile fort (bas de la barre du lecteur, panneaux). */
    val ScrimStrong = Color(0xF2000000)
    /** Bouton « verre » au repos sur image. */
    val GlassLight = Color(0x33FFFFFF)
    /** Bouton « verre » mis en avant (action principale au repos sur TV). */
    val GlassHeavy = Color(0x59FFFFFF)
    /** Panneaux opaques au-dessus de la vidéo. */
    val Panel = Color(0xF2111118)
    /** Texte et icônes posés sur un fond blanc (bouton focalisé). */
    val OnLight = Color(0xFF0A0A0F)
    /** Teinte d'accent très légère (élément courant dans une liste). */
    val AccentTint = Color(0x147C6CFF)
    /** Fin du dégradé de la marque (logo S). */
    val AccentEnd = Color(0xFFB36CFF)
    /** Jaune IMDb (badge de note). */
    val Imdb = Color(0xFFF5C518)
    /** Dégradé des cartes sans image. */
    val PlaceholderTop = Color(0xFF262638)
    val PlaceholderBottom = Color(0xFF15151F)

    /** Fond d'écran assombri (dégradés et voiles posés sur les images). */
    fun bg(alpha: Float) = Background.copy(alpha = alpha)
    /** Noir transparent (ombres et voiles sur la vidéo). */
    fun black(alpha: Float) = Color.Black.copy(alpha = alpha)
    /** Blanc transparent (verre, traits fins). */
    fun white(alpha: Float) = Color.White.copy(alpha = alpha)
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
    surfaceContainer = SokoulColors.SurfaceHigh,
    surfaceContainerHigh = SokoulColors.SurfaceHigher,
    outline = SokoulColors.Divider,
    error = SokoulColors.Live,
)

private val typography = Typography(
    displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, lineHeight = 44.sp),
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/** Vrai sur Android TV / Fire TV (télécommande, pas d'écran tactile). */
val LocalIsTv = staticCompositionLocalOf { false }

/** Vrai si l'utilisateur a coupé les animations (Accessibilité ou Options développeur : échelle 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun SokoulTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduced = androidx.compose.runtime.remember {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Sans Surface parente, la couleur de texte par défaut serait le noir : illisible sur fond sombre.
        CompositionLocalProvider(
            LocalContentColor provides SokoulColors.TextPrimary,
            LocalLayout provides rememberAppLayout(LocalIsTv.current),
            LocalReducedMotion provides reduced,
            content = content,
        )
    }
}
