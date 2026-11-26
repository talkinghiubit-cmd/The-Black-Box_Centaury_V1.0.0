package com.example.autofill

import com.example.model.VaultCategory
import com.example.model.VaultItem

/**
 * Holds strictly in-memory unlocked login snapshots for the Android AutofillService
 * while the user's vault is actively unlocked. Purged immediately on lock, background, or wipe.
 */
object AutofillVaultSession {

    @Volatile
    private var unlocked: Boolean = false

    @Volatile
    private var duressMode: Boolean = false

    @Volatile
    private var cachedLogins: List<VaultItem> = emptyList()

    fun updateUnlockedItems(items: List<VaultItem>, isDuress: Boolean) {
        unlocked = true
        duressMode = isDuress
        cachedLogins = items
            .filter { it.category == VaultCategory.LOGIN && (it.username.isNotEmpty() || it.password.isNotEmpty()) }
            .map { it.copy(notes = "", cardNumber = "", cardCvv = "", keyValue = "", attachments = emptyList()) }
    }

    fun getUnlockedLogins(): List<VaultItem> = if (unlocked) cachedLogins else emptyList()

    fun isUnlocked(): Boolean = unlocked

    fun isDuressMode(): Boolean = duressMode

    fun clear() {
        unlocked = false
        duressMode = false
        cachedLogins = emptyList()
    }
}
