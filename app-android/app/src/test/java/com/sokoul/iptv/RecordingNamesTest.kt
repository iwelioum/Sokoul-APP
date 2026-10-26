package com.sokoul.iptv

import com.sokoul.iptv.data.db.RecordingEntity
import com.sokoul.iptv.data.db.RecordingStatus
import com.sokoul.iptv.recording.RecordingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar

class RecordingNamesTest {
    private val start = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 26, 20, 50, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun `nom de chaîne lisible`() {
        assertEquals("TF1", RecordingRules.channelLabel("FR| TF1 FHD"))
        assertEquals("TF1 +1", RecordingRules.channelLabel("FR| TF1 +1 FHD"))
        assertEquals("M6", RecordingRules.channelLabel("FR: M6 HEVC HD"))
        assertEquals("Arte", RecordingRules.channelLabel("Arte"))
        assertEquals("HD", RecordingRules.channelLabel("HD"))
    }

    @Test fun `le titre ne répète plus la chaîne`() {
        assertEquals("TF1 - 2026-10-26 20h50", RecordingRules.fileBase("FR| TF1 FHD", "FR| TF1 FHD", start))
        assertEquals("TF1 - 2026-10-26 20h50", RecordingRules.fileBase("FR| TF1 FHD", "TF1", start))
        assertEquals("TF1 - Le Journal - 2026-10-26 20h50", RecordingRules.fileBase("FR| TF1 FHD", "Le Journal", start))
    }

    @Test fun `nom de fichier sûr`() {
        val n = RecordingRules.fileBase("../../TF1", "Journal/20h:..", start)
        assertFalse(n.contains('/'))
        assertFalse(n.contains(':'))
        assertFalse(n.contains(".."))
    }

    private fun rec(id: Long, status: RecordingStatus, at: Long) =
        RecordingEntity(id = id, playlistId = 1, channelId = "c", channelName = "TF1", title = "T$id", startAt = at, endAt = at + 1, status = status)

    @Test fun `rubriques dans l'ordre, à venir chronologiques, terminés du plus récent`() {
        val s = RecordingRules.sections(listOf(
            rec(1, RecordingStatus.COMPLETED, 100), rec(2, RecordingStatus.SCHEDULED, 300), rec(3, RecordingStatus.SCHEDULED, 200),
            rec(4, RecordingStatus.COMPLETED, 500), rec(5, RecordingStatus.CANCELLED, 50), rec(6, RecordingStatus.RECORDING, 400),
        ))
        assertEquals(listOf(RecordingRules.Section.RECORDING, RecordingRules.Section.SCHEDULED, RecordingRules.Section.COMPLETED, RecordingRules.Section.FAILED), s.map { it.first })
        assertEquals(listOf(3L, 2L), s[1].second.map { it.id })
        assertEquals(listOf(4L, 1L), s[2].second.map { it.id })
        assertEquals(emptyList<Any>(), RecordingRules.sections(emptyList()))
    }
}
