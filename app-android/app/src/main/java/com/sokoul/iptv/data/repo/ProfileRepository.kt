package com.sokoul.iptv.data.repo

import com.sokoul.iptv.data.db.AppDatabase
import com.sokoul.iptv.data.db.ProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profils de l'app (« Qui regarde ? »). Chaque profil a ses favoris, sa reprise de lecture et ses recommandations ;
 * le profil en cours est rangé dans la base, si bien que toutes les listes se mettent à jour d'elles-mêmes au changement.
 * Un profil Enfant ne voit que les groupes adaptés et n'a accès ni aux réglages ni à Real-Debrid.
 */
@Singleton
class ProfileRepository @Inject constructor(private val db: AppDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao get() = db.profiles()

    val profiles: StateFlow<List<ProfileEntity>> = dao.observeAll().catch { }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val current: StateFlow<ProfileEntity?> = dao.observeCurrent().catch { }.stateIn(scope, SharingStarted.Eagerly, null)
    /** Profil Enfant actif (faux tant que rien n'est chargé : le contrôle parental fait alors foi). */
    val kids: StateFlow<Boolean> = current.map { it?.kids == true }.stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun currentId(): Long = dao.current()?.id ?: ProfileEntity.MAIN
    suspend fun isKids(): Boolean = dao.current()?.kids == true

    suspend fun switchTo(id: Long) = dao.setCurrent(id)

    /** Crée un profil et renvoie son identifiant. */
    suspend fun create(name: String, kids: Boolean, color: Int? = null): Long = db.withTransaction {
        val all = dao.all()
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        dao.upsert(ProfileEntity(id, name.trim().ifEmpty { "Profil $id" }.take(MAX_NAME), color ?: (id % AVATAR_COLORS).toInt(), kids, all.size))
        id
    }

    suspend fun update(p: ProfileEntity) = dao.upsert(p.copy(name = p.name.trim().ifEmpty { "Profil" }.take(MAX_NAME)))

    /** Supprime un profil, ses favoris et sa reprise. Le profil Principal ne se supprime pas. */
    suspend fun delete(id: Long) {
        if (id == ProfileEntity.MAIN) return
        db.withTransaction {
            if (currentId() == id) dao.setCurrent(ProfileEntity.MAIN)
            dao.deleteFavorites(id)
            dao.deleteProgress(id)
            dao.deleteProfile(id)
        }
    }

    companion object {
        const val MAX_NAME = 20
        const val MAX_PROFILES = 6
        /** Taille de la palette des avatars (ui/theme : SokoulColors.avatar). */
        const val AVATAR_COLORS = 6
    }
}

/** Profil en cours, pour écrire un favori ou une reprise. */
suspend fun AppDatabase.currentProfileId(): Long = profiles().current()?.id ?: ProfileEntity.MAIN
