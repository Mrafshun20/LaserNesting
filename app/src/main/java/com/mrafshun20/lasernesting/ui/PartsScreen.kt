package com.mrafshun20.lasernesting.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartEntity
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.ui.theme.partColor

/**
 * Parts library — the list of parts to cut, with add / edit / delete.
 * Each part gets a stable colour used on the nest drawing and in exports.
 */
@Composable
fun PartsScreen(viewModel: NestingViewModel) {
    val parts by viewModel.parts.collectAsState()
    var editingPart by remember { mutableStateOf<PartEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val totalPieces = parts.sumOf { it.quantity }

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
                Text("No parts yet", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add the parts you want to cut. Rectangles and circles, " +
                        "any size, any quantity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showAddDialog = true }) {
                    Text("Add your first part")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "${parts.size} part types  •  $totalPieces pieces",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                itemsIndexed(parts, key = { _, part -> part.id }) { index, part ->
                    PartRow(
                        part = part,
                        color = partColor(index),
                        onEdit = { editingPart = part },
                        onDelete = { viewModel.deletePart(part) }
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
            Icon(Icons.Filled.Add, contentDescription = "Add part")
        }
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                Text(
                    text = "${part.quantity}x  •  ${part.material.displayName} " +
                        "${formatMm(part.thicknessMm)} mm" +
                        if (part.allowRotation) "  •  rotation allowed" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit part")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete part")
            }
        }
    }
}

private fun partDescription(part: PartEntity): String {
    return if (part.shape == PartShape.CIRCLE) {
        "Circle  Ø ${formatMm(part.widthMm)} mm"
    } else {
        "Rectangle  ${formatMm(part.widthMm)} x ${formatMm(part.heightMm)} mm"
    }
}

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

    val width = widthText.toDoubleOrNull()
    val height = if (shape == PartShape.CIRCLE) width else heightText.toDoubleOrNull()
    val quantity = quantityText.toIntOrNull()
    val thickness = thicknessText.toDoubleOrNull()
    val valid = name.isNotBlank() &&
        width != null && width > 0 &&
        height != null && height > 0 &&
        quantity != null && quantity > 0 &&
        thickness != null && thickness > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (part == null) "Add part" else "Edit part") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = shape == PartShape.RECTANGLE,
                        onClick = { shape = PartShape.RECTANGLE },
                        label = { Text("Rectangle") }
                    )
                    FilterChip(
                        selected = shape == PartShape.CIRCLE,
                        onClick = { shape = PartShape.CIRCLE },
                        label = { Text("Circle") }
                    )
                }

                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it },
                    label = {
                        Text(if (shape == PartShape.CIRCLE) "Diameter (mm)" else "Width (mm)")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (shape == PartShape.RECTANGLE) {
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it },
                        label = { Text("Height (mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Material.entries.forEach { entry ->
                        FilterChip(
                            selected = material == entry,
                            onClick = { material = entry },
                            label = { Text(entry.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = thicknessText,
                    onValueChange = { thicknessText = it },
                    label = { Text("Thickness (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Allow 90° rotation",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowRotation,
                        onCheckedChange = { allowRotation = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        PartEntity(
                            id = part?.id ?: 0,
                            name = name.trim(),
                            shape = shape,
                            widthMm = width!!,
                            heightMm = height!!,
                            quantity = quantity!!,
                            material = material,
                            thicknessMm = thickness!!,
                            allowRotation = allowRotation
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
