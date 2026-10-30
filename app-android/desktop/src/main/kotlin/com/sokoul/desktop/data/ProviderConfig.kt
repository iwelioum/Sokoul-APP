package com.sokoul.desktop.data

data class ProviderStatus(val id: String, val label: String, val configured: Boolean, val source: String?, val hint: String?)

/**
 * Clés des services externes : celle saisie dans Réglages (base protégée par ACL) l'emporte, sinon la clé intégrée
 * au build ([DesktopKeys], depuis `local.properties`). Jamais affichée en entier ni journalisée.
 */
object ProviderConfig {
    val providers = listOf(
        "tmdb" to "TMDB",
        "realdebrid" to "Real-Debrid",
        "omdb" to "OMDb",
        "fanart" to "fanart.tv",
        "tvdb" to "TheTVDB",
        "tvmaze" to "TVmaze",
        "watchmode" to "Watchmode",
        "tastedive" to "TasteDive",
        "motn" to "Movie of the Night",
        "opensubtitles" to "OpenSubtitles",
        "subdl" to "Subdl",
    )

    /** Remplaçable dans les tests. */
    internal var builtIn: () -> Map<String, String> = { DesktopKeys.all }

    private fun settingKey(id: String) = "api_key_$id"

    fun statuses(db: DesktopDb): List<ProviderStatus> = providers.map { (id, label) ->
        val override = db.setting(settingKey(id))?.takeIf { it.isNotBlank() }
        val built = builtIn()[id]?.takeIf { it.isNotBlank() }
        ProviderStatus(
            id = id, label = label, configured = override != null || built != null,
            source = when { override != null -> "Clé saisie"; built != null -> "Clé intégrée"; else -> null },
            hint = (override ?: built)?.let(::hint),
        )
    }

    fun key(db: DesktopDb, id: String): String? =
        db.setting(settingKey(id))?.takeIf { it.isNotBlank() } ?: builtIn()[id]?.takeIf { it.isNotBlank() }

    /** Clé saisie dans Réglages ; vide = revenir à la clé intégrée. */
    fun setKey(db: DesktopDb, id: String, value: String?) = db.putSetting(settingKey(id), value?.trim()?.takeIf { it.isNotEmpty() })

    /** « •••• 0136 » : seulement les 4 derniers caractères. */
    fun hint(key: String): String = "•••• " + key.takeLast(4)
}
