package com.sokoul.iptv.data.debrid

import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.SourceState
import com.sokoul.iptv.parser.long
import com.sokoul.iptv.parser.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Une version proposée par l'extension de sources, lisible via Real-Debrid. */
data class DebridStream(
    /** Clé stable : « hash:fichier », ou empreinte du lien de l'extension. */
    val key: String,
    /** Nom brut de la version (affiché en détail, jamais analysé ailleurs). */
    val title: String,
    /** Lien déjà résolu par l'extension (extension configurée avec Real-Debrid). */
    val url: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val filename: String?,
    val release: Release,
    /** Déjà dans le compte Real-Debrid de l'utilisateur, prêt (« Mes fichiers ») : lecture immédiate. */
    val inAccount: Boolean = false,
    /** En cours de téléchargement dans le compte (pourcentage), null sinon. */
    val preparing: Int? = null,
) {
    val magnet get() = infoHash?.let { "magnet:?xt=urn:btih:$it" }

    /** Sans doute prête : dans le compte, ou annoncée en cache par l'extension (indice pour le classement seulement). */
    val readyNow: Boolean? get() = if (inAccount) true else if (preparing != null) false else release.cached

    /**
     * Affichée « instantanée » seulement quand c'est sûr (dans le compte) : depuis que Real-Debrid ne dit plus ce qu'il
     * a en cache, le repère « ⚡ / [RD+] » des extensions peut se tromper.
     */
    fun toSource(): PlaybackSource = PlaybackSource(
        id = key, origin = SourceOrigin.DEBRID, quality = release.quality, hdr = release.hdr, language = release.language,
        state = if (inAccount) SourceState.CACHED_INSTANT else SourceState.AVAILABLE,
        sizeBytes = release.sizeBytes, rip = release.source?.label,
    )
}

/** Œuvre à chercher : film, ou épisode d'une série (identifiants TMDB). */
data class DebridQuery(val tmdbId: Int, val series: Boolean, val season: Int? = null, val episode: Int? = null) {
    /** Forme sérialisable pour une route : « m:157336 », « s:1396:2:5 ». */
    fun encode() = if (series) "s:$tmdbId:$season:$episode" else "m:$tmdbId"

    /** Chemin Stremio de l'œuvre (`stream/movie/tt….json`, `stream/series/tt…:S:E.json`). */
    fun streamPath(imdb: String) = if (series) "stream/series/$imdb:$season:$episode.json" else "stream/movie/$imdb.json"

    companion object {
        fun decode(s: String?): DebridQuery? {
            val p = s?.split(':') ?: return null
            return when (p.firstOrNull()) {
                "m" -> p.getOrNull(1)?.toIntOrNull()?.let { DebridQuery(it, false) }
                "s" -> {
                    val id = p.getOrNull(1)?.toIntOrNull() ?: return null
                    DebridQuery(id, true, p.getOrNull(2)?.toIntOrNull(), p.getOrNull(3)?.toIntOrNull())
                }
                else -> null
            }
        }
    }
}

/** Lecture et tri des réponses des extensions de sources (format Stremio), communs à l'Android et au PC. */
object DebridStreams {
    /**
     * Version sans aucune langue dans son nom : c'est la langue d'origine du film (les releases étrangères ne
     * l'indiquent pas). VO, ou VF si l'œuvre est française ; rangée dans sa section au lieu de « Autres ».
     */
    private val tagStart = Regex("""^\s*(\[[^\]]*\]|\([^)]*\)|\{[^}]*\})\s*""")
    private val titleEnd = Regex(
        "\\b(s\\d{1,2}(e\\d{1,3})?|e\\d{2,3}|season|saison|integrale|complete|(19|20)\\d{2}|\\d{3,4}p|4k|uhd|multi|truefrench|french|vff|vfq|vostfr|bluray|web|webrip|hdtv)\\b",
    )
    /** Mots qu'on peut trouver après le titre sans changer d'œuvre (« The Office US »). */
    private val sameWork = setOf("us", "uk", "au", "nz", "ca", "fr", "the", "series", "serie", "tv")

    private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace('&', ' ').replace(Regex("[^a-z0-9]+"), " ").trim()

    /**
     * Le nom de la release est celui d'une autre œuvre qui commence par le même titre (« Lupin Zero » pour
     * « Lupin ») : seule la partie avant la saison, l'année ou la qualité compte. Un nom sans rapport (titre
     * anglais d'une série étrangère) n'est jamais écarté : on ne rejette que ce qui est sûr.
     */
    fun otherWork(name: String, titles: List<String>, series: Boolean = false): Boolean {
        // série : « Lupin Part 2 » est une saison ; film : « Dune Part Two » est un autre film
        val allowed = if (series) sameWork + setOf("part", "partie", "vol", "volume", "1", "2", "3", "4", "5", "6", "7", "8", "9") else sameWork
        var raw = name
        while (true) { val m = tagStart.find(raw) ?: break; raw = raw.substring(m.range.last + 1) }
        val n = norm(raw)
        val head = titleEnd.find(n)?.let { n.substring(0, it.range.first) }?.trim() ?: return false
        if (head.isEmpty()) return false
        val known = titles.map(::norm).filter { it.isNotEmpty() }
        if (known.any { it == head || it.startsWith("$head ") }) return false
        return known.any { t ->
            head.startsWith("$t ") && head.removePrefix("$t ").split(' ').any { it !in allowed && it.isNotEmpty() }
        }
    }

    fun untagged(streams: List<DebridStream>, originalLanguage: String?): List<DebridStream> {
        if (originalLanguage.isNullOrBlank()) return streams
        val lang = if (originalLanguage.equals("fr", ignoreCase = true)) com.sokoul.iptv.domain.AudioLanguage.VF else com.sokoul.iptv.domain.AudioLanguage.VO
        return streams.map { s ->
            if (s.release.language == null && s.release.audio.isEmpty()) s.copy(release = s.release.copy(language = lang)) else s
        }
    }

    /**
     * Résultats de plusieurs extensions → une liste sans doublon : même clé, même empreinte de torrent (et fichier),
     * ou même nom de fichier et même taille. L'ordre de la première extension est gardé.
     */
    fun combine(results: List<List<DebridStream>>): List<DebridStream> {
        val seen = HashSet<String>()
        val out = mutableListOf<DebridStream>()
        for (list in results) for (s in list) {
            val name = (s.filename ?: s.title.lineSequence().firstOrNull())?.lowercase()?.filter { it.isLetterOrDigit() }
            val ids = listOfNotNull(
                "k:" + s.key,
                s.infoHash?.let { "h:$it:" + (s.fileIdx ?: -1) },
                if (!name.isNullOrEmpty() && s.release.sizeBytes != null) "n:$name|" + s.release.sizeBytes / 50_000_000 else null,
            )
            if (ids.any { it in seen }) continue
            seen += ids
            out += s
        }
        return out
    }

    /**
     * Versions de l'extension + fichiers du compte : une version de l'extension déjà dans le compte (même empreinte)
     * est marquée « dans votre compte » (ou « en préparation ») ; les autres fichiers du compte s'ajoutent à la fin.
     */
    fun merge(addon: List<DebridStream>, account: List<DebridStream>): List<DebridStream> {
        val byHash = account.filter { it.infoHash != null }.associateBy { it.infoHash!!.lowercase() }
        val used = HashSet<String>()
        val out = addon.map { a ->
            val c = a.infoHash?.lowercase()?.let(byHash::get) ?: return@map a
            used += c.key
            a.copy(inAccount = c.inAccount, preparing = c.preparing)
        }
        return out + account.filter { it.key !in used }
    }

    /** Réponse Stremio `{"streams":[…]}` → versions lisibles (films filmés en salle écartés). */
    fun parse(text: String, json: Json = Json): List<DebridStream> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        return (root["streams"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val hints = o["behaviorHints"] as? JsonObject
            val url = o.str("url")?.takeIf { it.startsWith("http") }
            val hash = o.str("infoHash")?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{40}")) }
                // extension réglée avec Real-Debrid : l'empreinte est dans le lien (« /resolve/realdebrid/…/<hash>/… »)
                ?: url?.let { u -> Regex("[/=]([0-9a-fA-F]{40})(?=[/?]|$)").find(u)?.groupValues?.get(1)?.lowercase() }
                // Comet : lien opaque (base64), l'empreinte est dans « bingeGroup » (« comet|<hash> »)
                ?: hints?.str("bingeGroup")?.let { b -> Regex("(?<![0-9a-fA-F])([0-9a-fA-F]{40})(?![0-9a-fA-F])").find(b)?.groupValues?.get(1)?.lowercase() }
            if (url == null && hash == null) return@mapNotNull null
            val name = o.str("name").orEmpty()
            val title = o.str("title") ?: o.str("description").orEmpty()
            val filename = hints?.str("filename")
            val release = ReleaseName.parse(name, title, filename).let { r ->
                if (r.sizeBytes == null) r.copy(sizeBytes = hints?.long("videoSize")) else r
            }
            if (release.cam) return@mapNotNull null
            val fileIdx = o.long("fileIdx")?.toInt()
            DebridStream(
                key = hash?.let { "$it:${fileIdx ?: -1}" } ?: ("u" + url.hashCode().toUInt().toString(16)),
                title = (filename ?: title.lineSequence().firstOrNull() ?: name).trim(),
                url = url, infoHash = hash, fileIdx = fileIdx, filename = filename, release = release,
            )
        }.distinctBy { it.key }
    }
}
