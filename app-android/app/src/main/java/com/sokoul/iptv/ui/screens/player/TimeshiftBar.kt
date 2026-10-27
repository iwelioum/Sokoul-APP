package com.sokoul.iptv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.player.timeshift.LiveTimeshift
import com.sokoul.iptv.ui.components.KeyHint
import com.sokoul.iptv.ui.components.KeyHints
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.iptv.ui.util.formatClock

/** « −03:12 » : retard sur le direct. */
internal fun behindLabel(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "−%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "−%02d:%02d".format(s / 60, s % 60)
}

/**
 * Barre de la pause du direct : fenêtre enregistrée (du début du tampon au direct), position regardée,
 * retard en grand et légendes des touches. Visible pendant la pause et quelques secondes après une action.
 */
@Composable
internal fun TimeshiftBar(
    s: LiveTimeshift.State, channel: String, modifier: Modifier = Modifier,
    // tactile : boutons et barre touchable (à la télécommande, ◀ ▶ OK ▲ ▼ font la même chose)
    onSeek: (Long) -> Unit = {}, onToggle: () -> Unit = {}, onLive: () -> Unit = {},
) {
    val touch = LocalLayout.current.isTouch
    val side = if (LocalLayout.current.isCompact) 16.dp else 40.dp
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, SokoulColors.black(0.88f), SokoulColors.black(0.95f))))
            .padding(start = side, end = side, top = 56.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Icon(if (s.paused) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(34.dp), tint = SokoulColors.OnVideo)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(if (s.paused) "PAUSE DU DIRECT" else if (s.atLive) "EN DIRECT" else "EN DIFFÉRÉ", style = Type.Kicker)
                Text(channel, style = Type.PlayerTitle, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (s.atLive && !s.paused) "Direct" else behindLabel(s.behindMs), style = Type.ScreenTitle, color = if (s.atLive && !s.paused) SokoulColors.Live else SokoulColors.OnVideo)
                Text("sur le direct", style = Type.Meta, color = SokoulColors.TextMuted)
            }
        }
        // fenêtre : début du tampon → direct ; la position regardée en blanc, le reste enregistré en gris
        BoxWithConstraints(
            Modifier.padding(top = 16.dp).fillMaxWidth().height(if (touch) 32.dp else 14.dp)
                // tap sur la barre : aller à cet instant de la fenêtre enregistrée
                .then(if (touch) Modifier.pointerInput(s.start, s.end, s.position) {
                    detectTapGestures { o -> onSeek(timeshiftTapDelta(o.x / size.width, s.start, s.end, s.position)) }
                } else Modifier),
        ) {
            val span = (s.end - s.start).coerceAtLeast(1)
            val f = ((s.position - s.start).toFloat() / span).coerceIn(0f, 1f)
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(5.dp).background(SokoulColors.white(0.25f), Radius.Tiny))
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(f).height(5.dp).background(SokoulColors.Accent, Radius.Tiny))
            Box(Modifier.align(Alignment.CenterStart).offset(x = maxWidth * f - 7.dp).size(14.dp).background(SokoulColors.OnVideo, Radius.Round))
        }
        Row(Modifier.padding(top = 6.dp).fillMaxWidth()) {
            Text(formatClock(s.start), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(SokoulColors.Live, Radius.Round))
                Text(" DIRECT ${formatClock(s.end)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SokoulColors.TextSecondary)
            }
        }
        KeyHints(
            listOf(KeyHint("OK", if (s.paused) "Reprendre" else "Pause"), KeyHint("◀", "−10 s"), KeyHint("▶", "+30 s"), KeyHint("▲", "Retour au direct"), KeyHint("▼", "Masquer")),
            Modifier.padding(top = 12.dp),
        )
        if (touch) Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(PlayerAction("−10 s", Icons.Default.Replay10) { onSeek(-10_000) })
            PillButton(PlayerAction(if (s.paused) "Reprendre" else "Pause", if (s.paused) Icons.Default.PlayArrow else Icons.Default.Pause) { onToggle() })
            PillButton(PlayerAction("+30 s", Icons.Default.Forward30) { onSeek(30_000) })
            if (!s.atLive) PillButton(PlayerAction("Retour au direct", Icons.Default.LiveTv) { onLive() })
        }
    }
}

/** Repère discret en haut à droite quand on regarde en différé (barre masquée). */
@Composable
internal fun DelayBadge(behindMs: Long, modifier: Modifier = Modifier) {
    Row(
        modifier.background(SokoulColors.black(0.6f), Radius.Card).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(14.dp).background(SokoulColors.Accent))
        Text("DIFFÉRÉ ${behindLabel(behindMs)}", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = SokoulColors.OnVideo)
    }
}

/** Tap à la fraction [f] de la barre (0 = début du tampon, 1 = direct) : déplacement depuis la position regardée. */
internal fun timeshiftTapDelta(f: Float, start: Long, end: Long, position: Long): Long =
    (start + (end - start) * f.coerceIn(0f, 1f)).toLong() - position
