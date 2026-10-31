package com.sokoul.desktop.data

/** État d'un enregistrement (mêmes états que la tablette). */
enum class RecordingStatus { SCHEDULED, RECORDING, COMPLETED, FAILED, CANCELLED }

/** Enregistrement d'un programme du direct sur le PC. */
data class Recording(
    val id: Long,
    val playlistId: Long,
    val channelId: String,
    val channelName: String,
    val channelLogo: String?,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val status: RecordingStatus,
    val path: String? = null,
    val bytes: Long = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Règles des enregistrements du PC, reprises de `RecordingRules` de la tablette (pures, testées). L'abonnement
 * n'autorise qu'une connexion : deux enregistrements qui se chevauchent sont refusés, même sur deux chaînes.
 */
object PcRecordingRules {
    const val MAX_DURATION_MS = 12 * 3600_000L
    const val MARGIN_BEFORE_MS = 120_000L
    const val MARGIN_AFTER_MS = 300_000L

    sealed interface Verdict {
        data class Ok(val warning: String? = null) : Verdict
        data class Refused(val reason: String) : Verdict
    }

    private fun Recording.isPending() = status == RecordingStatus.SCHEDULED || status == RecordingStatus.RECORDING
    private fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long) = aStart < bEnd && bStart < aEnd

    /** Un nouvel enregistrement [start]..[end] sur [channelId] est-il possible ? */
    fun check(channelId: String, start: Long, end: Long, existing: List<Recording>, now: Long = System.currentTimeMillis()): Verdict {
        if (end <= start) return Verdict.Refused("L'heure de fin doit suivre l'heure de début")
        if (end <= now) return Verdict.Refused("Ce programme est déjà terminé")
        if (end - start > MAX_DURATION_MS) return Verdict.Refused("Durée maximale d'un enregistrement : 12 h")
        val overlapping = existing.filter { it.isPending() && overlaps(start, end, it.startAt, it.endAt) }
        if (overlapping.any { it.channelId == channelId }) return Verdict.Refused("Cette chaîne est déjà enregistrée sur ce créneau")
        if (overlapping.isNotEmpty()) return Verdict.Refused("Un seul enregistrement à la fois (une connexion) : « ${overlapping.first().title} » est déjà enregistré sur ce créneau")
        return Verdict.Ok("Pendant l'enregistrement, regarder une autre chaîne l'interromprait : la lecture d'une autre chaîne sera refusée")
    }

    /** Créneau d'un programme du guide, marges comprises (les horaires du guide sont rarement exacts). */
    fun slot(programStart: Long, programEnd: Long, now: Long): Pair<Long, Long> = maxOf(programStart - MARGIN_BEFORE_MS, now) to programEnd + MARGIN_AFTER_MS

    /** Au lancement de l'app : un enregistrement programmé dont l'heure est passée a été manqué (app fermée). */
    fun missed(r: Recording, now: Long): Boolean = (r.status == RecordingStatus.SCHEDULED && r.endAt <= now) || (r.status == RecordingStatus.RECORDING)

    /** Rubriques de l'écran Enregistrements de la tablette, dans l'ordre d'affichage. */
    enum class Section(val kicker: String, val title: String) {
        RECORDING("EN DIRECT", "En cours"),
        SCHEDULED("À VENIR", "Programmés"),
        COMPLETED("PRÊTS À REGARDER", "Terminés"),
        FAILED("À VÉRIFIER", "Échecs et annulés"),
    }

    /** À venir dans l'ordre chronologique, le reste du plus récent au plus ancien ; rubriques vides retirées. */
    fun sections(list: List<Recording>): List<Pair<Section, List<Recording>>> = Section.entries.mapNotNull { s ->
        val items = when (s) {
            Section.RECORDING -> list.filter { it.status == RecordingStatus.RECORDING }.sortedBy { it.startAt }
            Section.SCHEDULED -> list.filter { it.status == RecordingStatus.SCHEDULED }.sortedBy { it.startAt }
            Section.COMPLETED -> list.filter { it.status == RecordingStatus.COMPLETED }.sortedByDescending { it.startAt }
            Section.FAILED -> list.filter { it.status == RecordingStatus.FAILED || it.status == RecordingStatus.CANCELLED }.sortedByDescending { it.startAt }
        }
        if (items.isEmpty()) null else s to items
    }

    // Suffixes techniques des noms de chaîne (« TF1 FHD », « M6 HEVC ») : inutiles dans un nom de fichier ou une carte.
    private val QUALITY_SUFFIX = Regex("""(?:\s+(?:FHD|UHD|HD|SD|4K|HEVC|H\.?265|H\.?264|50FPS|60FPS|RAW|BACKUP|VIP))+\s*$""", RegexOption.IGNORE_CASE)
    private val COUNTRY_PREFIX = Regex("""^\s*[A-Z]{2,4}\s*[|:\-]\s*""")

    /** Nom de chaîne lisible : sans préfixe pays (« FR| ») ni suffixe de qualité (« FHD »). */
    fun channelLabel(name: String): String =
        name.trim().replace(COUNTRY_PREFIX, "").replace(QUALITY_SUFFIX, "").trim().ifEmpty { name.trim() }

    /** Base du nom de fichier : « FRANCE 2 - Télématin - 2026-10-31 08h30 ». */
    fun fileBase(channel: String, title: String, start: Long): String {
        val ch = channelLabel(channel)
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH'h'mm", java.util.Locale.FRANCE).format(java.util.Date(start))
        val raw = if (title.isNotBlank() && !title.equals(ch, true)) "$ch - ${title.trim()} - $stamp" else "$ch - $stamp"
        return raw.replace(Regex("""[^\p{L}\p{N}._ -]"""), "").replace(Regex("""\.{2,}"""), ".").replace(Regex(" {2,}"), " ")
            .take(110).trim('.', ' ', '_', '-').ifEmpty { "enregistrement" }
    }

    /** Programme couvert à plus de la moitié par un enregistrement en attente (pastille rouge du guide). */
    fun covering(start: Long, end: Long, recordings: List<Recording>): Recording? =
        recordings.firstOrNull { it.isPending() && minOf(end, it.endAt) - maxOf(start, it.startAt) >= (end - start) / 2 }
}
