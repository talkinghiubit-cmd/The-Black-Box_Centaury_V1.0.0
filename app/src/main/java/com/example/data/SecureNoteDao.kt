package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for encrypted secure notes with dual-vault partition support.
 *
 * Partition 0 = Primary Vault (unlocked with Master Password)
 * Partition 1 = Duress / Decoy Vault (unlocked with Duress PIN)
 */
@Dao
interface SecureNoteDao {

    // =========================================================================
    // Dual-Vault Partition Queries (Reactive Flow & Suspend)
    // =========================================================================

    @Query("SELECT * FROM secure_notes WHERE partition_id = :partitionId ORDER BY isFavorite DESC, updatedAt DESC")
    fun getNotesByPartitionFlow(partitionId: Int = VaultPartition.PRIMARY): Flow<List<SecureNoteEntity>>

    @Query("SELECT * FROM secure_notes WHERE partition_id = :partitionId ORDER BY updatedAt DESC")
    suspend fun getNotesByPartition(partitionId: Int = VaultPartition.PRIMARY): List<SecureNoteEntity>

    @Query("SELECT * FROM secure_notes WHERE id = :id AND partition_id = :partitionId LIMIT 1")
    suspend fun getNoteByIdAndPartition(id: String, partitionId: Int = VaultPartition.PRIMARY): SecureNoteEntity?

    @Query("SELECT * FROM secure_notes WHERE partition_id = :partitionId AND folder = :folder ORDER BY updatedAt DESC")
    fun getNotesByFolderAndPartitionFlow(partitionId: Int = VaultPartition.PRIMARY, folder: String): Flow<List<SecureNoteEntity>>

    @Query("SELECT * FROM secure_notes WHERE partition_id = :partitionId AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteNotesByPartitionFlow(partitionId: Int = VaultPartition.PRIMARY): Flow<List<SecureNoteEntity>>

    @Query("""
        SELECT * FROM secure_notes 
        WHERE partition_id = :partitionId
          AND (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%')
          AND (:folder IS NULL OR :folder = '' OR folder = :folder)
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    fun searchNotesByPartitionFlow(partitionId: Int = VaultPartition.PRIMARY, query: String?, folder: String?): Flow<List<SecureNoteEntity>>

    @Query("""
        SELECT * FROM secure_notes 
        WHERE partition_id = :partitionId
          AND (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%')
          AND (:folder IS NULL OR :folder = '' OR folder = :folder)
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    suspend fun searchNotesByPartition(partitionId: Int = VaultPartition.PRIMARY, query: String?, folder: String?): List<SecureNoteEntity>

    @Query("SELECT DISTINCT folder FROM secure_notes WHERE partition_id = :partitionId ORDER BY folder ASC")
    fun getFoldersByPartitionFlow(partitionId: Int = VaultPartition.PRIMARY): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM secure_notes WHERE partition_id = :partitionId")
    suspend fun getNoteCountByPartition(partitionId: Int = VaultPartition.PRIMARY): Int

    @Query("DELETE FROM secure_notes WHERE id = :id AND partition_id = :partitionId")
    suspend fun deleteNoteByIdAndPartition(id: String, partitionId: Int)

    @Query("DELETE FROM secure_notes WHERE partition_id = :partitionId")
    suspend fun deleteNotesByPartition(partitionId: Int)

    // =========================================================================
    // Universal & Backwards-Compatible Queries
    // =========================================================================

    @Query("SELECT * FROM secure_notes ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllNotesFlow(): Flow<List<SecureNoteEntity>>

    @Query("SELECT * FROM secure_notes ORDER BY updatedAt DESC")
    suspend fun getAllNotes(): List<SecureNoteEntity>

    @Query("SELECT * FROM secure_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): SecureNoteEntity?

    @Query("SELECT * FROM secure_notes WHERE folder = :folder ORDER BY updatedAt DESC")
    fun getNotesByFolder(folder: String): Flow<List<SecureNoteEntity>>

    @Query("""
        SELECT * FROM secure_notes 
        WHERE (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%')
          AND (:folder IS NULL OR :folder = '' OR folder = :folder)
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    fun searchNotesFlow(query: String?, folder: String?): Flow<List<SecureNoteEntity>>

    @Query("""
        SELECT * FROM secure_notes 
        WHERE (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%')
          AND (:folder IS NULL OR :folder = '' OR folder = :folder)
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    suspend fun searchNotes(query: String?, folder: String?): List<SecureNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: SecureNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<SecureNoteEntity>)

    @Query("DELETE FROM secure_notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("DELETE FROM secure_notes")
    suspend fun deleteAllNotes()
}
