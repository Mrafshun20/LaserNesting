package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * True-shape nesting on a raster ("pixel") model of the sheet.
 *
 * Every part is rasterised at each allowed rotation into a bit mask that is
 * already inflated by half the required spacing, so two parts can never come
 * closer than kerf + gap. The sheet is a bit grid; a part fits wherever its
 * mask does not overlap occupied cells. Because the mask follows the real
 * outline, parts nest into the concave gaps, slots and holes of other parts —
 * that is what lifts the yield above plain bounding-box packing.
 *
 * The scan for the bottom-left-most free position uses 64-bit row words and
 * skips over blocked positions in jumps, so a full 2000 x 1000 mm sheet nests
 * in well under a second per pass.
 *
 * Rasterisation is conservative (a cell is "occupied" whenever any point of
 * it can be closer than the required spacing), so a result never violates the
 * gap; the price is up to about one cell of extra clearance per edge.
 */

/** Geometry of one part at one rotation, normalised to its bounding box. */
internal data class PartGeom(
    val outer: List<Point>,
    val holes: List<CircleHole>,
    val cutouts: List<List<Point>>,
    val w: Double,
    val h: Double
)

internal object PartGeometry {
    private const val CIRCLE_SEGMENTS = 96

    fun forPart(part: PartInput, angle: Int): PartGeom {
        when (part.shape) {
            PartShape.CIRCLE -> {
                val r = part.widthMm / 2.0
                // Circumscribed polygon, so the raster never under-covers the disc.
                val rr = r / cos(PI / CIRCLE_SEGMENTS)
                return PartGeom(
                    OutlineGeometry.circlePolygon(r, r, rr, CIRCLE_SEGMENTS),
                    emptyList(), emptyList(), part.widthMm, part.widthMm
                )
            }
            PartShape.DXF -> {
                val o = part.outline
                if (o != null && o.points.size >= 3) {
                    val (w, h) = OutlineGeometry.rotatedSize(o.widthMm, o.heightMm, angle)
                    return PartGeom(
                        OutlineGeometry.rotatePoints(o.points, angle, o.widthMm, o.heightMm),
                        o.holes.map { OutlineGeometry.rotateHole(it, angle, o.widthMm, o.heightMm) },
                        OutlineGeometry.rotateCutouts(o.cutouts, angle, o.widthMm, o.heightMm),
                        w, h
                    )
                }
            }
            else -> Unit
        }
        val bw = part.widthMm
        val bh = part.heightMm
        val (w, h) = OutlineGeometry.rotatedSize(bw, bh, angle)
        val rect = listOf(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        return PartGeom(rect, emptyList(), emptyList(), w, h)
    }

    /** Geometry of an already placed part (as drawn), in part-local coordinates. */
    fun forPlaced(p: PlacedPart): PartGeom {
        when (p.shape) {
            PartShape.CIRCLE -> {
                val r = p.widthMm / 2.0
                return PartGeom(
                    OutlineGeometry.circlePolygon(r, r, r / cos(PI / CIRCLE_SEGMENTS), CIRCLE_SEGMENTS),
                    emptyList(), emptyList(), p.widthMm, p.widthMm
                )
            }
            PartShape.DXF -> {
                val pts = NestingEngine.placedOutlinePoints(p)
                if (pts.size >= 3) {
                    return PartGeom(
                        pts, NestingEngine.placedHoles(p), NestingEngine.placedCutouts(p),
                        p.widthMm, p.heightMm
                    )
                }
            }
            else -> Unit
        }
        val rect = listOf(
            Point(0.0, 0.0), Point(p.widthMm, 0.0),
            Point(p.widthMm, p.heightMm), Point(0.0, p.heightMm)
        )
        return PartGeom(rect, emptyList(), emptyList(), p.widthMm, p.heightMm)
    }
}

/** Rasterises closed [rings] (even-odd) into [out]; see [rasterizeRings]. */
internal fun rasterizeRings(
    rings: List<List<Point>>,
    cell: Double,
    offsetX: Double,
    offsetY: Double,
    cols: Int,
    rows: Int,
    reach: Double,
    out: Array<BooleanArray>
) {
    if (rings.isEmpty()) return
    // Flatten edges.
    var edgeCount = 0
    for (ring in rings) edgeCount += ring.size
    val ax = DoubleArray(edgeCount)
    val ay = DoubleArray(edgeCount)
    val bx = DoubleArray(edgeCount)
    val by = DoubleArray(edgeCount)
    var e = 0
    for (ring in rings) {
        val n = ring.size
        for (k in 0 until n) {
            val a = ring[k]
            val b = ring[(k + 1) % n]
            ax[e] = a.x; ay[e] = a.y; bx[e] = b.x; by[e] = b.y
            e++
        }
    }

    // Region of interest (cells), from the ring bounding box.
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    for (i in 0 until edgeCount) {
        minX = min(minX, min(ax[i], bx[i])); maxX = max(maxX, max(ax[i], bx[i]))
        minY = min(minY, min(ay[i], by[i])); maxY = max(maxY, max(ay[i], by[i]))
    }
    val j0 = max(0, floor((minY - reach - offsetY) / cell - 0.5).toInt())
    val j1 = min(rows - 1, ceil((maxY + reach - offsetY) / cell - 0.5).toInt())

    // 1) Interior fill at cell centres (scanline, even-odd).
    val xs = DoubleArray(edgeCount + 1)
    for (j in j0..j1) {
        val yc = offsetY + (j + 0.5) * cell
        var n = 0
        for (i in 0 until edgeCount) {
            val y0 = ay[i]
            val y1 = by[i]
            if ((y0 > yc) != (y1 > yc)) {
                xs[n++] = ax[i] + (yc - y0) * (bx[i] - ax[i]) / (y1 - y0)
            }
        }
        if (n < 2) continue
        java.util.Arrays.sort(xs, 0, n)
        var k = 0
        while (k + 1 < n) {
            val i0 = max(0, ceil((xs[k] - offsetX) / cell - 0.5).toInt())
            val i1 = min(cols - 1, ceil((xs[k + 1] - offsetX) / cell - 0.5).toInt() - 1)
            val row = out[j]
            for (i in i0..i1) row[i] = true
            k += 2
        }
    }

    // 2) Boundary band: every cell whose centre is within `reach` of an edge.
    val r2 = reach * reach
    for (i in 0 until edgeCount) {
        val x0 = ax[i]; val y0 = ay[i]; val x1 = bx[i]; val y1 = by[i]
        val ci0 = max(0, ceil((min(x0, x1) - reach - offsetX) / cell - 0.5).toInt())
        val ci1 = min(cols - 1, floor((max(x0, x1) + reach - offsetX) / cell - 0.5).toInt())
        val cj0 = max(0, ceil((min(y0, y1) - reach - offsetY) / cell - 0.5).toInt())
        val cj1 = min(rows - 1, floor((max(y0, y1) + reach - offsetY) / cell - 0.5).toInt())
        val dx = x1 - x0
        val dy = y1 - y0
        val len2 = dx * dx + dy * dy
        for (j in cj0..cj1) {
            val py = offsetY + (j + 0.5) * cell
            val row = out[j]
            for (c in ci0..ci1) {
                if (row[c]) continue
                val px = offsetX + (c + 0.5) * cell
                var t = if (len2 > 0.0) ((px - x0) * dx + (py - y0) * dy) / len2 else 0.0
                if (t < 0.0) t = 0.0 else if (t > 1.0) t = 1.0
                val qx = x0 + t * dx - px
                val qy = y0 + t * dy - py
                if (qx * qx + qy * qy <= r2) row[c] = true
            }
        }
    }
}

/** A part at one rotation as a row-bit mask (bit k of a row = column k). */
internal class PartMask(
    val rows: Int,
    val cols: Int,
    val bits: Array<LongArray>,
    val first: IntArray,
    val last: IntArray,
    val contiguous: BooleanArray,
    val widthMm: Double,
    val heightMm: Double,
    val angle: Int
)

internal object MaskBuilder {
    fun build(
        g: PartGeom,
        angle: Int,
        cell: Double,
        padCells: Int,
        reachMm: Double
    ): PartMask {
        val padMm = padCells * cell
        val cols = ceil((g.w + 2 * padMm) / cell).toInt() + 1
        val rows = ceil((g.h + 2 * padMm) / cell).toInt() + 1
        val occ = Array(rows) { BooleanArray(cols) }
        val rings = ArrayList<List<Point>>()
        rings.add(g.outer)
        for (h in g.holes) {
            // Inscribed polygon: the hole is never over-stated.
            rings.add(OutlineGeometry.circlePolygon(h.center.x, h.center.y, h.radius, 48))
        }
        rings.addAll(g.cutouts)
        rasterizeRings(rings, cell, -padMm, -padMm, cols, rows, reachMm, occ)

        val words = (cols + 63) ushr 6
        val bits = Array(rows) { LongArray(words) }
        val first = IntArray(rows) { -1 }
        val last = IntArray(rows) { -1 }
        val contiguous = BooleanArray(rows) { true }
        for (j in 0 until rows) {
            val row = occ[j]
            var f = -1
            var l = -1
            var count = 0
            for (i in 0 until cols) {
                if (row[i]) {
                    bits[j][i ushr 6] = bits[j][i ushr 6] or (1L shl (i and 63))
                    if (f < 0) f = i
                    l = i
                    count++
                }
            }
            first[j] = f
            last[j] = l
            contiguous[j] = f < 0 || (l - f + 1 == count)
        }
        return PartMask(rows, cols, bits, first, last, contiguous, g.w, g.h, angle)
    }
}

/** Bit grid of one sheet. */
internal class SheetGrid(val cols: Int, val rows: Int) {
    val words = ((cols + 63) ushr 6) + 2
    val occ = Array(rows) { LongArray(words) }

    private fun shifted(src: LongArray, t: Int, s: Int): Long {
        val n = src.size
        return if (s == 0) {
            if (t in 0 until n) src[t] else 0L
        } else {
            val hi = if (t in 0 until n) src[t] shl s else 0L
            val lo = if (t - 1 in 0 until n) src[t - 1] ushr (64 - s) else 0L
            hi or lo
        }
    }

    /**
     * Returns -1 when [m] fits with its origin at ([gx], [gy]); otherwise the
     * next x worth trying (always > gx).
     */
    fun conflict(m: PartMask, gx: Int, gy: Int): Int {
        val s = gx and 63
        val wo = gx ushr 6
        for (r in 0 until m.rows) {
            val f = m.first[r]
            if (f < 0) continue
            val src = m.bits[r]
            val occRow = occ[gy + r]
            val tLo = f ushr 6
            val tHi = (m.last[r] ushr 6) + 1
            var t = tHi
            while (t >= tLo) {
                val c = shifted(src, t, s) and occRow[wo + t]
                if (c != 0L) {
                    if (!m.contiguous[r]) return gx + 1
                    val bit = 63 - java.lang.Long.numberOfLeadingZeros(c)
                    val b = ((wo + t) shl 6) + bit
                    return max(gx + 1, b - f + 1)
                }
                t--
            }
        }
        return -1
    }

    fun place(m: PartMask, gx: Int, gy: Int) {
        val s = gx and 63
        val wo = gx ushr 6
        for (r in 0 until m.rows) {
            val f = m.first[r]
            if (f < 0) continue
            val src = m.bits[r]
            val occRow = occ[gy + r]
            val tLo = f ushr 6
            val tHi = (m.last[r] ushr 6) + 1
            for (t in tLo..tHi) {
                occRow[wo + t] = occRow[wo + t] or shifted(src, t, s)
            }
        }
    }

    /** First free position in (gy, gx) order, or null. */
    fun scan(m: PartMask, maxGx: Int, maxGy: Int): IntArray? {
        if (maxGx < 0 || maxGy < 0) return null
        for (gy in 0..maxGy) {
            var x = 0
            while (x <= maxGx) {
                val j = conflict(m, x, gy)
                if (j < 0) return intArrayOf(x, gy)
                x = j
            }
        }
        return null
    }
}

/** One nesting layout: parts per sheet plus how many copies of each part are left. */
internal class Layout(
    val placed: List<MutableList<PlacedPart>>,
    val unplacedCounts: Map<Long, Int>
)

internal enum class RasterScore { TOP_EDGE, FIRST_FIT, CENTROID }

internal object RasterNester {

    class Item(val part: PartInput, val colorIndex: Int)

    /**
     * Runs one greedy true-shape pass over [items] (already sorted).
     * Returns null when [deadlineNanos] passes before the pass completes.
     */
    fun run(
        items: List<Item>,
        usableW: Double,
        usableH: Double,
        margin: Double,
        sheetCount: Int,
        cell: Double,
        padCells: Int,
        reachMm: Double,
        allowRotationGlobal: Boolean,
        score: RasterScore,
        masks: HashMap<Long, PartMask>,
        deadlineNanos: Long,
        angleStep: Int = 45
    ): Layout? {
        val cols = ceil(usableW / cell).toInt() + 2 * padCells + 3
        val rows = ceil(usableH / cell).toInt() + 2 * padCells + 3
        val grids = List(sheetCount) { SheetGrid(cols, rows) }
        val placed = List(sheetCount) { mutableListOf<PlacedPart>() }
        val unplaced = HashMap<Long, Int>()
        // Placed-mass centroid per sheet (sum of placed part bbox centres + count),
        // so CENTROID scoring can cluster new parts against the mass already down.
        val massX = DoubleArray(sheetCount)
        val massY = DoubleArray(sheetCount)
        val massCount = IntArray(sheetCount)

        for (item in items) {
            if (System.nanoTime() > deadlineNanos) return null
            val part = item.part
            val angles = distinctAngles(part, allowRotationGlobal, angleStep)
            var done = false
            for (si in 0 until sheetCount) {
                var bestScore = Double.MAX_VALUE
                var bestGx = 0
                var bestGy = 0
                var bestMask: PartMask? = null
                for (angle in angles) {
                    val mask = masks.getOrPut(part.id * 1000L + angle) {
                        MaskBuilder.build(PartGeometry.forPart(part, angle), angle, cell, padCells, reachMm)
                    }
                    val maxGx = floor((usableW - mask.widthMm) / cell + 1e-9).toInt()
                    val maxGy = floor((usableH - mask.heightMm) / cell + 1e-9).toInt()
                    val pos = grids[si].scan(mask, maxGx, maxGy) ?: continue
                    val s = when (score) {
                        RasterScore.TOP_EDGE -> (pos[1] * cell + mask.heightMm) * 100000.0 + pos[0] * cell
                        RasterScore.FIRST_FIT -> pos[1].toDouble() * 100000.0 + pos[0]
                        RasterScore.CENTROID -> {
                            val cx = pos[0] * cell + mask.widthMm / 2.0
                            val cy = pos[1] * cell + mask.heightMm / 2.0
                            if (massCount[si] == 0) {
                                cy * 100000.0 + cx
                            } else {
                                val mx = massX[si] / massCount[si]
                                val my = massY[si] / massCount[si]
                                val dx = cx - mx
                                val dy = cy - my
                                dx * dx + dy * dy
                            }
                        }
                    }
                    if (s < bestScore) {
                        bestScore = s
                        bestGx = pos[0]
                        bestGy = pos[1]
                        bestMask = mask
                    }
                }
                val m = bestMask ?: continue
                grids[si].place(m, bestGx, bestGy)
                massX[si] += bestGx * cell + m.widthMm / 2.0
                massY[si] += bestGy * cell + m.heightMm / 2.0
                massCount[si]++
                val (pw, ph) = OutlineGeometry.rotatedSize(part.widthMm, part.heightMm, m.angle)
                val shapeIsCircle = part.shape == PartShape.CIRCLE
                placed[si].add(
                    PlacedPart(
                        partId = part.id,
                        name = part.name,
                        shape = part.shape,
                        xMm = margin + bestGx * cell,
                        yMm = margin + bestGy * cell,
                        widthMm = if (shapeIsCircle) part.widthMm else pw,
                        heightMm = if (shapeIsCircle) part.widthMm else ph,
                        rotationDegrees = m.angle,
                        colorIndex = item.colorIndex,
                        outline = part.outline
                    )
                )
                done = true
                break
            }
            if (!done) unplaced[part.id] = (unplaced[part.id] ?: 0) + 1
        }
        return Layout(placed, unplaced)
    }

    /**
     * Rotation angles worth trying: rectangles and circles look the same at
     * 0/180 and 90/270 (circles at every angle), so only one of each pair is
     * tried; outlines with unrestricted rotation try every [angleStep]
     * degrees, so diagonal interlocking at 15/10 degrees is possible.
     */
    fun distinctAngles(part: PartInput, allowRotationGlobal: Boolean, angleStep: Int = 45): List<Int> {
        val angles = NestingEngine.allowedAngles(part, allowRotationGlobal)
        return when {
            part.shape == PartShape.CIRCLE -> listOf(angles.first())
            part.shape == PartShape.DXF && part.outline != null -> {
                val unrestricted = angles.toSet() == NestingEngine.ALL_ROTATIONS
                if (unrestricted && angleStep > 0 && angleStep < 45) {
                    (0 until 360 step angleStep).toList()
                } else {
                    angles
                }
            }
            else -> angles.distinctBy { it % 180 }
        }
    }

    /** Largest free axis-aligned rectangle of a finished sheet (usable coordinates). */
    fun largestFreeRect(
        placed: List<PlacedPart>,
        usableW: Double,
        usableH: Double,
        margin: Double,
        spacing: Double,
        cell: Double
    ): Rect? {
        val cols = ceil(usableW / cell).toInt()
        val rows = ceil(usableH / cell).toInt()
        if (cols <= 0 || rows <= 0) return null
        val occ = Array(rows) { BooleanArray(cols) }
        val reach = spacing / 2.0 + cell * 0.7072
        for (p in placed) {
            val g = PartGeometry.forPlaced(p)
            val ox = p.xMm - margin
            val oy = p.yMm - margin
            val outer = g.outer.map { Point(it.x + ox, it.y + oy) }
            rasterizeRings(listOf(outer), cell, 0.0, 0.0, cols, rows, reach, occ)
        }
        // Maximal rectangle in a binary matrix (histogram method).
        val heights = IntArray(cols)
        var bestArea = 0
        var bx = 0
        var by = 0
        var bw = 0
        var bh = 0
        val stack = IntArray(cols + 1)
        for (j in 0 until rows) {
            for (i in 0 until cols) heights[i] = if (occ[j][i]) 0 else heights[i] + 1
            var sp = 0
            for (i in 0..cols) {
                val cur = if (i == cols) 0 else heights[i]
                while (sp > 0 && heights[stack[sp - 1]] >= cur) {
                    val h = heights[stack[--sp]]
                    val left = if (sp == 0) 0 else stack[sp - 1] + 1
                    val w = i - left
                    if (h * w > bestArea) {
                        bestArea = h * w
                        bw = w; bh = h; bx = left; by = j - h + 1
                    }
                }
                stack[sp++] = i
            }
        }
        if (bestArea == 0) return null
        val x = bx * cell
        val y = by * cell
        val w = min(bw * cell, usableW - x)
        val h = min(bh * cell, usableH - y)
        return Rect(x, y, w, h)
    }

    /** Rough cell size (mm) for a sheet, so grids stay a manageable size. */
    fun cellSize(spacing: Double, usableW: Double, usableH: Double, cellFactor: Double): Double {
        val byGap = (spacing / 2.0).coerceIn(1.0, 3.0) * cellFactor
        val byArea = sqrt(max(1.0, usableW * usableH) / 1_200_000.0) * cellFactor
        return max(byGap, byArea).coerceAtLeast(0.8)
    }
}
