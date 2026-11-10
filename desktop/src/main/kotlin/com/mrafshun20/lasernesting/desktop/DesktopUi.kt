package com.mrafshun20.lasernesting.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrafshun20.lasernesting.data.MachinePreset
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.SheetPreset
import com.mrafshun20.lasernesting.export.ExportCore
import com.mrafshun20.lasernesting.nesting.NestSheet
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PartInput
import com.mrafshun20.lasernesting.nesting.PlacedPart
import com.mrafshun20.lasernesting.nesting.UnplacedReason
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Distinct part colours, same palette as the Android app. */
private val PartPalette = listOf(
    Color(0xFF42A5F5), Color(0xFFEF5350), Color(0xFF66BB6A), Color(0xFFFFCA28),
    Color(0xFFAB47BC), Color(0xFFFF7043), Color(0xFF26A69A), Color(0xFFEC407A),
    Color(0xFF8D6E63), Color(0xFF78909C), Color(0xFF9CCC65), Color(0xFFFFA726)
)
private fun partColor(index: Int): Color = PartPalette[index % PartPalette.size]
private val SheetBackground = Color(0xFFF5F5F5)

/** Opens a Swing file chooser. Returns the chosen file, or null. */
private fun chooseFile(
    title: String,
    filterDescription: String,
    vararg extensions: String,
    save: Boolean = false,
    suggestedName: String? = null
): File? {
    val chooser = JFileChooser()
    chooser.dialogTitle = title
    if (extensions.isNotEmpty()) {
        chooser.fileFilter = FileNameExtensionFilter(filterDescription, *extensions)
    }
    if (suggestedName != null) chooser.selectedFile = File(suggestedName)
    val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    var file = chooser.selectedFile ?: return null
    if (save && extensions.isNotEmpty()) {
        val ext = extensions[0]
        if (!file.name.endsWith(".$ext", ignoreCase = true)) {
            file = File(file.parentFile, "${file.name}.$ext")
        }
    }
    return file
}

@Composable
fun DesktopApp(state: DesktopState) {
    val settings by state.settings.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val message by state.message.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(state.t("app_title"), style = MaterialTheme.typography.headlineMedium)
                Text(
                    state.t("app_subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = {
                val file = chooseFile(
                    title = state.t("open_project"),
                    filterDescription = "Laser Nesting project (*.json)",
                    "json"
                )
                if (file != null) state.loadProject(file)
            }) { Text(state.t("open_project")) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val file = chooseFile(
                    title = state.t("save_project"),
                    filterDescription = "Laser Nesting project (*.json)",
                    "json",
                    save = true,
                    suggestedName = "LaserNesting-project.json"
                )
                if (file != null) state.saveProject(file)
            }) { Text(state.t("save_project")) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text(state.t("tab_parts")) }
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1 },
                label = { Text(state.t("tab_setup")) }
            )
            FilterChip(
                selected = tab == 2,
                onClick = { tab = 2 },
                label = { Text(state.t("tab_nest")) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (message != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message!!,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { state.clearMessage() }) {
                        Text("×")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (tab) {
            0 -> DesktopPartsScreen(state)
            1 -> DesktopSetupScreen(state)
            2 -> DesktopNestScreen(state)
        }
    }
}

// ---------------------------------------------------------------------
// Parts
// ---------------------------------------------------------------------

@Composable
private fun DesktopPartsScreen(state: DesktopState) {
    val parts by state.parts.collectAsState()
    var editing by remember { mutableStateOf<PartInput?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val totalPieces = parts.sumOf { it.quantity }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${parts.size} part types  •  $totalPieces pieces",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val file = chooseFile(
                    title = state.t("import_dxf"),
                    filterDescription = "DXF files (*.dxf)",
                    "dxf"
                )
                if (file != null) state.importDxf(file)
            }) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(state.t("import_dxf"))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(state.t("add_simple"))
            }
        }

        if (parts.isEmpty()) {
            Text(
                text = state.t("no_parts"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        parts.forEachIndexed { index, part ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp).height(14.dp)
                            .background(partColor(index), RoundedCornerShape(7.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(part.name, style = MaterialTheme.typography.titleMedium)
                        val shapeText = when (part.shape) {
                            PartShape.CIRCLE -> "${state.t("circle")}  Ø ${formatMm(part.widthMm)} mm"
                            PartShape.DXF -> "${state.t("dxf_outline")}  " +
                                "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)} mm"
                            else -> "${state.t("rectangle")}  " +
                                "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)} mm"
                        }
                        Text(
                            shapeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val holes = part.outline?.holes?.size ?: 0
                        val details = buildString {
                            append("${part.quantity}x  •  ${part.material.displayName} ")
                            append("${formatMm(part.thicknessMm)} mm")
                            if (holes > 0) append("  •  $holes ${state.t("holes")}")
                            if (part.priority > 0) append("  •  P${part.priority}")
                        }
                        Text(
                            details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { editing = part }) {
                        Icon(Icons.Filled.Edit, contentDescription = state.t("edit"))
                    }
                    IconButton(onClick = { state.deletePart(part) }) {
                        Icon(Icons.Filled.Delete, contentDescription = state.t("delete"))
                    }
                }
            }
        }
    }

    if (showAdd) {
        PartEditorDialog(
            state = state,
            part = null,
            onDismiss = { showAdd = false },
            onSave = { part ->
                state.upsertPart(part)
                showAdd = false
            }
        )
    }
    editing?.let { part ->
        PartEditorDialog(
            state = state,
            part = part,
            onDismiss = { editing = null },
            onSave = { updated ->
                state.upsertPart(updated)
                editing = null
            }
        )
    }
}

@Composable
private fun PartEditorDialog(
    state: DesktopState,
    part: PartInput?,
    onDismiss: () -> Unit,
    onSave: (PartInput) -> Unit
) {
    var name by remember { mutableStateOf(part?.name ?: "") }
    var shape by remember { mutableStateOf(part?.shape ?: PartShape.RECTANGLE) }
    var widthText by remember { mutableStateOf(part?.let { formatMm(it.widthMm) } ?: "") }
    var heightText by remember { mutableStateOf(part?.let { formatMm(it.heightMm) } ?: "") }
    var quantityText by remember { mutableStateOf(part?.quantity?.toString() ?: "1") }
    var material by remember { mutableStateOf(part?.material ?: Material.MILD_STEEL) }
    var thicknessText by remember { mutableStateOf(part?.let { formatMm(it.thicknessMm) } ?: "3") }
    var priorityText by remember { mutableStateOf(part?.priority?.toString() ?: "0") }
    var allowRotation by remember {
        mutableStateOf(part?.allowedRotations?.let { it != setOf(0) } ?: true)
    }
    var rotations by remember {
        mutableStateOf(part?.allowedRotations ?: setOf(0, 90, 180, 270))
    }

    val isDxf = part?.shape == PartShape.DXF
    val width = widthText.toDoubleOrNull()
    val height = if (shape == PartShape.CIRCLE) width else heightText.toDoubleOrNull()
    val quantity = quantityText.toIntOrNull()
    val thickness = thicknessText.toDoubleOrNull()
    val priority = priorityText.toIntOrNull() ?: 0
    val valid = name.isNotBlank() && width != null && width > 0 &&
        height != null && height > 0 && quantity != null && quantity > 0 &&
        thickness != null && thickness > 0 &&
        (!allowRotation || rotations.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(state.t(if (part == null) "add_part" else "edit_part"))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(state.t("name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isDxf) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = shape == PartShape.RECTANGLE,
                            onClick = { shape = PartShape.RECTANGLE },
                            label = { Text(state.t("rectangle")) }
                        )
                        FilterChip(
                            selected = shape == PartShape.CIRCLE,
                            onClick = { shape = PartShape.CIRCLE },
                            label = { Text(state.t("circle")) }
                        )
                    }
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it },
                        label = {
                            Text(
                                state.t(
                                    if (shape == PartShape.CIRCLE) "diameter" else "width"
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
                            label = { Text(state.t("height")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(
                        text = "${formatMm(part!!.widthMm)} x ${formatMm(part.heightMm)} mm  •  " +
                            "${part.outline?.holes?.size ?: 0} ${state.t("holes")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(state.t("quantity")) },
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
                    label = { Text(state.t("thickness")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it },
                    label = { Text(state.t("priority")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.t("allow_rotation"),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowRotation,
                        onCheckedChange = { allowRotation = it }
                    )
                }
                if (allowRotation) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                label = { Text("$angle°") }
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
                        PartInput(
                            id = part?.id ?: 0,
                            name = name.trim(),
                            shape = part?.shape ?: shape,
                            widthMm = width!!,
                            heightMm = height!!,
                            quantity = quantity!!,
                            material = material,
                            thicknessMm = thickness!!,
                            allowedRotations = effectiveRotations,
                            priority = priority,
                            outline = part?.outline
                        )
                    )
                }
            ) { Text(state.t("save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(state.t("cancel")) }
        }
    )
}

// ---------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------

@Composable
private fun DesktopSetupScreen(state: DesktopState) {
    val settings by state.settings.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(title = state.t("language")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.language == "system",
                    onClick = {
                        state.updateSettings { it.copy(language = "system") }
                    },
                    label = { Text(state.t("lang_system")) }
                )
                FilterChip(
                    selected = settings.language == "en",
                    onClick = { state.updateSettings { it.copy(language = "en") } },
                    label = { Text(state.t("lang_en")) }
                )
                FilterChip(
                    selected = settings.language == "fa",
                    onClick = { state.updateSettings { it.copy(language = "fa") } },
                    label = { Text(state.t("lang_fa")) }
                )
            }
        }

        SectionCard(title = state.t("machine")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MachinePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.machinePreset == preset,
                        onClick = {
                            state.updateSettings { it.copy(machinePreset = preset) }
                        },
                        label = { Text(preset.displayName) }
                    )
                }
            }
            Text(
                text = "Max working area: ${formatMm(settings.machinePreset.maxWidthMm)} x " +
                    "${formatMm(settings.machinePreset.maxLengthMm)} mm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = state.t("sheet")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.sheetPreset == preset,
                        onClick = {
                            state.updateSettings { current ->
                                val w = preset.widthMm
                                val l = preset.lengthMm
                                if (w != null && l != null) {
                                    current.copy(
                                        sheetPreset = preset,
                                        sheetWidthMm = w,
                                        sheetLengthMm = l
                                    )
                                } else {
                                    current.copy(sheetPreset = preset)
                                }
                            }
                        },
                        label = { Text(preset.displayName) }
                    )
                }
            }
            DecimalField(
                label = state.t("sheet_width"),
                value = settings.sheetWidthMm,
                onValueChange = { v ->
                    state.updateSettings {
                        it.copy(sheetWidthMm = v, sheetPreset = SheetPreset.CUSTOM)
                    }
                }
            )
            DecimalField(
                label = state.t("sheet_length"),
                value = settings.sheetLengthMm,
                onValueChange = { v ->
                    state.updateSettings {
                        it.copy(sheetLengthMm = v, sheetPreset = SheetPreset.CUSTOM)
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Material.entries.forEach { entry ->
                    FilterChip(
                        selected = settings.sheetMaterial == entry,
                        onClick = {
                            state.updateSettings { it.copy(sheetMaterial = entry) }
                        },
                        label = { Text(entry.displayName) }
                    )
                }
            }
            DecimalField(
                label = state.t("sheet_thickness"),
                value = settings.sheetThicknessMm,
                onValueChange = { v ->
                    state.updateSettings { it.copy(sheetThicknessMm = v) }
                }
            )
            IntField(
                label = state.t("sheet_count"),
                value = settings.sheetCount,
                onValueChange = { v ->
                    state.updateSettings { it.copy(sheetCount = v) }
                }
            )
        }

        SectionCard(title = state.t("nesting")) {
            DecimalField(
                label = state.t("kerf"),
                value = settings.kerfMm,
                onValueChange = { v -> state.updateSettings { it.copy(kerfMm = v) } }
            )
            DecimalField(
                label = state.t("gap"),
                value = settings.gapMm,
                onValueChange = { v -> state.updateSettings { it.copy(gapMm = v) } }
            )
            DecimalField(
                label = state.t("margin"),
                value = settings.marginMm,
                onValueChange = { v -> state.updateSettings { it.copy(marginMm = v) } }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = state.t("allow_rotation"), modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.allowRotation,
                    onCheckedChange = { checked ->
                        state.updateSettings { it.copy(allowRotation = checked) }
                    }
                )
            }
        }

        SectionCard(title = state.t("cutting")) {
            DecimalField(
                label = state.t("cutting_speed"),
                value = settings.cuttingSpeedMmPerMin,
                onValueChange = { v ->
                    state.updateSettings { it.copy(cuttingSpeedMmPerMin = v) }
                }
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DecimalField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(formatMm(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toIntOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------------
// Nest / results
// ---------------------------------------------------------------------

@Composable
private fun DesktopNestScreen(state: DesktopState) {
    val result by state.nestResult.collectAsState()
    val error by state.nestError.collectAsState()
    val settings by state.settings.collectAsState()
    var selectedSheet by remember(result) { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { state.runNesting() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(state.t("run_nest")) }

        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = error!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        result?.let { r ->
            val sheetAreaM2 = settings.sheetWidthMm * settings.sheetLengthMm / 1_000_000.0
            val totalSheetAreaM2 = r.sheetsUsed * sheetAreaM2
            val wastePercent = 100.0 - r.overallUtilizationPercent
            val wasteAreaM2 = totalSheetAreaM2 * wastePercent / 100.0

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.t("utilization"),
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
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
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
                        MiniStat(state.t("sheets"), "${r.sheetsUsed}")
                        MiniStat(state.t("cut_length"), "%.1f m".format(r.totalCutLengthMm / 1000.0))
                        MiniStat(state.t("est_time"), formatMinutes(r.estimatedCutMinutes))
                        MiniStat(state.t("placed"), "${r.totalPartsPlaced}/${r.totalPartsRequested}")
                    }
                }
            }

            if (r.sheets.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.sheets.forEach { sheet ->
                        FilterChip(
                            selected = selectedSheet == sheet.index,
                            onClick = { selectedSheet = sheet.index },
                            label = {
                                Text(
                                    "Sheet ${sheet.index + 1} — " +
                                        "%.0f%%".format(sheet.utilizationPercent)
                                )
                            }
                        )
                    }
                }
                val sheet = r.sheets.firstOrNull { it.index == selectedSheet }
                    ?: r.sheets.first()
                DesktopSheetCanvas(
                    sheet = sheet,
                    sheetWidthMm = settings.sheetWidthMm,
                    sheetLengthMm = settings.sheetLengthMm,
                    marginMm = settings.marginMm,
                    modifier = Modifier.fillMaxWidth()
                )
                sheet.remnant?.let { remnant ->
                    Text(
                        text = "${state.t("remnant")}: " +
                            "${formatMm(remnant.widthMm)} x ${formatMm(remnant.heightMm)} mm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            if (r.unplaced.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${state.t("unplaced")} (${r.unplaced.sumOf { it.quantity }})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        r.unplaced.forEach { unplaced ->
                            val reasonKey = when (unplaced.reason) {
                                UnplacedReason.INVALID -> "reason_invalid"
                                UnplacedReason.NO_USABLE_AREA -> "reason_no_area"
                                UnplacedReason.NO_FREE_SPACE -> "reason_no_space"
                                UnplacedReason.DOES_NOT_FIT -> "reason_no_fit"
                            }
                            Text(
                                text = "• ${unplaced.name} " +
                                    "(${formatMm(unplaced.widthMm)} x " +
                                    "${formatMm(unplaced.heightMm)} mm, " +
                                    "${unplaced.quantity}x) — ${state.t(reasonKey)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val file = chooseFile(
                            title = state.t("export_csv"),
                            filterDescription = "CSV files (*.csv)",
                            "csv",
                            save = true,
                            suggestedName = "cut-list.csv"
                        )
                        if (file != null) {
                            file.writeText(
                                ExportCore.cutListCsv(
                                    r, settings.sheetWidthMm, settings.sheetLengthMm
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(state.t("export_csv")) }
                OutlinedButton(
                    onClick = {
                        val file = chooseFile(
                            title = state.t("export_svg"),
                            filterDescription = "SVG files (*.svg)",
                            "svg",
                            save = true,
                            suggestedName = "nest-layout.svg"
                        )
                        if (file != null) {
                            file.writeText(
                                ExportCore.nestSvg(
                                    r, settings.sheetWidthMm, settings.sheetLengthMm
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(state.t("export_svg")) }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Desktop sheet drawing: same true-outline rendering and single-line
 * fitted labels as the Android canvas.
 */
@Composable
private fun DesktopSheetCanvas(
    sheet: NestSheet,
    sheetWidthMm: Double,
    sheetLengthMm: Double,
    marginMm: Double,
    modifier: Modifier = Modifier
) {
    val aspectRatio = (sheetWidthMm / sheetLengthMm).toFloat()
    val textMeasurer = rememberTextMeasurer()
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

        val gridStepMm = 500.0
        val gridColor = Color(0xFFE0E0E0)
        var gridX = gridStepMm
        while (gridX < sheetWidthMm) {
            val x = (gridX * scale).toFloat()
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            gridX += gridStepMm
        }
        var gridY = gridStepMm
        while (gridY < sheetLengthMm) {
            val y = (gridY * scale).toFloat()
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            gridY += gridStepMm
        }

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
            drawDesktopPart(part, scale, textMeasurer)
        }

        sheet.remnant?.let { remnant ->
            val left = (remnant.xMm * scale).toFloat()
            val top = (remnant.yMm * scale).toFloat()
            val width = (remnant.widthMm * scale).toFloat()
            val height = (remnant.heightMm * scale).toFloat()
            drawRect(
                color = Color(0xFF2E7D32),
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 2f)
            )
        }
    }
}

private fun DrawScope.drawDesktopPart(
    part: PlacedPart,
    scale: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
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
                color = Color.Black, radius = width / 2f, center = center,
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
                for (hole in NestingEngine.placedHoles(part)) {
                    val cx = left + (hole.center.x * scale).toFloat()
                    val cy = top + (hole.center.y * scale).toFloat()
                    val r = (hole.radius * scale).toFloat()
                    drawCircle(color = SheetBackground, radius = r, center = Offset(cx, cy))
                    drawCircle(
                        color = Color.Black, radius = r, center = Offset(cx, cy),
                        style = Stroke(width = 1f)
                    )
                }
            } else {
                drawRect(color = color, topLeft = Offset(left, top), size = Size(width, height))
                drawRect(
                    color = Color.Black, topLeft = Offset(left, top),
                    size = Size(width, height), style = Stroke(width = 1f)
                )
            }
        }
        else -> {
            drawRect(color = color, topLeft = Offset(left, top), size = Size(width, height))
            drawRect(
                color = Color.Black, topLeft = Offset(left, top),
                size = Size(width, height), style = Stroke(width = 1f)
            )
        }
    }

    // Single-line label (name + dimensions), measured to fit the part.
    if (width > 40f && height > 16f) {
        val dims = if (part.shape == PartShape.CIRCLE) {
            "Ø ${formatMm(part.widthMm)}"
        } else {
            "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)}"
        }
        val label = "${part.name} $dims"
        val maxTextWidth = width * 0.92f
        var fontSizeSp = (height * 0.28f).coerceAtMost(44f)
        var layout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(fontSize = fontSizeSp.sp, color = Color.Black)
        )
        while (fontSizeSp > 14f && layout.size.width > maxTextWidth) {
            fontSizeSp *= 0.9f
            layout = textMeasurer.measure(
                text = AnnotatedString(label),
                style = TextStyle(fontSize = fontSizeSp.sp, color = Color.Black)
            )
        }
        if (fontSizeSp >= 14f && layout.size.width <= maxTextWidth) {
            val centerX = left + width / 2f
            val centerY = top + height / 2f
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(
                    centerX - layout.size.width / 2f,
                    centerY - layout.size.height / 2f
                ),
                style = TextStyle(fontSize = fontSizeSp.sp, color = Color.Black)
            )
        }
    }
}
