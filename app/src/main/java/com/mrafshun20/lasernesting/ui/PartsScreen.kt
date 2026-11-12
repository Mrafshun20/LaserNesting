package com.mrafshun20.lasernesting.ui

import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mrafshun20.lasernesting.R
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartEntity
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.ui.theme.SheetBackground
import com.mrafshun20.lasernesting.ui.theme.partColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Parts library — the list of parts to cut, with DXF import and edit /
 * delete. Parts are added ONLY by importing a DXF file from a customer
 * (one file holds several parts); there is no manual rectangle/circle
 * entry. Each part gets a stable colour used on the nest drawing and in
 * exports, and a small preview of its true shape. Quantity can be
 * changed right on the row, with hold-to-repeat steppers.
 */
@Composable
fun PartsScreen(viewModel: NestingViewModel) {
    val parts by viewModel.parts.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var editingPart by remember { mutableStateOf<PartEntity?>(null) }
    val totalPieces = parts.sumOf { it.quantity }

    val dxfMessage = viewModel.dxfImportMessage
    LaunchedEffect(dxfMessage) {
        if (dxfMessage != null) {
            snackbarHostState.showSnackbar(dxfMessage)
            viewModel.clearDxfImportMessage()
        }
    }
    // Delete offers Undo: a part (with its DXF outline) is too easy to
    // lose to a mistap, and there is no other way to get it back.
    val scope = rememberCoroutineScope()
    val deleteWithUndo = { part: PartEntity ->
        viewModel.deletePart(part)
        scope.launch {
            val outcome = snackbarHostState.showSnackbar(
                message = context.getString(R.string.part_deleted),
                actionLabel = context.getString(R.string.action_undo)
            )
            if (outcome == SnackbarResult.ActionPerformed) {
                viewModel.upsertPart(part)
            }
        }
    }

    // DXF file picker (Storage Access Framework). Any file can be picked;
    // the parser rejects non-DXF content with a message.
    val dxfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, null, null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: "file.dxf"
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull()
        if (text.isNullOrBlank()) {
            viewModel.importDxf("", fileName)
        } else {
            viewModel.importDxf(text, fileName)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (parts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Category,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.parts_empty_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.parts_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { dxfPicker.launch("*/*") }) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.parts_import_dxf))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.parts_count, parts.size, totalPieces
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { dxfPicker.launch("*/*") }) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.parts_import_dxf))
                        }
                    }
                }
                itemsIndexed(parts, key = { _, part -> part.id }) { index, part ->
                    PartRow(
                        part = part,
                        color = partColor(index),
                        onEdit = { editingPart = part },
                        onDelete = { deleteWithUndo(part) },
                        onQuantityChange = { quantity ->
                            viewModel.setPartQuantity(part, quantity)
                        },
                        onDuplicate = { viewModel.duplicatePart(part) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    editingPart?.let { part ->
        PartEditorDialog(
            part = part,
            onDismiss = { editingPart = null },
            onSave = { updated ->
                viewModel.upsertPart(updated)
                editingPart = null
            }
        )
    }
}

@Composable
private fun PartRow(
    part: PartEntity,
    color: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    onDuplicate: () -> Unit
) {
    val materialName = part.material.localizedName()
    var previewPart by remember { mutableStateOf<PartEntity?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PartPreview(part = part, color = color, onClick = { previewPart = part })
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(part.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = partDescription(part),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val holes = part.outline?.holes?.size ?: 0
                    val details = buildString {
                        append("${part.quantity}x  •  $materialName ")
                        append("${formatMm(part.thicknessMm)} mm")
                        if (holes > 0) append("  •  ${stringResource(R.string.part_holes, holes)}")
                        if (part.shape == PartShape.DXF && part.sourceFile != null) {
                            append("  •  ${part.sourceFile}")
                        }
                        if (part.priority > 0) append("  •  P${part.priority}")
                        if (part.allowRotation) {
                            append("  •  ${stringResource(R.string.part_rotation_allowed)}")
                        }
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.dialog_edit_part),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.part_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantity as one segmented control: a bordered pill with
                // the value in the middle, instead of loose round buttons.
                Row(
                    modifier = Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HoldRepeatButton(
                        onRepeat = { onQuantityChange(part.quantity - 1) },
                        enabled = part.quantity > 1,
                        description = stringResource(R.string.part_quantity_decrease)
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${part.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable { onEdit() }
                            .padding(horizontal = 10.dp)
                    )
                    HoldRepeatButton(
                        onRepeat = { onQuantityChange(part.quantity + 1) },
                        description = stringResource(R.string.part_quantity_increase)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.part_duplicate), maxLines = 1)
                }
            }
        }
    }

    previewPart?.let { preview ->
        PartPreviewDialog(
            part = preview,
            color = color,
            onDismiss = { previewPart = null }
        )
    }
}

/**
 * A round button that fires once on tap and keeps firing while held down:
 * the first repeat comes after ~400 ms, then every ~80 ms. Releasing
 * stops it at once. The callback is always the latest one
 * (rememberUpdatedState), so a hold keeps stepping from the current
 * quantity instead of repeating a stale value. Used for the part
 * quantity stepper. A quick tap fires exactly once.
 */
@Composable
private fun HoldRepeatButton(
    onRepeat: () -> Unit,
    enabled: Boolean = true,
    description: String?,
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val latestOnRepeat by rememberUpdatedState(onRepeat)
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        latestOnRepeat()
        delay(400)
        while (true) {
            latestOnRepeat()
            delay(80)
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (pressed && enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }
            )
            .semantics(mergeDescendants = true) {
                if (description != null) contentDescription = description
                role = Role.Button
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        }
                    }
                )
            }
    ) {
        content()
    }
}

/**
 * Small preview of a part's true shape in a 60 dp square, with a zoom
 * badge: tapping it opens the part large in a zoomable dialog, so the
 * outline, holes and slots can be inspected properly.
 */
@Composable
private fun PartPreview(part: PartEntity, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SheetBackground)
            .border(1.dp, Color(0xFFC9CED2), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        PartShapeCanvas(
            part = part,
            color = color,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    RoundedCornerShape(4.dp)
                )
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ZoomIn,
                contentDescription = stringResource(R.string.part_preview_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Draws a part's true shape: the DXF outline (with holes and polygonal
 * cutouts), a circle, or a rectangle, in the part's colour on a light
 * sheet-like background, fitted into the available size while preserving
 * the part's aspect ratio. Shared by the small row preview and the large
 * zoomable preview dialog.
 */
@Composable
private fun PartShapeCanvas(part: PartEntity, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pad = 6.dp.toPx()
        val availW = size.width - 2 * pad
        val availH = size.height - 2 * pad
        val w = part.widthMm.toFloat()
        val h = part.heightMm.toFloat()
        if (w <= 0f || h <= 0f || availW <= 0f || availH <= 0f) return@Canvas
        val scale = minOf(availW / w, availH / h)
        val drawW = w * scale
        val drawH = h * scale
        val ox = pad + (availW - drawW) / 2f
        val oy = pad + (availH - drawH) / 2f
        when (part.shape) {
            PartShape.CIRCLE -> {
                val cx = ox + drawW / 2f
                val cy = oy + drawH / 2f
                val r = minOf(drawW, drawH) / 2f
                drawCircle(color = color, radius = r, center = Offset(cx, cy))
                drawCircle(
                    color = Color.Black,
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1f)
                )
            }
            PartShape.DXF -> {
                val outline = part.outline
                if (outline != null && outline.points.isNotEmpty()) {
                    val path = Path().apply {
                        outline.points.forEachIndexed { i, p ->
                            val x = ox + (p.x * scale).toFloat()
                            val y = oy + (p.y * scale).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(path = path, color = color)
                    drawPath(path = path, color = Color.Black, style = Stroke(width = 1f))
                    for (hole in outline.holes) {
                        val cx = ox + (hole.center.x * scale).toFloat()
                        val cy = oy + (hole.center.y * scale).toFloat()
                        val r = (hole.radius * scale).toFloat()
                        drawCircle(color = SheetBackground, radius = r, center = Offset(cx, cy))
                        drawCircle(
                            color = Color.Black,
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1f)
                        )
                    }
                    // Polygonal cutouts (slots, windows): sheet-coloured,
                    // so they read as empty space cut out of the part.
                    for (poly in outline.cutouts) {
                        val cutPath = Path().apply {
                            poly.forEachIndexed { i, p ->
                                val x = ox + (p.x * scale).toFloat()
                                val y = oy + (p.y * scale).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        drawPath(path = cutPath, color = SheetBackground)
                        drawPath(path = cutPath, color = Color.Black, style = Stroke(width = 1f))
                    }
                } else {
                    drawRect(
                        color = color,
                        topLeft = Offset(ox, oy),
                        size = Size(drawW, drawH)
                    )
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(ox, oy),
                        size = Size(drawW, drawH),
                        style = Stroke(width = 1f)
                    )
                }
            }
            else -> {
                drawRect(
                    color = color,
                    topLeft = Offset(ox, oy),
                    size = Size(drawW, drawH)
                )
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(ox, oy),
                    size = Size(drawW, drawH),
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

/**
 * Keeps a panned part preview from drifting away: the pan is limited so
 * the scaled part still covers the viewport, with a little overshoot.
 * At 1x (or before layout) no pan.
 */
private fun clampPreviewOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    if (size.width <= 0 || size.height <= 0 || scale <= 1f) return Offset.Zero
    val maxX = size.width * (scale - 1f) / 2f + size.width * 0.25f
    val maxY = size.height * (scale - 1f) / 2f + size.height * 0.25f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}

/**
 * Full-screen preview of one part: the same true-shape drawing, large,
 * with the standard image-viewer gestures — pinch to zoom around the
 * pinch point, one-finger drag to pan 1:1 with the finger at any zoom,
 * double-tap to zoom in/out, and fling with inertia after a drag.
 * Zoom buttons cover users who cannot pinch. Same gesture pattern as
 * the full-screen sheet view in ResultsScreen.
 */
@Composable
private fun PartPreviewDialog(
    part: PartEntity,
    color: Color,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    fun clamp(offset: Offset, scale: Float): Offset =
        clampPreviewOffset(offset, scale, viewportSize)

    fun animateZoomTo(targetScale: Float, targetOffset: Offset) {
        animJob?.cancel()
        val startScale = scale
        val startOffset = offset
        val endOffset = clamp(targetOffset, targetScale)
        animJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(250)) { f, _ ->
                scale = startScale + (targetScale - startScale) * f
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.nest_zoom_close),
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = part.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = partDescription(part),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
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
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { tap -> onDoubleTap(tap) })
                        }
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
                    PartShapeCanvas(
                        part = part,
                        color = color,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
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
}

@Composable
private fun partDescription(part: PartEntity): String {
    return when (part.shape) {
        PartShape.CIRCLE -> stringResource(
            R.string.part_shape_circle
        ) + "  Ø ${formatMm(part.widthMm)} mm"
        PartShape.DXF -> stringResource(
            R.string.part_shape_dxf
        ) + "  ${formatMm(part.widthMm)} x ${formatMm(part.heightMm)} mm"
        else -> stringResource(
            R.string.part_shape_rectangle
        ) + "  ${formatMm(part.widthMm)} x ${formatMm(part.heightMm)} mm"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartEditorDialog(
    part: PartEntity,
    onDismiss: () -> Unit,
    onSave: (PartEntity) -> Unit
) {
    var name by remember { mutableStateOf(part.name) }
    var shape by remember { mutableStateOf(part.shape) }
    var widthText by remember { mutableStateOf(formatMm(part.widthMm)) }
    var heightText by remember { mutableStateOf(formatMm(part.heightMm)) }
    var quantityText by remember { mutableStateOf(part.quantity.toString()) }
    var material by remember { mutableStateOf(part.material) }
    var thicknessText by remember { mutableStateOf(formatMm(part.thicknessMm)) }
    var allowRotation by remember { mutableStateOf(part.allowRotation) }
    var priorityText by remember { mutableStateOf(part.priority.toString()) }
    var rotations by remember {
        mutableStateOf(part.allowedRotations)
    }
    var materialMenuExpanded by remember { mutableStateOf(false) }

    // DXF parts keep their true outline; the editor only changes quantity,
    // priority and rotations for them (size comes from the DXF file).
    val isDxf = part.shape == PartShape.DXF
    // Numeric fields accept Persian/Arabic digits too ("۱۰" parses as 10).
    val width = parseDouble(widthText)
    val height = if (shape == PartShape.CIRCLE) width else parseDouble(heightText)
    val quantity = parseInt(quantityText)
    val thickness = parseDouble(thicknessText)
    val priority = parseInt(priorityText) ?: 0
    val valid = name.isNotBlank() &&
        width != null && width > 0 &&
        height != null && height > 0 &&
        quantity != null && quantity > 0 &&
        thickness != null && thickness > 0 &&
        (!allowRotation || rotations.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.dialog_edit_part))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isDxf) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = shape == PartShape.RECTANGLE,
                            onClick = { shape = PartShape.RECTANGLE },
                            label = { Text(stringResource(R.string.part_shape_rectangle), maxLines = 1) }
                        )
                        FilterChip(
                            selected = shape == PartShape.CIRCLE,
                            onClick = { shape = PartShape.CIRCLE },
                            label = { Text(stringResource(R.string.part_shape_circle), maxLines = 1) }
                        )
                    }

                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it },
                        label = {
                            Text(
                                stringResource(
                                    if (shape == PartShape.CIRCLE) {
                                        R.string.field_diameter
                                    } else {
                                        R.string.field_width
                                    }
                                )
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (shape == PartShape.RECTANGLE) {
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            label = { Text(stringResource(R.string.field_height)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    val dxfPart = part
                    Text(
                        text = "${formatMm(dxfPart.widthMm)} x " +
                            "${formatMm(dxfPart.heightMm)} mm  •  " +
                            stringResource(
                                R.string.part_holes,
                                dxfPart.outline?.holes?.size ?: 0
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(stringResource(R.string.field_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Material as a dropdown: a full-width field whose options
                // are full-width single-line rows, so a long name can never
                // be squeezed into a narrow column (no vertical stacking).
                ExposedDropdownMenuBox(
                    expanded = materialMenuExpanded,
                    onExpandedChange = { materialMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = material.localizedName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.field_material)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = materialMenuExpanded
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = materialMenuExpanded,
                        onDismissRequest = { materialMenuExpanded = false }
                    ) {
                        Material.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.localizedName(), maxLines = 1) },
                                onClick = {
                                    material = entry
                                    materialMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = thicknessText,
                    onValueChange = { thicknessText = it },
                    label = { Text(stringResource(R.string.field_thickness)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it },
                    label = { Text(stringResource(R.string.field_priority)) },
                    supportingText = { Text(stringResource(R.string.field_priority_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.setup_allow_rotation),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowRotation,
                        onCheckedChange = { allowRotation = it }
                    )
                }

                if (allowRotation) {
                    Text(
                        text = stringResource(R.string.field_rotations),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 90, 180, 270).forEach { angle ->
                            FilterChip(
                                selected = angle in rotations,
                                onClick = {
                                    rotations = if (angle in rotations) {
                                        rotations - angle
                                    } else {
                                        rotations + angle
                                    }
                                },
                                label = { Text("$angle°", maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val effectiveRotations = if (allowRotation) {
                        rotations.ifEmpty { setOf(0) }
                    } else {
                        setOf(0)
                    }
                    onSave(
                        PartEntity(
                            id = part.id,
                            name = name.trim(),
                            shape = if (isDxf) PartShape.DXF else shape,
                            widthMm = width!!,
                            heightMm = height!!,
                            quantity = quantity!!,
                            material = material,
                            thicknessMm = thickness!!,
                            allowRotation = allowRotation,
                            allowedRotationsCsv = effectiveRotations
                                .sorted().joinToString(","),
                            priority = priority,
                            outlineJson = part.outlineJson,
                            sourceFile = part.sourceFile
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
