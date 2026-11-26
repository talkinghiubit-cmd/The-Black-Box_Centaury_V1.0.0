package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Constants and utility definitions for the dual-vault partition architecture:
 *
 * - Partition 0: Primary Vault (unlocked with Master Password)
 * - Partition 1: Duress / Decoy Vault (unlocked with Duress PIN under coercion)
 */
object VaultPartition {
    const val PRIMARY = 0
    const val DURESS = 1
    const val DECOY = 1

    fun isValidPartition(partitionId: Int): Boolean =
        partitionId == PRIMARY || partitionId == DURESS

    fun getPartitionLabel(partitionId: Int): String = when (partitionId) {
        PRIMARY -> "Primary Vault"
        DURESS -> "Duress / Decoy Vault"
        else -> "Partition #$partitionId"
    }
}

/**
 * Value object representing an encrypted data field with its associated
 * 12-byte initialization vector (IV) and AES-256-GCM ciphertext as Base64 strings.
 */
data class EncryptedField(
    val ciphertext: String,
    val iv: String
) {
    /**
     * Converts this [EncryptedField] into a raw binary byte array (SQLite BLOB):
     * [0..11] = 12-byte IV, [12..end] = ciphertext + tag.
     */
    fun toBlob(): ByteArray {
        val ivBytes = android.util.Base64.decode(iv, android.util.Base64.NO_WRAP)
        val ctBytes = android.util.Base64.decode(ciphertext, android.util.Base64.NO_WRAP)
        val blob = ByteArray(ivBytes.size + ctBytes.size)
        System.arraycopy(ivBytes, 0, blob, 0, ivBytes.size)
        System.arraycopy(ctBytes, 0, blob, ivBytes.size, ctBytes.size)
        return blob
    }

    fun toEncryptedBlob(): EncryptedBlob {
        return EncryptedBlob.fromEncryptedField(this)
    }

    companion object {
        fun fromPair(pair: Pair<String, String>): EncryptedField {
            return EncryptedField(ciphertext = pair.first, iv = pair.second)
        }

        fun fromBlob(blob: ByteArray): EncryptedField {
            val ivLen = 12
            require(blob.size >= ivLen) { "Invalid blob length for EncryptedField: ${blob.size}" }
            val ivBytes = ByteArray(ivLen)
            val ctBytes = ByteArray(blob.size - ivLen)
            System.arraycopy(blob, 0, ivBytes, 0, ivLen)
            System.arraycopy(blob, ivLen, ctBytes, 0, ctBytes.size)
            return EncryptedField(
                ciphertext = android.util.Base64.encodeToString(ctBytes, android.util.Base64.NO_WRAP),
                iv = android.util.Base64.encodeToString(ivBytes, android.util.Base64.NO_WRAP)
            )
        }
    }
}

/**
 * Binary representation of an AES-256-GCM encrypted blob:
 * Holds raw byte arrays for [ciphertext] (including 16-byte GCM authentication tag)
 * and the [iv] (12-byte initialization vector).
 *
 * Mapped to native SQLite BLOB columns via [EncryptedBlobConverter], avoiding Base64
 * serialization overhead and offering maximum throughput and minimal storage footprint.
 */
data class EncryptedBlob(
    val ciphertext: ByteArray,
    val iv: ByteArray = ByteArray(GCM_IV_LENGTH)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedBlob
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "EncryptedBlob(ivLength=${iv.size}, ciphertextLength=${ciphertext.size})"
    }

    /**
     * Packs IV and Ciphertext into a contiguous byte array (SQLite BLOB):
     * Bytes 0..11: 12-byte GCM Initialization Vector
     * Bytes 12..N: Ciphertext + Auth Tag
     */
    fun toBlob(): ByteArray {
        val blob = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, blob, 0, iv.size)
        System.arraycopy(ciphertext, 0, blob, iv.size, ciphertext.size)
        return blob
    }

    fun toEncryptedField(): EncryptedField {
        val ctStr = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
        val ivStr = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
        return EncryptedField(ciphertext = ctStr, iv = ivStr)
    }

    companion object {
        const val GCM_IV_LENGTH = 12

        /**
         * Unpacks contiguous byte array from SQLite BLOB into [EncryptedBlob].
         */
        fun fromBlob(blob: ByteArray): EncryptedBlob {
            require(blob.size >= GCM_IV_LENGTH) {
                "Invalid encrypted blob: expected at least $GCM_IV_LENGTH bytes, got ${blob.size}"
            }
            val iv = ByteArray(GCM_IV_LENGTH)
            val ciphertext = ByteArray(blob.size - GCM_IV_LENGTH)
            System.arraycopy(blob, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(blob, GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)
            return EncryptedBlob(ciphertext = ciphertext, iv = iv)
        }

        fun fromEncryptedField(field: EncryptedField): EncryptedBlob {
            val ivBytes = android.util.Base64.decode(field.iv, android.util.Base64.NO_WRAP)
            val ctBytes = android.util.Base64.decode(field.ciphertext, android.util.Base64.NO_WRAP)
            return EncryptedBlob(ciphertext = ctBytes, iv = ivBytes)
        }
    }
}

/**
 * Entity dedicated to storing encrypted credentials (logins & passwords),
 * structured with dual-vault partition support, fast query indexing, and
 * dual support for string-based [EncryptedField] and native binary [EncryptedBlob].
 */
@Entity(
    tableName = "passwords",
    indices = [
        Index(value = ["partition_id"]),
        Index(value = ["isFavorite"]),
        Index(value = ["updatedAt"])
    ]
)
data class PasswordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String = "",
    val encryptedPassword: EncryptedField,
    val url: String = "",
    val encryptedNotes: EncryptedField? = null,
    @ColumnInfo(name = "encrypted_password_blob", typeAffinity = ColumnInfo.BLOB)
    val encryptedPasswordBlob: EncryptedBlob? = null,
    @ColumnInfo(name = "encrypted_notes_blob", typeAffinity = ColumnInfo.BLOB)
    val encryptedNotesBlob: EncryptedBlob? = null,
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "partition_id", defaultValue = "0")
    val partitionId: Int = VaultPartition.PRIMARY,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getEffectivePasswordBlob(): EncryptedBlob {
        return encryptedPasswordBlob ?: EncryptedBlob.fromEncryptedField(encryptedPassword)
    }

    fun getEffectiveNotesBlob(): EncryptedBlob? {
        return encryptedNotesBlob ?: encryptedNotes?.let { EncryptedBlob.fromEncryptedField(it) }
    }
}

/**
 * Entity dedicated to storing encrypted personal notes, documents, and memos,
 * structured with dual-vault partition support, folder indexing, and native
 * binary [EncryptedBlob] persistence.
 */
@Entity(
    tableName = "secure_notes",
    indices = [
        Index(value = ["partition_id"]),
        Index(value = ["folder"]),
        Index(value = ["isFavorite"]),
        Index(value = ["updatedAt"])
    ]
)
data class SecureNoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val encryptedContent: EncryptedField,
    @ColumnInfo(name = "encrypted_content_blob", typeAffinity = ColumnInfo.BLOB)
    val encryptedContentBlob: EncryptedBlob? = null,
    val folder: String = "General",
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "partition_id", defaultValue = "0")
    val partitionId: Int = VaultPartition.PRIMARY,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getEffectiveContentBlob(): EncryptedBlob {
        return encryptedContentBlob ?: EncryptedBlob.fromEncryptedField(encryptedContent)
    }
}

/**
 * Universal encrypted vault item record supporting all secret categories
 * (Logins, Secure Notes, Cards, Encrypted Keys, and File Attachments).
 */
@Entity(
    tableName = "vault_records",
    indices = [
        Index(value = ["partition_id"]),
        Index(value = ["category"]),
        Index(value = ["isFavorite"]),
        Index(value = ["updatedAt"])
    ]
)
data class VaultRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val encryptedPayload: String,     // Base64 ciphertext with GCM tag
    val initializationVector: String, // Base64 12-byte IV
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "partition_id", defaultValue = "0")
    val partitionId: Int = VaultPartition.PRIMARY, // 0 = Primary Vault, 1 = Duress / Decoy Vault
    @ColumnInfo(name = "password_updated_at", defaultValue = "0")
    val passwordUpdatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pwned_breach_count", defaultValue = "0")
    val pwnedBreachCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Encrypted attachment record linked to a parent vault item.
 */
@Entity(
    tableName = "vault_attachments",
    indices = [Index(value = ["record_id"]), Index(value = ["partition_id"])]
)
data class VaultAttachmentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "partition_id", defaultValue = "0")
    val partitionId: Int = VaultPartition.PRIMARY,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "encrypted_storage_path")
    val encryptedStoragePath: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Master metadata entity for vault cryptographic parameters and dual-partition state.
 * Supports direct binary BLOB storage for biometric wrapped keys and master key salts.
 */
@Entity(tableName = "vault_metadata")
data class VaultMetadataEntity(
    @PrimaryKey
    val id: Int = 1,
    val masterSalt: String,            // Base64 16-byte PBKDF2 salt
    val verifierHash: String,          // Base64 password verification hash
    val isBiometricEnabled: Boolean = false,
    val biometricWrappedPassword: String? = null, // Protected by AndroidKeyStore AES
    @ColumnInfo(name = "biometric_key_blob", typeAffinity = ColumnInfo.BLOB)
    val biometricKeyBlob: ByteArray? = null,      // Direct binary BLOB for hardware-backed keys
    val autoLockMinutes: Int = 5,
    val isVaultInitialized: Boolean = true,
    @ColumnInfo(name = "has_duress_pin", defaultValue = "0")
    val hasDuressPin: Boolean = false,
    @ColumnInfo(name = "duress_salt")
    val duressSalt: String? = null,
    @ColumnInfo(name = "duress_verifier_hash")
    val duressVerifierHash: String? = null,
    @ColumnInfo(name = "is_voice_biometric_enabled", defaultValue = "0")
    val isVoiceBiometricEnabled: Boolean = false,
    @ColumnInfo(name = "encrypted_voice_profile")
    val encryptedVoiceProfile: String? = null,
    @ColumnInfo(name = "voice_wrapped_password")
    val voiceWrappedPassword: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isDualVaultConfigured: Boolean
        get() = hasDuressPin && !duressSalt.isNullOrEmpty() && !duressVerifierHash.isNullOrEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultMetadataEntity
        if (id != other.id) return false
        if (masterSalt != other.masterSalt) return false
        if (verifierHash != other.verifierHash) return false
        if (isBiometricEnabled != other.isBiometricEnabled) return false
        if (biometricWrappedPassword != other.biometricWrappedPassword) return false
        if (biometricKeyBlob != null) {
            if (other.biometricKeyBlob == null) return false
            if (!biometricKeyBlob.contentEquals(other.biometricKeyBlob)) return false
        } else if (other.biometricKeyBlob != null) return false
        if (autoLockMinutes != other.autoLockMinutes) return false
        if (isVaultInitialized != other.isVaultInitialized) return false
        if (hasDuressPin != other.hasDuressPin) return false
        if (duressSalt != other.duressSalt) return false
        if (duressVerifierHash != other.duressVerifierHash) return false
        if (isVoiceBiometricEnabled != other.isVoiceBiometricEnabled) return false
        if (encryptedVoiceProfile != other.encryptedVoiceProfile) return false
        if (voiceWrappedPassword != other.voiceWrappedPassword) return false
        if (createdAt != other.createdAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + masterSalt.hashCode()
        result = 31 * result + verifierHash.hashCode()
        result = 31 * result + isBiometricEnabled.hashCode()
        result = 31 * result + (biometricWrappedPassword?.hashCode() ?: 0)
        result = 31 * result + (biometricKeyBlob?.contentHashCode() ?: 0)
        result = 31 * result + autoLockMinutes
        result = 31 * result + isVaultInitialized.hashCode()
        result = 31 * result + hasDuressPin.hashCode()
        result = 31 * result + (duressSalt?.hashCode() ?: 0)
        result = 31 * result + (duressVerifierHash?.hashCode() ?: 0)
        result = 31 * result + isVoiceBiometricEnabled.hashCode()
        result = 31 * result + (encryptedVoiceProfile?.hashCode() ?: 0)
        result = 31 * result + (voiceWrappedPassword?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
