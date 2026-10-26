package com.sokoul.iptv.ui.screens.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.sokoul.iptv.data.repo.BrowseRepository
import com.sokoul.iptv.data.repo.EnrichmentRepository
import com.sokoul.iptv.data.repo.PersonPage
import com.sokoul.iptv.data.repo.PlaylistRepository
import com.sokoul.iptv.data.repo.Work
import com.sokoul.iptv.data.repo.tmdbSized
import com.sokoul.iptv.ui.components.SokoulButton
import com.sokoul.iptv.ui.components.ButtonKind
import com.sokoul.iptv.ui.components.CardRow
import com.sokoul.iptv.ui.components.EmptyState
import com.sokoul.iptv.ui.components.FocusMemory
import com.sokoul.iptv.ui.components.FocusStyle
import com.sokoul.iptv.ui.components.FocusSurface
import com.sokoul.iptv.ui.components.NetImage
import com.sokoul.iptv.ui.components.RowHeader
import com.sokoul.iptv.ui.components.WorkCard
import com.sokoul.iptv.ui.components.focusMemory
import com.sokoul.iptv.ui.components.initials
import com.sokoul.iptv.ui.components.rememberFocusMemory
import com.sokoul.iptv.ui.components.shimmer
import com.sokoul.iptv.ui.screens.browse.openWork
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Type
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PersonUi(val loading: Boolean = true, val page: PersonPage? = null, val noKey: Boolean = false, val failed: Boolean = false)

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val repo: BrowseRepository,
    private val enrich: EnrichmentRepository,
) : ViewModel() {
    val ui = MutableStateFlow(PersonUi())
    private var id: Int? = null

    fun load(personId: Int, force: Boolean = false) {
        if (id == personId && !force) return
        id = personId
        ui.value = PersonUi()
        viewModelScope.launch {
            val p = playlists.activeNow() ?: run { ui.value = PersonUi(loading = false, failed = true); return@launch }
            if (enrich.apiKey().isBlank()) { ui.value = PersonUi(loading = false, noKey = true); return@launch }
            val page = try { repo.personPage(p.id, personId) } catch (e: CancellationException) { throw e } catch (e: Exception) { Timber.w(e, "Personne"); null }
            ui.value = PersonUi(loading = false, page = page, failed = page == null)
        }
    }

    fun retry() { id?.let { load(it, force = true) } }
}

/** « Aussi connue pour » / « Aussi connu pour », selon le métier accordé. */
internal fun alsoKnownFor(role: String?) =
    if (role?.endsWith("ice") == true) "Aussi connue pour" else "Aussi connu pour"

/**
 * Page d'un acteur ou d'un réalisateur : en-tête (photo ronde, nom, dates, biographie dépliable), puis sa filmographie
 * présente dans l'abonnement (films, séries) et ses rôles marquants hors catalogue, grisés. Fond flou tiré d'une œuvre.
 */
@Composable
fun PersonScreen(nav: NavHostController, personId: Int, name: String) {
    val vm: PersonViewModel = hiltViewModel()
    LaunchedEffect(personId) { vm.load(personId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val l = LocalLayout.current
    val memory = rememberFocusMemory()
    val rdPlay = com.sokoul.iptv.ui.components.rememberDebridPlay(nav)
    val page = ui.page
    Box(Modifier.fillMaxSize().background(SokoulColors.Background)) {
        page?.backdrop?.let { Backdrop(it) }
        when {
            ui.loading -> PersonSkeleton(name)
            ui.noKey -> EmptyState(Icons.Default.Key, "Clé TMDB nécessaire", "Les filmographies s'appuient sur TheMovieDB : ajoutez une clé dans Réglages.")
            page == null -> EmptyState(Icons.Default.Refresh, "Impossible de charger cette page", "Vérifiez la connexion, puis réessayez.") {
                SokoulButton("Réessayer", vm::retry, kind = ButtonKind.Primary, icon = Icons.Default.Refresh, modifier = Modifier.focusMemory(memory, "retry"))
            }
            else -> {
                val open: (Work) -> Unit = { w -> if (w.available) nav.openWork(w) else rdPlay?.invoke(w) }
                LazyColumn(Modifier.fillMaxSize(), rememberLazyListState(), contentPadding = PaddingValues(bottom = 56.dp)) {
                    item("head") { PersonHeader(page, memory) }
                    if (page.movies.isEmpty() && page.series.isEmpty()) item("none") {
                        Text(
                            "Rien de sa filmographie dans votre abonnement pour l'instant.",
                            Modifier.padding(horizontal = l.margin, vertical = 12.dp), color = SokoulColors.TextSecondary, style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    workRow("Films", "DANS VOTRE CATALOGUE", null, page.movies, "m", memory, open)
                    workRow("Séries", "DANS VOTRE CATALOGUE", null, page.series, "s", memory, open)
                    workRow(alsoKnownFor(page.role), "FILMOGRAPHIE", "Pas dans votre catalogue", page.elsewhere, "x", memory, open)
                }
                // arrivée : la biographie (ou la première œuvre), jamais rien de focalisé
                LaunchedEffect(page.id) {
                    delay(120)
                    if (memory.restore()) return@LaunchedEffect
                    val first = when {
                        page.biography != null -> "bio"
                        page.movies.isNotEmpty() -> "m|${page.movies.first().uid}"
                        page.series.isNotEmpty() -> "s|${page.series.first().uid}"
                        page.elsewhere.isNotEmpty() -> "x|${page.elsewhere.first().uid}"
                        else -> return@LaunchedEffect
                    }
                    runCatching { memory.requester(first).requestFocus() }
                }
            }
        }
    }
}

private fun LazyListScope.workRow(title: String, kicker: String, tagline: String?, works: List<Work>, key: String, memory: FocusMemory, open: (Work) -> Unit) {
    if (works.isEmpty()) return
    item(key) {
        Column(Modifier.padding(bottom = LocalLayout.current.rowSpacing)) {
            RowHeader(title, tagline, kicker = kicker)
            CardRow(works, { it.uid }) { _, w -> WorkCard(w, { open(w) }, Modifier.focusMemory(memory, "$key|${w.uid}"), reveal = false) }
        }
    }
}

/** Fond : une image de la filmographie, floutée et très assombrie (le texte reste lisible à 3 m). */
@Composable
private fun Backdrop(url: String) {
    Box(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
        NetImage(tmdbSized(url, "w780"), null, Modifier.fillMaxSize().blur(24.dp).graphicsLayer { alpha = 0.45f })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to SokoulColors.Background)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to SokoulColors.Background.copy(alpha = 0.85f), 0.7f to Color.Transparent)))
    }
}

@Composable
private fun PersonHeader(page: PersonPage, memory: FocusMemory) {
    val l = LocalLayout.current
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.padding(start = l.margin, end = l.margin, top = l.safeTop + 36.dp, bottom = 24.dp), verticalAlignment = Alignment.Top) {
        NetImage(
            tmdbSized(page.photo, "w342"), page.name,
            Modifier.size(if (l.isCompact) 110.dp else 168.dp).clip(Radius.Round).background(SokoulColors.SurfaceHigher),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(initials(page.name), style = Type.ScreenTitle, color = SokoulColors.TextSecondary) }
        }
        Column(Modifier.padding(start = 28.dp).widthIn(max = 720.dp)) {
            page.role?.let { Text(it.uppercase(), style = Type.Kicker) }
            Text(page.name, style = Type.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
            page.facts?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = SokoulColors.TextSecondary) }
            page.biography?.let { bio ->
                // biographie : 3 lignes, OK pour tout lire (focalisable : c'est aussi le point d'arrivée)
                FocusSurface(
                    onClick = { expanded = !expanded }, style = FocusStyle.Row, shape = Radius.Card,
                    modifier = Modifier.padding(top = 14.dp).focusMemory(memory, "bio"),
                ) { focused ->
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).animateContentSize()) {
                        Text(
                            bio, maxLines = if (expanded) 14 else 3, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, color = if (focused) SokoulColors.TextPrimary else SokoulColors.TextSecondary,
                        )
                        if (focused && bio.length > 220) Text(
                            if (expanded) "OK : réduire" else "OK : lire la suite", Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelMedium, color = SokoulColors.Accent,
                        )
                    }
                }
            }
            val n = page.movies.size + page.series.size
            if (n > 0) Text(
                "$n œuvre${if (n > 1) "s" else ""} dans votre catalogue", Modifier.padding(top = 10.dp, start = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = SokoulColors.Accent,
            )
        }
    }
}

@Composable
private fun PersonSkeleton(name: String) {
    val l = LocalLayout.current
    Column(Modifier.fillMaxSize().padding(start = l.margin, top = l.safeTop + 36.dp)) {
        Row {
            Box(Modifier.size(168.dp).clip(Radius.Round).shimmer())
            Column(Modifier.padding(start = 28.dp)) {
                Text(name, style = Type.Display)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(420.dp).height(18.dp).shimmer())
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(560.dp).height(60.dp).shimmer())
            }
        }
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(l.gutter)) {
            repeat(6) { Box(Modifier.width(l.poster).height(l.poster * 1.5f).shimmer()) }
        }
    }
}
