package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Universal Data Access Object for vault records, file attachments, and metadata,
 * providing comprehensive multi-partition operations for the dual-vault structure.
 */
@Dao
interface VaultDao {

    // =========================================================================
    // Universal Records with Dual-Vault Partition Support
    // =========================================================================

    @Query("SELECT * FROM vault_records WHERE partition_id = :partitionId ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllRecordsFlow(partitionId: Int = VaultPartition.PRIMARY): Flow<List<VaultRecordEntity>>

    @Query("SELECT * FROM vault_records WHERE partition_id = :partitionId ORDER BY updatedAt DESC")
    suspend fun getAllRecords(partitionId: Int = VaultPartition.PRIMARY): List<VaultRecordEntity>

    @Query("SELECT * FROM vault_records WHERE partition_id = :partitionId AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteRecordsByPartitionFlow(partitionId: Int = VaultPartition.PRIMARY): Flow<List<VaultRecordEntity>>

    @Query("SELECT * FROM vault_records ORDER BY updatedAt DESC")
    suspend fun getAllRecordsUnfiltered(): List<VaultRecordEntity>

    @Query("SELECT * FROM vault_records WHERE id = :id AND partition_id = :partitionId LIMIT 1")
    suspend fun getRecordById(id: String, partitionId: Int = VaultPartition.PRIMARY): VaultRecordEntity?

    @Query("SELECT * FROM vault_records WHERE id = :id LIMIT 1")
    suspend fun getRecordByIdAny(id: String): VaultRecordEntity?

    @Query("""
        SELECT * FROM vault_records 
        WHERE partition_id = :partitionId
          AND (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%')
          AND (:category IS NULL OR :category = '' OR category = :category)
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    fun searchRecordsFlow(partitionId: Int = VaultPartition.PRIMARY, query: String?, category: String?): Flow<List<VaultRecordEntity>>

    @Query("""
        SELECT * FROM vault_records 
        WHERE partition_id = :partitionId
          AND (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%')
          AND (:category IS NULL OR :category = '' OR category = :category)
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    suspend fun searchRecords(partitionId: Int = VaultPartition.PRIMARY, query: String?, category: String?): List<VaultRecordEntity>

    @Query("SELECT COUNT(*) FROM vault_records WHERE partition_id = :partitionId")
    suspend fun getRecordCountByPartition(partitionId: Int = VaultPartition.PRIMARY): Int

    @Query("SELECT DISTINCT partition_id FROM vault_records ORDER BY partition_id ASC")
    suspend fun getActivePartitionIds(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: VaultRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<VaultRecordEntity>)

    @Query("UPDATE vault_records SET pwned_breach_count = :count WHERE id = :id")
    suspend fun updateBreachCount(id: String, count: Int)

    @Query("DELETE FROM vault_records WHERE id = :id AND partition_id = :partitionId")
    suspend fun deleteRecordById(id: String, partitionId: Int)

    @Query("DELETE FROM vault_records WHERE id = :id")
    suspend fun deleteRecordByIdAny(id: String)

    @Query("DELETE FROM vault_records WHERE partition_id = :partitionId")
    suspend fun deleteAllRecordsInPartition(partitionId: Int)

    @Query("DELETE FROM vault_records")
    suspend fun deleteAllRecords()

    // =========================================================================
    // Vault Attachments with Dual-Vault Partition Support
    // =========================================================================

    @Query("SELECT * FROM vault_attachments WHERE record_id = :recordId AND partition_id = :partitionId ORDER BY created_at DESC")
    suspend fun getAttachmentsForRecord(recordId: String, partitionId: Int = VaultPartition.PRIMARY): List<VaultAttachmentEntity>

    @Query("SELECT * FROM vault_attachments WHERE record_id = :recordId AND partition_id = :partitionId ORDER BY created_at DESC")
    fun getAttachmentsForRecordFlow(recordId: String, partitionId: Int = VaultPartition.PRIMARY): Flow<List<VaultAttachmentEntity>>

    @Query("SELECT * FROM vault_attachments WHERE partition_id = :partitionId ORDER BY created_at DESC")
    suspend fun getAllAttachmentsForPartition(partitionId: Int = VaultPartition.PRIMARY): List<VaultAttachmentEntity>

    @Query("SELECT COUNT(*) FROM vault_attachments WHERE partition_id = :partitionId")
    suspend fun getAttachmentCountByPartition(partitionId: Int = VaultPartition.PRIMARY): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: VaultAttachmentEntity)

    @Query("DELETE FROM vault_attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: String)

    @Query("DELETE FROM vault_attachments WHERE record_id = :recordId")
    suspend fun deleteAttachmentsForRecord(recordId: String)

    @Query("DELETE FROM vault_attachments WHERE partition_id = :partitionId")
    suspend fun deleteAllAttachmentsInPartition(partitionId: Int)

    @Query("DELETE FROM vault_attachments")
    suspend fun deleteAllAttachments()

    // =========================================================================
    // Vault Cryptographic Metadata & Dual-Vault State
    // =========================================================================

    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    fun getMetadataFlow(): Flow<VaultMetadataEntity?>

    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    suspend fun getMetadata(): VaultMetadataEntity?

    @Query("SELECT has_duress_pin FROM vault_metadata WHERE id = 1 LIMIT 1")
    suspend fun hasDuressPin(): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: VaultMetadataEntity)

    @Query("UPDATE vault_metadata SET has_duress_pin = :hasDuress, duress_salt = :salt, duress_verifier_hash = :hash WHERE id = 1")
    suspend fun updateDuressPin(hasDuress: Boolean, salt: String?, hash: String?)

    @Query("UPDATE vault_metadata SET has_duress_pin = 0, duress_salt = NULL, duress_verifier_hash = NULL WHERE id = 1")
    suspend fun removeDuressPin()

    @Query("DELETE FROM vault_metadata")
    suspend fun deleteMetadata()
}
