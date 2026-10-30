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
        RecordingRuleEntity::class,
        ReminderEntity::class,
        TmdbCacheEntity::class,
        TmdbLinkEntity::class,
        VodChoiceEntity::class,
        TmdbMetaEntity::class,
        TmdbMatchEntity::class,
        VodProbeEntity::class,
        ProfileEntity::class,
        CurrentProfileEntity::class,
    ],
    version = 10,
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
    abstract fun browse(): BrowseDao
    abstract fun profiles(): ProfileDao
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
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Migrations.seed(db)
            })
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
    @Provides fun browse(db: AppDatabase) = db.browse()
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

    /** v3 : métadonnées TMDB en français (rangées éditoriales, sagas) et correspondances œuvre → TMDB. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tmdb_meta` (`type` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`originalTitle` TEXT, `overview` TEXT, `overviewLang` TEXT, `tagline` TEXT, `genres` TEXT, `poster` TEXT, " +
                    "`backdrop` TEXT, `logo` TEXT, `rating` REAL, `year` INTEGER, `runtimeMin` INTEGER, `seasons` INTEGER, " +
                    "`collectionId` INTEGER, `collectionName` TEXT, `certification` TEXT, `fetchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`type`, `tmdbId`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tmdb_meta_collectionId` ON `tmdb_meta` (`collectionId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tmdb_match` (`playlistId` INTEGER NOT NULL, `type` TEXT NOT NULL, `workKey` TEXT NOT NULL, " +
                    "`tmdbId` INTEGER, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `type`, `workKey`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tmdb_match_type_tmdbId` ON `tmdb_match` (`type`, `tmdbId`)")
        }
    }

    /**
     * v4 : progression des épisodes rattachée à la saison/épisode (reprise juste quelle que soit la version)
     * et marquage manuel distingué du visionnage réel.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `progress` ADD COLUMN `season` INTEGER")
            db.execSQL("ALTER TABLE `progress` ADD COLUMN `episode` INTEGER")
            db.execSQL("ALTER TABLE `progress` ADD COLUMN `manual` INTEGER NOT NULL DEFAULT 0")
            // épisodes déjà regardés : saison/numéro recopiés depuis la liste des épisodes
            db.execSQL(
                "UPDATE `progress` SET " +
                    "`season` = (SELECT e.season FROM episodes e WHERE e.playlistId = progress.playlistId AND e.seriesId = progress.parentId AND e.id = progress.itemId), " +
                    "`episode` = (SELECT e.episode FROM episodes e WHERE e.playlistId = progress.playlistId AND e.seriesId = progress.parentId AND e.id = progress.itemId) " +
                    "WHERE `type` = 'SERIES'"
            )
            // ancien « marquer comme vu » : position 1 ms sur 1 ms
            db.execSQL("UPDATE `progress` SET `manual` = 1 WHERE `type` = 'SERIES' AND `positionMs` = 1 AND `durationMs` = 1")
        }
    }

    /**
     * v5 : de quoi explorer le catalogue (genres, langue, votes, mots-clés TMDB). Les fiches existantes
     * gardent tout le reste ; l'indexation complète les nouveaux champs en arrière-plan.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tmdb_meta` ADD COLUMN `originalLanguage` TEXT")
            db.execSQL("ALTER TABLE `tmdb_meta` ADD COLUMN `genreIds` TEXT")
            db.execSQL("ALTER TABLE `tmdb_meta` ADD COLUMN `voteCount` INTEGER")
            db.execSQL("ALTER TABLE `tmdb_meta` ADD COLUMN `keywords` TEXT")
        }
    }

    /**
     * v6 : index sur le nom normalisé des films et séries : retrouver un titre publié sans année (sagas, Ma liste)
     * sans parcourir tout le catalogue.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_playlistId_searchName` ON `movies` (`playlistId`, `searchName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_playlistId_searchName` ON `series` (`playlistId`, `searchName`)")
        }
    }

    /**
     * v7 : qualité réelle des versions (résolution, HDR, codecs, son), mesurée par le panel ou le lecteur.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vod_probe` (`playlistId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                    "`width` INTEGER, `height` INTEGER, `videoCodec` TEXT, `hdr` TEXT, `fps` REAL, `audioCodec` TEXT, `audioChannels` INTEGER, " +
                    "`audioLanguage` TEXT, `bitrateKbps` INTEGER, `durationSecs` INTEGER, `fromPlayer` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`playlistId`, `type`, `itemId`))"
            )
        }
    }

    /**
     * v8 : profils. Favoris et reprises appartiennent à un profil (clé élargie, tables recopiées) ;
     * tout l'existant revient au profil 0 « Principal », qui devient le profil courant.
     * Groupes : choix « Enfants » (0 = selon le nom, 1 = oui, 2 = non).
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `kids` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `current_profile` (`k` INTEGER NOT NULL, `profileId` INTEGER NOT NULL, PRIMARY KEY(`k`))")
            seed(db)

            db.execSQL("CREATE TABLE IF NOT EXISTS `favorites_new` (`playlistId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `profileId` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`profileId`, `playlistId`, `type`, `itemId`))")
            db.execSQL("INSERT INTO `favorites_new` (`playlistId`, `type`, `itemId`, `addedAt`, `profileId`) SELECT `playlistId`, `type`, `itemId`, `addedAt`, 0 FROM `favorites`")
            db.execSQL("DROP TABLE `favorites`")
            db.execSQL("ALTER TABLE `favorites_new` RENAME TO `favorites`")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `progress_new` (`playlistId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemId` TEXT NOT NULL, `parentId` TEXT, `title` TEXT NOT NULL, " +
                    "`subtitle` TEXT, `image` TEXT, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `finished` INTEGER NOT NULL, " +
                    "`season` INTEGER, `episode` INTEGER, `manual` INTEGER NOT NULL DEFAULT 0, `profileId` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`profileId`, `playlistId`, `type`, `itemId`))"
            )
            val cols = "`playlistId`, `type`, `itemId`, `parentId`, `title`, `subtitle`, `image`, `positionMs`, `durationMs`, `updatedAt`, `finished`, `season`, `episode`, `manual`"
            db.execSQL("INSERT INTO `progress_new` ($cols, `profileId`) SELECT $cols, 0 FROM `progress`")
            db.execSQL("DROP TABLE `progress`")
            db.execSQL("ALTER TABLE `progress_new` RENAME TO `progress`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_profileId_playlistId_type_updatedAt` ON `progress` (`profileId`, `playlistId`, `type`, `updatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_profileId_playlistId_parentId` ON `progress` (`profileId`, `playlistId`, `parentId`)")

            db.execSQL("ALTER TABLE `category_prefs` ADD COLUMN `kids` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v9 : règles d'enregistrement en série, indexées par playlist et clé titre + chaîne. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recording_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`playlistId` INTEGER NOT NULL, `channelId` TEXT NOT NULL, `channelName` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `ruleKey` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recording_rules_playlistId_ruleKey` ON `recording_rules` (`playlistId`, `ruleKey`)")
        }
    }

    /** v10 : diffusions refusées au dernier passage d'une règle (conflit, connexions, espace). */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `recording_rules` ADD COLUMN `skipped` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Profil « Principal » et profil courant : posés à la création de la base comme après la migration. */
    fun seed(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO `profiles` (`id`, `name`, `color`, `kids`, `position`) VALUES (0, 'Principal', 0, 0, 0)")
        db.execSQL("INSERT OR IGNORE INTO `current_profile` (`k`, `profileId`) VALUES (0, 0)")
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
}