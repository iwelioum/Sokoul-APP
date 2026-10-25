package com.sokoul.iptv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors

private fun isSelectKey(code: Int) = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
    code == AndroidKeyEvent.KEYCODE_ENTER || code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
    code == AndroidKeyEvent.KEYCODE_BUTTON_A

/**
 * Clic compatible télécommande et tactile :
 * - OK = clic, OK maintenu ou touche Menu = appui long (actions contextuelles) ;
 * - toucher / appui long au doigt sur téléphone.
 */
fun Modifier.dpadClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier = composed {
    val press = remember { PressState() }
    val currentClick by rememberUpdatedState(onClick)
    val currentLong by rememberUpdatedState(onLongClick)
    Modifier
        .onPreviewKeyEvent { e ->
            if (!enabled) return@onPreviewKeyEvent false
            val code = e.nativeKeyEvent.keyCode
            val long = currentLong
            if (code == AndroidKeyEvent.KEYCODE_MENU && long != null) {
                if (e.type == KeyEventType.KeyUp) long()
                return@onPreviewKeyEvent true
            }
            if (!isSelectKey(code)) return@onPreviewKeyEvent false
            when (e.type) {
                KeyEventType.KeyDown -> {
                    if (e.nativeKeyEvent.repeatCount == 0) {
                        press.longFired = false; press.down = true
                    } else if (!press.longFired && long != null &&
                        (e.nativeKeyEvent.repeatCount >= 8 || e.nativeKeyEvent.isLongPress)) {
                        press.longFired = true
                        long()
                    }
                }
                KeyEventType.KeyUp -> {
                    if (press.down && !press.longFired) currentClick()
                    press.down = false
                }
            }
            true
        }
        .combinedClickable(
            interactionSource = null,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

private class PressState {
    var down = false
    var longFired = false
}

/**
 * Les trois seuls effets de focus de l'app (un même geste = un même retour visuel partout) :
 * - [Card] : affiches, vignettes, tuiles — agrandissement léger + liseré blanc ;
 * - [Row] : lignes de liste (chaînes, réglages, épisodes, guide) — fond éclairci + barre d'accent à gauche ;
 * - [Chip] : onglets, pastilles, boutons — fond blanc, texte sombre (le contenu lit `focused`).
 */
enum class FocusStyle(val scale: Float, val border: Dp, val accentBar: Boolean) {
    Card(1.06f, 2.dp, false),
    Row(1f, 0.dp, true),
    Chip(1.05f, 0.dp, false),
}

/**
 * Surface focalisable, dessinée selon un [FocusStyle].
 * Transition douce (ressort pour l'échelle, fondu pour la couleur et le liseré) calculée
 * à l'étape de dessin : aucune recomposition pendant l'animation.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FocusStyle = FocusStyle.Card,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = if (style == FocusStyle.Chip) Radius.Pill else Radius.Card,
    color: Color = if (style == FocusStyle.Card) SokoulColors.SurfaceHigh else Color.Transparent,
    focusedColor: Color = when (style) {
        FocusStyle.Card -> color
        FocusStyle.Row -> SokoulColors.SurfaceHigher
        FocusStyle.Chip -> SokoulColors.Focus
    },
    onFocus: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val progress = animateFloatAsState(
        if (focused) 1f else 0f,
        if (reduced) snap() else Motion.focusSpring(),
        label = "focus",
    )
    val bg by rememberUpdatedState(color)
    val fbg by rememberUpdatedState(focusedColor)
    Box(
        modifier
            .graphicsLayer {
                if (style.scale != 1f) {
                    val s = 1f + (style.scale - 1f) * progress.value
                    scaleX = s; scaleY = s
                }
            }
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus?.invoke()
                focused = it.isFocused
            }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val bar = FocusBarWidth.toPx()
                onDrawBehind {
                    val p = progress.value.coerceIn(0f, 1f)
                    drawOutline(outline, androidx.compose.ui.graphics.lerp(bg, fbg, p))
                    if (p > 0.01f) {
                        if (style.accentBar) drawRect(
                            SokoulColors.Accent.copy(alpha = p),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.2f),
                            size = androidx.compose.ui.geometry.Size(bar, size.height * 0.6f),
                        )
                    }
                }
            }
            .clip(shape)
            .then(if (style.border.value > 0f) Modifier.focusRing(shape, style.border) { progress.value } else Modifier)
            .dpadClickable(onClick, onLongClick)
    ) {
        content(focused)
    }
}

private val FocusBarWidth = 3.dp

/**
 * Anneau de focus des cartes, dessiné PAR-DESSUS le contenu (l'image ne le recouvre pas) :
 * contour en dégradé blanc → violet à 135° et filet sombre intérieur de 1 dp, pour rester
 * visible même sur une affiche à bord blanc.
 */
private fun Modifier.focusRing(shape: Shape, width: Dp, progress: () -> Float): Modifier = drawWithCache {
    val w = width.toPx()
    val inner = 1.dp.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(SokoulColors.Focus, SokoulColors.AccentEnd.copy(alpha = 0.85f)),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
    )
    onDrawWithContent {
        drawContent()
        val p = progress().coerceIn(0f, 1f)
        if (p > 0.01f) {
            // le contour est centré sur le bord : moitié intérieure visible sur l'image
            drawOutline(outline, SokoulColors.black(0.6f), alpha = p, style = Stroke(w + inner * 2))
            drawOutline(outline, brush, alpha = p, style = Stroke(w))
        }
    }
}

/**
 * Met l'élément focalisé (ou qui contient le focus) au premier plan parmi ses voisins :
 * une carte agrandie n'est jamais recouverte par la suivante. À poser sur la racine d'un
 * élément de rangée ou de grille.
 */
fun Modifier.focusOnTop(): Modifier = composed {
    var inside by remember { mutableStateOf(false) }
    this.onFocusChanged { inside = it.hasFocus }.zIndex(if (inside) 1f else 0f)
}

/** Couleur du texte et des icônes posés sur une surface [FocusStyle.Chip]. */
fun onChip(focused: Boolean, enabled: Boolean = true): Color = when {
    !enabled -> SokoulColors.TextMuted
    focused -> SokoulColors.OnLight
    else -> SokoulColors.TextPrimary
}

/** Mémorise un MutableInteractionSource (utile pour les champs texte). */
@Composable
fun rememberInteraction() = remember { MutableInteractionSource() }

