package com.sokoul.iptv

import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.ui.screens.player.PlayerSource
import com.sokoul.iptv.ui.screens.player.StartPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** « Lecture » : la meilleure version lisible part directement, sans démarrer l'IPTV puis changer. */
class StartPickTest {
    private fun iptv(id: String, failed: Boolean = false) =
        PlayerSource(id, SourceOrigin.IPTV, AudioLanguage.VF, quality = VideoQuality.FHD_1080, where = "x", failed = failed)

    private fun rd(key: String, ready: Boolean? = true, failed: Boolean = false) =
        PlayerSource("rd:$key", SourceOrigin.DEBRID, AudioLanguage.MULTI, quality = VideoQuality.UHD_4K, where = "x", ready = ready, failed = failed)

    @Test fun `Real-Debrid classe devant l IPTV - lance directement`() {
        assertEquals("h:1", StartPick.debridFirst(listOf(rd("h:1"), iptv("m1"))))
    }

    @Test fun `IPTV classee devant - la version demandee part`() {
        assertNull(StartPick.debridFirst(listOf(iptv("m1"), rd("h:1"))))
    }

    @Test fun `version Real-Debrid a telecharger ou en echec - ecartee, la suivante compte`() {
        assertEquals("h:3", StartPick.debridFirst(listOf(rd("h:1", ready = false), rd("h:2", failed = true), rd("h:3"), iptv("m1"))))
        assertNull(StartPick.debridFirst(listOf(rd("h:1", ready = false), iptv("m1"))))
    }

    @Test fun `etat inconnu - tente comme une version prete`() {
        assertEquals("h:1", StartPick.debridFirst(listOf(rd("h:1", ready = null), iptv("m1"))))
    }

    @Test fun `rien de lisible ou recherche depassee - rien n est impose`() {
        assertNull(StartPick.debridFirst(emptyList()))
        assertNull(StartPick.debridFirst(listOf(iptv("m1", failed = true), rd("h:1", ready = false))))
    }
}
