package com.sokoul.iptv.data.debrid

import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** La version n'est pas (encore) disponible : on passe à la suivante. */
open class DebridNotReady(message: String) : IOException(message)

/** Version en cours de téléchargement dans le compte Real-Debrid ([progress] %) : pas en panne, juste pas encore prête. */
class DebridPreparing(val progress: Int) : DebridNotReady("Version en préparation sur Real-Debrid ($progress %)")

/** Version pas en cache chez Real-Debrid (essai automatique) : écartée pour ce lancement, pas comptée comme un échec. */
class DebridNotCached : DebridNotReady("Version pas encore prête sur Real-Debrid")

/**
 * Version Real-Debrid → lien direct lisible.
 *
 * - Empreinte du torrent connue : on passe par le compte (le lien de l'extension n'est qu'un secours si le compte
 *   répond en erreur ; jamais pour une version pas prête, l'extension renverrait sa vidéo « téléchargement en cours »).
 * - Sans empreinte : lien de l'extension, tel quel.
 * - Par le compte : torrent déjà présent dans le compte et prêt → réutilisé ; présent mais pas prêt →
 *   [DebridPreparing] (rien n'est ajouté). Sinon ajouté, seul le bon fichier est choisi (l'épisode dans un pack de saison).
 * - Pas prêt en ~3 s : choisi par l'utilisateur ([keep]), le torrent reste dans le compte pour se télécharger
 *   ([DebridPreparing]) ; essai automatique, il est supprimé (rien ne s'accumule) et [DebridNotReady] est levée.
 */
@Singleton
class DebridResolver @Inject constructor(private val rd: RealDebridClient) {
    /** Remplaçable dans les tests. */
    internal var pollDelayMs = 1_000L

    suspend fun resolve(s: DebridStream, q: DebridQuery, keep: Boolean = false): String {
        val what = s.title.lineSequence().firstOrNull().orEmpty().take(90)
        val hash = s.infoHash
        if (hash == null) {
            // release sans empreinte : seul le lien de l'extension permet de la lire
            val u = s.url ?: throw DebridNotReady("Version sans lien").also { SourceLog.add("Résolution « $what » : ni empreinte ni lien") }
            SourceLog.add("Résolution « $what » : lien de l'extension (${host(u)}), forme " + shape(u))
            return u
        }
        return try {
            viaAccount(s, hash, q, keep).also { SourceLog.add("Résolution « $what » : lien direct obtenu par le compte") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DebridNotReady) {
            // pas prête : le lien de l'extension ne ferait que renvoyer sa vidéo « téléchargement en cours »
            SourceLog.add("Résolution « $what » : ${e.message}")
            throw e
        } catch (e: Exception) {
            val u = s.url
            if (u == null || (e is RdException && e.code in setOf(401, 403))) {
                SourceLog.add("Résolution « $what » : échec par le compte, ${SourceLog.reason(e)}")
                throw e
            }
            SourceLog.add("Résolution « $what » : compte en erreur (${SourceLog.reason(e)}), lien de l'extension (${host(u)})")
            u
        }
    }

    /** Forme du chemin sans rien de sensible : segments longs remplacés par leur longueur (« /<b64:120>/playback/<hex:40>/0 »). */
    private fun shape(u: String): String = runCatching { java.net.URI(u).rawPath }.getOrNull().orEmpty().split('/').joinToString("/") { seg ->
        when {
            seg.length >= 16 && seg.all { it.isLetterOrDigit() && it.lowercaseChar() in "0123456789abcdef" } -> "<hex:${seg.length}>"
            seg.length >= 16 -> "<${seg.length}>"
            else -> seg
        }
    }.take(160)

    private fun host(u: String) = runCatching { java.net.URI(u).host }.getOrNull() ?: "?"

    /** Par le compte : torrent déjà présent et prêt, sinon ajouté avec le seul bon fichier. */
    private suspend fun viaAccount(s: DebridStream, hash: String, q: DebridQuery, keep: Boolean): String {
        val season = q.season.takeIf { q.series }
        val episode = q.episode.takeIf { q.series }

        // déjà dans le compte : prêt avec le bon fichier choisi → aucun ajout ; en cours de téléchargement → on attend
        val existing = rd.torrents(100).filter { it.hash == hash && it.status !in DEAD }
        existing.firstOrNull { it.ready }?.let { t ->
            val info = rd.torrentInfo(t.id)
            val file = pick(info.files.filter { it.selected }, s, season, episode) ?: pick(info.files, s, season, episode)
            if (file != null && file.selected) linkFor(info, file)?.let { return rd.unrestrict(it).url }
        }
        existing.firstOrNull { !it.ready }?.let { throw DebridPreparing(it.progress.coerceIn(0, 100)) }

        val id = rd.addMagnet("magnet:?xt=urn:btih:$hash")
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
            if (keep) throw DebridPreparing(info.progress.coerceIn(0, 100))
            throw DebridNotCached()
        } catch (e: DebridPreparing) {
            throw e
        } catch (e: Exception) {
            rd.deleteTorrent(id)
            throw e
        }
    }

    /** Fichier voulu : l'épisode demandé, sinon celui indiqué par l'extension, sinon la plus grosse vidéo. */
    private fun pick(files: List<RdFile>, s: DebridStream, season: Int?, episode: Int?): RdFile? {
        if (files.isEmpty()) return null
        val byName = s.filename?.let { n -> files.firstOrNull { it.name.equals(n, ignoreCase = true) && ReleaseName.isVideo(it.path) } }
        if (season != null && episode != null) {
            // épisode d'une série : jamais « la plus grosse vidéo » d'un pack où l'épisode manque
            return ReleaseName.pickFile(files, season, episode)
                ?: byName?.takeIf { f -> ReleaseName.episodeTag(f.name).let { it == null || it == season to episode } }
        }
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
