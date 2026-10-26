package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.repo.HomeComposer
import com.sokoul.iptv.data.repo.RowCatalog
import com.sokoul.iptv.data.repo.TasteEntry
import com.sokoul.iptv.data.repo.TasteProfile
import com.sokoul.iptv.data.repo.TmdbGenres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accueil personnalisé : profil de goûts, catalogue de rangées, classement et titres du jour. */
class TasteTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun entry(
        id: String, genres: List<Int>, completion: Float = 1f, daysAgo: Int = 1, favorite: Boolean = false,
        type: ContentType = ContentType.MOVIE, lang: String? = "en", kw: List<String> = emptyList(), year: Int? = 2015,
    ) = TasteEntry(type, id, "Titre $id", genres, year, lang, kw, null, completion, now - daysAgo * day, favorite)

    @Test fun `profil - le genre le plus regarde pese le plus, normalise a 1`() {
        val p = TasteProfile.from(
            listOf(
                entry("1", listOf(TmdbGenres.THRILLER, TmdbGenres.CRIME)),
                entry("2", listOf(TmdbGenres.THRILLER)),
                entry("3", listOf(TmdbGenres.COMEDY)),
            ), now,
        )
        val g = p.genres.getValue(ContentType.MOVIE)
        assertEquals(1.0, g.getValue(TmdbGenres.THRILLER), 1e-9)
        assertTrue(g.getValue(TmdbGenres.COMEDY) < g.getValue(TmdbGenres.THRILLER))
        assertFalse(p.isEmpty)
    }

    @Test fun `profil - decroissance, abandon et Ma liste`() {
        val fresh = TasteProfile.weight(entry("a", emptyList(), daysAgo = 0), now)
        val month = TasteProfile.weight(entry("b", emptyList(), daysAgo = 30), now)
        assertEquals("demi-vie d'un mois", 0.5, month / fresh, 0.02)
        val dropped = TasteProfile.weight(entry("c", emptyList(), completion = 0.05f, daysAgo = 0), now)
        assertTrue("un abandon rapide compte peu", dropped < fresh / 3)
        val listed = TasteProfile.weight(entry("d", emptyList(), completion = 0f, favorite = true), now)
        assertEquals("Ma liste seule", 0.6, listed, 1e-9)
    }

    @Test fun `profil - trois oeuvres de depart, les plus marquantes, sans abandon`() {
        val p = TasteProfile.from(
            listOf(
                entry("old", listOf(1), daysAgo = 90),
                entry("new", listOf(1), daysAgo = 1),
                entry("mid", listOf(1), daysAgo = 10),
                entry("drop", listOf(1), completion = 0.05f, daysAgo = 0),
                entry("fav", listOf(1), completion = 0f, favorite = true),
            ), now,
        )
        assertEquals(listOf("new", "mid", "old"), p.seeds.map { it.itemId })
    }

    @Test fun `profil vide - demarrage a froid sans affinite`() {
        val p = TasteProfile.from(emptyList(), now)
        assertTrue(p.isEmpty)
        assertTrue(RowCatalog.all.all { p.affinity(it) == 0.0 })
    }

    @Test fun `catalogue - plus de 30 rangees par type, identifiants uniques, titres varies`() {
        val movies = RowCatalog.forType(ContentType.MOVIE)
        val series = RowCatalog.forType(ContentType.SERIES)
        assertTrue("films : ${movies.size}", movies.size >= 40)
        assertTrue("séries : ${series.size}", series.size >= 30)
        assertEquals("identifiants uniques", RowCatalog.all.size, RowCatalog.all.map { it.id }.toSet().size)
        // hors ambiances (titre fixe), chaque rangée a au moins 3 tournures
        val editorial = RowCatalog.all.filter { "-mood-" !in it.id && "-micro-" !in it.id }
        assertTrue(editorial.all { it.titles.size >= 3 })
        // titres en français naturel : majuscule au premier mot seulement (pas de « Title Case » anglais)
        editorial.flatMap { it.titles }.forEach { (title, _) ->
            val words = title.split(' ').filter { it.length > 3 && it.first().isLetter() }
            assertTrue("titre trop « Title Case » : $title", words.drop(1).count { it.first().isUpperCase() } <= 2)
        }
    }

    @Test fun `titres du jour - stables dans la journee, changent selon les jours`() {
        val spec = RowCatalog.byId("m-thriller")!!
        assertEquals(spec.title(100), spec.title(100))
        val week = (100 until 107).map { spec.title(it).first }.toSet()
        assertTrue("au moins deux titres sur une semaine : $week", week.size >= 2)
    }

    @Test fun `classement - les gouts remontent les rangees et leur donnent un titre personnel`() {
        val p = TasteProfile.from(List(5) { entry("t$it", listOf(TmdbGenres.THRILLER)) }, now)
        val ranked = HomeComposer.rank(RowCatalog.forType(ContentType.MOVIE), p, 100)
        val pos = ranked.indexOfFirst { it.spec.id == "m-thriller" }
        assertTrue("thrillers dans les 3 premières : $pos", pos in 0..2)
        val thriller = ranked[pos]
        assertTrue(thriller.personal)
        assertTrue(thriller.title(100).first.contains("thrillers"))
    }

    @Test fun `classement - jamais deux rangees voisines du meme genre principal`() {
        val p = TasteProfile.from(List(5) { entry("t$it", listOf(TmdbGenres.THRILLER, TmdbGenres.ACTION)) }, now)
        for (d in 0 until 20) {
            val ranked = HomeComposer.rank(RowCatalog.forType(ContentType.MOVIE), p, d)
            assertEquals(RowCatalog.forType(ContentType.MOVIE).size, ranked.size)
            ranked.zipWithNext().forEach { (a, b) ->
                val ga = a.spec.primaryGenre; val gb = b.spec.primaryGenre
                // tant qu'il reste d'autres rangées, deux voisines ne partagent pas leur genre principal
                if (ga != null && ga == gb) {
                    val rest = ranked.dropWhile { it !== b }.drop(1)
                    assertTrue("genre répété alors qu'il restait le choix (jour $d)", rest.all { it.spec.primaryGenre == ga })
                }
            }
        }
    }

    @Test fun `classement - sans historique, l'ordre change d'un jour a l'autre`() {
        val a = HomeComposer.rank(RowCatalog.forType(ContentType.MOVIE), TasteProfile.EMPTY, 10).map { it.spec.id }
        val b = HomeComposer.rank(RowCatalog.forType(ContentType.MOVIE), TasteProfile.EMPTY, 11).map { it.spec.id }
        assertNotEquals(a.take(10), b.take(10))
        assertEquals(a, HomeComposer.rank(RowCatalog.forType(ContentType.MOVIE), TasteProfile.EMPTY, 10).map { it.spec.id })
    }
}
