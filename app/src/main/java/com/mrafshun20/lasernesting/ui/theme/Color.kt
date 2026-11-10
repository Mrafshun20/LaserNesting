package com.mrafshun20.lasernesting.ui.theme

import androidx.compose.ui.graphics.Color

// Workshop dark theme — deep charcoal surfaces, IGEMS-style amber accent.
val DarkBackground = Color(0xFF101214)
val DarkSurface = Color(0xFF1C1F22)
val DarkSurfaceVariant = Color(0xFF262A2E)
val DarkOnSurface = Color(0xFFE8EAED)
val DarkOnSurfaceVariant = Color(0xFF9AA0A6)
val AmberPrimary = Color(0xFFFFC107)
val SteelBlue = Color(0xFF64B5F6)
val SteelTeal = Color(0xFF4DB6AC)

// The sheet itself is drawn light, like a real sheet of metal on the bed.
val SheetBackground = Color(0xFFF5F5F5)

/**
 * Distinct fill colours for parts, shared by the on-screen canvas and the
 * SVG export. Vivid Material shades so they read clearly on the light
 * sheet and as dots on the dark UI.
 */
val PartPaletteHex = listOf(
    "#42A5F5",
    "#EF5350",
    "#66BB6A",
    "#FFCA28",
    "#AB47BC",
    "#FF7043",
    "#26A69A",
    "#EC407A",
    "#8D6E63",
    "#78909C",
    "#9CCC65",
    "#FFA726"
)

val PartPalette: List<Color> = PartPaletteHex.map {
    Color(android.graphics.Color.parseColor(it))
}

fun partColor(index: Int): Color = PartPalette[index % PartPalette.size]

fun partColorHex(index: Int): String = PartPaletteHex[index % PartPaletteHex.size]
