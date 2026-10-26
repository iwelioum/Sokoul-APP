package com.sokoul.iptv

import com.sokoul.iptv.data.repo.People
import com.sokoul.iptv.ui.screens.person.alsoKnownFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Page d'une personne : métier accordé, ligne d'identité (naissance, âge, décès). */
class PeopleTest {
    private val today = LocalDate.of(2026, 10, 26)

    @Test fun `metier accorde`() {
        assertEquals("Actrice", People.role("Acting", 1))
        assertEquals("Acteur", People.role("Acting", 2))
        assertEquals("Acteur", People.role("Acting", 0))
        assertEquals("Réalisatrice", People.role("Directing", 1))
        assertNull(People.role(null, 0))
        assertEquals("Aussi connue pour", alsoKnownFor("Actrice"))
        assertEquals("Aussi connu pour", alsoKnownFor("Réalisateur"))
        assertEquals("Aussi connu pour", alsoKnownFor(null))
    }

    @Test fun `naissance, age et lieu`() {
        assertEquals("Née le 17 décembre 1975 à Kyiv, Ukraine · 50 ans", People.facts("1975-12-17", null, "Kyiv, Ukraine", 1, today))
        assertEquals("Né le 1er mars 1980 · 46 ans", People.facts("1980-03-01", null, null, 2, today))
    }

    @Test fun `deces et dates illisibles`() {
        assertEquals("1942 – 2016 (73 ans) · Londres", People.facts("1942-07-14", "2016-01-10", "Londres", 2, today))
        assertEquals("Paris", People.facts("vers 1900", null, "Paris", 0, today))
        assertNull(People.facts(null, null, null, 0, today))
    }
}
