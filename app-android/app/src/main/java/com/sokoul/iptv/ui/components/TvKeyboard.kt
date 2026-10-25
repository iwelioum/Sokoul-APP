package com.sokoul.iptv.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type

/** Une action de saisie, qu'elle vienne d'une touche à l'écran, de la télécommande ou d'un clavier. */
sealed interface KeyInput {
    data class Char(val c: kotlin.Char) : KeyInput
    data object Space : KeyInput
    data object Delete : KeyInput
    data object Clear : KeyInput
}

/** Saisie pure (testée) : longueur bornée, pas d'espace en tête ni de double espace. */
fun applyKey(query: String, input: KeyInput, max: Int = 100): String = when (input) {
    is KeyInput.Char -> if (query.length >= max) query else query + input.c
    KeyInput.Space -> if (query.isEmpty() || query.endsWith(' ') || query.length >= max) query else "$query "
    KeyInput.Delete -> query.dropLast(1)
    KeyInput.Clear -> ""
}

/**
 * Raccourcis de la télécommande sur le clavier : Lecture/Pause = espace, Retour rapide = effacer ;
 * un vrai clavier (ou une télécommande à touches) tape directement.
 */
fun remoteKeyInput(e: KeyEvent): KeyInput? {
    if (e.type != KeyEventType.KeyDown) return null
    return when (e.key) {
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> KeyInput.Space
        Key.MediaRewind, Key.Backspace -> KeyInput.Delete
        else -> e.utf16CodePoint.takeIf { it > 0 }?.toChar()?.takeIf { it.isLetterOrDigit() }?.let { KeyInput.Char(it.lowercaseChar()) }
    }
}

private val Letters = ('a'..'z') + ('0'..'9')
private const val Columns = 7
private val KeySize = 44.dp
private val Gap = 6.dp

/** Largeur de [n] touches côte à côte. */
private fun keys(n: Int) = KeySize * n + Gap * (n - 1)

/**
 * Clavier de recherche pour la télécommande : champ en tête, 3 suggestions de complétion dès 2 lettres,
 * grille A–Z puis 0–9, puis Espace, Effacer et le micro (si la box a la reconnaissance vocale).
 * ▶ depuis la dernière colonne passe naturellement aux résultats, à droite.
 */
@Composable
fun TvKeyboard(
    query: String,
    onQuery: (String) -> Unit,
    suggestions: List<String>,
    placeholder: String,
    modifier: Modifier = Modifier,
    firstKey: FocusRequester = remember { FocusRequester() },
) {
    val voice = rememberVoiceInput { onQuery(it) }
    fun input(k: KeyInput) = onQuery(applyKey(query, k))
    Column(
        modifier.width(keys(Columns)).onPreviewKeyEvent { e ->
            val k = remoteKeyInput(e) ?: return@onPreviewKeyEvent false
            input(k); true
        },
    ) {
        // champ : la saisie en cours, ou ce qu'on peut chercher ici
        Row(
            Modifier.fillMaxWidth().background(SokoulColors.SurfaceHigh, Radius.Card).padding(horizontal = Space.m, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(22.dp), tint = SokoulColors.Accent)
            Text(
                query.ifEmpty { placeholder }, Modifier.padding(start = Space.s).weight(1f),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (query.isEmpty()) SokoulColors.TextMuted else SokoulColors.TextPrimary,
            )
        }
        // suggestions de complétion (place réservée : la grille ne saute pas quand elles arrivent)
        Column(Modifier.fillMaxWidth().padding(vertical = Space.s).height(SuggestionsHeight), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            suggestions.take(3).forEach { s ->
                FocusSurface(onClick = { onQuery(s) }, Modifier.fillMaxWidth(), style = FocusStyle.Row) { focused ->
                    Text(
                        s, Modifier.padding(horizontal = Space.m, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        // 7 colonnes : A–Z et 0–8 sur 5 lignes, puis 9, Espace, Effacer (appui long : tout) et Dicter
        Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
            val rows = Letters.chunked(Columns)
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    row.forEachIndexed { c, ch ->
                        KeyButton(Modifier.size(KeySize).then(if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier), onClick = { input(KeyInput.Char(ch)) }) { focused ->
                            Text(ch.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = onChip(focused))
                        }
                    }
                    if (r == rows.lastIndex) {
                        val free = Columns - row.size
                        val span = if (voice != null) free / 3 else free / 2
                        KeyButton(Modifier.size(keys(span), KeySize), onClick = { input(KeyInput.Space) }) { f -> KeyIcon(Icons.Default.SpaceBar, "Espace", f) }
                        KeyButton(Modifier.size(keys(free - span * (if (voice != null) 2 else 1)), KeySize), onClick = { input(KeyInput.Delete) }, onLongClick = { input(KeyInput.Clear) }) { f ->
                            KeyIcon(Icons.AutoMirrored.Filled.Backspace, "Effacer", f)
                        }
                        if (voice != null) KeyButton(Modifier.size(keys(span), KeySize), onClick = voice) { f -> KeyIcon(Icons.Default.Mic, "Dicter", f) }
                    }
                }
            }
        }
        Text("Lecture : espace  ·  Retour rapide : effacer", Modifier.padding(top = Space.s), style = Type.Meta, color = SokoulColors.TextMuted)
    }
}

private val SuggestionsHeight = 28.dp * 3 + 4.dp * 2

@Composable
private fun KeyButton(modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable (Boolean) -> Unit) {
    FocusSurface(onClick, modifier, style = FocusStyle.Chip, shape = Radius.Card, color = SokoulColors.SurfaceHigh, onLongClick = onLongClick) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content(focused) }
    }
}

@Composable
private fun KeyIcon(icon: ImageVector, label: String, focused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = onChip(focused))
        Text(label, Modifier.padding(start = Space.xs), style = MaterialTheme.typography.labelMedium, color = onChip(focused))
    }
}

/** Dictée par la reconnaissance vocale du système ; null si la box n'en a pas. */
@Composable
private fun rememberVoiceInput(onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites un titre ou une chaîne")
    }
    val available = remember { intent.resolveActivity(context.packageManager) != null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { onText(it.trim().take(100)) }
        }
    }
    return if (available) ({ runCatching { launcher.launch(intent) } }) else null
}
