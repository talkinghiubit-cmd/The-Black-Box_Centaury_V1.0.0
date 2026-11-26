package com.example.data

import androidx.room.TypeConverter

/**
 * Room TypeConverter for [EncryptedBlob] and encrypted binary blobs.
 *
 * Persists AES-256-GCM encrypted data directly as SQLite BLOB columns:
 * - Bytes 0..11: 12-byte GCM Initialization Vector (IV)
 * - Bytes 12..end: Encrypted Ciphertext + 16-byte Authentication Tag
 *
 * Eliminates Base64 encoding overhead (33% size reduction) and ensures
 * cryptographically optimal on-device database storage.
 */
class EncryptedBlobConverter {

    @TypeConverter
    fun fromEncryptedBlob(blob: EncryptedBlob?): ByteArray? {
        return blob?.toBlob()
    }

    @TypeConverter
    fun toEncryptedBlob(bytes: ByteArray?): EncryptedBlob? {
        if (bytes == null || bytes.size < EncryptedBlob.GCM_IV_LENGTH) return null
        return try {
            EncryptedBlob.fromBlob(bytes)
        } catch (e: Exception) {
            null
        }
    }
}
