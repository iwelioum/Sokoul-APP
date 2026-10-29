package com.sokoul.iptv.data.debrid

/**
 * Contrat commun des services debrid.
 *
 * Les modèles sont volontairement ceux déjà utilisés par les résolveurs : un fournisseur
 * supplémentaire ne doit pas modifier la logique de sélection, de cache ou de lecture.
 */
interface DebridService {
    val providerName: String

    suspend fun configured(): Boolean
    suspend fun user(): RdUser
    suspend fun addMagnet(magnet: String): String
    suspend fun torrentInfo(id: String): RdTorrent
    suspend fun selectFiles(id: String, fileIds: List<Int>)
    suspend fun deleteTorrent(id: String)
    suspend fun unrestrict(link: String): RdLink
    suspend fun torrents(limit: Int = 50, page: Int = 1): List<RdTorrent>
    suspend fun downloads(limit: Int = 50): List<RdDownload>
}
