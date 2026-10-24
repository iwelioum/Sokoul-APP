package com.sokoul.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun SokoulTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
