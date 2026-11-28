package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.nfc.NfcContactlessHelper
import com.example.security.BiometricAccessResult
import com.example.security.BiometricCipherInitResult
import com.example.security.BiometricManagerWrapper
import com.example.security.BiometricVaultAuthService
import com.example.security.ExportFileCleaner
import com.example.security.TaskAnalyticsEngine
import com.example.security.TaskLifecycleState
import com.example.security.TrackedTaskType
import com.example.ui.VaultViewModel
import com.example.ui.components.IncognitoKeyboardArea
import com.example.ui.components.PrivacyShieldOverlay
import com.example.ui.dialogs.BackupExportDialog
import com.example.ui.dialogs.CsvImportDialog
import com.example.ui.dialogs.ItemDetailSheet
import com.example.ui.dialogs.ItemEditSheet
import com.example.ui.dialogs.PasswordGeneratorSheet
import com.example.ui.dialogs.PrivacyPolicyDialog
import com.example.ui.dialogs.ReceiveShareDialog
import com.example.ui.dialogs.SecureShareDialog
import com.example.ui.dialogs.SecurityHealthSheet
import com.example.ui.dialogs.SettingsDialog
import com.example.ui.dialogs.VoiceUnlockDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LockScreen
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxTheme
import com.example.widget.ConfidentialNotesWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private val biometricAuthService by lazy { BiometricVaultAuthService.getInstance(applicationContext) }
    private val biometricManager by lazy { BiometricManagerWrapper.getInstance(applicationContext) }

    private var pendingSharedFile: File? = null
    private var pendingShareCleanupJob: Job? = null

    private val shareSheetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val fileToPurge = pendingSharedFile
        pendingSharedFile = null
        ExportFileCleaner.markShareFinished()
        if (fileToPurge != null) {
            if (pendingShareCleanupJob?.isActive == true) {
                pendingShareCleanupJob?.cancel()
                TaskAnalyticsEngine.recordTaskStopped(
                    context = applicationContext,
                    taskType = TrackedTaskType.SHARE_CLEANUP,
                    taskName = "Share Export Cleanup",
                    stopReasonCode = TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED,
                    stopReasonLabel = "Rescheduled by newer export share",
                    state = TaskLifecycleState.STOPPED
                )
            }
            val startedAt = System.currentTimeMillis()
            TaskAnalyticsEngine.recordTaskStarted(
                context = applicationContext,
                taskType = TrackedTaskType.SHARE_CLEANUP,
                taskName = "Share Export Cleanup",
                timestampMillis = startedAt
            )
            pendingShareCleanupJob = lifecycleScope.launch(Dispatchers.IO) {
                // Allow receiving app (Drive, Gmail, Files) a brief grace window to stream the FileProvider FD
                delay(15_000L)
                ExportFileCleaner.secureDeleteFile(fileToPurge)
                ExportFileCleaner.clearActiveShare(fileToPurge)
                TaskAnalyticsEngine.recordTaskCompleted(
                    context = applicationContext,
                    taskType = TrackedTaskType.SHARE_CLEANUP,
                    taskName = "Share Export Cleanup",
                    durationMs = System.currentTimeMillis() - startedAt
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Protect screen content against screenshots, screen recording, and recent task switcher previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()

        // Monitor Activity lifecycle directly from VaultViewModel to lock on background and enforce biometric re-auth
        viewModel.monitorAppLifecycle(
            lifecycle = lifecycle,
            biometricManager = biometricManager,
            onRequireBiometricReauth = { promptBiometrics(isManualTrigger = false) },
            onCancelBiometricPrompt = { biometricAuthService.cancelAuthentication() }
        )

        handleWidgetIntent(intent)

        setContent {
            BlackBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BlackBoxPitch
                ) {
                    IncognitoKeyboardArea {
                        BlackBoxAppContent(
                            viewModel = viewModel,
                            biometricManager = biometricManager,
                            onTriggerBiometric = { promptBiometrics(isManualTrigger = true) },
                            onShareFile = { file -> shareExportedFile(file) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val widgetAction = intent?.getStringExtra(ConfidentialNotesWidgetProvider.EXTRA_WIDGET_ACTION)
        val widgetNoteId = intent?.getStringExtra(ConfidentialNotesWidgetProvider.EXTRA_WIDGET_NOTE_ID)
        if (!widgetAction.isNullOrBlank()) {
            viewModel.handleWidgetRequest(widgetAction, widgetNoteId)
            intent.removeExtra(ConfidentialNotesWidgetProvider.EXTRA_WIDGET_ACTION)
            intent.removeExtra(ConfidentialNotesWidgetProvider.EXTRA_WIDGET_NOTE_ID)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.recordUserInteraction()
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            if (pendingShareCleanupJob?.isActive == true) {
                pendingShareCleanupJob?.cancel()
                TaskAnalyticsEngine.recordTaskStopped(
                    context = applicationContext,
                    taskType = TrackedTaskType.SHARE_CLEANUP,
                    taskName = "Share Export Cleanup",
                    stopReasonCode = TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED,
                    stopReasonLabel = "Cleanup timer stopped on Activity finish",
                    state = TaskLifecycleState.STOPPED
                )
            }
            ExportFileCleaner.cleanupAllExportsUnconditionally(applicationContext)
        }
        super.onDestroy()
    }

    private fun promptBiometrics(isManualTrigger: Boolean = false) {
        if (!biometricAuthService.isStrongBiometricAvailable()) {
            if (isManualTrigger) {
                viewModel.setErrorMessage(getString(R.string.biometric_strong_required_error))
            }
            return
        }

        lifecycleScope.launch {
            when (val cipherResult = viewModel.prepareBiometricUnlockCipher()) {
                is BiometricCipherInitResult.Ready -> {
                    biometricAuthService.authenticateWithCryptoObject(
                        activity = this@MainActivity,
                        cipher = cipherResult.cipher,
                        title = getString(R.string.biometric_prompt_title),
                        subtitle = biometricAuthService.getModalityLabel(),
                        description = biometricAuthService.getSensorActionHint(),
                        onResult = { result ->
                            when (result) {
                                is BiometricAccessResult.Granted -> {
                                    viewModel.resetBiometricReauthGuard()
                                    viewModel.unlockWithBiometrics(result.cryptoObject?.cipher)
                                }
                                is BiometricAccessResult.Denied -> {
                                    viewModel.setErrorMessage(result.message)
                                }
                                is BiometricAccessResult.Cancelled -> {
                                    // User chose Master Password or dismissed prompt
                                }
                                is BiometricAccessResult.Error -> {
                                    viewModel.setErrorMessage(result.message)
                                }
                            }
                        }
                    )
                }
                is BiometricCipherInitResult.Invalidated -> {
                    // ViewModel already disabled stale biometric enrollment and displayed guidance
                }
                is BiometricCipherInitResult.Unavailable -> {
                    if (isManualTrigger) {
                        viewModel.setErrorMessage(cipherResult.reason)
                    }
                }
            }
        }
    }

    private fun shareExportedFile(file: File) {
        try {
            pendingShareCleanupJob?.cancel()
            pendingSharedFile = file
            ExportFileCleaner.markShareStarted(file)

            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(applicationContext, authority, file)
            val mimeType = if (file.name.endsWith(".html")) "text/html" else "application/octet-stream"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "The Black-Box Export — ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareSheetLauncher.launch(Intent.createChooser(shareIntent, "Save or Export File"))
        } catch (e: Exception) {
            pendingSharedFile = null
            ExportFileCleaner.clearActiveShare(file)
            ExportFileCleaner.secureDeleteFile(file)
            viewModel.setErrorMessage("Export failed: ${e.message ?: "Unable to share file"}")
        } finally {
            viewModel.clearShareIntent()
        }
    }
}

@Composable
fun BlackBoxAppContent(
    viewModel: VaultViewModel,
    biometricManager: BiometricManagerWrapper,
    onTriggerBiometric: () -> Unit,
    onShareFile: (File) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showGeneratorSheet by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSecurityHealthSheet by remember { mutableStateOf(false) }
    var showVoiceUnlockDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Handle file sharing
    LaunchedEffect(uiState.shareFileIntent) {
        uiState.shareFileIntent?.let { file ->
            onShareFile(file)
        }
    }

    // Prioritize BlackBoxHceService while user arms "Pay with NFC" in foreground
    val activity = context as? FragmentActivity
    val isNfcArmed = uiState.armedNfcSession != null && uiState.isUnlocked && !uiState.isSensitiveContentHidden
    DisposableEffect(isNfcArmed, activity) {
        if (isNfcArmed && activity != null) {
            NfcContactlessHelper.enableForegroundPreferredService(activity)
        }
        onDispose {
            if (isNfcArmed && activity != null) {
                NfcContactlessHelper.disableForegroundPreferredService(activity)
            }
        }
    }

    if (!uiState.isUnlocked) {
        LockScreen(
            uiState = uiState,
            biometricManager = biometricManager,
            onUnlockPassword = { viewModel.unlockWithPassword(it) },
            onUnlockBiometrics = onTriggerBiometric,
            onInitializeVault = { pw, bio -> viewModel.initializeVault(pw, bio) },
            onTriggerBiometricPrompt = onTriggerBiometric,
            onTriggerVoiceUnlock = { showVoiceUnlockDialog = true },
            onOpenPrivacyPolicy = { showPrivacyDialog = true }
        )
    } else if (uiState.isSensitiveContentHidden) {
        PrivacyShieldOverlay(
            isBiometricAvailable = !uiState.isDuressMode && uiState.isBiometricEnabled && biometricManager.isReady(),
            isVoiceBiometricEnabled = !uiState.isDuressMode && uiState.isVoiceBiometricEnabled,
            modalityLabel = biometricManager.getModalityDisplayName(),
            onTriggerBiometric = onTriggerBiometric,
            onTriggerVoiceUnlock = { showVoiceUnlockDialog = true },
            onEnterPassword = { viewModel.lockVault() }
        )
    } else {
        DashboardScreen(
            uiState = uiState,
            onSearchChanged = { viewModel.setSearchQuery(it) },
            onCategorySelected = { viewModel.setCategoryFilter(it) },
            onToggleFavoritesFilter = { viewModel.toggleFavoritesFilter() },
            onClearHealthFilter = { viewModel.setHealthFilter(null) },
            onItemClick = { viewModel.openItemDetail(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onCopyText = { label, text, sensitive -> viewModel.copyToClipboard(context, label, text, sensitive) },
            onCreateNewItem = { viewModel.startCreateNewItem() },
            onLockVault = { viewModel.lockVault() },
            onOpenGenerator = { showGeneratorSheet = true },
            onOpenReceiveShare = { viewModel.openReceiveShareDialog() },
            onOpenCsvImport = { showCsvImportDialog = true },
            onOpenBackupExport = { showBackupDialog = true },
            onOpenSecurityHealth = { showSecurityHealthSheet = true },
            onOpenSettings = { showSettingsDialog = true },
            onClearMessages = { viewModel.clearMessages() }
        )

        // Item Edit Sheet
        uiState.activeItemForEdit?.let { editItem ->
            ItemEditSheet(
                item = editItem,
                isNew = uiState.isCreatingNewItem,
                onSave = { viewModel.saveItem(it) },
                onDismiss = { viewModel.cancelEdit() }
            )
        }

        // Item Detail Sheet
        uiState.activeItemForDetail?.let { detailItem ->
            ItemDetailSheet(
                item = detailItem,
                totpSecondsRemaining = uiState.totpSecondsRemaining,
                isBreachChecking = uiState.isBreachChecking,
                activeAttachmentPreview = uiState.activeAttachmentPreview,
                armedNfcSession = uiState.armedNfcSession,
                nfcRemainingSeconds = uiState.nfcRemainingSeconds,
                nfcHardwareStatus = uiState.nfcHardwareStatus,
                onArmNfcPayment = { viewModel.armNfcPayment(detailItem) },
                onDisarmNfcPayment = { viewModel.disarmNfcPayment() },
                onRefreshNfcStatus = { viewModel.refreshNfcStatus() },
                onEdit = {
                    viewModel.closeItemDetail()
                    viewModel.startEditItem(detailItem)
                },
                onShare = {
                    viewModel.startShareItem(detailItem)
                },
                onDelete = { viewModel.deleteItem(detailItem.id) },
                onToggleFavorite = { viewModel.toggleFavorite(detailItem.id) },
                onCopyText = { label, text, sensitive ->
                    viewModel.copyToClipboard(context, label, text, sensitive)
                },
                onCheckBreach = { viewModel.checkSingleItemBreach(detailItem) },
                onAddAttachmentUri = { uri ->
                    viewModel.addAttachmentFromUri(context, detailItem.id, uri)
                },
                onPreviewAttachment = { viewModel.previewAttachment(it) },
                onDeleteAttachment = { viewModel.deleteAttachment(it) },
                onDismissAttachmentPreview = { viewModel.dismissAttachmentPreview() },
                onDismiss = { viewModel.closeItemDetail() }
            )
        }

        // Password Generator Sheet
        if (showGeneratorSheet) {
            PasswordGeneratorSheet(
                uiState = uiState,
                onConfigChanged = { viewModel.updatePasswordConfig(it) },
                onRegenerate = { viewModel.generatePassword() },
                onCopyPassword = {
                    viewModel.copyToClipboard(context, "Password", it, true)
                    showGeneratorSheet = false
                },
                onDismiss = { showGeneratorSheet = false }
            )
        }

        // Backup & PC Export Dialog
        if (showBackupDialog) {
            BackupExportDialog(
                uiState = uiState,
                onExportHtml = { viewModel.exportHtmlVault(context) },
                onExportSelectiveHtml = { ids, pw -> viewModel.exportCustomHtmlVault(context, ids, pw) },
                onExportSnapshot = { viewModel.exportSnapshot(context) },
                onInspectSnapshot = { viewModel.inspectSnapshotContent(it) },
                onInspectSnapshotUri = { viewModel.inspectSnapshotFromUri(context, it) },
                onOpenCsvImport = { showCsvImportDialog = true },
                onRestoreSnapshot = { replace -> viewModel.restoreSnapshot(replace) },
                onDismissSnapshotPreview = { viewModel.dismissSnapshotPreview() },
                onDismiss = { showBackupDialog = false }
            )
        }

        // Multi-Browser (Chrome, Edge, Firefox, Opera) & CSV Password Import Dialog
        if (showCsvImportDialog) {
            CsvImportDialog(
                preview = uiState.csvImportPreview,
                isImporting = uiState.isCsvImporting,
                onInspectCsvUri = { uri, preset -> viewModel.inspectCsvFromUri(context, uri, preset) },
                onInspectCsvText = { csv, preset -> viewModel.inspectCsvContent(csv, preset) },
                onCommitImport = { skipDups ->
                    viewModel.commitCsvImport(skipDups) {
                        showCsvImportDialog = false
                    }
                },
                onDismissPreview = { viewModel.dismissCsvImportPreview() },
                onDismiss = { showCsvImportDialog = false }
            )
        }

        // Security Health & Breach Audit Sheet
        if (showSecurityHealthSheet) {
            SecurityHealthSheet(
                healthReport = uiState.healthReport,
                isCheckingBreaches = uiState.isBreachChecking,
                scanProgress = uiState.breachScanProgress,
                isNetworkOnline = uiState.isNetworkOnline,
                offlineHibpStats = uiState.offlineHibpStats,
                onRunBreachScan = { viewModel.auditAllLoginsBreach() },
                onImportOfflineHibpUri = { uri -> viewModel.importOfflineHibpFromUri(context, uri) },
                onClearOfflineHibpCache = { viewModel.clearOfflineHibpCache() },
                onFilterCategory = { category -> viewModel.setHealthFilter(category) },
                onSelectVulnerableItem = { item -> viewModel.openItemDetail(item) },
                onDismiss = { showSecurityHealthSheet = false }
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                uiState = uiState,
                onToggleBiometrics = { enabled, pw -> viewModel.toggleBiometrics(enabled, pw) },
                onVerifyPasswordForVoice = { pw, cb -> viewModel.verifyMasterPasswordForVoiceEnrollment(pw, cb) },
                onEnrollVoiceSamples = { pw, samples, cb -> viewModel.enrollVoiceBiometrics(pw, samples, cb) },
                onDisableVoiceBiometrics = { pw, cb -> viewModel.disableVoiceBiometrics(pw, cb) },
                onChangeMasterPassword = { oldPw, newPw, cb -> viewModel.changeMasterPassword(oldPw, newPw, cb) },
                onSetAutoLockTimeout = { viewModel.setAutoLockTimeout(it) },
                onToggleBackgroundGuard = { viewModel.toggleBackgroundGuard(it) },
                onRunBackgroundMaintenance = { viewModel.runBackgroundMaintenanceNow() },
                onClearTaskAnalytics = { viewModel.clearTaskAnalytics() },
                onRefreshNfcStatus = { viewModel.refreshNfcStatus() },
                onSetupDuressPin = { pin, decoys -> viewModel.setupDuressPin(pin, decoys) },
                onRemoveDuressPin = { viewModel.removeDuressPin() },
                onOpenSecurityHealth = { showSecurityHealthSheet = true },
                onOpenPrivacyPolicy = { showPrivacyDialog = true },
                onWipeVault = { viewModel.wipeEntireVault() },
                onDismiss = { showSettingsDialog = false }
            )
        }

        // Secure E2EE Share Capsule Dialog
        uiState.activeItemForShare?.let { shareItem ->
            SecureShareDialog(
                item = shareItem,
                onDismiss = { viewModel.dismissShareDialog() },
                onShareFile = { file -> onShareFile(file) },
                onCopyToken = { token ->
                    viewModel.copyToClipboard(context, "Share Token", token, true)
                }
            )
        }

        // Receive Secure E2EE Share Dialog
        if (uiState.showReceiveShareDialog) {
            ReceiveShareDialog(
                onDismiss = { viewModel.dismissReceiveShareDialog() },
                onSaveToVault = { decrypted ->
                    viewModel.saveSharedItemToVault(decrypted)
                },
                onCopyText = { label, text, sensitive ->
                    viewModel.copyToClipboard(context, label, text, sensitive)
                }
            )
        }
    }

    if (showVoiceUnlockDialog && (!uiState.isUnlocked || uiState.isSensitiveContentHidden)) {
        VoiceUnlockDialog(
            lockoutRemainingSeconds = uiState.voiceLockoutRemainingSeconds,
            lastSimilarityPercent = uiState.lastVoiceSimilarityPercent,
            onVerifyVoiceSample = { pcm, cb -> viewModel.unlockWithVoiceBiometrics(pcm, cb) },
            onDismiss = { showVoiceUnlockDialog = false }
        )
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyDialog = false }
        )
    }
}
