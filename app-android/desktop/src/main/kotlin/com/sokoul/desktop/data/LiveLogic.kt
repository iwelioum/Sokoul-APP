package com.sokoul.desktop.data

import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.parser.TitleTagger
import com.sokoul.iptv.parser.XtreamUrls

/** Logique pure du Direct et du guide (testée sans fenêtre). */
object LiveLogic {
    /** Groupe affiché dans la colonne : spéciaux en tête (Favoris, Tout), puis groupes visibles ; verrouillés marqués. */
    data class Group(val id: String, val name: String, val locked: Boolean)

    fun groups(categories: List<Category>, hasFavorites: Boolean): List<Group> = buildList {
        if (hasFavorites) add(Group(SpecialGroup.FAVORITES, "Favoris", false))
        add(Group(SpecialGroup.ALL, "Toutes les chaînes", false))
        categories.filter { !it.hidden }.forEach { add(Group(it.id, it.name, it.locked)) }
    }

    /** Clé de recherche : même normalisation que le rapprochement du guide (accents, casse, suffixes de qualité). */
    fun searchKey(q: String): String = TitleTagger.channelKey(q.trim())

    fun stream(p: Playlist, c: Channel, defaultUa: String): StreamRequest {
        val url = c.url ?: XtreamUrls.live(p.url.trimEnd('/'), p.username.orEmpty(), p.password.orEmpty(), c.id.toLong(), "ts")
        return StreamRequest(url, c.userAgent ?: p.userAgent ?: defaultUa, c.referrer, c.name)
    }

    data class NowNext(val now: Program?, val next: Program?) {
        fun progress(t: Long): Float = now?.let { ((t - it.start).toFloat() / (it.end - it.start).coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
    }

    fun nowNext(programs: List<Program>, t: Long): NowNext {
        val sorted = programs.sortedBy { it.start }
        val now = sorted.firstOrNull { it.start <= t && it.end > t }
        val next = sorted.firstOrNull { it.start >= (now?.end ?: t) }
        return NowNext(now, next)
    }

    /** Chaîne suivante / précédente dans la liste, en boucle (zapping). */
    fun zap(size: Int, index: Int, delta: Int): Int = if (size == 0) 0 else Math.floorMod(index + delta, size)

    /** Début de la fenêtre du guide : l'heure pile, une demi-heure avant maintenant. */
    fun guideStart(now: Long): Long {
        val half = 30 * 60_000L
        return now - now % half - half
    }

    /** Largeur (en minutes) d'un programme dans la fenêtre [from, to), bornée. */
    fun span(p: Program, from: Long, to: Long): Pair<Long, Long> = maxOf(p.start, from) to minOf(p.end, to)
}
