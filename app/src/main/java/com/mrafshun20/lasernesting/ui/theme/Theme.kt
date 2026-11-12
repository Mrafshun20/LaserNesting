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
 * Vazirmatn — Persian font, bundled with the app.
 */
val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

/**
 * Clean, modern type scale with tight hierarchy.
 */
private fun buildTypography(fontFamily: FontFamily?): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily, fontSize = 30.sp, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily, fontSize = 12.sp),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    )
}

/**
 * Modern radii — slightly softer than pure industrial, still tight.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

/**
 * Dark professional theme — deeper blacks, clearer elevation ladder,
 * refined brass accent.
 */
private val DarkColors = darkColorScheme(
    primary = BrassPrimary,
    onPrimary = Color(0xFF12100C),
    primaryContainer = Color(0xFF3D3426),
    onPrimaryContainer = Color(0xFFE2C98A),
    secondary = SteelBlue,
    onSecondary = Color(0xFF0C1216),
    secondaryContainer = Color(0xFF1E2C36),
    onSecondaryContainer = Color(0xFFA8C8DC),
    tertiary = SageGreen,
    onTertiary = Color(0xFF0C1210),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF070809),
    surfaceContainerLow = Color(0xFF0F1215),
    surfaceContainer = Color(0xFF161A1E),
    surfaceContainerHigh = Color(0xFF1E242A),
    surfaceContainerHighest = Color(0xFF262D34),
    error = Color(0xFFD08A7C),
    onError = Color(0xFF1A100E),
    errorContainer = Color(0xFF3A221E),
    onErrorContainer = Color(0xFFF0C4B8),
    outline = Color(0xFF2A323A),
    outlineVariant = Color(0xFF1E242A)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A6A82),
    onPrimary = Color.White,
    secondary = Color(0xFF5A7A6C),
    background = Color(0xFFF4F5F6),
    surface = Color.White
)

@Composable
fun LaserNestingTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPersian = configuration.locales.get(0)?.language == "fa"
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = buildTypography(if (isPersian) VazirmatnFamily else null),
        shapes = AppShapes,
        content = content
    )
}
