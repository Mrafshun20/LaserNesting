package com.mrafshun20.lasernesting.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.PlacedPart
import com.mrafshun20.lasernesting.ui.theme.partColorHex
import java.io.File

/**
 * Cut-list (CSV) and nest-layout (SVG) export, shared through the
 * Android share sheet. Files are written to the app cache directory and
 * shared with a FileProvider URI — no storage permission is needed.
 */
object Export {

    /** CSV cut list: one row per placed part, across all sheets. */
    fun cutListCsv(result: NestResult, sheetWidthMm: Double, sheetLengthMm: Double): String {
        val sb = StringBuilder()
        sb.append("Part,Sheet,X (mm),Y (mm),Width (mm),Height (mm),Rotated,Shape\n")
        for (sheet in result.sheets) {
            for (part in sheet.placed) {
                sb.append(escapeCsv(part.name)).append(',')
                sb.append(sheet.index + 1).append(',')
                sb.append(formatNumber(part.xMm)).append(',')
                sb.append(formatNumber(part.yMm)).append(',')
                sb.append(formatNumber(part.widthMm)).append(',')
                sb.append(formatNumber(part.heightMm)).append(',')
                sb.append(if (part.rotated) "yes" else "no").append(',')
                sb.append(if (part.shape == PartShape.CIRCLE) "circle" else "rectangle")
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
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun partSvg(part: PlacedPart, offsetY: Double): String {
        val color = partColorHex(part.colorIndex)
        val sb = StringBuilder()
        if (part.shape == PartShape.CIRCLE) {
            val cx = part.xMm + part.widthMm / 2.0
            val cy = offsetY + part.yMm + part.heightMm / 2.0
            val r = part.widthMm / 2.0
            sb.append(
                """<circle cx="${formatNumber(cx)}" cy="${formatNumber(cy)}" r="${formatNumber(r)}" fill="$color" fill-opacity="0.55" stroke="#222222" stroke-width="1.5"/>"""
            )
        } else {
            sb.append(
                """<rect x="${formatNumber(part.xMm)}" y="${formatNumber(offsetY + part.yMm)}" width="${formatNumber(part.widthMm)}" height="${formatNumber(part.heightMm)}" fill="$color" fill-opacity="0.55" stroke="#222222" stroke-width="1.5"/>"""
            )
        }
        sb.append('\n')
        // Label, only when the part is large enough to hold text.
        if (part.widthMm > 90 && part.heightMm > 50) {
            val cx = part.xMm + part.widthMm / 2.0
            val cy = offsetY + part.yMm + part.heightMm / 2.0
            val dims = if (part.shape == PartShape.CIRCLE) {
                "Ø ${formatNumber(part.widthMm)}"
            } else {
                "${formatNumber(part.widthMm)} x ${formatNumber(part.heightMm)}"
            }
            sb.append(
                """<text x="${formatNumber(cx)}" y="${formatNumber(cy - 6)}" font-size="28" text-anchor="middle" fill="#111111">${escapeXml(part.name)}</text>"""
            )
            sb.append('\n')
            sb.append(
                """<text x="${formatNumber(cx)}" y="${formatNumber(cy + 26)}" font-size="24" text-anchor="middle" fill="#111111">$dims</text>"""
            )
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Writes [content] to the cache dir and opens the Android share sheet. */
    fun shareFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
        chooserTitle: String
    ) {
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

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
