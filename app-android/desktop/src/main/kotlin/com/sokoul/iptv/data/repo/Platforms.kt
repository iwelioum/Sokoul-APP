package com.sokoul.iptv.data.repo

/**
 * Plateformes de streaming reconnues dans les noms de catégories du fournisseur
 * (« [MULTI-LANG] NETFLIX », « [EN] AMAZON PRIME+ », « [IT] APPLETV »…).
 */
enum class Platform(val label: String, val color: Long, val accent: Long, val tagline: String, private val pattern: String) {
    NETFLIX("Netflix", 0xFFE50914, 0xFF831010, "Les productions Netflix dont tout le monde parle.", """\bNETFLIX\b"""),
    DISNEY("Disney+", 0xFF0E47A1, 0xFF0A1C5C, "Magie, aventures et héros légendaires.", """\bDISNEY\s*\+?"""),
    PRIME("Prime Video", 0xFF00A8E1, 0xFF0B3A5C, "Les exclusivités Prime Video.", """\b(AMAZON|PRIME\s*VIDEO|PRIME\s*\+|PRIME)\b"""),
    APPLE("Apple TV+", 0xFF2B2B2B, 0xFF0D0D0D, "Les créations Apple Originals.", """\bAPPLE\s*TV\s*\+?|\bAPPLETV\b"""),
    MAX("Max", 0xFF5822F5, 0xFF1E0B5C, "Les grandes séries et les films HBO.", """\b(HBO|HBO\s*MAX)\b"""),
    CANAL("Canal+", 0xFF1A1A1A, 0xFF000000, "Créations originales et cinéma Canal+.", """\b(MY\s*)?CANAL\s*\+|\bCANAL\s*PLUS\b"""),
    PARAMOUNT("Paramount+", 0xFF0064FF, 0xFF00296B, "Les franchises et séries Paramount+.", """\bPARAMOUNT"""),
    HULU("Hulu", 0xFF1CE783, 0xFF0B5C35, "Les séries originales Hulu.", """\bHULU\b"""),
    SKY("Sky", 0xFF0072C9, 0xFF0B2F5C, "Les séries Sky Originals.", """\bSKY\b"""),
    ;

    private val regex = Regex(pattern, RegexOption.IGNORE_CASE)

    fun matches(categoryName: String): Boolean = regex.containsMatchIn(stripLang(categoryName))

    companion object {
        fun detect(categoryName: String): Platform? = entries.firstOrNull { it.matches(categoryName) }
    }
}

/**
 * Thèmes éditoriaux déduits des catégories (genres, années, nouveautés) avec leur phrase d'accroche.
 * Ordre = ordre d'affichage des rangées.
 */
enum class Theme(val title: String, val tagline: String, private val pattern: String, val seriesTitle: String? = null, val seriesTagline: String? = null) {
    NEW("Tout juste arrivés", "Les dernières sorties ajoutées au catalogue.", """NEW\s*RELEASE|R[EÉ]CEMM?ENT|LATEST|NOUVEAU|DERNI[EÈ]RES?\s*SORTIES""",
        "Nouvelles séries", "Les dernières séries et saisons ajoutées."),
    HORROR("Frissons garantis", "À regarder les lumières allumées.", """HORROR|HORREUR|[EÉ]POUVANTE"""),
    SUPERHERO("Super-héros", "Capes, pouvoirs et univers étendus.", """SUPER\s*-?\s*H[EÉ]RO"""),
    ACTION("Adrénaline", "Explosions, poursuites et héros sans peur.", """\bACTION\b|\bWAR\b|GUERRE"""),
    COMEDY("Fous rires", "De quoi rire jusqu'au générique.", """COM[EÉ]D(Y|IE)"""),
    SCIFI("Autres mondes", "Science-fiction et fantastique, pour s'évader.", """SCI\s*-?\s*FI|FANTAS|SCIENCE"""),
    CRIME("Enquêtes & suspense", "Le coupable est peut-être sous vos yeux.", """CRIME|MYST|THRILLER|POLICIER"""),
    FAMILY("En famille", "Pour petits et grands, bien installés sur le canapé.", """FAMIL|ENFANT|\bKIDS?\b|CHILDREN|JEUNESSE"""),
    ANIME("Animés & mangas", "L'animation japonaise dans toute sa splendeur.", """ANIM[EÉ]|MANGA"""),
    ASIA("Coup de cœur asiatique", "K-dramas et pépites venues d'Asie.", """KOREAN|K-?DRAMA|ASIA|ASIATIQUE"""),
    REALITY("Télé-réalité", "Rebondissements, clashs et confessions.", """T[EÉ]L[EÉ]\s*-?\s*R[EÉ]ALIT|REALITY"""),
    DOCS("Comprendre le monde", "Des documentaires captivants.", """DOCUMENT"""),
    CLASSIC("Les grands classiques", "Les films qui ont marqué l'histoire du cinéma.", """CLASSI"""),
    UHD("En 4K Ultra HD", "Une image à couper le souffle.", """\b4K\b|\bUHD\b"""),
    CHRISTMAS("Magie de Noël", "Des histoires qui sentent le chocolat chaud.", """NO[EË]L|CHRISTMAS|XMAS"""),
    ;

    private val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    fun matches(categoryName: String) = regex.containsMatchIn(stripLang(categoryName))

    companion object {
        fun detect(categoryName: String): List<Theme> = entries.filter { it.matches(categoryName) }
    }
}

private val LANG_PREFIX = Regex("""^\s*[\[(]\s*([A-Za-z\- ]{2,12})\s*[\])]\s*|^\s*([A-Z]{2,5})\s*[|:\-]\s+""")
private val TOP_YEAR = Regex("""\bTOP\s*(\d{4})\b|\b(\d{4})\s*TOP\b""", RegexOption.IGNORE_CASE)

/** Nom de catégorie sans préfixe de langue (« [FR] FILMS 4K » → « FILMS 4K »). */
fun stripLang(name: String): String = LANG_PREFIX.find(name)?.let { name.substring(it.range.last + 1) } ?: name

/** Langue d'une catégorie d'après son préfixe : FR, MULTI, EN… ou null. */
fun categoryLang(name: String): String? {
    val m = LANG_PREFIX.find(name) ?: return null
    val code = (m.groupValues[1].ifEmpty { m.groupValues[2] }).uppercase().trim()
    return when {
        code.startsWith("MULTI") -> "MULTI"
        code == "VF" || code == "FRA" || code == "FRENCH" -> "FR"
        code == "UK" || code == "US" || code == "ENG" -> "EN"
        else -> code
    }
}

/**
 * Priorité d'une catégorie pour l'utilisateur francophone : FR, puis MULTI, puis EN / sans préfixe,
 * les autres langues ensuite (leurs œuvres restent accessibles via la recherche et « Toutes les catégories »).
 */
fun langRank(name: String): Int = when (categoryLang(name)) {
    "FR" -> 0
    "MULTI" -> 1
    "EN", null -> 2
    else -> 3
}

/** Année d'une catégorie « TOP 2025 MOVIES ». */
fun topYear(name: String): Int? = TOP_YEAR.find(name)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }?.takeIf { it in 1950..2100 }

/** Catégorie d'adultes : jamais mise en avant dans les rangées éditoriales. */
fun isAdultCategory(name: String): Boolean = Regex("""\b(XXX|ADULT|18\s*\+|PORN|EROTI)""", RegexOption.IGNORE_CASE).containsMatchIn(name)
