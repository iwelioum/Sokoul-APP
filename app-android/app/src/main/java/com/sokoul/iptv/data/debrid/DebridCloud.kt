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
    /** Image TMDB du film ou de la série, si le titre est reconnu. */
    val backdrop: String? = null,
    val poster: String? = null,
)

/**
 * « Mes fichiers Real-Debrid » : torrents prêts et liens débridés du compte, vidéos seulement,
 * sans doublon (un même fichier en torrent et en lien), du plus récent au plus ancien. Cache 5 min.
 */
@Singleton
class DebridCloud @Inject constructor(
    private val rd: DebridService,
    private val tmdb: com.sokoul.iptv.data.net.TmdbClient,
    private val settings: com.sokoul.iptv.data.SettingsStore,
    private val profiles: com.sokoul.iptv.data.repo.ProfileRepository,
) {
    @Volatile private var cache: Pair<Long, List<CloudItem>>? = null
    @Volatile private var account: Pair<Long, RdUser?>? = null

    private class Snapshot(val at: Long, val torrents: List<RdTorrent>, val downloads: List<RdDownload>)
    @Volatile private var snap: Snapshot? = null

    /** Torrents (prêts ou non) et liens du compte, relus au plus une fois par minute. */
    private suspend fun snapshot(force: Boolean = false): Snapshot {
        snap?.takeIf { !force && System.currentTimeMillis() - it.at < SNAP_MS }?.let { return it }
        val s = Snapshot(
            System.currentTimeMillis(),
            runCatching { rd.torrents(100) }.getOrDefault(emptyList()),
            runCatching { rd.downloads(100) }.getOrDefault(emptyList()),
        )
        snap = s
        return s
    }

    /**
     * Fichiers du compte qui sont cette œuvre (titre français ou original, année à ±1 ; la bonne saison pour une série) :
     * proposés comme sources, prêts (« dans votre compte ») ou en cours de téléchargement (« en préparation »).
     */
    suspend fun forWork(titles: List<String>, year: Int?, season: Int?, episode: Int?): List<DebridStream> {
        if (profiles.isKids() || !rd.configured()) return emptyList()
        val s = snapshot()
        val torrents = s.torrents.filter { it.status !in DEAD && matches(it.filename, titles, year, season, episode) }.map { t ->
            DebridStream(
                key = "$KEY_PREFIX${t.id}", title = t.filename, url = null, infoHash = t.hash.ifEmpty { null }, fileIdx = null, filename = null,
                release = ReleaseName.parse(t.filename).let { r -> if (r.sizeBytes == null && t.bytes > 0) r.copy(sizeBytes = t.bytes) else r },
                inAccount = t.ready, preparing = if (t.ready) null else t.progress.coerceIn(0, 100),
            )
        }
        val downloads = s.downloads.filter { ReleaseName.isVideo(it.filename) && matches(it.filename, titles, year, season, episode) }.map { d ->
            DebridStream(
                key = "${KEY_PREFIX}d${d.id}", title = d.filename, url = d.url, infoHash = null, fileIdx = null, filename = d.filename,
                release = ReleaseName.parse(d.filename).let { r -> if (r.sizeBytes == null && d.bytes > 0) r.copy(sizeBytes = d.bytes) else r },
                inAccount = true,
            )
        }
        return torrents + downloads
    }

    suspend fun items(force: Boolean = false): List<CloudItem> {
        if (profiles.isKids()) return emptyList()
        cache?.takeIf { !force && System.currentTimeMillis() - it.first < TTL_MS }?.let { return it.second }
        if (!rd.configured()) return emptyList()
        val torrents = runCatching { rd.torrents(50) }.getOrDefault(emptyList()).filter { it.ready }
            .map { t -> CloudItem("t:${t.id}", cleanTitle(t.filename), year(t.filename), ReleaseName.parse(t.filename), t.bytes, t.addedAt) }
        val downloads = runCatching { rd.downloads(50) }.getOrDefault(emptyList()).filter { ReleaseName.isVideo(it.filename) }
            .map { d -> CloudItem("d:${d.id}", cleanTitle(d.filename), year(d.filename), ReleaseName.parse(d.filename), d.bytes, d.generatedAt) }
        val list = withArt(merge(torrents, downloads))
        cache = System.currentTimeMillis() to list
        return list
    }

    /** Images TMDB : une recherche par titre (au plus 15, mises en cache par le client TMDB). */
    private suspend fun withArt(list: List<CloudItem>): List<CloudItem> {
        val key = runCatching { settings.current().tmdbApiKey }.getOrDefault("")
        if (key.isBlank()) return list
        val art = HashMap<String, com.sokoul.iptv.data.net.TmdbItem?>()
        for (i in list) {
            val k = (if (i.release.season != null) "tv|" else "movie|") + i.title.lowercase()
            if (k in art || art.size >= 15) continue
            art[k] = runCatching { tmdb.findByName(key, k.substringBefore('|'), i.title) }.getOrNull()
        }
        return list.map { i ->
            val t = art[(if (i.release.season != null) "tv|" else "movie|") + i.title.lowercase()] ?: return@map i
            i.copy(backdrop = t.backdrop, poster = t.poster)
        }
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
        const val SNAP_MS = 60_000L
        const val KEY_PREFIX = "rdc:"
        private val DEAD = setOf("error", "magnet_error", "virus", "dead")

        /** « Amélie » = « amelie » : minuscules, sans accents, lettres et chiffres seulement. */
        fun norm(s: String): String =
            java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").filter { it.isLetterOrDigit() }

        /** Saison d'un pack (« Show.S02.1080p », « Saison 2 ») ; l'épisode passe par `ReleaseName.episodeTag`. */
        fun seasonOf(name: String): Int? =
            ReleaseName.episodeTag(name)?.first
                ?: Regex("(?i)(?:^|[ ._\\[(-])S(\\d{1,2})(?=$|[ ._\\])-])").find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("(?i)\\b(?:season|saison)[ ._-]?(\\d{1,2})\\b").find(name)?.groupValues?.get(1)?.toIntOrNull()

        /**
         * Le fichier [name] est-il l'œuvre cherchée ? Titre identique (français ou original), année à ±1 an pour un film ;
         * pour une série, même saison, et le bon épisode s'il est indiqué (un pack de saison convient).
         */
        fun matches(name: String, titles: List<String>, year: Int?, season: Int?, episode: Int?): Boolean {
            val base = name.substringAfterLast('/')
            val t = norm(cleanTitle(base))
            if (t.length < 2 || titles.none { norm(it) == t }) return false
            val series = season != null
            val tag = ReleaseName.episodeTag(base)
            if (!series) {
                if (tag != null || seasonOf(base) != null) return false
                val y = year(base)
                return year == null || y == null || kotlin.math.abs(y - year) <= 1
            }
            if (seasonOf(base) != season) return false
            return episode == null || tag == null || tag.second == episode
        }

        /** Un même film ou épisode en plusieurs fichiers : la meilleure définition reste, placée à la date du plus récent. */
        fun merge(torrents: List<CloudItem>, downloads: List<CloudItem>): List<CloudItem> =
            (torrents + downloads).groupBy { sameKey(it) }.values.map { g ->
                val best = g.minWith(compareBy<CloudItem> { it.release.quality?.ordinal ?: Int.MAX_VALUE }.thenByDescending { it.addedAt })
                best.copy(addedAt = g.maxOf { it.addedAt })
            }.sortedByDescending { it.addedAt }

        /** Titre sans casse ni ponctuation (« Interview With The Vampire » = « Interview with the Vampire »). */
        private fun sameKey(i: CloudItem) =
            i.title.lowercase().filter { it.isLetterOrDigit() } + "|" + i.year + "|" + i.release.season + "x" + i.release.episode

        /** « The.Batman.2022.2160p.WEB-DL… .mkv » → « The Batman ». */
        fun cleanTitle(name: String): String {
            val base = name.substringAfterLast('/').replace(Regex("(?i)\\.(mkv|mp4|avi|m4v|ts)$"), "")
            val cut = Regex("(?i)[ ._(\\[-]((19|20)\\d{2})[ ._)\\]-]|[ ._-]S\\d{1,2}E\\d{1,3}|[ ._-]S\\d{1,2}[ ._-]|[ ._-](2160p|1080p|720p|480p|4k|uhd|multi|french|truefrench|vostfr|webrip|web-dl|bluray|hdtv)\\b")
                .find(base)?.range?.first ?: base.length
            return base.substring(0, cut).replace(Regex("[._]+"), " ").trim().trimEnd('-', '–', ':', ' ').ifEmpty { base }
        }

        fun year(name: String): Int? = Regex("[ ._(\\[-]((19|20)\\d{2})([ ._)\\]-]|$)").find(name)?.groupValues?.get(1)?.toIntOrNull()

        /** Carte d'accueil : titre propre, « 4K · 18,3 Go » ; ouverte directement dans le lecteur. */
        fun toWork(i: CloudItem): Work = Work(
            type = if (i.release.season != null) ContentType.SERIES else ContentType.MOVIE,
            id = KEY_PREFIX + i.id, key = KEY_PREFIX + i.id,
            title = i.title + (i.release.season?.let { s -> " · S%02dE%02d".format(s, i.release.episode ?: 0) } ?: ""),
            year = i.year, poster = i.poster, backdrop = i.backdrop,
            quality = when (i.release.quality) { VideoQuality.UHD_4K -> "4K"; VideoQuality.FHD_1080 -> "FHD"; VideoQuality.HD_720 -> "HD"; else -> null },
            subtitle = listOfNotNull(
                i.release.quality?.let { com.sokoul.iptv.ui.components.SourceLabels.quality(it) },
                i.sizeBytes.takeIf { it > 0 }?.let { com.sokoul.iptv.ui.components.SourceLabels.size(it) },
            ).joinToString(" · ").ifEmpty { null },
        )
    }
}
