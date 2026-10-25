package com.sokoul.iptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Retient l'élément focalisé d'un écran pour le retrouver au retour (fiche → lecteur → fiche,
 * accueil → fiche → accueil), comme sur les applis de streaming du commerce.
 * La clé survit à la navigation (état sauvegardé) ; chaque élément s'inscrit avec [focusMemory].
 */
@Stable
class FocusMemory internal constructor(private val saved: MutableState<String?>) {
    private val requesters = HashMap<String, FocusRequester>()

    val last: String? get() = saved.value

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    internal fun remember(key: String) { saved.value = key }

    fun forget() { saved.value = null }

    /** Redonne le focus au dernier élément retenu ; `false` s'il n'est pas (ou plus) affiché. */
    fun restore(): Boolean {
        val key = saved.value ?: return false
        val r = requesters[key] ?: return false
        return runCatching { r.requestFocus(); true }.getOrDefault(false)
    }
}

@Composable
fun rememberFocusMemory(): FocusMemory {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { FocusMemory(saved) }
}

/** Inscrit l'élément (ou le premier élément focalisable qu'il contient) sous [key]. */
fun Modifier.focusMemory(memory: FocusMemory, key: String): Modifier =
    this.focusRequester(memory.requester(key)).onFocusChanged { if (it.hasFocus) memory.remember(key) }
