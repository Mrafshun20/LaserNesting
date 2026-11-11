package com.mrafshun20.lasernesting.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Restrained type scale: small, disciplined steps, semibold titles.
 * Industrial tools read through hierarchy, not through size jumps.
 */
private fun buildTypography(fontFamily: FontFamily?): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily, fontSize = 40.sp, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily, fontSize = 34.sp, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, fontSize = 15.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, fontSize = 13.sp),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily, fontSize = 12.sp),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    )
}

/**
 * Tight, professional corner radii: small chamfer-like corners the way
 * industrial software does them — no big bubbly 16–28 dp rounds.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/**
 * Dark professional theme for the workshop: deep neutral charcoal
 * surfaces with one restrained brass accent. Dark is the default — a
 * nesting tool is used on the shop floor, next to the machine.
 */
private val DarkColors = darkColorScheme(
    primary = BrassPrimary,
    onPrimary = Color(0xFF141210),
    primaryContainer = Color(0xFF3A3226),
    onPrimaryContainer = Color(0xFFD9C08A),
    secondary = SteelBlue,
    onSecondary = Color(0xFF10161B),
    secondaryContainer = Color(0xFF22303B),
    onSecondaryContainer = Color(0xFFA9C3D6),
    tertiary = SageGreen,
    onTertiary = Color(0xFF101613),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    // M3 tonal surface ladder — cards sit on surfaceContainer, raised
    // controls on surfaceContainerHigh, the page on background.
    surfaceContainerLowest = Color(0xFF0B0D0F),
    surfaceContainerLow = Color(0xFF14181B),
    surfaceContainer = Color(0xFF1C2125),
    surfaceContainerHigh = Color(0xFF23292E),
    surfaceContainerHighest = Color(0xFF2A3138),
    error = Color(0xFFC07A6E),
    onError = Color(0xFF1A1210),
    errorContainer = Color(0xFF3A2320),
    onErrorContainer = Color(0xFFE8B8AE),
    outline = Color(0xFF2E353B),
    outlineVariant = Color(0xFF23282D)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4E6E86),
    onPrimary = Color.White,
    secondary = Color(0xFF5F7A6E),
    background = Color(0xFFF2F3F4),
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
        typography = buildTypography(if (isPersian) VazirmatnFamily else null),
        shapes = AppShapes,
        content = content
    )
}
