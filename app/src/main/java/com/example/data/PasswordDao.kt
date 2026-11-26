package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for encrypted password entities with dual-vault partition support.
 *
 * Partition 0 = Primary Vault (unlocked with Master Password)
 * Partition 1 = Duress / Decoy Vault (unlocked with Duress PIN)
 */
@Dao
interface PasswordDao {

    // =========================================================================
    // Dual-Vault Partition Queries (Reactive Flow & Suspend)
    // =========================================================================

    @Query("SELECT * FROM passwords WHERE partition_id = :partitionId ORDER BY isFavorite DESC, updatedAt DESC")
    fun getPasswordsByPartitionFlow(partitionId: Int = VaultPartition.PRIMARY): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE partition_id = :partitionId ORDER BY updatedAt DESC")
    suspend fun getPasswordsByPartition(partitionId: Int = VaultPartition.PRIMARY): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE id = :id AND partition_id = :partitionId LIMIT 1")
    suspend fun getPasswordByIdAndPartition(id: String, partitionId: Int = VaultPartition.PRIMARY): PasswordEntity?

    @Query("SELECT * FROM passwords WHERE partition_id = :partitionId AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoritePasswordsByPartitionFlow(partitionId: Int = VaultPartition.PRIMARY): Flow<List<PasswordEntity>>

    @Query("""
        SELECT * FROM passwords 
        WHERE partition_id = :partitionId
          AND (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%')
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    fun searchPasswordsByPartitionFlow(partitionId: Int = VaultPartition.PRIMARY, query: String?): Flow<List<PasswordEntity>>

    @Query("""
        SELECT * FROM passwords 
        WHERE partition_id = :partitionId
          AND (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%')
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    suspend fun searchPasswordsByPartition(partitionId: Int = VaultPartition.PRIMARY, query: String?): List<PasswordEntity>

    @Query("SELECT COUNT(*) FROM passwords WHERE partition_id = :partitionId")
    suspend fun getPasswordCountByPartition(partitionId: Int = VaultPartition.PRIMARY): Int

    @Query("DELETE FROM passwords WHERE id = :id AND partition_id = :partitionId")
    suspend fun deletePasswordByIdAndPartition(id: String, partitionId: Int)

    @Query("DELETE FROM passwords WHERE partition_id = :partitionId")
    suspend fun deletePasswordsByPartition(partitionId: Int)

    // =========================================================================
    // Universal & Backwards-Compatible Queries
    // =========================================================================

    @Query("SELECT * FROM passwords ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllPasswordsFlow(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords ORDER BY updatedAt DESC")
    suspend fun getAllPasswords(): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE id = :id LIMIT 1")
    suspend fun getPasswordById(id: String): PasswordEntity?

    @Query("""
        SELECT * FROM passwords 
        WHERE (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%')
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    fun searchPasswordsFlow(query: String?): Flow<List<PasswordEntity>>

    @Query("""
        SELECT * FROM passwords 
        WHERE (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%')
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    suspend fun searchPasswords(query: String?): List<PasswordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: PasswordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasswords(passwords: List<PasswordEntity>)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePasswordById(id: String)

    @Query("DELETE FROM passwords")
    suspend fun deleteAllPasswords()
}
