package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.abs

/**
 * Pure geometry helpers for part outlines. Angles are clockwise degrees;
 * any integer angle is supported (0, 45, 90, ...), not just the four
 * cardinal ones, so diagonal nesting (the way real CAM nests interlocking
 * parts) works. Outlines are normalized so their bounding-box min corner
 * is at (0, 0); rotating re-normalizes to the new bounding box.
 */
object OutlineGeometry {

    /** Cosine/sine of [angleDegrees], clockwise in the doc's convention. */
    private fun cosSin(angleDegrees: Int): Pair<Double, Double> {
        val angle = ((angleDegrees % 360) + 360) % 360
        val rad = Math.toRadians(angle.toDouble())
        return Pair(kotlin.math.cos(rad), kotlin.math.sin(rad))
    }

    /**
     * Rotates [points] (a closed outline normalized to its bounding box
     * [widthMm] x [heightMm]) by [angleDegrees] clockwise and returns the
     * rotated points, re-normalized so the new bounding-box min corner is
     * at (0, 0). The returned list is closed the same way as the input.
     * Rotation is about the bounding-box centre, so it matches the old
     * 90/180/270 results exactly and extends to any angle.
     */
    fun rotatePoints(
        points: List<Point>,
        angleDegrees: Int,
        widthMm: Double,
        heightMm: Double
    ): List<Point> {
        val angle = ((angleDegrees % 360) + 360) % 360
        if (points.isEmpty() || angle == 0) return points
        val (cos, sin) = cosSin(angle)
        val cx = widthMm / 2.0
        val cy = heightMm / 2.0
        val rotated = points.map { p ->
            val dx = p.x - cx
            val dy = p.y - cy
            Point(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
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
        val (cos, sin) = cosSin(angle)
        val cx = widthMm / 2.0
        val cy = heightMm / 2.0
        val c = hole.center
        val dx = c.x - cx
        val dy = c.y - cy
        val rotated = Point(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
        return CircleHole(rotated, hole.radius)
    }

    /**
     * Bounding-box size of a part after rotating it by [angleDegrees].
     * 0 and 180 keep (width, height); 90 and 270 swap them; any other
     * angle uses the rotated-rectangle AABB formula.
     */
    fun rotatedSize(widthMm: Double, heightMm: Double, angleDegrees: Int): Pair<Double, Double> {
        val angle = ((angleDegrees % 360) + 360) % 360
        if (angle == 0 || angle == 180) return Pair(widthMm, heightMm)
        if (angle == 90 || angle == 270) return Pair(heightMm, widthMm)
        val (cos, sin) = cosSin(angle)
        val w = abs(widthMm * cos) + abs(heightMm * sin)
        val h = abs(widthMm * sin) + abs(heightMm * cos)
        return Pair(w, h)
    }

    /** Area centroid of a polygon (closed). Returns (0,0) for a degenerate ring. */
    fun polygonCentroid(points: List<Point>): Point {
        if (points.size < 3) return Point(0.0, 0.0)
        var crossSum = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val cross = a.x * b.y - b.x * a.y
            crossSum += cross
            cx += (a.x + b.x) * cross
            cy += (a.y + b.y) * cross
        }
        val area2 = crossSum
        if (abs(area2) < 1e-9) return Point(0.0, 0.0)
        return Point(cx / (3.0 * area2), cy / (3.0 * area2))
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
        return cutouts.map { poly -> rotatePoints(poly, angle, widthMm, heightMm) }
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
