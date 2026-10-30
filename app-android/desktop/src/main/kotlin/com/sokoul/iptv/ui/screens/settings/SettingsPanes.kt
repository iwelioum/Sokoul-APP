package com.sokoul.iptv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type

/** Les 10 rubriques des réglages, dans l'ordre d'affichage. */
enum class SettingsCategory(val label: String, val icon: ImageVector, val blurb: String) {
    ACCOUNT("Compte et playlists", Icons.Default.AccountCircle, "Vos abonnements, leur synchronisation et leur mise à jour."),
    PLAYBACK("Lecture", Icons.Default.PlayCircle, "Langues, sous-titres et enchaînement des épisodes. Les réglages techniques sont sous « Avancé »."),
    TV("Télévision et guide", Icons.Default.LiveTv, "Démarrage, sens du zapping et guide des programmes."),
    LIBRARY("Films et séries", Icons.Default.Movie, "Fiches TMDB, regroupement des doublons et version lancée en premier."),
    DEBRID("Real-Debrid", Icons.Default.Bolt, "Films et séries en 4K depuis votre compte Real-Debrid, sans utiliser la connexion IPTV."),
    PARENTAL("Contrôle parental", Icons.Default.Lock, "Code parental, groupes masqués et verrouillés."),
    PROFILES("Profils", Icons.Default.People, "Un profil par personne : sa liste, sa reprise et ses recommandations. Le profil Enfant ne voit que les groupes adaptés."),
    SOURCES("Sources de données", Icons.Default.Cloud, "Services facultatifs en complément de TMDB : notes, « Aussi sur », sous-titres, logos."),
    BACKUP("Sauvegarde", Icons.Default.Backup, "Exporter ou restaurer toute la configuration."),
    ABOUT("À propos", Icons.Default.Info, "Version de l'application."),
}

/**
 * Réglages en deux volets de la tablette paysage : rubriques à gauche, contenu de la rubrique à droite.
 * PC : seule la mise en page de la tablette paysage est reprise (la fenêtre est toujours « étendue »).
 */
@Composable
fun SettingsPanes(
    title: String = "Réglages",
    initial: SettingsCategory = SettingsCategory.ACCOUNT,
    pane: @Composable (SettingsCategory, Modifier) -> Unit,
) {
    val l = LocalLayout.current
    var selected by rememberSaveable { mutableStateOf(initial) }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(SokoulColors.Surface.copy(alpha = 0.55f))
                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, Modifier.padding(start = 16.dp, top = 28.dp, bottom = 18.dp), style = Type.ScreenTitle)
            SettingsCategory.entries.forEach { c -> CategoryRow(c, selected = c == selected, modifier = Modifier) { selected = c } }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp, end = l.margin)) {
            PaneHeader(selected, Modifier.padding(top = 28.dp))
            pane(selected, Modifier.weight(1f).fillMaxWidth())
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
private fun CategoryRow(c: SettingsCategory, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        style = FocusStyle.Row, onClick = onClick, modifier = modifier.fillMaxWidth(),
        color = if (selected) SokoulColors.AccentTint else androidx.compose.ui.graphics.Color.Transparent,
    ) { focused ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(c.icon, null, Modifier.size(22.dp), tint = if (selected || focused) SokoulColors.Accent else SokoulColors.TextSecondary)
            Text(
                c.label, Modifier.padding(start = 14.dp).weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                color = if (selected || focused) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
            )
        }
    }
}
