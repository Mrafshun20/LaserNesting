package com.mrafshun20.lasernesting.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.export.Export
import com.mrafshun20.lasernesting.nesting.NestSheet
import com.mrafshun20.lasernesting.ui.theme.SheetBackground
import com.mrafshun20.lasernesting.ui.theme.partColor

/**
 * Nesting results: auto-nest, utilization and waste summary, per-sheet
 * drawing with a sheet selector, unplaced parts, and CSV / SVG export.
 * Built to read at a glance on a phone, next to the machine.
 */
@Composable
fun ResultsScreen(viewModel: NestingViewModel) {
    val result = viewModel.nestResult
    val error = viewModel.nestError
    val isNesting = viewModel.isNesting
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var selectedSheet by remember(result) { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (viewModel.nestResult == null && viewModel.nestError == null) {
            viewModel.runNesting()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { viewModel.runNesting() },
            enabled = !isNesting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isNesting) "Nesting..." else "Auto-nest parts")
        }

        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        result?.let { r ->
            // ---- Utilization and waste summary ----
            val sheetAreaM2 = settings.sheetWidthMm * settings.sheetLengthMm / 1_000_000.0
            val totalSheetAreaM2 = r.sheetsUsed * sheetAreaM2
            val wastePercent = 100.0 - r.overallUtilizationPercent
            val wasteAreaM2 = totalSheetAreaM2 * wastePercent / 100.0

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Material utilization",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%.1f%%".format(r.overallUtilizationPercent),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (r.overallUtilizationPercent / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Text(
                        text = "Waste: %.1f%%  •  %.2f m² of %.2f m²".format(
                            wastePercent, wasteAreaM2, totalSheetAreaM2
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(
                            label = "Sheets",
                            value = "${r.sheetsUsed}"
                        )
                        MiniStat(
                            label = "Cut length",
                            value = "%.1f m".format(r.totalCutLengthMm / 1000.0)
                        )
                        MiniStat(
                            label = "Est. time",
                            value = formatMinutes(r.estimatedCutMinutes)
                        )
                        MiniStat(
                            label = "Placed",
                            value = "${r.totalPartsPlaced}/${r.totalPartsRequested}"
                        )
                    }
                }
            }

            // ---- Sheet selector + drawing ----
            if (r.sheets.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    r.sheets.forEach { sheet ->
                        SheetSelectorCard(
                            sheet = sheet,
                            selected = selectedSheet == sheet.index,
                            onClick = { selectedSheet = sheet.index }
                        )
                    }
                }

                val sheet = r.sheets.firstOrNull { it.index == selectedSheet }
                    ?: r.sheets.first()
                Text(
                    text = "Sheet ${sheet.index + 1} — " +
                        "%.1f%%".format(sheet.utilizationPercent) + " used",
                    style = MaterialTheme.typography.titleMedium
                )
                LinearProgressIndicator(
                    progress = { (sheet.utilizationPercent / 100.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                SheetCanvas(
                    sheet = sheet,
                    sheetWidthMm = settings.sheetWidthMm,
                    sheetLengthMm = settings.sheetLengthMm,
                    marginMm = settings.marginMm,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Sheet ${formatMm(settings.sheetWidthMm)} x " +
                        "${formatMm(settings.sheetLengthMm)} mm  •  " +
                        "margin ${formatMm(settings.marginMm)} mm  •  " +
                        "kerf ${formatMm(settings.kerfMm)} mm + gap " +
                        "${formatMm(settings.gapMm)} mm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Unplaced parts ----
            if (r.unplaced.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unplaced parts " +
                                    "(${r.unplaced.sumOf { it.quantity }})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        r.unplaced.forEach { unplaced ->
                            val dims = if (unplaced.shape == PartShape.CIRCLE) {
                                "Ø ${formatMm(unplaced.widthMm)} mm"
                            } else {
                                "${formatMm(unplaced.widthMm)} x " +
                                    "${formatMm(unplaced.heightMm)} mm"
                            }
                            Text(
                                text = "• ${unplaced.name} ($dims, ${unplaced.quantity}x) — " +
                                    unplaced.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ---- Export ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val csv = Export.cutListCsv(
                            result = r,
                            sheetWidthMm = settings.sheetWidthMm,
                            sheetLengthMm = settings.sheetLengthMm
                        )
                        Export.shareFile(
                            context = context,
                            fileName = "cut-list.csv",
                            mimeType = "text/csv",
                            content = csv,
                            chooserTitle = "Share cut list"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cut list (CSV)")
                }
                OutlinedButton(
                    onClick = {
                        val svg = Export.nestSvg(
                            result = r,
                            sheetWidthMm = settings.sheetWidthMm,
                            sheetLengthMm = settings.sheetLengthMm
                        )
                        Export.shareFile(
                            context = context,
                            fileName = "nest-layout.svg",
                            mimeType = "image/svg+xml",
                            content = svg,
                            chooserTitle = "Share nest layout"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Layout (SVG)")
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SheetSelectorCard(
    sheet: NestSheet,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.width(120.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Sheet ${sheet.index + 1}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "%.0f%% used".format(sheet.utilizationPercent),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { (sheet.utilizationPercent / 100.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

/**
 * Draws one sheet to scale with its parts, colour-coded and labelled.
 * A light grid (every 500 mm) and the edge-margin boundary are drawn
 * so the layout reads like the machine bed.
 */
@Composable
private fun SheetCanvas(
    sheet: NestSheet,
    sheetWidthMm: Double,
    sheetLengthMm: Double,
    marginMm: Double,
    modifier: Modifier = Modifier
) {
    val aspectRatio = (sheetWidthMm / sheetLengthMm).toFloat()
    Canvas(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(SheetBackground)
            .border(1.dp, Color.Gray)
    ) {
        val scale = minOf(
            size.width / sheetWidthMm.toFloat(),
            size.height / sheetLengthMm.toFloat()
        )

        // Light grid every 500 mm.
        val gridStepMm = 500.0
        val gridColor = Color(0xFFE0E0E0)
        var gridX = gridStepMm
        while (gridX < sheetWidthMm) {
            val x = (gridX * scale).toFloat()
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            gridX += gridStepMm
        }
        var gridY = gridStepMm
        while (gridY < sheetLengthMm) {
            val y = (gridY * scale).toFloat()
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            gridY += gridStepMm
        }

        // Edge-margin boundary.
        if (marginMm > 0) {
            val m = (marginMm * scale).toFloat()
            drawRect(
                color = Color(0xFF9E9E9E),
                topLeft = Offset(m, m),
                size = Size(size.width - 2 * m, size.height - 2 * m),
                style = Stroke(width = 1.5f)
            )
        }

        for (part in sheet.placed) {
            val color = partColor(part.colorIndex)
            val left = (part.xMm * scale).toFloat()
            val top = (part.yMm * scale).toFloat()
            val width = (part.widthMm * scale).toFloat()
            val height = (part.heightMm * scale).toFloat()

            if (part.shape == PartShape.CIRCLE) {
                val center = Offset(left + width / 2f, top + height / 2f)
                drawCircle(color = color, radius = width / 2f, center = center)
                drawCircle(
                    color = Color.Black,
                    radius = width / 2f,
                    center = center,
                    style = Stroke(width = 1f)
                )
            } else {
                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 1f)
                )
            }

            if (width > 70f && height > 28f) {
                val textSize = (minOf(width, height) * 0.18f).coerceIn(18f, 44f)
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.BLACK
                        this.textSize = textSize
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    val centerX = left + width / 2f
                    val centerY = top + height / 2f
                    canvas.nativeCanvas.drawText(part.name, centerX, centerY - 4f, paint)
                    val dims = if (part.shape == PartShape.CIRCLE) {
                        "Ø ${formatMm(part.widthMm)}"
                    } else {
                        "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)}"
                    }
                    canvas.nativeCanvas.drawText(dims, centerX, centerY + textSize, paint)
                }
            }
        }
    }
}
