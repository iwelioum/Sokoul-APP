package com.sokoul.desktop.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * Grande franchise décrite à la main (`curated_sagas.json`, le même fichier que la tablette) : plusieurs collections
 * TMDB réunies, films hors collection, ordre du récit et séries du même univers.
 */
data class CuratedSaga(
    val key: String, val name: String, val collectionIds: List<Int>, val extraMovieIds: List<Int>,
    val chronologicalOrderIds: List<Int>, val relatedSeriesTmdbIds: List<Int>,
)

object CuratedSagas {
    val all: List<CuratedSaga> by lazy {
        runCatching { CuratedSagas::class.java.getResourceAsStream("/curated_sagas.json")?.use { parse(it.readBytes().decodeToString()) } }.getOrNull().orEmpty()
    }

    fun parse(text: String): List<CuratedSaga> {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        fun ids(o: JsonObject, k: String) = (o[k] as? JsonArray).orEmpty().mapNotNull { runCatching { it.jsonPrimitive.int }.getOrNull() }
        return (root["sagas"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            CuratedSaga(
                o["key"]?.jsonPrimitive?.content ?: return@mapNotNull null, o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                ids(o, "collectionIds"), ids(o, "extraMovieIds"), ids(o, "chronologicalOrderIds"), ids(o, "relatedSeriesTmdbIds"),
            )
        }
    }

    /** Franchise qui réunit la collection TMDB [collectionId], s'il y en a une. */
    fun forCollection(collectionId: Int, sagas: List<CuratedSaga> = all) = sagas.firstOrNull { collectionId in it.collectionIds }
}

/** Film d'une saga, tel que l'affiche la page (frise, prochain film). */
data class SagaFilm(val id: Int, val title: String, val year: Int?, val poster: String?, val backdrop: String?, val rating: Double?)

/** Ordre et suite d'une saga (logique pure, testée). */
object SagaOrder {
    /** Ordre de sortie (année), ou du récit quand [chronological] est connu : les films hors liste viennent ensuite. */
    fun ordered(films: List<SagaFilm>, chronological: List<Int>, narrative: Boolean): List<SagaFilm> {
        val byYear = films.sortedWith(compareBy<SagaFilm> { it.year ?: 9999 }.thenBy { it.id })
        if (!narrative || chronological.isEmpty()) return byYear
        val rank = chronological.withIndex().associate { (i, id) -> id to i }
        return byYear.sortedBy { rank[it.id] ?: (chronological.size + byYear.indexOf(it)) }
    }

    /** Premier film pas encore vu jusqu'au bout dans [ordered], ou null si tout est vu. */
    fun next(ordered: List<SagaFilm>, finished: Set<Int>): SagaFilm? = ordered.firstOrNull { it.id !in finished }

    /** « 11 films · 1977 → 2019 · ★ 7,2 de moyenne · 11 au catalogue · 4 séries » (films sortis seulement). */
    fun facts(films: List<SagaFilm>, series: Int, thisYear: Int = java.time.Year.now().value): String {
        val out = films.filter { it.year != null && it.year <= thisYear }
        val years = out.mapNotNull { it.year }
        val rating = out.mapNotNull { it.rating?.takeIf { r -> r >= 1.0 } }.average().takeIf { !it.isNaN() }
        return listOfNotNull(
            if (out.size > 1) "${out.size} films" else "1 film",
            years.minOrNull()?.let { a -> years.maxOrNull()?.let { b -> if (b > a) "$a → $b" else "$a" } },
            rating?.let { "★ " + String.format(java.util.Locale.FRANCE, "%.1f", it) + " de moyenne" },
            "${out.size} au catalogue",
            if (series > 1) "$series séries" else if (series == 1) "1 série" else null,
        ).joinToString(" · ")
    }
}
