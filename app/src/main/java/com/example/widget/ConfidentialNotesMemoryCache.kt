package com.example.widget

import com.example.model.VaultCategory
import com.example.model.VaultItem

data class ConfidentialNoteSummary(
    val id: String,
    val title: String,
    val previewSnippet: String,
    val isFavorite: Boolean,
    val updatedAt: Long
)

/**
 * Volatile in-RAM cache for the Confidential Notes Vault widget.
 *
 * Strictly zero-persistence: never writes plaintext notes to SharedPreferences or disk.
 * Automatically cleared on vault lock, duress mode, backgrounding, or OS memory pressure.
 */
object ConfidentialNotesMemoryCache {

    @Volatile
    private var unlocked: Boolean = false

    @Volatile
    private var notes: List<ConfidentialNoteSummary> = emptyList()

    @Volatile
    private var peekRevealed: Boolean = false

    @Synchronized
    fun updateFromVaultItems(items: List<VaultItem>, isDuress: Boolean) {
        if (isDuress) {
            clear()
            return
        }
        unlocked = true
        notes = items
            .asSequence()
            .filter { it.category == VaultCategory.NOTE }
            .sortedWith(compareByDescending<VaultItem> { it.isFavorite }.thenByDescending { it.updatedAt })
            .map { item ->
                val cleanedPreview = item.notes
                    .replace('\n', ' ')
                    .trim()
                    .take(80)
                ConfidentialNoteSummary(
                    id = item.id,
                    title = item.title.ifBlank { "Untitled Note" },
                    previewSnippet = cleanedPreview,
                    isFavorite = item.isFavorite,
                    updatedAt = item.updatedAt
                )
            }
            .toList()
    }

    @Synchronized
    fun isUnlocked(): Boolean = unlocked

    @Synchronized
    fun getUnlockedNotes(): List<ConfidentialNoteSummary>? {
        return if (unlocked) notes else null
    }

    @Synchronized
    fun isPeekRevealed(): Boolean = unlocked && peekRevealed

    @Synchronized
    fun togglePeek(): Boolean {
        if (!unlocked) return false
        peekRevealed = !peekRevealed
        return peekRevealed
    }

    @Synchronized
    fun clear() {
        unlocked = false
        peekRevealed = false
        notes = emptyList()
    }
}
