package com.sokoul.iptv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        CategoryPrefEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        SeriesDetailsEntity::class,
        ProgramEntity::class,
        EpgChannelEntity::class,
        FavoriteEntity::class,
        ProgressEntity::class,
        RecordingEntity::class,
        ReminderEntity::class,
        TmdbCacheEntity::class,
        TmdbLinkEntity::class,
        VodChoiceEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun categories(): CategoryDao
    abstract fun channels(): ChannelDao
    abstract fun movies(): MovieDao
    abstract fun series(): SeriesDao
    abstract fun programs(): ProgramDao
    abstract fun library(): LibraryDao
    abstract fun recordings(): RecordingDao
    abstract fun tmdb(): TmdbDao
    abstract fun vod(): VodDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "sokoul.db")
            // WAL : lectures fluides pendant les grosses synchronisations EPG
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // migrations explicites uniquement : jamais d'effacement silencieux des données
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides fun playlists(db: AppDatabase) = db.playlists()
    @Provides fun categories(db: AppDatabase) = db.categories()
    @Provides fun channels(db: AppDatabase) = db.channels()
    @Provides fun movies(db: AppDatabase) = db.movies()
    @Provides fun series(db: AppDatabase) = db.series()
    @Provides fun programs(db: AppDatabase) = db.programs()
    @Provides fun library(db: AppDatabase) = db.library()
    @Provides fun recordings(db: AppDatabase) = db.recordings()
    @Provides fun tmdb(db: AppDatabase) = db.tmdb()
    @Provides fun vod(db: AppDatabase) = db.vod()
}

/** Évolutions du schéma. Chaque version ajoute sans rien supprimer. */
object Migrations {
    /** v2 : regroupement des doublons films/séries (clé calculée, liens TMDB, version préférée). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `movies` ADD COLUMN `groupKey` TEXT")
            db.execSQL("ALTER TABLE `series` ADD COLUMN `groupKey` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_playlistId_groupKey` ON `movies` (`playlistId`, `groupKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_playlistId_groupKey` ON `series` (`playlistId`, `groupKey`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tmdb_links` (`playlistId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                    "`tmdbId` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `type`, `itemId`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vod_choice` (`playlistId` INTEGER NOT NULL, `type` TEXT NOT NULL, `groupKey` TEXT NOT NULL, " +
                    "`itemId` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `type`, `groupKey`))"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}