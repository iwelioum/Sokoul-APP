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
import com.sokoul.iptv.player.ScreenLoading
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
    /** « Ma liste » : un type, ou films et séries mêlés ([type] null), du plus récent ajout au plus ancien. */
    data class Favorites(val type: ContentType?) : GridSpec
    /** Ambiance ou micro-genre (fiches TMDB indexées). */
    data class Mooded(val type: ContentType, val mood: Mood?, val micro: MicroGenre?) : GridSpec

    fun encode(): String = when (this) {
        is Mooded -> join("mood", type.name, mood?.name ?: "-", micro?.name ?: "-")
        is Cats -> join("cats", type.name, if (byPosition) "p" else "a", *ids.toTypedArray())
        is Recent -> join("recent", type.name)
        is Ids -> join("ids", type.name, *ids.toTypedArray())
        is Plat -> join("plat", type?.name ?: "ALL", platform.name)
        is Themed -> join("theme", type.name, theme.name)
        is Saga -> join("saga", id.toString())
        is Favorites -> join("fav", type?.name ?: "ALL")
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
                "fav" -> Favorites(if (p[1] == "ALL") null else type(1))
                "mood" -> Mooded(type(1), p[2].takeIf { it != "-" }?.let(Mood::valueOf), p[3].takeIf { it != "-" }?.let(MicroGenre::valueOf))
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
    private val curated: CuratedSagaStore,
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
        val page = ScreenLoading.track { build(p.id, kind, onPartial) }
        // page dégradée (TMDB en panne) : pas de cache, la prochaine visite retentera
        if (page.partial) return page
        cache.keys.removeAll { it.pid == p.id && it.kind == kind }
        cache[key] = now to page
        return page
    }

    fun invalidate() { cache.clear(); hubCache.clear() }

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

    /** Œuvres par clé de regroupement, dans l'ordre des clés (contenus masqués ou verrouillés exclus). */
    private suspend fun Ctx.byKeys(keys: List<String>): List<Work> {
        if (keys.isEmpty()) return emptyList()
        val b = db.browse()
        val (direct, grouped) = keys.partition { it.startsWith("id:") }
        val ids = direct.map { it.removePrefix("id:") }
        val works = if (type == ContentType.MOVIE) {
            movieWorks(grouped.chunked(500).flatMap { b.moviesByKeys(pid, it) } + ids.chunked(500).flatMap { b.moviesByIds(pid, it) })
        } else {
            seriesWorks(grouped.chunked(500).flatMap { b.seriesByKeys(pid, it) } + ids.chunked(500).flatMap { b.seriesByIds(pid, it) })
        }
        val order = keys.withIndex().associate { it.value to it.index }
        return works.filter { it.key in order }.sortedBy { order[it.key] }
    }

    /** Explorer : œuvres dont la fiche répond aux filtres, décorées (fonds, logos). */
    private suspend fun Ctx.explore(filter: ExploreFilter, sort: GridSort, limit: Int): List<Work> {
        val rows = db.browse().explore(ExploreQuery.build(pid, type, filter, sort, limit * 2))
        val metas = rows.associate { it.workKey to it.meta }
        // une même fiche TMDB peut couvrir deux groupes (titre du fournisseur / clé « tmdb:N ») :
        // une seule carte, à la place de la première, celle qui a le plus de versions
        val byTmdb = LinkedHashMap<Any, Work>()
        for (w in byKeys(rows.map { it.workKey })) {
            val id: Any = metas[w.key]?.tmdbId ?: w.key
            val kept = byTmdb[id]
            if (kept == null || w.versions > kept.versions) byTmdb[id] = w
        }
        val works = distinctTitles(byTmdb.values.toList()).take(limit)
        return works.map { it.withMeta(metas[it.key]) }
    }

    suspend fun explore(pid: Long, type: ContentType, filter: ExploreFilter, sort: GridSort = GridSort.DEFAULT, limit: Int = GRID_LIMIT): List<Work> =
        withContext(Dispatchers.Default) { ctx(pid, type).explore(filter, sort, limit) }

    /** Rangées d'ambiances et de micro-genres d'une page Films / Séries (au moins [MicroGenre.MIN_ROW] titres chacune). */
    /** Ambiances : chaque requête parcourt la table des fiches (~1 s sur la box) ; deux à la fois laissent des cœurs à l'interface. */
    private val moodGate = Semaphore(2)

    private suspend fun Ctx.moodRows(): List<BrowseRow> = coroutineScope {
        val moods = Mood.entries.map { m ->
            async {
                val works = runCatching { moodGate.withPermit { explore(ExploreFilter(mood = m), GridSort.DEFAULT, 24) } }.getOrDefault(emptyList())
                BrowseRow("mood-" + m.name, m.title(type), m.tagline, works = works, seeAll = GridSpec.Mooded(type, m, null).encode())
            }
        }
        val micros = MicroGenre.entries.filter { it != MicroGenre.SPECTACLE || DeviceCaps.supports4k }.map { g ->
            async {
                val works = runCatching { moodGate.withPermit { explore(ExploreFilter(micro = g), GridSort.DEFAULT, 24) } }.getOrDefault(emptyList())
                BrowseRow("micro-" + g.name, g.title, g.tagline, works = works, seeAll = GridSpec.Mooded(type, null, g).encode())
            }
        }
        (moods + micros).awaitAll().filter { it.works.size >= MicroGenre.MIN_ROW }
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
    private suspend fun Ctx.matchItems(items: List<TmdbItem>, limit: Int = 40): List<Work> = matchPairs(items, limit).map { it.second }

    /** Comme [matchItems], en gardant l'élément TMDB de chaque œuvre trouvée. */
    private suspend fun Ctx.matchPairs(items: List<TmdbItem>, limit: Int = 40, byName: Boolean = false): List<Pair<TmdbItem, Work>> {
        if (items.isEmpty()) return emptyList()
        val b = db.browse()
        // correspondances déjà établies (indexation, fiches) : seul moyen de retrouver un titre publié sans année
        val linked = items.map { it.id }.chunked(500).flatMap { b.matchesOf(pid, type, it) }.groupBy({ it.tmdbId }, { it.workKey })
        // [byName] : titres publiés sans année, retrouvés par leur nom exact (pages ponctuelles : parcours complet)
        val named: Map<String, List<String>> = if (!byName) emptyMap() else {
            val names = items.flatMap { listOfNotNull(it.title, it.originalTitle) }.map { SyncEngine.searchable(it) }.distinct()
            names.chunked(400).flatMap { if (type == ContentType.MOVIE) b.moviesNamedNoYear(pid, it) else b.seriesNamedNoYear(pid, it) }
                .groupBy({ it.searchName }, { it.workKey })
        }
        val keysPer = items.map { item ->
            val byTitle = if (named.isEmpty()) emptyList() else listOfNotNull(item.title, item.originalTitle).flatMap { named[SyncEngine.searchable(it)].orEmpty() }
            item to (candidateKeys(item) + linked[item.id].orEmpty() + byTitle)
        }
        val (direct, grouped) = keysPer.flatMap { it.second }.distinct().partition { it.startsWith("id:") }
        val works = if (type == ContentType.MOVIE) {
            movieWorks(grouped.chunked(500).flatMap { b.moviesByKeys(pid, it) } + direct.chunked(500).flatMap { c -> b.moviesByIds(pid, c.map { it.removePrefix("id:") }) })
        } else {
            seriesWorks(grouped.chunked(500).flatMap { b.seriesByKeys(pid, it) } + direct.chunked(500).flatMap { c -> b.seriesByIds(pid, c.map { it.removePrefix("id:") }) })
        }
        val byKey = HashMap<String, Work>()
        works.forEach { byKey.putIfAbsent(it.key, it) }
        val out = ArrayList<Pair<TmdbItem, Work>>()
        val seen = HashSet<String>()
        for ((item, keys) in keysPer) {
            val w = keys.firstNotNullOfOrNull { byKey[it] } ?: continue
            if (!seen.add(w.key)) continue
            out += item to w.copy(tmdbId = w.tmdbId ?: item.id)
            if (out.size >= limit) break
        }
        // seules les correspondances nouvelles sont écrites (chaque écriture inutile ralentit la base)
        val fresh = out.filter { (item, w) -> w.key !in linked[item.id].orEmpty() }
        runCatching { enrich.remember(pid, type, fresh.map { (item, w) -> item to w.key }) }.onFailure { Timber.w(it, "Correspondances TMDB") }
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

    /** Rangée « Sagas cultes » : les premières sagas du hub (en cache 20 min, donc gratuit pendant l'indexation). */
    suspend fun sagas(pid: Long, limit: Int = 12): List<SagaCard> = sagaHub(pid).take(limit)

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
        // ambiances et micro-genres : fiches déjà indexées, sans réseau
        val moodD = async { runCatching { c.moodRows() }.getOrDefault(emptyList()) }

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

        val tmdbParts = listOfNotNull(trendingD, topRatedD, bywD, sagasD, moodD)
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
        val moodRows = ArrayDeque(moodD.v(emptyList()))
        val plats = ArrayDeque(platformRows)
        val rows = ArrayList<BrowseRow>()
        rows += tiles
        rows += top10Row
        rows += seasonalRows
        rows += newRow
        rows += BrowseRow(
            "moods", "Ambiances", "Choisissez une humeur, on s'occupe du reste.", RowStyle.MOODS,
            chips = Mood.entries.map { GridSpec.Mooded(type, it, null).encode() to it.chip },
        )
        plats.removeFirstOrNull()?.let { rows += it }
        rows += popularRow
        moodRows.removeFirstOrNull()?.let { rows += it }
        yearRows.firstOrNull()?.let { rows += it }
        plats.removeFirstOrNull()?.let { rows += it }
        byw?.let { rows += it }
        moodRows.removeFirstOrNull()?.let { rows += it }
        themes.removeFirstOrNull()?.let { rows += it }
        if (movies) rows += sagaRow
        plats.removeFirstOrNull()?.let { rows += it }
        themes.removeFirstOrNull()?.let { rows += it }
        rows += topRatedRow
        while (plats.isNotEmpty() || themes.isNotEmpty() || moodRows.isNotEmpty()) {
            moodRows.removeFirstOrNull()?.let { rows += it }
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

    /**
     * Titres cités par nom (recommandations TasteDive) retrouvés dans le catalogue : chaque nom passe par une
     * recherche TMDB exacte (en cache 7 jours), puis par la correspondance stricte habituelle (id TMDB ou titre + année).
     */
    suspend fun similarByNames(pid: Long, type: ContentType, names: List<String>, exclude: Set<String>, limit: Int = 12): List<Work> {
        val api = enrich.apiKey()
        if (api.isBlank() || names.isEmpty()) return emptyList()
        val c = ctx(pid, type)
        val items = names.distinct().take(limit).mapNotNull { n -> net.withPermit { tmdb.findByName(api, c.kind, n) } }
        return enrich.decorate(pid, c.matchItems(items, limit).filter { it.key !in exclude })
    }

    /** Films d'une saga présents dans le catalogue, dans l'ordre de sortie. */
    suspend fun saga(pid: Long, collectionId: Int): Pair<String, List<Work>>? {
        val api = enrich.apiKey()
        if (api.isBlank()) return null
        val col = net.withPermit { tmdb.collection(api, collectionId) } ?: return null
        val works = ctx(pid, ContentType.MOVIE).matchItems(col.parts, 50)
        return col.name to enrich.decorate(pid, works)
    }

    // --- hub des sagas et page « musée » -------------------------------------------------------------

    /**
     * Toutes les sagas du catalogue : les grandes franchises éditorialisées d'abord (vérifiées sur TMDB, dès un film
     * au catalogue), puis les collections TMDB repérées par l'analyse du catalogue (au moins 2 films).
     */
    suspend fun sagaHub(pid: Long, limit: Int = 60): List<SagaCard> = ScreenLoading.track { withContext(Dispatchers.Default) {
        val api = enrich.apiKey()
        if (api.isBlank()) return@withContext emptyList()
        val key = pid to parental.canAccessLocked()
        hubCache[key]?.let { (t, list) -> if (System.currentTimeMillis() - t < CACHE_MS) return@withContext list }
        val c = ctx(pid, ContentType.MOVIE)
        val counts = db.browse().collections(pid, 200)
        val taken = curated.all.flatMap { it.collectionIds }.toSet()
        val result = coroutineScope {
            // grandes franchises : collections (en cache TMDB), puis une seule recherche au catalogue pour toutes
            val cols = curated.all.flatMap { it.collectionIds }.distinct()
                .map { id -> async { net.withPermit { tmdb.collection(api, id) } } }.awaitAll().filterNotNull().associateBy { it.id }
            // films rattachés hors collection (Rogue One, Solo…) : comptés comme sur la page de la saga
            val extras = curated.all.flatMap { it.extraMovieIds }.distinct()
                .map { id -> async { net.withPermit { tmdb.details(api, "movie", id, full = false) }?.toItem() } }.awaitAll().filterNotNull()
            val parts = (cols.values.flatMap { it.parts } + extras).distinctBy { it.id }
            val present = c.matchPairs(parts, parts.size, byName = true).mapTo(HashSet()) { it.first.id }
            val big = curated.all.mapNotNull { cs ->
                val mine = cs.collectionIds.mapNotNull { cols[it] }
                val first = mine.firstOrNull() ?: return@mapNotNull null
                val n = (mine.flatMap { it.parts }.map { it.id } + cs.extraMovieIds).distinct().count { it in present }
                if (n == 0) null else SagaCard(first.id, cs.name, first.backdrop, first.poster, n, curated = true)
            }
            val others = counts.filter { it.collectionId !in taken && it.works >= 2 }.take(limit).map { col ->
                async {
                    // sans image, une collection obscure (souvent d'une autre langue) n'a pas sa place dans le hub
                    val full = net.withPermit { tmdb.collection(api, col.collectionId) } ?: return@async null
                    if (full.backdrop == null) null
                    else SagaCard(col.collectionId, SagaLogic.displayName(full.name), full.backdrop, full.poster, col.works)
                }
            }
            big.sortedByDescending { it.count } + others.awaitAll().filterNotNull()
        }.take(limit)
        hubCache[key] = System.currentTimeMillis() to result
        result
    } }

    private val hubCache = ConcurrentHashMap<Pair<Long, Boolean>, Pair<Long, List<SagaCard>>>()

    /** Page « musée » : tous les films de la saga (présents ou non), visages récurrents, coulisses, séries liées. */
    suspend fun sagaPage(pid: Long, collectionId: Int, onPartial: (suspend (SagaPage) -> Unit)? = null): SagaPage? = ScreenLoading.track { withContext(Dispatchers.Default) {
        val api = enrich.apiKey()
        if (api.isBlank()) return@withContext null
        val cs = curated.forCollection(collectionId)
        val ids = cs?.collectionIds ?: listOf(collectionId)
        coroutineScope {
            val cols = ids.map { id -> async { net.withPermit { tmdb.collection(api, id) } } }.awaitAll().filterNotNull()
            val main = cols.firstOrNull { it.id == collectionId } ?: cols.firstOrNull() ?: return@coroutineScope null
            val extras = cs?.extraMovieIds.orEmpty().map { id -> async { net.withPermit { tmdb.details(api, "movie", id, full = false) }?.toItem() } }
            val items = (cols.flatMap { it.parts } + extras.awaitAll().filterNotNull())
                .distinctBy { it.id }
                // films annoncés sans date : en fin de frise ; jamais de film à venir non daté en tête
                .sortedBy { it.year ?: 9999 }
            // générique (films sortis seulement : les annonces n'ont pas encore de distribution fiable) et séries liées,
            // en parallèle de la recherche au catalogue
            val creditsD = items.filter { it.year != null }.take(MAX_SAGA_CREDITS).map { m -> async { net.withPermit { tmdb.credits(api, m.id) } } }
            val seriesD = async {
                val list = cs?.relatedSeriesTmdbIds.orEmpty()
                if (list.isEmpty()) return@async emptyList()
                val tv = list.map { id -> async { net.withPermit { tmdb.details(api, "tv", id, full = false) }?.toItem() } }.awaitAll().filterNotNull()
                val there = ctx(pid, ContentType.SERIES).matchPairs(tv, tv.size, byName = true).associate { it.first.id to it.second }
                val dec = enrich.decorate(pid, there.values.toList()).associateBy { it.key }
                tv.map { t -> there[t.id]?.let { dec[it.key] ?: it } ?: t.offCatalog(ContentType.SERIES) }
            }
            val movies = ctx(pid, ContentType.MOVIE)
            val found = movies.matchPairs(items, items.size, byName = true).associate { it.first.id to it.second }
            val dec = enrich.decorate(pid, found.values.toList()).associateBy { it.key }
            val films = items.map { item -> found[item.id]?.let { dec[it.key] ?: it } ?: item.offCatalog(ContentType.MOVIE) }
            val base = SagaPage(
                id = main.id,
                name = cs?.name ?: SagaLogic.displayName(main.name),
                overview = main.overview?.takeIf { it.isNotBlank() },
                backdrop = main.backdrop,
                films = films,
                story = cs?.chronologicalOrderIds?.takeIf { it.isNotEmpty() }?.let { SagaLogic.storyOrder(films, it) },
                series = emptyList(),
                faces = emptyList(),
                crew = emptyList(),
            )
            // première visite : le générique (une requête par film) arrive après la frise, qui s'affiche aussitôt
            if (onPartial != null && creditsD.any { !it.isCompleted }) onPartial(base.copy(series = if (seriesD.isCompleted) seriesD.await() else emptyList()))
            val credits = creditsD.awaitAll().filterNotNull()
            base.copy(series = seriesD.await(), faces = SagaLogic.faces(credits), crew = SagaLogic.crew(credits))
        }
    } }

    /** « Ma liste » pour une œuvre hors catalogue : retenue par son identifiant TMDB (« tmdb:N »). */
    suspend fun toggleOffCatalog(pid: Long, w: Work): Boolean {
        val id = w.tmdbId ?: tmdbIdOfKey(w.key) ?: return false
        val key = "tmdb:$id"
        val lib = db.library()
        if (lib.observeIsFavorite(pid, w.type, key).first()) { lib.removeFavorite(pid, w.type, key); return false }
        // fiche minimale pour afficher la carte dans Ma liste (complétée plus tard par l'enrichissement)
        if (db.browse().meta(w.type, id) == null) {
            db.browse().putMeta(
                com.sokoul.iptv.data.db.TmdbMetaEntity(
                    type = w.type, tmdbId = id, title = w.title, originalTitle = null, overview = w.overview, overviewLang = w.overview?.let { "fr" },
                    tagline = null, genres = null, poster = w.poster, backdrop = w.backdrop, logo = null, rating = w.rating, year = w.year,
                    runtimeMin = null, seasons = null, collectionId = null, collectionName = null, certification = null, fetchedAt = 0,
                )
            )
        }
        lib.addFavorite(com.sokoul.iptv.data.db.FavoriteEntity(pid, w.type, key))
        return true
    }

    fun isOffCatalogFavorite(pid: Long, w: Work) = db.library().observeIsFavorite(pid, w.type, "tmdb:${w.tmdbId ?: tmdbIdOfKey(w.key)}")

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
            is GridSpec.Mooded -> {
                val title = spec.mood?.title(spec.type) ?: spec.micro?.title.orEmpty()
                val tagline = spec.mood?.tagline ?: spec.micro?.tagline
                GridHeader(title, tagline) to ctx(pid, spec.type).explore(ExploreFilter(mood = spec.mood, micro = spec.micro), GridSort.DEFAULT, GRID_LIMIT)
            }
            is GridSpec.Favorites -> {
                val types = spec.type?.let { listOf(it) } ?: listOf(ContentType.MOVIE, ContentType.SERIES)
                val favs = db.library().allFavorites(pid).filter { it.type in types }.sortedByDescending { it.addedAt }
                // ordre d'ajout conservé quand films et séries sont mêlés
                val rank = favs.withIndex().associate { (i, f) -> (f.type to f.itemId) to i }
                val out = ArrayList<Pair<Int, Work>>()
                for (t in types) {
                    val c = ctx(pid, t)
                    val (offIds, ids) = favs.filter { it.type == t }.map { it.itemId }.partition { it.startsWith("tmdb:") }
                    c.byIds(ids).forEach { w -> out += (rank[t to w.id] ?: Int.MAX_VALUE) to w }
                    // titres retenus hors catalogue (sagas) : lisibles dès qu'une version arrive au catalogue
                    if (offIds.isNotEmpty()) {
                        val items = db.browse().metaByIds(t, offIds.mapNotNull { tmdbIdOfKey(it) }).map { it.toItem() }
                        val found = c.matchPairs(items, items.size, byName = true).associate { it.first.id to it.second }
                        items.forEach { item -> out += (rank[t to "tmdb:${item.id}"] ?: Int.MAX_VALUE) to (found[item.id] ?: item.offCatalog(t)) }
                    }
                }
                null to out.sortedBy { it.first }.map { it.second }.distinctBy { it.uid }
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
        /** Films dont le générique est lu pour une saga (une requête TMDB chacun, en cache 7 jours). */
        private const val MAX_SAGA_CREDITS = 25
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
