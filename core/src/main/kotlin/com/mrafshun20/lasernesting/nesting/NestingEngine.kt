package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.MaterialDb
import com.mrafshun20.lasernesting.data.PartOutline
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.Point
import kotlin.math.PI

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
    val xMm: Double,
    val yMm: Double,
    val widthMm: Double,
    val heightMm: Double,
    /** Rotation applied, one of 0, 90, 180, 270. */
    val rotationDegrees: Int,
    val colorIndex: Int,
    /** True DXF outline (normalized); rotate by [rotationDegrees] when drawing. */
    val outline: PartOutline? = null
) {
    /** True when the part was placed rotated 90 or 270 degrees. */
    val rotated: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270
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
    val remnant: Remnant? = null,
    val cutLengthMm: Double = 0.0,
    val pierces: Int = 0,
    val cutMinutes: Double = 0.0
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
    /** Number of pierces (one per contour). */
    val totalPierces: Int = 0,
    /** Torch travel between contours (rapid moves), millimetres. */
    val rapidLengthMm: Double = 0.0,
    /** Weight of all placed parts / of the sheets used / of the scrap, kg. */
    val partWeightKg: Double = 0.0,
    val sheetWeightKg: Double = 0.0,
    val scrapWeightKg: Double = 0.0,
    /** Material cost of the sheets used (0 when no price was given). */
    val materialCost: Double = 0.0,
    /** Laser cutting fee (cut length x price per metre), 0 when no rate was given. */
    val cuttingCost: Double = 0.0,
    /** Pierce cost (pierces x cost per pierce), 0 when no rate was given. */
    val pierceCostTotal: Double = 0.0,
    /** Total job price: cutting fee + pierces + material. */
    val totalCost: Double = 0.0,
    /** Total laser-head travel: cut length + rapid moves, millimetres. */
    val totalTravelMm: Double = 0.0,
    /** Cutting speed used for the estimate, mm/min. */
    val cuttingSpeedUsedMmPerMin: Double = 0.0,
    /** Which nesting strategy won (for display / debugging). */
    val strategy: String = ""
)

/** How hard the nester works. */
enum class NestQuality(val cellFactor: Double, val seconds: Double) {
    FAST(1.8, 6.0),
    BALANCED(1.0, 15.0),
    HIGH(0.7, 40.0)
}

/** Optional settings for a nesting run. */
data class NestOptions(
    val quality: NestQuality = NestQuality.BALANCED,
    /** True-shape nesting (parts fit into each other's concave gaps and holes). */
    val trueShape: Boolean = true,
    val sheetMaterial: Material = Material.MILD_STEEL,
    val sheetThicknessMm: Double = 3.0,
    /** Price per kg of sheet; 0 disables the cost figure. */
    val pricePerKg: Double = 0.0,
    /** Price of one metre of laser cut (currency/m); 0 disables the figure. */
    val cutPricePerMeter: Double = 0.0,
    /** Cost per pierce (currency); 0 disables the figure. */
    val pierceCost: Double = 0.0,
    /** Pierce time in seconds; 0 = take it from the material database. */
    val pierceSeconds: Double = 0.0,
    /** Rapid travel speed in mm/min. */
    val rapidSpeedMmPerMin: Double = 100_000.0
)

/**
 * Nesting engine, pure Kotlin with no external dependency.
 *
 * Each part is kept at least (kerf + gap) away from its neighbours, and the
 * usable area is the sheet minus the edge margin on all four sides. Parts are
 * ordered highest-priority first and tried on every sheet in turn at the
 * allowed rotations (0 / 90 / 180 / 270).
 *
 * Two packers compete and the better layout wins:
 *  - MaxRects bounding-box packing (fast, exact for rectangles), and
 *  - true-shape raster packing ([RasterNester]) in several sort orders, which
 *    nests parts into the gaps, slots and holes of other parts.
 * "Better" means: fewer unplaced parts, then fewer sheets, then the bigger
 * reusable remnant.
 */
object NestingEngine {

    /** All rotation angles, in the order they are preferred. */
    val ALL_ROTATIONS = setOf(0, 90, 180, 270)

    fun nest(
        parts: List<PartInput>,
        sheetWidthMm: Double,
        sheetLengthMm: Double,
        sheetCount: Int,
        kerfMm: Double,
        gapMm: Double,
        marginMm: Double,
        allowRotationGlobal: Boolean,
        cuttingSpeedMmPerMin: Double,
        options: NestOptions = NestOptions()
    ): NestResult {
        val count = sheetCount.coerceAtLeast(1)
        val spacing = (kerfMm + gapMm).coerceAtLeast(0.0)
        val margin = marginMm.coerceAtLeast(0.0)
        val usableW = sheetWidthMm - 2 * margin
        val usableH = sheetLengthMm - 2 * margin
        val sheetArea = sheetWidthMm * sheetLengthMm

        val totalRequested = parts.sumOf { it.quantity.coerceAtLeast(0) }

        val invalid = parts.filter { it.quantity <= 0 || it.widthMm <= 0 || it.heightMm <= 0 }
        val valid = parts.filter { it.quantity > 0 && it.widthMm > 0 && it.heightMm > 0 }

        val unplaced = mutableListOf<UnplacedPart>()
        for (part in invalid) {
            unplaced.add(
                UnplacedPart(
                    part.name, part.shape, part.widthMm, part.heightMm,
                    part.quantity.coerceAtLeast(0), UnplacedReason.INVALID
                )
            )
        }

        if (usableW <= 0 || usableH <= 0) {
            for (part in valid) {
                unplaced.add(
                    UnplacedPart(
                        part.name, part.shape, part.widthMm, part.heightMm,
                        part.quantity, UnplacedReason.NO_USABLE_AREA
                    )
                )
            }
            return NestResult(
                sheets = emptyList(), unplaced = unplaced, sheetsUsed = 0,
                overallUtilizationPercent = 0.0, totalPartsPlaced = 0,
                totalPartsRequested = totalRequested, totalCutLengthMm = 0.0,
                estimatedCutMinutes = 0.0
            )
        }

        // Candidate layouts.
        class Candidate(val name: String, val layout: Layout, val rank: LongArray)
        val candidates = mutableListOf<Candidate>()

        fun items(order: Comparator<RasterNester.Item>): List<RasterNester.Item> {
            val list = mutableListOf<RasterNester.Item>()
            valid.forEachIndexed { index, part ->
                repeat(part.quantity) { list.add(RasterNester.Item(part, index)) }
            }
            list.sortWith(order)
            return list
        }

        fun byPriority(): Comparator<RasterNester.Item> =
            compareByDescending<RasterNester.Item> { it.part.priority }

        val areaDesc = byPriority().thenByDescending { it.part.widthMm * it.part.heightMm }
        val longSideDesc = byPriority()
            .thenByDescending { maxOf(it.part.widthMm, it.part.heightMm) }
            .thenByDescending { it.part.widthMm * it.part.heightMm }
        val heightDesc = byPriority()
            .thenByDescending { minOf(it.part.widthMm, it.part.heightMm) }
            .thenByDescending { it.part.widthMm * it.part.heightMm }

        val cell = RasterNester.cellSize(spacing, usableW, usableH, options.quality.cellFactor)
        val remnantCell = maxOf(5.0, cell)

        fun addCandidate(name: String, layout: Layout) {
            val missing = layout.unplacedCounts.values.sum()
            val used = layout.placed.count { it.isNotEmpty() }
            // Bigger remnants are better; rank values are minimised.
            var remnantArea = 0.0
            for (sheet in layout.placed) {
                if (sheet.isEmpty()) continue
                val r = RasterNester.largestFreeRect(sheet, usableW, usableH, margin, spacing, remnantCell)
                if (r != null) remnantArea += r.width * r.height
            }
            candidates.add(
                Candidate(name, layout, longArrayOf(missing.toLong(), used.toLong(), -(remnantArea / 100.0).toLong()))
            )
        }

        // 1) Bounding-box baseline: always available, always fast.
        addCandidate("maxrects", packMaxRects(items(areaDesc), usableW, usableH, margin, count, spacing, allowRotationGlobal))

        // 2) True-shape passes within a time budget.
        if (options.trueShape && valid.isNotEmpty()) {
            val deadline = System.nanoTime() + (options.quality.seconds * 1e9).toLong()
            val reach = spacing / 2.0 + cell * 0.7072
            val padCells = kotlin.math.ceil(reach / cell).toInt() + 1
            val masks = HashMap<Long, PartMask>()
            val passes = listOf(
                Triple("raster-area-top", areaDesc, RasterScore.TOP_EDGE),
                Triple("raster-long-top", longSideDesc, RasterScore.TOP_EDGE),
                Triple("raster-area-first", areaDesc, RasterScore.FIRST_FIT),
                Triple("raster-short-top", heightDesc, RasterScore.TOP_EDGE)
            )
            for ((name, order, score) in passes) {
                if (System.nanoTime() > deadline) break
                val layout = RasterNester.run(
                    items(order), usableW, usableH, margin, count, cell, padCells, reach,
                    allowRotationGlobal, score, masks, deadline
                ) ?: break
                addCandidate(name, layout)
            }

            // 3) Refinement: randomised restarts (jittered sort keys, random
            // placement rule) until the budget is spent or six in a row bring no
            // improvement. Priority groups stay strictly ordered. Seeded, so the
            // same job always gives the same layout.
            fun better(a: LongArray, b: LongArray): Boolean {
                for (i in a.indices) {
                    if (a[i] != b[i]) return a[i] < b[i]
                }
                return false
            }
            fun bestRank(): LongArray = candidates.minWithOrNull { a, b ->
                if (better(a.rank, b.rank)) -1 else if (better(b.rank, a.rank)) 1 else 0
            }!!.rank
            val refineUntil = System.nanoTime() +
                ((deadline - System.nanoTime()) * 0.7).toLong()
            val rnd = java.util.Random(12345L)
            var sinceImprovement = 0
            var round = 0
            val scores = RasterScore.entries
            // Only worth it when it can still save parts or a whole sheet.
            fun canImprove(): Boolean {
                val r = bestRank()
                return r[0] > 0L || r[1] > 1L
            }
            while (canImprove() && System.nanoTime() < refineUntil &&
                sinceImprovement < 6 && round < 60
            ) {
                round++
                val before = bestRank()
                val jitter = HashMap<RasterNester.Item, Double>()
                val base = items(areaDesc)
                val spread = 0.15 + 0.35 * rnd.nextDouble()
                for (it in base) {
                    val a = it.part.widthMm * it.part.heightMm
                    jitter[it] = a * (1.0 + spread * (rnd.nextDouble() * 2.0 - 1.0))
                }
                val order = base.sortedWith(
                    compareByDescending<RasterNester.Item> { it.part.priority }
                        .thenByDescending { jitter[it] ?: 0.0 }
                )
                val layout = RasterNester.run(
                    order, usableW, usableH, margin, count, cell, padCells, reach,
                    allowRotationGlobal, scores[rnd.nextInt(scores.size)], masks, deadline
                ) ?: break
                addCandidate("raster-refine-$round", layout)
                if (better(bestRank(), before)) sinceImprovement = 0 else sinceImprovement++
            }
        }

        val best = candidates.minWithOrNull { a, b ->
            var r = 0
            for (i in a.rank.indices) {
                r = a.rank[i].compareTo(b.rank[i])
                if (r != 0) break
            }
            r
        }!!
        val layout = best.layout

        // Group unplaced copies per part with a reason.
        for (part in valid) {
            val missing = layout.unplacedCounts[part.id] ?: 0
            if (missing > 0) {
                val packW = part.widthMm + spacing
                val packH = part.heightMm + spacing
                val allowed = allowedAngles(part, allowRotationGlobal)
                val fitsEmptySheet = allowed.any { angle ->
                    val (w, h) = OutlineGeometry.rotatedSize(packW, packH, angle)
                    w <= usableW && h <= usableH
                }
                unplaced.add(
                    UnplacedPart(
                        part.name, part.shape, part.widthMm, part.heightMm, missing,
                        if (fitsEmptySheet) UnplacedReason.NO_FREE_SPACE else UnplacedReason.DOES_NOT_FIT
                    )
                )
            }
        }

        // Cutting model.
        val speed = if (cuttingSpeedMmPerMin > 0) cuttingSpeedMmPerMin
        else MaterialDb.suggestedSpeedMmPerMin(options.sheetMaterial, options.sheetThicknessMm)
        val pierceSec = if (options.pierceSeconds > 0) options.pierceSeconds
        else MaterialDb.pierceSeconds(options.sheetMaterial, options.sheetThicknessMm)

        var totalPlacedArea = 0.0
        var totalCutLength = 0.0
        var totalPierces = 0
        var totalRapid = 0.0
        var totalMinutes = 0.0
        var totalPlaced = 0
        val sheets = mutableListOf<NestSheet>()
        for (sheetIndex in layout.placed.indices) {
            val placed = layout.placed[sheetIndex]
            if (placed.isEmpty()) continue
            var area = 0.0
            for (p in placed) area += partArea(p)
            val base = NestSheet(sheetIndex, placed, if (sheetArea > 0) area / sheetArea * 100.0 else 0.0)
            val plan = CutPlanner.plan(base, sheetLengthMm)
            val minutes = (if (speed > 0) plan.cutLengthMm / speed else 0.0) +
                plan.pierces * pierceSec / 60.0 +
                (if (options.rapidSpeedMmPerMin > 0) plan.rapidLengthMm / options.rapidSpeedMmPerMin else 0.0)
            val rem = RasterNester.largestFreeRect(placed, usableW, usableH, margin, spacing, remnantCell)
                ?.takeIf { it.width >= 50.0 && it.height >= 50.0 }
                ?.let { Remnant(margin + it.x, margin + it.y, it.width, it.height) }
            totalPlacedArea += area
            totalCutLength += plan.cutLengthMm
            totalPierces += plan.pierces
            totalRapid += plan.rapidLengthMm
            totalMinutes += minutes
            totalPlaced += placed.size
            sheets.add(
                base.copy(
                    remnant = rem, cutLengthMm = plan.cutLengthMm,
                    pierces = plan.pierces, cutMinutes = minutes
                )
            )
        }

        val sheetsUsed = sheets.size
        val overall = if (sheetsUsed > 0 && sheetArea > 0) {
            totalPlacedArea / (sheetsUsed * sheetArea) * 100.0
        } else 0.0

        val t = options.sheetThicknessMm
        val partWeight = MaterialDb.weightKg(options.sheetMaterial, totalPlacedArea, t)
        val sheetWeight = MaterialDb.weightKg(options.sheetMaterial, sheetsUsed * sheetArea, t)
        val materialCost = sheetWeight * options.pricePerKg.coerceAtLeast(0.0)
        val cuttingCost = (totalCutLength / 1000.0) * options.cutPricePerMeter.coerceAtLeast(0.0)
        val pierceCostTotal = totalPierces * options.pierceCost.coerceAtLeast(0.0)

        return NestResult(
            sheets = sheets,
            unplaced = unplaced,
            sheetsUsed = sheetsUsed,
            overallUtilizationPercent = overall,
            totalPartsPlaced = totalPlaced,
            totalPartsRequested = totalRequested,
            totalCutLengthMm = totalCutLength,
            estimatedCutMinutes = totalMinutes,
            totalPierces = totalPierces,
            rapidLengthMm = totalRapid,
            partWeightKg = partWeight,
            sheetWeightKg = sheetWeight,
            scrapWeightKg = (sheetWeight - partWeight).coerceAtLeast(0.0),
            materialCost = materialCost,
            cuttingCost = cuttingCost,
            pierceCostTotal = pierceCostTotal,
            totalCost = cuttingCost + pierceCostTotal + materialCost,
            totalTravelMm = totalCutLength + totalRapid,
            cuttingSpeedUsedMmPerMin = speed,
            strategy = best.name
        )
    }

    // ---------------------------------------------------------------
    // Bounding-box packer (MaxRects)
    // ---------------------------------------------------------------

    private fun packMaxRects(
        items: List<RasterNester.Item>,
        usableW: Double,
        usableH: Double,
        margin: Double,
        count: Int,
        spacing: Double,
        allowRotationGlobal: Boolean
    ): Layout {
        val bins = List(count) { MaxRectsBin(usableW, usableH) }
        val placedPerSheet = List(count) { mutableListOf<PlacedPart>() }
        val unplacedCounts = HashMap<Long, Int>()
        for (item in items) {
            val part = item.part
            val packW = part.widthMm + spacing
            val packH = part.heightMm + spacing
            val allowed = allowedAngles(part, allowRotationGlobal)
            var placed = false
            for (sheetIndex in bins.indices) {
                val placement = tryPlace(bins[sheetIndex], packW, packH, allowed) ?: continue
                val (angle, rotated, rect) = placement
                val placedW = if (rotated) part.heightMm else part.widthMm
                val placedH = if (rotated) part.widthMm else part.heightMm
                placedPerSheet[sheetIndex].add(
                    PlacedPart(
                        partId = part.id, name = part.name, shape = part.shape,
                        xMm = margin + rect.x, yMm = margin + rect.y,
                        widthMm = placedW, heightMm = placedH,
                        rotationDegrees = angle, colorIndex = item.colorIndex,
                        outline = part.outline
                    )
                )
                placed = true
                break
            }
            if (!placed) unplacedCounts[part.id] = (unplacedCounts[part.id] ?: 0) + 1
        }
        return Layout(placedPerSheet, unplacedCounts)
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

    private data class TryPlacement(val angle: Int, val rotated: Boolean, val rect: Rect)

    private fun tryPlace(
        bin: MaxRectsBin,
        packW: Double,
        packH: Double,
        allowedAngles: List<Int>
    ): TryPlacement? {
        val canStraight = allowedAngles.any { it == 0 || it == 180 }
        val canRotated = allowedAngles.any { it == 90 || it == 270 }
        var best: MaxRectsBin.Placement? = null
        var bestAngle = 0
        var bestRotated = false
        var bestScore: Pair<Double, Double>? = null

        fun consider(angle: Int, w: Double, h: Double, rotated: Boolean) {
            val placement = bin.findBest(w, h, false) ?: return
            val score = Pair(placement.shortSideLeftover, placement.longSideLeftover)
            val prev = bestScore
            if (prev == null || score.first < prev.first ||
                (score.first == prev.first && score.second < prev.second)
            ) {
                best = placement
                bestAngle = angle
                bestRotated = rotated
                bestScore = score
            }
        }

        if (canStraight) consider(if (0 in allowedAngles) 0 else 180, packW, packH, false)
        if (canRotated) consider(if (90 in allowedAngles) 90 else 270, packH, packW, true)

        val placement = best ?: return null
        bin.place(placement.rect)
        return TryPlacement(bestAngle, bestRotated, placement.rect)
    }

    // ---------------------------------------------------------------
    // Part geometry helpers
    // ---------------------------------------------------------------

    /** True area of a placed part (circle area, DXF outline area, or rect). */
    fun partArea(part: PlacedPart): Double {
        return when (part.shape) {
            PartShape.CIRCLE -> {
                val radius = part.widthMm / 2.0
                PI * radius * radius
            }
            PartShape.DXF -> {
                val outline = part.outline
                if (outline != null) {
                    OutlineGeometry.outlineArea(outline.points, outline.holes, outline.cutouts)
                } else {
                    part.widthMm * part.heightMm
                }
            }
            else -> part.widthMm * part.heightMm
        }
    }

    /** Cut length of one placed part (outline perimeter, circumference, or rect). */
    fun partPerimeter(part: PlacedPart): Double {
        return when (part.shape) {
            PartShape.CIRCLE -> PI * part.widthMm
            PartShape.DXF -> {
                val outline = part.outline
                if (outline != null) {
                    val poly = OutlineGeometry.polygonPerimeter(outline.points)
                    val holes = outline.holes.sumOf { 2.0 * PI * it.radius }
                    val cuts = outline.cutouts.sumOf { OutlineGeometry.polygonPerimeter(it) }
                    poly + holes + cuts
                } else {
                    2.0 * (part.widthMm + part.heightMm)
                }
            }
            else -> 2.0 * (part.widthMm + part.heightMm)
        }
    }

    private fun baseSize(part: PlacedPart): Pair<Double, Double> {
        val outline = part.outline
        if (outline != null) return Pair(outline.widthMm, outline.heightMm)
        return if (part.rotated) Pair(part.heightMm, part.widthMm) else Pair(part.widthMm, part.heightMm)
    }

    /** Outline points of a placed part, rotated to its placed angle. */
    fun placedOutlinePoints(part: PlacedPart): List<Point> {
        val outline = part.outline ?: return emptyList()
        val (baseW, baseH) = baseSize(part)
        return OutlineGeometry.rotatePoints(outline.points, part.rotationDegrees, baseW, baseH)
    }

    /** Hole centres of a placed part, rotated to its placed angle. */
    fun placedHoles(part: PlacedPart): List<CircleHole> {
        val outline = part.outline ?: return emptyList()
        val (baseW, baseH) = baseSize(part)
        return outline.holes.map {
            OutlineGeometry.rotateHole(it, part.rotationDegrees, baseW, baseH)
        }
    }

    /** Polygon cutouts (slots, windows) of a placed part, rotated to its placed angle. */
    fun placedCutouts(part: PlacedPart): List<List<Point>> {
        val outline = part.outline ?: return emptyList()
        val (baseW, baseH) = baseSize(part)
        return OutlineGeometry.rotateCutouts(outline.cutouts, part.rotationDegrees, baseW, baseH)
    }
}
