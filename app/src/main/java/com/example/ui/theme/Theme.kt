package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackBoxColorScheme = darkColorScheme(
    primary = EmeraldNeon,
    onPrimary = Color(0xFF003314),
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
    error = CrimsonAlert,
    onError = Color.White
)

@Composable
fun BlackBoxTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BlackBoxColorScheme,
        typography = Typography,
        content = content
    )
}
