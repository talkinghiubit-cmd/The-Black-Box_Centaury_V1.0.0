package com.example.ui

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.security.BiometricManagerWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel-aware Lifecycle Observer that protects sensitive vault information
 * and orchestrates biometric authentication across application lifecycle events:
 *
 * 1. Backgrounding (ON_PAUSE, ON_STOP):
 *    - Immediately conceals sensitive UI content and dismisses active inspection sheets.
 *    - Purges plaintext session keys and locks the vault.
 *    - Dismisses active biometric prompts to prevent window leaks.
 *
 * 2. Foregrounding (ON_START, ON_RESUME):
 *    - Restores foreground operational state.
 *    - Checks if vault requires unlock and biometrics is enabled.
 *    - Automatically initiates the biometric authentication prompt flow.
 */
class VaultViewModelLifecycleObserver(
    private val viewModel: VaultViewModel,
    private val biometricManager: BiometricManagerWrapper? = null,
    private val onTriggerBiometric: () -> Unit = {},
    private val onCancelBiometric: () -> Unit = {},
    private val autoPromptDelayMs: Long = 200L,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : DefaultLifecycleObserver {

    private var autoPromptJob: Job? = null
    private var hasPromptedForCurrentSession: Boolean = false

    override fun onPause(owner: LifecycleOwner) {
        autoPromptJob?.cancel()
        // Immediately conceal sensitive UI content when moving to background / recent tasks
        viewModel.hideSensitiveContent()
        onCancelBiometric()
        biometricManager?.cancelAuthentication()
    }

    override fun onStop(owner: LifecycleOwner) {
        autoPromptJob?.cancel()
        // High-security locking and purging when app is fully stopped/backgrounded
        viewModel.onAppBackgrounded()
        hasPromptedForCurrentSession = false
        onCancelBiometric()
        biometricManager?.cancelAuthentication()
    }

    override fun onStart(owner: LifecycleOwner) {
        viewModel.onAppForegrounded()
    }

    override fun onResume(owner: LifecycleOwner) {
        viewModel.onAppForegrounded()
        checkAndTriggerBiometrics()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        autoPromptJob?.cancel()
        onCancelBiometric()
        biometricManager?.cancelAuthentication()
    }

    /**
     * Resets the session prompt guard, allowing a subsequent biometric prompt to be initiated.
     */
    fun resetSessionPromptGuard() {
        hasPromptedForCurrentSession = false
    }

    /**
     * Inspects current state and initiates the biometric unlock flow if criteria are met.
     */
    fun checkAndTriggerBiometrics() {
        val state = viewModel.uiState.value
        // Only trigger if vault is initialized, not in duress mode, locked or concealed, biometrics is enabled, and sensor is ready
        if (state.isInitialized && !state.isDuressMode && (!state.isUnlocked || state.isSensitiveContentHidden) && state.isBiometricEnabled) {
            val isReady = biometricManager?.isReady() ?: true
            val isPromptActive = biometricManager?.isPromptActive() ?: false

            if (!hasPromptedForCurrentSession && !isPromptActive && isReady) {
                hasPromptedForCurrentSession = true
                autoPromptJob?.cancel()
                autoPromptJob = coroutineScope.launch {
                    if (autoPromptDelayMs > 0) {
                        delay(autoPromptDelayMs)
                    }
                    val currentState = viewModel.uiState.value
                    if ((!currentState.isUnlocked || currentState.isSensitiveContentHidden) && !(biometricManager?.isPromptActive() ?: false)) {
                        onTriggerBiometric()
                    }
                }
            }
        }
    }
}
