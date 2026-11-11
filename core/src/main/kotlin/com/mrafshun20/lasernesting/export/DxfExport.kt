package com.mrafshun20.lasernesting.export

import com.mrafshun20.lasernesting.nesting.CutKind
import com.mrafshun20.lasernesting.nesting.CutPlanner
import com.mrafshun20.lasernesting.nesting.NestSheet
import java.util.Locale

/**
 * Writes a nested sheet as an ASCII DXF (AutoCAD R12, millimetres) that
 * Bystronic BySoft / any CAM imports directly.
 *
 * Layers: SHEET (sheet border), CUT_INNER (holes, slots — cut first),
 * CUT_OUTER (part contours — cut last), LABELS (part names, ASCII only).
 * Contours are written in the planned cutting order and the Y axis is flipped
 * back to CAD orientation (Y up, origin bottom-left of the sheet).
 */
object DxfExport {

    fun sheetDxf(
        sheet: NestSheet,
        sheetWidthMm: Double,
        sheetLengthMm: Double,
        includeLabels: Boolean = true
    ): String {
        val sb = StringBuilder()
        fun g(code: Int, value: String) {
            sb.append(code).append('\n').append(value).append('\n')
        }
        fun num(v: Double) = String.format(Locale.ROOT, "%.4f", v)
        fun y(v: Double) = sheetLengthMm - v

        // HEADER
        g(0, "SECTION"); g(2, "HEADER")
        g(9, "\$ACADVER"); g(1, "AC1009")
        g(9, "\$EXTMIN"); g(10, "0.0"); g(20, "0.0")
        g(9, "\$EXTMAX"); g(10, num(sheetWidthMm)); g(20, num(sheetLengthMm))
        g(0, "ENDSEC")

        // TABLES: continuous line type + layers.
        val layers = listOf("SHEET" to 8, "CUT_INNER" to 1, "CUT_OUTER" to 7, "LABELS" to 3)
        g(0, "SECTION"); g(2, "TABLES")
        g(0, "TABLE"); g(2, "LTYPE"); g(70, "1")
        g(0, "LTYPE"); g(2, "CONTINUOUS"); g(70, "0"); g(3, "Solid line"); g(72, "65"); g(73, "0"); g(40, "0.0")
        g(0, "ENDTAB")
        g(0, "TABLE"); g(2, "LAYER"); g(70, layers.size.toString())
        for ((name, color) in layers) {
            g(0, "LAYER"); g(2, name); g(70, "0"); g(62, color.toString()); g(6, "CONTINUOUS")
        }
        g(0, "ENDTAB")
        g(0, "ENDSEC")

        // ENTITIES
        g(0, "SECTION"); g(2, "ENTITIES")

        fun polyline(layer: String, pts: List<Pair<Double, Double>>) {
            g(0, "POLYLINE"); g(8, layer); g(66, "1"); g(70, "1")
            for ((px, py) in pts) {
                g(0, "VERTEX"); g(8, layer); g(10, num(px)); g(20, num(py))
            }
            g(0, "SEQEND"); g(8, layer)
        }

        polyline(
            "SHEET",
            listOf(0.0 to 0.0, sheetWidthMm to 0.0, sheetWidthMm to sheetLengthMm, 0.0 to sheetLengthMm)
        )

        val plan = CutPlanner.plan(sheet, sheetLengthMm)
        for (op in plan.ops) {
            val layer = if (op.kind == CutKind.INNER) "CUT_INNER" else "CUT_OUTER"
            if (op.isCircle) {
                g(0, "CIRCLE"); g(8, layer)
                g(10, num(op.circleCx)); g(20, num(y(op.circleCy))); g(40, num(op.circleR))
            } else {
                polyline(layer, op.points.map { it.x to y(it.y) })
            }
        }

        if (includeLabels) {
            for (p in sheet.placed) {
                val text = p.name.filter { it.code in 32..126 }.trim()
                if (text.isEmpty()) continue
                val h = minOf(12.0, p.heightMm / 4.0, p.widthMm / (text.length * 0.8 + 1))
                if (h < 3.0) continue
                g(0, "TEXT"); g(8, "LABELS")
                g(10, num(p.xMm + p.widthMm / 2.0 - text.length * h * 0.3))
                g(20, num(y(p.yMm + p.heightMm / 2.0)))
                g(40, num(h)); g(1, text)
            }
        }

        g(0, "ENDSEC")
        g(0, "EOF")
        return sb.toString()
    }
}
