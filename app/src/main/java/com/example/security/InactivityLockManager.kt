package com.example.security

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background inactivity monitor that tracks user touch / keyboard interactions
 * and automatically triggers vault locking and in-memory credential purging after
 * a specified period of user inactivity.
 */
class InactivityLockManager(
    private val scope: CoroutineScope,
    private val appContext: Context? = null,
    private val onLockTriggered: () -> Unit
) {

    @Volatile
    private var lastInteractionTimestamp: Long = System.currentTimeMillis()

    @Volatile
    private var monitorStartedAtMillis: Long = 0L

    @Volatile
    private var isMonitoring: Boolean = false

    @Volatile
    var timeoutDurationMillis: Long = 5 * 60 * 1000L // Default 5 minutes
        private set

    private var monitorJob: Job? = null

    /**
     * Updates the inactivity timeout duration.
     * @param timeoutMinutes Timeout in minutes (e.g. 1, 2, 5, 10)
     */
    fun setTimeoutMinutes(timeoutMinutes: Int) {
        timeoutDurationMillis = (timeoutMinutes.coerceAtLeast(1)) * 60 * 1000L
    }

    /**
     * Sets timeout in seconds for high-security environments.
     */
    fun setTimeoutSeconds(timeoutSeconds: Int) {
        timeoutDurationMillis = (timeoutSeconds.coerceAtLeast(15)) * 1000L
    }

    /**
     * Notifies the monitor of recent user interaction (tap, scroll, keypress).
     */
    fun recordUserInteraction() {
        lastInteractionTimestamp = System.currentTimeMillis()
    }

    /**
     * Begins background observation of user inactivity.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        val now = System.currentTimeMillis()
        lastInteractionTimestamp = now
        monitorStartedAtMillis = now
        appContext?.let { ctx ->
            TaskAnalyticsEngine.recordTaskStarted(
                context = ctx,
                taskType = TrackedTaskType.INACTIVITY_TIMER,
                taskName = "Inactivity Auto-Lock Monitor",
                timestampMillis = now
            )
        }

        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.Default) {
            while (isActive && isMonitoring) {
                delay(2000L) // Check every 2 seconds
                val elapsed = System.currentTimeMillis() - lastInteractionTimestamp
                if (elapsed >= timeoutDurationMillis) {
                    isMonitoring = false
                    appContext?.let { ctx ->
                        TaskAnalyticsEngine.recordTaskCompleted(
                            context = ctx,
                            taskType = TrackedTaskType.INACTIVITY_TIMER,
                            taskName = "Inactivity Auto-Lock Monitor",
                            durationMs = System.currentTimeMillis() - monitorStartedAtMillis,
                            note = "Idle timeout triggered vault lock"
                        )
                    }
                    launch(Dispatchers.Main) {
                        onLockTriggered()
                    }
                    break
                }
            }
        }
    }

    /**
     * Stops monitoring when the vault is already locked.
     */
    fun stopMonitoring(stopReasonLabel: String = "Inactivity monitor stopped on vault lock") {
        val wasMonitoring = isMonitoring
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        if (wasMonitoring) {
            val elapsed = if (monitorStartedAtMillis > 0L) System.currentTimeMillis() - monitorStartedAtMillis else null
            appContext?.let { ctx ->
                TaskAnalyticsEngine.recordTaskStopped(
                    context = ctx,
                    taskType = TrackedTaskType.INACTIVITY_TIMER,
                    taskName = "Inactivity Auto-Lock Monitor",
                    stopReasonCode = TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED,
                    stopReasonLabel = stopReasonLabel,
                    state = TaskLifecycleState.STOPPED,
                    durationMs = elapsed
                )
            }
        }
    }

    /**
     * Immediate force lock (e.g. when app is backgrounded or device screen turns off).
     */
    fun triggerImmediateLock() {
        stopMonitoring("Immediate force lock stopped inactivity timer")
        onLockTriggered()
    }
}
