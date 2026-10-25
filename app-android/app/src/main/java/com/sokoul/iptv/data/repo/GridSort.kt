package com.sokoul.iptv.data.repo

/** Tri d'une grille « Tout voir ». [DEFAULT] garde l'ordre éditorial (classement, ajout, position). */
enum class GridSort(val label: String) {
    DEFAULT("Sélection"),
    TITLE("A → Z"),
    YEAR("Plus récents"),
    RATING("Mieux notés");

    fun apply(works: List<Work>): List<Work> = when (this) {
        DEFAULT -> works
        // tri stable : à égalité, l'ordre éditorial est conservé
        TITLE -> works.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.FRENCH).apply { strength = java.text.Collator.PRIMARY }) { it.title })
        YEAR -> works.sortedWith(compareByDescending<Work, Int?>(nullsFirst()) { it.year })
        RATING -> works.sortedWith(compareByDescending<Work, Double?>(nullsFirst()) { it.rating?.takeIf { r -> r > 0 } })
    }

    companion object {
        fun from(name: String?): GridSort = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
