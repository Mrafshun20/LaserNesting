package com.mrafshun20.lasernesting.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mrafshun20.lasernesting.R

/**
 * Vazirmatn — a neat, proper Persian font, bundled with the app.
 * Used for Persian text; English falls back to the system font.
 */
val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val AppTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = VazirmatnFamily),
        displayMedium = base.displayMedium.copy(fontFamily = VazirmatnFamily),
        displaySmall = base.displaySmall.copy(fontFamily = VazirmatnFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = VazirmatnFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = VazirmatnFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = VazirmatnFamily),
        titleLarge = base.titleLarge.copy(fontFamily = VazirmatnFamily),
        titleMedium = base.titleMedium.copy(fontFamily = VazirmatnFamily),
        titleSmall = base.titleSmall.copy(fontFamily = VazirmatnFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = VazirmatnFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = VazirmatnFamily),
        bodySmall = base.bodySmall.copy(fontFamily = VazirmatnFamily),
        labelLarge = base.labelLarge.copy(fontFamily = VazirmatnFamily),
        labelMedium = base.labelMedium.copy(fontFamily = VazirmatnFamily),
        labelSmall = base.labelSmall.copy(fontFamily = VazirmatnFamily)
    )
}

/**
 * Dark professional theme for the workshop: deep charcoal surfaces with an
 * IGEMS-style amber accent. Dark is the default — a nesting tool is used
 * on the shop floor, next to the machine.
 */
private val DarkColors = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color(0xFF1A0D00),
    primaryContainer = Color(0xFF3B1F08),
    onPrimaryContainer = Color(0xFFFFD7B5),
    secondary = SteelBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF14354A),
    onSecondaryContainer = Color(0xFFCDEBFF),
    tertiary = SteelTeal,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Color(0xFFFF5D6C),
    onError = Color.Black,
    errorContainer = Color(0xFF4A1620),
    onErrorContainer = Color(0xFFFFD6DB),
    outline = OutlineDark,
    outlineVariant = OutlineDark
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    background = Color(0xFFF5F5F5),
    surface = Color.White
)

/** Rounded, friendly shapes for every Material component. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LaserNestingTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Vazirmatn covers Persian and Latin, so it is used for every language.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
