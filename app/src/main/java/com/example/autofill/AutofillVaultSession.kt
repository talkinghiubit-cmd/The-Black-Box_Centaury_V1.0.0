package com.example.autofill

import android.os.SystemClock
import com.example.model.VaultCategory
import com.example.model.VaultItem

/**
 * Holds strictly in-memory unlocked login snapshots for the Android AutofillService
 * while the user's vault is actively unlocked.
 *
 * Implements defeasible session trust: cached logins automatically self-purge upon
 * explicit lock, security invalidation, clock rollback, or idle TTL expiration.
 */
object AutofillVaultSession {

    private const val DEFAULT_MAX_IDLE_MINUTES = 5

    @Volatile
    private var unlocked: Boolean = false

    @Volatile
    private var duressMode: Boolean = false

    @Volatile
    private var cachedLogins: List<VaultItem> = emptyList()

    @Volatile
    private var lastActivityMs: Long = 0L

    @Volatile
    private var maxIdleMs: Long = DEFAULT_MAX_IDLE_MINUTES * 60_000L

    @Volatile
    internal var clockProvider: () -> Long = { SystemClock.elapsedRealtime() }

    @Synchronized
    fun updateUnlockedItems(
        items: List<VaultItem>,
        isDuress: Boolean,
        autoLockMinutes: Int? = null
    ) {
        val now = clockProvider()
        if (autoLockMinutes != null) {
            maxIdleMs = autoLockMinutes.coerceIn(1, 60) * 60_000L
        }
        lastActivityMs = now
        unlocked = true
        duressMode = isDuress
        cachedLogins = items
            .filter { it.category == VaultCategory.LOGIN && (it.username.isNotEmpty() || it.password.isNotEmpty()) }
            .map {
                it.copy(
                    notes = "",
                    cardHolder = "",
                    cardNumber = "",
                    cardExpiry = "",
                    cardCvv = "",
                    keyType = "",
                    keyValue = "",
                    totpSecret = "",
                    attachments = emptyList()
                )
            }
    }

    @Synchronized
    fun updateMaxIdleMinutes(autoLockMinutes: Int) {
        maxIdleMs = autoLockMinutes.coerceIn(1, 60) * 60_000L
        if (unlocked) {
            checkTtlAndEnforce()
        }
    }

    @Synchronized
    fun touchSession() {
        if (checkTtlAndEnforce()) {
            lastActivityMs = clockProvider()
        }
    }

    @Synchronized
    fun getUnlockedLogins(): List<VaultItem> {
        if (!checkTtlAndEnforce()) return emptyList()
        return cachedLogins.toList()
    }

    @Synchronized
    fun isUnlocked(): Boolean = checkTtlAndEnforce()

    @Synchronized
    fun isDuressMode(): Boolean {
        if (!checkTtlAndEnforce()) return false
        return duressMode
    }

    @Synchronized
    fun invalidateOnSecurityEvent() {
        clear()
    }

    @Synchronized
    fun clear() {
        unlocked = false
        duressMode = false
        lastActivityMs = 0L
        cachedLogins = emptyList()
    }

    private fun checkTtlAndEnforce(): Boolean {
        if (!unlocked) return false
        val elapsed = clockProvider() - lastActivityMs
        if (elapsed < 0L || elapsed > maxIdleMs) {
            clear()
            return false
        }
        return true
    }
}
