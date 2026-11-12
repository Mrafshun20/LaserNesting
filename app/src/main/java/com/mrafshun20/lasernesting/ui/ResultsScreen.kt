package com.mrafshun20.lasernesting.ui

import android.os.SystemClock
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.mrafshun20.lasernesting.R
import com.mrafshun20.lasernesting.data.CutGas
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.export.Export
import com.mrafshun20.lasernesting.nesting.NestSheet
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PlacedPart
import com.mrafshun20.lasernesting.ui.theme.DarkSurfaceVariant
import com.mrafshun20.lasernesting.ui.theme.MutedRemnant
import com.mrafshun20.lasernesting.ui.theme.SheetBackground
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
    val parts by viewModel.parts.collectAsState()
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { viewModel.runNesting() },
            enabled = !isNesting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
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

        // No parts yet is a normal first-run state, not an error: a
        // friendly empty state instead of a red error card.
        if (parts.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nest_no_parts_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.nest_no_parts_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                )
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
            val money: (Double) -> String = { v ->
                if (v > 0) String.format(Locale.US, "%,.0f", v) else "—"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hero band: dark slate gradient into the primary
                    // container, with the utilization number large, bold
                    // and light — the first thing the eye lands on.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        DarkSurfaceVariant,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.nest_utilization),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "%.1f%%".format(Locale.US, r.overallUtilizationPercent),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (r.overallUtilizationPercent / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    Text(
                        text = stringResource(
                            R.string.nest_waste, wastePercent, wasteAreaM2, totalSheetAreaM2
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Material, thickness and the assist gas that decides
                    // the per-metre cutting rate.
                    Text(
                        text = settings.sheetMaterial.localizedName() + " " +
                            formatMm(settings.sheetThicknessMm) + " mm • " +
                            stringResource(R.string.setup_cut_gas) + ": " +
                            CutGas.forMaterial(
                                settings.sheetMaterial, settings.sheetThicknessMm
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Six tinted tiles: sheets / cut length / time, then
                    // placed / price / laser travel. The price tile is
                    // primary-tinted so the money figure stands out.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatTile(
                                label = stringResource(R.string.nest_sheets),
                                value = "${r.sheetsUsed}",
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = stringResource(R.string.nest_cut_length),
                                value = "%.1f m".format(Locale.US, r.totalCutLengthMm / 1000.0),
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = stringResource(R.string.nest_est_time),
                                value = formatMinutes(
                                    r.estimatedCutMinutes,
                                    stringResource(R.string.time_hour),
                                    stringResource(R.string.time_min)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatTile(
                                label = stringResource(R.string.nest_placed),
                                value = "${r.totalPartsPlaced}/${r.totalPartsRequested}",
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = stringResource(R.string.nest_price),
                                value = money(r.totalCost),
                                modifier = Modifier.weight(1f),
                                highlighted = true
                            )
                            StatTile(
                                label = stringResource(R.string.nest_laser_travel),
                                value = "%.1f m".format(Locale.US, r.totalTravelMm / 1000.0),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    // Price breakdown: the per-metre cutting fee is what
                    // drives the price, shown next to pierces and sheet.
                    Text(
                        text = stringResource(
                            R.string.nest_price_breakdown,
                            money(r.cuttingCost),
                            money(r.pierceCostTotal),
                            money(r.materialCost)
                        ),
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
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                        color = MutedRemnant
                    )
                }
            }

            // ---- Unplaced parts ----
            if (r.unplaced.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    )
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
        }
    }
}

/**
 * One tinted stat tile in the summary card: small label on top, large
 * value below. The price tile is primary-tinted so the money figure
 * stands out from the rest.
 */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlighted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SheetSelectorCard(
    sheet: NestSheet,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Subtle press feedback: the card shrinks and fades slightly while
    // held, then springs back — the standard modern touch response.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        label = "sheetCardPress"
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        label = "sheetCardPressAlpha"
    )
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 4.dp else 2.dp
        ),
        modifier = Modifier
            .width(112.dp)
            .graphicsLayer(
                scaleX = pressScale,
                scaleY = pressScale,
                alpha = pressAlpha
            )
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
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = stringResource(
                    R.string.nest_sheet_card_used,
                    sheet.utilizationPercent
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            LinearProgressIndicator(
                progress = { (sheet.utilizationPercent / 100.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
 * with the standard image-viewer gestures — pinch to zoom around the
 * pinch point, one-finger drag to pan 1:1 with the finger at any zoom,
 * double-tap to zoom in/out, and fling with inertia after a drag.
 * The zoom is applied with graphicsLayer on the vector Canvas, so the
 * drawing stays sharp. Buttons cover zoom in/out and fit-to-screen for
 * users who cannot pinch; the zoom percentage is shown in the top bar.
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
    val scope = rememberCoroutineScope()
    // The running fling / zoom animation, so a new gesture or button
    // press can cancel it at once instead of fighting it.
    var animJob by remember { mutableStateOf<Job?>(null) }

    fun clamp(offset: Offset, scale: Float): Offset =
        clampSheetOffset(offset, scale, viewportSize)

    // Smoothly animate to a scale/offset — used by the zoom buttons and
    // double-tap, so the view glides instead of jumping.
    fun animateZoomTo(targetScale: Float, targetOffset: Offset) {
        animJob?.cancel()
        val startScale = scale
        val startOffset = offset
        val endOffset = clamp(targetOffset, targetScale)
        animJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(250)) { f, _ ->
                scale = lerp(startScale, targetScale, f)
                offset = startOffset + (endOffset - startOffset) * f
            }
        }
    }

    val reset = {
        animJob?.cancel()
        animJob = null
        scale = 1f
        offset = Offset.Zero
    }
    val zoomBy = { factor: Float ->
        val newScale = (scale * factor).coerceIn(1f, 8f)
        animateZoomTo(newScale, offset)
    }
    // Double-tap toggles between fit (1x) and 3x, centred on the tap
    // point — the standard image-viewer behaviour. The tap is in
    // viewport coordinates (top-left origin) while the offset is a
    // translation from the centred position, so the tap is first made
    // relative to the viewport centre.
    val onDoubleTap = { tap: Offset ->
        val targetScale = if (scale < 2f) 3f else 1f
        val targetOffset = if (targetScale <= 1f) {
            Offset.Zero
        } else {
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val rel = tap - center
            rel + (offset - rel) * (targetScale / scale)
        }
        animateZoomTo(targetScale, targetOffset)
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
                    .onSizeChanged { viewportSize = it }
                    // Double-tap to zoom in/out, centred on the tap.
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { tap -> onDoubleTap(tap) })
                    }
                    // Pinch + pan + fling. The gestures run on this
                    // unscaled viewport (not on the scaled sheet), so the
                    // pan is 1:1 with the finger at any zoom — the drag
                    // is never divided by the zoom level. The pinch zooms
                    // around the pinch centroid, so the point under the
                    // fingers stays put, like Google Maps. When the
                    // gesture ends, the sheet keeps gliding with inertia.
                    .pointerInput(Unit) {
                        while (true) {
                            animJob?.cancel()
                            animJob = null
                            val tracker = VelocityTracker()
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                tracker.addPosition(
                                    SystemClock.uptimeMillis(), centroid
                                )
                                val oldScale = scale
                                val newScale = (oldScale * zoom).coerceIn(1f, 8f)
                                val factor = newScale / oldScale
                                // Zoom around the pinch centroid, so the
                                // point under the fingers stays put. The
                                // centroid is in viewport coordinates
                                // (top-left origin); the offset is a
                                // translation from the centred position,
                                // so the centroid is made relative to
                                // the viewport centre first.
                                val center = Offset(
                                    viewportSize.width / 2f,
                                    viewportSize.height / 2f
                                )
                                val rel = centroid - center
                                val zoomed = rel + (offset - rel) * factor
                                offset = clamp(zoomed + pan, newScale)
                                scale = newScale
                            }
                            val velocity = tracker.calculateVelocity()
                            if (scale > 1f && hypot(velocity.x, velocity.y) > 100f) {
                                animJob = scope.launch {
                                    var v = velocity
                                    var last = withFrameNanos { it }
                                    while (hypot(v.x, v.y) > 20f) {
                                        val now = withFrameNanos { it }
                                        val dt = ((now - last) / 1_000_000_000f)
                                            .coerceIn(0f, 0.05f)
                                        last = now
                                        v *= exp(-dt * 4.2f)
                                        val unclamped = offset + Offset(v.x * dt, v.y * dt)
                                        val clamped = clamp(unclamped, scale)
                                        if (clamped.x != unclamped.x) v = v.copy(x = 0f)
                                        if (clamped.y != unclamped.y) v = v.copy(y = 0f)
                                        offset = clamped
                                    }
                                }
                            }
                        }
                    },
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
                )
            }
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
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        val scale = minOf(
            size.width / sheetWidthMm.toFloat(),
            size.height / sheetLengthMm.toFloat()
        )

        // Light grid every 500 mm.
        val gridStepMm = 500.0
        val gridColor = Color(0xFFE4E7E9)
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
                color = Color(0xFF8A939A),
                topLeft = Offset(m, m),
                size = Size(size.width - 2 * m, size.height - 2 * m),
                style = Stroke(width = 1.5f)
            )
        }

        for (part in sheet.placed) {
            drawPart(part, scale)
        }

        // Remnant: largest leftover rectangle, dashed muted-green outline.
        sheet.remnant?.let { remnant ->
            val left = (remnant.xMm * scale).toFloat()
            val top = (remnant.yMm * scale).toFloat()
            val width = (remnant.widthMm * scale).toFloat()
            val height = (remnant.heightMm * scale).toFloat()
            drawRect(
                color = MutedRemnant,
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
        PartShape.CIRCLE -> {
            val center = Offset(left + width / 2f, top + height / 2f)
            drawCircle(color = color, radius = width / 2f, center = center)
            drawCircle(
                color = Color.Black,
                radius = width / 2f,
                center = center,
                style = Stroke(width = 1f)
            )
        }
        PartShape.DXF -> {
            val points = NestingEngine.placedOutlinePoints(part)
            if (points.isNotEmpty()) {
                val path = Path().apply {
                    points.forEachIndexed { i, p ->
                        val x = left + (p.x * scale).toFloat()
                        val y = top + (p.y * scale).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(path = path, color = color)
                drawPath(path = path, color = Color.Black, style = Stroke(width = 1f))
                // Holes: sheet-coloured circles with a dark outline.
                for (hole in NestingEngine.placedHoles(part)) {
                    val cx = left + (hole.center.x * scale).toFloat()
                    val cy = top + (hole.center.y * scale).toFloat()
                    val r = (hole.radius * scale).toFloat()
                    drawCircle(color = SheetBackground, radius = r, center = Offset(cx, cy))
                    drawCircle(
                        color = Color.Black,
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1f)
                    )
                }
                // Cutouts (polygonal holes/slots): sheet-coloured, so a part
                // nested inside a cutout reads as sitting in empty space,
                // not overlapping solid material.
                for (poly in NestingEngine.placedCutouts(part)) {
                    val cutPath = Path().apply {
                        poly.forEachIndexed { i, p ->
                            val x = left + (p.x * scale).toFloat()
                            val y = top + (p.y * scale).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(path = cutPath, color = SheetBackground)
                    drawPath(path = cutPath, color = Color.Black, style = Stroke(width = 1f))
                }
            } else {
                // No outline stored: fall back to the bounding rectangle.
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
        }
        else -> {
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
    }

    // ---- Single-line label, fitted to the part ----
    // Name + dimensions only (never the material name). The text is
    // measured and shrunk until it fits the part width; if even a small
    // legible size does not fit, the label is skipped entirely.
    if (width > 40f && height > 16f) {
        val dims = if (part.shape == PartShape.CIRCLE) {
            "Ø ${formatMm(part.widthMm)}"
        } else {
            "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)}"
        }
        val label = "${part.name} $dims"
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.BLACK
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            // Largest size that fits the part width (with a small inset)
            // and stays within half the part height, capped for legibility.
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
