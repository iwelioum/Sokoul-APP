package com.sokoul.iptv

import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.ui.screens.guide.GuideTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GuideTimeTest {
    private fun at(day: Int, h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, h, m, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun prog(start: Long, end: Long) = ProgramEntity(1, "c", start, end, "Titre", null, null, null, null, 0)

    @Test fun `fenêtre par défaut = demi-heure en cours moins une demi-heure`() {
        val now = at(26, 20, 47)
        assertEquals(at(26, 20, 0), GuideTime.defaultWindow(now))
    }

    @Test fun `⏩ et ⏪ font glisser de 2 h et posent le focus dans la fenêtre`() {
        val w = at(26, 20, 0)
        val (w2, f2) = GuideTime.shift(w, GuideTime.JUMP)
        assertEquals(at(26, 22, 0), w2)
        assertEquals(at(26, 22, 30), f2)
        val (w3, _) = GuideTime.shift(w, -GuideTime.JUMP)
        assertEquals(at(26, 18, 0), w3)
    }

    @Test fun `sept jours à partir d'aujourd'hui, à minuit`() {
        val days = GuideTime.days(at(26, 20, 47))
        assertEquals(7, days.size)
        assertEquals(at(26, 0, 0), days[0])
        assertEquals(at(27, 0, 0), days[1])
        assertEquals(at(32, 0, 0), days[6]) // 1er novembre : passage à l'heure d'hiver le 25/10 déjà fait, calendrier lenient
        assertTrue(days.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun `aller à demain garde l'heure du programme focalisé, aujourd'hui revient à maintenant`() {
        val now = at(26, 10, 5)
        val focus = at(26, 20, 50)
        val (w, f) = GuideTime.jumpTo(GuideTime.dayStart(at(27, 3, 0)), now, focus)
        assertEquals(at(27, 20, 50), f)
        assertEquals(at(27, 20, 0), w)
        val (w0, f0) = GuideTime.jumpTo(GuideTime.dayStart(now), now, f)
        assertEquals(now, f0)
        assertEquals(GuideTime.defaultWindow(now), w0)
    }

    @Test fun `étiquette, durée et temps restant de l'aperçu`() {
        val now = at(26, 21, 0)
        val live = prog(at(26, 20, 50), at(26, 22, 20))
        assertEquals("EN CE MOMENT", GuideTime.kicker(live, now, catchup = false))
        assertEquals("1 h 30", GuideTime.duration(live))
        assertEquals("reste 1 h 20", GuideTime.remaining(live, now))
        val past = prog(at(26, 18, 0), at(26, 18, 45))
        assertEquals("DÉJÀ DIFFUSÉ · REPLAY", GuideTime.kicker(past, now, catchup = true))
        assertEquals("45 min", GuideTime.duration(past))
        val tomorrow = prog(at(27, 20, 50), at(27, 23, 0))
        assertEquals("DEMAIN · 20:50", GuideTime.kicker(tomorrow, now, catchup = false))
    }
}
