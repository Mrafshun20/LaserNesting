package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.abs

/**
 * Pure geometry helpers for part outlines. All angles are clockwise
 * degrees (0, 90, 180, 270). Outlines are normalized so their bounding-box
 * min corner is at (0, 0); rotating re-normalizes to the new bounding box.
 */
object OutlineGeometry {

    /**
     * Rotates [points] (a closed outline normalized to its bounding box
     * [widthMm] x [heightMm]) by [angleDegrees] clockwise and returns the
     * rotated points, re-normalized so the new bounding-box min corner is
     * at (0, 0). The returned list is closed the same way as the input.
     */
    fun rotatePoints(
        points: List<Point>,
        angleDegrees: Int,
        widthMm: Double,
        heightMm: Double
    ): List<Point> {
        val angle = ((angleDegrees % 360) + 360) % 360
        if (points.isEmpty() || angle == 0) return points
        val rotated = when (angle) {
            // Clockwise 90: (x, y) -> (h - y, x)
            90 -> points.map { Point(heightMm - it.y, it.x) }
            // 180: (x, y) -> (w - x, h - y)
            180 -> points.map { Point(widthMm - it.x, heightMm - it.y) }
            // Clockwise 270: (x, y) -> (y, w - x)
            270 -> points.map { Point(it.y, widthMm - it.x) }
            else -> points
        }
        // Re-normalize so the min corner is at (0, 0) (guards float drift).
        val minX = rotated.minOf { it.x }
        val minY = rotated.minOf { it.y }
        if (abs(minX) < 1e-9 && abs(minY) < 1e-9) return rotated
        return rotated.map { Point(it.x - minX, it.y - minY) }
    }

    /** Rotates a circle hole the same way as [rotatePoints]. */
    fun rotateHole(
        hole: CircleHole,
        angleDegrees: Int,
        widthMm: Double,
        heightMm: Double
    ): CircleHole {
        val angle = ((angleDegrees % 360) + 360) % 360
        if (angle == 0) return hole
        val c = hole.center
        val rotated = when (angle) {
            90 -> Point(heightMm - c.y, c.x)
            180 -> Point(widthMm - c.x, heightMm - c.y)
            270 -> Point(c.y, widthMm - c.x)
            else -> c
        }
        return CircleHole(rotated, hole.radius)
    }

    /**
     * Bounding-box size of a part after rotating it by [angleDegrees].
     * 0 and 180 keep (width, height); 90 and 270 swap them.
     */
    fun rotatedSize(widthMm: Double, heightMm: Double, angleDegrees: Int): Pair<Double, Double> {
        return when (((angleDegrees % 360) + 360) % 360) {
            90, 270 -> Pair(heightMm, widthMm)
            else -> Pair(widthMm, heightMm)
        }
    }

    /** Area of a polygon (shoelace formula), absolute value. */
    fun polygonArea(points: List<Point>): Double {
        if (points.size < 3) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2.0
    }

    /** Perimeter of a polygon (closed). */
    fun polygonPerimeter(points: List<Point>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            sum += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return sum
    }

    /** True area of a part outline minus its circular holes (and cutouts). */
    fun outlineArea(
        points: List<Point>,
        holes: List<CircleHole>,
        cutouts: List<List<Point>> = emptyList()
    ): Double {
        val poly = polygonArea(points)
        val holeArea = holes.sumOf { kotlin.math.PI * it.radius * it.radius }
        val cutArea = cutouts.sumOf { polygonArea(it) }
        return (poly - holeArea - cutArea).coerceAtLeast(0.0)
    }

    /** Rotates every cutout polygon like [rotatePoints] (same normalisation basis). */
    fun rotateCutouts(
        cutouts: List<List<Point>>,
        angleDegrees: Int,
        widthMm: Double,
        heightMm: Double
    ): List<List<Point>> {
        val angle = ((angleDegrees % 360) + 360) % 360
        if (angle == 0 || cutouts.isEmpty()) return cutouts
        return cutouts.map { poly ->
            when (angle) {
                90 -> poly.map { Point(heightMm - it.y, it.x) }
                180 -> poly.map { Point(widthMm - it.x, heightMm - it.y) }
                270 -> poly.map { Point(it.y, widthMm - it.x) }
                else -> poly
            }
        }
    }

    /** Even-odd point-in-polygon test. */
    fun pointInPolygon(x: Double, y: Double, poly: List<Point>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val pi = poly[i]
            val pj = poly[j]
            if ((pi.y > y) != (pj.y > y) &&
                x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Polygon approximating a circle (counter-clockwise), [segments] sides. */
    fun circlePolygon(cx: Double, cy: Double, r: Double, segments: Int = 96): List<Point> {
        return List(segments) { i ->
            val a = 2.0 * kotlin.math.PI * i / segments
            Point(cx + r * kotlin.math.cos(a), cy + r * kotlin.math.sin(a))
        }
    }
}
