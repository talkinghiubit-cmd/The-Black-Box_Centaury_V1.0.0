package com.example.ui.components

import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession

/**
 * Intercepts Compose platform text input connections across the subtree and sets
 * [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING] (API 26+) so third-party and system
 * keyboards (Gboard, Samsung Keyboard, etc.) run in Incognito mode without caching or
 * learning passwords, PINs, vault secrets, passphrases, or search queries.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoKeyboardArea(
    content: @Composable () -> Unit
) {
    InterceptPlatformTextInput(
        interceptor = { request: PlatformTextInputMethodRequest, nextHandler: PlatformTextInputSession ->
            val incognitoRequest = PlatformTextInputMethodRequest { outAttributes: EditorInfo ->
                val connection: InputConnection = request.createInputConnection(outAttributes)
                applyNoPersonalizedLearningFlag(outAttributes)
                connection
            }
            nextHandler.startInputMethod(incognitoRequest)
        },
        content = content
    )
}

internal fun applyNoPersonalizedLearningFlag(outAttributes: EditorInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        outAttributes.imeOptions =
            outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
}
