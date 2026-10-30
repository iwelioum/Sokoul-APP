package com.sokoul.iptv.ui.components

import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import com.sokoul.iptv.ui.theme.LocalIsTv
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun NetImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    /** Taille affichée connue (cartes) : image décodée directement à cette taille, sans attendre la mise en page. */
    size: androidx.compose.ui.unit.DpSize? = null,
    fallback: @Composable () -> Unit = {},
) {
    if (url.isNullOrBlank()) {
        Box(modifier, contentAlignment = alignment) { fallback() }
        return
    }
    // PC : chargeur d'images du bureau (cache mémoire et disque), même repli que Coil sur la tablette
    com.sokoul.desktop.ui.NetImage(url, contentDescription, modifier, contentScale, alignment) {
        Box(Modifier.fillMaxSize(), contentAlignment = alignment) { fallback() }
    }
}

/** Logo de chaîne avec repli sur les initiales. */
@Composable
fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(Radius.Card).background(SokoulColors.SurfaceHigher), contentAlignment = Alignment.Center) {
        NetImage(url, name, Modifier.fillMaxSize().padding(4.dp), ContentScale.Fit) {
            Text(
                initials(name), color = SokoulColors.TextSecondary, style = com.sokoul.iptv.ui.theme.Type.Initials,
                maxLines = 1,
            )
        }
    }
}

fun initials(name: String): String {
    val words = name.replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(2).joinToString("") { it.take(1) }.uppercase()
    }
}



@Composable
fun Tag(text: String, color: Color = SokoulColors.SurfaceHigher, textColor: Color = SokoulColors.TextPrimary) {
    Text(
        text, Modifier.background(color, Radius.Tiny).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium, color = textColor,
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // icône dans un halo teinté : même rendu pour les écrans vides et les erreurs, partout
        Box(
            Modifier.size(96.dp).background(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(SokoulColors.AccentSoft, androidx.compose.ui.graphics.Color.Transparent)),
                Radius.Round,
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(44.dp), tint = SokoulColors.TextSecondary) }
        Text(title, Modifier.padding(top = 12.dp), style = com.sokoul.iptv.ui.theme.Type.Section, textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, Modifier.padding(top = 8.dp).widthIn(max = 520.dp), color = SokoulColors.TextSecondary, textAlign = TextAlign.Center)
        }
        if (action != null) Box(Modifier.padding(top = 24.dp)) { action() }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoEdit: Boolean = false,
) {
    // Sur TV, un champ qui reçoit le focus ouvre le clavier et piège la télécommande :
    // le champ reste en lecture seule tant que l'utilisateur n'appuie pas sur OK.
    val isTv = LocalIsTv.current
    var editing by remember { mutableStateOf(!isTv || autoEdit) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val accent = focused || (editing && isTv)
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = !editing,
        modifier = modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused; if (isTv && !autoEdit && !it.isFocused) editing = false }
            .onPreviewKeyEvent { e ->
                if (!isTv) return@onPreviewKeyEvent false
                // Les champs Compose consomment les flèches (déplacement du curseur) : on rend la main au focus.
                val dir = when (e.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> if (editing) null else FocusDirection.Left
                    Key.DirectionRight -> if (editing) null else FocusDirection.Right
                    else -> null
                }
                if (dir != null && (singleLine || dir == FocusDirection.Left || dir == FocusDirection.Right)) {
                    if (e.type == KeyEventType.KeyDown) focusManager.moveFocus(dir)
                    return@onPreviewKeyEvent true
                }
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (editing || !ok) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) { editing = true; keyboard?.show() }
                true
            },
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = SokoulColors.TextPrimary),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(SokoulColors.Accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
        // champ maison : fond plein, libellé au-dessus de la valeur, trait d'accent en bas au focus
        decorationBox = { inner ->
            Column(
                Modifier.fillMaxWidth().clip(Radius.Card)
                    .background(if (accent) SokoulColors.SurfaceHigher else SokoulColors.SurfaceHigh)
                    .drawBehind {
                        val h = (if (accent) 2.dp else 1.dp).toPx()
                        drawRect(if (accent) SokoulColors.Accent else SokoulColors.Divider, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
                    }
                    .padding(horizontal = Space.m, vertical = 10.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (accent) SokoulColors.Accent else SokoulColors.TextSecondary, maxLines = 1)
                Box(Modifier.padding(top = 2.dp).fillMaxWidth()) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                if (isTv && !editing && focused) Text("OK pour modifier", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted)
            }
        },
    )
}

/** @param enabled faux : ligne grisée (on peut la lire, pas la choisir) ; [hint] explique pourquoi. */
data class MenuOption(
    val label: String, val icon: ImageVector? = null, val selected: Boolean = false,
    val enabled: Boolean = true, val hint: String? = null, val onClick: () -> Unit,
)

/** Menu contextuel modal, navigable à la télécommande. */
@Composable
fun OptionsDialog(title: String, options: List<MenuOption>, onDismiss: () -> Unit, subtitle: String? = null) {
    // TV : panneau à droite (la page reste visible) ; téléphone : boîte centrée
    SokoulDialog(onDismiss, DialogPlacement.Side, maxWidth = 520.dp) {
        val first = remember { FocusRequester() }
        run {
            DialogHeader(title, subtitle)
            Spacer(Modifier.height(Space.m))
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(options) { i, o ->
                    FocusSurface(style = FocusStyle.Row,
                        onClick = { if (o.enabled) { o.onClick(); onDismiss() } },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        color = if (o.selected) SokoulColors.AccentTint else Color.Transparent,
                    ) { _ ->
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (o.icon != null) {
                                Icon(o.icon, null, Modifier.size(22.dp), tint = if (o.selected) SokoulColors.Accent else SokoulColors.TextSecondary)
                                Spacer(Modifier.width(14.dp))
                            }
                            Column {
                                Text(
                                    o.label, style = MaterialTheme.typography.bodyLarge,
                                    color = when { !o.enabled -> SokoulColors.TextMuted; o.selected -> SokoulColors.Accent; else -> SokoulColors.TextPrimary },
                                )
                                o.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextMuted) }
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SokoulDialog(onDismiss) {
        val focus = remember { FocusRequester() }
        run {
            DialogHeader(title)
            Text(message, Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SokoulButton("Annuler", onDismiss, focusRequester = focus)
                SokoulButton(confirm, { onConfirm(); onDismiss() }, kind = ButtonKind.Primary)
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

/** Saisie d'un code PIN à 4 chiffres avec pavé numérique (télécommande ou tactile). */
@Composable
fun PinDialog(title: String, onResult: (String) -> Unit, onDismiss: () -> Unit, error: String? = null) {
    var pin by remember { mutableStateOf("") }
    // Code refusé : on vide la saisie pour permettre un nouvel essai sans effacer chiffre par chiffre
    LaunchedEffect(error) { if (error != null) pin = "" }
    SokoulDialog(onDismiss, maxWidth = 380.dp) {
        val first = remember { FocusRequester() }
        Column(
            Modifier.fillMaxWidth()
                // touches numériques de la télécommande (0-9) et retour arrière
                .onPreviewKeyEvent { e ->
                    // PC : chiffres du clavier (rangée ou pavé) et retour arrière
                    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val ch = (e.nativeKeyEvent as? java.awt.event.KeyEvent)?.keyChar ?: ' '
                    when {
                        ch in '0'..'9' -> {
                            if (pin.length < 4) {
                                pin += ch
                                if (pin.length == 4) onResult(pin)
                            }
                            true
                        }
                        e.key == androidx.compose.ui.input.key.Key.Backspace -> { pin = pin.dropLast(1); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, null, tint = SokoulColors.Accent)
            Text(title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(16.dp).clip(Radius.Round).background(if (i < pin.length) SokoulColors.Accent else SokoulColors.SurfaceHigher))
                }
            }
            if (error != null) Text(error, color = SokoulColors.Live, style = MaterialTheme.typography.bodyMedium)
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
            keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { colIndex, k ->
                        FocusSurface(style = FocusStyle.Chip,
                            onClick = {
                                when (k) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> if (pin.length == 4) onResult(pin)
                                    else -> if (pin.length < 4) {
                                        pin += k
                                        if (pin.length == 4) onResult(pin)
                                    }
                                }
                            },
                            modifier = Modifier.size(64.dp, 52.dp).then(if (rowIndex == 0 && colIndex == 0) Modifier.focusRequester(first) else Modifier),
                            color = SokoulColors.SurfaceHigher,
                        ) { focused ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(k, style = MaterialTheme.typography.titleLarge, color = onChip(focused)) }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

val BottomFade = Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.bg(0.9f), SokoulColors.Background))
val LeftFade = Brush.horizontalGradient(listOf(SokoulColors.Background, SokoulColors.bg(0.8f), Color.Transparent))
