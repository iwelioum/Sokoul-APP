package com.sokoul.iptv.data.debrid

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.domain.VideoQuality
import javax.inject.Inject
import javax.inject.Singleton

/** Un fichier du compte Real-Debrid (torrent prêt ou lien débridé), ajouté depuis le téléphone ou l'ordinateur. */
data class CloudItem(
    /** « t:ID » (torrent) ou « d:ID » (téléchargement). */
    val id: String,
    val title: String,
    val year: Int?,
    val release: Release,
    val sizeBytes: Long,
    val addedAt: Long,
)

/**
 * « Mes fichiers Real-Debrid » : torrents prêts et liens débridés du compte, vidéos seulement,
 * sans doublon (un même fichier en torrent et en lien), du plus récent au plus ancien. Cache 5 min.
 */
@Singleton
class DebridCloud @Inject constructor(private val rd: RealDebridClient) {
    @Volatile private var cache: Pair<Long, List<CloudItem>>? = null
    @Volatile private var account: Pair<Long, RdUser?>? = null

    suspend fun items(force: Boolean = false): List<CloudItem> {
        cache?.takeIf { !force && System.currentTimeMillis() - it.first < TTL_MS }?.let { return it.second }
        if (!rd.configured()) return emptyList()
        val torrents = runCatching { rd.torrents(50) }.getOrDefault(emptyList()).filter { it.ready }
            .map { t -> CloudItem("t:${t.id}", cleanTitle(t.filename), year(t.filename), ReleaseName.parse(t.filename), t.bytes, t.addedAt) }
        val downloads = runCatching { rd.downloads(50) }.getOrDefault(emptyList()).filter { ReleaseName.isVideo(it.filename) }
            .map { d -> CloudItem("d:${d.id}", cleanTitle(d.filename), year(d.filename), ReleaseName.parse(d.filename), d.bytes, d.generatedAt) }
        val list = merge(torrents, downloads)
        cache = System.currentTimeMillis() to list
        return list
    }

    /** Jours de premium restants (null = inconnu). Vérifié au plus une fois par heure. */
    suspend fun daysLeft(): Int? {
        val a = account?.takeIf { System.currentTimeMillis() - it.first < 3_600_000L }
            ?: (System.currentTimeMillis() to runCatching { rd.user() }.getOrNull()).also { account = it }
        return a.second?.daysLeft(System.currentTimeMillis())
    }

    /** Lien direct d'un fichier du compte : le plus gros fichier vidéo d'un torrent, ou le lien déjà débridé. */
    suspend fun playable(id: String): String = when {
        id.startsWith("d:") -> rd.downloads(100).firstOrNull { it.id == id.removePrefix("d:") }?.url
            ?: throw DebridNotReady("Fichier introuvable dans votre compte Real-Debrid")
        id.startsWith("t:") -> {
            val info = rd.torrentInfo(id.removePrefix("t:"))
            val selected = info.files.filter { it.selected }.sortedBy { it.id }
            val file = ReleaseName.pickFile(selected, null, null) ?: throw DebridNotReady("Aucune vidéo dans ce torrent")
            val link = info.links.getOrNull(selected.indexOf(file)) ?: info.links.firstOrNull() ?: throw DebridNotReady("Torrent pas encore prêt")
            rd.unrestrict(link).url
        }
        else -> throw DebridNotReady("Fichier inconnu")
    }

    companion object {
        const val TTL_MS = 5 * 60_000L
        const val KEY_PREFIX = "rdc:"

        fun merge(torrents: List<CloudItem>, downloads: List<CloudItem>): List<CloudItem> {
            val seen = HashSet<String>()
            return (torrents + downloads).sortedByDescending { it.addedAt }
                .filter { seen.add(it.title.lowercase() + "|" + it.year + "|" + it.release.season + "x" + it.release.episode) }
        }

        /** « The.Batman.2022.2160p.WEB-DL… .mkv » → « The Batman ». */
        fun cleanTitle(name: String): String {
            val base = name.substringAfterLast('/').replace(Regex("(?i)\\.(mkv|mp4|avi|m4v|ts)$"), "")
            val cut = Regex("(?i)[ ._(\\[-]((19|20)\\d{2})[ ._)\\]-]|[ ._-]S\\d{1,2}E\\d{1,3}|[ ._-]S\\d{1,2}[ ._-]|[ ._-](2160p|1080p|720p|480p|4k|uhd|multi|french|truefrench|vostfr|webrip|web-dl|bluray|hdtv)\\b")
                .find(base)?.range?.first ?: base.length
            return base.substring(0, cut).replace(Regex("[._]+"), " ").trim().ifEmpty { base }
        }

        fun year(name: String): Int? = Regex("[ ._(\\[-]((19|20)\\d{2})([ ._)\\]-]|$)").find(name)?.groupValues?.get(1)?.toIntOrNull()

        /** Carte d'accueil : titre propre, « 4K · 18,3 Go » ; ouverte directement dans le lecteur. */
        fun toWork(i: CloudItem): Work = Work(
            type = if (i.release.season != null) ContentType.SERIES else ContentType.MOVIE,
            id = KEY_PREFIX + i.id, key = KEY_PREFIX + i.id,
            title = i.title + (i.release.season?.let { s -> " · S%02dE%02d".format(s, i.release.episode ?: 0) } ?: ""),
            year = i.year, poster = null,
            quality = when (i.release.quality) { VideoQuality.UHD_4K -> "4K"; VideoQuality.FHD_1080 -> "FHD"; VideoQuality.HD_720 -> "HD"; else -> null },
            subtitle = listOfNotNull(
                i.release.quality?.let { com.sokoul.iptv.ui.components.SourceLabels.quality(it) },
                i.sizeBytes.takeIf { it > 0 }?.let { com.sokoul.iptv.ui.components.SourceLabels.size(it) },
            ).joinToString(" · ").ifEmpty { null },
        )
    }
}
