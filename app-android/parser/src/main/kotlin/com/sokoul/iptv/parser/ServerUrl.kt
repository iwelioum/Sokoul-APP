package com.sokoul.iptv.parser

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Identifiants Xtream extraits d'une URL de playlist. */
data class XtreamCredentials(val server: String, val username: String, val password: String)

object ServerUrl {

    private val knownEndpoints = listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php")

    /**
     * Normalise l'adresse saisie par l'utilisateur :
     * - ajoute `http://` si le schéma manque (la plupart des panels sont en HTTP) ;
     * - retire la requête, les endpoints connus et les slashs de fin ;
     * - conserve le port et un éventuel préfixe de chemin.
     */
    fun normalize(raw: String): String {
        var s = raw.trim()
        require(s.isNotEmpty()) { "L'adresse du serveur est vide" }
        if (!s.contains("://")) s = "http://$s"
        s = s.substringBefore('?').substringBefore('#').trimEnd('/')
        for (endpoint in knownEndpoints) {
            if (s.endsWith(endpoint, ignoreCase = true)) s = s.dropLast(endpoint.length)
        }
        s = s.trimEnd('/')
        val scheme = s.substringBefore("://").lowercase()
        require(scheme == "http" || scheme == "https") { "Protocole non supporté : $scheme" }
        val rest = s.substringAfter("://")
        require(rest.isNotBlank() && !rest.startsWith("/")) { "Adresse du serveur invalide" }
        return "$scheme://$rest"
    }

    /**
     * Reconnaît une URL M3U générée par un panel Xtream
     * (`http://h:p/get.php?username=u&password=p&type=m3u_plus`) pour proposer
     * le mode Xtream, beaucoup plus riche (EPG, VOD détaillée, catch-up).
     */
    fun extractXtream(url: String): XtreamCredentials? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val path = uri.rawPath ?: return null
        if (!path.endsWith("/get.php", true) && !path.endsWith("/player_api.php", true)) return null
        val query = uri.rawQuery ?: return null
        val params = query.split('&').mapNotNull {
            val k = it.substringBefore('=', "")
            if (k.isEmpty()) null else k.lowercase() to decode(it.substringAfter('=', ""))
        }.toMap()
        val user = params["username"]?.takeIf { it.isNotEmpty() } ?: return null
        val pass = params["password"]?.takeIf { it.isNotEmpty() } ?: return null
        return XtreamCredentials(normalize(url), user, pass)
    }

    /** Encode un segment de chemin (identifiants avec caractères spéciaux). */
    fun encodeSegment(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun encodeQuery(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
