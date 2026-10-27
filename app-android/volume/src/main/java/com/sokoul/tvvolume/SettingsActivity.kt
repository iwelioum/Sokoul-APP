package com.sokoul.tvvolume

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Réglages : adresse de la TV, recherche automatique, essais des touches, activation du service. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Panel, background = Background)) {
                SettingsScreen(prefs, serviceEnabled = ::serviceEnabled, openAccessibility = ::openAccessibility)
            }
        }
    }

    private fun serviceEnabled(): Boolean {
        val me = ComponentName(this, VolumeKeyService::class.java).flattenToString()
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun openAccessibility() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
}

private val Accent = Color(0xFF6C5CE7)
private val Background = Color(0xFF0E0C1A)
private val Panel = Color(0xFF1A1730)
private val Soft = Color(0xFFB8B4D0)
private val SecondaryButton = Color(0xFF3A3560)
private val Ok = Color(0xFF55D88A)
private val Bad = Color(0xFFFF7A7A)

@Composable
private fun SettingsScreen(prefs: Prefs, serviceEnabled: () -> Boolean, openAccessibility: () -> Unit) {
    val scope = rememberCoroutineScope()
    val remote = remember { TclRemote() }
    var ip by remember { mutableStateOf(prefs.tvIp) }
    var mac by remember { mutableStateOf(prefs.tvMac) }
    var enabled by remember { mutableStateOf(serviceEnabled()) }
    var reach by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // état du service relu au retour des réglages d'accessibilité
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { enabled = serviceEnabled() } }
    LaunchedEffect(ip) {
        reach = if (TclProtocol.isPrivateIpv4(ip)) withContext(Dispatchers.IO) { remote.reachable(ip) } else null
    }

    fun send(key: TvKey) = scope.launch {
        if (!TclProtocol.isPrivateIpv4(ip)) { message = "Indiquez d'abord l'adresse de la TV"; return@launch }
        val ok = withContext(Dispatchers.IO) { remote.send(ip, key) }
        message = if (ok) "${key.label} envoyé à la TV" else "La TV ne répond pas à ${ip}"
        reach = ok
    }

    Column(
        Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 64.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Volume TCL", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(
            "Les touches Volume +, Volume − et Muet de la télécommande règlent le son de la TV TCL par le Wi-Fi. " +
                "Appui long sur Muet : mettre la TV en veille.",
            color = Soft, fontSize = 16.sp,
        )

        Section("1. Service de la télécommande") {
            StatusLine(enabled, if (enabled) "Activé : les touches de volume vont à la TV" else "Désactivé : activez « Volume TCL » dans Accessibilité")
            TvButton(onClick = openAccessibility, text = if (enabled) "Réglages d'accessibilité" else "Activer dans Accessibilité")
        }

        Section("2. La TV") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvTextField(
                    value = ip, onValueChange = { v -> ip = v.filter { it.isDigit() || it == '.' }.take(15); prefs.tvIp = ip },
                    label = "Adresse de la TV", width = 260, keyboardType = KeyboardType.Decimal,
                )
                TvButton(enabled = !searching, secondary = true, text = if (searching) "Recherche…" else "Rechercher la TV", onClick = {
                    scope.launch {
                        val self = TvFinder.localNetwork(context)?.first
                        if (self == null) { message = "Pas de réseau local"; return@launch }
                        searching = true
                        message = "Recherche de la TV sur le réseau…"
                        val found = TvFinder.scan(self)
                        searching = false
                        if (found.isEmpty()) message = "Aucune TV TCL trouvée (TV allumée, même Wi-Fi ?)"
                        else { ip = found.first(); prefs.tvIp = ip; message = "TV trouvée : ${found.joinToString()}" }
                    }
                })
            }
            when (reach) {
                true -> StatusLine(true, "TV connectée")
                false -> StatusLine(false, "TV injoignable (éteinte, ou adresse incorrecte)")
                null -> Unit
            }
        }

        Section("3. Essayer") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in listOf(TvKey.VOL_DOWN, TvKey.VOL_UP, TvKey.MUTE, TvKey.POWER)) {
                    TvButton(onClick = { send(k) }, text = k.label)
                }
            }
            message?.let { Text(it, color = Soft) }
        }

        Section("Allumage par le réseau (facultatif)") {
            Text(
                "Une TV éteinte coupe souvent son Wi-Fi. Si la vôtre a une « veille réseau », indiquez son adresse MAC : " +
                    "l'appui long sur Muet essaiera de l'allumer quand elle ne répond pas.",
                color = Soft, fontSize = 14.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvTextField(
                    value = mac, onValueChange = { v -> mac = v.take(17); prefs.tvMac = mac },
                    label = "Adresse MAC (AA:BB:CC:DD:EE:FF)", width = 360, keyboardType = KeyboardType.Text,
                )
                TvButton(secondary = true, text = "Allumer", onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { remote.wake(mac) }
                        message = if (ok) "Réveil demandé à la TV" else "Adresse MAC invalide"
                    }
                })
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        content()
    }
}

/**
 * Champ de saisie pour télécommande : le focus le traverse sans ouvrir le clavier ; OK passe en saisie,
 * ✓ (ou la perte du focus) en sort. Sinon le clavier s'ouvre à chaque passage et bloque les flèches.
 */
@Composable
private fun TvTextField(value: String, onValueChange: (String) -> Unit, label: String, width: Int, keyboardType: KeyboardType) {
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange, readOnly = !editing,
        label = { Text(label) }, singleLine = true,
        modifier = Modifier.width(width.dp)
            .onFocusChanged { if (!it.isFocused) editing = false }
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                val move = when (e.key) {
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                }
                when {
                    // en saisie, Haut / Bas sortent quand même du champ (Gauche / Droite déplacent le curseur)
                    move != null && (!editing || move == FocusDirection.Down || move == FocusDirection.Up) ->
                        { if (e.type == KeyEventType.KeyDown) focus.moveFocus(move); true }
                    editing || !ok -> false
                    e.type == KeyEventType.KeyUp -> { editing = true; keyboard?.show(); true }
                    else -> true
                }
            },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { editing = false; keyboard?.hide(); focus.moveFocus(FocusDirection.Down) }),
    )
}

/** Bouton lisible à 3 m : couleur pleine, et au focus un contour blanc et un léger grossissement. */
@Composable
private fun TvButton(text: String, onClick: () -> Unit, enabled: Boolean = true, secondary: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Button(
        onClick = onClick, enabled = enabled, interactionSource = source,
        modifier = Modifier.scale(if (focused) 1.08f else 1f),
        border = if (focused) BorderStroke(3.dp, Color.White) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) SecondaryButton else Accent, contentColor = Color.White,
            disabledContainerColor = SecondaryButton.copy(alpha = 0.5f), disabledContentColor = Soft,
        ),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Text((if (ok) "● " else "● ") + text, color = if (ok) Ok else Bad, fontSize = 16.sp)
}
