package com.sokoul.iptv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sokoul.iptv.data.SettingsStore
import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.CategoryEntity
import com.sokoul.iptv.data.db.CategoryPrefEntity
import com.sokoul.iptv.data.db.ChannelEntity
import com.sokoul.iptv.data.db.ContentType
import com.sokoul.iptv.data.db.KidsAccess
import com.sokoul.iptv.data.db.Migrations
import com.sokoul.iptv.data.db.ProfileEntity
import com.sokoul.iptv.data.repo.LibraryRepository
import com.sokoul.iptv.data.repo.ProfileRepository
import com.sokoul.iptv.ui.ParentalGate
import com.sokoul.iptv.ui.screens.profiles.ProfileRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Profils : favoris et reprise séparés, profil Enfant limité aux groupes adaptés. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProfilesTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var lib: LibraryRepository
    private val pid = 1L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Migrations.seed(db)
            }).build()
        profiles = ProfileRepository(db)
        lib = LibraryRepository(db)
        runBlocking {
            db.categories().upsert(
                listOf(
                    CategoryEntity(pid, ContentType.LIVE, "kids", "FR | KIDS", 0, 1),
                    CategoryEntity(pid, ContentType.LIVE, "sport", "FR | SPORT", 1, 1),
                    CategoryEntity(pid, ContentType.LIVE, "anim", "Dessins animés", 2, 1),
                    CategoryEntity(pid, ContentType.LIVE, "xxx", "Kids XXX adult", 3, 1),
                )
            )
            db.channels().upsert(listOf(channel("1", "kids"), channel("2", "sport"), channel("3", "anim"), channel("4", "xxx")))
        }
    }

    @After fun tearDown() = db.close()

    private fun channel(id: String, cat: String) =
        ChannelEntity(pid, id, id.toInt(), "Ch $id", "ch $id", null, cat, null, null, "ch $id", null, null, null, 0, null, null, id.toInt(), 1)

    @Test fun `le profil Principal existe des la creation`() = runBlocking {
        assertEquals(listOf(ProfileEntity.MAIN), profiles.profiles.value.ifEmpty { db.profiles().all() }.map { it.id })
        assertEquals(ProfileEntity.MAIN, profiles.currentId())
    }

    @Test fun `favoris et reprise appartiennent au profil en cours`() = runBlocking {
        lib.toggleFavorite(pid, ContentType.MOVIE, "m1")
        lib.saveProgress(pid, ContentType.MOVIE, "m1", null, "Film", null, null, 600_000, 6_000_000)
        val other = profiles.create("Léa", kids = false)
        profiles.switchTo(other)
        assertTrue(lib.favoriteIds(pid, ContentType.MOVIE).first().isEmpty())
        assertTrue(lib.continueWatching(pid).first().isEmpty())
        lib.toggleFavorite(pid, ContentType.MOVIE, "m2")
        assertEquals(listOf("m2"), lib.favoriteIds(pid, ContentType.MOVIE).first())
        profiles.switchTo(ProfileEntity.MAIN)
        assertEquals(listOf("m1"), lib.favoriteIds(pid, ContentType.MOVIE).first())
        assertEquals(listOf("m1"), lib.continueWatching(pid).first().map { it.itemId })
        // supprimer un profil efface sa liste, pas celle des autres
        profiles.delete(other)
        assertEquals(1, db.library().allFavorites(pid).size)
    }

    @Test fun `profil Enfant limite aux groupes adaptes`() = runBlocking {
        assertEquals(setOf("1", "2", "3", "4"), db.channels().listAllVisible(pid).map { it.id }.toSet())
        profiles.switchTo(profiles.create("Petit", kids = true))
        assertEquals(setOf("1", "3"), db.channels().listAllVisible(pid).map { it.id }.toSet())
        assertEquals(setOf("sport", "xxx"), db.categories().observeLocked(pid, ContentType.LIVE).first().toSet())
        val hidden = db.categories().observe(pid, ContentType.LIVE).first().filter { it.hidden }.map { it.categoryId }.toSet()
        assertEquals(setOf("sport", "xxx"), hidden)
        // autorisé à la main / interdit à la main
        db.categories().upsertPref(CategoryPrefEntity(pid, ContentType.LIVE, "sport", kids = KidsAccess.ALLOWED))
        db.categories().upsertPref(CategoryPrefEntity(pid, ContentType.LIVE, "anim", kids = KidsAccess.BLOCKED))
        assertEquals(setOf("1", "2"), db.channels().listAllVisible(pid).map { it.id }.toSet())
        // retour au profil adulte : tout revient
        profiles.switchTo(ProfileEntity.MAIN)
        assertEquals(4, db.channels().listAllVisible(pid).size)
    }

    @Test fun `profil Enfant ferme les groupes verrouilles meme sans code`() = runBlocking {
        val settings = SettingsStore(context)
        settings.update { it.copy(parentalPin = "") }
        val gate = ParentalGate(settings, profiles)
        assertTrue(gate.canAccessLocked())
        profiles.switchTo(profiles.create("Petit", kids = true))
        assertFalse(gate.canAccessLocked())
    }

    @Test fun `quitter un profil Enfant demande le code`() {
        val main = ProfileEntity(0, "Principal")
        val kid = ProfileEntity(1, "Petit", kids = true)
        val kid2 = ProfileEntity(2, "Autre", kids = true)
        assertTrue(ProfileRules.needsPin(kid, main, hasPin = true))
        assertFalse(ProfileRules.needsPin(kid, main, hasPin = false))
        assertFalse(ProfileRules.needsPin(kid, kid2, hasPin = true))
        assertFalse(ProfileRules.needsPin(main, kid, hasPin = true))
    }
}

class KidsRatingTest {
    @Test fun `classifications enfant et adulte`() {
        listOf(null, "", "U", "TP", "-10", "10", "G", "PG", "TV-Y7", "TV-G").forEach { assertTrue(it.toString(), com.sokoul.iptv.data.repo.KidsRating.allowed(it)) }
        listOf("-12", "12", "-16", "16+", "18", "PG-13", "R", "TV-14", "TV-MA", "FSK 16").forEach { assertFalse(it, com.sokoul.iptv.data.repo.KidsRating.allowed(it)) }
        assertFalse(com.sokoul.iptv.data.repo.KidsRating.allowed("U", listOf("Animation", "Horreur")))
    }
}
