package com.sokoul.desktop.data

// Réglages de lecture et de télévision de la tablette (`app/.../data/Settings.kt`), mêmes choix et mêmes libellés,
// rangés dans la table `settings` du PC.

enum class SubtitleSize(val label: String, val vlcRelFontSize: Int) { SMALL("Petite", 20), NORMAL("Normale", 16), LARGE("Grande", 12), HUGE("Très grande", 8) }
enum class SubtitleColor(val label: String, val vlcColor: Int) { WHITE("Blanc", 0xFFFFFF), YELLOW("Jaune", 0xFFFF00) }
enum class SubtitleBackground(val label: String) { OUTLINE("Contour"), BOX("Fond noir"), NONE("Aucun") }
enum class SubtitlePosition(val label: String, val marginPx: Int) { LOW("En bas", 0), HIGH("Plus haut", 120) }
enum class BufferProfile(val label: String, val liveMs: Int, val vodMs: Int) {
    LOW("Faible (zapping rapide)", 800, 1500), NORMAL("Normal", 1500, 3000), HIGH("Élevé (connexion instable)", 4000, 8000)
}

/** Ce que le lecteur lit des réglages au moment d'ouvrir un titre. */
data class PlayerPrefs(
    val audioLanguage: String = "fr",
    val subtitleLanguage: String = "",
    val subtitleSize: SubtitleSize = SubtitleSize.NORMAL,
    val subtitleColor: SubtitleColor = SubtitleColor.WHITE,
    val subtitleBackground: SubtitleBackground = SubtitleBackground.OUTLINE,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.LOW,
    val buffer: BufferProfile = BufferProfile.NORMAL,
) {
    /** Options libVLC du média pour l'apparence des sous-titres (appliquées dès le titre suivant). */
    fun subtitleOptions(): List<String> = buildList {
        add(":freetype-rel-fontsize=${subtitleSize.vlcRelFontSize}")
        add(":freetype-color=${subtitleColor.vlcColor}")
        when (subtitleBackground) {
            SubtitleBackground.OUTLINE -> { add(":freetype-outline-thickness=4"); add(":freetype-background-opacity=0") }
            SubtitleBackground.BOX -> { add(":freetype-outline-thickness=0"); add(":freetype-background-opacity=160"); add(":freetype-background-color=0") }
            SubtitleBackground.NONE -> { add(":freetype-outline-thickness=0"); add(":freetype-background-opacity=0"); add(":freetype-shadow-opacity=160") }
        }
        if (subtitlePosition.marginPx > 0) add(":sub-margin=${subtitlePosition.marginPx}")
    }
}

/** Réglages typés du PC (lecture, télévision et guide) ; une valeur absente = celle de la tablette par défaut. */
class DesktopPrefs(private val db: DesktopDb) {
    private inline fun <reified E : Enum<E>> enum(key: String, default: E): E =
        db.setting(key)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    var audioLanguage: String
        get() = db.setting(K_AUDIO) ?: "fr"
        set(v) = db.putSetting(K_AUDIO, v)
    var subtitleLanguage: String
        get() = db.setting(K_SUBS).orEmpty()
        set(v) = db.putSetting(K_SUBS, v.ifBlank { null })
    var subtitleSize: SubtitleSize
        get() = enum(K_SUB_SIZE, SubtitleSize.NORMAL)
        set(v) = db.putSetting(K_SUB_SIZE, v.name)
    var subtitleColor: SubtitleColor
        get() = enum(K_SUB_COLOR, SubtitleColor.WHITE)
        set(v) = db.putSetting(K_SUB_COLOR, v.name)
    var subtitleBackground: SubtitleBackground
        get() = enum(K_SUB_BG, SubtitleBackground.OUTLINE)
        set(v) = db.putSetting(K_SUB_BG, v.name)
    var subtitlePosition: SubtitlePosition
        get() = enum(K_SUB_POS, SubtitlePosition.LOW)
        set(v) = db.putSetting(K_SUB_POS, v.name)
    var autoNext: Boolean
        get() = db.setting(K_AUTONEXT) != "0"
        set(v) = db.putSetting(K_AUTONEXT, if (v) "1" else "0")
    var buffer: BufferProfile
        get() = enum(K_BUFFER, BufferProfile.NORMAL)
        set(v) = db.putSetting(K_BUFFER, v.name)
    var userAgent: String
        get() = db.setting(Settings.USER_AGENT) ?: Settings.DEFAULT_USER_AGENT
        set(v) = db.putSetting(Settings.USER_AGENT, v.trim().ifBlank { null })
    var resumeLastChannel: Boolean
        get() = db.setting(K_START) == "1"
        set(v) = db.putSetting(K_START, if (v) "1" else "0")
    var zapInverted: Boolean
        get() = db.setting(K_ZAP) == "1"
        set(v) = db.putSetting(K_ZAP, if (v) "1" else "0")
    var epgOffsetMinutes: Int
        get() = db.setting(Settings.EPG_OFFSET)?.toIntOrNull() ?: 0
        set(v) = db.putSetting(Settings.EPG_OFFSET, v.toString())
    var lastChannel: String?
        get() = db.setting(K_LAST_CHANNEL)
        set(v) = db.putSetting(K_LAST_CHANNEL, v)

    fun player() = PlayerPrefs(audioLanguage, subtitleLanguage, subtitleSize, subtitleColor, subtitleBackground, subtitlePosition, buffer)

    companion object {
        const val K_AUDIO = "play.audio"
        const val K_SUBS = "play.subs"
        const val K_SUB_SIZE = "sub.size"
        const val K_SUB_COLOR = "sub.color"
        const val K_SUB_BG = "sub.bg"
        const val K_SUB_POS = "sub.pos"
        const val K_AUTONEXT = "play.autonext"
        const val K_BUFFER = "play.cache"
        const val K_START = "tv.start"
        const val K_ZAP = "tv.zap"
        const val K_LAST_CHANNEL = "tv.last_channel"

        /** Langues proposées (celles de la tablette). */
        val languages = listOf(
            "fr" to "Français", "en" to "Anglais", "ar" to "Arabe", "es" to "Espagnol", "de" to "Allemand",
            "it" to "Italien", "pt" to "Portugais", "tr" to "Turc", "nl" to "Néerlandais",
        )

        fun languageName(code: String) = languages.firstOrNull { it.first == code }?.second ?: code.ifBlank { "Automatique" }

        fun offsetLabel(min: Int) = when {
            min == 0 -> "Aucun"
            min > 0 -> "+${min / 60} h"
            else -> "−${-min / 60} h"
        }

        /** Libellé du sens du zapping (clavier : Page préc. / Page suiv.). */
        fun zapLabel(inverted: Boolean) = if (inverted) "Page préc. : chaîne suivante · Page suiv. : précédente" else "Page préc. : chaîne précédente · Page suiv. : suivante"

        /** Pas de zapping selon le sens choisi : Page préc. = −1 par défaut. */
        fun zapStep(delta: Int, inverted: Boolean) = if (inverted) -delta else delta

        /** Heure du guide décalée du réglage (programmes du fournisseur mal calés). */
        fun shiftGuide(t: Long, offsetMinutes: Int) = t + offsetMinutes * 60_000L
    }
}
