package com.sokoul.iptv.ui.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.sokoul.iptv.ui.theme.Edge
import com.sokoul.iptv.ui.theme.Radius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import com.sokoul.iptv.ui.theme.SokoulColors

/**
 * Clic de l'app tablette, version PC : clic souris (clic droit = appui long, menu contextuel), Entrée ou Espace
 * au clavier sur l'élément focalisé (Tab), curseur main au survol.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.dpadClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val currentClick by rememberUpdatedState(onClick)
    val currentLong by rememberUpdatedState(onLongClick)
    Modifier
        .onPreviewKeyEvent { e ->
            if (!enabled || e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
            if (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Spacebar) { currentClick(); true } else false
        }
        .then(
            if (onLongClick != null) Modifier.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press) { ev ->
                if (enabled && ev.button == androidx.compose.ui.input.pointer.PointerButton.Secondary) currentLong?.invoke()
            } else Modifier
        )
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * Les trois seuls effets de focus de l'app (un même geste = un même retour visuel partout) :
 * - [Card] : affiches, vignettes, tuiles — agrandissement net, soulèvement, contour blanc franc, halo et liseré d'accent ;
 * - [Row] : lignes de liste (chaînes, réglages, épisodes, guide) — fond éclairci + barre d'accent à gauche ;
 * - [Chip] : onglets, pastilles, boutons — fond blanc, texte sombre (le contenu lit `focused`).
 */
enum class FocusStyle(val scale: Float, val border: Dp, val accentBar: Boolean, val lift: Dp) {
    Card(1.12f, Edge.Focus, false, 4.dp),
    // grandes tuiles 16:9 (sagas) : même dessin que Card, agrandissement plus mesuré
    Tile(1.06f, Edge.Focus, false, 3.dp),
    Row(1f, 0.dp, true, 0.dp),
    Chip(1.05f, 0.dp, false, 0.dp),
    // touches du clavier : fond blanc au focus, agrandissement net
    Key(1.1f, 0.dp, false, 0.dp),
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
    color: Color = if (style == FocusStyle.Card || style == FocusStyle.Tile) SokoulColors.SurfaceHigh else Color.Transparent,
    focusedColor: Color = when (style) {
        FocusStyle.Card, FocusStyle.Tile -> color
        FocusStyle.Row -> SokoulColors.SurfaceHigher
        FocusStyle.Chip, FocusStyle.Key -> SokoulColors.Focus
    },
    onFocus: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    // PC : le survol de la souris vaut le focus de la télécommande (même agrandissement, contour, halo)
    var keyFocused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused = keyFocused || hovered
    val onFocusNow by rememberUpdatedState(onFocus)
    androidx.compose.runtime.LaunchedEffect(hovered) { if (hovered && !keyFocused) onFocusNow?.invoke() }
    val reduced = LocalReducedMotion.current
    val progress = animateFloatAsState(
        if (focused) 1f else 0f,
        if (reduced) snap() else Motion.focusSpring(),
        label = "focus",
    )
    // toucher (tablette, téléphone) : pas de focus visible, donc un retour d'appui — léger enfoncement et
    // couleur de focus à moitié, le temps du doigt posé
    val touch = !LocalLayout.current.isTv
    val pressed by interactions.collectIsPressedAsState()
    val press = animateFloatAsState(
        if (touch && pressed) 1f else 0f,
        if (reduced) snap() else Motion.focusSpring(),
        label = "press",
    )
    val bg by rememberUpdatedState(color)
    val fbg by rememberUpdatedState(focusedColor)
    val card = style == FocusStyle.Card || style == FocusStyle.Tile
    Box(
        modifier
            .graphicsLayer {
                val p = progress.value
                val s = (if (style.scale != 1f) 1f + (style.scale - 1f) * p else 1f) * (1f - PressDepth * press.value)
                if (s != 1f) { scaleX = s; scaleY = s }
                if (style.lift.value > 0f) translationY = -style.lift.toPx() * p
            }
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus?.invoke()
                keyFocused = it.isFocused
            }
            .hoverable(interactions)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val bar = FocusBarWidth.toPx()
                // halo et ombre (cartes) : contours élargis autour de la forme, hors du découpage
                val glow1 = 3.dp.toPx(); val glow2 = 7.dp.toPx()
                val halo1 = if (card) expanded(shape, glow1) else null
                val halo2 = if (card) expanded(shape, glow2) else null
                val shadowH = 18.dp.toPx()
                val shadow = Brush.verticalGradient(
                    0f to SokoulColors.black(0.55f), 1f to Color.Transparent,
                    startY = size.height - 2f, endY = size.height + shadowH,
                )
                onDrawBehind {
                    val p = progress.value.coerceIn(0f, 1f)
                    if (card && p > 0.01f) {
                        drawRect(shadow, topLeft = Offset(size.width * 0.06f, size.height - 2f), size = Size(size.width * 0.88f, shadowH + 2f), alpha = p)
                        halo2?.let { o -> translate(-glow2, -glow2) { drawOutline(o, SokoulColors.Accent, alpha = 0.10f * p) } }
                        halo1?.let { o -> translate(-glow1, -glow1) { drawOutline(o, SokoulColors.AccentEnd, alpha = 0.22f * p) } }
                    }
                    val fill = androidx.compose.ui.graphics.lerp(bg, fbg, maxOf(p, press.value * 0.5f))
                    if (fill.alpha > 0f) drawOutline(outline, fill)
                    if (p > 0.01f && style.accentBar) drawRect(
                        SokoulColors.Accent.copy(alpha = p),
                        topLeft = Offset(0f, size.height * 0.2f),
                        size = Size(bar, size.height * 0.6f),
                    )
                }
            }
            .clip(shape)
            // liseré d'accent dans le bas de la carte (même signe que le soulignement de l'onglet actif) :
            // dessiné à l'intérieur, il ne déborde jamais sur le titre placé sous la carte
            .then(if (card) Modifier.drawWithContent {
                drawContent()
                val p = progress.value.coerceIn(0f, 1f)
                if (p > 0.01f) {
                    val h = Edge.Accent.toPx(); val inset = style.border.toPx() + 3.dp.toPx()
                    val w = size.width * 0.4f * p.coerceAtLeast(0.2f)
                    drawRect(SokoulColors.Accent, topLeft = Offset((size.width - w) / 2f, size.height - inset - h), size = Size(w, h), alpha = p)
                }
            } else Modifier)
            .then(if (card) Modifier.focusRing(shape, style.border) { progress.value } else Modifier)
            .dpadClickable(onClick, onLongClick, interactionSource = interactions)
    ) {
        content(focused)
    }
}

private val FocusBarWidth = 3.dp

/** Enfoncement d'une surface sous le doigt (4 %). */
private const val PressDepth = 0.04f

/** Contour de [shape] agrandi de [by] px de chaque côté (halo), à dessiner décalé de (-by, -by). */
private fun androidx.compose.ui.draw.CacheDrawScope.expanded(shape: Shape, by: Float) =
    shape.createOutline(Size(size.width + by * 2, size.height + by * 2), layoutDirection, this)

/**
 * Contours des cartes, dessinés PAR-DESSUS le contenu (l'image ne les recouvre pas) :
 * - au repos, un filet clair de 1 dp qui donne un bord net aux affiches sombres ;
 * - au focus, un contour blanc franc et un filet sombre intérieur (visible même sur une affiche à bord blanc).
 */
private fun Modifier.focusRing(shape: Shape, width: Dp, progress: () -> Float): Modifier = drawWithCache {
    val w = width.toPx()
    val inner = 1.dp.toPx()
    val hair = Edge.Hairline.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawWithContent {
        drawContent()
        val p = progress().coerceIn(0f, 1f)
        // les traits sont centrés sur le bord : seule leur moitié intérieure est visible (découpage)
        if (p < 0.99f) drawOutline(outline, SokoulColors.white(0.08f), alpha = 1f - p, style = Stroke(hair * 2))
        if (p > 0.01f) {
            drawOutline(outline, SokoulColors.black(0.65f), alpha = p, style = Stroke(w * 2 + inner * 2))
            drawOutline(outline, SokoulColors.Focus, alpha = p, style = Stroke(w * 2))
        }
    }
}

/**
 * Met l'élément focalisé (ou qui contient le focus) au premier plan parmi ses voisins :
 * une carte agrandie n'est jamais recouverte par la suivante. À poser sur la racine d'un
 * élément de rangée ou de grille.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.focusOnTop(): Modifier = composed {
    var inside by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    // PC : la carte survolée (agrandie) passe aussi devant ses voisines
    this.onFocusChanged { inside = it.hasFocus }
        .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { over = true }
        .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) { over = false }
        .zIndex(if (inside || over) 1f else 0f)
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

