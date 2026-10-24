package com.sokoul.iptv.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chiffre les mots de passe IPTV avec une clé AES-GCM non exportable de l'Android Keystore.
 * Remplace EncryptedSharedPreferences (déprécié) utilisé par l'ancienne version.
 */
@Singleton
class SecretBox @Inject constructor() {

    private val alias = "sokoul_secret_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        "v1:" + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(out, Base64.NO_WRAP)
    } catch (e: Exception) {
        // Keystore défaillant sur certaines box : on conserve le secret (base privée de l'app, sauvegarde désactivée)
        Timber.w(e, "Keystore indisponible, secret stocké sans chiffrement")
        "p0:$plain"
    }

    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        if (stored.startsWith("p0:")) return stored.substring(3)
        if (!stored.startsWith("v1:")) return stored
        return try {
            val (_, ivB64, dataB64) = stored.split(':', limit = 3)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(dataB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "Impossible de déchiffrer un secret")
            null
        }
    }
}
