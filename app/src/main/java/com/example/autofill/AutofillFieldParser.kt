package com.example.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import java.util.Locale

data class ParsedAutofillStructure(
    val usernameId: AutofillId? = null,
    val passwordId: AutofillId? = null,
    val webDomain: String? = null,
    val packageName: String = ""
) {
    val hasFillableFields: Boolean
        get() = usernameId != null || passwordId != null

    fun allAutofillIds(): Array<AutofillId> = listOfNotNull(usernameId, passwordId).toTypedArray()
}

/**
 * Traverses an Android [AssistStructure] view hierarchy to locate login username/email fields,
 * password fields, calling app package names, and Chrome/WebView web domains.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AutofillFieldParser {

    private val USERNAME_KEYWORDS = listOf("user", "email", "mail", "login", "account", "identifier")
    private val PASSWORD_KEYWORDS = listOf("pass", "pwd", "pin", "secret")

    fun parse(structure: AssistStructure): ParsedAutofillStructure {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var webDomain: String? = null
        var fallbackPackage = structure.activityComponent?.packageName.orEmpty()

        fun traverse(node: AssistStructure.ViewNode) {
            if (fallbackPackage.isEmpty() && !node.idPackage.isNullOrBlank()) {
                fallbackPackage = node.idPackage.orEmpty()
            }
            if (webDomain.isNullOrBlank() && !node.webDomain.isNullOrBlank()) {
                webDomain = node.webDomain
            }

            val autofillId = node.autofillId
            if (autofillId != null && node.autofillType != View.AUTOFILL_TYPE_NONE) {
                val hints = node.autofillHints?.map { it.lowercase(Locale.US) }.orEmpty()
                val htmlType = node.htmlInfo?.attributes
                    ?.firstOrNull { it.first.equals("type", ignoreCase = true) }
                    ?.second
                    ?.lowercase(Locale.US)
                    .orEmpty()
                val htmlName = node.htmlInfo?.attributes
                    ?.firstOrNull {
                        it.first.equals("name", ignoreCase = true) ||
                            it.first.equals("id", ignoreCase = true) ||
                            it.first.equals("autocomplete", ignoreCase = true)
                    }
                    ?.second
                    ?.lowercase(Locale.US)
                    .orEmpty()
                val idEntry = node.idEntry.orEmpty().lowercase(Locale.US)
                val hintText = node.hint.orEmpty().lowercase(Locale.US)

                val isPasswordField =
                    hints.any { it.contains("password") } ||
                        htmlType == "password" ||
                        isPasswordInputType(node.inputType) ||
                        PASSWORD_KEYWORDS.any { kw ->
                            idEntry.contains(kw) || hintText.contains(kw) || htmlName.contains(kw)
                        }

                val isUsernameField =
                    !isPasswordField && (
                        hints.any {
                            it.contains("username") ||
                                it.contains("email") ||
                                it == View.AUTOFILL_HINT_USERNAME ||
                                it == View.AUTOFILL_HINT_EMAIL_ADDRESS
                        } ||
                            htmlType == "email" ||
                            isEmailInputType(node.inputType) ||
                            USERNAME_KEYWORDS.any { kw ->
                                idEntry.contains(kw) || hintText.contains(kw) || htmlName.contains(kw)
                            }
                        )

                if (isPasswordField && passwordId == null) {
                    passwordId = autofillId
                } else if (isUsernameField && usernameId == null) {
                    usernameId = autofillId
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i))
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            traverse(structure.getWindowNodeAt(i).rootViewNode)
        }

        return ParsedAutofillStructure(
            usernameId = usernameId,
            passwordId = passwordId,
            webDomain = webDomain,
            packageName = fallbackPackage
        )
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val cls = inputType and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        if (cls == InputType.TYPE_CLASS_NUMBER) {
            return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        return false
    }

    private fun isEmailInputType(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            )
    }
}
