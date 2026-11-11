package com.mrafshun20.lasernesting.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ibebels desktop design tokens.
 *
 * Deep neutral charcoal surfaces, one restrained brass accent, muted
 * steel-blue / sage secondaries. No bright candy colours anywhere:
 * the accent, the CAM colours (laser / rapid / pierce) and the part
 * fills are all desaturated industrial tones, so the tool reads as a
 * professional CAD/CAM workstation, not a toy.
 */
object Ui {
    val Bg = Color(0xFF0E1113)
    val Surface = Color(0xFF161B1F)
    val Card = Color(0xFF1D2329)
    val CardHover = Color(0xFF242C33)
    val Border = Color(0xFF2A323A)

    val TextPrimary = Color(0xFFE7EAEC)
    val TextSecondary = Color(0xFF98A1A7)
    val TextMuted = Color(0xFF6E787F)

    // Single restrained accent: muted brass.
    val Accent = Color(0xFFB89B5E)
    val AccentDim = Color(0x2EB89B5E)

    // CAM colours, all muted: burnt orange laser, steel-blue rapid,
    // dusty ochre pierce, sage success, muted rose danger.
    val Laser = Color(0xFFC07A52)
    val Rapid = Color(0xFF7A9BB5)
    val Pierce = Color(0xFFC9A959)
    val Success = Color(0xFF7FA08A)
    val Danger = Color(0xFFC07A6E)

    val SheetFill = Color(0xFF1B2127)
    val SheetGrid = Color(0xFF262E37)
    val SheetMargin = Color(0x66C07A6E)
    val Remnant = Color(0xFF7FA08A)

    // Tight, professional radii — small chamfer-like corners.
    val RCard = RoundedCornerShape(12.dp)
    val RPanel = RoundedCornerShape(10.dp)
    val RButton = RoundedCornerShape(8.dp)
    val RField = RoundedCornerShape(8.dp)
    val RChip = RoundedCornerShape(8.dp)

    val Pad = 16.dp
    val Gap = 12.dp

    /** Part fills: muted industrial tones, same 12-tone family as Android. */
    val PartColors = listOf(
        Color(0xFF7A9BB5), Color(0xFFA96A56), Color(0xFF7FA08A), Color(0xFFB89B5E),
        Color(0xFF8A7A9E), Color(0xFF5F8F8A), Color(0xFF9C8A76), Color(0xFF7E8894)
    )
}
