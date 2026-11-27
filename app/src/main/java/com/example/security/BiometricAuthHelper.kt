package com.example.security

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Facade providing direct access to [BiometricManagerWrapper] and [BiometricVaultAuthenticator].
 */
object BiometricAuthHelper {

    fun getWrapper(context: Context): BiometricManagerWrapper {
        return BiometricManagerWrapper.getInstance(context)
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return BiometricManagerWrapper.getInstance(context).isReady()
    }

    fun isStrongBiometricAvailable(context: Context): Boolean {
        return BiometricManagerWrapper.getInstance(context).isStrongBiometricReady()
    }

    fun getAvailability(context: Context): BiometricAvailability {
        return BiometricVaultAuthenticator.getInstance(context).checkAvailability()
    }

    fun getStatus(context: Context): BiometricStatus {
        return BiometricManagerWrapper.getInstance(context).checkStatus()
    }

    fun getModalityDisplayName(context: Context): String {
        return BiometricManagerWrapper.getInstance(context).getModalityDisplayName()
    }

    fun getSensorActionHint(context: Context): String {
        return BiometricManagerWrapper.getInstance(context).getSensorActionHint()
    }

    fun promptBiometric(
        activity: FragmentActivity,
        title: String = "The Black-Box Security",
        subtitle: String? = null,
        description: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val wrapper = BiometricManagerWrapper.getInstance(activity)
        val options = BiometricPromptOptions(
            title = title,
            subtitle = subtitle ?: wrapper.getModalityDisplayName(),
            description = description ?: wrapper.getSensorActionHint(),
            negativeButtonText = "Use Master Password"
        )

        wrapper.authenticate(
            activity = activity,
            options = options,
            callback = object : BiometricAuthCallback {
                override fun onSuccess(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onFailed() {
                    onError("Biometric authentication not recognized. Please try again.")
                }

                override fun onCancelled() {
                    // User explicitly chose Master Password or dismissed
                }

                override fun onError(errorCode: Int, errorMessage: String, isLockout: Boolean) {
                    onError(errorMessage)
                }
            }
        )
    }

    fun promptBiometricWithCipher(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String = "The Black-Box Security",
        subtitle: String? = null,
        onSuccess: (Cipher?) -> Unit,
        onError: (String) -> Unit
    ) {
        val wrapper = BiometricManagerWrapper.getInstance(activity)
        val options = BiometricPromptOptions(
            title = title,
            subtitle = subtitle ?: wrapper.getModalityDisplayName(),
            description = "Authorize cryptographic key release via hardware biometric sensor",
            negativeButtonText = "Use Master Password"
        )

        wrapper.authenticateWithCipher(
            activity = activity,
            cipher = cipher,
            options = options,
            callback = object : BiometricAuthCallback {
                override fun onSuccess(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess(result.cryptoObject?.cipher)
                }

                override fun onFailed() {
                    onError("Biometric authentication not recognized. Please try again.")
                }

                override fun onCancelled() {
                    // Cancelled
                }

                override fun onError(errorCode: Int, errorMessage: String, isLockout: Boolean) {
                    onError(errorMessage)
                }
            }
        )
    }

    fun cancelActivePrompt(context: Context) {
        BiometricManagerWrapper.getInstance(context).cancelAuthentication()
    }
}
