package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import kotlin.coroutines.resume

/**
 * Result state representing the outcome of a biometric access verification.
 */
sealed class BiometricAccessResult {
    data class Granted(val cryptoObject: BiometricPrompt.CryptoObject? = null) : BiometricAccessResult()
    data class Denied(val message: String) : BiometricAccessResult()
    data class Error(val errorCode: Int, val message: String, val isLockout: Boolean = false) : BiometricAccessResult()
    object Cancelled : BiometricAccessResult()
}

/**
 * High-level biometric authentication service built on the `androidx.biometric` library.
 *
 * Dedicated to guarding vaulted credentials and enforcing user verification before
 * any decrypted vault data or cryptographic session keys are released into memory.
 *
 * Capabilities:
 * - Direct sensor status verification using [BiometricManager]
 * - Standard biometric authentication (callback and cancellable coroutine APIs)
 * - Hardware KeyStore [BiometricPrompt.CryptoObject] authentication (requires Class 3 Strong biometrics)
 * - Automatic lockout, retry guard, and cancellation state tracking
 */
class BiometricVaultAuthService private constructor(private val appContext: Context) {

    private val biometricWrapper: BiometricManagerWrapper by lazy {
        BiometricManagerWrapper.getInstance(appContext)
    }

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    /**
     * Inspects whether the hardware sensor is available and biometric credentials
     * (fingerprint, face, or iris) are enrolled and ready.
     */
    fun isBiometricAvailable(): Boolean {
        return biometricWrapper.isReady()
    }

    /**
     * Inspects whether Class 3 Strong biometrics are available (required for KeyStore CryptoObject).
     */
    fun isStrongBiometricAvailable(): Boolean {
        return biometricWrapper.isStrongBiometricReady()
    }

    /**
     * Returns detailed availability status of the biometric subsystem.
     */
    fun getAvailabilityStatus(): BiometricStatus {
        return biometricWrapper.checkStatus()
    }

    /**
     * Returns human-readable label of the device's biometric modality (e.g. "Fingerprint", "Facial Recognition").
     */
    fun getModalityLabel(): String {
        return biometricWrapper.getModalityDisplayName()
    }

    /**
     * Returns appropriate guidance text for sensor interaction.
     */
    fun getSensorActionHint(): String {
        return biometricWrapper.getSensorActionHint()
    }

    /**
     * Coroutine-friendly wrapper over [authenticateToAccessVault] that automatically cancels
     * the system [BiometricPrompt] if the calling coroutine is cancelled.
     */
    suspend fun authenticateSuspend(
        activity: FragmentActivity,
        title: String = "Unlock The Black-Box",
        subtitle: String? = null,
        description: String = "Biometric authentication required before accessing vaulted data",
        negativeButtonText: String = "Use Master Password"
    ): BiometricAccessResult = suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) {
                cancelAuthentication()
            }
        }
        authenticateToAccessVault(
            activity = activity,
            title = title,
            subtitle = subtitle,
            description = description,
            negativeButtonText = negativeButtonText
        ) { result ->
            // Denied occurs per bad finger scan while the dialog stays open; wait for terminal outcome or keep single-resume safety
            if (result !is BiometricAccessResult.Denied && completed.compareAndSet(false, true)) {
                continuation.resume(result)
            }
        }
    }

    /**
     * Coroutine-friendly wrapper over [authenticateWithCryptoObject].
     */
    suspend fun authenticateWithCryptoSuspend(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String = "Cryptographic Authorization",
        subtitle: String? = null,
        description: String = "Biometric verification required to unlock AES-256 vault encryption key"
    ): BiometricAccessResult = suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) {
                cancelAuthentication()
            }
        }
        authenticateWithCryptoObject(
            activity = activity,
            cipher = cipher,
            title = title,
            subtitle = subtitle,
            description = description
        ) { result ->
            if (result !is BiometricAccessResult.Denied && completed.compareAndSet(false, true)) {
                continuation.resume(result)
            }
        }
    }

    /**
     * Enforces biometric authentication before accessing the vaulted data.
     */
    fun authenticateToAccessVault(
        activity: FragmentActivity,
        title: String = "Unlock The Black-Box",
        subtitle: String? = null,
        description: String = "Biometric authentication required before accessing vaulted data",
        negativeButtonText: String = "Use Master Password",
        onResult: (BiometricAccessResult) -> Unit
    ) {
        if (!isBiometricAvailable()) {
            _isAuthenticating.value = false
            onResult(
                BiometricAccessResult.Error(
                    errorCode = BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    message = "Biometric authentication is unavailable or no credentials enrolled on this device."
                )
            )
            return
        }

        if (biometricWrapper.isPromptActive()) {
            onResult(BiometricAccessResult.Cancelled)
            return
        }

        _isAuthenticating.value = true

        val promptOptions = BiometricPromptOptions(
            title = title,
            subtitle = subtitle ?: getModalityLabel(),
            description = description,
            negativeButtonText = negativeButtonText,
            confirmationRequired = false
        )

        biometricWrapper.authenticate(
            activity = activity,
            options = promptOptions,
            callback = object : BiometricAuthCallback {
                override fun onSuccess(result: BiometricPrompt.AuthenticationResult) {
                    _isAuthenticating.value = false
                    onResult(BiometricAccessResult.Granted(result.cryptoObject))
                }

                override fun onFailed() {
                    // BiometricPrompt stays open for retry until lockout or cancellation
                    onResult(BiometricAccessResult.Denied("Biometric not recognized. Please scan again."))
                }

                override fun onCancelled() {
                    _isAuthenticating.value = false
                    onResult(BiometricAccessResult.Cancelled)
                }

                override fun onError(errorCode: Int, errorMessage: String, isLockout: Boolean) {
                    _isAuthenticating.value = false
                    onResult(BiometricAccessResult.Error(errorCode, errorMessage, isLockout))
                }
            }
        )
    }

    /**
     * Enforces biometric authentication coupled to a cryptographic [Cipher] object.
     * Guarantees that hardware-backed AES keys cannot be released without Class 3 Strong biometric authorization.
     */
    fun authenticateWithCryptoObject(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String = "Cryptographic Authorization",
        subtitle: String? = null,
        description: String = "Biometric verification required to unlock AES-256 vault encryption key",
        onResult: (BiometricAccessResult) -> Unit
    ) {
        if (!isStrongBiometricAvailable()) {
            _isAuthenticating.value = false
            onResult(
                BiometricAccessResult.Error(
                    errorCode = BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    message = "Class 3 Strong biometric hardware is required for cryptographic key authorization."
                )
            )
            return
        }

        if (biometricWrapper.isPromptActive()) {
            onResult(BiometricAccessResult.Cancelled)
            return
        }

        _isAuthenticating.value = true

        val promptOptions = BiometricPromptOptions(
            title = title,
            subtitle = subtitle ?: getModalityLabel(),
            description = description,
            negativeButtonText = "Use Master Password",
            confirmationRequired = false
        )

        biometricWrapper.authenticateWithCipher(
            activity = activity,
            cipher = cipher,
            options = promptOptions,
            callback = object : BiometricAuthCallback {
                override fun onSuccess(result: BiometricPrompt.AuthenticationResult) {
                    _isAuthenticating.value = false
                    onResult(BiometricAccessResult.Granted(result.cryptoObject))
                }

                override fun onFailed() {
                    onResult(BiometricAccessResult.Denied("Biometric not recognized."))
                }

                override fun onCancelled() {
                    _isAuthenticating.value = false
                    onResult(BiometricAccessResult.Cancelled)
                }

                override fun onError(errorCode: Int, errorMessage: String, isLockout: Boolean) {
                    _isAuthenticating.value = false
                    onResult(BiometricAccessResult.Error(errorCode, errorMessage, isLockout))
                }
            }
        )
    }

    /**
     * Immediately dismisses any active biometric authentication dialog.
     */
    fun cancelAuthentication() {
        biometricWrapper.cancelAuthentication()
        _isAuthenticating.value = false
    }

    companion object {
        @Volatile
        private var INSTANCE: BiometricVaultAuthService? = null

        fun getInstance(context: Context): BiometricVaultAuthService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BiometricVaultAuthService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
