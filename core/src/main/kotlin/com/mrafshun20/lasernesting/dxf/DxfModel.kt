package com.mrafshun20.lasernesting.dxf

import com.mrafshun20.lasernesting.data.CircleHole
import com.mrafshun20.lasernesting.data.PartOutline
import com.mrafshun20.lasernesting.data.Point

/**
 * A 2D point in millimetres, in the part's local (normalised) coordinate
 * system unless stated otherwise.
 */
data class DxfPoint(val x: Double, val y: Double)

/**
 * A circular hole cut into a part: a CIRCLE entity whose centre lies inside
 * the part's outer outline. Kept as centre + radius (never polygonised),
 * in the same normalised coordinates as the part outline.
 */
data class DxfCircleHole(val center: DxfPoint, val radius: Double)

/**
 * One cuttable part imported from a DXF file.
 *
 * @param name stable part name: "Part 1", "Part 2", ... in the order the
 *   outlines were found, or the (clean, non-anonymous) block name when the
 *   part came from an INSERT of a named block.
 * @param outline the closed outer contour as a polyline. LINE/ARC chains
 *   are traced and ARC segments are sampled into straight segments with a
 *   step of at most 5 degrees, so arcs look smooth. The outline is
 *   implicitly closed: the last point connects back to the first (the
 *   first point is NOT repeated at the end). Normalised so the bounding-box
 *   minimum corner (min X, min Y) is at (0, 0).
 * @param holes circular holes inside the outline, in the same normalised
 *   coordinates as [outline].
 * @param widthMm bounding-box width of the normalised outline, in mm.
 * @param heightMm bounding-box height of the normalised outline, in mm.
 * @param layer the DXF layer most of the outline was drawn on.
 */
data class DxfPart(
    val name: String,
    val outline: List<DxfPoint>,
    val holes: List<DxfCircleHole>,
    val widthMm: Double,
    val heightMm: Double,
    val layer: String
)

/**
 * Result of parsing one DXF file.
 *
 * @param parts the parts found, in discovery order.
 * @param warnings non-fatal problems: non-mm units (with the scale factor
 *   applied), unsupported entities skipped, unclosed outlines, inner loops
 *   treated as cutouts instead of parts, and anything else the importer
 *   had to guess at or drop. Empty when the file parsed cleanly.
 */
data class DxfParseResult(
    val parts: List<DxfPart>,
    val warnings: List<String>
)

/**
 * Converts this DXF part's outline and holes to the core [PartOutline]
 * used by the nesting engine, the sheet drawing and the exporters.
 * The outline is already normalised (min corner at the origin) by the
 * parser, so the points and holes map across unchanged.
 */
fun DxfPart.toPartOutline(): PartOutline {
    val points = outline.map { Point(it.x, it.y) }
    val holes = holes.map { CircleHole(Point(it.center.x, it.center.y), it.radius) }
    return PartOutline(points, holes, widthMm, heightMm)
}
