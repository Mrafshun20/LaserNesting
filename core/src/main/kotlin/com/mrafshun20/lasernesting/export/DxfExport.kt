package com.mrafshun20.lasernesting.export

import com.mrafshun20.lasernesting.data.Point
import com.mrafshun20.lasernesting.nesting.CutKind
import com.mrafshun20.lasernesting.nesting.CutOp
import com.mrafshun20.lasernesting.nesting.CutPlanner
import com.mrafshun20.lasernesting.nesting.NestSheet
import com.mrafshun20.lasernesting.nesting.OutlineGeometry
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/**
 * Cutting technology written to the DXF.
 *
 * [leadInMm] is the length of a straight lead-in drawn on layer LEAD_IN: the
 * pierce point lies in the scrap (outside a part's outer contour, inside a
 * hole or slot) and the line runs perpendicular into the contour start.
 * 0 disables it. [tabCount] marks that many micro-joint positions per outer
 * contour on layer TABS (short ticks of [tabWidthMm] across the contour);
 * they are markers for BySoft, the contour itself stays closed. 0 disables.
 */
data class CutTech(
    val leadInMm: Double = 3.0,
    val tabCount: Int = 0,
    val tabWidthMm: Double = 0.5
)

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
        includeLabels: Boolean = true,
        tech: CutTech = CutTech()
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
        val layers = listOf("SHEET" to 8, "CUT_INNER" to 1, "CUT_OUTER" to 7, "LEAD_IN" to 5, "TABS" to 6, "LABELS" to 3)
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
            if (tech.leadInMm > 0.0) {
                leadIn(op, tech.leadInMm)?.let { (a, b) ->
                    g(0, "LINE"); g(8, "LEAD_IN")
                    g(10, num(a.x)); g(20, num(y(a.y))); g(11, num(b.x)); g(21, num(y(b.y)))
                }
            }
            if (tech.tabCount > 0 && op.kind == CutKind.OUTER && !op.isCircle) {
                for ((a, b) in tabTicks(op.points, tech.tabCount, tech.tabWidthMm)) {
                    g(0, "LINE"); g(8, "TABS")
                    g(10, num(a.x)); g(20, num(y(a.y))); g(11, num(b.x)); g(21, num(y(b.y)))
                }
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

    /**
     * Lead-in line (pierce point, contour entry) in sheet coordinates (Y down),
     * or null for degenerate contours. The pierce point is on the scrap side.
     */
    fun leadIn(op: CutOp, length: Double): Pair<Point, Point>? {
        if (length <= 0.0) return null
        if (op.isCircle) {
            val entry = Point(op.startX, op.startY)
            val dx = entry.x - op.circleCx
            val dy = entry.y - op.circleCy
            val d = hypot(dx, dy)
            if (d <= 0.0) return null
            val ux = dx / d
            val uy = dy / d
            return if (op.kind == CutKind.INNER) {
                // Hole: pierce inside the hole (scrap), never beyond its centre.
                val l = min(length, op.circleR * 0.6)
                Point(entry.x - ux * l, entry.y - uy * l) to entry
            } else {
                Point(entry.x + ux * length, entry.y + uy * length) to entry
            }
        }
        val pts = op.points
        if (pts.size < 3) return null
        val a = pts[0]
        val b = pts[1]
        val ex = b.x - a.x
        val ey = b.y - a.y
        val el = hypot(ex, ey)
        if (el <= 0.0) return null
        var nx = -ey / el
        var ny = ex / el
        // n must point out of the contour polygon for an outer cut and into
        // it for an inner cut (that is the scrap side in both cases).
        val probe = 0.01 * length.coerceAtLeast(1.0)
        val insideProbe = OutlineGeometry.pointInPolygon(
            a.x + (b.x - a.x) / el * probe * 2 + nx * probe,
            a.y + (b.y - a.y) / el * probe * 2 + ny * probe, pts
        )
        val wantInside = op.kind == CutKind.INNER
        if (insideProbe != wantInside) { nx = -nx; ny = -ny }
        if (op.kind == CutKind.OUTER) {
            return Point(a.x + nx * length, a.y + ny * length) to a
        }
        // Inner contour: run in diagonally (normal + along the first edge) so the
        // pierce point is strictly inside the slot even at a convex corner.
        val tx = ex / el
        val ty = ey / el
        var l = length
        var tries = 0
        while (tries < 8) {
            val px = a.x + (nx + tx) * l * 0.7071
            val py = a.y + (ny + ty) * l * 0.7071
            if (OutlineGeometry.pointInPolygon(px, py, pts)) return Point(px, py) to a
            l *= 0.5
            tries++
        }
        return null
    }

    /** Micro-joint ticks: [count] short lines across the contour, evenly spaced by length. */
    fun tabTicks(pts: List<Point>, count: Int, width: Double): List<Pair<Point, Point>> {
        val n = pts.size
        if (n < 3 || count <= 0) return emptyList()
        val per = OutlineGeometry.polygonPerimeter(pts)
        if (per <= 0.0) return emptyList()
        val out = ArrayList<Pair<Point, Point>>()
        val step = per / count
        var target = step / 2.0
        var walked = 0.0
        for (i in 0 until n) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            val len = hypot(b.x - a.x, b.y - a.y)
            while (len > 0.0 && target <= walked + len && out.size < count) {
                val t = (target - walked) / len
                val cx = a.x + (b.x - a.x) * t
                val cy = a.y + (b.y - a.y) * t
                val nx = -(b.y - a.y) / len * width
                val ny = (b.x - a.x) / len * width
                out.add(Point(cx - nx, cy - ny) to Point(cx + nx, cy + ny))
                target += step
            }
            walked += len
        }
        return out
    }
}
