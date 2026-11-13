package com.mrafshun20.lasernesting.nesting

/**
 * Axis-aligned rectangle, in millimetres.
 */
data class Rect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height

    /**
     * Overlap test with a tolerance: rectangles that merely touch, or that
     * overlap by less than [EPS] because of floating-point rounding, do not
     * intersect. Without it a one-ulp sliver can re-split forever.
     */
    fun intersects(other: Rect): Boolean =
        x < other.right - EPS && right - EPS > other.x &&
            y < other.bottom - EPS && bottom - EPS > other.y

    fun contains(other: Rect): Boolean =
        x <= other.x + EPS && y <= other.y + EPS &&
            right >= other.right - EPS && bottom >= other.bottom - EPS

    companion object {
        const val EPS = 1e-6
    }
}

/** Placement rule of the bounding-box packer (lower score = better). */
enum class RectHeuristic {
    /** Tightest fit on the shorter leftover side (classic BSSF). */
    BEST_SHORT_SIDE,

    /** Tightest fit on the longer leftover side. */
    BEST_LONG_SIDE,

    /** Smallest free rectangle that holds the part. */
    BEST_AREA,

    /** Lowest, then leftmost (compact toward the sheet origin). */
    BOTTOM_LEFT
}

/**
 * MaxRects bin-packing, pure Kotlin. Rectangles are placed into the free
 * space; each placement splits the free rectangles it overlaps and prunes
 * rectangles contained in others. Several placement heuristics are
 * available; the nesting engine tries a few and keeps the best result.
 */
class MaxRectsBin(val width: Double, val height: Double) {

    /** Result of a successful insert, with its two-part score. */
    data class Placement(
        val rect: Rect,
        val rotated: Boolean,
        /** Primary score (lower is better). */
        val score1: Double = 0.0,
        /** Secondary score, breaks ties (lower is better). */
        val score2: Double = 0.0
    ) {
        val shortSideLeftover: Double get() = score1
        val longSideLeftover: Double get() = score2
    }

    val usedRectangles = mutableListOf<Rect>()
    private val freeRectangles = mutableListOf(Rect(0.0, 0.0, width, height))

    /** Snapshot of the current free rectangles (for remnant reporting). */
    fun freeRectanglesSnapshot(): List<Rect> = freeRectangles.toList()

    private fun scoreOf(
        free: Rect, w: Double, h: Double, heuristic: RectHeuristic
    ): Pair<Double, Double> {
        val leftoverH = free.width - w
        val leftoverV = free.height - h
        val shortSide = minOf(leftoverH, leftoverV)
        val longSide = maxOf(leftoverH, leftoverV)
        return when (heuristic) {
            RectHeuristic.BEST_SHORT_SIDE -> shortSide to longSide
            RectHeuristic.BEST_LONG_SIDE -> longSide to shortSide
            RectHeuristic.BEST_AREA -> (free.width * free.height - w * h) to shortSide
            RectHeuristic.BOTTOM_LEFT -> (free.y + h) to free.x
        }
    }

    /**
     * Finds the best placement for a rectangle of [rectWidth] x [rectHeight]
     * without committing it. If [allowRotation] is true the 90-degree rotated
     * orientation is tried too. Returns null when it does not fit anywhere.
     */
    fun findBest(
        rectWidth: Double,
        rectHeight: Double,
        allowRotation: Boolean,
        heuristic: RectHeuristic = RectHeuristic.BEST_SHORT_SIDE
    ): Placement? {
        var best1 = Double.MAX_VALUE
        var best2 = Double.MAX_VALUE
        var bestRect: Rect? = null
        var bestRotated = false

        fun consider(free: Rect, w: Double, h: Double, rotated: Boolean) {
            val (s1, s2) = scoreOf(free, w, h, heuristic)
            if (s1 < best1 || (s1 == best1 && s2 < best2)) {
                best1 = s1
                best2 = s2
                bestRect = Rect(free.x, free.y, w, h)
                bestRotated = rotated
            }
        }

        for (free in freeRectangles) {
            if (rectWidth <= free.width && rectHeight <= free.height) {
                consider(free, rectWidth, rectHeight, false)
            }
            if (allowRotation && rectHeight <= free.width && rectWidth <= free.height) {
                consider(free, rectHeight, rectWidth, true)
            }
        }

        val placed = bestRect ?: return null
        return Placement(placed, bestRotated, best1, best2)
    }

    /** Places a rectangle at the best spot; null when it does not fit. */
    fun insert(
        rectWidth: Double,
        rectHeight: Double,
        allowRotation: Boolean,
        heuristic: RectHeuristic = RectHeuristic.BEST_SHORT_SIDE
    ): Placement? {
        val placement = findBest(rectWidth, rectHeight, allowRotation, heuristic) ?: return null
        place(placement.rect)
        return placement
    }

    /** Commits a rectangle found by [findBest] into the bin. */
    fun place(rect: Rect) {
        var i = 0
        while (i < freeRectangles.size) {
            val free = freeRectangles[i]
            if (rect.intersects(free)) {
                splitFreeRect(free, rect)
                freeRectangles.removeAt(i)
            } else {
                i++
            }
        }
        pruneFreeList()
        usedRectangles.add(rect)
    }

    private fun splitFreeRect(free: Rect, placed: Rect) {
        val eps = Rect.EPS
        if (placed.x > free.x + eps && placed.x < free.right - eps) {
            freeRectangles.add(Rect(free.x, free.y, placed.x - free.x, free.height))
        }
        if (placed.right < free.right - eps) {
            freeRectangles.add(
                Rect(placed.right, free.y, free.right - placed.right, free.height)
            )
        }
        if (placed.y > free.y + eps && placed.y < free.bottom - eps) {
            freeRectangles.add(Rect(free.x, free.y, free.width, placed.y - free.y))
        }
        if (placed.bottom < free.bottom - eps) {
            freeRectangles.add(
                Rect(free.x, placed.bottom, free.width, free.bottom - placed.bottom)
            )
        }
    }

    private fun pruneFreeList() {
        var i = 0
        while (i < freeRectangles.size) {
            var j = i + 1
            var removed = false
            while (j < freeRectangles.size) {
                if (freeRectangles[i].contains(freeRectangles[j])) {
                    freeRectangles.removeAt(j)
                } else if (freeRectangles[j].contains(freeRectangles[i])) {
                    freeRectangles.removeAt(i)
                    removed = true
                    break
                } else {
                    j++
                }
            }
            if (!removed) i++
        }
    }
}
