package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.autofill.AutofillVaultSession
import com.example.nfc.NfcPaymentSession
import com.example.security.BlackBoxBackgroundScheduler
import com.example.security.ExportFileCleaner
import com.example.security.TaskAnalyticsEngine
import com.example.security.TaskLifecycleState
import com.example.security.TrackedTaskType
import com.example.widget.ConfidentialNotesMemoryCache
import com.example.widget.ConfidentialNotesWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight Application entry point for The Black-Box.
 *
 * Ensures silent background maintenance stays scheduled across restarts, inspects OS exit
 * telemetry for unexpectedly stopped app tasks, and responds immediately to OS memory trim
 * signals by purging volatile RAM caches cleanly.
 */
class BlackBoxApplication : Application() {

    private val appIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        BlackBoxBackgroundScheduler.ensureScheduledIfEnabled(this)
        TaskAnalyticsEngine.recordTaskStarted(
            context = applicationContext,
            taskType = TrackedTaskType.APP_PROCESS_TASK,
            taskName = "Application Process Active"
        )
        val epochAtLaunch = TaskAnalyticsEngine.currentEpoch()
        appIoScope.launch {
            TaskAnalyticsEngine.inspectAndRecordPreviousProcessStop(applicationContext, expectedEpoch = epochAtLaunch)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        handleMemoryPressure(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        handleMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    internal fun handleMemoryPressure(level: Int) {
        val hadWidgetCache = ConfidentialNotesMemoryCache.isUnlocked()
        val hadAutofillSession = AutofillVaultSession.isUnlocked()
        val hadNfcSession = NfcPaymentSession.getActiveSession() != null

        val isBackgroundOrCritical = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        if (isBackgroundOrCritical) {
            ConfidentialNotesMemoryCache.clear()
            ConfidentialNotesWidgetProvider.refreshAllWidgets(this)
            if (hadNfcSession) {
                NfcPaymentSession.disarm(
                    context = this,
                    reason = "OS trimMemory(level=$level) disarmed NFC contactless session"
                )
            } else {
                NfcPaymentSession.clear()
            }
        }
        val isSeverePressure = level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        if (isSeverePressure) {
            AutofillVaultSession.clear()
            appIoScope.launch {
                ExportFileCleaner.cleanupIdleExports(applicationContext)
            }
        }
        if ((isBackgroundOrCritical && hadWidgetCache) || (isSeverePressure && hadAutofillSession)) {
            TaskAnalyticsEngine.recordTaskStopped(
                context = this,
                taskType = TrackedTaskType.APP_PROCESS_TASK,
                taskName = "Volatile Session Cache",
                stopReasonCode = TaskAnalyticsEngine.STOP_REASON_MEMORY_PRESSURE,
                stopReasonLabel = "OS trimMemory(level=$level) stopped active RAM caches",
                state = TaskLifecycleState.STOPPED
            )
        }
    }
}
