package com.sokoul.iptv.ui.components

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sokoul.iptv.data.repo.BrowseRow
import com.sokoul.iptv.data.repo.RowStyle
import com.sokoul.iptv.data.repo.tmdbSized

/**
 * Préchargement des images des rangées suivantes de l'accueil : téléchargées dans le cache disque de Coil
 * (décodées en miniature, sans occuper le cache mémoire). Quand la rangée apparaît, l'image vient du disque
 * au lieu du réseau : c'était la principale cause des saccades lors des premières descentes.
 */
object ImagePrefetch {
    private const val PER_ROW = 8
    private val seen = object : LinkedHashMap<String, Unit>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 800
    }

    /** Adresses affichées par les premières cartes de [row], à la même taille que les cartes (même clé de cache disque). */
    fun urls(row: BrowseRow, perRow: Int = PER_ROW): List<String> = when (row.style) {
        RowStyle.LANDSCAPE, RowStyle.CONTINUE -> row.works.take(perRow).mapNotNull { tmdbSized(it.backdrop, "w780") ?: tmdbSized(it.poster, "w342") }
        RowStyle.POSTER, RowStyle.TOP10 -> row.works.take(perRow).mapNotNull { tmdbSized(it.poster, "w342") }
        RowStyle.SAGAS -> row.sagas.take(perRow).mapNotNull { tmdbSized(it.backdrop, "w780") ?: tmdbSized(it.poster, "w342") }
        else -> emptyList()
    }

    /** Lance le téléchargement des adresses jamais demandées (au plus une fois chacune par session). */
    fun prefetch(context: Context, urls: List<String>) {
        val fresh = synchronized(seen) { urls.filter { seen.put(it, Unit) == null } }
        if (fresh.isEmpty()) return
        val loader = context.imageLoader
        for (u in fresh) loader.enqueue(
            ImageRequest.Builder(context).data(u).size(48).memoryCachePolicy(CachePolicy.DISABLED).build(),
        )
    }

    /** Rangées [from] et suivantes ([count]) de [rows]. */
    fun ahead(context: Context, rows: List<BrowseRow>, from: Int, count: Int = 2) =
        prefetch(context, rows.drop(from.coerceAtLeast(0)).take(count).flatMap { urls(it) })
}
