package com.sokoul.tvvolume

import android.view.KeyEvent

/** Ce que le service fait d'un appui : laisser passer au système, l'absorber, ou envoyer une touche à la TV. */
sealed interface KeyDecision {
    data object Pass : KeyDecision
    data object Consume : KeyDecision
    data class Send(val key: TvKey) : KeyDecision
}

/**
 * Touches de la télécommande → touches de la TV.
 * - Volume + / − : envoyé à chaque appui ; touche maintenue → répétition limitée à une tous les [repeatGapMs].
 * - Muet : appui court → Muet ; appui long ([longPressMs]) → Veille (éteindre la TV).
 * - Toute autre touche passe au système, intacte.
 */
class KeyPolicy(private val repeatGapMs: Long = 150, private val longPressMs: Long = 800) {
    private val lastSent = HashMap<TvKey, Long>()
    private var muteDownAt = -1L
    private var longFired = false

    fun decide(keyCode: Int, action: Int, repeatCount: Int, now: Long): KeyDecision = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> volume(TvKey.VOL_UP, action, repeatCount, now)
        KeyEvent.KEYCODE_VOLUME_DOWN -> volume(TvKey.VOL_DOWN, action, repeatCount, now)
        KeyEvent.KEYCODE_VOLUME_MUTE -> mute(action, now)
        else -> KeyDecision.Pass
    }

    private fun volume(key: TvKey, action: Int, repeatCount: Int, now: Long): KeyDecision {
        if (action != KeyEvent.ACTION_DOWN) return KeyDecision.Consume
        val last = lastSent[key]
        if (repeatCount > 0 && last != null && now - last < repeatGapMs) return KeyDecision.Consume
        lastSent[key] = now
        return KeyDecision.Send(key)
    }

    private fun mute(action: Int, now: Long): KeyDecision {
        if (action == KeyEvent.ACTION_DOWN) {
            if (muteDownAt < 0) { muteDownAt = now; longFired = false; return KeyDecision.Consume }
            if (!longFired && now - muteDownAt >= longPressMs) { longFired = true; return KeyDecision.Send(TvKey.POWER) }
            return KeyDecision.Consume
        }
        // relâchée
        val down = muteDownAt
        val fired = longFired
        muteDownAt = -1L
        longFired = false
        return when {
            down < 0 || fired -> KeyDecision.Consume
            now - down >= longPressMs -> KeyDecision.Send(TvKey.POWER)
            else -> KeyDecision.Send(TvKey.MUTE)
        }
    }
}
