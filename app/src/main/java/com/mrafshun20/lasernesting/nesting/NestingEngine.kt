package com.mrafshun20.lasernesting.nesting

import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartShape
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
    val allowRotation: Boolean
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
    val rotated: Boolean,
    val colorIndex: Int
)

/** One sheet of the nesting result. */
data class NestSheet(
    val index: Int,
    val placed: List<PlacedPart>,
    val utilizationPercent: Double
)

/** A part type that could not be placed, grouped with a reason. */
data class UnplacedPart(
    val name: String,
    val shape: PartShape,
    val widthMm: Double,
    val heightMm: Double,
    val quantity: Int,
    val reason: String
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
 * all four sides. Parts are packed largest-first across as many sheets as
 * available. Circles are packed by their bounding square and drawn as
 * circles; their true area is used for utilization.
 */
object NestingEngine {

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
                    reason = "Invalid size or quantity"
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
                        reason = "No usable area — reduce the edge margin"
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

        // Expand quantities into single items, largest first.
        data class Item(val part: PartInput, val colorIndex: Int)
        val items = mutableListOf<Item>()
        valid.forEachIndexed { index, part ->
            repeat(part.quantity) { items.add(Item(part, index)) }
        }
        items.sortByDescending { it.part.widthMm * it.part.heightMm }

        val bins = List(count) { MaxRectsBin(usableW, usableH) }
        val placedPerSheet = List(count) { mutableListOf<PlacedPart>() }
        val unplacedCounts = mutableMapOf<Long, Int>()

        for (item in items) {
            val part = item.part
            val packW = part.widthMm + spacing
            val packH = part.heightMm + spacing
            val allowRotation = allowRotationGlobal && part.allowRotation
            var placed = false
            for (sheetIndex in bins.indices) {
                val placement = bins[sheetIndex].insert(packW, packH, allowRotation)
                if (placement != null) {
                    val placedW = if (placement.rotated) part.heightMm else part.widthMm
                    val placedH = if (placement.rotated) part.widthMm else part.heightMm
                    placedPerSheet[sheetIndex].add(
                        PlacedPart(
                            partId = part.id,
                            name = part.name,
                            shape = part.shape,
                            xMm = margin + placement.rect.x,
                            yMm = margin + placement.rect.y,
                            widthMm = placedW,
                            heightMm = placedH,
                            rotated = placement.rotated,
                            colorIndex = item.colorIndex
                        )
                    )
                    placed = true
                    break
                }
            }
            if (!placed) {
                unplacedCounts[part.id] = (unplacedCounts[part.id] ?: 0) + 1
            }
        }

        // Group unplaced copies per part with a reason.
        for (part in valid) {
            val missing = unplacedCounts[part.id] ?: 0
            if (missing > 0) {
                val packW = part.widthMm + spacing
                val packH = part.heightMm + spacing
                val allowRotation = allowRotationGlobal && part.allowRotation
                val fitsEmptySheet =
                    (packW <= usableW && packH <= usableH) ||
                        (allowRotation && packH <= usableW && packW <= usableH)
                unplaced.add(
                    UnplacedPart(
                        name = part.name,
                        shape = part.shape,
                        widthMm = part.widthMm,
                        heightMm = part.heightMm,
                        quantity = missing,
                        reason = if (fitsEmptySheet) {
                            "No free space left on the available sheets"
                        } else {
                            "Does not fit the sheet"
                        }
                    )
                )
            }
        }

        // Per-sheet results and statistics.
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
                    utilizationPercent = if (sheetArea > 0) area / sheetArea * 100.0 else 0.0
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

    /** True area of a placed part (circle area for circles). */
    fun partArea(part: PlacedPart): Double {
        return if (part.shape == PartShape.CIRCLE) {
            val radius = part.widthMm / 2.0
            PI * radius * radius
        } else {
            part.widthMm * part.heightMm
        }
    }

    /** Cut length of one placed part: perimeter (circumference for circles). */
    fun partPerimeter(part: PlacedPart): Double {
        return if (part.shape == PartShape.CIRCLE) {
            PI * part.widthMm
        } else {
            2.0 * (part.widthMm + part.heightMm)
        }
    }
}
