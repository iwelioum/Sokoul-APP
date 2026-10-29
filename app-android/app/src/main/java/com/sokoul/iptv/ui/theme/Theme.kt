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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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
    /** Texte et icônes posés sur la vidéo ou une image (lecteur, héros). */
    val OnVideo = Color.White
    /** Sous-titres jaunes (Réglages › Lecture) : même teinte que les sous-titres du fichier (`SubtitleLook`). */
    val SubtitleYellow = Color(0xFFFFE600)
    /** Fond noir derrière la vidéo. */
    val VideoBlack = Color.Black
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
    /** Couleurs des avatars de profil (ProfileRepository.AVATAR_COLORS). */
    val Avatars = listOf(Color(0xFF7C6CFF), Color(0xFFFF6B8B), Color(0xFF3DDC97), Color(0xFFFFB547), Color(0xFF4DB6FF), Color(0xFFB36CFF))
    fun avatar(i: Int) = Avatars[Math.floorMod(i, Avatars.size)]

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

/**
 * Manrope (Google Fonts, licence OFL — `assets/licenses/Manrope-OFL.txt`) : géométrique et nette, très lisible à 3 m.
 * Une seule police variable ; chaque graisse en est une instance (de 300 à 800 : « Black » retombe sur 800).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(w: Int) = if (android.os.Build.VERSION.SDK_INT >= 26) Font(
    com.sokoul.iptv.R.font.manrope, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
) else Font(manropeStatic(w), FontWeight(w)) // Android 7 (Fire TV) : pas de police variable, une graisse par fichier

/** Graisses figées de Manrope (mêmes dessins, licence OFL) pour les appareils sans polices variables. */
private fun manropeStatic(w: Int) = when (w) {
    300 -> com.sokoul.iptv.R.font.manrope_w300
    400 -> com.sokoul.iptv.R.font.manrope_w400
    500 -> com.sokoul.iptv.R.font.manrope_w500
    600 -> com.sokoul.iptv.R.font.manrope_w600
    700 -> com.sokoul.iptv.R.font.manrope_w700
    else -> com.sokoul.iptv.R.font.manrope_w800
}

val Manrope = FontFamily(manrope(300), manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

/**
 * DM Serif Display (Google Fonts, licence OFL — `assets/licenses/DMSerifDisplay-OFL.txt`) : titres de cinéma, contraste
 * plein/délié. Une seule graisse (jamais de gras simulé) ; réservée aux tailles d'au moins 20 sp pour rester lisible à 3 m.
 */
val SerifDisplay = FontFamily(
    Font(com.sokoul.iptv.R.font.dm_serif_display, FontWeight.Normal),
    Font(com.sokoul.iptv.R.font.dm_serif_display_italic, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
)

/** Taille minimale de la police à empattements. */
const val SERIF_MIN_SP = 20

private fun style(size: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f, line: Int? = null) = TextStyle(
    fontFamily = Manrope, fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp,
    lineHeight = line?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
)

private val typography = Typography(
    // grands titres resserrés (Manrope est large) ; étiquettes légèrement aérées
    displayMedium = style(40, FontWeight.ExtraBold, -1.2f, 44),
    displaySmall = style(34, FontWeight.ExtraBold, -0.9f),
    headlineMedium = style(26, FontWeight.Bold, -0.6f),
    headlineSmall = style(22, FontWeight.Bold, -0.4f),
    titleLarge = style(19, FontWeight.Bold, -0.2f),
    titleMedium = style(16, FontWeight.SemiBold),
    titleSmall = style(14, FontWeight.SemiBold),
    bodyLarge = style(16, line = 24),
    bodyMedium = style(14, line = 21),
    bodySmall = style(12, line = 17),
    labelLarge = style(14, FontWeight.SemiBold, 0.2f),
    labelMedium = style(12, FontWeight.SemiBold, 0.2f),
    labelSmall = style(11, FontWeight.SemiBold, 0.3f),
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
