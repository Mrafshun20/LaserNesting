package com.mrafshun20.lasernesting.ui.theme

import androidx.compose.ui.graphics.Color
import com.mrafshun20.lasernesting.util.Palette

// iBebels: graphite and steel from the logo, laser orange as the accent.
val DarkBackground = Color(0xFF090C11)
val DarkSurface = Color(0xFF131A24)
val DarkSurfaceVariant = Color(0xFF1A2330)
val DarkOnSurface = Color(0xFFEAF0FA)
val DarkOnSurfaceVariant = Color(0xFF93A0B8)
val AmberPrimary = Color(0xFFFF7A1A)
val SteelBlue = Color(0xFF3FB8FF)
val SteelTeal = Color(0xFF34D399)
val OutlineDark = Color(0xFF263143)

// The sheet is drawn as dark steel; parts glow on it like a CAD screen.
val SheetBackground = Color(0xFF1C2535)
val SheetGrid = Color(0xFF2A3548)
val SheetBorder = Color(0xFF5B6B86)
val RemnantGreen = Color(0xFF34D399)

/** Part colours, shared with the Windows app and the exports (core Palette). */
val PartPaletteHex: List<String> = Palette.hex

val PartPalette: List<Color> = PartPaletteHex.map {
    Color(android.graphics.Color.parseColor(it))
}

fun partColor(index: Int): Color = PartPalette[((index % PartPalette.size) + PartPalette.size) % PartPalette.size]

fun partColorHex(index: Int): String = Palette.hex(index)
