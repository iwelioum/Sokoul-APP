package com.sokoul.iptv.data.repo

import androidx.sqlite.db.SimpleSQLiteQuery
import com.sokoul.iptv.data.db.ContentType

/**
 * Genres TMDB. Les films et les séries n'ont pas les mêmes identifiants (10759 « Action & Adventure »
 * n'existe que pour les séries). Relevés sur themoviedb.org/movie et /tv le 25/10/2026.
 */
object TmdbGenres {
    // films
    const val ACTION = 28; const val ADVENTURE = 12; const val ANIMATION = 16; const val COMEDY = 35
    const val CRIME = 80; const val DOCUMENTARY = 99; const val DRAMA = 18; const val FAMILY = 10751
    const val FANTASY = 14; const val HISTORY = 36; const val HORROR = 27; const val MUSIC = 10402
    const val MYSTERY = 9648; const val ROMANCE = 10749; const val SCIFI = 878; const val TV_MOVIE = 10770
    const val THRILLER = 53; const val WAR = 10752; const val WESTERN = 37
    // séries uniquement
    const val TV_ACTION_ADVENTURE = 10759; const val TV_KIDS = 10762; const val TV_NEWS = 10763
    const val TV_REALITY = 10764; const val TV_SCIFI_FANTASY = 10765; const val TV_SOAP = 10766
    const val TV_TALK = 10767; const val TV_WAR_POLITICS = 10768

    /** Libellés français des filtres « Genre » de l'Explorer. */
    val movie: Map<Int, String> = linkedMapOf(
        ACTION to "Action", ADVENTURE to "Aventure", ANIMATION to "Animation", COMEDY to "Comédie", CRIME to "Crime",
        DOCUMENTARY to "Documentaire", DRAMA to "Drame", FAMILY to "Familial", FANTASY to "Fantastique", HISTORY to "Histoire",
        HORROR to "Horreur", MUSIC to "Musique", MYSTERY to "Mystère", ROMANCE to "Romance", SCIFI to "Science-fiction",
        TV_MOVIE to "Téléfilm", THRILLER to "Thriller", WAR to "Guerre", WESTERN to "Western",
    )
    val tv: Map<Int, String> = linkedMapOf(
        TV_ACTION_ADVENTURE to "Action & aventure", ANIMATION to "Animation", COMEDY to "Comédie", CRIME to "Crime",
        DOCUMENTARY to "Documentaire", DRAMA to "Drame", FAMILY to "Familial", TV_KIDS to "Enfants", MYSTERY to "Mystère",
        TV_NEWS to "Actualités", TV_REALITY to "Téléréalité", TV_SCIFI_FANTASY to "Science-fiction & fantastique",
        TV_SOAP to "Feuilleton", TV_TALK to "Talk-show", TV_WAR_POLITICS to "Guerre & politique", WESTERN to "Western",
    )

    fun of(type: ContentType): Map<Int, String> = if (type == ContentType.SERIES) tv else movie
}

/**
 * Règle de sélection sur les fiches TMDB. [anyGenre] : au moins un ; [keywords] : mots-clés TMDB
 * (noms vérifiés sur themoviedb.org) — obligatoires si [requireKeyword], sinon ils font remonter les titres.
 */
data class TitleRule(
    val anyGenre: Set<Int> = emptySet(),
    val noGenre: Set<Int> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val requireKeyword: Boolean = false,
    val minRating: Double? = null,
    val minVotes: Int? = null,
    val maxVotes: Int? = null,
    val maxRuntime: Int? = null,
    /** Tous publics (classification française absente ou TP/U/0). */
    val allAudiences: Boolean = false,
    val uhd: Boolean = false,
    /** Langue originale (code ISO : « ko », « fr »…). */
    val language: String? = null,
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val minRuntime: Int? = null,
    /** Séries : nombre de saisons au plus (mini-séries). */
    val maxSeasons: Int? = null,
)

/** Ambiances : pastilles courtes, titres de rangée en français naturel. */
enum class Mood(val chip: String, val movieTitle: String, val seriesTitle: String, val tagline: String) {
    FRISSONS("Frissons", "Frissons garantis", "Frissons garantis", "À regarder les lumières allumées."),
    LEGER("Esprit léger", "Pour se détendre", "Pour se détendre", "Rire, sourire, et rien d'autre."),
    FAMILLE("En famille", "À voir en famille", "À voir en famille", "Des petits aux grands, tout le monde suit."),
    PUZZLE("Puzzle mental", "Films qui retournent le cerveau", "Séries qui retournent le cerveau", "Rien n'est ce qu'il paraît."),
    EMOTIONS("À fleur de peau", "Grandes émotions", "Grandes émotions", "Prévoyez les mouchoirs."),
    ADRENALINE("Adrénaline", "Haute tension", "Haute tension", "Le cœur ne redescend pas."),
    ;

    fun title(type: ContentType) = if (type == ContentType.SERIES) seriesTitle else movieTitle

    fun rule(type: ContentType): TitleRule = if (type == ContentType.SERIES) seriesRule() else movieRule()

    private fun movieRule(): TitleRule = with(TmdbGenres) {
        when (this@Mood) {
            FRISSONS -> TitleRule(setOf(HORROR, THRILLER), keywords = setOf("suspense", "survival", "supernatural"), minRating = 6.2)
            LEGER -> TitleRule(setOf(COMEDY, ROMANCE, ANIMATION), noGenre = setOf(DRAMA, WAR, HORROR), maxRuntime = 114)
            FAMILLE -> TitleRule(setOf(FAMILY), noGenre = setOf(HORROR, THRILLER, CRIME, WAR), allAudiences = true)
            PUZZLE -> TitleRule(
                setOf(SCIFI, MYSTERY), keywords = setOf("twist ending", "plot twist", "nonlinear timeline", "dystopia", "mind game", "time loop"),
                requireKeyword = true, minRating = 7.0,
            )
            EMOTIONS -> TitleRule(setOf(DRAMA, HISTORY, ROMANCE), keywords = setOf("tragedy", "biography", "grief"), minRating = 7.2)
            ADRENALINE -> TitleRule(
                setOf(ACTION, CRIME, THRILLER), noGenre = setOf(ANIMATION, FAMILY),
                keywords = setOf("heist", "car chase", "martial arts", "espionage"),
            )
        }
    }

    private fun seriesRule(): TitleRule = with(TmdbGenres) {
        when (this@Mood) {
            FRISSONS -> TitleRule(setOf(MYSTERY, TV_SCIFI_FANTASY), keywords = setOf("supernatural", "survival", "suspense"), requireKeyword = true, minRating = 6.2)
            LEGER -> TitleRule(setOf(COMEDY, ANIMATION), noGenre = setOf(DRAMA, CRIME, TV_WAR_POLITICS))
            FAMILLE -> TitleRule(setOf(FAMILY, TV_KIDS), noGenre = setOf(CRIME, TV_WAR_POLITICS, TV_ACTION_ADVENTURE), allAudiences = true)
            PUZZLE -> TitleRule(
                setOf(TV_SCIFI_FANTASY, MYSTERY), keywords = setOf("twist ending", "plot twist", "nonlinear timeline", "dystopia", "mind game", "time loop"),
                requireKeyword = true, minRating = 7.0,
            )
            EMOTIONS -> TitleRule(setOf(DRAMA), keywords = setOf("tragedy", "biography", "grief"), minRating = 7.2)
            ADRENALINE -> TitleRule(setOf(TV_ACTION_ADVENTURE, CRIME), noGenre = setOf(ANIMATION, FAMILY, TV_KIDS), keywords = setOf("heist", "car chase", "martial arts", "espionage"))
        }
    }
}

/** Micro-genres : rangées plus pointues, affichées à partir de [MIN_ROW] titres. */
enum class MicroGenre(val title: String, val tagline: String) {
    PSY("Thrillers psychologiques", "Le danger est dans la tête."),
    GEMS("Pépites méconnues", "Très bien notées, encore peu vues."),
    TIME("Paradoxes temporels", "Hier, demain, et tout recommence."),
    CLOSED("Huis clos", "Un lieu, une nuit, aucune issue."),
    NOIR("Polars sombres", "Pluie, néons et cœurs troubles."),
    SPECTACLE("Grand spectacle en 4K", "Le format qui fait honneur à votre écran."),
    ;

    fun rule(type: ContentType): TitleRule = with(TmdbGenres) {
        val tv = type == ContentType.SERIES
        when (this@MicroGenre) {
            PSY -> TitleRule(keywords = setOf("psychological thriller"), requireKeyword = true, minRating = 6.0)
            GEMS -> TitleRule(minRating = 7.2, minVotes = 150, maxVotes = 2_500)
            TIME -> TitleRule(keywords = setOf("time travel", "time loop", "time paradox"), requireKeyword = true)
            CLOSED -> TitleRule(keywords = setOf("single location"), requireKeyword = true)
            NOIR -> TitleRule(setOf(CRIME), keywords = setOf("neo-noir", "serial killer"), requireKeyword = true)
            SPECTACLE -> TitleRule(if (tv) setOf(TV_ACTION_ADVENTURE, TV_SCIFI_FANTASY) else setOf(ACTION, ADVENTURE, SCIFI, FANTASY), uhd = true, minRating = 6.0)
        }
    }

    companion object {
        const val MIN_ROW = 8
    }
}

/** Durée (films) : bornes à la française. */
enum class DurationBand(val label: String, val min: Int?, val max: Int?) {
    SHORT("Moins de 1 h 30", null, 89),
    MEDIUM("1 h 30 à 2 h", 90, 120),
    LONG("Plus de 2 h", 121, null),
}

/** Langue originale proposée dans l'Explorer. */
enum class OriginalLanguage(val label: String, val code: String) {
    FR("Français", "fr"), EN("Anglais", "en"), ES("Espagnol", "es"), IT("Italien", "it"),
    DE("Allemand", "de"), KO("Coréen", "ko"), JA("Japonais", "ja"), HI("Hindi", "hi"),
}

/** Filtres de l'Explorer ; une ambiance ou un micro-genre est un filtre comme un autre. */
data class ExploreFilter(
    val genre: Int? = null,
    val decade: Int? = null,
    val minRating: Double? = null,
    val duration: DurationBand? = null,
    val language: String? = null,
    val uhd: Boolean = false,
    val mood: Mood? = null,
    val micro: MicroGenre? = null,
    /** Rangée du catalogue éditorial ([RowCatalog]). */
    val rule: TitleRule? = null,
) {
    val isEmpty get() = this == ExploreFilter()
}

/**
 * Requête Explorer : œuvres du catalogue (une par clé de regroupement) dont la fiche TMDB répond aux filtres.
 * Les œuvres sans fiche n'apparaissent pas (l'indexation les complète en arrière-plan).
 */
object ExploreQuery {
    fun build(pid: Long, type: ContentType, f: ExploreFilter, sort: GridSort, limit: Int): SimpleSQLiteQuery {
        val args = ArrayList<Any>()
        val table = if (type == ContentType.SERIES) "series" else "movies"
        val where = ArrayList<String>()
        val rules = listOfNotNull(f.mood?.rule(type), f.micro?.rule(type), f.rule)
        rules.forEach { r -> rule(r, where, args) }
        f.genre?.let { where += "t.genreIds LIKE ?"; args += "%,$it,%" }
        f.decade?.let { where += "t.year BETWEEN ? AND ?"; args += it; args += it + 9 }
        f.minRating?.let { where += "t.rating >= ?"; args += it }
        f.duration?.let { d ->
            d.min?.let { where += "t.runtimeMin >= ?"; args += it }
            d.max?.let { where += "t.runtimeMin <= ?"; args += it }
            where += "t.runtimeMin IS NOT NULL"
        }
        f.language?.let { where += "t.originalLanguage = ?"; args += it }
        // 4K : qualité annoncée par le fournisseur ou nom d'une des versions (films seulement)
        // filtre sur la clé de l'œuvre (appliqué après la jointure au catalogue)
        val keyWhere = ArrayList<String>()
        val keyArgs = ArrayList<Any>()
        if ((f.uhd || rules.any { it.uhd }) && type == ContentType.MOVIE) {
            // deux recherches par index (regroupement, puis identifiant) : un « OU » dans une seule recherche
            // empêchait l'index et parcourait tout le catalogue pour chaque titre (mesuré : plus de 20 s)
            val uhd = "(q.quality LIKE '%4K%' OR q.quality LIKE '%UHD%' OR q.quality LIKE '%2160%' OR q.name LIKE '%4K%' OR q.name LIKE '%UHD%')"
            keyWhere += "(EXISTS (SELECT 1 FROM movies q WHERE q.playlistId = ? AND q.groupKey = t.workKey AND $uhd) " +
                "OR (t.workKey LIKE 'id:%' AND EXISTS (SELECT 1 FROM movies q WHERE q.playlistId = ? AND q.id = substr(t.workKey, 4) AND $uhd)))"
            keyArgs += pid; keyArgs += pid
        }
        val kw = rules.flatMap { it.keywords }.distinct()
        val kwScore = kw.joinToString(" + ") { "(CASE WHEN t.keywords LIKE ? THEN 1 ELSE 0 END)" }
        val kwArgs = kw.map { "%|$it|%" }
        val order = when (sort) {
            GridSort.TITLE -> "t.title COLLATE NOCASE"
            GridSort.YEAR -> "t.year DESC"
            GridSort.RATING -> "t.rating DESC"
            // sélection : mots-clés recherchés d'abord, puis note pondérée par la notoriété
            GridSort.DEFAULT -> (if (kw.isEmpty()) "" else "($kwScore) DESC, ") + "(COALESCE(t.rating, 0) * (1 - 1.0 / (1 + COALESCE(t.voteCount, 0) / 50.0))) DESC"
        }
        // 1. fiches TMDB qui répondent aux filtres (table des fiches seule : quelques dizaines de milliers de lignes) ;
        // 2. pour chacune, ses œuvres au catalogue par les index : correspondances établies par l'app (type, tmdbId),
        //    plus la clé « tmdb:N » du fournisseur (playlistId, groupKey).
        // Partir des correspondances obligeait à dédoublonner tout le catalogue à chaque requête : ~1 s de calcul
        // par ambiance sur la box, douze fois par page Films ou Séries.
        // CROSS JOIN impose l'ordre (fiches d'abord) : sans lui, SQLite peut parcourir les correspondances et
        // refiltrer toutes les fiches pour chacune (mesuré : 30 s). Pas de « WITH … MATERIALIZED » : SQLite trop
        // ancien sur Android 7.
        val metaFilter = where.joinToString("") { " AND $it" }
        val sql = """
            SELECT * FROM (
                SELECT m.workKey AS workKey, t.* FROM tmdb_meta t CROSS JOIN tmdb_match m
                WHERE t.type = ?$metaFilter AND m.type = ? AND m.tmdbId = t.tmdbId AND m.playlistId = ?
                UNION
                SELECT 'tmdb:' || t.tmdbId AS workKey, t.* FROM tmdb_meta t
                WHERE t.type = ?$metaFilter
                AND EXISTS (SELECT 1 FROM $table v WHERE v.playlistId = ? AND v.groupKey = 'tmdb:' || t.tmdbId)
            ) t
            ${if (keyWhere.isEmpty()) "" else "WHERE " + keyWhere.joinToString(" AND ")}
            ORDER BY $order, t.year DESC
            LIMIT ?
        """.trimIndent()
        val all = ArrayList<Any>()
        all += type.name; all.addAll(args); all += type.name; all += pid
        all += type.name; all.addAll(args); all += pid
        all.addAll(keyArgs)
        if (sort == GridSort.DEFAULT) all.addAll(kwArgs)
        all += limit
        return SimpleSQLiteQuery(sql, all.toTypedArray())
    }

    private fun rule(r: TitleRule, where: MutableList<String>, args: MutableList<Any>) {
        if (r.anyGenre.isNotEmpty()) {
            where += r.anyGenre.joinToString(" OR ", "(", ")") { "t.genreIds LIKE ?" }
            r.anyGenre.forEach { args += "%,$it,%" }
        }
        r.noGenre.forEach { where += "(t.genreIds IS NULL OR t.genreIds NOT LIKE ?)"; args += "%,$it,%" }
        if (r.requireKeyword && r.keywords.isNotEmpty()) {
            where += r.keywords.joinToString(" OR ", "(", ")") { "t.keywords LIKE ?" }
            r.keywords.forEach { args += "%|$it|%" }
        }
        r.minRating?.let { where += "t.rating >= ?"; args += it }
        r.minVotes?.let { where += "t.voteCount >= ?"; args += it }
        r.maxVotes?.let { where += "t.voteCount <= ?"; args += it }
        r.maxRuntime?.let { where += "(t.runtimeMin IS NULL OR t.runtimeMin <= ?)"; args += it }
        if (r.allAudiences) where += "(t.certification IS NULL OR t.certification IN ('TP', 'U', '0', '0+'))"
        r.language?.let { where += "t.originalLanguage = ?"; args += it }
        r.minYear?.let { where += "t.year >= ?"; args += it }
        r.maxYear?.let { where += "t.year <= ?"; args += it }
        r.minRuntime?.let { where += "t.runtimeMin >= ?"; args += it }
        r.maxSeasons?.let { where += "t.seasons <= ?"; args += it }
    }
}
