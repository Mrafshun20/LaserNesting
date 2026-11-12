package com.mrafshun20.lasernesting.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Modern desktop design tokens.
 * Deeper charcoal, refined brass accent, clean CAM colours.
 */
object Ui {
    val Bg = Color(0xFF0A0C0E)
    val Surface = Color(0xFF121518)
    val Card = Color(0xFF181E24)
    val CardHover = Color(0xFF222A32)
    val Border = Color(0xFF2A323A)

    val TextPrimary = Color(0xFFF0F2F4)
    val TextSecondary = Color(0xFF8B959C)
    val TextMuted = Color(0xFF5E686F)

    // Refined brass accent
    val Accent = Color(0xFFC9A96E)
    val AccentDim = Color(0x2EC9A96E)

    // CAM colours — muted industrial
    val Laser = Color(0xFFC07A52)
    val Rapid = Color(0xFF6B9BB8)
    val Pierce = Color(0xFFC9A96E)
    val Success = Color(0xFF6F9B82)
    val Danger = Color(0xFFD08A7C)

    val SheetFill = Color(0xFF161C22)
    val SheetGrid = Color(0xFF222A32)
    val SheetMargin = Color(0x66D08A7C)
    val Remnant = Color(0xFF6F9B82)

    // Modern radii
    val RCard = RoundedCornerShape(14.dp)
    val RPanel = RoundedCornerShape(12.dp)
    val RButton = RoundedCornerShape(10.dp)
    val RField = RoundedCornerShape(10.dp)
    val RChip = RoundedCornerShape(8.dp)

    val Pad = 16.dp
    val Gap = 12.dp

    val PartColors = listOf(
        Color(0xFF6B9BB8), Color(0xFFB86A55), Color(0xFF6F9B82), Color(0xFFC9A96E),
        Color(0xFF8A7A9E), Color(0xFF5A8F8A), Color(0xFF9C8A76), Color(0xFF7E8894)
    )
}
