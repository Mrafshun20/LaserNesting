package com.mrafshun20.lasernesting.ui.theme

import androidx.compose.ui.graphics.Color

// Workshop dark theme — deep neutral charcoal surfaces, one restrained
// brass accent. No bright candy colours anywhere: the accent, the
// secondary tones and the part fills are all desaturated and muted,
// like a professional industrial CAM tool (IGEMS-style), not a toy.
val DarkBackground = Color(0xFF0F1214)
val DarkSurface = Color(0xFF171B1E)
val DarkSurfaceVariant = Color(0xFF21272C)
val DarkOnSurface = Color(0xFFE7EAEC)
val DarkOnSurfaceVariant = Color(0xFF98A1A7)
val BrassPrimary = Color(0xFFB89B5E)
val SteelBlue = Color(0xFF7A9BB5)
val SageGreen = Color(0xFF7FA08A)

// Muted remnant green (nest view + exports): sage, not traffic-light.
val MutedRemnant = Color(0xFF5E8A6B)

// The sheet itself is drawn light, like a real sheet of metal on the bed.
val SheetBackground = Color(0xFFF2F3F4)

/**
 * Distinct fill colours for parts, shared by the on-screen canvas and the
 * SVG export. Muted, desaturated industrial tones — every part stays
 * distinguishable, but nothing reads as a bright crayon colour. The same
 * 12 tones live in core ExportCore so the SVG export matches the screen.
 */
val PartPaletteHex = listOf(
    "#7A9BB5",
    "#A96A56",
    "#7FA08A",
    "#B89B5E",
    "#8A7A9E",
    "#5F8F8A",
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
