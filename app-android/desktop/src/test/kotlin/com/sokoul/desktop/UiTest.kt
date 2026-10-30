package com.sokoul.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopSync
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.iptv.ui.theme.SokoulTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Interface de la tablette sur PC, sans réseau ni lecteur : clés absentes (états vides), base temporaire. */
@OptIn(ExperimentalTestApi::class)
class UiTest {
    private lateinit var file: File
    private lateinit var db: DesktopDb
    private val keys = ProviderConfig.builtIn

    @Before fun open() {
        file = File.createTempFile("sokoul-ui", ".db")
        db = DesktopDb(file)
        ProviderConfig.builtIn = { emptyMap() }
    }

    @After fun close() {
        ProviderConfig.builtIn = keys
        db.close()
        file.delete()
    }

    @Test fun `navigation par le rail et retour par Echap`() = runComposeUiTest {
        val app = AppState(db, DesktopSync(db), null)
        setContent { SokoulTheme { AppContent(app) } }
        // sans clé TMDB : l'accueil dit quoi faire au lieu d'un écran vide
        onNodeWithText("Clé TMDB absente").assertExists()
        onAllNodesWithText("Films")[0].performClick()
        waitForIdle()
        assertEquals(Screen.MOVIES, app.screen)
        onAllNodesWithText("Direct")[0].performClick()
        waitForIdle()
        assertEquals(Screen.LIVE, app.screen)
        onNodeWithText("Aucune playlist").assertExists()
        assertTrue(app.back())
        waitForIdle()
        assertEquals(Screen.HOME, app.screen)
    }

    @Test fun `fiche poussee puis retour, ma liste et reglages`() = runComposeUiTest {
        val app = AppState(db, DesktopSync(db), null)
        setContent { SokoulTheme { AppContent(app) } }
        app.go(Screen.MOVIES)
        app.openDetail("movie", 27205)
        waitForIdle()
        onNodeWithContentDescription("Retour").assertExists()
        assertTrue(app.back())
        waitForIdle()
        assertEquals(null, app.route)
        assertEquals(Screen.MOVIES, app.screen)
        app.toggleMyList("movie", 27205)
        assertTrue(app.inMyList("movie", 27205))
        app.toggleMyList("movie", 27205)
        assertTrue(!app.inMyList("movie", 27205))
        app.go(Screen.SETTINGS)
        waitForIdle()
        onNodeWithText("Sources de données").performClick()
        waitForIdle()
        onNodeWithText("TMDB").assertExists()
        // un code existant ne se change qu'après l'avoir saisi
        db.putSetting(com.sokoul.desktop.data.Settings.PIN, com.sokoul.iptv.data.PinHasher.hash("1234"))
        app.dataVersion++
        onNodeWithText("Contrôle parental").performClick()
        waitForIdle()
        onNodeWithText("Modifier le code parental").performClick()
        waitForIdle()
        onNodeWithText("Code actuel").assertExists()
    }
}
