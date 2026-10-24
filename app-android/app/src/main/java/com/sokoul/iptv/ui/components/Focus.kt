package com.sokoul.iptv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
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
                    } else if (!press.longFired && long != null && e.nativeKeyEvent.repeatCount >= 8) {
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
 * Surface focalisable : agrandissement + liseré blanc au focus, lisible à 3 mètres.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = SokoulColors.SurfaceHigh,
    focusedColor: Color = color,
    focusScale: Float = 1.06f,
    borderWidth: Dp = 2.5.dp,
    onFocus: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(140), label = "scale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus?.invoke()
                focused = it.isFocused
            }
            .clip(shape)
            .background(if (focused) focusedColor else color, shape)
            .then(if (focused) Modifier.border(borderWidth, SokoulColors.Focus, shape) else Modifier)
            .dpadClickable(onClick, onLongClick)
    ) {
        content(focused)
    }
}

/** Mémorise un MutableInteractionSource (utile pour les champs texte). */
@Composable
fun rememberInteraction() = remember { MutableInteractionSource() }

