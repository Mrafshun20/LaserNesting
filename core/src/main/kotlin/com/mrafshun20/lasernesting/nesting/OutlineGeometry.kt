package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.PartOutline
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geometry helpers for part outlines. Angles are clockwise degrees
 * (screen coordinates: x right, y down). Outlines are normalized so their
 * bounding-box min corner is at (0, 0); rotating re-normalizes to the new
 * bounding box. Any angle is supported (multiples of 90 are exact).
 */
object OutlineGeometry {

    /** Exact sin/cos for multiples of 90 degrees, plain trig otherwise. */
    private fun sinCos(angleDegrees: Double): Pair<Double, Double> {
        val a = ((angleDegrees % 360.0) + 360.0) % 360.0
        return when (a) {
            0.0 -> 0.0 to 1.0
            90.0 -> 1.0 to 0.0
            180.0 -> 0.0 to -1.0
            270.0 -> -1.0 to 0.0
            else -> {
                val r = a * PI / 180.0
                sin(r) to cos(r)
            }
        }
    }

    private fun rotatePoint(p: Point, s: Double, c: Double): Point =
        // Clockwise on a y-down screen: (x, y) -> (x*c - y*s, x*s + y*c)
        Point(p.x * c - p.y * s, p.x * s + p.y * c)

    /**
     * Rotates [outline] clockwise by [angleDegrees] and re-normalizes so the
     * new bounding-box min corner is at (0, 0). Holes and cutouts follow.
     */
    fun rotateOutline(outline: PartOutline, angleDegrees: Double): PartOutline {
        val (s, c) = sinCos(angleDegrees)
        val pts = outline.points.map { rotatePoint(it, s, c) }
        if (pts.isEmpty()) return outline
        val minX = pts.minOf { it.x }
        val minY = pts.minOf { it.y }
        val maxX = pts.maxOf { it.x }
        val maxY = pts.maxOf { it.y }
        fun shift(p: Point) = Point(p.x - minX, p.y - minY)
        return PartOutline(
            points = pts.map(::shift),
            holes = outline.holes.map {
                CircleHole(shift(rotatePoint(it.center, s, c)), it.radius)
            },
            widthMm = maxX - minX,
            heightMm = maxY - minY,
            cutouts = outline.cutouts.map { loop ->
                loop.map { shift(rotatePoint(it, s, c)) }
            }
        )
    }

    fun rotateOutline(outline: PartOutline, angleDegrees: Int): PartOutline =
        rotateOutline(outline, angleDegrees.toDouble())

    /**
     * Rotates [points] (a closed outline normalized to its bounding box)
     * by [angleDegrees] clockwise and re-normalizes to (0, 0).
     * [widthMm] / [heightMm] are kept for API compatibility.
     */
    @Suppress("UNUSED_PARAMETER")
    fun rotatePoints(
        points: List<Point>,
        angleDegrees: Int,
        widthMm: Double,
        heightMm: Double
    ): List<Point> {
        if (points.isEmpty()) return points
        val a = ((angleDegrees % 360) + 360) % 360
        if (a == 0) return points
        val (s, c) = sinCos(a.toDouble())
        val rotated = points.map { rotatePoint(it, s, c) }
        val minX = rotated.minOf { it.x }
        val minY = rotated.minOf { it.y }
        return rotated.map { Point(it.x - minX, it.y - minY) }
    }

    /** Rotates a circle hole inside a part of base size [widthMm] x [heightMm]. */
    fun rotateHole(
        hole: CircleHole,
        angleDegrees: Int,
        widthMm: Double,
        heightMm: Double
    ): CircleHole {
        val a = ((angleDegrees % 360) + 360) % 360
        if (a == 0) return hole
        val c = hole.center
        val rotated = when (a) {
            90 -> Point(heightMm - c.y, c.x)
            180 -> Point(widthMm - c.x, heightMm - c.y)
            270 -> Point(c.y, widthMm - c.x)
            else -> {
                val (s, co) = sinCos(a.toDouble())
                rotatePoint(c, s, co)
            }
        }
        return CircleHole(rotated, hole.radius)
    }

    /**
     * Bounding-box size of a part after rotating it by [angleDegrees]
     * (rectangle semantics: 0/180 keep the size, 90/270 swap it).
     */
    fun rotatedSize(widthMm: Double, heightMm: Double, angleDegrees: Int): Pair<Double, Double> {
        return when (((angleDegrees % 360) + 360) % 360) {
            90, 270 -> Pair(heightMm, widthMm)
            else -> Pair(widthMm, heightMm)
        }
    }

    /** Signed-area-free polygon area (shoelace), absolute value. */
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

    /** Perimeter of a closed polygon. */
    fun polygonPerimeter(points: List<Point>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            sum += sqrt(dx * dx + dy * dy)
        }
        return sum
    }

    /** True material area: outline minus circular holes (legacy helper). */
    fun outlineArea(points: List<Point>, holes: List<CircleHole>): Double {
        val poly = polygonArea(points)
        val holeArea = holes.sumOf { PI * it.radius * it.radius }
        return (poly - holeArea).coerceAtLeast(0.0)
    }

    /** True material area of a full outline: minus holes and cutouts. */
    fun outlineArea(outline: PartOutline): Double {
        val poly = polygonArea(outline.points)
        val holeArea = outline.holes.sumOf { PI * it.radius * it.radius }
        val cutArea = outline.cutouts.sumOf { polygonArea(it) }
        return (poly - holeArea - cutArea).coerceAtLeast(0.0)
    }

    /** Total cut length of a full outline: outer + holes + cutouts. */
    fun outlinePerimeter(outline: PartOutline): Double {
        return polygonPerimeter(outline.points) +
            outline.holes.sumOf { 2.0 * PI * it.radius } +
            outline.cutouts.sumOf { polygonPerimeter(it) }
    }

    /** Approximates a circle as a regular polygon with [segments] sides. */
    fun circlePolygon(cx: Double, cy: Double, r: Double, segments: Int = 48): List<Point> {
        val n = segments.coerceAtLeast(8)
        return List(n) {
            val t = 2.0 * PI * it / n
            Point(cx + r * cos(t), cy + r * sin(t))
        }
    }
}
