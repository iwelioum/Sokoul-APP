package com.sokoul.iptv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.theme.LocalReducedMotion
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors

/** Couleur de remplissage d'une barre d'avancement. */
enum class ProgressTone(val fill: Color) {
    /** Reprise d'un film, saga vue, téléchargement. */
    Accent(SokoulColors.Accent),
    /** Programme en direct, enregistrement en cours. */
    Live(SokoulColors.Live),
    /** Dans un bouton blanc ou sur la vidéo. */
    OnVideo(SokoulColors.OnVideo),
}

/**
 * La seule barre d'avancement de l'app. [onImage] : piste sombre (posée sur une affiche ou une vidéo),
 * sinon piste claire translucide (listes, panneaux). Remplissage minimal visible dès 2 %.
 */
@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    tone: ProgressTone = ProgressTone.Accent,
    thickness: Dp = 4.dp,
    onImage: Boolean = true,
    track: Color = if (onImage) SokoulColors.black(0.5f) else SokoulColors.white(0.14f),
) {
    Box(modifier.fillMaxWidth().height(thickness).background(track, Radius.Tiny)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0.02f, 1f)).background(tone.fill, Radius.Tiny))
    }
}

/** Seule roue de chargement autorisée : mise en mémoire tampon du lecteur et attente d'une action courte. */
@Composable
fun SokoulSpinner(modifier: Modifier = Modifier, size: Dp = 40.dp, color: Color = SokoulColors.Accent) {
    CircularProgressIndicator(modifier.size(size), color = color, strokeWidth = size / 12, trackColor = SokoulColors.white(0.08f))
}

/**
 * Interrupteur oui/non (réglages). Purement visuel : c'est la ligne entière qui est focalisable et
 * cliquable ; [focused] inverse les couleurs quand la ligne a le focus (fond blanc).
 */
@Composable
fun SokoulSwitch(checked: Boolean, modifier: Modifier = Modifier, focused: Boolean = false, enabled: Boolean = true) {
    val reduced = LocalReducedMotion.current
    val p by animateFloatAsState(if (checked) 1f else 0f, if (reduced) snap() else Motion.focusSpring(), label = "switch")
    val trackOn = if (enabled) SokoulColors.Accent else SokoulColors.TextMuted
    val trackOff = if (focused) SokoulColors.black(0.18f) else SokoulColors.white(0.16f)
    Box(
        modifier.size(SwitchW, SwitchH)
            .semantics { role = Role.Switch; stateDescription = if (checked) "Activé" else "Désactivé" }
            .background(androidx.compose.ui.graphics.lerp(trackOff, trackOn, p), Radius.Pill)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = (SwitchW - SwitchH) * p).size(SwitchH - 6.dp)
                .background(if (focused && !checked) SokoulColors.OnLight else SokoulColors.OnVideo, Radius.Card)
        )
    }
}

private val SwitchW = 46.dp
private val SwitchH = 26.dp
