package com.sokoul.iptv.data.net.providers

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/** Une réplique : affichée de [startMs] à [endMs]. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Lecture des sous-titres téléchargés (SRT ou WebVTT, éventuellement dans un .zip), affichés par l'app
 * au-dessus de la vidéo : le flux n'est jamais relancé (le fournisseur n'accepte qu'une connexion).
 */
object SubtitleFile {
    private val TIME = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val SHORT_TIME = Regex("""(\d{2}):(\d{2})[,.](\d{3})""")
    private val TAGS = Regex("""<[^>]+>|\{\\[^}]*\}""")

    /** Décode des octets : .zip (premier .srt / .vtt), sinon texte UTF-8 (repli Windows-1252, fréquent en français). */
    fun decode(bytes: ByteArray): List<Cue> {
        val raw = if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) unzip(bytes) ?: return emptyList() else bytes
        return parse(text(raw))
    }

    private fun unzip(bytes: ByteArray): ByteArray? = ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        generateSequence { z.nextEntry }.firstOrNull { e ->
            val n = e.name.lowercase()
            !e.isDirectory && (n.endsWith(".srt") || n.endsWith(".vtt"))
        }?.let { z.readBytes().takeIf { it.size < 5 * 1024 * 1024 } }
    }

    private fun text(b: ByteArray): String {
        val utf8 = b.toString(Charsets.UTF_8)
        // caractère de remplacement : le fichier n'était pas en UTF-8
        return if (Char(0xFFFD) in utf8) b.toString(Charset.forName("windows-1252")) else utf8.removePrefix(Char(0xFEFF).toString())
    }

    fun parse(text: String): List<Cue> {
        val cues = ArrayList<Cue>()
        val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))
        for (block in blocks) {
            val lines = block.trim('\n').lines()
            val i = lines.indexOfFirst { "-->" in it }
            if (i < 0) continue
            val (a, b) = lines[i].split("-->").map { it.trim() }.takeIf { it.size == 2 } ?: continue
            val start = time(a) ?: continue
            val end = time(b.substringBefore(' ')) ?: continue
            val body = lines.drop(i + 1).joinToString("\n") { TAGS.replace(it, "").trim() }.trim()
            if (body.isNotEmpty() && end > start) cues += Cue(start, end, body)
        }
        return cues.sortedBy { it.startMs }
    }

    private fun time(s: String): Long? {
        TIME.find(s)?.let { m ->
            val (h, mi, se, ms) = m.destructured
            return h.toLong() * 3_600_000 + mi.toLong() * 60_000 + se.toLong() * 1000 + ms.padEnd(3, '0').toLong()
        }
        SHORT_TIME.find(s)?.let { m ->
            val (mi, se, ms) = m.destructured
            return mi.toLong() * 60_000 + se.toLong() * 1000 + ms.toLong()
        }
        return null
    }

    /** Réplique affichée à [positionMs] (recherche dichotomique), null entre deux répliques. */
    fun at(cues: List<Cue>, positionMs: Long): Cue? {
        var lo = 0
        var hi = cues.size - 1
        var found: Cue? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= positionMs) { found = cues[mid]; lo = mid + 1 } else hi = mid - 1
        }
        return found?.takeIf { positionMs < it.endMs }
    }
}
