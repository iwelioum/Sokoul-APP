package com.sokoul.iptv.parser

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Qualité réelle lue dans get_vod_info (bloc ffprobe du panel). */
class MediaInfoTest {
    private fun vod(s: String) = XtreamParse.vodInfo(Json.parseToJsonElement(s))

    @Test fun `4K HDR10 HEVC et son 5_1 Dolby Digital+`() {
        val m = vod(
            """{"info":{"name":"Dune","duration_secs":9300,"bitrate":18500,
               "video":{"codec_name":"hevc","width":3840,"height":1608,"color_transfer":"smpte2084","avg_frame_rate":"24000/1001"},
               "audio":{"codec_name":"eac3","channels":6,"tags":{"language":"fre"}}},
               "movie_data":{"stream_id":1,"container_extension":"mkv"}}"""
        )!!.media!!
        assertEquals("4K", MediaInfo.resolution(m.width, m.height))
        assertEquals("HEVC", MediaInfo.codec(m.videoCodec))
        assertEquals("HDR10", m.hdr)
        assertEquals(23.976, m.fps!!, 0.001)
        assertEquals("5.1", MediaInfo.channels(m.audioChannels))
        assertEquals("Dolby Digital+", MediaInfo.audioCodec(m.audioCodec))
        assertEquals("fre", m.audioLanguage)
        assertEquals(18500, m.bitrateKbps)
        assertEquals(9300, m.durationSecs)
    }

    @Test fun `scope 1920x800 est du 1080p, 720x576 du SD, Dolby Vision et HLG`() {
        assertEquals("1080p", MediaInfo.resolution(1920, 800))
        assertEquals("720p", MediaInfo.resolution(1280, 534))
        assertEquals("SD", MediaInfo.resolution(720, 576))
        assertNull(MediaInfo.resolution(null, null))
        val dv = vod("""{"info":{"video":{"codec_name":"hevc","width":3840,"height":2160,"side_data_list":[{"side_data_type":"DOVI configuration record"}]}}}""")!!.media!!
        assertEquals("Dolby Vision", dv.hdr)
        val hlg = vod("""{"info":{"video":{"codec_name":"hevc","width":3840,"height":2160,"color_transfer":"arib-std-b67"}}}""")!!.media!!
        assertEquals("HLG", hlg.hdr)
    }

    @Test fun `panel sans bloc video ou info vide - pas de mesure, debit en bits par seconde converti`() {
        assertNull(vod("""{"info":[],"movie_data":{"stream_id":1}}""")!!.media)
        assertNull(vod("""{"info":{"plot":"x"}}""")!!.media)
        assertEquals(4200, vod("""{"info":{"bitrate":"4200000"}}""")!!.media!!.bitrateKbps)
    }
}
