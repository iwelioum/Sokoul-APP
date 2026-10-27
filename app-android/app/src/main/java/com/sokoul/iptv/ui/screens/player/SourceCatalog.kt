package com.sokoul.iptv.ui.screens.player

import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.SourceOrigin
import com.sokoul.iptv.domain.VideoQuality

/**
 * Une version de l'œuvre : une langue et une qualité d'image (définition, HDR). Toutes les sources qui la proposent,
 * quelle que soit leur origine, sont rangées dessous, de la meilleure à la moins bonne.
 */
data class VersionGroup(
    val language: AudioLanguage?,
    val region: String?,
    val quality: VideoQuality?,
    val hdr: String?,
    /** Sources de cette version, dans l'ordre du classement (la première est celle qui sera lue). */
    val sources: List<PlayerSource>,
) {
    val best: PlayerSource get() = sources.first()
    val current: PlayerSource? get() = sources.firstOrNull { it.current }
    val isCurrent: Boolean get() = current != null
    /** Source affichée sur la ligne : celle en cours si on regarde cette version, sinon celle qui sera lue. */
    val shown: PlayerSource get() = current ?: best
    /** Les autres sources de la version (repliées sous la ligne, et secours automatique). */
    val others: List<PlayerSource> get() = sources.filter { it !== shown }
}

/**
 * Catalogue des sources du lecteur, sans rien supposer des origines : IPTV, Real-Debrid ou toute source ajoutée plus tard
 * n'ont qu'à fournir des [PlayerSource].
 * - [merge] : fusionne les doublons (même fichier dans deux rubriques IPTV, même release renvoyée deux fois) sans rien perdre ;
 * - [group] : range tout par version (langue + qualité) ;
 * - [fallbackOrder] : ordre de secours quand une source échoue.
 */
object SourceCatalog {
    /** Clé de langue : langue connue, sinon pays du fournisseur, sinon inconnue. */
    fun languageKey(s: PlayerSource): String = s.language?.name ?: s.region?.let { "R:$it" } ?: "?"

    /** Version = langue + définition. Les variantes HDR d'une même définition sont des sources de la version (la meilleure en tête). */
    fun versionKey(s: PlayerSource): String = languageKey(s) + "|" + (s.quality?.name ?: "?")

    /** Langue choisie dans les Réglages (VF, MULTI, VOSTFR, VO) : les autres sont réunies sous « Autres langues ». */
    fun mainLanguage(g: VersionGroup, languages: List<String>): Boolean = g.language?.name?.let { it in languages } == true

    /**
     * Doublons : même origine et même empreinte de fichier. On garde la ligne la mieux classée (ou celle en cours),
     * les autres deviennent « aussi dans » et des identifiants de secours.
     */
    fun merge(sources: List<PlayerSource>): List<PlayerSource> {
        val out = LinkedHashMap<String, PlayerSource>()
        for (s in sources) {
            val k = s.sameFile?.let { s.origin.name + "|" + it } ?: ("id|" + s.id)
            val prev = out[k]
            if (prev == null) { out[k] = s; continue }
            val (keep, drop) = if (s.current && !prev.current) s to prev else prev to s
            out[k] = keep.copy(
                current = keep.current || drop.current,
                alsoIn = (keep.alsoIn + listOfNotNull(drop.category) + drop.alsoIn).distinct().filter { it != keep.category },
                mergedIds = (keep.mergedIds + drop.id + drop.mergedIds).distinct().filter { it != keep.id },
                failed = keep.failed && drop.failed,
                basis = if (drop.basis == QualityBasis.MEASURED && keep.basis != QualityBasis.MEASURED) drop.basis else keep.basis,
            )
        }
        return out.values.toList()
    }

    private fun languageOrder(s: PlayerSource, languages: List<String>): Int =
        s.language?.let { l -> languages.indexOf(l.name).takeIf { it >= 0 } ?: languages.size } ?: (languages.size + if (s.region != null) 1 else 2)

    /**
     * Versions, dans l'ordre : langue (ordre des Réglages, puis pays, puis inconnue), puis définition.
     * Dans une version, les sources gardent l'ordre reçu (classement neutre de `SourceRanker`).
     */
    fun group(sources: List<PlayerSource>, languages: List<String>): List<VersionGroup> =
        merge(sources).groupBy(::versionKey).values.map { list ->
            val f = list.first()
            VersionGroup(f.language, f.region, f.quality, f.hdr, list)
        }.sortedWith(
            compareBy<VersionGroup> { languageOrder(it.best, languages) }
                .thenBy { it.region.orEmpty() }
                .thenBy { it.quality?.ordinal ?: VideoQuality.entries.size },
        )

    /** Nombre de sources (doublons fusionnés) par origine, dans l'ordre de l'énumération. */
    fun counts(sources: List<PlayerSource>): List<Pair<SourceOrigin, Int>> {
        val merged = merge(sources)
        return SourceOrigin.entries.map { o -> o to merged.count { it.origin == o } }.filter { it.second > 0 }
    }

    /**
     * Secours quand [failedId] échoue, quelle que soit l'origine :
     * 1. le même fichier dans une autre rubrique (doublon fusionné) ;
     * 2. les autres sources de la même version (même langue, même qualité) ;
     * 3. les autres versions de la même langue, de la meilleure qualité à la moins bonne ;
     * 4. si [anyLanguage] (connexion IPTV indisponible), toutes les autres versions.
     * Les origines [skip] et les sources déjà en échec sont écartées.
     */
    fun fallbackOrder(
        sources: List<PlayerSource>, failedId: String, languages: List<String>,
        skip: Set<SourceOrigin> = emptySet(), anyLanguage: Boolean = false,
    ): List<String> {
        val groups = group(sources, languages)
        val failed = groups.flatMap { it.sources }.firstOrNull { it.id == failedId || failedId in it.mergedIds }
        val home = groups.firstOrNull { g -> g.sources.any { it === failed } }
        val ordered = buildList {
            if (failed != null) add(failed)
            home?.sources?.filter { it !== failed }?.let(::addAll)
            val lang = home?.let { languageKey(it.best) }
            groups.filter { it !== home && lang != null && languageKey(it.best) == lang }.forEach { addAll(it.sources) }
            if (anyLanguage || home == null) groups.filter { it !== home && (lang == null || languageKey(it.best) != lang) }.forEach { addAll(it.sources) }
        }
        // jamais une version à télécharger ou en préparation : le secours doit démarrer tout de suite
        return ordered.filter { it.origin !in skip && (!it.failed || it === failed) && (it.ready != false || it === failed) }
            .flatMap { listOf(it.id) + it.mergedIds }
            .filter { it != failedId }
            .distinct()
    }
}
