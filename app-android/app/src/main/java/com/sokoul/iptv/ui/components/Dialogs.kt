package com.sokoul.iptv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type

/**
 * Où s'ouvre une fenêtre :
 * - [Center] : boîte centrée (confirmation, saisie, code) ;
 * - [Side] : sur TV, panneau à droite sur toute la hauteur, comme les tiroirs du lecteur (menus d'options,
 *   listes) — la page reste visible à gauche. Sur téléphone, il devient une boîte centrée.
 */
enum class DialogPlacement { Center, Side }

/** Largeur du panneau latéral (menus « … », groupes, versions). */
val SidePanelWidth = 460.dp

/** Le seul cadre de fenêtre de l'app : mêmes fonds, arrondis, marges et entrée animée partout. */
@Composable
fun SokoulDialog(
    onDismiss: () -> Unit,
    placement: DialogPlacement = DialogPlacement.Center,
    maxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val side = placement == DialogPlacement.Side && LocalLayout.current.isTv
    val reduced = LocalReducedMotion.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shown = remember { Animatable(if (reduced) 1f else 0f) }
        LaunchedEffect(Unit) { shown.animateTo(1f, Motion.enter()) }
        if (side) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(
                    Modifier.fillMaxHeight().width(SidePanelWidth)
                        .graphicsLayer { alpha = shown.value; translationX = (1f - shown.value) * 48.dp.toPx() }
                        .background(SokoulColors.Panel)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(start = Space.l, end = Space.l, top = 48.dp, bottom = Space.l),
                    content = content,
                )
            }
        } else {
            // tactile : largeur bornée (sinon la fenêtre occupe 92 % d'une tablette) ; TV inchangée
            val touch = LocalLayout.current.isTouch
            Column(
                (if (touch) Modifier.padding(horizontal = 16.dp).widthIn(max = maxWidth).fillMaxWidth() else Modifier.fillMaxWidth(0.92f).widthIn(max = maxWidth))
                    .graphicsLayer { alpha = shown.value; val s = 0.96f + 0.04f * shown.value; scaleX = s; scaleY = s }
                    .clip(Radius.Panel).background(SokoulColors.SurfaceHigh).padding(Space.l),
                content = content,
            )
        }
    }
}

/** Titre d'une fenêtre, avec une ligne d'explication facultative. */
@Composable
fun DialogHeader(title: String, subtitle: String? = null) {
    Text(title, style = Type.Section, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (subtitle != null) Text(subtitle, Modifier.padding(top = Space.xs), style = Type.Meta, maxLines = 3, overflow = TextOverflow.Ellipsis)
}
