package com.sokoul.iptv.ui.screens.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sokoul.iptv.data.db.ProfileEntity
import com.sokoul.iptv.data.repo.ProfileRepository
import com.sokoul.iptv.ui.ParentalGate
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.PinDialog
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Écran « Qui regarde ? » : ouvert au lancement s'il y a plusieurs profils, ou depuis l'avatar. */
object WhoIsWatching {
    val open = MutableStateFlow(false)
    /** Posé une fois par lancement de l'app : on ne redemande pas à chaque retour à l'accueil. */
    @Volatile var askedThisLaunch = false
}

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    val repo: ProfileRepository,
    private val parental: ParentalGate,
) : ViewModel() {
    val profiles = repo.profiles
    val current = repo.current

    /**
     * Change de profil. Quitter un profil Enfant pour un profil adulte demande le code parental (s'il existe) :
     * [onNeedPin] est alors appelé et rien ne change.
     */
    fun choose(target: ProfileEntity, onNeedPin: () -> Unit, onDone: () -> Unit) = viewModelScope.launch {
        val cur = repo.current.value
        if (ProfileRules.needsPin(cur, target, parental.hasPin())) { onNeedPin(); return@launch }
        switch(target); onDone()
    }

    fun unlockAndChoose(pin: String, target: ProfileEntity, onError: (String) -> Unit, onDone: () -> Unit) = viewModelScope.launch {
        val r = parental.tryUnlock(pin)
        if (r.ok) { switch(target); onDone() } else onError(r.message ?: "Code incorrect")
    }

    private suspend fun switch(target: ProfileEntity) {
        repo.switchTo(target.id)
        // le déverrouillage éventuel ne doit pas profiter au profil suivant
        parental.lock()
    }
}

/** Règles pures (testées) du changement de profil. */
object ProfileRules {
    fun needsPin(current: ProfileEntity?, target: ProfileEntity, hasPin: Boolean): Boolean =
        hasPin && current?.kids == true && !target.kids && target.id != current.id
}

/** Avatar d'un profil : initiale sur la couleur du profil ; petit badge pour un profil Enfant. */
@Composable
fun ProfileAvatar(p: ProfileEntity?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = SokoulColors.avatar(p?.color ?: 0)
    Box(modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f))), Radius.Card),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (p?.name?.trim()?.firstOrNull() ?: 'S').uppercaseChar().toString(),
                style = Type.Initials.copy(fontSize = (size.value * 0.46f).sp), color = SokoulColors.OnVideo,
            )
        }
        if (p?.kids == true) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.38f).background(SokoulColors.Background, Radius.Round).padding(size * 0.04f),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.ChildCare, "Profil Enfant", Modifier.fillMaxSize(), tint = SokoulColors.Warning) }
        }
    }
}

/** « Qui regarde ? » en plein écran, au-dessus de l'app. */
@Composable
fun WhoIsWatchingOverlay() {
    val open by WhoIsWatching.open.collectAsStateWithLifecycle()
    if (!open) return
    val vm: ProfilesViewModel = hiltViewModel()
    val list by vm.profiles.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val close = { WhoIsWatching.open.value = false }
    val first = remember { FocusRequester() }
    val l = LocalLayout.current
    val tile = if (l.isTv) 132.dp else 96.dp

    BackHandler { close() }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(SokoulColors.AccentTint, SokoulColors.Background), radius = 1400f))
            .background(SokoulColors.bg(0.94f))
            // le focus reste dans l'écran : rien derrière ne doit être atteint au pavé
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Qui regarde ?", style = Type.Display, color = SokoulColors.TextPrimary)
            LazyRow(
                Modifier.padding(top = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(if (l.isTv) 36.dp else 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = l.margin, vertical = 16.dp),
            ) {
                items(list, key = { it.id }) { p ->
                    val isCurrent = p.id == current?.id
                    Column(Modifier.width(tile + 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FocusSurface(
                            onClick = { vm.choose(p, onNeedPin = { pinError = null; pinFor = p }, onDone = close) },
                            modifier = if (isCurrent) Modifier.focusRequester(first) else Modifier,
                            style = FocusStyle.Card,
                        ) { _ -> ProfileAvatar(p, size = tile) }
                        Text(
                            p.name, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrent) SokoulColors.TextPrimary else SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (p.kids) Text("Enfant", style = MaterialTheme.typography.labelMedium, color = SokoulColors.Warning)
                    }
                }
            }
            if (current?.kids != true) {
                Text(
                    "Ajouter ou modifier un profil : Réglages › Profils", Modifier.padding(top = 28.dp),
                    style = MaterialTheme.typography.bodyMedium, color = SokoulColors.TextMuted,
                )
            }
        }
    }
    LaunchedEffect(list.isNotEmpty(), current?.id) {
        repeat(20) { withFrameNanos { }; if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    pinFor?.let { target ->
        PinDialog(
            "Code parental pour quitter le profil Enfant",
            onResult = { pin -> vm.unlockAndChoose(pin, target, onError = { pinError = it }) { pinFor = null; close() } },
            onDismiss = { pinFor = null },
            error = pinError,
        )
    }
}

/** Profil en cours, fourni par la racine de l'app (null dans les tests d'écran : comportement adulte). */
val LocalProfile = androidx.compose.runtime.compositionLocalOf<ProfileEntity?> { null }

/** Avatar cliquable de la barre du haut : ouvre « Qui regarde ? ». */
@Composable
fun ProfileSwitchButton(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val current = LocalProfile.current
    FocusSurface(
        onClick = { WhoIsWatching.open.value = true }, modifier = modifier, style = FocusStyle.Chip,
        shape = Radius.Card, color = androidx.compose.ui.graphics.Color.Transparent,
    ) { _ -> ProfileAvatar(current, Modifier.padding(4.dp), size) }
}

/** Le profil en cours est-il un profil Enfant (réglages masqués, etc.) ? */
@Composable
fun isKidsProfile(): Boolean = LocalProfile.current?.kids == true

/**
 * Profil Enfant : sans les œuvres classées 12 ans et plus ou d'un genre adulte (`KidsRating`), partout où des
 * œuvres s'affichent (grilles, Explorer, recherche), pas seulement sur l'accueil.
 */
@Composable
fun List<com.sokoul.iptv.data.repo.Work>.forProfile(): List<com.sokoul.iptv.data.repo.Work> {
    val kids = isKidsProfile()
    return androidx.compose.runtime.remember(this, kids) { if (kids) filter(com.sokoul.iptv.data.repo.KidsRating::allowed) else this }
}


/** Choix « visible pour les enfants » d'un groupe (menu d'appui long). */
fun kidsAccessOptions(onPick: (Int) -> Unit): List<com.sokoul.iptv.ui.components.MenuOption> = listOf(
    com.sokoul.iptv.ui.components.MenuOption("Selon le nom du groupe (par défaut)") { onPick(com.sokoul.iptv.data.db.KidsAccess.AUTO) },
    com.sokoul.iptv.ui.components.MenuOption("Toujours visible pour les enfants") { onPick(com.sokoul.iptv.data.db.KidsAccess.ALLOWED) },
    com.sokoul.iptv.ui.components.MenuOption("Jamais visible pour les enfants") { onPick(com.sokoul.iptv.data.db.KidsAccess.BLOCKED) },
)
