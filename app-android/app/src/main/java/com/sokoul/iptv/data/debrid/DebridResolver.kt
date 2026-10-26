package com.sokoul.iptv.data.debrid

import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** La version n'est pas (encore) disponible : on passe à la suivante. */
class DebridNotReady(message: String) : IOException(message)

/**
 * Version Real-Debrid → lien direct lisible.
 *
 * - Lien déjà résolu par l'extension : utilisé tel quel (le lecteur suit la redirection).
 * - Sinon (empreinte du torrent) : torrent déjà présent dans le compte et prêt → réutilisé ; sinon ajouté,
 *   seul le bon fichier est choisi (l'épisode dans un pack de saison), et s'il n'est pas prêt en ~3 s,
 *   le torrent ajouté est supprimé (rien ne s'accumule dans le compte) et [DebridNotReady] est levée.
 */
@Singleton
class DebridResolver @Inject constructor(private val rd: RealDebridClient) {
    /** Remplaçable dans les tests. */
    internal var pollDelayMs = 1_000L

    suspend fun resolve(s: DebridStream, q: DebridQuery): String {
        s.url?.let { return it }
        val hash = s.infoHash ?: throw DebridNotReady("Version sans lien")
        val season = q.season.takeIf { q.series }
        val episode = q.episode.takeIf { q.series }

        // déjà dans le compte, prêt, avec le bon fichier choisi : aucun ajout
        rd.torrents(100).firstOrNull { it.hash == hash && it.ready }?.let { t ->
            val info = rd.torrentInfo(t.id)
            val file = pick(info.files, s, season, episode)
            if (file != null && file.selected) linkFor(info, file)?.let { return rd.unrestrict(it).url }
        }

        val id = rd.addMagnet(s.magnet!!)
        try {
            var info = rd.torrentInfo(id)
            val file = pick(info.files, s, season, episode) ?: throw DebridNotReady("Aucun fichier vidéo correspondant dans cette version")
            rd.selectFiles(id, listOf(file.id))
            repeat(POLLS) { attempt ->
                info = rd.torrentInfo(id)
                if (info.ready) return rd.unrestrict(linkFor(info, file) ?: throw DebridNotReady("Lien manquant")).url
                if (info.status in DEAD) throw DebridNotReady("Version indisponible sur Real-Debrid")
                if (attempt < POLLS - 1) delay(pollDelayMs)
            }
            throw DebridNotReady("Version pas encore prête sur Real-Debrid")
        } catch (e: Exception) {
            rd.deleteTorrent(id)
            throw e
        }
    }

    /** Fichier voulu : l'épisode demandé, sinon celui indiqué par l'extension, sinon la plus grosse vidéo. */
    private fun pick(files: List<RdFile>, s: DebridStream, season: Int?, episode: Int?): RdFile? {
        if (files.isEmpty()) return null
        if (season != null && episode != null) ReleaseName.pickFile(files, season, episode)?.let { return it }
        val byName = s.filename?.let { n -> files.firstOrNull { it.name.equals(n, ignoreCase = true) } }
        val byIndex = s.fileIdx?.let { files.sortedBy { f -> f.id }.getOrNull(it) }?.takeIf { ReleaseName.isVideo(it.path) }
        return byName ?: byIndex ?: ReleaseName.pickFile(files, null, null)
    }

    /** Les liens suivent l'ordre des fichiers choisis. */
    private fun linkFor(info: RdTorrent, file: RdFile): String? {
        val selected = info.files.filter { it.selected }.sortedBy { it.id }
        val i = selected.indexOfFirst { it.id == file.id }
        return info.links.getOrNull(i) ?: info.links.singleOrNull()
    }

    companion object {
        const val POLLS = 3
        private val DEAD = setOf("error", "magnet_error", "virus", "dead")
    }
}
