package com.sokoul.iptv.recording

import com.sokoul.iptv.data.db.RecordingEntity
import com.sokoul.iptv.data.db.RecordingStatus
import java.util.zip.CRC32

/** Règles métier des enregistrements (pures, testées unitairement). */
object RecordingRules {
    const val MAX_DURATION_MS = 12 * 3600_000L
    const val MIN_FREE_BYTES = 200L * 1024 * 1024

    sealed interface Verdict {
        data class Ok(val warning: String? = null) : Verdict
        data class Refused(val reason: String) : Verdict
    }

    private fun RecordingEntity.isPending() = status == RecordingStatus.SCHEDULED || status == RecordingStatus.RECORDING

    private fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long) = aStart < bEnd && bStart < aEnd

    /**
     * Vérifie qu'un nouvel enregistrement est possible.
     * @param existing enregistrements de la même playlist
     * @param maxConnections connexions simultanées autorisées par l'abonnement (null = inconnu, pas de limite)
     */
    fun check(
        channelId: String,
        start: Long,
        end: Long,
        existing: List<RecordingEntity>,
        maxConnections: Int?,
        now: Long = System.currentTimeMillis(),
    ): Verdict {
        if (end <= start) return Verdict.Refused("L'heure de fin doit suivre l'heure de début")
        if (end <= now) return Verdict.Refused("Ce programme est déjà terminé")
        if (end - start > MAX_DURATION_MS) return Verdict.Refused("Durée maximale d'un enregistrement : 12 h")
        val overlapping = existing.filter { it.isPending() && overlaps(start, end, it.startAt, it.endAt) }
        if (overlapping.any { it.channelId == channelId }) return Verdict.Refused("Cette chaîne est déjà enregistrée sur ce créneau")
        val limit = maxConnections?.takeIf { it > 0 }
        if (limit != null && overlapping.size + 1 > limit) {
            return Verdict.Refused(
                "Votre abonnement n'autorise que $limit connexion${if (limit > 1) "s" else ""} simultanée${if (limit > 1) "s" else ""} : " +
                    "« ${overlapping.first().title} » est déjà enregistré sur ce créneau"
            )
        }
        val warning = if (limit == 1) "Avec une seule connexion autorisée, regarder une autre chaîne pendant l'enregistrement l'interrompra" else null
        return Verdict.Ok(warning)
    }

    /** Codes de PendingIntent : un espace par type, sans collision entre enregistrements. */
    fun recordRequestCode(id: Long): Int = (id and 0x3fffffff).toInt()

    fun reminderRequestCode(pid: Long, channelId: String, start: Long): Int {
        val crc = CRC32().apply { update("$pid|$channelId|$start".toByteArray()) }.value
        return (crc and 0x3fffffff).toInt() or 0x40000000
    }

    /** Nom de fichier sûr (sans caractères interdits ni séparateurs de chemin). */
    fun safeFileName(channel: String, title: String): String =
        "${channel}_$title".replace(Regex("[^\\p{L}\\p{N}._ -]"), "").replace(Regex("\\.{2,}"), ".")
            .take(80).trim('.', ' ', '_').ifEmpty { "enregistrement" }

    // Suffixes techniques des noms de chaîne (« TF1 FHD », « M6 HEVC ») : inutiles dans un nom de fichier ou une carte.
    private val QUALITY_SUFFIX = Regex("""(?:\s+(?:FHD|UHD|HD|SD|4K|HEVC|H\.?265|H\.?264|50FPS|60FPS|RAW|BACKUP|VIP))+\s*$""", RegexOption.IGNORE_CASE)

    /** Nom de chaîne lisible : sans préfixe pays (« FR| ») ni suffixe de qualité (« FHD ») ; « TF1 +1 » est gardé. */
    fun channelLabel(name: String): String {
        val noCountry = com.sokoul.iptv.data.repo.VodGrouping.stripCountry(name.trim()).trim()
        return noCountry.replace(QUALITY_SUFFIX, "").trim().ifEmpty { name.trim() }
    }

    private fun same(a: String, b: String) =
        a.lowercase().filter { it.isLetterOrDigit() } == b.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Base du nom de fichier : « TF1 - Le journal - 2026-10-26 20h00 ». Le titre est omis quand il répète la chaîne
     * (enregistrement manuel sans guide), au lieu de « FR TF1 FHD_FR TF1 FHD ».
     */
    fun fileBase(channel: String, title: String, start: Long): String {
        val ch = channelLabel(channel)
        val t = title.trim()
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH'h'mm", java.util.Locale.FRANCE).format(java.util.Date(start))
        val withTitle = t.isNotEmpty() && !same(t, ch) && !same(t, channel) && !same(channelLabel(t), ch)
        val raw = if (withTitle) "$ch - $t - $stamp" else "$ch - $stamp"
        return raw.replace(Regex("""[^\p{L}\p{N}._ -]"""), "").replace(Regex("""\.{2,}"""), ".").replace(Regex(" {2,}"), " ")
            .take(110).trim('.', ' ', '_', '-').ifEmpty { "enregistrement" }
    }

    /** Rubriques de l'écran Enregistrements, dans l'ordre d'affichage. */
    enum class Section(val kicker: String, val title: String) {
        RECORDING("EN DIRECT", "En cours"),
        SCHEDULED("À VENIR", "Programmés"),
        COMPLETED("PRÊTS À REGARDER", "Terminés"),
        FAILED("À VÉRIFIER", "Échecs et annulés"),
    }

    /** Regroupe les enregistrements par rubrique : à venir dans l'ordre chronologique, le reste du plus récent au plus ancien. */
    fun sections(list: List<RecordingEntity>): List<Pair<Section, List<RecordingEntity>>> = Section.entries.mapNotNull { s ->
        val items = when (s) {
            Section.RECORDING -> list.filter { it.status == RecordingStatus.RECORDING }.sortedBy { it.startAt }
            Section.SCHEDULED -> list.filter { it.status == RecordingStatus.SCHEDULED }.sortedBy { it.startAt }
            Section.COMPLETED -> list.filter { it.status == RecordingStatus.COMPLETED }.sortedByDescending { it.startAt }
            Section.FAILED -> list.filter { it.status == RecordingStatus.FAILED || it.status == RecordingStatus.CANCELLED }.sortedByDescending { it.startAt }
        }
        if (items.isEmpty()) null else s to items
    }
}
