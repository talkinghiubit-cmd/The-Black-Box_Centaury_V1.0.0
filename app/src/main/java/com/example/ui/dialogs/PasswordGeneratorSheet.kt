package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.PasswordOptions
import com.example.ui.PasswordGeneratorConfig
import com.example.ui.VaultUiState
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorSheet(
    uiState: VaultUiState,
    onConfigChanged: (PasswordGeneratorConfig) -> Unit,
    onRegenerate: () -> Unit,
    onCopyPassword: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val config = uiState.passwordConfig

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
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldContainer)
                            .border(1.dp, EmeraldNeon, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SECURE PASSWORD GENERATOR",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_generator_sheet")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Selector: Characters vs Memorable Passphrase
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !config.isPassphraseMode,
                    onClick = { onConfigChanged(config.copy(isPassphraseMode = false)) },
                    label = { Text("Random Characters", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldNeon,
                        containerColor = BlackBoxSurfaceVariant,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = BlackBoxBorder,
                        selectedBorderColor = EmeraldNeon,
                        enabled = true,
                        selected = !config.isPassphraseMode
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = config.isPassphraseMode,
                    onClick = { onConfigChanged(config.copy(isPassphraseMode = true)) },
                    label = { Text("Memorable Passphrase", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldNeon,
                        containerColor = BlackBoxSurfaceVariant,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = BlackBoxBorder,
                        selectedBorderColor = EmeraldNeon,
                        enabled = true,
                        selected = config.isPassphraseMode
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Output Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BlackBoxSurfaceVariant)
                    .border(1.5.dp, EmeraldNeon, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.generatedPassword,
                            color = EmeraldNeon,
                            fontSize = if (uiState.generatedPassword.length > 28) 14.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("regenerate_password_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate",
                                    tint = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { onCopyPassword(uiState.generatedPassword) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("copy_generated_password_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = EmeraldNeon
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    PasswordStrengthBar(strength = uiState.generatedPasswordStrength)

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "BRUTE-FORCE RESISTANCE: ${uiState.estimatedCrackTime.uppercase()}",
                        fontSize = 10.sp,
                        color = CyanIce,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!config.isPassphraseMode) {
                // Character Mode Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "LENGTH",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Text(
                        text = "${config.length} CHARACTERS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = EmeraldNeon
                    )
                }

                Slider(
                    value = config.length.toFloat(),
                    onValueChange = {
                        onConfigChanged(config.copy(length = it.toInt()))
                    },
                    valueRange = 8f..64f,
                    steps = 55,
                    colors = SliderDefaults.colors(
                        thumbColor = EmeraldNeon,
                        activeTrackColor = EmeraldNeon,
                        inactiveTrackColor = BlackBoxSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_length_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))

                GeneratorToggleRow(
                    label = "Uppercase Letters (A-Z)",
                    checked = config.includeUppercase,
                    onCheckedChange = { onConfigChanged(config.copy(includeUppercase = it)) }
                )

                GeneratorToggleRow(
                    label = "Lowercase Letters (a-z)",
                    checked = config.includeLowercase,
                    onCheckedChange = { onConfigChanged(config.copy(includeLowercase = it)) }
                )

                GeneratorToggleRow(
                    label = "Numbers (0-9)",
                    checked = config.includeNumbers,
                    onCheckedChange = { onConfigChanged(config.copy(includeNumbers = it)) }
                )

                GeneratorToggleRow(
                    label = "Symbols",
                    checked = config.includeSymbols,
                    onCheckedChange = { onConfigChanged(config.copy(includeSymbols = it)) }
                )

                AnimatedVisibility(visible = config.includeSymbols) {
                    Column(modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                        OutlinedTextField(
                            value = config.customSymbols,
                            onValueChange = { onConfigChanged(config.copy(customSymbols = it)) },
                            label = { Text("Allowed Symbols Set") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = BlackBoxBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_symbols_input")
                        )
                    }
                }

                GeneratorToggleRow(
                    label = "Exclude Ambiguous (0, O, 1, l, I, |)",
                    checked = config.excludeAmbiguous,
                    onCheckedChange = { onConfigChanged(config.copy(excludeAmbiguous = it)) }
                )
            } else {
                // Passphrase Mode Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "WORD COUNT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Text(
                        text = "${config.passphraseWords} WORDS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = EmeraldNeon
                    )
                }

                Slider(
                    value = config.passphraseWords.toFloat(),
                    onValueChange = {
                        onConfigChanged(config.copy(passphraseWords = it.toInt()))
                    },
                    valueRange = 3f..8f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = EmeraldNeon,
                        activeTrackColor = EmeraldNeon,
                        inactiveTrackColor = BlackBoxSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("passphrase_words_slider")
                )

                Text(
                    text = "Generates high-entropy, human-memorable passphrases picked from a cryptographic dictionary separated by hyphens with trailing random digits.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onCopyPassword(uiState.generatedPassword) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldNeon,
                    contentColor = Color(0xFF003816)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generator_copy_and_done")
            ) {
                Text(
                    text = "COPY TO CLIPBOARD",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun GeneratorToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextPrimary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = EmeraldNeon,
                checkedTrackColor = EmeraldDark
            )
        )
    }
}
