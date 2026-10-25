package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryWithPrefs
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.MovieEntity
import com.sokoul.iptv.data.db.PlaylistEntity
import com.sokoul.iptv.data.db.SeriesEntity
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.ui.ParentalGate
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Contenu d'une grille « Tout voir », encodé dans la route de navigation. */
sealed interface GridSpec {
    data class Cats(val type: ContentType, val ids: List<String>, val byPosition: Boolean) : GridSpec
    data class Recent(val type: ContentType) : GridSpec
    data class Ids(val type: ContentType, val ids: List<String>) : GridSpec
    data class Plat(val type: ContentType?, val platform: Platform) : GridSpec
    data class Themed(val type: ContentType, val theme: Theme) : GridSpec
    data class Saga(val id: Int) : GridSpec
    data class Favorites(val type: ContentType) : GridSpec

    fun encode(): String = when (this) {
        is Cats -> join("cats", type.name, if (byPosition) "p" else "a", *ids.toTypedArray())
        is Recent -> join("recent", type.name)
        is Ids -> join("ids", type.name, *ids.toTypedArray())
        is Plat -> join("plat", type?.name ?: "ALL", platform.name)
        is Themed -> join("theme", type.name, theme.name)
        is Saga -> join("saga", id.toString())
        is Favorites -> join("fav", type.name)
    }

    companion object {
        private const val SEP = '\u001F'
        private fun join(vararg parts: String) = parts.joinToString(SEP.toString())

        fun decode(s: String): GridSpec? = runCatching {
            val p = s.split(SEP)
            fun type(i: Int) = ContentType.valueOf(p[i])
            when (p[0]) {
                "cats" -> Cats(type(1), p.drop(3), p[2] == "p")
                "recent" -> Recent(type(1))
                "ids" -> Ids(type(1), p.drop(2))
                "plat" -> Plat(if (p[1] == "ALL") null else type(1), Platform.valueOf(p[2]))
                "theme" -> Themed(type(1), Theme.valueOf(p[2]))
                "saga" -> Saga(p[1].toInt())
                "fav" -> Favorites(type(1))
                else -> null
            }
        }.getOrNull()
    }
}

/**
 * Pages éditoriales (Accueil, Films, Séries) : héros + rangées à accroche construites à partir des catégories
 * du fournisseur (plateformes, genres, années) et des listes TMDB (tendances, mieux notés, sagas, recommandations)
 * croisées avec le catalogue. Une carte par œuvre ; contenus masqués, verrouillés ou adultes exclus.
 */
@Singleton
class BrowseRepository @Inject constructor(
    private val db: AppDatabase,
    private val vod: VodRepository,
    private val enrich: EnrichmentRepository,
    private val tmdb: TmdbClient,
    private val parental: ParentalGate,
    private val settings: SettingsStore,
) {
    private data class CacheKey(val pid: Long, val kind: BrowseKind, val sync: Long, val open: Boolean, val grouping: Boolean)
    private val cache = ConcurrentHashMap<CacheKey, Pair<Long, BrowsePage>>()
    private val net = Semaphore(EnrichmentRepository.PARALLEL)
    // délais d'affichage progressif (réduits dans les tests)
    internal var quickMs = QUICK_MS
    internal var tmdbBudgetMs = TMDB_BUDGET_MS

    /** Page construite (cache mémoire 20 min, invalidé par une synchronisation ou le contrôle parental). */
    suspend fun page(p: PlaylistEntity, kind: BrowseKind, force: Boolean = false, onPartial: (suspend (BrowsePage) -> Unit)? = null): BrowsePage {
        val key = CacheKey(p.id, kind, p.lastSync, parental.canAccessLocked(), settings.current().groupDuplicates)
        val now = System.currentTimeMillis()
        if (!force) cache[key]?.let { (t, page) -> if (now - t < CACHE_MS) return page }
        val page = build(p.id, kind, onPartial)
        // page dégradée (TMDB en panne) : pas de cache, la prochaine visite retentera
        if (page.partial) return page
        cache.keys.removeAll { it.pid == p.id && it.kind == kind }
        cache[key] = now to page
        return page
    }

    fun invalidate() = cache.clear()

    // --- contexte : catégories visibles d'un type ------------------------------------------------

    private inner class Ctx(val pid: Long, val type: ContentType, all: List<CategoryWithPrefs>, open: Boolean) {
        val visible = all.filter { !it.hidden && (!it.locked || open) && !isAdultCategory(it.name) }
        val visibleIds = visible.mapTo(HashSet()) { it.categoryId }
        val excluded = all.map { it.categoryId }.filter { it !in visibleIds }
        val platformOf: Map<String, Platform> = visible.mapNotNull { c -> Platform.detect(c.name)?.let { c.categoryId to it } }.toMap()
        val kind = if (type == ContentType.MOVIE) "movie" else "tv"

        /** Catégories dans la langue de l'utilisateur d'abord ; les autres langues seulement à défaut. */
        fun preferred(list: List<CategoryWithPrefs>): List<String> {
            val best = list.filter { langRank(it.name) <= 2 }.ifEmpty { list }
            return best.sortedBy { langRank(it.name) }.map { it.categoryId }
        }
    }

    private suspend fun ctx(pid: Long, type: ContentType) =
        Ctx(pid, type, db.categories().observe(pid, type).first(), parental.canAccessLocked())

    // --- œuvres ------------------------------------------------------------------------------------

    private suspend fun Ctx.movieWorks(list: List<MovieEntity>) =
        vod.groupMovies(pid, list.filter { it.categoryId in visibleIds }).map { it.item.toWork(it.versions, platformOf[it.item.categoryId]) }

    private suspend fun Ctx.seriesWorks(list: List<SeriesEntity>) =
        vod.groupSeries(pid, list.filter { it.categoryId in visibleIds }).map { it.item.toWork(it.versions, platformOf[it.item.categoryId]) }

    private suspend fun Ctx.fromCats(ids: List<String>, limit: Int, byPosition: Boolean): List<Work> {
        if (ids.isEmpty()) return emptyList()
        val b = db.browse()
        val fetch = limit * 3
        val cats = ids.take(500)
        val works = if (type == ContentType.MOVIE) {
            movieWorks(if (byPosition) b.moviesInByPosition(pid, cats, fetch) else b.moviesInByAdded(pid, cats, fetch))
        } else {
            seriesWorks(if (byPosition) b.seriesInByPosition(pid, cats, fetch) else b.seriesInByAdded(pid, cats, fetch))
        }
        return distinctTitles(works).take(limit)
    }

    private suspend fun Ctx.recent(limit: Int): List<Work> {
        val b = db.browse()
        val works = if (type == ContentType.MOVIE) movieWorks(b.recentMovies(pid, excluded, limit * 3))
        else seriesWorks(b.recentSeries(pid, excluded, limit * 3))
        return distinctTitles(works).take(limit)
    }

    private suspend fun Ctx.byIds(ids: List<String>): List<Work> {
        val b = db.browse()
        val order = ids.withIndex().associate { it.value to it.index }
        val works = ids.chunked(500).flatMap { chunk ->
            if (type == ContentType.MOVIE) movieWorks(b.moviesByIds(pid, chunk)) else seriesWorks(b.seriesByIds(pid, chunk))
        }
        return works.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }

    /**
     * Œuvres du catalogue correspondant à une liste TMDB (même identifiant TMDB, ou même titre français/original
     * à un an près), dans l'ordre de la liste. Les correspondances sont mémorisées (affichage immédiat des fonds).
     */
    private suspend fun Ctx.matchItems(items: List<TmdbItem>, limit: Int = 40): List<Work> {
        if (items.isEmpty()) return emptyList()
        val keysPer = items.map { it to candidateKeys(it) }
        val all = keysPer.flatMap { it.second }.distinct()
        val b = db.browse()
        val works = all.chunked(500).flatMap { chunk ->
            if (type == ContentType.MOVIE) movieWorks(b.moviesByKeys(pid, chunk)) else seriesWorks(b.seriesByKeys(pid, chunk))
        }
        val byKey = HashMap<String, Work>()
        works.forEach { byKey.putIfAbsent(it.key, it) }
        val out = ArrayList<Work>()
        val pairs = ArrayList<Pair<TmdbItem, String>>()
        val seen = HashSet<String>()
        for ((item, keys) in keysPer) {
            val w = keys.firstNotNullOfOrNull { byKey[it] } ?: continue
            if (!seen.add(w.key)) continue
            out += w
            pairs += item to w.key
            if (out.size >= limit) break
        }
        runCatching { enrich.remember(pid, type, pairs) }.onFailure { Timber.w(it, "Correspondances TMDB") }
        return out
    }

    private suspend fun Ctx.trending(): List<Work> {
        val api = enrich.apiKey()
        if (api.isBlank()) return emptyList()
        return matchItems(net.withPermit { tmdb.trending(api, kind) })
    }

    private suspend fun Ctx.topRated(): List<Work> {
        val api = enrich.apiKey()
        if (api.isBlank()) return emptyList()
        return matchItems(net.withPermit { tmdb.list(api, kind, "top_rated") }, 30)
    }

    /** « Parce que vous avez regardé X » : recommandations TMDB présentes dans le catalogue. */
    private suspend fun becauseYouWatched(pid: Long, types: Set<ContentType>): BrowseRow? {
        val api = enrich.apiKey()
        if (api.isBlank()) return null
        val last = db.browse().lastWatched(pid, 12).firstOrNull { it.type in types } ?: return null
        val type = last.type
        val itemId = if (type == ContentType.SERIES) last.parentId ?: return null else last.itemId
        val c = ctx(pid, type)
        val seed = c.byIds(listOf(itemId)).firstOrNull() ?: return null
        val meta = enrich.ensure(pid, seed) ?: return null
        val recs = net.withPermit { tmdb.recommendations(api, c.kind, meta.tmdbId) }
        val works = c.matchItems(recs, 24).filter { it.key != seed.key }
        if (works.size < 3) return null
        return BrowseRow(
            "byw", "Parce que vous avez regardé ${meta.title}", "Dans la même veine, rien que pour vous.", RowStyle.LANDSCAPE, works,
            seeAll = GridSpec.Ids(type, works.map { it.id }).encode(),
        )
    }

    /** Sagas (collections TMDB) dont au moins deux films sont au catalogue. */
    suspend fun sagas(pid: Long, limit: Int = 12): List<SagaCard> {
        val api = enrich.apiKey()
        if (api.isBlank()) return emptyList()
        val c = ctx(pid, ContentType.MOVIE)
        val cols = db.browse().collections(pid, 30)
        return coroutineScope {
            cols.map { col ->
                async {
                    val full = net.withPermit { tmdb.collection(api, col.collectionId) } ?: return@async null
                    val present = c.matchItems(full.parts, 50)
                    if (present.size >= 2) SagaCard(full.id, full.name, full.backdrop, full.poster ?: present.first().poster, present.size) else null
                }
            }.awaitAll()
        }.filterNotNull().take(limit)
    }

    // --- construction des pages -------------------------------------------------------------------

    private suspend fun build(pid: Long, kind: BrowseKind, onPartial: (suspend (BrowsePage) -> Unit)?): BrowsePage = withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()
        val page = when (kind) {
            BrowseKind.MOVIES -> typedPage(pid, ContentType.MOVIE, onPartial)
            BrowseKind.SERIES -> typedPage(pid, ContentType.SERIES, onPartial)
            BrowseKind.HOME -> homePage(pid, onPartial)
        }
        Timber.d("Page $kind : ${page.rows.size} rangées en ${System.currentTimeMillis() - started} ms")
        page
    }

    private suspend fun typedPage(pid: Long, type: ContentType, onPartial: (suspend (BrowsePage) -> Unit)?): BrowsePage = coroutineScope {
        val c = ctx(pid, type)
        val movies = type == ContentType.MOVIE
        val noun = if (movies) "films" else "séries"
        val trendingD = async { runCatching { c.trending() }.getOrDefault(emptyList()) }
        val topRatedD = async { runCatching { c.topRated() }.getOrDefault(emptyList()) }
        val bywD = async { runCatching { becauseYouWatched(pid, setOf(type)) }.getOrNull() }
        val sagasD: Deferred<List<SagaCard>>? = if (movies) async { runCatching { sagas(pid) }.getOrDefault(emptyList()) } else null

        // nouveautés : catégorie « récemment ajoutés » du fournisseur, sinon derniers ajouts
        val newCats = c.preferred(c.visible.filter { Theme.NEW.matches(it.name) })
        val newD = async { if (newCats.isNotEmpty()) c.fromCats(newCats, 24, byPosition = false) else c.recent(24) }

        val platforms = Platform.entries.mapNotNull { p ->
            val cats = c.visible.filter { p.matches(it.name) }
            if (cats.isEmpty()) null else p to c.preferred(cats)
        }
        val platformRowsD = platforms.map { (p, ids) ->
            async {
                BrowseRow(
                    "plat-${p.name}", "À voir sur ${p.label}", p.tagline, works = c.fromCats(ids, 24, byPosition = false),
                    platform = p, seeAll = GridSpec.Plat(type, p).encode(),
                )
            }
        }

        val season = seasonal()
        val themeRowsD = Theme.entries.filter { it != Theme.NEW }.mapNotNull { t ->
            if (t == Theme.CHRISTMAS && season != Season.CHRISTMAS) return@mapNotNull null
            if (t == Theme.UHD && !DeviceCaps.supports4k) return@mapNotNull null
            val cats = c.visible.filter { t.matches(it.name) && Platform.detect(it.name) == null && topYear(it.name) == null }
            if (cats.isEmpty()) return@mapNotNull null
            val ids = c.preferred(cats)
            val (title, tagline) = when {
                t == Theme.HORROR && season == Season.HALLOWEEN -> "Spécial Halloween" to "Sorcières, monstres et nuits blanches."
                !movies && t.seriesTitle != null -> t.seriesTitle to (t.seriesTagline ?: t.tagline)
                else -> t.title to t.tagline
            }
            t to async { BrowseRow("theme-${t.name}", title, tagline, works = c.fromCats(ids, 24, byPosition = false), seeAll = GridSpec.Themed(type, t).encode()) }
        }

        val years = c.visible.mapNotNull { cat -> topYear(cat.name)?.let { it to cat } }.groupBy({ it.first }, { it.second })
            .toSortedMap(compareByDescending { it }).entries.take(2)
        val yearRowsD = years.map { (y, cats) ->
            val ids = c.preferred(cats)
            async {
                BrowseRow(
                    "year-$y", "Le meilleur de $y", "Les $noun qui ont marqué $y.", works = c.fromCats(ids, 24, byPosition = true),
                    seeAll = GridSpec.Cats(type, ids, byPosition = true).encode(),
                )
            }
        }

        val tmdbParts = listOfNotNull(trendingD, topRatedD, bywD, sagasD)
        progressive(tmdbParts, onPartial) {
        val trending = trendingD.v(emptyList())
        val top10 = trending.take(10)
        val popular = trending.drop(10)
        val newRow = BrowseRow(
            "new", if (movies) "Tout juste arrivés" else "Nouvelles séries & saisons",
            if (movies) "Les dernières sorties ajoutées au catalogue." else "Les derniers épisodes ajoutés au catalogue.",
            works = newD.await(),
            seeAll = if (newCats.isNotEmpty()) GridSpec.Cats(type, newCats, false).encode() else GridSpec.Recent(type).encode(),
        )
        val tiles = BrowseRow("platforms", "Plateformes", null, RowStyle.PLATFORMS, platforms = platforms.map { (p, _) -> PlatformTile(p, type, 0) })
        val top10Row = BrowseRow(
            "top10", "Top 10 des $noun cette semaine", "Ce que tout le monde regarde en ce moment.", RowStyle.TOP10, top10,
            seeAll = GridSpec.Ids(type, trending.map { it.id }).encode(),
        )
        val popularRow = BrowseRow("popular", "Populaires en ce moment", "Les $noun dont on parle partout.", RowStyle.LANDSCAPE, popular)
        val topRatedRow = BrowseRow(
            "toprated", if (movies) "Les chefs-d'œuvre" else "Séries cultes",
            if (movies) "Les films les mieux notés de tous les temps." else "Les séries les mieux notées de tous les temps.",
            works = topRatedD.v(emptyList()), seeAll = GridSpec.Ids(type, topRatedD.v(emptyList()).map { it.id }).encode(),
        )
        val sagaRow = BrowseRow("sagas", "Sagas cultes", "Toute l'histoire, dans l'ordre.", RowStyle.SAGAS, sagas = sagasD?.v(emptyList()).orEmpty())
        val platformRows = platformRowsD.awaitAll()
        val themeRows = themeRowsD.map { it.first to it.second.await() }
        val yearRows = yearRowsD.awaitAll()
        val byw = bywD.v(null)

        // rythme : plateformes, genres et listes alternent ; les thèmes de saison passent devant
        val seasonalRows = themeRows.filter { (t, _) -> (t == Theme.HORROR && season == Season.HALLOWEEN) || t == Theme.CHRISTMAS }.map { it.second }
        val themes = ArrayDeque(themeRows.map { it.second }.filter { it !in seasonalRows })
        val plats = ArrayDeque(platformRows)
        val rows = ArrayList<BrowseRow>()
        rows += tiles
        rows += top10Row
        rows += seasonalRows
        rows += newRow
        plats.removeFirstOrNull()?.let { rows += it }
        rows += popularRow
        yearRows.firstOrNull()?.let { rows += it }
        plats.removeFirstOrNull()?.let { rows += it }
        byw?.let { rows += it }
        themes.removeFirstOrNull()?.let { rows += it }
        if (movies) rows += sagaRow
        plats.removeFirstOrNull()?.let { rows += it }
        themes.removeFirstOrNull()?.let { rows += it }
        rows += topRatedRow
        while (plats.isNotEmpty() || themes.isNotEmpty()) {
            plats.removeFirstOrNull()?.let { rows += it }
            themes.removeFirstOrNull()?.let { rows += it }
        }
        yearRows.drop(1).forEach { rows += it }
        // accès à tout le catalogue : catégories du fournisseur dans la langue de l'utilisateur d'abord
        val chipCats = c.visible.sortedBy { langRank(it.name) }.filter { langRank(it.name) <= 2 }.ifEmpty { c.visible }.take(60)
        rows += BrowseRow(
            "cats", "Explorer par catégorie", "Tout le catalogue de votre abonnement, rangé par catégorie.", RowStyle.CATEGORIES,
            chips = chipCats.map { it.categoryId to prettyCategory(it.name) },
        )

        val hero = heroFrom(top10 + popular, rows)
        BrowsePage(hero, rows)
        }
    }

    private suspend fun homePage(pid: Long, onPartial: (suspend (BrowsePage) -> Unit)?): BrowsePage = coroutineScope {
        val m = ctx(pid, ContentType.MOVIE)
        val s = ctx(pid, ContentType.SERIES)
        val tmD = async { runCatching { m.trending() }.getOrDefault(emptyList()) }
        val tsD = async { runCatching { s.trending() }.getOrDefault(emptyList()) }
        val bywD = async { runCatching { becauseYouWatched(pid, setOf(ContentType.MOVIE, ContentType.SERIES)) }.getOrNull() }
        val sagasD = async { runCatching { sagas(pid) }.getOrDefault(emptyList()) }
        val newMoviesD = async {
            val cats = m.preferred(m.visible.filter { Theme.NEW.matches(it.name) })
            if (cats.isNotEmpty()) m.fromCats(cats, 24, false) else m.recent(24)
        }
        val newSeriesD = async { s.recent(24) }
        val platforms = Platform.entries.filter { p -> m.visible.any { p.matches(it.name) } || s.visible.any { p.matches(it.name) } }

        progressive(listOf(tmD, tsD, bywD, sagasD), onPartial) {
        val tm = tmD.v(emptyList())
        val ts = tsD.v(emptyList())
        val rows = ArrayList<BrowseRow>()
        rows += BrowseRow(
            "top10-m", "Top 10 des films cette semaine", "Les films que tout le monde regarde.", RowStyle.TOP10, tm.take(10),
            seeAll = GridSpec.Ids(ContentType.MOVIE, tm.map { it.id }).encode(),
        )
        rows += BrowseRow("platforms", "Plateformes", null, RowStyle.PLATFORMS, platforms = platforms.map { PlatformTile(it, null, 0) })
        rows += BrowseRow(
            "new-m", "Nouveaux films", "Fraîchement débarqués dans votre catalogue.", works = newMoviesD.await(),
            seeAll = GridSpec.Recent(ContentType.MOVIE).encode(),
        )
        rows += BrowseRow(
            "top10-s", "Top 10 des séries cette semaine", "Les séries dont tout le monde parle.", RowStyle.TOP10, ts.take(10),
            seeAll = GridSpec.Ids(ContentType.SERIES, ts.map { it.id }).encode(),
        )
        bywD.v(null)?.let { rows += it }
        rows += BrowseRow(
            "new-s", "Nouveaux épisodes", "Vos séries continuent : les derniers épisodes sont là.", works = newSeriesD.await(),
            seeAll = GridSpec.Recent(ContentType.SERIES).encode(),
        )
        rows += BrowseRow("sagas", "Sagas cultes", "Toute l'histoire, dans l'ordre.", RowStyle.SAGAS, sagas = sagasD.v(emptyList()))
        rows += BrowseRow("popular-m", "Films populaires", "À voir absolument cette semaine.", RowStyle.LANDSCAPE, tm.drop(10))
        rows += BrowseRow("popular-s", "Séries populaires", "Le prochain marathon commence ici.", RowStyle.LANDSCAPE, ts.drop(10))

        // héros : films et séries du moment en alternance
        val mixed = buildList {
            val a = tm.iterator(); val b = ts.iterator()
            while (a.hasNext() || b.hasNext()) { if (a.hasNext()) add(a.next()); if (b.hasNext()) add(b.next()) }
        }
        BrowsePage(heroFrom(mixed, rows), rows)
        }
    }

    /**
     * Affichage progressif : si TMDB tarde (plus de [QUICK_MS]), une première page sans ses rangées est
     * envoyée à [onPartial] ; on attend ensuite TMDB au plus [TMDB_BUDGET_MS] avant de rendre la page finale.
     */
    private suspend fun progressive(parts: List<Deferred<*>>, onPartial: (suspend (BrowsePage) -> Unit)?, assemble: suspend () -> BrowsePage): BrowsePage {
        if (onPartial != null && withTimeoutOrNull(quickMs) { parts.forEach { it.join() } } == null) onPartial(assemble().copy(partial = true))
        if (withTimeoutOrNull(tmdbBudgetMs) { parts.forEach { it.join() } } == null) {
            Timber.w("TMDB trop lent : page affichée sans les rangées manquantes")
            parts.forEach { if (!it.isCompleted) it.cancel() }
            return assemble().copy(partial = true)
        }
        return assemble()
    }

    /** Valeur d'un calcul terminé, sinon [default] (rangée TMDB pas encore arrivée ou en échec). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> Deferred<T>.v(default: T): T = if (isCompleted && !isCancelled) runCatching { getCompleted() }.getOrDefault(default) else default

    /** Héros : œuvres du moment, sinon premières œuvres des rangées (6 au plus). */
    private fun heroFrom(preferred: List<Work>, rows: List<BrowseRow>): List<Work> {
        val pool = preferred.ifEmpty { rows.filter { it.style == RowStyle.POSTER }.flatMap { it.works.take(3) } }
        return pool.distinctBy { it.uid }.take(HERO_SIZE)
    }

    // --- fiches : titres similaires, saga --------------------------------------------------------------

    /** Recommandations TMDB présentes dans le catalogue (l'œuvre elle-même exclue). */
    suspend fun similar(pid: Long, type: ContentType, tmdbId: Int, exclude: String?): List<Work> {
        val api = enrich.apiKey()
        if (api.isBlank()) return emptyList()
        val c = ctx(pid, type)
        val recs = net.withPermit { tmdb.recommendations(api, c.kind, tmdbId) }
        return enrich.decorate(pid, c.matchItems(recs, 20).filter { it.key != exclude })
    }

    /** Films d'une saga présents dans le catalogue, dans l'ordre de sortie. */
    suspend fun saga(pid: Long, collectionId: Int): Pair<String, List<Work>>? {
        val api = enrich.apiKey()
        if (api.isBlank()) return null
        val col = net.withPermit { tmdb.collection(api, collectionId) } ?: return null
        val works = ctx(pid, ContentType.MOVIE).matchItems(col.parts, 50)
        return col.name to enrich.decorate(pid, works)
    }

    // --- rangées personnelles (reprise, ma liste) ----------------------------------------------------

    /** « Reprendre » : une carte par œuvre, avec le fond TMDB de la série/du film et le temps restant. */
    suspend fun continueWorks(pid: Long, list: List<com.sokoul.iptv.data.db.ProgressEntity>): List<Work> {
        if (list.isEmpty()) return emptyList()
        val b = db.browse()
        val movies = b.moviesByIds(pid, list.filter { it.type == ContentType.MOVIE }.map { it.itemId }.distinct()).associateBy { it.id }
        val series = b.seriesByIds(pid, list.filter { it.type == ContentType.SERIES }.mapNotNull { it.parentId }.distinct()).associateBy { it.id }
        val works = list.mapNotNull { p ->
            val fraction = if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else null
            val left = if (p.durationMs > 0) ((p.durationMs - p.positionMs) / 60_000).coerceAtLeast(1) else null
            val remaining = left?.let { if (it >= 60) "Reste ${it / 60} h ${(it % 60).toString().padStart(2, '0')}" else "Reste $it min" }
            when (p.type) {
                ContentType.MOVIE -> movies[p.itemId]?.toWork()?.let { it.copy(backdrop = it.backdrop ?: p.image, progress = fraction, resume = p, subtitle = remaining) }
                    ?: Work(ContentType.MOVIE, p.itemId, "id:${p.itemId}", p.title, null, p.image, progress = fraction, resume = p, subtitle = remaining)
                ContentType.SERIES -> {
                    val sid = p.parentId ?: return@mapNotNull null
                    // dernier épisode terminé : la carte propose le suivant (lancé depuis le début, toutes versions) ;
                    // fin de série : elle sort de la rangée ; épisodes pas encore en cache : elle ouvre la fiche
                    val (entry, caption, frac) = if (p.finished) {
                        when (val next = nextEpisode(pid, sid, series[sid], p)) {
                            is Next.Found -> next.episode.let { n ->
                                Triple(
                                    p.copy(itemId = n.id, parentId = n.seriesId, positionMs = 0, finished = false, season = n.season, episode = n.episode, subtitle = "S%02dE%02d · %s".format(n.season, n.episode, n.title)),
                                    "Épisode suivant · S${n.season} · É${n.episode}", null,
                                )
                            }
                            Next.End -> return@mapNotNull null
                            Next.Unknown -> Triple(null, "Terminé · voir la suite", null)
                        }
                    } else Triple(p, listOfNotNull(EpisodeMerge.caption(p.subtitle), remaining).joinToString(" · ").ifEmpty { null }, fraction)
                    series[sid]?.toWork()?.let { it.copy(backdrop = it.backdrop ?: p.image, progress = frac, resume = entry, subtitle = caption) }
                        ?: Work(ContentType.SERIES, sid, "id:$sid", p.title, null, p.image, progress = frac, resume = entry, subtitle = caption)
                }
                ContentType.LIVE -> null
            }
        }
        // fond TMDB prioritaire ; à défaut, image du fournisseur (épisode ou affiche)
        return enrich.decorate(pid, works)
    }

    /** Suite d'un épisode terminé : trouvée, fin de série, ou inconnue (épisodes pas encore en cache). */
    private sealed interface Next {
        data class Found(val episode: com.sokoul.iptv.data.db.EpisodeEntity) : Next
        data object End : Next
        data object Unknown : Next
    }

    /**
     * Épisode qui suit [p], toutes versions de la série confondues (liste fusionnée comme sur la fiche),
     * à partir du cache uniquement : aucun appel au fournisseur.
     */
    private suspend fun nextEpisode(pid: Long, sid: String, series: com.sokoul.iptv.data.db.SeriesEntity?, p: com.sokoul.iptv.data.db.ProgressEntity): Next {
        val versions = series?.let { vod.orderedSeries(pid, vod.seriesVersions(pid, it)).map { v -> v.id } }.orEmpty()
        val ids = (listOf(sid) + versions).distinct()
        val all = db.vod().episodesIn(pid, ids)
        if (all.isEmpty()) return Next.Unknown
        val merged = VodRepository.mergeEpisodes(all, ids)
        val se = EpisodeResolver.seOf(p, all.associate { it.id to (it.season to it.episode) }) ?: return Next.Unknown
        return EpisodeResolver.nextAfter(merged, se)?.let { Next.Found(it) } ?: Next.End
    }

    // --- grilles « Tout voir » -------------------------------------------------------------------------

    suspend fun grid(pid: Long, spec: GridSpec): Pair<GridHeader?, List<Work>> {
        return when (spec) {
            is GridSpec.Cats -> null to ctx(pid, spec.type).fromCats(spec.ids, GRID_LIMIT, spec.byPosition)
            is GridSpec.Recent -> null to ctx(pid, spec.type).recent(GRID_LIMIT)
            is GridSpec.Ids -> null to ctx(pid, spec.type).byIds(spec.ids)
            is GridSpec.Themed -> {
                val c = ctx(pid, spec.type)
                val ids = c.preferred(c.visible.filter { spec.theme.matches(it.name) })
                GridHeader(spec.theme.title, spec.theme.tagline) to c.fromCats(ids, GRID_LIMIT, false)
            }
            is GridSpec.Plat -> {
                val types = spec.type?.let { listOf(it) } ?: listOf(ContentType.MOVIE, ContentType.SERIES)
                val lists = types.map { t ->
                    val c = ctx(pid, t)
                    c.fromCats(c.preferred(c.visible.filter { spec.platform.matches(it.name) }), GRID_LIMIT, false)
                }
                val merged = if (lists.size == 1) lists[0] else buildList {
                    val a = lists[0].iterator(); val b = lists[1].iterator()
                    while (a.hasNext() || b.hasNext()) { if (a.hasNext()) add(a.next()); if (b.hasNext()) add(b.next()) }
                }
                GridHeader(spec.platform.label, spec.platform.tagline, platform = spec.platform) to merged
            }
            is GridSpec.Saga -> {
                val api = enrich.apiKey()
                val col = if (api.isBlank()) null else tmdb.collection(api, spec.id)
                if (col == null) null to emptyList()
                else GridHeader(col.name, "${col.parts.size} films", col.overview, col.backdrop) to ctx(pid, ContentType.MOVIE).matchItems(col.parts, 50)
            }
            is GridSpec.Favorites -> {
                val c = ctx(pid, spec.type)
                val favs = db.library().allFavorites(pid).filter { it.type == spec.type }.sortedByDescending { it.addedAt }.map { it.itemId }
                null to c.byIds(favs)
            }
        }
    }

    private enum class Season { NONE, HALLOWEEN, CHRISTMAS }

    private fun seasonal(now: Calendar = Calendar.getInstance()): Season {
        val m = now.get(Calendar.MONTH)
        val d = now.get(Calendar.DAY_OF_MONTH)
        return when {
            (m == Calendar.OCTOBER && d >= 10) || (m == Calendar.NOVEMBER && d <= 2) -> Season.HALLOWEEN
            (m == Calendar.NOVEMBER && d >= 20) || m == Calendar.DECEMBER || (m == Calendar.JANUARY && d <= 6) -> Season.CHRISTMAS
            else -> Season.NONE
        }
    }

    companion object {
        const val HERO_SIZE = 6
        const val GRID_LIMIT = 300
        private const val CACHE_MS = 20 * 60_000L
        // au-delà, la page s'affiche sans attendre TMDB ; TMDB a ensuite [TMDB_BUDGET_MS] pour compléter
        const val QUICK_MS = 1_500L
        const val TMDB_BUDGET_MS = 12_000L

        /** Clés de regroupement possibles d'un résultat TMDB (identifiant, titre français/original à ±1 an). */
        fun candidateKeys(item: TmdbItem): List<String> {
            val out = LinkedHashSet<String>()
            out += "tmdb:${item.id}"
            val y = item.year ?: return out.toList()
            for (t in listOfNotNull(item.title, item.originalTitle).distinct()) {
                for (yy in listOf(y, y - 1, y + 1)) VodGrouping.titleKey(t, yy)?.let { out += it }
            }
            return out.toList()
        }

        /**
         * Doublons restés séparés (titre sans année, code pays…) : une seule carte par titre dans une rangée.
         * Les autres restent accessibles par la recherche et « Toutes les catégories ».
         */
        fun distinctTitles(works: List<Work>): List<Work> {
            // titre + année : un remake (Dune 1984 / Dune 2021) garde sa carte ; une copie sans année est écartée
            val seen = HashSet<String>()
            val titles = HashSet<String>()
            return works.filter { w ->
                val t = "${w.type}|" + VodGrouping.normalizeTitle(w.providerTitle, w.year).ifEmpty { w.key }
                if (w.year == null) titles.add(t) && seen.add("$t|")
                else seen.add("$t|${w.year}").also { titles.add(t) }
            }
        }
    }
}
