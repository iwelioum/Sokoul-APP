package com.sokoul.iptv.ui.screens.settings.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokoul.iptv.data.repo.VersionPicker
import com.sokoul.iptv.data.repo.VodGrouping
import com.sokoul.iptv.ui.components.MenuOption
import com.sokoul.iptv.ui.screens.settings.SettingRow
import com.sokoul.iptv.ui.screens.settings.SettingsCtx
import com.sokoul.iptv.ui.screens.settings.TextEdit

/** Films et séries : clé TMDB et couverture, doublons, version lancée en premier, statistiques. */
internal fun LazyListScope.librarySection(c: SettingsCtx) {
    val s = c.s
    item("lib-tmdb") {
        SettingRow("Clé API TMDB", if (s.tmdbApiKey.isBlank()) "Non configurée" else "Configurée", "Affiches HD, résumés, casting, bandes-annonces et sagas", icon = Icons.Default.Key) {
            c.edit(TextEdit("Clé API TMDB (gratuite sur themoviedb.org)", s.tmdbApiKey) { v -> c.vm.setTmdbKey(v) })
        }
    }
    if (s.tmdbApiKey.isNotBlank()) item("lib-coverage") {
        val cov by c.vm.coverage.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { c.vm.refreshCoverage() }
        SettingRow(
            cov?.let { "Fiches enrichies : " + it.percent + " % du catalogue" } ?: "Fiches enrichies",
            null,
            when {
                cov == null -> "Calcul…"
                cov?.complete == true -> "Genres, ambiances et Explorer couvrent tout le catalogue"
                else -> "L'analyse continue en arrière-plan, et se met en pause pendant la lecture"
            },
            icon = Icons.Default.LibraryBooks,
        ) { c.vm.refreshCoverage() }
    }
    item("lib-group") {
        SettingRow("Regrouper les doublons", null, "Une fiche par film ou série, toutes versions réunies", icon = Icons.Default.MergeType, trailing = { com.sokoul.iptv.ui.components.SokoulSwitch(s.groupDuplicates) }) {
            c.vm.update { it.copy(groupDuplicates = !s.groupDuplicates) }
        }
    }
    item("lib-version") {
        val order = VersionPicker.parseOrder(s.vodLanguages)
        val presetName = VersionPicker.PRESETS.firstOrNull { it.second == order }?.first ?: order.joinToString(" › ")
        SettingRow("Version lancée en premier", presetName, "${order.joinToString(" › ")}, puis la meilleure qualité lisible", icon = Icons.Default.Language) {
            c.choose("Version lancée en premier", VersionPicker.PRESETS.map { (name, langs) ->
                MenuOption("$name — ${langs.joinToString(" › ")}", selected = langs == order) { c.vm.update { it.copy(vodLanguages = langs.joinToString(",")) } }
            })
        }
    }
    item("lib-tie") {
        val tie = com.sokoul.iptv.domain.SourceOrigin.entries.firstOrNull { it.name == s.sourceTie }
        SettingRow(
            "Source à égalité", tie?.let(com.sokoul.iptv.ui.components.SourceLabels::originName) ?: "Aucune préférence",
            "Toutes les sources sont classées ensemble (langue, qualité, fiabilité) ; ce choix ne départage que deux sources identiques",
            icon = Icons.Default.MergeType,
        ) {
            c.choose("Source à égalité", listOf(MenuOption("Aucune préférence", selected = tie == null) { c.vm.update { it.copy(sourceTie = "") } }) +
                com.sokoul.iptv.domain.SourceOrigin.entries.map { o ->
                    MenuOption(com.sokoul.iptv.ui.components.SourceLabels.originName(o), selected = o == tie) { c.vm.update { it.copy(sourceTie = o.name) } }
                })
        }
    }
    item("lib-stats") {
        SettingRow("Statistiques du catalogue", null, "Doublons regroupés, versions par œuvre, plus gros regroupements", icon = Icons.Default.BarChart) {
            c.vm.stats { st ->
                if (st == null) { c.toast("Aucune playlist active"); return@stats }
                fun pct(a: Int, b: Int) = if (b == 0) 0 else a * 100 / b
                c.choose("Statistiques du catalogue", buildList {
                    add(MenuOption("Films : ${st.movies} versions → ${st.movieWorks} œuvres (TMDB fourni : ${pct(st.moviesWithTmdb, st.movies)} %)") {})
                    add(MenuOption("Séries : ${st.series} versions → ${st.seriesWorks} œuvres (TMDB fourni : ${pct(st.seriesWithTmdb, st.series)} %)") {})
                    if (st.pending > 0) add(MenuOption("Calcul en cours : ${st.pending} titres pas encore regroupés") {})
                    st.biggestMovies.forEach { g -> add(MenuOption("Film · ${VodGrouping.stripCountry(g.title)} — ${g.c} versions") {}) }
                    st.biggestSeries.forEach { g -> add(MenuOption("Série · ${VodGrouping.stripCountry(g.title)} — ${g.c} versions") {}) }
                })
            }
        }
    }
}
