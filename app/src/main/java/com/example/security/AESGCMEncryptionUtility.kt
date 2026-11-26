package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Utility for AES-GCM encryption using the Android Keystore.
 * Manages the generation, storage, and retrieval of a master encryption key.
 */
class AESGCMEncryptionUtility private constructor() {

    companion object {
        private const val KEY_ALIAS = "master_vault_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        @Volatile
        private var instance: AESGCMEncryptionUtility? = null

        fun getInstance(): AESGCMEncryptionUtility {
            return instance ?: synchronized(this) {
                instance ?: AESGCMEncryptionUtility().also { instance = it }
            }
        }
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Ensures the master key exists in the Keystore or generates it.
     */
    private fun getMasterKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * Gets an initialized Cipher for encryption.
     * Android KeyStore requires KeyStore-generated IVs unless randomized encryption is disabled.
     * @return Pair of Cipher and the KeyStore-generated IV
     */
    fun getEncryptionCipher(): Pair<Cipher, ByteArray> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        return Pair(cipher, cipher.iv)
    }

    /**
     * Gets an initialized Cipher for decryption.
     */
    fun getDecryptionCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher
    }
}
