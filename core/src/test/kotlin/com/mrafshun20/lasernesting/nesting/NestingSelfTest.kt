package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartOutline
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Standalone self-test for the nesting engine (no JUnit needed). Run with:
 *
 *   kotlinc core/src/main/kotlin core/src/test/kotlin -include-runtime -d nest-test.jar
 *   java -cp nest-test.jar com.mrafshun20.lasernesting.nesting.NestingSelfTestKt
 *
 * It nests rectangles, L / U shapes and parts with holes, checks every layout
 * for overlaps, spacing and sheet bounds, and prints utilisation + timing.
 */
private fun rectPart(id: Long, w: Double, h: Double, qty: Int, rot: Set<Int> = setOf(0, 90, 180, 270)) =
    PartInput(id, "R$id", PartShape.RECTANGLE, w, h, qty, Material.MILD_STEEL, 3.0, rot)

private fun polyPart(id: Long, name: String, pts: List<Point>, holes: List<CircleHole>, qty: Int,
                     cutouts: List<List<Point>> = emptyList()): PartInput {
    val o = PartOutline.normalized(pts, holes, cutouts)
    return PartInput(id, name, PartShape.DXF, o.widthMm, o.heightMm, qty, Material.MILD_STEEL, 3.0,
        setOf(0, 90, 180, 270), 0, o)
}

private fun lShape(w: Double, h: Double, t: Double) = listOf(
    Point(0.0, 0.0), Point(t, 0.0), Point(t, h - t), Point(w, h - t), Point(w, h), Point(0.0, h)
)

private fun uShape(w: Double, h: Double, t: Double) = listOf(
    Point(0.0, 0.0), Point(t, 0.0), Point(t, h - t), Point(w - t, h - t), Point(w - t, 0.0),
    Point(w, 0.0), Point(w, h), Point(0.0, h)
)

private fun segDist(a: Point, b: Point, c: Point, d: Point): Double {
    fun pd(p: Point, s0: Point, s1: Point): Double {
        val dx = s1.x - s0.x; val dy = s1.y - s0.y
        val l2 = dx * dx + dy * dy
        var t = if (l2 > 0) ((p.x - s0.x) * dx + (p.y - s0.y) * dy) / l2 else 0.0
        t = t.coerceIn(0.0, 1.0)
        return hypot(p.x - (s0.x + t * dx), p.y - (s0.y + t * dy))
    }
    fun cross(o: Point, p: Point, q: Point) = (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)
    val d1 = cross(c, d, a); val d2 = cross(c, d, b); val d3 = cross(a, b, c); val d4 = cross(a, b, d)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return 0.0
    return minOf(pd(a, c, d), pd(b, c, d), pd(c, a, b), pd(d, a, b))
}

private class Shape(val rings: List<List<Point>>, val outer: List<Point>, val inner: List<List<Point>>)

private fun worldShape(p: PlacedPart): Shape {
    val g = PartGeometry.forPlaced(p)
    fun tr(l: List<Point>) = l.map { Point(it.x + p.xMm, it.y + p.yMm) }
    val outer = tr(if (p.shape == PartShape.CIRCLE)
        OutlineGeometry.circlePolygon(p.widthMm / 2, p.widthMm / 2, p.widthMm / 2, 96) else g.outer)
    val inner = ArrayList<List<Point>>()
    for (h in g.holes) inner.add(tr(OutlineGeometry.circlePolygon(h.center.x, h.center.y, h.radius, 96)))
    for (c in g.cutouts) inner.add(tr(c))
    return Shape(listOf(outer) + inner, outer, inner)
}

private fun inMaterial(pt: Point, s: Shape): Boolean =
    OutlineGeometry.pointInPolygon(pt.x, pt.y, s.outer) &&
        s.inner.none { OutlineGeometry.pointInPolygon(pt.x, pt.y, it) }

/** Returns a list of violations (empty = valid). */
private fun validate(result: NestResult, sw: Double, sl: Double, margin: Double, spacing: Double): List<String> {
    val errs = ArrayList<String>()
    for (sheet in result.sheets) {
        val shapes = sheet.placed.map { worldShape(it) }
        for ((i, s) in shapes.withIndex()) {
            for (pt in s.outer) {
                if (pt.x < margin - 0.01 || pt.y < margin - 0.01 || pt.x > sw - margin + 0.01 || pt.y > sl - margin + 0.01)
                    errs.add("sheet ${sheet.index}: part $i out of usable area at $pt")
            }
        }
        for (i in shapes.indices) for (j in i + 1 until shapes.size) {
            val a = shapes[i]; val b = shapes[j]
            // quick bbox reject
            val ax0 = a.outer.minOf { it.x } - spacing; val ax1 = a.outer.maxOf { it.x } + spacing
            val ay0 = a.outer.minOf { it.y } - spacing; val ay1 = a.outer.maxOf { it.y } + spacing
            val bx0 = b.outer.minOf { it.x }; val bx1 = b.outer.maxOf { it.x }
            val by0 = b.outer.minOf { it.y }; val by1 = b.outer.maxOf { it.y }
            if (bx1 < ax0 || bx0 > ax1 || by1 < ay0 || by0 > ay1) continue
            var minD = Double.MAX_VALUE
            for (ra in a.rings) for (rb in b.rings) {
                for (k in ra.indices) for (m in rb.indices) {
                    val d = segDist(ra[k], ra[(k + 1) % ra.size], rb[m], rb[(m + 1) % rb.size])
                    if (d < minD) minD = d
                }
            }
            if (minD < spacing - 0.05) errs.add("sheet ${sheet.index}: parts $i,$j distance $minD < $spacing")
            // containment inside material
            if (b.outer.any { inMaterial(it, a) } || a.outer.any { inMaterial(it, b) })
                errs.add("sheet ${sheet.index}: parts $i,$j overlap (vertex inside material)")
        }
    }
    return errs
}

private fun run(label: String, parts: List<PartInput>, sw: Double = 2000.0, sl: Double = 1000.0,
                sheets: Int = 1, kerf: Double = 0.2, gap: Double = 3.0, margin: Double = 10.0,
                options: NestOptions = NestOptions()): NestResult {
    val t0 = System.nanoTime()
    val r = NestingEngine.nest(parts, sw, sl, sheets, kerf, gap, margin, true, 0.0, options)
    val ms = (System.nanoTime() - t0) / 1e6
    val errs = validate(r, sw, sl, margin, kerf + gap)
    val rem = r.sheets.firstOrNull()?.remnant
    println("%-34s placed %3d/%3d  sheets %d  util %5.1f%%  remnant %s  %-18s %6.0f ms  %s".format(
        label, r.totalPartsPlaced, r.totalPartsRequested, r.sheetsUsed, r.overallUtilizationPercent,
        rem?.let { "%.0fx%.0f".format(it.widthMm, it.heightMm) } ?: "-", r.strategy, ms,
        if (errs.isEmpty()) "VALID" else "INVALID: ${errs.take(3)}"))
    check(errs.isEmpty()) { "$label: ${errs.take(5)}" }
    return r
}

fun main() {
    // 1) Plain rectangles: must be at least as good as bounding-box packing.
    val rects = listOf(rectPart(1, 200.0, 100.0, 30), rectPart(2, 150.0, 150.0, 20), rectPart(3, 80.0, 300.0, 10))
    val rBox = run("rects (maxrects only)", rects, options = NestOptions(trueShape = false))
    val rRas = run("rects (best of both)", rects)
    check(rRas.totalPartsPlaced >= rBox.totalPartsPlaced)

    // 2) L shapes: true-shape should beat bounding boxes clearly.
    val ls = listOf(polyPart(1, "L", lShape(300.0, 200.0, 60.0), emptyList(), 40))
    val lBox = run("L-shapes (maxrects only)", ls, options = NestOptions(trueShape = false))
    val lRas = run("L-shapes (true shape)", ls)
    println("  -> L-shapes placed: bbox ${lBox.totalPartsPlaced} vs true-shape ${lRas.totalPartsPlaced}")
    check(lRas.totalPartsPlaced > lBox.totalPartsPlaced) { "true-shape should place more L parts" }

    // 3) U shapes with small rectangles that can drop into the gap, and flanges with holes.
    val mixed = listOf(
        polyPart(1, "U", uShape(400.0, 250.0, 60.0), emptyList(), 12),
        rectPart(2, 60.0, 150.0, 40),
        polyPart(3, "Flange", OutlineGeometry.circlePolygon(100.0, 100.0, 100.0, 72),
            listOf(CircleHole(Point(100.0, 100.0), 60.0)), 6)
    )
    run("U + small rect + flange", mixed, sheets = 2)

    // 4) A small part must be able to nest inside the hole of a big one.
    val big = polyPart(1, "Ring", OutlineGeometry.circlePolygon(250.0, 250.0, 250.0, 72),
        listOf(CircleHole(Point(250.0, 250.0), 200.0)), 1)
    val small = rectPart(2, 80.0, 80.0, 1)
    val ringRes = run("part inside a hole", listOf(big, small), sw = 560.0, sl = 560.0, margin = 5.0,
        options = NestOptions(quality = NestQuality.HIGH))
    check(ringRes.totalPartsPlaced == 2) { "small part should fit inside the ring's hole" }

    // 5) Cutouts (slots) are respected: part inside a rectangular window.
    val frame = polyPart(1, "Frame", listOf(Point(0.0, 0.0), Point(400.0, 0.0), Point(400.0, 300.0), Point(0.0, 300.0)),
        emptyList(), 1, cutouts = listOf(listOf(Point(40.0, 40.0), Point(360.0, 40.0), Point(360.0, 260.0), Point(40.0, 260.0))))
    run("part inside a window cutout", listOf(frame, rectPart(2, 150.0, 150.0, 1)), sw = 420.0, sl = 320.0, margin = 5.0)

    // 6) Rotation lock (0/180 only) is respected.
    val locked = listOf(rectPart(1, 300.0, 100.0, 10, setOf(0, 180)))
    val lockRes = run("rotation lock 0/180", locked)
    check(lockRes.sheets.all { s -> s.placed.all { it.rotationDegrees == 0 || it.rotationDegrees == 180 } })

    // 7) Priority: high-priority parts are placed first when space runs out.
    val prio = listOf(
        rectPart(1, 900.0, 900.0, 3).copy(priority = 0),
        rectPart(2, 900.0, 900.0, 1).copy(priority = 5)
    )
    val prioRes = run("priority", prio, sw = 2000.0, sl = 1000.0)
    check(prioRes.sheets[0].placed.any { it.partId == 2L })

    // 8) Many small parts (speed check).
    val many = listOf(polyPart(1, "Bracket", lShape(60.0, 40.0, 12.0), listOf(CircleHole(Point(6.0, 6.0), 2.5)), 400))
    val t = System.nanoTime()
    run("400 small brackets", many, sheets = 2)
    println("  total ${(System.nanoTime() - t) / 1e6} ms")

    // 9) Realistic wide-gap case: large sheet 2500x1250 with mixed parts.
    val shop = listOf(
        polyPart(1, "Plate A", lShape(500.0, 350.0, 120.0), listOf(CircleHole(Point(250.0, 100.0), 20.0)), 8),
        polyPart(2, "Plate B", uShape(300.0, 200.0, 50.0), emptyList(), 10),
        rectPart(3, 120.0, 80.0, 60), rectPart(4, 40.0, 40.0, 100)
    )
    run("shop mix 2500x1250", shop, sw = 2500.0, sl = 1250.0, sheets = 2)
    // 10) Tight multi-sheet job: refinement must keep the result valid.
    val tight = listOf(
        polyPart(1, "L", lShape(420.0, 310.0, 90.0), emptyList(), 36),
        rectPart(2, 333.0, 211.0, 14)
    )
    run("tight 2-sheet job", tight, sheets = 2)
    println("ALL NESTING CHECKS PASSED")
}
