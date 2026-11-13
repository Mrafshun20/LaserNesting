package com.mrafshun20.lasernesting.export

import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.Point
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.NestSheet
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PlacedPart
import com.mrafshun20.lasernesting.util.Fmt
import com.mrafshun20.lasernesting.util.Palette

/**
 * Pure-Kotlin exports: cut list (CSV), nest layout (SVG), one DXF per sheet
 * for the machine software, and a printable HTML job report. Produces
 * strings only; the platform (Android share sheet, desktop save dialog)
 * decides how to deliver them. No Android dependency.
 *
 * Every number is written with '.' and ASCII digits whatever the device
 * language, so files open correctly everywhere.
 */
object ExportCore {

    // ------------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------------

    /** CSV cut list: one row per placed part, across all sheets. */
    fun cutListCsv(
        result: NestResult,
        sheetWidthMm: Double,
        sheetLengthMm: Double,
        withBom: Boolean = true
    ): String {
        val sb = StringBuilder()
        if (withBom) sb.append('\uFEFF')
        sb.append("Part,Sheet,X (mm),Y (mm),Width (mm),Height (mm),Rotation (deg),Shape,Cut length (mm),Pierces\n")
        for (sheet in result.sheets) {
            for (part in sheet.placed) {
                sb.append(escapeCsv(part.name)).append(',')
                sb.append(sheet.index + 1).append(',')
                sb.append(Fmt.num(part.xMm)).append(',')
                sb.append(Fmt.num(part.yMm)).append(',')
                sb.append(Fmt.num(part.widthMm)).append(',')
                sb.append(Fmt.num(part.heightMm)).append(',')
                sb.append(part.rotationDegrees).append(',')
                sb.append(
                    when (part.shape) {
                        PartShape.CIRCLE -> "circle"
                        PartShape.DXF -> "dxf"
                        else -> "rectangle"
                    }
                ).append(',')
                sb.append(Fmt.num(NestingEngine.partPerimeter(part))).append(',')
                sb.append(NestingEngine.partPierces(part))
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // SVG
    // ------------------------------------------------------------------

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
            """<svg xmlns="http://www.w3.org/2000/svg" width="${Fmt.num(sheetWidthMm)}mm" height="${Fmt.num(totalHeight)}mm" viewBox="0 0 ${Fmt.num(sheetWidthMm)} ${Fmt.num(totalHeight)}">"""
        )
        sb.append('\n')
        if (result.sheets.isEmpty()) {
            sb.append(
                """<rect x="0" y="0" width="${Fmt.num(sheetWidthMm)}" height="${Fmt.num(sheetLengthMm)}" fill="#f5f5f5" stroke="#333333" stroke-width="2"/>"""
            )
            sb.append('\n')
            sb.append("""<text x="20" y="60" font-size="32" fill="#333333">No parts placed</text>""")
            sb.append('\n')
        }
        for ((position, sheet) in result.sheets.withIndex()) {
            val offsetY = position * (sheetLengthMm + gapBetweenSheets)
            sb.append(sheetSvgBody(sheet, sheetWidthMm, sheetLengthMm, offsetY, true))
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    /** One standalone SVG document for a single sheet. */
    fun sheetSvg(sheet: NestSheet, sheetWidthMm: Double, sheetLengthMm: Double): String {
        val sb = StringBuilder()
        sb.append(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${Fmt.num(sheetWidthMm)} ${Fmt.num(sheetLengthMm)}" style="width:100%;height:auto">"""
        )
        sb.append('\n')
        sb.append(sheetSvgBody(sheet, sheetWidthMm, sheetLengthMm, 0.0, false))
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun sheetSvgBody(
        sheet: NestSheet,
        sheetWidthMm: Double,
        sheetLengthMm: Double,
        offsetY: Double,
        withTitle: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append(
            """<rect x="0" y="${Fmt.num(offsetY)}" width="${Fmt.num(sheetWidthMm)}" height="${Fmt.num(sheetLengthMm)}" fill="#f5f5f5" stroke="#333333" stroke-width="2"/>"""
        )
        sb.append('\n')
        if (withTitle) {
            sb.append(
                """<text x="20" y="${Fmt.num(offsetY + 48)}" font-size="32" fill="#333333">Sheet ${sheet.index + 1} - ${Fmt.f(sheet.utilizationPercent, 1)}% used</text>"""
            )
            sb.append('\n')
        }
        for (part in sheet.placed) sb.append(partSvg(part, offsetY))
        sheet.remnant?.let { remnant ->
            sb.append(
                """<rect x="${Fmt.num(remnant.xMm)}" y="${Fmt.num(offsetY + remnant.yMm)}" width="${Fmt.num(remnant.widthMm)}" height="${Fmt.num(remnant.heightMm)}" fill="none" stroke="#2E7D32" stroke-width="2" stroke-dasharray="12 8"/>"""
            )
            sb.append('\n')
            sb.append(
                """<text x="${Fmt.num(remnant.xMm + 8)}" y="${Fmt.num(offsetY + remnant.yMm + 30)}" font-size="24" fill="#2E7D32">Remnant ${Fmt.num(remnant.widthMm)} x ${Fmt.num(remnant.heightMm)}</text>"""
            )
            sb.append('\n')
        }
        return sb.toString()
    }

    /** SVG path data for a closed polygon at an offset. */
    private fun polyPath(points: List<Point>, dx: Double, dy: Double): String = buildString {
        points.forEachIndexed { i, p ->
            append(if (i == 0) "M " else " L ")
            append(Fmt.num(dx + p.x)).append(' ').append(Fmt.num(dy + p.y))
        }
        append(" Z")
    }

    private fun circlePath(cx: Double, cy: Double, r: Double): String =
        "M ${Fmt.num(cx - r)} ${Fmt.num(cy)} a ${Fmt.num(r)} ${Fmt.num(r)} 0 1 0 ${Fmt.num(2 * r)} 0 " +
            "a ${Fmt.num(r)} ${Fmt.num(r)} 0 1 0 ${Fmt.num(-2 * r)} 0 Z"

    private fun partSvg(part: PlacedPart, offsetY: Double): String {
        val color = Palette.hex(part.colorIndex)
        val sb = StringBuilder()
        val style = """fill="$color" fill-opacity="0.55" stroke="#222222" stroke-width="1.5" fill-rule="evenodd""""
        when (part.shape) {
            PartShape.CIRCLE -> {
                val cx = part.xMm + part.widthMm / 2.0
                val cy = offsetY + part.yMm + part.heightMm / 2.0
                sb.append("""<circle cx="${Fmt.num(cx)}" cy="${Fmt.num(cy)}" r="${Fmt.num(part.widthMm / 2.0)}" $style/>""")
            }
            PartShape.DXF -> {
                val outline = NestingEngine.placedOutline(part)
                if (outline != null && outline.points.isNotEmpty()) {
                    val d = buildString {
                        append(polyPath(outline.points, part.xMm, offsetY + part.yMm))
                        for (loop in outline.cutouts) {
                            append(' ').append(polyPath(loop, part.xMm, offsetY + part.yMm))
                        }
                        for (h in outline.holes) {
                            append(' ').append(
                                circlePath(part.xMm + h.center.x, offsetY + part.yMm + h.center.y, h.radius)
                            )
                        }
                    }
                    sb.append("""<path d="$d" $style/>""")
                } else {
                    sb.append(rectSvg(part, offsetY, style))
                }
            }
            else -> sb.append(rectSvg(part, offsetY, style))
        }
        sb.append('\n')
        val label = partLabel(part)
        val fontSize = labelFontSize(part, label)
        if (fontSize != null) {
            val cx = part.xMm + part.widthMm / 2.0
            val cy = offsetY + part.yMm + part.heightMm / 2.0
            sb.append(
                """<text x="${Fmt.num(cx)}" y="${Fmt.num(cy + fontSize / 3.0)}" font-size="${Fmt.num(fontSize)}" text-anchor="middle" fill="#111111">${escapeXml(label)}</text>"""
            )
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun rectSvg(part: PlacedPart, offsetY: Double, style: String) =
        """<rect x="${Fmt.num(part.xMm)}" y="${Fmt.num(offsetY + part.yMm)}" width="${Fmt.num(part.widthMm)}" height="${Fmt.num(part.heightMm)}" $style/>"""

    /** Single-line label: part name plus dimensions, no material name. */
    fun partLabel(part: PlacedPart): String {
        val dims = if (part.shape == PartShape.CIRCLE) {
            "Ø ${Fmt.num(part.widthMm)}"
        } else {
            "${Fmt.num(part.widthMm)} x ${Fmt.num(part.heightMm)}"
        }
        return "${part.name} $dims"
    }

    /**
     * Font size (mm) for [label] so it fits inside [part], or null when the
     * part is too small to hold the label legibly.
     */
    fun labelFontSize(part: PlacedPart, label: String): Double? {
        if (label.isEmpty()) return null
        val maxWidth = part.widthMm * 0.92
        val maxHeight = part.heightMm * 0.5
        if (maxWidth < 30.0 || maxHeight < 12.0) return null
        val charCount = label.length.coerceAtLeast(1)
        val byWidth = maxWidth / (0.6 * charCount)
        val size = minOf(byWidth, maxHeight, 28.0)
        if (size < 10.0) return null
        return size
    }

    // ------------------------------------------------------------------
    // DXF (one file per sheet, for the machine software)
    // ------------------------------------------------------------------

    /**
     * ASCII DXF (R12 entities, widest compatibility) of one sheet: sheet
     * border on layer SHEET, outer contours on CUT_OUTER, holes and cutouts
     * on CUT_INNER, part names on LABELS. The Y axis is flipped so the
     * drawing looks the same as on screen (DXF Y points up). Units: mm.
     */
    fun sheetDxf(sheet: NestSheet, sheetWidthMm: Double, sheetLengthMm: Double): String {
        val sb = StringBuilder()
        fun pair(code: Int, value: String) {
            sb.append(code).append('\n').append(value).append('\n')
        }
        fun y(v: Double) = sheetLengthMm - v

        pair(0, "SECTION"); pair(2, "HEADER")
        pair(9, "\$ACADVER"); pair(1, "AC1009")
        pair(9, "\$INSUNITS"); pair(70, "4")
        pair(0, "ENDSEC")

        pair(0, "SECTION"); pair(2, "TABLES")
        pair(0, "TABLE"); pair(2, "LAYER"); pair(70, "4")
        for ((name, color) in listOf("SHEET" to 8, "CUT_OUTER" to 1, "CUT_INNER" to 5, "LABELS" to 3)) {
            pair(0, "LAYER"); pair(2, name); pair(70, "0"); pair(62, color.toString()); pair(6, "CONTINUOUS")
        }
        pair(0, "ENDTAB")
        pair(0, "ENDSEC")

        pair(0, "SECTION"); pair(2, "ENTITIES")

        fun polyline(layer: String, pts: List<Point>, dx: Double, dy: Double) {
            pair(0, "POLYLINE"); pair(8, layer); pair(66, "1"); pair(70, "1")
            for (p in pts) {
                pair(0, "VERTEX"); pair(8, layer)
                pair(10, Fmt.f(dx + p.x, 4)); pair(20, Fmt.f(y(dy + p.y), 4)); pair(30, "0.0")
            }
            pair(0, "SEQEND"); pair(8, layer)
        }
        fun circle(layer: String, cx: Double, cy: Double, r: Double) {
            pair(0, "CIRCLE"); pair(8, layer)
            pair(10, Fmt.f(cx, 4)); pair(20, Fmt.f(y(cy), 4)); pair(30, "0.0"); pair(40, Fmt.f(r, 4))
        }

        polyline(
            "SHEET",
            listOf(Point(0.0, 0.0), Point(sheetWidthMm, 0.0), Point(sheetWidthMm, sheetLengthMm), Point(0.0, sheetLengthMm)),
            0.0, 0.0
        )
        for (part in sheet.placed) {
            when (part.shape) {
                PartShape.CIRCLE -> circle("CUT_OUTER", part.xMm + part.widthMm / 2, part.yMm + part.heightMm / 2, part.widthMm / 2)
                PartShape.DXF -> {
                    val o = NestingEngine.placedOutline(part)
                    if (o != null && o.points.isNotEmpty()) {
                        polyline("CUT_OUTER", o.points, part.xMm, part.yMm)
                        for (loop in o.cutouts) polyline("CUT_INNER", loop, part.xMm, part.yMm)
                        for (h in o.holes) circle("CUT_INNER", part.xMm + h.center.x, part.yMm + h.center.y, h.radius)
                    } else {
                        polyline("CUT_OUTER", rectPoints(part.widthMm, part.heightMm), part.xMm, part.yMm)
                    }
                }
                else -> polyline("CUT_OUTER", rectPoints(part.widthMm, part.heightMm), part.xMm, part.yMm)
            }
            val label = partLabel(part)
            val size = labelFontSize(part, label)
            if (size != null) {
                pair(0, "TEXT"); pair(8, "LABELS")
                pair(10, Fmt.f(part.xMm + part.widthMm / 2, 4)); pair(20, Fmt.f(y(part.yMm + part.heightMm / 2), 4)); pair(30, "0.0")
                pair(40, Fmt.f(size, 2)); pair(1, asciiSafe(label)); pair(72, "1")
                pair(11, Fmt.f(part.xMm + part.widthMm / 2, 4)); pair(21, Fmt.f(y(part.yMm + part.heightMm / 2), 4)); pair(31, "0.0")
            }
        }
        pair(0, "ENDSEC"); pair(0, "EOF")
        return sb.toString()
    }

    private fun rectPoints(w: Double, h: Double) =
        listOf(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))

    /** R12 DXF text is not Unicode; keep labels to safe ASCII. */
    private fun asciiSafe(text: String): String =
        text.map { if (it.code in 32..126) it else '_' }.joinToString("")

    // ------------------------------------------------------------------
    // HTML job report
    // ------------------------------------------------------------------

    /**
     * Self-contained printable HTML report: summary, per-sheet drawing and
     * part table. Open in any browser and print / save as PDF.
     */
    fun reportHtml(
        result: NestResult,
        sheetWidthMm: Double,
        sheetLengthMm: Double,
        title: String,
        machine: String,
        materialLine: String,
        persian: Boolean
    ): String {
        fun t(en: String, fa: String) = if (persian) fa else en
        val dir = if (persian) "rtl" else "ltr"
        val sheetArea = sheetWidthMm * sheetLengthMm / 1_000_000.0
        val used = result.sheetsUsed * sheetArea
        val waste = used * (100.0 - result.overallUtilizationPercent) / 100.0
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html lang=\"${if (persian) "fa" else "en"}\" dir=\"$dir\"><head><meta charset=\"utf-8\">")
        sb.append("<title>${escapeXml(title)}</title><style>")
        sb.append(
            "body{font-family:Vazirmatn,Tahoma,'Segoe UI',Arial,sans-serif;margin:24px;color:#111;background:#fff}" +
                "h1{margin:0 0 4px;font-size:24px}.sub{color:#666;margin-bottom:18px}" +
                ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin:14px 0 22px}" +
                ".card{border:1px solid #ddd;border-radius:14px;padding:12px 14px;background:#fafafa}" +
                ".card b{display:block;font-size:22px;color:#E8590C}.card span{color:#666;font-size:12px}" +
                "h2{font-size:17px;margin:26px 0 8px}svg{border:1px solid #ccc;border-radius:12px;background:#fff}" +
                "table{border-collapse:collapse;width:100%;font-size:12.5px;margin-top:10px}" +
                "th,td{border-bottom:1px solid #e3e3e3;padding:6px 8px;text-align:start}th{background:#f3f3f3}" +
                ".bad{color:#b00020}@media print{.pb{page-break-before:always}}"
        )
        sb.append("</style></head><body>")
        sb.append("<h1>${escapeXml(title)}</h1><div class=\"sub\">${escapeXml(machine)} • ${escapeXml(materialLine)}</div>")
        sb.append("<div class=\"grid\">")
        fun card(label: String, value: String) =
            sb.append("<div class=\"card\"><b>${escapeXml(value)}</b><span>${escapeXml(label)}</span></div>")
        card(t("Utilization", "بهره‌وری"), Fmt.f(result.overallUtilizationPercent, 1) + "%")
        card(t("Sheets", "ورق‌ها"), "${result.sheetsUsed}")
        card(t("Parts placed", "قطعات چیده‌شده"), "${result.totalPartsPlaced}/${result.totalPartsRequested}")
        card(t("Cut length", "طول برش"), Fmt.f(result.totalCutLengthMm / 1000.0, 1) + " m")
        card(t("Pierces", "تعداد سوراخ‌کاری"), "${result.totalPierces}")
        card(t("Est. cutting time", "زمان تقریبی برش"), Fmt.f(result.estimatedCutMinutes, 1) + " min")
        card(t("Waste", "ضایعات"), Fmt.f(waste, 2) + " m²")
        if (result.sheetsWeightKg > 0) {
            card(t("Sheet weight", "وزن ورق"), Fmt.f(result.sheetsWeightKg, 1) + " kg")
            card(t("Parts weight", "وزن قطعات"), Fmt.f(result.partsWeightKg, 1) + " kg")
        }
        sb.append("</div>")

        if (result.unplaced.isNotEmpty()) {
            sb.append("<h2 class=\"bad\">${t("Unplaced parts", "قطعات چیده‌نشده")}</h2><ul class=\"bad\">")
            for (u in result.unplaced) {
                sb.append("<li>${escapeXml(u.name)} × ${u.quantity}</li>")
            }
            sb.append("</ul>")
        }

        for ((i, sheet) in result.sheets.withIndex()) {
            sb.append("<div class=\"${if (i > 0) "pb" else ""}\">")
            sb.append(
                "<h2>${t("Sheet", "ورق")} ${sheet.index + 1} — ${Fmt.f(sheet.utilizationPercent, 1)}% " +
                    "(${Fmt.num(sheetWidthMm)} × ${Fmt.num(sheetLengthMm)} mm)</h2>"
            )
            sb.append(sheetSvg(sheet, sheetWidthMm, sheetLengthMm))
            sheet.remnant?.let {
                sb.append(
                    "<p>${t("Remnant", "باقیمانده")}: ${Fmt.num(it.widthMm)} × ${Fmt.num(it.heightMm)} mm</p>"
                )
            }
            val groups = sheet.placed.groupBy { it.partId to it.name }
            sb.append(
                "<table><tr><th>${t("Part", "قطعه")}</th><th>${t("Qty", "تعداد")}</th>" +
                    "<th>${t("Size (mm)", "اندازه (mm)")}</th><th>${t("Cut length (m)", "طول برش (m)")}</th></tr>"
            )
            for ((key, list) in groups) {
                val first = list.first()
                val cut = list.sumOf { NestingEngine.partPerimeter(it) } / 1000.0
                sb.append(
                    "<tr><td>${escapeXml(key.second)}</td><td>${list.size}</td>" +
                        "<td>${Fmt.num(first.widthMm)} × ${Fmt.num(first.heightMm)}</td><td>${Fmt.f(cut, 2)}</td></tr>"
                )
            }
            sb.append("</table></div>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    // ------------------------------------------------------------------

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
}
