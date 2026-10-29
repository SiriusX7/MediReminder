package com.medassist.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MedTrackerLightColorScheme = lightColorScheme(
    primary = MedicalBlue,
    onPrimary = Color.White,
    primaryContainer = MedicalBlueLight,
    onPrimaryContainer = MedicalBlueDark,

    secondary = HealthTeal,
    onSecondary = Color.White,
    secondaryContainer = HealthTealLight,
    onSecondaryContainer = HealthTealDark,

    tertiary = AccentMint,
    onTertiary = Color.White,
    tertiaryContainer = AccentMintLight,
    onTertiaryContainer = Color(0xFF00201B),

    background = BackgroundWhite,
    onBackground = TextPrimaryDark,

    surface = SurfaceWhite,
    onSurface = TextPrimaryDark,

    surfaceVariant = SurfaceVariantClean,
    onSurfaceVariant = TextSecondaryMuted,

    surfaceContainer = Color.White.copy(alpha = 0.65f),
    surfaceContainerLow = Color.White.copy(alpha = 0.55f),
    surfaceContainerLowest = Color.White.copy(alpha = 0.45f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.80f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.90f),

    outline = OutlineBorder,
    outlineVariant = DividerColor,

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val MedTrackerDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFC2E8FF),

    secondary = Color(0xFF38D399),
    onSecondary = Color(0xFF00373F),
    secondaryContainer = Color(0xFF004F5A),
    onSecondaryContainer = Color(0xFFACE8FB),

    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFB2DFDB),

    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),

    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),

    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),

    surfaceContainer = Color(0xFF1E293B).copy(alpha = 0.85f),
    surfaceContainerLow = Color(0xFF0F172A).copy(alpha = 0.75f),
    surfaceContainerLowest = Color(0xFF0B0F19).copy(alpha = 0.65f),
    surfaceContainerHigh = Color(0xFF334155).copy(alpha = 0.90f),
    surfaceContainerHighest = Color(0xFF475569).copy(alpha = 0.95f),

    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MedAssistTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colors = if (darkTheme) MedTrackerDarkColorScheme else MedTrackerLightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = MedAssistTypography,
        shapes = MedAssistShapes,
        content = content,
    )
}
