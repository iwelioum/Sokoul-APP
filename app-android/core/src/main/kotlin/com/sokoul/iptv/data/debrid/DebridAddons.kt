package com.sokoul.iptv.data.debrid

/** Extensions de recherche intégrées : interrogées ensemble, résultats fusionnés sans doublon. */
enum class SourceAddon(val label: String) { TORRENTIO("Torrentio"), COMET("Comet") }

/**
 * Adresses des extensions de recherche Real-Debrid. Le jeton Real-Debrid n'est jamais stocké dans les réglages :
 * il est ajouté au moment de la requête, et ces adresses ne sont jamais écrites dans un journal.
 */
object DebridAddons {
    const val TORRENTIO = "https://torrentio.strem.fun"
    const val COMET = "https://comet.elfhosted.com"

    /** Torrentio : releases françaises en tête, liens Real-Debrid (« [RD+] » = déjà prête). */
    fun torrentio(token: String): String = "$TORRENTIO/language=french%7Crealdebrid=$token"

    /** Comet : configuration JSON en base64 dans le chemin (format de `comet/core/models.py`, champs historiques). */
    fun comet(token: String): String = "$COMET/" + cometConfig(token)

    /**
     * Base64 standard sans « / » ni « + » (ils casseraient le chemin) : on ajoute des espaces au JSON,
     * sans effet sur son sens, jusqu'à obtenir un encodage sûr.
     */
    fun cometConfig(token: String): String {
        val esc = token.replace("\\", "\\\\").replace("\"", "\\\"")
        val base = """{"debridService":"realdebrid","debridApiKey":"$esc","cachedOnly":false,"removeTrash":true,"resultFormat":["all"],"maxResultsPerResolution":0,"maxSize":0"""
        for (pad in 0 until 64) {
            val b64 = java.util.Base64.getEncoder().encodeToString((base + " ".repeat(pad) + "}").toByteArray())
            if ('/' !in b64 && '+' !in b64) return b64
        }
        return java.util.Base64.getUrlEncoder().encodeToString((base + "}").toByteArray())
    }

    /**
     * Toutes les adresses à interroger : extensions intégrées activées, puis celles ajoutées à la main
     * (une par ligne ou séparées par des virgules, « /manifest.json » retiré).
     */
    fun urls(enabled: Set<SourceAddon>, custom: String, token: String?): List<String> {
        val builtIn = if (token.isNullOrBlank()) emptyList() else enabled.sortedBy { it.ordinal }.map {
            when (it) { SourceAddon.TORRENTIO -> torrentio(token); SourceAddon.COMET -> comet(token) }
        }
        val extra = custom.split(',', '\n').map { it.trim().removeSuffix("/manifest.json").trimEnd('/') }.filter { it.startsWith("http") }
        return (builtIn + extra).distinct()
    }

    /** Réglage → extensions activées (« TORRENTIO,COMET »). */
    fun parse(s: String?): Set<SourceAddon> =
        s?.split(',')?.mapNotNull { n -> SourceAddon.entries.firstOrNull { it.name == n.trim().uppercase() } }?.toSet().orEmpty()
}
