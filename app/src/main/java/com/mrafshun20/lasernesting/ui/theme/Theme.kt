package com.mrafshun20.lasernesting.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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

private val PersianTypography = Typography().let { base ->
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
    // Persian UI uses the bundled Vazirmatn font; English keeps the
    // system font. The locale follows the in-app language selector.
    val configuration = LocalConfiguration.current
    val isPersian = configuration.locales.get(0)?.language == "fa"
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = if (isPersian) PersianTypography else Typography(),
        content = content
    )
}
