package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
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
 * Hardened with monotonic clock verification and rollback clamping so device clock tampering
 * cannot bypass an active cooldown or trap a legitimate user in a multi-year lockout.
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
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val elapsedClock: () -> Long = clock
) {
    constructor(
        context: Context,
        clock: () -> Long = { System.currentTimeMillis() },
        elapsedClock: () -> Long = { SystemClock.elapsedRealtime() }
    ) : this(
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        clock = clock,
        elapsedClock = elapsedClock
    )

    fun getState(): UnlockBackoffState {
        val now = clock()
        val elapsedNow = elapsedClock()
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0)
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        if (failed == 0 || lockoutUntil <= 0L) {
            return UnlockBackoffState(failedAttempts = failed)
        }

        val expectedDurationMs = prefs.getLong(
            KEY_LOCKOUT_DURATION_MS,
            calculateCooldownSeconds(failed) * 1000L
        ).coerceIn(0L, MAX_COOLDOWN_MS)

        if (expectedDurationMs <= 0L) {
            return UnlockBackoffState(failedAttempts = failed)
        }

        val startEpoch = prefs.getLong(KEY_LOCKOUT_START_EPOCH_MS, 0L)
        val startElapsed = prefs.getLong(KEY_LOCKOUT_START_ELAPSED_MS, -1L)

        val sameBootValidElapsed = startElapsed >= 0L && elapsedNow >= startElapsed
        val elapsedRemainingMs = if (sameBootValidElapsed) {
            (startElapsed + expectedDurationMs - elapsedNow).coerceIn(0L, expectedDurationMs)
        } else {
            0L
        }

        val rawWallRemainingMs = (lockoutUntil - now).coerceAtLeast(0L)
        val wallRollbackDetected = (startEpoch > 0L && now < startEpoch) || rawWallRemainingMs > expectedDurationMs
        val clampedWallRemainingMs = if (wallRollbackDetected) {
            if (sameBootValidElapsed) elapsedRemainingMs else expectedDurationMs
        } else {
            rawWallRemainingMs.coerceAtMost(expectedDurationMs)
        }

        // Enforce whichever trustworthy clock still has cooldown remaining within [0, expectedDurationMs]
        val effectiveRemainingMs = maxOf(clampedWallRemainingMs, elapsedRemainingMs)
            .coerceIn(0L, expectedDurationMs)

        val remainingSec = if (effectiveRemainingMs > 0L) ceil(effectiveRemainingMs / 1000.0).toInt() else 0
        return UnlockBackoffState(
            failedAttempts = failed,
            lockoutRemainingSeconds = remainingSec,
            lockoutUntilEpochMs = if (remainingSec > 0) now + effectiveRemainingMs else 0L
        )
    }

    fun recordFailure(): UnlockBackoffState {
        val now = clock()
        val elapsedNow = elapsedClock()
        val currentState = getState()
        if (currentState.isLockedOut) {
            return currentState
        }
        val nextAttempts = currentState.failedAttempts + 1
        val cooldownSec = calculateCooldownSeconds(nextAttempts)
        val durationMs = cooldownSec * 1000L
        val lockoutUntil = if (cooldownSec > 0) now + durationMs else 0L

        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, nextAttempts)
            .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntil)
            .putLong(KEY_LOCKOUT_START_EPOCH_MS, if (cooldownSec > 0) now else 0L)
            .putLong(KEY_LOCKOUT_DURATION_MS, durationMs)
            .putLong(KEY_LOCKOUT_START_ELAPSED_MS, if (cooldownSec > 0) elapsedNow else -1L)
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
            .remove(KEY_LOCKOUT_START_EPOCH_MS)
            .remove(KEY_LOCKOUT_DURATION_MS)
            .remove(KEY_LOCKOUT_START_ELAPSED_MS)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "blackbox_auth_guard"
        private const val KEY_FAILED_ATTEMPTS = "failed_password_attempts"
        private const val KEY_LOCKOUT_UNTIL_MS = "password_lockout_until_ms"
        private const val KEY_LOCKOUT_START_EPOCH_MS = "password_lockout_start_epoch_ms"
        private const val KEY_LOCKOUT_DURATION_MS = "password_lockout_duration_ms"
        private const val KEY_LOCKOUT_START_ELAPSED_MS = "password_lockout_start_elapsed_ms"
        const val LOCKOUT_THRESHOLD = 5
        const val MAX_COOLDOWN_SECONDS = 300
        private const val MAX_COOLDOWN_MS = MAX_COOLDOWN_SECONDS * 1000L

        fun calculateCooldownSeconds(failedAttempts: Int): Int {
            if (failedAttempts < LOCKOUT_THRESHOLD) return 0
            return when (failedAttempts) {
                5 -> 30
                6 -> 60
                7 -> 120
                else -> MAX_COOLDOWN_SECONDS
            }
        }
    }
}
