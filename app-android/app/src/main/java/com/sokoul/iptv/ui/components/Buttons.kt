package com.sokoul.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type

/**
 * Familles de boutons. Une seule règle de focus pour toutes : le bouton focalisé devient blanc, texte sombre.
 * - [Primary] : l'action principale (Lecture, Valider). Tactile : blanc au repos ; TV : verre fort au repos
 *   (sinon on croirait le focus posé dessus).
 * - [Glass] : action secondaire posée sur une image (héros, fiche, lecteur).
 * - [Secondary] : action secondaire sur fond uni (réglages, dialogues, formulaires).
 */
enum class ButtonKind { Primary, Glass, Secondary }

/** Le bouton de l'app : rectangle aux coins de carte, 48 dp de haut (44 sur téléphone). */
@Composable
fun SokoulButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: ButtonKind = ButtonKind.Secondary,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
) {
    val l = LocalLayout.current
    val solidRest = kind == ButtonKind.Primary && !l.isTv
    FocusSurface(
        style = FocusStyle.Chip,
        onClick = { if (enabled) onClick() },
        onFocus = onFocus,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = Radius.Card,
        color = buttonRest(kind, l.isTv),
        focusedColor = SokoulColors.Focus,
    ) { focused ->
        val c = buttonContent(focused || solidRest, enabled)
        Row(
            Modifier.height(if (l.isCompact) 44.dp else 48.dp).padding(horizontal = if (l.isCompact) 18.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(22.dp), tint = c)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = c, style = Type.Button, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bouton carré, icône seule (barres d'outils, lecteur) : mêmes couleurs que [SokoulButton]. */
@Composable
fun SokoulIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Glass,
    enabled: Boolean = true,
) {
    val l = LocalLayout.current
    FocusSurface(
        style = FocusStyle.Chip, onClick = { if (enabled) onClick() }, shape = Radius.Card,
        modifier = modifier.size(if (l.isCompact) 44.dp else 48.dp).semantics { contentDescription = label },
        color = buttonRest(kind, l.isTv), focusedColor = SokoulColors.Focus,
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = buttonContent(focused, enabled))
        }
    }
}

/** Fond au repos d'un bouton. */
fun buttonRest(kind: ButtonKind, tv: Boolean): Color = when (kind) {
    ButtonKind.Primary -> if (tv) SokoulColors.GlassHeavy else SokoulColors.Focus
    ButtonKind.Glass -> SokoulColors.GlassLight
    ButtonKind.Secondary -> SokoulColors.SurfaceHigher
}

/** Texte et icône d'un bouton : sombre sur blanc (focus ou bouton principal tactile), clair sinon. */
fun buttonContent(onWhite: Boolean, enabled: Boolean = true): Color = when {
    !enabled -> SokoulColors.TextMuted
    onWhite -> SokoulColors.OnLight
    else -> SokoulColors.OnVideo
}
