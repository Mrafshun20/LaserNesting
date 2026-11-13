package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.PartOutline
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * True-shape nesting on a bitmap of the sheet.
 *
 * The sheet is a grid of [cell]-mm squares kept as bit rows. A part
 * orientation is rasterised once into a mask: every cell whose centre lies
 * within (spacing / 2 + half a cell diagonal) of the part's material is
 * marked. Two masks that do not overlap guarantee the parts are at least
 * `spacing` apart (the half-diagonal term makes the test conservative), so
 * the layout is always valid, only slightly looser than an exact geometric
 * nest. Because the interior of a hole or cutout is NOT part of the mask,
 * smaller parts are nested inside the holes of larger ones automatically.
 */
internal class RasterMask(
    val mw: Int,
    val mh: Int,
    /** Material size in cells (without the spacing padding). */
    val matW: Int,
    val matH: Int,
    val rows: Array<LongArray>
) {
    val wordsM: Int = (mw + 63) ushr 6
    private val shiftedCache = arrayOfNulls<Array<LongArray>>(64)

    /** Rows shifted left by [s] bits (0..63); each row has wordsM + 1 words. */
    fun shifted(s: Int): Array<LongArray> {
        shiftedCache[s]?.let { return it }
        val out = Array(mh) { r ->
            val src = rows[r]
            val dst = LongArray(wordsM + 1)
            if (s == 0) {
                for (k in 0 until wordsM) dst[k] = src[k]
            } else {
                for (k in 0 until wordsM) {
                    val w = src[k]
                    if (w != 0L) {
                        dst[k] = dst[k] or (w shl s)
                        dst[k + 1] = dst[k + 1] or (w ushr (64 - s))
                    }
                }
            }
            dst
        }
        shiftedCache[s] = out
        return out
    }

    fun sameShapeAs(other: RasterMask): Boolean {
        if (mw != other.mw || mh != other.mh) return false
        for (r in 0 until mh) if (!rows[r].contentEquals(other.rows[r])) return false
        return true
    }
}

internal class RasterSheet(val gw: Int, val gh: Int, val pad: Int) {
    val totalW = gw + 2 * pad
    val totalH = gh + 2 * pad
    val words = (totalW + 63) ushr 6
    val rows = Array(totalH) { LongArray(words) }

    /** True when row [r] of the shifted mask [sh] does not hit the sheet at [y0] / word offset [wo]. */
    private fun rowFree(sh: Array<LongArray>, r: Int, y0: Int, wo: Int, limit: Int): Boolean {
        val sheetRow = rows[y0 + r]
        val m = sh[r]
        val n = if (m.size < limit) m.size else limit
        for (k in 0 until n) {
            val bits = m[k]
            if (bits != 0L && (sheetRow[wo + k] and bits) != 0L) return false
        }
        return true
    }

    fun fits(mask: RasterMask, x0: Int, y0: Int): Boolean {
        val wo = x0 ushr 6
        val sh = mask.shifted(x0 and 63)
        val limit = words - wo
        val mh = mask.mh
        // Fail fast: probe the middle and last rows first, where a
        // collision is most likely, then the rest.
        val mid = mh ushr 1
        if (!rowFree(sh, mid, y0, wo, limit)) return false
        if (!rowFree(sh, mh - 1, y0, wo, limit)) return false
        for (r in 0 until mh) {
            if (r == mid || r == mh - 1) continue
            if (!rowFree(sh, r, y0, wo, limit)) return false
        }
        return true
    }

    fun place(mask: RasterMask, x0: Int, y0: Int) {
        val wo = x0 ushr 6
        val sh = mask.shifted(x0 and 63)
        val limit = words - wo
        for (r in 0 until mask.mh) {
            val sheetRow = rows[y0 + r]
            val m = sh[r]
            val n = if (m.size < limit) m.size else limit
            for (k in 0 until n) sheetRow[wo + k] = sheetRow[wo + k] or m[k]
        }
    }

    private fun blocked(x: Int, y: Int): Boolean =
        (rows[y][x ushr 6] ushr (x and 63)) and 1L != 0L

    /**
     * Largest free axis-aligned rectangle inside the usable area, in cells:
     * returns [x, y, w, h] relative to the usable origin, or null.
     */
    fun largestFreeRect(): IntArray? {
        val heights = IntArray(gw)
        var bestArea = 0
        var best: IntArray? = null
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                heights[x] = if (blocked(x + pad, y + pad)) 0 else heights[x] + 1
            }
            // Largest rectangle in the histogram (monotonic stack).
            val stack = IntArray(gw + 1)
            var sp = 0
            for (x in 0..gw) {
                val h = if (x == gw) 0 else heights[x]
                while (sp > 0 && heights[stack[sp - 1]] >= h) {
                    val height = heights[stack[--sp]]
                    val left = if (sp == 0) 0 else stack[sp - 1] + 1
                    val width = x - left
                    val area = width * height
                    if (area > bestArea) {
                        bestArea = area
                        best = intArrayOf(left, y - height + 1, width, height)
                    }
                }
                stack[sp++] = x
            }
        }
        return best
    }
}

internal object Rasterizer {

    /**
     * Rasterises [loops] (outer contour + inner contours, local mm with the
     * material bounding-box min corner at the origin) into a mask padded by
     * [pad] cells on every side. [radiusMm] is the dilation distance.
     */
    fun buildMask(
        loops: List<List<Point>>,
        widthMm: Double,
        heightMm: Double,
        cell: Double,
        pad: Int,
        radiusMm: Double
    ): RasterMask {
        val matW = ceil(widthMm / cell - 1e-9).toInt().coerceAtLeast(1)
        val matH = ceil(heightMm / cell - 1e-9).toInt().coerceAtLeast(1)
        val mw = matW + 2 * pad
        val mh = matH + 2 * pad
        val bits = Array(mh) { BooleanArray(mw) }

        // 1) Interior by even-odd scanline fill (holes / cutouts stay free).
        val edges = ArrayList<DoubleArray>()
        for (loop in loops) {
            if (loop.size < 3) continue
            for (i in loop.indices) {
                val a = loop[i]
                val b = loop[(i + 1) % loop.size]
                if (a.y != b.y) edges.add(doubleArrayOf(a.x, a.y, b.x, b.y))
            }
        }
        val xs = DoubleArray(edges.size + 1)
        for (j in 0 until mh) {
            val y = (j + 0.5 - pad) * cell
            var n = 0
            for (e in edges) {
                val y1 = e[1]
                val y2 = e[3]
                if ((y1 <= y && y < y2) || (y2 <= y && y < y1)) {
                    xs[n++] = e[0] + (y - y1) * (e[2] - e[0]) / (y2 - y1)
                }
            }
            if (n < 2) continue
            java.util.Arrays.sort(xs, 0, n)
            var k = 0
            while (k + 1 < n) {
                val xa = xs[k]
                val xb = xs[k + 1]
                val i0 = ceil(xa / cell + pad - 0.5).toInt().coerceAtLeast(0)
                val i1 = floor(xb / cell + pad - 0.5).toInt().coerceAtMost(mw - 1)
                for (i in i0..i1) bits[j][i] = true
                k += 2
            }
        }

        // 2) Dilate the boundary by the spacing radius (disc stamps along
        //    every contour, sampled at half-cell steps).
        val rs = radiusMm + cell * 0.1
        val rs2 = rs * rs
        fun stamp(px: Double, py: Double) {
            val i0 = floor((px - rs) / cell + pad - 0.5).toInt().coerceAtLeast(0)
            val i1 = ceil((px + rs) / cell + pad - 0.5).toInt().coerceAtMost(mw - 1)
            val j0 = floor((py - rs) / cell + pad - 0.5).toInt().coerceAtLeast(0)
            val j1 = ceil((py + rs) / cell + pad - 0.5).toInt().coerceAtMost(mh - 1)
            for (j in j0..j1) {
                val dy = (j + 0.5 - pad) * cell - py
                val row = bits[j]
                for (i in i0..i1) {
                    if (row[i]) continue
                    val dx = (i + 0.5 - pad) * cell - px
                    if (dx * dx + dy * dy <= rs2) row[i] = true
                }
            }
        }
        val step = cell * 0.5
        for (loop in loops) {
            if (loop.size < 2) continue
            for (i in loop.indices) {
                val a = loop[i]
                val b = loop[(i + 1) % loop.size]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = sqrt(dx * dx + dy * dy)
                val steps = ceil(len / step).toInt().coerceAtLeast(1)
                for (s in 0..steps) {
                    val t = s.toDouble() / steps
                    stamp(a.x + dx * t, a.y + dy * t)
                }
            }
        }

        // 3) Pack into bit rows.
        val words = (mw + 63) ushr 6
        val rows = Array(mh) { j ->
            val w = LongArray(words)
            val row = bits[j]
            for (i in 0 until mw) if (row[i]) w[i ushr 6] = w[i ushr 6] or (1L shl (i and 63))
            w
        }
        return RasterMask(mw, mh, matW, matH, rows)
    }

    /** Contours (outer first) of an outline as polygons, circles polygonised. */
    fun loopsOf(outline: PartOutline): List<List<Point>> {
        val loops = ArrayList<List<Point>>()
        loops.add(outline.points)
        loops.addAll(outline.cutouts)
        for (h in outline.holes) {
            val segs = (h.radius * 0.9).toInt().coerceIn(24, 96)
            loops.add(OutlineGeometry.circlePolygon(h.center.x, h.center.y, h.radius, segs))
        }
        return loops
    }
}
