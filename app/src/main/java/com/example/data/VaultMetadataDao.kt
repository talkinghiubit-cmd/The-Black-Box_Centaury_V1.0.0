package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object specifically for Vault Metadata, PBKDF2 parameters,
 * and Dual-Vault / Duress PIN partition configuration.
 */
@Dao
interface VaultMetadataDao {

    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    fun getMetadataFlow(): Flow<VaultMetadataEntity?>

    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    suspend fun getMetadata(): VaultMetadataEntity?

    @Query("SELECT isVaultInitialized FROM vault_metadata WHERE id = 1 LIMIT 1")
    suspend fun isVaultInitialized(): Boolean?

    @Query("SELECT has_duress_pin FROM vault_metadata WHERE id = 1 LIMIT 1")
    suspend fun hasDuressPin(): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: VaultMetadataEntity)

    @Query("UPDATE vault_metadata SET has_duress_pin = :hasDuress, duress_salt = :salt, duress_verifier_hash = :hash WHERE id = 1")
    suspend fun updateDuressPin(hasDuress: Boolean, salt: String?, hash: String?)

    @Query("UPDATE vault_metadata SET has_duress_pin = 0, duress_salt = NULL, duress_verifier_hash = NULL WHERE id = 1")
    suspend fun removeDuressPin()

    @Query("UPDATE vault_metadata SET autoLockMinutes = :minutes WHERE id = 1")
    suspend fun updateAutoLockMinutes(minutes: Int)

    @Query("UPDATE vault_metadata SET isBiometricEnabled = :enabled, biometricWrappedPassword = :wrappedPassword WHERE id = 1")
    suspend fun updateBiometricSettings(enabled: Boolean, wrappedPassword: String?)

    @Query("DELETE FROM vault_metadata")
    suspend fun deleteMetadata()
}
