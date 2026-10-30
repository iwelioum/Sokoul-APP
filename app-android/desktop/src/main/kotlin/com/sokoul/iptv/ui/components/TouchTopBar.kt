package com.sokoul.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors

/**
 * Tablette, pages plein écran (fiche, personne) : bouton Retour en haut à gauche et, quand on descend,
 * un voile sous la barre d'état pour que le texte ne passe pas sous l'heure et les icônes.
 * [scrimProvider] : opacité du voile (0 = en haut de la page).
 */
@Composable
fun TouchTopBar(onBack: () -> Unit, scrimProvider: () -> Float) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().graphicsLayer { alpha = scrimProvider() }
                .background(Brush.verticalGradient(0f to SokoulColors.Background, 0.72f to SokoulColors.Background, 1f to Color.Transparent))
                .statusBarsPadding().height(84.dp)
        )
        Box(
            Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp).size(48.dp).clip(Radius.Round).background(SokoulColors.Scrim)
                .dpadClickable(onBack).semantics { contentDescription = "Retour" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = SokoulColors.OnVideo) }
    }
}
