package com.sokoul.iptv.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class BufferProfile(val label: String) { LOW("Faible (zapping rapide)"), NORMAL("Normal"), HIGH("Élevé (connexion instable)") }
enum class LiveFormat(val label: String, val ext: String) { TS("MPEG-TS (.ts)", "ts"), HLS("HLS (.m3u8)", "m3u8") }
enum class DecoderMode(val label: String) { AUTO("Auto (matériel + FFmpeg)"), HARDWARE("Matériel uniquement"), SOFTWARE("Logiciel prioritaire") }

data class AppSettings(
    val activePlaylistId: Long = 0,
    val bufferProfile: BufferProfile = BufferProfile.NORMAL,
    val liveFormat: LiveFormat = LiveFormat.TS,
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    val userAgent: String = DEFAULT_USER_AGENT,
    val audioLanguage: String = "fr",
    val subtitleLanguage: String = "",
    val epgOffsetMinutes: Int = 0,
    val playlistRefreshHours: Int = 24,
    val epgRefreshHours: Int = 12,
    val parentalPin: String = "",
    val tmdbApiKey: String = "",
    val autoplayNextEpisode: Boolean = true,
    val resumeLastChannel: Boolean = false,
    val lastChannelId: String = "",
    val showClock: Boolean = true,
    val channelNumbers: Boolean = true,
) {
    companion object {
        const val DEFAULT_USER_AGENT = "SokoulTV/2.0 (Linux; Android) ExoPlayer"
    }
}

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val activePlaylist = longPreferencesKey("active_playlist")
        val buffer = stringPreferencesKey("buffer")
        val liveFormat = stringPreferencesKey("live_format")
        val decoder = stringPreferencesKey("decoder")
        val userAgent = stringPreferencesKey("user_agent")
        val audioLang = stringPreferencesKey("audio_lang")
        val subLang = stringPreferencesKey("sub_lang")
        val epgOffset = intPreferencesKey("epg_offset")
        val playlistRefresh = intPreferencesKey("playlist_refresh")
        val epgRefresh = intPreferencesKey("epg_refresh")
        val pin = stringPreferencesKey("parental_pin")
        val tmdb = stringPreferencesKey("tmdb_key")
        val autoplay = booleanPreferencesKey("autoplay_next")
        val resumeLast = booleanPreferencesKey("resume_last_channel")
        val lastChannel = stringPreferencesKey("last_channel")
        val clock = booleanPreferencesKey("show_clock")
        val numbers = booleanPreferencesKey("channel_numbers")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        activePlaylistId = this[K.activePlaylist] ?: 0,
        bufferProfile = enumOr(this[K.buffer], BufferProfile.NORMAL),
        liveFormat = enumOr(this[K.liveFormat], LiveFormat.TS),
        decoderMode = enumOr(this[K.decoder], DecoderMode.AUTO),
        userAgent = this[K.userAgent]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_USER_AGENT,
        audioLanguage = this[K.audioLang] ?: "fr",
        subtitleLanguage = this[K.subLang] ?: "",
        epgOffsetMinutes = this[K.epgOffset] ?: 0,
        playlistRefreshHours = this[K.playlistRefresh] ?: 24,
        epgRefreshHours = this[K.epgRefresh] ?: 12,
        parentalPin = this[K.pin] ?: "",
        tmdbApiKey = this[K.tmdb] ?: "",
        autoplayNextEpisode = this[K.autoplay] ?: true,
        resumeLastChannel = this[K.resumeLast] ?: false,
        lastChannelId = this[K.lastChannel] ?: "",
        showClock = this[K.clock] ?: true,
        channelNumbers = this[K.numbers] ?: true,
    )

    private inline fun <reified T : Enum<T>> enumOr(v: String?, default: T): T =
        v?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    /** Dernier groupe ouvert par écran (« live », « guide »…), par playlist. */
    suspend fun lastGroup(screen: String, playlistId: Long): String? =
        context.dataStore.data.first()[stringPreferencesKey("last_group_${screen}_$playlistId")]

    suspend fun setLastGroup(screen: String, playlistId: Long, categoryId: String) {
        context.dataStore.edit { it[stringPreferencesKey("last_group_${screen}_$playlistId")] = categoryId }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[K.activePlaylist] = s.activePlaylistId
            prefs[K.buffer] = s.bufferProfile.name
            prefs[K.liveFormat] = s.liveFormat.name
            prefs[K.decoder] = s.decoderMode.name
            prefs[K.userAgent] = s.userAgent
            prefs[K.audioLang] = s.audioLanguage
            prefs[K.subLang] = s.subtitleLanguage
            prefs[K.epgOffset] = s.epgOffsetMinutes
            prefs[K.playlistRefresh] = s.playlistRefreshHours
            prefs[K.epgRefresh] = s.epgRefreshHours
            prefs[K.pin] = s.parentalPin
            prefs[K.tmdb] = s.tmdbApiKey
            prefs[K.autoplay] = s.autoplayNextEpisode
            prefs[K.resumeLast] = s.resumeLastChannel
            prefs[K.lastChannel] = s.lastChannelId
            prefs[K.clock] = s.showClock
            prefs[K.numbers] = s.channelNumbers
        }
    }
}
