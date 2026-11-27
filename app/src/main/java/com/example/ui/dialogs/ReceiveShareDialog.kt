package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.security.DecryptedSharedItem
import com.example.security.EncryptedShareCapsule
import com.example.security.SecureShareManager
import com.example.security.ShareDecryptionException
import com.example.security.ShareExpiredException
import com.example.ui.components.CategoryIcon
import com.example.ui.components.IncognitoKeyboardArea
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveShareDialog(
    onDismiss: () -> Unit,
    onSaveToVault: (DecryptedSharedItem) -> Unit,
    onCopyText: (String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var rawTokenInput by remember { mutableStateOf("") }
    var passphraseInput by remember { mutableStateOf("") }
    var isPassphraseVisible by remember { mutableStateOf(false) }
    var isSecretVisible by remember { mutableStateOf(false) }

    var decryptedItem by remember { mutableStateOf<DecryptedSharedItem?>(null) }
    var parsedCapsule by remember { mutableStateOf<EncryptedShareCapsule?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isDecrypting by remember { mutableStateOf(false) }

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
                .testTag("receive_share_dialog")
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
                            .background(CyanIce.copy(alpha = 0.12f))
                            .border(1.dp, CyanIce.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = CyanIce,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "RECEIVE SECURE SHARE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "ZERO-KNOWLEDGE • E2EE CAPSULE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CyanIce
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_receive_share_button")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (decryptedItem == null) {
                // TOKEN / CAPSULE INPUT
                Text(
                    text = "SHARE TOKEN OR CAPSULE PAYLOAD",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyanIce,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = rawTokenInput,
                    onValueChange = {
                        rawTokenInput = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_token_input"),
                    placeholder = {
                        Text(
                            "Paste BBOX-SHARE-v1:... token or JSON capsule",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString().orEmpty()
                                    rawTokenInput = text
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier.testTag("paste_share_token_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste from clipboard",
                                tint = CyanIce
                            )
                        }
                    },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanIce,
                        unfocusedBorderColor = BlackBoxBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanIce
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // PASSPHRASE INPUT
                Text(
                    text = "DECRYPTION PASSPHRASE",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyanIce,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = passphraseInput,
                    onValueChange = {
                        passphraseInput = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_passphrase_input"),
                    placeholder = {
                        Text(
                            "Enter the shared secret passphrase",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    },
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                            Icon(
                                imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle passphrase visibility",
                                tint = TextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanIce,
                        unfocusedBorderColor = BlackBoxBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanIce
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CrimsonAlert.copy(alpha = 0.12f))
                            .border(1.dp, CrimsonAlert.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CrimsonAlert,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage.orEmpty(),
                                color = CrimsonAlert,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (rawTokenInput.isBlank()) {
                            errorMessage = "Please enter or paste a valid share token."
                            return@Button
                        }
                        if (passphraseInput.isBlank()) {
                            errorMessage = "Passphrase cannot be empty."
                            return@Button
                        }

                        coroutineScope.launch {
                            isDecrypting = true
                            errorMessage = null
                            try {
                                val (capsule, decrypted) = withContext(Dispatchers.Default) {
                                    val c = SecureShareManager.parseCapsuleToken(rawTokenInput)
                                    if (c.burnAfterReading && SecureShareManager.isCapsuleBurned(context, c.id)) {
                                        throw ShareExpiredException("This burn-after-reading capsule has already been viewed and destroyed.")
                                    }
                                    val d = SecureShareManager.decryptCapsule(c, passphraseInput)
                                    if (c.burnAfterReading) {
                                        SecureShareManager.markCapsuleBurned(context, c.id)
                                    }
                                    c to d
                                }
                                parsedCapsule = capsule
                                decryptedItem = decrypted
                            } catch (e: ShareExpiredException) {
                                errorMessage = e.message ?: "This capsule has expired."
                            } catch (e: ShareDecryptionException) {
                                errorMessage = e.message ?: "Decryption failed. Please verify the passphrase."
                            } catch (e: Exception) {
                                errorMessage = "Invalid share token format: ${e.message}"
                            } finally {
                                isDecrypting = false
                            }
                        }
                    },
                    enabled = !isDecrypting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("decrypt_share_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanIce,
                        contentColor = Color(0xFF002933)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isDecrypting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF002933),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DECRYPT & VERIFY PAYLOAD",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // SUCCESS DECRYPTED VIEW
                val item = decryptedItem!!

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldNeon.copy(alpha = 0.12f))
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = EmeraldNeon,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DECRYPTED & AUTHENTICATED",
                                color = EmeraldNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = CyanIce,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.remainingTimeText,
                                color = CyanIce,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (item.burnAfterReading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CrimsonAlert.copy(alpha = 0.12f))
                            .border(1.dp, CrimsonAlert.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "BURN AFTER READING • CAPSULE DESTROYED FOR RE-OPENING",
                            color = CrimsonAlert,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Item Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryIcon(
                        category = item.category,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = item.category.displayName,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Username field (if not empty)
                if (item.username.isNotEmpty()) {
                    Text(
                        text = "USERNAME / IDENTIFIER",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.username,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(
                            onClick = { onCopyText("Username", item.username, false) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy username",
                                tint = CyanIce,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Password field (if not empty)
                if (item.password.isNotEmpty()) {
                    Text(
                        text = "PASSWORD",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSecretVisible) item.password else "•".repeat(item.password.length.coerceIn(8, 24)),
                            color = EmeraldNeon,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            IconButton(
                                onClick = { isSecretVisible = !isSecretVisible },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onCopyText("Password", item.password, true) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy password",
                                    tint = EmeraldNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Live Password Strength Meter
                if (item.passwordStrength != null) {
                    PasswordStrengthCard(
                        strength = item.passwordStrength,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Payment Card fields (if present)
                if (item.cardHolder.isNotEmpty() || item.cardNumber.isNotEmpty() || item.cardExpiry.isNotEmpty() || item.cardCvv.isNotEmpty()) {
                    Text(
                        text = "PAYMENT CARD",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.cardHolder.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Holder: ${item.cardHolder}",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onCopyText("Cardholder", item.cardHolder, false) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy cardholder",
                                        tint = CyanIce,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        if (item.cardNumber.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSecretVisible) item.cardNumber else "•••• ${item.cardNumber.takeLast(4)}",
                                    color = EmeraldNeon,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(
                                        onClick = { isSecretVisible = !isSecretVisible },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle card details visibility",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onCopyText("Card Number", item.cardNumber, true) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy card number",
                                            tint = EmeraldNeon,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (item.cardExpiry.isNotEmpty() || item.cardCvv.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val expiryPart = if (item.cardExpiry.isNotEmpty()) "Exp: ${item.cardExpiry}" else ""
                                val cvvPart = if (item.cardCvv.isNotEmpty()) {
                                    "CVV: ${if (isSecretVisible) item.cardCvv else "•••"}"
                                } else ""
                                Text(
                                    text = listOf(expiryPart, cvvPart).filter { it.isNotEmpty() }.joinToString(" • "),
                                    color = AmberWarning,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.cardCvv.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onCopyText("CVV", item.cardCvv, true) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy CVV",
                                            tint = EmeraldNeon,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Cryptographic Key fields (if present)
                if (item.keyValue.isNotEmpty()) {
                    Text(
                        text = item.keyType.ifEmpty { "CRYPTOGRAPHIC KEY" }.uppercase(),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSecretVisible) item.keyValue else "••••••••••••••••",
                            color = EmeraldNeon,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            IconButton(
                                onClick = { isSecretVisible = !isSecretVisible },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key visibility",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onCopyText("Key", item.keyValue, true) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy key",
                                    tint = EmeraldNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // TOTP Secret (if present)
                if (item.totpSecret.isNotEmpty()) {
                    Text(
                        text = "2FA TOTP SECRET",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSecretVisible) item.totpSecret else "••••••••••••",
                            color = CyanIce,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onCopyText("2FA Secret", item.totpSecret, true) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy 2FA secret",
                                tint = CyanIce,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // URL (if present)
                if (item.url.isNotEmpty()) {
                    Text(
                        text = "SERVICE / URL",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.url,
                            color = CyanIce,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onCopyText("URL", item.url, false) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy URL",
                                tint = CyanIce,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Notes (if present)
                if (item.notes.isNotEmpty()) {
                    Text(
                        text = "NOTES",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = item.notes,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ACTIONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            decryptedItem = null
                            rawTokenInput = ""
                            passphraseInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlackBoxSurfaceVariant,
                            contentColor = TextSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reset", fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = {
                            onSaveToVault(item)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldNeon,
                            contentColor = Color(0xFF003816)
                        ),
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp)
                            .testTag("save_shared_to_vault_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAVE TO MY VAULT",
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
