package com.sokoul.iptv.data.repo

import android.content.Context
import androidx.compose.runtime.Immutable
import com.sokoul.iptv.data.net.TmdbCredits
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Grande franchise décrite à la main (assets/curated_sagas.json) : elle peut réunir plusieurs collections TMDB
 * (Harry Potter + Les Animaux fantastiques), ajouter des films hors collection (Rogue One), donner l'ordre
 * du récit et les séries du même univers. Identifiants vérifiés sur TMDB au moment de l'écriture.
 */
@Serializable
data class CuratedSaga(
    val key: String,
    val name: String,
    val collectionIds: List<Int>,
    val extraMovieIds: List<Int> = emptyList(),
    val chronologicalOrderIds: List<Int> = emptyList(),
    val relatedSeriesTmdbIds: List<Int> = emptyList(),
)

@Serializable
data class CuratedSagaFile(val version: Int, val verified: String? = null, val sagas: List<CuratedSaga>)

object CuratedSagaParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): List<CuratedSaga> = json.decodeFromString(CuratedSagaFile.serializer(), text).sagas
}

@Singleton
class CuratedSagaStore @Inject constructor(@ApplicationContext private val context: Context) {
    val all: List<CuratedSaga> by lazy {
        runCatching { context.assets.open(FILE).bufferedReader().use { CuratedSagaParser.parse(it.readText()) } }
            .onFailure { Timber.w(it, "Sagas éditorialisées") }.getOrDefault(emptyList())
    }

    fun forCollection(id: Int): CuratedSaga? = all.firstOrNull { id in it.collectionIds }

    companion object { const val FILE = "curated_sagas.json" }
}

/** Visage récurrent d'une saga. */
@Immutable
data class SagaFace(val id: Int, val name: String, val photo: String?, val films: Int)

/** « Les coulisses » : un poste (RÉALISATION…) et les personnes qui l'ont tenu, les plus fidèles d'abord. */
@Immutable
data class SagaCrew(val label: String, val names: List<String>)

/** Page « musée » d'une saga. */
@Immutable
data class SagaPage(
    val id: Int,
    val name: String,
    val overview: String?,
    val backdrop: String?,
    /** Films par date de sortie ; ceux qui manquent au catalogue ont `available = false`. */
    val films: List<Work>,
    /** Films dans l'ordre du récit, si la saga est éditorialisée (sinon null : pas de bascule). */
    val story: List<Work>?,
    val series: List<Work>,
    val faces: List<SagaFace>,
    val crew: List<SagaCrew>,
    /** Logo de la saga (celui du film qui porte son nom), sinon null : nom en toutes lettres. */
    val logo: String? = null,
    /** Films déjà vus (uid), pour la frise et « Continuer la saga ». */
    val seen: Set<String> = emptySet(),
) {
    val inCatalog get() = films.count { it.available }
    val facts get() = SagaLogic.facts(films.map { it.year to it.rating })
}

object SagaLogic {
    /** Nom affiché d'une collection TMDB (« Star Wars - Saga » → « Star Wars »). */
    fun displayName(name: String) = name.replace(" - Saga", "").replace(" (Collection)", "").replace(" - Collection", "").trim()

    /** Acteurs présents dans au moins [min] films, les plus fidèles d'abord (puis les mieux placés au générique). */
    fun faces(credits: List<TmdbCredits>, min: Int = 2, limit: Int = 20): List<SagaFace> {
        data class Acc(val id: Int, var name: String, var photo: String?, val films: HashSet<Int> = HashSet(), var bestOrder: Int = 99)
        val acc = LinkedHashMap<Int, Acc>()
        for (c in credits) for (p in c.cast) {
            val a = acc.getOrPut(p.id) { Acc(p.id, p.name, p.photo) }
            a.films += c.movieId
            if (a.photo == null) a.photo = p.photo
            a.bestOrder = minOf(a.bestOrder, p.order)
        }
        return acc.values.filter { it.films.size >= min }
            .sortedWith(compareByDescending<Acc> { it.films.size }.thenBy { it.bestOrder })
            .take(limit).map { SagaFace(it.id, it.name, it.photo, it.films.size) }
    }

    private val JOBS = listOf(
        "RÉALISATION" to setOf("Director"),
        "MUSIQUE ORIGINALE" to setOf("Original Music Composer", "Music"),
        "PRODUCTION" to setOf("Producer"),
    )

    /** Réalisation, musique et production : 3 noms au plus par poste, les plus présents d'abord. */
    fun crew(credits: List<TmdbCredits>, perJob: Int = 3): List<SagaCrew> = JOBS.mapNotNull { (label, jobs) ->
        val count = LinkedHashMap<String, MutableSet<Int>>()
        for (c in credits) for (p in c.crew) if (p.role in jobs) count.getOrPut(p.name) { HashSet() } += c.movieId
        val names = count.entries.sortedByDescending { it.value.size }.take(perJob).map { it.key }
        if (names.isEmpty()) null else SagaCrew(label, names)
    }

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

    /** Faits d'une saga (films sortis seulement) : nombre, première et dernière année, note moyenne. */
    fun facts(films: List<Pair<Int?, Double?>>, thisYear: Int = java.time.Year.now().value): SagaFacts {
        val out = films.filter { (y, _) -> y != null && y <= thisYear }
        val years = out.mapNotNull { it.first }
        val ratings = out.mapNotNull { it.second?.takeIf { r -> r >= 1.0 } }
        return SagaFacts(out.size, years.minOrNull(), years.maxOrNull(), if (ratings.isEmpty()) null else ratings.average())
    }

    /**
     * Prochain film à regarder, dans l'ordre affiché : le premier film du catalogue non vu qui suit le dernier vu
     * (on reprend là où l'on s'est arrêté), sinon le premier non vu. Null si tout est vu ou rien n'est disponible.
     */
    fun next(films: List<Work>, seen: Set<String>): Work? {
        val last = films.indexOfLast { it.uid in seen }
        return films.drop(last + 1).firstOrNull { it.available && it.uid !in seen }
            ?: films.firstOrNull { it.available && it.uid !in seen }
    }

    /** Ordre du récit : films listés d'abord dans l'ordre éditorial, les autres ensuite par date de sortie. */
    fun storyOrder(films: List<Work>, order: List<Int>): List<Work> {
        val rank = order.withIndex().associate { it.value to it.index }
        return films.withIndex().sortedWith(compareBy({ rank[it.value.tmdbId] ?: (order.size + it.index) })).map { it.value }
    }
}
