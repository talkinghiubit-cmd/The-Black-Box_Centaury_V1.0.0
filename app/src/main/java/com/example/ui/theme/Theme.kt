package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BlackBoxColorScheme = darkColorScheme(
    primary = EmeraldNeon,
    onPrimary = Color(0xFF00331D),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldLight,
    secondary = CyanIce,
    onSecondary = Color(0xFF00283A),
    secondaryContainer = Color(0xFF0C2B3E),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AmberWarning,
    onTertiary = Color(0xFF3B2500),
    background = BlackBoxPitch,
    onBackground = TextPrimary,
    surface = BlackBoxSurface,
    onSurface = TextPrimary,
    surfaceVariant = BlackBoxSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BlackBoxBorder,
    outlineVariant = BlackBoxBorderSubtle,
    error = CrimsonAlert,
    onError = Color.White
)

private val BlackBoxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun BlackBoxTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BlackBoxColorScheme,
        typography = Typography,
        shapes = BlackBoxShapes,
        content = content
    )
}
