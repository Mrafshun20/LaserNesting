package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.Material
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
    val estimatedCutMinutes: Double
)

/**
 * Nesting engine, pure Kotlin with no external dependency.
 *
 * Each part is inflated by (kerf + gap) for packing so neighbouring cuts
 * never merge, and the usable area is the sheet minus the edge margin on
 * all four sides. Parts are packed highest-priority first, then
 * largest-first, across as many sheets as available. Rotation angles are
 * 0 / 90 / 180 / 270 per part; for the bounding-box packer 0 and 180 share
 * one footprint and 90 and 270 share the other. Circles are packed by
 * their bounding square and drawn as circles; DXF outlines are packed by
 * their bounding box and drawn as their true outline (with holes).
 * The largest leftover rectangle on each sheet is reported as a remnant.
 *
 * Limitation: packing is bounding-box based (MaxRects). A DXF outline's
 * true shape is used for area, cut length and drawing, but the packer
 * reserves the full bounding box — so concave outlines waste the space
 * inside their bounding box. True-shape (polygon) packing is not in this
 * version.
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
        cuttingSpeedMmPerMin: Double
    ): NestResult {
        val count = sheetCount.coerceAtLeast(1)
        val spacing = (kerfMm + gapMm).coerceAtLeast(0.0)
        val margin = marginMm.coerceAtLeast(0.0)
        val usableW = sheetWidthMm - 2 * margin
        val usableH = sheetLengthMm - 2 * margin
        val sheetArea = sheetWidthMm * sheetLengthMm

        val totalRequested = parts.sumOf { it.quantity.coerceAtLeast(0) }

        // Invalid parts can never be placed.
        val invalid = parts.filter { it.quantity <= 0 || it.widthMm <= 0 || it.heightMm <= 0 }
        val valid = parts.filter { it.quantity > 0 && it.widthMm > 0 && it.heightMm > 0 }

        val unplaced = mutableListOf<UnplacedPart>()
        for (part in invalid) {
            unplaced.add(
                UnplacedPart(
                    name = part.name,
                    shape = part.shape,
                    widthMm = part.widthMm,
                    heightMm = part.heightMm,
                    quantity = part.quantity.coerceAtLeast(0),
                    reason = UnplacedReason.INVALID
                )
            )
        }

        // No usable area at all (margin larger than the sheet).
        if (usableW <= 0 || usableH <= 0) {
            for (part in valid) {
                unplaced.add(
                    UnplacedPart(
                        name = part.name,
                        shape = part.shape,
                        widthMm = part.widthMm,
                        heightMm = part.heightMm,
                        quantity = part.quantity,
                        reason = UnplacedReason.NO_USABLE_AREA
                    )
                )
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

        // Expand quantities into single items: highest priority first,
        // then largest area first, so important and big parts claim space.
        data class Item(val part: PartInput, val colorIndex: Int)
        val items = mutableListOf<Item>()
        valid.forEachIndexed { index, part ->
            repeat(part.quantity) { items.add(Item(part, index)) }
        }
        items.sortWith(
            compareByDescending<Item> { it.part.priority }
                .thenByDescending { it.part.widthMm * it.part.heightMm }
        )

        val bins = List(count) { MaxRectsBin(usableW, usableH) }
        val placedPerSheet = List(count) { mutableListOf<PlacedPart>() }
        val unplacedCounts = mutableMapOf<Long, Int>()

        for (item in items) {
            val part = item.part
            val packW = part.widthMm + spacing
            val packH = part.heightMm + spacing
            val allowed = allowedAngles(part, allowRotationGlobal)
            var placed: PlacedPart? = null
            // Try each sheet; within a sheet the bin picks the best fit
            // across the part's allowed orientations.
            for (sheetIndex in bins.indices) {
                val placement = tryPlace(bins[sheetIndex], packW, packH, allowed)
                if (placement != null) {
                    val (angle, rotated, rect) = placement
                    val placedW = if (rotated) part.heightMm else part.widthMm
                    val placedH = if (rotated) part.widthMm else part.heightMm
                    placed = PlacedPart(
                        partId = part.id,
                        name = part.name,
                        shape = part.shape,
                        xMm = margin + rect.x,
                        yMm = margin + rect.y,
                        widthMm = placedW,
                        heightMm = placedH,
                        rotationDegrees = angle,
                        colorIndex = item.colorIndex,
                        outline = part.outline
                    )
                    placedPerSheet[sheetIndex].add(placed)
                    break
                }
            }
            if (placed == null) {
                unplacedCounts[part.id] = (unplacedCounts[part.id] ?: 0) + 1
            }
        }

        // Group unplaced copies per part with a reason.
        for (part in valid) {
            val missing = unplacedCounts[part.id] ?: 0
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
                        name = part.name,
                        shape = part.shape,
                        widthMm = part.widthMm,
                        heightMm = part.heightMm,
                        quantity = missing,
                        reason = if (fitsEmptySheet) {
                            UnplacedReason.NO_FREE_SPACE
                        } else {
                            UnplacedReason.DOES_NOT_FIT
                        }
                    )
                )
            }
        }

        // Per-sheet results, statistics and remnants.
        var totalPlacedArea = 0.0
        var totalCutLength = 0.0
        var totalPlaced = 0
        val sheets = mutableListOf<NestSheet>()
        for (sheetIndex in placedPerSheet.indices) {
            val placed = placedPerSheet[sheetIndex]
            if (placed.isEmpty()) continue
            var area = 0.0
            for (p in placed) {
                area += partArea(p)
                totalCutLength += partPerimeter(p)
            }
            totalPlacedArea += area
            totalPlaced += placed.size
            sheets.add(
                NestSheet(
                    index = sheetIndex,
                    placed = placed,
                    utilizationPercent = if (sheetArea > 0) area / sheetArea * 100.0 else 0.0,
                    remnant = largestRemnant(bins[sheetIndex], margin)
                )
            )
        }

        val sheetsUsed = sheets.size
        val overallUtilization = if (sheetsUsed > 0 && sheetArea > 0) {
            totalPlacedArea / (sheetsUsed * sheetArea) * 100.0
        } else {
            0.0
        }
        val estimatedMinutes = if (cuttingSpeedMmPerMin > 0) {
            totalCutLength / cuttingSpeedMmPerMin
        } else {
            0.0
        }

        return NestResult(
            sheets = sheets,
            unplaced = unplaced,
            sheetsUsed = sheetsUsed,
            overallUtilizationPercent = overallUtilization,
            totalPartsPlaced = totalPlaced,
            totalPartsRequested = totalRequested,
            totalCutLengthMm = totalCutLength,
            estimatedCutMinutes = estimatedMinutes
        )
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

    /**
     * Tries to place a part in [bin] using any of [allowedAngles].
     * Returns the chosen angle, whether the bounding box is rotated
     * (90/270), and the placed rect — or null when it fits nowhere.
     * The bin's best-fit rule picks the tightest free rectangle across
     * the tried orientations.
     */
    private data class TryPlacement(val angle: Int, val rotated: Boolean, val rect: Rect)

    private fun tryPlace(
        bin: MaxRectsBin,
        packW: Double,
        packH: Double,
        allowedAngles: List<Int>
    ): TryPlacement? {
        // Distinct footprints: 0/180 share one, 90/270 share the other.
        val canStraight = allowedAngles.any { it == 0 || it == 180 }
        val canRotated = allowedAngles.any { it == 90 || it == 270 }
        var best: MaxRectsBin.Placement? = null
        var bestAngle = 0
        var bestRotated = false
        var bestScore: Pair<Double, Double>? = null

        fun consider(angle: Int, w: Double, h: Double, rotated: Boolean) {
            val placement = bin.findBest(w, h, false) ?: return
            // Score is the leftover of the free rectangle used:
            // tighter is better (same rule as MaxRects).
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

        if (canStraight) {
            val angle = if (0 in allowedAngles) 0 else 180
            consider(angle, packW, packH, false)
        }
        if (canRotated) {
            val angle = if (90 in allowedAngles) 90 else 270
            consider(angle, packH, packW, true)
        }

        val placement = best ?: return null
        bin.place(placement.rect)
        return TryPlacement(bestAngle, bestRotated, placement.rect)
    }

    /**
     * Largest leftover rectangle in [bin] as a reusable remnant, in sheet
     * coordinates (margin added). Only rectangles at least 50 x 50 mm are
     * reported; smaller scraps are not useful as remnants.
     */
    private fun largestRemnant(bin: MaxRectsBin, margin: Double): Remnant? {
        val best = bin.freeRectanglesSnapshot()
            .filter { it.width >= 50.0 && it.height >= 50.0 }
            .maxByOrNull { it.width * it.height }
            ?: return null
        return Remnant(
            xMm = margin + best.x,
            yMm = margin + best.y,
            widthMm = best.width,
            heightMm = best.height
        )
    }

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
                    OutlineGeometry.outlineArea(outline.points, outline.holes)
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
                    poly + holes
                } else {
                    2.0 * (part.widthMm + part.heightMm)
                }
            }
            else -> 2.0 * (part.widthMm + part.heightMm)
        }
    }

    /** Outline points of a placed part, rotated to its placed angle. */
    fun placedOutlinePoints(part: PlacedPart): List<Point> {
        val outline = part.outline ?: return emptyList()
        // Outline was stored against the part's unrotated bounding box;
        // rotate it to the placed angle. The stored width/height are the
        // placed (possibly swapped) size, so un-rotate the size first.
        val (baseW, baseH) = if (part.rotated) {
            Pair(part.heightMm, part.widthMm)
        } else {
            Pair(part.widthMm, part.heightMm)
        }
        return OutlineGeometry.rotatePoints(outline.points, part.rotationDegrees, baseW, baseH)
    }

    /** Hole centres of a placed part, rotated to its placed angle. */
    fun placedHoles(part: PlacedPart): List<CircleHole> {
        val outline = part.outline ?: return emptyList()
        val (baseW, baseH) = if (part.rotated) {
            Pair(part.heightMm, part.widthMm)
        } else {
            Pair(part.widthMm, part.heightMm)
        }
        return outline.holes.map {
            OutlineGeometry.rotateHole(it, part.rotationDegrees, baseW, baseH)
        }
    }
}
