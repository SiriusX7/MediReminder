package com.medi.reminder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// A full Material 3 baseline light scheme, mirroring the Material 3 Design Kit.
private val LightColors = lightColorScheme(
    primary = M3Primary,
    onPrimary = M3OnPrimary,
    primaryContainer = M3PrimaryContainer,
    onPrimaryContainer = M3OnPrimaryContainer,
    inversePrimary = M3InversePrimary,
    secondary = M3Secondary,
    onSecondary = M3OnSecondary,
    secondaryContainer = M3SecondaryContainer,
    onSecondaryContainer = M3OnSecondaryContainer,
    tertiary = M3Tertiary,
    onTertiary = M3OnTertiary,
    tertiaryContainer = M3TertiaryContainer,
    onTertiaryContainer = M3OnTertiaryContainer,
    error = M3Error,
    onError = M3OnError,
    errorContainer = M3ErrorContainer,
    onErrorContainer = M3OnErrorContainer,
    background = M3Background,
    onBackground = M3OnBackground,
    surface = M3Surface,
    onSurface = M3OnSurface,
    surfaceVariant = M3SurfaceVariant,
    onSurfaceVariant = M3OnSurfaceVariant,
    outline = M3Outline,
    outlineVariant = M3OutlineVariant,
    surfaceContainerLowest = M3SurfaceContainerLowest,
    surfaceContainerLow = M3SurfaceContainerLow,
    surfaceContainer = M3SurfaceContainer,
    surfaceContainerHigh = M3SurfaceContainerHigh,
    surfaceContainerHighest = M3SurfaceContainerHighest,
    inverseSurface = M3InverseSurface,
    inverseOnSurface = M3InverseOnSurface,
    scrim = M3Scrim,
)

@Composable
fun MediTheme(
    content: @Composable () -> Unit,
) {
    // Single, deliberate Material 3 light scheme — a warm, calm daytime UI.
    MaterialTheme(
        colorScheme = LightColors,
        typography = MediTypography,
        shapes = MediShapes,
        content = content,
    )
}
