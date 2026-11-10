package com.mrafshun20.lasernesting.export

import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.PlacedPart

/**
 * Pure-Kotlin cut-list (CSV) and nest-layout (SVG) generation.
 * Produces strings only; the platform (Android share sheet, desktop save
 * dialog) decides how to deliver them. No Android dependency.
 *
 * DXF parts are drawn as their true outline (polygon + circular holes).
 * Labels are a single line — part name plus dimensions — sized to fit
 * inside the part; parts too small to hold the label legibly get no label.
 */
object ExportCore {

    /** CSV cut list: one row per placed part, across all sheets. */
    fun cutListCsv(result: NestResult, sheetWidthMm: Double, sheetLengthMm: Double): String {
        val sb = StringBuilder()
        sb.append("Part,Sheet,X (mm),Y (mm),Width (mm),Height (mm),Rotation (deg),Shape\n")
        for (sheet in result.sheets) {
            for (part in sheet.placed) {
                sb.append(escapeCsv(part.name)).append(',')
                sb.append(sheet.index + 1).append(',')
                sb.append(formatNumber(part.xMm)).append(',')
                sb.append(formatNumber(part.yMm)).append(',')
                sb.append(formatNumber(part.widthMm)).append(',')
                sb.append(formatNumber(part.heightMm)).append(',')
                sb.append(part.rotationDegrees).append(',')
                sb.append(
                    when (part.shape) {
                        PartShape.CIRCLE -> "circle"
                        PartShape.DXF -> "dxf"
                        else -> "rectangle"
                    }
                )
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    /**
     * SVG layout of every used sheet, stacked vertically with a label.
     * Coordinates are in millimetres (1 unit = 1 mm).
     */
    fun nestSvg(result: NestResult, sheetWidthMm: Double, sheetLengthMm: Double): String {
        val gapBetweenSheets = 60.0
        val totalHeight = if (result.sheets.isEmpty()) {
            sheetLengthMm
        } else {
            result.sheets.size * sheetLengthMm + (result.sheets.size - 1) * gapBetweenSheets
        }
        val sb = StringBuilder()
        sb.append(
            """<svg xmlns="http://www.w3.org/2000/svg" width="${formatNumber(sheetWidthMm)}mm" height="${formatNumber(totalHeight)}mm" viewBox="0 0 ${formatNumber(sheetWidthMm)} ${formatNumber(totalHeight)}">"""
        )
        sb.append('\n')
        if (result.sheets.isEmpty()) {
            sb.append(
                """<rect x="0" y="0" width="${formatNumber(sheetWidthMm)}" height="${formatNumber(sheetLengthMm)}" fill="#f5f5f5" stroke="#333333" stroke-width="2"/>"""
            )
            sb.append('\n')
            sb.append("""<text x="20" y="60" font-size="32" fill="#333333">No parts placed</text>""")
            sb.append('\n')
        }
        for ((position, sheet) in result.sheets.withIndex()) {
            val offsetY = position * (sheetLengthMm + gapBetweenSheets)
            sb.append(
                """<rect x="0" y="${formatNumber(offsetY)}" width="${formatNumber(sheetWidthMm)}" height="${formatNumber(sheetLengthMm)}" fill="#f5f5f5" stroke="#333333" stroke-width="2"/>"""
            )
            sb.append('\n')
            sb.append(
                """<text x="20" y="${formatNumber(offsetY + 48)}" font-size="32" fill="#333333">Sheet ${sheet.index + 1} - ${"%.1f".format(sheet.utilizationPercent)}% used</text>"""
            )
            sb.append('\n')
            for (part in sheet.placed) {
                sb.append(partSvg(part, offsetY))
            }
            // Remnant outline (largest leftover rectangle), if any.
            sheet.remnant?.let { remnant ->
                sb.append(
                    """<rect x="${formatNumber(remnant.xMm)}" y="${formatNumber(offsetY + remnant.yMm)}" width="${formatNumber(remnant.widthMm)}" height="${formatNumber(remnant.heightMm)}" fill="none" stroke="#2E7D32" stroke-width="2" stroke-dasharray="12 8"/>"""
                )
                sb.append('\n')
                sb.append(
                    """<text x="${formatNumber(remnant.xMm + 8)}" y="${formatNumber(offsetY + remnant.yMm + 30)}" font-size="24" fill="#2E7D32">Remnant ${formatNumber(remnant.widthMm)} x ${formatNumber(remnant.heightMm)}</text>"""
                )
                sb.append('\n')
            }
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun partSvg(part: PlacedPart, offsetY: Double): String {
        val color = partColorHex(part.colorIndex)
        val sb = StringBuilder()
        when (part.shape) {
            PartShape.CIRCLE -> {
                val cx = part.xMm + part.widthMm / 2.0
                val cy = offsetY + part.yMm + part.heightMm / 2.0
                val r = part.widthMm / 2.0
                sb.append(
                    """<circle cx="${formatNumber(cx)}" cy="${formatNumber(cy)}" r="${formatNumber(r)}" fill="$color" fill-opacity="0.55" stroke="#222222" stroke-width="1.5"/>"""
                )
            }
            PartShape.DXF -> {
                val points = com.mrafshun20.lasernesting.nesting.NestingEngine
                    .placedOutlinePoints(part)
                if (points.isNotEmpty()) {
                    val path = buildString {
                        append("M ")
                        points.forEachIndexed { i, p ->
                            if (i > 0) append(" L ")
                            append(formatNumber(part.xMm + p.x))
                            append(' ')
                            append(formatNumber(offsetY + part.yMm + p.y))
                        }
                        append(" Z")
                    }
                    sb.append(
                        """<path d="$path" fill="$color" fill-opacity="0.55" stroke="#222222" stroke-width="1.5" fill-rule="evenodd"/>"""
                    )
                    // Holes as unfilled circles on top of the outline.
                    val holes = com.mrafshun20.lasernesting.nesting.NestingEngine
                        .placedHoles(part)
                    for (hole in holes) {
                        val cx = part.xMm + hole.center.x
                        val cy = offsetY + part.yMm + hole.center.y
                        sb.append('\n')
                        sb.append(
                            """<circle cx="${formatNumber(cx)}" cy="${formatNumber(cy)}" r="${formatNumber(hole.radius)}" fill="#f5f5f5" stroke="#222222" stroke-width="1.5"/>"""
                        )
                    }
                } else {
                    // Fallback to the bounding rectangle when no outline.
                    sb.append(
                        """<rect x="${formatNumber(part.xMm)}" y="${formatNumber(offsetY + part.yMm)}" width="${formatNumber(part.widthMm)}" height="${formatNumber(part.heightMm)}" fill="$color" fill-opacity="0.55" stroke="#222222" stroke-width="1.5"/>"""
                    )
                }
            }
            else -> {
                sb.append(
                    """<rect x="${formatNumber(part.xMm)}" y="${formatNumber(offsetY + part.yMm)}" width="${formatNumber(part.widthMm)}" height="${formatNumber(part.heightMm)}" fill="$color" fill-opacity="0.55" stroke="#222222" stroke-width="1.5"/>"""
                )
            }
        }
        sb.append('\n')
        // Single-line label (name + dimensions), sized to fit inside the
        // part; skipped when the part is too small to hold it legibly.
        val label = partLabel(part)
        val fontSize = labelFontSize(part, label)
        if (fontSize != null) {
            val cx = part.xMm + part.widthMm / 2.0
            val cy = offsetY + part.yMm + part.heightMm / 2.0
            sb.append(
                """<text x="${formatNumber(cx)}" y="${formatNumber(cy + fontSize / 3.0)}" font-size="${formatNumber(fontSize)}" text-anchor="middle" fill="#111111">${escapeXml(label)}</text>"""
            )
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Single-line label: part name plus dimensions, no material name. */
    fun partLabel(part: PlacedPart): String {
        val dims = if (part.shape == PartShape.CIRCLE) {
            "Ø ${formatNumber(part.widthMm)}"
        } else {
            "${formatNumber(part.widthMm)} x ${formatNumber(part.heightMm)}"
        }
        return "${part.name} $dims"
    }

    /**
     * Font size (mm) for [label] so it fits inside [part], or null when the
     * part is too small to hold the label legibly. Text width is estimated
     * at ~0.6 * fontSize per character.
     */
    fun labelFontSize(part: PlacedPart, label: String): Double? {
        if (label.isEmpty()) return null
        val maxWidth = part.widthMm * 0.92
        val maxHeight = part.heightMm * 0.5
        if (maxWidth < 30.0 || maxHeight < 12.0) return null
        val charCount = label.length.coerceAtLeast(1)
        // Width-limited size, and height-limited size.
        val byWidth = maxWidth / (0.6 * charCount)
        val byHeight = maxHeight
        val size = minOf(byWidth, byHeight, 28.0)
        if (size < 10.0) return null
        return size
    }

    /** Part fill colours, shared with the Android theme (same palette). */
    private val PartPaletteHex = listOf(
        "#42A5F5", "#EF5350", "#66BB6A", "#FFCA28", "#AB47BC", "#FF7043",
        "#26A69A", "#EC407A", "#8D6E63", "#78909C", "#9CCC65", "#FFA726"
    )

    private fun partColorHex(index: Int): String =
        PartPaletteHex[index % PartPaletteHex.size]

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            "%.2f".format(value)
        }
    }
}
