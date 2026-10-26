package com.sokoul.iptv

import com.sokoul.iptv.ui.screens.playlist.SyncCounters
import com.sokoul.iptv.ui.screens.playlist.SyncCounters.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

/** Écran de synchronisation : compteurs par section déduits des étapes envoyées par la synchro. */
class OnboardingTest {
    @Test fun `xtream - sections terminees, en cours, en attente`() {
        var s = SyncCounters.State()
        s = SyncCounters.update(s, "Connexion…", 0)
        s = SyncCounters.update(s, "Chaînes…", 5000)
        s = SyncCounters.update(s, "Chaînes…", 28000)
        s = SyncCounters.update(s, "Films…", 1200)
        assertEquals(
            listOf(Triple("Chaînes", 28000, Phase.DONE), Triple("Films", 1200, Phase.RUNNING), Triple("Séries", 0, Phase.WAITING)),
            SyncCounters.sections(s),
        )
        // regroupement final : tout ce qui a été compté est terminé
        s = SyncCounters.update(s, "Séries…", 64000)
        s = SyncCounters.update(s, "Regroupement des doublons…", 0)
        assertEquals(listOf(Phase.DONE, Phase.DONE, Phase.DONE), SyncCounters.sections(s).map { it.third })
    }

    @Test fun `m3u - un seul compteur qui ne recule jamais`() {
        var s = SyncCounters.update(SyncCounters.State(), "Lecture de la playlist…", 900)
        s = SyncCounters.update(s, "Lecture de la playlist…", 400)
        assertEquals(listOf(Triple("Éléments", 900, Phase.RUNNING)), SyncCounters.sections(s))
    }
}
