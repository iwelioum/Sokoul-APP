package com.sokoul.iptv.ui.screens.player

import android.view.KeyEvent
import com.sokoul.iptv.ui.screens.tv.zapDelta

/**
 * Panneaux du direct. La liste des chaînes peut rester ouverte à côté des catégories (seconde colonne) ;
 * [details] = détails de la chaîne (OK), un panneau à boutons qui prend le focus.
 */
internal data class LivePanels(
    val list: Boolean = false, val groups: Boolean = false, val guide: Boolean = false, val details: Boolean = false,
) {
    val any get() = list || groups || guide || details
}

/** État de navigation du direct : panneaux ouverts et ligne surlignée de chaque liste. */
internal data class LiveNav(val panels: LivePanels = LivePanels(), val list: Int = 0, val group: Int = 0, val guide: Int = 0)

/** Ce que l'écran doit faire après une touche (en plus du nouvel état). */
internal sealed interface LiveCmd {
    data class Zap(val delta: Int) : LiveCmd
    data class Play(val index: Int) : LiveCmd
    data class SelectGroup(val index: Int) : LiveCmd
}

internal data class LiveKeyResult(val nav: LiveNav, val cmd: LiveCmd? = null, val hideInfo: Boolean = false)

/** Tailles des listes et positions courantes, lues au moment de la touche. */
internal data class LiveLists(val channels: Int, val groups: Int, val programs: Int, val currentChannel: Int, val currentGroup: Int)

/**
 * Touches du direct, comme une box TV :
 * - sans panneau : ▲▼ zappent (sens réglable), OK = détails de la chaîne, ◀ = chaînes, ▶ = programmes ;
 * - chaînes : ▲▼ parcourent, OK regarde, ◀ ouvre les catégories à côté, ▶ passe aux programmes ;
 * - catégories : ▲▼ parcourent, OK ouvre la catégorie, ▶ revient aux chaînes ;
 * - programmes : ▲▼ font défiler (jamais de zapping), OK ou ◀ ferme.
 * CH+/CH− zappent toujours. Null = touche non gérée ici (Menu, Retour, chiffres… sont traités par l'écran).
 */
internal object LiveKeys {
    fun reduce(code: Int, repeat: Int, nav: LiveNav, lists: LiveLists, inverted: Boolean): LiveKeyResult? {
        val p = nav.panels
        fun clamp(i: Int, size: Int) = i.coerceIn(0, (size - 1).coerceAtLeast(0))
        fun move(d: Int): LiveNav = when {
            p.groups -> nav.copy(group = clamp(nav.group + d, lists.groups))
            p.list -> nav.copy(list = clamp(nav.list + d, lists.channels))
            p.guide -> nav.copy(guide = clamp(nav.guide + d, lists.programs))
            else -> nav
        }
        val scrolling = p.groups || p.list || p.guide
        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val up = code == KeyEvent.KEYCODE_DPAD_UP
                if (scrolling) LiveKeyResult(move(if (up) -1 else 1))
                else LiveKeyResult(nav, LiveCmd.Zap(zapDelta(up = up, inverted = inverted)))
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> LiveKeyResult(nav, LiveCmd.Zap(1))
            KeyEvent.KEYCODE_CHANNEL_DOWN -> LiveKeyResult(nav, LiveCmd.Zap(-1))
            KeyEvent.KEYCODE_PAGE_UP -> LiveKeyResult(move(-10))
            KeyEvent.KEYCODE_PAGE_DOWN -> LiveKeyResult(move(10))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> when {
                repeat > 0 -> LiveKeyResult(nav)
                p.groups -> LiveKeyResult(nav.copy(panels = LivePanels(list = true), list = 0), LiveCmd.SelectGroup(nav.group))
                p.list -> LiveKeyResult(nav.copy(panels = LivePanels()), LiveCmd.Play(nav.list))
                p.guide -> LiveKeyResult(nav.copy(panels = LivePanels()))
                else -> LiveKeyResult(nav.copy(panels = LivePanels(details = true)), hideInfo = true)
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> when {
                p.groups -> LiveKeyResult(nav)
                p.list -> LiveKeyResult(nav.copy(panels = LivePanels(list = true, groups = true), group = clamp(lists.currentGroup, lists.groups)))
                p.guide -> LiveKeyResult(nav.copy(panels = LivePanels()))
                else -> LiveKeyResult(nav.copy(panels = LivePanels(list = true), list = clamp(lists.currentChannel, lists.channels)), hideInfo = true)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                p.groups -> LiveKeyResult(nav.copy(panels = LivePanels(list = true)))
                p.guide -> LiveKeyResult(nav)
                else -> LiveKeyResult(nav.copy(panels = LivePanels(guide = true), guide = 0), hideInfo = true)
            }
            else -> null
        }
    }

    /** Retour : ferme le panneau du dessus (catégories, puis chaînes ; programmes ; détails). Null = rien d'ouvert. */
    fun back(nav: LiveNav): LiveNav? {
        val p = nav.panels
        return when {
            p.groups -> nav.copy(panels = p.copy(groups = false))
            p.any -> nav.copy(panels = LivePanels())
            else -> null
        }
    }
}
