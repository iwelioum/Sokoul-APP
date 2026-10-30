package com.sokoul.iptv.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatClock
import com.sokoul.iptv.ui.util.formatDuration
import com.sokoul.iptv.ui.util.rememberNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Action d'un bouton du lecteur. [label] : nom de l'action (lu par l'accessibilité) ; [text] : valeur affichée
 * à la place du nom (« Français », « VF · 1080p ») ; [iconOnly] : bouton rond, nom affiché au survol.
 */
data class PlayerAction(
    val label: String, val icon: ImageVector, val primary: Boolean = false,
    val text: String? = null, val iconOnly: Boolean = false,
    val onClick: () -> Unit,
)

// --- Haut : titre, épisode, version, heure ------------------------------------------------------------

/** Bandeau du haut de la tablette : Retour, titre et épisode à gauche, version en pastille, heure à droite. */
@Composable
fun VodTopBar(title: String, subtitle: String?, badge: String?, leading: List<PlayerAction>) {
    val now = rememberNow(15_000)
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SokoulColors.ScrimStrong, SokoulColors.Scrim, Color.Transparent)))
            .padding(start = 48.dp, end = 48.dp, top = 30.dp, bottom = 56.dp),
        verticalAlignment = Alignment.Top,
    ) {
        leading.forEach { PlayerButton(it, Modifier.padding(end = 8.dp), compact = true) }
        Column(Modifier.weight(1f).padding(start = if (leading.isEmpty()) 0.dp else 4.dp, top = if (leading.isEmpty()) 0.dp else 2.dp)) {
            Text(title, style = com.sokoul.iptv.ui.theme.Type.PlayerTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                subtitle?.let {
                    Text(it, Modifier.weight(1f, fill = false), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                badge?.let { VersionBadge(it) }
            }
        }
        Text(formatClock(now), Modifier.padding(start = 24.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun VersionBadge(label: String) {
    Text(
        label, Modifier.padding(start = 10.dp).background(SokoulColors.GlassLight, Radius.Tiny).padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = "Vous regardez : $label" },
        style = com.sokoul.iptv.ui.theme.Type.Badge, color = SokoulColors.TextPrimary, maxLines = 1,
    )
}

// --- Bas : progression, temps, boutons -----------------------------------------------------------------

/**
 * Commandes de lecture à la demande de la tablette : barre de progression glissée à la souris (déplacement appliqué
 * au relâchement), temps écoulé, reste et heure de fin, puis −10 / ⏯ / +30, boutons ronds et pastilles.
 */
@Composable
fun VodControls(
    position: Long,
    duration: Long,
    playing: Boolean,
    onBack10: () -> Unit,
    onPlayPause: () -> Unit,
    onFwd30: () -> Unit,
    pills: List<PlayerAction>,
    onSeekTo: (Long) -> Unit,
    onInteract: () -> Unit,
    /** Boutons ronds après ⏯ (Épisodes, Suivant) : icône seule, nom affiché au survol. */
    tools: List<PlayerAction> = emptyList(),
) {
    var hint by remember { mutableStateOf<String?>(null) }
    val onHint: (String, Boolean) -> Unit = { label, has -> if (has) hint = label else if (hint == label) hint = null }
    val now = rememberNow(15_000)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.Scrim, SokoulColors.ScrimStrong)))
            .padding(horizontal = 48.dp).padding(top = 80.dp, bottom = 30.dp)
    ) {
        TouchScrubber(position, duration, onSeekTo, onInteract)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(position), color = SokoulColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
            // nom du bouton rond survolé (icône seule)
            Text(hint.orEmpty(), Modifier.weight(1f), color = SokoulColors.TextPrimary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
            if (duration > 0) {
                val left = (duration - position).coerceAtLeast(0)
                Text("−${formatDuration(left)}", color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Text("  ·  Fin à ${formatClock(now + left)}", color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TransportButtons(playing, onBack10, onPlayPause, onFwd30, onHint)
            tools.forEach { t ->
                RoundButton(t.label, 48.dp, Modifier.padding(start = 12.dp), onHint = onHint, onClick = t.onClick) { tint -> Icon(t.icon, null, Modifier.size(24.dp), tint = tint) }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                pills.forEach { p ->
                    if (p.iconOnly) RoundButton(p.label, 48.dp, onHint = onHint, onClick = p.onClick) { tint -> Icon(p.icon, null, Modifier.size(24.dp), tint = tint) }
                    else PillButton(p, Modifier.widthIn(max = 220.dp))
                }
            }
        }
    }
}

/** −10 s · ⏯ (grand bouton rond) · +30 s. */
@Composable
private fun TransportButtons(playing: Boolean, onBack10: () -> Unit, onPlayPause: () -> Unit, onFwd30: () -> Unit, onHint: (String, Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RoundButton("Reculer de 10 secondes", 48.dp, onHint = onHint, onClick = onBack10) { tint -> Icon(Icons.Default.Replay10, null, Modifier.size(26.dp), tint = tint) }
        RoundButton(if (playing) "Pause" else "Lecture", 62.dp, primary = true, onHint = onHint, onClick = onPlayPause) { tint ->
            AnimatedContent(
                playing,
                transitionSpec = { (scaleIn(Motion.enter(), initialScale = 0.6f) + fadeIn(Motion.enter())) togetherWith (scaleOut(Motion.exit(), targetScale = 0.6f) + fadeOut(Motion.exit())) },
                label = "playpause",
            ) { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(34.dp), tint = tint) }
        }
        RoundButton("Avancer de 30 secondes", 48.dp, onHint = onHint, onClick = onFwd30) { tint -> Icon(Icons.Default.Forward30, null, Modifier.size(26.dp), tint = tint) }
    }
}

@Composable
private fun RoundButton(
    label: String, size: Dp, modifier: Modifier = Modifier, primary: Boolean = false,
    onHint: (String, Boolean) -> Unit = { _, _ -> },
    onClick: () -> Unit, icon: @Composable (Color) -> Unit,
) {
    FocusSurface(
        style = FocusStyle.Chip,
        onClick = onClick,
        modifier = modifier.size(size).semantics { contentDescription = label },
        shape = Radius.Round, color = if (primary) SokoulColors.GlassHeavy else SokoulColors.GlassLight,
        focusedColor = SokoulColors.OnVideo,
    ) { focused ->
        // PC : le survol vaut le focus, le nom du bouton s'affiche au-dessus des boutons
        androidx.compose.runtime.LaunchedEffect(focused) { onHint(label, focused) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon(if (focused) SokoulColors.OnLight else SokoulColors.OnVideo) }
    }
}

/** Pastille icône + texte (toujours lisible). */
@Composable
fun PillButton(a: PlayerAction, modifier: Modifier = Modifier) = com.sokoul.iptv.ui.components.SokoulButton(
    a.text ?: a.label, a.onClick,
    modifier.semantics { contentDescription = if (a.text != null) "${a.label} : ${a.text}" else a.label },
    a.icon, com.sokoul.iptv.ui.components.ButtonKind.Glass,
)

/** Cible d'un déplacement : bornée au début et à 1 s avant la fin (jamais de saut au-delà du contenu). */
fun clampSeek(target: Long, duration: Long): Long =
    target.coerceIn(0L, if (duration > 1_000) duration - 1_000 else Long.MAX_VALUE)

private val ScrubberThumb = 16.dp

/** Fraction visée par un clic à [x] sur une barre de largeur [width] dont la piste est rentrée de [inset] de chaque côté. */
fun scrubFraction(x: Float, width: Float, inset: Float): Float {
    val track = width - 2 * inset
    return if (track <= 0f) 0f else ((x - inset) / track).coerceIn(0f, 1f)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TouchScrubber(position: Long, duration: Long, onSeekTo: (Long) -> Unit, onInteract: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val scope = rememberCoroutineScope()
    // une seule requête au relâchement (pas de rafale de requêtes pendant le glissement)
    val commit = { drag?.let { if (duration > 0) onSeekTo(clampSeek((it * duration).toLong(), duration)) }; drag = null }
    Slider(
        value = fraction,
        onValueChange = { drag = it; onInteract() },
        onValueChangeFinished = commit,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Position de lecture" }
            // un clic bref ne fait rien sur le Slider : le geste est observé sans être consommé ;
            // au relâchement, la position glissée est validée, ou à défaut celle du clic
            .pointerInput(duration) {
                val inset = ScrubberThumb.toPx() / 2
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var moved = false
                    do {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.firstOrNull { it.id == down.id }?.let {
                            if ((it.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        }
                    } while (e.changes.any { it.pressed })
                    val tapped = scrubFraction(down.position.x, size.width.toFloat(), inset)
                    scope.launch {
                        delay(60)
                        if (drag != null) commit()
                        else if (!moved && duration > 0) onSeekTo(clampSeek((tapped * duration).toLong(), duration))
                    }
                }
            },
        thumb = { Box(Modifier.size(if (drag != null) 22.dp else ScrubberThumb).background(SokoulColors.Accent, Radius.Round)) },
        track = {
            Box(Modifier.fillMaxWidth().height(if (drag != null) 6.dp else 4.dp).background(SokoulColors.GlassLight, Radius.Round)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(SokoulColors.Accent, Radius.Round))
            }
        },
    )
}

/** Bouton du haut (Retour…) : icône et nom. */
@Composable
fun PlayerButton(a: PlayerAction, modifier: Modifier = Modifier, compact: Boolean = false) {
    FocusSurface(
        style = FocusStyle.Chip,
        onClick = a.onClick, modifier = modifier.semantics { contentDescription = a.label },
        shape = Radius.Card, color = com.sokoul.iptv.ui.components.buttonRest(if (a.primary) com.sokoul.iptv.ui.components.ButtonKind.Primary else com.sokoul.iptv.ui.components.ButtonKind.Glass, true),
        focusedColor = SokoulColors.Focus,
    ) { focused ->
        val fg = com.sokoul.iptv.ui.components.buttonContent(focused)
        Row(Modifier.height(if (compact) 44.dp else 48.dp).padding(horizontal = if (focused || !compact) 16.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(a.icon, null, Modifier.size(24.dp), tint = fg)
            if (focused || !compact) Text(a.label, Modifier.padding(start = 8.dp), color = fg, style = com.sokoul.iptv.ui.theme.Type.Button, maxLines = 1)
        }
    }
}

// --- États : chargement, erreur ------------------------------------------------------------------------

/** Ouverture d'un titre : fond de l'œuvre assombri, titre qui pulse, étape en cours dessous. */
@Composable
fun LoadingLogo(title: String, backdrop: String?, status: String, detail: String? = null) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "logo")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 1f, label = "logoAlpha",
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
    )
    Box(Modifier.fillMaxSize().background(SokoulColors.VideoBlack).semantics { contentDescription = "Chargement de $title" }, contentAlignment = Alignment.Center) {
        if (backdrop != null) {
            com.sokoul.iptv.ui.components.NetImage(backdrop, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = 0.35f })
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(SokoulColors.black(0.35f), SokoulColors.black(0.85f)))))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text(
                title, Modifier.widthIn(max = 720.dp).graphicsLayer { this.alpha = alpha },
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(status, label = "startStatus", transitionSpec = { fadeIn(androidx.compose.animation.core.tween(200)) togetherWith fadeOut(androidx.compose.animation.core.tween(200)) }) { s ->
                Text(s, Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelLarge, color = SokoulColors.TextMuted, textAlign = TextAlign.Center, maxLines = 2)
            }
            detail?.let { Text(it, Modifier.padding(top = 6.dp).widthIn(max = 640.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Indicateur de chargement affiché seulement après 300 ms : pas de clignotement sur les chargements rapides. */
@Composable
fun DelayedSpinner(color: Color = SokoulColors.Accent) {
    var show by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { delay(300); show = true }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (show) 1f else 0f, Motion.enter(), label = "spinner")
    com.sokoul.iptv.ui.components.SokoulSpinner(Modifier.graphicsLayer { this.alpha = alpha }.semantics { contentDescription = "Chargement" }, color = color)
}

@Composable
fun ErrorCard(title: String, message: String, hint: String?, buttons: @Composable () -> Unit) {
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

// --- Direct ----------------------------------------------------------------------------------------------

/** Programme affiché dans le bandeau du direct (début, fin, titre, résumé). */
data class LiveProgram(val title: String, val start: Long, val end: Long, val description: String?)

/**
 * Bandeau du direct de la tablette : numéro, logo, nom de la chaîne, programme en cours avec sa progression, résumé,
 * « Ensuite », heure et définition du flux ; [actions] en pastilles sous le bandeau.
 */
@Composable
fun LiveInfoBar(
    number: Int, logo: String?, name: String, favorite: Boolean,
    now: LiveProgram?, next: LiveProgram?, videoInfo: String?, actions: List<PlayerAction>,
) {
    val time = rememberNow(15_000)
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.black(0.9f), SokoulColors.black(0.95f))))
            .padding(start = 40.dp, end = 40.dp, top = 60.dp, bottom = 32.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number.toString(), Modifier.padding(end = 18.dp), style = com.sokoul.iptv.ui.theme.Type.ChannelNumberMedium, color = SokoulColors.OnVideo)
                ChannelLogo(logo, name, Modifier.size(120.dp, 72.dp))
                Column(Modifier.weight(1f).padding(start = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = com.sokoul.iptv.ui.theme.Type.PlayerTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (favorite) Icon(Icons.Default.Star, null, Modifier.padding(start = 8.dp).size(18.dp), tint = SokoulColors.Warning)
                    }
                    if (now != null) {
                        Text(now.title, style = MaterialTheme.typography.titleLarge, color = SokoulColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text(formatClock(now.start), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                            val f = ((time - now.start).toFloat() / (now.end - now.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                            Box(Modifier.padding(horizontal = 10.dp).weight(1f).height(4.dp).background(SokoulColors.white(0.2f), Radius.Tiny)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(SokoulColors.Accent, Radius.Tiny))
                            }
                            Text(formatClock(now.end), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        }
                        now.description?.let { Text(it, Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    } else {
                        Text("Pas d'information de programme", color = SokoulColors.TextMuted)
                    }
                    next?.let { n -> Text("Ensuite · ${formatClock(n.start)}  ${n.title}", Modifier.padding(top = 6.dp), color = SokoulColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 24.dp)) {
                    Text(formatClock(time), style = MaterialTheme.typography.headlineSmall)
                    if (!videoInfo.isNullOrBlank()) Text(videoInfo, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            if (actions.isNotEmpty()) Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { PillButton(it) }
            }
        }
    }
}
