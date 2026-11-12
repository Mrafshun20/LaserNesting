package com.mrafshun20.lasernesting.ui.theme

import androidx.compose.ui.graphics.Color

// Modern industrial dark theme — deeper blacks, sharper hierarchy,
// single refined brass accent. Clean CAM-tool look.
val DarkBackground = Color(0xFF0A0C0E)
val DarkSurface = Color(0xFF121518)
val DarkSurfaceVariant = Color(0xFF1A1F24)
val DarkOnSurface = Color(0xFFF0F2F4)
val DarkOnSurfaceVariant = Color(0xFF8B959C)
val BrassPrimary = Color(0xFFC9A96E)
val SteelBlue = Color(0xFF6B9BB8)
val SageGreen = Color(0xFF6F9B82)

// Muted remnant green (nest view + exports)
val MutedRemnant = Color(0xFF5A8A6A)

// Sheet drawn light, like real metal on the bed
val SheetBackground = Color(0xFFF4F5F6)

/**
 * Distinct fill colours for parts. Muted industrial tones.
 */
val PartPaletteHex = listOf(
    "#6B9BB8",
    "#B86A55",
    "#6F9B82",
    "#C9A96E",
    "#8A7A9E",
    "#5A8F8A",
    "#9C8A76",
    "#7E8894",
    "#A87E7E",
    "#8A8A62",
    "#6E7F92",
    "#B08D5F"
)

val PartPalette: List<Color> = PartPaletteHex.map {
    Color(android.graphics.Color.parseColor(it))
}

fun partColor(index: Int): Color = PartPalette[index % PartPalette.size]

fun partColorHex(index: Int): String = PartPaletteHex[index % PartPaletteHex.size]
