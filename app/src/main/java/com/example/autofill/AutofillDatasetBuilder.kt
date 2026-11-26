package com.example.autofill

import android.os.Build
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.R
import com.example.model.VaultItem

@RequiresApi(Build.VERSION_CODES.O)
object AutofillDatasetBuilder {

    fun buildPresentation(
        packageName: String,
        title: String,
        subtitle: String
    ): RemoteViews {
        return RemoteViews(packageName, R.layout.autofill_suggestion_row).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_subtitle, subtitle)
        }
    }

    fun buildDataset(
        packageName: String,
        item: VaultItem,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ): Dataset? {
        if (usernameId == null && passwordId == null) return null
        val displayTitle = item.title.ifBlank { item.url.ifBlank { "Vault Login" } }
        val displayUser = item.username.ifBlank { "Tap to fill credential" }
        val presentation = buildPresentation(packageName, displayTitle, displayUser)

        val builder = Dataset.Builder()
        var hasField = false

        if (usernameId != null && item.username.isNotEmpty()) {
            builder.setValue(usernameId, AutofillValue.forText(item.username), presentation)
            hasField = true
        } else if (usernameId != null && passwordId == null) {
            builder.setValue(usernameId, AutofillValue.forText(item.username), presentation)
            hasField = true
        }

        if (passwordId != null && item.password.isNotEmpty()) {
            builder.setValue(passwordId, AutofillValue.forText(item.password), presentation)
            hasField = true
        }

        return if (hasField) builder.build() else null
    }
}
