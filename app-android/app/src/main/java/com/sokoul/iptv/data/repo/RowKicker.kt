package com.sokoul.iptv.data.repo

/**
 * Étiquette en capitales au-dessus du titre d'une rangée (« POUR VOUS », « GENRE », « ÉPOQUE »…) :
 * elle dit d'un coup d'œil pourquoi la rangée est là.
 */
object RowKicker {
    private val PERSONAL = listOf("Vous avez un faible", "Encore plus de", "Votre dose de")

    /** Étiquette de la rangée, sauf si le titre commence déjà par le même mot (« Reprendre la lecture »). */
    fun of(id: String, title: String): String? = raw(id, title)?.takeUnless { title.uppercase().startsWith(it) }

    private fun raw(id: String, title: String): String? = when {
        id == "continue" -> "REPRENDRE"
        id == "mylist" -> "MA LISTE"
        id == "channels" -> "EN DIRECT"
        id.startsWith("top10") -> "TENDANCE"
        id.startsWith("popular") || id.startsWith("buzz") -> "TENDANCE"
        id == "new" || id.startsWith("new-") || id.startsWith("arrivals") -> "NOUVEAUTÉS"
        id.startsWith("byw-") || id == "finish-saga" -> "POUR VOUS"
        id == "sagas" -> "SAGAS"
        id == "platforms" || id.startsWith("plat-") -> "PLATEFORMES"
        id.startsWith("year-") -> "ÉPOQUE"
        id == "toprated" -> "INCONTOURNABLES"
        id == "moods" -> "AMBIANCES"
        id == "cats" -> "CATALOGUE"
        id.startsWith("theme-") -> "THÈME"
        id.startsWith("spec-") -> spec(id.removePrefix("spec-"), title)
        else -> null
    }

    private fun spec(specId: String, title: String): String? {
        if (PERSONAL.any { title.startsWith(it) }) return "POUR VOUS"
        val s = RowCatalog.byId(specId) ?: return null
        return when {
            s.decade != null -> "ÉPOQUE"
            s.language != null -> "CINÉMA DU MONDE"
            s.keywords.isNotEmpty() -> "THÈME"
            s.genres.isNotEmpty() -> "GENRE"
            else -> "SÉLECTION"
        }
    }
}
