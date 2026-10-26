package com.sokoul.iptv.data.repo

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.Period

/**
 * Page d'un acteur ou d'un réalisateur : [movies] et [series] = œuvres présentes dans l'abonnement (les plus connues
 * d'abord) ; [elsewhere] = rôles marquants absents du catalogue (affichés grisés) ; [backdrop] = fond tiré d'une œuvre.
 */
@Immutable
data class PersonPage(
    val id: Int,
    val name: String,
    val photo: String?,
    val role: String?,
    val facts: String?,
    val biography: String?,
    val backdrop: String?,
    val movies: List<Work>,
    val series: List<Work>,
    val elsewhere: List<Work>,
)

object People {
    /** Métier affiché au-dessus du nom (accordé quand TMDB connaît le genre : 1 femme, 2 homme). */
    fun role(department: String?, gender: Int): String? = when (department) {
        "Acting" -> if (gender == 1) "Actrice" else "Acteur"
        "Directing" -> if (gender == 1) "Réalisatrice" else "Réalisateur"
        "Writing" -> "Scénariste"
        "Production" -> if (gender == 1) "Productrice" else "Producteur"
        "Sound" -> "Musique"
        "Camera" -> "Image"
        "Editing" -> "Montage"
        null, "" -> null
        else -> null
    }

    /**
     * « Né le 3 mars 1975 à Lyon, France · 51 ans » ; décès : « 1942 – 2016 (73 ans) ». Dates au format TMDB (AAAA-MM-JJ),
     * illisibles ou absentes : simplement omises.
     */
    fun facts(birthday: String?, deathday: String?, place: String?, gender: Int, today: LocalDate = LocalDate.now()): String? {
        val born = birthday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val died = deathday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val parts = ArrayList<String>()
        if (born != null && died != null) {
            parts += "${born.year} – ${died.year} (${Period.between(born, died).years} ans)"
            place?.takeIf { it.isNotBlank() }?.let { parts += it }
        } else if (born != null) {
            val e = if (gender == 1) "Née" else "Né"
            parts += "$e le ${frenchDate(born)}" + (place?.takeIf { it.isNotBlank() }?.let { " à $it" } ?: "")
            parts += "${Period.between(born, today).years} ans"
        } else {
            place?.takeIf { it.isNotBlank() }?.let { parts += it }
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    private val MONTHS = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")

    private fun frenchDate(d: LocalDate) = "${if (d.dayOfMonth == 1) "1er" else d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
}
