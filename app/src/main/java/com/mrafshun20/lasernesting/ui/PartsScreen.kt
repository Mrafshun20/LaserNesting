package com.mrafshun20.lasernesting.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrafshun20.lasernesting.R
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartEntity
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.ui.theme.partColor

/**
 * Parts library — the list of parts to cut, with DXF import, add / edit /
 * delete. The primary way to add parts is a DXF import (one customer file
 * holds several parts); manual rectangle/circle entry is a secondary
 * "add simple part" option. Each part gets a stable colour used on the
 * nest drawing and in exports.
 */
@Composable
fun PartsScreen(viewModel: NestingViewModel) {
    val parts by viewModel.parts.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var editingPart by remember { mutableStateOf<PartEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val totalPieces = parts.sumOf { it.quantity }

    val dxfMessage = viewModel.dxfImportMessage
    LaunchedEffect(dxfMessage) {
        if (dxfMessage != null) {
            snackbarHostState.showSnackbar(dxfMessage)
            viewModel.clearDxfImportMessage()
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
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { showAddDialog = true }) {
                    Text(stringResource(R.string.parts_add_simple))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        onDelete = { viewModel.deletePart(part) },
                        onQuantityChange = { quantity ->
                            viewModel.setPartQuantity(part, quantity)
                        },
                        onDuplicate = { viewModel.duplicatePart(part) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.parts_add_simple))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showAddDialog) {
        PartEditorDialog(
            part = null,
            onDismiss = { showAddDialog = false },
            onSave = { part ->
                viewModel.upsertPart(part)
                showAddDialog = false
            }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(color, CircleShape)
                )
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
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.dialog_edit_part))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.part_delete))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onQuantityChange(part.quantity - 1) },
                    enabled = part.quantity > 1
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.part_quantity_decrease)
                    )
                }
                Text(
                    text = "${part.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clickable { onEdit() }
                        .padding(horizontal = 6.dp)
                )
                IconButton(onClick = { onQuantityChange(part.quantity + 1) }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.part_quantity_increase)
                    )
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
    part: PartEntity?,
    onDismiss: () -> Unit,
    onSave: (PartEntity) -> Unit
) {
    var name by remember { mutableStateOf(part?.name ?: "") }
    var shape by remember { mutableStateOf(part?.shape ?: PartShape.RECTANGLE) }
    var widthText by remember { mutableStateOf(part?.let { formatMm(it.widthMm) } ?: "") }
    var heightText by remember { mutableStateOf(part?.let { formatMm(it.heightMm) } ?: "") }
    var quantityText by remember { mutableStateOf(part?.quantity?.toString() ?: "1") }
    var material by remember { mutableStateOf(part?.material ?: Material.MILD_STEEL) }
    var thicknessText by remember { mutableStateOf(part?.let { formatMm(it.thicknessMm) } ?: "3") }
    var allowRotation by remember { mutableStateOf(part?.allowRotation ?: true) }
    var priorityText by remember { mutableStateOf(part?.priority?.toString() ?: "0") }
    var rotations by remember {
        mutableStateOf(part?.allowedRotations ?: setOf(0, 90, 180, 270))
    }
    var materialMenuExpanded by remember { mutableStateOf(false) }

    // DXF parts keep their true outline; the editor only changes quantity,
    // priority and rotations for them (size comes from the DXF file).
    val isDxf = part?.shape == PartShape.DXF
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
            Text(
                stringResource(
                    if (part == null) R.string.dialog_add_part else R.string.dialog_edit_part
                )
            )
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
                    val dxfPart = part!!
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
                            id = part?.id ?: 0,
                            name = name.trim(),
                            shape = part?.shape ?: shape,
                            widthMm = width!!,
                            heightMm = height!!,
                            quantity = quantity!!,
                            material = material,
                            thicknessMm = thickness!!,
                            allowRotation = allowRotation,
                            allowedRotationsCsv = effectiveRotations
                                .sorted().joinToString(","),
                            priority = priority,
                            outlineJson = part?.outlineJson,
                            sourceFile = part?.sourceFile
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
