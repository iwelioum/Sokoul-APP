package com.sokoul.iptv

import com.sokoul.iptv.data.repo.ExploreFilter
import com.sokoul.iptv.data.repo.ExploreQuery
import com.sokoul.iptv.data.repo.GridSort
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.ui.util.endsAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EveningTest {
    private fun at(h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 26, h, m, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun finitA() {
        assertEquals("Finit à 23h28", endsAt(148, at(21, 0)))
        assertEquals("Finit à 00h15", endsAt(75, at(23, 0))) // passe minuit
    }

    @Test fun finiAvant() {
        assertEquals(150, ExploreQuery.minutesUntil(23, at(20, 30)))
        assertEquals(90, ExploreQuery.minutesUntil(24, at(22, 30))) // minuit
        assertEquals(0, ExploreQuery.minutesUntil(22, at(22, 30))) // heure passée
    }

    @Test fun filtreDansLaRequete() {
        val q = ExploreQuery.build(1, ContentType.MOVIE, ExploreFilter(finishBy = 23), GridSort.DEFAULT, 10, nowMs = at(21, 0))
        assertTrue(q.sql.contains("t.runtimeMin <= ?"))
        val sansFiltre = ExploreQuery.build(1, ContentType.MOVIE, ExploreFilter(), GridSort.DEFAULT, 10, nowMs = at(21, 0))
        assertEquals(sansFiltre.argCount + 2, q.argCount) // la durée maximale, dans les deux branches de la requête
    }
}
