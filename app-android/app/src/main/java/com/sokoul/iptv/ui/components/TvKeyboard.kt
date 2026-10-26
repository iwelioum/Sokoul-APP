package com.sokoul.iptv.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space

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

/** Touche spéciale du clavier. */
enum class KeyAction { TOGGLE, SPACE, DELETE, VOICE }

/** Touche : un caractère, ou une action ; [span] en unités (10 par ligne). */
data class KeySpec(val label: String, val char: Char? = null, val action: KeyAction? = null, val span: Int = 1)

/**
 * Disposition du clavier de recherche (testée) : AZERTY sur 3 lignes, puis Espace et la dictée.
 * La touche « 123 » bascule vers les chiffres et les lettres accentuées (« ABC » pour revenir).
 * Chaque ligne fait exactement [UNITS] unités : les colonnes restent alignées d'une ligne à l'autre.
 */
object KeyboardLayout {
    const val UNITS = 10
    private val LETTERS = listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
    private val SYMBOLS = listOf("1234567890", "éèàçù'-:&.", "âêîôû!")

    fun rows(digits: Boolean, voice: Boolean): List<List<KeySpec>> {
        val src = if (digits) SYMBOLS else LETTERS
        fun chars(s: String) = s.map { KeySpec(it.uppercase(), char = it) }
        return listOf(
            chars(src[0]),
            chars(src[1]),
            listOf(KeySpec(if (digits) "ABC" else "123", action = KeyAction.TOGGLE, span = 2)) + chars(src[2]) +
                KeySpec("Effacer", action = KeyAction.DELETE, span = 2),
            if (voice) listOf(KeySpec("Espace", action = KeyAction.SPACE, span = 7), KeySpec("Dicter", action = KeyAction.VOICE, span = 3))
            else listOf(KeySpec("Espace", action = KeyAction.SPACE, span = UNITS)),
        )
    }
}

private val KeyUnit = 46.dp
private val KeyHeight = 42.dp
private val Gap = 6.dp

/** Largeur de [n] unités côte à côte. */
private fun units(n: Int): Dp = KeyUnit * n + Gap * (n - 1)

/** Largeur totale du clavier (pour aligner le champ et les suggestions dessus). */
val TvKeyboardWidth: Dp = units(KeyboardLayout.UNITS)

/**
 * Clavier de recherche pour la télécommande : AZERTY compact (3 lignes), « 123 » pour les chiffres et accents,
 * Espace et la dictée (si la box a la reconnaissance vocale). Touches rectangulaires, bien espacées.
 * Effacer : appui long = tout effacer.
 */
@Composable
fun TvKeyboard(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    firstKey: FocusRequester = remember { FocusRequester() },
) {
    val voice = rememberVoiceInput { onQuery(it) }
    var digits by remember { mutableStateOf(false) }
    fun input(k: KeyInput) = onQuery(applyKey(query, k))
    Column(
        modifier.background(SokoulColors.Surface, Radius.Panel).padding(10.dp).width(TvKeyboardWidth).onPreviewKeyEvent { e ->
            val k = remoteKeyInput(e) ?: return@onPreviewKeyEvent false
            input(k); true
        },
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        KeyboardLayout.rows(digits, voice != null).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                row.forEachIndexed { c, k ->
                    val first = if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier
                    val onClick: () -> Unit = when (k.action) {
                        null -> ({ input(KeyInput.Char(k.char!!)) })
                        KeyAction.TOGGLE -> ({ digits = !digits })
                        KeyAction.SPACE -> ({ input(KeyInput.Space) })
                        KeyAction.DELETE -> ({ input(KeyInput.Delete) })
                        KeyAction.VOICE -> ({ voice?.invoke() })
                    }
                    KeyButton(
                        Modifier.size(units(k.span), KeyHeight).then(first), onClick,
                        onLongClick = if (k.action == KeyAction.DELETE) ({ input(KeyInput.Clear) }) else null,
                    ) { focused ->
                        when (k.action) {
                            null -> Text(k.label, style = com.sokoul.iptv.ui.theme.Type.Key, color = onChip(focused))
                            KeyAction.TOGGLE -> Text(k.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = onChip(focused))
                            KeyAction.SPACE -> KeyIcon(Icons.Default.SpaceBar, k.label, focused)
                            KeyAction.DELETE -> KeyIcon(Icons.AutoMirrored.Filled.Backspace, k.label, focused)
                            KeyAction.VOICE -> KeyIcon(Icons.Default.Mic, k.label, focused)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable (Boolean) -> Unit) {
    FocusSurface(onClick, modifier, style = FocusStyle.Key, shape = Radius.Tiny, color = SokoulColors.SurfaceHigher, onLongClick = onLongClick) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // relief discret : filet clair en haut, ombre en bas (effacés au focus : la touche devient blanche)
            if (!focused) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(SokoulColors.white(0.09f)))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(SokoulColors.black(0.35f)))
            }
            content(focused)
        }
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
