package com.sokoul.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
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
    val Divider = Color(0x1FFFFFFF)
    val Panel = Color(0xF2111118)
    val VideoBlack = Color.Black
}

object Radius {
    val Tiny = RoundedCornerShape(4.dp)
    val Card = RoundedCornerShape(10.dp)
    val Panel = RoundedCornerShape(16.dp)
    val Round = RoundedCornerShape(50)
}

object Space {
    val Xs = 6.dp
    val Sm = 10.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
}

private val sokoulTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 36.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp),
)

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
    error = SokoulColors.Live,
)

@Composable
fun SokoulTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = scheme,
    typography = sokoulTypography,
    content = content,
)
