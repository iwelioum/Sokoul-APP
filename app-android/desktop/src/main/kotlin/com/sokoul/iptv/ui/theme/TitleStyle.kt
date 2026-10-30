package com.sokoul.iptv.ui.theme

import java.text.Normalizer

/** Style d'un titre généré (œuvre sans logo officiel), choisi par le genre. */
enum class TitleStyle { METAL, GOLD, NEON, HORROR, STAMP, ELEGANT, PLAYFUL, WESTERN, DOC }

object TitleStyles {
    /** Du genre le plus marqué au plus général : « Horreur » l'emporte sur « Drame ». */
    private val RULES: List<Pair<List<String>, TitleStyle>> = listOf(
        listOf("horreur", "horror") to TitleStyle.HORROR,
        listOf("western") to TitleStyle.WESTERN,
        listOf("animation", "kids", "familial", "family") to TitleStyle.PLAYFUL,
        listOf("science-fiction", "science fiction", "sci-fi") to TitleStyle.NEON,
        listOf("documentaire", "documentary") to TitleStyle.DOC,
        listOf("fantastique", "fantasy", "histoire", "history", "guerre", "war") to TitleStyle.GOLD,
        listOf("crime", "policier", "thriller", "mystere", "mystery") to TitleStyle.STAMP,
        listOf("action", "aventure", "adventure") to TitleStyle.METAL,
        listOf("comedie", "comedy") to TitleStyle.PLAYFUL,
    )

    private val MARKS = Regex("\\p{M}+")

    private fun norm(s: String) = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    /** Style d'après les genres (noms TMDB français ou anglais) ; drame, romance ou inconnu → [TitleStyle.ELEGANT]. */
    fun of(genres: List<String>): TitleStyle {
        val g = genres.map(::norm)
        return RULES.firstOrNull { (keys, _) -> g.any { name -> keys.any { name.contains(it) } } }?.second ?: TitleStyle.ELEGANT
    }
}
