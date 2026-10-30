package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.Screen

/** Rail de navigation à gauche (même rôle que le rail tablette de l'app Android). */
@Composable
fun NavRail(current: Screen, onSelect: (Screen) -> Unit) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().background(SokoulColors.Surface).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(Brush.linearGradient(listOf(SokoulColors.Accent, SokoulColors.AccentEnd)), Radius.Card),
            contentAlignment = Alignment.Center,
        ) { Text("S", fontWeight = FontWeight.Black, fontSize = 22.sp) }
        Spacer(Modifier.height(18.dp))
        RailItem("Direct", Icons.Default.LiveTv, current == Screen.LIVE) { onSelect(Screen.LIVE) }
        RailItem("Guide TV", Icons.Default.CalendarViewWeek, current == Screen.GUIDE) { onSelect(Screen.GUIDE) }
        Spacer(Modifier.weight(1f))
        RailItem("Réglages", Icons.Default.Settings, current == Screen.SETTINGS) { onSelect(Screen.SETTINGS) }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(
        Modifier.width(80.dp).background(if (selected) SokoulColors.AccentSoft else if (hovered) SokoulColors.SurfaceHigh else SokoulColors.Surface, Radius.Card)
            .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (selected) SokoulColors.Accent else SokoulColors.TextSecondary)
        Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = if (selected) SokoulColors.TextPrimary else SokoulColors.TextSecondary)
    }
}

/** Bouton plein (action principale) ou discret. */
@Composable
fun SokoulButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, icon: ImageVector? = null, enabled: Boolean = true) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val bg = when {
        !enabled -> SokoulColors.SurfaceHigh
        primary -> if (hovered) SokoulColors.AccentEnd else SokoulColors.Accent
        else -> if (hovered) SokoulColors.SurfaceHigher else SokoulColors.SurfaceHigh
    }
    Row(
        modifier.background(bg, Radius.Card).hoverable(hover).pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp).padding(end = 0.dp), tint = SokoulColors.TextPrimary); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (enabled) SokoulColors.TextPrimary else SokoulColors.TextMuted)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextSecondary) }
    }
}

/** Saisie du code parental (4 chiffres), vérifié par [check]. */
@Composable
fun PinDialog(check: (String) -> Boolean, onDismiss: () -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Code parental") },
        text = {
            Column {
                Text("Ce groupe est verrouillé sur la tablette.", color = SokoulColors.TextSecondary)
                OutlinedTextField(
                    pin, { v -> pin = v.filter(Char::isDigit).take(4); error = null }, Modifier.padding(top = 12.dp).fillMaxWidth(),
                    singleLine = true, label = { Text("Code à 4 chiffres") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                )
            }
        },
        confirmButton = { TextButton({ if (check(pin)) onOk() else error = "Code incorrect" }) { Text("Valider") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
        containerColor = SokoulColors.SurfaceHigh,
    )
}

fun clock(t: Long): String = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    .let { "%02d:%02d".format(it.hour, it.minute) }

fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (d) {
        today -> "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    }
}
