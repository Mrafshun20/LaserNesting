package com.mrafshun20.lasernesting.tools

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartOutline
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.Point
import com.mrafshun20.lasernesting.nesting.NestMode
import com.mrafshun20.lasernesting.nesting.NestOptions
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.OutlineGeometry
import com.mrafshun20.lasernesting.nesting.PartInput
import com.mrafshun20.lasernesting.nesting.PlacedPart
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Standalone self-test + benchmark for the nesting engine (no Gradle needed):
 *
 *   kotlinc -include-runtime -d nest-test.jar $(find core/src/main -name '*.kt') tools/NestingSelfTest.kt
 *   java -cp nest-test.jar com.mrafshun20.lasernesting.tools.NestingSelfTestKt
 *
 * Verifies, for every engine mode, that no two parts overlap, every part
 * keeps at least kerf+gap from its neighbours (parts nested inside holes
 * included) and stays inside the edge margin, and prints utilisation/timing.
 */
private fun poly(vararg xy: Double): List<Point> =
    xy.toList().chunked(2).map { Point(it[0], it[1]) }

private fun outline(points: List<Point>, holes: List<CircleHole> = emptyList(),
                    cutouts: List<List<Point>> = emptyList()) =
    PartOutline.normalized(points, holes, cutouts)

private fun dxf(id: Long, name: String, o: PartOutline, qty: Int) = PartInput(
    id, name, PartShape.DXF, o.widthMm, o.heightMm, qty, Material.MILD_STEEL, 3.0, outline = o
)

private fun rect(id: Long, name: String, w: Double, h: Double, qty: Int) = PartInput(
    id, name, PartShape.RECTANGLE, w, h, qty, Material.MILD_STEEL, 3.0
)

private fun circle(id: Long, name: String, d: Double, qty: Int) = PartInput(
    id, name, PartShape.CIRCLE, d, d, qty, Material.MILD_STEEL, 3.0
)

private fun gear(teeth: Int, rOuter: Double, rInner: Double): List<Point> {
    val pts = ArrayList<Point>()
    for (i in 0 until teeth * 4) {
        val a = 2 * PI * i / (teeth * 4)
        val r = if ((i / 2) % 2 == 0) rOuter else rInner
        pts.add(Point(rOuter + r * cos(a), rOuter + r * sin(a)))
    }
    return pts
}

// ---- exact geometry for verification --------------------------------

private fun loopsOf(p: PlacedPart): List<List<Point>> {
    val ox = p.xMm
    val oy = p.yMm
    fun shift(l: List<Point>) = l.map { Point(it.x + ox, it.y + oy) }
    return when (p.shape) {
        PartShape.RECTANGLE -> listOf(shift(poly(0.0, 0.0, p.widthMm, 0.0, p.widthMm, p.heightMm, 0.0, p.heightMm)))
        PartShape.CIRCLE -> listOf(shift(OutlineGeometry.circlePolygon(p.widthMm / 2, p.heightMm / 2, p.widthMm / 2, 180)))
        else -> {
            val o = NestingEngine.placedOutline(p)!!
            val res = ArrayList<List<Point>>()
            res.add(shift(o.points))
            o.cutouts.forEach { res.add(shift(it)) }
            o.holes.forEach { res.add(shift(OutlineGeometry.circlePolygon(it.center.x, it.center.y, it.radius, 180))) }
            res
        }
    }
}

private fun segDist(a: Point, b: Point, c: Point, d: Point): Double {
    fun pd(p: Point, s: Point, e: Point): Double {
        val dx = e.x - s.x; val dy = e.y - s.y
        val l2 = dx * dx + dy * dy
        var t = if (l2 == 0.0) 0.0 else ((p.x - s.x) * dx + (p.y - s.y) * dy) / l2
        t = t.coerceIn(0.0, 1.0)
        val px = s.x + t * dx - p.x; val py = s.y + t * dy - p.y
        return sqrt(px * px + py * py)
    }
    fun ccw(p: Point, q: Point, r: Point) = (r.y - p.y) * (q.x - p.x) - (q.y - p.y) * (r.x - p.x)
    val d1 = ccw(a, b, c); val d2 = ccw(a, b, d); val d3 = ccw(c, d, a); val d4 = ccw(c, d, b)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return 0.0
    return minOf(pd(a, c, d), pd(b, c, d), pd(c, a, b), pd(d, a, b))
}

private fun inside(p: Point, l: List<Point>): Boolean {
    var c = false
    var j = l.size - 1
    for (i in l.indices) {
        if ((l[i].y > p.y) != (l[j].y > p.y) &&
            p.x < (l[j].x - l[i].x) * (p.y - l[i].y) / (l[j].y - l[i].y) + l[i].x) c = !c
        j = i
    }
    return c
}

private fun inMaterial(p: Point, loops: List<List<Point>>): Boolean =
    loops.count { inside(p, it) } % 2 == 1

private fun bbox(loops: List<List<Point>>): DoubleArray {
    val pts = loops[0]
    return doubleArrayOf(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
}

/** Returns a list of problems (empty = valid). */
fun verify(res: NestResult, w: Double, h: Double, margin: Double, spacing: Double): List<String> {
    val problems = ArrayList<String>()
    for (sheet in res.sheets) {
        val parts = sheet.placed
        val all = parts.map { loopsOf(it) }
        for ((i, l) in all.withIndex()) {
            val b = bbox(l)
            if (b[0] < margin - 0.01 || b[1] < margin - 0.01 || b[2] > w - margin + 0.01 || b[3] > h - margin + 0.01)
                problems.add("sheet ${sheet.index + 1}: ${parts[i].name} outside margin $b")
        }
        for (i in all.indices) for (j in i + 1 until all.size) {
            val a = all[i]; val b = all[j]
            val ba = bbox(a); val bb = bbox(b)
            if (ba[2] + spacing < bb[0] || bb[2] + spacing < ba[0] || ba[3] + spacing < bb[1] || bb[3] + spacing < ba[1]) continue
            var minD = Double.MAX_VALUE
            for (la in a) for (lb in b) {
                for (x in la.indices) {
                    val p = la[x]; val q = la[(x + 1) % la.size]
                    for (y in lb.indices) {
                        val r = lb[y]; val s = lb[(y + 1) % lb.size]
                        minD = min(minD, segDist(p, q, r, s))
                    }
                }
            }
            if (minD < spacing - 0.05)
                problems.add("sheet ${sheet.index + 1}: ${parts[i].name}#$i and ${parts[j].name}#$j gap %.2f < %.2f".format(minD, spacing))
            else if (minD > 0.0) {
                // fully inside the other's material means overlap even with no boundary contact
                if (inMaterial(b[0][0], a) || inMaterial(a[0][0], b))
                    problems.add("sheet ${sheet.index + 1}: ${parts[i].name}#$i overlaps ${parts[j].name}#$j")
            }
        }
    }
    return problems
}

private fun report(label: String, res: NestResult, w: Double, h: Double, margin: Double, spacing: Double) {
    val problems = verify(res, w, h, margin, spacing)
    println(
        "%-34s placed %3d/%-3d sheets %d util %5.1f%% cut %6.1f m pierces %4d  %5d ms  [%s]  %s".format(
            label, res.totalPartsPlaced, res.totalPartsRequested, res.sheetsUsed,
            res.overallUtilizationPercent, res.totalCutLengthMm / 1000, res.totalPierces, res.elapsedMs,
            res.engineLabel, if (problems.isEmpty()) "VALID" else "INVALID (${problems.size})"
        )
    )
    problems.take(5).forEach { println("    ! $it") }
    if (problems.isNotEmpty()) System.exit(1)
}

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "samples") { writeSamples(java.io.File(args[1])); return }
    val W = 2000.0; val H = 1000.0; val margin = 10.0; val kerf = 0.2; val gap = 3.0
    val spacing = kerf + gap

    val lShape = outline(poly(0.0, 0.0, 300.0, 0.0, 300.0, 70.0, 70.0, 70.0, 70.0, 200.0, 0.0, 200.0))
    val frame = outline(
        poly(0.0, 0.0, 400.0, 0.0, 400.0, 300.0, 0.0, 300.0),
        holes = listOf(CircleHole(Point(200.0, 150.0), 110.0)),
        cutouts = listOf(poly(20.0, 20.0, 60.0, 20.0, 60.0, 40.0, 20.0, 40.0))
    )
    val gearO = outline(gear(14, 60.0, 50.0), holes = listOf(CircleHole(Point(60.0, 60.0), 15.0)))
    val tri = outline(poly(0.0, 0.0, 180.0, 0.0, 0.0, 120.0))

    val rectOnly = listOf(rect(1, "R200x100", 200.0, 100.0, 12), rect(2, "R150x150", 150.0, 150.0, 10), rect(3, "R90x400", 90.0, 400.0, 5))
    val mixed = listOf(
        dxf(10, "L", lShape, 8), dxf(11, "Frame", frame, 3), dxf(12, "Gear", gearO, 14),
        dxf(13, "Tri", tri, 10), rect(14, "Plate60", 60.0, 60.0, 20), circle(15, "Disc80", 80.0, 12)
    )

    fun opts(mode: NestMode, sheets: Int = 3, step: Int = 0) = NestOptions(
        W, H, sheets, kerf, gap, margin, true, 6000.0, mode = mode, rotationStepDeg = step
    )

    for ((name, set) in listOf("RECT ONLY" to rectOnly, "MIXED" to mixed)) {
        println("== $name ==")
        for (mode in NestMode.entries) {
            val r = NestingEngine.nest(set, opts(mode))
            report("$name ${mode.name}", r, W, H, margin, spacing)
        }
    }
    println("== MIXED with 15 deg steps ==")
    report("MIXED TRUE_SHAPE step15", NestingEngine.nest(mixed, opts(NestMode.TRUE_SHAPE, 3, 15)), W, H, margin, spacing)
    println("== part-in-part ==")
    val pip = listOf(dxf(20, "Frame", frame, 4), rect(21, "Small", 100.0, 100.0, 8), circle(22, "Pin", 60.0, 8))
    report("PIP FAST_BOX", NestingEngine.nest(pip, opts(NestMode.FAST_BOX, 1)), W, H, margin, spacing)
    report("PIP TRUE_SHAPE", NestingEngine.nest(pip, opts(NestMode.TRUE_SHAPE, 1)), W, H, margin, spacing)
    println("== overfilled single sheet (who packs more?) ==")
    val heavy = listOf(
        dxf(30, "L", lShape, 24), dxf(31, "Frame", frame, 6), dxf(32, "Gear", gearO, 40),
        dxf(33, "Tri", tri, 30), rect(34, "Plate60", 60.0, 60.0, 60), circle(35, "Disc80", 80.0, 30)
    )
    for (mode in NestMode.entries) {
        report("HEAVY ${mode.name}", NestingEngine.nest(heavy, opts(mode, 1)), W, H, margin, spacing)
    }
    val pipHeavy = listOf(dxf(40, "Frame", frame, 6), rect(41, "Sq100", 100.0, 100.0, 30), circle(42, "Pin50", 50.0, 40))
    for (mode in NestMode.entries) {
        report("PIPHEAVY ${mode.name}", NestingEngine.nest(pipHeavy, opts(mode, 1)), W, H, margin, spacing)
    }
    println("ALL OK")
}

/** Writes sample exports of the MIXED job to the given directory (manual inspection). */
fun writeSamples(dir: java.io.File) {
    dir.mkdirs()
    val lShape = outline(poly(0.0, 0.0, 300.0, 0.0, 300.0, 70.0, 70.0, 70.0, 70.0, 200.0, 0.0, 200.0))
    val frame = outline(
        poly(0.0, 0.0, 400.0, 0.0, 400.0, 300.0, 0.0, 300.0),
        holes = listOf(CircleHole(Point(200.0, 150.0), 110.0)),
        cutouts = listOf(poly(20.0, 20.0, 60.0, 20.0, 60.0, 40.0, 20.0, 40.0))
    )
    val gearO = outline(gear(14, 60.0, 50.0), holes = listOf(CircleHole(Point(60.0, 60.0), 15.0)))
    val tri = outline(poly(0.0, 0.0, 180.0, 0.0, 0.0, 120.0))
    val parts = listOf(
        dxf(30, "L", lShape, 24), dxf(31, "Frame", frame, 6), dxf(32, "Gear", gearO, 40),
        dxf(33, "Tri", tri, 30), rect(34, "Plate60", 60.0, 60.0, 60), circle(35, "Disc80", 80.0, 30)
    )
    for (mode in listOf(NestMode.FAST_BOX, NestMode.TRUE_SHAPE)) {
        val res = NestingEngine.nest(parts, NestOptions(2000.0, 1000.0, 1, 0.2, 3.0, 10.0, true, 6000.0, mode = mode))
        val tag = mode.name.lowercase()
        java.io.File(dir, "sheet_$tag.dxf").writeText(com.mrafshun20.lasernesting.export.ExportCore.sheetDxf(res.sheets[0], 2000.0, 1000.0))
        java.io.File(dir, "layout_$tag.svg").writeText(com.mrafshun20.lasernesting.export.ExportCore.nestSvg(res, 2000.0, 1000.0))
        java.io.File(dir, "report_$tag.html").writeText(
            com.mrafshun20.lasernesting.export.ExportCore.reportHtml(res, 2000.0, 1000.0, "iBebels", "ByStar Fiber 3015", "Mild steel 3 mm", true)
        )
        java.io.File(dir, "cutlist_$tag.csv").writeText(com.mrafshun20.lasernesting.export.ExportCore.cutListCsv(res, 2000.0, 1000.0))
    }
}
