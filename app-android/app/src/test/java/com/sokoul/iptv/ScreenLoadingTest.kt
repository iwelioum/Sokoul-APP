package com.sokoul.iptv

import com.sokoul.iptv.player.ScreenLoading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** L'indexation attend la fin du chargement d'un écran, même si celui-ci échoue. */
class ScreenLoadingTest {
    @Test fun `attente pendant un chargement, reprise ensuite`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val load = launch { ScreenLoading.track { release.await() } }
        yield()
        val waiter = async { ScreenLoading.awaitIdle(); true }
        repeat(5) { yield() }
        assertFalse(waiter.isCompleted)
        release.complete(Unit)
        assertTrue(waiter.await())
        load.join()
    }

    @Test fun `un chargement en erreur libere l'indexation`() = runBlocking {
        runCatching { ScreenLoading.track<Unit> { error("TMDB") } }
        ScreenLoading.awaitIdle() // ne bloque pas
    }
}
