package com.mrafshun20.lasernesting.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import com.mrafshun20.lasernesting.R
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.export.Export
import com.mrafshun20.lasernesting.export.ExportCore
import com.mrafshun20.lasernesting.nesting.NestSheet
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PlacedPart
import com.mrafshun20.lasernesting.ui.theme.RemnantGreen
import com.mrafshun20.lasernesting.ui.theme.SheetBackground
import com.mrafshun20.lasernesting.ui.theme.SheetBorder
import com.mrafshun20.lasernesting.ui.theme.SheetGrid
import com.mrafshun20.lasernesting.ui.theme.partColor

/**
 * Nesting results: auto-nest, utilization and waste summary, per-sheet
 * drawing with a sheet selector, unplaced parts, remnants, and CSV / SVG
 * export. Built to read at a glance on a phone, next to the machine.
 */
@Composable
fun ResultsScreen(viewModel: NestingViewModel) {
    val result = viewModel.nestResult
    val error = viewModel.nestError
    val isNesting = viewModel.isNesting
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var selectedSheet by remember(result) { mutableIntStateOf(0) }
    var showFullscreen by remember(result) { mutableStateOf(false) }

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
            Text(
                stringResource(
                    if (isNesting) R.string.nest_running else R.string.nest_run
                )
            )
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
                            text = stringResource(R.string.nest_utilization),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%.1f%%".format(Locale.US, r.overallUtilizationPercent),
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
                        text = stringResource(
                            R.string.nest_waste, wastePercent, wasteAreaM2, totalSheetAreaM2
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(
                            label = stringResource(R.string.nest_sheets),
                            value = "${r.sheetsUsed}"
                        )
                        MiniStat(
                            label = stringResource(R.string.nest_cut_length),
                            value = "%.1f m".format(Locale.US, r.totalCutLengthMm / 1000.0)
                        )
                        MiniStat(
                            label = stringResource(R.string.nest_est_time),
                            value = formatMinutes(
                                r.estimatedCutMinutes,
                                stringResource(R.string.time_hour),
                                stringResource(R.string.time_min)
                            )
                        )
                        MiniStat(
                            label = stringResource(R.string.nest_placed),
                            value = "${r.totalPartsPlaced}/${r.totalPartsRequested}"
                        )
                    }
                }
            }

            // ---- Extra stats: pierces, weight, engine ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(
                            label = stringResource(R.string.nest_pierces),
                            value = "${r.totalPierces}"
                        )
                        if (r.sheetsWeightKg > 0) {
                            MiniStat(
                                label = stringResource(R.string.nest_weight),
                                value = "%.1f kg".format(Locale.US, r.sheetsWeightKg)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.nest_engine_info, r.engineLabel, r.elapsedMs.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    text = stringResource(
                        R.string.nest_sheet_used,
                        sheet.index + 1, sheet.utilizationPercent
                    ),
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
                // Tap the sheet (or the expand button) to open it large:
                // pinch to zoom, drag to pan, so the layout can be
                // inspected precisely. The in-place drawing is left
                // untransformed so the page keeps scrolling normally.
                Box(modifier = Modifier.fillMaxWidth()) {
                    SheetCanvas(
                        sheet = sheet,
                        sheetWidthMm = settings.sheetWidthMm,
                        sheetLengthMm = settings.sheetLengthMm,
                        marginMm = settings.marginMm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFullscreen = true }
                    )
                    IconButton(
                        onClick = { showFullscreen = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = stringResource(R.string.nest_enlarge)
                        )
                    }
                }
                if (showFullscreen) {
                    Dialog(
                        onDismissRequest = { showFullscreen = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        FullscreenSheetView(
                            sheet = sheet,
                            sheetWidthMm = settings.sheetWidthMm,
                            sheetLengthMm = settings.sheetLengthMm,
                            marginMm = settings.marginMm,
                            onClose = { showFullscreen = false }
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.nest_sheet_summary,
                        formatMm(settings.sheetWidthMm),
                        formatMm(settings.sheetLengthMm),
                        formatMm(settings.marginMm),
                        formatMm(settings.kerfMm),
                        formatMm(settings.gapMm)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Remnant: largest leftover rectangle on this sheet.
                sheet.remnant?.let { remnant ->
                    Text(
                        text = stringResource(
                            R.string.nest_remnant,
                            formatMm(remnant.widthMm),
                            formatMm(remnant.heightMm)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RemnantGreen
                    )
                }
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
                                text = stringResource(
                                    R.string.nest_unplaced,
                                    r.unplaced.sumOf { it.quantity }
                                ),
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
                                    unplaced.reason.localized(),
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
                            chooserTitle = context.getString(R.string.nest_share_csv)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.nest_export_csv))
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
                            chooserTitle = context.getString(R.string.nest_share_svg)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.nest_export_svg))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val files = r.sheets.map { sheet ->
                            "ibebels-sheet-${sheet.index + 1}.dxf" to ExportCore.sheetDxf(
                                sheet, settings.sheetWidthMm, settings.sheetLengthMm
                            )
                        }
                        Export.shareFiles(
                            context = context,
                            files = files,
                            mimeType = "application/dxf",
                            chooserTitle = context.getString(R.string.nest_share_dxf)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.nest_export_dxf))
                }
                OutlinedButton(
                    onClick = {
                        val persian = context.resources.configuration.locales.get(0)?.language == "fa"
                        val html = ExportCore.reportHtml(
                            result = r,
                            sheetWidthMm = settings.sheetWidthMm,
                            sheetLengthMm = settings.sheetLengthMm,
                            title = "iBebels",
                            machine = settings.machinePreset.displayName,
                            materialLine = settings.sheetMaterial.displayName + " " +
                                formatMm(settings.sheetThicknessMm) + " mm",
                            persian = persian
                        )
                        Export.shareFile(
                            context = context,
                            fileName = "ibebels-report.html",
                            mimeType = "text/html",
                            content = html,
                            chooserTitle = context.getString(R.string.nest_share_report)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.nest_export_report))
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
                text = stringResource(
                    R.string.nest_sheet_card_title,
                    sheet.index + 1
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(
                    R.string.nest_sheet_card_used,
                    sheet.utilizationPercent
                ),
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
 * Keeps a panned sheet from drifting away: the pan is limited so the
 * scaled sheet still covers the viewport, with a little overshoot so an
 * edge can be brought to the centre. At 1x (or before layout) no pan.
 */
private fun clampSheetOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    if (size.width <= 0 || size.height <= 0 || scale <= 1f) return Offset.Zero
    val maxX = size.width * (scale - 1f) / 2f + size.width * 0.25f
    val maxY = size.height * (scale - 1f) / 2f + size.height * 0.25f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}

/**
 * Full-screen view of one nest sheet: the same vector sheet drawing,
 * pinch-zoomable (1x–8x) and draggable, so the layout can be inspected
 * precisely. The zoom is applied with graphicsLayer on the vector
 * Canvas, so the drawing stays sharp. Buttons cover zoom in/out and
 * fit-to-screen for users who cannot pinch; the zoom percentage is
 * shown in the top bar.
 */
@Composable
private fun FullscreenSheetView(
    sheet: NestSheet,
    sheetWidthMm: Double,
    sheetLengthMm: Double,
    marginMm: Double,
    onClose: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val reset = {
        scale = 1f
        offset = Offset.Zero
    }
    val zoomBy = { factor: Float ->
        val newScale = (scale * factor).coerceIn(1f, 8f)
        scale = newScale
        offset = if (newScale <= 1f) {
            Offset.Zero
        } else {
            clampSheetOffset(offset, newScale, viewportSize)
        }
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 8f)
        scale = newScale
        offset = if (newScale <= 1f) {
            Offset.Zero
        } else {
            clampSheetOffset(offset + panChange, newScale, viewportSize)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.nest_zoom_close),
                        tint = Color.White
                    )
                }
                Text(
                    text = stringResource(
                        R.string.nest_sheet_used,
                        sheet.index + 1, sheet.utilizationPercent
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(scale * 100).roundToInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = { zoomBy(1f / 1.25f) }) {
                    Icon(
                        imageVector = Icons.Filled.ZoomOut,
                        contentDescription = stringResource(R.string.nest_zoom_out),
                        tint = Color.White
                    )
                }
                IconButton(onClick = { zoomBy(1.25f) }) {
                    Icon(
                        imageVector = Icons.Filled.ZoomIn,
                        contentDescription = stringResource(R.string.nest_zoom_in),
                        tint = Color.White
                    )
                }
                IconButton(onClick = reset) {
                    Icon(
                        imageVector = Icons.Filled.FitScreen,
                        contentDescription = stringResource(R.string.nest_zoom_fit),
                        tint = Color.White
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { viewportSize = it },
                contentAlignment = Alignment.Center
            ) {
                SheetCanvas(
                    sheet = sheet,
                    sheetWidthMm = sheetWidthMm,
                    sheetLengthMm = sheetLengthMm,
                    marginMm = marginMm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(transformState)
                )
            }
            Text(
                text = stringResource(R.string.nest_zoom_hint),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Draws one sheet to scale with its parts, colour-coded and labelled.
 * A light grid (every 500 mm) and the edge-margin boundary are drawn
 * so the layout reads like the machine bed. DXF parts are drawn as their
 * true outline (with holes); rectangles and circles keep their shape.
 *
 * Labels are a single line — part name plus dimensions, never the
 * material name — measured to fit inside the part. A part too small to
 * hold its label legibly gets no label, so text never overflows the part
 * bounds and never stacks vertically.
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
            .border(1.dp, SheetBorder)
    ) {
        val scale = minOf(
            size.width / sheetWidthMm.toFloat(),
            size.height / sheetLengthMm.toFloat()
        )

        // Light grid every 500 mm.
        val gridStepMm = 500.0
        val gridColor = SheetGrid
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
                color = Color(0xFF7C8CA8),
                topLeft = Offset(m, m),
                size = Size(size.width - 2 * m, size.height - 2 * m),
                style = Stroke(width = 1.5f)
            )
        }

        for (part in sheet.placed) {
            drawPart(part, scale)
        }

        // Remnant: largest leftover rectangle, dashed green outline.
        sheet.remnant?.let { remnant ->
            val left = (remnant.xMm * scale).toFloat()
            val top = (remnant.yMm * scale).toFloat()
            val width = (remnant.widthMm * scale).toFloat()
            val height = (remnant.heightMm * scale).toFloat()
            drawRect(
                color = RemnantGreen,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 2f)
            )
        }
    }
}

/** Draws one placed part (true shape + single-line fitted label). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPart(
    part: PlacedPart,
    scale: Float
) {
    val color = partColor(part.colorIndex)
    val left = (part.xMm * scale).toFloat()
    val top = (part.yMm * scale).toFloat()
    val width = (part.widthMm * scale).toFloat()
    val height = (part.heightMm * scale).toFloat()

    when (part.shape) {
        PartShape.CIRCLE -> drawCircleShape(
            Offset(left + width / 2f, top + height / 2f), width / 2f, color
        )
        PartShape.DXF -> {
            val outline = NestingEngine.placedOutline(part)
            if (outline != null && outline.points.isNotEmpty()) {
                drawOutlineShape(outline, left, top, scale, color)
            } else {
                drawRectShape(Offset(left, top), Size(width, height), color)
            }
        }
        else -> drawRectShape(Offset(left, top), Size(width, height), color)
    }

    // ---- Single-line label, fitted to the part ----
    // Name + dimensions only. Shrunk until it fits the part width; if even
    // a small legible size does not fit, the label is skipped entirely.
    val big = if (part.shape == PartShape.DXF) width > 110f && height > 40f else width > 40f && height > 16f
    if (big) {
        val dims = if (part.shape == PartShape.CIRCLE) {
            "Ø ${formatMm(part.widthMm)}"
        } else {
            "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)}"
        }
        val label = "${part.name} $dims"
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val maxTextWidth = width * 0.92f
            var textSize = (height * 0.28f).coerceAtMost(44f)
            paint.textSize = textSize
            while (textSize > 14f && paint.measureText(label) > maxTextWidth) {
                textSize *= 0.9f
                paint.textSize = textSize
            }
            if (textSize >= 14f && paint.measureText(label) <= maxTextWidth) {
                val centerX = left + width / 2f
                val centerY = top + height / 2f
                val metrics = paint.fontMetrics
                val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
                canvas.nativeCanvas.drawText(label, centerX, baseline, paint)
            }
        }
    }
}
