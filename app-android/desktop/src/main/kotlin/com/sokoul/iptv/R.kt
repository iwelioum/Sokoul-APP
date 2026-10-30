package com.sokoul.iptv

/**
 * Ressources de l'app tablette sur PC : mêmes noms que `R.font.*` d'Android, ici les chemins des polices dans les
 * ressources du bureau (`desktop/src/main/resources/font`). Les fichiers d'interface copiés de la tablette compilent
 * ainsi sans modification (`Font(R.font.bebas_neue)` → `platform.Font(String)`).
 */
object R {
    object font {
        const val manrope_w300 = "font/manrope_w300.ttf"
        const val manrope_w400 = "font/manrope_w400.ttf"
        const val manrope_w500 = "font/manrope_w500.ttf"
        const val manrope_w600 = "font/manrope_w600.ttf"
        const val manrope_w700 = "font/manrope_w700.ttf"
        const val manrope_w800 = "font/manrope_w800.ttf"
        const val dm_serif_display = "font/dm_serif_display.ttf"
        const val dm_serif_display_italic = "font/dm_serif_display_italic.ttf"
        const val bebas_neue = "font/bebas_neue.ttf"
        const val cinzel = "font/cinzel.ttf"
        const val orbitron = "font/orbitron.ttf"
        const val creepster = "font/creepster.ttf"
        const val special_elite = "font/special_elite.ttf"
        const val fredoka = "font/fredoka.ttf"
        const val rye = "font/rye.ttf"
    }
}
