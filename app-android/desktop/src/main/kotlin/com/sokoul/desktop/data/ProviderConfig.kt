package com.sokoul.desktop.data

import java.io.File
import java.util.Properties

data class ProviderStatus(val id: String, val label: String, val configured: Boolean, val source: String?)

object ProviderConfig {
    private val providers = listOf(
        "tmdb" to "TMDB",
        "realdebrid" to "Real-Debrid",
        "tvdb" to "TheTVDB",
        "fanart" to "fanart.tv",
        "motn" to "Movie of the Night",
        "watchmode" to "Watchmode",
        "tastedive" to "TasteDive",
        "tvmaze" to "TVmaze",
        "omdb" to "OMDb",
        "opensubtitles" to "OpenSubtitles",
        "subdl" to "Subdl",
    )

    fun statuses(db: DesktopDb): List<ProviderStatus> {
        val local = loadLocalProperties()
        return providers.map { (id, label) ->
            val override = db.setting("api_key_$id")
            val localKey = local.getProperty("sokoul.key.$id")
            ProviderStatus(
                id = id,
                label = label,
                configured = !override.isNullOrBlank() || !localKey.isNullOrBlank(),
                source = when {
                    !override.isNullOrBlank() -> "clé desktop"
                    !localKey.isNullOrBlank() -> "configuration locale"
                    else -> null
                },
            )
        }
    }

    fun key(db: DesktopDb, id: String): String? {
        val override = db.setting("api_key_$id")
        if (!override.isNullOrBlank()) return override
        return loadLocalProperties().getProperty("sokoul.key.$id")?.takeIf { it.isNotBlank() }
    }

    private fun loadLocalProperties(): Properties {
        val candidates = listOf(
            File("local.properties"),
            File("app-android", "local.properties"),
            File("..", "local.properties"),
        )
        val file = candidates.firstOrNull { it.isFile } ?: return Properties()
        return Properties().apply { file.inputStream().use(::load) }
    }
}
