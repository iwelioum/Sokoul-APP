package com.sokoul.iptv.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.GridHeader
import com.sokoul.iptv.data.repo.GridSpec
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.PlatformBadge
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.dpadClickable
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GridUi(val loading: Boolean = true, val header: GridHeader? = null, val works: List<Work> = emptyList())

@OptIn(FlowPreview::class)
@HiltViewModel
class BrowseGridViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val repo: BrowseRepository,
    private val enrich: EnrichmentRepository,
) : ViewModel() {
    val ui = MutableStateFlow(GridUi())
    private var spec: String? = null
    private var job: Job? = null

    fun load(s: String) {
        if (spec == s) return
        spec = s
        job?.cancel()
        job = viewModelScope.launch {
            val p = playlists.activeNow() ?: run { ui.value = GridUi(loading = false); return@launch }
            val gs = GridSpec.decode(s) ?: run { ui.value = GridUi(loading = false); return@launch }
            val (header, base) = runCatching { repo.grid(p.id, gs) }.getOrDefault(null to emptyList())
            ui.value = GridUi(false, header, enrich.decorate(p.id, base))
            enrich.request(p.id, base.take(60))
            enrich.updates.drop(1).debounce(900).collect { ui.value = ui.value.copy(works = enrich.decorate(p.id, base)) }
        }
    }
}

/** Grille « Tout voir » : rangée complète, plateforme, saga, catégorie. */
@Composable
fun BrowseGridScreen(nav: NavHostController, title: String, spec: String) {
    val vm: BrowseGridViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    LaunchedEffect(spec) { vm.load(spec) }
    val header = ui.header
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(l.gridCell),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = l.margin, end = l.margin, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(l.gutter),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { GridBanner(title, header, ui.works.size, ui.loading) }
            when {
                ui.loading -> items(18) { Box(Modifier.fillMaxWidth().padding(bottom = 30.dp).height(l.gridCell * 1.5f).shimmer()) }
                ui.works.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.Movie, "Rien à afficher", "Aucun titre de cette sélection n'est disponible dans votre abonnement.", Modifier.height(320.dp))
                }
                else -> items(ui.works, key = { it.uid }) { w ->
                    WorkCard(w, { nav.openWork(w) }, Modifier.fillMaxWidth(), width = l.gridCell * 2, showPlatform = header?.platform == null)
                }
            }
        }
        if (l.isTouch) {
            Box(
                Modifier.statusBarsPadding().padding(8.dp).size(44.dp).clip(RoundedCornerShape(50)).background(Color(0x80000000))
                    .dpadClickable({ nav.popBackStack() }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
        }
    }
}

@Composable
private fun GridBanner(title: String, header: GridHeader?, count: Int, loading: Boolean) {
    val l = LocalLayout.current
    val rich = header?.backdrop != null || header?.platform != null
    if (!rich) {
        Column(Modifier.statusBarsPadding().padding(top = if (l.isTouch) 56.dp else 28.dp, bottom = 8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = header?.subtitle ?: if (!loading) "$count titres" else null
            if (sub != null) Text(sub, Modifier.padding(top = 4.dp), color = SokoulColors.TextSecondary)
        }
        return
    }
    val h = (l.heightDp * if (l.isCompact) 0.36f else 0.42f).dp
    Box(Modifier.fillMaxWidth().height(h).padding(bottom = 8.dp).clip(RoundedCornerShape(if (l.isCompact) 0.dp else 16.dp))) {
        val p = header!!.platform
        if (p != null) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(p.color), Color(p.accent), SokoulColors.Background))))
        } else {
            NetImage(tmdbSized(header.backdrop, "w1280"), null, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE60A0A0F), 0.6f to Color(0x660A0A0F), 1f to Color.Transparent)))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC0A0A0F))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp).widthIn(max = l.heroTextWidth)) {
            if (p != null) PlatformBadge(p, Modifier.padding(bottom = 10.dp), large = true)
            else Text("SAGA", color = SokoulColors.Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text(header.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp)) {
                Text(listOfNotNull(header.subtitle, if (!loading) "$count disponibles" else null).joinToString(" · "), color = SokoulColors.TextSecondary)
            }
            header.overview?.let {
                Text(it, Modifier.padding(top = 8.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
