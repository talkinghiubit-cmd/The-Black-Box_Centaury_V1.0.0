package com.example.security

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.example.autofill.AutofillVaultSession
import com.example.widget.ConfidentialNotesMemoryCache
import com.example.widget.ConfidentialNotesWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BackgroundMaintenanceReport(
    val purgedExportsCount: Int,
    val prunedHibpFilesCount: Int,
    val sessionExpired: Boolean,
    val completedAtMillis: Long
)

data class BackgroundGuardStatus(
    val isEnabled: Boolean,
    val isJobScheduled: Boolean,
    val lastRunAtMillis: Long?,
    val lastCleanedItemsCount: Int
)

/**
 * Silent, ultra-low-memory background maintenance service powered by Android [JobScheduler].
 *
 * Designed for zero-impact background operation:
 * - Zero persistent notifications and zero wake-lock polling loops
 * - Batched by the OS with `requiresBatteryNotLow` when available
 * - Runs only for milliseconds to sweep orphaned temp exports, enforce RAM session TTLs,
 *   prune stale HIBP cache files, and refresh the Confidential Notes widget state, then exits.
 */
class BlackBoxVaultMaintenanceJobService : JobService() {

    private var activeJob: Job? = null
    private var jobStartedAtMillis: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        val appContext = applicationContext
        val startedAt = System.currentTimeMillis()
        jobStartedAtMillis = startedAt
        TaskAnalyticsEngine.recordTaskStarted(
            context = appContext,
            taskType = TrackedTaskType.BACKGROUND_MAINTENANCE_JOB,
            taskName = "OS Background Vault Guard",
            timestampMillis = startedAt
        )
        activeJob = serviceScope.launch {
            try {
                BlackBoxBackgroundScheduler.executeMaintenancePass(appContext, emitAnalytics = false)
                if (isActive) {
                    TaskAnalyticsEngine.recordTaskCompleted(
                        context = appContext,
                        taskType = TrackedTaskType.BACKGROUND_MAINTENANCE_JOB,
                        taskName = "OS Background Vault Guard",
                        durationMs = System.currentTimeMillis() - startedAt
                    )
                }
            } catch (_: CancellationException) {
                // Stop reason already recorded by onStopJob()
            } catch (t: Throwable) {
                TaskAnalyticsEngine.recordTaskFailed(
                    context = appContext,
                    taskType = TrackedTaskType.BACKGROUND_MAINTENANCE_JOB,
                    taskName = "OS Background Vault Guard",
                    reasonLabel = t.message ?: "Maintenance job failed",
                    durationMs = System.currentTimeMillis() - startedAt
                )
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        val rawReason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params?.stopReason
        } else {
            null
        }
        val (reasonCode, reasonLabel) = TaskAnalyticsEngine.mapJobStopReason(rawReason)
        val elapsed = if (jobStartedAtMillis > 0L) System.currentTimeMillis() - jobStartedAtMillis else null
        TaskAnalyticsEngine.recordTaskStopped(
            context = applicationContext,
            taskType = TrackedTaskType.BACKGROUND_MAINTENANCE_JOB,
            taskName = "OS Background Vault Guard",
            stopReasonCode = reasonCode,
            stopReasonLabel = reasonLabel,
            state = TaskLifecycleState.STOPPED,
            durationMs = elapsed
        )
        activeJob?.cancel()
        activeJob = null
        // Do not aggressively reschedule on preemption to conserve battery
        return false
    }
}

object BlackBoxBackgroundScheduler {

    const val JOB_ID = 4107
    private const val PREFS_NAME = "blackbox_bg_prefs"
    private const val KEY_ENABLED = "bg_guard_enabled"
    private const val KEY_LAST_RUN_AT = "bg_last_run_at"
    private const val KEY_LAST_CLEANED_COUNT = "bg_last_cleaned_count"
    private const val PERIODIC_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        prefs(appContext).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            ensureScheduled(appContext)
        } else {
            cancelScheduled(appContext)
        }
    }

    fun getStatus(context: Context): BackgroundGuardStatus {
        val appContext = context.applicationContext
        val p = prefs(appContext)
        val lastRun = p.getLong(KEY_LAST_RUN_AT, 0L)
        return BackgroundGuardStatus(
            isEnabled = p.getBoolean(KEY_ENABLED, true),
            isJobScheduled = isJobScheduled(appContext),
            lastRunAtMillis = if (lastRun > 0L) lastRun else null,
            lastCleanedItemsCount = p.getInt(KEY_LAST_CLEANED_COUNT, 0)
        )
    }

    fun isJobScheduled(context: Context): Boolean {
        val scheduler = context.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return false
        return scheduler.getPendingJob(JOB_ID) != null
    }

    fun ensureScheduledIfEnabled(context: Context) {
        if (isEnabled(context)) {
            ensureScheduled(context)
        }
    }

    fun ensureScheduled(context: Context): Boolean {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return false
        if (scheduler.getPendingJob(JOB_ID) != null) {
            return true
        }
        val component = ComponentName(appContext, BlackBoxVaultMaintenanceJobService::class.java)
        val builder = JobInfo.Builder(JOB_ID, component)
            .setPeriodic(PERIODIC_INTERVAL_MS)
            .setPersisted(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setRequiresBatteryNotLow(true)
        }
        return try {
            scheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
        } catch (_: SecurityException) {
            // Fallback without setPersisted if a stripped test manifest lacks RECEIVE_BOOT_COMPLETED
            val nonPersisted = JobInfo.Builder(JOB_ID, component)
                .setPeriodic(PERIODIC_INTERVAL_MS)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setRequiresBatteryNotLow(true)
                    }
                }
                .build()
            scheduler.schedule(nonPersisted) == JobScheduler.RESULT_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    fun cancelScheduled(context: Context) {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
        scheduler?.cancel(JOB_ID)
        val reasonCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            JobParameters.STOP_REASON_CANCELLED_BY_APP
        } else {
            TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED
        }
        TaskAnalyticsEngine.recordTaskStopped(
            context = appContext,
            taskType = TrackedTaskType.BACKGROUND_MAINTENANCE_JOB,
            taskName = "OS Background Vault Guard",
            stopReasonCode = reasonCode,
            stopReasonLabel = "Background Guard job cancelled by app",
            state = TaskLifecycleState.CANCELLED
        )
    }

    fun executeMaintenancePass(
        context: Context,
        emitAnalytics: Boolean = true
    ): BackgroundMaintenanceReport {
        val appContext = context.applicationContext
        val startedAt = System.currentTimeMillis()
        if (emitAnalytics) {
            TaskAnalyticsEngine.recordTaskStarted(
                context = appContext,
                taskType = TrackedTaskType.BACKGROUND_MAINTENANCE_JOB,
                taskName = "Silent Maintenance Sweep",
                timestampMillis = startedAt
            )
        }

        val purgedExports = ExportFileCleaner.cleanupIdleExports(appContext)
        val prunedHibp = OfflineHibpStore.pruneStaleCache(appContext)

        // Enforce passive idle TTL on Autofill RAM session; if expired/cleared, wipe widget cache too
        val stillUnlocked = AutofillVaultSession.isUnlocked()
        if (!stillUnlocked && ConfidentialNotesMemoryCache.isUnlocked()) {
            ConfidentialNotesMemoryCache.clear()
        }
        ConfidentialNotesWidgetProvider.refreshAllWidgets(appContext)

        val now = System.currentTimeMillis()
        val totalCleaned = purgedExports + prunedHibp
        prefs(appContext).edit()
            .putLong(KEY_LAST_RUN_AT, now)
            .putInt(KEY_LAST_CLEANED_COUNT, totalCleaned)
            .apply()

        if (emitAnalytics) {
            TaskAnalyticsEngine.recordTaskCompleted(
                context = appContext,
                taskType = TrackedTaskType.BACKGROUND_MAINTENANCE_JOB,
                taskName = "Silent Maintenance Sweep",
                durationMs = now - startedAt,
                note = "$totalCleaned item(s) purged",
                timestampMillis = now
            )
        }

        return BackgroundMaintenanceReport(
            purgedExportsCount = purgedExports,
            prunedHibpFilesCount = prunedHibp,
            sessionExpired = !stillUnlocked,
            completedAtMillis = now
        )
    }
}
