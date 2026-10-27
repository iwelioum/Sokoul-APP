package com.sokoul.iptv

import com.sokoul.iptv.data.debrid.Candidate
import com.sokoul.iptv.data.debrid.DebridAddons
import com.sokoul.iptv.data.debrid.DebridSources
import com.sokoul.iptv.data.debrid.SourceAddon
import com.sokoul.iptv.data.debrid.SourceRanker
import com.sokoul.iptv.data.net.HttpModule
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.ui.screens.player.PlayerSource
import com.sokoul.iptv.ui.screens.player.SourceCatalog
import com.sokoul.iptv.ui.screens.player.VersionLine
import com.sokoul.iptv.ui.screens.player.versionLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recherche Real-Debrid (Torrentio + Comet), fusion des résultats, jeton jamais journalisé, Real-Debrid d'abord, affichage rangé. */
class SourceSearchTest {
    private val token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFGHIJKLMNOP"

    @Test fun `adresses des extensions - jeton ajoute a la demande, base64 Comet sans slash ni plus`() {
        val urls = DebridAddons.urls(setOf(SourceAddon.COMET, SourceAddon.TORRENTIO), "https://x.example/abc/manifest.json, ftp://non", token)
        assertEquals(3, urls.size)
        assertTrue(urls[0].startsWith("https://torrentio.strem.fun/language=french%7Crealdebrid="))
        assertTrue(urls[1].startsWith("https://comet.elfhosted.com/"))
        assertEquals("https://x.example/abc", urls[2])
        // sans jeton : seulement les extensions ajoutées à la main
        assertEquals(listOf("https://x.example/abc"), DebridAddons.urls(SourceAddon.entries.toSet(), "https://x.example/abc", null))
        // la configuration Comet reste un JSON valide quel que soit le jeton
        listOf(token, "a", "zz/++//", "Q".repeat(52)).forEach { t ->
            val b64 = DebridAddons.cometConfig(t)
            assertFalse(b64, '/' in b64 || '+' in b64)
            val json = String(java.util.Base64.getDecoder().decode(b64))
            assertTrue(json.contains("\"debridService\":\"realdebrid\""))
            assertTrue(json.trimEnd().endsWith("}"))
        }
        assertEquals(setOf(SourceAddon.TORRENTIO, SourceAddon.COMET), DebridAddons.parse(" torrentio,COMET,inconnu"))
        assertTrue(DebridAddons.parse("").isEmpty())
    }

    @Test fun `jeton Real-Debrid masque dans les journaux`() {
        DebridAddons.urls(SourceAddon.entries.toSet(), "", token).forEach { u ->
            val log = HttpModule.redact("--> GET $u/stream/movie/tt1234567.json")
            assertFalse(log, token in log)
            assertFalse(log, DebridAddons.cometConfig(token) in log)
        }
        assertFalse(HttpModule.redact("https://torrentio.strem.fun/resolve/realdebrid/$token/abc/null/0/x.mkv").contains(token))
    }

    private fun json(vararg items: String) = """{"streams":[${items.joinToString(",")}]}"""
    private fun item(title: String, hash: String, idx: Int? = null) =
        """{"name":"[RD+] x","title":"$title","infoHash":"$hash"${idx?.let { ",\"fileIdx\":$it" } ?: ""}}"""

    @Test fun `resultats de plusieurs extensions fusionnes sans doublon`() {
        val h1 = "a".repeat(40); val h2 = "b".repeat(40); val h3 = "c".repeat(40)
        val torrentio = DebridSources.parse(json(item("Film.2024.FRENCH.1080p.WEB.x264\n💾 4 GB", h1), item("Film.2024.MULTi.2160p.WEB.DV\n💾 20 GB", h2)))
        // Comet renvoie la même release (même empreinte) et une copie du même fichier sous une autre empreinte
        val comet = DebridSources.parse(json(item("Film.2024.FRENCH.1080p.WEB.x264", h1), item("Film.2024.MULTi.2160p.WEB.DV\n💾 20 GB", h3), item("Film.2024.VOSTFR.720p.WEB", "d".repeat(40))))
        val all = DebridSources.combine(listOf(torrentio, comet))
        assertEquals(listOf("$h1:-1", "$h2:-1", "${"d".repeat(40)}:-1"), all.map { it.key })
    }

    @Test fun `Real-Debrid d abord a langue et image egales, une meilleure image l emporte toujours`() {
        val rd = DebridSources.parse(json(item("Film.2024.FRENCH.1080p.WEB.x264", "e".repeat(40)))).single()
        val iptv1080 = VodVersion("101", "Film FHD", "VF", "FHD", "c")
        val iptv4k = VodVersion("102", "Film 4K", "VF", "4K", "c")
        val rdFirst = SourceRanker.Prefs(tieOrigin = SourceOrigin.DEBRID)
        assertTrue(SourceRanker.rank(listOf(iptv1080), listOf(rd), rdFirst).first() is Candidate.Debrid)
        assertEquals("102", SourceRanker.id(SourceRanker.rank(listOf(iptv4k), listOf(rd), rdFirst).first()))
    }

    @Test fun `MULTI contient le francais - une MULTI 4K passe devant une VF 1080p, la VF gagne a image egale`() {
        val vf1080 = DebridSources.parse(json(item("Film.2024.FRENCH.1080p.WEBRip.x264", "1".repeat(40)))).single()
        val multi4k = DebridSources.parse(json(item("Film.2024.MULTi.2160p.WEB.DV.HEVC", "2".repeat(40)))).single()
        val multi1080 = DebridSources.parse(json(item("Film.2024.MULTi.1080p.WEB.x264", "3".repeat(40)))).single()
        val vostfr4k = DebridSources.parse(json(item("Film.2024.VOSTFR.2160p.WEB.HEVC", "4".repeat(40)))).single()
        val r = SourceRanker.rank(emptyList(), listOf(vf1080, multi1080, vostfr4k, multi4k), SourceRanker.Prefs())
        assertEquals(listOf(multi4k.key, vf1080.key, multi1080.key, vostfr4k.key), r.map { SourceRanker.id(it) })
    }

    @Test fun `MULTI sans francais annonce - range avec les autres langues`() {
        assertEquals(null, com.sokoul.iptv.data.debrid.ReleaseName.parse("Film.2024.MULTi.1080p.WEB.x264", "🇬🇧 / 🇮🇳").language)
        assertEquals(AudioLanguage.MULTI, com.sokoul.iptv.data.debrid.ReleaseName.parse("Film.2024.MULTi.1080p.WEB.x264", "🇫🇷 / 🇬🇧").language)
        assertEquals(AudioLanguage.MULTI, com.sokoul.iptv.data.debrid.ReleaseName.parse("Film.2024.MULTi.1080p.WEB.x264").language)
    }

    private fun src(id: String, lang: AudioLanguage?, q: VideoQuality?, hdr: String? = null, region: String? = null) =
        PlayerSource(id, SourceOrigin.DEBRID, lang, region = region, quality = q, hdr = hdr, where = "x")

    @Test fun `affichage range - HDR sous la definition, langues rares dans Autres langues`() {
        val langs = listOf("VF", "MULTI", "VOSTFR", "VO")
        val list = listOf(
            src("dv", AudioLanguage.VF, VideoQuality.UHD_4K, "Dolby Vision"),
            src("sdr", AudioLanguage.VF, VideoQuality.UHD_4K),
            src("vf1080", AudioLanguage.VF, VideoQuality.FHD_1080),
            src("it", null, VideoQuality.FHD_1080, region = "IT"),
            src("de", null, VideoQuality.UHD_4K, region = "DE"),
            src("unk", null, VideoQuality.FHD_1080),
        )
        val g = SourceCatalog.group(list, langs)
        // une seule version VF 4K (Dolby Vision en tête, la 4K SDR en autre source)
        assertEquals("Dolby Vision", g[0].hdr)
        assertEquals(listOf("dv", "sdr"), g[0].sources.map { it.id })
        assertEquals(listOf(true, true, false, false, false), g.map { SourceCatalog.mainLanguage(it, langs) })
        val lines = versionLines(emptyList(), g, emptySet(), live = false, languages = langs)
        assertEquals(listOf("h-VF", "h-OTHER"), lines.filterIsInstance<VersionLine.Header>().map { it.key })
        assertEquals(listOf(true, true, false, false, false), lines.filterIsInstance<VersionLine.Version>().map { it.main })
    }
}
