package io.github.lucaskfp.learnharness.engine.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts secrets (API keys) before they are stored. */
interface KeyVault {
    fun encrypt(plain: String): String

    fun decrypt(encrypted: String): String
}

/**
 * AES-256-GCM with a key that never leaves the Android Keystore. The stored form is
 * `base64(iv || ciphertext)`; a fresh IV is generated for every encryption.
 */
class AndroidKeystoreKeyVault(private val alias: String = "learnharness.api-keys") : KeyVault {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(Provider).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Provider)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(Transformation).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    override fun decrypt(encrypted: String): String {
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IvBytes)
        val cipher = Cipher.getInstance(Transformation).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TagBits, iv))
        }
        return String(cipher.doFinal(bytes, IvBytes, bytes.size - IvBytes), Charsets.UTF_8)
    }

    private companion object {
        const val Provider = "AndroidKeyStore"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvBytes = 12
        const val TagBits = 128
    }
}
