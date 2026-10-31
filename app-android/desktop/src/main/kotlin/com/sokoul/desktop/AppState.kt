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
import com.sokoul.desktop.player.AudioChoice
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Onglets de la barre du haut. */
enum class Screen(val label: String) {
    HOME("Accueil"), MOVIES("Films"), SERIES("Séries"), MY_LIST("Ma liste"), SAGAS("Sagas"), LIVE("Direct"), GUIDE("Guide TV"),
    SEARCH("Recherche"), RECORDINGS("Enregistrements"), SETTINGS("Réglages"),
}

/** Écrans poussés au-dessus d'un onglet (Échap revient). */
sealed interface Route {
    data class Detail(val kind: String, val id: Int) : Route
    data class Saga(val id: Int) : Route
    data class Platform(val platform: com.sokoul.iptv.data.repo.Platform, val kind: String) : Route
    data object Upcoming : Route
    data object History : Route
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
    /** Enregistrement du direct (fichier local). */
    data class Recorded(val rec: com.sokoul.desktop.data.Recording) : NowPlaying
}

/** État global de l'app PC : base, catalogue, Real-Debrid, lecteur (unique), navigation et playlist active. */
class AppState(val db: DesktopDb, val sync: DesktopSync, val player: DesktopPlayer?) {
    val catalog = Catalog(db)
    val debrid = DesktopDebrid(db, catalog)
    val upcoming = com.sokoul.desktop.data.DesktopUpcoming(db, catalog)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var screen by mutableStateOf(Screen.HOME)
    val stack = mutableStateListOf<Route>()
    var playlist by mutableStateOf<Playlist?>(null)
        private set
    var playing by mutableStateOf<NowPlaying?>(null)
    var fullscreen by mutableStateOf(false)
    /** Fenêtre réduite (480×270, toujours au premier plan) : l'image dans l'image du PC. */
    var mini by mutableStateOf(false)
    /** Barre du haut opaque (page défilée). */
    var navSolid by mutableStateOf(false)
    /** Code parental saisi pendant cette session (groupes verrouillés accessibles jusqu'à la fermeture). */
    var unlocked by mutableStateOf(false)
    /** Incrémenté après une synchronisation, un import ou une clé changée : les écrans rechargent. */
    var dataVersion by mutableStateOf(0)
    /** Fenêtre Sources ouverte pour cette œuvre. */
    var sources by mutableStateOf<NowPlaying.Title?>(null)
    private var playJob: Job? = null

    /** Versions en échec pour le titre en cours (clé → raison), affichées dans « Langue et qualité ». */
    val failures = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /** Réglages de lecture et de télévision (lus par le lecteur à chaque ouverture). */
    val prefs = com.sokoul.desktop.data.DesktopPrefs(db)

    /** Enregistrements du direct (planificateur démarré par la fenêtre, pas par les tests). */
    val recorder = com.sokoul.desktop.data.DesktopRecorder(db, sync, scope)

    /** Message bref en bas de la fenêtre (enregistrement prioritaire, programmation…), effacé tout seul. */
    var notice by mutableStateOf<String?>(null)
    private var noticeJob: Job? = null
    fun notify(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = scope.launch { kotlinx.coroutines.delay(6_000); notice = null }
    }

    /**
     * Démarre le planificateur d'enregistrements. Quand un enregistrement commence, il prend la connexion : une
     * chaîne d'une autre lecture (ou de l'aperçu) est arrêtée avec un message.
     */
    fun startRecorder() {
        recorder.start { playlist }
        scope.launch {
            recorder.active.collect { r ->
                r ?: return@collect
                if (previewing != null && previewing?.id != r.channelId) stopPreview()
                (playing as? NowPlaying.Live)?.let { live ->
                    if (live.list[live.index].id != r.channelId) { closePlayer(); notify("Enregistrement de « ${r.title} » sur ${com.sokoul.desktop.data.PcRecordingRules.channelLabel(r.channelName)} : la lecture a été arrêtée (une seule connexion)") }
                }
            }
        }
    }

    /** Programme l'enregistrement du programme [title] de [c] (créneau du guide, marges ajoutées). */
    fun record(c: Channel, title: String, programStart: Long, programEnd: Long) {
        val p = playlist ?: return
        val (s, e) = com.sokoul.desktop.data.PcRecordingRules.slot(programStart, programEnd, System.currentTimeMillis())
        when (val v = recorder.schedule(p, c, title, s, e)) {
            is com.sokoul.desktop.data.PcRecordingRules.Verdict.Ok -> notify("« $title » sera enregistré" + (v.warning?.let { ". $it" } ?: ""))
            is com.sokoul.desktop.data.PcRecordingRules.Verdict.Refused -> notify(v.reason)
        }
        dataVersion++
    }

    /** Lecture d'un enregistrement (terminé, ou en cours : on rejoint la fin du fichier). */
    fun playRecording(r: com.sokoul.desktop.data.Recording, live: Boolean = false) {
        val path = r.path ?: return
        saveProgress(); playJob?.cancel(); previewing = null
        playing = NowPlaying.Recorded(r)
        val start = if (live) (System.currentTimeMillis() - r.startAt - 8_000).coerceAtLeast(0) else 0
        // chemin Windows tel quel : File.toURI donne « file:/C:/… », que libVLC refuse
        player?.play(StreamRequest(java.io.File(path).absolutePath, null, null, r.title, live = false, startMs = start))
    }

    init {
        reloadPlaylist()
        player?.prefs = { prefs.player() }
    }

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
        // un enregistrement a la connexion : sa chaîne se regarde depuis le fichier, les autres sont refusées
        recorder.active.value?.let { r ->
            if (r.channelId == c.id) { playRecording(r, live = true); return }
            notify("« ${r.title} » est en cours d'enregistrement sur ${com.sokoul.desktop.data.PcRecordingRules.channelLabel(r.channelName)} : une seule connexion, la chaîne ${com.sokoul.desktop.data.PcRecordingRules.channelLabel(c.name)} attendra la fin")
            return
        }
        saveProgress()
        playJob?.cancel()
        previewing = null
        playing = NowPlaying.Live(list, index)
        prefs.lastChannel = c.id
        player?.play(com.sokoul.desktop.data.LiveLogic.stream(p, c, sync.userAgent()))
    }

    /** Chaîne en aperçu dans le Direct (lecteur unique, son coupé), ou null. */
    var previewing by mutableStateOf<Channel?>(null)
        private set

    /** Aperçu de la chaîne choisie dans le Direct : jamais pendant une lecture en plein cadre. */
    fun preview(c: Channel) {
        val p = playlist ?: return
        if (playing != null || previewing?.id == c.id) return
        // pendant un enregistrement, pas d'aperçu (il prendrait la connexion)
        if (recorder.active.value != null) return
        previewing = c
        player?.preview(com.sokoul.desktop.data.LiveLogic.stream(p, c, sync.userAgent()))
    }

    /** Quitter le Direct : l'aperçu est arrêté (la connexion est libérée). */
    fun stopPreview() {
        if (previewing == null) return
        previewing = null
        if (playing == null) player?.stop()
    }

    /** « Regarder » : la chaîne en aperçu passe en plein cadre sans rouvrir le flux ; sinon lecture normale. */
    fun watch(list: List<Channel>, index: Int) {
        val c = list.getOrNull(index) ?: return
        if (previewing?.id == c.id && player != null) {
            previewing = null
            playing = NowPlaying.Live(list, index)
            prefs.lastChannel = c.id
            player.endPreview()
        } else { previewing = null; play(list, index) }
    }

    /** Zapping (Page préc. = −1, Page suiv. = +1), dans le sens choisi dans les Réglages. */
    fun zap(delta: Int) {
        val live = playing as? NowPlaying.Live ?: return
        play(live.list, com.sokoul.desktop.data.LiveLogic.zap(live.list.size, live.index, com.sokoul.desktop.data.DesktopPrefs.zapStep(delta, prefs.zapInverted)))
    }

    /** « Au démarrage : dernière chaîne regardée » : relance la dernière chaîne, dans son groupe. */
    fun resumeLastChannel() {
        val p = playlist ?: return
        val id = prefs.lastChannel ?: return
        val all = db.allChannels(p.id)
        val i = all.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        val group = all.filter { it.categoryId == all[i].categoryId }
        play(group, group.indexOfFirst { it.id == id }.coerceAtLeast(0))
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
        previewing = null
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
        // un autre titre (ou un nouvel essai automatique) : les échecs notés ne valent plus
        if (chosen == null || (playing as? NowPlaying.Title)?.let { it.tmdbId != t.tmdbId || it.season != t.season || it.episode != t.episode } != false) failures.clear()
        previewing = null
        playing = t.copy(step = "Recherche de la meilleure version…", error = null, stream = chosen)
        if (player == null) return  // écran « Lecteur indisponible » : ni recherche ni torrent ajouté au compte
        playJob = scope.launch {
            val candidates = if (chosen != null) listOf(chosen) else {
                val info = catalog.details(t.kind, t.tmdbId)
                val r = debrid.find(t.query, info)
                if (r.streams.isEmpty()) { update { it.copy(step = null, error = r.problem ?: "Aucune version trouvée") }; return@launch }
                r.streams
            }
            var lastError: String? = null
            // chaque version pas prête est ajoutée au compte Real-Debrid par l'extension : trois essais au plus, pris
            // dans toute la liste (les trois premières peuvent toutes être en préparation)
            var tries = 0
            // version lancée faute de mieux (annoncée française, sans piste française) : gardée si rien d'autre ne passe
            var fallback: DebridStream? = null
            // après un échec, une autre extension d'abord (Comet peut annoncer prêt un fichier refusé par Real-Debrid)
            val remaining = candidates.toMutableList()
            val failedHosts = HashSet<String>()
            while (tries < MAX_TRIES) {
                // après une version pas prête, on n'essaie plus que celles annoncées prêtes par l'extension
                val s = com.sokoul.desktop.data.PlayAttempts.next(remaining, failedHosts, readyOnly = tries > 0 && lastError == DesktopDebrid.NotReady().message) ?: break
                remaining.remove(s)
                update { it.copy(stream = s, step = if (tries == 0) "Préparation de la lecture…" else if (fallback != null) "Pas de français dans cette version, version suivante…" else "Version suivante…") }
                tries++
                val url = debrid.resolve(s).getOrElse {
                    lastError = it.message; failures[s.key] = it.message ?: "échec"
                    com.sokoul.desktop.data.PlayAttempts.host(s)?.let(failedHosts::add)
                    null
                } ?: continue
                val start = withContext(Dispatchers.IO) { db.resume(t.resumeKey) }?.takeIf { !it.finished && it.positionMs > 30_000 }?.positionMs ?: 0
                player?.play(StreamRequest(url, null, null, t.title, live = false, startMs = start))
                // « MULTI » ou « VF » dans le nom ne garantit pas le français (MULTI de groupes étrangers) : pistes
                // vérifiées à l'ouverture, version suivante s'il n'y en a pas (sauf version choisie à la main)
                if (chosen == null && tries < MAX_TRIES && s.release.language in FRENCH_EXPECTED) {
                    // les pistes arrivent une à une : la liste est relue un peu après la première
                    val tracks = withTimeoutOrNull(TRACKS_WAIT_MS) { player?.status?.first { it.audio.isNotEmpty() || it.error != null } }
                        ?.let { kotlinx.coroutines.delay(TRACKS_SETTLE_MS); player?.status?.value?.audio }
                    if (!tracks.isNullOrEmpty() && !AudioChoice.hasFrench(tracks)) {
                        fallback = s
                        lastError = "Pas de piste française"
                        failures[s.key] = "pas de piste française dans le fichier"
                        continue
                    }
                }
                update { it.copy(stream = s, step = null) }
                return@launch
            }
            // aucune autre version ne passe : la version sans français continue (lue en anglais si possible)
            if (fallback != null) update { it.copy(stream = fallback, step = null) }
            else update { it.copy(step = null, error = lastError ?: "Lecture impossible") }
        }
    }

    private companion object {
        /** Versions essayées au plus pour une lecture (une version pas prête est ajoutée au compte à chaque essai). */
        const val MAX_TRIES = 3
        /** Attente des pistes du fichier avant de vérifier le français. */
        const val TRACKS_WAIT_MS = 20_000L
        const val TRACKS_SETTLE_MS = 1_500L
        /** Versions annoncées avec une piste française. */
        val FRENCH_EXPECTED = setOf(com.sokoul.iptv.domain.AudioLanguage.MULTI, com.sokoul.iptv.domain.AudioLanguage.VF)
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
        mini = false
        dataVersion++
    }
}
