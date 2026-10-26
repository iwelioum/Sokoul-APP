package com.sokoul.iptv

import com.sokoul.iptv.data.repo.HomeFreshness
import com.sokoul.iptv.data.repo.HomeFreshness.Reason
import com.sokoul.iptv.data.repo.PageStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accueil vivant : règles de recalcul (horloge et état simulés), nouveautés de la semaine, héros selon les goûts. */
class HomeFreshnessTest {
    private val t0 = 1_800_000_000_000L
    private val stamp = PageStamp(builtAt = t0, day = 100, lastPlay = t0 - 60_000, favorites = 4)

    @Test fun `page encore juste`() {
        assertNull(HomeFreshness.reason(stamp, t0 + 10 * 60_000, 100, t0 - 60_000, 4))
    }

    @Test fun `raisons de recalcul`() {
        assertEquals(Reason.AGE, HomeFreshness.reason(stamp, t0 + 31 * 60_000, 100, t0 - 60_000, 4))
        assertEquals(Reason.DAY, HomeFreshness.reason(stamp, t0 + 60_000, 101, t0 - 60_000, 4))
        assertEquals(Reason.PLAYED, HomeFreshness.reason(stamp, t0 + 60_000, 100, t0 + 30_000, 4))
        assertEquals(Reason.TASTE, HomeFreshness.reason(stamp, t0 + 60_000, 100, t0 - 60_000, 5))
    }

    @Test fun `ajouts de la semaine, dates fantaisistes ignorees`() {
        val now = t0
        assertTrue(HomeFreshness.addedThisWeek((now - 2 * 24 * 3600_000L) / 1000, now))
        assertFalse(HomeFreshness.addedThisWeek((now - 9 * 24 * 3600_000L) / 1000, now))
        assertFalse(HomeFreshness.addedThisWeek(0, now))
        assertFalse(HomeFreshness.addedThisWeek((now + 30L * 24 * 3600_000L) / 1000, now))
    }

    @Test fun `nouveaux episodes des series suivies`() {
        data class S(val id: String, val mod: Long)
        val list = listOf(S("a", 2_000), S("b", 1_000), S("c", 3_000), S("d", 5_000))
        // a : vue avant la mise à jour ; b : vue après ; c : vue avant ; d : jamais regardée
        val watched = mapOf("a" to 1_500_000L, "b" to 1_500_000L, "c" to 2_500_000L)
        assertEquals(listOf("c", "a"), HomeFreshness.newEpisodes(list, { it.id }, { it.mod }, watched).map { it.id })
    }

    @Test fun `heros - les gouts reordonnent sans effacer l'actualite`() {
        val pool = listOf("n1", "n2", "n3", "n4", "n5")
        val likes = mapOf("n4" to 1.0)
        val hero = HomeFreshness.heroPick(pool, { likes[it] ?: 0.0 }, 3)
        assertEquals(listOf("n4", "n1", "n2"), hero)
        assertEquals(listOf("n1", "n2"), HomeFreshness.heroPick(listOf("n1", "n2"), { 0.0 }, 6))
    }
}
