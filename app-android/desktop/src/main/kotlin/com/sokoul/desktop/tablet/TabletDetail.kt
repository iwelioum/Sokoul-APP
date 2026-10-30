package com.sokoul.desktop.tablet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.data.Resume
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.ui.components.AgeBadge
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.MIN_RATING
import com.sokoul.iptv.ui.components.MetaItem
import com.sokoul.iptv.ui.components.MetaKind
import com.sokoul.iptv.ui.components.MetaRow
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.ProgressLine
import com.sokoul.iptv.ui.components.RatingChip
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.SourceBadge
import com.sokoul.iptv.ui.components.SourceLabels
import com.sokoul.iptv.ui.components.TitleArt
import com.sokoul.iptv.ui.components.TouchTopBar
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.buttonContent
import com.sokoul.iptv.ui.components.buttonRest
import com.sokoul.iptv.ui.components.formatRuntime
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Motion
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.TitleSize
import com.sokoul.iptv.ui.theme.Type
import com.sokoul.desktop.data.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val KB_MAX = 1.08f
private const val KB_MS = 26_000

/**
 * Fiche film ou série de la tablette (`DetailScaffold`), plein cadre : fond Ken Burns, logo, accroche, repères avec
 * notes (TMDB, IMDb, RT, MC), résumé, « Lecture · MULTI · 4K HDR10 », Ma liste, Bande-annonce, Plus ; puis versions
 * disponibles, épisodes, distribution et « Vous aimerez aussi ». Le premier écran laisse dépasser la suite (paysage).
 */
@Composable
fun TabletDetail(app: AppState, data: TabletData, kind: String, id: Int) {
    val l = LocalLayout.current
    val density = LocalDensity.current
    val tv = kind == "tv"
    val info by produceState<TmdbInfo?>(null, kind, id) { value = app.catalog.details(kind, id) }
    val similar by produceState(emptyList<TmdbItem>(), kind, id) { value = app.catalog.similar(kind, id) }
    val ratings by produceState(emptyList<Pair<String, String>>(), info?.imdbId) { value = data.externalRatings(info?.imdbId) }
    var season by remember(id) { mutableIntStateOf(1) }
    val resume by produceState<Resume?>(null, id, app.dataVersion) {
        value = withContext(Dispatchers.IO) { if (tv) app.db.lastOf("tv", id) else app.db.resume(Resume.key(kind, id, null, null)) }
    }
    // épisode à reprendre : le dernier lu, ou le suivant s'il est fini
    val next = resume?.let { r -> if (tv && r.season != null && r.episode != null) r.season to (if (r.finished) r.episode + 1 else r.episode) else null }
    val q = if (tv) DebridQuery(id, true, next?.first ?: 1, next?.second ?: 1) else DebridQuery(id, false)
    val versions by produceState<List<VersionGroup>?>(null, q, info) {
        val i = info ?: return@produceState
        value = VersionGroup.of(app.debrid.find(q, i).streams)
    }
    val episodes by produceState(emptyList<TmdbEpisode>(), id, season, info) { value = if (tv && info != null) app.catalog.season(id, season) else emptyList() }
    val inList = remember(id, app.dataVersion) { app.inMyList(kind, id) }

    val listState = rememberLazyListState()
    // essais (SOKOUL_ROUTE detail:…:<n>) : fiche défilée jusqu'à la section n
    LaunchedEffect(Unit) { com.sokoul.desktop.TrialRoute.scrollItem?.let { kotlinx.coroutines.delay(5_000); listState.scrollToItem(it) } }
    val heroHeight = (l.heightDp * 0.74f).dp
    val heroPx = with(density) { heroHeight.toPx() }
    val depth by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / (heroPx * 0.5f)).coerceIn(0f, 1f) } }
    val kb = remember(info?.backdrop) { Animatable(1f) }
    LaunchedEffect(kb, depth > 0f) { if (depth == 0f && kb.value < KB_MAX) kb.animateTo(KB_MAX, tween(((KB_MAX - kb.value) / (KB_MAX - 1f) * KB_MS).toInt().coerceAtLeast(1), easing = LinearEasing)) }

    fun title(s: Int? = null, e: Int? = null, ep: TmdbEpisode? = null) = NowPlaying.Title(
        kind, id, info?.title ?: "", if (s != null) "S$s · É$e${ep?.name?.let { " · $it" }.orEmpty()}" else info?.year?.toString(), ep?.still ?: info?.backdrop, s, e,
    )

    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        DetailBackdrop(info?.backdrop, info?.poster, kb, { depth })
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 72.dp)) {
            item("hero") {
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    val i = info
                    Column(Modifier.align(Alignment.BottomStart).padding(start = l.margin, end = l.margin, bottom = 36.dp)) {
                        if (i == null) {
                            Box(Modifier.size(420.dp, 110.dp).shimmer()); Spacer(Modifier.height(16.dp)); Box(Modifier.size(520.dp, 70.dp).shimmer())
                        } else HeroBlock(
                            app, i, kind, ratings, versions, resume, next, inList,
                            onPlay = { if (tv) app.playTitle(title(next?.first ?: 1, next?.second ?: 1)) else app.playTitle(title()) },
                            onSources = { app.sources = if (tv) title(next?.first ?: 1, next?.second ?: 1) else title() },
                        )
                    }
                }
            }
            item("versions") {
                Column {
                    val v = versions
                    val count = v?.sumOf { it.streams.size } ?: 0
                    RowHeader(
                        "Versions disponibles",
                        when {
                            v == null -> "Recherche des versions sur Real-Debrid…"
                            count == 0 -> "Aucune version trouvée sur Real-Debrid pour le moment"
                            else -> "$count sources trouvées · RD $count · cliquez pour lire, clic droit : toutes les sources"
                        },
                    )
                    if (v == null) Row(Modifier.padding(start = l.margin, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { Box(Modifier.size(244.dp, 118.dp).shimmer()) } }
                    else if (v.isNotEmpty()) VersionsShelf(v) { g ->
                        app.playTitle(if (tv) title(next?.first ?: 1, next?.second ?: 1) else title(), g.best)
                    }
                }
            }
            if (tv && info != null) item("episodes") {
                Column(Modifier.padding(top = l.rowSpacing)) {
                    RowHeader("Épisodes", "Saison $season · ${episodes.size} épisodes")
                    SeasonTabs((info!!.seasons ?: 1).coerceAtLeast(1), season) { season = it }
                    CardRow(episodes, { "${it.season}:${it.episode}" }) { _, e ->
                        val key = Resume.key("tv", id, e.season, e.episode)
                        val r by produceState<Resume?>(null, key, app.dataVersion) { value = withContext(Dispatchers.IO) { app.db.resume(key) } }
                        EpisodeCard(e, r, current = next == e.season to e.episode) { app.playTitle(title(e.season, e.episode, e)) }
                    }
                }
            }
            if (info?.cast?.isNotEmpty() == true) item("cast") {
                Column(Modifier.padding(top = l.rowSpacing)) {
                    RowHeader("Distribution", "Les visages de ${if (tv) "la série" else "ce film"}")
                    LazyRow(contentPadding = PaddingValues(horizontal = l.margin, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(info!!.cast) { p ->
                            Column(Modifier.width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                NetImage(p.photo, p.name, Modifier.size(112.dp).clip(Radius.Round).background(SokoulColors.SurfaceHigh, Radius.Round)) {
                                    Text(com.sokoul.iptv.ui.components.initials(p.name), style = MaterialTheme.typography.titleLarge, color = SokoulColors.TextSecondary)
                                }
                                Text(p.name, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                p.role?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                            }
                        }
                    }
                }
            }
            if (similar.isNotEmpty()) item("similar") {
                Column(Modifier.padding(top = l.rowSpacing)) {
                    RowHeader("Vous aimerez aussi", "Dans la même veine.")
                    CardRow(similar, { it.id }) { _, s -> WorkCard(s.toWork(s.mediaType ?: kind, Catalog::genreName), { app.openDetail(s.mediaType ?: kind, s.id) }) }
                }
            }
        }
        TouchTopBar(onBack = { app.back() }, scrimProvider = { (depth * 6f).coerceAtMost(1f) })
    }
}

/** Fond plein cadre de la fiche : Ken Burns, dégradés de lisibilité, assombri quand on descend. */
@Composable
private fun DetailBackdrop(img: String?, poster: String?, kb: Animatable<Float, *>, depthProvider: () -> Float) {
    val l = LocalLayout.current
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = 1f - 0.7f * depthProvider()
            scaleX = kb.value; scaleY = kb.value
        }
    ) {
        if (img != null) NetImage(com.sokoul.iptv.data.repo.tmdbSized(img, "w1280"), null, Modifier.fillMaxSize())
        else if (poster != null) {
            NetImage(poster, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f; renderEffect = androidx.compose.ui.graphics.BlurEffect(60f, 60f, androidx.compose.ui.graphics.TileMode.Clamp) })
            NetImage(poster, null, Modifier.align(Alignment.CenterEnd).padding(end = l.margin * 2).fillMaxHeight(0.72f).aspectRatio(2f / 3f).clip(Radius.Panel))
        }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val left = Brush.horizontalGradient(0f to SokoulColors.Background, 0.34f to SokoulColors.Background.copy(alpha = 0.86f), 0.68f to Color.Transparent)
                val bottom = Brush.verticalGradient(0.42f to Color.Transparent, 1f to SokoulColors.Background)
                onDrawBehind { drawRect(left); drawRect(bottom) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroBlock(
    app: AppState, i: TmdbInfo, kind: String, ratings: List<Pair<String, String>>, versions: List<VersionGroup>?,
    resume: Resume?, next: Pair<Int, Int>?, inList: Boolean, onPlay: () -> Unit, onSources: () -> Unit,
) {
    val l = LocalLayout.current
    val tv = kind == "tv"
    val tw = Modifier.widthIn(max = (l.widthDp * 0.5f).dp)
    Box(tw) {
        if (i.logo != null) {
            NetImage(
                com.sokoul.iptv.data.repo.tmdbSized(i.logo, "w500"), i.title, Modifier.heightIn(max = 120.dp).widthIn(max = 440.dp),
                ContentScale.Fit, alignment = Alignment.BottomStart,
            ) { TitleArt(i.title, i.genres, TitleSize.Detail, Modifier.widthIn(max = (l.widthDp * 0.5f).dp), maxLines = 2) }
        } else TitleArt(i.title, i.genres, TitleSize.Detail, Modifier.widthIn(max = (l.widthDp * 0.5f).dp), maxLines = 2)
    }
    i.tagline?.let {
        Text(it, tw.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic, color = SokoulColors.TextPrimary.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val best = versions?.firstOrNull()?.best?.toSource()
    val facts = listOfNotNull(
        i.year?.let { MetaItem(MetaKind.YEAR, it.toString()) },
        if (tv) i.seasons?.let { MetaItem(MetaKind.SEASONS, if (it > 1) "$it saisons" else "1 saison") } else formatRuntime(i.runtimeMin)?.let { MetaItem(MetaKind.DURATION, it) },
        i.runtimeMin?.takeIf { !tv && it > 0 }?.let { MetaItem(MetaKind.ENDS, com.sokoul.iptv.ui.util.endsAt(it)) },
        best?.language?.let { MetaItem(MetaKind.LANGUAGE, it.name) },
    )
    MetaRow(
        facts, tw.padding(top = 12.dp), certification = i.certification, genres = i.genres.take(4),
        lead = {
            if (i.rating != null && i.rating!! >= MIN_RATING) RatingChip(i.rating!!)
            ratings.forEach { (s, v) -> ExternalRating(s, v) }
        },
        trail = {
            best?.let { b -> SourceBadge(listOfNotNull(b.quality?.let(SourceLabels::quality), b.hdr?.let(SourceLabels::hdr), "Real-Debrid").joinToString(" · "), filled = false) }
            if (resume?.finished == true && !tv) Text("✓ Vu", style = MaterialTheme.typography.labelLarge, color = SokoulColors.Success)
        },
    )
    (i.overview ?: i.overviewEn)?.let { s ->
        Text(s, tw.padding(top = 14.dp), style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextPrimary.copy(alpha = 0.9f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (i.overview == null) Text("Résumé disponible en anglais uniquement", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = SokoulColors.TextMuted)
    }
    FlowRow(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val label = when {
            tv && next != null -> "Reprendre S${next.first} · É${next.second}"
            tv -> "Lecture S1 · É1"
            resume != null && !resume.finished && resume.positionMs > 30_000 -> "Reprendre"
            else -> "Lecture"
        } + (best?.let { " · " + SourceLabels.short(it) } ?: "")
        val progress = resume?.takeIf { !it.finished && it.positionMs > 30_000 }
        DetailPrimaryButton(label, progress?.fraction, progress?.let { "reste " + TabletData.remaining(it.durationMs - it.positionMs) }, onPlay)
        DetailActionButton(if (inList) "Dans ma liste" else "Ma liste", if (inList) Icons.Default.Check else Icons.Default.Add) { app.toggleMyList(kind, i.id) }
        i.trailerYoutubeKey?.let { k -> DetailActionButton("Bande-annonce", Icons.Default.Movie) { com.sokoul.desktop.ui.openBrowser("https://www.youtube.com/watch?v=$k") } }
        DetailActionButton("Plus", Icons.Default.MoreHoriz, onSources)
    }
    val credit = i.director?.let { (if (tv) "Création : " else "Réalisation : ") + it }
    credit?.let { Text(it, tw.padding(top = 14.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Note externe : « IMDb 8,7 » (fond jaune IMDb), « RT 73 % », « MC 74 ». */
@Composable
private fun ExternalRating(source: String, value: String) {
    val imdb = source == "IMDb"
    Row(
        Modifier.then(if (imdb) Modifier.background(SokoulColors.Imdb, Radius.Tiny) else Modifier.border(1.dp, SokoulColors.TextMuted, Radius.Tiny))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(source, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = if (imdb) SokoulColors.OnLight else SokoulColors.TextSecondary)
        Text(value, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (imdb) SokoulColors.OnLight else SokoulColors.TextPrimary)
    }
}

/** Bouton principal blanc : « ▶ Lecture · MULTI · 4K HDR10 », progression sous le libellé. */
@Composable
private fun DetailPrimaryButton(label: String, progress: Float?, detail: String?, onClick: () -> Unit) {
    FocusSurface(style = FocusStyle.Chip, onClick = onClick, shape = Radius.Card, color = buttonRest(com.sokoul.iptv.ui.components.ButtonKind.Primary, false), focusedColor = SokoulColors.Focus) { _ ->
        val c = buttonContent(true)
        Row(Modifier.height(54.dp).padding(start = 18.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(28.dp), tint = c)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, color = c, style = Type.Button, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress != null || detail != null) Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (progress != null) { ProgressLine(progress, Modifier.width(72.dp), thickness = 3.dp, track = c.copy(alpha = 0.28f)); Spacer(Modifier.width(8.dp)) }
                    detail?.let { Text(it, color = c.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }
        }
    }
}

/** Action secondaire en verre, libellé toujours visible (souris : pas de focus permanent). */
@Composable
private fun DetailActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    FocusSurface(style = FocusStyle.Chip, onClick = onClick, shape = Radius.Pill, color = buttonRest(com.sokoul.iptv.ui.components.ButtonKind.Glass, true), focusedColor = SokoulColors.Focus) { focused ->
        val c = buttonContent(focused)
        Row(Modifier.height(54.dp).widthIn(min = 54.dp).animateContentSize(Motion.enter()).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = c)
            AnimatedVisibility(true, enter = fadeIn(Motion.enter()) + expandHorizontally(Motion.enter()), exit = fadeOut(Motion.exit()) + shrinkHorizontally(Motion.exit())) {
                Text(label, Modifier.padding(start = 8.dp), color = c, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Étagère des versions de la tablette : une section par langue, une carte par définition (« 4K · HDR10 »). */
@Composable
private fun VersionsShelf(groups: List<VersionGroup>, onPlay: (VersionGroup) -> Unit) {
    val l = LocalLayout.current
    val byLanguage = groups.groupBy { it.language }
    LazyRow(contentPadding = PaddingValues(horizontal = l.margin, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        var gi = 0
        byLanguage.forEach { (lang, list) ->
            val first = gi++ == 0
            item(key = "g:$lang") {
                Row(Modifier.height(118.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!first) Box(Modifier.padding(end = 14.dp).width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(SokoulColors.white(0.12f)))
                    Column(Modifier.width(118.dp)) {
                        Text(lang?.name ?: "Autres", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(VersionGroup.explain(lang), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 3)
                    }
                }
            }
            items(list.take(6), key = { "${it.language}:${it.quality}:${it.hdr}" }) { g ->
                val firstCard = g == groups.first()
                FocusSurface(style = FocusStyle.Chip, onClick = { onPlay(g) }, modifier = Modifier.width(244.dp).height(118.dp), shape = Radius.Card, color = if (firstCard) SokoulColors.AccentSoft else SokoulColors.SurfaceHigh) { focused ->
                    val ink = if (focused) SokoulColors.OnLight else SokoulColors.TextPrimary
                    val soft = if (focused) SokoulColors.OnLight.copy(alpha = 0.72f) else SokoulColors.TextSecondary
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (firstCard) Icon(Icons.Default.PlayArrow, "Lancée par Lecture", Modifier.size(20.dp), tint = if (focused) SokoulColors.OnLight else SokoulColors.Accent)
                        }
                        g.note?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SourceBadge("RD", filled = true, onLight = focused)
                            Spacer(Modifier.weight(1f))
                            val more = g.streams.size - 1
                            if (more > 0) Text("+$more", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = soft, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Onglets de saisons (pastilles de la tablette). */
@Composable
private fun SeasonTabs(count: Int, selected: Int, onSelect: (Int) -> Unit) {
    val l = LocalLayout.current
    LazyRow(contentPadding = PaddingValues(horizontal = l.margin, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items((1..count).toList()) { s -> com.sokoul.iptv.ui.components.SokoulChip("Saison $s", { onSelect(s) }, selected = s == selected) }
    }
}

/** Carte d'épisode : vignette 16:9, numéro et titre, durée, résumé ; progression et « en cours ». */
@Composable
private fun EpisodeCard(e: TmdbEpisode, r: Resume?, current: Boolean, onClick: () -> Unit) {
    val l = LocalLayout.current
    Column(Modifier.width(l.landscape)) {
        FocusSurface(onClick = onClick, shape = Radius.Card) { _ ->
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                NetImage(com.sokoul.iptv.data.repo.tmdbSized(e.still, "w780"), e.name, Modifier.fillMaxSize())
                if (current) Text(
                    "EN COURS", Modifier.align(Alignment.TopStart).padding(8.dp).background(SokoulColors.Accent, Radius.Tiny).padding(horizontal = 6.dp, vertical = 1.dp),
                    style = Type.Badge, color = SokoulColors.OnVideo,
                )
                val f = r?.let { if (it.finished) 1f else it.fraction }
                if (f != null && f > 0f) ProgressLine(f, Modifier.align(Alignment.BottomStart).fillMaxWidth())
            }
        }
        Text("${e.episode}. ${e.name ?: "Épisode ${e.episode}"}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(e.runtimeMin?.let(::formatRuntime), e.overview).joinToString(" · "), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
