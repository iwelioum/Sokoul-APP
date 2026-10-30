package com.sokoul.desktop.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Base SQLite du PC (sqlite-jdbc). Tables reprises du schéma Room de l'app Android, limitées à ce que le PC utilise.
 * Accès synchronisé : une seule connexion, appelée depuis Dispatchers.IO.
 */
class DesktopDb(file: File) : AutoCloseable {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").apply {
        createStatement().use { it.execute("PRAGMA journal_mode=WAL"); it.execute("PRAGMA foreign_keys=ON") }
    }

    init { migrate() }

    private fun migrate() = exec(
        """CREATE TABLE IF NOT EXISTS playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL,
            url TEXT NOT NULL, username TEXT, password TEXT, epg_url TEXT, user_agent TEXT, synced_at INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE IF NOT EXISTS categories(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL,
            hidden INTEGER NOT NULL DEFAULT 0, locked INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(playlist_id, id))""",
        """CREATE TABLE IF NOT EXISTS channels(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, number INTEGER NOT NULL, name TEXT NOT NULL,
            logo TEXT, category_id TEXT NOT NULL, epg_key TEXT, name_key TEXT NOT NULL, url TEXT, user_agent TEXT, referrer TEXT,
            position INTEGER NOT NULL, PRIMARY KEY(playlist_id, id))""",
        "CREATE INDEX IF NOT EXISTS channels_cat ON channels(playlist_id, category_id, position)",
        """CREATE TABLE IF NOT EXISTS programs(playlist_id INTEGER NOT NULL, channel_key TEXT NOT NULL, start INTEGER NOT NULL,
            stop INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, PRIMARY KEY(playlist_id, channel_key, start))""",
        "CREATE INDEX IF NOT EXISTS programs_time ON programs(playlist_id, stop)",
        """CREATE TABLE IF NOT EXISTS favorites(playlist_id INTEGER NOT NULL, type TEXT NOT NULL, id TEXT NOT NULL, added_at INTEGER NOT NULL,
            PRIMARY KEY(playlist_id, type, id))""",
        """CREATE TABLE IF NOT EXISTS progress(playlist_id INTEGER NOT NULL, type TEXT NOT NULL, id TEXT NOT NULL, title TEXT NOT NULL,
            position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, updated_at INTEGER NOT NULL, finished INTEGER NOT NULL,
            PRIMARY KEY(playlist_id, type, id))""",
        "CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        """CREATE TABLE IF NOT EXISTS movies(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL,
            year INTEGER, poster TEXT, stream_url TEXT, rating REAL, category_id TEXT, extension TEXT, added INTEGER, tmdb_id TEXT,
            position INTEGER NOT NULL, PRIMARY KEY(playlist_id, id))""",
        """CREATE TABLE IF NOT EXISTS series(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL,
            year INTEGER, poster TEXT, backdrop TEXT, plot TEXT, rating REAL, category_id TEXT, last_modified INTEGER,
            tmdb_id TEXT, position INTEGER NOT NULL, PRIMARY KEY(playlist_id, id))""",
        "CREATE INDEX IF NOT EXISTS movies_search ON movies(playlist_id, name)",
        "CREATE INDEX IF NOT EXISTS series_search ON series(playlist_id, name)",
        "CREATE TABLE IF NOT EXISTS tmdb_cache(key TEXT PRIMARY KEY, json TEXT NOT NULL, fetched_at INTEGER NOT NULL)",
        // reprise des films et épisodes TMDB (lus via Real-Debrid ou l'IPTV) : rangée « Reprendre » de l'accueil
        """CREATE TABLE IF NOT EXISTS resume(key TEXT PRIMARY KEY, kind TEXT NOT NULL, tmdb_id INTEGER NOT NULL, season INTEGER,
            episode INTEGER, title TEXT NOT NULL, subtitle TEXT, image TEXT, position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL,
            updated_at INTEGER NOT NULL, finished INTEGER NOT NULL)""",
    )

    init {
        runCatching { exec("ALTER TABLE movies ADD COLUMN stream_url TEXT") }
    }

    @Synchronized
    private fun exec(vararg sql: String) = conn.createStatement().use { st -> sql.forEach { st.execute(it) } }

    @Synchronized
    fun <T> tx(block: () -> T): T {
        conn.autoCommit = false
        try {
            val r = block(); conn.commit(); return r
        } catch (e: Throwable) {
            conn.rollback(); throw e
        } finally {
            conn.autoCommit = true
        }
    }

    // --- playlists ---------------------------------------------------------------------------------------

    @Synchronized
    fun playlists(): List<Playlist> = query("SELECT * FROM playlists ORDER BY id") { it.toPlaylist() }

    @Synchronized
    fun upsertPlaylist(p: Playlist): Long {
        val existing = playlists().firstOrNull { sameSource(it, p) }
        return if (existing != null) {
            update(
                "UPDATE playlists SET name=?, type=?, url=?, username=?, password=?, epg_url=?, user_agent=? WHERE id=?",
                p.name, p.type.name, p.url, p.username, p.password ?: existing.password, p.epgUrl, p.userAgent, existing.id,
            )
            existing.id
        } else {
            conn.prepareStatement(
                "INSERT INTO playlists(name, type, url, username, password, epg_url, user_agent) VALUES(?,?,?,?,?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { st ->
                bind(st, p.name, p.type.name, p.url, p.username, p.password, p.epgUrl, p.userAgent)
                st.executeUpdate()
                st.generatedKeys.use { k -> k.next(); k.getLong(1) }
            }
        }
    }

    @Synchronized
    fun markSynced(pid: Long, at: Long) = update("UPDATE playlists SET synced_at=? WHERE id=?", at, pid)

    @Synchronized
    fun deletePlaylist(pid: Long) = tx {
        listOf("channels", "categories", "programs", "favorites", "progress", "movies", "series").forEach { update("DELETE FROM $it WHERE playlist_id=?", pid) }
        update("DELETE FROM playlists WHERE id=?", pid)
    }

    // --- catégories et chaînes -------------------------------------------------------------------------

    @Synchronized
    fun categories(pid: Long): List<Category> =
        query("SELECT * FROM categories WHERE playlist_id=? ORDER BY position", pid) {
            Category(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), it.getInt("position"), it.getInt("hidden") != 0, it.getInt("locked") != 0)
        }

    /** Remplace catégories et chaînes d'une playlist ; garde les drapeaux masqué / verrouillé déjà posés. */
    @Synchronized
    fun replaceLive(pid: Long, cats: List<Category>, channels: List<Channel>) = tx {
        val flags = categories(pid).associateBy { it.id }
        update("DELETE FROM categories WHERE playlist_id=?", pid)
        update("DELETE FROM channels WHERE playlist_id=?", pid)
        conn.prepareStatement("INSERT OR REPLACE INTO categories VALUES(?,?,?,?,?,?)").use { st ->
            cats.forEach { c ->
                val f = flags[c.id]
                bind(st, pid, c.id, c.name, c.position, if (f?.hidden ?: c.hidden) 1 else 0, if (f?.locked ?: c.locked) 1 else 0)
                st.addBatch()
            }
            st.executeBatch()
        }
        conn.prepareStatement("INSERT OR REPLACE INTO channels VALUES(?,?,?,?,?,?,?,?,?,?,?,?)").use { st ->
            channels.forEachIndexed { i, c ->
                bind(st, pid, c.id, c.number, c.name, c.logo, c.categoryId, c.epgKey, c.nameKey, c.url, c.userAgent, c.referrer, c.position)
                st.addBatch()
                if (i % 2000 == 1999) st.executeBatch()
            }
            st.executeBatch()
        }
    }

    @Synchronized
    fun setCategoryFlags(pid: Long, id: String, hidden: Boolean, locked: Boolean) =
        update("UPDATE categories SET hidden=?, locked=? WHERE playlist_id=? AND id=?", if (hidden) 1 else 0, if (locked) 1 else 0, pid, id)

    @Synchronized
    fun channels(pid: Long, categoryId: String?): List<Channel> = when (categoryId) {
        null, SpecialGroup.ALL -> query(
            "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
                "WHERE c.playlist_id=? AND g.hidden=0 AND g.locked=0 ORDER BY g.position, c.position", pid,
        ) { it.toChannel() }
        SpecialGroup.FAVORITES -> query(
            "SELECT c.* FROM channels c JOIN favorites f ON f.playlist_id=c.playlist_id AND f.type='LIVE' AND f.id=c.id " +
                "WHERE c.playlist_id=? ORDER BY f.added_at", pid,
        ) { it.toChannel() }
        else -> query("SELECT * FROM channels WHERE playlist_id=? AND category_id=? ORDER BY position", pid, categoryId) { it.toChannel() }
    }

    /** Toutes les chaînes, groupes masqués et verrouillés compris (guide, recherche du rapprochement). */
    @Synchronized
    fun allChannels(pid: Long): List<Channel> = query("SELECT * FROM channels WHERE playlist_id=? ORDER BY position", pid) { it.toChannel() }

    /** Recherche par nom dans les groupes visibles (insensible aux accents grâce à la clé normalisée). */
    @Synchronized
    fun searchChannels(pid: Long, key: String, limit: Int = 200): List<Channel> = query(
        "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
            "WHERE c.playlist_id=? AND g.hidden=0 AND g.locked=0 AND (c.name_key LIKE ? OR lower(c.name) LIKE ?) ORDER BY g.position, c.position LIMIT ?",
        pid, "%$key%", "%$key%", limit,
    ) { it.toChannel() }

    /**
     * Recherche dans le guide (« Au programme » de la tablette) : programmes pas encore finis dont le titre contient
     * [text], chacun avec la première chaîne visible qui le diffuse (programmes sans chaîne visible écartés).
     */
    @Synchronized
    fun searchPrograms(pid: Long, text: String, now: Long, limit: Int = 30): List<Pair<Program, Channel>> {
        val progs = query(
            "SELECT * FROM programs WHERE playlist_id=? AND stop>? AND lower(title) LIKE ? ORDER BY start LIMIT ?",
            pid, now, "%${text.trim().lowercase()}%", limit * 4,
        ) { Program(it.getString("channel_key"), it.getLong("start"), it.getLong("stop"), it.getString("title"), it.getString("description")) }
        val keys = progs.mapTo(LinkedHashSet()) { it.channelKey }
        if (keys.isEmpty()) return emptyList()
        val byKey = query(
            "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
                "WHERE c.playlist_id=? AND g.hidden=0 AND g.locked=0 AND c.epg_key IN (${keys.joinToString(",") { "?" }}) ORDER BY g.position, c.position",
            pid, *keys.toTypedArray(),
        ) { it.toChannel() }.groupBy { it.epgKey }.mapValues { it.value.first() }
        return progs.mapNotNull { p -> byKey[p.channelKey]?.let { p to it } }.take(limit)
    }

    @Synchronized
    fun channelCount(pid: Long): Int = query("SELECT COUNT(*) FROM channels WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    @Synchronized
    fun replaceVod(pid: Long, movies: List<Movie>, series: List<Series>) = tx {
        update("DELETE FROM movies WHERE playlist_id=?", pid)
        update("DELETE FROM series WHERE playlist_id=?", pid)
        // colonnes nommées : sur une base plus ancienne, stream_url a été ajoutée en dernier (ALTER TABLE)
        conn.prepareStatement("INSERT INTO movies(playlist_id, id, name, year, poster, stream_url, rating, category_id, extension, added, tmdb_id, position) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)").use { st ->
            movies.forEachIndexed { i, movie ->
                bind(st, pid, movie.id, movie.name, movie.year, movie.poster, movie.streamUrl, movie.rating, movie.categoryId, movie.extension, movie.added, movie.tmdbId, i)
                st.addBatch()
                if (i % 500 == 499) st.executeBatch()
            }
            st.executeBatch()
        }
        conn.prepareStatement("INSERT INTO series(playlist_id, id, name, year, poster, backdrop, plot, rating, category_id, last_modified, tmdb_id, position) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)").use { st ->
            series.forEachIndexed { i, item ->
                bind(st, pid, item.id, item.name, item.year, item.poster, item.backdrop, item.plot, item.rating, item.categoryId, item.lastModified, item.tmdbId, i)
                st.addBatch()
                if (i % 500 == 499) st.executeBatch()
            }
            st.executeBatch()
        }
    }

    @Synchronized
    fun movies(pid: Long, search: String? = null, limit: Int = 500): List<Movie> {
        val q = search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val sql = if (q == null) "SELECT * FROM movies WHERE playlist_id=? ORDER BY position LIMIT ?"
        else "SELECT * FROM movies WHERE playlist_id=? AND lower(name) LIKE ? ORDER BY position LIMIT ?"
        val args = if (q == null) arrayOf(pid, limit) else arrayOf(pid, "%$q%", limit)
        return query(sql, *args) {
            val year = (it.getObject("year") as? Number)?.toInt()
            val rating = (it.getObject("rating") as? Number)?.toDouble()
            val added = (it.getObject("added") as? Number)?.toLong()
            Movie(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), year, it.getString("poster"), rating,
                it.getString("category_id"), it.getString("extension"), added, it.getString("tmdb_id"), it.getString("stream_url"))
        }
    }

    @Synchronized
    fun series(pid: Long, search: String? = null, limit: Int = 500): List<Series> {
        val q = search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val sql = if (q == null) "SELECT * FROM series WHERE playlist_id=? ORDER BY position LIMIT ?"
        else "SELECT * FROM series WHERE playlist_id=? AND lower(name) LIKE ? ORDER BY position LIMIT ?"
        val args = if (q == null) arrayOf(pid, limit) else arrayOf(pid, "%$q%", limit)
        return query(sql, *args) {
            val year = (it.getObject("year") as? Number)?.toInt()
            val rating = (it.getObject("rating") as? Number)?.toDouble()
            val modified = (it.getObject("last_modified") as? Number)?.toLong()
            Series(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), year, it.getString("poster"), it.getString("backdrop"),
                it.getString("plot"), rating, it.getString("category_id"), modified, it.getString("tmdb_id"))
        }
    }

    @Synchronized
    fun movieCount(pid: Long): Int = query("SELECT COUNT(*) FROM movies WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    @Synchronized
    fun seriesCount(pid: Long): Int = query("SELECT COUNT(*) FROM series WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    // --- guide ---------------------------------------------------------------------------------------

    @Synchronized
    fun replacePrograms(pid: Long, programs: Sequence<Program>, keepFrom: Long): Int = tx {
        update("DELETE FROM programs WHERE playlist_id=?", pid)
        var n = 0
        conn.prepareStatement("INSERT OR REPLACE INTO programs VALUES(?,?,?,?,?,?)").use { st ->
            for (p in programs) {
                if (p.end < keepFrom) continue
                bind(st, pid, p.channelKey, p.start, p.end, p.title, p.description)
                st.addBatch()
                if (++n % 5000 == 0) st.executeBatch()
            }
            st.executeBatch()
        }
        n
    }

    @Synchronized
    fun programs(pid: Long, keys: Collection<String>, from: Long, to: Long): List<Program> {
        if (keys.isEmpty()) return emptyList()
        val marks = keys.joinToString(",") { "?" }
        return query(
            "SELECT * FROM programs WHERE playlist_id=? AND channel_key IN ($marks) AND stop>? AND start<? ORDER BY start",
            pid, *keys.toTypedArray(), from, to,
        ) { Program(it.getString("channel_key"), it.getLong("start"), it.getLong("stop"), it.getString("title"), it.getString("description")) }
    }

    /** Chaînes rapprochées du guide par leur nom : (id de chaîne, identifiant du guide). */
    @Synchronized
    fun assignEpgKeys(pid: Long, links: List<Pair<String, String>>) = tx {
        conn.prepareStatement("UPDATE channels SET epg_key=? WHERE playlist_id=? AND id=?").use { st ->
            links.forEach { (id, key) -> bind(st, key, pid, id); st.addBatch() }
            st.executeBatch()
        }
    }

    @Synchronized
    fun programCount(pid: Long): Int = query("SELECT COUNT(*) FROM programs WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    // --- favoris, progression, réglages ----------------------------------------------------------------

    @Synchronized
    fun favoriteIds(pid: Long, type: String = "LIVE"): Set<String> =
        query("SELECT id FROM favorites WHERE playlist_id=? AND type=?", pid, type) { it.getString(1) }.toSet()

    @Synchronized
    fun addFavorite(pid: Long, type: String, id: String, at: Long) =
        update("INSERT OR IGNORE INTO favorites VALUES(?,?,?,?)", pid, type, id, at)

    @Synchronized
    fun removeFavorite(pid: Long, type: String, id: String) =
        update("DELETE FROM favorites WHERE playlist_id=? AND type=? AND id=?", pid, type, id)

    /** Progression : la plus récente l'emporte. */
    @Synchronized
    fun mergeProgress(pid: Long, type: String, id: String, title: String, pos: Long, dur: Long, at: Long, finished: Boolean) {
        val current = query("SELECT updated_at FROM progress WHERE playlist_id=? AND type=? AND id=?", pid, type, id) { it.getLong(1) }.firstOrNull()
        if (current != null && current >= at) return
        update("INSERT OR REPLACE INTO progress VALUES(?,?,?,?,?,?,?,?)", pid, type, id, title, pos, dur, at, if (finished) 1 else 0)
    }

    @Synchronized
    fun progressCount(pid: Long): Int = query("SELECT COUNT(*) FROM progress WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    @Synchronized
    fun setting(key: String): String? = query("SELECT value FROM settings WHERE key=?", key) { it.getString(1) }.firstOrNull()

    @Synchronized
    fun putSetting(key: String, value: String?) {
        if (value == null) update("DELETE FROM settings WHERE key=?", key) else update("INSERT OR REPLACE INTO settings VALUES(?,?)", key, value)
    }

    // --- cache TMDB, reprise --------------------------------------------------------------------------

    @Synchronized
    fun tmdbGet(key: String): Pair<String, Long>? =
        query("SELECT json, fetched_at FROM tmdb_cache WHERE key=?", key) { it.getString(1) to it.getLong(2) }.firstOrNull()

    @Synchronized
    fun tmdbPut(key: String, json: String, at: Long) { update("INSERT OR REPLACE INTO tmdb_cache VALUES(?,?,?)", key, json, at) }

    @Synchronized
    fun saveResume(r: Resume) {
        update(
            "INSERT OR REPLACE INTO resume VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            r.key, r.kind, r.tmdbId, r.season, r.episode, r.title, r.subtitle, r.image, r.positionMs, r.durationMs, r.updatedAt, r.finished,
        )
    }

    @Synchronized
    fun resume(key: String): Resume? = query("SELECT * FROM resume WHERE key=?", key) { it.toResume() }.firstOrNull()

    /** « Reprendre » : lectures commencées et non terminées, la plus récente d'abord ; une seule ligne par série. */
    @Synchronized
    fun resumeList(limit: Int = 20): List<Resume> =
        query("SELECT * FROM resume WHERE finished=0 AND position_ms > 0 ORDER BY updated_at DESC LIMIT ?", limit * 3) { it.toResume() }
            .distinctBy { "${it.kind}:${it.tmdbId}" }.take(limit)

    /** Films vus jusqu'au bout (identifiants TMDB) : part déjà vue d'une saga. */
    @Synchronized
    fun finishedMovies(): Set<Int> =
        query("SELECT tmdb_id FROM resume WHERE kind='movie' AND finished=1") { it.getInt(1) }.toHashSet()

    /** Dernière lecture d'une œuvre (épisode terminé compris) : point de départ de « Reprendre » sur une série. */
    @Synchronized
    fun lastOf(kind: String, tmdbId: Int): Resume? =
        query("SELECT * FROM resume WHERE kind=? AND tmdb_id=? ORDER BY updated_at DESC LIMIT 1", kind, tmdbId) { it.toResume() }.firstOrNull()

    private fun ResultSet.toResume() = Resume(
        getString("key"), getString("kind"), getInt("tmdb_id"), (getObject("season") as? Number)?.toInt(), (getObject("episode") as? Number)?.toInt(),
        getString("title"), getString("subtitle"), getString("image"), getLong("position_ms"), getLong("duration_ms"), getLong("updated_at"), getInt("finished") == 1,
    )

    override fun close() = conn.close()

    // --- outils -------------------------------------------------------------------------------------

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        conn.prepareStatement(sql).use { st ->
            bind(st, *args)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
        }

    private fun update(sql: String, vararg args: Any?): Int = conn.prepareStatement(sql).use { st -> bind(st, *args); st.executeUpdate() }

    private fun bind(st: java.sql.PreparedStatement, vararg args: Any?) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> st.setObject(i + 1, null)
                is Int -> st.setInt(i + 1, a)
                is Long -> st.setLong(i + 1, a)
                is Boolean -> st.setInt(i + 1, if (a) 1 else 0)
                else -> st.setString(i + 1, a.toString())
            }
        }
    }

    private fun ResultSet.toPlaylist() = Playlist(
        getLong("id"), getString("name"), PlaylistType.valueOf(getString("type")), getString("url"), getString("username"),
        getString("password"), getString("epg_url"), getString("user_agent"), getLong("synced_at"),
    )

    private fun ResultSet.toChannel() = Channel(
        getLong("playlist_id"), getString("id"), getInt("number"), getString("name"), getString("logo"), getString("category_id"),
        getString("epg_key"), getString("name_key"), getString("url"), getString("user_agent"), getString("referrer"), getInt("position"),
    )

    companion object {
        /** Même playlist : même serveur (sans « / » final) et même identifiant. */
        fun sameSource(a: Playlist, b: Playlist) =
            a.type == b.type && a.url.trimEnd('/').equals(b.url.trimEnd('/'), ignoreCase = true) && (a.username ?: "") == (b.username ?: "")

        fun open(): DesktopDb = DesktopDb(File(AppDirs.data, "sokoul.db"))
    }
}
