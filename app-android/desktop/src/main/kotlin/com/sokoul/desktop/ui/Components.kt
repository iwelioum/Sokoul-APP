package com.sokoul.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- interactions ----------------------------------------------------------------------------------------

/** État de survol / focus d'un élément cliquable (curseur main, focus clavier par Tab). */
class Hover(val source: MutableInteractionSource, hovered: Boolean, focused: Boolean) {
    val hovered = hovered
    val focused = focused
    val active get() = hovered || focused
}

@Composable
fun rememberHover(): Hover {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    return Hover(source, hovered, focused)
}

/** Clic avec survol, focus clavier et curseur main ; sans effet d'ondulation Material. */
fun Modifier.interactive(h: Hover, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.hoverable(h.source, enabled)
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(interactionSource = h.source, indication = null, enabled = enabled, onClick = onClick)

/** Contour blanc quand l'élément a le focus clavier. */
fun Modifier.focusRing(h: Hover, shape: androidx.compose.ui.graphics.Shape = Radius.Card): Modifier =
    if (h.focused) this.border(Edge.Focus, SokoulColors.Focus, shape) else this

// --- boutons -------------------------------------------------------------------------------------------

enum class ButtonStyle { Primary, Accent, Glass, Ghost }

@Composable
fun SokoulButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Accent,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val h = rememberHover()
    val (bg, fg) = when {
        !enabled -> SokoulColors.SurfaceHigh to SokoulColors.TextMuted
        style == ButtonStyle.Primary -> (if (h.active) Color.White else Color(0xFFEDEDF2)) to SokoulColors.OnLight
        style == ButtonStyle.Accent -> (if (h.active) SokoulColors.AccentEnd else SokoulColors.Accent) to Color.White
        style == ButtonStyle.Glass -> (if (h.active) SokoulColors.GlassHeavy else SokoulColors.GlassLight) to SokoulColors.TextPrimary
        else -> (if (h.active) SokoulColors.SurfaceHigh else Color.Transparent) to SokoulColors.TextPrimary
    }
    val animBg by animateColorAsState(bg, tween(140))
    Row(
        modifier.background(animBg, Radius.Pill)
            .then(if (style == ButtonStyle.Ghost) Modifier.border(Edge.Hairline, SokoulColors.Divider, Radius.Pill) else Modifier)
            .focusRing(h, Radius.Pill)
            .interactive(h, enabled, onClick)
            .padding(horizontal = if (large) 26.dp else 18.dp, vertical = if (large) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, null, Modifier.size(if (large) 22.dp else 18.dp), tint = fg); Spacer(Modifier.width(10.dp)) }
        Text(text, style = Type.Button.copy(color = fg))
    }
}

/** Bouton rond à icône (verre) : retour, plein écran, flèches de rangée. */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, filled: Boolean = false) {
    val h = rememberHover()
    val bg by animateColorAsState(
        when { filled && h.active -> Color.White; filled -> Color(0xFFEDEDF2); h.active -> SokoulColors.GlassHeavy; else -> SokoulColors.GlassLight }, tween(140),
    )
    Box(
        modifier.size(size).background(bg, Radius.Round).focusRing(h, Radius.Round).interactive(h, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(size * 0.5f), tint = if (filled) SokoulColors.OnLight else SokoulColors.TextPrimary) }
}

/** Puce de filtre (genre, tri, saison). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val h = rememberHover()
    val bg by animateColorAsState(
        when { selected -> SokoulColors.TextPrimary; h.active -> SokoulColors.SurfaceHigher; else -> SokoulColors.SurfaceHigh }, tween(140),
    )
    Box(
        modifier.background(bg, Radius.Pill).focusRing(h, Radius.Pill).interactive(h, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = Type.Small.copy(color = if (selected) SokoulColors.OnLight else SokoulColors.TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
    }
}

/** Élément texte cliquable de menu ou d'onglet secondaire. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, color: Color = SokoulColors.Accent) {
    val h = rememberHover()
    Text(
        text, Modifier.interactive(h, onClick = onClick).padding(vertical = 4.dp),
        style = Type.Small.copy(color = if (h.active) SokoulColors.TextPrimary else color, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    )
}

// --- repères -------------------------------------------------------------------------------------------

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = SokoulColors.Accent) =
    Text(text.uppercase(), modifier, style = Type.Kicker.copy(color = color))

/** Repères d'une œuvre avec icônes (comme `MetaRow` de la tablette) : année, durée, note, saisons, âge, genres. */
@Composable
fun MetaRow(
    year: Int?,
    runtimeMin: Int? = null,
    rating: Double? = null,
    seasons: Int? = null,
    certification: String? = null,
    genres: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        rating?.let { MetaItem(Icons.Filled.Star, "%.1f".format(java.util.Locale.FRANCE, it), SokoulColors.Gold) }
        year?.let { MetaItem(Icons.Outlined.CalendarToday, it.toString()) }
        runtimeMin?.takeIf { it > 0 }?.let { MetaItem(Icons.Filled.Schedule, duration(it)) }
        seasons?.takeIf { it > 0 }?.let { MetaItem(Icons.Outlined.Tv, if (it == 1) "1 saison" else "$it saisons") }
        certification?.let { AgeBadge(it) }
        if (genres.isNotEmpty()) Text(genres.take(3).joinToString(" · "), style = Type.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String, tint: Color = SokoulColors.TextSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.width(5.dp))
        Text(text, style = Type.Meta.copy(color = if (tint == SokoulColors.Gold) SokoulColors.TextPrimary else SokoulColors.TextSecondary))
    }
}

fun duration(min: Int): String = if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)}" else "$min min"

/** Badge d'âge aux couleurs de la signalétique française (TP, 10, 12, 16, 18). */
@Composable
fun AgeBadge(cert: String) {
    val c = cert.trim().uppercase().removePrefix("-")
    val (label, color) = when {
        c == "U" || c == "TP" || c == "TOUS PUBLICS" || c == "0" || c == "ALL" -> "TP" to SokoulColors.AgeAll
        c.startsWith("10") -> "10" to SokoulColors.Age10
        c.startsWith("12") -> "12" to SokoulColors.Age12
        c.startsWith("16") -> "16" to SokoulColors.Age16
        c.startsWith("18") -> "18" to SokoulColors.Age18
        else -> c.take(3) to SokoulColors.SurfaceHigher
    }
    Box(
        Modifier.background(color, Radius.Tiny).border(Edge.Hairline, Color.White.copy(alpha = 0.35f), Radius.Tiny)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) { Text(if (label == "TP") label else "-$label", style = Type.Badge.copy(color = Color.White)) }
}

/** Badge plein (4K, HDR, VF, EN DIRECT…). */
@Composable
fun Badge(text: String, color: Color = SokoulColors.SurfaceHigher, textColor: Color = SokoulColors.TextPrimary, modifier: Modifier = Modifier) {
    Box(modifier.background(color, Radius.Tiny).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, style = Type.Badge.copy(color = textColor), maxLines = 1)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, kicker: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            kicker?.let { Kicker(it, Modifier.padding(bottom = 4.dp)) }
            Text(title, style = Type.Section)
        }
        trailing()
    }
}

// --- squelettes, panneaux ------------------------------------------------------------------------------

@Composable
fun Skeleton(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = Radius.Card) {
    val t = rememberInfiniteTransition()
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart))
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(SokoulColors.Skeleton, SokoulColors.SkeletonShine, SokoulColors.Skeleton),
                start = androidx.compose.ui.geometry.Offset(x * 600f, 0f), end = androidx.compose.ui.geometry.Offset(x * 600f + 600f, 300f),
            ),
            shape,
        ),
    )
}

/** Message centré d'un écran vide ou en erreur, avec une action. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Type.Section, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(message, Modifier.padding(top = 10.dp), style = Type.Body, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) SokoulButton(action, onAction, Modifier.padding(top = 22.dp))
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.background(SokoulColors.Surface, Radius.Panel).border(Edge.Hairline, SokoulColors.Hairline, Radius.Panel).padding(22.dp),
        content = content,
    )
}

/**
 * Fenêtre par-dessus l'écran (dans la fenêtre de l'app) : voile, panneau centré, Échap ou clic sur le voile ferme.
 */
@Composable
fun SokoulDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier, width: Dp = 560.dp, content: @Composable ColumnScope.() -> Unit) {
    val focus = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize().background(SokoulColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onDismiss(); true } else false }
            .focusRequester(focus).focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier.width(width).background(SokoulColors.SurfaceHigh, Radius.Panel).border(Edge.Hairline, SokoulColors.Divider, Radius.Panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(26.dp),
            content = content,
        )
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}


// --- saisie --------------------------------------------------------------------------------------------

@Composable
fun SokoulTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    password: Boolean = false,
    digits: Boolean = false,
    style: TextStyle = Type.Input,
    focusRequester: FocusRequester? = null,
    onSubmit: (() -> Unit)? = null,
) {
    val h = rememberHover()
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.background(SokoulColors.SurfaceHigh, Radius.Pill)
            .border(Edge.Hairline, if (focused) SokoulColors.Accent else if (h.hovered) SokoulColors.GlassHeavy else SokoulColors.Divider, Radius.Pill)
            .hoverable(h.source).pointerHoverIcon(PointerIcon.Text)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { Icon(it, null, Modifier.size(if (style.fontSize.value > 20f) 28.dp else 20.dp), tint = SokoulColors.TextSecondary); Spacer(Modifier.width(if (style.fontSize.value > 20f) 14.dp else 10.dp)) }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = style.copy(color = SokoulColors.TextMuted), maxLines = 1)
            BasicTextField(
                value, onValueChange,
                Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e -> if (onSubmit != null && e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { onSubmit(); true } else false },
                singleLine = true, textStyle = style, cursorBrush = SolidColor(SokoulColors.Accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (digits) KeyboardType.NumberPassword else KeyboardType.Text),
            )
        }
    }
}


@Composable
fun PinDialog(check: (String) -> Boolean, onDismiss: () -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    SokoulDialog(onDismiss, width = 420.dp) {
        Text("Code parental", style = Type.Section)
        Text("Ce groupe est verrouillé. Saisissez le code à 4 chiffres.", Modifier.padding(top = 6.dp, bottom = 18.dp), style = Type.Body)
        SokoulTextField(
            pin, { v -> pin = v.filter(Char::isDigit).take(4); error = null }, "Code", Modifier.fillMaxWidth(),
            password = true, digits = true, focusRequester = focus, onSubmit = { if (check(pin)) onOk() else error = "Code incorrect" },
        )
        error?.let { Text(it, Modifier.padding(top = 8.dp), style = Type.Small.copy(color = SokoulColors.Live)) }
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            SokoulButton("Annuler", onDismiss, style = ButtonStyle.Ghost)
            SokoulButton("Valider", { if (check(pin)) onOk() else error = "Code incorrect" })
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Fond dégradé de remplacement d'une image absente (même teinte que la tablette). */
@Composable
fun Placeholder(title: String?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier.background(Brush.verticalGradient(listOf(SokoulColors.PlaceholderTop, SokoulColors.PlaceholderBottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (title != null) Text(
            title, Modifier.padding(12.dp), style = Type.Section.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp), color = SokoulColors.TextSecondary),
            maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        content()
    }
}

fun clock(t: Long): String = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (d) {
        today -> "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        today.minusDays(1) -> "Hier"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    }
}

/** « 1:42:07 » ou « 42:07 ». */
fun timecode(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun gb(bytes: Long?): String? = bytes?.takeIf { it > 0 }?.let {
    if (it >= 1_000_000_000L) "%.1f Go".format(java.util.Locale.FRANCE, it / 1e9) else "${it / 1_000_000} Mo"
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
