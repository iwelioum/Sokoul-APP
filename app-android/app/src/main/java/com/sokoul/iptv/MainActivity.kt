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
        val pid = intent?.getLongExtra(EXTRA_PLAY_PLAYLIST, 0) ?: 0
        val cid = intent?.getStringExtra(EXTRA_PLAY_CHANNEL)
        if (pid > 0 && cid != null) pendingPlay.value = PlayIntent(pid, cid)
    }

    private fun isTelevision(): Boolean {
        val ui = getSystemService(UiModeManager::class.java)
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

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
    }
}
