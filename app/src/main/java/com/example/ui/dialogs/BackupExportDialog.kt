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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.BlackBoxSecurity
import com.example.security.SnapshotPackage
import com.example.ui.VaultUiState
import com.example.ui.components.IncognitoKeyboardArea
import com.example.ui.components.CategoryIcon
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupExportDialog(
    uiState: VaultUiState,
    onExportHtml: () -> Unit,
    onExportSelectiveHtml: (Set<String>, String) -> Unit = { _, _ -> },
    onExportSnapshot: () -> Unit,
    onInspectSnapshot: (String) -> Unit,
    onInspectSnapshotUri: (Uri) -> Unit = {},
    onOpenCsvImport: () -> Unit = {},
    onRestoreSnapshot: (Boolean) -> Unit,
    onDismissSnapshotPreview: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showImportInputModal by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var showSelectiveExportModal by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customExportPassphrase by remember { mutableStateOf("") }
    var customPassphraseVisible by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldDark)
                            .border(1.dp, EmeraldNeon, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "BACKUP & PC EXPORT",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text(
                            text = "Offline portability & disaster recovery",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_backup_sheet")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Card 1: Self-Decrypting HTML Vault Export (For PC)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = EmeraldNeon,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SELF-DECRYPTING HTML VAULT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                        SecurityBadge(text = "PC READY")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Exports an all-in-one standalone HTML file containing your encrypted records and a client-side WebCrypto decryption engine. Open on any PC, Mac, or Linux browser without installing third-party apps.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportHtml,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldNeon,
                                contentColor = Color(0xFF003816)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("export_html_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EXPORT ALL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = {
                                selectedItemIds = uiState.decryptedItems.map { it.id }.toSet()
                                customExportPassphrase = ""
                                showSelectiveExportModal = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlackBoxSurface,
                                contentColor = EmeraldNeon
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .border(1.dp, EmeraldNeon, RoundedCornerShape(6.dp))
                                .testTag("export_selective_html_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CUSTOM EXPORT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Encrypted Local Snapshot (.blackbox)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, CyanIce.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = CyanIce,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENCRYPTED DATABASE SNAPSHOT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                        SecurityBadge(text = "SHA-256 SIGNED", color = CyanIce)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Creates a tamper-verified .blackbox snapshot file for complete offline backup and recovery. Easily restored anytime.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportSnapshot,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlackBoxSurface,
                                contentColor = CyanIce
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .border(1.dp, CyanIce.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .testTag("export_snapshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EXPORT SNAPSHOT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = { showImportInputModal = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlackBoxSurface,
                                contentColor = AmberWarning
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .border(1.dp, AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .testTag("restore_snapshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RESTORE SNAPSHOT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: Google Chrome & CSV Password Import
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = EmeraldNeon,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "IMPORT FROM CHROME / .CSV",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                        SecurityBadge(text = "MIGRATION")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Import passwords exported from Google Chrome, Edge, Firefox, Bitwarden, or any standard .csv file directly into your encrypted vault.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenCsvImport()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlackBoxSurface,
                            contentColor = EmeraldNeon
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .testTag("open_csv_import_from_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "IMPORT CHROME OR .CSV FILE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SECURITY GUARANTEE: Neither HTML exports nor database snapshots ever expose your plaintext passwords unencrypted. The recipient must possess your Master Password to decrypt.",
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
        }
    }

    // Modal to pick a .blackbox file or paste snapshot JSON
    if (showImportInputModal) {
        val snapshotFilePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                showImportInputModal = false
                onInspectSnapshotUri(uri)
            }
        }

        AlertDialog(
            onDismissRequest = { showImportInputModal = false },
            title = {
                Text(
                    text = "RESTORE SNAPSHOT DATA",
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                IncognitoKeyboardArea {
                    Column {
                        Button(
                            onClick = { snapshotFilePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanIce,
                                contentColor = Color(0xFF00283A)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pick_snapshot_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SELECT .BLACKBOX FILE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Or paste your .blackbox snapshot JSON content below to verify checksum and import records:",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = importJsonText,
                            onValueChange = { importJsonText = it },
                            placeholder = { Text("{\"format\":\"BLACKBOX_ENCRYPTED_SNAPSHOT_V1\"...}") },
                            minLines = 5,
                            maxLines = 9,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedContainerColor = BlackBoxSurface,
                                unfocusedContainerColor = BlackBoxSurface,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("snapshot_json_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportInputModal = false
                        onInspectSnapshot(importJsonText)
                    },
                    enabled = importJsonText.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                ) {
                    Text("VERIFY & PREVIEW", color = Color(0xFF003816), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportInputModal = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Snapshot Preview & Confirmation Modal
    uiState.snapshotPreview?.let { pkg ->
        val snapshotDate = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(pkg.createdAt))

        AlertDialog(
            onDismissRequest = onDismissSnapshotPreview,
            title = {
                Text(
                    text = "RESTORE SNAPSHOT VERIFIED",
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldNeon
                )
            },
            text = {
                Column {
                    Text(
                        text = "The cryptographic SHA-256 signature was verified successfully.",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Records in snapshot: ${pkg.recordsCount}\n• Snapshot created: $snapshotDate\n• SHA-256: ${pkg.checksum.take(16)}...",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Choose how to apply this snapshot to your current database:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRestoreSnapshot(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanIce)
                    ) {
                        Text("Merge Items", color = Color(0xFF00283A), fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onRestoreSnapshot(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                    ) {
                        Text("Replace Vault", color = Color(0xFF3B2500), fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSnapshotPreview) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Modal to customize and selectively export HTML vault with user-specified passphrase
    if (showSelectiveExportModal) {
        val passphraseStrength = remember(customExportPassphrase) {
            BlackBoxSecurity.evaluatePasswordStrength(customExportPassphrase)
        }

        AlertDialog(
            onDismissRequest = { showSelectiveExportModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = EmeraldNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CUSTOM HTML VAULT EXPORT",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
            },
            text = {
                IncognitoKeyboardArea {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Set a standalone passphrase to encrypt this exported HTML file. You will use this passphrase to decrypt it in the browser.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customExportPassphrase,
                            onValueChange = { customExportPassphrase = it },
                            label = { Text("Export Decryption Passphrase") },
                            placeholder = { Text("Enter a secure passphrase") },
                            singleLine = true,
                            visualTransformation = if (customPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { customPassphraseVisible = !customPassphraseVisible }) {
                                    Icon(
                                        imageVector = if (customPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedContainerColor = BlackBoxSurface,
                                unfocusedContainerColor = BlackBoxSurface,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                    if (customExportPassphrase.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        PasswordStrengthBar(strength = passphraseStrength)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELECT ITEMS (${selectedItemIds.size}/${uiState.decryptedItems.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                        Row {
                            TextButton(
                                onClick = { selectedItemIds = uiState.decryptedItems.map { it.id }.toSet() },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("All", color = EmeraldNeon, fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = { selectedItemIds = emptySet() }
                            ) {
                                Text("Clear", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurface)
                            .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        items(uiState.decryptedItems, key = { it.id }) { item ->
                            val isChecked = item.id in selectedItemIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedItemIds = if (isChecked) {
                                            selectedItemIds - item.id
                                        } else {
                                            selectedItemIds + item.id
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedItemIds = if (checked) {
                                            selectedItemIds + item.id
                                        } else {
                                            selectedItemIds - item.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = EmeraldNeon,
                                        checkmarkColor = Color(0xFF003816),
                                        uncheckedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                CategoryIcon(category = item.category, modifier = Modifier.size(26.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedItemIds
                        val pw = customExportPassphrase
                        showSelectiveExportModal = false
                        onDismiss()
                        onExportSelectiveHtml(ids, pw)
                    },
                    enabled = selectedItemIds.isNotEmpty() && customExportPassphrase.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldNeon,
                        contentColor = Color(0xFF003816),
                        disabledContainerColor = BlackBoxBorder,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Generate Encrypted HTML", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectiveExportModal = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
