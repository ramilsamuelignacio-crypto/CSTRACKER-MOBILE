package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = MintContainerLight,
    onPrimaryContainer = DeepPine,
    secondary = ForestGreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EFE7),
    onSecondaryContainer = LightText,
    tertiary = EventQuizDot,
    onTertiary = Color.White,
    tertiaryContainer = EventQuizBgLight,
    onTertiaryContainer = EventQuizTextLight,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    outlineVariant = Color(0xFFEAF0EC),
    error = StatusDangerTextLight,
    onError = Color.White,
    errorContainer = StatusDangerBgLight,
    onErrorContainer = StatusDangerTextLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = MintGlow,
    onPrimary = Color(0xFF062916),
    primaryContainer = MintContainerDark,
    onPrimaryContainer = Color(0xFF8FF0B2),
    secondary = Color(0xFF3FBD6D),
    onSecondary = Color(0xFF062916),
    secondaryContainer = DarkSurfaceAlt,
    onSecondaryContainer = DarkText,
    tertiary = Color(0xFF91C5EF),
    onTertiary = Color(0xFF092A45),
    tertiaryContainer = Color(0xFF152B3B),
    onTertiaryContainer = Color(0xFF91C5EF),
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    outlineVariant = Color(0xFF1E3226),
    error = Color(0xFFF2A097),
    onError = Color(0xFF470E0A),
    errorContainer = Color(0xFF351F1D),
    onErrorContainer = Color(0xFFF2A097),
)

val CstrackerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CstrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = CstrackerShapes,
        content = content,
    )
}
