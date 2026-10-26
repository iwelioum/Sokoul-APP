package com.sokoul.iptv

import com.sokoul.iptv.ui.screens.player.TrackOption
import com.sokoul.iptv.ui.screens.player.showAudioColumn
import org.junit.Assert.assertFalse
import org.junit.Test

/** Carte « Audio et sous-titres » : pas de colonne audio sans vrai choix. */
class TracksPanelTest {
    @Test fun `aucune ou une seule piste audio - pas de colonne`() {
        assertFalse(showAudioColumn(emptyList()))
        assertFalse(showAudioColumn(listOf(TrackOption("Piste unique", null, -1, true))))
    }
}
