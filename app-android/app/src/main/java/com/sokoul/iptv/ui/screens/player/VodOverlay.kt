package com.sokoul.iptv.ui.screens.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.repo.EpisodeMerge
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.player.StreamState
import com.sokoul.iptv.ui.components.AppButton
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatDuration
import com.sokoul.iptv.ui.util.rememberNow
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Action d'un bouton du lecteur (pastille icône + texte, ou bouton du haut en tactile). */
data class PlayerAction(val label: String, val icon: ImageVector, val primary: Boolean = false, val onClick: () -> Unit)

// --- Haut : titre, épisode, version, heure ------------------------------------------------------------

/** Bandeau du haut (glisse depuis le haut) : titre et épisode à gauche, heure à droite ; Retour / PiP en tactile. */
@Composable
fun VodTopBar(ui: PlayerUi, leading: List<PlayerAction>) {
    val l = LocalLayout.current
    val now = rememberNow(15_000)
    val side = if (l.isCompact) 12.dp else 48.dp
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SokoulColors.ScrimStrong, SokoulColors.Scrim, Color.Transparent)))
            .padding(start = side, end = side, top = if (l.isCompact) 12.dp else 30.dp, bottom = 56.dp),
        verticalAlignment = Alignment.Top,
    ) {
        leading.forEach { PlayerButton(it, Modifier.padding(end = 8.dp), compact = true) }
        Column(Modifier.weight(1f).padding(start = if (leading.isEmpty()) 0.dp else 4.dp, top = if (leading.isEmpty()) 0.dp else 2.dp)) {
            Text(
                ui.title, style = if (l.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                ui.subtitle?.let {
                    Text(it, Modifier.weight(1f, fill = false), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ui.versionLabel?.let { VersionBadge(it) }
            }
        }
        if (!l.isCompact) {
            Text(formatClock(now), Modifier.padding(start = 24.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun VersionBadge(label: String) {
    Text(
        label, Modifier.padding(start = 10.dp).border(1.dp, SokoulColors.GlassHeavy, Radius.Tiny).padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextSecondary,
    )
}

// --- Bas : progression, temps, boutons -----------------------------------------------------------------

/**
 * Commandes de lecture à la demande, pensées pour la télécommande :
 * - barre de progression focalisable (◀ ▶ = recul/avance avec aperçu, OK = lecture/pause, ▼ = ⏯) ;
 * - ordre fixe des boutons pour tous les titres : transport (−10 / ⏯ / +30) puis pastilles
 *   (Épisodes, Suivant, Audio et sous-titres, Plus).
 * Sur écran tactile, la barre se glisse au doigt (déplacement appliqué au relâchement).
 */
@Composable
fun VodControls(
    position: Long,
    duration: Long,
    preview: Long?,
    buffering: Boolean,
    playing: Boolean,
    scrubberFocus: FocusRequester,
    playFocus: FocusRequester,
    onBack10: () -> Unit,
    onPlayPause: () -> Unit,
    onFwd30: () -> Unit,
    pills: List<PlayerAction>,
    onSeekStep: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onInteract: () -> Unit,
) {
    val l = LocalLayout.current
    val side = if (l.isCompact) 16.dp else 48.dp
    val shown = preview ?: position
    val now = rememberNow(15_000)
    val up = if (l.isTouch) null else scrubberFocus
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.Scrim, SokoulColors.ScrimStrong)))
            .padding(horizontal = side).padding(top = 80.dp, bottom = if (l.isCompact) 16.dp else 30.dp)
    ) {
        if (l.isTouch) TouchScrubber(shown, duration, onSeekTo, onInteract)
        else Scrubber(shown, duration, preview != null, buffering, scrubberFocus, playFocus, onSeekStep, onPlayPause)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(shown), color = SokoulColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            if (duration > 0) {
                val left = (duration - shown).coerceAtLeast(0)
                Text("−${formatDuration(left)}", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Text("  ·  Fin à ${formatClock(now + left)}", color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (l.isCompact) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                TransportButtons(playing, playFocus, up, onBack10, onPlayPause, onFwd30)
            }
            // passage à la ligne plutôt qu'une rangée défilante : tous les boutons restent visibles
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(top = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pills.forEach { PillButton(it, upFocus = up) }
            }
        } else {
            Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TransportButtons(playing, playFocus, up, onBack10, onPlayPause, onFwd30)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pills.forEach { PillButton(it, upFocus = up) }
                }
            }
        }
    }
}

/** −10 s · ⏯ (grand bouton rond) · +30 s. */
@Composable
private fun TransportButtons(
    playing: Boolean, playFocus: FocusRequester, upFocus: FocusRequester?,
    onBack10: () -> Unit, onPlayPause: () -> Unit, onFwd30: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RoundButton("Reculer de 10 secondes", 48.dp, upFocus, onClick = onBack10) { tint -> Icon(Icons.Default.Replay10, null, Modifier.size(26.dp), tint = tint) }
        RoundButton(if (playing) "Pause" else "Lecture", 62.dp, upFocus, Modifier.focusRequester(playFocus), primary = true, onClick = onPlayPause) { tint ->
            AnimatedContent(
                playing,
                transitionSpec = { (scaleIn(Motion.enter(), initialScale = 0.6f) + fadeIn(Motion.enter())) togetherWith (scaleOut(Motion.exit(), targetScale = 0.6f) + fadeOut(Motion.exit())) },
                label = "playpause",
            ) { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(34.dp), tint = tint) }
        }
        RoundButton("Avancer de 30 secondes", 48.dp, upFocus, onClick = onFwd30) { tint -> Icon(Icons.Default.Forward30, null, Modifier.size(26.dp), tint = tint) }
    }
}

@Composable
private fun RoundButton(
    label: String, size: Dp, upFocus: FocusRequester?, modifier: Modifier = Modifier, primary: Boolean = false,
    onClick: () -> Unit, icon: @Composable (Color) -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.then(if (upFocus != null) Modifier.focusProperties { up = upFocus } else Modifier)
            .size(size).semantics { contentDescription = label },
        shape = CircleShape, color = if (primary) SokoulColors.GlassHeavy else SokoulColors.GlassLight,
        focusedColor = Color.White, focusScale = 1.12f, borderWidth = 0.dp,
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon(if (focused) SokoulColors.OnLight else Color.White) }
    }
}

/** Pastille icône + texte (toujours lisible, sans attendre le focus). */
@Composable
fun PillButton(a: PlayerAction, modifier: Modifier = Modifier, upFocus: FocusRequester? = null) {
    FocusSurface(
        onClick = a.onClick,
        modifier = modifier.then(if (upFocus != null) Modifier.focusProperties { up = upFocus } else Modifier)
            .semantics { contentDescription = a.label },
        shape = Radius.Pill, color = SokoulColors.GlassLight, focusedColor = Color.White, focusScale = 1.06f, borderWidth = 0.dp,
    ) { focused ->
        val fg = if (focused) SokoulColors.OnLight else Color.White
        Row(Modifier.height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(a.icon, null, Modifier.size(20.dp), tint = fg)
            Text(a.label, Modifier.padding(start = 8.dp), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Barre de progression pilotée au D-pad : s'épaissit au focus, repère qui grossit, temps visé en bulle pendant l'aperçu. */
@Composable
private fun Scrubber(
    position: Long, duration: Long, previewing: Boolean, buffering: Boolean, focus: FocusRequester, downFocus: FocusRequester,
    onSeekStep: (Long) -> Unit, onPlayPause: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val track by animateDpAsState(if (focused) 8.dp else 4.dp, if (reduced) tween(0) else Motion.focusSpring(), label = "track")
    val knob by animateFloatAsState(if (focused || previewing) 1f else 0f, if (reduced) tween(0) else Motion.focusSpring(), label = "knob")
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(28.dp)
            .focusRequester(focus)
            // ▼ : toujours ⏯, pas le bouton qui se trouve sous le curseur
            .focusProperties { down = downFocus }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val r = e.nativeKeyEvent.repeatCount
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onSeekStep(-seekStep(r, back = true)); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onSeekStep(seekStep(r, back = false)); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { if (r == 0) onPlayPause(); true }
                    else -> false
                }
            }
            .focusable()
            .semantics { contentDescription = "Position de lecture, ${formatDuration(position)}" + if (duration > 0) " sur ${formatDuration(duration)}" else "" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(track).clip(Radius.Tiny).background(SokoulColors.GlassLight)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(if (previewing) Color.White else SokoulColors.Accent))
        }
        val knobSize = 18.dp
        Box(
            Modifier.offset(x = (maxWidth - knobSize) * fraction).size(knobSize)
                .graphicsLayer { scaleX = knob; scaleY = knob; alpha = knob }
                .background(Color.White, CircleShape).border(3.dp, SokoulColors.Accent, CircleShape)
        )
        if (previewing) {
            val bubble = 76.dp
            Text(
                formatDuration(position),
                Modifier.offset(x = ((maxWidth * fraction) - bubble / 2).coerceIn(0.dp, maxWidth - bubble), y = (-30).dp).width(bubble)
                    .background(Color.White, Radius.Card).padding(vertical = 3.dp),
                color = SokoulColors.OnLight, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
        }
        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.CenterEnd).offset(y = (-22).dp).size(16.dp), color = Color.White, strokeWidth = 2.dp)
    }
}

/** Pas d'avance/recul selon le temps d'appui (touche maintenue = déplacement accéléré). */
fun seekStep(repeat: Int, back: Boolean): Long = when {
    repeat > 20 -> if (back) 60_000L else 120_000L
    repeat > 5 -> if (back) 30_000L else 60_000L
    else -> if (back) 10_000L else 30_000L
}

/** Cible d'un déplacement : bornée au début et à 1 s avant la fin (jamais de saut au-delà du contenu). */
fun clampSeek(target: Long, duration: Long): Long =
    target.coerceIn(0L, if (duration > 1_000) duration - 1_000 else Long.MAX_VALUE)

/** « 30 s », « 1 min 30 », « 2 min ». */
fun seekDeltaLabel(ms: Long): String {
    val s = abs(ms) / 1000
    return if (s < 60) "$s s" else "${s / 60} min" + if (s % 60 > 0) " ${s % 60}" else ""
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TouchScrubber(position: Long, duration: Long, onSeekTo: (Long) -> Unit, onInteract: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Slider(
        value = fraction,
        onValueChange = { drag = it; onInteract() },
        // une seule requête au relâchement (pas de rafale de requêtes pendant le glissement)
        onValueChangeFinished = { drag?.let { if (duration > 0) onSeekTo((it * duration).toLong()) }; drag = null },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Position de lecture" },
        // piste fine et repère rond (le style Material par défaut — piste épaisse, repère en trait — jure avec la TV)
        thumb = { Box(Modifier.size(if (drag != null) 22.dp else 16.dp).background(SokoulColors.Accent, CircleShape)) },
        track = {
            Box(Modifier.fillMaxWidth().height(if (drag != null) 6.dp else 4.dp).background(SokoulColors.GlassLight, CircleShape)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(SokoulColors.Accent, CircleShape))
            }
        },
    )
}

/** Bouton rond du haut (tactile : Retour, image dans l'image…) ; libellé révélé au focus sur TV. */
@Composable
fun PlayerButton(a: PlayerAction, modifier: Modifier = Modifier, compact: Boolean = false) {
    val l = LocalLayout.current
    FocusSurface(
        onClick = a.onClick, modifier = modifier.semantics { contentDescription = a.label },
        shape = Radius.Pill, color = if (a.primary) SokoulColors.GlassHeavy else SokoulColors.GlassLight,
        focusedColor = Color.White, focusScale = 1.08f, borderWidth = 0.dp,
    ) { focused ->
        val fg = if (focused) SokoulColors.OnLight else Color.White
        Row(
            Modifier.height(if (compact) 44.dp else 48.dp).padding(horizontal = if (focused || (!l.isTv && !compact)) 16.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(a.icon, null, Modifier.size(24.dp), tint = fg)
            if (focused || (!l.isTv && !compact)) {
                Text(a.label, Modifier.padding(start = 8.dp), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Haut de l'écran en direct (tactile) : retour, liste des chaînes, options. */
@Composable
fun TopBarActions(actions: List<PlayerAction>) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { PlayerButton(it, compact = true) }
    }
}

// --- Retours visuels -----------------------------------------------------------------------------------

/** Retour latéral d'un saut sans barre (D-pad, double-tap) : « ⟲ 30 s » à gauche, « 1 min 30 ⟳ » à droite, cumulé. */
@Composable
fun SeekFeedback(deltaMs: Long, target: Long, modifier: Modifier = Modifier) {
    val forward = deltaMs >= 0
    val shade = SokoulColors.Scrim
    Box(
        modifier.fillMaxHeight().width(260.dp)
            .background(Brush.horizontalGradient(if (forward) listOf(Color.Transparent, shade) else listOf(shade, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (forward) Icons.Default.FastForward else Icons.Default.FastRewind, null, Modifier.size(40.dp), tint = Color.White)
            Text((if (forward) "+" else "−") + seekDeltaLabel(deltaMs), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(formatDuration(target), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Rappel « Reprise à … » (contenu seul : l'apparition est animée par l'écran). */
@Composable
fun ResumeHint(fromMs: Long, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp).background(SokoulColors.Scrim, Radius.Card).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Reprise à ${formatDuration(fromMs)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("« Depuis le début » dans ⋯ Plus", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

/** Pause prolongée (TV) : la barre laisse place au titre, à l'épisode et au résumé sur fond assombri. */
@Composable
fun PauseScreen(ui: PlayerUi, position: Long, duration: Long) {
    val l = LocalLayout.current
    val now = rememberNow(15_000)
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(SokoulColors.ScrimStrong, SokoulColors.Scrim, SokoulColors.black(0.2f))))) {
        Text(formatClock(now), Modifier.align(Alignment.TopEnd).padding(32.dp), style = MaterialTheme.typography.titleLarge)
        Column(Modifier.align(Alignment.BottomStart).padding(start = l.margin, bottom = 56.dp, end = l.margin).widthIn(max = 640.dp)) {
            Text("EN PAUSE", color = SokoulColors.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(ui.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ui.subtitle?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            ui.overview?.let {
                Text(it, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextPrimary.copy(alpha = 0.9f), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (duration > 0) {
                Text(
                    "${formatDuration(position)} / ${formatDuration(duration)}  ·  reste ${formatDuration((duration - position).coerceAtLeast(0))}",
                    Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextMuted,
                )
            }
            Text("OK pour reprendre  ·  Retour pour fermer  ·  autre touche : commandes", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted)
        }
    }
}

// --- États : chargement, mise en mémoire tampon, refus du fournisseur, erreur --------------------------

@Composable
fun StatusOverlay(state: StreamState, error: String?, retryFocus: FocusRequester, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null || state is StreamState.Failed -> {
                val failed = state as? StreamState.Failed
                val wait = rememberCountdown(if (failed?.refused == true) failed.retryAtMs else 0L)
                ErrorCard(
                    title = if (failed?.refused == true) "Le fournisseur refuse la connexion" else "Lecture impossible",
                    message = error ?: failed?.message.orEmpty(),
                    hint = if (failed?.refused == true) "Fermez la lecture sur vos autres appareils. Pour protéger votre abonnement, l'app attend avant de réessayer." else null,
                ) {
                    AppButton(if (wait > 0) "Réessayer dans $wait s" else "Réessayer", { if (wait <= 0) onRetry() }, icon = Icons.Default.Replay, primary = wait <= 0, enabled = wait <= 0, focusRequester = retryFocus)
                    AppButton("Retour", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack)
                }
                LaunchedEffect(Unit) { delay(80); runCatching { retryFocus.requestFocus() } }
            }
            state is StreamState.Waiting -> {
                val wait = rememberCountdown(state.untilMs)
                ErrorCard(
                    title = "Connexion en pause",
                    message = state.message,
                    hint = "Nouvel essai automatique dans $wait s — une seule connexion à la fois pour ne pas bloquer votre abonnement.",
                ) {
                    AppButton("Retour", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, focusRequester = retryFocus)
                }
                LaunchedEffect(Unit) { delay(80); runCatching { retryFocus.requestFocus() } }
            }
            state is StreamState.Loading || state is StreamState.Buffering -> DelayedSpinner(SokoulColors.Accent)
            state is StreamState.Reconnecting -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SokoulColors.Warning)
                Text(
                    "Connexion perdue, nouvelle tentative (${state.attempt})…",
                    Modifier.padding(top = 12.dp).background(SokoulColors.Scrim, Radius.Card).padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                )
            }
            else -> Unit
        }
    }
}

/** Indicateur de chargement affiché seulement après 300 ms : pas de clignotement sur les chargements rapides. */
@Composable
private fun DelayedSpinner(color: Color) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(300); show = true }
    val alpha by animateFloatAsState(if (show) 1f else 0f, Motion.enter(), label = "spinner")
    CircularProgressIndicator(Modifier.graphicsLayer { this.alpha = alpha }.semantics { contentDescription = "Chargement" }, color = color)
}

@Composable
private fun ErrorCard(title: String, message: String, hint: String?, buttons: @Composable () -> Unit) {
    Column(
        Modifier.padding(24.dp).widthIn(max = 600.dp).background(SokoulColors.Panel, Radius.Panel).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (message.isNotBlank()) Text(message, Modifier.padding(top = 10.dp), color = SokoulColors.TextSecondary, textAlign = TextAlign.Center)
        if (hint != null) Text(hint, Modifier.padding(top = 8.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
    }
}

/** Secondes restantes jusqu'à [untilMs] (0 si passé), mises à jour chaque seconde. */
@Composable
fun rememberCountdown(untilMs: Long): Long {
    var left by remember(untilMs) { mutableStateOf(((untilMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) }
    LaunchedEffect(untilMs) {
        while (true) {
            left = ((untilMs - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0)
            if (left <= 0) break
            delay(250)
        }
    }
    return left
}

// --- Épisode suivant -----------------------------------------------------------------------------------

/** Durée du compte à rebours avant l'épisode suivant (secondes), utilisée pour remplir le bouton. */
const val NEXT_COUNTDOWN_S = 20

/**
 * Carte « Épisode suivant » : vignette de l'épisode **suivant** (sinon fond de la série), titre, et bouton
 * « Lire maintenant » qui se remplit pendant le compte à rebours.
 */
@Composable
fun NextUpCard(
    next: EpisodeEntity, seconds: Int?, fallbackImage: String?, playFocus: FocusRequester, modifier: Modifier,
    tmdb: TmdbEpisode? = null, onPlay: () -> Unit, onCancel: () -> Unit,
) {
    val l = LocalLayout.current
    val view = EpisodeMerge.merge(next, tmdb, null)
    // largeur d'abord (moitié de l'écran), plafond ensuite : dans l'autre ordre la carte ne fait que 260 dp
    Column(modifier.fillMaxWidth(if (l.isCompact) 0.94f else 0.5f).widthIn(max = 560.dp).background(SokoulColors.Panel, Radius.Panel).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (l.isCompact) 112.dp else 168.dp).aspectRatio(16f / 9f).clip(Radius.Card).background(SokoulColors.SurfaceHigher).semantics { contentDescription = "Vignette de l'épisode ${next.episode}" }) {
                NetImage(view.image ?: fallbackImage, null, Modifier.fillMaxSize()) {
                    NetImage(fallbackImage, null, Modifier.fillMaxSize()) { Icon(Icons.Default.PlayArrow, null, tint = SokoulColors.TextMuted) }
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(if (seconds != null) "Épisode suivant dans $seconds s" else "Épisode suivant", color = SokoulColors.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    "S${next.season} · É${next.episode} — ${view.title}",
                    Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CountdownButton("Lire maintenant", seconds?.let { 1f - it.toFloat() / NEXT_COUNTDOWN_S }, playFocus, onPlay)
            AppButton(if (seconds != null) "Annuler" else "Fermer", onCancel)
        }
    }
    LaunchedEffect(next.id) { delay(80); runCatching { playFocus.requestFocus() } }
}

@Composable
private fun CountdownButton(label: String, fraction: Float?, focus: FocusRequester, onClick: () -> Unit) {
    val fill by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), tween(1_000, easing = LinearEasing), label = "countdown")
    FocusSurface(
        onClick = onClick, modifier = Modifier.focusRequester(focus).semantics { contentDescription = label },
        shape = Radius.Pill, color = SokoulColors.GlassHeavy, focusedColor = Color.White, focusScale = 1.06f, borderWidth = 0.dp,
    ) { focused ->
        val fg = if (focused) SokoulColors.OnLight else Color.White
        Row(
            Modifier.height(46.dp)
                .drawBehind { if (fraction != null) drawRect(if (focused) SokoulColors.AccentTint.copy(alpha = 0.35f) else SokoulColors.GlassLight, size = Size(size.width * fill, size.height)) }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(22.dp), tint = fg)
            Text(label, Modifier.padding(start = 8.dp), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// --- Panneau « Épisodes » -------------------------------------------------------------------------------

@Composable
fun EpisodesPanel(
    episodes: List<EpisodeEntity>, current: Pair<Int, Int>?, fallbackImage: String?, modifier: Modifier,
    tmdb: Map<Pair<Int, Int>, TmdbEpisode> = emptyMap(), onSeason: (Int) -> Unit = {},
    onPick: (EpisodeEntity) -> Unit,
) {
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    var season by remember(current) { mutableStateOf(current?.first ?: seasons.firstOrNull()) }
    LaunchedEffect(season) { season?.let(onSeason) }
    val list = episodes.filter { it.season == season }
    val state = rememberLazyListState()
    val currentFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val l = LocalLayout.current
    Column(
        modifier.fillMaxHeight().fillMaxWidth(if (l.isCompact) 1f else 0.42f).widthIn(max = 560.dp)
            .background(Brush.horizontalGradient(listOf(SokoulColors.Panel.copy(alpha = 0.9f), SokoulColors.Panel))).padding(vertical = 24.dp)
    ) {
        Text("Épisodes", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (seasons.size > 1) {
            LazyRow(Modifier.padding(top = 12.dp), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons) { n ->
                    FocusSurface(
                        onClick = { season = n }, shape = Radius.Pill, focusScale = 1.06f, borderWidth = 2.dp,
                        color = if (n == season) Color.White else SokoulColors.SurfaceHigh, focusedColor = if (n == season) Color.White else SokoulColors.SurfaceHigher,
                        onFocus = { season = n },
                    ) { _ ->
                        Text("Saison $n", Modifier.padding(horizontal = 14.dp, vertical = 7.dp), color = if (n == season) SokoulColors.OnLight else SokoulColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        LazyColumn(Modifier.padding(top = 12.dp), state = state, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.id }) { e ->
                val isCurrent = current == (e.season to e.episode)
                val view = EpisodeMerge.merge(e, tmdb[e.season to e.episode], null)
                val title = view.title
                FocusSurface(
                    onClick = { onPick(e) },
                    modifier = Modifier.fillMaxWidth().then(
                        when {
                            isCurrent -> Modifier.focusRequester(currentFocus)
                            e == list.firstOrNull() -> Modifier.focusRequester(firstFocus)
                            else -> Modifier
                        }
                    ).semantics { contentDescription = "Épisode ${e.episode} : $title" + if (isCurrent) ", en cours" else "" },
                    color = if (isCurrent) SokoulColors.AccentSoft else Color.Transparent, focusedColor = SokoulColors.SurfaceHigher, focusScale = 1.02f, borderWidth = 2.dp,
                ) { _ ->
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(Radius.Card).background(SokoulColors.SurfaceHigher)) {
                            NetImage(view.image ?: fallbackImage, null, Modifier.fillMaxSize()) { NetImage(fallbackImage, null, Modifier.fillMaxSize()) }
                            if (isCurrent) Icon(Icons.Default.PlayArrow, null, Modifier.align(Alignment.Center).size(28.dp).background(SokoulColors.Scrim, CircleShape), tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(if (title == "Épisode ${e.episode}") title else "${e.episode}. $title", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium)
                            e.durationSecs?.takeIf { it > 0 }?.let { Text("${it / 60} min", color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
    // ouverture : la liste se place sur l'épisode en cours, qui reçoit le focus (une seule fois)
    LaunchedEffect(Unit) {
        val i = list.indexOfFirst { current == (it.season to it.episode) }
        if (i >= 0) state.scrollToItem((i - 1).coerceAtLeast(0))
        delay(60)
        runCatching { if (i >= 0) currentFocus.requestFocus() else firstFocus.requestFocus() }
    }
}
