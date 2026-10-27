package com.sokoul.iptv.domain

/**
 * Langues réellement entendues dans un fichier, retenues après une lecture (ou données par le panel).
 * Stockées en codes séparés par des virgules (« fr,en ») ; « ? » note une piste sans langue indiquée.
 *
 * Une mesure du lecteur avec toutes les pistes étiquetées fait foi sur le nom (« VF » qui n'a qu'une piste anglaise
 * devient « Anglais (EN) ») ; une mesure partielle (panel : première piste seulement, ou piste sans étiquette)
 * ne fait que compléter un nom muet.
 */
object SpokenLanguages {
    const val UNTAGGED = "?"
    private val NONE = setOf("", "und", "mul", "zxx", "mis", "qaa", UNTAGGED)

    /** Codes ISO 639-2 bibliographiques absents de `Locale.getISO3Language`. */
    private val ISO3_B = mapOf(
        "fre" to "fr", "ger" to "de", "dut" to "nl", "chi" to "zh", "gre" to "el", "per" to "fa", "cze" to "cs", "rum" to "ro",
        "slo" to "sk", "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "geo" to "ka", "ice" to "is", "mac" to "mk",
        "mao" to "mi", "may" to "ms", "tib" to "bo", "wel" to "cy",
    )
    private val ISO3 by lazy {
        java.util.Locale.getISOLanguages().associateBy { runCatching { java.util.Locale(it).isO3Language }.getOrDefault("") } + ISO3_B
    }

    /** « fre », « fra », « fr-FR », « FR » → « FR » ; inconnu ou « und » → null. */
    fun code(raw: String?): String? {
        val t = raw?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return null
        if (t in NONE) return null
        return when (t.length) { 2 -> t; 3 -> ISO3[t]; else -> null }?.uppercase()
    }

    /** Texte stocké → codes distincts (« fr,en,? » → [FR, EN]). */
    fun parse(raw: String?): List<String> = raw?.split(',', ';', '|', ' ')?.mapNotNull(::code)?.distinct().orEmpty()

    /** Pistes audio du fichier (langues brutes du lecteur) → texte à stocker ; null si aucune piste. */
    fun store(trackLanguages: List<String?>): String? =
        trackLanguages.map { code(it) ?: UNTAGGED }.distinct().joinToString(",").ifEmpty { null }

    /** Toutes les pistes ont une langue : la mesure fait foi. */
    fun complete(raw: String?, fromPlayer: Boolean): Boolean = fromPlayer && !raw.isNullOrBlank() && UNTAGGED !in raw.split(',')

    /** Langue retenue d'après le nom et ce qui a été entendu. */
    fun language(name: AudioLanguage?, heard: List<String>, complete: Boolean): AudioLanguage? {
        if (heard.isEmpty()) return name
        val fr = "FR" in heard
        val other = heard.any { it != "FR" }
        if (!complete) return name ?: when { fr && other -> AudioLanguage.MULTI; fr -> AudioLanguage.VF; else -> null }
        return when {
            fr && other -> AudioLanguage.MULTI
            fr -> AudioLanguage.VF
            // pas de piste française : une VOSTFR / VO l'annonce déjà, sinon la langue entendue devient la section
            name == AudioLanguage.VOSTFR || name == AudioLanguage.VO -> name
            else -> null
        }
    }

    /** Section d'une source sans langue « française » connue : la première langue entendue. */
    fun region(language: AudioLanguage?, heard: List<String>): String? =
        if (language == null) heard.firstOrNull { it != "FR" } else null
}
