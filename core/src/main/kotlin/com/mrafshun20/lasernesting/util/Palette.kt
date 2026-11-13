package com.mrafshun20.lasernesting.util

/**
 * Part colours shared by the Android app, the Windows app and the exports,
 * so a part has the same colour on screen, in SVG and in the HTML report.
 * Vivid on a dark canvas and still readable on white paper.
 */
object Palette {
    val hex: List<String> = listOf(
        "#FF8A1F", "#2DB6FF", "#34D399", "#A78BFA", "#F472B6", "#FACC15",
        "#22D3EE", "#FB7185", "#A3E635", "#818CF8", "#F97316", "#2DD4BF"
    )

    fun hex(index: Int): String = hex[((index % hex.size) + hex.size) % hex.size]

    /** The colour as 0xRRGGBB. */
    fun rgb(index: Int): Int = hex(index).substring(1).toInt(16)
}
