package com.sokoul.iptv

import com.sokoul.iptv.data.debrid.DebridSources
import com.sokoul.iptv.data.debrid.RdException
import com.sokoul.iptv.data.debrid.SourceLog
import com.sokoul.iptv.player.ErrorPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Phase 17 : journal des sources masqué, empreinte Comet, raisons lisibles des refus de lien. */
class SourceHardeningTest {
    private val printed = mutableListOf<String>()

    @Before fun setUp() { SourceLog.clear(); SourceLog.logcat = { printed += it } }
    @After fun tearDown() = SourceLog.clear()

    @Test fun `journal - jamais de cle ni d identifiant`() {
        SourceLog.add("Recherche https://torrentio.example/realdebrid=SECRETKEY99/stream/movie/tt1.json")
        SourceLog.add("Lecture http://h.example/movie/USER1/PASS1/42.mkv?token=ABCDEF")
        val all = SourceLog.recent().joinToString { it.text } + printed.joinToString()
        assertFalse(all.contains("SECRETKEY99"))
        assertFalse(all.contains("USER1") || all.contains("PASS1") || all.contains("ABCDEF"))
        assertEquals("aussi écrit dans logcat", 2, printed.size)
    }

    @Test fun `journal - 50 derniers evenements, le plus recent d abord, sur une ligne`() {
        repeat(60) { SourceLog.add("évènement $it\nsuite", now = it.toLong()) }
        val r = SourceLog.recent()
        assertEquals(SourceLog.MAX, r.size)
        assertEquals("évènement 59 suite", r.first().text)
        assertEquals("évènement 10 suite", r.last().text)
    }

    @Test fun `raison lisible d une erreur`() {
        assertEquals("limite atteinte (429)", SourceLog.reason(RdException(429, "too_many_requests")))
        assertTrue(SourceLog.reason(RdException(503, "service_unavailable")).contains("503"))
        assertEquals("pas de réseau", SourceLog.reason(java.io.IOException("pas de réseau")))
    }

    @Test fun `Comet - empreinte lue dans bingeGroup quand le lien est opaque`() {
        val h = "0123456789abcdef0123456789abcdef01234567"
        val text = """{"streams":[{"name":"[RD+] Comet 1080p","title":"Film.2024.MULTI.1080p.mkv","url":"https://comet.example/playback/b64opaque/0",
            "behaviorHints":{"bingeGroup":"comet|${h.uppercase()}","filename":"Film.2024.MULTI.1080p.mkv"}}]}"""
        val s = DebridSources.parse(text).single()
        assertEquals(h, s.infoHash)
        assertEquals("https://comet.example/playback/b64opaque/0", s.url)
    }

    @Test fun `Comet - bingeGroup sans empreinte complete ignore`() {
        val text = """{"streams":[{"name":"Comet","title":"Film.1080p","url":"https://comet.example/p/x","behaviorHints":{"bingeGroup":"comet|1080p|abc123"}}]}"""
        assertEquals(null, DebridSources.parse(text).single().infoHash)
    }

    @Test fun `release sans langue - VO, ou VF pour une oeuvre francaise`() {
        fun s(name: String) = com.sokoul.iptv.data.debrid.DebridStream(name, name, "u", null, null, null, com.sokoul.iptv.data.debrid.ReleaseName.parse(name))
        val list = listOf(s("Heat.1995.1080p.BluRay.x264"), s("Heat.1995.FRENCH.1080p.BluRay"), s("Heat.1995.MULTi.2160p"))
        val en = DebridSources.untagged(list, "en").map { it.release.language }
        assertEquals(com.sokoul.iptv.domain.AudioLanguage.VO, en[0])
        assertEquals("les étiquettes lues restent", list.drop(1).map { it.release.language }, en.drop(1))
        assertEquals(com.sokoul.iptv.domain.AudioLanguage.VF, DebridSources.untagged(list, "fr")[0].release.language)
        assertEquals("langue d'origine inconnue : rien deviné", null, DebridSources.untagged(list, null)[0].release.language)
    }

    @Test fun `sans decodeur Dolby Vision - la HDR10 passe devant, ordre stable par cle`() {
        fun s(name: String) = com.sokoul.iptv.data.debrid.DebridStream(name, name, "u", null, null, null, com.sokoul.iptv.data.debrid.ReleaseName.parse(name))
        val dv = s("Dune.2021.MULTi.2160p.WEB-DL.DV.HEVC")
        val hdr = s("Dune.2021.MULTi.2160p.WEB-DL.HDR10.HEVC")
        fun first(p: com.sokoul.iptv.data.debrid.SourceRanker.Prefs, list: List<com.sokoul.iptv.data.debrid.DebridStream>) =
            (com.sokoul.iptv.data.debrid.SourceRanker.rank(emptyList(), list, p).first() as com.sokoul.iptv.data.debrid.Candidate.Debrid).stream.key
        val p = com.sokoul.iptv.data.debrid.SourceRanker.Prefs()
        assertEquals(dv.key, first(p, listOf(hdr, dv)))
        assertEquals(hdr.key, first(p.copy(dolbyVision = false), listOf(dv, hdr)))
        // deux versions identiques : même gagnante quel que soit l'ordre d'arrivée
        val a = s("Dune.2021.MULTi.1080p.WEB-DL.x264-AAA"); val b = s("Dune.2021.MULTi.1080p.WEB-DL.x264-BBB")
        assertEquals(first(p, listOf(a, b)), first(p, listOf(b, a)))
    }

    @Test fun `release d une autre oeuvre renvoyee par l extension - ecartee`() {
        val lupin = listOf("Lupin")
        assertTrue(DebridSources.otherWork("[Mo7tas] Lupin Zero - S01E01 [BD 1080p] [Boudda - Crunchyroll].mkv", lupin, series = true))
        assertFalse(DebridSources.otherWork("Lupin.2021.S01E01.FRENCH.HDR.DV.2160p.WEB.H265-FW.mkv", lupin, series = true))
        assertFalse(DebridSources.otherWork("Lupin.Part.2.S02E01.MULTi.1080p.WEB.x264", lupin, series = true))
        assertFalse(DebridSources.otherWork("[Team] Lupin - S01E01 (1080p).mkv", lupin, series = true))
        val dune = listOf("Dune", "Dune")
        assertTrue("Dune 2021 : la suite est un autre film", DebridSources.otherWork("Dune.Part.Two.2024.MULTi.2160p.WEB-DL", dune))
        assertFalse(DebridSources.otherWork("Dune.2021.MULTi.2160p.UHD.BluRay.REMUX", dune))
        assertFalse(DebridSources.otherWork("Dune Part Two (2024) 1080p", listOf("Dune : Deuxième partie", "Dune: Part Two")))
        assertFalse("titre anglais d'une série étrangère : jamais écarté", DebridSources.otherWork("Money.Heist.S01E01.1080p", listOf("La casa de papel")))
        assertFalse(DebridSources.otherWork("The.Office.US.S02E01.720p", listOf("The Office"), series = true))
        assertFalse(DebridSources.otherWork("Spider-Man.Brand.New.Day.2026.MULTi.2160p", listOf("Spider-Man : Brand New Day", "Spider-Man: Brand New Day")))
        assertFalse("nom sans repère : rien n'est affirmé", DebridSources.otherWork("Lupin Zero", lupin, series = true))
    }

    @Test fun `expressions regulieres compatibles Android (ICU)`() {
        // Java accepte « []] » et « [^]] » (crochet fermant en tête de classe), Android (ICU) les refuse :
        // l'app plantait à l'ouverture des sources alors que les tests passaient.
        val bad = Regex("""\[\^?]""")
        val offenders = java.io.File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> ("Regex(" in l || "toRegex" in l) && bad.containsMatchIn(l) }.map { "${f.name}:${it.index + 1}" }
        }.toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun `refus d un lien Real-Debrid - message par code`() {
        assertTrue(ErrorPolicy.linkRefusal(403).contains("403"))
        assertTrue(ErrorPolicy.linkRefusal(404).startsWith("Fichier introuvable"))
        assertTrue(ErrorPolicy.linkRefusal(429).startsWith("Limite atteinte"))
        assertTrue(ErrorPolicy.linkRefusal(418).contains("418"))
    }
}
