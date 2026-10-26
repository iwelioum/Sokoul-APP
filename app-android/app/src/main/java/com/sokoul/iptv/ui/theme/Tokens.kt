package com.sokoul.iptv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Jetons visuels communs à toute l'app : un seul jeu d'arrondis, d'espacements et de rôles
 * de texte. Les écrans n'écrivent jamais de valeurs en dur (vérifié par `DesignTokensTest`).
 */

/**
 * Les seuls arrondis autorisés. Style « net » : des coins presque droits partout ; le rond est réservé
 * à ce qui est rond par nature (visages, points, bouton lecture) via [Round].
 */
object Radius {
    /** Barres de progression, badges, petits repères. */
    val Tiny = RoundedCornerShape(2.dp)
    /** Cartes, vignettes, logos, lignes de liste, boutons carrés. */
    val Card = RoundedCornerShape(4.dp)
    /** Panneaux, surimpressions, boîtes de dialogue. */
    val Panel = RoundedCornerShape(6.dp)
    /** Pastilles, onglets, boutons : rectangles aux coins adoucis (plus de gélules). */
    val Pill = RoundedCornerShape(4.dp)
    /** Cercle : visages, points, disque de lecture, curseur. */
    val Round = RoundedCornerShape(50)
    /** Aucun arrondi (bords d'écran, pistes pleine largeur). */
    val None = RoundedCornerShape(0.dp)
}

/** Traits fins : les « détails de contour » du style net. */
object Edge {
    /** Filet intérieur des cartes au repos. */
    val Hairline = 1.dp
    /** Contour blanc de la carte focalisée. */
    val Focus = 2.5.dp
    /** Liseré d'accent sous la carte focalisée et sous l'onglet actif. */
    val Accent = 3.dp
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
    // Titres « cinéma » : DM Serif Display (une seule graisse, >= SERIF_MIN_SP). Le reste reste en Manrope.
    private fun serif(size: Float, spacing: Float = 0f, line: Float? = null) = TextStyle(
        fontFamily = SerifDisplay, fontWeight = FontWeight.Normal, fontSize = size.sp, letterSpacing = spacing.sp,
        lineHeight = line?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
    )

    /** Titre d'une œuvre sans logo (fiche, héros) : grand, à empattements. */
    val Display: TextStyle = serif(46f, -0.4f, 50f)
    /** Titre de page (en-tête d'écran, volets des réglages). */
    val ScreenTitle: TextStyle = serif(38f, -0.2f)
    /** Titre de section ou de rangée. */
    val Section: TextStyle = serif(25f, 0.1f)
    /** Onglets de la barre du haut. */
    val Tab: TextStyle = serif(21f, 0.2f)
    /** Titre dans le lecteur (film, épisode, chaîne). */
    val PlayerTitle: TextStyle = serif(30f, -0.2f)
    /** Étiquette au-dessus d'un titre de rangée (« POUR VOUS », « GENRE ») : petites capitales espacées, couleur d'accent. */
    val Kicker: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelSmall.copy(
            color = SokoulColors.Accent, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp,
        )
    /** Ligne d'informations secondaires (année · durée · genre). */
    val Meta: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge.copy(color = SokoulColors.TextSecondary, fontWeight = FontWeight.Medium)
    /** Petite étiquette en capitales (EN CE MOMENT, ENSUITE…). */
    val Overline: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMedium.copy(
            color = SokoulColors.TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        )
    /** Libellé de bouton. */
    val Button: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    /** Badge (4K, HDR, VF, EN DIRECT…) : jamais sous 12 sp, lisible à 3 m. */
    val Badge: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    /** Grand numéro de chaîne (saisie au pavé, bandeau du direct). Chiffres à chasse fixe. */
    val ChannelNumber: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, letterSpacing = 1.sp, fontFeatureSettings = "tnum",
        )
    /** Numéro de chaîne moyen (bandeau de zapping, barre du direct). */
    val ChannelNumberMedium: TextStyle
        @Composable @ReadOnlyComposable get() = ChannelNumber.copy(fontSize = 34.sp)
    /** Badge mis en avant (pastille de plateforme dans un titre de rangée). */
    val BadgeLarge: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
    /** Étiquette dorée des sagas (« SAGA · 6 FILMS ») : Kicker en or. */
    val SagaKicker: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMedium.copy(color = SokoulColors.Gold, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    /** Libellé de la barre de navigation (téléphone, rail). */
    val NavLabel: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMedium
    /** Touche du clavier à l'écran. */
    val Key: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold)
    /** Texte saisi dans la grande barre de recherche. */
    val SearchQuery: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    /** Nom d'une plateforme en tuile (Netflix, Disney+…). */
    val PlatformName: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
    /** Initiales d'une chaîne sans logo. */
    val Initials: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    /** Petit numéro de chaîne dans les listes. */
    val ChannelNumberSmall: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
}

/** Paliers de taille des titres dessinés (logos de titre sans image) : un nom par usage. */
object TitleSize {
    /** Carte paysage en mode dense. */
    val Dense = 14.sp
    /** Révélation dans une affiche focalisée. */
    val Card = 20.sp
    /** Carte paysage, tuile de saga. */
    val Landscape = 22.sp
    /** Affiche sans image. */
    val Poster = 24.sp
    /** Carte d'enregistrement. */
    val Recording = 26.sp
    /** Titre de la fiche sur téléphone. */
    val DetailCompact = 30.sp
    /** Bandeau d'une grille, titre dans le lecteur. */
    val Banner = 34.sp
    /** En-tête d'une page saga. */
    val Saga = 44.sp
    /** Titre de la fiche sur TV. */
    val Detail = 50.sp
}
