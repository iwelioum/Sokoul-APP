package com.sokoul.iptv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.ScreenHeader
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/** Les 8 rubriques des réglages, dans l'ordre d'affichage. */
enum class SettingsCategory(val label: String, val icon: ImageVector, val blurb: String) {
    ACCOUNT("Compte et playlists", Icons.Default.AccountCircle, "Vos abonnements, leur synchronisation et leur mise à jour."),
    PLAYBACK("Lecture", Icons.Default.PlayCircle, "Langues, sous-titres et enchaînement des épisodes. Les réglages techniques sont sous « Avancé »."),
    TV("Télévision et guide", Icons.Default.LiveTv, "Démarrage, sens du zapping et guide des programmes."),
    LIBRARY("Films et séries", Icons.Default.Movie, "Fiches TMDB, regroupement des doublons et version lancée en premier."),
    DEBRID("Real-Debrid", Icons.Default.Bolt, "Films et séries en 4K depuis votre compte Real-Debrid, sans utiliser la connexion IPTV."),
    PARENTAL("Contrôle parental", Icons.Default.Lock, "Code parental, groupes masqués et verrouillés."),
    SOURCES("Sources de données", Icons.Default.Cloud, "Services facultatifs en complément de TMDB : notes, « Aussi sur », sous-titres, logos."),
    BACKUP("Sauvegarde", Icons.Default.Backup, "Exporter ou restaurer toute la configuration."),
    ABOUT("À propos", Icons.Default.Info, "Version de l'application."),
}

/** Délai avant que la rubrique suive le focus (pas de chargement à chaque rubrique traversée). */
const val SETTINGS_FOLLOW_MS = 250L

/**
 * Réglages en deux volets (TV) : rubriques à gauche, contenu de la rubrique à droite.
 * - la rubrique suit le focus après [SETTINGS_FOLLOW_MS] ; OK ou ▶ entre dans le volet ;
 * - ◀ ou Retour depuis le volet revient sur la rubrique choisie (jamais sur une voisine) ;
 * - téléphone : liste des rubriques, puis une page par rubrique (Retour revient à la liste).
 */
@Composable
fun SettingsPanes(
    title: String = "Réglages",
    pane: @Composable (SettingsCategory, Modifier) -> Unit,
) {
    val l = LocalLayout.current
    var selected by rememberSaveable { mutableStateOf(SettingsCategory.ACCOUNT) }
    val cats = SettingsCategory.entries
    val requesters = remember { cats.associateWith { FocusRequester() } }

    if (l.isTouch) {
        var open by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
        BackHandler(enabled = open != null) { open = null }
        val o = open
        if (o == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = l.margin)) {
                ScreenHeader(title)
                cats.forEach { c -> CategoryRow(c, selected = false, chevron = true, modifier = Modifier) { selected = c; open = c } }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                PaneHeader(o, Modifier.padding(start = l.margin, end = l.margin, top = 16.dp))
                pane(o, Modifier.weight(1f).fillMaxWidth())
            }
        }
        return
    }

    var hovered by remember { mutableStateOf<SettingsCategory?>(null) }
    var inPane by remember { mutableStateOf(false) }
    val paneFocus = remember { FocusRequester() }
    // la rubrique suit le focus une fois le curseur posé
    LaunchedEffect(hovered) {
        val h = hovered ?: return@LaunchedEffect
        if (h != selected) { delay(SETTINGS_FOLLOW_MS); selected = h }
    }
    BackHandler(enabled = inPane) { runCatching { requesters.getValue(selected).requestFocus() } }
    // à l'ouverture : focus sur la rubrique choisie
    LaunchedEffect(Unit) {
        repeat(10) { withFrameNanos { }; if (runCatching { requesters.getValue(selected).requestFocus() }.isSuccess) return@LaunchedEffect }
    }

    Row(Modifier.fillMaxSize().padding(top = l.safeTop)) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(SokoulColors.Surface.copy(alpha = 0.55f))
                .padding(start = l.margin - 16.dp, end = 12.dp)
                // ◀ depuis le volet : retour sur la rubrique choisie, pas sur la plus proche
                .focusProperties { enter = { requesters.getValue(selected) } }
                // ▶ : on entre en haut du volet de la rubrique sous le curseur (pas sur la ligne la plus proche)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionRight) return@onPreviewKeyEvent false
                    hovered?.let { selected = it }
                    runCatching { paneFocus.requestFocus() }.isSuccess
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, Modifier.padding(start = 16.dp, top = 28.dp, bottom = 18.dp), style = Type.ScreenTitle)
            cats.forEach { c ->
                CategoryRow(
                    c, selected = c == selected, chevron = false,
                    modifier = Modifier.focusRequester(requesters.getValue(c)),
                    onFocus = { hovered = c },
                ) {
                    selected = c; hovered = c
                    runCatching { paneFocus.requestFocus() }
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp, end = l.margin)
                .onFocusChanged { inPane = it.hasFocus },
        ) {
            PaneHeader(selected, Modifier.padding(top = 28.dp))
            Box(Modifier.weight(1f).fillMaxWidth().focusRequester(paneFocus).focusGroup()) {
                pane(selected, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun PaneHeader(c: SettingsCategory, modifier: Modifier) {
    Column(modifier.padding(bottom = 12.dp)) {
        Text(c.label, style = Type.Section)
        Text(c.blurb, Modifier.padding(top = 4.dp), style = Type.Meta)
    }
}

@Composable
private fun CategoryRow(
    c: SettingsCategory, selected: Boolean, chevron: Boolean, modifier: Modifier,
    onFocus: (() -> Unit)? = null, onClick: () -> Unit,
) {
    FocusSurface(
        style = FocusStyle.Row, onClick = onClick, onFocus = onFocus, modifier = modifier.fillMaxWidth(),
        color = if (selected) SokoulColors.AccentTint else androidx.compose.ui.graphics.Color.Transparent,
    ) { focused ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(c.icon, null, Modifier.size(22.dp), tint = if (selected || focused) SokoulColors.Accent else SokoulColors.TextSecondary)
            Text(
                c.label, Modifier.padding(start = 14.dp).weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                color = if (selected || focused) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
            )
            if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SokoulColors.TextMuted)
        }
    }
}
