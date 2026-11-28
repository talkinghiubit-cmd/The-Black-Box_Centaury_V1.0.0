package com.example.ui.dialogs

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.VaultPartition
import com.example.model.VaultCategory
import com.example.nfc.NfcContactlessHelper
import com.example.security.BiometricAuthHelper
import com.example.security.BlackBoxSecurity
import com.example.security.TaskAnalyticsSummary
import com.example.security.TaskLifecycleState
import com.example.security.TrackedTaskType
import com.example.security.VoiceprintTemplate
import com.example.ui.VaultUiState
import com.example.ui.components.IncognitoKeyboardArea
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxMonoFont
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.widget.ConfidentialNotesWidgetProvider
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    uiState: VaultUiState,
    onToggleBiometrics: (Boolean, String) -> Unit,
    onVerifyPasswordForVoice: (String, (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) },
    onEnrollVoiceSamples: (String, List<ShortArray>, (Result<VoiceprintTemplate>) -> Unit) -> Unit = { _, _, cb -> cb(Result.failure(IllegalStateException())) },
    onDisableVoiceBiometrics: (String, (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) },
    onChangeMasterPassword: (String, String, (Boolean) -> Unit) -> Unit,
    onSetAutoLockTimeout: (Int) -> Unit = {},
    onToggleBackgroundGuard: (Boolean) -> Unit = {},
    onRunBackgroundMaintenance: () -> Unit = {},
    onClearTaskAnalytics: () -> Unit = {},
    onRefreshNfcStatus: () -> Unit = {},
    onSetupDuressPin: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveDuressPin: () -> Unit = {},
    onOpenSecurityHealth: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onWipeVault: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isBiometricAvailable = remember { BiometricAuthHelper.isStrongBiometricAvailable(context) }
    val autofillSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    var isAutofillServiceActive by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
            } else false
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    isAutofillServiceActive =
                        context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
                }
                onRefreshNfcStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showChangePasswordModal by remember { mutableStateOf(false) }
    var showBiometricPasswordPrompt by remember { mutableStateOf(false) }
    var biometricPendingState by remember { mutableStateOf(false) }
    var showVoicePasswordPrompt by remember { mutableStateOf(false) }
    var voiceActionIsDisable by remember { mutableStateOf(false) }
    var verifiedVoiceEnrollmentPassword by remember { mutableStateOf<String?>(null) }
    var showDuressPinModal by remember { mutableStateOf(false) }
    var showRemoveDuressConfirm by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

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
                            .background(BlackBoxSurfaceVariant)
                            .border(1.dp, TextSecondary, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "VAULT SECURITY SETTINGS",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_settings_sheet")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Security Health & Breach Audit Shortcut Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable {
                        onDismiss()
                        onOpenSecurityHealth()
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Security Health Score: ${uiState.healthReport.overallScore}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "HaveIBeenPwned audit, weak & expired passwords",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                    SecurityBadge(text = "AUDIT")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Duress Partition / Decoy PIN Configuration
            // Only visible if in Primary partition (Plausible Deniability guarantee)
            if (uiState.activePartitionId == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(
                            1.dp,
                            if (uiState.hasDuressPin) EmeraldNeon.copy(alpha = 0.5f) else BlackBoxBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DURESS PIN / DECOY VAULT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            SecurityBadge(
                                text = if (uiState.hasDuressPin) "CONFIGURED" else "NOT SET",
                                color = if (uiState.hasDuressPin) EmeraldNeon else TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "A separate PIN that opens an isolated, believable decoy vault. Defends against physical coercion with zero cross-partition link.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showDuressPinModal = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.hasDuressPin) BlackBoxSurface else EmeraldNeon,
                                    contentColor = if (uiState.hasDuressPin) EmeraldNeon else Color(0xFF003816)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .border(
                                        1.dp,
                                        if (uiState.hasDuressPin) EmeraldNeon.copy(alpha = 0.4f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("configure_duress_pin_button")
                            ) {
                                Text(
                                    text = if (uiState.hasDuressPin) "Change Duress PIN" else "Configure Duress PIN",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (uiState.hasDuressPin) {
                                Button(
                                    onClick = { showRemoveDuressConfirm = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BlackBoxSurface,
                                        contentColor = CrimsonAlert
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .border(1.dp, CrimsonAlert.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .testTag("disable_duress_pin_button")
                                ) {
                                    Text(
                                        text = "Disable Duress",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Biometric Option
            if (isBiometricAvailable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(1.dp, BlackBoxBorder, RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val modalityName = remember { BiometricAuthHelper.getModalityDisplayName(context) }
                            Text(
                                text = "$modalityName Unlock",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Decrypt vault with $modalityName",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                    Switch(
                        checked = uiState.isBiometricEnabled,
                        onCheckedChange = { newState ->
                            biometricPendingState = newState
                            showBiometricPasswordPrompt = true
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldNeon,
                            checkedTrackColor = EmeraldDark
                        )
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Voice Biometric Recognition Card (Primary partition only)
            if (uiState.activePartitionId == VaultPartition.PRIMARY) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(
                            1.dp,
                            if (uiState.isVoiceBiometricEnabled) EmeraldNeon.copy(alpha = 0.5f) else BlackBoxBorder,
                            RoundedCornerShape(10.dp)
                        )
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
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Voice Biometric Recognition",
                                    tint = EmeraldNeon,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VOICE BIOMETRIC RECOGNITION",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                            }
                            SecurityBadge(
                                text = if (uiState.isVoiceBiometricEnabled) "ACTIVE" else "NOT ENROLLED",
                                color = if (uiState.isVoiceBiometricEnabled) EmeraldNeon else TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "On-device 42-D MFCC & pitch speaker verification sealed via Hardware Keystore. Never uploads voice audio.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    voiceActionIsDisable = false
                                    showVoicePasswordPrompt = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isVoiceBiometricEnabled) BlackBoxSurface else EmeraldNeon,
                                    contentColor = if (uiState.isVoiceBiometricEnabled) EmeraldNeon else Color(0xFF003816)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(
                                        1.dp,
                                        if (uiState.isVoiceBiometricEnabled) EmeraldNeon.copy(alpha = 0.4f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("configure_voice_biometric_button")
                            ) {
                                Text(
                                    text = if (uiState.isVoiceBiometricEnabled) "Re-Enroll Voice" else "Enroll Voiceprint",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (uiState.isVoiceBiometricEnabled) {
                                Button(
                                    onClick = {
                                        voiceActionIsDisable = true
                                        showVoicePasswordPrompt = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BlackBoxSurface,
                                        contentColor = CrimsonAlert
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .border(1.dp, CrimsonAlert.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .testTag("disable_voice_biometric_button")
                                ) {
                                    Text(
                                        text = "Disable Voice",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Inactivity Auto-Lock Duration Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INACTIVITY AUTO-LOCK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text(
                            text = "${uiState.autoLockMinutes} MIN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldNeon
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Automatically locks the vault and purges sensitive decryption keys from RAM when idle.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 5, 10, 15).forEach { mins ->
                            FilterChip(
                                selected = uiState.autoLockMinutes == mins,
                                onClick = { onSetAutoLockTimeout(mins) },
                                label = { Text("${mins}m", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldContainer,
                                    selectedLabelColor = EmeraldNeon,
                                    containerColor = BlackBoxSurface,
                                    labelColor = TextMuted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = BlackBoxBorder,
                                    selectedBorderColor = EmeraldNeon,
                                    enabled = true,
                                    selected = uiState.autoLockMinutes == mins
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Android Autofill Service Card (VIP Chrome & App Autofill)
            if (autofillSupported) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(
                            1.dp,
                            if (isAutofillServiceActive) EmeraldNeon.copy(alpha = 0.5f) else BlackBoxBorder,
                            RoundedCornerShape(10.dp)
                        )
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
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = CyanIce,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.autofill_settings_title),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                            }
                            SecurityBadge(
                                text = if (isAutofillServiceActive) "ACTIVE" else "OPT-IN",
                                color = if (isAutofillServiceActive) EmeraldNeon else TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.autofill_settings_desc),
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAutofillServiceActive) BlackBoxSurface else EmeraldNeon,
                                    contentColor = if (isAutofillServiceActive) EmeraldNeon else Color(0xFF003816)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(
                                        1.dp,
                                        if (isAutofillServiceActive) EmeraldNeon.copy(alpha = 0.4f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("enable_autofill_button")
                            ) {
                                Text(
                                    text = if (isAutofillServiceActive) "Autofill Settings" else stringResource(R.string.autofill_enable_cta),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val chromeIntent = Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
                                            addCategory(Intent.CATEGORY_DEFAULT)
                                            addCategory(Intent.CATEGORY_APP_BROWSER)
                                            addCategory(Intent.CATEGORY_PREFERENCE)
                                        }
                                        context.startActivity(Intent.createChooser(chromeIntent, "Browser Autofill Settings"))
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("chrome_autofill_settings_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.autofill_chrome_cta),
                                    fontSize = 12.sp,
                                    color = CyanIce,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Pay with NFC (Contactless HCE) Card
            val nfcStatus = uiState.nfcHardwareStatus
            val nfcBadgeText = when {
                !nfcStatus.hasNfcHardware -> "NO NFC HARDWARE"
                !nfcStatus.isNfcEnabled -> "NFC OFF"
                nfcStatus.isDefaultPaymentService -> "DEFAULT TAP & PAY"
                else -> "HCE READY"
            }
            val nfcAccent = when {
                !nfcStatus.hasNfcHardware -> TextSecondary
                !nfcStatus.isNfcEnabled -> AmberWarning
                nfcStatus.isDefaultPaymentService -> EmeraldNeon
                else -> CyanIce
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(
                        1.dp,
                        nfcAccent.copy(alpha = 0.45f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(16.dp)
                    .testTag("settings_nfc_card")
            ) {
                Column {
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
                                tint = nfcAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${stringResource(R.string.nfc_pay_title)} (CONTACTLESS)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                        SecurityBadge(text = nfcBadgeText, color = nfcAccent)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.nfc_pay_advantage_summary),
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { NfcContactlessHelper.openNfcSettings(context) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_nfc_toggle_button")
                        ) {
                            Text(
                                text = "NFC Settings",
                                fontSize = 12.sp,
                                color = CyanIce,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { NfcContactlessHelper.openTapAndPayDefaultSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlackBoxSurface,
                                contentColor = EmeraldNeon
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .border(1.dp, EmeraldNeon.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .testTag("settings_tap_and_pay_button")
                        ) {
                            Text(
                                text = "Tap & Pay Default",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Silent Background Vault Guard Card (JobScheduler, zero notifications, low RAM)
            val bgStatus = uiState.backgroundGuardStatus
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(
                        1.dp,
                        if (bgStatus.isEnabled) EmeraldNeon.copy(alpha = 0.45f) else BlackBoxBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
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
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (bgStatus.isEnabled) EmeraldNeon else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.bg_guard_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                        Switch(
                            checked = bgStatus.isEnabled,
                            onCheckedChange = onToggleBackgroundGuard,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldNeon,
                                checkedTrackColor = EmeraldDark
                            ),
                            modifier = Modifier.testTag("background_guard_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.bg_guard_desc),
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val lastSweepText = bgStatus.lastRunAtMillis?.let { ts ->
                        "Last silent sweep: ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))} (${bgStatus.lastCleanedItemsCount} purged)"
                    } ?: if (bgStatus.isJobScheduled) {
                        "OS JobScheduler armed • Battery-friendly idle batching"
                    } else {
                        "Silent sweep ready • Zero persistent RAM footprint"
                    }
                    Text(
                        text = lastSweepText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (bgStatus.isEnabled) CyanIce else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRunBackgroundMaintenance,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_background_sweep_button")
                    ) {
                        Text(
                            text = "Run Silent Sweep Now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldNeon
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // On-Device Task Execution & Stop-Tracking Analytics Card
            TaskExecutionAnalyticsCard(
                analytics = uiState.taskAnalytics,
                onClear = onClearTaskAnalytics
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Confidential Notes Vault Home Screen Widget Card
            val activeWidgetCount = remember(context) {
                ConfidentialNotesWidgetProvider.getActiveWidgetIds(context).size
            }
            var widgetPinNotice by remember { mutableStateOf<String?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(
                        1.dp,
                        if (activeWidgetCount > 0) EmeraldNeon.copy(alpha = 0.45f) else BlackBoxBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
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
                                imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CONFIDENTIAL NOTES WIDGET",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                        SecurityBadge(
                            text = if (activeWidgetCount > 0) "$activeWidgetCount PINNED" else "HOME SCREEN",
                            color = if (activeWidgetCount > 0) EmeraldNeon else AmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pin a Confidential Notes Vault widget to your home screen. Encrypted notes never touch disk cache—locked state shows only sealed note count, while unlocked state offers Masked/Peek view and 1-tap Lock.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                    if (widgetPinNotice != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = widgetPinNotice!!,
                            fontSize = 11.sp,
                            color = CyanIce
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val manager = AppWidgetManager.getInstance(context)
                                val provider = ComponentName(context, ConfidentialNotesWidgetProvider::class.java)
                                if (manager.isRequestPinAppWidgetSupported) {
                                    val requested = manager.requestPinAppWidget(provider, null, null)
                                    widgetPinNotice = if (requested) {
                                        "Launcher prompt opened to add Confidential Notes Vault widget."
                                    } else {
                                        "Long-press your home screen -> Widgets -> The Black-Box to place it."
                                    }
                                } else {
                                    widgetPinNotice = "Long-press your home screen -> Widgets -> Confidential Notes Vault."
                                }
                            } else {
                                widgetPinNotice = "Long-press your home screen -> Widgets -> Confidential Notes Vault."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldNeon,
                            contentColor = Color(0xFF003816)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pin_notes_widget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pin Notes Widget to Home Screen",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Privacy Policy & Data Safety Disclosure Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        onDismiss()
                        onOpenPrivacyPolicy()
                    }
                    .padding(16.dp)
                    .testTag("settings_privacy_policy_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = CyanIce,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.privacy_notice_link),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Local encryption, RECORD_AUDIO, HIBP k-Anonymity & Play Policy",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    SecurityBadge(text = "POLICY", color = CyanIce)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Change Master Password Button
            Button(
                onClick = { showChangePasswordModal = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlackBoxSurfaceVariant,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                    .testTag("open_change_password_button")
            ) {
                Icon(
                    imageVector = Icons.Default.LockReset,
                    contentDescription = null,
                    tint = CyanIce,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ROTATE MASTER PASSWORD",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Wipe Entire Vault Button
            Button(
                onClick = { showWipeConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlackBoxSurfaceVariant,
                    contentColor = CrimsonAlert
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, CrimsonAlert.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .testTag("wipe_vault_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = CrimsonAlert,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ERASE & RESET VAULT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        }
    }

    // Duress PIN Setup Dialog
    if (showDuressPinModal) {
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var autoGenDecoys by remember { mutableStateOf(true) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val canSubmit = pin.length >= 4 && pin == confirmPin

        AlertDialog(
            onDismissRequest = { showDuressPinModal = false },
            title = {
                Text(
                    text = "CONFIGURE DURESS PIN",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                IncognitoKeyboardArea {
                    Column {
                        Text(
                            text = "Set a secondary PIN that triggers the decoy vault. If forced to unlock your phone, enter this PIN to display convincing decoy accounts.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it },
                            label = { Text("Duress PIN / Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { confirmPin = it },
                            label = { Text("Confirm Duress PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = autoGenDecoys,
                                onCheckedChange = { autoGenDecoys = it },
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldNeon)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Populate realistic decoy accounts (Netflix, Spotify, Wi-Fi)",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }

                        if (errorMsg != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = errorMsg!!, color = CrimsonAlert, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pin == confirmPin) {
                            onSetupDuressPin(pin, autoGenDecoys)
                            showDuressPinModal = false
                        } else {
                            errorMsg = "PINs do not match"
                        }
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                ) {
                    Text("Save Duress PIN", color = Color(0xFF003816), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuressPinModal = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Remove Duress PIN Confirm
    if (showRemoveDuressConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveDuressConfirm = false },
            title = { Text("Disable Duress Partition?", color = TextPrimary) },
            text = {
                Text(
                    text = "This will delete the Duress PIN and purge the decoy vault partition records.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveDuressConfirm = false
                        onRemoveDuressPin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Disable & Purge", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDuressConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Biometric Password Confirmation Dialog
    if (showBiometricPasswordPrompt) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBiometricPasswordPrompt = false },
            title = { Text("Confirm Master Password", color = TextPrimary) },
            text = {
                IncognitoKeyboardArea {
                    Column {
                        Text(
                            text = "Enter your Master Password to authenticate biometric key store modification:",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pw,
                            onValueChange = { pw = it },
                            label = { Text("Master Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBiometricPasswordPrompt = false
                        onToggleBiometrics(biometricPendingState, pw)
                    },
                    enabled = pw.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldNeon,
                        contentColor = Color(0xFF003816),
                        disabledContainerColor = BlackBoxSurface,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBiometricPasswordPrompt = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Voice Biometric Master Password Authorization Dialog
    if (showVoicePasswordPrompt) {
        var pw by remember { mutableStateOf("") }
        var authError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showVoicePasswordPrompt = false },
            title = {
                Text(
                    text = if (voiceActionIsDisable) "Disable Voice Biometrics" else "Authorize Voice Enrollment",
                    color = TextPrimary
                )
            },
            text = {
                IncognitoKeyboardArea {
                    Column {
                        Text(
                            text = if (voiceActionIsDisable) {
                                "Enter your Master Password to purge your sealed voiceprint profile:"
                            } else {
                                "Enter your Master Password to seal an encrypted voiceprint in the Android Keystore:"
                            },
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pw,
                            onValueChange = {
                                pw = it
                                authError = null
                            },
                            label = { Text("Master Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_auth_master_password_input")
                        )
                        if (authError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = authError!!, color = CrimsonAlert, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (voiceActionIsDisable) {
                            onDisableVoiceBiometrics(pw) { ok ->
                                if (ok) {
                                    showVoicePasswordPrompt = false
                                } else {
                                    authError = "Master Password incorrect."
                                }
                            }
                        } else {
                            onVerifyPasswordForVoice(pw) { ok ->
                                if (ok) {
                                    showVoicePasswordPrompt = false
                                    verifiedVoiceEnrollmentPassword = pw
                                } else {
                                    authError = "Master Password incorrect."
                                }
                            }
                        }
                    },
                    enabled = pw.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (voiceActionIsDisable) CrimsonAlert else EmeraldNeon,
                        contentColor = if (voiceActionIsDisable) Color.White else Color(0xFF003816),
                        disabledContainerColor = BlackBoxSurface,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Text(if (voiceActionIsDisable) "Purge Voiceprint" else "Continue", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoicePasswordPrompt = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }

    verifiedVoiceEnrollmentPassword?.let { verifiedPw ->
        VoiceEnrollmentDialog(
            masterPassword = verifiedPw,
            onEnrollVoiceSamples = onEnrollVoiceSamples,
            onDismiss = { verifiedVoiceEnrollmentPassword = null }
        )
    }

    // Change Master Password Dialog
    if (showChangePasswordModal) {
        var oldPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmNewPw by remember { mutableStateOf("") }
        var statusMsg by remember { mutableStateOf<String?>(null) }
        val strength = remember(newPw) { BlackBoxSecurity.evaluatePasswordStrength(newPw) }
        val canSubmit = oldPw.isNotEmpty() && newPw.length >= 8 && newPw == confirmNewPw

        AlertDialog(
            onDismissRequest = { showChangePasswordModal = false },
            title = {
                Text(
                    text = "ROTATE MASTER PASSWORD",
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                IncognitoKeyboardArea {
                    Column {
                        Text(
                            text = "All primary records will be decrypted with your old key and re-encrypted with a freshly derived key.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = oldPw,
                            onValueChange = { oldPw = it },
                            label = { Text("Current Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newPw,
                            onValueChange = { newPw = it },
                            label = { Text("New Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (newPw.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            PasswordStrengthBar(strength = strength)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = confirmNewPw,
                            onValueChange = { confirmNewPw = it },
                            label = { Text("Confirm New Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (statusMsg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = statusMsg!!, color = CrimsonAlert, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onChangeMasterPassword(oldPw, newPw) { success ->
                            if (success) {
                                showChangePasswordModal = false
                            } else {
                                statusMsg = "Current password is incorrect."
                            }
                        }
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                ) {
                    Text("Re-Encrypt Vault", color = Color(0xFF003816), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordModal = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Factory Reset Confirmation Dialog
    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Erase Entire Vault?", color = CrimsonAlert, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "WARNING: This permanently purges the local database, cryptographic salts, all partitions, and all encrypted credentials from this device. This operation cannot be undone.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        onDismiss()
                        onWipeVault()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Permanently Erase", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskExecutionAnalyticsCard(
    analytics: TaskAnalyticsSummary,
    onClear: () -> Unit
) {
    val hasStops = analytics.stoppedTasksCount > 0 || analytics.stoppedTaskTypes.isNotEmpty()
    val borderColor = when {
        analytics.osTerminatedCount > 0 -> CrimsonAlert.copy(alpha = 0.5f)
        hasStops -> AmberWarning.copy(alpha = 0.45f)
        else -> BlackBoxBorder
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BlackBoxSurfaceVariant)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(16.dp)
            .testTag("task_analytics_card")
    ) {
        Column {
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
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Task Stop Analytics",
                        tint = if (hasStops) AmberWarning else CyanIce,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TASK STOP & LIFECYCLE ANALYTICS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BlackBoxMonoFont,
                        color = TextPrimary
                    )
                }
                if (analytics.recentEvents.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("clear_task_analytics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Task Analytics",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Local zero-PII engine tracking whether background jobs, timers, scans, and app tasks are active, completed, or stopped by the OS or user.",
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3 Telemetry Metric Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsCounterPill(
                    label = "ACTIVE",
                    value = analytics.activeTasksCount,
                    accent = EmeraldNeon,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task_analytics_active_count")
                )
                AnalyticsCounterPill(
                    label = "DONE",
                    value = analytics.completedTasksCount,
                    accent = CyanIce,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task_analytics_completed_count")
                )
                AnalyticsCounterPill(
                    label = "STOPPED",
                    value = analytics.stoppedTasksCount,
                    accent = if (analytics.stoppedTasksCount > 0) AmberWarning else TextSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task_analytics_stopped_count")
                )
            }

            // Last Stopped Task Diagnostic Banner
            analytics.lastStoppedEvent?.let { stopped ->
                Spacer(modifier = Modifier.height(10.dp))
                val stoppedAccent = if (stopped.state == TaskLifecycleState.OS_TERMINATED) CrimsonAlert else AmberWarning
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BlackBoxPitch)
                        .border(1.dp, stoppedAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LAST STOPPED: ${stopped.taskName.uppercase()}",
                            fontSize = 10.sp,
                            fontFamily = BlackBoxMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = stoppedAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(stopped.timestampMillis)),
                            fontSize = 10.sp,
                            fontFamily = BlackBoxMonoFont,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stopped.stopReasonLabel ?: stopped.state.name,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            // Per-Task Current State Matrix
            if (analytics.taskStatusByType.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TrackedTaskType.entries.forEach { taskType ->
                        val state = analytics.taskStatusByType[taskType] ?: return@forEach
                        val stateColor = when (state) {
                            TaskLifecycleState.STARTED -> EmeraldNeon
                            TaskLifecycleState.COMPLETED -> CyanIce
                            TaskLifecycleState.STOPPED,
                            TaskLifecycleState.CANCELLED -> AmberWarning
                            TaskLifecycleState.OS_TERMINATED,
                            TaskLifecycleState.FAILED -> CrimsonAlert
                        }
                        val stateLabel = when (state) {
                            TaskLifecycleState.STARTED -> "RUN"
                            TaskLifecycleState.COMPLETED -> "OK"
                            TaskLifecycleState.STOPPED -> "STOPPED"
                            TaskLifecycleState.CANCELLED -> "CANCEL"
                            TaskLifecycleState.OS_TERMINATED -> "OS_STOP"
                            TaskLifecycleState.FAILED -> "ERR"
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BlackBoxSurface)
                                .border(1.dp, stateColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${taskType.shortTag}: ",
                                fontSize = 10.sp,
                                fontFamily = BlackBoxMonoFont,
                                color = TextMuted
                            )
                            Text(
                                text = stateLabel,
                                fontSize = 10.sp,
                                fontFamily = BlackBoxMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = stateColor
                            )
                        }
                    }
                }
            }

            // Recent Task Events Log (top 5)
            if (analytics.recentEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "RECENT TASK TELEMETRY (${analytics.recentEvents.size})",
                    fontSize = 10.sp,
                    fontFamily = BlackBoxMonoFont,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                analytics.recentEvents.take(5).forEach { ev ->
                    val evColor = when (ev.state) {
                        TaskLifecycleState.STARTED -> EmeraldNeon
                        TaskLifecycleState.COMPLETED -> CyanIce
                        TaskLifecycleState.STOPPED,
                        TaskLifecycleState.CANCELLED -> AmberWarning
                        TaskLifecycleState.OS_TERMINATED,
                        TaskLifecycleState.FAILED -> CrimsonAlert
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[${ev.state.name}] ${ev.taskName}",
                            fontSize = 10.sp,
                            fontFamily = BlackBoxMonoFont,
                            color = evColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ev.timestampMillis))
                        val durText = ev.durationMs?.let { " • ${it}ms" } ?: ""
                        Text(
                            text = "$timeText$durText",
                            fontSize = 10.sp,
                            fontFamily = BlackBoxMonoFont,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCounterPill(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BlackBoxSurface)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BlackBoxMonoFont,
            color = accent
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = BlackBoxMonoFont,
            color = TextMuted,
            letterSpacing = 0.6.sp
        )
    }
}
