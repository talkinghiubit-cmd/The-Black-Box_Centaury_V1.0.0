package com.example.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.BlackBoxSecurity
import com.example.security.CsvImportPreview
import com.example.ui.components.IncognitoKeyboardArea
import com.example.security.CsvPasswordImporter
import com.example.ui.components.CategoryIcon
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportDialog(
    preview: CsvImportPreview?,
    isImporting: Boolean,
    onInspectCsvUri: (Uri) -> Unit,
    onInspectCsvText: (String) -> Unit,
    onCommitImport: (Boolean) -> Unit,
    onDismissPreview: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pastedCsv by remember { mutableStateOf("") }
    var skipDuplicates by remember { mutableStateOf(true) }

    val csvFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onInspectCsvUri(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismissPreview()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = BlackBoxSurface,
        dragHandle = null
    ) {
        IncognitoKeyboardArea {
            Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldContainer)
                            .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import CSV",
                            tint = EmeraldNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CHROME & CSV VAULT IMPORT",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text(
                            text = "AES-256-GCM on-device migration",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onDismissPreview()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("close_csv_import_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close import dialog",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (preview == null) {
                // Step 1: Select Chrome / CSV file or paste CSV
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(1.dp, CyanIce.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = CyanIce,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GOOGLE CHROME EXPORT GUIDE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyanIce
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Open Google Chrome on Android or Desktop.\n" +
                                "2. Go to Settings → Google Password Manager → Settings.\n" +
                                "3. Tap Export passwords to save a .csv file, then select it below.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        csvFilePicker.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "application/csv",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldNeon,
                        contentColor = Color(0xFF003816)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("select_chrome_csv_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SELECT CHROME OR .CSV FILE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "OR PASTE CSV CONTENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = pastedCsv,
                    onValueChange = { pastedCsv = it },
                    placeholder = { Text("name,url,username,password,note\nGitHub,https://github.com,octocat,s3cr3t,SSH token") },
                    minLines = 4,
                    maxLines = 7,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldNeon,
                        unfocusedBorderColor = BlackBoxBorder,
                        focusedContainerColor = BlackBoxPitch,
                        unfocusedContainerColor = BlackBoxPitch,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("csv_paste_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { onInspectCsvText(pastedCsv) },
                    enabled = pastedCsv.trim().isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanIce),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanIce.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("parse_pasted_csv_button")
                ) {
                    Text(
                        text = "PARSE & PREVIEW CSV DATA",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(1.dp, AmberWarning.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tip: Plaintext .csv exports are unencrypted until imported. Delete the exported .csv file from Downloads after importing.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            } else {
                // Step 2: Review Parsed Records & Commit
                val importableCount = if (skipDuplicates) {
                    (preview.parsedItems.size - preview.duplicateCount).coerceAtLeast(0)
                } else {
                    preview.parsedItems.size
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preview.sourceFileName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SecurityBadge(text = preview.detectedFormat.uppercase())
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Parsed entries: ${preview.parsedItems.size}\n" +
                                "• Existing duplicates matched: ${preview.duplicateCount}\n" +
                                "• Empty/skipped rows: ${preview.skippedRows}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }

                if (preview.duplicateCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = skipDuplicates,
                            onCheckedChange = { skipDuplicates = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = EmeraldNeon,
                                checkmarkColor = Color(0xFF003816)
                            )
                        )
                        Text(
                            text = "Skip ${preview.duplicateCount} duplicate credential(s)",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "RECORDS PREVIEW (PASSWORDS MASKED)",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BlackBoxPitch)
                        .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(preview.parsedItems, key = { it.id }) { item ->
                        val isDup = item.id in preview.duplicateKeys
                        val strength = remember(item.password) {
                            BlackBoxSecurity.evaluatePasswordStrength(item.password)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(BlackBoxSurfaceVariant)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIcon(category = item.category, modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val detailLine = listOfNotNull(
                                    item.username.takeIf { it.isNotBlank() },
                                    CsvPasswordImporter.extractHostLabel(item.url).takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (detailLine.isNotEmpty()) {
                                    Text(
                                        text = detailLine,
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isDup && skipDuplicates) {
                                SecurityBadge(text = "SKIP DUP", color = AmberWarning)
                            } else if (item.password.isNotEmpty()) {
                                val badgeColor = when {
                                    strength.score <= 1 -> CrimsonAlert
                                    strength.score == 2 -> AmberWarning
                                    else -> EmeraldNeon
                                }
                                SecurityBadge(text = strength.label.uppercase(), color = badgeColor)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissPreview,
                        enabled = !isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Back", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            onCommitImport(skipDuplicates)
                        },
                        enabled = !isImporting && importableCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldNeon,
                            contentColor = Color(0xFF003816),
                            disabledContainerColor = BlackBoxBorder,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp)
                            .testTag("confirm_csv_import_button")
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                color = Color(0xFF003816),
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ENCRYPT & IMPORT ($importableCount)",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
