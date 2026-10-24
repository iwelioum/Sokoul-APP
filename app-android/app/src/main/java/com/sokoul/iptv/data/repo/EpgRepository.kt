package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.PlaylistType
import com.sokoul.iptv.data.db.ProgramEntity
import com.sokoul.iptv.data.net.XtreamClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class NowNext(val now: ProgramEntity?, val next: ProgramEntity?) {
    fun progress(time: Long = System.currentTimeMillis()): Float {
        val n = now ?: return 0f
        return ((time - n.start).toFloat() / (n.end - n.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
}

@Singleton
class EpgRepository @Inject constructor(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
    private val xtream: XtreamClient,
) {
    private val shortEpgFetched = ConcurrentHashMap<String, Long>()

    /** Clé EPG d'une chaîne : identifiant XMLTV, ou EPG court Xtream à défaut. */
    fun keyFor(c: ChannelEntity): String = c.epgKey ?: (XT_PREFIX + c.id)

    /** Programme en cours pour une liste de chaînes (requêtes par paquets : limite SQLite de 999 variables). */
    suspend fun nowFor(pid: Long, channels: List<ChannelEntity>, now: Long = System.currentTimeMillis()): Map<String, ProgramEntity> {
        val keys = channels.map(::keyFor).distinct()
        val result = HashMap<String, ProgramEntity>(keys.size)
        keys.chunked(500).forEach { chunk ->
            db.programs().nowFor(pid, chunk, now).forEach { result[it.channelKey] = it }
        }
        return channels.mapNotNull { c -> result[keyFor(c)]?.let { c.id to it } }.toMap()
    }

    suspend fun nowNext(pid: Long, c: ChannelEntity, now: Long = System.currentTimeMillis()): NowNext {
        var list = db.programs().upcoming(pid, keyFor(c), now, 2)
        if (list.isEmpty() && fetchShortEpg(pid, c)) list = db.programs().upcoming(pid, keyFor(c), now, 2)
        val current = list.firstOrNull()?.takeIf { it.start <= now }
        val next = if (current != null) list.getOrNull(1) else list.firstOrNull()
        return NowNext(current, next)
    }

    suspend fun range(pid: Long, c: ChannelEntity, from: Long, to: Long): List<ProgramEntity> {
        val list = db.programs().range(pid, keyFor(c), from, to)
        if (list.isEmpty() && fetchShortEpg(pid, c)) return db.programs().range(pid, keyFor(c), from, to)
        return list
    }

    /** EPG de secours Xtream (get_short_epg) quand le XMLTV ne couvre pas la chaîne. */
    private suspend fun fetchShortEpg(pid: Long, c: ChannelEntity): Boolean {
        val tag = "$pid:${c.id}"
        val last = shortEpgFetched[tag] ?: 0
        if (System.currentTimeMillis() - last < 30 * 60_000L) return false
        shortEpgFetched[tag] = System.currentTimeMillis()
        val p = playlists.get(pid) ?: return false
        if (p.type != PlaylistType.XTREAM) return false
        val auth = playlists.auth(p) ?: return false
        val streamId = c.id.toLongOrNull() ?: return false
        val items = runCatching { xtream.shortEpg(auth, streamId, 12) }.getOrNull().orEmpty()
        if (items.isEmpty()) return false
        val key = keyFor(c)
        db.programs().upsert(items.map { ProgramEntity(pid, key, it.start, it.stop, it.title, it.description, null, null, null, 0) })
        return true
    }

    companion object {
        const val XT_PREFIX = "xt:"
    }
}
