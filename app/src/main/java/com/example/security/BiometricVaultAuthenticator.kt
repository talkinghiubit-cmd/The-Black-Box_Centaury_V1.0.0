package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Biometric status classification representing hardware and enrollment capabilities.
 */
sealed class BiometricAvailability {
    object Ready : BiometricAvailability()
    data class NoneEnrolled(val message: String = "No biometric credentials registered on this device.") : BiometricAvailability()
    data class NoHardware(val message: String = "This device lacks biometric scanning hardware.") : BiometricAvailability()
    data class HardwareUnavailable(val message: String = "Biometric hardware is currently busy or unavailable.") : BiometricAvailability()
    data class SecurityUpdateRequired(val message: String = "A security update is required to use biometrics.") : BiometricAvailability()
    data class Unsupported(val message: String = "Biometric authentication is not supported.") : BiometricAvailability()
}

/**
 * Configuration options for the BiometricPrompt dialog.
 */
data class BiometricPromptConfig(
    val title: String = "Unlock The Black-Box",
    val subtitle: String = "Biometric verification required",
    val description: String = "Verify your identity to decrypt your local vault secrets",
    val negativeButtonText: String = "Use Master Password",
    val confirmationRequired: Boolean = false
)

/**
 * Callback interface for biometric authentication outcomes.
 */
interface BiometricAuthenticationListener {
    fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult)
    fun onAuthenticationFailed()
    fun onAuthenticationCancelled()
    fun onAuthenticationError(errorCode: Int, errorMessage: String)
}

/**
 * Comprehensive helper class wrapping the Android Biometric library.
 *
 * Ensures hardware-enforced and user-verified access to encrypted vault content
 * with support for both standard biometrics and Cipher-backed CryptoObjects.
 */
class BiometricVaultAuthenticator(private val context: Context) {

    private val biometricManager: BiometricManager = BiometricManager.from(context)
    private var currentPrompt: BiometricPrompt? = null

    /**
     * Inspects device biometric capabilities and returns a detailed status.
     */
    fun checkAvailability(
        authenticators: Int = Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
    ): BiometricAvailability {
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Ready
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled()
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NoHardware()
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HardwareUnavailable()
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.SecurityUpdateRequired()
            else -> BiometricAvailability.Unsupported()
        }
    }

    /**
     * Checks if biometric unlock can proceed immediately.
     */
    fun isBiometricReady(): Boolean {
        return checkAvailability() is BiometricAvailability.Ready
    }

    /**
     * Requires biometric authentication to unlock the vault.
     *
     * @param activity The host FragmentActivity
     * @param config Custom prompt dialog configuration
     * @param listener Callback listener
     */
    fun requireAuthentication(
        activity: FragmentActivity,
        config: BiometricPromptConfig = BiometricPromptConfig(),
        listener: BiometricAuthenticationListener
    ) {
        val promptInfo = buildPromptInfo(config)
        val prompt = createBiometricPrompt(activity, listener)
        currentPrompt = prompt
        prompt.authenticate(promptInfo)
    }

    /**
     * Requires biometric authentication coupled with a javax.crypto.Cipher CryptoObject.
     * Guarantees cryptographic hardware key release only upon successful biometric verification.
     *
     * @param activity The host FragmentActivity
     * @param cipher Initialized Cipher (e.g. for decryption or encryption)
     * @param config Custom prompt dialog configuration
     * @param listener Callback listener
     */
    fun requireAuthenticationWithCrypto(
        activity: FragmentActivity,
        cipher: Cipher,
        config: BiometricPromptConfig = BiometricPromptConfig(),
        listener: BiometricAuthenticationListener
    ) {
        val promptInfo = buildPromptInfo(config, requireStrongOnly = true)
        val prompt = createBiometricPrompt(activity, listener)
        currentPrompt = prompt
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        prompt.authenticate(promptInfo, cryptoObject)
    }

    /**
     * Cancels any active biometric prompt.
     */
    fun cancelAuthentication() {
        currentPrompt?.cancelAuthentication()
        currentPrompt = null
    }

    private fun buildPromptInfo(
        config: BiometricPromptConfig,
        requireStrongOnly: Boolean = false
    ): BiometricPrompt.PromptInfo {
        val allowed = if (requireStrongOnly) {
            Authenticators.BIOMETRIC_STRONG
        } else {
            Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
        }
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(config.title)
            .setSubtitle(config.subtitle)
            .setDescription(config.description)
            .setNegativeButtonText(config.negativeButtonText)
            .setConfirmationRequired(config.confirmationRequired)
            .setAllowedAuthenticators(allowed)
            .build()
    }

    private fun createBiometricPrompt(
        activity: FragmentActivity,
        listener: BiometricAuthenticationListener
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                currentPrompt = null
                listener.onAuthenticationSucceeded(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                currentPrompt = null
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        listener.onAuthenticationCancelled()
                    }
                    BiometricPrompt.ERROR_LOCKOUT -> {
                        listener.onAuthenticationError(
                            errorCode,
                            "Too many failed attempts. Biometric sensor temporarily locked."
                        )
                    }
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        listener.onAuthenticationError(
                            errorCode,
                            "Biometric sensor locked permanently. Master Password required."
                        )
                    }
                    else -> {
                        listener.onAuthenticationError(errorCode, errString.toString())
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                listener.onAuthenticationFailed()
            }
        }

        return BiometricPrompt(activity, executor, callback)
    }

    companion object {
        @Volatile
        private var instance: BiometricVaultAuthenticator? = null

        fun getInstance(context: Context): BiometricVaultAuthenticator {
            return instance ?: synchronized(this) {
                instance ?: BiometricVaultAuthenticator(context.applicationContext).also { instance = it }
            }
        }
    }
}
