package com.sokoul.iptv.ui.screens.player

/**
 * Minuterie de mise en veille du lecteur : au bout du délai (ou à la fin du film / de l'épisode), la lecture
 * se met en pause, la position est enregistrée et le lecteur se ferme pour que la télé puisse se mettre en veille.
 */
object SleepTimer {
    sealed interface Choice {
        val label: String
        data class Minutes(val minutes: Int) : Choice { override val label get() = "$minutes min" }
        /** À la fin du film ou de l'épisode en cours : l'épisode suivant ne se lance pas. */
        data object EndOfItem : Choice { override val label get() = "Fin de l'épisode" }
    }

    /** Minuterie en cours : échéance (horloge murale, ms) ou null pour « fin de l'épisode ». */
    data class State(val choice: Choice, val dueAt: Long?)

    val CHOICES: List<Choice> = listOf(Choice.Minutes(15), Choice.Minutes(30), Choice.Minutes(45), Choice.Minutes(60), Choice.Minutes(90), Choice.EndOfItem)

    fun start(choice: Choice, nowMs: Long): State = when (choice) {
        is Choice.Minutes -> State(choice, nowMs + choice.minutes * 60_000L)
        Choice.EndOfItem -> State(choice, null)
    }

    /** Minutes restantes, arrondies au-dessus (« reste 12 min »), ou null sans échéance. */
    fun remainingMinutes(s: State, nowMs: Long): Int? = s.dueAt?.let { ((it - nowMs).coerceAtLeast(0) + 59_999) / 60_000 }?.toInt()

    /** Libellé du menu : « Minuterie : 12 min », « Minuterie : fin de l'épisode », « Minuterie ». */
    fun menuLabel(s: State?, nowMs: Long): String = when {
        s == null -> "Minuterie de mise en veille"
        s.dueAt == null -> "Minuterie : fin de l'épisode"
        else -> "Minuterie : reste ${remainingMinutes(s, nowMs)} min"
    }

    /** Vitesses proposées en VOD. */
    val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** « ×1,25 », « Normale ». */
    fun speedLabel(f: Float): String = if (f == 1f) "Normale" else "×" + (if (f % 1f == 0f) f.toInt().toString() else f.toString().trimEnd('0').replace('.', ','))
}
