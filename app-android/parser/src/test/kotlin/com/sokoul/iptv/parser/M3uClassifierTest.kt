package com.sokoul.iptv.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uClassifierTest {
    private fun e(name: String, url: String, group: String? = null) =
        M3uEntry(name, url, emptyMap(), group, null, null, emptyMap())

    @Test
    fun classify() {
        assertEquals(M3uKind.LIVE, M3uClassifier.classify(e("TF1", "http://h/live/u/p/1.ts")))
        assertEquals(M3uKind.LIVE, M3uClassifier.classify(e("TF1", "http://h/u/p/1")))
        assertEquals(M3uKind.MOVIE, M3uClassifier.classify(e("Dune", "http://h/movie/u/p/2.mkv")))
        assertEquals(M3uKind.EPISODE, M3uClassifier.classify(e("Lupin S01 E02", "http://h/series/u/p/3.mp4")))
        assertEquals(M3uKind.MOVIE, M3uClassifier.classify(e("Film", "http://cdn/x.mp4?token=1")))
        assertEquals(M3uKind.LIVE, M3uClassifier.classify(e("BFM", "http://cdn/bfm/index.m3u8", "VOD")))
    }
}
