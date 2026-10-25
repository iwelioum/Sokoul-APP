package com.sokoul.iptv.ui.nav

import android.os.SystemClock
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute

private var lastNavigationAt = 0L

/** Délai minimal entre deux ouvertures d'écran : un double appui n'ouvre qu'une fiche ou qu'un lecteur. */
const val NAVIGATION_DEBOUNCE_MS = 700L

/**
 * Ouvre un écran une seule fois même si l'utilisateur appuie deux fois (télécommande qui rebondit,
 * double toucher). Un lecteur n'est jamais ouvert par-dessus un autre lecteur : deux lecteurs
 * signifieraient deux connexions au fournisseur.
 */
fun NavController.navigateOnce(route: Any) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastNavigationAt in 0 until NAVIGATION_DEBOUNCE_MS) return
    if (route is PlayerRoute && currentBackStackEntry?.destination?.hasRoute(PlayerRoute::class) == true) return
    lastNavigationAt = now
    navigate(route)
}
