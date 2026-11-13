package com.mrafshun20.lasernesting.dxf

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin DXF importer for the Laser Nesting app.
 *
 * No Android dependency: only the Kotlin standard library (plus
 * [java.io.File] for the [parseFile] convenience wrapper).
 *
 * What it does:
 * - Reads the HEADER for `$INSUNITS` and converts everything to millimetres.
 * - Reads the BLOCKS section so INSERT references can be resolved
 *   (including nested INSERTs, up to a small depth, with cycle protection).
 * - Expands the ENTITIES section into line segments (LINE, ARC sampled to
 *   <= 5 degree steps, LWPOLYLINE/POLYLINE with bulge arcs sampled the same
 *   way), circles and closed polylines.
 * - Chains segments whose endpoints meet (0.01 mm match, 0.1 mm snap) into
 *   closed loops. A part is an outer contour plus everything inside it:
 *   loops lying strictly inside another loop are cutouts/slots of that
 *   part, not separate parts. A top-level loop that is tiny next to the
 *   largest NON-SHEET outline (under 1% of its area, or under 100 mm^2)
 *   is an ignored detail/artifact, not a part — so a single-part drawing
 *   is not split into phantom parts. The cutoff is two-pass: sheets are
 *   first identified with the 100 mm^2 floor only, and only then is the
 *   1% cutoff taken from the largest non-sheet outline, so a huge sheet
 *   can never inflate the cutoff and drop real parts. A top-level
 *   outline that contains two or more part-sized outlines is a SHEET
 *   (a nest layout), not a part: its part-sized children are the parts,
 *   and the sheet itself is never emitted as a part — unless three or
 *   more of those children are effectively identical (same
 *   bounding-box size within 0.5 mm and same point count), in which
 *   case the outline is a PANEL whose children are repeated slots
 *   (e.g. ventilation slots), not a sheet: it stays one part and its
 *   identical children are its cutouts. Two identical children is a
 *   pair of identical parts, not a slot pattern. A sheet holds varied
 *   parts; a panel holds repeated identical slots. A genuine
 *   multi-part file still yields one part per large disjoint outer
 *   contour, each with its own holes.
 * - A CIRCLE inside a part outline is a hole of that part; a CIRCLE outside
 *   every outline is itself a round part (sampled as a polygon), unless
 *   it is tiny (same cutoff), in which case it is ignored.
 * - A closed LWPOLYLINE/POLYLINE is its own outline, unless it lies inside
 *   another outline, in which case it is a detail/cutout of that part.
 * - Normalises every part so its bounding-box minimum corner is at (0, 0).
 *
 * The parser never throws on malformed input: bad group codes, truncated
 * entities and unclosable chains are recorded in [DxfParseResult.warnings]
 * and skipped.
 */
object DxfParser {

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Parse DXF content given as text. Never throws. */
    fun parse(text: String): DxfParseResult {
        return try {
            Parser(text).run()
        } catch (e: Exception) {
            DxfParseResult(
                emptyList(),
                listOf("Failed to parse DXF file: ${e.message ?: e.javaClass.simpleName}")
            )
        }
    }

    /** Parse a DXF file from disk. Never throws. */
    fun parseFile(file: java.io.File): DxfParseResult {
        return try {
            parse(file.readText())
        } catch (e: Exception) {
            DxfParseResult(
                emptyList(),
                listOf("Could not read DXF file '${file.path}': ${e.message ?: e.javaClass.simpleName}")
            )
        }
    }

    // ------------------------------------------------------------------
    // Tuning constants
    // ------------------------------------------------------------------

    /** Endpoints closer than this are the same point. */
    private const val MATCH_TOLERANCE_MM = 0.01

    /** Endpoints up to this far apart are snapped together when chaining. */
    private const val SNAP_TOLERANCE_MM = 0.1

    /** Largest angular step when sampling an ARC / CIRCLE / bulge arc. */
    private const val MAX_ARC_STEP_DEGREES = 5.0

    /** Deepest INSERT nesting followed (a block containing an INSERT, ...). */
    private const val MAX_INSERT_DEPTH = 3

    /** How many open chains / inner loops get named individually in warnings. */
    private const val MAX_WARNING_EXAMPLES = 5

    /**
     * A top-level outline (or a lone circle) smaller than this is an
     * ignored detail, not a part. The cutoff is the larger of this floor
     * and [MIN_PART_AREA_FRACTION] of the largest NON-SHEET top-level
     * outline, so a speck stays a speck on both a small bracket and a
     * large panel — and a huge sheet can never inflate the cutoff.
     */
    private const val MIN_PART_AREA_MM2 = 100.0

    /** Fraction of the largest non-sheet top-level outline's area below
     *  which a top-level outline is an ignored detail, not a part. */
    private const val MIN_PART_AREA_FRACTION = 0.01

    /**
     * Two part-sized children count as identical slots (not varied
     * parts) when their bounding-box sizes agree within this tolerance.
     */
    private const val IDENTICAL_CHILD_TOLERANCE_MM = 0.5

    // ------------------------------------------------------------------
    // Raw DXF records
    // ------------------------------------------------------------------

    private class RawEntity(val type: String) {
        val groups = ArrayList<Pair<Int, String>>()
        fun first(code: Int): String? {
            for ((c, v) in groups) if (c == code) return v
            return null
        }
        fun all(code: Int): List<String> {
            val out = ArrayList<String>()
            for ((c, v) in groups) if (c == code) out.add(v)
            return out
        }
        fun double(code: Int, default: Double = 0.0): Double =
            first(code)?.trim()?.toDoubleOrNull() ?: default
        fun int(code: Int, default: Int = 0): Int =
            first(code)?.trim()?.toIntOrNull() ?: default
        fun string(code: Int, default: String = ""): String =
            first(code)?.trim() ?: default
        fun layer(): String = string(8, "0").ifBlank { "0" }
    }

    private class BlockDef(
        val name: String,
        val baseX: Double,
        val baseY: Double,
        val entities: List<RawEntity>
    )

    // ------------------------------------------------------------------
    // 2D affine transform (x' = a*x + b*y + e, y' = c*x + d*y + f).
    // Used so INSERT scale / rotation / mirroring composes exactly, and so
    // arcs can be sampled in local coordinates and then transformed, which
    // stays correct under mirroring (negative scale) and rotation.
    // ------------------------------------------------------------------

    private class Affine(
        val a: Double, val b: Double, val c: Double, val d: Double,
        val e: Double, val f: Double
    ) {
        fun apply(x: Double, y: Double): DxfPoint =
            DxfPoint(a * x + b * y + e, c * x + d * y + f)

        fun apply(p: DxfPoint): DxfPoint = apply(p.x, p.y)

        /** This transform after [other]: first [other], then this. */
        fun after(other: Affine): Affine = Affine(
            a * other.a + b * other.c,
            a * other.b + b * other.d,
            c * other.a + d * other.c,
            c * other.b + d * other.d,
            a * other.e + b * other.f + e,
            c * other.e + d * other.f + f
        )

        /** Scale applied to the X axis / Y axis of the input. */
        fun scaleX(): Double = hypot(a, c)
        fun scaleY(): Double = hypot(b, d)

        companion object {
            fun identity(): Affine = Affine(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
            fun scale(s: Double): Affine = Affine(s, 0.0, 0.0, s, 0.0, 0.0)

            /**
             * Transform for one INSERT: block point p maps to
             * insertion + rotate(rotation) * scale(sx, sy) * (p - base).
             */
            fun forInsert(
                insX: Double, insY: Double,
                sx: Double, sy: Double, rotationDeg: Double,
                baseX: Double, baseY: Double
            ): Affine {
                val r = rotationDeg * PI / 180.0
                val cosR = cos(r)
                val sinR = sin(r)
                val m00 = cosR * sx
                val m01 = -sinR * sy
                val m10 = sinR * sx
                val m11 = cosR * sy
                return Affine(
                    m00, m01, m10, m11,
                    insX - (m00 * baseX + m01 * baseY),
                    insY - (m10 * baseX + m11 * baseY)
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Expanded geometry (all coordinates already in millimetres)
    // ------------------------------------------------------------------

    private class Seg(
        val a: DxfPoint, val b: DxfPoint,
        val layer: String, val block: String?, val order: Int
    )

    private class CircleGeom(
        val center: DxfPoint, val radius: Double,
        val layer: String, val block: String?, val order: Int
    )

    private class ClosedPoly(
        val points: List<DxfPoint>,
        val layer: String, val block: String?, val order: Int
    )

    private class GeomSink {
        val segments = ArrayList<Seg>()
        val circles = ArrayList<CircleGeom>()
        val closedPolys = ArrayList<ClosedPoly>()
        val unsupported = LinkedHashMap<String, Int>()
        private var orderCounter = 0
        fun nextOrder(): Int = orderCounter++
    }

    // ------------------------------------------------------------------
    // The actual parser (one instance per parse call, so no shared state)
    // ------------------------------------------------------------------

    private class Parser(private val text: String) {
        private val warnings = ArrayList<String>()
        private val sink = GeomSink()

        fun run(): DxfParseResult {
            val pairs = readPairs(text)
            val sections = splitSections(pairs)

            val unitScale = readUnits(sections["HEADER"] ?: emptyList())

            val blocks = readBlocks(sections["BLOCKS"] ?: emptyList())

            val entities = splitEntities(sections["ENTITIES"] ?: emptyList())
            val baseTransform = Affine.scale(unitScale)
            expandEntities(
                entities, blocks, baseTransform,
                depth = 0, chain = emptySet(), originBlock = null,
                inheritedLayer = null
            )

            for ((type, count) in sink.unsupported) {
                warnings.add("Skipped $count unsupported ${type} entit${if (count == 1) "y" else "ies"}")
            }

            val parts = groupIntoParts()

            return DxfParseResult(parts, warnings)
        }

        // --------------------------------------------------------------
        // Tokenising and section splitting
        // --------------------------------------------------------------

        /** DXF is flat (code, value) line pairs. Tolerates bad code lines. */
        private fun readPairs(text: String): List<Pair<Int, String>> {
            val lines = text.split('\n')
            val out = ArrayList<Pair<Int, String>>(lines.size / 2)
            var malformed = 0
            var i = 0
            while (i + 1 < lines.size) {
                val code = lines[i].trim().toIntOrNull()
                if (code == null) {
                    // Not a group code: skip this one line and try to resync.
                    malformed++
                    i++
                    continue
                }
                out.add(code to lines[i + 1])
                i += 2
            }
            if (malformed > 0) {
                warnings.add("Skipped $malformed malformed group-code line(s)")
            }
            return out
        }

        /** Split the pair stream into sections by name ("HEADER", ...). */
        private fun splitSections(
            pairs: List<Pair<Int, String>>
        ): Map<String, List<Pair<Int, String>>> {
            val out = LinkedHashMap<String, MutableList<Pair<Int, String>>>()
            var current: MutableList<Pair<Int, String>>? = null
            var pendingSection = false
            for ((code, value) in pairs) {
                val v = value.trim()
                if (code == 0 && v == "SECTION") {
                    pendingSection = true
                    current = null
                    continue
                }
                if (code == 0 && v == "ENDSEC") {
                    pendingSection = false
                    current = null
                    continue
                }
                if (pendingSection && code == 2) {
                    current = out.getOrPut(v) { ArrayList() }
                    pendingSection = false
                    continue
                }
                current?.add(code to value)
            }
            return out
        }

        /** Split a section's pairs into entities: a (0, TYPE) pair starts one. */
        private fun splitEntities(
            pairs: List<Pair<Int, String>>
        ): List<RawEntity> {
            val out = ArrayList<RawEntity>()
            var current: RawEntity? = null
            for ((code, value) in pairs) {
                if (code == 0) {
                    val type = value.trim()
                    if (type == "SECTION" || type == "ENDSEC") {
                        current = null
                        continue
                    }
                    current = RawEntity(type)
                    out.add(current)
                } else {
                    current?.groups?.add(code to value)
                }
            }
            return out
        }

        // --------------------------------------------------------------
        // HEADER: drawing units
        // --------------------------------------------------------------

        private fun readUnits(headerPairs: List<Pair<Int, String>>): Double {
            var units: Int? = null
            var i = 0
            while (i < headerPairs.size) {
                val (code, value) = headerPairs[i]
                if (code == 9 && value.trim() == "\$INSUNITS") {
                    var j = i + 1
                    while (j < headerPairs.size && headerPairs[j].first != 70) {
                        if (headerPairs[j].first == 9) break
                        j++
                    }
                    if (j < headerPairs.size && headerPairs[j].first == 70) {
                        units = headerPairs[j].second.trim().toIntOrNull()
                    }
                    break
                }
                i++
            }
            if (units == null) {
                warnings.add("\$INSUNITS is missing; assuming millimetres")
                return 1.0
            }
            return when (units) {
                4 -> 1.0 // millimetres: no warning, nothing to convert
                1 -> {
                    warnings.add("Drawing units are inches; scaled by x25.4 to millimetres")
                    25.4
                }
                2 -> {
                    warnings.add("Drawing units are feet; scaled by x304.8 to millimetres")
                    304.8
                }
                3 -> {
                    warnings.add("Drawing units are miles; scaled by x1609344.0 to millimetres")
                    1609344.0
                }
                5 -> {
                    warnings.add("Drawing units are centimetres; scaled by x10.0 to millimetres")
                    10.0
                }
                6 -> {
                    warnings.add("Drawing units are metres; scaled by x1000.0 to millimetres")
                    1000.0
                }
                0 -> {
                    warnings.add("Drawing units are unitless; assuming millimetres")
                    1.0
                }
                else -> {
                    warnings.add("Unknown drawing units code $units; assuming millimetres")
                    1.0
                }
            }
        }

        // --------------------------------------------------------------
        // BLOCKS section
        // --------------------------------------------------------------

        private fun readBlocks(pairs: List<Pair<Int, String>>): Map<String, BlockDef> {
            val entities = splitEntities(pairs)
            val out = LinkedHashMap<String, BlockDef>()
            var i = 0
            while (i < entities.size) {
                val entity = entities[i]
                if (entity.type != "BLOCK") {
                    i++
                    continue
                }
                val name = entity.string(2, "")
                val baseX = entity.double(10, 0.0)
                val baseY = entity.double(20, 0.0)
                val body = ArrayList<RawEntity>()
                i++
                while (i < entities.size && entities[i].type != "ENDBLK") {
                    body.add(entities[i])
                    i++
                }
                // i now points at ENDBLK (or the end); the outer loop skips it.
                if (name.isNotEmpty() && !out.containsKey(name)) {
                    out[name] = BlockDef(name, baseX, baseY, body)
                }
                i++
            }
            return out
        }

        // --------------------------------------------------------------
        // Entity expansion (ENTITIES + blocks, recursively via INSERT)
        // --------------------------------------------------------------

        /**
         * Expand [entities] through [transform] into the sink.
         *
         * @param depth INSERT nesting depth so far.
         * @param chain block names on the current INSERT path (cycle guard).
         * @param originBlock outermost non-anonymous block name on this path,
         *   used to name a part after its block; null when the geometry came
         *   straight from ENTITIES or only through anonymous blocks.
         * @param inheritedLayer layer of the enclosing INSERT, which entities
         *   drawn on layer "0" inside a block inherit.
         */
        private fun expandEntities(
            entities: List<RawEntity>,
            blocks: Map<String, BlockDef>,
            transform: Affine,
            depth: Int,
            chain: Set<String>,
            originBlock: String?,
            inheritedLayer: String?
        ) {
            var i = 0
            while (i < entities.size) {
                val entity = entities[i]
                try {
                    when (entity.type) {
                        "LINE" -> {
                            val a = transform.apply(entity.double(10), entity.double(20))
                            val b = transform.apply(entity.double(11), entity.double(21))
                            if (a != b) {
                                sink.segments.add(
                                    Seg(a, b, effectiveLayer(entity, inheritedLayer), originBlock, sink.nextOrder())
                                )
                            }
                        }
                        "ARC" -> {
                            val cx = entity.double(10)
                            val cy = entity.double(20)
                            val r = entity.double(40)
                            if (r > 0.0) {
                                val pts = sampleArc(cx, cy, r, entity.double(50), entity.double(51))
                                    .map { transform.apply(it) }
                                addPolylineSegments(pts, entity, inheritedLayer, originBlock)
                            }
                        }
                        "CIRCLE" -> {
                            val center = transform.apply(entity.double(10), entity.double(20))
                            val sx = transform.scaleX()
                            val sy = transform.scaleY()
                            val radius = entity.double(40) * (sx + sy) / 2.0
                            if (radius > 0.0) {
                                if (abs(sx - sy) > 0.01 * max(sx, sy)) {
                                    warnings.add(
                                        "CIRCLE at (${fmt(center.x)}, ${fmt(center.y)}) is scaled " +
                                            "non-uniformly (${fmt(sx)} x ${fmt(sy)}); " +
                                            "using mean radius ${fmt(radius)} mm"
                                    )
                                }
                                sink.circles.add(
                                    CircleGeom(center, radius, effectiveLayer(entity, inheritedLayer), originBlock, sink.nextOrder())
                                )
                            }
                        }
                        "LWPOLYLINE" -> {
                            expandPolyline(entity, transform, inheritedLayer, originBlock)
                        }
                        "POLYLINE" -> {
                            // Gather the VERTEX entities up to SEQEND.
                            val vertices = ArrayList<RawEntity>()
                            var j = i + 1
                            while (j < entities.size && entities[j].type != "SEQEND") {
                                if (entities[j].type == "VERTEX") vertices.add(entities[j])
                                j++
                            }
                            expandPolylineVertices(entity, vertices, transform, inheritedLayer, originBlock)
                            i = j // skip past SEQEND (outer i++ moves beyond it)
                        }
                        "VERTEX", "SEQEND" -> {
                            // Structural: only meaningful inside a POLYLINE.
                        }
                        "INSERT" -> {
                            val name = entity.string(2, "")
                            val block = blocks[name]
                            when {
                                name.isEmpty() -> warnings.add("INSERT with no block name skipped")
                                block == null -> warnings.add("INSERT references unknown block '$name'; skipped")
                                depth >= MAX_INSERT_DEPTH ->
                                    warnings.add("INSERT of block '$name' is nested deeper than $MAX_INSERT_DEPTH; skipped")
                                chain.contains(name) ->
                                    warnings.add("INSERT cycle through block '$name'; skipped")
                                else -> {
                                    val insertTransform = Affine.forInsert(
                                        entity.double(10), entity.double(20),
                                        entity.double(41, 1.0), entity.double(42, 1.0),
                                        entity.double(50, 0.0),
                                        block.baseX, block.baseY
                                    )
                                    val nextOrigin = originBlock
                                        ?: if (!isAnonymousBlock(name)) name else null
                                    expandEntities(
                                        block.entities, blocks,
                                        transform.after(insertTransform),
                                        depth + 1, chain + name, nextOrigin,
                                        effectiveLayer(entity, inheritedLayer)
                                    )
                                }
                            }
                        }
                        else -> {
                            sink.unsupported[entity.type] =
                                (sink.unsupported[entity.type] ?: 0) + 1
                        }
                    }
                } catch (e: Exception) {
                    warnings.add(
                        "Skipped malformed ${entity.type} entity: " +
                            (e.message ?: e.javaClass.simpleName)
                    )
                }
                i++
            }
        }

        /** Entities on layer "0" inside a block inherit the INSERT's layer. */
        private fun effectiveLayer(entity: RawEntity, inheritedLayer: String?): String {
            val own = entity.layer()
            return if (own != "0" && own.isNotBlank()) own else (inheritedLayer ?: own)
        }

        /** Anonymous blocks are generated ones: "*..." or AutoCAD's "A$C...". */
        private fun isAnonymousBlock(name: String): Boolean =
            name.startsWith("*") || name.startsWith("A\$C")

        // --------------------------------------------------------------
        // Polylines (LWPOLYLINE and POLYLINE/VERTEX)
        // --------------------------------------------------------------

        private fun expandPolyline(
            entity: RawEntity,
            transform: Affine,
            inheritedLayer: String?,
            originBlock: String?
        ) {
            val xs = entity.all(10)
            val ys = entity.all(20)
            val bulges = entity.all(42)
            val count = min(xs.size, ys.size)
            if (count == 0) return
            val vertices = ArrayList<DxfPoint>(count)
            for (k in 0 until count) {
                val x = xs[k].trim().toDoubleOrNull() ?: continue
                val y = ys[k].trim().toDoubleOrNull() ?: continue
                vertices.add(DxfPoint(x, y))
            }
            if (vertices.size < 2) return
            val closed = (entity.int(70, 0) and 1) == 1
            val bulgeValues = bulges.mapNotNull { it.trim().toDoubleOrNull() }
            finishPolyline(vertices, bulgeValues, closed, transform, entity, inheritedLayer, originBlock)
        }

        private fun expandPolylineVertices(
            polyline: RawEntity,
            vertices: List<RawEntity>,
            transform: Affine,
            inheritedLayer: String?,
            originBlock: String?
        ) {
            if (vertices.size < 2) return
            val points = ArrayList<DxfPoint>(vertices.size)
            val bulges = ArrayList<Double>()
            for (v in vertices) {
                points.add(DxfPoint(v.double(10), v.double(20)))
                bulges.add(v.double(42, 0.0))
            }
            val closed = (polyline.int(70, 0) and 1) == 1
            finishPolyline(points, bulges, closed, transform, polyline, inheritedLayer, originBlock)
        }

        /**
         * Sample one polyline (bulge arcs included) through [transform].
         * Closed polylines become outline candidates; open ones become
         * ordinary segments so they can chain with LINE/ARC geometry.
         */
        private fun finishPolyline(
            vertices: List<DxfPoint>,
            bulges: List<Double>,
            closed: Boolean,
            transform: Affine,
            entity: RawEntity,
            inheritedLayer: String?,
            originBlock: String?
        ) {
            val layer = effectiveLayer(entity, inheritedLayer)
            val sampled = samplePolyline(vertices, bulges, closed)
            if (sampled.size < 2) return
            val mapped = sampled.map { transform.apply(it) }
            if (closed) {
                // Drop the closing duplicate of the first point, if present.
                val pts = if (mapped.size > 1 && mapped.first() == mapped.last()) {
                    mapped.dropLast(1)
                } else {
                    mapped
                }
                if (pts.size >= 3) {
                    sink.closedPolys.add(ClosedPoly(pts, layer, originBlock, sink.nextOrder()))
                }
            } else {
                addPolylineSegments(mapped, entity, inheritedLayer, originBlock)
            }
        }

        /** Add consecutive point pairs as segments (skipping zero-length). */
        private fun addPolylineSegments(
            points: List<DxfPoint>,
            entity: RawEntity,
            inheritedLayer: String?,
            originBlock: String?
        ) {
            val layer = effectiveLayer(entity, inheritedLayer)
            for (k in 0 until points.size - 1) {
                val a = points[k]
                val b = points[k + 1]
                if (a != b) {
                    sink.segments.add(Seg(a, b, layer, originBlock, sink.nextOrder()))
                }
            }
        }

        // --------------------------------------------------------------
        // Sampling: ARC, CIRCLE, bulge arcs
        // --------------------------------------------------------------

        /** Points along an arc, start and end included, step <= 5 degrees. */
        private fun sampleArc(
            cx: Double, cy: Double, radius: Double,
            startDeg: Double, endDeg: Double
        ): List<DxfPoint> {
            var sweep = (endDeg - startDeg) % 360.0
            if (sweep < 0) sweep += 360.0
            if (sweep == 0.0) sweep = 360.0
            val steps = max(2, ceil(sweep / MAX_ARC_STEP_DEGREES).toInt())
            val out = ArrayList<DxfPoint>(steps + 1)
            for (k in 0..steps) {
                val a = (startDeg + sweep * k / steps) * PI / 180.0
                out.add(DxfPoint(cx + radius * cos(a), cy + radius * sin(a)))
            }
            return out
        }

        /** Points around a full circle, step <= 5 degrees. */
        private fun sampleCircle(cx: Double, cy: Double, radius: Double): List<DxfPoint> {
            val steps = max(2, ceil(360.0 / MAX_ARC_STEP_DEGREES).toInt())
            val out = ArrayList<DxfPoint>(steps)
            for (k in 0 until steps) {
                val a = (360.0 * k / steps) * PI / 180.0
                out.add(DxfPoint(cx + radius * cos(a), cy + radius * sin(a)))
            }
            return out
        }

        /**
         * One polyline edge, from [from] to [to], with DXF bulge value
         * (tan of a quarter of the included angle; negative = clockwise).
         * Returns the edge's points excluding [from] and including [to].
         */
        private fun sampleBulgeEdge(
            from: DxfPoint, to: DxfPoint, bulge: Double
        ): List<DxfPoint> {
            if (abs(bulge) < 1e-9) return listOf(to)
            val dx = to.x - from.x
            val dy = to.y - from.y
            val chord = hypot(dx, dy)
            if (chord < 1e-12) return listOf(to)
            val included = 4.0 * atan2(bulge, 1.0)
            val sinHalf = sin(included / 2.0)
            if (abs(sinHalf) < 1e-9) return listOf(to)
            val radius = abs(chord / (2.0 * sinHalf))
            val halfChord = chord / 2.0
            val h2 = radius * radius - halfChord * halfChord
            val h = if (h2 > 0.0) sqrt(h2) else 0.0
            val mx = (from.x + to.x) / 2.0
            val my = (from.y + to.y) / 2.0
            val sign = if (bulge > 0) 1.0 else -1.0
            val cx = mx + sign * h * (-dy / chord)
            val cy = my + sign * h * (dx / chord)
            val a0 = atan2(from.y - cy, from.x - cx) * 180.0 / PI
            val a1 = atan2(to.y - cy, to.x - cx) * 180.0 / PI
            var sweep = (a1 - a0) % 360.0
            if (bulge > 0) {
                if (sweep < 0) sweep += 360.0
            } else {
                if (sweep > 0) sweep -= 360.0
            }
            if (sweep == 0.0) return listOf(to)
            val steps = max(2, ceil(abs(sweep) / MAX_ARC_STEP_DEGREES).toInt())
            val out = ArrayList<DxfPoint>(steps)
            for (k in 1..steps) {
                val a = (a0 + sweep * k / steps) * PI / 180.0
                out.add(DxfPoint(cx + radius * cos(a), cy + radius * sin(a)))
            }
            return out
        }

        /** Sample a whole polyline; result includes the closing point if closed. */
        private fun samplePolyline(
            vertices: List<DxfPoint>, bulges: List<Double>, closed: Boolean
        ): List<DxfPoint> {
            val out = ArrayList<DxfPoint>()
            out.add(vertices[0])
            for (k in 1 until vertices.size) {
                val bulge = if (k - 1 < bulges.size) bulges[k - 1] else 0.0
                out.addAll(sampleBulgeEdge(vertices[k - 1], vertices[k], bulge))
            }
            if (closed && vertices.size > 1) {
                val bulge = if (vertices.size - 1 < bulges.size) bulges[vertices.size - 1] else 0.0
                out.addAll(sampleBulgeEdge(vertices.last(), vertices[0], bulge))
            }
            return out
        }

        // --------------------------------------------------------------
        // Grouping segments / polylines / circles into parts
        // --------------------------------------------------------------

        private class OutlineCandidate(
            val points: List<DxfPoint>,
            val layer: String,
            val block: String?,
            val order: Int,
            val area: Double,
            val fromTracedLoop: Boolean
        )

        private fun groupIntoParts(): List<DxfPart> {
            val loops = traceLoops()
            val candidates = ArrayList<OutlineCandidate>()

            for (loop in loops) {
                val area = polygonArea(loop.points)
                if (loop.points.size >= 3 && area > 1e-9) {
                    candidates.add(
                        OutlineCandidate(loop.points, loop.layer, loop.block, loop.order, area, true)
                    )
                }
            }
            for (poly in sink.closedPolys) {
                val area = polygonArea(poly.points)
                if (poly.points.size >= 3 && area > 1e-9) {
                    candidates.add(
                        OutlineCandidate(poly.points, poly.layer, poly.block, poly.order, area, false)
                    )
                }
            }

            // Containment: a candidate strictly inside another candidate is a
            // cutout/slot of that part, not a part of its own.
            val parent = IntArray(candidates.size) { -1 }
            for (i in candidates.indices) {
                var best = -1
                var bestArea = Double.MAX_VALUE
                for (j in candidates.indices) {
                    if (i == j) continue
                    if (candidates[j].area <= candidates[i].area) continue
                    if (!bboxContains(candidates[j].points, candidates[i].points)) continue
                    if (!pointInPolygon(candidates[i].points[0], candidates[j].points)) continue
                    if (candidates[j].area < bestArea) {
                        best = j
                        bestArea = candidates[j].area
                    }
                }
                parent[i] = best
            }

            // Top-level candidate indices (no container).
            val topLevelIdx = ArrayList<Int>()
            for (i in candidates.indices) {
                if (parent[i] < 0) topLevelIdx.add(i)
            }

            // Two-pass cutoff, so a sheet cannot inflate the part-size
            // floor and drop real parts. A part is the largest outer
            // contour plus everything inside it. A top-level outline that
            // is tiny next to the largest NON-SHEET one is a
            // detail/artifact (a speck, a tick mark, a fragment), not a
            // part: dropping it stops a single-part drawing from being
            // split into phantom parts. Large disjoint outer contours are
            // still parts of their own, each with its own holes.
            //
            // Pass 1 (sheet identification, cutoff-independent): a
            // top-level outline is a sheet candidate when it contains
            // two or more direct children of at least the absolute floor
            // (100 mm^2). This uses no fraction of any outline, so a
            // huge sheet cannot inflate it.
            // Direct children of each candidate, by the parent pointer.
            val childrenOf = HashMap<Int, MutableList<Int>>()
            for (i in candidates.indices) {
                val p = parent[i]
                if (p >= 0) childrenOf.getOrPut(p) { ArrayList() }.add(i)
            }
            val sheetCandidateIdx = HashSet<Int>()
            for (idx in topLevelIdx) {
                val floorChildren =
                    (childrenOf[idx] ?: emptyList()).count { candidates[it].area >= MIN_PART_AREA_MM2 }
                if (floorChildren >= 2) sheetCandidateIdx.add(idx)
            }
            // Pass 2: the cutoff is 1% of the largest NON-SHEET
            // top-level outline (a sheet's area must never set it), with
            // the absolute floor as a lower bound. If every top-level
            // outline is a sheet, fall back to the largest top-level
            // outline as before.
            val largestArea = topLevelIdx.maxOfOrNull { candidates[it].area } ?: 0.0
            val largestNonSheetArea = topLevelIdx
                .filter { it !in sheetCandidateIdx }
                .maxOfOrNull { candidates[it].area }
            val referenceArea = largestNonSheetArea ?: largestArea
            val minPartArea = max(MIN_PART_AREA_FRACTION * referenceArea, MIN_PART_AREA_MM2)
            val keptTopLevelIdx = ArrayList<Int>()
            var tinyOutlineCount = 0
            for (idx in topLevelIdx) {
                if (candidates[idx].area >= minPartArea) {
                    keptTopLevelIdx.add(idx)
                } else {
                    tinyOutlineCount++
                }
            }
            if (tinyOutlineCount > 0) {
                warnings.add(
                    "$tinyOutlineCount tiny closed outline(s) ignored as details " +
                        "(area below ${fmt(minPartArea)} mm^2)"
                )
            }

            fun isPartSized(idx: Int): Boolean = candidates[idx].area >= minPartArea

            /** Bounding-box width and height of [points], in millimetres. */
            fun bboxWidthHeight(points: List<DxfPoint>): Pair<Double, Double> {
                var minX = Double.MAX_VALUE
                var minY = Double.MAX_VALUE
                var maxX = -Double.MAX_VALUE
                var maxY = -Double.MAX_VALUE
                for (p in points) {
                    minX = min(minX, p.x)
                    minY = min(minY, p.y)
                    maxX = max(maxX, p.x)
                    maxY = max(maxY, p.y)
                }
                return (maxX - minX) to (maxY - minY)
            }

            /**
             * True when every child in [children] is effectively the
             * same outline: same bounding-box width and height within
             * [IDENTICAL_CHILD_TOLERANCE_MM] and the same point count.
             * Repeated identical slots (e.g. the ventilation slots of
             * a panel) are not varied parts of a sheet. Needs three or
             * more children: two identical outlines is a pair of
             * identical parts, the normal nesting case, not a pattern.
             */
            fun allChildrenIdentical(children: List<Int>): Boolean {
                if (children.size < 3) return false
                val first = candidates[children[0]]
                val (firstW, firstH) = bboxWidthHeight(first.points)
                val firstPts = first.points.size
                for (k in 1 until children.size) {
                    val cand = candidates[children[k]]
                    val (w, h) = bboxWidthHeight(cand.points)
                    if (abs(w - firstW) > IDENTICAL_CHILD_TOLERANCE_MM) return false
                    if (abs(h - firstH) > IDENTICAL_CHILD_TOLERANCE_MM) return false
                    if (cand.points.size != firstPts) return false
                }
                return true
            }

            // Sheets: a kept top-level outline that contains two or more
            // part-sized outlines is a sheet (a nest layout), not a part.
            // Its part-sized children are the parts; the sheet itself is
            // never emitted as a part. EXCEPTION: when three or more of
            // its part-sized children are effectively identical (same
            // bbox size within 0.5 mm and same point count), the outline
            // is a panel with repeated identical slots (e.g. ventilation
            // slots), not a sheet of varied parts — it stays a part and
            // its children are its cutouts. A top-level outline with at
            // most one part-sized child is a part whose inner loops are
            // cutouts.
            val sheetIdx = HashSet<Int>()
            for (idx in keptTopLevelIdx) {
                val partSizedChildren =
                    (childrenOf[idx] ?: emptyList()).filter { isPartSized(it) }
                if (partSizedChildren.size >= 2 && !allChildrenIdentical(partSizedChildren)) {
                    sheetIdx.add(idx)
                }
            }
            if (sheetIdx.isNotEmpty()) {
                warnings.add(
                    "${sheetIdx.size} sheet outline(s) unpacked into their parts " +
                        "(not parts themselves)"
                )
            }

            // Effective part outlines: kept non-sheet top-level outlines,
            // plus the part-sized direct children of sheets.
            val partOutlineIdx = ArrayList<Int>()
            for (idx in keptTopLevelIdx) {
                if (idx in sheetIdx) {
                    for (child in childrenOf[idx] ?: emptyList()) {
                        if (isPartSized(child)) partOutlineIdx.add(child)
                    }
                } else {
                    partOutlineIdx.add(idx)
                }
            }
            val partOutlineSet = partOutlineIdx.toHashSet()

            // Inner loops that are not emitted as parts are cutouts/slots
            // of the part (or sheet) containing them.
            var innerLoopCount = 0
            var innerPolyCount = 0
            for (i in candidates.indices) {
                if (parent[i] < 0 || i in partOutlineSet) continue
                if (candidates[i].fromTracedLoop) innerLoopCount++ else innerPolyCount++
            }
            if (innerLoopCount > 0) {
                warnings.add(
                    "$innerLoopCount closed loop(s) lie inside another outline " +
                        "(slots/cutouts) and are not separate parts"
                )
            }
            if (innerPolyCount > 0) {
                warnings.add(
                    "$innerPolyCount closed polyline(s) lie inside another outline " +
                        "(small details/cutouts) and are not separate parts"
                )
            }

            // Circles: holes of the smallest part outline containing
            // them. A circle inside a sheet but outside every part in
            // it is an ignored detail; a lone circle outside every
            // outline is a round part of its own when part-sized, else
            // an ignored detail.
            val holesByPart = HashMap<Int, MutableList<DxfCircleHole>>()
            val roundParts = ArrayList<OutlineCandidate>()
            val roundPartHoles = ArrayList<List<DxfCircleHole>>()
            var tinyCircleCount = 0
            var sheetCircleCount = 0
            for (circle in sink.circles) {
                var best = -1
                var bestArea = Double.MAX_VALUE
                for (k in partOutlineIdx.indices) {
                    val cand = candidates[partOutlineIdx[k]]
                    if (cand.area >= bestArea) continue
                    if (pointInPolygon(circle.center, cand.points)) {
                        best = k
                        bestArea = cand.area
                    }
                }
                if (best >= 0) {
                    holesByPart.getOrPut(best) { ArrayList() }
                        .add(DxfCircleHole(circle.center, circle.radius))
                } else if (sheetIdx.any { pointInPolygon(circle.center, candidates[it].points) }) {
                    sheetCircleCount++
                } else {
                    val circleArea = PI * circle.radius * circle.radius
                    if (circleArea < minPartArea) {
                        tinyCircleCount++
                    } else {
                        val pts = sampleCircle(circle.center.x, circle.center.y, circle.radius)
                        roundParts.add(
                            OutlineCandidate(pts, circle.layer, circle.block, circle.order, circleArea, false)
                        )
                        roundPartHoles.add(emptyList())
                    }
                }
            }
            if (sheetCircleCount > 0) {
                warnings.add(
                    "$sheetCircleCount circle(s) inside a sheet but outside every part " +
                        "ignored as details"
                )
            }
            if (tinyCircleCount > 0) {
                warnings.add(
                    "$tinyCircleCount tiny circle(s) outside every outline ignored as details " +
                        "(area below ${fmt(minPartArea)} mm^2)"
                )
            }

            // Discovery order: loops/polylines/circles in the order found.
            val allParts = ArrayList<OutlineCandidate>()
            val allHoles = ArrayList<List<DxfCircleHole>>()
            val allCutouts = ArrayList<List<List<DxfPoint>>>()
            for (k in partOutlineIdx.indices) {
                allParts.add(candidates[partOutlineIdx[k]])
                allHoles.add(holesByPart[k] ?: emptyList())
                // Direct inner loops that are not parts themselves are the
                // part's cutouts (slots, windows, notches): they must be
                // cut, drawn and subtracted from the area.
                val cuts = (childrenOf[partOutlineIdx[k]] ?: emptyList())
                    .filter { it !in partOutlineSet }
                    .map { candidates[it].points }
                allCutouts.add(cuts)
            }
            for (i in roundParts.indices) {
                allParts.add(roundParts[i])
                allHoles.add(roundPartHoles[i])
                allCutouts.add(emptyList())
            }
            val order = allParts.indices.sortedBy { allParts[it].order }
            val result = ArrayList<DxfPart>()
            var partNumber = 0
            for (idx in order) {
                partNumber++
                result.add(buildPart(allParts[idx], allHoles[idx], allCutouts[idx], partNumber))
            }
            return result
        }

        /** Translate one outline + its holes so the bbox min corner is (0, 0). */
        private fun buildPart(
            candidate: OutlineCandidate,
            holes: List<DxfCircleHole>,
            cutouts: List<List<DxfPoint>>,
            partNumber: Int
        ): DxfPart {
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (p in candidate.points) {
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
            val outline = candidate.points.map { DxfPoint(it.x - minX, it.y - minY) }
            val normHoles = holes.map {
                DxfCircleHole(DxfPoint(it.center.x - minX, it.center.y - minY), it.radius)
            }
            val name = candidate.block?.takeIf { isCleanPartName(it) } ?: "Part $partNumber"
            return DxfPart(
                name = name,
                outline = outline,
                holes = normHoles,
                widthMm = maxX - minX,
                heightMm = maxY - minY,
                layer = candidate.layer,
                cutouts = cutouts.map { loop -> loop.map { DxfPoint(it.x - minX, it.y - minY) } }
            )
        }

        /** A block name is usable as a part name when it looks like an identifier. */
        private fun isCleanPartName(name: String): Boolean {
            if (name.isBlank()) return false
            if (isAnonymousBlock(name)) return false
            for (ch in name) {
                if (!(ch.isLetterOrDigit() || ch == '_' || ch == '-')) return false
            }
            return name[0].isLetter() || name[0] == '_'
        }

        // --------------------------------------------------------------
        // Chaining segments into closed loops
        // --------------------------------------------------------------

        private class TracedLoop(
            val points: List<DxfPoint>,
            val layer: String,
            val block: String?,
            val order: Int
        )

        private class AdjEntry(val segIndex: Int, val other: DxfPoint)

        private fun traceLoops(): List<TracedLoop> {
            val segments = sink.segments
            if (segments.isEmpty()) return emptyList()

            // Snap endpoints within SNAP_TOLERANCE_MM (union-find over a grid).
            val endpoints = ArrayList<DxfPoint>()
            val endpointIndex = HashMap<DxfPoint, Int>()
            fun indexOf(p: DxfPoint): Int {
                val existing = endpointIndex[p]
                if (existing != null) return existing
                val idx = endpoints.size
                endpoints.add(p)
                endpointIndex[p] = idx
                return idx
            }
            val snappedSegs = ArrayList<Pair<Int, Int>>() // (aIdx, bIdx) into endpoints
            for (seg in segments) {
                snappedSegs.add(indexOf(seg.a) to indexOf(seg.b))
            }
            val parent = IntArray(endpoints.size) { it }
            fun find(x: Int): Int {
                var root = x
                while (parent[root] != root) root = parent[root]
                var node = x
                while (parent[node] != root) {
                    val next = parent[node]
                    parent[node] = root
                    node = next
                }
                return root
            }
            fun union(x: Int, y: Int) {
                val rx = find(x)
                val ry = find(y)
                if (rx != ry) parent[rx] = ry
            }
            val cellSize = SNAP_TOLERANCE_MM
            val grid = HashMap<Pair<Long, Long>, MutableList<Int>>()
            for (idx in endpoints.indices) {
                val p = endpoints[idx]
                val key = floor(p.x / cellSize).toLong() to floor(p.y / cellSize).toLong()
                grid.getOrPut(key) { ArrayList() }.add(idx)
            }
            for (idx in endpoints.indices) {
                val p = endpoints[idx]
                val cx = floor(p.x / cellSize).toLong()
                val cy = floor(p.y / cellSize).toLong()
                for (dx in -1L..1L) {
                    for (dy in -1L..1L) {
                        val neighbours = grid[cx + dx to cy + dy] ?: continue
                        for (other in neighbours) {
                            if (other <= idx) continue
                            val q = endpoints[other]
                            if (hypot(p.x - q.x, p.y - q.y) <= SNAP_TOLERANCE_MM) {
                                union(idx, other)
                            }
                        }
                    }
                }
            }
            val clusters = HashMap<Int, MutableList<DxfPoint>>()
            for (idx in endpoints.indices) {
                clusters.getOrPut(find(idx)) { ArrayList() }.add(endpoints[idx])
            }
            val representative = HashMap<DxfPoint, DxfPoint>()
            for ((_, members) in clusters) {
                var sx = 0.0
                var sy = 0.0
                for (m in members) {
                    sx += m.x
                    sy += m.y
                }
                val rep = DxfPoint(sx / members.size, sy / members.size)
                for (m in members) representative[m] = rep
            }

            // Canonical segments (degenerate ones dropped) + adjacency.
            // NOTE: duplicates are removed AFTER snapping, on the canonical
            // endpoints: two segments whose endpoints only differ within the
            // snap tolerance are the same edge, and removing them before
            // snapping would leave parallel edges that mislead the walk.
            data class CanonSeg(val a: DxfPoint, val b: DxfPoint, val src: Seg)
            val canon = ArrayList<CanonSeg>()
            val seenEdges = HashSet<Pair<QuantPoint, QuantPoint>>()
            for (i in segments.indices) {
                val a = representative[endpoints[snappedSegs[i].first]]!!
                val b = representative[endpoints[snappedSegs[i].second]]!!
                if (a == b) continue
                val ka = QuantPoint(a)
                val kb = QuantPoint(b)
                val key = if (ka <= kb) ka to kb else kb to ka
                if (!seenEdges.add(key)) continue
                canon.add(CanonSeg(a, b, segments[i]))
            }
            val adjacency = HashMap<DxfPoint, MutableList<AdjEntry>>()
            for (i in canon.indices) {
                val seg = canon[i]
                adjacency.getOrPut(seg.a) { ArrayList() }.add(AdjEntry(i, seg.b))
                adjacency.getOrPut(seg.b) { ArrayList() }.add(AdjEntry(i, seg.a))
            }

            // Greedy walk: at each junction take the straightest continuation
            // (smallest turn from the incoming direction). Deliberately NOT
            // preferring a segment that closes the loop: at a T-junction the
            // closing segment can be a shortcut across the outline (a spur
            // back to the start node), and taking it would cut the loop
            // short. Spurs and detail lines hanging off an outline are left
            // as open chains. The loop closes naturally when the walk runs
            // out of unused segments at the start node.
            val used = BooleanArray(canon.size)
            val loops = ArrayList<TracedLoop>()
            var openChains = 0
            var openLengthMm = 0.0
            val openExamples = ArrayList<String>()
            for (start in canon.indices) {
                if (used[start]) continue
                used[start] = true
                val first = canon[start]
                val chain = ArrayList<DxfPoint>()
                chain.add(first.a)
                chain.add(first.b)
                val usedSegs = ArrayList<Seg>()
                usedSegs.add(first.src)
                var current = first.b
                var incoming = atan2(first.b.y - first.a.y, first.b.x - first.a.x)
                var closed = false
                var guard = 0
                while (guard <= canon.size) {
                    guard++
                    val options = (adjacency[current] ?: emptyList())
                        .filter { !used[it.segIndex] }
                    if (options.isEmpty()) break
                    val chosen = options.minByOrNull { entry ->
                        val dir = atan2(entry.other.y - current.y, entry.other.x - current.x)
                        abs(wrapAngle(dir - incoming))
                    }!!
                    used[chosen.segIndex] = true
                    usedSegs.add(canon[chosen.segIndex].src)
                    val next = chosen.other
                    incoming = atan2(next.y - current.y, next.x - current.x)
                    current = next
                    if (next == chain[0]) {
                        closed = true
                        break
                    }
                    chain.add(next)
                }
                if (closed && chain.size >= 3) {
                    // chain holds the start point once; drop nothing: the
                    // closing step did not append a duplicate.
                    loops.add(
                        TracedLoop(
                            chain.toList(),
                            dominantLayer(usedSegs),
                            dominantBlock(usedSegs),
                            usedSegs.minOf { it.order }
                        )
                    )
                } else {
                    openChains++
                    var len = 0.0
                    for (k in 0 until chain.size - 1) {
                        len += hypot(chain[k + 1].x - chain[k].x, chain[k + 1].y - chain[k].y)
                    }
                    openLengthMm += len
                    if (openExamples.size < MAX_WARNING_EXAMPLES) {
                        var mnx = Double.MAX_VALUE
                        var mny = Double.MAX_VALUE
                        var mxx = -Double.MAX_VALUE
                        var mxy = -Double.MAX_VALUE
                        for (p in chain) {
                            mnx = min(mnx, p.x)
                            mny = min(mny, p.y)
                            mxx = max(mxx, p.x)
                            mxy = max(mxy, p.y)
                        }
                        openExamples.add(
                            "${fmt(mnx)}..${fmt(mxx)} x ${fmt(mny)}..${fmt(mxy)} mm " +
                                "(${chain.size} points, ${fmt(len)} mm long)"
                        )
                    }
                }
            }
            if (openChains > 0) {
                val detail = if (openExamples.isEmpty()) "" else
                    "; e.g. " + openExamples.joinToString("; ")
                warnings.add(
                    "Skipped $openChains unclosed chain(s) " +
                        "(total ${fmt(openLengthMm)} mm)$detail"
                )
            }
            return loops
        }

        /** A point quantised to the 0.01 mm match tolerance, for hash keys. */
        private class QuantPoint(val qx: Long, val qy: Long) : Comparable<QuantPoint> {
            constructor(p: DxfPoint) : this(
                (p.x / MATCH_TOLERANCE_MM).roundToLong(),
                (p.y / MATCH_TOLERANCE_MM).roundToLong()
            )
            override fun compareTo(other: QuantPoint): Int {
                val byX = qx.compareTo(other.qx)
                return if (byX != 0) byX else qy.compareTo(other.qy)
            }
            override fun equals(other: Any?): Boolean =
                other is QuantPoint && qx == other.qx && qy == other.qy
            override fun hashCode(): Int = 31 * qx.hashCode() + qy.hashCode()
        }

        private fun dominantLayer(segs: List<Seg>): String {
            if (segs.isEmpty()) return "0"
            return segs.groupingBy { it.layer }.eachCount()
                .maxByOrNull { it.value }?.key ?: "0"
        }

        private fun dominantBlock(segs: List<Seg>): String? {
            if (segs.isEmpty()) return null
            val counts = segs.mapNotNull { it.block }.groupingBy { it }.eachCount()
            if (counts.isEmpty()) return null
            val (name, count) = counts.maxByOrNull { it.value }!!
            // Only trust the block name when (nearly) every segment came from it.
            return if (count * 2 >= segs.size) name else null
        }

        // --------------------------------------------------------------
        // Geometry helpers
        // --------------------------------------------------------------

        private fun polygonArea(points: List<DxfPoint>): Double {
            var sum = 0.0
            for (i in points.indices) {
                val p = points[i]
                val q = points[(i + 1) % points.size]
                sum += p.x * q.y - q.x * p.y
            }
            return abs(sum) / 2.0
        }

        /** True when [inner]'s bbox sits inside [outer]'s bbox (0.01 slack). */
        private fun bboxContains(outer: List<DxfPoint>, inner: List<DxfPoint>): Boolean {
            var oMinX = Double.MAX_VALUE
            var oMinY = Double.MAX_VALUE
            var oMaxX = -Double.MAX_VALUE
            var oMaxY = -Double.MAX_VALUE
            for (p in outer) {
                oMinX = min(oMinX, p.x)
                oMinY = min(oMinY, p.y)
                oMaxX = max(oMaxX, p.x)
                oMaxY = max(oMaxY, p.y)
            }
            for (p in inner) {
                if (p.x < oMinX - MATCH_TOLERANCE_MM || p.x > oMaxX + MATCH_TOLERANCE_MM ||
                    p.y < oMinY - MATCH_TOLERANCE_MM || p.y > oMaxY + MATCH_TOLERANCE_MM
                ) return false
            }
            return true
        }

        /** Even-odd ray casting; a point on an edge counts as inside. */
        private fun pointInPolygon(point: DxfPoint, polygon: List<DxfPoint>): Boolean {
            var inside = false
            var j = polygon.size - 1
            for (i in polygon.indices) {
                val a = polygon[i]
                val b = polygon[j]
                if (pointOnSegment(point, a, b)) return true
                if ((a.y > point.y) != (b.y > point.y)) {
                    val xAtY = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
                    if (point.x < xAtY) inside = !inside
                }
                j = i
            }
            return inside
        }

        private fun pointOnSegment(p: DxfPoint, a: DxfPoint, b: DxfPoint): Boolean {
            val cross = (p.y - a.y) * (b.x - a.x) - (p.x - a.x) * (b.y - a.y)
            if (abs(cross) > 1e-9) return false
            val dot = (p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)
            if (dot < 0) return false
            val len2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
            return dot <= len2
        }

        /** Wrap an angle to (-PI, PI]. */
        private fun wrapAngle(angle: Double): Double {
            var a = angle % (2.0 * PI)
            if (a <= -PI) a += 2.0 * PI
            if (a > PI) a -= 2.0 * PI
            return a
        }

        /** Short human-readable number for warnings (2 decimals, no -0). */
        private fun fmt(value: Double): String {
            val v = if (abs(value) < 0.005) 0.0 else value
            val rounded = (v * 100.0).roundToLong() / 100.0
            return rounded.toString()
        }
    }
}
