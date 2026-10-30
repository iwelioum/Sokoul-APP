package com.sokoul.iptv.data.repo

/**
 * Regroupement des doublons films/séries (même œuvre publiée en VF, MULTI, 4K, dans plusieurs catégories…).
 *
 * On ne fusionne jamais les lignes du fournisseur : chaque version garde sa ligne, on calcule seulement
 * une clé commune (`groupKey`). Règles volontairement prudentes :
 * - même identifiant TMDB → même œuvre ;
 * - sinon même titre normalisé ET même année (un titre sans année ne se regroupe jamais : « Dune » 1984 ≠ 2021) ;
 * - titres trop courts ou génériques : uniquement par TMDB ;
 * - une version TMDB et une version sans TMDB de même titre+année sont réunies (pont).
 */
object VodGrouping {
    /** À incrémenter quand les règles changent : les clés sont alors recalculées au lancement suivant. */
    const val RULES_VERSION = 3

    /** Ligne minimale nécessaire au calcul. */
    data class Row(val id: String, val title: String, val year: Int?, val tmdbId: String?)

    private val GENERIC = setOf(
        "episode", "film", "movie", "trailer", "bande annonce", "test", "untitled", "sans titre", "info", "bonus",
        "serie", "series", "show", "special", "documentaire", "live", "replay",
    )
    private val LEADING_ARTICLE = Regex("""^(le|la|les|l|un|une|des|the|a|an)\s+""")
    private val NON_ALNUM = Regex("""[^a-z0-9]+""")

    // Codes pays/langue que certains fournisseurs laissent collés au titre (« HU Chernobyl », « ALB Avatar »).
    // Liste fermée, en majuscules uniquement : « It Follows » ou « Us » ne sont pas touchés.
    private val COUNTRY_CODES = setOf(
        "AF", "AL", "ALB", "AR", "ARA", "AT", "AU", "BA", "BD", "BE", "BG", "BR", "CA", "CH", "CN", "CZ", "DE", "DK", "DZ", "EE",
        "EG", "EN", "ENG", "ES", "EXYU", "FI", "FR", "GE", "GER", "GR", "HI", "HR", "HU", "ID", "IE", "IL", "IN", "IR", "IT", "JP",
        "KR", "KU", "KUR", "LAT", "LT", "LV", "MA", "MK", "MX", "MY", "NL", "NO", "NZ", "PH", "PK", "PL", "PT", "QC", "RO", "RS", "RU", "SA",
        "SE", "SI", "SK", "SQ", "TA", "TE", "TH", "TN", "TR", "TW", "UA", "UK", "US", "VN",
        "KO", "JA", "ZH", "VI", "FA", "HE",
    )
    private val LEADING_CODE = Regex("""^([A-Z]{2,4})(?:\s*[|:•\-]\s*|\s+)(?=\S)""")
    private val MARKS = Regex("\\p{Mn}+")

    /** Comme `SyncEngine.searchable` : sans accents, en minuscules. */
    private fun searchable(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    /** Retire un code pays de tête (« HU Chernobyl » → « Chernobyl »), seulement s'il reste un titre. */
    fun stripCountry(title: String): String {
        val m = LEADING_CODE.find(title.trim()) ?: return title
        return if (m.groupValues[1] in COUNTRY_CODES) title.trim().substring(m.range.last + 1).trim() else title
    }

    fun tmdbKey(tmdbId: String?): String? =
        tmdbId?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.trimStart('0').isNotEmpty() }?.let { "tmdb:${it.trimStart('0')}" }

    /** Titre comparable : sans accents, ponctuation, article de tête ni année répétée en fin de titre. */
    fun normalizeTitle(title: String, year: Int?): String {
        var s = searchable(stripCountry(title)).replace('&', ' ').replace(NON_ALNUM, " ").trim()
        s = s.replace(LEADING_ARTICLE, "")
        if (year != null) s = s.removeSuffix(" $year").trim()
        return s
    }

    /** Clé par titre+année, ou null si le titre ne permet pas un rapprochement sûr. */
    fun titleKey(title: String, year: Int?): String? {
        if (year == null || year !in 1900..2100) return null
        val t = normalizeTitle(title, year)
        if (t.length < 3 || t in GENERIC || t.all(Char::isDigit)) return null
        return "t:$t|$year"
    }

    /** Clé calculée ligne par ligne (à l'insertion) ; le pont TMDB ↔ titre est fait par [assign]. */
    fun provisionalKey(row: Row): String = tmdbKey(row.tmdbId) ?: titleKey(row.title, row.year) ?: "id:${row.id}"

    /**
     * Clé définitive de chaque ligne (union-find) : chaque ligne appartient à exactement un groupe.
     * La clé d'un groupe est stable : la plus petite clé TMDB du groupe, sinon sa clé titre.
     */
    fun assign(rows: List<Row>): Map<String, String> {
        // clés calculées une seule fois par ligne (159k films : la normalisation coûte)
        val mks = arrayOfNulls<String>(rows.size)
        val tks = arrayOfNulls<String>(rows.size)
        rows.forEachIndexed { i, r -> mks[i] = tmdbKey(r.tmdbId); tks[i] = titleKey(r.title, r.year) }
        val parent = HashMap<String, String>(rows.size * 2)
        fun find(x: String): String {
            var r = x
            while (true) {
                val p = parent[r] ?: return r
                if (p == r) return r
                parent[p]?.let { gp -> parent[r] = gp } // compression de chemin
                r = p
            }
        }
        fun union(a: String, b: String) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) { if (ra < rb) parent[rb] = ra else parent[ra] = rb }
        }
        // Un titre+année porté par plusieurs identifiants TMDB différents désigne des œuvres distinctes
        // (homonymes) : les versions TMDB n'y sont alors pas rattachées.
        val tmdbByTitle = HashMap<String, MutableSet<String>>()
        for (i in rows.indices) {
            val tk = tks[i] ?: continue
            val mk = mks[i] ?: continue
            tmdbByTitle.getOrPut(tk) { HashSet(2) }.add(mk)
        }
        // nœuds : "row:<id>", "tmdb:…", "t:…" — chaque ligne est reliée à ses clés
        rows.forEachIndexed { i, r ->
            val node = "row:${r.id}"
            parent.putIfAbsent(node, node)
            val mk = mks[i]
            mk?.let { k -> parent.putIfAbsent(k, k); union(node, k) }
            tks[i]?.let { k ->
                if (mk == null || (tmdbByTitle[k]?.size ?: 0) <= 1) { parent.putIfAbsent(k, k); union(node, k) }
            }
        }
        // clé lisible par composante : plus petite clé tmdb, sinon plus petite clé titre, sinon id
        val best = HashMap<String, String>()
        val roots = arrayOfNulls<String>(rows.size)
        rows.forEachIndexed { i, r ->
            val root = find("row:${r.id}")
            roots[i] = root
            val candidate = mks[i] ?: tks[i] ?: "id:${r.id}"
            val current = best[root]
            if (current == null || rank(candidate) < rank(current) || (rank(candidate) == rank(current) && candidate < current)) {
                best[root] = candidate
            }
        }
        val out = HashMap<String, String>(rows.size * 2)
        rows.forEachIndexed { i, r -> out[r.id] = best.getValue(roots[i]!!) }
        return out
    }
    private fun rank(key: String) = when {
        key.startsWith("tmdb:") -> 0
        key.startsWith("t:") -> 1
        else -> 2
    }

    /** Clé d'affichage d'une ligne dont la clé n'est pas encore calculée (juste après la migration). */
    fun keyOf(id: String, groupKey: String?): String = groupKey ?: "id:$id"
}
