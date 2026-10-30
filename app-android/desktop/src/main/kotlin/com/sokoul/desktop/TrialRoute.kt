package com.sokoul.desktop

import kotlinx.coroutines.delay

/**
 * Écran imposé au lancement par `SOKOUL_ROUTE` (essais réels sans clavier ni souris, captures par PrintWindow) :
 * `home`, `movies`, `series`, `live`, `guide`, `settings`, `search:dune`, `detail:movie:27205`, `detail:tv:1396`,
 * `sources:movie:27205`, `play:movie:27205`, `play:tv:1396:1:1`.
 */
object TrialRoute {
    /** Texte de recherche imposé (lu par l'écran Recherche au premier affichage). */
    @Volatile var searchQuery: String? = null
    /** Fiche défilée jusqu'à cette section (`detail:tv:1396:1` → saisons). */
    @Volatile var scrollItem: Int? = null
    /** Rubrique des réglages ouverte (`settings:debrid`). */
    @Volatile var settingsPane: String? = null

    suspend fun apply(app: AppState, route: String) {
        val p = route.split(':')
        // essai : jamais de son (l'utilisateur peut être en train de regarder autre chose)
        app.player?.let { if (!it.status.value.muted) it.toggleMute() }
        when (p[0].lowercase()) {
            // import de la sauvegarde de la tablette (fichier et phrase par variables d'environnement, jamais en argument)
            "import" -> {
                val file = java.io.File(System.getenv("SOKOUL_IMPORT_FILE") ?: return)
                val pass = System.getenv("SOKOUL_IMPORT_PASS")
                val s = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.sokoul.desktop.data.DesktopImport(app.db).apply(com.sokoul.desktop.data.BackupReader.read(file.readText(), pass))
                }
                System.err.println("Import : ${s.playlists} playlist(s), ${s.favorites} favori(s), ${s.progress} reprise(s)")
                app.reloadPlaylist()
                app.go(Screen.SETTINGS)
                app.playlist?.let { pl -> app.sync.sync(pl); app.reloadPlaylist() }
                System.err.println("Synchronisation : ${app.sync.state.value}")
            }
            "sync" -> {
                app.go(Screen.SETTINGS)
                app.playlist?.let { pl -> app.sync.sync(pl); app.reloadPlaylist() }
                System.err.println("Synchronisation : ${app.sync.state.value}")
            }
            "home" -> { scrollItem = p.getOrNull(1)?.toIntOrNull(); app.go(Screen.HOME) }
            "movies" -> { scrollItem = p.getOrNull(1)?.toIntOrNull(); app.go(Screen.MOVIES) }
            "series" -> { scrollItem = p.getOrNull(1)?.toIntOrNull(); app.go(Screen.SERIES) }
            "mylist" -> app.go(Screen.MY_LIST)
            "sagas" -> app.go(Screen.SAGAS)
            "saga" -> app.openSaga(p[1].toInt())
            "live" -> {
                app.go(Screen.LIVE)
                // live:play:<n> : lit la chaîne n° n (une seule connexion : la tablette ne doit rien lire)
                if (p.getOrNull(1) == "play") app.playlist?.let { pl ->
                    val all = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.db.allChannels(pl.id) }
                    val i = all.indexOfFirst { it.number == (p.getOrNull(2)?.toIntOrNull() ?: 1) }.coerceAtLeast(0)
                    app.play(all, i)
                }
            }
            "guide" -> app.go(Screen.GUIDE)
            // settings:debrid → rubrique ouverte (nom de l'enum SettingsCategory)
            "settings" -> { settingsPane = p.getOrNull(1)?.uppercase(); app.go(Screen.SETTINGS) }
            "search" -> { searchQuery = p.getOrNull(1); app.go(Screen.SEARCH) }
            "detail" -> { scrollItem = p.getOrNull(3)?.toIntOrNull(); app.openDetail(p[1], p[2].toInt()) }
            "sources" -> {
                app.openDetail(p[1], p[2].toInt())
                delay(1500)
                val info = app.catalog.details(p[1], p[2].toInt())
                app.sources = NowPlaying.Title(p[1], p[2].toInt(), info?.title ?: "", info?.year?.toString(), info?.backdrop,
                    p.getOrNull(3)?.toInt(), p.getOrNull(4)?.toInt())
            }
            "play" -> {
                val info = app.catalog.details(p[1], p[2].toInt())
                app.playTitle(NowPlaying.Title(p[1], p[2].toInt(), info?.title ?: "", info?.year?.toString(), info?.backdrop,
                    p.getOrNull(3)?.toInt(), p.getOrNull(4)?.toInt()))
            }
        }
    }
}
