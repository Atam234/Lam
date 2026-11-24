package com.example.argus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ArgusDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    error = CoralError,
    onError = CoralOnError,
    errorContainer = CoralContainer,
    onErrorContainer = CoralOnContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val ArgusLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightSurface,
    primaryContainer = EmeraldOnContainer,
    onPrimaryContainer = EmeraldOnPrimary,
    secondary = LightSecondary,
    onSecondary = LightSurface,
    secondaryContainer = CyanOnContainer,
    onSecondaryContainer = CyanOnSecondary,
    tertiary = LightTertiary,
    onTertiary = LightSurface,
    tertiaryContainer = AmberOnContainer,
    onTertiaryContainer = AmberOnTertiary,
    error = CoralError,
    onError = LightSurface,
    errorContainer = CoralOnContainer,
    onErrorContainer = CoralOnError,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline
)

val ArgusShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun ArgusTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ArgusDarkColorScheme else ArgusLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArgusTypography,
        shapes = ArgusShapes,
        content = content
    )
}
