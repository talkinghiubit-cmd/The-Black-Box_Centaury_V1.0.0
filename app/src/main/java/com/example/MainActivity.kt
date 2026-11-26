package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.security.BiometricAccessResult
import com.example.security.BiometricAuthCallback
import com.example.security.BiometricManagerWrapper
import com.example.security.BiometricPromptOptions
import com.example.security.BiometricVaultAuthService
import com.example.ui.VaultViewModel
import com.example.ui.VaultViewModelLifecycleObserver
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private val biometricAuthService by lazy { BiometricVaultAuthService.getInstance(applicationContext) }
    private val biometricManager by lazy { BiometricManagerWrapper.getInstance(applicationContext) }

    private val vaultLifecycleObserver by lazy {
        VaultViewModelLifecycleObserver(
            viewModel = viewModel,
            biometricManager = biometricManager,
            onTriggerBiometric = { promptBiometrics(isManualTrigger = false) },
            onCancelBiometric = { biometricAuthService.cancelAuthentication() },
            autoPromptDelayMs = 250L
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Protect screen content against screenshots, screen recording, and recent task switcher previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()

        // Register ViewModel lifecycle observer to manage background privacy concealment and foreground biometric unlock
        lifecycle.addObserver(vaultLifecycleObserver)

        setContent {
            BlackBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BlackBoxPitch
                ) {
                    BlackBoxAppContent(
                        viewModel = viewModel,
                        biometricManager = biometricManager,
                        onTriggerBiometric = { promptBiometrics(isManualTrigger = true) },
                        onShareFile = { file -> shareExportedFile(file) }
                    )
                }
            }
        }

        observeVaultUnlockState()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.recordUserInteraction()
    }

    private fun observeVaultUnlockState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect { state ->
                    if (state.isUnlocked && !state.isSensitiveContentHidden) {
                        vaultLifecycleObserver.resetSessionPromptGuard()
                    } else if (state.isInitialized && !state.isDuressMode && state.isBiometricEnabled) {
                        vaultLifecycleObserver.checkAndTriggerBiometrics()
                    }
                }
            }
        }
    }

    private fun promptBiometrics(isManualTrigger: Boolean = false) {
        if (!biometricAuthService.isBiometricAvailable()) {
            if (isManualTrigger) {
                viewModel.setErrorMessage("Biometric sensor is not available or no credentials enrolled.")
            }
            return
        }

        biometricAuthService.authenticateToAccessVault(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = biometricAuthService.getModalityLabel(),
            description = biometricAuthService.getSensorActionHint(),
            negativeButtonText = getString(R.string.biometric_prompt_negative),
            onResult = { result ->
                when (result) {
                    is BiometricAccessResult.Granted -> {
                        vaultLifecycleObserver.resetSessionPromptGuard()
                        viewModel.unlockWithBiometrics()
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

    private fun shareExportedFile(file: File) {
        try {
            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(applicationContext, authority, file)
            val mimeType = if (file.name.endsWith(".html")) "text/html" else "application/octet-stream"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "The Black-Box Export — ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Save or Export File"))
        } catch (e: Exception) {
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

    if (!uiState.isUnlocked) {
        LockScreen(
            uiState = uiState,
            biometricManager = biometricManager,
            onUnlockPassword = { viewModel.unlockWithPassword(it) },
            onUnlockBiometrics = { viewModel.unlockWithBiometrics() },
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

        // Google Chrome & CSV Password Import Dialog
        if (showCsvImportDialog) {
            CsvImportDialog(
                preview = uiState.csvImportPreview,
                isImporting = uiState.isCsvImporting,
                onInspectCsvUri = { viewModel.inspectCsvFromUri(context, it) },
                onInspectCsvText = { viewModel.inspectCsvContent(it) },
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
