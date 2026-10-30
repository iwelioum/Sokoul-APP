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

    suspend fun apply(app: AppState, route: String) {
        val p = route.split(':')
        // essai : jamais de son (l'utilisateur peut être en train de regarder autre chose)
        app.player?.let { if (!it.status.value.muted) it.toggleMute() }
        when (p[0].lowercase()) {
            "home" -> app.go(Screen.HOME)
            "movies" -> app.go(Screen.MOVIES)
            "series" -> app.go(Screen.SERIES)
            "live" -> app.go(Screen.LIVE)
            "guide" -> app.go(Screen.GUIDE)
            "settings" -> app.go(Screen.SETTINGS)
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
