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
import com.sokoul.desktop.ui.SokoulTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Interface sans réseau ni lecteur : clés absentes (états vides), base temporaire. */
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

    @Test fun `navigation par la barre du haut et retour par Echap`() = runComposeUiTest {
        val app = AppState(db, DesktopSync(db), null)
        setContent { SokoulTheme { AppContent(app) } }
        // sans clé TMDB : l'accueil dit quoi faire au lieu d'un écran vide
        onNodeWithText("Branchez TMDB").assertExists()
        onNodeWithText("Films").performClick()
        waitForIdle()
        assertEquals(Screen.MOVIES, app.screen)
        onAllNodesWithText("Films")[0].assertExists()
        onNodeWithText("Direct").performClick()
        waitForIdle()
        onNodeWithText("Ajouter une playlist").assertExists()
        assertTrue(app.back())
        waitForIdle()
        assertEquals(Screen.HOME, app.screen)
    }

    @Test fun `fiche poussee puis retour, recherche et reglages`() = runComposeUiTest {
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
        onNodeWithContentDescription("Réglages").performClick()
        waitForIdle()
        onNodeWithText("Clés API").performClick()
        waitForIdle()
        onNodeWithText("TMDB").assertExists()
        onAllNodesWithText("Non configuré", substring = true)[0].assertExists()
        onNodeWithContentDescription("Recherche (/)").performClick()
        waitForIdle()
        assertEquals(Screen.SEARCH, app.screen)
        onNodeWithText("Un film, une série, une chaîne…").assertExists()
    }
}
