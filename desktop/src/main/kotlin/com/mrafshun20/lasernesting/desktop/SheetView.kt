package com.mrafshun20.lasernesting.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.mrafshun20.lasernesting.data.PartOutline
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.Point
import com.mrafshun20.lasernesting.nesting.NestSheet
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PlacedPart
import com.mrafshun20.lasernesting.util.Fmt
import com.mrafshun20.lasernesting.util.Palette

/** Part colour from the shared palette (same as the SVG/HTML exports). */
fun partColor(index: Int): Color = Color(0xFF000000L or Palette.rgb(index).toLong())

/** Builds an even-odd path of [outline] (outer + cutouts + holes) mapped by [map]. */
private fun outlinePath(outline: PartOutline, map: (Point) -> Offset, scale: Float): Path {
    val path = Path()
    path.fillType = PathFillType.EvenOdd
    fun loop(points: List<Point>) {
        points.forEachIndexed { i, p ->
            val o = map(p)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        path.close()
    }
    loop(outline.points)
    outline.cutouts.forEach { loop(it) }
    for (h in outline.holes) {
        val c = map(h.center)
        val r = (h.radius * scale).toFloat()
        path.addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
    }
    return path
}

/** Draws a part (unrotated, fitted into [boxSize] at [topLeft]) for the Parts list tiles. */
fun DrawScope.drawPartPreview(
    shape: PartShape,
    widthMm: Double,
    heightMm: Double,
    outline: PartOutline?,
    color: Color,
    topLeft: Offset,
    boxSize: Size
) {
    val pad = boxSize.minDimension * 0.12f
    val availW = boxSize.width - 2 * pad
    val availH = boxSize.height - 2 * pad
    val w = if (shape == PartShape.DXF && outline != null) outline.widthMm else widthMm
    val h = if (shape == PartShape.DXF && outline != null) outline.heightMm else heightMm
    if (w <= 0.0 || h <= 0.0) return
    val s = minOf(availW / w.toFloat(), availH / h.toFloat())
    val ox = topLeft.x + (boxSize.width - (w * s).toFloat()) / 2f
    val oy = topLeft.y + (boxSize.height - (h * s).toFloat()) / 2f
    val fill = color.copy(alpha = 0.55f)
    val line = Stroke(width = 1.6f)
    when {
        shape == PartShape.CIRCLE -> {
            val c = Offset(ox + (w * s).toFloat() / 2f, oy + (h * s).toFloat() / 2f)
            drawCircle(fill, radius = (w * s).toFloat() / 2f, center = c)
            drawCircle(color, radius = (w * s).toFloat() / 2f, center = c, style = line)
        }
        shape == PartShape.DXF && outline != null && outline.points.isNotEmpty() -> {
            val path = outlinePath(outline, { p -> Offset(ox + (p.x * s).toFloat(), oy + (p.y * s).toFloat()) }, s)
            drawPath(path, fill)
            drawPath(path, color, style = line)
        }
        else -> {
            drawRect(fill, Offset(ox, oy), Size((w * s).toFloat(), (h * s).toFloat()))
            drawRect(color, Offset(ox, oy), Size((w * s).toFloat(), (h * s).toFloat()), style = line)
        }
    }
}

/**
 * Zoomable sheet drawing: wheel zooms toward the cursor, drag pans,
 * double-click fits. Everything is vector so it stays sharp at any zoom.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SheetViewer(
    sheet: NestSheet,
    sheetWidthMm: Double,
    sheetLengthMm: Double,
    marginMm: Double,
    remnantLabel: String,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()
    val framePad = 28f

    fun fitScale(): Float {
        if (box.width <= 0 || box.height <= 0) return 1f
        return minOf(
            (box.width - 2 * framePad) / sheetWidthMm.toFloat(),
            (box.height - 2 * framePad) / sheetLengthMm.toFloat()
        ).coerceAtLeast(0.01f)
    }

    fun zoomAt(factor: Float, at: Offset) {
        val old = zoom
        val next = (old * factor).coerceIn(1f, 60f)
        if (next == old) return
        val fit = fitScale()
        val center = Offset(box.width / 2f, box.height / 2f)
        val sheetPx = Offset(sheetWidthMm.toFloat(), sheetLengthMm.toFloat())
        val originOld = center + pan - sheetPx * (fit * old / 2f)
        val mm = (at - originOld) / (fit * old)
        val panNew = at - mm * (fit * next) - center + sheetPx * (fit * next / 2f)
        zoom = next
        pan = if (next <= 1f) Offset.Zero else panNew
    }

    Box(modifier = modifier.onSizeChanged { box = it }) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { _, drag -> if (zoom > 1f) pan += drag }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        zoom = 1f
                        pan = Offset.Zero
                    })
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.firstOrNull()
                    val dy = change?.scrollDelta?.y ?: 0f
                    if (change != null && dy != 0f) {
                        zoomAt(if (dy < 0f) 1.18f else 1f / 1.18f, change.position)
                    }
                    event.changes.forEach { it.consume() }
                }
        ) {
            val fit = fitScale()
            val scale = fit * zoom
            val sheetPxW = sheetWidthMm.toFloat() * scale
            val sheetPxH = sheetLengthMm.toFloat() * scale
            val origin = Offset(size.width / 2f, size.height / 2f) + pan -
                Offset(sheetPxW / 2f, sheetPxH / 2f)

            // Sheet plate.
            drawRoundRect(
                color = Color(0xFF1C2535),
                topLeft = origin,
                size = Size(sheetPxW, sheetPxH),
                cornerRadius = CornerRadius(10f, 10f)
            )
            // Fine grid every 100 mm (only when it is legible).
            val gridMm = if (scale * 100f >= 22f) 100.0 else 500.0
            val gridColor = Color(0xFF2A3548)
            var gx = gridMm
            while (gx < sheetWidthMm) {
                val x = origin.x + (gx * scale).toFloat()
                drawLine(gridColor, Offset(x, origin.y), Offset(x, origin.y + sheetPxH), 1f)
                gx += gridMm
            }
            var gy = gridMm
            while (gy < sheetLengthMm) {
                val y = origin.y + (gy * scale).toFloat()
                drawLine(gridColor, Offset(origin.x, y), Offset(origin.x + sheetPxW, y), 1f)
                gy += gridMm
            }
            drawRoundRect(
                color = Color(0xFF5B6B86),
                topLeft = origin,
                size = Size(sheetPxW, sheetPxH),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2f)
            )
            // Usable-area margin.
            if (marginMm > 0) {
                val m = (marginMm * scale).toFloat()
                drawRect(
                    color = Color(0xFF7C8CA8),
                    topLeft = origin + Offset(m, m),
                    size = Size(sheetPxW - 2 * m, sheetPxH - 2 * m),
                    style = Stroke(
                        width = 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                )
            }

            // Remnant under the parts.
            sheet.remnant?.let { r ->
                val tl = origin + Offset((r.xMm * scale).toFloat(), (r.yMm * scale).toFloat())
                val sz = Size((r.widthMm * scale).toFloat(), (r.heightMm * scale).toFloat())
                drawRect(Color(0x2234D399), tl, sz)
                drawRect(
                    Color(0xFF34D399), tl, sz,
                    style = Stroke(
                        width = 1.8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))
                    )
                )
                if (sz.width > 150f && sz.height > 40f) {
                    drawLabel(
                        measurer,
                        "$remnantLabel ${Fmt.mm(r.widthMm)} × ${Fmt.mm(r.heightMm)}",
                        tl + Offset(sz.width / 2f, sz.height / 2f),
                        sz.width * 0.9f,
                        14f,
                        Color(0xFF34D399)
                    )
                }
            }

            for (part in sheet.placed) {
                val tl = origin + Offset((part.xMm * scale).toFloat(), (part.yMm * scale).toFloat())
                val w = (part.widthMm * scale).toFloat()
                val h = (part.heightMm * scale).toFloat()
                if (tl.x > size.width || tl.y > size.height || tl.x + w < 0f || tl.y + h < 0f) continue
                drawPlacedPart(part, tl, scale, measurer)
            }
        }
    }
}

private fun DrawScope.drawPlacedPart(
    part: PlacedPart,
    topLeft: Offset,
    scale: Float,
    measurer: TextMeasurer
) {
    val color = partColor(part.colorIndex)
    val fill = color.copy(alpha = 0.5f)
    val w = (part.widthMm * scale).toFloat()
    val h = (part.heightMm * scale).toFloat()
    val line = Stroke(width = 1.7f)
    when (part.shape) {
        PartShape.CIRCLE -> {
            val c = topLeft + Offset(w / 2f, h / 2f)
            drawCircle(fill, radius = w / 2f, center = c)
            drawCircle(color, radius = w / 2f, center = c, style = line)
        }
        PartShape.DXF -> {
            val outline = NestingEngine.placedOutline(part)
            if (outline != null && outline.points.isNotEmpty()) {
                val path = outlinePath(
                    outline,
                    { p -> topLeft + Offset((p.x * scale).toFloat(), (p.y * scale).toFloat()) },
                    scale
                )
                drawPath(path, fill)
                drawPath(path, color, style = line)
            } else {
                drawRect(fill, topLeft, Size(w, h))
                drawRect(color, topLeft, Size(w, h), style = line)
            }
        }
        else -> {
            drawRect(fill, topLeft, Size(w, h))
            drawRect(color, topLeft, Size(w, h), style = line)
        }
    }
    // Label only when it fits comfortably.
    if (w > 70f && h > 26f && part.shape != PartShape.DXF || (part.shape == PartShape.DXF && w > 110f && h > 40f)) {
        val dims = if (part.shape == PartShape.CIRCLE) "Ø ${Fmt.mm(part.widthMm)}"
        else "${Fmt.mm(part.widthMm)} × ${Fmt.mm(part.heightMm)}"
        drawLabel(
            measurer, "${part.name}  $dims",
            topLeft + Offset(w / 2f, h / 2f), w * 0.9f, (h * 0.24f).coerceIn(11f, 22f), Color.White
        )
    }
}

/** Centered single-line text, shrunk to fit [maxWidth]; skipped when too small. */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    maxWidth: Float,
    startPx: Float,
    color: Color
) {
    var px = startPx
    var layout = measurer.measure(AnnotatedString(text), TextStyle(fontSize = (px / density).sp, color = color))
    while (px > 10f && layout.size.width > maxWidth) {
        px *= 0.88f
        layout = measurer.measure(AnnotatedString(text), TextStyle(fontSize = (px / density).sp, color = color))
    }
    if (layout.size.width > maxWidth) return
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    )
}
