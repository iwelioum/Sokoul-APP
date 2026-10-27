package com.sokoul.iptv.data.repo

/** Une version jouable d'une œuvre (film ou série). */
data class VodVersion(
    val id: String,
    val name: String,
    val language: String?,
    val quality: String?,
    val categoryId: String,
    val categoryName: String? = null,
    /** Qualité réelle, quand elle a été mesurée : prime sur l'étiquette du nom. */
    val measured: VodQuality? = null,
) {
    /** Langues entendues lors d'une lecture (ou données par le panel) : « FR », « EN »… ; vide = jamais mesuré. */
    val heard: List<String> get() = com.sokoul.iptv.domain.SpokenLanguages.parse(measured?.audioLanguage)

    /** Toutes les pistes ont été entendues et étiquetées : la mesure fait foi sur le nom. */
    val heardComplete: Boolean get() = com.sokoul.iptv.domain.SpokenLanguages.complete(measured?.audioLanguage, measured?.fromPlayer == true)

    /** Langue retenue (VF, MULTI, VOSTFR, VO ou null) : celle entendue si on la connaît, sinon celle du nom. */
    val spokenLanguage: String?
        get() = if (heard.isEmpty()) language else com.sokoul.iptv.domain.SpokenLanguages.language(
            language?.let { runCatching { com.sokoul.iptv.domain.AudioLanguage.valueOf(it) }.getOrNull() }, heard, heardComplete,
        )?.name
}

/**
 * Ordre de lancement des versions d'une même œuvre :
 * 1. la version choisie manuellement pour cette œuvre ;
 * 2. la langue selon l'ordre réglé (VF d'abord par défaut) ;
 * 3. la meilleure qualité que l'appareil sait décoder (pas de 4K si le décodeur ne suit pas) ;
 * 4. l'ordre du fournisseur.
 * Les versions en échec pendant la séance passent en dernier.
 */
object VersionPicker {
    val DEFAULT_LANGUAGES = listOf("VF", "MULTI", "VOSTFR", "VO")

    /** Préréglages proposés dans les Réglages. */
    val PRESETS: List<Pair<String, List<String>>> = listOf(
        "VF d'abord" to listOf("VF", "MULTI", "VOSTFR", "VO"),
        "MULTI d'abord" to listOf("MULTI", "VF", "VOSTFR", "VO"),
        "VOSTFR d'abord" to listOf("VOSTFR", "VO", "MULTI", "VF"),
        "VO d'abord" to listOf("VO", "VOSTFR", "MULTI", "VF"),
    )

    fun parseOrder(s: String?): List<String> =
        s?.split(',')?.map { it.trim().uppercase() }?.filter { it in DEFAULT_LANGUAGES }?.distinct()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_LANGUAGES

    fun order(
        versions: List<VodVersion>,
        languages: List<String> = DEFAULT_LANGUAGES,
        preferredId: String? = null,
        failed: Set<String> = emptySet(),
        supports4k: Boolean = true,
    ): List<VodVersion> {
        fun langRank(v: VodVersion) = languages.indexOf(v.spokenLanguage).let { if (it < 0) languages.size else it }
        fun qualityRank(v: VodVersion) = when (v.measured?.resolution?.let { r -> mapOf("4K" to "4K", "1080p" to "FHD", "720p" to "HD", "SD" to "SD")[r] } ?: v.quality) {
            "4K" -> if (supports4k) 0 else 4
            "FHD" -> 1
            "HD", "HDR", "HEVC" -> 2
            null -> 3
            "SD" -> 4
            else -> 3
        }
        return versions.withIndex().sortedWith(
            compareBy<IndexedValue<VodVersion>>(
                { if (it.value.id in failed) 1 else 0 },
                { if (it.value.id == preferredId) 0 else 1 },
                { langRank(it.value) },
                { qualityRank(it.value) },
                { -(it.value.measured?.imageScore ?: 0) },
                { it.index },
            )
        ).map { it.value }
    }

    private val NOT_REGION = setOf("VOD", "4K", "UHD", "FHD", "HD", "SD")

    /** Pays affiché par le fournisseur dans le nom (« AR », « EN »), quand la langue est inconnue. */
    fun region(name: String): String? =
        com.sokoul.iptv.parser.TitleTagger.parse(name).prefix?.uppercase()?.takeIf { it.length in 2..4 && it.all(Char::isLetter) && it !in NOT_REGION }
}
