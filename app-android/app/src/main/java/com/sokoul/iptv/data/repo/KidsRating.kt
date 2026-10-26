package com.sokoul.iptv.data.repo

/**
 * Profil Enfant, deuxième filet après les groupes : une œuvre classée 12 ans ou plus (France, États-Unis…)
 * ou de genre horreur / thriller n'est pas proposée, même rangée dans un groupe « Animation ».
 * Sans classification connue, le groupe fait foi.
 */
object KidsRating {
    private val adultUs = setOf("PG-13", "R", "NC-17", "TV-14", "TV-MA", "X", "M", "MA15+", "R18+")
    private val adultGenres = listOf("horreur", "horror", "thriller", "érotique", "erotic")

    fun allowed(certification: String?, genres: List<String> = emptyList()): Boolean {
        if (genres.any { g -> adultGenres.any { g.contains(it, ignoreCase = true) } }) return false
        val c = certification?.trim()?.uppercase()?.ifEmpty { null } ?: return true
        if (c in adultUs) return false
        // « -12 », « 12 », « 16+ », « FSK 16 »… : âge minimal de 12 ans ou plus
        val age = Regex("""\d{1,2}""").find(c)?.value?.toIntOrNull()
        return age == null || age < 12
    }

    fun allowed(w: Work): Boolean = allowed(w.certification, w.genres)
}
