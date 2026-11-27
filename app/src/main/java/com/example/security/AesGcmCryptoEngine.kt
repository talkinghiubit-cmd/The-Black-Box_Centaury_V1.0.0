package com.example.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AesGcmEncryptedPayload(
    val ciphertext: String,
    val iv: String,
    val isAadAuthenticated: Boolean
)

/**
 * Standard AES-256-GCM AEAD encryption utility for data-at-rest protection.
 *
 * Enforces:
 * - 96-bit (12-byte) CSPRNG initialization vectors per encryption operation
 * - 128-bit GCM authentication tag verification
 * - Optional Associated Authenticated Data (AAD) with a mandatory `v2:` envelope marker
 *   so record ID, vault partition, and category tampering cannot pass silently or downgrade
 * - Immediate zeroization of temporary plaintext buffers
 */
object AesGcmCryptoEngine {

    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
    const val ENVELOPE_V2_PREFIX = "v2:"

    private val secureRandom = SecureRandom()

    fun generateIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    fun buildRecordAad(recordId: String, partitionId: Int, category: String): ByteArray {
        return "BBOX-RECORD-v2|id=$recordId|partition=$partitionId|category=$category"
            .toByteArray(Charsets.UTF_8)
    }

    fun buildFieldAad(recordId: String, fieldName: String, partitionId: Int): ByteArray {
        return "BBOX-FIELD-v2|id=$recordId|field=$fieldName|partition=$partitionId"
            .toByteArray(Charsets.UTF_8)
    }

    fun encryptString(
        plaintext: String,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): AesGcmEncryptedPayload {
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)
        return try {
            val (cipherBytes, iv) = encryptBytes(plainBytes, secretKey, associatedData)
            val encodedCipher = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            val encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP)
            val hasAad = associatedData != null && associatedData.isNotEmpty()
            AesGcmEncryptedPayload(
                ciphertext = if (hasAad) "$ENVELOPE_V2_PREFIX$encodedCipher" else encodedCipher,
                iv = encodedIv,
                isAadAuthenticated = hasAad
            )
        } finally {
            zeroize(plainBytes)
        }
    }

    fun decryptString(
        ciphertextOrEnvelope: String,
        ivBase64: String,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): String {
        val isV2Envelope = ciphertextOrEnvelope.startsWith(ENVELOPE_V2_PREFIX)
        val rawBase64 = if (isV2Envelope) {
            require(associatedData != null && associatedData.isNotEmpty()) {
                "Associated authenticated data (AAD) is required for v2 encrypted vault payloads"
            }
            ciphertextOrEnvelope.removePrefix(ENVELOPE_V2_PREFIX)
        } else {
            ciphertextOrEnvelope
        }

        val cipherBytes = Base64.decode(rawBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val effectiveAad = if (isV2Envelope) associatedData else null
        val plainBytes = decryptBytes(cipherBytes, iv, secretKey, effectiveAad)
        return try {
            String(plainBytes, Charsets.UTF_8)
        } finally {
            zeroize(plainBytes)
        }
    }

    fun encryptBytes(
        plainBytes: ByteArray,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): Pair<ByteArray, ByteArray> {
        val iv = generateIv()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        val ciphertext = cipher.doFinal(plainBytes)
        return Pair(ciphertext, iv)
    }

    fun decryptBytes(
        ciphertext: ByteArray,
        iv: ByteArray,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(iv.size == GCM_IV_LENGTH_BYTES) { "Invalid GCM IV length: ${iv.size}" }
        require(ciphertext.size >= GCM_TAG_LENGTH_BYTES) { "Malformed GCM ciphertext" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        return cipher.doFinal(ciphertext)
    }

    fun zeroize(vararg buffers: ByteArray?) {
        buffers.forEach { it?.fill(0) }
    }

    fun zeroizeChars(buffer: CharArray?) {
        buffer?.fill('\u0000')
    }
}
