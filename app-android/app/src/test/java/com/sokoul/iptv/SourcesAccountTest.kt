package com.sokoul.iptv

import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.VodProbeEntity
import com.sokoul.iptv.data.debrid.Candidate
import com.sokoul.iptv.data.debrid.DebridCloud
import com.sokoul.iptv.data.debrid.DebridSources
import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.data.debrid.SourceRanker
import com.sokoul.iptv.data.repo.VodQuality
import com.sokoul.iptv.data.repo.VodVersion
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.SpokenLanguages
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.ui.screens.player.PlayerSource
import com.sokoul.iptv.ui.screens.player.PlayerSources
import com.sokoul.iptv.ui.screens.player.PlayerViewModel
import com.sokoul.iptv.ui.screens.player.SourceCatalog
import com.sokoul.iptv.ui.screens.player.SourceSearch
import com.sokoul.iptv.ui.screens.player.sourceCountLine
import com.sokoul.iptv.ui.screens.player.sourceDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Langues réellement entendues, fichiers du compte Real-Debrid comme sources, versions en préparation, échecs gardés. */
class SourcesAccountTest {

    // --- langues entendues ------------------------------------------------------------------

    @Test fun `codes de langue des pistes - ISO 639-1 et 639-2, pistes sans langue reperees`() {
        assertEquals("FR", SpokenLanguages.code("fre"))
        assertEquals("FR", SpokenLanguages.code("fra"))
        assertEquals("EN", SpokenLanguages.code("eng"))
        assertEquals("DE", SpokenLanguages.code("ger"))
        assertEquals("PT", SpokenLanguages.code("pt-BR"))
        assertNull(SpokenLanguages.code("und"))
        assertNull(SpokenLanguages.code(null))
        assertEquals("FR,EN", SpokenLanguages.store(listOf("fre", "eng", "fre")))
        assertEquals("FR,?", SpokenLanguages.store(listOf("fr", null)))
        assertNull(SpokenLanguages.store(emptyList()))
        assertEquals(listOf("FR", "EN"), SpokenLanguages.parse("FR,EN,?"))
        assertTrue(SpokenLanguages.complete("FR,EN", fromPlayer = true))
        assertFalse(SpokenLanguages.complete("FR,?", fromPlayer = true))
        assertFalse("panel : première piste seulement", SpokenLanguages.complete("FR", fromPlayer = false))
    }

    @Test fun `langue entendue - corrige le nom quand la mesure est complete, le complete sinon`() {
        val heardEn = VodQuality(width = 1920, height = 1080, audioLanguage = "EN", fromPlayer = true)
        // « VF » dans le nom, mais le lecteur n'a entendu que de l'anglais
        val lie = VodVersion("1", "FR - Dune (2021)", "VF", "FHD", "c", measured = heardEn)
        assertNull(lie.spokenLanguage)
        val s = PlayerSources.fromIptv(lie, current = false)
        assertEquals("EN", s.region)
        assertEquals("Anglais (EN)", PlayerSources.languageName(s.language, s.region))
        assertEquals("langue entendue : Anglais", PlayerSources.audioLine(s))
        // fichier muet dans le nom, deux pistes entendues : MULTI
        val multi = VodVersion("2", "Dune (2021)", null, "FHD", "c", measured = heardEn.copy(audioLanguage = "FR,EN"))
        assertEquals("MULTI", multi.spokenLanguage)
        assertEquals("langues entendues : Français, Anglais", PlayerSources.audioLine(PlayerSources.fromIptv(multi, current = false)))
        // panel (première piste seulement) : ne contredit jamais le nom, complète un nom muet
        assertEquals("VF", lie.copy(measured = heardEn.copy(fromPlayer = false)).spokenLanguage)
        assertEquals("VF", VodVersion("3", "Dune", null, null, "c", measured = VodQuality(audioLanguage = "fre")).spokenLanguage)
        // piste sans étiquette : mesure partielle, le nom reste
        assertEquals("VF", lie.copy(measured = heardEn.copy(audioLanguage = "EN,?")).spokenLanguage)
        // VOSTFR sans piste française : reste VOSTFR
        assertEquals("VOSTFR", VodVersion("4", "Dune", "VOSTFR", null, "c", measured = heardEn).spokenLanguage)
        // le classement suit la langue entendue : la fausse VF passe après une vraie VF
        val real = VodVersion("5", "Dune", "VF", "HD", "c")
        assertEquals(listOf("5", "1"), com.sokoul.iptv.data.repo.VersionPicker.order(listOf(lie, real)).map { it.id })
    }

    @Test fun `Real-Debrid - langue entendue lors d une lecture precedente`() {
        val s = PlayerSources.fromDebrid(stream("Dune.2021.FRENCH.1080p.WEB", "k"), current = false, id = "rd:k")
        val heard = PlayerSources.withHeard(s, "FR,EN", fromPlayer = true)
        assertEquals(AudioLanguage.MULTI, heard.language)
        assertTrue(heard.audioChecked)
        // le classement en tient compte aussi
        val prefs = SourceRanker.Prefs(debridMeasured = mapOf("k" to VodQuality(width = 1920, height = 1080, audioLanguage = "EN", fromPlayer = true)))
        val c = SourceRanker.rank(emptyList(), listOf(stream("Dune.2021.FRENCH.1080p.WEB", "k")), prefs).single()
        assertNull(c.source.language)
        assertEquals("EN", c.source.region)
    }

    // --- fichiers du compte -----------------------------------------------------------------

    @Test fun `fichier du compte reconnu - titre francais ou original, annee, saison et episode`() {
        val titles = listOf("Le Comte de Monte-Cristo", "The Count of Monte Cristo")
        assertTrue(DebridCloud.matches("Le.Comte.de.Monte-Cristo.2024.FRENCH.2160p.WEB.mkv", titles, 2024, null, null))
        assertTrue(DebridCloud.matches("The Count of Monte Cristo (2024) 1080p", titles, 2024, null, null))
        assertFalse("autre film du même nom", DebridCloud.matches("The.Count.of.Monte.Cristo.2002.1080p", titles, 2024, null, null))
        assertFalse("épisode, pas un film", DebridCloud.matches("Le.Comte.de.Monte-Cristo.S01E02.1080p", titles, 2024, null, null))
        assertTrue("accents", DebridCloud.matches("Amelie.2001.MULTi.1080p", listOf("Le Fabuleux Destin d'Amélie Poulain", "Amélie"), 2001, null, null))
        // série : bon épisode, ou pack de la saison
        val show = listOf("Breaking Bad")
        assertTrue(DebridCloud.matches("Breaking.Bad.S02E05.1080p.mkv", show, 2008, 2, 5))
        assertFalse(DebridCloud.matches("Breaking.Bad.S02E06.1080p.mkv", show, 2008, 2, 5))
        assertTrue(DebridCloud.matches("Breaking.Bad.S02.1080p.BluRay", show, 2008, 2, 5))
        assertFalse(DebridCloud.matches("Breaking.Bad.S03.1080p.BluRay", show, 2008, 2, 5))
        assertFalse("film, pas la série", DebridCloud.matches("Breaking.Bad.2008.1080p", show, 2008, 2, 5))
    }

    @Test fun `extension et compte fusionnes - une version deja dans le compte n apparait qu une fois`() {
        val hash = "a".repeat(40)
        val addon = listOf(stream("Dune.2021.MULTi.2160p", "$hash:-1", hash = hash, cached = false), stream("Dune.2021.FRENCH.1080p", "b:-1", hash = "b".repeat(40)))
        val account = listOf(
            stream("Dune.2021.MULTi.2160p", "rdc:T1", hash = hash).copy(inAccount = true),
            stream("Dune.2021.VOSTFR.720p", "rdc:T2", hash = "c".repeat(40)).copy(preparing = 40),
        )
        val merged = DebridSources.merge(addon, account)
        assertEquals(listOf("$hash:-1", "b:-1", "rdc:T2"), merged.map { it.key })
        assertTrue(merged[0].inAccount)
        assertEquals(true, merged[0].readyNow)
        assertEquals(false, merged[2].readyNow)
        // affichage
        assertTrue(sourceDetail(PlayerSources.fromDebrid(merged[0], current = false, id = "rd:x")).contains("dans votre compte"))
        assertTrue(sourceDetail(PlayerSources.fromDebrid(merged[2], current = false, id = "rd:y")).contains("en préparation · 40 %"))
    }

    @Test fun `classement - un fichier du compte passe devant une version a telecharger de meme qualite`() {
        val dl = stream("Dune.2021.FRENCH.1080p.WEB", "dl", cached = false)
        val mine = stream("Dune.2021.FRENCH.1080p.WEB", "mine", cached = null).copy(inAccount = true)
        val r = SourceRanker.rank(emptyList(), listOf(dl, mine), SourceRanker.Prefs())
        assertEquals("mine", (r.first() as Candidate.Debrid).stream.key)
    }

    @Test fun `secours automatique - jamais une version a telecharger ou en preparation`() {
        val langs = listOf("VF", "MULTI", "VOSTFR", "VO")
        val list = listOf(
            src("m1", AudioLanguage.VF, VideoQuality.FHD_1080, current = true),
            src("rd:dl", AudioLanguage.VF, VideoQuality.FHD_1080, SourceOrigin.DEBRID).copy(ready = false),
            src("rd:ok", AudioLanguage.VF, VideoQuality.FHD_1080, SourceOrigin.DEBRID).copy(ready = true),
        )
        assertEquals(listOf("rd:ok"), SourceCatalog.fallbackOrder(list, "m1", langs))
    }

    @Test fun `etat de la recherche - compte seulement, non configure`() {
        val one = listOf(src("m1", AudioLanguage.VF, VideoQuality.FHD_1080, current = true))
        assertEquals("1 source trouvée : IPTV 1 · RD 0 · Real-Debrid : vos fichiers seulement (recherche de sources non activée)", sourceCountLine(one, SourceSearch.ACCOUNT))
        assertEquals("1 source trouvée : IPTV 1 · RD 0 · Real-Debrid : non configuré", sourceCountLine(one, SourceSearch.OFF))
    }

    @Test fun `echecs IPTV gardes 24 h, effaces quand on choisit a nouveau la source`() {
        val now = 1_000_000_000L
        val rows = listOf(
            VodProbeEntity(1, ContentType.MOVIE, PlayerViewModel.IPTV_FAIL_PREFIX + "m1", fromPlayer = true, fetchedAt = now - 3_600_000),
            VodProbeEntity(1, ContentType.MOVIE, PlayerViewModel.IPTV_FAIL_PREFIX + "m2", fromPlayer = true, fetchedAt = 0),
            VodProbeEntity(1, ContentType.MOVIE, PlayerViewModel.IPTV_FAIL_PREFIX + "m3", fromPlayer = true, fetchedAt = now - 25 * 3_600_000L),
        )
        assertEquals(setOf("m1"), PlayerViewModel.readFailures(rows, PlayerViewModel.IPTV_FAIL_PREFIX, now))
    }

    private fun src(id: String, lang: AudioLanguage?, q: VideoQuality?, origin: SourceOrigin = SourceOrigin.IPTV, current: Boolean = false) =
        PlayerSource(id, origin, lang, quality = q, where = "x", current = current)

    private fun stream(name: String, key: String, hash: String? = null, cached: Boolean? = true) = DebridStream(
        key = key, title = name, url = null, infoHash = hash, fileIdx = null, filename = name,
        release = ReleaseName.parse(name).copy(cached = cached),
    )
}
