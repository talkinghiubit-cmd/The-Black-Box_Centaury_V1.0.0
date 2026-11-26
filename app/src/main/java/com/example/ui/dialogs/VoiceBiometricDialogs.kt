package com.example.ui.dialogs

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VoiceUnlockOutcome
import com.example.security.VoiceAudioCaptureSession
import com.example.security.VoiceBiometricEngine
import com.example.security.VoiceprintTemplate
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun VoiceSpectrumVisualizer(
    bands: List<Float>,
    isRecording: Boolean,
    accentColor: Color = EmeraldNeon,
    modifier: Modifier = Modifier
) {
    val safeBands = if (bands.size == 16) bands else List(16) { 0.08f }
    val animatedLevels = safeBands.mapIndexed { idx, level ->
        val animated by animateFloatAsState(
            targetValue = if (isRecording) level.coerceIn(0.08f, 1f) else 0.08f,
            animationSpec = spring(stiffness = 500f),
            label = "SpectrumBar$idx"
        )
        animated
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BlackBoxPitch)
            .border(
                1.dp,
                if (isRecording) accentColor.copy(alpha = 0.6f) else BlackBoxBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            val barCount = animatedLevels.size
            val slotWidth = size.width / barCount
            val barWidth = (slotWidth * 0.56f).coerceAtLeast(4f)
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

            animatedLevels.forEachIndexed { index, level ->
                val barHeight = (size.height * level).coerceAtLeast(6f)
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                val y = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = if (isRecording) accentColor else TextMuted.copy(alpha = 0.4f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEnrollmentDialog(
    masterPassword: String,
    onEnrollVoiceSamples: (String, List<ShortArray>, (Result<VoiceprintTemplate>) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val captureSession = remember { VoiceAudioCaptureSession() }
    val captureState by captureSession.captureState.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(VoiceBiometricEngine.hasAudioPermission(context)) }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) permissionDeniedOnce = true
    }

    val recordedSamples = remember { mutableStateListOf<ShortArray>() }
    val sampleQualityLabels = remember { mutableStateListOf<String>() }
    var isProcessingSample by remember { mutableStateOf(false) }
    var isSealingTemplate by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            captureSession.requestStop()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            captureSession.requestStop()
            onDismiss()
        },
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldContainer)
                            .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Voice Biometrics",
                            tint = EmeraldNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "VOICE BIOMETRIC ENROLLMENT",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text(
                            text = "ON-DEVICE MFCC + HARDWARE KEYSTORE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldNeon
                        )
                    }
                }
                IconButton(
                    onClick = {
                        captureSession.requestStop()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("close_voice_enrollment_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close voice enrollment",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(1.dp, AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "MICROPHONE ACCESS REQUIRED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = AmberWarning
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Microphone access is used exclusively on-device to extract an encrypted 42-dimensional acoustic voiceprint. No audio is ever uploaded or saved.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldNeon,
                                    contentColor = Color(0xFF003816)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("grant_mic_permission_enroll_button")
                            ) {
                                Text("GRANT MICROPHONE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (permissionDeniedOnce) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Settings", color = CyanIce, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // Step Progress Indicators (3 Samples)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (idx in 0 until VoiceBiometricEngine.ENROLLMENT_SAMPLES_REQUIRED) {
                        val completed = idx < recordedSamples.size
                        val active = idx == recordedSamples.size
                        val borderColor = when {
                            completed -> EmeraldNeon
                            active -> CyanIce
                            else -> BlackBoxBorder
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (completed) EmeraldContainer else BlackBoxSurfaceVariant)
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (completed) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Sample ${idx + 1} captured",
                                        tint = EmeraldNeon,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = "SAMPLE ${idx + 1}/3",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (completed) EmeraldNeon else if (active) CyanIce else TextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Spoken Passphrase Guidance
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlackBoxSurfaceVariant)
                        .border(1.dp, BlackBoxBorder, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "CONSISTENT VOCAL PHRASE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"My voice is my passport, verify me\"",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanIce
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Speak clearly in a quiet space for ~2–3 seconds per sample using the same phrase each time.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Audio FFT Spectrum Visualizer
                VoiceSpectrumVisualizer(
                    bands = captureState.spectrumBands,
                    isRecording = captureState.isRecording,
                    accentColor = EmeraldNeon
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val secs = String.format(Locale.US, "%.1fs / 3.2s", captureState.elapsedMs / 1000f)
                    Text(
                        text = if (captureState.isRecording) "CAPTURING PCM AUDIO • $secs" else if (isProcessingSample) "EXTRACTING MFCC VOICEPRINT..." else "${recordedSamples.size} of 3 vocal samples captured",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (captureState.isRecording) EmeraldNeon else TextSecondary
                    )
                    if (recordedSamples.isNotEmpty() && !captureState.isRecording && !isSealingTemplate) {
                        TextButton(
                            onClick = {
                                recordedSamples.clear()
                                sampleQualityLabels.clear()
                                errorMessage = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset samples",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                if (sampleQualityLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    sampleQualityLabels.forEachIndexed { idx, label ->
                        Text(
                            text = "Sample ${idx + 1}: $label",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldNeon
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = CrimsonAlert,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val allCaptured = recordedSamples.size >= VoiceBiometricEngine.ENROLLMENT_SAMPLES_REQUIRED
                if (!allCaptured) {
                    Button(
                        onClick = {
                            if (captureState.isRecording) {
                                captureSession.requestStop()
                            } else {
                                errorMessage = null
                                scope.launch {
                                    val recResult = captureSession.recordUtterance(context, maxDurationMs = 3_200L)
                                    if (recResult.isFailure) {
                                        errorMessage = recResult.exceptionOrNull()?.message ?: "Microphone capture failed."
                                        return@launch
                                    }
                                    val pcm = recResult.getOrThrow()
                                    isProcessingSample = true
                                    val featureResult = withContext(Dispatchers.Default) {
                                        VoiceBiometricEngine.extractSpeakerProfile(pcm)
                                    }
                                    isProcessingSample = false
                                    if (featureResult.isSuccess) {
                                        val feat = featureResult.getOrThrow()
                                        recordedSamples.add(pcm)
                                        sampleQualityLabels.add(
                                            "${feat.voicedFrameCount} voiced frames • ${feat.meanPitchHz.toInt()} Hz F0"
                                        )
                                    } else {
                                        errorMessage = featureResult.exceptionOrNull()?.message
                                            ?: "Sample quality check failed. Speak louder and clearer."
                                    }
                                }
                            }
                        },
                        enabled = !isProcessingSample,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (captureState.isRecording) CrimsonAlert else EmeraldNeon,
                            contentColor = if (captureState.isRecording) Color.White else Color(0xFF003816)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("record_voice_sample_button")
                    ) {
                        if (isProcessingSample) {
                            CircularProgressIndicator(
                                color = EmeraldNeon,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (captureState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (captureState.isRecording) {
                                    "STOP RECORDING (${recordedSamples.size + 1}/3)"
                                } else {
                                    "RECORD SAMPLE ${recordedSamples.size + 1} OF 3"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            errorMessage = null
                            isSealingTemplate = true
                            onEnrollVoiceSamples(masterPassword, recordedSamples.toList()) { result ->
                                isSealingTemplate = false
                                if (result.isSuccess) {
                                    onDismiss()
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message
                                        ?: "Enrollment failed. Please reset and try again."
                                }
                            }
                        },
                        enabled = !isSealingTemplate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldNeon,
                            contentColor = Color(0xFF003816)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("seal_voiceprint_button")
                    ) {
                        if (isSealingTemplate) {
                            CircularProgressIndicator(
                                color = Color(0xFF003816),
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SEAL ENCRYPTED VOICEPRINT",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceUnlockDialog(
    lockoutRemainingSeconds: Int,
    lastSimilarityPercent: Int?,
    onVerifyVoiceSample: (ShortArray, (VoiceUnlockOutcome) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureSession = remember { VoiceAudioCaptureSession() }
    val captureState by captureSession.captureState.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(VoiceBiometricEngine.hasAudioPermission(context)) }
    var isVerifying by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Tap Speak to authenticate with your encrypted voiceprint.") }
    var isError by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            isError = true
            statusMessage = "Microphone permission is required for voice biometric unlock."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            captureSession.requestStop()
        }
    }

    fun startVerificationCapture() {
        if (lockoutRemainingSeconds > 0 || captureState.isRecording || isVerifying) return
        isError = false
        statusMessage = "Listening... Speak your enrolled passphrase now."
        scope.launch {
            val recResult = captureSession.recordUtterance(context, maxDurationMs = 3_200L)
            if (recResult.isFailure) {
                isError = true
                statusMessage = recResult.exceptionOrNull()?.message ?: "Microphone capture error."
                return@launch
            }
            val pcm = recResult.getOrThrow()
            isVerifying = true
            statusMessage = "Analyzing vocal formants & unwrapping vault key..."
            onVerifyVoiceSample(pcm) { outcome ->
                isVerifying = false
                when (outcome) {
                    is VoiceUnlockOutcome.Success -> {
                        isError = false
                        statusMessage = outcome.verification.qualityMessage
                        onDismiss()
                    }
                    is VoiceUnlockOutcome.Rejected -> {
                        isError = true
                        statusMessage = outcome.verification.qualityMessage
                    }
                    is VoiceUnlockOutcome.Unavailable -> {
                        isError = true
                        statusMessage = outcome.reason
                    }
                }
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && lockoutRemainingSeconds == 0) {
            startVerificationCapture()
        }
    }

    AlertDialog(
        onDismissRequest = {
            captureSession.requestStop()
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(EmeraldContainer)
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = EmeraldNeon,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "VOICE BIOMETRIC UNLOCK",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    SecurityBadge(text = "MFCC SPEAKER VERIFICATION")
                }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!hasPermission) {
                    Text(
                        text = "Allow microphone permission to verify your spoken voiceprint locally on-device.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldNeon,
                            contentColor = Color(0xFF003816)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("ALLOW MICROPHONE", fontWeight = FontWeight.Bold)
                    }
                } else {
                    VoiceSpectrumVisualizer(
                        bands = captureState.spectrumBands,
                        isRecording = captureState.isRecording,
                        accentColor = if (isError) CrimsonAlert else EmeraldNeon
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (lockoutRemainingSeconds > 0) {
                        Text(
                            text = "TOO MANY FAILED ATTEMPTS • LOCKED FOR ${lockoutRemainingSeconds}S",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonAlert,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = if (isError) CrimsonAlert else TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    if (lastSimilarityPercent != null && !captureState.isRecording && !isVerifying) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Last match score: $lastSimilarityPercent% (threshold 85%)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (hasPermission) {
                Button(
                    onClick = {
                        if (captureState.isRecording) {
                            captureSession.requestStop()
                        } else {
                            startVerificationCapture()
                        }
                    },
                    enabled = !isVerifying && lockoutRemainingSeconds == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (captureState.isRecording) CyanIce else EmeraldNeon,
                        contentColor = Color(0xFF003816)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("voice_unlock_capture_button")
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            color = Color(0xFF003816),
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (captureState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (captureState.isRecording) "VERIFY NOW" else "SPEAK TO UNLOCK",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    captureSession.requestStop()
                    onDismiss()
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("Use Password", color = TextSecondary)
            }
        },
        containerColor = BlackBoxSurfaceVariant,
        shape = RoundedCornerShape(14.dp)
    )
}
