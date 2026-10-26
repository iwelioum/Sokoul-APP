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

/** Recherches récentes : la nouvelle en tête, sans doublon (casse ignorée), 8 au plus, 2 lettres minimum. */
object RecentSearches {
    const val MAX = 8
    fun add(list: List<String>, q: String): List<String> {
        val t = q.trim().replace(Regex("\\s+"), " ").take(60)
        if (t.length < 2) return list
        return (listOf(t) + list.filterNot { it.equals(t, ignoreCase = true) }).take(MAX)
    }
}

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
    /** TV : au démarrage, ouvrir directement la dernière chaîne regardée (sinon l'Accueil). */
    val resumeLastChannel: Boolean = false,
    /** Zapping : ▲ = chaîne suivante (numéro plus grand) au lieu de la précédente. */
    val zapInverted: Boolean = false,
    val lastChannelId: String = "",
    val showClock: Boolean = true,
    val channelNumbers: Boolean = true,
    /** Une seule fiche par film/série : les doublons (VF, MULTI, 4K…) deviennent des versions. */
    val groupDuplicates: Boolean = true,
    /** Ordre de préférence des langues pour choisir la version lancée (VF,MULTI,VOSTFR,VO). */
    val vodLanguages: String = DEFAULT_VOD_LANGUAGES,
    /** Format d'image du lecteur (0 = adapter, 1 = rogner, 2 = étirer), mémorisé d'une lecture à l'autre. */
    val resizeMode: Int = 0,
    /** Clés saisies dans Réglages pour les services de données (id du service → clé) ; vide = clé du build. */
    val apiKeys: Map<String, String> = emptyMap(),
    /** Guide TV complémentaire xmltv.fr (France, Belgique, Suisse) : gros fichier, désactivé par défaut. */
    val epgXmltvFr: Boolean = false,
) {
    /** Bornes appliquées à chaque écriture : une valeur aberrante (import, bug d'UI) ne doit pas casser l'app. */
    fun sanitized(): AppSettings = copy(
        userAgent = userAgent.trim().take(MAX_USER_AGENT).filter { it >= ' ' }.ifBlank { DEFAULT_USER_AGENT },
        audioLanguage = audioLanguage.trim().take(8),
        subtitleLanguage = subtitleLanguage.trim().take(8),
        epgOffsetMinutes = epgOffsetMinutes.coerceIn(-12 * 60, 12 * 60),
        playlistRefreshHours = playlistRefreshHours.coerceIn(1, 168),
        epgRefreshHours = epgRefreshHours.coerceIn(1, 168),
        parentalPin = parentalPin.takeIf { PinHasher.isValidStored(it) } ?: "",
        tmdbApiKey = cleanKey(tmdbApiKey),
        apiKeys = apiKeys.filterKeys { it.matches(Regex("[a-z0-9]{2,20}")) }.mapValues { cleanKey(it.value) }.filterValues { it.isNotEmpty() },
        lastChannelId = lastChannelId.take(64),
        vodLanguages = com.sokoul.iptv.data.repo.VersionPicker.parseOrder(vodLanguages).joinToString(","),
        resizeMode = resizeMode.coerceIn(0, 2),
    )

    companion object {
        const val DEFAULT_USER_AGENT = "SokoulTV/2.0 (Linux; Android) ExoPlayer"
        const val MAX_USER_AGENT = 256
        const val DEFAULT_VOD_LANGUAGES = "VF,MULTI,VOSTFR,VO"

        /** Une clé d'API ne contient que lettres, chiffres, « . _ - » (pas d'espace, pas de saut de ligne collé). */
        fun cleanKey(k: String): String = k.trim().take(256).filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
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
        val resize = intPreferencesKey("resize_mode")
        val resumeLast = booleanPreferencesKey("resume_last_channel")
        val zapInvert = booleanPreferencesKey("zap_inverted")
        val lastChannel = stringPreferencesKey("last_channel")
        val clock = booleanPreferencesKey("show_clock")
        val numbers = booleanPreferencesKey("channel_numbers")
        val group = booleanPreferencesKey("group_duplicates")
        val vodLangs = stringPreferencesKey("vod_languages")
        val xmltvFr = booleanPreferencesKey("epg_xmltv_fr")
    }

    private val API_KEY_PREFIX = "api_key_"

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
        resizeMode = this[K.resize] ?: 0,
        resumeLastChannel = this[K.resumeLast] ?: false,
        zapInverted = this[K.zapInvert] ?: false,
        lastChannelId = this[K.lastChannel] ?: "",
        showClock = this[K.clock] ?: true,
        channelNumbers = this[K.numbers] ?: true,
        groupDuplicates = this[K.group] ?: true,
        vodLanguages = this[K.vodLangs] ?: AppSettings.DEFAULT_VOD_LANGUAGES,
        apiKeys = asMap().entries.mapNotNull { (k, v) ->
            if (k.name.startsWith(API_KEY_PREFIX) && v is String) k.name.removePrefix(API_KEY_PREFIX) to v else null
        }.toMap(),
        epgXmltvFr = this[K.xmltvFr] ?: false,
    )

    private inline fun <reified T : Enum<T>> enumOr(v: String?, default: T): T =
        v?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    /** Dernier groupe ouvert par écran (« live », « guide »…), par playlist. */
    suspend fun lastGroup(screen: String, playlistId: Long): String? =
        context.dataStore.data.first()[stringPreferencesKey("last_group_${screen}_$playlistId")]

    suspend fun setLastGroup(screen: String, playlistId: Long, categoryId: String) {
        context.dataStore.edit { it[stringPreferencesKey("last_group_${screen}_$playlistId")] = categoryId }
    }

    /** Échecs consécutifs de saisie du code parental et fin du blocage en cours. */
    suspend fun pinAttempts(): Pair<Int, Long> {
        val p = context.dataStore.data.first()
        return (p[pinFailures] ?: 0) to (p[pinLockedUntil] ?: 0L)
    }

    suspend fun setPinAttempts(failures: Int, lockedUntil: Long) {
        context.dataStore.edit { it[pinFailures] = failures; it[pinLockedUntil] = lockedUntil }
    }

    /** Recherches récentes (8 au plus, la plus récente d'abord). */
    val recentSearches: Flow<List<String>> = context.dataStore.data.map { p ->
        p[recentKey]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun addRecentSearch(q: String) {
        context.dataStore.edit { p ->
            val now = p[recentKey]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            p[recentKey] = RecentSearches.add(now, q).joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() { context.dataStore.edit { it.remove(recentKey) } }

    /** Sagas regardées dans l'ordre du récit (identifiants de collection TMDB). */
    suspend fun sagaStoryOrder(collectionId: Int): Boolean =
        context.dataStore.data.first()[sagaStoryKey]?.split(',')?.contains(collectionId.toString()) == true

    suspend fun setSagaStoryOrder(collectionId: Int, story: Boolean) {
        context.dataStore.edit { p ->
            val ids = p[sagaStoryKey]?.split(',')?.filter { it.isNotBlank() }.orEmpty().toMutableSet()
            if (story) ids += collectionId.toString() else ids -= collectionId.toString()
            p[sagaStoryKey] = ids.joinToString(",")
        }
    }

    private val recentKey = stringPreferencesKey("recent_searches")
    private val sagaStoryKey = stringPreferencesKey("saga_story_order")
    private val pinFailures = intPreferencesKey("pin_failures")
    private val groupingRules = intPreferencesKey("grouping_rules")

    /** Version des règles de regroupement avec laquelle les clés actuelles ont été calculées. */
    suspend fun groupingRulesVersion(): Int = context.dataStore.data.first()[groupingRules] ?: 0
    suspend fun setGroupingRulesVersion(v: Int) { context.dataStore.edit { it[groupingRules] = v } }
    private val pinLockedUntil = longPreferencesKey("pin_locked_until")

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings()).sanitized()
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
            prefs[K.resize] = s.resizeMode
            prefs[K.resumeLast] = s.resumeLastChannel
            prefs[K.zapInvert] = s.zapInverted
            prefs[K.lastChannel] = s.lastChannelId
            prefs[K.clock] = s.showClock
            prefs[K.numbers] = s.channelNumbers
            prefs[K.group] = s.groupDuplicates
            prefs[K.vodLangs] = s.vodLanguages
            prefs[K.xmltvFr] = s.epgXmltvFr
            // clés des services : une préférence par service ; clé effacée → préférence retirée
            prefs.asMap().keys.filter { it.name.startsWith(API_KEY_PREFIX) }.forEach { prefs.remove(it) }
            s.apiKeys.forEach { (id, key) -> prefs[stringPreferencesKey(API_KEY_PREFIX + id)] = key }
        }
    }
}
