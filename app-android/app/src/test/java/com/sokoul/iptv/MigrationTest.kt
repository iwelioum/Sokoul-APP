package com.sokoul.iptv

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mises à jour de la base installée (v1 → v7) : aucune donnée perdue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun `migration 1 vers 2 conserve films favoris progression et groupes`() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO playlists (id, name, type, url, lastSync, lastEpgSync, liveCount, movieCount, seriesCount, createdAt) VALUES (1, 'p', 'XTREAM', 'http://h', 0, 0, 0, 2, 1, 0)")
            execSQL("INSERT INTO movies (playlistId, id, name, title, searchName, year, language, quality, poster, rating, categoryId, extension, streamUrl, added, tmdbId, position, gen) VALUES (1, '10', 'Film VF', 'Film', 'film', 2020, 'VF', 'HD', NULL, NULL, 'c', 'mkv', NULL, 0, NULL, 0, 1)")
            execSQL("INSERT INTO movies (playlistId, id, name, title, searchName, year, language, quality, poster, rating, categoryId, extension, streamUrl, added, tmdbId, position, gen) VALUES (1, '11', 'Film MULTI', 'Film', 'film', 2020, 'MULTI', 'HD', NULL, NULL, 'c', 'mkv', NULL, 0, NULL, 1, 1)")
            execSQL("INSERT INTO series (playlistId, id, name, title, searchName, year, poster, backdrop, plot, genre, cast, rating, categoryId, lastModified, tmdbId, position, gen) VALUES (1, '20', 'Serie', 'Serie', 'serie', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'c', 0, NULL, 0, 1)")
            execSQL("INSERT INTO favorites (playlistId, type, itemId, addedAt) VALUES (1, 'MOVIE', '10', 5)")
            execSQL("INSERT INTO progress (playlistId, type, itemId, parentId, title, subtitle, image, positionMs, durationMs, updatedAt, finished) VALUES (1, 'MOVIE', '11', NULL, 'Film', NULL, NULL, 60000, 600000, 9, 0)")
            execSQL("INSERT INTO category_prefs (playlistId, type, categoryId, hidden, locked) VALUES (1, 'MOVIE', 'c', 0, 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, *Migrations.ALL)
        fun count(sql: String) = db.query(sql).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(2, count("SELECT COUNT(*) FROM movies"))
        assertEquals(1, count("SELECT COUNT(*) FROM series"))
        assertEquals(1, count("SELECT COUNT(*) FROM favorites"))
        assertEquals(1, count("SELECT COUNT(*) FROM progress WHERE positionMs = 60000"))
        assertEquals(1, count("SELECT COUNT(*) FROM category_prefs WHERE locked = 1"))
        // clé pas encore calculée : le premier lancement s'en charge (regroupIfNeeded)
        assertEquals(2, count("SELECT COUNT(*) FROM movies WHERE groupKey IS NULL"))
        assertTrue(count("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('tmdb_links','vod_choice')") == 2)
        db.close()
    }

    @Test fun `migration 2 vers 3 ajoute les metadonnees TMDB sans toucher au reste`() {
        helper.createDatabase(dbName + "3", 2).apply {
            execSQL("INSERT INTO playlists (id, name, type, url, lastSync, lastEpgSync, liveCount, movieCount, seriesCount, createdAt) VALUES (1, 'p', 'XTREAM', 'http://h', 0, 0, 0, 1, 0, 0)")
            execSQL("INSERT INTO favorites (playlistId, type, itemId, addedAt) VALUES (1, 'MOVIE', '10', 5)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName + "3", 3, true, *Migrations.ALL)
        fun count(sql: String) = db.query(sql).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count("SELECT COUNT(*) FROM favorites"))
        assertEquals(2, count("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('tmdb_meta','tmdb_match')"))
        assertEquals(0, count("SELECT COUNT(*) FROM tmdb_meta"))
        db.close()
    }

    @Test fun `migration 3 vers 4 rattache la progression aux saisons et repere les marquages manuels`() {
        val name = dbName + "4"
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO playlists (id, name, type, url, lastSync, lastEpgSync, liveCount, movieCount, seriesCount, createdAt) VALUES (1, 'p', 'XTREAM', 'http://h', 0, 0, 0, 1, 1, 0)")
            execSQL("INSERT INTO episodes (playlistId, seriesId, id, season, episode, title, plot, image, durationSecs, extension, streamUrl, rating) VALUES (1, 's', 'e1', 2, 5, 'Ep', NULL, NULL, NULL, 'mkv', NULL, NULL)")
            execSQL("INSERT INTO progress (playlistId, type, itemId, parentId, title, subtitle, image, positionMs, durationMs, updatedAt, finished) VALUES (1, 'SERIES', 'e1', 's', 'Serie', NULL, NULL, 600000, 2400000, 9, 0)")
            execSQL("INSERT INTO progress (playlistId, type, itemId, parentId, title, subtitle, image, positionMs, durationMs, updatedAt, finished) VALUES (1, 'SERIES', 'e2', 's', 'Serie', NULL, NULL, 1, 1, 10, 1)")
            execSQL("INSERT INTO progress (playlistId, type, itemId, parentId, title, subtitle, image, positionMs, durationMs, updatedAt, finished) VALUES (1, 'MOVIE', 'm1', NULL, 'Film', NULL, NULL, 60000, 600000, 11, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 4, true, *Migrations.ALL)
        fun str(sql: String) = db.query(sql).use { it.moveToFirst(); if (it.isNull(0)) null else it.getString(0) }
        assertEquals("2|5|0", str("SELECT season || '|' || episode || '|' || manual FROM progress WHERE itemId = 'e1'"))
        // épisode inconnu de la liste : pas de saison, mais le marquage manuel est reconnu
        assertEquals(null, str("SELECT season FROM progress WHERE itemId = 'e2'"))
        assertEquals("1", str("SELECT manual FROM progress WHERE itemId = 'e2'"))
        assertEquals("0", str("SELECT manual FROM progress WHERE itemId = 'm1'"))
        assertEquals(null, str("SELECT season FROM progress WHERE itemId = 'm1'"))
        assertEquals("3", str("SELECT COUNT(*) FROM progress"))
        db.close()
    }

    @Test fun `migration 4 vers 5 garde les fiches TMDB et ajoute les champs d exploration`() {
        val name = dbName + "5"
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO tmdb_meta (type, tmdbId, title, originalTitle, overview, overviewLang, tagline, genres, poster, backdrop, logo, rating, year, runtimeMin, seasons, collectionId, collectionName, certification, fetchedAt) VALUES ('MOVIE', 603, 'Matrix', NULL, NULL, NULL, NULL, 'Action', NULL, NULL, NULL, 8.2, 1999, 136, NULL, 2344, 'Matrix', NULL, 7)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 5, true, *Migrations.ALL)
        fun str(sql: String) = db.query(sql).use { it.moveToFirst(); if (it.isNull(0)) null else it.getString(0) }
        assertEquals("Matrix|2344|7", str("SELECT title || '|' || collectionId || '|' || fetchedAt FROM tmdb_meta"))
        // pas encore indexée : l'indexation la complétera
        assertEquals(null, str("SELECT keywords FROM tmdb_meta"))
        assertEquals(null, str("SELECT genreIds FROM tmdb_meta"))
        db.close()
    }

    @Test fun `migration 5 vers 6 indexe le nom des films et series`() {
        val name = dbName + "6"
        helper.createDatabase(name, 5).close()
        val db = helper.runMigrationsAndValidate(name, 6, true, *Migrations.ALL)
        val plan = db.query("EXPLAIN QUERY PLAN SELECT id FROM movies WHERE playlistId = 1 AND searchName IN ('dune')").use { c ->
            buildString { while (c.moveToNext()) append(c.getString(c.columnCount - 1)) }
        }
        assertTrue(plan, plan.contains("index_movies_playlistId_searchName"))
        db.close()
    }

    @Test fun `migration 6 vers 7 ajoute la qualite reelle des versions`() {
        val name = dbName + "7"
        helper.createDatabase(name, 6).close()
        val db = helper.runMigrationsAndValidate(name, 7, true, *Migrations.ALL)
        db.execSQL("INSERT INTO vod_probe (playlistId, type, itemId, width, height, hdr, fromPlayer, fetchedAt) VALUES (1, 'MOVIE', '42', 3840, 2160, 'HDR10', 0, 1)")
        val hdr = db.query("SELECT hdr FROM vod_probe WHERE itemId = '42'").use { it.moveToFirst(); it.getString(0) }
        assertEquals("HDR10", hdr)
        db.close()
    }

    @Test fun `migration 7 vers 8 range favoris et reprise dans le profil Principal`() {
        val name = dbName + "8"
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO favorites (playlistId, type, itemId, addedAt) VALUES (1, 'MOVIE', '10', 5)")
            execSQL("INSERT INTO progress (playlistId, type, itemId, parentId, title, subtitle, image, positionMs, durationMs, updatedAt, finished, season, episode, manual) VALUES (1, 'SERIES', 'e1', 's', 'Serie', NULL, NULL, 600000, 2400000, 9, 0, 2, 5, 0)")
            execSQL("INSERT INTO category_prefs (playlistId, type, categoryId, hidden, locked) VALUES (1, 'LIVE', 'c', 1, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 8, true, *Migrations.ALL)
        fun long(sql: String) = db.query(sql).use { it.moveToFirst(); it.getLong(0) }
        assertEquals(0L, long("SELECT profileId FROM favorites WHERE itemId = '10'"))
        assertEquals(600000L, long("SELECT positionMs FROM progress WHERE itemId = 'e1' AND profileId = 0"))
        assertEquals(5L, long("SELECT episode FROM progress WHERE itemId = 'e1'"))
        assertEquals(1L, long("SELECT hidden FROM category_prefs WHERE categoryId = 'c'"))
        assertEquals(0L, long("SELECT kids FROM category_prefs WHERE categoryId = 'c'"))
        assertEquals(1L, long("SELECT COUNT(*) FROM profiles WHERE id = 0 AND kids = 0"))
        assertEquals(0L, long("SELECT profileId FROM current_profile WHERE k = 0"))
        // même élément pour un autre profil : la clé élargie l'accepte
        db.execSQL("INSERT INTO favorites (playlistId, type, itemId, addedAt, profileId) VALUES (1, 'MOVIE', '10', 6, 1)")
        assertEquals(2L, long("SELECT COUNT(*) FROM favorites"))
        db.close()
    }
}
