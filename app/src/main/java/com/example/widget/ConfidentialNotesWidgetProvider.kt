package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.BlackBoxDatabase
import com.example.data.VaultPartition
import com.example.model.VaultCategory
import com.example.security.TaskAnalyticsEngine
import com.example.security.TaskLifecycleState
import com.example.security.TrackedTaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home screen AppWidgetProvider for The Black-Box Confidential Notes Vault.
 *
 * Security & Resource Guarantees:
 * - Never writes plaintext confidential notes to disk or SharedPreferences.
 * - When the vault is locked, only queries the encrypted NOTE row count from Room (no payload decryption).
 * - When unlocked in RAM, displays up to 3 recent confidential note headers (masked until Peek is toggled)
 *   plus instant one-tap Lock and New Note actions.
 * - Uses full `updateAppWidget` updates (never partial updates) for minimal launcher memory footprint.
 */
class ConfidentialNotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val startedAt = System.currentTimeMillis()
        widgetScope.launch {
            try {
                val sealedCount = querySealedNoteCount(appContext)
                for (widgetId in appWidgetIds) {
                    val views = buildRemoteViews(appContext, sealedCount)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
                TaskAnalyticsEngine.recordTaskCompleted(
                    context = appContext,
                    taskType = TrackedTaskType.WIDGET_REFRESH,
                    taskName = "Notes Widget Update",
                    durationMs = System.currentTimeMillis() - startedAt
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TaskAnalyticsEngine.recordTaskStopped(
            context = context.applicationContext,
            taskType = TrackedTaskType.WIDGET_REFRESH,
            taskName = "Notes Widget Provider",
            stopReasonCode = TaskAnalyticsEngine.STOP_REASON_WIDGET_REMOVED,
            stopReasonLabel = "All home screen Notes widgets removed",
            state = TaskLifecycleState.STOPPED
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_LOCK_WIDGET -> {
                ConfidentialNotesMemoryCache.clear()
                refreshAllWidgets(appContext)
            }
            ACTION_TOGGLE_PEEK -> {
                ConfidentialNotesMemoryCache.togglePeek()
                refreshAllWidgets(appContext)
            }
            ACTION_REFRESH_NOTES -> {
                refreshAllWidgets(appContext)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_NOTES = "com.example.widget.ACTION_REFRESH_NOTES"
        const val ACTION_LOCK_WIDGET = "com.example.widget.ACTION_LOCK_WIDGET"
        const val ACTION_TOGGLE_PEEK = "com.example.widget.ACTION_TOGGLE_PEEK"

        const val EXTRA_WIDGET_ACTION = "com.example.widget.EXTRA_WIDGET_ACTION"
        const val EXTRA_WIDGET_NOTE_ID = "com.example.widget.EXTRA_WIDGET_NOTE_ID"

        const val WIDGET_ACTION_OPEN_NOTES = "OPEN_NOTES"
        const val WIDGET_ACTION_NEW_NOTE = "NEW_NOTE"
        const val WIDGET_ACTION_OPEN_NOTE_ID = "OPEN_NOTE_ID"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        suspend fun querySealedNoteCount(context: Context): Int {
            return try {
                BlackBoxDatabase.getDatabase(context.applicationContext)
                    .vaultDao()
                    .getRecordCountByCategory(VaultPartition.PRIMARY, VaultCategory.NOTE.name)
            } catch (_: Exception) {
                0
            }
        }

        fun getActiveWidgetIds(context: Context): IntArray {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, ConfidentialNotesWidgetProvider::class.java)
            return manager.getAppWidgetIds(component)
        }

        fun refreshAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val widgetIds = getActiveWidgetIds(appContext)
            if (widgetIds.isEmpty()) return
            val manager = AppWidgetManager.getInstance(appContext)
            val startedAt = System.currentTimeMillis()
            widgetScope.launch {
                val sealedCount = querySealedNoteCount(appContext)
                for (widgetId in widgetIds) {
                    val views = buildRemoteViews(appContext, sealedCount)
                    manager.updateAppWidget(widgetId, views)
                }
                TaskAnalyticsEngine.recordTaskCompleted(
                    context = appContext,
                    taskType = TrackedTaskType.WIDGET_REFRESH,
                    taskName = "Notes Widget Sync",
                    durationMs = System.currentTimeMillis() - startedAt
                )
            }
        }

        internal fun buildRemoteViews(context: Context, sealedNoteCount: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_confidential_notes)
            val unlockedNotes = ConfidentialNotesMemoryCache.getUnlockedNotes()
            val isUnlocked = unlockedNotes != null
            val peekRevealed = ConfidentialNotesMemoryCache.isPeekRevealed()

            if (!isUnlocked) {
                views.setTextViewText(R.id.widget_status_badge, "SEALED")
                views.setTextColor(R.id.widget_status_badge, Color.parseColor("#FFFBBF24"))
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_amber)
                views.setTextViewText(R.id.widget_subtitle, "AES-256-GCM • LOCKED ON DEVICE")

                views.setViewVisibility(R.id.widget_locked_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_unlocked_list, View.GONE)
                views.setViewVisibility(R.id.widget_btn_peek, View.GONE)

                val countText = when (sealedNoteCount) {
                    0 -> "NO CONFIDENTIAL NOTES YET"
                    1 -> "1 CONFIDENTIAL NOTE SEALED"
                    else -> "$sealedNoteCount CONFIDENTIAL NOTES SEALED"
                }
                views.setTextViewText(R.id.widget_locked_count, countText)
                views.setTextViewText(
                    R.id.widget_locked_hint,
                    if (sealedNoteCount == 0) {
                        "Tap + New Note to encrypt a private note inside your AES-256-GCM vault."
                    } else {
                        "Contents remain encrypted at rest. Unlock with biometrics or master password to read."
                    }
                )

                views.setTextViewText(R.id.widget_btn_primary_action, "UNLOCK NOTES")
                views.setOnClickPendingIntent(
                    R.id.widget_btn_primary_action,
                    createMainActivityPendingIntent(
                        context = context,
                        requestCode = 1001,
                        action = WIDGET_ACTION_OPEN_NOTES
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_locked_container,
                    createMainActivityPendingIntent(
                        context = context,
                        requestCode = 1002,
                        action = WIDGET_ACTION_OPEN_NOTES
                    )
                )
            } else {
                val list = unlockedNotes.orEmpty()
                views.setTextViewText(R.id.widget_status_badge, "UNLOCKED")
                views.setTextColor(R.id.widget_status_badge, Color.parseColor("#FF00E676"))
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_emerald)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "${list.size} CONFIDENTIAL NOTE(S) • IN-RAM"
                )

                views.setViewVisibility(R.id.widget_btn_peek, if (list.isNotEmpty()) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_btn_peek, if (peekRevealed) "MASK" else "PEEK")
                views.setOnClickPendingIntent(
                    R.id.widget_btn_peek,
                    createBroadcastPendingIntent(context, 2001, ACTION_TOGGLE_PEEK)
                )

                if (list.isEmpty()) {
                    views.setViewVisibility(R.id.widget_locked_container, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_unlocked_list, View.GONE)
                    views.setTextViewText(R.id.widget_locked_count, "VAULT UNLOCKED • 0 NOTES")
                    views.setTextViewText(
                        R.id.widget_locked_hint,
                        "Tap + New Note to create an encrypted confidential note."
                    )
                } else {
                    views.setViewVisibility(R.id.widget_locked_container, View.GONE)
                    views.setViewVisibility(R.id.widget_unlocked_list, View.VISIBLE)

                    val rowIds = listOf(R.id.widget_note_row_1, R.id.widget_note_row_2, R.id.widget_note_row_3)
                    val titleIds = listOf(R.id.widget_note_title_1, R.id.widget_note_title_2, R.id.widget_note_title_3)
                    val previewIds = listOf(R.id.widget_note_preview_1, R.id.widget_note_preview_2, R.id.widget_note_preview_3)

                    for (index in 0..2) {
                        val note = list.getOrNull(index)
                        if (note != null) {
                            views.setViewVisibility(rowIds[index], View.VISIBLE)
                            val titleText = if (note.isFavorite) "★ ${note.title}" else note.title
                            views.setTextViewText(titleIds[index], titleText)
                            val previewText = if (peekRevealed && note.previewSnippet.isNotBlank()) {
                                note.previewSnippet
                            } else {
                                "•••••••••••• • Tap to open encrypted note"
                            }
                            views.setTextViewText(previewIds[index], previewText)
                            views.setOnClickPendingIntent(
                                rowIds[index],
                                createMainActivityPendingIntent(
                                    context = context,
                                    requestCode = 3000 + index,
                                    action = WIDGET_ACTION_OPEN_NOTE_ID,
                                    noteId = note.id
                                )
                            )
                        } else {
                            views.setViewVisibility(rowIds[index], View.GONE)
                        }
                    }
                }

                views.setTextViewText(R.id.widget_btn_primary_action, "LOCK VAULT")
                views.setOnClickPendingIntent(
                    R.id.widget_btn_primary_action,
                    createBroadcastPendingIntent(context, 2002, ACTION_LOCK_WIDGET)
                )
            }

            views.setOnClickPendingIntent(
                R.id.widget_btn_new_note,
                createMainActivityPendingIntent(
                    context = context,
                    requestCode = 1003,
                    action = WIDGET_ACTION_NEW_NOTE
                )
            )
            return views
        }

        private fun createMainActivityPendingIntent(
            context: Context,
            requestCode: Int,
            action: String,
            noteId: String? = null
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_WIDGET_ACTION, action)
                if (!noteId.isNullOrBlank()) {
                    putExtra(EXTRA_WIDGET_NOTE_ID, noteId)
                }
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun createBroadcastPendingIntent(
            context: Context,
            requestCode: Int,
            action: String
        ): PendingIntent {
            val intent = Intent(context, ConfidentialNotesWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
