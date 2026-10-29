package com.sokoul.iptv

import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.SubtitleSize
import com.sokoul.iptv.player.AudioDelayProcessor
import com.sokoul.iptv.ui.screens.player.SleepTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Confort du lecteur : vitesse, minuterie, décalage du son, réglages des sous-titres. */
class PlayerComfortTest {
    @Test fun `minuterie - echeance, minutes restantes arrondies, fin d episode sans echeance`() {
        val now = 1_000_000L
        val s = SleepTimer.start(SleepTimer.Choice.Minutes(30), now)
        assertEquals(now + 30 * 60_000L, s.dueAt)
        assertEquals(30, SleepTimer.remainingMinutes(s, now))
        assertEquals(12, SleepTimer.remainingMinutes(s, now + 18 * 60_000L + 1))
        assertEquals(0, SleepTimer.remainingMinutes(s, now + 31 * 60_000L))
        val end = SleepTimer.start(SleepTimer.Choice.EndOfItem, now)
        assertNull(end.dueAt)
        assertEquals("Minuterie : fin de l'épisode", SleepTimer.menuLabel(end, now))
        assertEquals("Minuterie de mise en veille", SleepTimer.menuLabel(null, now))
    }

    @Test fun `vitesse - libelles`() {
        assertEquals("Normale", SleepTimer.speedLabel(1f))
        assertEquals("×1,25", SleepTimer.speedLabel(1.25f))
        assertEquals("×2", SleepTimer.speedLabel(2f))
        assertEquals("×0,5", SleepTimer.speedLabel(0.5f))
    }

    @Test fun `decalage du son - ligne a retard alignee sur une trame, bornee a 5 s`() {
        // 48 kHz stéréo 16 bits : 4 octets par trame
        assertEquals(48_000 / 10 * 4, AudioDelayProcessor.ringBytes(100, 48_000, 4))
        assertEquals(0, AudioDelayProcessor.ringBytes(0, 48_000, 4))
        assertEquals(48_000 * 5 * 4, AudioDelayProcessor.ringBytes(60_000, 48_000, 4))
        assertEquals(0, AudioDelayProcessor.ringBytes(200, 0, 4))
    }

    @Test fun `sous-titres - reglages par defaut, taille appliquee`() {
        val d = AppSettings()
        assertEquals(SubtitleSize.NORMAL, d.subtitleSize)
        assertEquals(1.3f, SubtitleSize.LARGE.scale)
        assertEquals(d, d.sanitized().copy(userAgent = d.userAgent))
    }
}
