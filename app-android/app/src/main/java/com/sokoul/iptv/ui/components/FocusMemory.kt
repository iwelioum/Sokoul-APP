package com.sokoul.iptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
    private val requesters = LinkedHashMap<String, FocusRequester>() // ordre d'affichage

    val last: String? get() = saved.value

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    internal fun remember(key: String) { saved.value = key }

    fun forget() { saved.value = null }

    /**
     * Redonne le focus au dernier élément retenu ; `false` s'il n'est pas (ou plus) affiché.
     * Avec [sameGroup], si l'élément exact a disparu (carte remplacée après la lecture), on se rabat
     * sur un autre élément du même groupe (préfixe avant « | », ex. la même rangée).
     */
    fun restore(sameGroup: Boolean = false): Boolean {
        val key = saved.value ?: return false
        if (requesters[key]?.let { tryFocus(it) } == true) return true
        if (!sameGroup || '|' !in key) return false
        val group = key.substringBefore('|') + "|"
        return requesters.entries.any { (k, r) -> k != key && k.startsWith(group) && tryFocus(r) }
    }

    // requestFocus() sans argument ne signale rien quand l'élément a disparu : on lit le résultat
    private fun tryFocus(r: FocusRequester) = runCatching { r.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
}

@Composable
fun rememberFocusMemory(): FocusMemory {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { FocusMemory(saved) }
}

/** Inscrit l'élément (ou le premier élément focalisable qu'il contient) sous [key]. */
fun Modifier.focusMemory(memory: FocusMemory, key: String): Modifier =
    this.focusRequester(memory.requester(key)).onFocusChanged { if (it.hasFocus) memory.remember(key) }
