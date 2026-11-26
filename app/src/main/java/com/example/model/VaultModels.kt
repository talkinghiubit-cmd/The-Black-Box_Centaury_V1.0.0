package com.example.model

enum class VaultCategory(val displayName: String) {
    LOGIN("Logins"),
    NOTE("Secure Notes"),
    CARD("Payment Cards"),
    KEY("SSH & Keys");

    companion object {
        fun fromString(value: String): VaultCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOGIN
        }
    }
}

data class VaultAttachment(
    val id: String,
    val recordId: String,
    val partitionId: Int = 0,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val encryptedStoragePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class VaultItem(
    val id: String,
    val title: String,
    val category: VaultCategory,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val cardHolder: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val keyType: String = "",
    val keyValue: String = "",
    val totpSecret: String = "",
    val isFavorite: Boolean = false,
    val partitionId: Int = 0, // 0 = Primary Vault, 1 = Duress / Decoy Vault
    val passwordUpdatedAt: Long = System.currentTimeMillis(),
    val pwnedBreachCount: Int = 0, // 0 = Clean, >0 = Compromised count, -1 = Unchecked
    val attachments: List<VaultAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PasswordStrength(
    val score: Int, // 0 to 4
    val label: String,
    val entropyBits: Int
)

data class DuplicatePasswordCluster(
    val id: String,
    val items: List<VaultItem>
)

data class VaultHealthReport(
    val overallScore: Int, // 0 to 100
    val totalLogins: Int,
    val strongCount: Int,
    val weakCount: Int,
    val reusedCount: Int,
    val breachedCount: Int,
    val expiredCount: Int,
    val uncheckedCount: Int = 0,
    val moderateCount: Int = 0,
    val duplicateGroupCount: Int = 0,
    val weakItems: List<VaultItem> = emptyList(),
    val reusedItems: List<VaultItem> = emptyList(),
    val breachedItems: List<VaultItem> = emptyList(),
    val expiredItems: List<VaultItem> = emptyList(),
    val uncheckedItems: List<VaultItem> = emptyList(),
    val duplicateClusters: List<DuplicatePasswordCluster> = emptyList()
)
