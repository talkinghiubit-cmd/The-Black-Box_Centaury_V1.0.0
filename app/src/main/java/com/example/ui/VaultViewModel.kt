package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.autofill.AutofillVaultSession
import com.example.data.BlackBoxDatabase
import com.example.data.VaultMetadataEntity
import com.example.data.VaultRepository
import com.example.data.VoiceUnlockOutcome
import com.example.model.PasswordStrength
import com.example.model.VaultAttachment
import com.example.model.VaultCategory
import com.example.model.VaultHealthReport
import com.example.model.VaultItem
import com.example.nfc.ArmedNfcCardSession
import com.example.nfc.NfcContactlessHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.NfcPaymentSession
import com.example.security.AttachmentStorageManager
import com.example.security.BackgroundGuardStatus
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricCipherInitResult
import com.example.security.BlackBoxBackgroundScheduler
import com.example.security.BlackBoxSecurity
import com.example.security.BreachCheckResult
import com.example.security.BreachChecker
import com.example.security.BreachScanProgress
import com.example.security.BreachVerificationSource
import com.example.security.CsvImportPreview
import com.example.security.CsvPasswordImporter
import com.example.security.DecryptedSharedItem
import com.example.security.EncryptedShareCapsule
import com.example.security.ExportFileCleaner
import com.example.security.HtmlVaultExporter
import com.example.security.ImportBrowserPreset
import com.example.security.InactivityLockManager
import com.example.security.OfflineHibpStats
import com.example.security.OfflineHibpStore
import com.example.security.PasswordOptions
import com.example.security.SecurePasswordGenerator
import com.example.security.SecureShareManager
import com.example.security.SecurityHealthAnalyzer
import com.example.security.SnapshotManager
import com.example.security.SnapshotPackage
import com.example.security.TaskAnalyticsEngine
import com.example.security.TaskAnalyticsSummary
import com.example.security.TaskLifecycleState
import com.example.security.TotpAuthenticatorService
import com.example.security.TotpSeedValidationResult
import com.example.security.TotpToken
import com.example.security.TotpValidationResult
import com.example.security.TrackedTaskType
import com.example.security.UnlockBackoffManager
import com.example.security.VoiceprintTemplate
import com.example.widget.ConfidentialNotesMemoryCache
import com.example.widget.ConfidentialNotesWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey

data class PasswordGeneratorConfig(
    val length: Int = 20,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val customSymbols: String = PasswordOptions.DEFAULT_SYMBOLS,
    val excludeAmbiguous: Boolean = true,
    val isPassphraseMode: Boolean = false,
    val passphraseWords: Int = 4
)

data class VaultUiState(
    val isInitialized: Boolean = false,
    val isUnlocked: Boolean = false,
    val isLoading: Boolean = true,
    val isBiometricEnabled: Boolean = false,
    val isVoiceBiometricEnabled: Boolean = false,
    val voiceLockoutRemainingSeconds: Int = 0,
    val passwordLockoutRemainingSeconds: Int = 0,
    val failedPasswordAttempts: Int = 0,
    val lastVoiceSimilarityPercent: Int? = null,
    val autoLockMinutes: Int = 5,
    val activePartitionId: Int = 0, // 0 = Primary, 1 = Duress / Decoy
    val isDuressMode: Boolean = false,
    val hasDuressPin: Boolean = false,
    val decryptedItems: List<VaultItem> = emptyList(),
    val filteredItems: List<VaultItem> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: VaultCategory? = null, // null = ALL
    val showFavoritesOnly: Boolean = false,
    val activeHealthFilter: String? = null, // "WEAK", "REUSED", "BREACHED", "EXPIRED", null = all
    val activeItemForEdit: VaultItem? = null,
    val isCreatingNewItem: Boolean = false,
    val activeItemForDetail: VaultItem? = null,
    val generatedPassword: String = "",
    val passwordConfig: PasswordGeneratorConfig = PasswordGeneratorConfig(),
    val generatedPasswordStrength: PasswordStrength = PasswordStrength(0, "", 0),
    val estimatedCrackTime: String = "Millions of years (Military Grade)",
    val snapshotPreview: SnapshotPackage? = null,
    val csvImportPreview: CsvImportPreview? = null,
    val isCsvImporting: Boolean = false,
    val shareFileIntent: File? = null,
    val activeAttachmentPreview: Pair<VaultAttachment, ByteArray>? = null,
    val activeItemForShare: VaultItem? = null,
    val showReceiveShareDialog: Boolean = false,
    val totpSecondsRemaining: Int = 30,
    val isBreachChecking: Boolean = false,
    val breachScanProgress: BreachScanProgress? = null,
    val offlineHibpStats: OfflineHibpStats = OfflineHibpStats(),
    val isNetworkOnline: Boolean = true,
    val isSensitiveContentHidden: Boolean = false,
    val isAppInBackground: Boolean = false,
    val requiresBiometricReauth: Boolean = false,
    val backgroundGuardStatus: BackgroundGuardStatus = BackgroundGuardStatus(
        isEnabled = true,
        isJobScheduled = false,
        lastRunAtMillis = null,
        lastCleanedItemsCount = 0
    ),
    val taskAnalytics: TaskAnalyticsSummary = TaskAnalyticsSummary(),
    val armedNfcSession: ArmedNfcCardSession? = null,
    val nfcRemainingSeconds: Int = 0,
    val nfcHardwareStatus: NfcHardwareStatus = NfcHardwareStatus(),
    val healthReport: VaultHealthReport = VaultHealthReport(100, 0, 0, 0, 0, 0, 0),
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VaultRepository
    private val unlockBackoffManager = UnlockBackoffManager(application)
    private var sessionSecretKey: SecretKey? = null
    private var cachedMasterPassword: String = ""
    private var failedVoiceAttempts: Int = 0
    private var voiceLockoutJob: Job? = null
    private var passwordLockoutJob: Job? = null
    private var totpTickerJob: Job? = null
    private var breachAuditJob: Job? = null
    private var breachAuditStartedAtMillis: Long = 0L
    private var observedLifecycle: Lifecycle? = null
    private var lifecycleEventObserver: LifecycleEventObserver? = null
    private var activeBiometricManager: com.example.security.BiometricManagerWrapper? = null
    private var pendingReauthCallback: (() -> Unit)? = null
    private var hasTriggeredReauthForCurrentResume: Boolean = false
    private var pendingWidgetAction: String? = null
    private var pendingWidgetNoteId: String? = null

    private val inactivityLockManager = InactivityLockManager(viewModelScope, application) {
        lockVault()
    }

    private val _uiState = MutableStateFlow(
        VaultUiState(
            taskAnalytics = TaskAnalyticsEngine.getSummary(application)
        )
    )
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _biometricReauthRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val biometricReauthRequests: SharedFlow<Unit> = _biometricReauthRequests.asSharedFlow()

    init {
        val database = BlackBoxDatabase.getDatabase(application)
        repository = VaultRepository(
            vaultDao = database.vaultDao(),
            passwordDao = database.passwordDao(),
            secureNoteDao = database.secureNoteDao()
        )
        viewModelScope.launch {
            TaskAnalyticsEngine.summaryFlow.collect { summary ->
                _uiState.update { it.copy(taskAnalytics = summary) }
            }
        }
        viewModelScope.launch {
            NfcPaymentSession.sessionFlow.collect { session ->
                _uiState.update {
                    it.copy(
                        armedNfcSession = session,
                        nfcRemainingSeconds = session?.remainingSeconds() ?: 0
                    )
                }
            }
        }
        generatePassword()
        checkInitialization()
        refreshOfflineHibpStatus()
        startTotpTicker()
    }

    private fun startTotpTicker() {
        totpTickerJob?.cancel()
        TaskAnalyticsEngine.recordTaskStarted(
            context = getApplication(),
            taskType = TrackedTaskType.TOTP_TICKER,
            taskName = "TOTP 2FA Live Ticker"
        )
        totpTickerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val trackedArmed = _uiState.value.armedNfcSession
                if (trackedArmed != null && now >= trackedArmed.expiresAtMillis) {
                    NfcPaymentSession.disarm(
                        context = getApplication(),
                        reason = "Contactless NFC window expired (60s)",
                        expired = true
                    )
                }
                val activeNfc = NfcPaymentSession.getActiveSession(now)
                val remaining = TotpAuthenticatorService.getRemainingSeconds()
                _uiState.update {
                    it.copy(
                        totpSecondsRemaining = remaining,
                        armedNfcSession = activeNfc,
                        nfcRemainingSeconds = activeNfc?.remainingSeconds(now) ?: 0
                    )
                }
                delay(1000)
            }
        }
    }

    fun validateTotpInput(input: String): TotpSeedValidationResult {
        return TotpAuthenticatorService.validateSeedInput(input)
    }

    fun generateTotpToken(storedSeedOrUri: String, timeMillis: Long = System.currentTimeMillis()): TotpToken? {
        return TotpAuthenticatorService.generateToken(storedSeedOrUri, timeMillis)
    }

    fun verifyTotpCode(
        storedSeedOrUri: String,
        candidateCode: String,
        allowedDriftSteps: Int = 1
    ): TotpValidationResult {
        return TotpAuthenticatorService.verifyCode(
            storedSeedOrUri = storedSeedOrUri,
            candidateCode = candidateCode,
            allowedDriftSteps = allowedDriftSteps
        )
    }

    fun generateRandomTotpSecret(): String {
        return TotpAuthenticatorService.generateRandomSecret()
    }

    private fun checkInitialization() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ExportFileCleaner.cleanupIdleExports(getApplication())
                BlackBoxBackgroundScheduler.ensureScheduledIfEnabled(getApplication())
            }
            val metadata = repository.getMetadata()
            val initialized = metadata != null && metadata.isVaultInitialized
            val timeout = metadata?.autoLockMinutes ?: 5
            val backoffState = unlockBackoffManager.getState()
            val bgStatus = withContext(Dispatchers.IO) {
                BlackBoxBackgroundScheduler.getStatus(getApplication())
            }
            val nfcStatus = withContext(Dispatchers.IO) {
                NfcContactlessHelper.queryHardwareStatus(getApplication())
            }
            inactivityLockManager.setTimeoutMinutes(timeout)
            AutofillVaultSession.updateMaxIdleMinutes(timeout)
            _uiState.update {
                it.copy(
                    isInitialized = initialized,
                    isBiometricEnabled = metadata?.isBiometricEnabled == true,
                    isVoiceBiometricEnabled = metadata?.isVoiceBiometricEnabled == true,
                    hasDuressPin = metadata?.hasDuressPin == true,
                    autoLockMinutes = timeout,
                    failedPasswordAttempts = backoffState.failedAttempts,
                    passwordLockoutRemainingSeconds = backoffState.lockoutRemainingSeconds,
                    backgroundGuardStatus = bgStatus,
                    nfcHardwareStatus = nfcStatus,
                    isLoading = false
                )
            }
            if (backoffState.isLockedOut) {
                startPasswordLockoutTicker(backoffState.lockoutRemainingSeconds)
            }
            if (observedLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
                triggerBiometricReauthIfRequired(
                    biometricManager = activeBiometricManager,
                    onRequireBiometricReauth = pendingReauthCallback ?: {}
                )
            }
        }
    }

    fun initializeVault(masterPassword: String, enableBiometrics: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val key = repository.initializeVault(masterPassword, enableBiometrics)
                val persistedBioEnabled = repository.getMetadata()?.isBiometricEnabled == true
                unlockBackoffManager.reset()
                passwordLockoutJob?.cancel()
                sessionSecretKey = key
                cachedMasterPassword = masterPassword
                inactivityLockManager.startMonitoring()
                val items = repository.getAllDecryptedItems(key, partitionId = 0)
                syncUnlockedSessions(items, isDuress = false)
                val health = computeHealthReport(items)
                _uiState.update {
                    it.copy(
                        isInitialized = true,
                        isUnlocked = true,
                        isSensitiveContentHidden = false,
                        isAppInBackground = false,
                        requiresBiometricReauth = false,
                        activePartitionId = 0,
                        isDuressMode = false,
                        isBiometricEnabled = persistedBioEnabled,
                        failedPasswordAttempts = 0,
                        passwordLockoutRemainingSeconds = 0,
                        decryptedItems = items,
                        healthReport = health,
                        isLoading = false,
                        infoMessage = "Vault initialized with AES-256-GCM encryption"
                    )
                }
                applyFilter()
                executePendingWidgetActionIfReady()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Initialization error: ${e.message}"
                    )
                }
            }
        }
    }

    fun unlockWithPassword(masterPassword: String) {
        val currentBackoff = unlockBackoffManager.getState()
        if (currentBackoff.isLockedOut) {
            startPasswordLockoutTicker(currentBackoff.lockoutRemainingSeconds)
            _uiState.update {
                it.copy(
                    failedPasswordAttempts = currentBackoff.failedAttempts,
                    passwordLockoutRemainingSeconds = currentBackoff.lockoutRemainingSeconds,
                    errorMessage = "Too many failed attempts. Wait ${currentBackoff.lockoutRemainingSeconds}s before retrying."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.unlockWithPasswordOrDuress(masterPassword)
            if (result != null) {
                unlockBackoffManager.reset()
                passwordLockoutJob?.cancel()
                sessionSecretKey = result.secretKey
                cachedMasterPassword = masterPassword
                inactivityLockManager.startMonitoring()
                val items = repository.getAllDecryptedItems(result.secretKey, result.partitionId)
                syncUnlockedSessions(items, isDuress = result.isDuress)
                val health = computeHealthReport(items)
                _uiState.update {
                    it.copy(
                        isUnlocked = true,
                        isSensitiveContentHidden = false,
                        isAppInBackground = false,
                        requiresBiometricReauth = false,
                        activePartitionId = result.partitionId,
                        isDuressMode = result.isDuress,
                        failedPasswordAttempts = 0,
                        passwordLockoutRemainingSeconds = 0,
                        decryptedItems = items,
                        healthReport = health,
                        isLoading = false,
                        errorMessage = null,
                        infoMessage = if (result.isDuress) null else "Vault unlocked"
                    )
                }
                applyFilter()
                executePendingWidgetActionIfReady()
            } else {
                val nextBackoff = unlockBackoffManager.recordFailure()
                if (nextBackoff.isLockedOut) {
                    startPasswordLockoutTicker(nextBackoff.lockoutRemainingSeconds)
                }
                val attemptsLeft = (UnlockBackoffManager.LOCKOUT_THRESHOLD - nextBackoff.failedAttempts).coerceAtLeast(0)
                val msg = when {
                    nextBackoff.isLockedOut ->
                        "Too many failed attempts (${nextBackoff.failedAttempts}). Vault unlock paused for ${nextBackoff.lockoutRemainingSeconds}s."
                    nextBackoff.failedAttempts >= 3 ->
                        "Invalid Master Password or PIN. $attemptsLeft attempt(s) before temporary cooldown."
                    else ->
                        "Invalid Master Password or PIN."
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        failedPasswordAttempts = nextBackoff.failedAttempts,
                        passwordLockoutRemainingSeconds = nextBackoff.lockoutRemainingSeconds,
                        errorMessage = msg
                    )
                }
            }
        }
    }

    private fun startPasswordLockoutTicker(initialSeconds: Int) {
        if (initialSeconds <= 0) return
        passwordLockoutJob?.cancel()
        passwordLockoutJob = viewModelScope.launch {
            while (true) {
                val remaining = unlockBackoffManager.getState().lockoutRemainingSeconds
                _uiState.update { it.copy(passwordLockoutRemainingSeconds = remaining) }
                if (remaining <= 0) break
                delay(1000L)
            }
        }
    }

    suspend fun prepareBiometricUnlockCipher(): BiometricCipherInitResult {
        val result = repository.prepareBiometricUnlockCipher()
        if (result is BiometricCipherInitResult.Invalidated) {
            _uiState.update {
                it.copy(
                    isBiometricEnabled = false,
                    requiresBiometricReauth = false,
                    errorMessage = getApplication<Application>().getString(R.string.biometric_invalidated_error)
                )
            }
        }
        return result
    }

    fun unlockWithBiometrics(authenticatedCipher: Cipher? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.unlockWithBiometrics(authenticatedCipher)
            if (result != null) {
                val (key, masterPassword) = result
                unlockBackoffManager.reset()
                passwordLockoutJob?.cancel()
                sessionSecretKey = key
                cachedMasterPassword = masterPassword
                inactivityLockManager.startMonitoring()
                val items = repository.getAllDecryptedItems(key, partitionId = 0)
                syncUnlockedSessions(items, isDuress = false)
                val health = computeHealthReport(items)
                _uiState.update {
                    it.copy(
                        isUnlocked = true,
                        isSensitiveContentHidden = false,
                        isAppInBackground = false,
                        requiresBiometricReauth = false,
                        activePartitionId = 0,
                        isDuressMode = false,
                        failedPasswordAttempts = 0,
                        passwordLockoutRemainingSeconds = 0,
                        decryptedItems = items,
                        healthReport = health,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                applyFilter()
                executePendingWidgetActionIfReady()
            } else {
                val persistedBioEnabled = repository.getMetadata()?.isBiometricEnabled == true
                val message = if (!persistedBioEnabled) {
                    getApplication<Application>().getString(R.string.biometric_invalidated_error)
                } else {
                    "Biometric credentials unverified. Use Master Password."
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isBiometricEnabled = persistedBioEnabled,
                        requiresBiometricReauth = false,
                        errorMessage = message
                    )
                }
            }
        }
    }

    fun lockVault() {
        inactivityLockManager.stopMonitoring()
        if (breachAuditJob?.isActive == true) {
            breachAuditJob?.cancel()
            breachAuditJob = null
            val elapsed = if (breachAuditStartedAtMillis > 0L) System.currentTimeMillis() - breachAuditStartedAtMillis else null
            TaskAnalyticsEngine.recordTaskStopped(
                context = getApplication(),
                taskType = TrackedTaskType.BREACH_AUDIT_SCAN,
                taskName = "HIBP Breach Audit Scan",
                stopReasonCode = TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED,
                stopReasonLabel = "Breach scan stopped when vault locked",
                state = TaskLifecycleState.STOPPED,
                durationMs = elapsed
            )
        }
        sessionSecretKey = null
        cachedMasterPassword = ""
        NfcPaymentSession.disarm(
            context = getApplication(),
            reason = "Vault locked — NFC contactless session disarmed"
        )
        AutofillVaultSession.clear()
        ConfidentialNotesMemoryCache.clear()
        ConfidentialNotesWidgetProvider.refreshAllWidgets(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            ExportFileCleaner.cleanupIdleExports(getApplication())
        }
        _uiState.update {
            it.copy(
                isUnlocked = false,
                isSensitiveContentHidden = true,
                activePartitionId = 0,
                isDuressMode = false,
                decryptedItems = emptyList(),
                filteredItems = emptyList(),
                activeItemForDetail = null,
                activeItemForEdit = null,
                activeItemForShare = null,
                showReceiveShareDialog = false,
                activeAttachmentPreview = null,
                snapshotPreview = null,
                csvImportPreview = null,
                isCsvImporting = false,
                isCreatingNewItem = false,
                isBreachChecking = false,
                breachScanProgress = null,
                armedNfcSession = null,
                nfcRemainingSeconds = 0,
                infoMessage = "Vault locked. Sensitive keys purged from memory."
            )
        }
    }

    /**
     * Immediately conceals sensitive vault data and dismisses active editing / inspection sheets.
     * Invoked when moving into background (e.g. onPause) to prevent recent task preview leaks.
     */
    fun hideSensitiveContent() {
        _uiState.update {
            it.copy(
                isSensitiveContentHidden = true,
                activeItemForDetail = null,
                activeItemForEdit = null,
                activeItemForShare = null,
                showReceiveShareDialog = false,
                activeAttachmentPreview = null,
                isCreatingNewItem = false
            )
        }
    }

    /**
     * Unconceals sensitive vault data if vault is unlocked.
     */
    fun showSensitiveContent() {
        _uiState.update {
            it.copy(isSensitiveContentHidden = false)
        }
        executePendingWidgetActionIfReady()
    }

    /**
     * Invoked when the application transitions to the background (e.g. ON_STOP).
     * Purges session secrets, locks the vault, and marks biometric re-authentication
     * as required when biometrics are enabled.
     */
    fun onAppBackgrounded() {
        val current = _uiState.value
        val shouldRequireBioReauth = current.isInitialized && !current.isDuressMode && current.isBiometricEnabled
        hasTriggeredReauthForCurrentResume = false
        lockVault()
        _uiState.update {
            it.copy(
                isAppInBackground = true,
                isSensitiveContentHidden = true,
                requiresBiometricReauth = shouldRequireBioReauth,
                infoMessage = if (shouldRequireBioReauth) {
                    "Vault locked in background. Biometric re-authentication required."
                } else {
                    it.infoMessage
                }
            )
        }
    }

    /**
     * Invoked when the application returns to foreground (e.g. ON_START / ON_RESUME).
     */
    fun onAppForegrounded() {
        _uiState.update {
            it.copy(isAppInBackground = false)
        }
    }

    /**
     * Monitors Android [Lifecycle] events directly from the ViewModel.
     *
     * - On `ON_PAUSE`: conceals sensitive vault UI and cancels any active biometric prompt.
     * - On `ON_STOP`: triggers a full vault lock state in the background and flags biometric
     *   re-authentication as required upon returning to the foreground.
     * - On `ON_START` / `ON_RESUME`: restores foreground state and triggers biometric
     *   re-authentication when required.
     * - On `ON_DESTROY`: detaches the lifecycle observer cleanly.
     */
    fun monitorAppLifecycle(
        lifecycle: Lifecycle,
        biometricManager: com.example.security.BiometricManagerWrapper? = null,
        onRequireBiometricReauth: () -> Unit = {},
        onCancelBiometricPrompt: () -> Unit = {}
    ): LifecycleEventObserver {
        detachLifecycleMonitor()
        activeBiometricManager = biometricManager
        pendingReauthCallback = onRequireBiometricReauth

        val observer = LifecycleEventObserver { _, event ->
            handleAppLifecycleEvent(
                event = event,
                biometricManager = biometricManager,
                onRequireBiometricReauth = onRequireBiometricReauth,
                onCancelBiometricPrompt = onCancelBiometricPrompt
            )
        }
        observedLifecycle = lifecycle
        lifecycleEventObserver = observer
        lifecycle.addObserver(observer)
        return observer
    }

    fun handleAppLifecycleEvent(
        event: Lifecycle.Event,
        biometricManager: com.example.security.BiometricManagerWrapper? = activeBiometricManager,
        onRequireBiometricReauth: () -> Unit = pendingReauthCallback ?: {},
        onCancelBiometricPrompt: () -> Unit = {}
    ) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                onCancelBiometricPrompt()
                biometricManager?.cancelAuthentication()
                if (_uiState.value.isUnlocked) {
                    hideSensitiveContent()
                }
            }
            Lifecycle.Event.ON_STOP -> {
                onCancelBiometricPrompt()
                biometricManager?.cancelAuthentication()
                onAppBackgrounded()
                TaskAnalyticsEngine.recordTaskStopped(
                    context = getApplication(),
                    taskType = TrackedTaskType.APP_PROCESS_TASK,
                    taskName = "UI Foreground Task",
                    stopReasonCode = TaskAnalyticsEngine.STOP_REASON_LIFECYCLE_BACKGROUND,
                    stopReasonLabel = "Activity ON_STOP moved app to background",
                    state = TaskLifecycleState.STOPPED
                )
            }
            Lifecycle.Event.ON_START -> {
                onAppForegrounded()
                TaskAnalyticsEngine.recordTaskStarted(
                    context = getApplication(),
                    taskType = TrackedTaskType.APP_PROCESS_TASK,
                    taskName = "UI Foreground Task"
                )
            }
            Lifecycle.Event.ON_RESUME -> {
                onAppForegrounded()
                triggerBiometricReauthIfRequired(
                    biometricManager = biometricManager,
                    onRequireBiometricReauth = onRequireBiometricReauth
                )
            }
            Lifecycle.Event.ON_DESTROY -> {
                onCancelBiometricPrompt()
                biometricManager?.cancelAuthentication()
                detachLifecycleMonitor()
            }
            else -> Unit
        }
    }

    fun resetBiometricReauthGuard() {
        hasTriggeredReauthForCurrentResume = false
    }

    fun triggerBiometricReauthIfRequired(
        biometricManager: com.example.security.BiometricManagerWrapper? = activeBiometricManager,
        onRequireBiometricReauth: () -> Unit = pendingReauthCallback ?: {}
    ): Boolean {
        val state = _uiState.value
        val needsUnlockOrReauth = !state.isUnlocked || state.isSensitiveContentHidden || state.requiresBiometricReauth
        if (!state.isInitialized || state.isDuressMode || !state.isBiometricEnabled || !needsUnlockOrReauth) {
            return false
        }
        val sensorReady = biometricManager?.isStrongBiometricReady() ?: true
        val promptActive = biometricManager?.isPromptActive() ?: false
        if (!sensorReady || promptActive || hasTriggeredReauthForCurrentResume) {
            return false
        }
        hasTriggeredReauthForCurrentResume = true
        _biometricReauthRequests.tryEmit(Unit)
        onRequireBiometricReauth()
        return true
    }

    private fun detachLifecycleMonitor() {
        val observer = lifecycleEventObserver
        val lifecycle = observedLifecycle
        if (observer != null && lifecycle != null) {
            lifecycle.removeObserver(observer)
        }
        lifecycleEventObserver = null
        observedLifecycle = null
    }

    override fun onCleared() {
        detachLifecycleMonitor()
        inactivityLockManager.stopMonitoring("ViewModel cleared — inactivity monitor stopped")
        if (totpTickerJob?.isActive == true) {
            totpTickerJob?.cancel()
            TaskAnalyticsEngine.recordTaskStopped(
                context = getApplication(),
                taskType = TrackedTaskType.TOTP_TICKER,
                taskName = "TOTP 2FA Live Ticker",
                stopReasonCode = TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED,
                stopReasonLabel = "ViewModel cleared — TOTP 2FA ticker stopped",
                state = TaskLifecycleState.STOPPED
            )
        }
        if (breachAuditJob?.isActive == true) {
            breachAuditJob?.cancel()
            TaskAnalyticsEngine.recordTaskStopped(
                context = getApplication(),
                taskType = TrackedTaskType.BREACH_AUDIT_SCAN,
                taskName = "HIBP Breach Audit Scan",
                stopReasonCode = TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED,
                stopReasonLabel = "ViewModel cleared during breach scan",
                state = TaskLifecycleState.STOPPED
            )
        }
        TaskAnalyticsEngine.recordTaskStopped(
            context = getApplication(),
            taskType = TrackedTaskType.APP_PROCESS_TASK,
            taskName = "Main UI ViewModel",
            stopReasonCode = TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED,
            stopReasonLabel = "VaultViewModel destroyed (onCleared)",
            state = TaskLifecycleState.STOPPED
        )
        voiceLockoutJob?.cancel()
        passwordLockoutJob?.cancel()
        sessionSecretKey = null
        cachedMasterPassword = ""
        NfcPaymentSession.disarm(
            context = getApplication(),
            reason = "VaultViewModel destroyed — NFC contactless session disarmed"
        )
        AutofillVaultSession.clear()
        ConfidentialNotesMemoryCache.clear()
        ConfidentialNotesWidgetProvider.refreshAllWidgets(getApplication())
        super.onCleared()
    }

    /**
     * Creates a lifecycle observer configured for this ViewModel.
     */
    fun createLifecycleObserver(
        biometricManager: com.example.security.BiometricManagerWrapper? = null,
        onTriggerBiometric: () -> Unit = {},
        onCancelBiometric: () -> Unit = {}
    ): VaultViewModelLifecycleObserver {
        return VaultViewModelLifecycleObserver(
            viewModel = this,
            biometricManager = biometricManager,
            onTriggerBiometric = onTriggerBiometric,
            onCancelBiometric = onCancelBiometric,
            coroutineScope = viewModelScope
        )
    }

    fun recordUserInteraction() {
        inactivityLockManager.recordUserInteraction()
        AutofillVaultSession.touchSession()
    }

    fun setAutoLockTimeout(minutes: Int) {
        viewModelScope.launch {
            repository.updateAutoLockMinutes(minutes)
            inactivityLockManager.setTimeoutMinutes(minutes)
            AutofillVaultSession.updateMaxIdleMinutes(minutes)
            _uiState.update {
                it.copy(
                    autoLockMinutes = minutes,
                    infoMessage = "Auto-lock timeout set to $minutes min"
                )
            }
        }
    }

    // =========================================================================
    // Duress PIN & Hidden Partition Management
    // =========================================================================

    fun setupDuressPin(duressPin: String, generateDecoys: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val decoyKey = repository.setupDuressPin(duressPin)
                if (generateDecoys) {
                    repository.populateDecoyVault(decoyKey)
                }
                _uiState.update {
                    it.copy(
                        hasDuressPin = true,
                        isLoading = false,
                        infoMessage = "Duress PIN configured. Hidden decoy partition is active."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to configure Duress PIN: ${e.message}"
                    )
                }
            }
        }
    }

    fun removeDuressPin() {
        viewModelScope.launch {
            repository.removeDuressPin()
            _uiState.update {
                it.copy(
                    hasDuressPin = false,
                    infoMessage = "Duress PIN and decoy partition cleared."
                )
            }
        }
    }

    // =========================================================================
    // HaveIBeenPwned k-Anonymity & Health Scoring
    // =========================================================================

    fun refreshOfflineHibpStatus() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val stats = OfflineHibpStore.getStats(ctx)
            val online = OfflineHibpStore.isNetworkAvailable(ctx)
            _uiState.update {
                it.copy(
                    offlineHibpStats = stats,
                    isNetworkOnline = online
                )
            }
        }
    }

    fun importOfflineHibpFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBreachChecking = true, errorMessage = null) }
            val result = OfflineHibpStore.importFromUri(context, uri)
            val stats = withContext(Dispatchers.IO) { OfflineHibpStore.getStats(context) }
            val online = withContext(Dispatchers.IO) { OfflineHibpStore.isNetworkAvailable(context) }
            result.fold(
                onSuccess = { imported ->
                    _uiState.update {
                        it.copy(
                            isBreachChecking = false,
                            offlineHibpStats = stats,
                            isNetworkOnline = online,
                            infoMessage = "Loaded ${imported.importedEntries} offline HIBP hashes across ${imported.updatedPrefixes} prefix ranges."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBreachChecking = false,
                            offlineHibpStats = stats,
                            isNetworkOnline = online,
                            errorMessage = error.message ?: "Failed to import offline HIBP dataset."
                        )
                    }
                }
            )
        }
    }

    fun clearOfflineHibpCache() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            OfflineHibpStore.clearCache(ctx)
            val stats = OfflineHibpStore.getStats(ctx)
            _uiState.update {
                it.copy(
                    offlineHibpStats = stats,
                    infoMessage = "Cleared on-device HIBP prefix cache."
                )
            }
        }
    }

    fun auditAllLoginsBreach() {
        val key = sessionSecretKey ?: return
        if (_uiState.value.isBreachChecking) return
        val ctx = getApplication<Application>()
        val startedAt = System.currentTimeMillis()
        breachAuditStartedAtMillis = startedAt
        breachAuditJob = viewModelScope.launch {
            val loginTargets = _uiState.value.decryptedItems.filter {
                it.category == VaultCategory.LOGIN && it.password.isNotEmpty()
            }
            if (loginTargets.isEmpty()) {
                _uiState.update {
                    it.copy(infoMessage = "No stored login passwords to audit.")
                }
                return@launch
            }

            TaskAnalyticsEngine.recordTaskStarted(
                context = ctx,
                taskType = TrackedTaskType.BREACH_AUDIT_SCAN,
                taskName = "HIBP Breach Audit Scan",
                timestampMillis = startedAt
            )

            val onlineNow = withContext(Dispatchers.IO) { OfflineHibpStore.isNetworkAvailable(ctx) }
            _uiState.update {
                it.copy(
                    isBreachChecking = true,
                    isNetworkOnline = onlineNow,
                    breachScanProgress = BreachScanProgress(0, loginTargets.size, 0, 0, 0),
                    errorMessage = null,
                    infoMessage = if (onlineNow) {
                        "Scanning ${loginTargets.size} credential(s) via k-Anonymity..."
                    } else {
                        "Offline mode: Checking ${loginTargets.size} credential(s) against local HIBP cache..."
                    }
                )
            }

            try {
                val summary = BreachChecker.auditPasswordsOfflineFirst(
                    context = ctx,
                    items = loginTargets,
                    isOnline = onlineNow,
                    onProgress = { progress ->
                        _uiState.update { it.copy(breachScanProgress = progress) }
                    }
                )

                summary.resultsByRecordId.forEach { (recordId, count) ->
                    repository.updateBreachCount(recordId, count)
                }

                val activeKey = sessionSecretKey ?: return@launch
                val updated = repository.getAllDecryptedItems(activeKey, _uiState.value.activePartitionId)
                val health = computeHealthReport(updated)
                val updatedStats = withContext(Dispatchers.IO) { OfflineHibpStore.getStats(ctx) }

                val allFailed = summary.errorCount > 0 && summary.checkedCount == 0
                val partialErrors = summary.errorCount > 0 && summary.checkedCount > 0
                val allOfflineUnverified = summary.wasOfflineMode && summary.checkedCount == 0 && summary.offlineUnverifiedCount > 0
                val elapsed = System.currentTimeMillis() - startedAt

                if (allFailed) {
                    TaskAnalyticsEngine.recordTaskFailed(
                        context = ctx,
                        taskType = TrackedTaskType.BREACH_AUDIT_SCAN,
                        taskName = "HIBP Breach Audit Scan",
                        reasonLabel = "Unable to reach HIBP and no cached prefixes",
                        durationMs = elapsed
                    )
                } else {
                    TaskAnalyticsEngine.recordTaskCompleted(
                        context = ctx,
                        taskType = TrackedTaskType.BREACH_AUDIT_SCAN,
                        taskName = "HIBP Breach Audit Scan",
                        durationMs = elapsed,
                        note = "${summary.checkedCount} audited"
                    )
                }

                _uiState.update {
                    it.copy(
                        isBreachChecking = false,
                        breachScanProgress = null,
                        offlineHibpStats = updatedStats,
                        isNetworkOnline = onlineNow,
                        decryptedItems = updated,
                        healthReport = health,
                        activeItemForDetail = it.activeItemForDetail?.let { detail ->
                            updated.firstOrNull { u -> u.id == detail.id }
                        },
                        errorMessage = when {
                            allFailed -> "Unable to reach HaveIBeenPwned and no matching prefixes were cached locally."
                            else -> null
                        },
                        infoMessage = when {
                            allFailed -> null
                            allOfflineUnverified ->
                                "Offline mode: ${summary.offlineUnverifiedCount} login(s) not yet cached locally. Connect or load an offline HIBP dataset."
                            summary.wasOfflineMode ->
                                buildString {
                                    append("Offline scan: Checked ${summary.checkedCount} from local cache")
                                    if (summary.breachedCount > 0) append(" (${summary.breachedCount} breached)")
                                    if (summary.offlineUnverifiedCount > 0) append(", ${summary.offlineUnverifiedCount} uncached")
                                    append(".")
                                }
                            partialErrors ->
                                "Partial scan: ${summary.checkedCount} checked (${summary.breachedCount} breached), ${summary.errorCount} network error(s)."
                            summary.breachedCount > 0 ->
                                "Scan complete: ${summary.breachedCount} compromised password(s) found (${summary.uniquePrefixQueries} online, ${summary.cacheHits} cached)."
                            else ->
                                "Scan complete: All ${summary.checkedCount} password(s) clean (${summary.uniquePrefixQueries} online, ${summary.cacheHits} cached)."
                        }
                    )
                }
                applyFilter()
            } catch (_: CancellationException) {
                // Stop reason already recorded if cancelled on lock/destroy
            } finally {
                breachAuditJob = null
                _uiState.update {
                    it.copy(isBreachChecking = false, breachScanProgress = null)
                }
            }
        }
    }

    fun checkSingleItemBreach(item: VaultItem) {
        val key = sessionSecretKey ?: return
        if (item.password.isEmpty()) {
            _uiState.update { it.copy(infoMessage = "No password stored on this item to verify.") }
            return
        }
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val onlineNow = withContext(Dispatchers.IO) { OfflineHibpStore.isNetworkAvailable(ctx) }
            _uiState.update {
                it.copy(isBreachChecking = true, isNetworkOnline = onlineNow, errorMessage = null)
            }
            when (val result = BreachChecker.checkPasswordOfflineFirst(ctx, item.password, isOnline = onlineNow)) {
                is BreachCheckResult.Breached -> {
                    repository.updateBreachCount(item.id, result.count)
                    val activeKey = sessionSecretKey ?: return@launch
                    val updated = repository.getAllDecryptedItems(activeKey, _uiState.value.activePartitionId)
                    val health = computeHealthReport(updated)
                    val stats = withContext(Dispatchers.IO) { OfflineHibpStore.getStats(ctx) }
                    val sourceLabel = if (result.source == BreachVerificationSource.OFFLINE_CACHE) "offline cache" else "k-Anonymity"
                    _uiState.update {
                        it.copy(
                            isBreachChecking = false,
                            offlineHibpStats = stats,
                            decryptedItems = updated,
                            healthReport = health,
                            activeItemForDetail = updated.firstOrNull { u -> u.id == item.id },
                            infoMessage = "Password breached ${result.count} times in public leaks! (Prefix ${result.hashPrefix}, $sourceLabel)"
                        )
                    }
                    applyFilter()
                }
                is BreachCheckResult.Clean -> {
                    repository.updateBreachCount(item.id, 0)
                    val activeKey = sessionSecretKey ?: return@launch
                    val updated = repository.getAllDecryptedItems(activeKey, _uiState.value.activePartitionId)
                    val health = computeHealthReport(updated)
                    val stats = withContext(Dispatchers.IO) { OfflineHibpStore.getStats(ctx) }
                    val sourceLabel = if (result.source == BreachVerificationSource.OFFLINE_CACHE) "offline cache" else "k-Anonymity"
                    _uiState.update {
                        it.copy(
                            isBreachChecking = false,
                            offlineHibpStats = stats,
                            decryptedItems = updated,
                            healthReport = health,
                            activeItemForDetail = updated.firstOrNull { u -> u.id == item.id },
                            infoMessage = "Verified clean via $sourceLabel (Prefix ${result.hashPrefix}): 0 leaks found."
                        )
                    }
                    applyFilter()
                }
                is BreachCheckResult.OfflineUnverified -> {
                    _uiState.update {
                        it.copy(
                            isBreachChecking = false,
                            infoMessage = "Offline: Prefix ${result.hashPrefix} is not cached locally yet. Connect to sync or load an offline HIBP file."
                        )
                    }
                }
                is BreachCheckResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isBreachChecking = false,
                            errorMessage = "Network error connecting to HaveIBeenPwned (${result.reason})."
                        )
                    }
                }
            }
        }
    }

    private fun computeHealthReport(items: List<VaultItem>): VaultHealthReport {
        return SecurityHealthAnalyzer.analyze(items)
    }

    // =========================================================================
    // Attachment Management
    // =========================================================================

    fun addAttachmentFromUri(context: Context, recordId: String, uri: Uri) {
        val key = sessionSecretKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var fileName = "attachment_${System.currentTimeMillis()}"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    _uiState.update { it.copy(errorMessage = "Unable to read selected file.") }
                    return@launch
                }
                val partitionId = _uiState.value.activePartitionId
                val relPath = AttachmentStorageManager.saveEncryptedFile(getApplication(), bytes, key)
                val attachment = VaultAttachment(
                    id = UUID.randomUUID().toString(),
                    recordId = recordId,
                    partitionId = partitionId,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = bytes.size.toLong(),
                    encryptedStoragePath = relPath
                )
                repository.saveAttachment(attachment)
                val updated = repository.getAllDecryptedItems(key, partitionId)
                _uiState.update {
                    it.copy(
                        decryptedItems = updated,
                        activeItemForDetail = updated.firstOrNull { item -> item.id == recordId },
                        infoMessage = "Encrypted file attached: $fileName"
                    )
                }
                applyFilter()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to attach file: ${e.message}") }
            }
        }
    }

    fun addAttachment(recordId: String, fileName: String, mimeType: String, bytes: ByteArray) {
        val key = sessionSecretKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val partitionId = _uiState.value.activePartitionId
            val relPath = AttachmentStorageManager.saveEncryptedFile(getApplication(), bytes, key)
            val attachment = VaultAttachment(
                id = UUID.randomUUID().toString(),
                recordId = recordId,
                partitionId = partitionId,
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = bytes.size.toLong(),
                encryptedStoragePath = relPath
            )
            repository.saveAttachment(attachment)
            val updated = repository.getAllDecryptedItems(key, partitionId)
            _uiState.update {
                it.copy(
                    decryptedItems = updated,
                    activeItemForDetail = updated.firstOrNull { item -> item.id == recordId },
                    infoMessage = "Encrypted file attached: $fileName"
                )
            }
            applyFilter()
        }
    }

    fun deleteAttachment(attachment: VaultAttachment) {
        val key = sessionSecretKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            AttachmentStorageManager.deleteFile(getApplication(), attachment.encryptedStoragePath)
            repository.deleteAttachment(attachment.id)
            val updated = repository.getAllDecryptedItems(key, _uiState.value.activePartitionId)
            _uiState.update {
                it.copy(
                    decryptedItems = updated,
                    activeItemForDetail = updated.firstOrNull { item -> item.id == attachment.recordId },
                    infoMessage = "Attachment removed"
                )
            }
            applyFilter()
        }
    }

    fun previewAttachment(attachment: VaultAttachment) {
        val key = sessionSecretKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = AttachmentStorageManager.readDecryptedFile(getApplication(), attachment.encryptedStoragePath, key)
            if (bytes != null) {
                _uiState.update { it.copy(activeAttachmentPreview = Pair(attachment, bytes)) }
            } else {
                _uiState.update { it.copy(errorMessage = "Could not decrypt attachment") }
            }
        }
    }

    fun dismissAttachmentPreview() {
        _uiState.update { it.copy(activeAttachmentPreview = null) }
    }

    // =========================================================================
    // Filtering & Search
    // =========================================================================

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    fun setCategoryFilter(category: VaultCategory?) {
        _uiState.update { it.copy(selectedCategory = category, activeHealthFilter = null) }
        applyFilter()
    }

    fun toggleFavoritesFilter() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly, activeHealthFilter = null) }
        applyFilter()
    }

    fun setHealthFilter(healthFilter: String?) {
        _uiState.update { it.copy(activeHealthFilter = healthFilter, selectedCategory = null, showFavoritesOnly = false) }
        applyFilter()
    }

    private fun applyFilter() {
        _uiState.update { current ->
            if (sessionSecretKey == null) {
                return@update current.copy(filteredItems = emptyList())
            }
            val query = current.searchQuery.trim()
            val category = current.selectedCategory
            val favOnly = current.showFavoritesOnly
            val healthFilter = current.activeHealthFilter

            val filtered = current.decryptedItems
                .filter { item ->
                    val matchesCategory = category == null || item.category == category
                    val matchesFav = !favOnly || item.isFavorite
                    val matchesHealth = when (healthFilter) {
                        "WEAK" -> item.category == VaultCategory.LOGIN && BlackBoxSecurity.evaluatePasswordStrength(item.password).score <= 1
                        "REUSED" -> current.healthReport.reusedItems.any { it.id == item.id }
                        "BREACHED" -> item.pwnedBreachCount > 0
                        "EXPIRED" -> current.healthReport.expiredItems.any { it.id == item.id }
                        "UNCHECKED" -> item.category == VaultCategory.LOGIN && item.password.isNotEmpty() && item.pwnedBreachCount < 0
                        else -> true
                    }
                    val matchesQuery = if (query.isEmpty()) true else {
                        item.title.contains(query, ignoreCase = true) ||
                            item.username.contains(query, ignoreCase = true) ||
                            item.url.contains(query, ignoreCase = true) ||
                            item.notes.contains(query, ignoreCase = true) ||
                            item.keyType.contains(query, ignoreCase = true) ||
                            item.cardHolder.contains(query, ignoreCase = true)
                    }
                    matchesCategory && matchesFav && matchesHealth && matchesQuery
                }
                .sortedWith(compareByDescending<VaultItem> { it.isFavorite }.thenByDescending { it.updatedAt })

            current.copy(filteredItems = filtered)
        }
    }

    // =========================================================================
    // CRUD Operations
    // =========================================================================

    fun saveItem(item: VaultItem) {
        val key = sessionSecretKey ?: return
        val partitionId = _uiState.value.activePartitionId
        viewModelScope.launch {
            // Check if password changed to update passwordUpdatedAt
            val existing = _uiState.value.decryptedItems.firstOrNull { it.id == item.id }
            val isPasswordChanged = existing == null || existing.password != item.password
            val updatedItem = if (isPasswordChanged) {
                item.copy(
                    passwordUpdatedAt = System.currentTimeMillis(),
                    pwnedBreachCount = if (item.category == VaultCategory.LOGIN && item.password.isNotEmpty()) -1 else 0
                )
            } else {
                item
            }

            repository.saveItem(updatedItem, key, partitionId)
            val updatedList = repository.getAllDecryptedItems(key, partitionId)
            syncUnlockedSessions(updatedList, isDuress = _uiState.value.isDuressMode)
            val health = computeHealthReport(updatedList)
            _uiState.update {
                it.copy(
                    decryptedItems = updatedList,
                    healthReport = health,
                    activeItemForEdit = null,
                    isCreatingNewItem = false,
                    activeItemForDetail = if (it.activeItemForDetail?.id == item.id) updatedItem else it.activeItemForDetail,
                    infoMessage = "Record encrypted and saved"
                )
            }
            applyFilter()
        }
    }

    fun deleteItem(id: String) {
        val key = sessionSecretKey ?: return
        val partitionId = _uiState.value.activePartitionId
        viewModelScope.launch {
            repository.deleteItem(id, partitionId)
            val updated = repository.getAllDecryptedItems(key, partitionId)
            syncUnlockedSessions(updated, isDuress = _uiState.value.isDuressMode)
            val health = computeHealthReport(updated)
            _uiState.update {
                it.copy(
                    decryptedItems = updated,
                    healthReport = health,
                    activeItemForDetail = null,
                    infoMessage = "Item purged"
                )
            }
            applyFilter()
        }
    }

    fun toggleFavorite(id: String) {
        val key = sessionSecretKey ?: return
        val partitionId = _uiState.value.activePartitionId
        viewModelScope.launch {
            repository.toggleFavorite(id, partitionId)
            val updated = repository.getAllDecryptedItems(key, partitionId)
            syncUnlockedSessions(updated, isDuress = _uiState.value.isDuressMode)
            _uiState.update {
                it.copy(
                    decryptedItems = updated,
                    activeItemForDetail = it.activeItemForDetail?.let { active ->
                        if (active.id == id) active.copy(isFavorite = !active.isFavorite) else active
                    }
                )
            }
            applyFilter()
        }
    }

    fun startCreateNewItem(category: VaultCategory = VaultCategory.LOGIN) {
        val newItem = VaultItem(
            id = UUID.randomUUID().toString(),
            title = "",
            category = category,
            partitionId = _uiState.value.activePartitionId
        )
        _uiState.update {
            it.copy(
                isCreatingNewItem = true,
                activeItemForEdit = newItem
            )
        }
    }

    fun startEditItem(item: VaultItem) {
        _uiState.update {
            it.copy(
                isCreatingNewItem = false,
                activeItemForEdit = item
            )
        }
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                isCreatingNewItem = false,
                activeItemForEdit = null
            )
        }
    }

    fun openItemDetail(item: VaultItem) {
        _uiState.update { it.copy(activeItemForDetail = item) }
    }

    fun closeItemDetail() {
        _uiState.update { it.copy(activeItemForDetail = null) }
    }

    // =========================================================================
    // E2EE Secure Sharing & Capsule Reception
    // =========================================================================

    fun startShareItem(item: VaultItem) {
        _uiState.update { it.copy(activeItemForShare = item) }
    }

    fun dismissShareDialog() {
        _uiState.update { it.copy(activeItemForShare = null) }
    }

    fun openReceiveShareDialog() {
        _uiState.update { it.copy(showReceiveShareDialog = true) }
    }

    fun dismissReceiveShareDialog() {
        _uiState.update { it.copy(showReceiveShareDialog = false) }
    }

    fun saveSharedItemToVault(decrypted: DecryptedSharedItem) {
        val key = sessionSecretKey
        if (key == null) {
            _uiState.update { it.copy(errorMessage = "Vault is locked. Unlock vault to import record.") }
            return
        }
        val partitionId = _uiState.value.activePartitionId
        viewModelScope.launch {
            val newItem = VaultItem(
                id = UUID.randomUUID().toString(),
                partitionId = partitionId,
                title = decrypted.title,
                category = decrypted.category,
                username = decrypted.username,
                password = decrypted.password,
                url = decrypted.url,
                notes = decrypted.notes,
                cardHolder = decrypted.cardHolder,
                cardNumber = decrypted.cardNumber,
                cardExpiry = decrypted.cardExpiry,
                cardCvv = decrypted.cardCvv,
                keyType = decrypted.keyType,
                keyValue = decrypted.keyValue,
                totpSecret = decrypted.totpSecret,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                passwordUpdatedAt = System.currentTimeMillis(),
                pwnedBreachCount = if (decrypted.category == VaultCategory.LOGIN && decrypted.password.isNotEmpty()) -1 else 0
            )
            repository.saveItem(newItem, key, partitionId)
            val updated = repository.getAllDecryptedItems(key, partitionId)
            syncUnlockedSessions(updated, isDuress = _uiState.value.isDuressMode)
            val health = computeHealthReport(updated)
            _uiState.update {
                it.copy(
                    decryptedItems = updated,
                    healthReport = health,
                    showReceiveShareDialog = false,
                    infoMessage = "Record \"${decrypted.title}\" saved to vault"
                )
            }
            applyFilter()
        }
    }

    // Password generator controls
    fun updatePasswordConfig(config: PasswordGeneratorConfig) {
        _uiState.update { it.copy(passwordConfig = config) }
        generatePassword()
    }

    fun generatePassword() {
        val config = _uiState.value.passwordConfig
        val pwd = if (config.isPassphraseMode) {
            SecurePasswordGenerator.generatePassphrase(wordCount = config.passphraseWords)
        } else {
            val options = PasswordOptions(
                length = config.length,
                includeUppercase = config.includeUppercase,
                includeLowercase = config.includeLowercase,
                includeNumbers = config.includeNumbers,
                includeSymbols = config.includeSymbols,
                customSymbols = config.customSymbols,
                excludeAmbiguous = config.excludeAmbiguous
            )
            SecurePasswordGenerator.generatePassword(options)
        }
        val analysis = SecurePasswordGenerator.analyzeEntropy(pwd)
        val strength = PasswordStrength(analysis.score, analysis.strengthLabel, analysis.entropyBits)
        _uiState.update {
            it.copy(
                generatedPassword = pwd,
                generatedPasswordStrength = strength,
                estimatedCrackTime = analysis.estimatedCrackTime
            )
        }
    }

    fun exportHtmlVault(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ExportFileCleaner.cleanupIdleExports(context)
            val state = _uiState.value
            val metadata = repository.getMetadata() ?: return@launch
            val partitionId = state.activePartitionId
            val effectiveMeta = if (state.isDuressMode && !metadata.duressSalt.isNullOrEmpty()) {
                metadata.copy(
                    masterSalt = metadata.duressSalt,
                    verifierHash = metadata.duressVerifierHash.orEmpty()
                )
            } else metadata
            val records = repository.getRawEntities(partitionId)
            val file = HtmlVaultExporter.generateHtmlVault(context, effectiveMeta, records)
            _uiState.update {
                it.copy(
                    shareFileIntent = file,
                    infoMessage = "Self-decrypting HTML vault ready for offline PC use"
                )
            }
        }
    }

    fun exportCustomHtmlVault(context: Context, selectedItemIds: Set<String>, exportPassphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ExportFileCleaner.cleanupIdleExports(context)
            val allItems = _uiState.value.decryptedItems
            val itemsToExport = if (selectedItemIds.isEmpty()) {
                allItems
            } else {
                allItems.filter { it.id in selectedItemIds }
            }
            if (itemsToExport.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "No items selected to export") }
                return@launch
            }
            val passphrase = exportPassphrase.ifBlank { cachedMasterPassword }
            if (passphrase.isBlank()) {
                _uiState.update { it.copy(errorMessage = "A passphrase is required to encrypt the HTML export") }
                return@launch
            }
            val file = HtmlVaultExporter.generateSelectiveHtmlVault(context, itemsToExport, passphrase)
            _uiState.update {
                it.copy(
                    shareFileIntent = file,
                    infoMessage = "Exported ${itemsToExport.size} item(s) to encrypted HTML"
                )
            }
        }
    }

    fun exportSnapshot(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ExportFileCleaner.cleanupIdleExports(context)
            val state = _uiState.value
            val metadata = repository.getMetadata() ?: return@launch
            val partitionId = state.activePartitionId
            val effectiveMeta = if (state.isDuressMode && !metadata.duressSalt.isNullOrEmpty()) {
                metadata.copy(
                    masterSalt = metadata.duressSalt,
                    verifierHash = metadata.duressVerifierHash.orEmpty()
                )
            } else metadata
            val records = repository.getRawEntities(partitionId)
            val file = SnapshotManager.createSnapshotFile(context, effectiveMeta, records)
            _uiState.update {
                it.copy(
                    shareFileIntent = file,
                    infoMessage = "Encrypted database snapshot created (${records.size} items)"
                )
            }
        }
    }

    fun inspectSnapshotFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }
                if (content.isNullOrBlank()) {
                    _uiState.update { it.copy(errorMessage = "Selected snapshot file is empty or unreadable.") }
                    return@launch
                }
                inspectSnapshotContent(content)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Unable to read snapshot file: ${e.message}") }
            }
        }
    }

    fun inspectSnapshotContent(jsonString: String) {
        val result = SnapshotManager.parseSnapshot(jsonString)
        if (result.isSuccess) {
            val pkg = result.getOrNull()
            _uiState.update {
                it.copy(
                    snapshotPreview = pkg,
                    infoMessage = "Snapshot verified: ${pkg?.recordsCount} records ready"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = "Corrupt snapshot: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun restoreSnapshot(replaceExisting: Boolean) {
        val preview = _uiState.value.snapshotPreview ?: return
        val key = sessionSecretKey ?: return
        val partitionId = _uiState.value.activePartitionId
        viewModelScope.launch(Dispatchers.IO) {
            if (preview.records.isNotEmpty()) {
                val decryptableCount = preview.records.count { entity ->
                    try {
                        BlackBoxSecurity.decryptRecordPayload(
                            id = entity.id,
                            title = entity.title,
                            category = VaultCategory.fromString(entity.category),
                            partitionId = entity.partitionId,
                            encryptedPayload = entity.encryptedPayload,
                            initializationVector = entity.initializationVector,
                            isFavorite = entity.isFavorite,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            secretKey = key
                        )
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (decryptableCount == 0) {
                    _uiState.update {
                        it.copy(
                            snapshotPreview = null,
                            errorMessage = "Snapshot cannot be decrypted with the current vault key."
                        )
                    }
                    return@launch
                }
            }
            if (replaceExisting) {
                repository.replaceAllEntities(preview.records, partitionId)
            } else {
                repository.mergeEntities(preview.records, partitionId)
            }
            val updated = repository.getAllDecryptedItems(key, partitionId)
            syncUnlockedSessions(updated, isDuress = _uiState.value.isDuressMode)
            val health = computeHealthReport(updated)
            _uiState.update {
                it.copy(
                    snapshotPreview = null,
                    decryptedItems = updated,
                    healthReport = health,
                    infoMessage = "Snapshot restored successfully (${updated.size} active items)"
                )
            }
            applyFilter()
        }
    }

    fun dismissSnapshotPreview() {
        _uiState.update { it.copy(snapshotPreview = null) }
    }

    // =========================================================================
    // Multi-Browser (Chrome, Edge, Firefox, Opera) & RFC-4180 CSV Import
    // =========================================================================

    fun inspectCsvFromUri(context: Context, uri: Uri, browserHint: ImportBrowserPreset? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _uiState.value.decryptedItems
            val result = CsvPasswordImporter.parseFromUri(context, uri, existing, browserHint)
            if (result.isSuccess) {
                val preview = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        csvImportPreview = preview,
                        infoMessage = "${preview.detectedFormat}: ${preview.parsedItems.size} records parsed"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Failed to parse CSV file.")
                }
            }
        }
    }

    fun inspectCsvContent(csvContent: String, browserHint: ImportBrowserPreset? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val existing = _uiState.value.decryptedItems
            val defaultName = when (browserHint) {
                ImportBrowserPreset.EDGE -> "pasted_edge_passwords.csv"
                ImportBrowserPreset.FIREFOX -> "pasted_firefox_logins.csv"
                ImportBrowserPreset.OPERA -> "pasted_opera_passwords.csv"
                ImportBrowserPreset.CHROME, null -> "pasted_chrome_export.csv"
            }
            val result = CsvPasswordImporter.parseFromString(csvContent, defaultName, existing, browserHint)
            if (result.isSuccess) {
                val preview = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        csvImportPreview = preview,
                        infoMessage = "${preview.detectedFormat}: ${preview.parsedItems.size} records parsed"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Invalid CSV content.")
                }
            }
        }
    }

    fun commitCsvImport(skipDuplicates: Boolean, onComplete: () -> Unit = {}) {
        val preview = _uiState.value.csvImportPreview ?: return
        val key = sessionSecretKey ?: return
        val partitionId = _uiState.value.activePartitionId
        viewModelScope.launch {
            _uiState.update { it.copy(isCsvImporting = true) }
            val itemsToImport = if (skipDuplicates) {
                preview.parsedItems.filterNot { it.id in preview.duplicateKeys }
            } else {
                preview.parsedItems
            }
            val skippedDupes = preview.parsedItems.size - itemsToImport.size
            if (itemsToImport.isNotEmpty()) {
                repository.saveItemsBatch(itemsToImport, key, partitionId)
            }
            val updated = repository.getAllDecryptedItems(key, partitionId)
            syncUnlockedSessions(updated, isDuress = _uiState.value.isDuressMode)
            val health = computeHealthReport(updated)
            _uiState.update {
                it.copy(
                    isCsvImporting = false,
                    csvImportPreview = null,
                    decryptedItems = updated,
                    healthReport = health,
                    infoMessage = if (skippedDupes > 0) {
                        "Imported ${itemsToImport.size} records ($skippedDupes duplicates skipped)"
                    } else {
                        "Imported ${itemsToImport.size} records into encrypted vault"
                    }
                )
            }
            applyFilter()
            onComplete()
        }
    }

    fun dismissCsvImportPreview() {
        _uiState.update { it.copy(csvImportPreview = null) }
    }

    fun clearShareIntent() {
        _uiState.update { it.copy(shareFileIntent = null) }
    }

    fun toggleBiometrics(enable: Boolean, currentPassword: String) {
        viewModelScope.launch {
            val success = repository.setBiometricEnabled(enable, currentPassword)
            if (success) {
                _uiState.update {
                    it.copy(
                        isBiometricEnabled = enable,
                        infoMessage = if (enable) "Biometric unlock activated" else "Biometric unlock deactivated"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Could not update biometric authentication")
                }
            }
        }
    }

    fun verifyMasterPasswordForVoiceEnrollment(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val unlockResult = repository.unlockWithPasswordOrDuress(password)
            val isPrimaryValid = unlockResult != null && !unlockResult.isDuress
            if (!isPrimaryValid) {
                _uiState.update { it.copy(errorMessage = "Master Password verification failed.") }
            }
            onResult(isPrimaryValid)
        }
    }

    fun enrollVoiceBiometrics(
        masterPassword: String,
        samples: List<ShortArray>,
        onComplete: (Result<VoiceprintTemplate>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.enrollVoiceBiometric(masterPassword, samples)
            if (result.isSuccess) {
                val tpl = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isVoiceBiometricEnabled = true,
                        infoMessage = "Voice biometric profile sealed (${(tpl.intraSpeakerConsistency * 100).toInt()}% consistency)"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Voice enrollment failed")
                }
            }
            onComplete(result)
        }
    }

    fun disableVoiceBiometrics(masterPassword: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.disableVoiceBiometric(masterPassword)
            if (success) {
                _uiState.update {
                    it.copy(
                        isVoiceBiometricEnabled = false,
                        lastVoiceSimilarityPercent = null,
                        infoMessage = "Voice biometric profile purged from vault"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Master Password incorrect. Voice profile unchanged.")
                }
            }
            onComplete(success)
        }
    }

    fun unlockWithVoiceBiometrics(
        audioSample: ShortArray,
        onResult: (VoiceUnlockOutcome) -> Unit
    ) {
        if (_uiState.value.voiceLockoutRemainingSeconds > 0) {
            val lockedOutcome = VoiceUnlockOutcome.Unavailable(
                "Voice unlock temporarily locked (${_uiState.value.voiceLockoutRemainingSeconds}s remaining). Use Master Password."
            )
            onResult(lockedOutcome)
            return
        }

        viewModelScope.launch {
            val outcome = repository.verifyAndUnlockWithVoice(audioSample)
            when (outcome) {
                is VoiceUnlockOutcome.Success -> {
                    failedVoiceAttempts = 0
                    voiceLockoutJob?.cancel()
                    unlockBackoffManager.reset()
                    passwordLockoutJob?.cancel()
                    sessionSecretKey = outcome.secretKey
                    cachedMasterPassword = outcome.masterPassword
                    inactivityLockManager.startMonitoring()
                    val items = repository.getAllDecryptedItems(outcome.secretKey, partitionId = 0)
                    syncUnlockedSessions(items, isDuress = false)
                    val health = computeHealthReport(items)
                    _uiState.update {
                        it.copy(
                            isUnlocked = true,
                            isSensitiveContentHidden = false,
                            isAppInBackground = false,
                            requiresBiometricReauth = false,
                            activePartitionId = 0,
                            isDuressMode = false,
                            failedPasswordAttempts = 0,
                            passwordLockoutRemainingSeconds = 0,
                            decryptedItems = items,
                            healthReport = health,
                            isLoading = false,
                            voiceLockoutRemainingSeconds = 0,
                            lastVoiceSimilarityPercent = outcome.verification.confidencePercent,
                            errorMessage = null,
                            infoMessage = "Vault unlocked via Voice Biometrics (${outcome.verification.confidencePercent}% match)"
                        )
                    }
                    applyFilter()
                    executePendingWidgetActionIfReady()
                }
                is VoiceUnlockOutcome.Rejected -> {
                    failedVoiceAttempts++
                    _uiState.update {
                        it.copy(lastVoiceSimilarityPercent = outcome.verification.confidencePercent)
                    }
                    if (failedVoiceAttempts >= 5) {
                        startVoiceLockout(30)
                    }
                }
                is VoiceUnlockOutcome.Unavailable -> {
                    _uiState.update { it.copy(errorMessage = outcome.reason) }
                }
            }
            onResult(outcome)
        }
    }

    private fun startVoiceLockout(seconds: Int) {
        voiceLockoutJob?.cancel()
        voiceLockoutJob = viewModelScope.launch {
            for (rem in seconds downTo 1) {
                _uiState.update { it.copy(voiceLockoutRemainingSeconds = rem) }
                delay(1000L)
            }
            failedVoiceAttempts = 0
            _uiState.update { it.copy(voiceLockoutRemainingSeconds = 0) }
        }
    }

    fun changeMasterPassword(oldPassword: String, newPassword: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.changeMasterPassword(oldPassword, newPassword)
            if (success) {
                val salt = repository.getMetadata()?.let { Base64.decode(it.masterSalt, Base64.NO_WRAP) }
                if (salt != null) {
                    sessionSecretKey = BlackBoxSecurity.deriveMasterKey(newPassword.toCharArray(), salt)
                    cachedMasterPassword = newPassword
                }
                _uiState.update {
                    it.copy(infoMessage = "Master Password updated and vault re-encrypted")
                }
                onComplete(true)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Failed to update password. Current password incorrect.")
                }
                onComplete(false)
            }
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String, isSensitive: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text).apply {
            if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(infoMessage = "Copied $label to clipboard") }

        if (isSensitive) {
            // Auto-clear sensitive clipboard content after 30 seconds
            viewModelScope.launch {
                delay(30_000)
                try {
                    val currentClip = clipboard.primaryClip
                    val stillMatches = currentClip != null &&
                        currentClip.itemCount > 0 &&
                        currentClip.getItemAt(0).text?.toString() == text
                    if (stillMatches) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            clipboard.clearPrimaryClip()
                        } else {
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    fun wipeEntireVault() {
        viewModelScope.launch {
            NfcPaymentSession.disarm(
                context = getApplication(),
                reason = "Vault wiped — NFC contactless session disarmed"
            )
            repository.wipeVault()
            withContext(Dispatchers.IO) {
                ExportFileCleaner.cleanupAllExportsUnconditionally(getApplication())
            }
            unlockBackoffManager.reset()
            passwordLockoutJob?.cancel()
            AutofillVaultSession.clear()
            ConfidentialNotesMemoryCache.clear()
            ConfidentialNotesWidgetProvider.refreshAllWidgets(getApplication())
            sessionSecretKey = null
            cachedMasterPassword = ""
            _uiState.update {
                VaultUiState(
                    isInitialized = false,
                    isUnlocked = false,
                    isLoading = false,
                    backgroundGuardStatus = BlackBoxBackgroundScheduler.getStatus(getApplication()),
                    nfcHardwareStatus = NfcContactlessHelper.queryHardwareStatus(getApplication()),
                    infoMessage = "Vault wiped cleanly"
                )
            }
        }
    }

    // =========================================================================
    // Pay with NFC (Contactless Host Card Emulation) Controls
    // =========================================================================

    fun refreshNfcStatus() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val status = NfcContactlessHelper.queryHardwareStatus(ctx)
            _uiState.update { it.copy(nfcHardwareStatus = status) }
        }
    }

    fun armNfcPayment(item: VaultItem) {
        val state = _uiState.value
        val ctx = getApplication<Application>()
        if (state.isDuressMode) {
            _uiState.update {
                it.copy(errorMessage = "Pay with NFC is disabled in decoy partition mode.")
            }
            return
        }
        if (item.category != VaultCategory.CARD || !item.isNfcPayEnabled) {
            _uiState.update {
                it.copy(errorMessage = "Enable Pay with NFC on this card record to pay contactlessly.")
            }
            return
        }
        if (item.cardNumber.filter(Char::isDigit).length < 4) {
            _uiState.update {
                it.copy(errorMessage = "Add a valid card number before arming Pay with NFC.")
            }
            return
        }
        val hardware = NfcContactlessHelper.queryHardwareStatus(ctx)
        _uiState.update { it.copy(nfcHardwareStatus = hardware) }
        if (hardware.hasNfcHardware && !hardware.isNfcEnabled) {
            _uiState.update {
                it.copy(errorMessage = "Turn on Android NFC in system settings to pay contactlessly.")
            }
            return
        }
        val session = NfcPaymentSession.arm(
            context = ctx,
            item = item,
            isDuress = state.isDuressMode
        )
        if (session != null) {
            _uiState.update {
                it.copy(
                    armedNfcSession = session,
                    nfcRemainingSeconds = session.remainingSeconds(),
                    infoMessage = "Pay with NFC armed (${session.network.displayName} ${session.maskedLast4}) — hold near terminal to pay contactlessly"
                )
            }
        }
    }

    fun disarmNfcPayment(reason: String = "NFC payment disarmed by user") {
        NfcPaymentSession.disarm(
            context = getApplication(),
            reason = reason,
            expired = false
        )
        _uiState.update {
            it.copy(
                armedNfcSession = null,
                nfcRemainingSeconds = 0,
                infoMessage = "NFC contactless session disarmed"
            )
        }
    }

    // =========================================================================
    // Confidential Notes Widget & Silent Background Guard Helpers
    // =========================================================================

    private fun syncUnlockedSessions(items: List<VaultItem>, isDuress: Boolean) {
        AutofillVaultSession.updateUnlockedItems(items, isDuress = isDuress)
        ConfidentialNotesMemoryCache.updateFromVaultItems(items, isDuress = isDuress)
        ConfidentialNotesWidgetProvider.refreshAllWidgets(getApplication())
    }

    fun handleWidgetRequest(action: String?, noteId: String?) {
        if (action.isNullOrBlank()) return
        pendingWidgetAction = action
        pendingWidgetNoteId = noteId
        executePendingWidgetActionIfReady()
    }

    private fun executePendingWidgetActionIfReady() {
        val action = pendingWidgetAction ?: return
        val state = _uiState.value
        if (!state.isUnlocked || state.isSensitiveContentHidden) return
        val targetNoteId = pendingWidgetNoteId
        pendingWidgetAction = null
        pendingWidgetNoteId = null

        when (action) {
            ConfidentialNotesWidgetProvider.WIDGET_ACTION_OPEN_NOTES -> {
                setCategoryFilter(VaultCategory.NOTE)
            }
            ConfidentialNotesWidgetProvider.WIDGET_ACTION_NEW_NOTE -> {
                setCategoryFilter(VaultCategory.NOTE)
                startCreateNewItem(VaultCategory.NOTE)
            }
            ConfidentialNotesWidgetProvider.WIDGET_ACTION_OPEN_NOTE_ID -> {
                setCategoryFilter(VaultCategory.NOTE)
                val target = state.decryptedItems.firstOrNull { it.id == targetNoteId }
                if (target != null) {
                    openItemDetail(target)
                }
            }
        }
    }

    fun toggleBackgroundGuard(enabled: Boolean) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            BlackBoxBackgroundScheduler.setEnabled(ctx, enabled)
            val status = BlackBoxBackgroundScheduler.getStatus(ctx)
            _uiState.update {
                it.copy(
                    backgroundGuardStatus = status,
                    infoMessage = if (enabled) {
                        "Silent Background Guard enabled (battery-friendly OS JobScheduler)"
                    } else {
                        "Silent Background Guard paused"
                    }
                )
            }
        }
    }

    fun runBackgroundMaintenanceNow() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val report = BlackBoxBackgroundScheduler.executeMaintenancePass(ctx)
            val status = BlackBoxBackgroundScheduler.getStatus(ctx)
            val stats = OfflineHibpStore.getStats(ctx)
            val analytics = TaskAnalyticsEngine.getSummary(ctx)
            _uiState.update {
                it.copy(
                    backgroundGuardStatus = status,
                    offlineHibpStats = stats,
                    taskAnalytics = analytics,
                    infoMessage = "Silent sweep complete (${report.purgedExportsCount} temp export(s), ${report.prunedHibpFilesCount} stale cache file(s) purged)"
                )
            }
        }
    }

    fun refreshTaskAnalytics() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val summary = TaskAnalyticsEngine.getSummary(ctx)
            _uiState.update { it.copy(taskAnalytics = summary) }
        }
    }

    fun clearTaskAnalytics() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val cleared = TaskAnalyticsEngine.clearAnalytics(ctx)
            _uiState.update {
                it.copy(
                    taskAnalytics = cleared,
                    infoMessage = "Cleared on-device task stop telemetry log"
                )
            }
        }
    }
}
