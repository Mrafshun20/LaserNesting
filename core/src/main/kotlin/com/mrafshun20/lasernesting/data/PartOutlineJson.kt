package com.mrafshun20.lasernesting.data

/**
 * Minimal JSON codec for [PartOutline], so a DXF outline can be stored as
 * a single text column (Room) or a file (desktop) without a serialization
 * library. Format: {"points":[[x,y],...],"holes":[[cx,cy,r],...]}.
 * Numbers are plain doubles. Malformed input decodes to null.
 */
object PartOutlineJson {

    fun encode(outline: PartOutline?): String? {
        if (outline == null) return null
        val sb = StringBuilder()
        sb.append("{\"points\":[")
        outline.points.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append('[').append(p.x).append(',').append(p.y).append(']')
        }
        sb.append("],\"holes\":[")
        outline.holes.forEachIndexed { i, h ->
            if (i > 0) sb.append(',')
            sb.append('[').append(h.center.x).append(',').append(h.center.y)
                .append(',').append(h.radius).append(']')
        }
        sb.append("],\"cutouts\":[")
        outline.cutouts.forEachIndexed { i, poly ->
            if (i > 0) sb.append(',')
            sb.append('[')
            poly.forEachIndexed { k, p ->
                if (k > 0) sb.append(',')
                sb.append(p.x).append(',').append(p.y)
            }
            sb.append(']')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun decode(json: String?): PartOutline? {
        if (json.isNullOrBlank()) return null
        return try {
            val points = mutableListOf<Point>()
            val holes = mutableListOf<CircleHole>()
            // The arrays are delimited by their keys: "points":[...] then
            // "holes":[...] at the end. Find each array by its key.
            val pointsKey = "\"points\":["
            val holesKey = "\"holes\":["
            val pointsStart = json.indexOf(pointsKey)
            val holesStart = json.indexOf(holesKey)
            if (pointsStart < 0 || holesStart < 0) return null
            val pointsContentStart = pointsStart + pointsKey.length
            // The points array ends just before ,"holes".
            val pointsContentEnd = json.indexOf("],\"holes\"", pointsContentStart)
            if (pointsContentEnd < 0) return null
            val pointsArray = json.substring(pointsContentStart, pointsContentEnd)
            val holesContentStart = holesStart + holesKey.length
            // The holes array ends before ,"cutouts" (v3) or at the final "]}" (v2).
            val cutoutsKey = "],\"cutouts\":["
            val cutoutsStart = json.indexOf(cutoutsKey, holesContentStart)
            val holesContentEnd = if (cutoutsStart >= 0) cutoutsStart else json.lastIndexOf("]}")
            if (holesContentEnd < 0 || holesContentEnd < holesContentStart) return null
            val holesArray = json.substring(holesContentStart, holesContentEnd)
            val cutouts = mutableListOf<List<Point>>()
            if (cutoutsStart >= 0) {
                val cStart = cutoutsStart + cutoutsKey.length
                val cEnd = json.lastIndexOf("]}")
                if (cEnd > cStart) {
                    // Each cutout is a flat [x,y,x,y,...] array.
                    var depth = 0
                    val cur = StringBuilder()
                    for (ch in json.substring(cStart, cEnd)) {
                        when (ch) {
                            '[' -> { depth++; cur.setLength(0) }
                            ']' -> {
                                depth--
                                val nums = cur.toString().split(',').mapNotNull { it.trim().toDoubleOrNull() }
                                val poly = ArrayList<Point>()
                                var k = 0
                                while (k + 1 < nums.size) { poly.add(Point(nums[k], nums[k + 1])); k += 2 }
                                if (poly.size >= 3) cutouts.add(poly)
                                cur.setLength(0)
                            }
                            else -> if (depth > 0) cur.append(ch)
                        }
                    }
                }
            }
            parsePairs(pointsArray) { x, y -> points.add(Point(x, y)) }
            parseTriples(holesArray) { x, y, r ->
                holes.add(CircleHole(Point(x, y), r))
            }
            if (points.isEmpty()) return null
            val minX = points.minOf { it.x }
            val minY = points.minOf { it.y }
            val maxX = points.maxOf { it.x }
            val maxY = points.maxOf { it.y }
            PartOutline(points, holes, maxX - minX, maxY - minY, cutouts)
        } catch (_: Exception) {
            null
        }
    }

    /** Parses "[x,y],[x,y],..." into (x, y) pairs. */
    private fun parsePairs(text: String, emit: (Double, Double) -> Unit) {
        val numbers = text.split(',', '[', ']', ' ')
            .mapNotNull { it.toDoubleOrNull() }
        var i = 0
        while (i + 1 < numbers.size) {
            emit(numbers[i], numbers[i + 1])
            i += 2
        }
    }

    /** Parses "[x,y,r],[x,y,r],..." into (x, y, r) triples. */
    private fun parseTriples(text: String, emit: (Double, Double, Double) -> Unit) {
        val numbers = text.split(',', '[', ']', ' ')
            .mapNotNull { it.toDoubleOrNull() }
        var i = 0
        while (i + 2 < numbers.size) {
            emit(numbers[i], numbers[i + 1], numbers[i + 2])
            i += 3
        }
    }
}
