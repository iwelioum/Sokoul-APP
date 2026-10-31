package com.sokoul.desktop.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.NowPlaying
import com.sokoul.desktop.data.CuratedSagas
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.data.SagaFilm
import com.sokoul.desktop.data.SagaOrder
import com.sokoul.iptv.data.repo.SagaLogic
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.ui.components.ButtonKind
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.TouchTopBar
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.buttonContent
import com.sokoul.iptv.ui.components.buttonRest
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Ce que la page d'une saga charge : films (collections réunies), séries de l'univers, logo, fond, résumé. */
private data class SagaPage(
    val name: String, val films: List<SagaFilm>, val chronological: List<Int>, val series: List<Work>,
    val logo: String?, val backdrop: String?, val overview: String?,
)

/**
 * Page d'une saga, comme la tablette : fond plein cadre, kicker « SAGA », logo, repères, « Commencer · ordre de
 * sortie » (ou « Continuer »), choix « Ordre de sortie » / « Ordre du récit », « Prochain : … », frise numérotée
 * par année, puis les séries du même univers. Les grandes franchises viennent de `curated_sagas.json`.
 */
@Composable
fun TabletSaga(app: AppState, id: Int) {
    val l = LocalLayout.current
    val page by produceState<SagaPage?>(null, id) { value = loadSaga(app, id) }
    var narrative by remember(id) { mutableStateOf(com.sokoul.desktop.TrialRoute.sagaNarrative.also { com.sokoul.desktop.TrialRoute.sagaNarrative = false }) }
    val finished = remember(id, app.dataVersion) { app.db.finishedMovies() }
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        val p = page
        if (p?.backdrop != null) {
            NetImage(com.sokoul.iptv.data.repo.tmdbSized(p.backdrop, "w1280"), null, Modifier.fillMaxWidth().height((l.heightDp * 0.62f).dp))
            Box(
                Modifier.fillMaxWidth().height((l.heightDp * 0.62f).dp).background(
                    Brush.horizontalGradient(0f to SokoulColors.Background, 0.45f to SokoulColors.Background.copy(alpha = 0.7f), 0.8f to Color.Transparent),
                ),
            )
            Box(Modifier.fillMaxWidth().height((l.heightDp * 0.62f).dp).background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to SokoulColors.Background)))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 64.dp, bottom = 48.dp)) {
            if (p == null) item("wait") {
                Column(Modifier.padding(horizontal = l.margin)) {
                    Box(Modifier.size(320.dp, 140.dp).shimmer()); Spacer(Modifier.height(16.dp)); Box(Modifier.size(560.dp, 24.dp).shimmer())
                }
            } else {
            val ordered = SagaOrder.ordered(p.films, p.chronological, narrative)
            val next = SagaOrder.next(ordered, finished)
            item("head") {
                Column(Modifier.padding(horizontal = l.margin)) {
                    Text("SAGA", style = Type.SagaKicker)
                    if (p.logo != null) NetImage(
                        com.sokoul.iptv.data.repo.tmdbSized(p.logo, "w500"), p.name, Modifier.padding(top = 6.dp).heightIn(max = 190.dp).widthIn(max = 440.dp),
                        ContentScale.Fit, alignment = Alignment.BottomStart,
                    ) { Text(p.name, style = Type.ScreenTitle) } else Text(p.name, Modifier.padding(top = 4.dp), style = Type.ScreenTitle)
                    Text(SagaOrder.facts(p.films, p.series.size), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, color = SokoulColors.TextSecondary)
                    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val inProgress = ordered.firstOrNull { f -> app.db.resume(Resume.key("movie", f.id, null, null))?.let { !it.finished && it.positionMs > 30_000 } == true }
                        val target = inProgress ?: next ?: ordered.firstOrNull()
                        val label = when {
                            inProgress != null -> "Continuer · ${inProgress.title}"
                            finished.none { f -> p.films.any { it.id == f } } -> "Commencer · " + if (narrative) "ordre du récit" else "ordre de sortie"
                            next != null -> "Regarder · ${next.title}"
                            else -> "Revoir depuis le début"
                        }
                        target?.let { t ->
                            FocusSurface(style = FocusStyle.Chip, onClick = {
                                app.playTitle(NowPlaying.Title("movie", t.id, t.title, t.year?.toString(), t.backdrop))
                            }, shape = Radius.Card, color = buttonRest(ButtonKind.Primary, false), focusedColor = SokoulColors.Focus) { _ ->
                                val c = buttonContent(true)
                                Row(Modifier.height(54.dp).padding(start = 18.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(26.dp), tint = c)
                                    Text(label, Modifier.padding(start = 8.dp), color = c, style = Type.Button, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (p.chronological.isNotEmpty()) {
                            OrderChip("Ordre de sortie", !narrative) { narrative = false }
                            OrderChip("Ordre du récit", narrative) { narrative = true }
                        }
                        next?.let { Text("Prochain : ${it.title}" + (it.year?.let { y -> " ($y)" } ?: ""), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            item("films") {
                Column(Modifier.padding(top = 40.dp)) {
                    Text("Les films", Modifier.padding(horizontal = l.margin), style = Type.Section)
                    p.overview?.takeIf { it.isNotBlank() }?.let {
                        Text(it, Modifier.padding(horizontal = l.margin).padding(top = 6.dp).widthIn(max = 900.dp), style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = l.margin, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        itemsIndexed(ordered, key = { _, f -> f.id }) { i, f -> TimelineCard(i + 1, f, f.id in finished, i == ordered.lastIndex) { app.openDetail("movie", f.id) } }
                    }
                }
            }
            if (p.series.isNotEmpty()) item("series") {
                Column(Modifier.padding(top = l.rowSpacing)) {
                    RowHeader("Séries", null, kicker = "DANS LE MÊME UNIVERS")
                    CardRow(p.series, { it.uid }) { _, w -> WorkCard(w, { open(app, w) }) }
                }
            }
            }
        }
        TouchTopBar(onBack = { app.back() }, scrimProvider = { 0f })
    }
}

@Composable
private fun OrderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(style = FocusStyle.Chip, onClick = onClick, shape = Radius.Card, color = if (selected) SokoulColors.AccentSoft else SokoulColors.SurfaceHigh) { f ->
        Text(label, Modifier.padding(horizontal = 20.dp, vertical = 14.dp), style = MaterialTheme.typography.titleSmall, color = if (f) SokoulColors.OnLight else SokoulColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

/** Étape de la frise : « 01  1977 », point sur la ligne du temps, affiche, titre ; ✓ quand le film est vu. */
@Composable
private fun TimelineCard(n: Int, f: SagaFilm, seen: Boolean, last: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(232.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(n), style = MaterialTheme.typography.labelLarge, color = SokoulColors.Gold, fontWeight = FontWeight.Bold)
            Text(f.year?.toString() ?: "", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (seen) Text("  ✓ Vu", style = MaterialTheme.typography.labelMedium, color = SokoulColors.Success)
        }
        Box(Modifier.padding(top = 10.dp, bottom = 12.dp).fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
            if (!last) Box(Modifier.fillMaxWidth().height(2.dp).background(SokoulColors.white(0.18f)))
            Box(Modifier.size(14.dp).background(if (seen) SokoulColors.Accent else SokoulColors.Background, Radius.Round).border(2.dp, if (seen) SokoulColors.Accent else SokoulColors.TextPrimary, Radius.Round))
        }
        Box(Modifier.padding(end = 20.dp)) {
            WorkCard(Work(com.sokoul.iptv.data.db.ContentType.MOVIE, f.id.toString(), "tmdb:${f.id}", f.title, f.year, f.poster, f.backdrop, tmdbId = f.id), onClick)
        }
    }
}

private suspend fun loadSaga(app: AppState, id: Int): SagaPage? = coroutineScope {
    val k = app.catalog.key()
    val t = app.catalog.tmdb
    val curated = CuratedSagas.forCollection(id)
    val cols = (curated?.collectionIds ?: listOf(id)).map { cid -> async { t.collection(k, cid) } }.mapNotNull { it.await() }
    val main = cols.firstOrNull { it.id == id } ?: cols.firstOrNull() ?: return@coroutineScope null
    val parts = cols.flatMap { it.parts }.map { SagaFilm(it.id, it.title, it.year, it.poster, it.backdrop, it.rating) }
    val extras = curated?.extraMovieIds.orEmpty().filter { e -> parts.none { it.id == e } }
        .map { e -> async { t.details(k, "movie", e, full = false)?.let { SagaFilm(it.id, it.title, it.year, it.poster, it.backdrop, it.rating) } } }
        .mapNotNull { it.await() }
    val series = curated?.relatedSeriesTmdbIds.orEmpty().map { s -> async { t.details(k, "tv", s, full = false)?.toWork("tv") } }.mapNotNull { it.await() }
    val logo = SagaLogic.logoPart(main.name, main.parts)?.let { lid ->
        val en = SagaLogic.englishLogo(main.name, main.parts, lid)
        t.details(k, "movie", lid, full = false)?.let { if (en) it.logoEn ?: it.logo else it.logo }
    }
    SagaPage(
        curated?.name ?: SagaLogic.displayName(main.name), (parts + extras).distinctBy { it.id },
        curated?.chronologicalOrderIds.orEmpty(), series, logo, main.backdrop, main.overview,
    )
}
