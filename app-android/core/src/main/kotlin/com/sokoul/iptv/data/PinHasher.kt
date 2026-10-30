package com.sokoul.iptv.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hachage du code parental (PBKDF2 + sel aléatoire) : le code n'est jamais stocké en clair.
 * Format stocké : `pbkdf2:<itérations>:<sel hex>:<hash hex>`.
 * Un ancien code à 4 chiffres stocké en clair reste accepté puis est re-haché.
 */
object PinHasher {
    private const val PREFIX = "pbkdf2:"
    private const val ITERATIONS = 20_000

    fun isValidPin(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }

    fun isHash(stored: String) = stored.startsWith(PREFIX)

    /** Valeur stockée acceptable : vide, ancien code en clair, ou hash bien formé. */
    fun isValidStored(stored: String) = stored.isEmpty() || isValidPin(stored) || parse(stored) != null

    fun hash(pin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }, iterations: Int = ITERATIONS): String {
        require(isValidPin(pin)) { "Le code doit comporter 4 chiffres" }
        return PREFIX + iterations + ":" + salt.hex() + ":" + derive(pin, salt, iterations).hex()
    }

    fun verify(pin: String, stored: String): Boolean {
        if (stored.isEmpty() || !isValidPin(pin)) return false
        if (!isHash(stored)) return MessageDigest.isEqual(pin.toByteArray(), stored.toByteArray())
        val (iterations, salt, expected) = parse(stored) ?: return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun parse(stored: String): Triple<Int, ByteArray, ByteArray>? {
        if (!isHash(stored)) return null
        val parts = stored.removePrefix(PREFIX).split(':')
        if (parts.size != 3) return null
        val it = parts[0].toIntOrNull()?.takeIf { it in 1_000..1_000_000 } ?: return null
        val salt = parts[1].unhex() ?: return null
        val hash = parts[2].unhex() ?: return null
        if (salt.size < 8 || hash.size < 16) return null
        return Triple(it, salt, hash)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        // SHA1 : disponible dès Android 7 (minSdk 24), contrairement à SHA256 (API 26)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded.also { spec.clearPassword() }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private fun String.unhex(): ByteArray? {
        if (length % 2 != 0 || any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

/**
 * Limitation des essais : 5 essais libres, puis attente croissante (30 s, 1 min, 2 min… plafonnée à 15 min).
 */
object PinThrottle {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 15 * 60_000L

    /** Délai de blocage après [failures] échecs consécutifs (0 tant qu'il reste des essais libres). */
    fun lockDuration(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val exp = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_MS shl exp).coerceAtMost(MAX_MS)
    }
}
