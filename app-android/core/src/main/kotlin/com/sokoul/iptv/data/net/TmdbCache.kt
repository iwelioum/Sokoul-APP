package com.sokoul.iptv.data.net

/** Réponse TMDB gardée en cache : texte JSON et date de l'appel. */
data class TmdbCached(val json: String, val fetchedAt: Long)

/** Cache des réponses TMDB : table Room `tmdb_cache` sur Android, table SQLite sur PC. */
interface TmdbCache {
    suspend fun get(key: String): TmdbCached?
    suspend fun put(key: String, json: String, fetchedAt: Long)
}

/** Journal du code partagé : branché sur Timber par l'app Android, sur la sortie d'erreur sur PC. */
object CoreLog {
    @Volatile var warn: (String, Throwable?) -> Unit = { _, _ -> }
}
