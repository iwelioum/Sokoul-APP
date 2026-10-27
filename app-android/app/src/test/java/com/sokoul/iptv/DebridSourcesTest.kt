package com.sokoul.iptv

import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.debrid.Candidate
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.data.debrid.DebridSources
import com.sokoul.iptv.data.debrid.RdFile
import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.data.debrid.RipSource
import com.sokoul.iptv.data.debrid.SourceRanker
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Noms de versions réels, sources Stremio, classement IPTV + Real-Debrid, fichier d'un pack de saison. */
class DebridSourcesTest {

    @Test fun `noms reels lus correctement`() {
        data class Case(val name: String, val q: VideoQuality?, val hdr: HdrFormat?, val lang: AudioLanguage?, val src: RipSource?)
        val cases = listOf(
            Case("Dune.Part.Two.2024.MULTi.TRUEFRENCH.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.TrueHD.Atmos-FGT", VideoQuality.UHD_4K, HdrFormat.DOLBY_VISION, AudioLanguage.MULTI, RipSource.REMUX),
            Case("Oppenheimer.2023.FRENCH.1080p.WEB-DL.x264-NoTag", VideoQuality.FHD_1080, null, AudioLanguage.VF, RipSource.WEBDL),
            Case("Interstellar.2014.VOSTFR.720p.BluRay.x264", VideoQuality.HD_720, null, AudioLanguage.VOSTFR, RipSource.BLURAY),
            Case("The.Batman.2022.2160p.WEB-DL.DDP5.1.Atmos.HDR10.HEVC-CMRG", VideoQuality.UHD_4K, HdrFormat.HDR10, null, RipSource.WEBDL),
            Case("Breaking.Bad.S02E05.1080p.BluRay.x265.VFF.AC3", VideoQuality.FHD_1080, null, AudioLanguage.VF, RipSource.BLURAY),
            Case("Avatar.The.Way.of.Water.2022.MULTi.VFQ.1080p.WEBRip.x265", VideoQuality.FHD_1080, null, AudioLanguage.MULTI, RipSource.WEBRIP),
            Case("Gladiator.II.2024.HDR10+.2160p.WEB.H265", VideoQuality.UHD_4K, HdrFormat.HDR10, null, RipSource.WEBDL),
            Case("Le.Comte.de.Monte-Cristo.2024.FRENCH.2160p.WEB.DV.HDR.H265", VideoQuality.UHD_4K, HdrFormat.DOLBY_VISION, AudioLanguage.VF, RipSource.WEBDL),
            Case("Friends.S01.COMPLETE.MULTi.1080p.BluRay.x264", VideoQuality.FHD_1080, null, AudioLanguage.MULTI, RipSource.BLURAY),
            Case("Les.Bronzes.1978.TRUEFRENCH.DVDRip.XviD", VideoQuality.SD, null, AudioLanguage.VF, null),
            Case("The.Office.US.S03E01.720p.HDTV.x264", VideoQuality.HD_720, null, null, RipSource.HDTV),
            Case("Top.Gun.Maverick.2022.IMAX.2160p.UHD.BluRay.x265.10bit.HDR.DTS-HD.MA", VideoQuality.UHD_4K, HdrFormat.HDR10, null, RipSource.BLURAY),
        )
        cases.forEach { c ->
            val r = ReleaseName.parse(c.name)
            assertEquals(c.name, c.q, r.quality)
            assertEquals(c.name, c.hdr, r.hdr)
            assertEquals(c.name, c.lang, r.language)
            assertEquals(c.name, c.src, r.source)
            assertFalse(c.name, r.cam)
        }
        assertEquals("TRUEFRENCH", ReleaseName.parse(cases[0].name).frenchVariant)
        assertEquals("HEVC", ReleaseName.parse(cases[0].name).codec)
    }

    @Test fun `films filmes en salle reperes, extension ts ignoree`() {
        assertTrue(ReleaseName.parse("Deadpool.and.Wolverine.2024.HDCAM.x264").cam)
        assertTrue(ReleaseName.parse("Joker.2019.FRENCH.TS.x264").cam)
        assertTrue(ReleaseName.parse("Movie.2024.TELESYNC.720p").cam)
        assertFalse(ReleaseName.parse("Show.S01E01.1080p.WEB.h264.ts").cam)
    }

    @Test fun `format Torrentio taille sources cache drapeaux`() {
        val r = ReleaseName.parse("[RD+] Torrentio\n4k DV | HDR", "Dune.Part.Two.2024.2160p.mkv\n👤 152 💾 61.3 GB ⚙️ ThePirateBay\nMulti Audio / 🇫🇷 / 🇬🇧")
        assertEquals(VideoQuality.UHD_4K, r.quality)
        assertEquals(61_300_000_000L, r.sizeBytes)
        assertEquals(152, r.seeders)
        assertEquals(true, r.cached)
        assertEquals(AudioLanguage.MULTI, r.language)
        assertEquals(false, ReleaseName.parse("[RD download] Torrentio\n1080p", "x").cached)
        assertNull(ReleaseName.parse("Torrentio\n1080p", "x").cached)
        assertEquals(4_700_000_000L, ReleaseName.size("4,7 Go"))
    }

    @Test fun `reponse Stremio en versions, sans doublon ni film filme en salle`() {
        val text = """{"streams":[
            {"name":"[RD+] Torrentio\n4k","title":"Film.2024.MULTi.2160p.WEB.DV.mkv\n👤 10 💾 18.3 GB","url":"https://torrentio.strem.fun/resolve/realdebrid/x/abc/null/0/Film.mkv"},
            {"name":"Torrentio\n1080p","title":"Film.2024.FRENCH.1080p.BluRay.x264\n💾 9.1 GB","infoHash":"ABCDEF0123456789ABCDEF0123456789ABCDEF01","fileIdx":2},
            {"name":"Torrentio\n1080p","title":"Film.2024.FRENCH.1080p.BluRay.x264\n💾 9.1 GB","infoHash":"abcdef0123456789abcdef0123456789abcdef01","fileIdx":2},
            {"name":"Torrentio\nCAM","title":"Film.2024.HDCAM.x264","infoHash":"1111111111111111111111111111111111111111"},
            {"name":"rien","title":"sans lien"}
        ]}"""
        val s = DebridSources.parse(text)
        assertEquals(2, s.size)
        assertEquals("abcdef0123456789abcdef0123456789abcdef01:2", s[1].key)
        assertEquals("magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01", s[1].magnet)
        assertEquals(VideoQuality.UHD_4K, s[0].toSource().quality)
        assertEquals(com.sokoul.iptv.domain.SourceState.CACHED_INSTANT, s[0].toSource().state)
        assertTrue(DebridSources.parse("pas du json").isEmpty())
    }

    private fun stream(name: String, size: Long? = null, cached: Boolean? = true) = DebridSources.parse(
        """{"streams":[{"name":"${if (cached == true) "[RD+] " else if (cached == false) "[RD download] " else ""}x","title":"$name${size?.let { "\\n💾 ${it / 1_000_000_000} GB" } ?: ""}","infoHash":"${name.hashCode().toUInt().toString(16).padStart(8, '0').repeat(5)}"}]}"""
    ).single()

    @Test fun `classement langue puis definition, limite de taille`() {
        val iptvVf1080 = VodVersion("101", "Film FHD", "VF", "FHD", "c")
        val rdVf4k = stream("Film.2024.FRENCH.2160p.WEB.DV.HEVC", size = 18_000_000_000)
        val rdVo4k = stream("Film.2024.2160p.BluRay.REMUX.HEVC", size = 60_000_000_000)
        val rdVf1080 = stream("Film.2024.TRUEFRENCH.1080p.BluRay.x264", size = 9_000_000_000)

        val wifi = SourceRanker.Prefs(maxBytes = 25_000_000_000)
        val r = SourceRanker.rank(listOf(iptvVf1080), listOf(rdVo4k, rdVf1080, rdVf4k), wifi).map { it.source.id }
        // VF 4K d'abord, puis les deux VF 1080p (égalité parfaite : ordre stable), VO 60 Go en dernier (Wi-Fi)
        assertEquals(listOf(rdVf4k.key, "101", rdVf1080.key, rdVo4k.key), r)

        // connexion IPTV prise par un enregistrement : l'IPTV passe en dernier
        val busy = SourceRanker.rank(listOf(iptvVf1080), listOf(rdVf1080), wifi.copy(iptvBlocked = true))
        assertTrue(busy.first() is Candidate.Debrid)

        // box sans 4K : la 4K n'est plus proposée en premier
        val no4k = SourceRanker.rank(listOf(iptvVf1080), listOf(rdVf4k), wifi.copy(supports4k = false))
        assertTrue(no4k.first() is Candidate.Iptv)
    }

    @Test fun `aucune origine favorisee - la fiabilite puis le reglage departagent`() {
        val rd = stream("Film.2024.FRENCH.1080p.WEB.x264", size = 4_000_000_000)
        val iptv = VodVersion("101", "Film FHD", "VF", "FHD", "c")
        val measured1080 = com.sokoul.iptv.data.repo.VodQuality(width = 1920, height = 1080, videoCodec = "h264")
        // IPTV déjà lue (qualité mesurée) : passe devant Real-Debrid annoncé 1080p
        assertTrue(SourceRanker.rank(listOf(iptv.copy(measured = measured1080)), listOf(rd), SourceRanker.Prefs()).first() is Candidate.Iptv)
        // Real-Debrid déjà lu (mesure gardée en base) : passe devant l'IPTV annoncée 1080p
        val rdMeasured = SourceRanker.Prefs(debridMeasured = mapOf(rd.key to measured1080))
        assertTrue(SourceRanker.rank(listOf(iptv), listOf(rd), rdMeasured).first() is Candidate.Debrid)
        // égalité parfaite : le réglage « Source à égalité » décide, dans les deux sens
        assertTrue(SourceRanker.rank(listOf(iptv), listOf(rd), SourceRanker.Prefs(tieOrigin = com.sokoul.iptv.domain.SourceOrigin.DEBRID)).first() is Candidate.Debrid)
        assertTrue(SourceRanker.rank(listOf(iptv), listOf(rd), SourceRanker.Prefs(tieOrigin = com.sokoul.iptv.domain.SourceOrigin.IPTV)).first() is Candidate.Iptv)
        // échec récent : la source passe après l'autre de même qualité, mais reste proposée
        val failed = SourceRanker.rank(listOf(iptv), listOf(rd), SourceRanker.Prefs(failed = setOf("101"), tieOrigin = com.sokoul.iptv.domain.SourceOrigin.IPTV))
        assertEquals(listOf(rd.key, "101"), failed.map { SourceRanker.id(it) })
        // annoncée 4K mais mesurée 720p : la mesure compte, Real-Debrid 1080p passe devant
        val fake4k = VodVersion("102", "Film 4K", "VF", "4K", "c", measured = com.sokoul.iptv.data.repo.VodQuality(width = 1280, height = 720))
        assertTrue(SourceRanker.rank(listOf(fake4k), listOf(rd), SourceRanker.Prefs()).first() is Candidate.Debrid)
    }

    @Test fun `version en cache avant version a preparer a qualite egale`() {
        val ready = stream("Film.FRENCH.1080p.WEB.A", cached = true)
        val unknown = stream("Film.FRENCH.1080p.WEB.B", cached = null)
        val notReady = stream("Film.FRENCH.1080p.WEB.C", cached = false)
        val r = SourceRanker.rank(emptyList(), listOf(notReady, unknown, ready), SourceRanker.Prefs())
        assertEquals(listOf(ready.key, unknown.key, notReady.key), r.map { it.source.id })
    }

    @Test fun `taille maximale automatique selon le reseau`() {
        assertEquals(25_000_000_000L, DebridSources.maxBytes(AppSettings(), ethernet = false))
        assertNull(DebridSources.maxBytes(AppSettings(), ethernet = true))
        assertEquals(10_000_000_000L, DebridSources.maxBytes(AppSettings(debridMaxGb = 10), ethernet = true))
        assertNull(DebridSources.maxBytes(AppSettings(debridMaxGb = 200), ethernet = false))
        assertEquals("https://torrentio.strem.fun/qualityfilter=cam", AppSettings.cleanAddon("stremio://torrentio.strem.fun/qualityfilter=cam/manifest.json"))
        assertEquals("", AppSettings.cleanAddon("http://insecure.example/manifest.json"))
    }

    @Test fun `episode choisi dans un pack de saison`() {
        val files = listOf(
            RdFile(1, "/Show.S02/Show.S02E04.1080p.mkv", 2_000, false),
            RdFile(2, "/Show.S02/Show.S02E05.1080p.mkv", 2_100, false),
            RdFile(3, "/Show.S02/Sample/Show.S02E05.sample.mkv", 50, false),
            RdFile(4, "/Show.S02/Extras/Bloopers.mkv", 9_000, false),
            RdFile(5, "/Show.S02/Show.S02E05.fr.srt", 1, false),
        )
        assertEquals(2, ReleaseName.pickFile(files, 2, 5)?.id)
        assertNull(ReleaseName.pickFile(files, 2, 9))
        // film : la plus grosse vidéo, jamais un bonus
        assertEquals(2, ReleaseName.pickFile(files, null, null)?.id)
        assertEquals(DebridQuery(1396, true, 2, 5), DebridQuery.decode(DebridQuery(1396, true, 2, 5).encode()))
        assertEquals(DebridQuery(157336, false), DebridQuery.decode("m:157336"))
    }
}
