package com.mrafshun20.lasernesting.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.mrafshun20.lasernesting.data.MachinePreset
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.SheetPreset
import com.mrafshun20.lasernesting.export.DxfExport
import com.mrafshun20.lasernesting.export.ExportCore
import com.mrafshun20.lasernesting.export.ReportExport
import com.mrafshun20.lasernesting.export.ReportInfo
import com.mrafshun20.lasernesting.nesting.CutPlanner
import com.mrafshun20.lasernesting.nesting.NestSheet
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PartInput
import com.mrafshun20.lasernesting.nesting.PlacedPart
import com.mrafshun20.lasernesting.nesting.UnplacedReason
import java.io.File
import java.time.LocalDate
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private fun partColor(index: Int): Color = Ui.PartColors[index % Ui.PartColors.size]

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

/**
 * Desktop entry point (called by Main.kt). Modern IGEMS-style layout:
 * dark sidebar with navigation and nesting parameters, main workspace
 * switching between Parts / Setup / Nest. Persian is RTL.
 */
@Composable
fun DesktopApp(state: DesktopState) {
    val settings by state.settings.collectAsState()
    val parts by state.parts.collectAsState()
    val nestResult by state.nestResult.collectAsState()
    val nestError by state.nestError.collectAsState()
    val message by state.message.collectAsState()
    val nesting by state.nesting.collectAsState()
    val isFa = state.isPersian()
    val layoutDir = if (isFa) LayoutDirection.Rtl else LayoutDirection.Ltr
    var tab by remember { mutableIntStateOf(0) }
    var selectedSheetIdx by remember { mutableIntStateOf(0) }
    var showRapid by remember { mutableStateOf(true) }
    var showLabels by remember { mutableStateOf(true) }
    var simulateCut by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Ui.Bg)
                .padding(Ui.Pad),
            horizontalArrangement = Arrangement.spacedBy(Ui.Gap)
        ) {
            // Sidebar: logo, navigation, nesting parameters, run, language.
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .clip(Ui.RCard)
                    .background(Ui.Surface)
                    .border(1.dp, Ui.Border, Ui.RCard)
                    .padding(Ui.Pad),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(Ui.RButton)
                                .background(Ui.AccentDim)
                                .border(1.5.dp, Ui.Accent, Ui.RButton),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ib", color = Ui.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                state.t("app_title"),
                                color = Ui.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(state.t("app_subtitle"), color = Ui.Accent, fontSize = 11.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Ui.Border)
                    )

                    NavPill(state.t("nav_parts"), Icons.Filled.List, tab == 0) { tab = 0 }
                    NavPill(state.t("nav_setup"), Icons.Filled.Settings, tab == 1) { tab = 1 }
                    NavPill(state.t("nav_nest"), Icons.Filled.GridView, tab == 2) { tab = 2 }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Ui.Border)
                    )

                    Text(
                        state.t("sec_nesting"),
                        style = TextStyle(
                            color = Ui.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                    )

                    CamParameterCard(
                        label = state.t("kerf"),
                        value = "${formatMm(settings.kerfMm)} ${state.t("mm")}",
                        onMinus = {
                            state.updateSettings {
                                it.copy(kerfMm = (it.kerfMm - 0.1).coerceAtLeast(0.0))
                            }
                        },
                        onPlus = {
                            state.updateSettings { it.copy(kerfMm = it.kerfMm + 0.1) }
                        }
                    )
                    CamParameterCard(
                        label = state.t("gap"),
                        value = "${formatMm(settings.gapMm)} ${state.t("mm")}",
                        onMinus = {
                            state.updateSettings {
                                it.copy(gapMm = (it.gapMm - 1.0).coerceAtLeast(0.0))
                            }
                        },
                        onPlus = {
                            state.updateSettings { it.copy(gapMm = it.gapMm + 1.0) }
                        }
                    )
                    CamParameterCard(
                        label = state.t("margin"),
                        value = "${formatMm(settings.marginMm)} ${state.t("mm")}",
                        onMinus = {
                            state.updateSettings {
                                it.copy(marginMm = (it.marginMm - 1.0).coerceAtLeast(0.0))
                            }
                        },
                        onPlus = {
                            state.updateSettings { it.copy(marginMm = it.marginMm + 1.0) }
                        }
                    )

                    ToggleRow(
                        label = state.t("show_path"),
                        checked = showRapid,
                        onCheckedChange = { showRapid = it }
                    )
                    ToggleRow(
                        label = state.t("show_labels"),
                        checked = showLabels,
                        onCheckedChange = { showLabels = it }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            state.runNesting()
                            tab = 2
                        },
                        shape = Ui.RButton,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Ui.Accent,
                            contentColor = Ui.Bg,
                            disabledContainerColor = Ui.Card,
                            disabledContentColor = Ui.TextMuted
                        ),
                        enabled = !nesting && parts.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            if (nesting) state.t("nesting_running") else state.t("run_nest"),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { state.newProject() },
                            shape = Ui.RButton,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.TextSecondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Ui.Border),
                            modifier = Modifier.weight(1f)
                        ) { Text(state.t("new_project"), fontSize = 11.sp) }
                        OutlinedButton(
                            onClick = {
                                val file = chooseFile(
                                    title = state.t("open_project"),
                                    filterDescription = "ibebels project (*.json)",
                                    "json"
                                )
                                if (file != null) state.loadProject(file)
                            },
                            shape = Ui.RButton,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.TextSecondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Ui.Border),
                            modifier = Modifier.weight(1f)
                        ) { Text(state.t("open_project"), fontSize = 11.sp) }
                        OutlinedButton(
                            onClick = {
                                val file = chooseFile(
                                    title = state.t("save_project"),
                                    filterDescription = "ibebels project (*.json)",
                                    "json",
                                    save = true,
                                    suggestedName = "LaserNesting-project.json"
                                )
                                if (file != null) state.saveProject(file)
                            },
                            shape = Ui.RButton,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.TextSecondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Ui.Border),
                            modifier = Modifier.weight(1f)
                        ) { Text(state.t("save_project"), fontSize = 11.sp) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.t("language"), color = Ui.TextSecondary, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .clip(Ui.RChip)
                                .background(Ui.Card)
                                .border(1.dp, Ui.Border, Ui.RChip)
                                .clickable {
                                    state.updateSettings {
                                        it.copy(language = if (isFa) "en" else "fa")
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (isFa) "فارسی (FA)" else "English (EN)",
                                color = Ui.Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Main workspace.
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Ui.Gap)
            ) {
                if (message != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Ui.RPanel)
                            .background(Ui.Card)
                            .border(1.dp, Ui.Border, Ui.RPanel)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message!!,
                            color = Ui.TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { state.clearMessage() }) {
                            Text("×", color = Ui.TextSecondary, fontSize = 16.sp)
                        }
                    }
                }

                when (tab) {
                    0 -> DesktopPartsContent(state, parts)
                    1 -> DesktopSetupContent(state, settings)
                    else -> DesktopNestContent(
                        state = state,
                        settings = settings,
                        nestResult = nestResult,
                        nestError = nestError,
                        selectedSheetIdx = selectedSheetIdx,
                        onSelectSheet = { selectedSheetIdx = it },
                        showRapid = showRapid,
                        showLabels = showLabels,
                        simulateCut = simulateCut,
                        onToggleSimulate = { simulateCut = !simulateCut }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Ui.RButton)
            .background(if (selected) Ui.AccentDim else Color.Transparent)
            .border(1.dp, if (selected) Ui.Accent else Color.Transparent, Ui.RButton)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Ui.Accent else Ui.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                title,
                color = if (selected) Ui.Accent else Ui.TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun CamParameterCard(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Ui.RField)
            .background(Ui.Card)
            .border(1.dp, Ui.Border, Ui.RField)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = Ui.TextSecondary, fontSize = 11.sp)
            Text(value, color = Ui.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallIconBtn("-", onMinus)
            SmallIconBtn("+", onPlus)
        }
    }
}

@Composable
private fun SmallIconBtn(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(Ui.RField)
            .background(Ui.Surface)
            .border(1.dp, Ui.Border, Ui.RField)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Ui.Accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Ui.RField)
            .background(Ui.Card)
            .border(1.dp, Ui.Border, Ui.RField)
            .clickable { onCheckedChange(!checked) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Ui.TextPrimary, fontSize = 12.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ui.Accent,
                checkedTrackColor = Ui.AccentDim,
                checkedBorderColor = Ui.Accent,
                uncheckedThumbColor = Ui.TextMuted,
                uncheckedTrackColor = Ui.Card,
                uncheckedBorderColor = Ui.Border
            )
        )
    }
}

@Composable
private fun MetricCard(modifier: Modifier, title: String, value: String, accent: Color) {
    Column(
        modifier = modifier
            .clip(Ui.RCard)
            .background(Ui.Surface)
            .border(1.dp, Ui.Border, Ui.RCard)
            .padding(16.dp)
    ) {
        Text(title, color = Ui.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
private fun ActionChipButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Ui.RField)
            .background(Ui.Surface)
            .border(1.dp, color.copy(alpha = 0.5f), Ui.RField)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Small selectable chip (machine / sheet / material / quality / language). */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(Ui.RChip)
            .background(if (selected) Ui.AccentDim else Ui.Card)
            .border(1.dp, if (selected) Ui.Accent else Ui.Border, Ui.RChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Ui.Accent else Ui.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Ui.RCard)
            .background(Ui.Surface)
            .border(1.dp, Ui.Border, Ui.RCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(Ui.Accent, RoundedCornerShape(2.dp))
            )
            Text(title, color = Ui.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        content()
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
            normalizeNumber(newText).toDoubleOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label, color = Ui.TextSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ui.TextPrimary,
            unfocusedTextColor = Ui.TextPrimary,
            focusedBorderColor = Ui.Accent,
            unfocusedBorderColor = Ui.Border,
            focusedLabelColor = Ui.Accent,
            unfocusedLabelColor = Ui.TextSecondary,
            cursorColor = Ui.Accent,
            focusedContainerColor = Ui.Card,
            unfocusedContainerColor = Ui.Card
        ),
        shape = Ui.RField
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
            normalizeNumber(newText).toIntOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label, color = Ui.TextSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ui.TextPrimary,
            unfocusedTextColor = Ui.TextPrimary,
            focusedBorderColor = Ui.Accent,
            unfocusedBorderColor = Ui.Border,
            focusedLabelColor = Ui.Accent,
            unfocusedLabelColor = Ui.TextSecondary,
            cursorColor = Ui.Accent,
            focusedContainerColor = Ui.Card,
            unfocusedContainerColor = Ui.Card
        ),
        shape = Ui.RField
    )
}

// ---------------------------------------------------------------------
// Parts
// ---------------------------------------------------------------------

@Composable
private fun DesktopPartsContent(state: DesktopState, parts: List<PartInput>) {
    var editing by remember { mutableStateOf<PartInput?>(null) }
    val totalPieces = parts.sumOf { it.quantity }
    val totalAreaM2 = parts.sumOf { it.widthMm * it.heightMm * it.quantity } / 1_000_000.0

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${parts.size} ${state.t("stat_types")}  •  $totalPieces ${state.t("stat_pieces")}  •  ${fmt(totalAreaM2, 2)} m²",
                color = Ui.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val file = chooseFile(
                        title = state.t("import_dxf"),
                        filterDescription = "DXF files (*.dxf)",
                        "dxf"
                    )
                    if (file != null) state.importDxf(listOf(file))
                },
                shape = Ui.RButton,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ui.Accent,
                    contentColor = Ui.Bg
                )
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(state.t("import_dxf"), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    editing = PartInput(
                        id = 0L,
                        name = state.t("new_part"),
                        shape = PartShape.RECTANGLE,
                        widthMm = 100.0,
                        heightMm = 100.0,
                        quantity = 1,
                        material = Material.MILD_STEEL,
                        thicknessMm = 3.0,
                        allowedRotations = setOf(0, 90, 180, 270),
                        priority = 0
                    )
                },
                shape = Ui.RButton,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.Accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, Ui.Accent)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(state.t("add_part"), fontWeight = FontWeight.Bold)
            }
        }

        if (parts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Ui.RCard)
                    .background(Ui.Surface)
                    .border(1.dp, Ui.Border, Ui.RCard)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    state.t("empty_title"),
                    color = Ui.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    state.t("empty_body"),
                    color = Ui.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        parts.forEachIndexed { index, part ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Ui.RPanel)
                    .background(Ui.Surface)
                    .border(1.dp, Ui.Border, Ui.RPanel)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DesktopPartPreview(part = part, color = partColor(index))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(part.name, color = Ui.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val shapeText = when (part.shape) {
                        PartShape.CIRCLE -> "${state.t("circle")}  Ø ${formatMm(part.widthMm)} ${state.t("mm")}"
                        PartShape.DXF -> "${state.t("dxf_outline")}  " +
                            "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)} ${state.t("mm")}"
                        else -> "${state.t("rectangle")}  " +
                            "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)} ${state.t("mm")}"
                    }
                    Text(shapeText, color = Ui.TextSecondary, fontSize = 12.sp)
                    val holes = part.outline?.holes?.size ?: 0
                    val cutouts = part.outline?.cutouts?.size ?: 0
                    val details = buildString {
                        append("${part.quantity}x  •  ${part.material.displayName} ")
                        append("${formatMm(part.thicknessMm)} ${state.t("mm")}")
                        if (holes > 0) append("  •  $holes ${state.t("holes")}")
                        if (cutouts > 0) append("  •  $cutouts ${state.t("cutouts")}")
                        if (part.priority > 0) append("  •  P${part.priority}")
                        if (part.allowedRotations == setOf(0)) append("  •  ${state.t("rot_locked")}")
                    }
                    Text(details, color = Ui.TextMuted, fontSize = 11.sp)
                }
                // Quantity stepper.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SmallIconBtn("-") {
                        if (part.quantity > 1) state.setQuantity(part, part.quantity - 1)
                    }
                    Text(
                        "${part.quantity}",
                        color = Ui.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.width(28.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    SmallIconBtn("+") { state.setQuantity(part, part.quantity + 1) }
                }
                IconButton(onClick = { editing = part }) {
                    Icon(Icons.Filled.Edit, contentDescription = state.t("edit"), tint = Ui.TextSecondary)
                }
                IconButton(onClick = { state.duplicatePart(part) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = state.t("duplicate"), tint = Ui.TextSecondary)
                }
                IconButton(onClick = { state.deletePart(part) }) {
                    Icon(Icons.Filled.Delete, contentDescription = state.t("delete"), tint = Ui.Danger)
                }
            }
        }
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

/** Small preview of a part's true shape (DXF outline with holes and cutouts, circle, or rectangle). */
@Composable
private fun DesktopPartPreview(part: PartInput, color: Color) {
    Canvas(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Ui.SheetFill)
            .border(1.dp, Ui.Border, RoundedCornerShape(8.dp))
    ) {
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
                drawCircle(color = Ui.TextPrimary, radius = r, center = Offset(cx, cy), style = Stroke(width = 1f))
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
                    drawPath(path = path, color = Ui.TextPrimary, style = Stroke(width = 1f))
                    for (hole in outline.holes) {
                        val cx = ox + (hole.center.x * scale).toFloat()
                        val cy = oy + (hole.center.y * scale).toFloat()
                        val r = (hole.radius * scale).toFloat()
                        drawCircle(color = Ui.SheetFill, radius = r, center = Offset(cx, cy))
                        drawCircle(color = Ui.TextPrimary, radius = r, center = Offset(cx, cy), style = Stroke(width = 1f))
                    }
                    for (poly in outline.cutouts) {
                        val cutPath = Path().apply {
                            poly.forEachIndexed { i, p ->
                                val x = ox + (p.x * scale).toFloat()
                                val y = oy + (p.y * scale).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        drawPath(path = cutPath, color = Ui.SheetFill)
                        drawPath(path = cutPath, color = Ui.TextPrimary, style = Stroke(width = 1f))
                    }
                } else {
                    drawRect(color = color, topLeft = Offset(ox, oy), size = Size(drawW, drawH))
                    drawRect(color = Ui.TextPrimary, topLeft = Offset(ox, oy), size = Size(drawW, drawH), style = Stroke(width = 1f))
                }
            }
            else -> {
                drawRect(color = color, topLeft = Offset(ox, oy), size = Size(drawW, drawH))
                drawRect(color = Ui.TextPrimary, topLeft = Offset(ox, oy), size = Size(drawW, drawH), style = Stroke(width = 1f))
            }
        }
    }
}

@Composable
private fun PartEditorDialog(
    state: DesktopState,
    part: PartInput,
    onDismiss: () -> Unit,
    onSave: (PartInput) -> Unit
) {
    var name by remember { mutableStateOf(part.name) }
    var shape by remember { mutableStateOf(part.shape) }
    var widthText by remember { mutableStateOf(formatMm(part.widthMm)) }
    var heightText by remember { mutableStateOf(formatMm(part.heightMm)) }
    var quantityText by remember { mutableStateOf(part.quantity.toString()) }
    var material by remember { mutableStateOf(part.material) }
    var thicknessText by remember { mutableStateOf(formatMm(part.thicknessMm)) }
    var priorityText by remember { mutableStateOf(part.priority.toString()) }
    var allowRotation by remember { mutableStateOf(part.allowedRotations != setOf(0)) }
    var rotations by remember { mutableStateOf(part.allowedRotations) }

    val isDxf = part.shape == PartShape.DXF
    val width = normalizeNumber(widthText).toDoubleOrNull()
    val height = if (shape == PartShape.CIRCLE) width else normalizeNumber(heightText).toDoubleOrNull()
    val quantity = normalizeNumber(quantityText).toIntOrNull()
    val thickness = normalizeNumber(thicknessText).toDoubleOrNull()
    val priority = normalizeNumber(priorityText).toIntOrNull() ?: 0
    val valid = name.isNotBlank() && width != null && width > 0 &&
        height != null && height > 0 && quantity != null && quantity > 0 &&
        thickness != null && thickness > 0 &&
        (!allowRotation || rotations.isNotEmpty())

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Ui.TextPrimary,
        unfocusedTextColor = Ui.TextPrimary,
        focusedBorderColor = Ui.Accent,
        unfocusedBorderColor = Ui.Border,
        focusedLabelColor = Ui.Accent,
        unfocusedLabelColor = Ui.TextSecondary,
        cursorColor = Ui.Accent,
        focusedContainerColor = Ui.Card,
        unfocusedContainerColor = Ui.Card
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ui.Surface,
        titleContentColor = Ui.TextPrimary,
        textContentColor = Ui.TextPrimary,
        title = { Text(state.t("edit_part"), fontWeight = FontWeight.Bold) },
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = Ui.RField
                )
                if (!isDxf) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(state.t("rectangle"), shape == PartShape.RECTANGLE) {
                            shape = PartShape.RECTANGLE
                        }
                        ChoiceChip(state.t("circle"), shape == PartShape.CIRCLE) {
                            shape = PartShape.CIRCLE
                        }
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        shape = Ui.RField
                    )
                    if (shape == PartShape.RECTANGLE) {
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            label = { Text(state.t("height")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                            shape = Ui.RField
                        )
                    }
                } else {
                    Text(
                        text = "${formatMm(part.widthMm)} x ${formatMm(part.heightMm)} ${state.t("mm")}  •  " +
                            "${part.outline?.holes?.size ?: 0} ${state.t("holes")}",
                        color = Ui.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(state.t("quantity")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = Ui.RField
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Material.entries.forEach { entry ->
                        ChoiceChip(entry.displayName, material == entry) { material = entry }
                    }
                }
                OutlinedTextField(
                    value = thicknessText,
                    onValueChange = { thicknessText = it },
                    label = { Text(state.t("thickness")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = Ui.RField
                )
                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it },
                    label = { Text(state.t("priority")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = Ui.RField
                )
                ToggleRow(
                    label = state.t("allow_rotation"),
                    checked = allowRotation,
                    onCheckedChange = { allowRotation = it }
                )
                if (allowRotation) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 90, 180, 270).forEach { angle ->
                            ChoiceChip(
                                "$angle°",
                                angle in rotations
                            ) {
                                rotations = if (angle in rotations) rotations - angle else rotations + angle
                            }
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
                            id = part.id,
                            name = name.trim(),
                            shape = if (isDxf) PartShape.DXF else shape,
                            widthMm = width!!,
                            heightMm = height!!,
                            quantity = quantity!!,
                            material = material,
                            thicknessMm = thickness!!,
                            allowedRotations = effectiveRotations,
                            priority = priority,
                            outline = part.outline
                        )
                    )
                }
            ) { Text(state.t("save"), color = Ui.Accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(state.t("cancel"), color = Ui.TextSecondary) }
        }
    )
}

// ---------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------

@Composable
private fun DesktopSetupContent(state: DesktopState, settings: DesktopSettings) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = state.t("sec_machine")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MachinePreset.entries.forEach { preset ->
                    ChoiceChip(preset.displayName, settings.machinePreset == preset) {
                        state.updateSettings { it.copy(machinePreset = preset) }
                    }
                }
            }
            Text(
                text = "${state.t("bed")}: ${formatMm(settings.machinePreset.maxWidthMm)} x " +
                    "${formatMm(settings.machinePreset.maxLengthMm)} ${state.t("mm")}",
                color = Ui.TextSecondary,
                fontSize = 13.sp
            )
        }

        SectionCard(title = state.t("sec_sheet")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetPreset.entries.forEach { preset ->
                    ChoiceChip(preset.displayName, settings.sheetPreset == preset) {
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
                    }
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
                    ChoiceChip(entry.displayName, settings.sheetMaterial == entry) {
                        state.updateSettings { it.copy(sheetMaterial = entry) }
                    }
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

        SectionCard(title = state.t("sec_nesting")) {
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
            ToggleRow(
                label = state.t("allow_rotation"),
                checked = settings.allowRotation,
                onCheckedChange = { checked ->
                    state.updateSettings { it.copy(allowRotation = checked) }
                }
            )
            ToggleRow(
                label = state.t("true_shape"),
                checked = settings.trueShape,
                onCheckedChange = { checked ->
                    state.updateSettings { it.copy(trueShape = checked) }
                }
            )
            Text(state.t("quality"), color = Ui.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(state.t("q_fast"), settings.quality == "FAST") {
                    state.updateSettings { it.copy(quality = "FAST") }
                }
                ChoiceChip(state.t("q_balanced"), settings.quality == "BALANCED") {
                    state.updateSettings { it.copy(quality = "BALANCED") }
                }
                ChoiceChip(state.t("q_high"), settings.quality == "HIGH") {
                    state.updateSettings { it.copy(quality = "HIGH") }
                }
            }
        }

        SectionCard(title = state.t("sec_cutting")) {
            ToggleRow(
                label = state.t("auto_speed"),
                checked = settings.autoSpeed,
                onCheckedChange = { checked ->
                    state.updateSettings { it.copy(autoSpeed = checked) }
                }
            )
            if (!settings.autoSpeed) {
                DecimalField(
                    label = state.t("cutting_speed"),
                    value = settings.cuttingSpeedMmPerMin,
                    onValueChange = { v ->
                        state.updateSettings { it.copy(cuttingSpeedMmPerMin = v) }
                    }
                )
            }
            Text(state.t("speed_hint"), color = Ui.TextMuted, fontSize = 12.sp)
            DecimalField(
                label = state.t("pierce_time"),
                value = settings.pierceSeconds,
                onValueChange = { v ->
                    state.updateSettings { it.copy(pierceSeconds = v) }
                }
            )
            DecimalField(
                label = state.t("price_kg"),
                value = settings.pricePerKg,
                onValueChange = { v ->
                    state.updateSettings { it.copy(pricePerKg = v) }
                }
            )
        }

        SectionCard(title = state.t("sec_app")) {
            Text(state.t("language"), color = Ui.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(state.t("lang_fa"), settings.language == "fa") {
                    state.updateSettings { it.copy(language = "fa") }
                }
                ChoiceChip(state.t("lang_en"), settings.language == "en") {
                    state.updateSettings { it.copy(language = "en") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Nest / results
// ---------------------------------------------------------------------

@Composable
private fun DesktopNestContent(
    state: DesktopState,
    settings: DesktopSettings,
    nestResult: com.mrafshun20.lasernesting.nesting.NestResult?,
    nestError: String?,
    selectedSheetIdx: Int,
    onSelectSheet: (Int) -> Unit,
    showRapid: Boolean,
    showLabels: Boolean,
    simulateCut: Boolean,
    onToggleSimulate: () -> Unit
) {
    val isFa = state.isPersian()
    var simProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(simulateCut) {
        if (!simulateCut) {
            simProgress = 0f
            return@LaunchedEffect
        }
        while (true) {
            delay(50)
            simProgress = (simProgress + 0.008f).let { if (it >= 1f) 0f else it }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Ui.Gap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Ui.Gap)
        ) {
            val util = nestResult?.overallUtilizationPercent ?: 0.0
            val pierces = nestResult?.totalPierces ?: 0
            val minutes = nestResult?.estimatedCutMinutes ?: 0.0
            MetricCard(
                Modifier.weight(1f), state.t("kpi_util"),
                String.format("%.1f%%", util), Ui.Success
            )
            MetricCard(
                Modifier.weight(1f), state.t("kpi_pierces"),
                "$pierces", Ui.Pierce
            )
            MetricCard(
                Modifier.weight(1f), state.t("kpi_time"),
                state.minutesText(minutes), Ui.Accent
            )
            MetricCard(
                Modifier.weight(1f), state.t("sec_sheet"),
                "${formatMm(settings.sheetWidthMm)}×${formatMm(settings.sheetLengthMm)} ${state.t("mm")}",
                Ui.Laser
            )
        }

        if (nestError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Ui.RPanel)
                    .background(Ui.Card)
                    .border(1.dp, Ui.Danger, Ui.RPanel)
                    .padding(14.dp)
            ) {
                Text(nestError!!, color = Ui.Danger, fontSize = 13.sp)
            }
        }

        val result = nestResult
        if (result == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Ui.RCard)
                    .background(Ui.Surface)
                    .border(1.dp, Ui.Border, Ui.RCard)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(state.t("nest_hint"), color = Ui.TextSecondary, fontSize = 14.sp)
                Button(
                    onClick = { state.runNesting() },
                    shape = Ui.RButton,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ui.Accent,
                        contentColor = Ui.Bg
                    )
                ) { Text(state.t("run_nest"), fontWeight = FontWeight.Bold) }
            }
            return@Column
        }

        // Sheet viewport.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Ui.RCard)
                .background(Ui.Surface)
                .border(1.dp, Ui.Border, Ui.RCard)
                .padding(Ui.Pad)
        ) {
            val sheet = result.sheets.firstOrNull { it.index == selectedSheetIdx }
                ?: result.sheets.firstOrNull()
            if (sheet != null) {
                DesktopZoomableSheet(
                    sheet = sheet,
                    sheetWidthMm = settings.sheetWidthMm,
                    sheetLengthMm = settings.sheetLengthMm,
                    marginMm = settings.marginMm,
                    showRapid = showRapid,
                    showLabels = showLabels,
                    simulate = simulateCut,
                    simProgress = simProgress,
                    state = state
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(Ui.RButton)
                    .background(Ui.Card.copy(alpha = 0.92f))
                    .border(1.dp, Ui.Border, Ui.RButton)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChipButton(state.t("import_dxf"), Ui.Accent) {
                    val file = chooseFile(
                        title = state.t("import_dxf"),
                        filterDescription = "DXF files (*.dxf)",
                        "dxf"
                    )
                    if (file != null) state.importDxf(listOf(file))
                }
                ActionChipButton(
                    if (isFa) "شبیه‌سازی برش" else "Simulate cut",
                    if (simulateCut) Ui.Laser else Ui.TextPrimary,
                    onToggleSimulate
                )
                ActionChipButton(state.t("export_dxf"), Ui.Success) {
                    val s = result.sheets.firstOrNull { it.index == selectedSheetIdx }
                        ?: result.sheets.firstOrNull() ?: return@ActionChipButton
                    val file = chooseFile(
                        title = state.t("export_dxf"),
                        filterDescription = "DXF files (*.dxf)",
                        "dxf",
                        save = true,
                        suggestedName = "sheet-${s.index + 1}.dxf"
                    )
                    if (file != null) {
                        file.writeText(
                            DxfExport.sheetDxf(s, settings.sheetWidthMm, settings.sheetLengthMm)
                        )
                        state.postMessage(state.t("export_done").format(file.name))
                    }
                }
            }
        }

        // Sheet tabs.
        if (result.sheets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                result.sheets.forEach { sheet ->
                    ChoiceChip(
                        "${state.t("sheet_tab")} ${sheet.index + 1} — %.0f%%".format(sheet.utilizationPercent),
                        selectedSheetIdx == sheet.index
                    ) { onSelectSheet(sheet.index) }
                }
            }
            val sheet = result.sheets.firstOrNull { it.index == selectedSheetIdx }
                ?: result.sheets.first()
            sheet.remnant?.let { remnant ->
                Text(
                    text = "${state.t("remnant")}: " +
                        "${formatMm(remnant.widthMm)} x ${formatMm(remnant.heightMm)} ${state.t("mm")}",
                    color = Ui.Remnant,
                    fontSize = 13.sp
                )
            }
        }

        if (result.unplaced.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Ui.RPanel)
                    .background(Ui.Card)
                    .border(1.dp, Ui.Danger, Ui.RPanel)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${state.t("unplaced")} (${result.unplaced.sumOf { it.quantity }})",
                    color = Ui.Danger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                result.unplaced.forEach { unplaced ->
                    val reasonKey = when (unplaced.reason) {
                        UnplacedReason.INVALID -> "reason_invalid"
                        UnplacedReason.NO_USABLE_AREA -> "reason_no_area"
                        UnplacedReason.NO_FREE_SPACE -> "reason_no_space"
                        UnplacedReason.DOES_NOT_FIT -> "reason_no_fit"
                    }
                    Text(
                        text = "• ${unplaced.name} " +
                            "(${formatMm(unplaced.widthMm)} x " +
                            "${formatMm(unplaced.heightMm)} ${state.t("mm")}, " +
                            "${unplaced.quantity}x) — ${state.t(reasonKey)}",
                        color = Ui.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Export row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
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
                                result, settings.sheetWidthMm, settings.sheetLengthMm
                            )
                        )
                        state.postMessage(state.t("export_done").format(file.name))
                    }
                },
                shape = Ui.RButton,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.Accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, Ui.Accent),
                modifier = Modifier.weight(1f)
            ) { Text(state.t("export_csv"), fontSize = 12.sp) }
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
                                result, settings.sheetWidthMm, settings.sheetLengthMm
                            )
                        )
                        state.postMessage(state.t("export_done").format(file.name))
                    }
                },
                shape = Ui.RButton,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.Accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, Ui.Accent),
                modifier = Modifier.weight(1f)
            ) { Text(state.t("export_svg"), fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    val file = chooseFile(
                        title = state.t("export_dxf_all"),
                        filterDescription = "DXF files (*.dxf)",
                        "dxf",
                        save = true,
                        suggestedName = "sheet-1.dxf"
                    )
                    if (file != null) {
                        val dir = file.parentFile
                        result.sheets.forEach { sheet ->
                            val out = File(dir, "sheet-${sheet.index + 1}.dxf")
                            out.writeText(
                                DxfExport.sheetDxf(
                                    sheet, settings.sheetWidthMm, settings.sheetLengthMm
                                )
                            )
                        }
                        state.postMessage(state.t("export_done").format(dir.name))
                    }
                },
                shape = Ui.RButton,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.Accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, Ui.Accent),
                modifier = Modifier.weight(1f)
            ) { Text(state.t("export_dxf_all"), fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    val file = chooseFile(
                        title = state.t("export_report"),
                        filterDescription = "HTML files (*.html)",
                        "html",
                        save = true,
                        suggestedName = "job-report.html"
                    )
                    if (file != null) {
                        val info = ReportInfo(
                            title = state.t("app_title"),
                            dateText = LocalDate.now().toString(),
                            machineName = settings.machinePreset.displayName,
                            materialName = settings.sheetMaterial.displayName,
                            thicknessMm = settings.sheetThicknessMm,
                            sheetWidthMm = settings.sheetWidthMm,
                            sheetLengthMm = settings.sheetLengthMm,
                            kerfMm = settings.kerfMm,
                            gapMm = settings.gapMm,
                            marginMm = settings.marginMm,
                            pricePerKg = settings.pricePerKg
                        )
                        file.writeText(ReportExport.html(result, info, isFa))
                        state.postMessage(state.t("export_done").format(file.name))
                    }
                },
                shape = Ui.RButton,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.Accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, Ui.Accent),
                modifier = Modifier.weight(1f)
            ) { Text(state.t("export_report"), fontSize = 12.sp) }
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
 * Zoomable sheet view (desktop): the CAM viewport with zoom controls,
 * mouse-drag panning and mouse-wheel zoom (1x–8x). The zoom is applied
 * with graphicsLayer on the vector Canvas, so the drawing stays sharp.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DesktopZoomableSheet(
    sheet: NestSheet,
    sheetWidthMm: Double,
    sheetLengthMm: Double,
    marginMm: Double,
    showRapid: Boolean,
    showLabels: Boolean,
    simulate: Boolean,
    simProgress: Float,
    state: DesktopState
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
    // Zoom around a viewport point (the mouse cursor for the wheel,
    // the click point for double-click) so the point under the cursor
    // stays put, instead of zooming toward the centre.
    val zoomAt = { cursor: Offset, factor: Float ->
        val oldScale = scale
        val newScale = (oldScale * factor).coerceIn(1f, 8f)
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val rel = cursor - center
        val f = newScale / oldScale
        val newOffset = rel + (offset - rel) * f
        scale = newScale
        offset = if (newScale <= 1f) {
            Offset.Zero
        } else {
            clampSheetOffset(newOffset, newScale, viewportSize)
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${(scale * 100).roundToInt()}%",
                color = Ui.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { zoomBy(1f / 1.25f) }) {
                Icon(Icons.Filled.ZoomOut, contentDescription = state.t("zoom_out"), tint = Ui.TextSecondary)
            }
            IconButton(onClick = { zoomBy(1.25f) }) {
                Icon(Icons.Filled.ZoomIn, contentDescription = state.t("zoom_in"), tint = Ui.TextSecondary)
            }
            IconButton(onClick = reset) {
                Icon(Icons.Filled.FitScreen, contentDescription = state.t("zoom_fit"), tint = Ui.TextSecondary)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .onSizeChanged { viewportSize = it }
                // Gestures on this unscaled viewport (not on the scaled
                // sheet), so a drag moves 1:1 with the mouse at any zoom
                // and the wheel position is in viewport coordinates.
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        if (scale > 1f) {
                            offset = clampSheetOffset(
                                offset + dragAmount, scale, viewportSize
                            )
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { tap ->
                        if (scale < 2f) zoomAt(tap, 3f / scale) else reset()
                    })
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.firstOrNull()
                    val delta = change?.scrollDelta ?: Offset.Zero
                    if (delta.y != 0f && change != null) {
                        zoomAt(
                            change.position,
                            if (delta.y < 0f) 1.15f else 1f / 1.15f
                        )
                    }
                    event.changes.forEach { it.consume() }
                },
            contentAlignment = Alignment.Center
        ) {
            SheetCamViewport(
                sheet = sheet,
                sheetWidthMm = sheetWidthMm,
                sheetLengthMm = sheetLengthMm,
                marginMm = marginMm,
                showRapid = showRapid,
                showLabels = showLabels,
                simulate = simulate,
                simProgress = simProgress,
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
        Text(
            text = state.t("zoom_hint"),
            color = Ui.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * CAM sheet viewport: dark sheet with a 100 mm grid, dashed margin,
 * true part outlines (with holes and cutouts) in part colours, the
 * planned cut order with rapid-travel dashed lines and pierce dots,
 * an optional cut simulation, and the remnant rectangle.
 */
@Composable
private fun SheetCamViewport(
    sheet: NestSheet,
    sheetWidthMm: Double,
    sheetLengthMm: Double,
    marginMm: Double,
    showRapid: Boolean,
    showLabels: Boolean,
    simulate: Boolean,
    simProgress: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val aspectRatio = (sheetWidthMm / sheetLengthMm).toFloat()
    Canvas(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(Ui.SheetFill)
            .border(1.dp, Ui.Border)
    ) {
        val scale = minOf(
            size.width / sheetWidthMm.toFloat(),
            size.height / sheetLengthMm.toFloat()
        )
        val drawW = sheetWidthMm.toFloat() * scale
        val drawH = sheetLengthMm.toFloat() * scale
        val originX = (size.width - drawW) / 2f
        val originY = (size.height - drawH) / 2f

        drawRect(
            Ui.SheetFill,
            topLeft = Offset(originX, originY),
            size = Size(drawW, drawH)
        )

        // 100 mm grid.
        val stepMm = 100f
        var gx = stepMm
        while (gx < sheetWidthMm) {
            val x = originX + gx * scale
            drawLine(Ui.SheetGrid, Offset(x, originY), Offset(x, originY + drawH), strokeWidth = 1f)
            gx += stepMm
        }
        var gy = stepMm
        while (gy < sheetLengthMm) {
            val y = originY + gy * scale
            drawLine(Ui.SheetGrid, Offset(originX, y), Offset(originX + drawW, y), strokeWidth = 1f)
            gy += stepMm
        }

        // Dashed margin.
        if (marginMm > 0) {
            val m = marginMm.toFloat() * scale
            drawRect(
                Ui.SheetMargin,
                topLeft = Offset(originX + m, originY + m),
                size = Size(drawW - 2 * m, drawH - 2 * m),
                style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            )
        }

        // Parts (true outlines, holes, cutouts).
        for (part in sheet.placed) {
            drawSheetPart(part, scale, originX, originY, showLabels, textMeasurer)
        }

        // Remnant.
        sheet.remnant?.let { remnant ->
            val left = originX + (remnant.xMm * scale).toFloat()
            val top = originY + (remnant.yMm * scale).toFloat()
            val width = (remnant.widthMm * scale).toFloat()
            val height = (remnant.heightMm * scale).toFloat()
            drawRect(
                color = Ui.Remnant,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            )
        }

        // Cut plan: rapid travel + pierce dots (+ simulation progress).
        val plan = CutPlanner.plan(sheet, sheetLengthMm)
        if (plan.ops.isNotEmpty()) {
            val maxOps = if (simulate) {
                (plan.ops.size * simProgress).toInt().coerceAtLeast(1)
            } else {
                plan.ops.size
            }.coerceAtMost(plan.ops.size)
            var lastX = originX
            var lastY = originY + drawH
            for (i in 0 until maxOps) {
                val op = plan.ops[i]
                val sx = originX + op.startX.toFloat() * scale
                val sy = originY + op.startY.toFloat() * scale
                if (showRapid) {
                    drawLine(
                        Ui.Rapid.copy(alpha = 0.7f),
                        start = Offset(lastX, lastY),
                        end = Offset(sx, sy),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }
                // Pierce dot at the contour start.
                drawCircle(
                    color = Ui.Pierce,
                    radius = 3f,
                    center = Offset(sx, sy)
                )
                if (simulate) {
                    // Draw the contour being cut in the laser colour.
                    val cutColor = if (i == maxOps - 1) Ui.Laser else Ui.Laser.copy(alpha = 0.55f)
                    if (op.isCircle) {
                        val cx = originX + op.circleCx.toFloat() * scale
                        val cy = originY + op.circleCy.toFloat() * scale
                        val r = op.circleR.toFloat() * scale
                        drawCircle(
                            color = cutColor,
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5f)
                        )
                    } else if (op.points.size >= 2) {
                        val path = Path().apply {
                            op.points.forEachIndexed { k, p ->
                                val x = originX + p.x.toFloat() * scale
                                val y = originY + p.y.toFloat() * scale
                                if (k == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        drawPath(path = path, color = cutColor, style = Stroke(width = 2.5f))
                    }
                }
                lastX = sx
                lastY = sy
            }
        }
    }
}

/** One placed part on the sheet: true outline (or rect/circle), holes, cutouts, label. */
private fun DrawScope.drawSheetPart(
    part: PlacedPart,
    scale: Float,
    originX: Float,
    originY: Float,
    showLabels: Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val color = partColor(part.colorIndex)
    val left = originX + (part.xMm * scale).toFloat()
    val top = originY + (part.yMm * scale).toFloat()
    val width = (part.widthMm * scale).toFloat()
    val height = (part.heightMm * scale).toFloat()

    when (part.shape) {
        PartShape.CIRCLE -> {
            val center = Offset(left + width / 2f, top + height / 2f)
            drawCircle(color = color.copy(alpha = 0.35f), radius = width / 2f, center = center)
            drawCircle(color = color, radius = width / 2f, center = center, style = Stroke(width = 2f))
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
                drawPath(path = path, color = color.copy(alpha = 0.30f))
                drawPath(path = path, color = color, style = Stroke(width = 2f))
                for (hole in NestingEngine.placedHoles(part)) {
                    val cx = left + (hole.center.x * scale).toFloat()
                    val cy = top + (hole.center.y * scale).toFloat()
                    val r = (hole.radius * scale).toFloat()
                    drawCircle(color = Ui.SheetFill, radius = r, center = Offset(cx, cy))
                    drawCircle(color = color, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.5f))
                }
                for (poly in NestingEngine.placedCutouts(part)) {
                    val cutPath = Path().apply {
                        poly.forEachIndexed { i, p ->
                            val x = left + (p.x * scale).toFloat()
                            val y = top + (p.y * scale).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(path = cutPath, color = Ui.SheetFill)
                    drawPath(path = cutPath, color = color, style = Stroke(width = 1.5f))
                }
            } else {
                drawRect(color = color.copy(alpha = 0.30f), topLeft = Offset(left, top), size = Size(width, height))
                drawRect(color = color, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(width = 2f))
            }
        }
        else -> {
            drawRect(color = color.copy(alpha = 0.30f), topLeft = Offset(left, top), size = Size(width, height))
            drawRect(color = color, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(width = 2f))
        }
    }

    // Single-line label (name + dimensions), measured to fit the part.
    if (showLabels && width > 40f && height > 16f) {
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
            style = TextStyle(fontSize = fontSizeSp.sp, color = Ui.TextPrimary)
        )
        while (fontSizeSp > 14f && layout.size.width > maxTextWidth) {
            fontSizeSp *= 0.9f
            layout = textMeasurer.measure(
                text = AnnotatedString(label),
                style = TextStyle(fontSize = fontSizeSp.sp, color = Ui.TextPrimary)
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
                style = TextStyle(fontSize = fontSizeSp.sp, color = Ui.TextPrimary)
            )
        }
    }
}
