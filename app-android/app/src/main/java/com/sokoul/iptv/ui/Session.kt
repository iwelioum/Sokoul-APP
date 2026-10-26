package com.sokoul.iptv.ui

import com.sokoul.iptv.data.PinHasher
import com.sokoul.iptv.data.PinThrottle
import com.sokoul.iptv.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'une saisie de code parental, avec le message à afficher en cas d'échec. */
data class PinCheck(val ok: Boolean, val message: String? = null) {
    companion object { val OK = PinCheck(true) }
}

/**
 * Contrôle parental.
 * - Code haché (PBKDF2), jamais stocké en clair.
 * - 5 essais, puis blocage croissant (persistant : redémarrer l'app ne le lève pas).
 * - Déverrouillage valable [UNLOCK_TTL_MS] après le dernier accès, et annulé dès que l'app passe en arrière-plan.
 */
@Singleton
class ParentalGate @Inject constructor(
    private val settings: SettingsStore,
    private val profiles: com.sokoul.iptv.data.repo.ProfileRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile private var lastAccess = 0L

    /** Vrai tant que le réglage n'est pas chargé : par prudence on considère qu'un code existe. */
    private val pinSet: StateFlow<Boolean> = settings.settings.map { it.parentalPin.isNotBlank() }
        .stateIn(scope, SharingStarted.Eagerly, true)

    init {
        // Ancien code stocké en clair : on le remplace par son hash.
        scope.launch {
            val stored = settings.current().parentalPin
            if (stored.isNotEmpty() && !PinHasher.isHash(stored)) settings.update { it.copy(parentalPin = PinHasher.hash(stored)) }
        }
    }

    suspend fun hasPin(): Boolean = settings.current().parentalPin.isNotBlank()

    /**
     * Accès libre aux groupes verrouillés : aucun code défini, ou déverrouillage encore valable.
     * Chaque accès réussi prolonge le déverrouillage.
     */
    fun isOpen(now: Long = System.currentTimeMillis()): Boolean {
        // profil Enfant : les groupes verrouillés restent fermés, code ou pas
        if (profiles.kids.value) return false
        if (!pinSet.value) return true
        if (!_unlocked.value) return false
        if (now - lastAccess > UNLOCK_TTL_MS) { lock(); return false }
        lastAccess = now
        return true
    }

    /** Version sûre au démarrage (lit le réglage au lieu du cache). */
    suspend fun canAccessLocked(now: Long = System.currentTimeMillis()): Boolean = !profiles.isKids() && (!hasPin() || isOpen(now))

    suspend fun tryUnlock(pin: String, now: Long = System.currentTimeMillis()): PinCheck = mutex.withLock {
        val (failures, lockedUntil) = settings.pinAttempts()
        if (lockedUntil > now) return@withLock PinCheck(false, blockedMessage(lockedUntil - now))
        val stored = settings.current().parentalPin
        if (stored.isEmpty()) { unlockNow(now); return@withLock PinCheck.OK }
        val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, stored) }
        if (ok) {
            settings.setPinAttempts(0, 0)
            if (!PinHasher.isHash(stored)) settings.update { it.copy(parentalPin = PinHasher.hash(pin)) }
            unlockNow(now)
            return@withLock PinCheck.OK
        }
        val n = failures + 1
        val wait = PinThrottle.lockDuration(n)
        settings.setPinAttempts(n, if (wait > 0) now + wait else 0)
        val left = PinThrottle.FREE_ATTEMPTS - n
        PinCheck(false, if (wait > 0) blockedMessage(wait) else "Code incorrect ($left essai${if (left > 1) "s" else ""} restant${if (left > 1) "s" else ""})")
    }

    /** Enregistre un nouveau code (haché). */
    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin)) { "Le code doit comporter 4 chiffres" }
        val h = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        settings.update { it.copy(parentalPin = h) }
        settings.setPinAttempts(0, 0)
    }

    suspend fun removePin() {
        settings.update { it.copy(parentalPin = "") }
        settings.setPinAttempts(0, 0)
        lock()
    }

    fun lock() { _unlocked.value = false }

    private fun unlockNow(now: Long) { lastAccess = now; _unlocked.value = true }

    private fun blockedMessage(ms: Long): String {
        val s = ((ms + 999) / 1000).coerceAtLeast(1)
        val wait = if (s >= 60) "${(s + 59) / 60} min" else "$s s"
        return "Trop d'essais : réessayez dans $wait"
    }

    companion object {
        const val UNLOCK_TTL_MS = 10 * 60_000L
    }
}

/** Chaînes choisies pour le multi-écran (4 au maximum). */
@Singleton
class MultiviewSession @Inject constructor() {
    data class Slot(val playlistId: Long, val channelId: String)

    enum class AddResult { ADDED, ALREADY_PRESENT, FULL }

    private val _slots = MutableStateFlow<List<Slot?>>(List(SIZE) { null })
    val slots: StateFlow<List<Slot?>> = _slots.asStateFlow()

    /** Ajoute la chaîne dans la première case libre ; ne remplace jamais une case occupée. */
    fun add(playlistId: Long, channelId: String): AddResult {
        var result = AddResult.FULL
        _slots.update { list ->
            if (list.any { it?.channelId == channelId && it.playlistId == playlistId }) { result = AddResult.ALREADY_PRESENT; return@update list }
            val i = list.indexOfFirst { it == null }
            if (i < 0) { result = AddResult.FULL; return@update list }
            result = AddResult.ADDED
            list.toMutableList().also { it[i] = Slot(playlistId, channelId) }
        }
        return result
    }

    fun set(index: Int, slot: Slot?) {
        if (index !in 0 until SIZE) return
        _slots.update { it.toMutableList().also { l -> l[index] = slot } }
    }

    /** Vide les cases d'une playlist supprimée. */
    fun removePlaylist(playlistId: Long) = _slots.update { list -> list.map { s -> s?.takeIf { it.playlistId != playlistId } } }

    companion object { const val SIZE = 4 }
}
