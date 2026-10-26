package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.ContentType
import kotlin.math.abs
import kotlin.math.exp

/** Une œuvre regardée (ou mise dans Ma liste), avec sa fiche TMDB : la matière du profil de goûts. */
data class TasteEntry(
    val type: ContentType,
    /** Identifiant du film, ou de la série (pas de l'épisode). */
    val itemId: String,
    val title: String,
    val genreIds: List<Int>,
    val year: Int?,
    val language: String?,
    val keywords: List<String>,
    val collectionId: Int?,
    /** Part regardée (0..1) ; 0 pour un titre seulement mis dans Ma liste. */
    val completion: Float,
    val updatedAt: Long,
    val favorite: Boolean,
)

/** Œuvre de départ des rangées « Parce que vous avez regardé X ». */
data class TasteSeed(val type: ContentType, val itemId: String, val title: String)

/**
 * Profil de goûts : poids (0..1) par genre (films et séries séparés), décennie, langue et mot-clé, calculés à partir
 * de ce qui a été regardé récemment et de Ma liste. Sert à classer les rangées de l'accueil : rien n'est envoyé ailleurs.
 */
class TasteProfile(
    val genres: Map<ContentType, Map<Int, Double>>,
    val decades: Map<Int, Double>,
    val languages: Map<String, Double>,
    val keywords: Map<String, Double>,
    val seeds: List<TasteSeed>,
    /** Sagas commencées : collection TMDB → nombre de films vus. */
    val collections: Map<Int, Int>,
) {
    val isEmpty get() = genres.values.all { it.isEmpty() } && keywords.isEmpty() && languages.isEmpty()

    /** Affinité (0..~1,5) d'une rangée avec ce profil. */
    fun affinity(spec: RowSpec): Double {
        if (isEmpty) return 0.0
        val byGenre = genres[spec.type].orEmpty()
        val g = spec.genres.maxOfOrNull { byGenre[it] ?: 0.0 } ?: 0.0
        val k = spec.keywords.maxOfOrNull { keywords[it] ?: 0.0 } ?: 0.0
        val l = spec.language?.let { languages[it] } ?: 0.0
        val d = spec.decade?.let { decades[it] } ?: 0.0
        return 0.7 * g + 0.6 * k + 0.4 * l + 0.3 * d
    }

    companion object {
        val EMPTY = TasteProfile(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyMap())
        private const val DAY = 86_400_000.0
        /** Demi-vie du goût : ce qu'on a regardé il y a un mois compte moitié moins (décroissance exponentielle). */
        private const val TAU_DAYS = 30.0 / 0.693

        /** Poids d'une œuvre : part regardée × récence, plus un bonus pour Ma liste ; un abandon rapide compte peu. */
        fun weight(e: TasteEntry, now: Long): Double {
            val age = ((now - e.updatedAt).coerceAtLeast(0L) / DAY)
            val decay = exp(-age / TAU_DAYS)
            val watched = when {
                e.completion <= 0f -> 0.0
                e.completion < 0.15f -> 0.25
                else -> 0.4 + 0.6 * e.completion.coerceAtMost(1f)
            }
            return watched * decay + if (e.favorite) 0.6 else 0.0
        }

        fun from(entries: List<TasteEntry>, now: Long = System.currentTimeMillis()): TasteProfile {
            if (entries.isEmpty()) return EMPTY
            val genres = HashMap<ContentType, HashMap<Int, Double>>()
            val decades = HashMap<Int, Double>()
            val langs = HashMap<String, Double>()
            val kws = HashMap<String, Double>()
            val collections = HashMap<Int, Int>()
            for (e in entries) {
                val w = weight(e, now)
                if (w <= 0.0) continue
                val byGenre = genres.getOrPut(e.type) { HashMap() }
                e.genreIds.forEach { byGenre.merge(it, w, Double::plus) }
                e.year?.let { decades.merge(it / 10 * 10, w, Double::plus) }
                e.language?.let { langs.merge(it, w, Double::plus) }
                e.keywords.forEach { kws.merge(it, w, Double::plus) }
                if (e.completion >= 0.8f) e.collectionId?.let { collections.merge(it, 1, Int::plus) }
            }
            // mots-clés : les 30 plus présents seulement (les autres sont du bruit)
            val topKw = kws.entries.sortedByDescending { it.value }.take(30).associate { it.key to it.value }
            val seeds = entries.filter { it.completion >= 0.15f }
                .sortedByDescending { weight(it, now) }
                .distinctBy { it.type to it.itemId }
                .take(3)
                .map { TasteSeed(it.type, it.itemId, it.title) }
            return TasteProfile(
                genres.mapValues { normalize(it.value) }, normalize(decades), normalize(langs), normalize(topKw), seeds, collections,
            )
        }

        private fun <K> normalize(m: Map<K, Double>): Map<K, Double> {
            val max = m.values.maxOrNull() ?: return emptyMap()
            if (max <= 0.0) return emptyMap()
            return m.mapValues { it.value / max }
        }
    }
}

/**
 * Ordre des rangées éditoriales : affinité avec le profil, priorité éditoriale et une part d'aléa du jour (la page
 * change d'un jour à l'autre sans bouger dans la journée). Deux rangées voisines n'ont jamais le même genre principal.
 */
object HomeComposer {
    /** Une rangée classée ; [personal] : remontée par les goûts (titre personnel). */
    data class Ranked(val spec: RowSpec, val score: Double, val personal: Boolean) {
        fun title(day: Int): Pair<String, String> = (if (personal) spec.personalTitle(day) else null) ?: spec.title(day)
    }

    /** Aléa déterministe (0..1) par rangée et par jour. */
    fun noise(id: String, day: Int): Double {
        var h = id.hashCode().toLong() * 31 + day * 2_654_435_761L
        h = h xor (h ushr 17); h *= 0x5bd1e995; h = h xor (h ushr 15)
        return (abs(h) % 10_000) / 10_000.0
    }

    fun rank(specs: List<RowSpec>, profile: TasteProfile, day: Int): List<Ranked> {
        val scored = specs.map { s ->
            val a = profile.affinity(s)
            Ranked(s, s.weight * 0.6 + a * 1.6 + noise(s.id, day) * 0.45, personal = false) to a
        }
        // les deux rangées les plus proches des goûts (et nommables) prennent un titre personnel
        val personalIds = scored.filter { it.second >= 0.35 && it.first.spec.noun != null }
            .sortedByDescending { it.second }.take(2).map { it.first.spec.id }.toSet()
        val pool = scored.map { (r, _) -> if (r.spec.id in personalIds) r.copy(personal = true, score = r.score + 0.3) else r }
            .sortedByDescending { it.score }.toMutableList()
        val out = ArrayList<Ranked>(pool.size)
        while (pool.isNotEmpty()) {
            val prev = out.lastOrNull()?.spec
            // la mieux classée qui ne répète ni le genre principal ni le type de règle de la précédente
            val i = pool.indexOfFirst { r -> prev == null || !similar(prev, r.spec) }.takeIf { it >= 0 } ?: 0
            out += pool.removeAt(i)
        }
        return out
    }

    private fun similar(a: RowSpec, b: RowSpec): Boolean =
        (a.primaryGenre != null && a.primaryGenre == b.primaryGenre) ||
            (a.language != null && a.language == b.language) ||
            (a.decade != null && b.decade != null)
}
