package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.PI
import kotlin.math.hypot

/** Whether a contour is cut before (inner) or after (outer) the part is freed. */
enum class CutKind { INNER, OUTER }

/**
 * One closed contour to cut, in sheet coordinates (millimetres, Y down).
 * Either a circle ([circleR] > 0) or a closed polygon ([points]).
 */
class CutOp(
    val partIndex: Int,
    val kind: CutKind,
    val circleCx: Double,
    val circleCy: Double,
    val circleR: Double,
    val points: List<Point>,
    val lengthMm: Double,
    val startX: Double,
    val startY: Double
) {
    val isCircle: Boolean get() = circleR > 0.0
}

/** Ordered cutting sequence of one sheet with its pierce and rapid-travel totals. */
class CutPlan(
    val ops: List<CutOp>,
    val cutLengthMm: Double,
    val pierces: Int,
    val rapidLengthMm: Double
)

/**
 * Cut-order planner: inside contours first (holes, slots), outer contour last,
 * parts visited nearest-neighbour from the machine's home corner (bottom-left),
 * every contour started at the vertex closest to the torch. This is the
 * sequence the DXF export writes and the rapid-travel estimate is based on.
 */
object CutPlanner {

    fun plan(sheet: NestSheet, sheetLengthMm: Double): CutPlan {
        val parts = sheet.placed
        if (parts.isEmpty()) return CutPlan(emptyList(), 0.0, 0, 0.0)

        // Contours per part (unordered).
        class Contour(val kind: CutKind, val circle: Boolean, val cx: Double, val cy: Double,
                      val r: Double, val pts: List<Point>)

        val contoursOf = parts.map { p ->
            val list = ArrayList<Contour>()
            when (p.shape) {
                PartShape.CIRCLE -> {
                    val r = p.widthMm / 2.0
                    list.add(Contour(CutKind.OUTER, true, p.xMm + r, p.yMm + r, r, emptyList()))
                }
                PartShape.DXF -> {
                    val outline = p.outline
                    val pts = NestingEngine.placedOutlinePoints(p)
                    if (outline != null && pts.size >= 3) {
                        for (h in NestingEngine.placedHoles(p)) {
                            list.add(Contour(CutKind.INNER, true, p.xMm + h.center.x,
                                p.yMm + h.center.y, h.radius, emptyList()))
                        }
                        for (poly in NestingEngine.placedCutouts(p)) {
                            list.add(Contour(CutKind.INNER, false, 0.0, 0.0, 0.0,
                                poly.map { Point(p.xMm + it.x, p.yMm + it.y) }))
                        }
                        list.add(Contour(CutKind.OUTER, false, 0.0, 0.0, 0.0,
                            pts.map { Point(p.xMm + it.x, p.yMm + it.y) }))
                    } else {
                        list.add(Contour(CutKind.OUTER, false, 0.0, 0.0, 0.0, rectPts(p)))
                    }
                }
                else -> list.add(Contour(CutKind.OUTER, false, 0.0, 0.0, 0.0, rectPts(p)))
            }
            list
        }

        val ops = ArrayList<CutOp>()
        var curX = 0.0
        var curY = sheetLengthMm
        var rapid = 0.0
        var cutLen = 0.0
        val visited = BooleanArray(parts.size)

        for (step in parts.indices) {
            // Nearest unvisited part, by distance to its bounding-box centre.
            var pick = -1
            var pickDist = Double.MAX_VALUE
            for (i in parts.indices) {
                if (visited[i]) continue
                val p = parts[i]
                val d = hypot(p.xMm + p.widthMm / 2 - curX, p.yMm + p.heightMm / 2 - curY)
                if (d < pickDist) { pickDist = d; pick = i }
            }
            visited[pick] = true

            val remaining = ArrayList(contoursOf[pick].filter { it.kind == CutKind.INNER })
            val outer = contoursOf[pick].filter { it.kind == CutKind.OUTER }
            val ordered = ArrayList<Contour>()
            while (remaining.isNotEmpty()) {
                var bi = 0
                var bd = Double.MAX_VALUE
                for ((k, c) in remaining.withIndex()) {
                    val (sx, sy) = entryPoint(c.circle, c.cx, c.cy, c.r, c.pts, curX, curY)
                    val d = hypot(sx - curX, sy - curY)
                    if (d < bd) { bd = d; bi = k }
                }
                val c = remaining.removeAt(bi)
                ordered.add(c)
                val (sx, sy) = entryPoint(c.circle, c.cx, c.cy, c.r, c.pts, curX, curY)
                curX = sx; curY = sy
            }
            ordered.addAll(outer)

            // Re-walk in order to emit ops with proper start points and travel.
            var px = if (ops.isEmpty()) 0.0 else ops.last().startX
            var py = if (ops.isEmpty()) sheetLengthMm else ops.last().startY
            for (c in ordered) {
                val (sx, sy, rotated) = entry(c.circle, c.cx, c.cy, c.r, c.pts, px, py)
                rapid += hypot(sx - px, sy - py)
                val len = if (c.circle) 2.0 * PI * c.r else OutlineGeometry.polygonPerimeter(c.pts)
                cutLen += len
                ops.add(CutOp(pick, c.kind, c.cx, c.cy, if (c.circle) c.r else 0.0, rotated, len, sx, sy))
                px = sx; py = sy
            }
            curX = px; curY = py
        }
        // Return to the home corner.
        rapid += hypot(curX - 0.0, curY - sheetLengthMm)
        return CutPlan(ops, cutLen, ops.size, rapid)
    }

    private fun rectPts(p: PlacedPart): List<Point> = listOf(
        Point(p.xMm, p.yMm), Point(p.xMm + p.widthMm, p.yMm),
        Point(p.xMm + p.widthMm, p.yMm + p.heightMm), Point(p.xMm, p.yMm + p.heightMm)
    )

    private fun entryPoint(
        circle: Boolean, cx: Double, cy: Double, r: Double, pts: List<Point>, fx: Double, fy: Double
    ): Pair<Double, Double> {
        val (x, y, _) = entry(circle, cx, cy, r, pts, fx, fy)
        return x to y
    }

    private data class Entry(val x: Double, val y: Double, val pts: List<Point>)

    /** Start point nearest to (fx, fy), plus the contour re-ordered to begin there. */
    private fun entry(
        circle: Boolean, cx: Double, cy: Double, r: Double, pts: List<Point>, fx: Double, fy: Double
    ): Entry {
        if (circle) {
            val cands = listOf(Point(cx - r, cy), Point(cx + r, cy), Point(cx, cy - r), Point(cx, cy + r))
            val best = cands.minByOrNull { hypot(it.x - fx, it.y - fy) }!!
            return Entry(best.x, best.y, emptyList())
        }
        var bi = 0
        var bd = Double.MAX_VALUE
        for (i in pts.indices) {
            val d = hypot(pts[i].x - fx, pts[i].y - fy)
            if (d < bd) { bd = d; bi = i }
        }
        val rotated = pts.subList(bi, pts.size) + pts.subList(0, bi)
        return Entry(pts[bi].x, pts[bi].y, rotated)
    }
}
