package com.sokoul.desktop.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.AppState
import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.data.Program
import com.sokoul.iptv.ui.util.dayLabel
import com.sokoul.iptv.ui.util.formatRange
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.toWork
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.ChannelLogo
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.FormField
import com.sokoul.iptv.ui.components.LandscapeCard
import com.sokoul.iptv.ui.components.RankLandscapeCard
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.SokoulChip
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Périmètre de la recherche de la tablette (pastilles sous le champ). */
enum class SearchKind(val label: String) { MOVIES("Films"), SERIES("Séries"), CHANNELS("Chaînes"), PROGRAMS("Programmes TV") }

private data class SearchUi(val movies: List<Work>, val series: List<Work>, val channels: List<Channel>, val programs: List<Pair<Program, Channel>>) {
    val isEmpty get() = movies.isEmpty() && series.isEmpty() && channels.isEmpty() && programs.isEmpty()
}

/**
 * Recherche de la tablette (hors TV) : champ en haut, pastilles de périmètre, puis les tendances (Top 10 films et
 * séries) tant que rien n'est tapé ; ensuite « MEILLEURS RÉSULTATS », Films, Séries et Chaînes en rangées.
 */
@Composable
fun TabletSearch(app: AppState) {
    val l = LocalLayout.current
    var query by remember { mutableStateOf(com.sokoul.desktop.TrialRoute.searchQuery.orEmpty().also { com.sokoul.desktop.TrialRoute.searchQuery = null }) }
    var kinds by remember { mutableStateOf(SearchKind.entries.toSet()) }
    val entry = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { entry.requestFocus() } }
    val trending by produceState(emptyList<Pair<Boolean, List<Work>>>()) {
        val k = app.catalog.key()
        value = listOf(
            false to app.catalog.tmdb.trending(k, "movie").map { it.toWork("movie", Catalog::genreName) },
            true to app.catalog.tmdb.trending(k, "tv").map { it.toWork("tv", Catalog::genreName) },
        )
    }
    val results by produceState<SearchUi?>(null, query, kinds) {
        val q = query.trim()
        if (q.length < 2) { value = null; return@produceState }
        delay(300)
        val tmdb = if (SearchKind.MOVIES in kinds || SearchKind.SERIES in kinds) app.catalog.search(q) else emptyList()
        val p = app.playlist
        val channels = if (p != null && SearchKind.CHANNELS in kinds) withContext(Dispatchers.IO) { app.db.searchChannels(p.id, LiveLogic.searchKey(q), 40) } else emptyList()
        val programs = if (p != null && SearchKind.PROGRAMS in kinds) withContext(Dispatchers.IO) { app.db.searchPrograms(p.id, q, System.currentTimeMillis()) } else emptyList()
        value = SearchUi(
            if (SearchKind.MOVIES in kinds) tmdb.filter { it.mediaType != "tv" }.map { it.toWork("movie", Catalog::genreName) } else emptyList(),
            if (SearchKind.SERIES in kinds) tmdb.filter { it.mediaType == "tv" }.map { it.toWork("tv", Catalog::genreName) } else emptyList(),
            channels,
            programs,
        )
    }
    val placeholder = when (kinds) {
        setOf(SearchKind.MOVIES) -> "Rechercher un film"
        setOf(SearchKind.SERIES) -> "Rechercher une série"
        setOf(SearchKind.CHANNELS) -> "Rechercher une chaîne"
        else -> "Rechercher un titre, une chaîne"
    }
    Column(Modifier.fillMaxSize()) {
        FormField(placeholder, query, { query = it }, Modifier.padding(horizontal = l.margin, vertical = 24.dp).focusRequester(entry))
        LazyRow(Modifier.padding(bottom = Space.m), contentPadding = PaddingValues(horizontal = l.margin), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            items(SearchKind.entries) { k -> SokoulChip(k.label, { kinds = if (k in kinds && kinds.size > 1) kinds - k else kinds + k }, selected = k in kinds) }
        }
        val r = results
        val open: (Work) -> Unit = { w -> open(app, w) }
        when {
            r == null -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                trending.forEach { (series, works) ->
                    if ((series && SearchKind.SERIES in kinds) || (!series && SearchKind.MOVIES in kinds)) rankRow(
                        if (series) "Top 10 des séries cette semaine" else "Top 10 des films cette semaine",
                        if (series) "Les séries que tout le monde regarde." else "Les films que tout le monde regarde.",
                        works, if (series) "t:tv" else "t:movie", open,
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
            r.isEmpty -> EmptyState(Icons.Default.Search, "Aucun résultat pour « $query »", if (kinds.size < SearchKind.entries.size) "Vérifiez l'orthographe, ou cochez d'autres types ci-dessus." else "Vérifiez l'orthographe ou essayez le titre original.", Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                val best = bestResults(r.movies, r.series)
                val bestIds = best.mapTo(HashSet()) { it.uid }
                if (best.isNotEmpty()) item("best") {
                    Column(Modifier.padding(bottom = l.rowSpacing)) {
                        Text("MEILLEURS RÉSULTATS · ${r.movies.size + r.series.size}", Modifier.padding(horizontal = l.margin), style = Type.Kicker, maxLines = 1)
                        Text("Les plus proches de votre saisie", Modifier.padding(horizontal = l.margin), style = Type.Section, maxLines = 1)
                        CardRow(best, { "best|${it.uid}" }) { _, w -> LandscapeCard(w, { open(w) }, width = 260.dp) }
                    }
                }
                r.movies.filterNot { it.uid in bestIds }.takeIf { it.isNotEmpty() }?.let { landscapeRow("Films", null, it, "movies", open) }
                r.series.filterNot { it.uid in bestIds }.takeIf { it.isNotEmpty() }?.let { landscapeRow("Séries", null, it, "series", open) }
                if (r.channels.isNotEmpty()) item("channels") {
                    Column(Modifier.padding(bottom = l.rowSpacing)) {
                        RowHeader("Chaînes", "${r.channels.size} résultat${if (r.channels.size > 1) "s" else ""}")
                        CardRow(r.channels, { it.id }) { i, c -> ChannelCard(c) { app.play(r.channels, i) } }
                    }
                }
                if (r.programs.isNotEmpty()) item("programs") {
                    Column(Modifier.padding(bottom = l.rowSpacing)) {
                        RowHeader("Au programme", "Dans le guide TV des prochains jours")
                        CardRow(r.programs, { "${it.first.channelKey}|${it.first.start}" }) { _, (p, c) ->
                            FocusSurface(onClick = { app.play(listOf(c), 0) }, modifier = Modifier.width(l.landscape)) { _ ->
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${dayLabel(p.start)} · ${formatRange(p.start, p.end)}", color = SokoulColors.Accent, style = MaterialTheme.typography.labelMedium)
                                    Text(c.name, color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

/** Meilleurs résultats : films et séries alternés (comme `bestResults` de la tablette). */
private fun bestResults(movies: List<Work>, series: List<Work>, n: Int = 12): List<Work> {
    val out = ArrayList<Work>(n)
    var i = 0
    while (out.size < n && (i < movies.size || i < series.size)) {
        movies.getOrNull(i)?.let(out::add)
        if (out.size < n) series.getOrNull(i)?.let(out::add)
        i++
    }
    return out
}

private fun LazyListScope.landscapeRow(title: String, tagline: String?, works: List<Work>, key: String, open: (Work) -> Unit) {
    item(key) {
        Column(Modifier.padding(bottom = LocalLayout.current.rowSpacing)) {
            RowHeader(title, tagline)
            CardRow(works, { it.uid }) { _, w -> LandscapeCard(w, { open(w) }, width = 260.dp) }
        }
    }
}

private fun LazyListScope.rankRow(title: String, tagline: String?, works: List<Work>, key: String, open: (Work) -> Unit) {
    item(key) {
        Column(Modifier.padding(bottom = LocalLayout.current.rowSpacing)) {
            RowHeader(title, tagline, kicker = "TENDANCE")
            CardRow(works.take(10), { it.uid }) { i, w -> RankLandscapeCard(i + 1, w, { open(w) }, width = 240.dp) }
        }
    }
}

/** `ChannelCard` de la tablette (accueil, recherche), sans programme en cours. */
@Composable
fun ChannelCard(c: Channel, onClick: () -> Unit) {
    val w = (LocalLayout.current.landscape * 0.72f)
    Column(Modifier.width(w)) {
        FocusSurface(onClick = onClick) { _ ->
            Box(
                Modifier.fillMaxWidth().height(w * 0.5f)
                    .background(Brush.linearGradient(listOf(SokoulColors.SurfaceHigher, SokoulColors.PlaceholderBottom))),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(c.logo, c.name, Modifier.size(w * 0.7f, w * 0.36f))
            }
        }
        Text(c.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("—", style = MaterialTheme.typography.bodySmall, color = SokoulColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
