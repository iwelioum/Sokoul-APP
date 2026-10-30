package com.sokoul.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.data.Resume
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fiche plein cadre (jamais en colonnes) : fond TMDB sur toute la fenêtre, logo, repères, résumé, actions ;
 * au défilement : saisons et épisodes, distribution, suggestions. Le fond reste fixe et s'assombrit au défilement.
 */
@Composable
fun DetailScreen(app: AppState, kind: String, id: Int) {
    val info by produceState<TmdbInfo?>(null, kind, id) { value = app.catalog.details(kind, id) }
    val similar by produceState(emptyList<TmdbItem>(), kind, id) { value = app.catalog.similar(kind, id) }
    val list = rememberLazyListState()
    val veil by remember { derivedStateOf { if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / 500f).coerceIn(0f, 1f) } }
    LaunchedEffect(Unit) { snapshotFlow { veil > 0.6f }.collect { app.navSolid = it } }
    val tv = kind == "tv"
    var season by remember(id) { mutableStateOf(1) }
    val episodes by produceState(emptyList<TmdbEpisode>(), id, season, info) {
        value = if (tv && info != null) app.catalog.season(id, season) else emptyList()
    }
    val resume = remember(id, app.dataVersion) {
        if (tv) null else app.db.resume(Resume.key(kind, id, null, null))?.takeIf { !it.finished && it.positionMs > 30_000 }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        val first = maxHeight
        Backdrop(info?.backdrop, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - veil * 0.75f })
        val i = info
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item(key = "head") {
                Box(Modifier.fillMaxWidth().height(maxOf(first * 0.92f, 560.dp))) {
                    if (i == null) Column(Modifier.align(Alignment.BottomStart).padding(start = Space.Gutter, bottom = 60.dp)) {
                        Skeleton(Modifier.width(420.dp).height(120.dp)); VSpace(20.dp); Skeleton(Modifier.width(560.dp).height(80.dp))
                    } else Head(app, kind, i, resume, Modifier.align(Alignment.BottomStart))
                }
            }
            if (tv && i != null) item(key = "seasons") { Seasons(app, i, season, { season = it }, episodes) }
            if (i != null && i.cast.isNotEmpty()) item(key = "cast") { Cast(i) }
            if (similar.isNotEmpty()) item(key = "similar") {
                CardRow("Vous aimerez aussi", Modifier.padding(top = 36.dp)) {
                    items(similar, key = { it.id }) { s -> PosterCard(s, onClick = { app.openDetail(s.mediaType ?: kind, s.id) }) }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun Head(app: AppState, kind: String, i: TmdbInfo, resume: Resume?, modifier: Modifier) {
    val tv = kind == "tv"
    fun title(season: Int? = null, episode: Int? = null) = NowPlaying.Title(
        kind, i.id, i.title, if (season != null) "Saison $season · Épisode $episode" else i.year?.toString(), i.backdrop, season, episode,
    )
    Column(modifier.padding(start = Space.Gutter, end = Space.Gutter, bottom = 56.dp).widthIn(max = 760.dp)) {
        Kicker(if (tv) "Série" else "Film")
        TitleArt(i.title, i.logo, Modifier.padding(top = 14.dp))
        MetaRow(
            year = i.year, runtimeMin = i.runtimeMin.takeIf { !tv }, rating = i.rating, seasons = i.seasons.takeIf { tv },
            certification = i.certification, genres = i.genres, modifier = Modifier.padding(top = 20.dp),
        )
        i.tagline?.let { Text(it, Modifier.padding(top = 18.dp), style = Type.BodyStrong.copy(fontStyle = FontStyle.Italic, color = SokoulColors.TextPrimary)) }
        Text(
            i.overview ?: i.overviewEn ?: "Pas de résumé disponible.", Modifier.padding(top = 10.dp).widthIn(max = 660.dp),
            style = Type.BodyStrong.copy(color = SokoulColors.TextPrimary.copy(alpha = 0.82f)), maxLines = 5, overflow = TextOverflow.Ellipsis,
        )
        listOfNotNull(i.director?.let { (if (tv) "Création : " else "Réalisation : ") + it }, i.cast.take(4).takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }?.let { "Avec $it" })
            .forEach { Text(it, Modifier.padding(top = 6.dp), style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        resume?.let { r ->
            Row(Modifier.padding(top = 18.dp).width(360.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(Radius.Tiny)) { ProgressLine(r.fraction) }
                Text("  ${timecode(r.durationMs - r.positionMs)} restantes", style = Type.Small)
            }
        }
        Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tv) {
                val next = remember(i.id, app.dataVersion) { nextEpisode(app, i.id) }
                SokoulButton(
                    next?.let { "Reprendre S${it.first} É${it.second}" } ?: "Lecture S1 É1", { val (s, e) = next ?: (1 to 1); app.playTitle(title(s, e)) },
                    style = ButtonStyle.Primary, icon = Icons.Filled.PlayArrow, large = true,
                )
                SokoulButton("Sources", { val (s, e) = next ?: (1 to 1); app.sources = title(s, e) }, style = ButtonStyle.Glass, icon = Icons.Outlined.Layers, large = true)
            } else {
                SokoulButton(if (resume != null) "Reprendre" else "Lecture", { app.playTitle(title()) }, style = ButtonStyle.Primary, icon = Icons.Filled.PlayArrow, large = true)
                SokoulButton("Sources", { app.sources = title() }, style = ButtonStyle.Glass, icon = Icons.Outlined.Layers, large = true)
            }
            i.trailerYoutubeKey?.let { key ->
                SokoulButton("Bande-annonce", { openBrowser("https://www.youtube.com/watch?v=$key") }, style = ButtonStyle.Glass, icon = Icons.Outlined.Movie, large = true)
            }
        }
    }
}

/** Épisode à reprendre : le dernier commencé (ou le suivant du dernier vu). */
private fun nextEpisode(app: AppState, tvId: Int): Pair<Int, Int>? {
    val r = app.db.resumeList(100).firstOrNull { it.kind == "tv" && it.tmdbId == tvId } ?: return null
    return (r.season ?: return null) to (r.episode ?: return null)
}

@Composable
private fun Seasons(app: AppState, i: TmdbInfo, season: Int, onSeason: (Int) -> Unit, episodes: List<TmdbEpisode>) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SectionTitle("Épisodes", Modifier.padding(horizontal = Space.Gutter))
        LazyRow(Modifier.padding(top = 14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((1..(i.seasons ?: 1).coerceAtLeast(1)).toList()) { s -> Chip("Saison $s", s == season, { onSeason(s) }) }
        }
        CardRow("", Modifier.padding(top = 4.dp)) {
            if (episodes.isEmpty()) items(6) { Skeleton(Modifier.width(320.dp).aspectRatio(16f / 9f)) }
            items(episodes, key = { "${it.season}:${it.episode}" }) { e ->
                val key = Resume.key("tv", i.id, e.season, e.episode)
                val r = remember(key, app.dataVersion) { app.db.resume(key) }
                EpisodeCard(e, r) {
                    app.playTitle(NowPlaying.Title("tv", i.id, i.title, "S${e.season} · É${e.episode}${e.name?.let { " · $it" }.orEmpty()}", e.still ?: i.backdrop, e.season, e.episode))
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(e: TmdbEpisode, r: Resume?, onClick: () -> Unit) {
    Column(Modifier.width(320.dp)) {
        LandscapeCard(
            "${e.episode}. ${e.name ?: "Épisode ${e.episode}"}", e.still, onClick, width = 320.dp,
            subtitle = e.runtimeMin?.let(::duration),
            progress = r?.let { if (it.finished) 1f else it.fraction }?.takeIf { it > 0f },
        )
        e.overview?.let { Text(it, Modifier.padding(top = 4.dp), style = Type.Small, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Cast(i: TmdbInfo) {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
        SectionTitle("Distribution", Modifier.padding(horizontal = Space.Gutter))
        LazyRow(Modifier.padding(top = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.Gutter), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(i.cast) { p ->
                Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NetImage(p.photo, p.name, Modifier.size(104.dp).clip(Radius.Round).border(Edge.Hairline, SokoulColors.Divider, Radius.Round)) {
                        Placeholder(p.name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), Modifier.fillMaxSize())
                    }
                    Text(p.name, Modifier.padding(top = 10.dp), style = Type.Small.copy(color = SokoulColors.TextPrimary), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    p.role?.let { Text(it, style = Type.Small.copy(color = SokoulColors.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
    }
}

fun openBrowser(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

/** Fenêtre Sources : versions Real-Debrid classées ; un clic lance celle-ci. */
@Composable
fun SourcesDialog(app: AppState, t: NowPlaying.Title) {
    val result by produceState<com.sokoul.desktop.data.DebridResult?>(null, t.query) {
        value = withContext(Dispatchers.Default) { app.debrid.find(t.query, app.catalog.details(t.kind, t.tmdbId)) }
    }
    SokoulDialog({ app.sources = null }, width = 820.dp) {
        Kicker("Real-Debrid")
        Text(t.title, Modifier.padding(top = 6.dp), style = Type.Section)
        t.subtitle?.let { Text(it, style = Type.Small) }
        val r = result
        Box(Modifier.fillMaxWidth().padding(top = 18.dp).height(520.dp)) {
            when {
                r == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Recherche des versions sur Torrentio et Comet…", style = Type.Small)
                    repeat(6) { Skeleton(Modifier.fillMaxWidth().height(58.dp)) }
                }
                r.streams.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("Aucune version", r.problem ?: "Aucune version trouvée sur Real-Debrid")
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(r.streams, key = { it.key }) { s -> SourceRow(s, first = s == r.streams.first()) { app.playTitle(t, s) } }
                }
            }
        }
        r?.let { Text("${it.streams.size} versions · classées par taille lisible, langue, image (SDR d'abord), définition et origine du fichier", Modifier.padding(top = 12.dp), style = Type.Small.copy(color = SokoulColors.TextMuted)) }
    }
}

@Composable
private fun SourceRow(s: com.sokoul.iptv.data.debrid.DebridStream, first: Boolean, onClick: () -> Unit) {
    val h = rememberHover()
    val rel = s.release
    Row(
        Modifier.fillMaxWidth().background(if (h.active) SokoulColors.SurfaceHigher else SokoulColors.Surface, Radius.Card).focusRing(h)
            .interactive(h, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(250.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val q = when (rel.quality) {
                com.sokoul.iptv.domain.VideoQuality.UHD_4K -> "4K"; com.sokoul.iptv.domain.VideoQuality.FHD_1080 -> "1080p"
                com.sokoul.iptv.domain.VideoQuality.HD_720 -> "720p"; com.sokoul.iptv.domain.VideoQuality.SD -> "SD"; null -> "?"
            }
            Badge(q, if (rel.quality == com.sokoul.iptv.domain.VideoQuality.UHD_4K) SokoulColors.Accent else SokoulColors.SurfaceHigher)
            rel.hdr?.let { Badge(if (it == com.sokoul.iptv.domain.HdrFormat.DOLBY_VISION) "DOLBY VISION" else "HDR10", SokoulColors.Gold, SokoulColors.OnLight) }
            rel.language?.let { Badge(it.name, SokoulColors.AccentSoft) }
            rel.source?.let { Badge(it.label.uppercase()) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(s.title, style = Type.Small.copy(color = SokoulColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(if (first) "Meilleure version" else null, rel.frenchVariant, rel.codec, rel.seeders?.let { "$it sources" }).joinToString(" · "),
                style = Type.Small.copy(color = if (first) SokoulColors.Success else SokoulColors.TextMuted), maxLines = 1,
            )
        }
        Text(gb(rel.sizeBytes) ?: "", style = Type.Meta)
        HSpace(12.dp)
        androidx.compose.material3.Icon(Icons.Filled.PlayArrow, "Lire", tint = if (h.active) SokoulColors.TextPrimary else SokoulColors.TextMuted)
    }
}
