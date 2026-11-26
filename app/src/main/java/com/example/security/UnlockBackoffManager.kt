package com.example.security

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.ceil

data class UnlockBackoffState(
    val failedAttempts: Int = 0,
    val lockoutRemainingSeconds: Int = 0,
    val lockoutUntilEpochMs: Long = 0L
) {
    val isLockedOut: Boolean
        get() = lockoutRemainingSeconds > 0
}

/**
 * Enforces progressive brute-force cooldowns after repeated failed Master Password attempts,
 * matching the 5-attempt voice biometric lockout threshold and persisting timestamps across restarts.
 *
 * Schedule:
 * - Attempts 1–4: 0s cooldown (warning surfaced after 3 failures)
 * - Attempt 5: 30s cooldown
 * - Attempt 6: 60s cooldown
 * - Attempt 7: 120s cooldown
 * - Attempt 8+: 300s (5 minutes) cooldown
 */
class UnlockBackoffManager(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    constructor(context: Context, clock: () -> Long = { System.currentTimeMillis() }) : this(
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        clock = clock
    )

    fun getState(): UnlockBackoffState {
        val now = clock()
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0)
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        val remainingMs = (lockoutUntil - now).coerceAtLeast(0L)
        val remainingSec = if (remainingMs > 0L) ceil(remainingMs / 1000.0).toInt() else 0
        return UnlockBackoffState(
            failedAttempts = failed,
            lockoutRemainingSeconds = remainingSec,
            lockoutUntilEpochMs = if (remainingSec > 0) lockoutUntil else 0L
        )
    }

    fun recordFailure(): UnlockBackoffState {
        val now = clock()
        val currentState = getState()
        if (currentState.isLockedOut) {
            return currentState
        }
        val nextAttempts = currentState.failedAttempts + 1
        val cooldownSec = calculateCooldownSeconds(nextAttempts)
        val lockoutUntil = if (cooldownSec > 0) now + cooldownSec * 1000L else 0L

        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, nextAttempts)
            .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntil)
            .apply()

        return UnlockBackoffState(
            failedAttempts = nextAttempts,
            lockoutRemainingSeconds = cooldownSec,
            lockoutUntilEpochMs = lockoutUntil
        )
    }

    fun reset() {
        prefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL_MS)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "blackbox_auth_guard"
        private const val KEY_FAILED_ATTEMPTS = "failed_password_attempts"
        private const val KEY_LOCKOUT_UNTIL_MS = "password_lockout_until_ms"
        const val LOCKOUT_THRESHOLD = 5

        fun calculateCooldownSeconds(failedAttempts: Int): Int {
            if (failedAttempts < LOCKOUT_THRESHOLD) return 0
            return when (failedAttempts) {
                5 -> 30
                6 -> 60
                7 -> 120
                else -> 300
            }
        }
    }
}
