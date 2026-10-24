package com.sokoul.iptv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    ],
    version = 1,
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
}
