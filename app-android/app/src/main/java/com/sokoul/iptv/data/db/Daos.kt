package com.sokoul.iptv.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class CategoryWithPrefs(
    val playlistId: Long,
    val type: ContentType,
    val categoryId: String,
    val name: String,
    val position: Int,
    val hidden: Boolean,
    val locked: Boolean,
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Insert
    suspend fun insert(p: PlaylistEntity): Long

    @Update
    suspend fun update(p: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlists SET lastSync = :time, liveCount = :live, movieCount = :movies, seriesCount = :series WHERE id = :id")
    suspend fun markSynced(id: Long, time: Long, live: Int, movies: Int, series: Int)

    @Query("UPDATE playlists SET lastEpgSync = :time WHERE id = :id")
    suspend fun markEpgSynced(id: Long, time: Long)
}

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT c.playlistId, c.type, c.categoryId, c.name, c.position,
               IFNULL(p.hidden, 0) AS hidden, IFNULL(p.locked, 0) AS locked
        FROM categories c
        LEFT JOIN category_prefs p ON p.playlistId = c.playlistId AND p.type = c.type AND p.categoryId = c.categoryId
        WHERE c.playlistId = :pid AND c.type = :type
        ORDER BY c.position
        """
    )
    fun observe(pid: Long, type: ContentType): Flow<List<CategoryWithPrefs>>

    @Upsert
    suspend fun upsert(items: List<CategoryEntity>)

    @Upsert
    fun upsertNow(items: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :pid AND type = :type AND gen != :gen")
    suspend fun deleteStale(pid: Long, type: ContentType, gen: Long)

    @Upsert
    suspend fun upsertPref(pref: CategoryPrefEntity)

    @Query("SELECT * FROM category_prefs WHERE playlistId = :pid AND type = :type AND categoryId = :cat")
    suspend fun getPref(pid: Long, type: ContentType, cat: String): CategoryPrefEntity?

    @Query("SELECT categoryId FROM category_prefs WHERE playlistId = :pid AND type = :type AND locked = 1")
    fun observeLocked(pid: Long, type: ContentType): Flow<List<String>>

    @Query("SELECT * FROM category_prefs WHERE playlistId = :pid")
    suspend fun allPrefs(pid: Long): List<CategoryPrefEntity>

    @Query(
        """
        SELECT c.playlistId, c.type, c.categoryId, c.name, c.position, p.hidden AS hidden, p.locked AS locked
        FROM category_prefs p JOIN categories c ON c.playlistId = p.playlistId AND c.type = p.type AND c.categoryId = p.categoryId
        WHERE p.playlistId = :pid AND (p.hidden = 1 OR p.locked = 1) ORDER BY c.type, c.position
        """
    )
    fun observeFlagged(pid: Long): Flow<List<CategoryWithPrefs>>
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :pid AND categoryId = :cat ORDER BY position")
    fun observeByCategory(pid: Long, cat: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT * FROM channels WHERE playlistId = :pid AND categoryId NOT IN (
            SELECT categoryId FROM category_prefs WHERE playlistId = :pid AND type = 'LIVE' AND (hidden = 1 OR locked = 1)
        ) ORDER BY position
        """
    )
    fun observeAllVisible(pid: Long): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT c.* FROM channels c
        JOIN favorites f ON f.playlistId = c.playlistId AND f.type = 'LIVE' AND f.itemId = c.id
        WHERE c.playlistId = :pid ORDER BY f.addedAt
        """
    )
    fun observeFavorites(pid: Long): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT c.* FROM channels c
        JOIN progress p ON p.playlistId = c.playlistId AND p.type = 'LIVE' AND p.itemId = c.id
        WHERE c.playlistId = :pid ORDER BY p.updatedAt DESC LIMIT :limit
        """
    )
    fun observeRecent(pid: Long, limit: Int = 30): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND id = :id")
    suspend fun get(pid: Long, id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND epgKey = :key LIMIT 1")
    suspend fun byEpgKey(pid: Long, key: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND number = :number LIMIT 1")
    suspend fun getByNumber(pid: Long, number: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND searchName LIKE '%' || :q || '%' ESCAPE '\\' ORDER BY position LIMIT :limit")
    suspend fun search(pid: Long, q: String, limit: Int = 60): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND categoryId = :cat ORDER BY position")
    suspend fun listByCategory(pid: Long, cat: String): List<ChannelEntity>

    @Query(
        """
        SELECT * FROM channels WHERE playlistId = :pid AND categoryId NOT IN (
            SELECT categoryId FROM category_prefs WHERE playlistId = :pid AND type = 'LIVE' AND (hidden = 1 OR locked = 1)
        ) ORDER BY position
        """
    )
    suspend fun listAllVisible(pid: Long): List<ChannelEntity>

    @Query(
        """
        SELECT c.* FROM channels c
        JOIN favorites f ON f.playlistId = c.playlistId AND f.type = 'LIVE' AND f.itemId = c.id
        WHERE c.playlistId = :pid ORDER BY f.addedAt
        """
    )
    suspend fun listFavorites(pid: Long): List<ChannelEntity>

    @Query("SELECT id, tvgId, nameKey FROM channels WHERE playlistId = :pid")
    suspend fun epgRefs(pid: Long): List<ChannelEpgRef>

    @Upsert
    suspend fun upsert(items: List<ChannelEntity>)

    @Upsert
    fun upsertNow(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :pid AND gen != :gen")
    suspend fun deleteStale(pid: Long, gen: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int

    /** epgKey = tvg-id d'origine, puis rapprochement par nom quand l'identifiant est inconnu de l'EPG. */
    @Query("UPDATE channels SET epgKey = tvgId WHERE playlistId = :pid")
    suspend fun resetEpgKeys(pid: Long)

    @Query(
        """
        UPDATE channels SET epgKey = (
            SELECT e.id FROM epg_channels e WHERE e.playlistId = :pid AND e.nameKey = channels.nameKey LIMIT 1
        )
        WHERE playlistId = :pid
          AND (epgKey IS NULL OR epgKey NOT IN (SELECT id FROM epg_channels WHERE playlistId = :pid))
          AND EXISTS (SELECT 1 FROM epg_channels e WHERE e.playlistId = :pid AND e.nameKey = channels.nameKey)
        """
    )
    suspend fun matchEpgByName(pid: Long)
}

data class ChannelEpgRef(val id: String, val tvgId: String?, val nameKey: String)

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE playlistId = :pid AND categoryId = :cat ORDER BY position")
    fun observeByCategory(pid: Long, cat: String): Flow<List<MovieEntity>>

    @Query(
        """
        SELECT * FROM movies WHERE playlistId = :pid AND categoryId NOT IN (
            SELECT categoryId FROM category_prefs WHERE playlistId = :pid AND type = 'MOVIE' AND (hidden = 1 OR locked = 1)
        ) ORDER BY added DESC LIMIT :limit
        """
    )
    fun observeRecent(pid: Long, limit: Int = 40): Flow<List<MovieEntity>>

    @Query(
        """
        SELECT m.* FROM movies m
        JOIN favorites f ON f.playlistId = m.playlistId AND f.type = 'MOVIE' AND f.itemId = m.id
        WHERE m.playlistId = :pid ORDER BY f.addedAt DESC
        """
    )
    fun observeFavorites(pid: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND id = :id")
    suspend fun get(pid: Long, id: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND searchName LIKE '%' || :q || '%' ESCAPE '\\' ORDER BY added DESC LIMIT :limit")
    suspend fun search(pid: Long, q: String, limit: Int = 60): List<MovieEntity>

    @Upsert
    suspend fun upsert(items: List<MovieEntity>)

    @Upsert
    fun upsertNow(items: List<MovieEntity>)

    @Query("DELETE FROM movies WHERE playlistId = :pid AND gen != :gen")
    suspend fun deleteStale(pid: Long, gen: Long)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE playlistId = :pid AND categoryId = :cat ORDER BY position")
    fun observeByCategory(pid: Long, cat: String): Flow<List<SeriesEntity>>

    @Query(
        """
        SELECT * FROM series WHERE playlistId = :pid AND categoryId NOT IN (
            SELECT categoryId FROM category_prefs WHERE playlistId = :pid AND type = 'SERIES' AND (hidden = 1 OR locked = 1)
        ) ORDER BY lastModified DESC LIMIT :limit
        """
    )
    fun observeRecent(pid: Long, limit: Int = 40): Flow<List<SeriesEntity>>

    @Query(
        """
        SELECT s.* FROM series s
        JOIN favorites f ON f.playlistId = s.playlistId AND f.type = 'SERIES' AND f.itemId = s.id
        WHERE s.playlistId = :pid ORDER BY f.addedAt DESC
        """
    )
    fun observeFavorites(pid: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND id = :id")
    suspend fun get(pid: Long, id: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE playlistId = :pid AND id = :id")
    fun observe(pid: Long, id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND searchName LIKE '%' || :q || '%' ESCAPE '\\' ORDER BY lastModified DESC LIMIT :limit")
    suspend fun search(pid: Long, q: String, limit: Int = 60): List<SeriesEntity>

    @Upsert
    suspend fun upsert(items: List<SeriesEntity>)

    @Upsert
    fun upsertNow(items: List<SeriesEntity>)

    @Upsert
    fun upsertEpisodesNow(items: List<EpisodeEntity>)

    @Query("DELETE FROM series WHERE playlistId = :pid AND gen != :gen")
    suspend fun deleteStale(pid: Long, gen: Long)

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int

    @Query("SELECT * FROM episodes WHERE playlistId = :pid AND seriesId = :sid ORDER BY season, episode")
    fun observeEpisodes(pid: Long, sid: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE playlistId = :pid AND seriesId = :sid ORDER BY season, episode")
    suspend fun episodes(pid: Long, sid: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE playlistId = :pid AND seriesId = :sid AND id = :eid")
    suspend fun episode(pid: Long, sid: String, eid: String): EpisodeEntity?

    @Upsert
    suspend fun upsertEpisodes(items: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE playlistId = :pid AND seriesId = :sid")
    suspend fun deleteEpisodes(pid: Long, sid: String)

    @Query("DELETE FROM episodes WHERE playlistId = :pid AND seriesId NOT IN (SELECT id FROM series WHERE playlistId = :pid)")
    suspend fun deleteOrphanEpisodes(pid: Long)

    @Query("SELECT * FROM series_details WHERE playlistId = :pid AND seriesId = :sid")
    suspend fun details(pid: Long, sid: String): SeriesDetailsEntity?

    @Query("SELECT * FROM series_details WHERE playlistId = :pid AND seriesId = :sid")
    fun observeDetails(pid: Long, sid: String): Flow<SeriesDetailsEntity?>

    @Upsert
    suspend fun upsertDetails(d: SeriesDetailsEntity)
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs WHERE playlistId = :pid AND channelKey = :key AND `end` > :from AND start < :to ORDER BY start")
    suspend fun range(pid: Long, key: String, from: Long, to: Long): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE playlistId = :pid AND channelKey = :key AND `end` > :from AND start < :to ORDER BY start")
    fun observeRange(pid: Long, key: String, from: Long, to: Long): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE playlistId = :pid AND channelKey IN (:keys) AND start <= :now AND `end` > :now")
    suspend fun nowFor(pid: Long, keys: List<String>, now: Long): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE playlistId = :pid AND channelKey = :key AND `end` > :now ORDER BY start LIMIT :limit")
    suspend fun upcoming(pid: Long, key: String, now: Long, limit: Int = 2): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE playlistId = :pid AND title LIKE '%' || :q || '%' ESCAPE '\\' AND `end` > :now ORDER BY start LIMIT :limit")
    suspend fun search(pid: Long, q: String, now: Long, limit: Int = 60): List<ProgramEntity>

    @Upsert
    suspend fun upsert(items: List<ProgramEntity>)

    @Upsert
    fun upsertNow(items: List<ProgramEntity>)

    @Upsert
    fun upsertEpgChannelsNow(items: List<EpgChannelEntity>)

    @Query("DELETE FROM programs WHERE playlistId = :pid AND gen != :gen")
    suspend fun deleteStaleGen(pid: Long, gen: Long)

    @Query("DELETE FROM programs WHERE `end` < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM programs WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int

    @Upsert
    suspend fun upsertEpgChannels(items: List<EpgChannelEntity>)

    @Query("DELETE FROM epg_channels WHERE playlistId = :pid")
    suspend fun clearEpgChannels(pid: Long)
}

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :pid AND type = :type AND itemId = :id")
    suspend fun removeFavorite(pid: Long, type: ContentType, id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :pid AND type = :type AND itemId = :id)")
    fun observeIsFavorite(pid: Long, type: ContentType, id: String): Flow<Boolean>

    @Query("SELECT itemId FROM favorites WHERE playlistId = :pid AND type = :type")
    fun observeFavoriteIds(pid: Long, type: ContentType): Flow<List<String>>

    @Upsert
    suspend fun saveProgress(p: ProgressEntity)

    @Query("SELECT * FROM progress WHERE playlistId = :pid AND type = :type AND itemId = :id")
    suspend fun progress(pid: Long, type: ContentType, id: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE playlistId = :pid AND type = 'SERIES' AND parentId = :sid")
    fun observeSeriesProgress(pid: Long, sid: String): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE playlistId = :pid AND type = 'SERIES' AND parentId = :sid ORDER BY updatedAt DESC LIMIT 1")
    suspend fun lastEpisodeProgress(pid: Long, sid: String): ProgressEntity?

    /** « Continuer à regarder » : films non terminés + dernier épisode de chaque série. */
    @Query(
        """
        SELECT * FROM progress p WHERE p.playlistId = :pid AND p.type != 'LIVE' AND p.finished = 0 AND p.positionMs > 0
          AND (p.type = 'MOVIE' OR p.updatedAt = (
              SELECT MAX(q.updatedAt) FROM progress q WHERE q.playlistId = p.playlistId AND q.type = 'SERIES' AND q.parentId = p.parentId))
        ORDER BY p.updatedAt DESC LIMIT :limit
        """
    )
    fun observeContinueWatching(pid: Long, limit: Int = 20): Flow<List<ProgressEntity>>

    @Query("DELETE FROM progress WHERE playlistId = :pid AND type = :type AND itemId = :id")
    suspend fun deleteProgress(pid: Long, type: ContentType, id: String)

    @Query("SELECT * FROM favorites WHERE playlistId = :pid")
    suspend fun allFavorites(pid: Long): List<FavoriteEntity>
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE status = 'SCHEDULED'")
    suspend fun scheduled(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE status = 'RECORDING'")
    suspend fun active(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE playlistId = :pid")
    suspend fun forPlaylist(pid: Long): List<RecordingEntity>

    @Query("SELECT * FROM reminders WHERE playlistId = :pid")
    suspend fun remindersFor(pid: Long): List<ReminderEntity>

    @Insert
    suspend fun insert(r: RecordingEntity): Long

    @Update
    suspend fun update(r: RecordingEntity)

    @Delete
    suspend fun delete(r: RecordingEntity)

    @Query("SELECT * FROM reminders WHERE start > :now ORDER BY start")
    fun observeReminders(now: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE start > :now ORDER BY start")
    suspend fun upcomingReminders(now: Long): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminder(r: ReminderEntity)

    @Query("DELETE FROM reminders WHERE playlistId = :pid AND channelId = :cid AND start = :start")
    suspend fun deleteReminder(pid: Long, cid: String, start: Long)

    @Query("SELECT * FROM reminders WHERE playlistId = :pid")
    fun observeRemindersFor(pid: Long): Flow<List<ReminderEntity>>
}

@Dao
interface TmdbDao {
    @Query("SELECT * FROM tmdb_cache WHERE `key` = :key")
    suspend fun get(key: String): TmdbCacheEntity?

    @Upsert
    suspend fun put(e: TmdbCacheEntity)

    @Query("DELETE FROM tmdb_cache WHERE fetchedAt < :before")
    suspend fun purge(before: Long)
}

/** Ligne minimale pour calculer le regroupement des doublons. */
data class GroupRow(val id: String, val title: String, val year: Int?, val tmdbId: String?, val groupKey: String?)

/** Nombre de versions par œuvre. */
data class GroupCount(val k: String, val c: Int)

/** Œuvre regroupée (statistiques). */
data class GroupSummary(val k: String, val c: Int, val title: String)

/**
 * Regroupement des doublons films/séries. Les lignes du fournisseur ne sont jamais supprimées ici :
 * seule la colonne calculée `groupKey` est écrite.
 */
@Dao
interface VodDao {
    @Query("SELECT id, title, year, tmdbId, groupKey FROM movies WHERE playlistId = :pid")
    suspend fun movieRows(pid: Long): List<GroupRow>

    @Query("SELECT id, title, year, tmdbId, groupKey FROM series WHERE playlistId = :pid")
    suspend fun seriesRows(pid: Long): List<GroupRow>

    @Query("UPDATE movies SET groupKey = :key WHERE playlistId = :pid AND id IN (:ids)")
    suspend fun setMovieKey(pid: Long, key: String, ids: List<String>)

    @Query("UPDATE series SET groupKey = :key WHERE playlistId = :pid AND id IN (:ids)")
    suspend fun setSeriesKey(pid: Long, key: String, ids: List<String>)

    @Query("UPDATE movies SET groupKey = :to WHERE playlistId = :pid AND groupKey = :from")
    suspend fun moveMovieGroup(pid: Long, from: String, to: String)

    @Query("UPDATE series SET groupKey = :to WHERE playlistId = :pid AND groupKey = :from")
    suspend fun moveSeriesGroup(pid: Long, from: String, to: String)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :pid AND groupKey IS NULL")
    suspend fun ungroupedMovies(pid: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :pid AND groupKey IS NULL")
    suspend fun ungroupedSeries(pid: Long): Int

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND groupKey = :key ORDER BY position")
    suspend fun movieVersions(pid: Long, key: String): List<MovieEntity>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND groupKey = :key ORDER BY position")
    suspend fun seriesVersions(pid: Long, key: String): List<SeriesEntity>

    @Query("SELECT groupKey AS k, COUNT(*) AS c FROM movies WHERE playlistId = :pid AND groupKey IN (:keys) GROUP BY groupKey")
    suspend fun movieCounts(pid: Long, keys: List<String>): List<GroupCount>

    @Query("SELECT groupKey AS k, COUNT(*) AS c FROM series WHERE playlistId = :pid AND groupKey IN (:keys) GROUP BY groupKey")
    suspend fun seriesCounts(pid: Long, keys: List<String>): List<GroupCount>

    @Query("SELECT id FROM movies WHERE playlistId = :pid AND (tmdbId = :tmdbId OR groupKey = 'tmdb:' || :tmdbId) LIMIT 1")
    suspend fun movieWithTmdb(pid: Long, tmdbId: String): String?

    @Query("SELECT id FROM series WHERE playlistId = :pid AND (tmdbId = :tmdbId OR groupKey = 'tmdb:' || :tmdbId) LIMIT 1")
    suspend fun seriesWithTmdb(pid: Long, tmdbId: String): String?

    @Query("SELECT * FROM tmdb_links WHERE playlistId = :pid AND type = :type")
    suspend fun links(pid: Long, type: ContentType): List<TmdbLinkEntity>

    @Upsert
    suspend fun putLink(link: TmdbLinkEntity)

    @Query("SELECT itemId FROM vod_choice WHERE playlistId = :pid AND type = :type AND groupKey = :key")
    suspend fun choice(pid: Long, type: ContentType, key: String): String?

    @Upsert
    suspend fun putChoice(c: VodChoiceEntity)

    // --- statistiques ---
    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :pid AND tmdbId IS NOT NULL AND tmdbId != ''")
    suspend fun moviesWithTmdb(pid: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :pid AND tmdbId IS NOT NULL AND tmdbId != ''")
    suspend fun seriesWithTmdbCount(pid: Long): Int

    @Query("SELECT COUNT(DISTINCT COALESCE(groupKey, 'id:' || id)) FROM movies WHERE playlistId = :pid")
    suspend fun movieGroups(pid: Long): Int

    @Query("SELECT COUNT(DISTINCT COALESCE(groupKey, 'id:' || id)) FROM series WHERE playlistId = :pid")
    suspend fun seriesGroups(pid: Long): Int

    @Query(
        "SELECT groupKey AS k, COUNT(*) AS c, MIN(title) AS title FROM movies WHERE playlistId = :pid AND groupKey IS NOT NULL " +
            "GROUP BY groupKey HAVING COUNT(*) > 1 ORDER BY COUNT(*) DESC LIMIT :limit"
    )
    suspend fun biggestMovieGroups(pid: Long, limit: Int = 20): List<GroupSummary>

    @Query(
        "SELECT groupKey AS k, COUNT(*) AS c, MIN(title) AS title FROM series WHERE playlistId = :pid AND groupKey IS NOT NULL " +
            "GROUP BY groupKey HAVING COUNT(*) > 1 ORDER BY COUNT(*) DESC LIMIT :limit"
    )
    suspend fun biggestSeriesGroups(pid: Long, limit: Int = 20): List<GroupSummary>

    @Query("DELETE FROM tmdb_links WHERE playlistId = :pid")
    suspend fun deleteLinks(pid: Long)

    @Query("DELETE FROM vod_choice WHERE playlistId = :pid")
    suspend fun deleteChoices(pid: Long)

    @Query("SELECT id, groupKey AS k FROM movies WHERE playlistId = :pid AND id IN (:ids)")
    suspend fun movieKeys(pid: Long, ids: List<String>): List<IdKey>

    @Query("SELECT id, groupKey AS k FROM series WHERE playlistId = :pid AND id IN (:ids)")
    suspend fun seriesKeys(pid: Long, ids: List<String>): List<IdKey>

    /** Dernière progression parmi plusieurs versions d'un film (reprise partagée). */
    @Query("SELECT * FROM progress WHERE playlistId = :pid AND type = 'MOVIE' AND itemId IN (:ids) ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestMovieProgress(pid: Long, ids: List<String>): ProgressEntity?

    @Query("SELECT * FROM progress WHERE playlistId = :pid AND type = 'MOVIE' AND itemId IN (:ids) ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestMovieProgress(pid: Long, ids: List<String>): Flow<ProgressEntity?>

    /** Progression des épisodes de toutes les versions d'une série. */
    @Query("SELECT * FROM progress WHERE playlistId = :pid AND type = 'SERIES' AND parentId IN (:sids)")
    fun observeSeriesProgressIn(pid: Long, sids: List<String>): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE playlistId = :pid AND type = 'SERIES' AND parentId IN (:sids) ORDER BY updatedAt DESC LIMIT 1")
    suspend fun lastEpisodeProgressIn(pid: Long, sids: List<String>): ProgressEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :pid AND type = :type AND itemId IN (:ids))")
    fun observeAnyFavorite(pid: Long, type: ContentType, ids: List<String>): Flow<Boolean>

    @Query("SELECT itemId FROM favorites WHERE playlistId = :pid AND type = :type AND itemId IN (:ids)")
    suspend fun favoriteAmong(pid: Long, type: ContentType, ids: List<String>): List<String>

    @Query("SELECT * FROM episodes WHERE playlistId = :pid AND seriesId IN (:sids) ORDER BY season, episode")
    fun observeEpisodesIn(pid: Long, sids: List<String>): Flow<List<EpisodeEntity>>
}

data class IdKey(val id: String, val k: String?)