package com.sokoul.iptv.data.net.providers

import com.sokoul.iptv.BuildConfig
import com.sokoul.iptv.data.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Services de données externes, tous **facultatifs** : TMDB reste la source principale.
 * Sans clé (ou après un refus), un service ne fait aucun appel et l'app garde son affichage habituel.
 */
enum class ApiService(
    val id: String,
    val label: String,
    /** Ce que le service apporte, tel qu'affiché dans Réglages. */
    val purpose: String,
    val needsKey: Boolean,
    /** Page d'inscription gratuite pour obtenir une clé. */
    val signupUrl: String? = null,
) {
    TVDB("tvdb", "TheTVDB", "Titres, résumés et vignettes d'épisodes quand TMDB n'en a pas", true, "https://thetvdb.com/api-information"),
    FANART("fanart", "fanart.tv", "Logos HD et fonds d'écran", true, "https://fanart.tv/get-an-api-key/"),
    TVMAZE("tvmaze", "TVmaze", "Date du prochain épisode des séries en cours", false),
    MOTN("motn", "Movie of the Night", "« Aussi sur Netflix, Prime Video… » (France)", true, "https://www.movieofthenight.com/about/api"),
    TASTEDIVE("tastedive", "TasteDive", "Recommandations « Vous aimerez aussi »", true, "https://tastedive.com/account/api_access"),
    OMDB("omdb", "OMDb", "Notes IMDb, Rotten Tomatoes et Metacritic", true, "https://www.omdbapi.com/apikey.aspx"),
    OPENSUBTITLES("opensubtitles", "OpenSubtitles", "Sous-titres en ligne (à la demande)", true, "https://www.opensubtitles.com/consumers"),
    SUBDL("subdl", "Subdl", "Sous-titres en ligne (à la demande)", true, "https://subdl.com/panel/api"),
    WATCHMODE("watchmode", "Watchmode", "Non utilisé : l'offre gratuite n'inclut pas la France", true, "https://api.watchmode.com/requestApiKey/"),
    IPTVORG("iptvorg", "iptv-org", "Logos et catégories de chaînes manquants", false),
    XMLTVFR("xmltvfr", "xmltv.fr", "Guide TV France, Belgique, Suisse (à activer)", false),
    REALDEBRID("realdebrid", "Real-Debrid", "Films et séries en 4K, sans utiliser la connexion IPTV", true, "https://real-debrid.com/apitoken"),
    ;

    /** Clé fournie au build (local.properties), vide si absente. */
    val buildKey: String
        get() = when (this) {
            TVDB -> BuildConfig.KEY_TVDB
            FANART -> BuildConfig.KEY_FANART
            MOTN -> BuildConfig.KEY_MOTN
            TASTEDIVE -> BuildConfig.KEY_TASTEDIVE
            OMDB -> BuildConfig.KEY_OMDB
            OPENSUBTITLES -> BuildConfig.KEY_OPENSUBTITLES
            SUBDL -> BuildConfig.KEY_SUBDL
            WATCHMODE -> BuildConfig.KEY_WATCHMODE
            REALDEBRID -> BuildConfig.KEY_REALDEBRID
            TVMAZE, IPTVORG, XMLTVFR -> ""
        }
}

/** Clé effective d'un service : celle saisie dans Réglages, sinon celle du build. */
@Singleton
class ApiKeys @Inject constructor(private val settings: SettingsStore) {
    suspend fun key(s: ApiService): String = resolve(s, settings.current().apiKeys)

    /** Le service peut-il être appelé (clé présente quand il en faut une) ? */
    suspend fun usable(s: ApiService): Boolean = !s.needsKey || key(s).isNotEmpty()

    companion object {
        fun resolve(s: ApiService, fromSettings: Map<String, String>): String =
            fromSettings[s.id]?.takeIf { it.isNotBlank() } ?: s.buildKey

        /** « Clé du fichier local » / « Clé saisie » / rien, pour Réglages (jamais la clé elle-même). */
        fun origin(s: ApiService, fromSettings: Map<String, String>): String? = when {
            !fromSettings[s.id].isNullOrBlank() -> "clé saisie"
            s.buildKey.isNotEmpty() -> "clé du fichier local"
            else -> null
        }

        /** Clé masquée pour l'affichage : « c16c…80be ». */
        fun masked(k: String): String = if (k.length <= 8) "••••" else k.take(4) + "…" + k.takeLast(4)
    }
}
