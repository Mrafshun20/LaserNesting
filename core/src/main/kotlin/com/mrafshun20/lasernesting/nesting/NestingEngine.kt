package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartOutline
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** One part type to nest (before quantity expansion). */
data class PartInput(
    val id: Long,
    val name: String,
    val shape: PartShape,
    val widthMm: Double,
    val heightMm: Double,
    val quantity: Int,
    val material: Material,
    val thicknessMm: Double,
    /** Rotation angles this part may use, subset of {0, 90, 180, 270}. */
    val allowedRotations: Set<Int> = setOf(0, 90, 180, 270),
    /** Higher priority nests first. */
    val priority: Int = 0,
    /** True DXF outline (normalized, min corner at origin); null for rect/circle. */
    val outline: PartOutline? = null
)

/** One placed copy of a part on a sheet. Coordinates are in millimetres. */
data class PlacedPart(
    val partId: Long,
    val name: String,
    val shape: PartShape,
    /** Bounding-box min corner of the placed (rotated) part. */
    val xMm: Double,
    val yMm: Double,
    /** Bounding-box size of the placed (rotated) part. */
    val widthMm: Double,
    val heightMm: Double,
    /** Rotation applied, degrees clockwise (0, 90, 180, 270 or a finer step). */
    val rotationDegrees: Int,
    val colorIndex: Int,
    /** True DXF outline as imported (unrotated, normalized). */
    val outline: PartOutline? = null,
    /** The outline already rotated to [rotationDegrees] and normalized. */
    val placedOutline: PartOutline? = null
) {
    /** True when the part was placed rotated 90 or 270 degrees. */
    val rotated: Boolean get() = rotationDegrees % 180 == 90
}

/** Largest leftover rectangular region on a sheet, a reusable remnant. */
data class Remnant(
    val xMm: Double,
    val yMm: Double,
    val widthMm: Double,
    val heightMm: Double
)

/** One sheet of the nesting result. */
data class NestSheet(
    val index: Int,
    val placed: List<PlacedPart>,
    val utilizationPercent: Double,
    /** Largest leftover rectangle on this sheet, if any is usable. */
    val remnant: Remnant? = null
)

/** Why a part could not be placed. */
enum class UnplacedReason {
    INVALID,
    NO_USABLE_AREA,
    NO_FREE_SPACE,
    DOES_NOT_FIT
}

/** A part type that could not be placed, grouped with a reason. */
data class UnplacedPart(
    val name: String,
    val shape: PartShape,
    val widthMm: Double,
    val heightMm: Double,
    val quantity: Int,
    val reason: UnplacedReason
)

/** Full result of one nesting run. */
data class NestResult(
    val sheets: List<NestSheet>,
    val unplaced: List<UnplacedPart>,
    val sheetsUsed: Int,
    val overallUtilizationPercent: Double,
    val totalPartsPlaced: Int,
    val totalPartsRequested: Int,
    val totalCutLengthMm: Double,
    val estimatedCutMinutes: Double,
    /** Number of pierce points (one per contour). */
    val totalPierces: Int = 0,
    /** Which engine produced this layout, e.g. "True shape". */
    val engineLabel: String = "",
    val elapsedMs: Long = 0,
    /** Weight of the sheets used / of the parts placed, in kg. */
    val sheetsWeightKg: Double = 0.0,
    val partsWeightKg: Double = 0.0
)

/** Which packing strategy to use. */
enum class NestMode {
    /** Try everything and keep the best layout. */
    AUTO,

    /** Bounding-box packing: instant, best for rectangular parts. */
    FAST_BOX,

    /** True-shape packing on a sheet bitmap: parts nest into each other's holes. */
    TRUE_SHAPE
}

/** All inputs of a nesting run except the parts. */
data class NestOptions(
    val sheetWidthMm: Double,
    val sheetLengthMm: Double,
    val sheetCount: Int,
    val kerfMm: Double,
    val gapMm: Double,
    val marginMm: Double,
    val allowRotationGlobal: Boolean,
    val cuttingSpeedMmPerMin: Double,
    val mode: NestMode = NestMode.AUTO,
    /** Raster cell size for true-shape nesting; 0 = automatic. */
    val precisionMm: Double = 0.0,
    /** Extra rotation step for DXF parts in true-shape mode (e.g. 15); 0 = off. */
    val rotationStepDeg: Int = 0,
    val pierceTimeSec: Double = 0.5,
    val sheetMaterial: Material = Material.MILD_STEEL,
    val sheetThicknessMm: Double = 3.0,
    /** Wall-clock budget for true-shape runs. */
    val timeBudgetMs: Long = 20_000,
    val isCancelled: () -> Boolean = { false },
    /** Called with 0..1 as candidate layouts finish. */
    val onProgress: ((Float) -> Unit)? = null
)

/**
 * Nesting engine, pure Kotlin with no external dependency.
 *
 * Two packers are available and AUTO keeps whichever lays out the job best
 * (most parts placed, then fewest sheets, then the biggest reusable remnant):
 *
 *  - Bounding box (MaxRects, several heuristics and orderings): instant and
 *    ideal for rectangular parts.
 *  - True shape ([RasterNester]): parts are rasterised and packed on a sheet
 *    bitmap, so concave parts interlock and small parts are nested inside
 *    the holes and cutouts of big ones. Optional fine rotation steps.
 *
 * Every part is kept (kerf + gap) away from its neighbours and the usable
 * area is the sheet minus the edge margin on all four sides.
 */
object NestingEngine {

    /** All rotation angles, in the order they are preferred. */
    val ALL_ROTATIONS = setOf(0, 90, 180, 270)

    /** Classic entry point (kept for existing callers): AUTO mode. */
    fun nest(
        parts: List<PartInput>,
        sheetWidthMm: Double,
        sheetLengthMm: Double,
        sheetCount: Int,
        kerfMm: Double,
        gapMm: Double,
        marginMm: Double,
        allowRotationGlobal: Boolean,
        cuttingSpeedMmPerMin: Double
    ): NestResult = nest(
        parts,
        NestOptions(
            sheetWidthMm = sheetWidthMm,
            sheetLengthMm = sheetLengthMm,
            sheetCount = sheetCount,
            kerfMm = kerfMm,
            gapMm = gapMm,
            marginMm = marginMm,
            allowRotationGlobal = allowRotationGlobal,
            cuttingSpeedMmPerMin = cuttingSpeedMmPerMin
        )
    )

    // ------------------------------------------------------------------
    // Shared context and helpers
    // ------------------------------------------------------------------

    private class Ctx(
        val parts: List<PartInput>,
        val opt: NestOptions,
        val spacing: Double,
        val margin: Double,
        val usableW: Double,
        val usableH: Double,
        val count: Int
    )

    /** One expanded copy of a part type. [type] indexes [Ctx.parts]. */
    private class Item(val part: PartInput, val type: Int)

    private enum class ItemOrder { AREA, LONG_SIDE, WIDTH }

    /** Raw outcome of one packing run. */
    private class Outcome(
        val placed: List<MutableList<PlacedPart>>,
        val remnants: List<Remnant?>,
        val missing: IntArray,
        val label: String
    )

    /** True material area of a part type (one copy). */
    private fun typeArea(p: PartInput): Double = when (p.shape) {
        PartShape.CIRCLE -> PI * (p.widthMm / 2.0) * (p.widthMm / 2.0)
        PartShape.DXF -> p.outline?.let { OutlineGeometry.outlineArea(it) } ?: (p.widthMm * p.heightMm)
        else -> p.widthMm * p.heightMm
    }

    private fun orderItems(items: List<Item>, order: ItemOrder): List<Item> {
        val cmp: Comparator<Item> = when (order) {
            ItemOrder.AREA -> compareByDescending<Item> { it.part.priority }
                .thenByDescending { typeArea(it.part) }
                .thenBy { it.type }
            ItemOrder.LONG_SIDE -> compareByDescending<Item> { it.part.priority }
                .thenByDescending { max(it.part.widthMm, it.part.heightMm) }
                .thenByDescending { typeArea(it.part) }
                .thenBy { it.type }
            ItemOrder.WIDTH -> compareByDescending<Item> { it.part.priority }
                .thenByDescending { min(it.part.widthMm, it.part.heightMm) }
                .thenByDescending { typeArea(it.part) }
                .thenBy { it.type }
        }
        return items.sortedWith(cmp)
    }

    /**
     * Angles a part may use, given the global rotation switch. When the
     * global switch is off only 0 is allowed. Returns the angles in
     * preference order (0, 90, 180, 270).
     */
    fun allowedAngles(part: PartInput, allowRotationGlobal: Boolean): List<Int> {
        if (!allowRotationGlobal) return listOf(0)
        val allowed = part.allowedRotations.intersect(ALL_ROTATIONS)
        if (allowed.isEmpty()) return listOf(0)
        return listOf(0, 90, 180, 270).filter { it in allowed }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    fun nest(parts: List<PartInput>, options: NestOptions): NestResult {
        val started = System.nanoTime()
        val count = options.sheetCount.coerceAtLeast(1)
        val spacing = (options.kerfMm + options.gapMm).coerceAtLeast(0.0)
        val margin = options.marginMm.coerceAtLeast(0.0)
        val usableW = options.sheetWidthMm - 2 * margin
        val usableH = options.sheetLengthMm - 2 * margin
        val totalRequested = parts.sumOf { it.quantity.coerceAtLeast(0) }

        val invalid = parts.filter { it.quantity <= 0 || it.widthMm <= 0 || it.heightMm <= 0 }
        val valid = parts.filter { it.quantity > 0 && it.widthMm > 0 && it.heightMm > 0 }

        val unplaced = mutableListOf<UnplacedPart>()
        for (part in invalid) {
            unplaced.add(unplacedOf(part, part.quantity.coerceAtLeast(0), UnplacedReason.INVALID))
        }

        if (usableW <= 0 || usableH <= 0) {
            for (part in valid) {
                unplaced.add(unplacedOf(part, part.quantity, UnplacedReason.NO_USABLE_AREA))
            }
            return NestResult(
                sheets = emptyList(),
                unplaced = unplaced,
                sheetsUsed = 0,
                overallUtilizationPercent = 0.0,
                totalPartsPlaced = 0,
                totalPartsRequested = totalRequested,
                totalCutLengthMm = 0.0,
                estimatedCutMinutes = 0.0
            )
        }

        val ctx = Ctx(valid, options, spacing, margin, usableW, usableH, count)
        val items = ArrayList<Item>()
        valid.forEachIndexed { index, part -> repeat(part.quantity) { items.add(Item(part, index)) } }

        // Candidate layouts.
        val candidates = ArrayList<NestResult>()
        val allRect = valid.all { it.shape == PartShape.RECTANGLE }
        val boxRuns = ArrayList<() -> Outcome>()
        val rasterRuns = ArrayList<() -> Outcome>()

        val boxVariants = when (options.mode) {
            NestMode.FAST_BOX, NestMode.AUTO -> {
                val heur = if (options.mode == NestMode.FAST_BOX) {
                    RectHeuristic.entries.toList()
                } else {
                    RectHeuristic.entries.toList()
                }
                ItemOrder.entries.flatMap { o -> heur.map { h -> o to h } }
            }
            NestMode.TRUE_SHAPE -> emptyList()
        }
        for ((o, h) in boxVariants) {
            boxRuns.add { runBox(ctx, orderItems(items, o), h, "Box ${o.name}/${h.name}") }
        }
        val useRaster = options.mode == NestMode.TRUE_SHAPE ||
            (options.mode == NestMode.AUTO && !allRect)
        if (useRaster) {
            for (o in listOf(ItemOrder.AREA, ItemOrder.LONG_SIDE)) {
                rasterRuns.add { runRaster(ctx, orderItems(items, o), "True shape ${o.name}") }
            }
        }

        val totalRuns = (boxRuns.size + rasterRuns.size).coerceAtLeast(1)
        var done = 0
        val progressLock = Any()
        fun tick() {
            synchronized(progressLock) {
                done++
                options.onProgress?.invoke(done.toFloat() / totalRuns)
            }
        }

        // Raster runs in parallel threads (each is single-threaded).
        val rasterResults = arrayOfNulls<NestResult>(rasterRuns.size)
        val threads = rasterRuns.mapIndexed { i, run ->
            Thread {
                try {
                    rasterResults[i] = finish(ctx, run(), totalRequested, unplaced, started)
                } catch (_: Throwable) {
                    // A failed candidate is simply not a candidate.
                } finally {
                    tick()
                }
            }.also { it.isDaemon = true; it.start() }
        }
        for (run in boxRuns) {
            if (options.isCancelled()) break
            candidates.add(finish(ctx, run(), totalRequested, unplaced, started))
            tick()
        }
        threads.forEach { it.join() }
        rasterResults.filterNotNull().forEach { candidates.add(it) }

        val best = candidates.reduceOrNull { a, b -> if (better(b, a)) b else a }
            ?: finish(ctx, runBox(ctx, orderItems(items, ItemOrder.AREA), RectHeuristic.BEST_SHORT_SIDE, "Box"),
                totalRequested, unplaced, started)
        return best.copy(elapsedMs = (System.nanoTime() - started) / 1_000_000)
    }

    private fun unplacedOf(part: PartInput, qty: Int, reason: UnplacedReason) = UnplacedPart(
        name = part.name,
        shape = part.shape,
        widthMm = part.widthMm,
        heightMm = part.heightMm,
        quantity = qty,
        reason = reason
    )

    /** True when [a] is a better layout than [b]. */
    private fun better(a: NestResult, b: NestResult): Boolean {
        if (a.totalPartsPlaced != b.totalPartsPlaced) return a.totalPartsPlaced > b.totalPartsPlaced
        if (a.sheetsUsed != b.sheetsUsed) return a.sheetsUsed < b.sheetsUsed
        val ra = a.sheets.lastOrNull()?.remnant?.let { it.widthMm * it.heightMm } ?: 0.0
        val rb = b.sheets.lastOrNull()?.remnant?.let { it.widthMm * it.heightMm } ?: 0.0
        if (kotlin.math.abs(ra - rb) > 1.0) return ra > rb
        return a.totalCutLengthMm < b.totalCutLengthMm
    }

    // ------------------------------------------------------------------
    // Bounding-box packer
    // ------------------------------------------------------------------

    private data class TryPlacement(val angle: Int, val rotated: Boolean, val rect: Rect)

    private fun runBox(ctx: Ctx, items: List<Item>, heuristic: RectHeuristic, label: String): Outcome {
        val bins = List(ctx.count) { MaxRectsBin(ctx.usableW, ctx.usableH) }
        val placed = List(ctx.count) { mutableListOf<PlacedPart>() }
        val missing = IntArray(ctx.parts.size)

        for (item in items) {
            val part = item.part
            val packW = part.widthMm + ctx.spacing
            val packH = part.heightMm + ctx.spacing
            val allowed = allowedAngles(part, ctx.opt.allowRotationGlobal)
            var done = false
            for (sheetIndex in bins.indices) {
                val tp = tryPlace(bins[sheetIndex], packW, packH, allowed, heuristic) ?: continue
                val w = if (tp.rotated) part.heightMm else part.widthMm
                val h = if (tp.rotated) part.widthMm else part.heightMm
                val rotatedOutline = part.outline?.let { OutlineGeometry.rotateOutline(it, tp.angle) }
                placed[sheetIndex].add(
                    PlacedPart(
                        partId = part.id,
                        name = part.name,
                        shape = part.shape,
                        xMm = ctx.margin + tp.rect.x,
                        yMm = ctx.margin + tp.rect.y,
                        widthMm = rotatedOutline?.widthMm ?: w,
                        heightMm = rotatedOutline?.heightMm ?: h,
                        rotationDegrees = tp.angle,
                        colorIndex = item.type,
                        outline = part.outline,
                        placedOutline = rotatedOutline
                    )
                )
                done = true
                break
            }
            if (!done) missing[item.type]++
        }
        val remnants = bins.mapIndexed { i, bin ->
            if (placed[i].isEmpty()) null else largestRemnant(bin, ctx.margin)
        }
        return Outcome(placed, remnants, missing, label)
    }

    private fun tryPlace(
        bin: MaxRectsBin,
        packW: Double,
        packH: Double,
        allowedAngles: List<Int>,
        heuristic: RectHeuristic
    ): TryPlacement? {
        val canStraight = allowedAngles.any { it == 0 || it == 180 }
        val canRotated = allowedAngles.any { it == 90 || it == 270 }
        var best: MaxRectsBin.Placement? = null
        var bestAngle = 0
        var bestRotated = false

        fun consider(angle: Int, w: Double, h: Double, rotated: Boolean) {
            val placement = bin.findBest(w, h, false, heuristic) ?: return
            val prev = best
            if (prev == null || placement.score1 < prev.score1 ||
                (placement.score1 == prev.score1 && placement.score2 < prev.score2)
            ) {
                best = placement
                bestAngle = angle
                bestRotated = rotated
            }
        }
        if (canStraight) consider(if (0 in allowedAngles) 0 else 180, packW, packH, false)
        if (canRotated) consider(if (90 in allowedAngles) 90 else 270, packH, packW, true)

        val placement = best ?: return null
        bin.place(placement.rect)
        return TryPlacement(bestAngle, bestRotated, placement.rect)
    }

    private fun largestRemnant(bin: MaxRectsBin, margin: Double): Remnant? {
        val best = bin.freeRectanglesSnapshot()
            .filter { it.width >= 50.0 && it.height >= 50.0 }
            .maxByOrNull { it.width * it.height }
            ?: return null
        return Remnant(margin + best.x, margin + best.y, best.width, best.height)
    }

    // ------------------------------------------------------------------
    // True-shape packer
    // ------------------------------------------------------------------

    private class Orient(
        val angle: Int,
        val outline: PartOutline,
        val mask: RasterMask
    )

    private fun runRaster(ctx: Ctx, items: List<Item>, label: String): Outcome {
        val opt = ctx.opt
        val longSide = max(ctx.usableW, ctx.usableH)
        val cell = if (opt.precisionMm > 0.0) opt.precisionMm
        else (Math.round(((longSide / 1300.0).coerceIn(1.0, 2.0)) * 2.0) / 2.0)
        val radius = ctx.spacing / 2.0 + cell * 0.7072
        val pad = ceil(radius / cell).toInt() + 1
        // Coarse scan stride in cells (~4 mm); the hit is refined cell by cell.
        val stride = (4.0 / cell).toInt().coerceIn(1, 4)
        val gw = (ctx.usableW / cell).toInt()
        val gh = (ctx.usableH / cell).toInt()
        val deadline = System.nanoTime() + opt.timeBudgetMs * 1_000_000L

        val sheets = ArrayList<RasterSheet>()
        val placed = ArrayList<MutableList<PlacedPart>>()
        val failed = ArrayList<BooleanArray>()
        val missing = IntArray(ctx.parts.size)
        val orientCache = arrayOfNulls<List<Orient>>(ctx.parts.size)

        fun orientsOf(type: Int): List<Orient> {
            orientCache[type]?.let { return it }
            val part = ctx.parts[type]
            val base = baseOutline(part)
            val angles = rasterAngles(part, opt)
            val out = ArrayList<Orient>()
            for (angle in angles) {
                val rot = OutlineGeometry.rotateOutline(base, angle)
                val mask = Rasterizer.buildMask(
                    Rasterizer.loopsOf(rot), rot.widthMm, rot.heightMm, cell, pad, radius
                )
                if (out.any { it.mask.sameShapeAs(mask) }) continue
                out.add(Orient(angle, rot, mask))
            }
            orientCache[type] = out
            return out
        }

        var cancelled = false
        for (item in items) {
            if (cancelled || System.nanoTime() > deadline || opt.isCancelled()) {
                cancelled = true
                missing[item.type]++
                continue
            }
            val type = item.type
            val orients = orientsOf(type)
            // Does it fit an empty sheet at all?
            val fitsEmpty = orients.any { it.mask.matW <= gw && it.mask.matH <= gh }
            if (!fitsEmpty) {
                missing[type]++
                continue
            }
            var done = false
            var sheetIndex = 0
            while (sheetIndex < ctx.count && !done) {
                if (sheetIndex >= sheets.size) {
                    sheets.add(RasterSheet(gw, gh, pad))
                    placed.add(mutableListOf())
                    failed.add(BooleanArray(ctx.parts.size))
                }
                if (!failed[sheetIndex][type]) {
                    val sheet = sheets[sheetIndex]
                    var bestRight = Int.MAX_VALUE
                    var bestY = Int.MAX_VALUE
                    var bestX = -1
                    var bestOrient: Orient? = null
                    for (o in orients) {
                        val m = o.mask
                        val maxX = gw - m.matW
                        val maxY = gh - m.matH
                        if (maxX < 0 || maxY < 0) continue
                        // Coarse scan with a stride, then refine around the hit.
                        var cx = -1
                        var cy = -1
                        var x0 = 0
                        while (x0 <= maxX && cx < 0) {
                            if (x0 + m.matW > bestRight) break
                            var y0 = 0
                            while (y0 <= maxY) {
                                if (sheet.fits(m, x0, y0)) {
                                    cx = x0
                                    cy = y0
                                    break
                                }
                                y0 += stride
                            }
                            x0 += stride
                        }
                        if (cx < 0) continue
                        // Refine: tightest position near the coarse hit.
                        var fx = cx
                        var fy = cy
                        val xLo = (cx - 2 * stride).coerceAtLeast(0)
                        val yLo = (cy - 2 * stride).coerceAtLeast(0)
                        val yHi = (cy + stride).coerceAtMost(maxY)
                        var refined = false
                        var rx = xLo
                        while (rx <= cx && !refined) {
                            var ry = yLo
                            while (ry <= yHi) {
                                if (sheet.fits(m, rx, ry)) {
                                    fx = rx
                                    fy = ry
                                    refined = true
                                    break
                                }
                                ry++
                            }
                            rx++
                        }
                        val right = fx + m.matW
                        if (right < bestRight || (right == bestRight && fy < bestY)) {
                            bestRight = right
                            bestY = fy
                            bestX = fx
                            bestOrient = o
                        }
                    }
                    val chosen = bestOrient
                    if (chosen != null) {
                        sheet.place(chosen.mask, bestX, bestY)
                        val part = ctx.parts[type]
                        val ro = chosen.outline
                        val isDxf = part.shape == PartShape.DXF && part.outline != null
                        placed[sheetIndex].add(
                            PlacedPart(
                                partId = part.id,
                                name = part.name,
                                shape = part.shape,
                                xMm = ctx.margin + bestX * cell,
                                yMm = ctx.margin + bestY * cell,
                                widthMm = ro.widthMm,
                                heightMm = ro.heightMm,
                                rotationDegrees = chosen.angle,
                                colorIndex = type,
                                outline = part.outline,
                                placedOutline = if (isDxf) ro else null
                            )
                        )
                        done = true
                    } else {
                        failed[sheetIndex][type] = true
                    }
                }
                sheetIndex++
            }
            if (!done) missing[type]++
        }

        // Pad the lists up to the sheet count.
        while (placed.size < ctx.count) placed.add(mutableListOf())
        val remnants = List(ctx.count) { i ->
            if (i >= sheets.size || placed[i].isEmpty()) null
            else sheets[i].largestFreeRect()?.let { r ->
                if (r[2] * cell >= 50.0 && r[3] * cell >= 50.0) {
                    Remnant(
                        ctx.margin + r[0] * cell,
                        ctx.margin + r[1] * cell,
                        r[2] * cell,
                        r[3] * cell
                    )
                } else null
            }
        }
        return Outcome(placed, remnants, missing, label)
    }

    /** The unrotated outline used for rasterising any part. */
    private fun baseOutline(part: PartInput): PartOutline {
        part.outline?.takeIf { part.shape == PartShape.DXF && it.points.size >= 3 }?.let { return it }
        return when (part.shape) {
            PartShape.CIRCLE -> {
                val r = part.widthMm / 2.0
                val segs = (r * 0.9).toInt().coerceIn(32, 120)
                PartOutline(
                    OutlineGeometry.circlePolygon(r, r, r, segs), emptyList(),
                    part.widthMm, part.widthMm
                )
            }
            else -> PartOutline(
                listOf(
                    Point(0.0, 0.0), Point(part.widthMm, 0.0),
                    Point(part.widthMm, part.heightMm), Point(0.0, part.heightMm)
                ),
                emptyList(), part.widthMm, part.heightMm
            )
        }
    }

    /** Angles the raster packer tries for a part. */
    private fun rasterAngles(part: PartInput, opt: NestOptions): List<Int> {
        if (part.shape == PartShape.CIRCLE) return listOf(0)
        val base = allowedAngles(part, opt.allowRotationGlobal)
        val canRotate = opt.allowRotationGlobal && part.allowedRotations.size > 1
        val step = opt.rotationStepDeg
        if (part.shape == PartShape.DXF && canRotate && step in 5..90) {
            return (0 until 360 step step).toList()
        }
        return base
    }

    // ------------------------------------------------------------------
    // Result assembly
    // ------------------------------------------------------------------

    private fun finish(
        ctx: Ctx,
        outcome: Outcome,
        totalRequested: Int,
        baseUnplaced: List<UnplacedPart>,
        startedNs: Long
    ): NestResult {
        val opt = ctx.opt
        val unplaced = baseUnplaced.toMutableList()
        for ((type, part) in ctx.parts.withIndex()) {
            val missing = outcome.missing[type]
            if (missing > 0) {
                val packW = part.widthMm + ctx.spacing
                val packH = part.heightMm + ctx.spacing
                val fitsEmpty = allowedAngles(part, opt.allowRotationGlobal).any { angle ->
                    val (w, h) = OutlineGeometry.rotatedSize(packW, packH, angle)
                    w <= ctx.usableW && h <= ctx.usableH
                }
                unplaced.add(
                    unplacedOf(
                        part, missing,
                        if (fitsEmpty) UnplacedReason.NO_FREE_SPACE else UnplacedReason.DOES_NOT_FIT
                    )
                )
            }
        }

        val sheetArea = opt.sheetWidthMm * opt.sheetLengthMm
        var totalPlacedArea = 0.0
        var totalCutLength = 0.0
        var totalPlaced = 0
        var totalPierces = 0
        val sheets = mutableListOf<NestSheet>()
        for (sheetIndex in outcome.placed.indices) {
            val placed = outcome.placed[sheetIndex]
            if (placed.isEmpty()) continue
            var area = 0.0
            for (p in placed) {
                area += partArea(p)
                totalCutLength += partPerimeter(p)
                totalPierces += partPierces(p)
            }
            totalPlacedArea += area
            totalPlaced += placed.size
            sheets.add(
                NestSheet(
                    index = sheetIndex,
                    placed = placed,
                    utilizationPercent = if (sheetArea > 0) area / sheetArea * 100.0 else 0.0,
                    remnant = outcome.remnants.getOrNull(sheetIndex)
                )
            )
        }

        val sheetsUsed = sheets.size
        val utilization = if (sheetsUsed > 0 && sheetArea > 0) {
            totalPlacedArea / (sheetsUsed * sheetArea) * 100.0
        } else 0.0
        val minutes = if (opt.cuttingSpeedMmPerMin > 0) {
            totalCutLength / opt.cuttingSpeedMmPerMin + totalPierces * opt.pierceTimeSec / 60.0
        } else 0.0

        val density = opt.sheetMaterial.densityGcm3
        val thickness = opt.sheetThicknessMm.coerceAtLeast(0.0)
        // mm^3 * g/cm^3 = mg*... : 1 mm^3 = 0.001 cm^3 -> g = mm^3 * density / 1000; kg = /1e6.
        val sheetsKg = sheetsUsed * sheetArea * thickness * density / 1_000_000.0
        val partsKg = totalPlacedArea * thickness * density / 1_000_000.0

        return NestResult(
            sheets = sheets,
            unplaced = unplaced,
            sheetsUsed = sheetsUsed,
            overallUtilizationPercent = utilization,
            totalPartsPlaced = totalPlaced,
            totalPartsRequested = totalRequested,
            totalCutLengthMm = totalCutLength,
            estimatedCutMinutes = minutes,
            totalPierces = totalPierces,
            engineLabel = outcome.label,
            elapsedMs = (System.nanoTime() - startedNs) / 1_000_000,
            sheetsWeightKg = sheetsKg,
            partsWeightKg = partsKg
        )
    }

    // ------------------------------------------------------------------
    // Part metrics (public: used by UI and export)
    // ------------------------------------------------------------------

    /** True area of a placed part (circle area, outline minus cutouts, or rect). */
    fun partArea(part: PlacedPart): Double = when (part.shape) {
        PartShape.CIRCLE -> {
            val radius = part.widthMm / 2.0
            PI * radius * radius
        }
        PartShape.DXF -> part.outline?.let { OutlineGeometry.outlineArea(it) }
            ?: (part.widthMm * part.heightMm)
        else -> part.widthMm * part.heightMm
    }

    /** Cut length of one placed part: outer contour plus every inner contour. */
    fun partPerimeter(part: PlacedPart): Double = when (part.shape) {
        PartShape.CIRCLE -> PI * part.widthMm
        PartShape.DXF -> part.outline?.let { OutlineGeometry.outlinePerimeter(it) }
            ?: (2.0 * (part.widthMm + part.heightMm))
        else -> 2.0 * (part.widthMm + part.heightMm)
    }

    /** Pierce points of a placed part: one per contour. */
    fun partPierces(part: PlacedPart): Int =
        if (part.shape == PartShape.DXF) 1 + (part.outline?.innerContourCount ?: 0) else 1

    /** Outline of a placed part, already rotated: points, holes, cutouts. */
    fun placedOutline(part: PlacedPart): PartOutline? {
        part.placedOutline?.let { return it }
        val base = part.outline ?: return null
        return OutlineGeometry.rotateOutline(base, part.rotationDegrees)
    }

    /** Outline points of a placed part, rotated to its placed angle. */
    fun placedOutlinePoints(part: PlacedPart): List<Point> =
        placedOutline(part)?.points ?: emptyList()

    /** Circular holes of a placed part, rotated to its placed angle. */
    fun placedHoles(part: PlacedPart): List<CircleHole> =
        placedOutline(part)?.holes ?: emptyList()

    /** Non-circular cutouts of a placed part, rotated to its placed angle. */
    fun placedCutouts(part: PlacedPart): List<List<Point>> =
        placedOutline(part)?.cutouts ?: emptyList()
}
