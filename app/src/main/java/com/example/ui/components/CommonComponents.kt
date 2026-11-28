package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.PasswordStrength
import com.example.model.VaultCategory
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxMonoFont
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceElevated
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.IndigoCipher
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

/**
 * Architectural midnight-obsidian canvas with subtle SecOps grid lines and ambient top aura.
 */
@Composable
fun CyberVaultBackdrop(
    modifier: Modifier = Modifier,
    accentColor: Color = EmeraldNeon,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBoxPitch)
            .drawBehind {
                // Top ambient enclave radial glow
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.08f),
                            CyanIce.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.maxDimension * 0.65f
                    )
                )
                // Ultra-subtle precision telemetry grid
                val gridStep = 32.dp.toPx()
                val lineColor = Color.White.copy(alpha = 0.024f)
                var x = gridStep
                while (x < size.width) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += gridStep
                }
                var y = gridStep
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += gridStep
                }
            },
        content = content
    )
}

/**
 * Displays the signature 3D Black-Box vault emblem with beveled obsidian housing and status dot.
 */
@Composable
fun VaultBrandEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 92.dp,
    statusColor: Color = EmeraldNeon,
    showStatusIndicator: Boolean = true
) {
    val cornerRadius = (size.value * 0.24f).dp
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BlackBoxSurfaceElevated, BlackBoxSurface)
                    )
                )
                .border(
                    width = 1.25.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.55f),
                            BlackBoxBorder
                        )
                    ),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .padding((size.value * 0.12f).dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_blackbox_icon),
                contentDescription = "The Black-Box Vault Emblem",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (showStatusIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size((size.value * 0.16f).coerceIn(8f, 15f).dp)
                    .clip(CircleShape)
                    .background(BlackBoxPitch)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityTelemetryStrip(
    tags: List<Pair<String, Color>>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { (label, color) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BlackBoxSurface)
                    .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    color = color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BlackBoxMonoFont,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@Composable
fun CategoryIcon(
    category: VaultCategory,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (category) {
        VaultCategory.LOGIN -> Icons.Default.Lock to EmeraldNeon
        VaultCategory.NOTE -> Icons.AutoMirrored.Filled.Notes to CyanIce
        VaultCategory.CARD -> Icons.Default.CreditCard to AmberWarning
        VaultCategory.KEY -> Icons.Default.Key to IndigoCipher
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.08f))
                )
            )
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.displayName,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SecurityBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = EmeraldNeon
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = BlackBoxMonoFont,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
fun PasswordStrengthBar(
    strength: PasswordStrength,
    modifier: Modifier = Modifier,
    showRecommendation: Boolean = true
) {
    val activeColor = when (strength.score) {
        0 -> CrimsonAlert
        1 -> Color(0xFFF97316)
        2 -> AmberWarning
        3 -> Color(0xFF34D399)
        else -> EmeraldNeon
    }
    val animatedColor by animateColorAsState(activeColor, label = "StrengthColor")

    val tip = when (strength.score) {
        0 -> "Critical: Highly vulnerable to dictionary attacks."
        1 -> "Weak: Add numbers, symbols, and uppercase letters."
        2 -> "Moderate: Increase length to 16+ chars for high security."
        3 -> "Strong: Good cryptographic resistance."
        else -> "Very Strong: Military-grade Shannon entropy."
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENTROPY: ${strength.entropyBits} BITS",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = BlackBoxMonoFont,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = strength.label.uppercase(),
                color = animatedColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BlackBoxMonoFont,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        val filledBars = if (strength.entropyBits <= 0) 0 else when (strength.score) {
            0, 1 -> 1
            2 -> 2
            3 -> 3
            else -> 4
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 0 until 4) {
                val isFilled = i < filledBars
                val barColor = if (isFilled) animatedColor else BlackBoxSurfaceVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                        .border(
                            0.5.dp,
                            if (isFilled) animatedColor.copy(alpha = 0.6f) else BlackBoxBorder,
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }

        if (showRecommendation) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = tip,
                fontSize = 10.sp,
                color = if (strength.score <= 1) CrimsonAlert.copy(alpha = 0.9f) else TextMuted,
                fontFamily = BlackBoxMonoFont,
                lineHeight = 14.sp
            )
        }
    }
}
