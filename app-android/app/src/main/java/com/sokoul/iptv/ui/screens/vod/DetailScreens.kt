package com.sokoul.iptv.ui.screens.vod

import com.sokoul.iptv.ui.components.SourceLabels
import com.sokoul.iptv.ui.components.MetaItem
import com.sokoul.iptv.ui.components.MetaKind
import com.sokoul.iptv.ui.components.onChip
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.nav.navigateOnce
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.EpisodeEntity
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.ProgressEntity
import com.sokoul.iptv.data.db.SeriesDetailsEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbPerson
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.CatalogRepository
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.EpisodeMerge
import com.sokoul.iptv.data.repo.EpisodeView
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.MovieDetails
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.VersionPicker
import com.sokoul.iptv.data.repo.VodRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.keyOf
import com.sokoul.iptv.data.repo.looksFrench
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.data.repo.toVersion
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.data.repo.userMessage
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.HeroButton
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.OptionsDialog
import com.sokoul.iptv.ui.components.ProgressLine
import com.sokoul.iptv.ui.components.RatingChip
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.certificationLabel
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.components.formatRuntime
import com.sokoul.iptv.ui.nav.PlayKind
import com.sokoul.iptv.ui.nav.PlayerRoute
import com.sokoul.iptv.ui.screens.browse.openWork
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.util.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.data.repo.EpisodeResolver
import com.sokoul.iptv.data.repo.SeriesAction
import com.sokoul.iptv.data.repo.DetailExtras
import com.sokoul.iptv.data.repo.ExtrasRepository
import com.sokoul.iptv.data.net.providers.NextEpisode
import com.sokoul.iptv.data.net.providers.OfferType
import com.sokoul.iptv.data.net.providers.Ratings
import com.sokoul.iptv.data.net.providers.StreamingOffer
import com.sokoul.iptv.data.net.providers.TvdbEpisode

/** Texte affichable et sa langue : TMDB français → fournisseur en français → TMDB anglais → fournisseur. */
data class Synopsis(val text: String, val french: Boolean)

fun pickSynopsis(tmdbFr: String?, provider: String?, tmdbEn: String?): Synopsis? {
    tmdbFr?.takeIf { it.isNotBlank() }?.let { return Synopsis(it, true) }
    provider?.takeIf { looksFrench(it) }?.let { return Synopsis(it, true) }
    tmdbEn?.takeIf { it.isNotBlank() }?.let { return Synopsis(it, false) }
    provider?.takeIf { it.isNotBlank() }?.let { return Synopsis(it, false) }
    return null
}

// --- Film ----------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val vod: VodRepository,
    private val enrich: EnrichmentRepository,
    private val browse: BrowseRepository,
    private val extrasRepo: ExtrasRepository,
    private val debrid: com.sokoul.iptv.data.debrid.DebridSources,
    private val gate: com.sokoul.iptv.player.ProviderGate,
    private val trakt: com.sokoul.iptv.data.trakt.TraktSync,
) : ViewModel() {
    val details = MutableStateFlow<MovieDetails?>(null)
    /** Versions Real-Debrid trouvées (vide sans compte, sans extension ou sans résultat). */
    val debridStreams = MutableStateFlow<List<com.sokoul.iptv.data.debrid.DebridStream>>(emptyList())
    /** Toutes les sources, IPTV et Real-Debrid, dans l'ordre de lancement (la première = « Lecture »). */
    val ranked = MutableStateFlow<List<com.sokoul.iptv.data.debrid.Candidate>>(emptyList())
    /** Ordre des langues des Réglages (sections de l'étagère des versions). */
    val languages = MutableStateFlow(com.sokoul.iptv.data.repo.VersionPicker.DEFAULT_LANGUAGES)
    var debridQuery: com.sokoul.iptv.data.debrid.DebridQuery? = null
        private set

    private suspend fun rerank() {
        val q = qualities.value
        val iptv = versions.value.map { it.toVersion().copy(measured = q[it.id]) }
        val iptvBlocked = gate.recording.value != null || gate.remainingMs() > 0
        val prefs = debrid.prefs(iptvBlocked)
        languages.value = prefs.languages
        ranked.value = com.sokoul.iptv.data.debrid.SourceRanker.rank(iptv, debridStreams.value, prefs)
    }

    /** Recherche Real-Debrid en cours : la fiche l'annonce sous « Lecture ». */
    val searching = MutableStateFlow(false)

    private fun loadDebrid(tmdbId: Int) = viewModelScope.launch {
        if (!debrid.enabled()) return@launch
        val q = com.sokoul.iptv.data.debrid.DebridQuery(tmdbId, series = false)
        debridQuery = q
        searching.value = true
        try {
            debridStreams.value = runCatching { debrid.find(q) }.getOrDefault(emptyList())
            rerank()
        } finally {
            searching.value = false
        }
    }
    val error = MutableStateFlow<String?>(null)
    /** Versions de l'œuvre dans l'ordre de lancement (la première est lancée par « Lecture »). */
    val versions = MutableStateFlow<List<MovieEntity>>(emptyList())
    /** Qualité réelle des versions (mesurée par le panel ou le lecteur). */
    val qualities = MutableStateFlow<Map<String, com.sokoul.iptv.data.repo.VodQuality>>(emptyMap())
    val preferredId = MutableStateFlow<String?>(null)
    val similar = MutableStateFlow<List<Work>>(emptyList())
    val saga = MutableStateFlow<Pair<String, List<Work>>?>(null)
    /** Compléments des sources secondaires (notes, « Aussi sur », logo de secours). */
    val extras = MutableStateFlow(DetailExtras())
    var categoryNames: Map<String, String> = emptyMap()
        private set
    var playlist: PlaylistEntity? = null

    private val ids = versions.map { l -> l.map { it.id } }.distinctUntilChanged()
    val progress: StateFlow<ProgressEntity?> = ids.flatMapLatest { l ->
        val p = playlist
        if (p == null || l.isEmpty()) flowOf(null) else vod.observeLatestMovieProgress(p.id, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val favorite: StateFlow<Boolean> = ids.flatMapLatest { l ->
        val p = playlist
        if (p == null || l.isEmpty()) flowOf(false) else vod.observeAnyFavorite(p.id, ContentType.MOVIE, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun load(id: String, force: Boolean = false) = viewModelScope.launch {
        if (!force && details.value?.movie?.id == id) return@launch
        error.value = null
        val p = playlists.activeNow() ?: run { error.value = "Aucune playlist active : ajoutez ou activez une playlist dans les réglages."; return@launch }
        playlist = p
        val m = catalog.movie(p.id, id) ?: run { error.value = "Ce film n'est plus proposé par votre fournisseur (catalogue mis à jour)."; return@launch }
        details.value = MovieDetails(m, null, null)
        categoryNames = vod.categoryNames(p.id, ContentType.MOVIE)
        loadVersions(p, m)
        try {
            val hint = runCatching { enrich.known(p.id, ContentType.MOVIE, listOf(m.keyOf()))[m.keyOf()]?.tmdbId }.getOrNull()
            val d = catalog.movieDetails(p, m, hint)
            details.value = d
            // TMDB a reconnu l'œuvre : on la rattache aux autres versions (année identique exigée, pour éviter un homonyme)
            val tm = d.tmdb
            val tmYear = tm?.year
            if (tm != null && m.year != null && tmYear != null && kotlin.math.abs(tmYear - m.year) <= 1) {
                vod.linkTmdb(p.id, ContentType.MOVIE, m.id, m.tmdbId, m.groupKey, tm.id)
                catalog.movie(p.id, m.id)?.let { fresh -> if (fresh.groupKey != m.groupKey) loadVersions(p, fresh) }
            }
            if (tm != null) {
                loadDebrid(tm.id)
                launch { extras.value = extrasRepo.extras(true, tm) }
                launch { moreLikeThis(browse, extrasRepo, p.id, ContentType.MOVIE, tm, m.keyOf()) { similar.value = it } }
                tm.collectionId?.let { cid -> launch { saga.value = runCatching { browse.saga(p.id, cid) }.getOrNull()?.takeIf { it.second.size >= 2 } } }
            }
        } catch (e: Exception) {
            // TMDB ou fiche du fournisseur indisponible : la fiche reste utilisable avec les données du catalogue
            timber.log.Timber.w(e, "Fiche film")
        }
        // qualité réelle des autres versions : mesurée chez le fournisseur, une à la fois, puis la fiche se met à jour
        if (versions.value.size > 1) {
            runCatching { catalog.probeMovies(p, versions.value) }
            details.value?.movie?.let { loadVersions(p, it) }
        }
    }

    private suspend fun loadVersions(p: PlaylistEntity, m: MovieEntity) {
        val all = vod.movieVersions(p.id, m)
        versions.value = vod.orderedMovies(p.id, all)
        qualities.value = vod.qualities(p.id, ContentType.MOVIE, all.map { it.id })
        preferredId.value = vod.choice(p.id, ContentType.MOVIE, m.groupKey)
        rerank()
    }

    fun prefer(v: MovieEntity) = viewModelScope.launch {
        val p = playlist ?: return@launch
        vod.setChoice(p.id, ContentType.MOVIE, v.groupKey, v.id)
        details.value?.movie?.let { loadVersions(p, it) }
    }

    fun toggleFavorite(shownId: String) = viewModelScope.launch {
        val added = playlist?.let { vod.toggleFavorite(it.id, ContentType.MOVIE, versions.value.map { v -> v.id }.ifEmpty { listOf(shownId) }, shownId) } ?: false
        if (added) details.value?.tmdb?.id?.let { trakt.addedToList(com.sokoul.iptv.data.trakt.TraktItem(it)) }
    }
    fun clearProgress() = viewModelScope.launch {
        playlist?.let { p -> vod.clearMovieProgress(p.id, versions.value.map { it.id }) }
    }

    /** « Marquer comme vu / non vu » (menu ⋯) : vaut pour toutes les versions du film. */
    fun setWatched(watched: Boolean) = viewModelScope.launch {
        val p = playlist ?: return@launch
        val m = details.value?.movie ?: return@launch
        val ids = versions.value.map { it.id }.ifEmpty { listOf(m.id) }
        if (watched) details.value?.tmdb?.id?.let { trakt.markedWatched(listOf(com.sokoul.iptv.data.trakt.TraktItem(it))) }
        if (watched) library.setWatched(p.id, ContentType.MOVIE, ids.first(), null, m.title, true)
        else vod.clearMovieProgress(p.id, ids)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailScreen(nav: NavHostController, id: String) {
    val vm: MovieDetailViewModel = hiltViewModel()
    val d by vm.details.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val preferredId by vm.preferredId.collectAsStateWithLifecycle()
    val qualities by vm.qualities.collectAsStateWithLifecycle()
    val similar by vm.similar.collectAsStateWithLifecycle()
    val saga by vm.saga.collectAsStateWithLifecycle()
    val ranked by vm.ranked.collectAsStateWithLifecycle()
    val languages by vm.languages.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    var groupMenu by remember { mutableStateOf<com.sokoul.iptv.ui.screens.player.VersionGroup?>(null) }
    var otherMenu by remember { mutableStateOf<List<com.sokoul.iptv.ui.screens.player.VersionGroup>?>(null) }
    val extras by vm.extras.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val l = LocalLayout.current
    val memory = rememberFocusMemory()
    var versionMenu by remember { mutableStateOf<MovieEntity?>(null) }
    var moreMenu by remember { mutableStateOf(false) }
    /** 1 = choisir la version à lire, 2 = choisir la version lancée en premier. */
    var versionPicker by remember { mutableStateOf(0) }
    var showSynopsis by remember { mutableStateOf(false) }
    LaunchedEffect(id) { vm.load(id) }

    val details = d
    val pid = vm.playlist?.id
    if (details == null || pid == null) {
        val e = error
        if (e != null) DetailError(e, onRetry = { vm.load(id, force = true) }, onBack = { nav.popBackStack() }) else DetailSkeleton()
        return
    }
    val m = details.movie
    val tm = details.tmdb
    val xt = details.xtream
    val synopsis = pickSynopsis(tm?.overview, xt?.plot, tm?.overviewEn)
    val durationMin = xt?.durationSecs?.div(60)?.takeIf { it > 0 } ?: tm?.runtimeMin
    val resume = progress?.takeIf { !it.finished && LibraryRepository.resumeFrom(it) > 0 }
    val watched = progress?.finished == true
    // version lancée par « Lecture » : la meilleure selon les préférences (langue, qualité, choix manuel)
    val best = versions.firstOrNull() ?: m
    val multi = versions.size > 1
    val title = tm?.title?.takeIf { it.isNotBlank() } ?: com.sokoul.iptv.data.repo.cleanDisplayTitle(m.title, m.year)
    val play = { v: MovieEntity, fromStart: Boolean -> nav.navigateOnce(PlayerRoute(PlayKind.MOVIE, pid, v.id, fromStart = fromStart)) }
    // version Real-Debrid : la reprise reste rangée sous la fiche IPTV du film (une seule ligne « Reprendre »)
    val playRd = { key: String, fromStart: Boolean ->
        nav.navigateOnce(PlayerRoute(PlayKind.MOVIE, pid, best.id, fromStart = fromStart, source = key, query = vm.debridQuery?.encode()))
    }
    val top = ranked.firstOrNull()
    val topRd = top as? com.sokoul.iptv.data.debrid.Candidate.Debrid
    val playBest = { fromStart: Boolean -> if (topRd != null) playRd(topRd.stream.key, fromStart) else play(best, fromStart) }
    // sources affichées : mêmes lignes que dans le lecteur (origine en clair, qualité mesurée ou d'après le nom)
    val detailSources = remember(ranked) {
        ranked.map { c ->
            when (c) {
                is com.sokoul.iptv.data.debrid.Candidate.Iptv -> com.sokoul.iptv.ui.screens.player.PlayerSources.fromIptv(
                    c.version.copy(categoryName = c.version.categoryName ?: vm.categoryNames[c.version.categoryId]), current = false,
                )
                is com.sokoul.iptv.data.debrid.Candidate.Debrid -> com.sokoul.iptv.ui.screens.player.PlayerSources.fromDebrid(c.stream, current = false, id = RD_ITEM + c.stream.key)
            }
        }
    }
    val groups = remember(detailSources, languages) { com.sokoul.iptv.ui.screens.player.SourceCatalog.group(detailSources, languages) }
    val playSource = { id: String -> if (id.startsWith(RD_ITEM)) playRd(id.removePrefix(RD_ITEM), false) else versions.firstOrNull { it.id == id }?.let { play(it, false) } ?: Unit }
    val sourceCount = com.sokoul.iptv.ui.screens.player.SourceCatalog.merge(detailSources).size.coerceAtLeast(if (multi) 2 else 1)
    val trailer = (tm?.trailerYoutubeKey ?: xt?.trailer)?.takeIf { it.isNotBlank() }
    val director = (tm?.director ?: xt?.director)?.takeIf { it.isNotBlank() }

    val hero = DetailHeroData(
        title = title, logo = tm?.logo ?: extras.art?.logo, backdrop = tm?.backdrop ?: xt?.backdrop ?: extras.art?.background, poster = tm?.poster ?: xt?.poster ?: m.poster,
        rating = tm?.rating ?: xt?.rating ?: m.rating,
        facts = listOfNotNull(
            (m.year ?: tm?.year)?.let { MetaItem(MetaKind.YEAR, it.toString()) },
            formatRuntime(durationMin)?.let { MetaItem(MetaKind.DURATION, it) },
            durationMin?.takeIf { it > 0 }?.let { MetaItem(MetaKind.ENDS, com.sokoul.iptv.ui.util.endsAt(it)) },
            (topRd?.source?.language?.name ?: best.language)?.let { MetaItem(MetaKind.LANGUAGE, it) },
        ),
        // qualité seule : l'origine est déjà dans le bouton Lecture et dans les versions
        certification = tm?.certification, quality = topRd?.let { listOfNotNull(it.source.quality?.let(SourceLabels::quality), it.source.hdr?.let(SourceLabels::hdr)).joinToString(" · ").ifEmpty { null } } ?: qualities[best.id]?.let { q -> listOfNotNull(q.resolution, q.hdr).joinToString(" ").ifEmpty { null } } ?: versionSource(best).let { s -> listOfNotNull(s.quality?.let(SourceLabels::quality), s.hdr?.let(SourceLabels::hdr)).joinToString(" ").ifEmpty { null } },
        genres = tm?.genres?.joinToString(", ")?.ifEmpty { null } ?: xt?.genre,
        tagline = tm?.tagline, synopsis = synopsis,
        credit = director?.let { "Réalisation : $it" } ?: xt?.cast?.takeIf { tm?.cast.isNullOrEmpty() && it.isNotBlank() }?.let { "Avec : $it" },
        watched = watched, externalRatings = ratingBadges(extras.ratings), availability = availabilityPills(extras.offers),
    )
    val primary = PrimaryAction(
        label = (when { resume != null -> "Reprendre"; watched -> "Revoir"; else -> "Lecture" }) + when {
            topRd != null -> " · ${SourceLabels.short(topRd.source)}"
            multi || sourceCount > 1 -> " · ${versionLabel(best)}"
            else -> ""
        },
        // recherche Real-Debrid en cours : Lecture reste possible, le lecteur attendra la meilleure version
        detail = resume?.let { EpisodeResolver.remaining(it) } ?: if (searching) "Recherche de la meilleure version…" else null,
        progress = resume?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs },
        onClick = { playBest(watched && resume == null) },
    )
    val actions = buildList {
        if (resume != null) add(SecondaryAction("restart", "Depuis le début", Icons.Default.Replay) { playBest(true) })
        add(SecondaryAction("fav", if (favorite) "Dans ma liste" else "Ma liste", if (favorite) Icons.Default.Check else Icons.Default.Add) { vm.toggleFavorite(m.id) })
        if (trailer != null) add(SecondaryAction("trailer", "Bande-annonce", Icons.Default.Theaters) { openYoutube(context, trailer) })
        add(SecondaryAction("more", "Plus", Icons.Default.MoreHoriz) { moreMenu = true })
    }
    val hasMore = multi || tm?.cast?.isNotEmpty() == true || saga != null || similar.isNotEmpty()

    DetailScaffold(
        nav, hero, primary, primaryLoading = false, actions = actions, hint = if (hasMore && l.isTv) "Plus d'infos" else null,
        memory = memory, onSynopsis = { showSynopsis = true },
    ) {
        // ▼ depuis les versions : première carte de la section suivante (évite le saut en bout de rangée de Compose)
        val belowVersions = (saga?.second?.firstOrNull()?.let { "saga:${it.uid}" } ?: similar.firstOrNull()?.let { "sim:${it.uid}" })?.let { memory.requester(it) }
        if (sourceCount > 1) {
            // toutes les sources, toutes origines classées ensemble, rangées par version (langue + qualité), doublons fusionnés
            detailSection(
                "versions", "Versions disponibles",
                com.sokoul.iptv.ui.screens.player.sourceCountLine(detailSources, com.sokoul.iptv.ui.screens.player.SourceSearch.DONE) + " · " +
                    if (l.isTouch) "touchez pour lire" else "OK pour lire, appui long : toutes les sources",
            ) {
                VersionsShelf(
                    groups, languages, firstId = detailSources.firstOrNull()?.id, preferredId = preferredId, memory = memory, below = belowVersions,
                    onPlay = { g -> playSource(g.best.id) },
                    onMenu = { g -> groupMenu = g },
                    onOthers = { list -> otherMenu = list },
                )
            }
        }
        castSection(tm?.cast.orEmpty(), memory) { p -> nav.navigateOnce(com.sokoul.iptv.ui.nav.PersonRoute(p.id ?: return@castSection, p.name)) }
        saga?.let { (name, works) ->
            detailSection("saga", "Saga : ${name.replace(" - Saga", "").replace(" (Collection)", "")}", "Toute l'histoire, dans l'ordre de sortie.") {
                CardRow(works, { it.uid }) { _, w ->
                    WorkCard(w, { if (w.id != m.id) nav.openWork(w) }, Modifier.focusMemory(memory, "saga:${w.uid}"), showPlatform = false)
                }
            }
        }
        if (similar.isNotEmpty()) {
            detailSection("similar", "Vous aimerez aussi", "Des films dans la même veine, disponibles dans votre abonnement.") {
                CardRow(similar, { it.uid }) { _, w -> WorkCard(w, { nav.openWork(w) }, Modifier.focusMemory(memory, "sim:${w.uid}")) }
            }
        }
    }

    if (moreMenu) {
        OptionsDialog(
            title,
            buildList {
                if (synopsis != null) add(MenuOption("Lire le résumé complet", Icons.Default.Info) { showSynopsis = true })
                if (multi) add(MenuOption("Choisir la version à lire…", Icons.Default.PlayArrow) { versionPicker = 1 })
                if (multi) add(MenuOption("Version lancée en premier…", Icons.Default.Star) { versionPicker = 2 })
                add(
                    if (watched) MenuOption("Marquer comme non vu", Icons.Default.Replay) { vm.setWatched(false) }
                    else MenuOption("Marquer comme vu", Icons.Default.CheckCircle) { vm.setWatched(true) }
                )
            },
            subtitle = hero.facts.joinToString(" · ") { it.text }.ifEmpty { null },
            onDismiss = { moreMenu = false },
        )
    }
    if (versionPicker != 0) {
        val prefer = versionPicker == 2
        OptionsDialog(
            if (prefer) "Version lancée en premier" else "Choisir la version",
            versions.map { v ->
                MenuOption(versionLabel(v) + (qualities[v.id]?.line()?.ifEmpty { null }?.let { " — $it" } ?: vm.categoryNames[v.categoryId]?.let { " · $it" } ?: ""), if (v.id == preferredId) Icons.Default.Star else Icons.Default.PlayArrow, selected = if (prefer) v.id == preferredId else v.id == best.id) {
                    if (prefer) vm.prefer(v) else play(v, false)
                }
            },
            onDismiss = { versionPicker = 0 },
        )
    }
    otherMenu?.let { list ->
        OptionsDialog(
            "Autres langues",
            list.map { g ->
                val n = g.sources.size
                MenuOption(
                    com.sokoul.iptv.ui.screens.player.PlayerSources.languageName(g.language, g.region) + " · " + com.sokoul.iptv.ui.screens.player.versionTitle(g) +
                        " · " + SourceLabels.originShort(g.best.origin) + if (n > 1) " · $n sources" else "",
                    Icons.Default.PlayArrow,
                ) { otherMenu = null; groupMenu = g }
            },
            subtitle = "Langues hors de vos préférences, et releases sans langue indiquée",
            onDismiss = { otherMenu = null },
        )
    }
    groupMenu?.let { g ->
        val lang = com.sokoul.iptv.ui.screens.player.PlayerSources.languageName(g.language, g.region)
        com.sokoul.iptv.ui.screens.player.SourcesDialog(
            "$lang · ${com.sokoul.iptv.ui.screens.player.versionTitle(g)}",
            subtitle = if (g.sources.size == 1) "Une seule source pour cette version" else "${g.sources.size} sources · la première est lue par défaut",
            sources = g.sources,
            defaultId = g.sources.firstOrNull()?.id,
            onPlay = { s -> playSource(s.id) },
            extra = g.sources.filter { it.origin == com.sokoul.iptv.domain.SourceOrigin.IPTV }.mapNotNull { s ->
                versions.firstOrNull { it.id == s.id }?.let { v ->
                    MenuOption("Toujours lancer en premier : " + s.where, Icons.Default.Star, selected = v.id == preferredId) { vm.prefer(v) }
                }
            },
            onDismiss = { groupMenu = null },
        )
    }
    versionMenu?.let { v ->
        OptionsDialog(
            versionLabel(v),
            listOf(
                MenuOption("Lire cette version", Icons.Default.PlayArrow) { play(v, false) },
                MenuOption("Toujours lancer cette version en premier", Icons.Default.Star) { vm.prefer(v) },
            ),
            subtitle = v.name,
            onDismiss = { versionMenu = null },
        )
    }
    if (showSynopsis && synopsis != null) SynopsisDialog(title, synopsis.text) { showSynopsis = false }
    // retour du lecteur ou d'une autre fiche : focus sur l'élément d'où l'on vient, sinon « Lecture »
    LaunchedEffect(m.id) {
        delay(120)
        if (!memory.restore()) runCatching { memory.requester("play").requestFocus() }
    }
}

// --- Série ---------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val vod: VodRepository,
    private val enrich: EnrichmentRepository,
    private val browse: BrowseRepository,
    private val extrasRepo: ExtrasRepository,
    private val debrid: com.sokoul.iptv.data.debrid.DebridSources,
    private val trakt: com.sokoul.iptv.data.trakt.TraktSync,
) : ViewModel() {
    /** Versions Real-Debrid d'un épisode, par (saison, épisode), chargées à la demande. */
    val episodeRd = MutableStateFlow<Map<Pair<Int, Int>, List<com.sokoul.iptv.data.debrid.DebridStream>>>(emptyMap())
    private val rdAsked = HashSet<Pair<Int, Int>>()
    /** Épisodes dont la meilleure version est sur Real-Debrid (mieux que l'IPTV). */
    val rdFirst = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    /** Préférences du classement (langues, réseau, origine à égalité), pour classer ensemble IPTV et Real-Debrid à l'écran. */
    val rankPrefs = MutableStateFlow(com.sokoul.iptv.data.debrid.SourceRanker.Prefs())

    /** Meilleur épisode de l'abonnement par (saison, épisode), parmi toutes les versions de la série qui l'ont. */
    val bestIptvEpisode = MutableStateFlow<Map<Pair<Int, Int>, EpisodeEntity>>(emptyMap())

    /**
     * Classe toutes les versions d'un épisode (une fois) : chaque version de la série (séries en double) qui l'a,
     * et Real-Debrid (rien sans compte ou en profil Enfant). « Lecture » lance la première, quelle que soit son origine.
     */
    fun loadEpisodeRd(season: Int, episode: Int) = viewModelScope.launch {
        val tmdbId = tmdb.value?.id ?: return@launch
        val k = season to episode
        if (!rdAsked.add(k)) return@launch
        val same = allEpisodes.value.filter { it.season == season && it.episode == episode }
        // épisodes pas encore chargés : on réessaiera quand ils arrivent
        if (same.isEmpty() && allEpisodes.value.isEmpty()) { rdAsked -= k; return@launch }
        val bySeries = same.associateBy { it.seriesId }
        // versions IPTV dans l'ordre de préférence de la série (celle affichée d'abord à égalité)
        val iptv = versions.value.filter { it.id in bySeries }.sortedBy { if (it.id == primaryId.value) 0 else 1 }
            .map { it.toVersion().copy(measured = qualities.value[it.id]) }
        val prefs = runCatching { debrid.prefs() }.getOrDefault(com.sokoul.iptv.data.debrid.SourceRanker.Prefs())
        rankPrefs.value = prefs
        val found = if (runCatching { debrid.enabled() }.getOrDefault(false)) {
            runCatching { debrid.find(com.sokoul.iptv.data.debrid.DebridQuery(tmdbId, series = true, season = season, episode = episode)) }.getOrDefault(emptyList())
        } else emptyList()
        val ranked = com.sokoul.iptv.data.debrid.SourceRanker.rank(iptv, found, prefs)
        ranked.filterIsInstance<com.sokoul.iptv.data.debrid.Candidate.Iptv>().firstOrNull()?.let { c ->
            bySeries[c.version.id]?.let { bestIptvEpisode.value = bestIptvEpisode.value + (k to it) }
        }
        if (ranked.firstOrNull() is com.sokoul.iptv.data.debrid.Candidate.Debrid) rdFirst.value = rdFirst.value + k
        episodeRd.value = episodeRd.value + (k to ranked.filterIsInstance<com.sokoul.iptv.data.debrid.Candidate.Debrid>().map { it.stream })
    }

    private var openedId: String? = null
    private val pidFlow = MutableStateFlow<Long?>(null)
    var playlist: PlaylistEntity? = null
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)
    val tmdb = MutableStateFlow<TmdbInfo?>(null)
    /** Épisodes TMDB en français, par (saison, épisode). */
    val tmdbEpisodes = MutableStateFlow<Map<Pair<Int, Int>, TmdbEpisode>>(emptyMap())
    private val loadedSeasons = HashSet<Int>()
    /** Repli TheTVDB (titres, résumés et vignettes en français), par (saison, épisode). */
    val tvdbEpisodes = MutableStateFlow<Map<Pair<Int, Int>, TvdbEpisode>>(emptyMap())
    val extras = MutableStateFlow(DetailExtras())
    val similar = MutableStateFlow<List<Work>>(emptyList())
    /** Versions de la série (VF, VOSTFR…), dans l'ordre de préférence. */
    val versions = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val qualities = MutableStateFlow<Map<String, com.sokoul.iptv.data.repo.VodQuality>>(emptyMap())
    /** Version principale : ses épisodes sont affichés, complétés par ceux des autres versions. */
    val primaryId = MutableStateFlow<String?>(null)
    val preferredId = MutableStateFlow<String?>(null)

    private val primaryKey = combine(pidFlow.filterNotNull(), primaryId.filterNotNull()) { p, s -> p to s }.distinctUntilChanged()
    private val versionIds = versions.map { l -> l.map { it.id } }.distinctUntilChanged()

    val series: StateFlow<SeriesEntity?> = primaryKey.flatMapLatest { (p, s) -> catalog.observeSeries(p, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val details: StateFlow<SeriesDetailsEntity?> = primaryKey.flatMapLatest { (p, s) -> catalog.seriesDetails(p, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** Épisodes bruts de toutes les versions (pour retrouver la saison/épisode d'une progression). */
    val allEpisodes: StateFlow<List<EpisodeEntity>> = combine(pidFlow.filterNotNull(), versionIds) { p, ids -> p to ids }
        .flatMapLatest { (p, ids) -> if (ids.isEmpty()) flowOf(emptyList()) else vod.observeEpisodes(p, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val episodes: StateFlow<List<EpisodeEntity>> = combine(allEpisodes, primaryId, versionIds) { all, primary, ids ->
        VodRepository.mergeEpisodes(all, listOfNotNull(primary) + ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progress: StateFlow<List<ProgressEntity>> = combine(pidFlow.filterNotNull(), versionIds) { p, ids -> p to ids }
        .flatMapLatest { (p, ids) -> if (ids.isEmpty()) flowOf(emptyList()) else vod.observeSeriesProgress(p, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorite: StateFlow<Boolean> = combine(pidFlow.filterNotNull(), versionIds) { p, ids -> p to ids }
        .flatMapLatest { (p, ids) -> if (ids.isEmpty()) flowOf(false) else vod.observeAnyFavorite(p, ContentType.SERIES, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** La série elle-même est introuvable (et non une simple erreur de chargement des épisodes). */
    val missing = MutableStateFlow<String?>(null)

    fun load(id: String, force: Boolean = false) = viewModelScope.launch {
        if (!force && openedId == id) return@launch
        missing.value = null
        val p = playlists.activeNow() ?: run { missing.value = "Aucune playlist active : ajoutez ou activez une playlist dans les réglages."; return@launch }
        playlist = p
        openedId = id
        loading.value = true
        error.value = null
        val s = catalog.seriesItem(p.id, id) ?: run { missing.value = "Cette série n'est plus proposée par votre fournisseur (catalogue mis à jour)."; loading.value = false; return@launch }
        val ordered = vod.orderedSeries(p.id, vod.seriesVersions(p.id, s))
        versions.value = ordered
        qualities.value = vod.qualities(p.id, ContentType.SERIES, ordered.map { it.id })
        preferredId.value = vod.choice(p.id, ContentType.SERIES, s.groupKey)
        val primary = ordered.firstOrNull()?.id ?: id
        primaryId.value = primary
        pidFlow.value = p.id
        // fiche TMDB en parallèle du chargement des épisodes (textes français, vignettes)
        launch {
            val hint = runCatching { enrich.known(p.id, ContentType.SERIES, listOf(s.keyOf()))[s.keyOf()]?.tmdbId }.getOrNull()
            val tm = catalog.seriesTmdb(s, hint)
            tmdb.value = tm
            if (tm != null) {
                launch { extras.value = extrasRepo.extras(false, tm) }
                launch { tvdbEpisodes.value = extrasRepo.tvdbEpisodes(tm) }
                launch { moreLikeThis(browse, extrasRepo, p.id, ContentType.SERIES, tm, s.keyOf()) { similar.value = it } }
                val tmYear = tm.year
                if (s.year != null && tmYear != null && kotlin.math.abs(tmYear - s.year) <= 1) {
                    vod.linkTmdb(p.id, ContentType.SERIES, s.id, s.tmdbId, s.groupKey, tm.id)
                    catalog.seriesItem(p.id, s.id)?.let { fresh ->
                        if (fresh.groupKey != s.groupKey) {
                            val more = vod.orderedSeries(p.id, vod.seriesVersions(p.id, fresh))
                            if (more.size > versions.value.size) versions.value = more
                        }
                    }
                }
            }
        }
        try {
            catalog.ensureEpisodes(p, primary, force)
        } catch (e: Exception) {
            error.value = e.userMessage()
        } finally {
            loading.value = false
        }
        // autres versions : épisodes chargés une par une (cache 12 h) pour combler les trous
        ordered.filter { it.id != primary }.take(MAX_EXTRA_VERSIONS).forEach { v -> runCatching { catalog.ensureEpisodes(p, v.id, force) } }
    }

    /** Textes et vignettes TMDB d'une saison (une seule requête par saison, en cache 7 jours). */
    fun loadSeason(season: Int) {
        val tm = tmdb.value ?: return
        if (!loadedSeasons.add(season)) return
        viewModelScope.launch {
            val list = runCatching { catalog.tmdbSeason(tm.id, season) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) tmdbEpisodes.value = tmdbEpisodes.value + list.associateBy { it.season to it.episode }
        }
    }

    /** Affiche les épisodes d'une autre version (sans en faire le choix par défaut). */
    fun selectVersion(v: SeriesEntity) = viewModelScope.launch {
        val p = playlist ?: return@launch
        primaryId.value = v.id
        error.value = null
        runCatching { catalog.ensureEpisodes(p, v.id) }.onFailure { error.value = it.userMessage() }
    }

    /** « Toujours utiliser cette version » (appui long) : elle passe en tête aux prochaines ouvertures. */
    fun preferVersion(v: SeriesEntity) = viewModelScope.launch {
        val p = playlist ?: return@launch
        vod.setChoice(p.id, ContentType.SERIES, v.groupKey, v.id)
        preferredId.value = v.id
        selectVersion(v)
    }

    fun toggleFavorite(shownId: String) = viewModelScope.launch {
        val added = playlist?.let { vod.toggleFavorite(it.id, ContentType.SERIES, versions.value.map { v -> v.id }.ifEmpty { listOf(shownId) }, shownId) } ?: false
        if (added) tmdb.value?.id?.let { trakt.addedToList(com.sokoul.iptv.data.trakt.TraktItem(it)) }
    }

    /** Vu / non vu, sur toutes les versions de l'épisode (même saison/numéro). */
    fun setWatched(e: EpisodeEntity, watched: Boolean) = setSeasonWatched(listOf(e), watched)

    fun setSeasonWatched(list: List<EpisodeEntity>, watched: Boolean) = viewModelScope.launch {
        val p = playlist ?: return@launch
        val keys = list.map { it.season to it.episode }.toSet()
        val same = allEpisodes.value.filter { (it.season to it.episode) in keys }.ifEmpty { list }
        same.forEach { e -> library.setWatched(p.id, ContentType.SERIES, e.id, e.seriesId, e.title, watched, e.season, e.episode) }
        if (watched) tmdb.value?.id?.let { t -> trakt.markedWatched(keys.map { (s, n) -> com.sokoul.iptv.data.trakt.TraktItem(t, s, n) }) }
    }

    companion object {
        const val MAX_EXTRA_VERSIONS = 4
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesDetailScreen(nav: NavHostController, id: String) {
    val vm: SeriesDetailViewModel = hiltViewModel()
    val s by vm.series.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val tmdb by vm.tmdb.collectAsStateWithLifecycle()
    val tmdbEpisodes by vm.tmdbEpisodes.collectAsStateWithLifecycle()
    val similar by vm.similar.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val tvdbEpisodes by vm.tvdbEpisodes.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val primaryId by vm.primaryId.collectAsStateWithLifecycle()
    val preferredId by vm.preferredId.collectAsStateWithLifecycle()
    val allEpisodes by vm.allEpisodes.collectAsStateWithLifecycle()
    val episodeRd by vm.episodeRd.collectAsStateWithLifecycle()
    val rdFirst by vm.rdFirst.collectAsStateWithLifecycle()
    val bestIptvEpisode by vm.bestIptvEpisode.collectAsStateWithLifecycle()
    val rankPrefs by vm.rankPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val l = LocalLayout.current
    // épisodes en liste verticale sur téléphone ; ailleurs, rangée sur le fond plein cadre
    val episodeList = l.isCompact
    val memory = rememberFocusMemory()
    var season by rememberSaveable { mutableStateOf<Int?>(null) }
    var episodeMenu by remember { mutableStateOf<EpisodeEntity?>(null) }
    var seasonMenu by remember { mutableStateOf<Int?>(null) }
    var moreMenu by remember { mutableStateOf(false) }
    var versionPicker by remember { mutableStateOf(false) }
    var showSynopsis by remember { mutableStateOf(false) }
    LaunchedEffect(id) { vm.load(id) }

    val series = s
    val pid = vm.playlist?.id
    if (series == null || pid == null) {
        val e = missing
        if (e != null) DetailError(e, onRetry = { vm.load(id, force = true) }, onBack = { nav.popBackStack() }) else DetailSkeleton()
        return
    }
    // progression rattachée à la saison/épisode : un épisode vu dans une version l'est dans toutes
    val bySe = remember(progress, allEpisodes) { EpisodeResolver.progressBySe(progress, allEpisodes) }
    val action = remember(episodes, bySe) { EpisodeResolver.primaryAction(episodes, bySe) }
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val currentSeason = season?.takeIf { it in seasons } ?: action?.episode?.season ?: seasons.firstOrNull()
    LaunchedEffect(currentSeason, tmdb?.id) { currentSeason?.let { vm.loadSeason(it) } }

    val backdrop = tmdb?.backdrop ?: details?.backdrop ?: series.backdrop ?: extras.art?.background
    val synopsis = pickSynopsis(tmdb?.overview, details?.plot ?: series.plot, tmdb?.overviewEn)
    val title = tmdb?.title?.takeIf { it.isNotBlank() } ?: com.sokoul.iptv.data.repo.cleanDisplayTitle(series.title, series.year)
    val seasonEpisodes = remember(episodes, currentSeason, tmdbEpisodes, tvdbEpisodes, backdrop) {
        episodes.filter { it.season == currentSeason }.map { EpisodeMerge.merge(it, tmdbEpisodes[it.season to it.episode], backdrop, tvdbEpisodes[it.season to it.episode]) }
    }
    val playEpisode = { e: EpisodeEntity, fromStart: Boolean ->
        nav.navigateOnce(PlayerRoute(PlayKind.EPISODE, pid, e.id, parentId = e.seriesId, fromStart = fromStart))
    }
    val playEpisodeRd = { e: EpisodeEntity, key: String, fromStart: Boolean ->
        nav.navigateOnce(PlayerRoute(PlayKind.EPISODE, pid, e.id, parentId = e.seriesId, fromStart = fromStart, source = key))
    }
    // Real-Debrid de l'épisode du bouton principal : cherché dès l'ouverture (une requête)
    LaunchedEffect(action?.episode?.id, tmdb?.id, allEpisodes.isEmpty()) { action?.episode?.let { vm.loadEpisodeRd(it.season, it.episode) } }
    val seasonSeen = { n: Int ->
        val list = episodes.filter { it.season == n }
        list.isNotEmpty() && list.all { bySe[it.season to it.episode]?.finished == true }
    }
    val allSeen = episodes.isNotEmpty() && episodes.all { bySe[it.season to it.episode]?.finished == true }
    val shownVersion = versions.firstOrNull { it.id == primaryId }
    val trailer = (tmdb?.trailerYoutubeKey ?: details?.trailer)?.takeIf { it.isNotBlank() }
    val creator = (tmdb?.director ?: details?.director)?.takeIf { it.isNotBlank() }

    val hero = DetailHeroData(
        title = title, logo = tmdb?.logo ?: extras.art?.logo, backdrop = backdrop, poster = tmdb?.poster ?: series.poster,
        rating = tmdb?.rating ?: series.rating,
        facts = listOfNotNull(
            (series.year ?: tmdb?.year)?.let { MetaItem(MetaKind.YEAR, it.toString()) },
            // TMDB connaît souvent plus de saisons que le fournisseur n'en propose : on affiche celles disponibles
            (seasons.size.takeIf { it > 0 } ?: tmdb?.seasons)?.let { MetaItem(MetaKind.SEASONS, if (it > 1) "$it saisons" else "1 saison") },
        ),
        certification = tmdb?.certification, quality = null,
        genres = tmdb?.genres?.joinToString(", ")?.ifEmpty { null } ?: details?.genre ?: series.genre,
        tagline = tmdb?.tagline, synopsis = synopsis, credit = creator?.let { "Création : $it" }, watched = allSeen,
        externalRatings = ratingBadges(extras.ratings), availability = availabilityPills(extras.offers),
        upcoming = extras.nextEpisode?.let { upcomingLabel(it) },
    )
    val primary = action?.let { a ->
        val p = (a as? SeriesAction.Resume)?.progress
        val rd = episodeRd[a.episode.season to a.episode.episode]?.firstOrNull()?.takeIf { (a.episode.season to a.episode.episode) in rdFirst }
        PrimaryAction(
            label = EpisodeResolver.label(a),
            detail = listOfNotNull(EpisodeResolver.hint(a), rd?.let { com.sokoul.iptv.ui.components.SourceLabels.short(it.toSource()) + " · " + com.sokoul.iptv.ui.components.SourceLabels.originShort(com.sokoul.iptv.domain.SourceOrigin.DEBRID) }).joinToString(" · ").ifEmpty { null },
            progress = p?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs },
            onClick = {
                // sinon : la meilleure version de l'abonnement pour cet épisode (une reprise garde l'épisode en cours)
                val iptvEp = bestIptvEpisode[a.episode.season to a.episode.episode]?.takeIf { a !is SeriesAction.Resume } ?: a.episode
                if (rd != null) playEpisodeRd(a.episode, rd.key, a is SeriesAction.Rewatch) else playEpisode(iptvEp, a is SeriesAction.Rewatch)
            },
        )
    }
    val actions = buildList {
        if (action is SeriesAction.Resume) add(SecondaryAction("restart", "Reprendre l'épisode au début", Icons.Default.Replay) { playEpisode(action.episode, true) })
        add(SecondaryAction("fav", if (favorite) "Dans ma liste" else "Ma liste", if (favorite) Icons.Default.Check else Icons.Default.Add) { vm.toggleFavorite(series.id) })
        if (trailer != null) add(SecondaryAction("trailer", "Bande-annonce", Icons.Default.Theaters) { openYoutube(context, trailer) })
        add(SecondaryAction("more", "Plus", Icons.Default.MoreHoriz) { moreMenu = true })
    }

    DetailScaffold(
        nav, hero, primary, primaryLoading = loading && action == null, actions = actions,
        hint = if (l.isTv && (seasons.isNotEmpty() || loading)) "Épisodes" else null,
        memory = memory, onSynopsis = { showSynopsis = true },
    ) {
        error?.let { e ->
            item("error") {
                Row(Modifier.padding(horizontal = l.margin, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e, Modifier.weight(1f, fill = false), color = SokoulColors.Live, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    DetailActionButton(SecondaryAction("retry", "Réessayer", Icons.Default.Refresh) { vm.load(id, force = true) }, Modifier.focusMemory(memory, "retry"))
                }
            }
        }
        if (loading && episodes.isEmpty()) item("loading") { EpisodeShelfSkeleton() }
        if (!loading && episodes.isEmpty() && error == null) {
            item("empty") { Text("Aucun épisode disponible pour le moment.", Modifier.padding(horizontal = l.margin, vertical = 16.dp), color = SokoulColors.TextMuted) }
        }
        if (seasons.isNotEmpty()) {
            detailSection("episodes", if (l.isTv) null else "Épisodes") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeasonTabs(
                        seasons, currentSeason, seasonSeen, memory, onSelect = { season = it }, onLongClick = { seasonMenu = it },
                        modifier = Modifier.weight(1f),
                    )
                    if (versions.size > 1) {
                        DetailActionButton(
                            SecondaryAction("versions", "Version : " + (shownVersion?.let { seriesVersionLabel(it) } ?: "—"), Icons.Default.Translate) { versionPicker = true },
                            Modifier.padding(end = l.margin).focusMemory(memory, "versions"),
                        )
                    }
                }
                if (!episodeList) {
                    EpisodeShelf(
                        currentSeason, seasonEpisodes, { ev -> bySe[ev.entity.season to ev.entity.episode] },
                        currentKey = action?.episode?.let { it.season to it.episode }, memory = memory,
                        onPlay = { playEpisode(it.entity, false) }, onLongClick = { episodeMenu = it.entity }, fallbackImage = backdrop,
                    )
                }
            }
            if (episodeList) {
                items(seasonEpisodes, key = { "ep:" + it.entity.id }) { ev ->
                    val p = bySe[ev.entity.season to ev.entity.episode]
                    EpisodeItem(
                        ev, p, current = action?.episode?.let { it.season == ev.entity.season && it.episode == ev.entity.episode } == true,
                        modifier = Modifier.focusMemory(memory, "ep:${ev.entity.season}:${ev.entity.episode}"),
                        onClick = { playEpisode(ev.entity, false) }, onLongClick = { episodeMenu = ev.entity },
                    )
                }
            }
        }
        castSection(tmdb?.cast.orEmpty(), memory) { p -> nav.navigateOnce(com.sokoul.iptv.ui.nav.PersonRoute(p.id ?: return@castSection, p.name)) }
        if (similar.isNotEmpty()) {
            detailSection("similar", "Vous aimerez aussi", "Des séries dans la même veine, disponibles dans votre abonnement.") {
                CardRow(similar, { it.uid }) { _, w -> WorkCard(w, { nav.openWork(w) }, Modifier.focusMemory(memory, "sim:${w.uid}")) }
            }
        }
    }

    if (moreMenu) {
        OptionsDialog(
            title,
            buildList {
                if (synopsis != null) add(MenuOption("Lire le résumé complet", Icons.Default.Info) { showSynopsis = true })
                if (versions.size > 1) add(MenuOption("Choisir la version…", Icons.Default.Translate) { versionPicker = true })
                currentSeason?.let { n ->
                    val list = episodes.filter { it.season == n }
                    if (seasonSeen(n)) add(MenuOption("Marquer la saison $n comme non vue", Icons.Default.Replay) { vm.setSeasonWatched(list, false) })
                    else add(MenuOption("Marquer la saison $n comme vue", Icons.Default.CheckCircle) { vm.setSeasonWatched(list, true) })
                }
                if (episodes.isNotEmpty()) {
                    if (allSeen) add(MenuOption("Marquer la série comme non vue", Icons.Default.Replay) { vm.setSeasonWatched(episodes, false) })
                    else add(MenuOption("Marquer toute la série comme vue", Icons.Default.CheckCircle) { vm.setSeasonWatched(episodes, true) })
                }
                add(MenuOption("Actualiser les épisodes", Icons.Default.Refresh) { vm.load(id, force = true) })
            },
            subtitle = hero.facts.joinToString(" · ") { it.text }.ifEmpty { null },
            onDismiss = { moreMenu = false },
        )
    }
    if (versionPicker) {
        OptionsDialog(
            "Version",
            versions.map { v ->
                MenuOption(
                    seriesVersionLabel(v) + (if (v.id == preferredId) " ★" else ""), Icons.Default.Translate, selected = v.id == primaryId,
                    hint = vm.qualities.value[v.id]?.line()?.ifEmpty { null },
                ) { vm.selectVersion(v) }
            } + listOfNotNull(
                shownVersion?.takeIf { it.id != preferredId }?.let { v ->
                    MenuOption("Toujours utiliser « ${seriesVersionLabel(v)} »", Icons.Default.Star) { vm.preferVersion(v) }
                },
            ),
            subtitle = "Les épisodes manquants sont complétés par les autres versions.",
            onDismiss = { versionPicker = false },
        )
    }
    seasonMenu?.let { n ->
        val list = episodes.filter { it.season == n }
        OptionsDialog(
            "Saison $n",
            listOf(
                MenuOption("Marquer la saison comme vue", Icons.Default.CheckCircle) { vm.setSeasonWatched(list, true) },
                MenuOption("Marquer la saison comme non vue", Icons.Default.Replay) { vm.setSeasonWatched(list, false) },
            ),
            onDismiss = { seasonMenu = null },
        )
    }
    episodeMenu?.let { e ->
        LaunchedEffect(e.id) { vm.loadEpisodeRd(e.season, e.episode) }
        val rdList = episodeRd[e.season to e.episode].orEmpty()
        val p = bySe[e.season to e.episode]
        val watched = p?.finished == true
        val resumable = p != null && !p.finished && LibraryRepository.resumeFrom(p) > 0
        OptionsDialog(
            "S%02dE%02d · %s".format(e.season, e.episode, EpisodeMerge.cleanTitle(e.title, e.episode)),
            buildList {
                add(MenuOption(if (resumable) "Reprendre" else "Lecture", Icons.Default.PlayArrow) { playEpisode(e, false) })
                if (resumable) add(MenuOption("Depuis le début", Icons.Default.Replay) { playEpisode(e, true) })
                // toutes les versions de l'épisode (abonnement et Real-Debrid classés ensemble), une ligne par langue et qualité
                if (rdList.isNotEmpty()) {
                    val iptvSrc = shownVersion?.let { v -> com.sokoul.iptv.ui.screens.player.PlayerSources.fromIptv(v.toVersion().copy(id = EP_IPTV, measured = vm.qualities.value[v.id]), current = false) }
                    val rdSrc = rdList.map { com.sokoul.iptv.ui.screens.player.PlayerSources.fromDebrid(it, current = false, id = RD_ITEM + it.key) }
                    val ranked = com.sokoul.iptv.data.debrid.SourceRanker.rank(
                        listOfNotNull(shownVersion?.let { v -> v.toVersion().copy(id = EP_IPTV, measured = vm.qualities.value[v.id]) }), rdList, rankPrefs,
                    ).mapNotNull { c -> if (c is com.sokoul.iptv.data.debrid.Candidate.Iptv) iptvSrc else rdSrc.firstOrNull { it.id == RD_ITEM + (c as com.sokoul.iptv.data.debrid.Candidate.Debrid).stream.key } }
                    com.sokoul.iptv.ui.screens.player.SourceCatalog.group(ranked, rankPrefs.languages).forEach { g ->
                        val s = g.best
                        val more = g.sources.size - 1
                        add(MenuOption(
                            // même ligne que sur la fiche d'un film : langue · définition · origine · rip et taille
                            "Lire en " + com.sokoul.iptv.ui.screens.player.PlayerSources.languageName(g.language, g.region) + " · " + com.sokoul.iptv.ui.screens.player.versionTitle(g) +
                                " · " + com.sokoul.iptv.ui.components.SourceLabels.originShort(s.origin) +
                                (com.sokoul.iptv.ui.screens.player.PlayerSources.facts(s)?.let { " · $it" } ?: "") + if (more > 0) " (+$more)" else "",
                            Icons.Default.PlayArrow,
                        ) { if (s.id == EP_IPTV) playEpisode(e, false) else playEpisodeRd(e, s.id.removePrefix(RD_ITEM), false) })
                    }
                }
                add(MenuOption(if (watched) "Marquer comme non vu" else "Marquer comme vu", Icons.Default.CheckCircle) { vm.setWatched(e, !watched) })
            },
            subtitle = e.plot,
            onDismiss = { episodeMenu = null },
        )
    }
    if (showSynopsis && synopsis != null) SynopsisDialog(title, synopsis.text) { showSynopsis = false }
    // retour du lecteur : focus sur l'épisode lancé (ou le bouton principal), jamais perdu
    LaunchedEffect(series.id, action != null) {
        if (action == null && loading) return@LaunchedEffect
        delay(120)
        if (!memory.restore()) runCatching { memory.requester("play").requestFocus() }
    }
}

// --- composants propres aux fiches (le reste est dans DetailKit.kt) -----------------------------------

/** Distribution : visages focalisables ; OK ouvre la page de la personne (sa filmographie dans l'abonnement). */
private fun LazyListScope.castSection(cast: List<TmdbPerson>, memory: com.sokoul.iptv.ui.components.FocusMemory, onPerson: (TmdbPerson) -> Unit) {
    if (cast.isEmpty()) return
    detailSection("cast", "Distribution", "Choisissez un visage pour voir sa filmographie") {
        val l = LocalLayout.current
        LazyRow(
            contentPadding = PaddingValues(start = l.margin, end = l.margin, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(cast, key = { it.id ?: it.name.hashCode() }) { person ->
                Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FocusSurface(
                        onClick = { onPerson(person) }, shape = Radius.Round, style = FocusStyle.Card,
                        modifier = Modifier.focusMemory(memory, "cast:${person.id ?: person.name}"),
                    ) { _ ->
                        NetImage(person.photo, person.name, Modifier.size(84.dp).background(SokoulColors.SurfaceHigher)) {
                            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                                Text(com.sokoul.iptv.ui.components.initials(person.name), color = SokoulColors.TextSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(person.name, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    person.role?.let { Text(it, color = SokoulColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** Ligne d'épisode : vignette 16:9, progression, « Vu », repère de l'épisode à reprendre. */
@Composable
private fun EpisodeItem(ev: EpisodeView, progress: ProgressEntity?, current: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val l = LocalLayout.current
    val thumbW = if (l.isCompact) 136.dp else (l.landscape * 0.9f)
    val watched = progress?.finished == true
    Box(Modifier.padding(horizontal = l.margin, vertical = 5.dp)) {
        FocusSurface(style = FocusStyle.Row,
            onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth().widthIn(max = 1100.dp),
            color = if (current) SokoulColors.AccentTint else Color.Transparent, shape = Radius.Card,
        ) { focused ->
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(thumbW).aspectRatio(16f / 9f).clip(Radius.Card).background(SokoulColors.SurfaceHigher)) {
                        NetImage(tmdbSized(ev.image, "w500"), ev.title, Modifier.fillMaxSize().graphicsLayer { alpha = if (watched && !focused) 0.55f else 1f }) {
                            Icon(Icons.Default.PlayArrow, null, tint = SokoulColors.TextMuted)
                        }
                        if (focused) {
                            Box(Modifier.fillMaxSize().background(SokoulColors.black(0.33f)), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(40.dp).background(SokoulColors.OnVideo, Radius.Round), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, null, tint = SokoulColors.OnLight)
                                }
                            }
                        }
                        if (progress != null && progress.durationMs > 0 && !progress.finished && LibraryRepository.resumeFrom(progress) > 0) {
                            ProgressLine(progress.positionMs.toFloat() / progress.durationMs, Modifier.align(Alignment.BottomStart))
                        }
                        if (watched) {
                            Icon(Icons.Default.CheckCircle, "Vu", Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp), tint = SokoulColors.Success)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("${ev.entity.episode}. ${ev.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(
                            if (current) (if (progress != null && !progress.finished && LibraryRepository.resumeFrom(progress) > 0) "En cours" else "À suivre") else null,
                            ev.runtimeMin?.let { formatRuntime(it) },
                            progress?.takeIf { !it.finished && LibraryRepository.resumeFrom(it) > 0 }?.let { EpisodeResolver.remaining(it) },
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, Modifier.padding(top = 2.dp), color = if (current) SokoulColors.Accent else SokoulColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                        if (!l.isCompact && ev.overview != null) {
                            Text(ev.overview, Modifier.padding(top = 6.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (l.isCompact && ev.overview != null) {
                    Text(ev.overview, Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Fiche introuvable / playlist inactive : message clair et sortie évidente (jamais de chargement infini). */
@Composable
private fun DetailError(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val back = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(SokoulColors.Background), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp).widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Theaters, null, Modifier.size(56.dp), tint = SokoulColors.TextMuted)
            Text("Fiche indisponible", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(message, Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroButton("Retour", Icons.AutoMirrored.Filled.ArrowBack, primary = true, onClick = onBack, modifier = Modifier.focusRequester(back))
                HeroButton("Réessayer", Icons.Default.Refresh, primary = false, onClick = onRetry)
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { back.requestFocus() } }
}

/** Identifiant d'une version Real-Debrid dans l'étagère des versions. */
internal const val RD_ITEM = "rd:"
/** Version IPTV de l'épisode dans le menu d'épisode. */
private const val EP_IPTV = "iptv-episode"

fun versionSource(m: MovieEntity) = com.sokoul.iptv.domain.PlaybackSources.fromIptv(m.toVersion())
fun versionLabel(m: MovieEntity): String = com.sokoul.iptv.ui.components.SourceLabels.text(versionSource(m))
fun seriesVersionLabel(s: SeriesEntity): String = com.sokoul.iptv.ui.components.SourceLabels.text(com.sokoul.iptv.domain.PlaybackSources.fromIptv(s.toVersion())).takeIf { it != "Version" } ?: s.name.take(28)

/** Identifiant YouTube depuis une clé TMDB, une URL watch?v=…, youtu.be/… ou /embed/…. */
fun youtubeId(key: String): String? {
    val k = key.trim()
    if (k.isEmpty()) return null
    val id = when {
        "v=" in k -> k.substringAfter("v=").substringBefore('&').substringBefore('#')
        "youtu.be/" in k -> k.substringAfter("youtu.be/").substringBefore('?').substringBefore('/')
        "/embed/" in k -> k.substringAfter("/embed/").substringBefore('?').substringBefore('/')
        else -> k.substringAfterLast('/').substringBefore('?')
    }
    return id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
}

private fun openYoutube(context: android.content.Context, key: String) {
    val id = youtubeId(key) ?: run {
        android.widget.Toast.makeText(context, "Bande-annonce indisponible", android.widget.Toast.LENGTH_SHORT).show(); return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "Installez YouTube pour voir la bande-annonce", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

// --- sources secondaires -------------------------------------------------------------------------------

/**
 * « Vous aimerez aussi » : recommandations TMDB d'abord ; si elles donnent moins de 6 titres disponibles,
 * complétées par les titres similaires de TasteDive retrouvés dans le catalogue.
 */
private suspend fun moreLikeThis(
    browse: BrowseRepository, extras: ExtrasRepository, pid: Long, type: ContentType, tm: TmdbInfo, own: String, emit: (List<Work>) -> Unit,
) {
    val base = runCatching { browse.similar(pid, type, tm.id, own) }.getOrDefault(emptyList())
    emit(base)
    if (base.size >= ExtrasRepository.MIN_SIMILAR) return
    val names = extras.similarNames(type == ContentType.MOVIE, tm)
    if (names.isEmpty()) return
    val more = runCatching { browse.similarByNames(pid, type, names, base.mapTo(HashSet()) { it.key } + own) }.getOrDefault(emptyList())
    if (more.isNotEmpty()) emit(base + more)
}

/** Notes OMDb pour la ligne d'infos : IMDb, Rotten Tomatoes (RT), Metacritic (MC). */
fun ratingBadges(r: Ratings?): List<Pair<String, String>> =
    listOfNotNull(r?.imdb?.let { "IMDb" to it }, r?.rottenTomatoes?.let { "RT" to it }, r?.metacritic?.let { "MC" to it })

/** Services inclus (abonnement, gratuit, option) seulement : location et achat n'apportent rien sur la fiche. */
fun availabilityPills(offers: List<StreamingOffer>): List<Pair<String, Long?>> =
    offers.filter { it.type == OfferType.SUBSCRIPTION || it.type == OfferType.FREE || it.type == OfferType.ADDON }.map { it.service to it.color }

/** « Prochain épisode : S3 · É4 demain », « … le mardi 12 novembre ». */
fun upcomingLabel(n: NextEpisode, now: java.time.Instant = java.time.Instant.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
    val day = n.airsAt.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    if (day.isBefore(today)) return null
    val whenText = when (day) {
        today -> "aujourd'hui"
        today.plusDays(1) -> "demain"
        else -> "le " + day.format(java.time.format.DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy", java.util.Locale.FRANCE))
    }
    return "Prochain épisode : S${n.season} · É${n.episode} $whenText"
}
