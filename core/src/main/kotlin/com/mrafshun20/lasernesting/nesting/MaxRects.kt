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

    fun intersects(other: Rect): Boolean =
        x < other.right && right > other.x && y < other.bottom && bottom > other.y

    fun contains(other: Rect): Boolean =
        x <= other.x && y <= other.y && right >= other.right && bottom >= other.bottom
}

/**
 * MaxRects bin-packing (Best Short Side Fit heuristic), pure Kotlin.
 * Rectangles are placed into the free space; each placement splits the
 * free rectangles it overlaps and prunes rectangles contained in others.
 * This is the classic algorithm used by nesting tools.
 */
class MaxRectsBin(val width: Double, val height: Double) {

    /** Result of a successful insert, with its best-fit score. */
    data class Placement(
        val rect: Rect,
        val rotated: Boolean,
        /** Leftover short side of the free rectangle used (smaller is tighter). */
        val shortSideLeftover: Double = 0.0,
        /** Leftover long side of the free rectangle used. */
        val longSideLeftover: Double = 0.0
    )

    val usedRectangles = mutableListOf<Rect>()
    private val freeRectangles = mutableListOf(Rect(0.0, 0.0, width, height))

    /** Snapshot of the current free rectangles (for remnant reporting). */
    fun freeRectanglesSnapshot(): List<Rect> = freeRectangles.toList()

    /**
     * Finds the best placement for a rectangle of [rectWidth] x [rectHeight]
     * without committing it. If [allowRotation] is true the 90-degree rotated
     * orientation is tried too. Returns null when it does not fit anywhere.
     */
    fun findBest(rectWidth: Double, rectHeight: Double, allowRotation: Boolean): Placement? {
        var bestShortSide = Double.MAX_VALUE
        var bestLongSide = Double.MAX_VALUE
        var bestRect: Rect? = null
        var bestRotated = false

        for (free in freeRectangles) {
            // Without rotation.
            if (rectWidth <= free.width && rectHeight <= free.height) {
                val leftoverH = free.width - rectWidth
                val leftoverV = free.height - rectHeight
                val shortSide = minOf(leftoverH, leftoverV)
                val longSide = maxOf(leftoverH, leftoverV)
                if (shortSide < bestShortSide ||
                    (shortSide == bestShortSide && longSide < bestLongSide)
                ) {
                    bestShortSide = shortSide
                    bestLongSide = longSide
                    bestRect = Rect(free.x, free.y, rectWidth, rectHeight)
                    bestRotated = false
                }
            }
            // Rotated 90 degrees.
            if (allowRotation && rectHeight <= free.width && rectWidth <= free.height) {
                val leftoverH = free.width - rectHeight
                val leftoverV = free.height - rectWidth
                val shortSide = minOf(leftoverH, leftoverV)
                val longSide = maxOf(leftoverH, leftoverV)
                if (shortSide < bestShortSide ||
                    (shortSide == bestShortSide && longSide < bestLongSide)
                ) {
                    bestShortSide = shortSide
                    bestLongSide = longSide
                    bestRect = Rect(free.x, free.y, rectHeight, rectWidth)
                    bestRotated = true
                }
            }
        }

        val placed = bestRect ?: return null
        return Placement(placed, bestRotated, bestShortSide, bestLongSide)
    }

    /**
     * Tries to place a rectangle of [rectWidth] x [rectHeight].
     * If [allowRotation] is true the 90-degree rotated orientation is tried too.
     * Returns null when the rectangle does not fit anywhere.
     */
    fun insert(rectWidth: Double, rectHeight: Double, allowRotation: Boolean): Placement? {
        val placement = findBest(rectWidth, rectHeight, allowRotation) ?: return null
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
        // Left strip.
        if (placed.x > free.x && placed.x < free.right) {
            freeRectangles.add(Rect(free.x, free.y, placed.x - free.x, free.height))
        }
        // Right strip.
        if (placed.right < free.right) {
            freeRectangles.add(
                Rect(placed.right, free.y, free.right - placed.right, free.height)
            )
        }
        // Top strip.
        if (placed.y > free.y && placed.y < free.bottom) {
            freeRectangles.add(Rect(free.x, free.y, free.width, placed.y - free.y))
        }
        // Bottom strip.
        if (placed.bottom < free.bottom) {
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
