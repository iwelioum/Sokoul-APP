package com.sokoul.iptv.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.TimeZone

class XtreamTest {

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test
    fun `account status and expiry`() {
        val acc = XtreamParse.account(
            Json.parseToJsonElement(
                """{"user_info":{"auth":1,"status":"Expired","exp_date":"1700000000","max_connections":"2"},"server_info":{"timezone":"Europe/Paris"}}"""
            )
        )
        assertTrue(acc.auth)
        assertFalse(acc.isActive)
        assertEquals(1_700_000_000_000L, acc.expiresAt)
        assertEquals(2, acc.maxConnections)
        assertEquals("Europe/Paris", acc.serverTimezone)
    }

    @Test
    fun `lenient live and vod items`() {
        val live = XtreamParse.live(obj("""{"num":"3","name":"TF1","stream_id":"101","stream_icon":"","epg_channel_id":null,"category_id":"5","tv_archive":"1","tv_archive_duration":"7"}"""))!!
        assertEquals(101L, live.streamId)
        assertNull(live.icon)
        assertNull(live.epgChannelId)
        assertTrue(live.hasArchive)
        assertEquals(7, live.archiveDays)

        val vod = XtreamParse.vod(obj("""{"name":"FR| Le Parrain (1972)","stream_id":55,"rating":"","rating_5based":4.5,"container_extension":"mkv","category_ids":[9]}"""))!!
        assertEquals(9.0, vod.rating!!, 0.001)
        assertEquals("9", vod.categoryId)
        assertEquals(1972, vod.year)
        assertNull(XtreamParse.vod(obj("""{"name":"sans id"}""")))
    }

    @Test
    fun `series info accepts episodes as map or array and info as empty array`() {
        val asMap = XtreamParse.seriesInfo(
            Json.parseToJsonElement(
                """{"info":{"name":"Show","backdrop_path":["http://b.jpg"]},"episodes":{"2":[{"id":"20","episode_num":"1","title":"A","container_extension":"mp4","info":[]}],"1":[{"id":"10","episode_num":2,"info":{"duration":"00:45:00"}},{"id":"9","episode_num":1}]}}"""
            ), 7
        )
        assertEquals(listOf("9", "10", "20"), asMap.episodes.map { it.id })
        assertEquals(2700, asMap.episodes[1].durationSecs)
        assertEquals("http://b.jpg", asMap.series!!.backdrop)
        assertEquals(7L, asMap.series!!.seriesId)

        val asArray = XtreamParse.seriesInfo(
            Json.parseToJsonElement("""{"info":[],"episodes":[[{"id":"1","season":1,"episode_num":1}],[{"id":"2","season":2,"episode_num":1}]]}"""), 1
        )
        assertEquals(2, asArray.episodes.size)
        assertNull(asArray.series)
    }

    @Test
    fun `short epg decodes base64 titles`() {
        val b64 = Base64.getEncoder().encodeToString("Journal télévisé".toByteArray())
        val list = XtreamParse.shortEpg(
            Json.parseToJsonElement("""{"epg_listings":[{"title":"$b64","description":"","start":"2024-01-01 20:00:00","start_timestamp":"1704135600","stop_timestamp":"1704139200"}]}""")
        )
        assertEquals("Journal télévisé", list.single().title)
        assertEquals(1704135600000L, list.single().start)
        // un titre non encodé qui ressemble à du base64 est conservé tel quel
        assertEquals("News", XtreamParse.decodeMaybeBase64("News"))
    }

    @Test
    fun `stream urls`() {
        val s = "http://h:8080"
        assertEquals("http://h:8080/live/u%40x/p%20w/1.ts", XtreamUrls.live(s, "u@x", "p w", 1))
        assertEquals("http://h:8080/movie/u/p/2.mkv", XtreamUrls.movie(s, "u", "p", 2, "mkv"))
        assertEquals("http://h:8080/series/u/p/3.mp4", XtreamUrls.episode(s, "u", "p", "3", null))
        assertEquals(
            "http://h:8080/timeshift/u/p/60/2024-01-01:20-00/1.ts",
            XtreamUrls.timeshift(s, "u", "p", 1, 1704135600000L, 60, TimeZone.getTimeZone("Europe/Paris"))
        )
    }

    @Test
    fun `catchup templates`() {
        val start = 1704135600000L
        val end = start + 3_600_000
        val now = start + 7_200_000
        assertEquals(
            "http://x/a.m3u8?utc=1704135600&lutc=1704142800",
            Catchup.build("shift", null, "http://x/a.m3u8", start, end, now)
        )
        assertEquals(
            "http://x/a.m3u8?utc=1704135600&dur=3600",
            Catchup.build("default", "?utc={utc}&dur={duration}", "http://x/a.m3u8", start, end, now)
        )
        assertEquals(
            "http://f/ch/index-1704135600-3600.m3u8?token=1",
            Catchup.build("flussonic", null, "http://f/ch/index.m3u8?token=1", start, end, now)
        )
        assertNull(Catchup.build(null, null, "http://x", start, end, now))
    }
}
