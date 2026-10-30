package com.sokoul.iptv.recording

import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.repo.EpgRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Règles « tous les épisodes » : programme les diffusions du guide qui correspondent à chaque règle.
 * Appelé après chaque synchronisation du guide, à la création d'une règle et au démarrage.
 * Une seule requête de guide par règle (sa chaîne, 7 jours).
 */
@Singleton
class SeriesRecorder @Inject constructor(
    private val db: AppDatabase,
    private val epg: EpgRepository,
    private val scheduler: RecordingScheduler,
) {
    private val mutex = Mutex()

    /** Nombre de diffusions nouvellement programmées. */
    suspend fun run(pid: Long): Int = mutex.withLock {
        var added = 0
        val now = System.currentTimeMillis()
        for (rule in db.recordings().rulesFor(pid)) {
            val channel = db.channels().get(pid, rule.channelId) ?: continue
            val programs = runCatching { epg.range(pid, channel, now, now + HORIZON_MS) }.getOrDefault(emptyList())
            val airings = RecordingRules.seriesAirings(
                rule.ruleKey, channel.id, channel.name, rule.createdAt, programs, db.recordings().forPlaylist(pid), now,
            )
            var refused = 0
            for (a in airings) {
                if (scheduler.schedule(pid, channel, a.title, a.start, a.end).ok) added++ else refused++
            }
            // seules les diffusions encore refusées comptent : un conflit levé disparaît au passage suivant
            if (refused != rule.skipped) db.recordings().setRuleSkipped(rule.id, refused)
        }
        added
    }

    /** Au démarrage : toutes les playlists qui ont des règles. */
    suspend fun runAll() {
        for (pid in db.recordings().rulePlaylists()) runCatching { run(pid) }
    }

    private companion object {
        const val HORIZON_MS = 7 * 86_400_000L
    }
}
