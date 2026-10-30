package com.sokoul.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.Screen

/** Mot-symbole « Sokoul » : DM Serif et point d'accent. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("Sokoul")
            withStyle(SpanStyle(color = SokoulColors.Accent)) { append(".") }
        },
        modifier, style = Type.Section.copy(fontSize = Type.Section.fontSize * 1.15f),
    )
}

/**
 * Barre du haut : translucide au-dessus du héros, opaque quand la page a défilé ([solid]). Onglets avec liseré
 * d'accent sous l'onglet actif ; retour quand un écran est poussé ([onBack]).
 */
@Composable
fun TopNav(current: Screen, solid: Boolean, onSelect: (Screen) -> Unit, onBack: (() -> Unit)?) {
    val bg by animateColorAsState(if (solid) SokoulColors.Background else Color.Transparent, tween(220))
    Box(
        Modifier.fillMaxWidth().height(76.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (solid) 0f else 0.65f), Color.Transparent)))
            .background(bg),
    ) {
        if (solid) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(Edge.Hairline).background(SokoulColors.Hairline))
        // retour dans la marge : le mot-symbole et les onglets ne bougent jamais
        if (onBack != null) RoundIconButton(
            Icons.AutoMirrored.Filled.ArrowBack, "Retour", onBack, Modifier.align(Alignment.CenterStart).padding(start = 10.dp), size = 36.dp,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.Gutter).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark()
            Spacer(Modifier.width(44.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Screen.HOME, Screen.MOVIES, Screen.SERIES, Screen.LIVE, Screen.GUIDE).forEach { s ->
                    NavTab(s.label, s == current) { onSelect(s) }
                }
            }
            Spacer(Modifier.weight(1f))
            NavIcon(Icons.Filled.Search, "Recherche (/)", current == Screen.SEARCH) { onSelect(Screen.SEARCH) }
            Spacer(Modifier.width(10.dp))
            NavIcon(Icons.Outlined.Settings, "Réglages", current == Screen.SETTINGS) { onSelect(Screen.SETTINGS) }
        }
    }
}

/** Bouton rond de la barre, avec le liseré d'accent quand son écran est ouvert. */
@Composable
private fun NavIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundIconButton(icon, description, onClick, size = 40.dp)
        Box(Modifier.padding(top = 4.dp).height(Edge.Accent).width(if (selected) 22.dp else 0.dp).background(SokoulColors.Accent, Radius.Tiny))
    }
}

@Composable
internal fun NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val h = rememberHover()
    val color by animateColorAsState(
        when { selected -> SokoulColors.TextPrimary; h.active -> SokoulColors.TextPrimary.copy(alpha = 0.9f); else -> SokoulColors.TextSecondary }, tween(140),
    )
    Column(Modifier.interactive(h, onClick = onClick).focusRing(h), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, Modifier.padding(vertical = 6.dp), style = Type.Nav.copy(color = color))
        Box(
            Modifier.height(Edge.Accent).width(if (selected) 22.dp else 0.dp)
                .background(SokoulColors.Accent, Radius.Tiny),
        )
    }
}
