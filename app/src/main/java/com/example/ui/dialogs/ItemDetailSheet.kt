package com.example.ui.dialogs

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.VaultAttachment
import com.example.model.VaultCategory
import com.example.model.VaultItem
import com.example.nfc.ArmedNfcCardSession
import com.example.nfc.NfcContactlessHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.NfcPaymentSession
import com.example.security.TotpAuthenticatorService
import com.example.security.TotpValidationResult
import com.example.ui.components.CategoryIcon
import com.example.ui.components.IncognitoKeyboardArea
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxPitch
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
fun ItemDetailSheet(
    item: VaultItem,
    totpSecondsRemaining: Int = 30,
    isBreachChecking: Boolean = false,
    activeAttachmentPreview: Pair<VaultAttachment, ByteArray>? = null,
    armedNfcSession: ArmedNfcCardSession? = null,
    nfcRemainingSeconds: Int = 0,
    nfcHardwareStatus: NfcHardwareStatus = NfcHardwareStatus(),
    onArmNfcPayment: () -> Unit = {},
    onDisarmNfcPayment: () -> Unit = {},
    onRefreshNfcStatus: () -> Unit = {},
    onEdit: () -> Unit,
    onShare: () -> Unit = {},
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyText: (String, String, Boolean) -> Unit,
    onCheckBreach: () -> Unit = {},
    onAddAttachmentUri: (Uri) -> Unit = {},
    onPreviewAttachment: (VaultAttachment) -> Unit = {},
    onDeleteAttachment: (VaultAttachment) -> Unit = {},
    onDismissAttachmentPreview: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var passwordRevealed by remember { mutableStateOf(false) }
    var cvvRevealed by remember { mutableStateOf(false) }

    LaunchedEffect(item.id, item.category) {
        if (item.category == VaultCategory.CARD) {
            onRefreshNfcStatus()
        }
    }

    val formattedDate = remember(item.updatedAt) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(item.updatedAt))
    }

    // Password age in days
    val passwordAgeDays = remember(item.passwordUpdatedAt) {
        val diff = System.currentTimeMillis() - item.passwordUpdatedAt
        (diff / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
    }

    // File picker for adding attachment
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onAddAttachmentUri(uri)
        }
    }

    // Live TOTP token using full Google Authenticator protocol
    val totpToken = remember(item.totpSecret, totpSecondsRemaining) {
        if (item.totpSecret.isNotBlank()) {
            TotpAuthenticatorService.generateToken(item.totpSecret)
        } else null
    }
    var showTotpVerifier by remember(item.id) { mutableStateOf(false) }
    var candidateTotpInput by remember(item.id) { mutableStateOf("") }
    var totpVerifyResult by remember(item.id) { mutableStateOf<TotpValidationResult?>(null) }

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
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(category = item.category)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            SecurityBadge(text = item.category.displayName.uppercase())
                        }
                    }
                    Row {
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("detail_favorite_button")) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) AmberWarning else TextMuted
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("detail_close_button")) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // TOTP 2FA Token Card (if configured)
                if (totpToken != null) {
                    val cfg = totpToken.config
                    val periodProgress = (totpToken.remainingSeconds.toFloat() / totpToken.periodSeconds.toFloat())
                        .coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BlackBoxSurfaceVariant)
                            .border(1.dp, CyanIce.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                            .testTag("detail_totp_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = CyanIce,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.totp_section_header),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanIce
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = buildString {
                                        append("${cfg.algorithm.displayName} • ${cfg.digits} digits • ${cfg.periodSeconds}s")
                                        if (cfg.issuer.isNotBlank()) append(" • ${cfg.issuer}")
                                    },
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = totpToken.formattedCode,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.testTag("detail_totp_code")
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Circular countdown
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(BlackBoxSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { periodProgress },
                                        modifier = Modifier.size(38.dp),
                                        color = if (totpToken.remainingSeconds > 5) CyanIce else CrimsonAlert,
                                        strokeWidth = 2.5.dp,
                                        trackColor = TextSecondary.copy(alpha = 0.2f)
                                    )
                                    Text(
                                        text = "${totpToken.remainingSeconds}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (totpToken.remainingSeconds > 5) CyanIce else CrimsonAlert
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { onCopyText("2FA Code", totpToken.code, true) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("detail_copy_totp_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy 2FA Code",
                                        tint = CyanIce,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    showTotpVerifier = !showTotpVerifier
                                    totpVerifyResult = null
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("totp_verify_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = stringResource(R.string.totp_verify_toggle),
                                    tint = EmeraldNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.totp_verify_toggle),
                                    color = EmeraldNeon,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            TextButton(
                                onClick = {
                                    val exportConfig = cfg.copy(
                                        issuer = cfg.issuer.ifBlank { item.title },
                                        accountName = cfg.accountName.ifBlank { item.username }
                                    )
                                    val uri = TotpAuthenticatorService.buildOtpAuthUri(exportConfig)
                                    onCopyText("Google Auth URI", uri, true)
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("totp_copy_uri_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = stringResource(R.string.totp_copy_uri_cta),
                                    tint = CyanIce,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.totp_copy_uri_cta),
                                    color = CyanIce,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (showTotpVerifier) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = candidateTotpInput,
                                    onValueChange = { raw ->
                                        candidateTotpInput = raw.filter { it.isDigit() }.take(cfg.digits)
                                        totpVerifyResult = null
                                    },
                                    label = { Text(stringResource(R.string.totp_verify_input_label), fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyanIce,
                                        unfocusedBorderColor = BlackBoxBorder,
                                        focusedContainerColor = BlackBoxPitch,
                                        unfocusedContainerColor = BlackBoxPitch,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("totp_verify_code_input")
                                )

                                Button(
                                    onClick = {
                                        totpVerifyResult = TotpAuthenticatorService.verifyCode(
                                            storedSeedOrUri = item.totpSecret,
                                            candidateCode = candidateTotpInput
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanIce,
                                        contentColor = BlackBoxPitch
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("totp_verify_submit_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.totp_verify_cta),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            totpVerifyResult?.let { res ->
                                Spacer(modifier = Modifier.height(6.dp))
                                val badgeColor = if (res.isValid) EmeraldNeon else CrimsonAlert
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor.copy(alpha = 0.12f))
                                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("totp_verify_result_banner")
                                ) {
                                    Icon(
                                        imageVector = if (res.isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                        contentDescription = res.message,
                                        tint = badgeColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = res.message,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

            // Category Fields
            when (item.category) {
                VaultCategory.LOGIN -> {
                    if (item.username.isNotEmpty()) {
                        DetailFieldRow(
                            label = "Username / Email",
                            value = item.username,
                            onCopy = { onCopyText("Username", item.username, false) }
                        )
                    }
                    if (item.password.isNotEmpty()) {
                        DetailSecretRow(
                            label = "Password",
                            secret = item.password,
                            isRevealed = passwordRevealed,
                            onToggleReveal = { passwordRevealed = !passwordRevealed },
                            onCopy = { onCopyText("Password", item.password, true) }
                        )

                        // HaveIBeenPwned & Expiry Status Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BlackBoxSurfaceVariant)
                                .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when {
                                        item.pwnedBreachCount > 0 -> {
                                            Icon(
                                                imageVector = Icons.Default.Dangerous,
                                                contentDescription = "Breached password",
                                                tint = CrimsonAlert,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "LEAKED: ${item.pwnedBreachCount} TIMES",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = CrimsonAlert
                                            )
                                        }
                                        item.pwnedBreachCount < 0 -> {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Not yet scanned",
                                                tint = AmberWarning,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "NOT YET SCANNED",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberWarning
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = "Verified clean",
                                                tint = EmeraldNeon,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "VERIFIED CLEAN (0 BREACHES)",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldNeon
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Password age: $passwordAgeDays days ${if (passwordAgeDays > 90) "(Expired)" else ""} • k-Anonymity",
                                    fontSize = 10.sp,
                                    color = if (passwordAgeDays > 90) AmberWarning else TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = onCheckBreach,
                                enabled = !isBreachChecking,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BlackBoxSurface,
                                    contentColor = CyanIce
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(6.dp))
                                    .testTag("verify_hibp_button")
                            ) {
                                if (isBreachChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = CyanIce,
                                        strokeWidth = 1.5.dp
                                    )
                                } else {
                                    Text("Verify HIBP", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    if (item.url.isNotEmpty()) {
                        DetailFieldRow(
                            label = "Website URL",
                            value = item.url,
                            onCopy = { onCopyText("URL", item.url, false) }
                        )
                    }
                }
                VaultCategory.NOTE -> {
                    DetailTextRow(
                        label = "Encrypted Note Content",
                        text = item.notes,
                        onCopy = { onCopyText("Note", item.notes, true) }
                    )
                }
                VaultCategory.CARD -> {
                    val detectedNetwork = remember(item.cardNumber) {
                        NfcPaymentSession.detectCardNetwork(item.cardNumber)
                    }
                    val maskedLast4 = remember(item.cardNumber) {
                        NfcPaymentSession.formatMaskedLast4(item.cardNumber)
                    }
                    val isThisCardArmed = armedNfcSession?.itemId == item.id && nfcRemainingSeconds > 0
                    val hasValidDigits = item.cardNumber.filter(Char::isDigit).length >= 4
                    val panelAccent = if (isThisCardArmed) EmeraldNeon else CyanIce

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BlackBoxSurfaceVariant)
                            .border(
                                1.dp,
                                panelAccent.copy(alpha = if (isThisCardArmed) 0.7f else 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(14.dp)
                            .testTag("detail_nfc_pay_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contactless,
                                    contentDescription = stringResource(R.string.nfc_pay_cta),
                                    tint = panelAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.nfc_pay_title),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = panelAccent
                                )
                            }
                            SecurityBadge(
                                text = if (isThisCardArmed) "ARMED ${nfcRemainingSeconds}s" else "${detectedNetwork.displayName} $maskedLast4",
                                color = if (isThisCardArmed) EmeraldNeon else CyanIce
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.nfc_pay_advantage_summary),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        if (isThisCardArmed && armedNfcSession != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BlackBoxPitch)
                                    .border(1.dp, EmeraldNeon.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "HOLD PHONE NEAR CONTACTLESS TERMINAL",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldNeon
                                    )
                                    Text(
                                        text = "Session ${armedNfcSession.tokenReference} • ${armedNfcSession.tapExchangeCount} tap(s) • RAM-only",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )
                                }
                                Text(
                                    text = "${nfcRemainingSeconds}s",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldNeon
                                )
                            }
                        }

                        if (!item.isNfcPayEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pay with NFC is disabled on this card. Tap Edit to enable contactless payments.",
                                fontSize = 11.sp,
                                color = AmberWarning
                            )
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isThisCardArmed) {
                                    Button(
                                        onClick = onDisarmNfcPayment,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BlackBoxSurface,
                                            contentColor = CrimsonAlert
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .border(1.dp, CrimsonAlert.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .testTag("disarm_nfc_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.nfc_pay_disarm_cta),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = onArmNfcPayment,
                                        enabled = hasValidDigits,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmeraldNeon,
                                            contentColor = Color(0xFF003816)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("pay_with_nfc_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Contactless,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.nfc_pay_cta),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (nfcHardwareStatus.hasNfcHardware && !nfcHardwareStatus.isNfcEnabled) {
                                    OutlinedButton(
                                        onClick = { NfcContactlessHelper.openNfcSettings(context) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("open_nfc_settings_button")
                                    ) {
                                        Text(
                                            text = "NFC Settings",
                                            fontSize = 11.sp,
                                            color = AmberWarning,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (nfcHardwareStatus.hasHceSupport && !nfcHardwareStatus.isDefaultPaymentService) {
                                    OutlinedButton(
                                        onClick = { NfcContactlessHelper.openTapAndPayDefaultSettings(context) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("open_tap_and_pay_button")
                                    ) {
                                        Text(
                                            text = "Tap & Pay",
                                            fontSize = 11.sp,
                                            color = CyanIce,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (item.cardHolder.isNotEmpty()) {
                        DetailFieldRow(
                            label = "Cardholder",
                            value = item.cardHolder,
                            onCopy = { onCopyText("Cardholder", item.cardHolder, false) }
                        )
                    }
                    if (item.cardNumber.isNotEmpty()) {
                        DetailFieldRow(
                            label = "Card Number",
                            value = item.cardNumber,
                            onCopy = { onCopyText("Card Number", item.cardNumber, true) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (item.cardExpiry.isNotEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                DetailFieldRow(
                                    label = "Expiry",
                                    value = item.cardExpiry,
                                    onCopy = { onCopyText("Expiry", item.cardExpiry, false) }
                                )
                            }
                        }
                        if (item.cardCvv.isNotEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                DetailSecretRow(
                                    label = "CVV",
                                    secret = item.cardCvv,
                                    isRevealed = cvvRevealed,
                                    onToggleReveal = { cvvRevealed = !cvvRevealed },
                                    onCopy = { onCopyText("CVV", item.cardCvv, true) }
                                )
                            }
                        }
                    }
                }
                VaultCategory.KEY -> {
                    DetailFieldRow(
                        label = "Key Type",
                        value = item.keyType,
                        onCopy = { onCopyText("Key Type", item.keyType, false) }
                    )
                    DetailTextRow(
                        label = "Key Body / Secret",
                        text = item.keyValue,
                        isMono = true,
                        onCopy = { onCopyText("Key", item.keyValue, true) }
                    )
                }
            }

            if (item.category != VaultCategory.NOTE && item.notes.isNotEmpty()) {
                DetailTextRow(
                    label = "Encrypted Notes",
                    text = item.notes,
                    onCopy = { onCopyText("Notes", item.notes, false) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Encrypted Attachments Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ENCRYPTED ATTACHMENTS (${item.attachments.size})",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )
                TextButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Add attachment",
                        tint = EmeraldNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add File", color = EmeraldNeon, fontSize = 12.sp)
                }
            }

            if (item.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                item.attachments.forEach { att ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlackBoxSurfaceVariant)
                            .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPreviewAttachment(att) }
                        ) {
                            Text(
                                text = att.fileName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            val sizeKb = (att.fileSizeBytes / 1024).coerceAtLeast(1)
                            Text(
                                text = "${sizeKb} KB • ${att.mimeType}",
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onPreviewAttachment(att) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "View",
                                    tint = CyanIce,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(onClick = { onDeleteAttachment(att) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = CrimsonAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Last updated: $formattedDate",
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxSurfaceVariant,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                        .testTag("detail_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = EmeraldNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit")
                }

                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxSurfaceVariant,
                        contentColor = CyanIce
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .border(1.dp, CyanIce.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .testTag("detail_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = CyanIce,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }

                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxSurfaceVariant,
                        contentColor = CrimsonAlert
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .border(1.dp, CrimsonAlert.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .testTag("detail_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = CrimsonAlert,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
        }
    }

    // Attachment Preview Dialog
    if (activeAttachmentPreview != null) {
        val (att, bytes) = activeAttachmentPreview
        val isImage = att.mimeType.startsWith("image/")
        val bitmap = remember(bytes) {
            if (isImage) {
                try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    null
                }
            } else null
        }

        AlertDialog(
            onDismissRequest = onDismissAttachmentPreview,
            title = {
                Text(
                    text = att.fileName,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = att.fileName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        val textContent = remember(bytes) {
                            try {
                                String(bytes.take(2000).toByteArray(), Charsets.UTF_8)
                            } catch (e: Exception) {
                                "Binary content (${bytes.size} bytes)"
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(BlackBoxSurface)
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = textContent,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Decrypted on-device in memory • Purged when closed",
                        fontSize = 10.sp,
                        color = EmeraldNeon,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismissAttachmentPreview,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                ) {
                    Text("Close", color = Color(0xFF003816))
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Encrypted Item?", color = TextPrimary) },
            text = {
                Text(
                    text = "Are you sure you want to permanently purge \"${item.title}\" and all attached files from this vault?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Purge Item", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun DetailFieldRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(BlackBoxSurfaceVariant)
                .border(1.dp, BlackBoxBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = EmeraldNeon,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailSecretRow(
    label: String,
    secret: String,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(BlackBoxSurfaceVariant)
                .border(1.dp, BlackBoxBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isRevealed) secret else "••••••••••••••••",
                color = if (isRevealed) EmeraldNeon else TextSecondary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Row {
                IconButton(onClick = onToggleReveal, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Reveal",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = EmeraldNeon,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailTextRow(
    label: String,
    text: String,
    isMono: Boolean = false,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = EmeraldNeon,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(BlackBoxSurfaceVariant)
                .border(1.dp, BlackBoxBorder, RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
                lineHeight = 18.sp
            )
        }
    }
}
