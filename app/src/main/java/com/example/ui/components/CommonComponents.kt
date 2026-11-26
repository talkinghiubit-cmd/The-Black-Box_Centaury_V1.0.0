package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PasswordStrength
import com.example.model.VaultCategory
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CategoryIcon(
    category: VaultCategory,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (category) {
        VaultCategory.LOGIN -> Icons.Default.Lock to EmeraldNeon
        VaultCategory.NOTE -> Icons.AutoMirrored.Filled.Notes to CyanIce
        VaultCategory.CARD -> Icons.Default.CreditCard to AmberWarning
        VaultCategory.KEY -> Icons.Default.Key to Color(0xFFA78BFA)
    }

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
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
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
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
        1 -> Color(0xFFF97316) // Orange
        2 -> AmberWarning
        3 -> Color(0xFF34D399) // Mint green
        else -> EmeraldNeon     // Neon emerald
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
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = strength.label.uppercase(),
                color = animatedColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        // 4 Color-coded segmented bars
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
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
    }
}
