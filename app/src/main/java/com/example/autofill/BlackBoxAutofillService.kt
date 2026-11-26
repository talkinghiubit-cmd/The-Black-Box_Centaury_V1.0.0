package com.example.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import androidx.annotation.RequiresApi
import com.example.R

/**
 * Opt-in Android AutofillService for The Black-Box.
 *
 * - When the vault is locked: returns an authentication-gated FillResponse that launches
 *   [AutofillAuthActivity] (protected by Biometrics / Master Password + FLAG_SECURE).
 * - When the vault is unlocked: returns matching domain/package Datasets from RAM-only
 *   [AutofillVaultSession] (capped to 10 items to keep Binder payloads compact) plus a
 *   manual vault picker dataset.
 */
@RequiresApi(Build.VERSION_CODES.O)
class BlackBoxAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        if (cancellationSignal.isCanceled) return

        val latestContext = request.fillContexts.lastOrNull()
        if (latestContext == null) {
            callback.onSuccess(null)
            return
        }

        val parsed = AutofillFieldParser.parse(latestContext.structure)
        if (!parsed.hasFillableFields) {
            callback.onSuccess(null)
            return
        }

        // Never autofill into The Black-Box itself
        if (parsed.packageName == packageName) {
            callback.onSuccess(null)
            return
        }

        val ids = parsed.allAutofillIds()
        if (ids.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val authIntent = Intent(this, AutofillAuthActivity::class.java).apply {
            putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, parsed.usernameId)
            putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, parsed.passwordId)
            putExtra(AutofillAuthActivity.EXTRA_WEB_DOMAIN, parsed.webDomain)
            putExtra(AutofillAuthActivity.EXTRA_TARGET_PACKAGE, parsed.packageName)
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val authSender = PendingIntent.getActivity(this, request.id, authIntent, pendingFlags).intentSender

        if (!AutofillVaultSession.isUnlocked()) {
            val presentation = AutofillDatasetBuilder.buildPresentation(
                packageName = packageName,
                title = getString(R.string.autofill_auth_prompt),
                subtitle = parsed.webDomain?.takeIf { it.isNotBlank() }
                    ?: parsed.packageName.ifBlank { getString(R.string.app_name) }
            )
            val response = FillResponse.Builder()
                .setAuthentication(ids, authSender, presentation)
                .build()
            callback.onSuccess(response)
            return
        }

        val unlockedLogins = AutofillVaultSession.getUnlockedLogins()
        val matched = AutofillMatcher.matchLogins(
            items = unlockedLogins,
            webDomain = parsed.webDomain,
            packageName = parsed.packageName,
            maxResults = 10
        )

        val responseBuilder = FillResponse.Builder()
        var addedDatasets = 0

        for (match in matched) {
            val dataset = AutofillDatasetBuilder.buildDataset(
                packageName = packageName,
                item = match.item,
                usernameId = parsed.usernameId,
                passwordId = parsed.passwordId
            )
            if (dataset != null) {
                responseBuilder.addDataset(dataset)
                addedDatasets++
            }
        }

        // Always include an authenticated/picker dataset to browse or search all credentials
        val pickerPresentation = AutofillDatasetBuilder.buildPresentation(
            packageName = packageName,
            title = if (addedDatasets == 0) "Open Black-Box Vault..." else "Search All Vault Logins...",
            subtitle = getString(R.string.app_name)
        )
        val pickerDatasetBuilder = Dataset.Builder(pickerPresentation)
            .setAuthentication(authSender)
        parsed.usernameId?.let { pickerDatasetBuilder.setValue(it, null) }
        parsed.passwordId?.let { pickerDatasetBuilder.setValue(it, null) }
        responseBuilder.addDataset(pickerDatasetBuilder.build())

        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // The Black-Box never silently writes credentials from third-party screens without explicit user review.
        callback.onSuccess()
    }
}
