package com.sokoul.iptv

import android.Manifest
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import com.sokoul.iptv.ui.AppRoot
import com.sokoul.iptv.ui.theme.LocalIsTv
import com.sokoul.iptv.ui.theme.SokoulTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Demande de lecture provenant d'une notification (rappel de programme). */
data class PlayIntent(val playlistId: Long, val channelId: String)

/** État partagé entre l'activité et l'écran de lecture (image dans l'image). */
object PlaybackUiState {
    /** Vrai quand un lecteur plein écran est actif et en lecture : autorise le PiP. */
    @Volatile var pipEligible: Boolean = false
    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()
    internal fun setInPip(v: Boolean) { _inPip.value = v }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingPlay = MutableStateFlow<PlayIntent?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val isTv = isTelevision()
        setContent {
            CompositionLocalProvider(LocalIsTv provides isTv) {
                SokoulTheme {
                    AppRoot(pendingPlay = pendingPlay, onPlayConsumed = { pendingPlay.value = null })
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && !isTv &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // rangée « À suivre » de Google TV : sokoul://resume/…
        if (intent?.action == Intent.ACTION_VIEW) com.sokoul.iptv.tv.DeepLinks.parse(intent.dataString)?.let { com.sokoul.iptv.tv.DeepLinks.pending.value = it }
        val pid = intent?.getLongExtra(EXTRA_PLAY_PLAYLIST, 0) ?: 0
        val cid = intent?.getStringExtra(EXTRA_PLAY_CHANNEL)
        // L'activité est exportée (lanceur) : n'importe quelle app peut envoyer ces extras.
        // On ne garde qu'un identifiant plausible ; le lecteur vérifie ensuite l'existence de la chaîne
        // et applique le contrôle parental.
        if (pid > 0 && cid != null && cid.length in 1..64 && cid.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            pendingPlay.value = PlayIntent(pid, cid)
        }
        intent?.removeExtra(EXTRA_PLAY_PLAYLIST)
        intent?.removeExtra(EXTRA_PLAY_CHANNEL)
        // notification « Nouvel épisode » : fiche de la série (même contrôle d'identifiant que pour une chaîne)
        val spid = intent?.getLongExtra(EXTRA_OPEN_SERIES_PLAYLIST, 0) ?: 0
        val sid = intent?.getStringExtra(EXTRA_OPEN_SERIES)
        if (spid > 0 && sid != null && sid.length in 1..64 && sid.all { it.isLetterOrDigit() || it == '-' || it == '_' }) pendingSeries.value = sid
        intent?.removeExtra(EXTRA_OPEN_SERIES_PLAYLIST)
        intent?.removeExtra(EXTRA_OPEN_SERIES)
    }

    private fun isTelevision(): Boolean {
        // test des mises en page tactiles sur le boîtier : adb shell setprop debug.sokoul.touch 1
        if (systemProperty("debug.sokoul.touch") == "1") return false
        val ui = getSystemService(UiModeManager::class.java)
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    @android.annotation.SuppressLint("PrivateApi")
    private fun systemProperty(name: String): String? = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    }.getOrNull()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPossible()
    }

    fun enterPipIfPossible(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !PlaybackUiState.pipEligible) return false
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }.getOrDefault(false)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlaybackUiState.setInPip(isInPictureInPictureMode)
    }

    companion object {
        const val EXTRA_PLAY_PLAYLIST = "play_playlist"
        const val EXTRA_PLAY_CHANNEL = "play_channel"
        const val EXTRA_OPEN_SERIES_PLAYLIST = "open_series_playlist"
        const val EXTRA_OPEN_SERIES = "open_series"
        /** Série à ouvrir (notification « Nouvel épisode »), consommée par la navigation. */
        val pendingSeries = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    }
}
