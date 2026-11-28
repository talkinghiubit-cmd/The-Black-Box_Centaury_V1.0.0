package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.security.BiometricManagerWrapper
import com.example.security.BlackBoxSecurity
import com.example.ui.VaultUiState
import com.example.ui.components.CyberVaultBackdrop
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.components.SecurityBadge
import com.example.ui.components.SecurityTelemetryStrip
import com.example.ui.components.VaultBrandEmblem
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxBorderSubtle
import com.example.ui.theme.BlackBoxDisplayFont
import com.example.ui.theme.BlackBoxMonoFont
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceElevated
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.IndigoCipher
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LockScreen(
    uiState: VaultUiState,
    biometricManager: BiometricManagerWrapper = BiometricManagerWrapper.getInstance(LocalContext.current),
    onUnlockPassword: (String) -> Unit,
    onUnlockBiometrics: () -> Unit,
    onInitializeVault: (String, Boolean) -> Unit,
    onTriggerBiometricPrompt: () -> Unit,
    onTriggerVoiceUnlock: () -> Unit = {},
    onForgotPasswordWipeVault: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isBiometricHardwareAvailable = remember { biometricManager.isStrongBiometricReady() }
    val modalityLabel = remember { biometricManager.getModalityDisplayName() }
    val actionHint = remember { biometricManager.getSensorActionHint() }

    if (uiState.isLoading && !uiState.isInitialized) {
        CyberVaultBackdrop(modifier = modifier) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VaultBrandEmblem(size = 92.dp, statusColor = EmeraldNeon)
                Spacer(modifier = Modifier.height(22.dp))
                CircularProgressIndicator(
                    color = EmeraldNeon,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.initializing_enclave),
                    fontFamily = BlackBoxMonoFont,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    } else if (!uiState.isInitialized) {
        VaultSetupView(
            isLoading = uiState.isLoading,
            infoMessage = uiState.infoMessage,
            errorMessage = uiState.errorMessage,
            isBiometricSupported = isBiometricHardwareAvailable,
            modalityLabel = modalityLabel,
            onInitialize = onInitializeVault,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            modifier = modifier
        )
    } else {
        VaultLockedView(
            isLoading = uiState.isLoading,
            isBiometricEnabled = uiState.isBiometricEnabled,
            isVoiceBiometricEnabled = uiState.isVoiceBiometricEnabled,
            requiresBiometricReauth = uiState.requiresBiometricReauth,
            passwordLockoutRemainingSeconds = uiState.passwordLockoutRemainingSeconds,
            errorMessage = uiState.errorMessage,
            modalityLabel = modalityLabel,
            actionHint = actionHint,
            onUnlock = onUnlockPassword,
            onTriggerBiometric = onTriggerBiometricPrompt,
            onTriggerVoiceUnlock = onTriggerVoiceUnlock,
            onForgotPasswordWipeVault = onForgotPasswordWipeVault,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            modifier = modifier
        )
    }
}

@Composable
private fun VaultSetupView(
    isLoading: Boolean,
    infoMessage: String? = null,
    errorMessage: String?,
    isBiometricSupported: Boolean,
    modalityLabel: String = "Biometrics",
    onInitialize: (String, Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var enableBiometrics by remember { mutableStateOf(isBiometricSupported) }
    val strength = remember(password) { BlackBoxSecurity.evaluatePasswordStrength(password) }

    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
    val canSubmit = password.length >= 8 && passwordsMatch && !isLoading

    CyberVaultBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 500.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VaultBrandEmblem(
                size = 94.dp,
                statusColor = EmeraldNeon
            )

            Spacer(modifier = Modifier.height(16.dp))

            SecurityBadge(
                text = "HARDWARE ENCLAVE INITIALIZATION",
                color = EmeraldNeon
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.setup_vault_title),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BlackBoxDisplayFont,
                letterSpacing = 0.5.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.setup_vault_subtitle),
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            SecurityTelemetryStrip(
                tags = listOf(
                    "AES-256-GCM" to EmeraldNeon,
                    "PBKDF2-100K" to CyanIce,
                    "ZERO-CLOUD" to IndigoCipher
                )
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Zero-Trust Setup Console Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(BlackBoxSurfaceVariant, BlackBoxSurface)
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            colors = listOf(EmeraldNeon.copy(alpha = 0.35f), BlackBoxBorder)
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(18.dp)
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master Password") },
                    placeholder = { Text("Enter a strong master password") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.testTag("toggle_password_visibility")
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility",
                                tint = TextSecondary
                            )
                        }
                    },
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
                        .testTag("setup_master_password_input")
                )

                AnimatedVisibility(visible = password.isNotEmpty()) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        PasswordStrengthBar(strength = strength)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Master Password") },
                    placeholder = { Text("Re-type master password") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSubmit) onInitialize(password, enableBiometrics)
                        }
                    ),
                    isError = confirmPassword.isNotEmpty() && !passwordsMatch,
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
                        .testTag("setup_confirm_password_input")
                )

                if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                    Text(
                        text = "Passwords do not match",
                        color = CrimsonAlert,
                        fontSize = 12.sp,
                        fontFamily = BlackBoxMonoFont,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 4.dp)
                    )
                }

                if (isBiometricSupported) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BlackBoxSurfaceElevated)
                            .border(1.dp, BlackBoxBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometrics",
                                tint = EmeraldNeon,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$modalityLabel Quick Unlock",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Hardware-bound StrongBox key",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        Switch(
                            checked = enableBiometrics,
                            onCheckedChange = { enableBiometrics = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldNeon,
                                checkedTrackColor = EmeraldDark
                            ),
                            modifier = Modifier.testTag("toggle_biometric_setup")
                        )
                    }
                }

                if (infoMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldNeon.copy(alpha = 0.12f))
                            .border(1.dp, EmeraldNeon.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("setup_info_banner")
                    ) {
                        Text(
                            text = infoMessage,
                            color = EmeraldNeon,
                            fontSize = 12.sp,
                            fontFamily = BlackBoxMonoFont,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = CrimsonAlert,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = { onInitialize(password, enableBiometrics) },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldNeon,
                        contentColor = Color(0xFF00331D),
                        disabledContainerColor = BlackBoxSurfaceElevated,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("initialize_vault_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = EmeraldNeon,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.initialize_vault_cta),
                            fontWeight = FontWeight.Bold,
                            fontFamily = BlackBoxMonoFont,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.setup_zero_knowledge_notice),
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier.testTag("setup_privacy_notice_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = CyanIce,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.privacy_notice_link),
                    fontSize = 11.sp,
                    color = CyanIce
                )
            }
        }
    }
}

@Composable
private fun VaultLockedView(
    isLoading: Boolean,
    isBiometricEnabled: Boolean,
    isVoiceBiometricEnabled: Boolean = false,
    requiresBiometricReauth: Boolean = false,
    passwordLockoutRemainingSeconds: Int = 0,
    errorMessage: String?,
    modalityLabel: String = "Biometrics",
    actionHint: String = "Authenticate to decrypt vault",
    onUnlock: (String) -> Unit,
    onTriggerBiometric: () -> Unit,
    onTriggerVoiceUnlock: () -> Unit = {},
    onForgotPasswordWipeVault: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var confirmEraseText by remember { mutableStateOf("") }
    val isPasswordLockedOut = passwordLockoutRemainingSeconds > 0
    val statusAccent = if (isPasswordLockedOut) CrimsonAlert else EmeraldNeon

    CyberVaultBackdrop(
        modifier = modifier,
        accentColor = statusAccent
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 500.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VaultBrandEmblem(
                size = 98.dp,
                statusColor = statusAccent
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_brand_upper),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BlackBoxDisplayFont,
                letterSpacing = 1.5.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            SecurityBadge(
                text = stringResource(R.string.vault_locked_badge),
                color = statusAccent
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecurityTelemetryStrip(
                tags = listOf(
                    "LOCAL KEYSTORE" to EmeraldNeon,
                    "ZERO-KNOWLEDGE" to CyanIce
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Elevated Zero-Trust Authentication Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(BlackBoxSurfaceVariant, BlackBoxSurface)
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            colors = listOf(statusAccent.copy(alpha = 0.4f), BlackBoxBorder)
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = statusAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AUTHENTICATE SESSION",
                            fontSize = 11.sp,
                            fontFamily = BlackBoxMonoFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = if (isBiometricEnabled) "MFA READY" else "PIN / KEY",
                        fontSize = 10.sp,
                        fontFamily = BlackBoxMonoFont,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Enter Master Password") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (password.isNotEmpty() && !isLoading && !isPasswordLockedOut) onUnlock(password)
                        }
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("toggle_lock_password_visibility")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility",
                                    tint = TextSecondary
                                )
                            }
                            if (isBiometricEnabled) {
                                IconButton(
                                    onClick = onTriggerBiometric,
                                    modifier = Modifier.testTag("biometric_quick_unlock_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Biometric Unlock",
                                        tint = EmeraldNeon
                                    )
                                }
                            }
                        }
                    },
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
                        .testTag("unlock_password_input")
                )

                if (isPasswordLockedOut) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CrimsonAlert.copy(alpha = 0.14f))
                            .border(1.dp, CrimsonAlert.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("password_lockout_banner")
                    ) {
                        Text(
                            text = "BRUTE-FORCE COOLDOWN ACTIVE • RETRY IN ${passwordLockoutRemainingSeconds}s",
                            color = CrimsonAlert,
                            fontSize = 11.sp,
                            fontFamily = BlackBoxMonoFont,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = CrimsonAlert,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (requiresBiometricReauth && isBiometricEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanIce.copy(alpha = 0.12f))
                            .border(1.dp, CyanIce.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("biometric_reauth_banner")
                    ) {
                        Text(
                            text = stringResource(R.string.biometric_reauth_required_banner),
                            color = CyanIce,
                            fontSize = 11.sp,
                            fontFamily = BlackBoxMonoFont,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onUnlock(password) },
                    enabled = password.isNotEmpty() && !isLoading && !isPasswordLockedOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldNeon,
                        contentColor = Color(0xFF00331D),
                        disabledContainerColor = BlackBoxSurfaceElevated,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("unlock_vault_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = EmeraldNeon,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isPasswordLockedOut) "LOCKED (${passwordLockoutRemainingSeconds}s)" else stringResource(R.string.unlock_vault_cta),
                            fontWeight = FontWeight.Bold,
                            fontFamily = BlackBoxMonoFont,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (isBiometricEnabled || isVoiceBiometricEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = BlackBoxBorderSubtle
                        )
                        Text(
                            text = "HARDWARE BIOMETRICS",
                            fontSize = 10.sp,
                            fontFamily = BlackBoxMonoFont,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = BlackBoxBorderSubtle
                        )
                    }
                }

                if (isBiometricEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onTriggerBiometric,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlackBoxSurfaceElevated,
                            contentColor = EmeraldNeon
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(1.dp, EmeraldNeon.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .testTag("trigger_biometric_bottom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UNLOCK WITH ${modalityLabel.uppercase()}",
                            fontSize = 12.sp,
                            fontFamily = BlackBoxMonoFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isVoiceBiometricEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onTriggerVoiceUnlock,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlackBoxSurfaceElevated,
                            contentColor = CyanIce
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(1.dp, CyanIce.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .testTag("trigger_voice_biometric_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Unlock with Voice Biometrics",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UNLOCK WITH VOICE BIOMETRICS",
                            fontSize = 12.sp,
                            fontFamily = BlackBoxMonoFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(
                onClick = {
                    confirmEraseText = ""
                    showForgotPasswordDialog = true
                },
                modifier = Modifier.testTag("forgot_master_password_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = stringResource(R.string.forgot_password_cta),
                    tint = CrimsonAlert,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.forgot_password_cta),
                    fontSize = 12.sp,
                    fontFamily = BlackBoxMonoFont,
                    fontWeight = FontWeight.SemiBold,
                    color = CrimsonAlert
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.strictly_offline_footer),
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = BlackBoxMonoFont,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier.testTag("lock_privacy_notice_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = CyanIce,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.privacy_notice_link),
                    fontSize = 11.sp,
                    color = CyanIce
                )
            }
        }
    }

    if (showForgotPasswordDialog) {
        val canConfirmErase = confirmEraseText.trim().equals("ERASE", ignoreCase = true)
        AlertDialog(
            onDismissRequest = {
                showForgotPasswordDialog = false
                confirmEraseText = ""
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("forgot_password_reset_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = CrimsonAlert,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.forgot_password_dialog_title),
                        fontFamily = BlackBoxMonoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonAlert
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.forgot_password_dialog_body),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = confirmEraseText,
                        onValueChange = { confirmEraseText = it },
                        label = { Text(stringResource(R.string.forgot_password_confirm_hint)) },
                        placeholder = { Text("ERASE") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonAlert,
                            unfocusedBorderColor = BlackBoxBorder,
                            focusedContainerColor = BlackBoxPitch,
                            unfocusedContainerColor = BlackBoxPitch,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CrimsonAlert,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_confirm_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForgotPasswordDialog = false
                        confirmEraseText = ""
                        onForgotPasswordWipeVault()
                    },
                    enabled = canConfirmErase,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonAlert,
                        contentColor = Color.White,
                        disabledContainerColor = BlackBoxSurfaceElevated,
                        disabledContentColor = TextMuted
                    ),
                    modifier = Modifier.testTag("forgot_password_confirm_wipe_button")
                ) {
                    Text(
                        text = stringResource(R.string.forgot_password_confirm_cta),
                        fontFamily = BlackBoxMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showForgotPasswordDialog = false
                        confirmEraseText = ""
                    },
                    modifier = Modifier.testTag("forgot_password_cancel_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BlackBoxSurfaceVariant,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
