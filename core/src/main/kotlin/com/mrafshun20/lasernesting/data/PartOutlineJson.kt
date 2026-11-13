package com.mrafshun20.lasernesting.data

/**
 * Minimal JSON codec for [PartOutline], so a DXF outline can be stored as
 * a single text column (Room) or a file (desktop) without a serialization
 * library.
 *
 * Format: {"points":[[x,y],...],"holes":[[cx,cy,r],...],"cutouts":[[[x,y],...],...]}
 * "cutouts" is optional (older files do not have it). Numbers are plain
 * doubles written with '.' regardless of the device locale. Malformed
 * input decodes to null.
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
        outline.cutouts.forEachIndexed { k, loop ->
            if (k > 0) sb.append(',')
            sb.append('[')
            loop.forEachIndexed { i, p ->
                if (i > 0) sb.append(',')
                sb.append('[').append(p.x).append(',').append(p.y).append(']')
            }
            sb.append(']')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun decode(json: String?): PartOutline? {
        if (json.isNullOrBlank()) return null
        return try {
            val pointsText = arrayAfter(json, "\"points\"") ?: return null
            val holesText = arrayAfter(json, "\"holes\"") ?: ""
            val cutoutsText = arrayAfter(json, "\"cutouts\"") ?: ""

            val points = numbers(pointsText).chunked(2).filter { it.size == 2 }
                .map { Point(it[0], it[1]) }
            if (points.isEmpty()) return null
            val holes = numbers(holesText).chunked(3).filter { it.size == 3 }
                .map { CircleHole(Point(it[0], it[1]), it[2]) }
            val cutouts = splitLoops(cutoutsText).map { loopText ->
                numbers(loopText).chunked(2).filter { it.size == 2 }.map { Point(it[0], it[1]) }
            }.filter { it.size >= 3 }

            val minX = points.minOf { it.x }
            val minY = points.minOf { it.y }
            val maxX = points.maxOf { it.x }
            val maxY = points.maxOf { it.y }
            PartOutline(points, holes, maxX - minX, maxY - minY, cutouts)
        } catch (_: Exception) {
            null
        }
    }

    /** Returns the inside of the balanced [...] that follows [key], or null. */
    private fun arrayAfter(json: String, key: String): String? {
        val k = json.indexOf(key)
        if (k < 0) return null
        val open = json.indexOf('[', k)
        if (open < 0) return null
        var depth = 0
        for (i in open until json.length) {
            when (json[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return json.substring(open + 1, i)
                }
            }
        }
        return null
    }

    /** Splits "[[x,y],...],[[x,y],...]" into the text of each loop. */
    private fun splitLoops(text: String): List<String> {
        val loops = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in text.indices) {
            when (text[i]) {
                '[' -> {
                    if (depth == 0) start = i + 1
                    depth++
                }
                ']' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        loops.add(text.substring(start, i))
                        start = -1
                    }
                }
            }
        }
        return loops
    }

    private fun numbers(text: String): List<Double> =
        text.split(',', '[', ']', ' ').mapNotNull { it.toDoubleOrNull() }
}
