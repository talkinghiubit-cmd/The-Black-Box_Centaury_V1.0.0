package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.security.BiometricAuthHelper
import com.example.security.BlackBoxSecurity
import com.example.ui.VaultUiState
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.components.SecurityBadge
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

import com.example.security.BiometricManagerWrapper

@Composable
fun LockScreen(
    uiState: VaultUiState,
    biometricManager: BiometricManagerWrapper = BiometricManagerWrapper.getInstance(LocalContext.current),
    onUnlockPassword: (String) -> Unit,
    onUnlockBiometrics: () -> Unit,
    onInitializeVault: (String, Boolean) -> Unit,
    onTriggerBiometricPrompt: () -> Unit,
    onTriggerVoiceUnlock: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isBiometricHardwareAvailable = remember { biometricManager.isReady() }
    val modalityLabel = remember { biometricManager.getModalityDisplayName() }
    val actionHint = remember { biometricManager.getSensorActionHint() }

    if (uiState.isLoading && !uiState.isInitialized) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BlackBoxPitch),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = EmeraldNeon,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.initializing_enclave),
                    fontFamily = FontFamily.Monospace,
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
            passwordLockoutRemainingSeconds = uiState.passwordLockoutRemainingSeconds,
            errorMessage = uiState.errorMessage,
            modalityLabel = modalityLabel,
            actionHint = actionHint,
            onUnlock = onUnlockPassword,
            onTriggerBiometric = onTriggerBiometricPrompt,
            onTriggerVoiceUnlock = onTriggerVoiceUnlock,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            modifier = modifier
        )
    }
}

@Composable
private fun VaultSetupView(
    isLoading: Boolean,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBoxPitch)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Vault Logo / Graphic
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BlackBoxSurfaceVariant)
                .border(1.5.dp, EmeraldNeon, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Security Shield",
                tint = EmeraldNeon,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.setup_vault_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.setup_vault_subtitle),
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Master Password Input
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master Password") },
            placeholder = { Text("Enter a strong master password") },
            singleLine = true,
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
                focusedContainerColor = BlackBoxSurface,
                unfocusedContainerColor = BlackBoxSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_master_password_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (password.isNotEmpty()) {
            PasswordStrengthBar(strength = strength)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Confirm Password Input
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Master Password") },
            placeholder = { Text("Re-type master password") },
            singleLine = true,
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
                focusedContainerColor = BlackBoxSurface,
                unfocusedContainerColor = BlackBoxSurface
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 4.dp)
            )
        }

        if (isBiometricSupported) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.dp, BlackBoxBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Unlock with $modalityLabel",
                            fontSize = 12.sp,
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

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = CrimsonAlert,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onInitialize(password, enableBiometrics) },
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldNeon,
                contentColor = Color(0xFF003816),
                disabledContainerColor = BlackBoxSurfaceVariant,
                disabledContentColor = TextMuted
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
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
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Notice
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
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onOpenPrivacyPolicy,
            modifier = Modifier.testTag("setup_privacy_notice_button")
        ) {
            Text(
                text = stringResource(R.string.privacy_notice_link),
                fontSize = 11.sp,
                color = CyanIce
            )
        }
    }
}

@Composable
private fun VaultLockedView(
    isLoading: Boolean,
    isBiometricEnabled: Boolean,
    isVoiceBiometricEnabled: Boolean = false,
    passwordLockoutRemainingSeconds: Int = 0,
    errorMessage: String?,
    modalityLabel: String = "Biometrics",
    actionHint: String = "Authenticate to decrypt vault",
    onUnlock: (String) -> Unit,
    onTriggerBiometric: () -> Unit,
    onTriggerVoiceUnlock: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val isPasswordLockedOut = passwordLockoutRemainingSeconds > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBoxPitch)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Vault Lock Emblem
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BlackBoxSurfaceVariant)
                .border(2.dp, EmeraldNeon, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Vault Locked",
                tint = EmeraldNeon,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.app_brand_upper),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        SecurityBadge(text = stringResource(R.string.vault_locked_badge))

        Spacer(modifier = Modifier.height(32.dp))

        // Master Password Entry
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Enter Master Password") },
            singleLine = true,
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
                focusedContainerColor = BlackBoxSurface,
                unfocusedContainerColor = BlackBoxSurface
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(CrimsonAlert.copy(alpha = 0.14f))
                    .border(1.dp, CrimsonAlert.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("password_lockout_banner")
            ) {
                Text(
                    text = "BRUTE-FORCE COOLDOWN ACTIVE • RETRY IN ${passwordLockoutRemainingSeconds}s",
                    color = CrimsonAlert,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
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
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onUnlock(password) },
            enabled = password.isNotEmpty() && !isLoading && !isPasswordLockedOut,
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldNeon,
                contentColor = Color(0xFF003816),
                disabledContainerColor = BlackBoxSurfaceVariant,
                disabledContentColor = TextMuted
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
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
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        if (isBiometricEnabled) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onTriggerBiometric,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlackBoxSurfaceVariant,
                    contentColor = EmeraldNeon
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
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
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (isVoiceBiometricEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onTriggerVoiceUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlackBoxSurfaceVariant,
                    contentColor = CyanIce
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, CyanIce.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
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
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = stringResource(R.string.strictly_offline_footer),
            fontSize = 10.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = onOpenPrivacyPolicy,
            modifier = Modifier.testTag("lock_privacy_notice_button")
        ) {
            Text(
                text = stringResource(R.string.privacy_notice_link),
                fontSize = 11.sp,
                color = CyanIce
            )
        }
    }
}
