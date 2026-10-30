package com.sokoul.iptv.data.repo

// Fonctions pures de `SagaLogic` (app/.../data/repo/Sagas.kt) reprises pour le PC : le logo d'une saga. Le reste du
// fichier de la tablette dépend d'Android (assets, Hilt).

object SagaLogic {
    /** Nom affiché d'une collection TMDB (« Star Wars - Saga » → « Star Wars »). */
    fun displayName(name: String) = name.replace(" - Saga", "").replace(" (Collection)", "").replace(" - Collection", "").trim()

    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace("&", "and").replace(Regex("[^a-z0-9]+"), "")

    /**
     * Film dont le logo peut représenter la saga : celui qui porte exactement son nom (titre français ou original :
     * « Alien », « Shrek », « Matrix »), sinon le premier film titré « Nom : sous-titre » (« Pirates des Caraïbes :
     * La Malédiction du Black Pearl ») dont le logo met le nom de la saga en avant. Sinon null : le logo d'un épisode
     * (« Harry Potter à l'école des sorciers ») tromperait sur le nom de la saga ; un titre généré prend le relais.
     */
    fun logoPart(name: String, parts: List<com.sokoul.iptv.data.net.TmdbItem>): Int? {
        val n = norm(displayName(name))
        if (n.isEmpty()) return null
        parts.firstOrNull { p -> norm(p.title) == n || p.originalTitle?.let(::norm) == n }?.let { return it.id }
        return parts.firstOrNull { p -> listOfNotNull(p.title, p.originalTitle).any { subtitled(it, n) } }?.id
    }

    /**
     * Le film retenu par [logoPart] ne porte le nom de la saga qu'en VO (« Star Wars » = « La Guerre des étoiles ») :
     * son logo français afficherait le titre de l'épisode, on prend alors le logo anglais.
     */
    fun englishLogo(name: String, parts: List<com.sokoul.iptv.data.net.TmdbItem>, id: Int): Boolean {
        val n = norm(displayName(name))
        val p = parts.firstOrNull { it.id == id } ?: return false
        return norm(p.title) != n && !subtitled(p.title, n)
    }

    /** « Nom : sous-titre » ou « Nom - sous-titre » dont le nom (normalisé) est exactement [n]. */
    private fun subtitled(title: String, n: String): Boolean {
        // chaque séparateur possible (« : », tirets) : la partie avant doit être le nom, la partie après non vide
        return SEPARATOR.findAll(title).any { m ->
            norm(title.substring(0, m.range.first)) == n && title.substring(m.range.last + 1).isNotBlank()
        }
    }

    private val SEPARATOR = Regex("\\s*[:\u2013\u2014]\\s*|\\s+-\\s+")
}
