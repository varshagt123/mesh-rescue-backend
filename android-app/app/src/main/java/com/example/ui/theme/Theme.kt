package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RescueLinkLightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = PureWhite,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealDark,
    secondary = DeepNavy,
    onSecondary = PureWhite,
    secondaryContainer = MutedSurface,
    onSecondaryContainer = DeepNavy,
    tertiary = StatusInfo,
    onTertiary = PureWhite,
    error = EmergencyRed,
    onError = PureWhite,
    errorContainer = EmergencyRedContainer,
    onErrorContainer = EmergencyRedDark,
    background = PureWhite,
    onBackground = DeepNavy,
    surface = PureWhite,
    onSurface = DeepNavy,
    surfaceVariant = SoftSurface,
    onSurfaceVariant = NavySecondary,
    outline = SubtleBorder,
    outlineVariant = MutedSurface
)

@Composable
fun RescueLinkTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RescueLinkLightColorScheme,
        typography = RescueLinkTypography,
        content = content
    )
}
