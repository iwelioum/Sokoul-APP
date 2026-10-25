package com.sokoul.iptv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Jetons visuels communs à toute l'app : un seul jeu d'arrondis, d'espacements et de rôles
 * de texte. Les écrans n'écrivent jamais de valeurs en dur (vérifié par `DesignTokensTest`).
 */

/** Les seuls arrondis autorisés. */
object Radius {
    /** Barres de progression, badges, petits repères. */
    val Tiny = RoundedCornerShape(4.dp)
    /** Cartes, vignettes, logos, lignes de liste, boutons carrés. */
    val Card = RoundedCornerShape(12.dp)
    /** Panneaux, surimpressions, boîtes de dialogue. */
    val Panel = RoundedCornerShape(20.dp)
    /** Pastilles, onglets, boutons arrondis. */
    val Pill = RoundedCornerShape(50)
    /** Aucun arrondi (bords d'écran, pistes pleine largeur). */
    val None = RectangleShape
}

/** Espacements : multiples de 4. Les marges d'écran passent par `LocalLayout.margin`. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 40.dp
}

/** Rôles de texte : un même rôle a le même style sur tous les écrans. */
object Type {
    /** Titre de page (en-tête d'écran). */
    val ScreenTitle: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold)
    /** Titre de section ou de rangée. */
    val Section: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    /** Ligne d'informations secondaires (année · durée · genre). */
    val Meta: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge.copy(color = SokoulColors.TextSecondary, fontWeight = FontWeight.Medium)
    /** Petite étiquette en capitales (EN CE MOMENT, ENSUITE…). */
    val Overline: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMedium.copy(
            color = SokoulColors.TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        )
}
