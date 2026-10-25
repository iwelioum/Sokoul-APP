package com.sokoul.iptv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Classe d'écran : téléphone, tablette portrait, grand écran tactile, TV (télécommande). */
enum class WindowClass { COMPACT, MEDIUM, EXPANDED, TV }

/**
 * Jetons de mise en page calculés selon l'écran. Toutes les tailles « en dur » des écrans
 * passent par ici : une seule source pour téléphone, tablette et TV.
 */
@Immutable
data class AppLayout(
    val window: WindowClass,
    val widthDp: Int,
    val heightDp: Int,
    /** Marge latérale du contenu. */
    val margin: Dp,
    /** Espace entre deux cartes. */
    val gutter: Dp,
    /** Largeur d'une affiche 2:3 dans une rangée. */
    val poster: Dp,
    /** Largeur d'une carte paysage 16:9. */
    val landscape: Dp,
    /** Hauteur du héros de l'écran d'accueil. */
    val heroHeight: Dp,
    /** Taille du titre du héros (quand le logo n'existe pas). */
    val heroTitle: TextUnit,
    /** Largeur maximale du bloc texte du héros. */
    val heroTextWidth: Dp,
    /** Colonne minimale d'une grille d'affiches. */
    val gridCell: Dp,
    /** Espace entre deux rangées. */
    val rowSpacing: Dp,
) {
    val isTv get() = window == WindowClass.TV
    val isCompact get() = window == WindowClass.COMPACT
    /** Écran tactile (pas de focus visible permanent) : titres toujours affichés sous les cartes. */
    val isTouch get() = window != WindowClass.TV
    /** Fiches : affiche à côté du texte (large) ou au-dessus (téléphone). */
    val sideBySide get() = window != WindowClass.COMPACT
}

fun computeLayout(widthDp: Int, heightDp: Int, isTv: Boolean): AppLayout {
    val window = when {
        isTv -> WindowClass.TV
        widthDp < 600 -> WindowClass.COMPACT
        widthDp < 840 -> WindowClass.MEDIUM
        else -> WindowClass.EXPANDED
    }
    val w = widthDp.toFloat()
    val h = heightDp.toFloat()
    return when (window) {
        // téléphone : affiche plein cadre façon appli mobile, 3,3 affiches visibles par rangée
        WindowClass.COMPACT -> AppLayout(
            window, widthDp, heightDp, margin = 16.dp, gutter = 10.dp,
            poster = ((w - 16 - 3 * 10) / 3.3f).coerceIn(96f, 130f).dp,
            landscape = (w * 0.68f).coerceIn(220f, 300f).dp,
            heroHeight = (w * 1.32f).coerceAtMost(h * 0.70f).coerceAtLeast(360f).dp,
            heroTitle = 30.sp, heroTextWidth = (w - 32).dp, gridCell = 104.dp, rowSpacing = 20.dp,
        )
        WindowClass.MEDIUM -> AppLayout(
            window, widthDp, heightDp, margin = 24.dp, gutter = 12.dp,
            poster = 132.dp, landscape = 280.dp,
            heroHeight = (w * 0.66f).coerceAtMost(h * 0.55f).coerceAtLeast(380f).dp,
            heroTitle = 36.sp, heroTextWidth = (w * 0.75f).dp, gridCell = 124.dp, rowSpacing = 24.dp,
        )
        WindowClass.EXPANDED -> AppLayout(
            window, widthDp, heightDp, margin = 40.dp, gutter = 14.dp,
            poster = 140.dp, landscape = 290.dp,
            heroHeight = (h * 0.68f).coerceIn(340f, 640f).dp,
            heroTitle = 42.sp, heroTextWidth = (w * 0.46f).coerceAtMost(620f).dp, gridCell = 132.dp, rowSpacing = 26.dp,
        )
        // TV (960×540 dp sur une box 1080p) : 7 affiches par rangée, le héros laisse dépasser la 1re rangée
        WindowClass.TV -> AppLayout(
            window, widthDp, heightDp, margin = 44.dp, gutter = 14.dp,
            poster = ((w - 44 - 72 - 6 * 14) / 6.6f).coerceIn(108f, 170f).dp,
            landscape = ((w - 44 - 72 - 3 * 14) / 3.6f).coerceIn(196f, 320f).dp,
            heroHeight = (h * 0.70f).dp,
            heroTitle = 38.sp, heroTextWidth = (w * 0.48f).dp, gridCell = 112.dp, rowSpacing = 18.dp,
        )
    }
}

val LocalLayout = staticCompositionLocalOf { computeLayout(960, 540, isTv = true) }

@Composable
fun rememberAppLayout(isTv: Boolean): AppLayout {
    val c = LocalConfiguration.current
    return remember(c.screenWidthDp, c.screenHeightDp, isTv) { computeLayout(c.screenWidthDp, c.screenHeightDp, isTv) }
}

/** Mouvements : durées et courbes communes (focus, apparition, fondus du héros). */
object Motion {
    const val FAST = 160
    const val MEDIUM = 300
    const val SLOW = 650
    /** Décalage entre deux éléments qui apparaissent l'un après l'autre (logo, méta, boutons…). */
    const val STAGGER = 60
    const val HERO_INTERVAL_MS = 8_000L
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    /** Ressort doux pour le focus : rapide, sans rebond visible. */
    fun <T> focusSpring() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.85f, stiffness = 900f)
    fun <T> enter(delay: Int = 0) = androidx.compose.animation.core.tween<T>(MEDIUM, delay, Decelerate)
    fun <T> exit() = androidx.compose.animation.core.tween<T>(FAST, 0, Accelerate)
}

