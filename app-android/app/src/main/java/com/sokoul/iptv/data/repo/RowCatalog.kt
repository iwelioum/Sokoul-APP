package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.ContentType
import kotlin.math.abs

/**
 * Une rangée éditoriale décrite par une règle sur les fiches TMDB indexées (genres, mots-clés, époque, langue…).
 * [titles] : plusieurs couples titre / accroche, dont un est choisi chaque jour ([title]) ; [noun] sert aux titres
 * personnels (« Vous avez un faible pour les thrillers »).
 */
data class RowSpec(
    val id: String,
    val type: ContentType,
    val rule: TitleRule,
    val titles: List<Pair<String, String>>,
    val noun: String? = null,
    val decade: Int? = null,
    /** Priorité éditoriale (0..1) : départage les rangées quand l'utilisateur n'a pas encore d'historique. */
    val weight: Double = 0.5,
) {
    val genres: Set<Int> get() = rule.anyGenre
    val keywords: Set<String> get() = rule.keywords
    val language: String? get() = rule.language
    /** Genre principal : deux rangées voisines n'ont jamais le même. */
    val primaryGenre: Int? get() = rule.anyGenre.firstOrNull()

    /** Titre et accroche du jour : stables dans la journée, différents d'un jour à l'autre. */
    fun title(day: Int): Pair<String, String> = titles[abs(id.hashCode() + day) % titles.size]

    /** Titre personnel (rangée remontée par les goûts de l'utilisateur), si la rangée s'y prête. */
    fun personalTitle(day: Int): Pair<String, String>? {
        val n = noun ?: return null
        val options = listOf(
            "Vous avez un faible pour $n" to "On a remarqué. En voici d'autres.",
            "Encore plus de $n" to "Parce que vous en redemandez.",
            "Votre dose de $n" to "Choisis d'après ce que vous regardez.",
        )
        return options[abs(id.hashCode() + day) % options.size]
    }
}

/** Le catalogue des rangées éditoriales (films et séries). */
object RowCatalog {
    private val M = ContentType.MOVIE
    private val S = ContentType.SERIES

    private fun g(vararg ids: Int) = ids.toSet()
    private fun kw(vararg k: String) = k.toSet()
    private fun t(vararg pairs: Pair<String, String>) = pairs.toList()

    val all: List<RowSpec> by lazy { movies() + series() }

    fun forType(type: ContentType): List<RowSpec> = all.filter { it.type == type }

    fun byId(id: String): RowSpec? = all.firstOrNull { it.id == id }

    private fun movies(): List<RowSpec> = with(TmdbGenres) {
        listOf(
            // --- genres ---
            RowSpec("m-action", M, TitleRule(g(ACTION), minRating = 6.0), t(
                "Action à haute dose" to "Poursuites, combats et cascades.",
                "Ça explose de partout" to "Le pied au plancher du début à la fin.",
                "Pur concentré d'action" to "Pour ceux qui ne tiennent pas en place.",
            ), "les films d'action", weight = 0.8),
            RowSpec("m-adventure", M, TitleRule(g(ADVENTURE), noGenre = g(ANIMATION), minRating = 6.2), t(
                "Grandes aventures" to "Cartes au trésor, terres inconnues et héros intrépides.",
                "Cap sur l'aventure" to "Partez loin sans quitter le canapé.",
                "L'appel du large" to "Des voyages qui ne s'oublient pas.",
            ), "les films d'aventure", weight = 0.7),
            RowSpec("m-animation", M, TitleRule(g(ANIMATION), minRating = 6.5), t(
                "Le meilleur de l'animation" to "Pour les petits… et surtout les grands.",
                "Dessins animés de légende" to "Des mondes qui ne vieillissent pas.",
                "Animation : nos coups de cœur" to "Image par image, des merveilles.",
            ), "les films d'animation", weight = 0.6),
            RowSpec("m-comedy", M, TitleRule(g(COMEDY), noGenre = g(HORROR), minRating = 6.0), t(
                "Comédies à ne pas manquer" to "Fous rires garantis.",
                "Rire un bon coup" to "La meilleure thérapie, sans ordonnance.",
                "Pour se dérider" to "Des comédies qui font du bien.",
            ), "les comédies", weight = 0.8),
            RowSpec("m-crime", M, TitleRule(g(CRIME), minRating = 6.3), t(
                "Crimes et enquêtes" to "Flics, truands et zones grises.",
                "Du côté obscur de la loi" to "Braquages, mafias et règlements de comptes.",
                "Les grands polars" to "On ne sort pas indemne de ces affaires-là.",
            ), "les polars", weight = 0.7),
            RowSpec("m-documentary", M, TitleRule(g(DOCUMENTARY), minRating = 6.8), t(
                "Documentaires captivants" to "La réalité dépasse souvent la fiction.",
                "Le monde tel qu'il est" to "Des histoires vraies qui ouvrent les yeux.",
                "Pour apprendre en regardant" to "Curieux, ces films sont pour vous.",
            ), "les documentaires", weight = 0.3),
            RowSpec("m-drama", M, TitleRule(g(DRAMA), minRating = 7.0, minVotes = 300), t(
                "Drames inoubliables" to "Des histoires qui restent longtemps.",
                "Cinéma d'émotion" to "Des personnages qu'on n'oublie pas.",
                "Les grands drames" to "Puissants, intenses, bouleversants.",
            ), "les drames", weight = 0.6),
            RowSpec("m-family", M, TitleRule(g(FAMILY), noGenre = g(HORROR, THRILLER, CRIME, WAR), allAudiences = true, minRating = 6.0), t(
                "Soirée en famille" to "Tout le monde sur le canapé.",
                "Pour petits et grands" to "Des films qui mettent tout le monde d'accord.",
                "Films à partager" to "Popcorn pour tout le monde.",
            ), "les films familiaux", weight = 0.5),
            RowSpec("m-fantasy", M, TitleRule(g(FANTASY), noGenre = g(ANIMATION), minRating = 6.2), t(
                "Mondes fantastiques" to "Magie, dragons et royaumes oubliés.",
                "Au-delà du réel" to "Là où tout devient possible.",
                "Épopées fantastiques" to "Des légendes plus grandes que nature.",
            ), "le fantastique", weight = 0.6),
            RowSpec("m-history", M, TitleRule(g(HISTORY), minRating = 6.8), t(
                "L'Histoire en grand" to "Rois, batailles et destins hors du commun.",
                "Il était une fois l'Histoire" to "Les grandes heures du passé, revécues.",
                "Fresques historiques" to "Le passé comme si vous y étiez.",
            ), "les films historiques", weight = 0.4),
            RowSpec("m-horror", M, TitleRule(g(HORROR), minRating = 5.8), t(
                "Films d'horreur" to "Âmes sensibles, s'abstenir.",
                "Nuits blanches assurées" to "Laissez la lumière allumée.",
                "Peur sur la ville" to "Cris, ombres et portes qui grincent.",
            ), "les films d'horreur", weight = 0.6),
            RowSpec("m-music", M, TitleRule(g(MUSIC), minRating = 6.5), t(
                "Musique, maestro" to "Des films qui ont du rythme.",
                "Sur un air de cinéma" to "Concerts, destins de musiciens et comédies musicales.",
                "Bandes originales et grandes voix" to "À regarder le son bien fort.",
            ), "les films musicaux", weight = 0.3),
            RowSpec("m-mystery", M, TitleRule(g(MYSTERY), minRating = 6.3), t(
                "Mystères et énigmes" to "Saurez-vous trouver avant la fin ?",
                "Qui a fait le coup ?" to "Indices, fausses pistes et révélations.",
                "Le goût du mystère" to "Des intrigues à démêler.",
            ), "les films à énigmes", weight = 0.6),
            RowSpec("m-romance", M, TitleRule(g(ROMANCE), noGenre = g(HORROR), minRating = 6.2), t(
                "Histoires d'amour" to "Coups de foudre et cœurs brisés.",
                "Romances à savourer" to "Pour croire encore au grand amour.",
                "L'amour au cinéma" to "Des rencontres qu'on n'oublie pas.",
            ), "les romances", weight = 0.5),
            RowSpec("m-scifi", M, TitleRule(g(SCIFI), minRating = 6.2), t(
                "Science-fiction" to "Le futur commence ici.",
                "Vers l'infini et au-delà" to "Robots, voyages spatiaux et mondes à venir.",
                "Demain, déjà" to "Des visions du futur qui donnent le vertige.",
            ), "la science-fiction", weight = 0.7),
            RowSpec("m-thriller", M, TitleRule(g(THRILLER), minRating = 6.2), t(
                "Suspense à couper le souffle" to "Impossible de décrocher.",
                "Thrillers qui tiennent en haleine" to "Le cœur bat plus vite à chaque scène.",
                "Sur le fil du rasoir" to "Tension maximale, jusqu'au dernier plan.",
            ), "les thrillers", weight = 0.8),
            RowSpec("m-war", M, TitleRule(g(WAR), minRating = 6.5), t(
                "Films de guerre" to "Le courage au cœur du chaos.",
                "Au front" to "Des récits de guerre saisissants.",
                "Ils ont écrit l'Histoire au combat" to "Héroïsme, sacrifice et fraternité.",
            ), "les films de guerre", weight = 0.4),
            RowSpec("m-western", M, TitleRule(g(WESTERN), minRating = 6.3), t(
                "Westerns" to "Duels au soleil et grands espaces.",
                "Il était une fois dans l'Ouest" to "Cow-boys, shérifs et hors-la-loi.",
                "La conquête de l'Ouest" to "Poussière, chevaux et règlements de comptes.",
            ), "les westerns", weight = 0.3),

            // --- époques ---
            RowSpec("m-80s", M, TitleRule(minYear = 1980, maxYear = 1989, minRating = 6.5, minVotes = 300), t(
                "Les années 80" to "Synthés, néons et grands classiques.",
                "Retour dans les années 80" to "L'âge d'or du cinéma populaire.",
                "Nostalgie 80's" to "Les films qui ont marqué une génération.",
            ), decade = 1980, weight = 0.4),
            RowSpec("m-90s", M, TitleRule(minYear = 1990, maxYear = 1999, minRating = 6.5, minVotes = 300), t(
                "Les années 90" to "VHS, blockbusters et films cultes.",
                "Culte des années 90" to "Les films qu'on se repasse en boucle.",
                "Nostalgie 90's" to "Toute une époque en quelques films.",
            ), decade = 1990, weight = 0.5),
            RowSpec("m-00s", M, TitleRule(minYear = 2000, maxYear = 2009, minRating = 6.5, minVotes = 300), t(
                "Les années 2000" to "Le cinéma du nouveau millénaire.",
                "Souvenirs des années 2000" to "Les films de notre adolescence.",
                "Classiques des années 2000" to "Déjà cultes.",
            ), decade = 2000, weight = 0.5),
            RowSpec("m-10s", M, TitleRule(minYear = 2010, maxYear = 2019, minRating = 6.8, minVotes = 500), t(
                "Le meilleur des années 2010" to "La décennie des grandes sagas.",
                "Les incontournables 2010-2019" to "Ceux qu'il ne fallait pas rater.",
                "Années 2010 : la sélection" to "Une décennie de pépites.",
            ), decade = 2010, weight = 0.5),
            RowSpec("m-classics", M, TitleRule(maxYear = 1979, minRating = 7.0, minVotes = 200), t(
                "Grands classiques" to "Le cinéma qui a tout inventé.",
                "Classiques intemporels" to "Ils n'ont pas pris une ride.",
                "Avant 1980" to "Les chefs-d'œuvre d'hier.",
            ), weight = 0.3),

            // --- cinémas du monde ---
            RowSpec("m-lang-fr", M, TitleRule(language = "fr", minRating = 6.0), t(
                "Cinéma français" to "Le meilleur de chez nous.",
                "Made in France" to "Comédies, drames et polars français.",
                "Films français à voir" to "Une certaine idée du cinéma.",
            ), "le cinéma français", weight = 0.7),
            RowSpec("m-lang-ko", M, TitleRule(language = "ko", minRating = 6.5), t(
                "Cinéma coréen" to "Thrillers chocs et émotions fortes.",
                "La vague coréenne" to "Le cinéma qui bouscule tout.",
                "Pépites de Corée" to "Audacieux, surprenants, inoubliables.",
            ), "le cinéma coréen", weight = 0.4),
            RowSpec("m-lang-ja", M, TitleRule(language = "ja", minRating = 6.8), t(
                "Cinéma japonais" to "Animation, samouraïs et poésie.",
                "Venus du Japon" to "Un autre regard sur le monde.",
                "Japon : la sélection" to "Des films à part.",
            ), "le cinéma japonais", weight = 0.3),
            RowSpec("m-lang-es", M, TitleRule(language = "es", minRating = 6.3), t(
                "Cinéma hispanique" to "Passion, suspense et soleil.",
                "D'Espagne et d'Amérique latine" to "Des films qui ont du caractère.",
                "Ola ! La sélection hispanique" to "Le meilleur en VO espagnole.",
            ), weight = 0.3),
            RowSpec("m-lang-hi", M, TitleRule(language = "hi", minRating = 6.3), t(
                "Bollywood et cinéma indien" to "Couleurs, musique et grands sentiments.",
                "Venus d'Inde" to "Un cinéma plus grand que la vie.",
                "L'Inde à l'écran" to "Spectacle garanti.",
            ), weight = 0.2),

            // --- thèmes ---
            RowSpec("m-heist", M, TitleRule(keywords = kw("heist", "bank robbery", "robbery"), requireKeyword = true, minRating = 6.0), t(
                "Casses et braquages" to "Le plan parfait… ou presque.",
                "Le casse du siècle" to "Des coups montés au millimètre.",
                "Haut les mains" to "Coffres-forts, plans et trahisons.",
            ), "les films de casse", weight = 0.5),
            RowSpec("m-space", M, TitleRule(keywords = kw("space travel", "outer space", "astronaut", "spacecraft"), requireKeyword = true, minRating = 6.0), t(
                "Odyssées spatiales" to "Bienvenue dans l'espace.",
                "Perdus dans les étoiles" to "Astronautes, vaisseaux et planètes lointaines.",
                "Direction l'espace" to "Le grand vide n'a jamais été aussi plein.",
            ), "les films d'espace", weight = 0.5),
            RowSpec("m-survival", M, TitleRule(keywords = kw("survival"), requireKeyword = true, minRating = 6.0), t(
                "Survivre à tout prix" to "Seuls contre tous… et contre la nature.",
                "Instinct de survie" to "Jusqu'où iriez-vous pour rester en vie ?",
                "Rester en vie" to "Chaque minute compte.",
            ), "les films de survie", weight = 0.4),
            RowSpec("m-superhero", M, TitleRule(keywords = kw("superhero", "based on comic", "marvel cinematic universe (mcu)", "dc extended universe (dceu)"), requireKeyword = true, minRating = 5.8), t(
                "Super-héros" to "Capes, masques et pouvoirs extraordinaires.",
                "Héros et super-pouvoirs" to "Sauver le monde, encore une fois.",
                "L'univers des comics" to "Des bulles au grand écran.",
            ), "les super-héros", weight = 0.7),
            RowSpec("m-true", M, TitleRule(keywords = kw("based on true story", "biography", "true crime"), requireKeyword = true, minRating = 6.5), t(
                "Tirés d'histoires vraies" to "Plus fort que la fiction.",
                "Ça s'est vraiment passé" to "Des destins réels, racontés au cinéma.",
                "Histoires vraies" to "Incroyable, mais vrai.",
            ), "les histoires vraies", weight = 0.5),
            RowSpec("m-sport", M, TitleRule(keywords = kw("sports", "boxing", "football (soccer)", "basketball", "baseball", "sport"), requireKeyword = true, minRating = 6.0), t(
                "Esprit sportif" to "Sueur, dépassement et victoires.",
                "Sur le terrain" to "Des films qui donnent envie de se dépasser.",
                "Le sport au cinéma" to "Rings, stades et grandes finales.",
            ), "les films de sport", weight = 0.3),
            RowSpec("m-road", M, TitleRule(keywords = kw("road trip", "road movie"), requireKeyword = true, minRating = 6.0), t(
                "Road movies" to "La route comme seule destination.",
                "Sur la route" to "Bagnoles, rencontres et grands espaces.",
                "Prendre la route" to "Des voyages qui changent tout.",
            ), weight = 0.3),
            RowSpec("m-zombie", M, TitleRule(keywords = kw("zombie", "zombie apocalypse"), requireKeyword = true, minRating = 5.5), t(
                "Zombies" to "Ils sont lents. Enfin, pas tous.",
                "L'invasion des morts-vivants" to "Visez la tête.",
                "Apocalypse zombie" to "La fin du monde a de drôles de têtes.",
            ), "les films de zombies", weight = 0.3),
            RowSpec("m-vampire", M, TitleRule(keywords = kw("vampire"), requireKeyword = true, minRating = 5.5), t(
                "Vampires" to "Des crocs, des capes et des nuits sans fin.",
                "Soif de sang" to "Les créatures de la nuit sont de sortie.",
                "Morsures nocturnes" to "Fermez bien les volets.",
            ), weight = 0.2),
            RowSpec("m-spy", M, TitleRule(keywords = kw("spy", "espionage", "secret agent", "cia"), requireKeyword = true, minRating = 6.0), t(
                "Espions et agents secrets" to "Missions impossibles et doubles jeux.",
                "Mission top secret" to "Gadgets, filatures et trahisons.",
                "Le monde de l'espionnage" to "Ne faites confiance à personne.",
            ), "les films d'espionnage", weight = 0.5),
            RowSpec("m-court", M, TitleRule(keywords = kw("courtroom", "lawyer", "trial", "legal drama"), requireKeyword = true, minRating = 6.3), t(
                "Au tribunal" to "Objection, votre honneur !",
                "Procès et plaidoiries" to "La vérité se joue à la barre.",
                "Affaires judiciaires" to "Coupable ou innocent ?",
            ), weight = 0.3),
            RowSpec("m-ww2", M, TitleRule(keywords = kw("world war ii", "nazi", "d-day"), requireKeyword = true, minRating = 6.5), t(
                "La Seconde Guerre mondiale" to "Résistance, front et héros anonymes.",
                "1939-1945" to "Des récits pour ne pas oublier.",
                "Les années de guerre" to "Courage et sacrifice.",
            ), weight = 0.3),
            RowSpec("m-romcom", M, TitleRule(g(ROMANCE), keywords = kw("romantic comedy"), requireKeyword = true, minRating = 5.8), t(
                "Comédies romantiques" to "L'amour, avec le sourire.",
                "Love & rires" to "Des histoires d'amour qui finissent bien.",
                "Feel-good et romance" to "Parfaites pour un dimanche soir.",
            ), "les comédies romantiques", weight = 0.4),
            RowSpec("m-ai", M, TitleRule(keywords = kw("artificial intelligence", "robot", "android", "cyborg"), requireKeyword = true, minRating = 6.0), t(
                "Robots et intelligences artificielles" to "Quand la machine se met à penser.",
                "L'ère des machines" to "Androïdes, IA et futur incertain.",
                "Ghost in the machine" to "Humains ou machines ?",
            ), weight = 0.4),
            RowSpec("m-dystopia", M, TitleRule(keywords = kw("dystopia", "post-apocalyptic future", "post-apocalyptic"), requireKeyword = true, minRating = 6.0), t(
                "Fins du monde et dystopies" to "Demain ne sera pas rose.",
                "Après l'apocalypse" to "Survivre dans les ruines.",
                "Futurs sombres" to "Des mondes à éviter… à l'écran seulement.",
            ), weight = 0.4),
            RowSpec("m-revenge", M, TitleRule(keywords = kw("revenge", "vigilante"), requireKeyword = true, minRating = 6.2), t(
                "Vengeance" to "Un plat qui se mange froid.",
                "Œil pour œil" to "Ils n'auraient jamais dû les provoquer.",
                "La revanche" to "Rien ne les arrêtera.",
            ), weight = 0.4),
            RowSpec("m-martial", M, TitleRule(keywords = kw("martial arts", "kung fu", "samurai"), requireKeyword = true, minRating = 6.0), t(
                "Arts martiaux" to "Kung-fu, sabres et chorégraphies de combat.",
                "Combats épiques" to "Chaque coup compte.",
                "La voie du guerrier" to "Maîtres, disciples et duels légendaires.",
            ), weight = 0.3),
            RowSpec("m-coming", M, TitleRule(keywords = kw("coming of age", "high school", "teenager"), requireKeyword = true, minRating = 6.3), t(
                "Grandir" to "L'adolescence, ses premières fois et ses drames.",
                "Années lycée" to "Amitiés, amours et rébellions.",
                "Récits d'apprentissage" to "Ces étés qui changent une vie.",
            ), weight = 0.3),
            RowSpec("m-monsters", M, TitleRule(keywords = kw("monster", "kaiju", "giant monster", "dinosaur", "creature"), requireKeyword = true, minRating = 5.6), t(
                "Monstres et créatures" to "Plus c'est gros, plus c'est bon.",
                "Bêtes géantes" to "Dinosaures, kaijus et autres colosses.",
                "Créatures de cauchemar" to "Elles sortent de l'ombre.",
            ), weight = 0.3),
            RowSpec("m-darkcomedy", M, TitleRule(keywords = kw("dark comedy", "black comedy", "satire"), requireKeyword = true, minRating = 6.5), t(
                "Humour noir" to "On ne devrait pas rire… et pourtant.",
                "Satires grinçantes" to "Le rire qui gratte.",
                "Comédies décalées" to "Pas pour tout le monde. Peut-être pour vous.",
            ), weight = 0.3),
            RowSpec("m-short", M, TitleRule(minRuntime = 60, maxRuntime = 89, minRating = 6.3, minVotes = 200), t(
                "Moins de 1 h 30" to "Pour une soirée qui ne finit pas trop tard.",
                "Vite vus, bien vus" to "Des films courts et efficaces.",
                "Format court" to "Pas besoin de toute la soirée.",
            ), weight = 0.4),
            RowSpec("m-acclaimed", M, TitleRule(minRating = 8.0, minVotes = 1_000), t(
                "Acclamés par tous" to "Le public et la critique sont d'accord.",
                "Les mieux notés" to "Notés 8 et plus.",
                "La crème du catalogue" to "Ce qui se fait de mieux.",
            ), weight = 0.9),
        ) + moodSpecs(M)
    }

    private fun series(): List<RowSpec> = with(TmdbGenres) {
        listOf(
            RowSpec("s-action", S, TitleRule(g(TV_ACTION_ADVENTURE), minRating = 6.5), t(
                "Séries d'action" to "Des épisodes qui filent à toute allure.",
                "Action et aventure" to "Missions, combats et rebondissements.",
                "Adrénaline en série" to "Un épisode de plus ? Évidemment.",
            ), "les séries d'action", weight = 0.7),
            RowSpec("s-animation", S, TitleRule(g(ANIMATION), noGenre = g(TV_KIDS), minRating = 7.0), t(
                "Séries animées" to "L'animation, version longue.",
                "Animés et dessins animés" to "Des univers à dévorer.",
                "Le meilleur de l'animé" to "Japanimation et séries cultes.",
            ), "les séries animées", weight = 0.5),
            RowSpec("s-comedy", S, TitleRule(g(COMEDY), minRating = 6.8), t(
                "Séries comiques" to "Des épisodes courts pour rire beaucoup.",
                "Sitcoms et comédies" to "Vos nouvelles bandes de potes.",
                "Rires en série" to "Une saison, et vous êtes accro.",
            ), "les séries comiques", weight = 0.7),
            RowSpec("s-crime", S, TitleRule(g(CRIME), minRating = 7.0), t(
                "Séries criminelles" to "Enquêtes, gangs et flics au bord du gouffre.",
                "Polars en série" to "Chaque épisode, un nouvel indice.",
                "Crimes et châtiments" to "Le crime ne paie pas… ou si ?",
            ), "les séries policières", weight = 0.8),
            RowSpec("s-documentary", S, TitleRule(g(DOCUMENTARY), minRating = 7.0), t(
                "Docu-séries" to "La réalité, épisode par épisode.",
                "Séries documentaires" to "Enquêtes, nature et grandes histoires.",
                "Pour les curieux" to "Apprendre sans s'ennuyer.",
            ), weight = 0.3),
            RowSpec("s-drama", S, TitleRule(g(DRAMA), minRating = 7.5, minVotes = 300), t(
                "Grandes séries dramatiques" to "Des personnages qu'on suit pendant des années.",
                "Drames addictifs" to "Impossible de s'arrêter.",
                "Séries à émotions" to "Rires, larmes et cliffhangers.",
            ), "les séries dramatiques", weight = 0.7),
            RowSpec("s-family", S, TitleRule(g(FAMILY, TV_KIDS), noGenre = g(CRIME, TV_ACTION_ADVENTURE), allAudiences = true), t(
                "Séries pour toute la famille" to "Des épisodes à partager.",
                "Pour les plus jeunes" to "Des séries pour les enfants (et leurs parents).",
                "En famille" to "Tout le monde devant l'écran.",
            ), weight = 0.4),
            RowSpec("s-mystery", S, TitleRule(g(MYSTERY), minRating = 7.0), t(
                "Séries à mystère" to "Chaque réponse amène une nouvelle question.",
                "Énigmes en série" to "On ne devine jamais la fin.",
                "Intrigues et secrets" to "Tout le monde cache quelque chose.",
            ), "les séries à énigmes", weight = 0.6),
            RowSpec("s-reality", S, TitleRule(g(TV_REALITY)), t(
                "Téléréalité et divertissement" to "Pour décompresser.",
                "Divertissement" to "Concours, aventures et coulisses.",
                "Pour se vider la tête" to "Sans prise de tête.",
            ), weight = 0.2),
            RowSpec("s-scifi", S, TitleRule(g(TV_SCIFI_FANTASY), minRating = 7.0), t(
                "Science-fiction et fantastique" to "D'autres mondes, saison après saison.",
                "Séries d'ailleurs" to "Dragons, vaisseaux et dimensions parallèles.",
                "Univers étendus" to "Plongez dans des mondes immenses.",
            ), "la science-fiction en série", weight = 0.7),
            RowSpec("s-war", S, TitleRule(g(TV_WAR_POLITICS), minRating = 7.0), t(
                "Guerre et pouvoir" to "Complots, batailles et coulisses du pouvoir.",
                "Jeux de pouvoir" to "La politique comme champ de bataille.",
                "Intrigues politiques" to "Qui tire vraiment les ficelles ?",
            ), weight = 0.4),
            RowSpec("s-western", S, TitleRule(g(WESTERN), minRating = 6.8), t(
                "Séries western" to "L'Ouest sauvage, saison après saison.",
                "Grands espaces" to "Ranchs, hors-la-loi et duels.",
                "Frontières sauvages" to "L'Ouest n'a jamais été aussi vivant.",
            ), weight = 0.2),

            // --- époques ---
            RowSpec("s-classics", S, TitleRule(maxYear = 2009, minRating = 7.8, minVotes = 300), t(
                "Séries cultes" to "Les monuments de la télé.",
                "Ces séries qui ont tout changé" to "L'âge d'or des séries.",
                "Indémodables" to "On ne s'en lasse pas.",
            ), weight = 0.6),

            // --- séries du monde ---
            RowSpec("s-lang-fr", S, TitleRule(language = "fr", minRating = 6.5), t(
                "Séries françaises" to "Le meilleur de la création française.",
                "Made in France" to "Polars, comédies et sagas d'ici.",
                "Séries de chez nous" to "Fabriquées en France.",
            ), "les séries françaises", weight = 0.6),
            RowSpec("s-lang-ko", S, TitleRule(language = "ko", minRating = 7.0), t(
                "K-dramas" to "Romances, thrillers et cliffhangers coréens.",
                "Séries coréennes" to "La vague coréenne, épisode après épisode.",
                "Venues de Corée" to "Addictives dès le premier épisode.",
            ), "les K-dramas", weight = 0.4),
            RowSpec("s-lang-ja", S, TitleRule(g(ANIMATION), language = "ja", minRating = 7.2), t(
                "Animés japonais" to "Les grandes sagas de l'animation japonaise.",
                "Le meilleur de l'animé" to "Shōnen, seinen et chefs-d'œuvre.",
                "Japanimation" to "Des épisodes qu'on enchaîne.",
            ), "les animés", weight = 0.5),
            RowSpec("s-lang-en-gb", S, TitleRule(language = "en", keywords = kw("british", "london, england", "england"), requireKeyword = true, minRating = 7.0), t(
                "Séries britanniques" to "Humour anglais et polars brumeux.",
                "God Save the Series" to "La touche britannique.",
                "Made in UK" to "Thé, flegme et intrigues.",
            ), weight = 0.3),
            RowSpec("s-lang-es", S, TitleRule(language = "es", minRating = 6.8), t(
                "Séries espagnoles et latines" to "Braquages, passions et telenovelas.",
                "En VO espagnole" to "Des séries qui ont du caractère.",
                "¡ Ola ! La sélection" to "Le meilleur de l'Espagne et de l'Amérique latine.",
            ), weight = 0.3),
            RowSpec("s-nordic", S, TitleRule(keywords = kw("scandinavia", "sweden", "denmark", "norway", "iceland", "nordic noir"), requireKeyword = true, minRating = 6.8), t(
                "Polars nordiques" to "Le froid, la nuit et des crimes glaçants.",
                "Venues du Nord" to "Des séries aussi sombres que l'hiver.",
                "Nordic noir" to "Ambiance glaciale garantie.",
            ), weight = 0.3),

            // --- thèmes ---
            RowSpec("s-mini", S, TitleRule(maxSeasons = 1, minRating = 7.2, minVotes = 150), t(
                "Mini-séries" to "Une saison, une histoire complète.",
                "À regarder en un week-end" to "Pas de saison 2 à attendre.",
                "Courtes et intenses" to "Commencées samedi, finies dimanche.",
            ), weight = 0.6),
            RowSpec("s-true", S, TitleRule(keywords = kw("based on true story", "true crime", "biography"), requireKeyword = true, minRating = 6.8), t(
                "Histoires vraies en série" to "Faits réels et affaires célèbres.",
                "True crime" to "Des affaires qui ont fait la une.",
                "Inspirées de faits réels" to "La réalité, en plusieurs épisodes.",
            ), weight = 0.4),
            RowSpec("s-superhero", S, TitleRule(keywords = kw("superhero", "based on comic"), requireKeyword = true, minRating = 6.5), t(
                "Super-héros en série" to "Des pouvoirs, et des épisodes à la pelle.",
                "Héros masqués" to "Sauver le monde, une saison à la fois.",
                "Comics à l'écran" to "Les héros de papier en série.",
            ), weight = 0.4),
            RowSpec("s-teen", S, TitleRule(keywords = kw("high school", "teenager", "coming of age"), requireKeyword = true, minRating = 6.8), t(
                "Séries ados" to "Lycée, amours et secrets.",
                "Teen dramas" to "Grandir n'a jamais été simple.",
                "Années lycée" to "Les meilleures séries d'adolescents.",
            ), weight = 0.3),
            RowSpec("s-medical", S, TitleRule(keywords = kw("hospital", "doctor", "medical drama"), requireKeyword = true, minRating = 6.8), t(
                "Séries médicales" to "Urgences, bistouris et cas désespérés.",
                "Blouses blanches" to "La vie et la mort, à chaque garde.",
                "À l'hôpital" to "Le cœur de l'action bat aux urgences.",
            ), weight = 0.3),
            RowSpec("s-heist", S, TitleRule(keywords = kw("heist", "robbery", "con artist"), requireKeyword = true, minRating = 6.8), t(
                "Arnaques et braquages" to "Plans géniaux et coups tordus.",
                "Séries de casse" to "Le casse dure toute une saison.",
                "Coups montés" to "Les escrocs les plus brillants.",
            ), weight = 0.3),
            RowSpec("s-zombie", S, TitleRule(keywords = kw("zombie", "post-apocalyptic future", "dystopia"), requireKeyword = true, minRating = 6.5), t(
                "Fin du monde en série" to "Survivre, saison après saison.",
                "Après l'apocalypse" to "Le monde d'après fait peur.",
                "Mondes dévastés" to "Zombies, virus et dystopies.",
            ), weight = 0.3),
            RowSpec("s-acclaimed", S, TitleRule(minRating = 8.2, minVotes = 1_000), t(
                "Les séries les mieux notées" to "Notées 8 et plus par les spectateurs.",
                "Chefs-d'œuvre de la télé" to "Le panthéon des séries.",
                "Incontournables" to "Si vous ne deviez en voir qu'une.",
            ), weight = 0.9),
        ) + moodSpecs(S)
    }

    /** Les ambiances et micro-genres existants rejoignent le catalogue (mêmes règles, mêmes titres). */
    private fun moodSpecs(type: ContentType): List<RowSpec> {
        val p = if (type == ContentType.MOVIE) "m" else "s"
        val moods = Mood.entries.map { m -> RowSpec("$p-mood-${m.name.lowercase()}", type, m.rule(type), listOf(m.title(type) to m.tagline), weight = 0.6) }
        val micros = MicroGenre.entries.filter { it != MicroGenre.SPECTACLE || DeviceCaps.supports4k }
            .map { g -> RowSpec("$p-micro-${g.name.lowercase()}", type, g.rule(type), listOf(g.title to g.tagline), weight = 0.5) }
        return moods + micros
    }
}
