package com.sokoul.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopDebrid
import com.sokoul.desktop.data.DesktopSync
import com.sokoul.desktop.data.Movie
import com.sokoul.desktop.data.Playlist
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.data.PinHasher
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.data.debrid.DebridStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Onglets de la barre du haut. */
enum class Screen(val label: String) {
    HOME("Accueil"), MOVIES("Films"), SERIES("Séries"), MY_LIST("Ma liste"), SAGAS("Sagas"), LIVE("Direct"), GUIDE("Guide TV"),
    SEARCH("Recherche"), SETTINGS("Réglages"),
}

/** Écrans poussés au-dessus d'un onglet (Échap revient). */
sealed interface Route {
    data class Detail(val kind: String, val id: Int) : Route
    data class Saga(val id: Int) : Route
    data class Platform(val platform: com.sokoul.iptv.data.repo.Platform, val kind: String) : Route
}

/** Ce que montre le lecteur : une chaîne (liste de zapping) ou une œuvre TMDB (film, épisode). */
sealed interface NowPlaying {
    data class Live(val list: List<Channel>, val index: Int) : NowPlaying
    data class Title(
        val kind: String,
        val tmdbId: Int,
        val title: String,
        val subtitle: String?,
        val image: String?,
        val season: Int? = null,
        val episode: Int? = null,
        /** Version en cours (Real-Debrid) ; null = lecture IPTV ou recherche en cours. */
        val stream: DebridStream? = null,
        /** Étape affichée tant que l'image n'est pas là (« Recherche de la meilleure version… »). */
        val step: String? = null,
        val error: String? = null,
    ) : NowPlaying {
        val query get() = DebridQuery(tmdbId, kind == "tv", season, episode)
        val resumeKey get() = Resume.key(kind, tmdbId, season, episode)
    }
    data class Iptv(val movie: Movie) : NowPlaying
}

/** État global de l'app PC : base, catalogue, Real-Debrid, lecteur (unique), navigation et playlist active. */
class AppState(val db: DesktopDb, val sync: DesktopSync, val player: DesktopPlayer?) {
    val catalog = Catalog(db)
    val debrid = DesktopDebrid(db, catalog)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var screen by mutableStateOf(Screen.HOME)
    val stack = mutableStateListOf<Route>()
    var playlist by mutableStateOf<Playlist?>(null)
        private set
    var playing by mutableStateOf<NowPlaying?>(null)
    var fullscreen by mutableStateOf(false)
    /** Barre du haut opaque (page défilée). */
    var navSolid by mutableStateOf(false)
    /** Code parental saisi pendant cette session (groupes verrouillés accessibles jusqu'à la fermeture). */
    var unlocked by mutableStateOf(false)
    /** Incrémenté après une synchronisation, un import ou une clé changée : les écrans rechargent. */
    var dataVersion by mutableStateOf(0)
    /** Fenêtre Sources ouverte pour cette œuvre. */
    var sources by mutableStateOf<NowPlaying.Title?>(null)
    private var playJob: Job? = null

    init { reloadPlaylist() }

    val route: Route? get() = stack.lastOrNull()

    fun go(s: Screen) { stack.clear(); screen = s; navSolid = false }
    fun open(r: Route) { if (stack.lastOrNull() != r) stack.add(r) }
    fun openDetail(kind: String, id: Int) = open(Route.Detail(kind, id))
    fun openSaga(id: Int) = open(Route.Saga(id))
    fun openPlatform(p: com.sokoul.iptv.data.repo.Platform, kind: String) = open(Route.Platform(p, kind))

    // --- Ma liste (films et séries TMDB), comme la tablette ---------------------------------------------

    private fun listType(kind: String) = if (kind == "tv") "MYLIST_TV" else "MYLIST_MOVIE"
    fun inMyList(kind: String, id: Int) = id.toString() in db.favoriteIds(0, listType(kind))
    fun toggleMyList(kind: String, id: Int) {
        if (inMyList(kind, id)) db.removeFavorite(0, listType(kind), id.toString())
        else db.addFavorite(0, listType(kind), id.toString(), System.currentTimeMillis())
        dataVersion++
    }
    fun myList(): List<Pair<String, Int>> =
        db.favoriteIds(0, "MYLIST_MOVIE").mapNotNull { it.toIntOrNull()?.let { id -> "movie" to id } } +
            db.favoriteIds(0, "MYLIST_TV").mapNotNull { it.toIntOrNull()?.let { id -> "tv" to id } }

    /** Échap : ferme la fenêtre Sources, le lecteur (plein écran d'abord), puis l'écran poussé. */
    fun back(): Boolean = when {
        sources != null -> { sources = null; true }
        playing != null && fullscreen -> { fullscreen = false; true }
        playing != null -> { closePlayer(); true }
        stack.isNotEmpty() -> { stack.removeAt(stack.lastIndex); true }
        screen != Screen.HOME -> { go(Screen.HOME); true }
        else -> false
    }

    fun reloadPlaylist() {
        val all = db.playlists()
        val active = db.setting(Settings.ACTIVE_PLAYLIST)?.toLongOrNull()
        playlist = all.firstOrNull { it.id == active } ?: all.firstOrNull()
        dataVersion++
    }

    fun selectPlaylist(id: Long) {
        db.putSetting(Settings.ACTIVE_PLAYLIST, id.toString())
        reloadPlaylist()
    }

    fun hasPin(): Boolean = !db.setting(Settings.PIN).isNullOrBlank()

    fun checkPin(pin: String): Boolean {
        val stored = db.setting(Settings.PIN) ?: return true
        return PinHasher.verify(pin, stored).also { if (it) unlocked = true }
    }

    // --- lecture -------------------------------------------------------------------------------------

    fun play(list: List<Channel>, index: Int) {
        val p = playlist ?: return
        val c = list.getOrNull(index) ?: return
        saveProgress()
        playJob?.cancel()
        playing = NowPlaying.Live(list, index)
        player?.play(com.sokoul.desktop.data.LiveLogic.stream(p, c, sync.userAgent()))
    }

    fun zap(delta: Int) {
        val live = playing as? NowPlaying.Live ?: return
        play(live.list, com.sokoul.desktop.data.LiveLogic.zap(live.list.size, live.index, delta))
    }

    fun playMovie(movie: Movie) {
        val p = playlist ?: return
        val url = movie.streamUrl ?: when (p.type) {
            com.sokoul.desktop.data.PlaylistType.XTREAM ->
                com.sokoul.iptv.parser.XtreamUrls.movie(p.url.trimEnd('/'), p.username.orEmpty(), p.password.orEmpty(), movie.id.toLongOrNull() ?: return, movie.extension)
            com.sokoul.desktop.data.PlaylistType.M3U -> return
        }
        saveProgress()
        playJob?.cancel()
        playing = NowPlaying.Iptv(movie)
        player?.play(StreamRequest(url, p.userAgent, null, movie.name, live = false))
    }

    /**
     * Lecture d'une œuvre TMDB par Real-Debrid : la version choisie, sinon la meilleure ; une version qui n'est pas
     * prête (ou en échec) laisse la place à la suivante, trois essais au plus. Reprise à la dernière position.
     */
    fun playTitle(t: NowPlaying.Title, chosen: DebridStream? = null) {
        saveProgress()
        playJob?.cancel()
        player?.stop()
        sources = null
        playing = t.copy(step = "Recherche de la meilleure version…", error = null, stream = chosen)
        if (player == null) return  // écran « Lecteur indisponible » : ni recherche ni torrent ajouté au compte
        playJob = scope.launch {
            val candidates = if (chosen != null) listOf(chosen) else {
                val info = catalog.details(t.kind, t.tmdbId)
                val r = debrid.find(t.query, info)
                if (r.streams.isEmpty()) { update { it.copy(step = null, error = r.problem ?: "Aucune version trouvée") }; return@launch }
                // chaque version pas prête est ajoutée au compte Real-Debrid par l'extension : trois essais au plus
                r.streams.take(3)
            }
            var lastError: String? = null
            for ((i, s) in candidates.withIndex()) {
                // après une version pas prête, on n'essaie plus que celles annoncées prêtes par l'extension
                if (i > 0 && lastError == DesktopDebrid.NotReady().message && s.release.cached != true) continue
                update { it.copy(stream = s, step = if (i == 0) "Préparation de la lecture…" else "Version suivante…") }
                val url = debrid.resolve(s).getOrElse { lastError = it.message; null } ?: continue
                val start = withContext(Dispatchers.IO) { db.resume(t.resumeKey) }?.takeIf { !it.finished && it.positionMs > 30_000 }?.positionMs ?: 0
                update { it.copy(step = null) }
                player?.play(StreamRequest(url, null, null, t.title, live = false, startMs = start))
                return@launch
            }
            update { it.copy(step = null, error = lastError ?: "Lecture impossible") }
        }
    }

    private fun update(block: (NowPlaying.Title) -> NowPlaying.Title) {
        (playing as? NowPlaying.Title)?.let { playing = block(it) }
    }

    /**
     * Position du film ou de l'épisode en cours (toutes les 10 s et à la fermeture). Écrite hors du fil de l'interface
     * (une synchronisation peut tenir la base) ; [now] = tout de suite, à la fermeture de l'app avant celle de la base.
     */
    fun saveProgress(now: Boolean = false) {
        val t = playing as? NowPlaying.Title ?: return
        val st = player?.status?.value ?: return
        // une version encore en préparation n'a pas de position à elle
        if (t.step != null || st.lengthMs <= 0 || st.timeMs <= 0 || st.title != t.title) return
        val finished = st.timeMs.toFloat() / st.lengthMs >= Resume.FINISHED_AT
        val r = Resume(t.resumeKey, t.kind, t.tmdbId, t.season, t.episode, t.title, t.subtitle, t.image, st.timeMs, st.lengthMs, System.currentTimeMillis(), finished)
        if (now) db.saveResume(r) else scope.launch(Dispatchers.IO) { db.saveResume(r) }
    }

    /** Fermer le lecteur rend la connexion à l'abonnement. */
    fun closePlayer(now: Boolean = false) {
        saveProgress(now)
        playJob?.cancel()
        player?.stop()
        playing = null
        fullscreen = false
        dataVersion++
    }
}
