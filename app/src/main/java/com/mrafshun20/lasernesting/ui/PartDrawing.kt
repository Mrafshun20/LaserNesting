package com.mrafshun20.lasernesting.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mrafshun20.lasernesting.data.PartOutline

/**
 * Shared part drawing: translucent fill with a crisp coloured cut line,
 * so parts glow on the dark sheet like on a CAD screen. Holes and cutouts
 * are real holes (even-odd fill), so parts nested inside them are visible.
 */
internal fun DrawScope.drawOutlineShape(
    outline: PartOutline,
    originX: Float,
    originY: Float,
    scale: Float,
    color: Color,
    lineWidth: Float = 1.6f
) {
    val path = Path()
    path.fillType = PathFillType.EvenOdd
    fun loop(points: List<com.mrafshun20.lasernesting.data.Point>) {
        points.forEachIndexed { i, p ->
            val x = originX + (p.x * scale).toFloat()
            val y = originY + (p.y * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    loop(outline.points)
    outline.cutouts.forEach { loop(it) }
    for (h in outline.holes) {
        val cx = originX + (h.center.x * scale).toFloat()
        val cy = originY + (h.center.y * scale).toFloat()
        val r = (h.radius * scale).toFloat()
        path.addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    }
    drawPath(path = path, color = color.copy(alpha = 0.5f))
    drawPath(path = path, color = color, style = Stroke(width = lineWidth))
}

internal fun DrawScope.drawCircleShape(center: Offset, radius: Float, color: Color, lineWidth: Float = 1.6f) {
    drawCircle(color = color.copy(alpha = 0.5f), radius = radius, center = center)
    drawCircle(color = color, radius = radius, center = center, style = Stroke(width = lineWidth))
}

internal fun DrawScope.drawRectShape(topLeft: Offset, size: Size, color: Color, lineWidth: Float = 1.6f) {
    drawRect(color = color.copy(alpha = 0.5f), topLeft = topLeft, size = size)
    drawRect(color = color, topLeft = topLeft, size = size, style = Stroke(width = lineWidth))
}
