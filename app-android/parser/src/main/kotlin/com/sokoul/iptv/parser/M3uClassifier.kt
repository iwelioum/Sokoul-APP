package com.sokoul.iptv.parser

enum class M3uKind { LIVE, MOVIE, EPISODE }

/**
 * Les playlists M3U mélangent souvent chaînes, films et épisodes.
 * On les répartit d'après l'URL (chemins Xtream `/movie/`, `/series/`, extension vidéo)
 * puis d'après le nom (S01E02) et le groupe.
 */
object M3uClassifier {

    private val vodExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg")
    private val vodGroupHints = listOf("vod", "film", "movie", "cinéma", "cinema", "série", "serie", "series", "saison")

    fun classify(entry: M3uEntry): M3uKind {
        val path = entry.url.substringBefore('?').lowercase()
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        val looksEpisode = TitleTagger.episode(entry.name) != null
        return when {
            "/series/" in path -> if (looksEpisode) M3uKind.EPISODE else M3uKind.MOVIE
            "/movie/" in path || "/movies/" in path -> M3uKind.MOVIE
            "/live/" in path -> M3uKind.LIVE
            ext in vodExtensions -> if (looksEpisode) M3uKind.EPISODE else M3uKind.MOVIE
            ext == "m3u8" || ext == "ts" -> M3uKind.LIVE
            entry.group?.lowercase()?.let { g -> vodGroupHints.any { it in g } } == true && entry.attrs["tvg-id"].isNullOrBlank() ->
                if (looksEpisode) M3uKind.EPISODE else M3uKind.MOVIE
            else -> M3uKind.LIVE
        }
    }
}
