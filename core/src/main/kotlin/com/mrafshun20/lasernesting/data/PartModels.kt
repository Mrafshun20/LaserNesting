package com.mrafshun20.lasernesting.data

/** A 2D point in millimetres. */
data class Point(val x: Double, val y: Double)

/** A circular hole in a part (centre + radius, millimetres). */
data class CircleHole(val center: Point, val radius: Double)

/** Shape of a part. Circles are stored with width == height == diameter. */
enum class PartShape {
    RECTANGLE,
    CIRCLE,
    /** A true outline imported from DXF (closed polyline, optionally with holes). */
    DXF
}

/** Sheet/part material. */
enum class Material(val displayName: String) {
    MILD_STEEL("Mild Steel"),
    STAINLESS_STEEL("Stainless Steel"),
    ALUMINIUM("Aluminium")
}

/**
 * A true part outline imported from DXF: a closed polyline plus circular
 * holes. Points are normalized so the outline bounding-box min corner is
 * at (0, 0). [widthMm] / [heightMm] are the bounding-box size.
 */
data class PartOutline(
    val points: List<Point>,
    val holes: List<CircleHole> = emptyList(),
    val widthMm: Double,
    val heightMm: Double
) {
    companion object {
        /** Builds an outline from raw points, normalizing min corner to (0, 0). */
        fun normalized(points: List<Point>, holes: List<CircleHole> = emptyList()): PartOutline {
            if (points.isEmpty()) return PartOutline(emptyList(), emptyList(), 0.0, 0.0)
            val minX = points.minOf { it.x }
            val minY = points.minOf { it.y }
            val maxX = points.maxOf { it.x }
            val maxY = points.maxOf { it.y }
            val shifted = points.map { Point(it.x - minX, it.y - minY) }
            val shiftedHoles = holes.map {
                CircleHole(Point(it.center.x - minX, it.center.y - minY), it.radius)
            }
            return PartOutline(shifted, shiftedHoles, maxX - minX, maxY - minY)
        }
    }
}
