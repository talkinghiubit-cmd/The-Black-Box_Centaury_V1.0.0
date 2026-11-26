package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.model.PasswordStrength
import com.example.model.VaultCategory
import com.example.model.VaultItem
import com.example.security.BlackBoxSecurity
import com.example.security.EncryptedShareCapsule
import com.example.security.SecurePasswordGenerator
import com.example.security.SecureShareManager
import com.example.security.ShareDuration
import com.example.ui.components.CategoryIcon
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SecureShareDialog(
    item: VaultItem,
    onDismiss: () -> Unit,
    onShareFile: (File) -> Unit,
    onCopyToken: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Expiration duration selection
    var selectedDuration by remember { mutableStateOf(ShareDuration.TWENTY_FOUR_HOURS) }
    var burnAfterReading by remember { mutableStateOf(false) }

    // Passphrase generation
    var passphrase by remember { mutableStateOf(SecurePasswordGenerator.generatePassphrase(wordCount = 4)) }
    var isPassphraseVisible by remember { mutableStateOf(true) }

    // Password strength evaluation
    val passwordStrength: PasswordStrength? = remember(item.password) {
        if (item.password.isNotEmpty()) BlackBoxSecurity.evaluatePasswordStrength(item.password) else null
    }

    var generatedCapsule by remember { mutableStateOf<EncryptedShareCapsule?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BlackBoxSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("secure_share_dialog")
        ) {
            // Header Bar
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
                            .background(EmeraldNeon.copy(alpha = 0.12f))
                            .border(1.dp, EmeraldNeon, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Secure End-to-End Share",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "AES-256-GCM Zero-Knowledge Capsule",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldNeon
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("share_close_button")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Item Preview Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIcon(category = item.category)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (item.username.isNotEmpty()) {
                        Text(
                            text = item.username,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                SecurityBadge(text = item.category.displayName.uppercase())
            }

            // Password Strength Indicator (if item contains password)
            if (passwordStrength != null) {
                Spacer(modifier = Modifier.height(14.dp))
                PasswordStrengthCard(strength = passwordStrength)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Time-Limited Access Options
            Text(
                text = "TIME-LIMITED ACCESS",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyanIce,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareDuration.entries.forEach { duration ->
                    FilterChip(
                        selected = selectedDuration == duration,
                        onClick = {
                            selectedDuration = duration
                            generatedCapsule = null // Reset if changed
                        },
                        label = {
                            Text(
                                text = duration.label,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldNeon.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldNeon,
                            containerColor = BlackBoxSurfaceVariant,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedDuration == duration,
                            borderColor = if (selectedDuration == duration) EmeraldNeon else BlackBoxBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Burn After Reading Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BlackBoxSurfaceVariant)
                    .clickable { burnAfterReading = !burnAfterReading }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = burnAfterReading,
                    onCheckedChange = { burnAfterReading = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CrimsonAlert,
                        uncheckedColor = TextMuted
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Burn After Reading (Single View)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (burnAfterReading) CrimsonAlert else TextPrimary
                    )
                    Text(
                        text = "Scans and purges secret from memory immediately upon opening",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Encryption Passphrase Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECRYPTION PASSPHRASE",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyanIce,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            passphrase = SecurePasswordGenerator.generatePassphrase(wordCount = 4)
                            generatedCapsule = null
                            errorMessage = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Generate New Passphrase",
                            tint = CyanIce,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Passphrase", passphrase))
                            statusMessage = "Passphrase copied! Share via separate secure channel."
                            errorMessage = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Passphrase",
                            tint = EmeraldNeon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = passphrase,
                onValueChange = {
                    passphrase = it
                    generatedCapsule = null
                    errorMessage = null
                },
                singleLine = true,
                visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_passphrase_input"),
                placeholder = { Text("Enter or generate share passphrase", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldNeon,
                    unfocusedBorderColor = BlackBoxBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BlackBoxSurfaceVariant,
                    unfocusedContainerColor = BlackBoxSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                trailingIcon = {
                    IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                        Icon(
                            imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPassphraseVisible) "Hide passphrase" else "Show passphrase",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tip: Send the passphrase to recipient via a separate secure channel (e.g. Signal or SMS).",
                fontSize = 11.sp,
                color = TextMuted
            )

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = CrimsonAlert,
                    fontFamily = FontFamily.Monospace
                )
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = msg,
                    fontSize = 12.sp,
                    color = EmeraldNeon,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: HTML Capsule & Encrypted Token
            Button(
                onClick = {
                    if (passphrase.isBlank()) {
                        errorMessage = "Passphrase cannot be empty."
                        return@Button
                    }
                    errorMessage = null
                    coroutineScope.launch {
                        isGenerating = true
                        try {
                            val file = withContext(Dispatchers.Default) {
                                val capsule = SecureShareManager.createEncryptedCapsule(
                                    item = item,
                                    passphrase = passphrase,
                                    durationMs = selectedDuration.durationMs,
                                    burnAfterReading = burnAfterReading
                                )
                                generatedCapsule = capsule
                                SecureShareManager.generateSelfDecryptingHtmlShare(context, capsule, item)
                            }
                            onShareFile(file)
                        } catch (e: Exception) {
                            errorMessage = "Failed to generate encrypted share: ${e.message}"
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldNeon,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_html_button")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHARE ENCRYPTED CAPSULE (.HTML)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    if (passphrase.isBlank()) {
                        errorMessage = "Passphrase cannot be empty."
                        return@OutlinedButton
                    }
                    errorMessage = null
                    coroutineScope.launch {
                        isGenerating = true
                        try {
                            val token = withContext(Dispatchers.Default) {
                                val capsule = generatedCapsule ?: SecureShareManager.createEncryptedCapsule(
                                    item = item,
                                    passphrase = passphrase,
                                    durationMs = selectedDuration.durationMs,
                                    burnAfterReading = burnAfterReading
                                )
                                generatedCapsule = capsule
                                SecureShareManager.exportCapsuleAsToken(capsule)
                            }
                            onCopyToken(token)
                            statusMessage = "Encrypted capsule token copied to clipboard!"
                        } catch (e: Exception) {
                            errorMessage = "Failed to generate token: ${e.message}"
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CyanIce
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanIce.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_token_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = CyanIce
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "COPY ENCRYPTED TOKEN",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = CyanIce
                )
            }
        }
    }
}

@Composable
fun PasswordStrengthCard(
    strength: PasswordStrength,
    modifier: Modifier = Modifier
) {
    val score = strength.score
    val (color, progress) = when (score) {
        0 -> CrimsonAlert to 0.15f
        1 -> CrimsonAlert to 0.35f
        2 -> AmberWarning to 0.60f
        3 -> CyanIce to 0.85f
        else -> EmeraldNeon to 1.0f
    }

    val crackTime = remember(strength.entropyBits) {
        SecureShareManager.estimateCrackTime(strength.entropyBits)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BlackBoxSurfaceVariant)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(14.dp)
            .testTag("password_strength_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PASSWORD SECURITY",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = strength.label.uppercase(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = color,
                trackColor = BlackBoxBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${strength.entropyBits} bits entropy",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Text(
                    text = "Crack Time: $crackTime",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = color
                )
            }
        }
    }
}
