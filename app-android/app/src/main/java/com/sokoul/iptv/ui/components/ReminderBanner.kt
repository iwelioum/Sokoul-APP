package com.sokoul.iptv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokoul.iptv.recording.ReminderBus
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import kotlinx.coroutines.delay

/** Durée d'affichage du bandeau de rappel. */
const val REMINDER_BANNER_MS = 15_000L

/**
 * Bandeau de rappel en haut à droite, au-dessus de tous les écrans. Il ne prend pas le focus (l'écran en cours
 * reste utilisable) : la touche ⏯ ouvre la chaîne, il disparaît seul après [REMINDER_BANNER_MS].
 */
@Composable
fun ReminderBanner(onWatch: (ReminderBus.Reminder) -> Unit) {
    val current by ReminderBus.current.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<ReminderBus.Reminder?>(null) }
    LaunchedEffect(current) {
        val r = current ?: return@LaunchedEffect
        shown = r
        delay(REMINDER_BANNER_MS)
        ReminderBus.dismiss(r)
    }
    val l = LocalLayout.current
    Box(Modifier.fillMaxSize().padding(top = l.safeTop + 8.dp, end = l.margin), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(current != null, enter = fadeIn() + slideInHorizontally { it / 3 }, exit = fadeOut() + slideOutHorizontally { it / 3 }) {
            val r = shown ?: return@AnimatedVisibility
            Row(
                Modifier.widthIn(max = 460.dp).background(SokoulColors.Panel, Radius.Panel)
                    .border(1.dp, SokoulColors.white(0.12f), Radius.Panel).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.NotificationsActive, null, Modifier.size(28.dp), tint = SokoulColors.Warning)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("DANS 1 MINUTE · ${r.channel.uppercase()}", style = Type.Kicker.copy(color = SokoulColors.Warning), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.title, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    KeyHints(listOf(KeyHint("⏯", "Regarder"), KeyHint("Retour", "Ignorer")), Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    // fonction appelée par la racine quand ⏯ est pressée pendant l'affichage
    LaunchedEffect(Unit) { ReminderKeys.onWatch = { current?.let { onWatch(it); ReminderBus.dismiss(it) } } }
}

/** Lien entre la touche ⏯ (interceptée à la racine) et le bandeau affiché. */
object ReminderKeys {
    @Volatile var onWatch: (() -> Unit)? = null

    /** ⏯ ou Retour pendant l'affichage du bandeau : consommé ici, sinon laissé à l'écran. */
    fun handle(keyCode: Int): Boolean {
        val r = ReminderBus.current.value ?: return false
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { onWatch?.invoke(); true }
            android.view.KeyEvent.KEYCODE_BACK -> { ReminderBus.dismiss(r); true }
            else -> false
        }
    }
}
