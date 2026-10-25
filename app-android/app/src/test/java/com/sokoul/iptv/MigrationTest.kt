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

/** Mises à jour de la base installée (v1 → v4) : aucune donnée perdue. */
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
}
