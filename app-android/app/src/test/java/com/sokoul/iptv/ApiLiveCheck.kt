package com.sokoul.iptv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.net.HttpModule
import com.sokoul.iptv.data.net.providers.ApiHttp
import com.sokoul.iptv.data.net.providers.ApiKeys
import com.sokoul.iptv.data.net.providers.FanartClient
import com.sokoul.iptv.data.net.providers.ProviderHealth
import com.sokoul.iptv.data.net.providers.StreamingClient
import com.sokoul.iptv.data.net.providers.TasteDiveClient
import com.sokoul.iptv.data.net.providers.TvdbClient
import com.sokoul.iptv.data.net.providers.TvmazeClient
import com.sokoul.iptv.data.net.providers.OmdbClient
import com.sokoul.iptv.data.net.providers.SubtitleQuery
import com.sokoul.iptv.data.net.providers.SubtitlesClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/**
 * Vrais appels aux services de données, avec les clés lues dans l'environnement
 * (SOKOUL_LIVE_API=1 et SOKOUL_KEY_TVDB, _FANART, _MOTN, _TASTEDIVE). Ignoré sinon : jamais en intégration continue,
 * aucune clé dans le dépôt. Une requête par service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ApiLiveCheck {

    @Test fun `vrais appels aux services de donnees`() = runBlocking {
        assumeTrue(System.getenv("SOKOUL_LIVE_API") == "1")
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsStore(context)
        val env = mapOf("tvdb" to "SOKOUL_KEY_TVDB", "fanart" to "SOKOUL_KEY_FANART", "motn" to "SOKOUL_KEY_MOTN", "tastedive" to "SOKOUL_KEY_TASTEDIVE", "omdb" to "SOKOUL_KEY_OMDB", "opensubtitles" to "SOKOUL_KEY_OPENSUBTITLES", "subdl" to "SOKOUL_KEY_SUBDL")
            .mapNotNull { (id, v) -> System.getenv(v)?.takeIf { it.isNotBlank() }?.let { id to it } }.toMap()
        settings.update { it.copy(apiKeys = env) }
        val api = ApiHttp(HttpModule.okHttp(), HttpModule.json(), db.tmdb(), ProviderHealth())
        val keys = ApiKeys(settings)
        val report = StringBuilder()
        fun line(s: String) { report.appendLine(s); println(s) }

        if ("fanart" in env) FanartClient(api, keys).movie(157336).also { line("fanart.tv : logo=${it?.logo != null} fond=${it?.background != null}"); assertTrue(it?.logo != null) }
        if ("tvdb" in env) TvdbClient(api, keys).episodes(81189).also { e -> line("TheTVDB : ${e.size} épisodes, S1E1 = ${e.firstOrNull { it.season == 1 && it.episode == 1 }?.name}"); assertTrue(e.size > 50) }
        TvmazeClient(api).nextEpisode(81189, null).also { line("TVmaze : série terminée → prochain épisode = $it") }
        if ("motn" in env) StreamingClient(api, keys).offers(true, 157336).also { o -> line("Movie of the Night : ${o.joinToString { "${it.service} (${it.type.label})" }}"); assertTrue(o.isNotEmpty()) }
        if ("tastedive" in env) TasteDiveClient(api, keys).similar("Interstellar", true).also { s -> line("TasteDive : ${s.take(5)}"); assertTrue(s.isNotEmpty()) }
        if ("omdb" in env) OmdbClient(api, keys).ratings("tt0816692").also { line("OMDb : $it"); assertTrue(it?.imdb != null) }
        val subs = SubtitlesClient(api, keys)
        val q = SubtitleQuery(157336, isMovie = true)
        if ("opensubtitles" in env) subs.searchOpenSubtitles(q).also { r ->
            line("OpenSubtitles : ${r.size} résultats, premier = ${r.firstOrNull()?.release}")
            assertTrue(r.isNotEmpty())
            val cues = subs.download(r.first())
            line("OpenSubtitles téléchargement : ${cues.size} répliques, 1re = ${cues.firstOrNull()?.text?.take(40)}")
            assertTrue(cues.isNotEmpty())
        }
        if ("subdl" in env) subs.searchSubdl(q).also { r ->
            line("Subdl : ${r.size} résultats, premier = ${r.firstOrNull()?.release}")
            assertTrue(r.isNotEmpty())
            val cues = subs.download(r.first())
            line("Subdl téléchargement : ${cues.size} répliques, 1re = ${cues.firstOrNull()?.text?.take(40)}")
            assertTrue(cues.isNotEmpty())
        }
        val ep = subs.search(SubtitleQuery(1396, isMovie = false, season = 1, episode = 1))
        if ("opensubtitles" in env || "subdl" in env) { line("Épisode Breaking Bad S1E1 : ${ep.size} résultats"); assertTrue(ep.isNotEmpty()) }
        db.close()
    }
}
