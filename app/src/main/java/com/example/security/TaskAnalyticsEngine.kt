package com.example.security

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.job.JobParameters
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TrackedTaskType(val displayName: String, val shortTag: String) {
    BACKGROUND_MAINTENANCE_JOB("Background Guard Job", "BG_JOB"),
    INACTIVITY_TIMER("Inactivity Lock Timer", "IDLE_LOCK"),
    BREACH_AUDIT_SCAN("Breach Audit Scan", "HIBP_SCAN"),
    TOTP_TICKER("TOTP 2FA Ticker", "TOTP_TICK"),
    SHARE_CLEANUP("Share File Cleanup", "EXPORT_GC"),
    WIDGET_REFRESH("Notes Widget Refresh", "WIDGET"),
    NFC_PAYMENT_SESSION("Pay with NFC Session", "NFC_PAY"),
    APP_PROCESS_TASK("App Process / Lifecycle", "LIFECYCLE")
}

enum class TaskLifecycleState {
    STARTED,
    COMPLETED,
    STOPPED,
    CANCELLED,
    FAILED,
    OS_TERMINATED;

    val isStoppedState: Boolean
        get() = this == STOPPED || this == CANCELLED || this == OS_TERMINATED
}

data class TaskExecutionEvent(
    val id: String,
    val taskType: TrackedTaskType,
    val taskName: String,
    val state: TaskLifecycleState,
    val stopReasonCode: Int? = null,
    val stopReasonLabel: String? = null,
    val durationMs: Long? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class TaskAnalyticsSummary(
    val totalTrackedRuns: Int = 0,
    val activeTasksCount: Int = 0,
    val completedTasksCount: Int = 0,
    val stoppedTasksCount: Int = 0,
    val failedTasksCount: Int = 0,
    val osTerminatedCount: Int = 0,
    val activeTaskTypes: Set<TrackedTaskType> = emptySet(),
    val stoppedTaskTypes: Set<TrackedTaskType> = emptySet(),
    val lastStoppedEvent: TaskExecutionEvent? = null,
    val taskStatusByType: Map<TrackedTaskType, TaskLifecycleState> = emptyMap(),
    val recentEvents: List<TaskExecutionEvent> = emptyList()
)

/**
 * Privacy-first, strictly on-device Task Execution & Stop-Tracking Analytics Engine.
 *
 * Monitors whether app tasks (background JobScheduler maintenance, inactivity auto-lock countdown,
 * HIBP breach scan, TOTP ticker, export cleanup, widget updates, and Android lifecycle/process exit
 * events) are actively running, completed cleanly, or stopped/cancelled/terminated by the app or OS.
 *
 * Zero-Knowledge Guarantee: Never records credentials, usernames, titles, notes, URLs, or PII.
 * All telemetry stays strictly on-device in a bounded 40-event ring buffer.
 */
object TaskAnalyticsEngine {

    const val STOP_REASON_APP_CANCELLED = -1001
    const val STOP_REASON_LIFECYCLE_BACKGROUND = -1002
    const val STOP_REASON_MEMORY_PRESSURE = -1003
    const val STOP_REASON_OS_PREEMPTED_LEGACY = -1004
    const val STOP_REASON_WIDGET_REMOVED = -1005

    private const val PREFS_NAME = "blackbox_task_analytics"
    private const val KEY_EVENTS_JSON = "task_events_json"
    private const val KEY_TOTAL_RUNS = "total_runs_count"
    private const val KEY_TOTAL_COMPLETED = "total_completed_count"
    private const val KEY_TOTAL_STOPPED = "total_stopped_count"
    private const val KEY_TOTAL_FAILED = "total_failed_count"
    private const val KEY_TOTAL_OS_TERMINATED = "total_os_terminated_count"
    private const val KEY_LAST_INSPECTED_EXIT_TS = "last_inspected_exit_ts"
    private const val MAX_RING_BUFFER_SIZE = 40

    private val lock = Any()
    @Volatile
    private var analyticsEpoch: Long = 0L
    private val _summaryFlow = MutableStateFlow(TaskAnalyticsSummary())
    val summaryFlow: StateFlow<TaskAnalyticsSummary> = _summaryFlow.asStateFlow()

    fun currentEpoch(): Long = analyticsEpoch

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordTaskStarted(
        context: Context,
        taskType: TrackedTaskType,
        taskName: String = taskType.displayName,
        timestampMillis: Long = System.currentTimeMillis()
    ): TaskExecutionEvent {
        return appendEvent(
            context = context,
            event = TaskExecutionEvent(
                id = UUID.randomUUID().toString(),
                taskType = taskType,
                taskName = taskName,
                state = TaskLifecycleState.STARTED,
                timestampMillis = timestampMillis
            )
        )
    }

    fun recordTaskCompleted(
        context: Context,
        taskType: TrackedTaskType,
        taskName: String = taskType.displayName,
        durationMs: Long? = null,
        note: String? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): TaskExecutionEvent {
        return appendEvent(
            context = context,
            event = TaskExecutionEvent(
                id = UUID.randomUUID().toString(),
                taskType = taskType,
                taskName = taskName,
                state = TaskLifecycleState.COMPLETED,
                stopReasonLabel = note,
                durationMs = durationMs?.coerceAtLeast(0L),
                timestampMillis = timestampMillis
            )
        )
    }

    fun recordTaskStopped(
        context: Context,
        taskType: TrackedTaskType,
        taskName: String = taskType.displayName,
        stopReasonCode: Int = STOP_REASON_APP_CANCELLED,
        stopReasonLabel: String = "Task stopped by app",
        state: TaskLifecycleState = TaskLifecycleState.STOPPED,
        durationMs: Long? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): TaskExecutionEvent {
        val resolvedState = if (state.isStoppedState) state else TaskLifecycleState.STOPPED
        return appendEvent(
            context = context,
            event = TaskExecutionEvent(
                id = UUID.randomUUID().toString(),
                taskType = taskType,
                taskName = taskName,
                state = resolvedState,
                stopReasonCode = stopReasonCode,
                stopReasonLabel = stopReasonLabel,
                durationMs = durationMs?.coerceAtLeast(0L),
                timestampMillis = timestampMillis
            )
        )
    }

    fun recordTaskFailed(
        context: Context,
        taskType: TrackedTaskType,
        taskName: String = taskType.displayName,
        reasonLabel: String = "Task failed",
        durationMs: Long? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): TaskExecutionEvent {
        return appendEvent(
            context = context,
            event = TaskExecutionEvent(
                id = UUID.randomUUID().toString(),
                taskType = taskType,
                taskName = taskName,
                state = TaskLifecycleState.FAILED,
                stopReasonLabel = reasonLabel,
                durationMs = durationMs?.coerceAtLeast(0L),
                timestampMillis = timestampMillis
            )
        )
    }

    fun mapJobStopReason(stopReasonCode: Int?): Pair<Int, String> {
        if (stopReasonCode == null) {
            return STOP_REASON_OS_PREEMPTED_LEGACY to "Preempted by Android JobScheduler"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val label = when (stopReasonCode) {
                JobParameters.STOP_REASON_UNDEFINED -> "OS stopped job (unspecified)"
                JobParameters.STOP_REASON_CANCELLED_BY_APP -> "Cancelled by app / Guard disabled"
                JobParameters.STOP_REASON_PREEMPT -> "Preempted by OS JobScheduler"
                JobParameters.STOP_REASON_TIMEOUT -> "OS job execution timeout reached"
                JobParameters.STOP_REASON_DEVICE_STATE -> "Stopped due to device state constraint"
                JobParameters.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "Stopped: battery dropped below threshold"
                JobParameters.STOP_REASON_CONSTRAINT_CHARGING -> "Stopped: charging constraint unmet"
                JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "Stopped: network constraint unmet"
                JobParameters.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "Stopped: device left idle state"
                JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "Stopped: low storage constraint"
                JobParameters.STOP_REASON_QUOTA -> "Stopped: OS background execution quota"
                JobParameters.STOP_REASON_BACKGROUND_RESTRICTION -> "Stopped: OS background restriction active"
                JobParameters.STOP_REASON_APP_STANDBY -> "Stopped: App Standby bucket restriction"
                JobParameters.STOP_REASON_USER -> "Stopped directly by user"
                JobParameters.STOP_REASON_SYSTEM_PROCESSING -> "Stopped for OS system processing"
                JobParameters.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "Stopped: launch prediction changed"
                else -> "OS stopped job (code $stopReasonCode)"
            }
            return stopReasonCode to label
        }
        return stopReasonCode to "OS stopped job (code $stopReasonCode)"
    }

    /**
     * Inspects Android's Historical Process Exit Reasons (API 30+) on app launch to detect
     * whether the previous app task/process was force-stopped, swiped away, killed by LMK, or
     * exited abnormally while off-screen.
     */
    fun inspectAndRecordPreviousProcessStop(
        context: Context,
        expectedEpoch: Long? = null
    ): TaskExecutionEvent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val appContext = context.applicationContext
        return try {
            val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
            val exits = activityManager.getHistoricalProcessExitReasons(appContext.packageName, 0, 5)
            val latestExit = exits.maxByOrNull { it.timestamp } ?: return null

            synchronized(lock) {
                if (expectedEpoch != null && analyticsEpoch != expectedEpoch) {
                    return null
                }
                val p = prefs(appContext)
                val lastRecordedTs = p.getLong(KEY_LAST_INSPECTED_EXIT_TS, 0L)
                if (latestExit.timestamp <= lastRecordedTs) {
                    return null
                }
                p.edit().putLong(KEY_LAST_INSPECTED_EXIT_TS, latestExit.timestamp).apply()
            }

            val (state, label) = mapProcessExitReason(latestExit)
            val event = TaskExecutionEvent(
                id = UUID.randomUUID().toString(),
                taskType = TrackedTaskType.APP_PROCESS_TASK,
                taskName = "Previous App Process",
                state = state,
                stopReasonCode = latestExit.reason,
                stopReasonLabel = label,
                timestampMillis = latestExit.timestamp
            )
            appendEvent(appContext, event)
        } catch (_: Throwable) {
            null
        }
    }

    private fun mapProcessExitReason(exitInfo: ApplicationExitInfo): Pair<TaskLifecycleState, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return TaskLifecycleState.OS_TERMINATED to "Previous process stopped"
        }
        return when (exitInfo.reason) {
            ApplicationExitInfo.REASON_EXIT_SELF ->
                TaskLifecycleState.COMPLETED to "Previous app process exited cleanly"
            ApplicationExitInfo.REASON_USER_REQUESTED ->
                TaskLifecycleState.OS_TERMINATED to "App task swiped away or stopped by user"
            ApplicationExitInfo.REASON_USER_STOPPED ->
                TaskLifecycleState.OS_TERMINATED to "App force-stopped in system settings"
            ApplicationExitInfo.REASON_LOW_MEMORY ->
                TaskLifecycleState.OS_TERMINATED to "OS Low Memory Killer stopped background task"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ->
                TaskLifecycleState.OS_TERMINATED to "OS stopped task due to resource usage"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE ->
                TaskLifecycleState.OS_TERMINATED to "OS stopped task after permission change"
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR ->
                TaskLifecycleState.OS_TERMINATED to "App process terminated unexpectedly (${exitInfo.reason})"
            else ->
                TaskLifecycleState.OS_TERMINATED to "OS terminated previous process (code ${exitInfo.reason})"
        }
    }

    fun getSummary(context: Context): TaskAnalyticsSummary {
        synchronized(lock) {
            val summary = buildSummaryLocked(context.applicationContext)
            _summaryFlow.value = summary
            return summary
        }
    }

    fun clearAnalytics(context: Context): TaskAnalyticsSummary {
        synchronized(lock) {
            analyticsEpoch++
            prefs(context.applicationContext).edit().clear().commit()
            val empty = TaskAnalyticsSummary()
            _summaryFlow.value = empty
            return empty
        }
    }

    private fun appendEvent(context: Context, event: TaskExecutionEvent): TaskExecutionEvent {
        synchronized(lock) {
            val appContext = context.applicationContext
            val p = prefs(appContext)
            val events = readEventsLocked(p).toMutableList()
            events.add(0, event)
            if (events.size > MAX_RING_BUFFER_SIZE) {
                events.subList(MAX_RING_BUFFER_SIZE, events.size).clear()
            }

            var totalRuns = p.getInt(KEY_TOTAL_RUNS, 0)
            var totalCompleted = p.getInt(KEY_TOTAL_COMPLETED, 0)
            var totalStopped = p.getInt(KEY_TOTAL_STOPPED, 0)
            var totalFailed = p.getInt(KEY_TOTAL_FAILED, 0)
            var totalOsTerminated = p.getInt(KEY_TOTAL_OS_TERMINATED, 0)

            when (event.state) {
                TaskLifecycleState.STARTED -> totalRuns++
                TaskLifecycleState.COMPLETED -> totalCompleted++
                TaskLifecycleState.STOPPED,
                TaskLifecycleState.CANCELLED -> totalStopped++
                TaskLifecycleState.OS_TERMINATED -> {
                    totalStopped++
                    totalOsTerminated++
                }
                TaskLifecycleState.FAILED -> totalFailed++
            }

            p.edit()
                .putString(KEY_EVENTS_JSON, serializeEventsLocked(events))
                .putInt(KEY_TOTAL_RUNS, totalRuns)
                .putInt(KEY_TOTAL_COMPLETED, totalCompleted)
                .putInt(KEY_TOTAL_STOPPED, totalStopped)
                .putInt(KEY_TOTAL_FAILED, totalFailed)
                .putInt(KEY_TOTAL_OS_TERMINATED, totalOsTerminated)
                .apply()

            _summaryFlow.value = buildSummaryLocked(appContext)
            return event
        }
    }

    private fun buildSummaryLocked(context: Context): TaskAnalyticsSummary {
        val p = prefs(context)
        val events = readEventsLocked(p)

        // Most recent status per TrackedTaskType (events are ordered newest-first)
        val latestStatusByType = linkedMapOf<TrackedTaskType, TaskLifecycleState>()
        for (ev in events) {
            if (!latestStatusByType.containsKey(ev.taskType)) {
                latestStatusByType[ev.taskType] = ev.state
            }
        }

        val activeTypes = latestStatusByType
            .filterValues { it == TaskLifecycleState.STARTED }
            .keys
            .toSet()
        val stoppedTypes = latestStatusByType
            .filterValues { it.isStoppedState }
            .keys
            .toSet()

        return TaskAnalyticsSummary(
            totalTrackedRuns = p.getInt(KEY_TOTAL_RUNS, 0),
            activeTasksCount = activeTypes.size,
            completedTasksCount = p.getInt(KEY_TOTAL_COMPLETED, 0),
            stoppedTasksCount = p.getInt(KEY_TOTAL_STOPPED, 0),
            failedTasksCount = p.getInt(KEY_TOTAL_FAILED, 0),
            osTerminatedCount = p.getInt(KEY_TOTAL_OS_TERMINATED, 0),
            activeTaskTypes = activeTypes,
            stoppedTaskTypes = stoppedTypes,
            lastStoppedEvent = events.firstOrNull { it.state.isStoppedState },
            taskStatusByType = latestStatusByType,
            recentEvents = events
        )
    }

    private fun readEventsLocked(p: android.content.SharedPreferences): List<TaskExecutionEvent> {
        val raw = p.getString(KEY_EVENTS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<TaskExecutionEvent>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val typeName = obj.optString("taskType")
                val stateName = obj.optString("state")
                val type = TrackedTaskType.entries.firstOrNull { it.name == typeName } ?: continue
                val state = TaskLifecycleState.entries.firstOrNull { it.name == stateName } ?: continue
                result.add(
                    TaskExecutionEvent(
                        id = obj.optString("id"),
                        taskType = type,
                        taskName = obj.optString("taskName", type.displayName),
                        state = state,
                        stopReasonCode = if (obj.has("stopReasonCode") && !obj.isNull("stopReasonCode")) {
                            obj.optInt("stopReasonCode")
                        } else null,
                        stopReasonLabel = obj.optString("stopReasonLabel", "").takeIf { it.isNotEmpty() },
                        durationMs = if (obj.has("durationMs") && !obj.isNull("durationMs")) {
                            obj.optLong("durationMs")
                        } else null,
                        timestampMillis = obj.optLong("timestampMillis", 0L)
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeEventsLocked(events: List<TaskExecutionEvent>): String {
        val array = JSONArray()
        for (ev in events) {
            val obj = JSONObject().apply {
                put("id", ev.id)
                put("taskType", ev.taskType.name)
                put("taskName", ev.taskName)
                put("state", ev.state.name)
                if (ev.stopReasonCode != null) put("stopReasonCode", ev.stopReasonCode)
                if (ev.stopReasonLabel != null) put("stopReasonLabel", ev.stopReasonLabel)
                if (ev.durationMs != null) put("durationMs", ev.durationMs)
                put("timestampMillis", ev.timestampMillis)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
