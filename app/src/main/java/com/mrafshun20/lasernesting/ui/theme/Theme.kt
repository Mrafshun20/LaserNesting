package com.mrafshun20.lasernesting.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark professional theme for the workshop: deep charcoal surfaces with an
 * IGEMS-style amber accent. Dark is the default — a nesting tool is used
 * on the shop floor, next to the machine.
 */
private val DarkColors = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF5C4300),
    onPrimaryContainer = Color(0xFFFFE082),
    secondary = SteelBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1E3A4C),
    onSecondaryContainer = Color(0xFFBBDEFB),
    tertiary = SteelTeal,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Color(0xFFFF5252),
    onError = Color.Black,
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFCDD2),
    outline = Color(0xFF3A3F44)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    background = Color(0xFFF5F5F5),
    surface = Color.White
)

@Composable
fun LaserNestingTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
