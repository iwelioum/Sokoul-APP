package com.sokoul.iptv

import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.PlaybackSource
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.player.FrameRateMatcher
import com.sokoul.iptv.player.FrameRateMatcher.Mode
import com.sokoul.iptv.ui.screens.player.IntroSkip
import com.sokoul.iptv.ui.screens.player.LighterVersion
import com.sokoul.iptv.ui.screens.player.SubtitleOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCinemaTest {
    private val modes = listOf(
        Mode(1, 3840, 2160, 60f), Mode(2, 3840, 2160, 50f), Mode(3, 3840, 2160, 24f),
        Mode(4, 3840, 2160, 23.976f), Mode(5, 1920, 1080, 24f), Mode(6, 3840, 2160, 59.94f),
    )
    private val now = modes[0]

    @Test fun frequenceDuFilm() {
        assertEquals(4, FrameRateMatcher.pick(modes, now, 23.976f)) // 23,976 ≠ 24
        assertEquals(3, FrameRateMatcher.pick(modes, now, 24f))
        assertEquals(4, FrameRateMatcher.pick(modes, now, 23.98f)) // fréquence arrondie par le fichier
        assertEquals(2, FrameRateMatcher.pick(modes, now, 25f)) // 50 Hz, même définition (pas le 1080p)
        assertNull(FrameRateMatcher.pick(modes, now, 30f)) // 60 Hz convient déjà
        assertNull(FrameRateMatcher.pick(modes, now, -1f)) // fréquence inconnue
        assertEquals(6, FrameRateMatcher.pick(modes, now, 29.97f))
        assertNull(FrameRateMatcher.pick(listOf(now), now, 24f)) // pas d'autre mode : rien à changer
        // 25 i/s : 50 Hz plutôt que 25 Hz quand les deux existent
        assertEquals(2, FrameRateMatcher.pick(modes + Mode(7, 3840, 2160, 25f), now, 25f))
        assertEquals(7, FrameRateMatcher.pick(listOf(now, Mode(7, 3840, 2160, 25f)), now, 25f))
    }

    @Test fun frequenceMesuree() {
        // horodatages MKV arrondis à la milliseconde
        fun pts(fps: Double, n: Int = 120) = (0 until n).map { i -> (i * 1000.0 / fps).toLong() * 1000 }
        assertEquals(23.976f, FrameRateMatcher.estimate(pts(24000.0 / 1001))!!, 0.001f)
        assertEquals(24f, FrameRateMatcher.estimate(pts(24.0))!!, 0.001f)
        assertEquals(25f, FrameRateMatcher.estimate(pts(25.0))!!, 0.001f)
        assertEquals(59.94f, FrameRateMatcher.estimate(pts(60000.0 / 1001))!!, 0.001f)
        assertNull(FrameRateMatcher.estimate(pts(24.0).reversed())) // pas dans l'ordre
        assertNull(FrameRateMatcher.estimate(pts(37.0))) // aucune fréquence standard
    }

    @Test fun introApprise() {
        val w = IntroSkip.learn(62_000, 152_000)!!
        assertEquals(62_000, w.startMs)
        assertNull(IntroSkip.learn(62_000, 92_000)) // saut trop court
        assertNull(IntroSkip.learn(700_000, 790_000)) // après 10 min
        assertNull(IntroSkip.learn(62_000, 400_000)) // saut trop long : pas une intro
        assertTrue(IntroSkip.visible(50_000, w))
        assertTrue(IntroSkip.visible(100_000, w))
        assertFalse(IntroSkip.visible(30_000, w))
        assertFalse(IntroSkip.visible(145_000, w))
    }

    @Test fun decalageSousTitres() {
        assertEquals(250, SubtitleOffset.shift(0, 250, delayOnly = false))
        assertEquals(-250, SubtitleOffset.shift(0, -250, delayOnly = false))
        assertEquals(0, SubtitleOffset.shift(0, -250, delayOnly = true))
        assertEquals(10_000, SubtitleOffset.shift(9_900, 250, delayOnly = false))
        assertEquals("+0,25 s", SubtitleOffset.label(250))
        assertEquals("−1,50 s", SubtitleOffset.label(-1_500))
        assertEquals("0 s", SubtitleOffset.label(0))
    }

    private fun src(q: VideoQuality?, gb: Long? = null, lang: AudioLanguage = AudioLanguage.VF) =
        PlaybackSource("x", SourceOrigin.DEBRID, quality = q, language = lang, sizeBytes = gb?.let { it * 1_000_000_000 })

    @Test fun versionPlusLegere() {
        val cur = src(VideoQuality.UHD_4K, 60)
        val options = listOf(
            "a" to cur,
            "b" to src(VideoQuality.FHD_1080, 12, AudioLanguage.VOSTFR),
            "c" to src(VideoQuality.FHD_1080, 6),
            "d" to src(VideoQuality.SD, 1),
        )
        assertEquals("c", LighterVersion.pick("a", cur, options)?.first) // même langue d'abord
        assertEquals("b", LighterVersion.pick("a", cur, options.take(2))?.first)
        // même définition, bien plus petite
        assertEquals("s", LighterVersion.pick("a", cur, listOf("s" to src(VideoQuality.UHD_4K, 20)))?.first)
        assertNull(LighterVersion.pick("a", cur, listOf("t" to src(VideoQuality.UHD_4K, 50))))
        // SD seulement en dernier recours
        assertEquals("c", LighterVersion.pick("a", cur, listOf("d" to src(VideoQuality.SD, 1), "c" to src(VideoQuality.FHD_1080, 6)))?.first)
    }
}
