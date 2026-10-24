package com.sokoul.iptv.data.repo

import java.io.IOException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Phrase de passe absente ou incorrecte pour une sauvegarde chiffrée. */
class BackupPassphraseException(message: String) : IOException(message)

/**
 * Chiffrement des sauvegardes contenant des identifiants : PBKDF2 (phrase de passe) → AES-256-GCM.
 * Indépendant de l'appareil : une sauvegarde se restaure sur une autre box.
 */
object BackupCrypto {
    const val MIN_PASSPHRASE = 8
    const val ITERATIONS = 100_000

    data class Sealed(val iterations: Int, val salt: String, val iv: String, val data: String)

    fun seal(plain: ByteArray, passphrase: String, iterations: Int = ITERATIONS): Sealed {
        require(passphrase.length >= MIN_PASSPHRASE) { "Phrase de passe trop courte ($MIN_PASSPHRASE caractères minimum)" }
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        return Sealed(iterations, salt.hex(), iv.hex(), cipher.doFinal(plain).hex())
    }

    fun open(s: Sealed, passphrase: String): ByteArray {
        if (s.iterations !in 10_000..2_000_000) throw IOException("Sauvegarde chiffrée invalide")
        val salt = s.salt.unhex() ?: throw IOException("Sauvegarde chiffrée invalide")
        val iv = s.iv.unhex()?.takeIf { it.size == 12 } ?: throw IOException("Sauvegarde chiffrée invalide")
        val data = s.data.unhex() ?: throw IOException("Sauvegarde chiffrée invalide")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, s.iterations), GCMParameterSpec(128, iv))
            cipher.doFinal(data)
        } catch (e: AEADBadTagException) {
            throw BackupPassphraseException("Phrase de passe incorrecte")
        }
    }

    private fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    private fun ByteArray.hex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) { val v = b.toInt() and 0xff; out.append(HEX[v shr 4]).append(HEX[v and 0x0f]) }
        return out.toString()
    }

    private fun String.unhex(): ByteArray? {
        if (length % 2 != 0) return null
        val out = ByteArray(length / 2)
        for (i in out.indices) {
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private const val HEX = "0123456789abcdef"
}
