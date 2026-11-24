package com.example.argus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val OmniDarkColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = VioletOnPrimary,
    primaryContainer = VioletContainer,
    onPrimaryContainer = VioletOnContainer,
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = EmeraldTertiary,
    onTertiary = EmeraldOnTertiary,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldOnContainer,
    error = CoralError,
    onError = CoralOnError,
    errorContainer = CoralContainer,
    onErrorContainer = CoralOnContainer,
    background = NebulaBg,
    onBackground = TextPrimaryDark,
    surface = NebulaSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = NebulaSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = NebulaSurfaceHighest,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val OmniLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightSurface,
    primaryContainer = VioletOnContainer,
    onPrimaryContainer = LightPrimary,
    secondary = LightSecondary,
    onSecondary = LightSurface,
    secondaryContainer = CyanOnContainer,
    onSecondaryContainer = LightSecondary,
    tertiary = LightTertiary,
    onTertiary = LightSurface,
    tertiaryContainer = EmeraldOnContainer,
    onTertiaryContainer = LightTertiary,
    error = CoralError,
    onError = LightSurface,
    errorContainer = CoralOnContainer,
    onErrorContainer = CoralError,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline
)

val OmniShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun OmniModeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) OmniDarkColorScheme else OmniLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArgusTypography,
        shapes = OmniShapes,
        content = content
    )
}
