package com.sokoul.iptv

import com.sokoul.iptv.ui.components.AgeTone
import com.sokoul.iptv.ui.components.ageTone
import org.junit.Assert.assertEquals
import org.junit.Test

class MetaChipsTest {
    @Test fun `tranches d'age`() {
        listOf("TP", "U", "0", "Tous publics", "0+").forEach { assertEquals(it, AgeTone.ALL, ageTone(it)) }
        assertEquals(AgeTone.TEN, ageTone("10"))
        assertEquals(AgeTone.TWELVE, ageTone("12"))
        assertEquals(AgeTone.TWELVE, ageTone("-12"))
        assertEquals(AgeTone.SIXTEEN, ageTone("16+"))
        assertEquals(AgeTone.EIGHTEEN, ageTone("18"))
        assertEquals("classification étrangère : badge neutre", AgeTone.OTHER, ageTone("PG-13"))
    }
}
