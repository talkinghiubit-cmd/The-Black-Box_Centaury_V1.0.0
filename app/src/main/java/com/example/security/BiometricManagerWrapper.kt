package com.example.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference
import javax.crypto.Cipher

/**
 * Distinguishes the specific biometric sensor modalities available on the device.
 */
enum class BiometricModality(val displayName: String) {
    FINGERPRINT("Fingerprint"),
    FACE("Facial Recognition"),
    IRIS("Iris Scanner"),
    UNKNOWN("Biometrics");

    companion object {
        fun formatModalities(modalities: Set<BiometricModality>): String {
            return when {
                modalities.isEmpty() -> "Biometrics"
                modalities.contains(FINGERPRINT) && modalities.contains(FACE) -> "Fingerprint & Facial Recognition"
                modalities.contains(FINGERPRINT) -> "Fingerprint"
                modalities.contains(FACE) -> "Facial Recognition"
                modalities.contains(IRIS) -> "Iris Recognition"
                else -> "Biometric Verification"
            }
        }
    }
}

/**
 * Detailed status evaluation of device biometric capabilities.
 */
sealed class BiometricStatus {
    data class Ready(
        val modalities: Set<BiometricModality>,
        val label: String
    ) : BiometricStatus()

    data class NotEnrolled(
        val modalities: Set<BiometricModality>,
        val message: String = "No biometric credentials registered on this device."
    ) : BiometricStatus()

    data class NoHardware(
        val message: String = "This device does not have biometric sensor hardware."
    ) : BiometricStatus()

    data class HardwareUnavailable(
        val message: String = "Biometric sensor hardware is currently busy or unavailable."
    ) : BiometricStatus()

    object SecurityUpdateRequired : BiometricStatus()

    data class Unsupported(
        val message: String = "Biometric authentication is not supported."
    ) : BiometricStatus()
}

/**
 * Configuration parameters for fingerprint and facial recognition prompt dialogs.
 */
data class BiometricPromptOptions(
    val title: String = "The Black-Box Security",
    val subtitle: String? = null,
    val description: String? = null,
    val negativeButtonText: String = "Use Master Password",
    val confirmationRequired: Boolean = false
)

/**
 * Callbacks for handling biometric authentication lifecycle and events.
 */
interface BiometricAuthCallback {
    fun onSuccess(result: BiometricPrompt.AuthenticationResult)
    fun onFailed()
    fun onCancelled()
    fun onError(errorCode: Int, errorMessage: String, isLockout: Boolean)
}

/**
 * Comprehensive wrapper over Android's [BiometricManager] and [BiometricPrompt].
 * Specifically designed to streamline fingerprint, facial recognition, and iris
 * authentication flows for vault unlocking and initial activity launch.
 */
class BiometricManagerWrapper private constructor(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)
    private var activePromptRef: WeakReference<BiometricPrompt>? = null

    /**
     * Inspects package manager hardware features to identify supported modalities.
     */
    fun getAvailableModalities(): Set<BiometricModality> {
        val pm = context.packageManager
        val modalities = mutableSetOf<BiometricModality>()

        if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            modalities.add(BiometricModality.FINGERPRINT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (pm.hasSystemFeature(PackageManager.FEATURE_FACE)) {
                modalities.add(BiometricModality.FACE)
            }
            if (pm.hasSystemFeature(PackageManager.FEATURE_IRIS)) {
                modalities.add(BiometricModality.IRIS)
            }
        }

        if (modalities.isEmpty()) {
            modalities.add(BiometricModality.UNKNOWN)
        }

        return modalities
    }

    /**
     * Returns a human-friendly name for the supported biometric modality
     * (e.g. "Fingerprint & Facial Recognition", "Fingerprint", "Facial Recognition").
     */
    fun getModalityDisplayName(): String {
        return BiometricModality.formatModalities(getAvailableModalities())
    }

    /**
     * Dynamic hint tailored to available hardware.
     */
    fun getSensorActionHint(): String {
        val modalities = getAvailableModalities()
        return when {
            modalities.contains(BiometricModality.FINGERPRINT) && modalities.contains(BiometricModality.FACE) ->
                "Touch sensor or glance at camera to decrypt"
            modalities.contains(BiometricModality.FACE) ->
                "Glance at camera to decrypt vault"
            modalities.contains(BiometricModality.FINGERPRINT) ->
                "Touch fingerprint sensor to decrypt vault"
            else ->
                "Authenticate to decrypt vault"
        }
    }

    /**
     * Checks the detailed biometric status including hardware support and enrollment.
     */
    fun checkStatus(
        authenticators: Int = Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
    ): BiometricStatus {
        val modalities = getAvailableModalities()
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                BiometricStatus.Ready(
                    modalities = modalities,
                    label = BiometricModality.formatModalities(modalities)
                )
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                BiometricStatus.NotEnrolled(modalities = modalities)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                BiometricStatus.NoHardware()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                BiometricStatus.HardwareUnavailable()
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                BiometricStatus.SecurityUpdateRequired
            }
            else -> {
                BiometricStatus.Unsupported()
            }
        }
    }

    /**
     * Fast check to determine if biometric unlocking is ready to use immediately.
     */
    fun isReady(): Boolean {
        return checkStatus() is BiometricStatus.Ready
    }

    /**
     * Checks if strong Class 3 biometrics are supported (required for Hardware Keystore CryptoObject).
     */
    fun isStrongBiometricReady(): Boolean {
        return biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Returns an Intent to launch Android's system biometric enrollment settings,
     * or null if not supported.
     */
    @Suppress("DEPRECATION")
    fun createEnrollmentIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Intent(Settings.ACTION_FINGERPRINT_ENROLL)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * Initiates standard biometric authentication flow (supports Fingerprint & Face Unlock).
     */
    fun authenticate(
        activity: FragmentActivity,
        options: BiometricPromptOptions = BiometricPromptOptions(),
        callback: BiometricAuthCallback
    ): BiometricPrompt {
        cancelAuthentication()

        val promptInfo = buildPromptInfo(options)
        val prompt = createBiometricPrompt(activity, callback)
        activePromptRef = WeakReference(prompt)

        prompt.authenticate(promptInfo)
        return prompt
    }

    /**
     * Initiates hardware Cipher-backed biometric authentication.
     */
    fun authenticateWithCipher(
        activity: FragmentActivity,
        cipher: Cipher,
        options: BiometricPromptOptions = BiometricPromptOptions(),
        callback: BiometricAuthCallback
    ): BiometricPrompt {
        cancelAuthentication()

        val promptInfo = buildPromptInfo(options, requireStrongOnly = true)
        val prompt = createBiometricPrompt(activity, callback)
        activePromptRef = WeakReference(prompt)

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        prompt.authenticate(promptInfo, cryptoObject)
        return prompt
    }

    /**
     * Cancels any active biometric prompt session to prevent window leaks.
     */
    fun cancelAuthentication() {
        activePromptRef?.get()?.cancelAuthentication()
        activePromptRef = null
    }

    /**
     * Checks if a prompt is currently active.
     */
    fun isPromptActive(): Boolean {
        return activePromptRef?.get() != null
    }

    private fun buildPromptInfo(
        options: BiometricPromptOptions,
        requireStrongOnly: Boolean = false
    ): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(options.title)
            .setNegativeButtonText(options.negativeButtonText)
            .setConfirmationRequired(options.confirmationRequired)

        val subtitle = options.subtitle ?: getModalityDisplayName()
        val description = options.description ?: getSensorActionHint()

        builder.setSubtitle(subtitle)
        builder.setDescription(description)

        val allowed = if (requireStrongOnly) {
            Authenticators.BIOMETRIC_STRONG
        } else {
            Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
        }
        builder.setAllowedAuthenticators(allowed)

        return builder.build()
    }

    private fun createBiometricPrompt(
        activity: FragmentActivity,
        callback: BiometricAuthCallback
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)
        val authCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                activePromptRef = null
                callback.onSuccess(result)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.onFailed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                activePromptRef = null
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        callback.onCancelled()
                    }
                    BiometricPrompt.ERROR_LOCKOUT -> {
                        callback.onError(
                            errorCode,
                            "Too many failed attempts. Biometric sensor is temporarily locked for 30 seconds.",
                            isLockout = true
                        )
                    }
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        callback.onError(
                            errorCode,
                            "Biometric sensor locked permanently. Master Password verification required.",
                            isLockout = true
                        )
                    }
                    else -> {
                        callback.onError(errorCode, errString.toString(), isLockout = false)
                    }
                }
            }
        }

        return BiometricPrompt(activity, executor, authCallback)
    }

    companion object {
        @Volatile
        private var instance: BiometricManagerWrapper? = null

        fun getInstance(context: Context): BiometricManagerWrapper {
            return instance ?: synchronized(this) {
                instance ?: BiometricManagerWrapper(context.applicationContext).also { instance = it }
            }
        }
    }
}
