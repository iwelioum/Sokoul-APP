package com.sokoul.iptv

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.repo.ResolvedStream
import com.sokoul.iptv.player.PlayerFactory
import com.sokoul.iptv.player.ProviderGate
import com.sokoul.iptv.player.StreamPlayer
import com.sokoul.iptv.player.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Lecteur réel (ExoPlayer + OkHttp) face à un faux serveur IPTV : vérifie qu'un refus du fournisseur
 * ne déclenche jamais de rafale de requêtes (abonnement limité à une connexion, bannissement temporaire).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StreamPlayerTest {
    private val server = MockWebServer()
    private lateinit var context: Context
    private lateinit var factory: PlayerFactory
    private val scope = TestScope()
    private val gate = ProviderGate().apply { clock = { 1_000_000L + scope.testScheduler.currentTime } }
    private val players = mutableListOf<StreamPlayer>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        factory = PlayerFactory(context, OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build())
    }

    @After fun tearDown() {
        players.forEach { it.release() }
        server.shutdown()
    }

    private fun serve(response: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = response()
        }
        server.start()
    }

    private fun player(): StreamPlayer =
        StreamPlayer(TestExoPlayerBuilder(context).setLooper(Looper.getMainLooper()).build(), factory, scope, gate).also { players += it }

    private fun movie(path: String = "/movie/u/p/1.mkv") = ResolvedStream(listOf(server.url(path).toString()), "Sokoul", null, isLive = false)

    private fun StreamPlayer.awaitState(what: String, cond: (StreamState) -> Boolean) {
        runCatching { RobolectricUtil.runMainLooperUntil { cond(state.value) } }
            .onFailure { throw AssertionError("état attendu : $what, obtenu : ${state.value}", it) }
    }

    /** Laisse passer le temps des coroutines (pause imposée) et traite ce que le lecteur a posté. */
    private fun advance(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `refus 403 une seule requete puis pause de 30 s et un seul nouvel essai`() {
        serve { MockResponse().setResponseCode(403) }
        val p = player()
        p.play(movie(), "Film")
        p.awaitState("Waiting") { it is StreamState.Waiting }
        assertEquals("aucune relance immédiate après un refus", 1, server.requestCount)

        advance(29_000)
        assertEquals("toujours rien pendant la pause", 1, server.requestCount)
        assertTrue(p.state.value is StreamState.Waiting)

        advance(1_500)
        p.awaitState("Failed (refus)") { it is StreamState.Failed }
        assertEquals("un seul nouvel essai automatique", 2, server.requestCount)
        val failed = p.state.value as StreamState.Failed
        assertTrue(failed.refused)
        assertTrue("Réessayer bloqué jusqu'à la fin de la nouvelle pause", failed.retryAtMs > gate.clock())

        // « Réessayer » pendant la pause : aucune requête, attente affichée
        p.retryNow()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(p.state.value is StreamState.Waiting)
        assertEquals(2, server.requestCount)
    }

    @Test fun `refus sur un lecteur aucun autre lecteur ne peut appeler le fournisseur pendant la pause`() {
        serve { MockResponse().setResponseCode(406) }
        val first = player()
        first.play(movie(), "Film")
        first.awaitState("Waiting") { it is StreamState.Waiting }
        // l'utilisateur quitte et relance un autre titre tout de suite
        first.release(); players.remove(first)
        val second = player()
        second.play(movie("/movie/u/p/2.mkv"), "Autre film")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("le nouveau lecteur attend la fin de la pause", second.state.value is StreamState.Waiting)
        assertEquals(1, server.requestCount)
    }

    @Test fun `un nouveau lecteur coupe l'ancien flux une seule connexion`() {
        // serveur qui ne répond jamais : le premier flux reste en chargement (connexion ouverte)
        serve { MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE) }
        val first = player()
        first.play(movie(), "Épisode 1")
        assertTrue(first.state.value is StreamState.Loading)
        val second = player()
        second.play(movie("/movie/u/p/2.mkv"), "Épisode 2")
        assertEquals("l'ancien lecteur est arrêté avant le nouvel appel", StreamState.Idle, first.state.value)
        assertTrue(second.state.value is StreamState.Loading)
    }

    @Test fun `URL sans extension HLS force en repli sans requete en rafale`() {
        serve { MockResponse().setResponseCode(200).setBody("pas une vidéo") }
        val p = player()
        p.play(ResolvedStream(listOf(server.url("/live/u/p/42").toString()), "Sokoul", null, isLive = false), "Flux")
        p.awaitState("échec ou nouvelle tentative") { it is StreamState.Failed || it is StreamState.Reconnecting }
        // un essai direct + un essai HLS forcé au plus, jamais de boucle rapide
        assertTrue("requêtes : ${server.requestCount}", server.requestCount in 1..4)
    }

    @Test fun `erreur serveur 503 nouvelles tentatives espacees puis echec`() {
        serve { MockResponse().setResponseCode(503) }
        val p = player()
        p.play(movie(), "Film")
        p.awaitState("Reconnecting(1)") { it == StreamState.Reconnecting(1) }
        val first = server.requestCount
        advance(400)
        assertEquals("rien avant la fin du délai (500 ms)", first, server.requestCount)
        advance(200)
        p.awaitState("Reconnecting(2)") { it == StreamState.Reconnecting(2) }
        assertTrue(server.requestCount > first)
        // délais croissants (0,5 s, 1 s, 2 s… 15 s max) jusqu'à l'abandon : jamais de boucle infinie
        repeat(12) {
            val n = (p.state.value as? StreamState.Reconnecting)?.attempt ?: return@repeat
            advance(16_000)
            p.awaitState("après la tentative $n") { s -> s is StreamState.Failed || (s is StreamState.Reconnecting && s.attempt > n) }
        }
        assertTrue("abandon propre : ${p.state.value}", p.state.value is StreamState.Failed)
        assertTrue("pas une erreur de refus", !(p.state.value as StreamState.Failed).refused)
    }
}
