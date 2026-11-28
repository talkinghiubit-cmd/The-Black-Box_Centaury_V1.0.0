package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxDisplayFont
import com.example.ui.theme.BlackBoxMonoFont
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Zero-trust privacy shield overlay rendered when sensitive vault content is hidden
 * during background transitions or app switcher snapshots.
 */
@Composable
fun PrivacyShieldOverlay(
    isBiometricAvailable: Boolean = true,
    isVoiceBiometricEnabled: Boolean = false,
    modalityLabel: String = "Biometrics",
    onTriggerBiometric: () -> Unit,
    onTriggerVoiceUnlock: () -> Unit = {},
    onEnterPassword: () -> Unit,
    modifier: Modifier = Modifier
) {
    CyberVaultBackdrop(
        modifier = modifier.testTag("privacy_shield_overlay"),
        accentColor = AmberWarning
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BlackBoxSurfaceVariant, BlackBoxSurface)
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(EmeraldNeon.copy(alpha = 0.45f), BlackBoxBorder)
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(28.dp)
        ) {
            VaultBrandEmblem(
                size = 86.dp,
                statusColor = AmberWarning
            )

            Spacer(modifier = Modifier.height(18.dp))

            SecurityBadge(
                text = "PRIVACY SHIELD ACTIVE",
                color = AmberWarning
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Sensitive Vault Content Sealed",
                fontSize = 20.sp,
                fontFamily = BlackBoxDisplayFont,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Decryption keys and plaintext secrets are masked and cleared while the enclave is backgrounded.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            if (isBiometricAvailable) {
                ElevatedButton(
                    onClick = onTriggerBiometric,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = EmeraldNeon,
                        contentColor = BlackBoxPitch
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("privacy_shield_biometric_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "UNLOCK WITH ${modalityLabel.uppercase()}",
                        fontFamily = BlackBoxMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isVoiceBiometricEnabled) {
                OutlinedButton(
                    onClick = onTriggerVoiceUnlock,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EmeraldNeon
                    ),
                    border = BorderStroke(1.dp, EmeraldNeon.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("privacy_shield_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = EmeraldNeon
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "UNLOCK WITH VOICE",
                        fontFamily = BlackBoxMonoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = EmeraldNeon
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = onEnterPassword,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CyanIce
                ),
                border = BorderStroke(1.dp, BlackBoxBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("privacy_shield_password_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CyanIce
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ENTER MASTER PASSWORD",
                    fontFamily = BlackBoxMonoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = CyanIce
                )
            }
        }
    }
}
