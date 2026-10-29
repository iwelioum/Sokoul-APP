package com.sokoul.iptv

import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.PinHasher
import com.sokoul.iptv.data.PinThrottle
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.RecordingEntity
import com.sokoul.iptv.data.db.RecordingStatus
import com.sokoul.iptv.data.net.HttpModule
import com.sokoul.iptv.data.repo.BackupCrypto
import com.sokoul.iptv.data.repo.BackupPassphraseException
import com.sokoul.iptv.data.repo.BackupRules
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.PlaylistBackup
import com.sokoul.iptv.data.repo.PlaylistSource
import com.sokoul.iptv.data.repo.StreamResolver
import com.sokoul.iptv.data.repo.SyncEngine
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.recording.RecordingRules
import com.sokoul.iptv.ui.MultiviewSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class PinTest {
    @Test fun `hash puis verification`() {
        val h = PinHasher.hash("1234")
        assertTrue(PinHasher.isHash(h))
        assertFalse(h.contains("1234"))
        assertTrue(PinHasher.verify("1234", h))
        assertFalse(PinHasher.verify("1235", h))
        assertNotEquals(h, PinHasher.hash("1234")) // sel aléatoire
    }

    @Test fun `ancien code en clair accepte`() {
        assertTrue(PinHasher.verify("4321", "4321"))
        assertFalse(PinHasher.verify("0000", "4321"))
    }

    @Test fun `codes invalides`() {
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.verify("", PinHasher.hash("1111")))
        assertFalse(PinHasher.isValidStored("pbkdf2:abc"))
        assertTrue(PinHasher.isValidStored(""))
    }

    @Test fun `limitation des essais`() {
        assertEquals(0, PinThrottle.lockDuration(4))
        assertEquals(30_000, PinThrottle.lockDuration(5))
        assertEquals(60_000, PinThrottle.lockDuration(6))
        assertEquals(15 * 60_000L, PinThrottle.lockDuration(50))
    }
}

class SettingsSanitizeTest {
    @Test fun `valeurs aberrantes bornees`() {
        val s = AppSettings(epgOffsetMinutes = 99_999, playlistRefreshHours = 0, epgRefreshHours = 10_000, userAgent = "  ", parentalPin = "abc", tmdbApiKey = "k e y<script>").sanitized()
        assertEquals(720, s.epgOffsetMinutes)
        assertEquals(1, s.playlistRefreshHours)
        assertEquals(168, s.epgRefreshHours)
        assertEquals(AppSettings.DEFAULT_USER_AGENT, s.userAgent)
        assertEquals("", s.parentalPin)
        assertEquals("keyscript", s.tmdbApiKey)
    }
}

class BackupCryptoTest {
    @Test fun `aller-retour chiffre`() {
        val sealed = BackupCrypto.seal("secret".toByteArray(), "phrase-longue", iterations = 10_000)
        assertFalse(sealed.data.contains("secret"))
        assertEquals("secret", BackupCrypto.open(sealed, "phrase-longue").decodeToString())
    }

    @Test(expected = BackupPassphraseException::class)
    fun `mauvaise phrase refusee`() {
        val sealed = BackupCrypto.seal("x".toByteArray(), "phrase-longue", iterations = 10_000)
        BackupCrypto.open(sealed, "autre-phrase")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `phrase trop courte`() { BackupCrypto.seal("x".toByteArray(), "court") }

    @Test(expected = IOException::class)
    fun `enveloppe corrompue`() { BackupCrypto.open(BackupCrypto.Sealed(10_000, "zz", "00", "00"), "phrase-longue") }
}

class BackupRulesTest {
    @Test fun `detection des identifiants`() {
        assertTrue(BackupRules.hasCredentials("http://h/get.php?username=a&password=b&type=m3u"))
        assertTrue(BackupRules.hasCredentials("http://u:p@h/list.m3u"))
        assertFalse(BackupRules.hasCredentials("https://iptv-org.github.io/iptv/index.m3u"))
    }

    @Test fun `schemas acceptes`() {
        assertTrue(BackupRules.isHttpUrl("http://h:8080/x"))
        assertFalse(BackupRules.isHttpUrl("file:///data/data/com.sokoul.iptv/databases/x"))
        assertFalse(BackupRules.isHttpUrl("javascript:alert(1)"))
        assertFalse(BackupRules.isHttpUrl("http://h/a b"))
    }

    @Test fun `playlist valide normalisee`() {
        val p = BackupRules.validPlaylist(PlaylistBackup("  ", "XTREAM", "host.tv:8080/player_api.php", "user", "pw", epgUrl = "file:///x"))!!
        assertEquals("http://host.tv:8080", p.url)
        assertEquals(PlaylistType.XTREAM, p.type)
        assertNull(p.epgUrl)
        assertNull(p.passwordEnc) // le mot de passe n'est jamais recopié tel quel
    }

    @Test fun `playlists invalides rejetees`() {
        assertNull(BackupRules.validPlaylist(PlaylistBackup("a", "M3U", "file:///sdcard/x.m3u")))
        assertNull(BackupRules.validPlaylist(PlaylistBackup("a", "INCONNU", "http://h")))
        assertNull(BackupRules.validPlaylist(PlaylistBackup("a", "XTREAM", "http://h", username = null)))
        assertNull(BackupRules.validPlaylist(PlaylistBackup("a", "XTREAM", "ftp://h", username = "u")))
    }

    @Test fun `code parental importe`() {
        assertEquals("", BackupRules.importedPin(null))
        assertEquals("", BackupRules.importedPin("abcd"))
        assertTrue(PinHasher.verify("1234", BackupRules.importedPin("1234")))
        val h = PinHasher.hash("9999")
        assertEquals(h, BackupRules.importedPin(h))
    }

    @Test fun `sources de playlist`() {
        assertEquals(PlaylistSource.HTTP, PlaylistSource.of("https://h/x.m3u"))
        assertEquals(PlaylistSource.CONTENT, PlaylistSource.of("content://com.android.providers/document/1"))
        assertEquals(PlaylistSource.INVALID, PlaylistSource.of("file:///data/data/x"))
        assertEquals(PlaylistSource.INVALID, PlaylistSource.of(""))
    }
}

class RecordingRulesTest {
    @Test fun series_key_ignores_accents_and_punctuation() {
        val key = RecordingRules.seriesKey("FR|TF1 HD", "L'été est à nous !")
        assertTrue(RecordingRules.matchesSeries("tf1", "L ETE EST A NOUS", key))
        assertFalse(RecordingRules.matchesSeries("M6", "L'été est à nous", key))
    }

    private val now = 1_000_000_000L
    private fun rec(ch: String, start: Long, end: Long, status: RecordingStatus = RecordingStatus.SCHEDULED) =
        RecordingEntity(playlistId = 1, channelId = ch, channelName = ch, title = "T-$ch", startAt = start, endAt = end, status = status)

    @Test fun `dates incoherentes`() {
        assertTrue(RecordingRules.check("a", now + 10, now, emptyList(), null, now) is RecordingRules.Verdict.Refused)
        assertTrue(RecordingRules.check("a", now - 100, now - 10, emptyList(), null, now) is RecordingRules.Verdict.Refused)
        assertTrue(RecordingRules.check("a", now, now + 13 * 3600_000L, emptyList(), null, now) is RecordingRules.Verdict.Refused)
    }

    @Test fun `meme chaine en chevauchement refusee`() {
        val v = RecordingRules.check("a", now, now + 3600_000, listOf(rec("a", now + 1000, now + 7200_000)), null, now)
        assertTrue(v is RecordingRules.Verdict.Refused)
    }

    @Test fun `limite de connexions`() {
        val existing = listOf(rec("b", now, now + 3600_000))
        assertTrue(RecordingRules.check("a", now, now + 3600_000, existing, 1, now) is RecordingRules.Verdict.Refused)
        assertTrue(RecordingRules.check("a", now, now + 3600_000, existing, 2, now) is RecordingRules.Verdict.Ok)
        // un enregistrement terminé ou annulé ne compte pas
        val done = listOf(rec("b", now, now + 3600_000, RecordingStatus.CANCELLED))
        assertTrue(RecordingRules.check("a", now, now + 3600_000, done, 1, now) is RecordingRules.Verdict.Ok)
        // créneaux contigus : pas de conflit
        assertTrue(RecordingRules.check("a", now + 3600_000, now + 7200_000, existing, 1, now) is RecordingRules.Verdict.Ok)
    }

    @Test fun `avertissement connexion unique`() {
        val v = RecordingRules.check("a", now, now + 60_000, emptyList(), 1, now) as RecordingRules.Verdict.Ok
        assertTrue(v.warning != null)
    }

    @Test fun `codes de requete distincts`() {
        val codes = (1L..2000L).map(RecordingRules::recordRequestCode).toSet()
        assertEquals(2000, codes.size)
        val reminder = RecordingRules.reminderRequestCode(1, "42", now)
        assertTrue(reminder !in codes)
        assertEquals(reminder, RecordingRules.reminderRequestCode(1, "42", now))
    }

    @Test fun `nom de fichier sur`() {
        val n = RecordingRules.safeFileName("../../TF1", "Journal/20h:..")
        assertFalse(n.contains("/"))
        assertFalse(n.contains(".."))
        assertEquals("enregistrement", RecordingRules.safeFileName("", "///"))
    }
}

class LibraryRulesTest {
    private fun pr(pos: Long, dur: Long, finished: Boolean = false) =
        ProgressEntity(1, ContentType.MOVIE, "m", null, "t", null, null, pos, dur, 0, finished)

    @Test fun `seuil vu`() {
        assertFalse(LibraryRepository.isFinished(50 * 60_000L, 100 * 60_000L))
        assertTrue(LibraryRepository.isFinished(92 * 60_000L, 100 * 60_000L))
        assertTrue(LibraryRepository.isFinished(98 * 60_000L, 100 * 60_000L)) // générique
        assertFalse(LibraryRepository.isFinished(60_000L, 5 * 60_000L)) // contenu court
        assertFalse(LibraryRepository.isFinished(10, 0))
    }

    @Test fun `position de reprise`() {
        assertEquals(0, LibraryRepository.resumeFrom(null))
        assertEquals(0, LibraryRepository.resumeFrom(pr(20_000, 100_000)))
        assertEquals(0, LibraryRepository.resumeFrom(pr(600_000, 1_000_000, finished = true)))
        assertEquals(595_000, LibraryRepository.resumeFrom(pr(600_000, 6_000_000)))
    }
}

class MiscRulesTest {
    @Test fun `echappement LIKE`() {
        assertEquals("100\\%", CatalogRepository.likeEscape("100%"))
        assertEquals("a\\_b", CatalogRepository.likeEscape("a_b"))
        assertEquals("c\\\\d", CatalogRepository.likeEscape("c\\d"))
    }

    @Test fun `fenetre de replay`() {
        val now = 100L * 86_400_000L
        fun ch(days: Int) = ChannelEntity(1, "1", 1, "c", "c", null, "g", null, null, "c", null, "xtream", null, days, null, null, 0, 0)
        assertTrue(StreamResolver.inCatchupWindow(ch(2), now - 86_400_000L, now))
        assertFalse(StreamResolver.inCatchupWindow(ch(2), now - 3 * 86_400_000L, now))
        assertTrue(StreamResolver.inCatchupWindow(ch(0), 0, now))
    }

    @Test fun `liste vide du serveur conservee`() {
        assertTrue(SyncEngine.shouldKeepExisting(0, 500))
        assertFalse(SyncEngine.shouldKeepExisting(0, 0))
        assertFalse(SyncEngine.shouldKeepExisting(10, 500))
    }

    @Test fun `messages expurges`() {
        val e = IOException("Erreur sur http://h:80/live/jean/motdepasse/123.ts et get.php?username=jean&password=motdepasse")
        val m = e.userMessage()
        assertFalse(m.contains("motdepasse"))
        assertFalse(m.contains("jean"))
        assertFalse(HttpModule.redact("http://h/timeshift/u/p/60/2024/1.ts").contains("/u/p/"))
    }

    @Test fun `multi-ecran sans ecrasement`() {
        val s = MultiviewSession()
        repeat(4) { assertEquals(MultiviewSession.AddResult.ADDED, s.add(1, "c$it")) }
        assertEquals(MultiviewSession.AddResult.FULL, s.add(1, "c9"))
        assertEquals("c3", s.slots.value[3]?.channelId)
        assertEquals(MultiviewSession.AddResult.ALREADY_PRESENT, s.add(1, "c0"))
        s.set(9, null) // index hors limites ignoré
        s.removePlaylist(1)
        assertTrue(s.slots.value.all { it == null })
    }

    @Test fun `identifiant de chaine invalide`() {
        try {
            com.sokoul.iptv.parser.ServerUrl.normalize("ftp://x")
            fail()
        } catch (e: IllegalArgumentException) { /* attendu */ }
    }
}
