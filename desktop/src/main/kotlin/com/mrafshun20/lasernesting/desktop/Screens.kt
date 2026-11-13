package com.mrafshun20.lasernesting.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Square
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrafshun20.lasernesting.data.MachinePreset
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.SheetPreset
import com.mrafshun20.lasernesting.nesting.NestMode
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.PartInput
import com.mrafshun20.lasernesting.nesting.UnplacedReason
import com.mrafshun20.lasernesting.util.Fmt
import kotlinx.coroutines.delay
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private enum class Tab { PARTS, SETUP, NEST }

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
    chooser.currentDirectory = appDir().also { it.mkdirs() }
    if (extensions.isNotEmpty()) {
        chooser.fileFilter = FileNameExtensionFilter(filterDescription, *extensions)
    }
    if (suggestedName != null) chooser.selectedFile = File(chooser.currentDirectory, suggestedName)
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

// ======================================================================
// App shell
// ======================================================================

@Composable
fun DesktopApp(state: DesktopState) {
    val settings by state.settings.collectAsState()
    val persian = DesktopStrings.isPersian(settings.language)
    IBebelsTheme(persian) {
        var tab by remember { mutableStateOf(Tab.PARTS) }
        val finished by state.finishedRuns.collectAsState()
        LaunchedEffect(finished) { if (finished > 0) tab = Tab.NEST }
        val message by state.message.collectAsState()
        LaunchedEffect(message) {
            if (message != null) {
                delay(4000)
                state.clearMessage()
            }
        }

        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(state, tab) { tab = it }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Ib.Bg)
                ) {
                    when (tab) {
                        Tab.PARTS -> PartsScreen(state)
                        Tab.SETUP -> SetupScreen(state)
                        Tab.NEST -> NestScreen(state)
                    }
                }
            }
            message?.let { text ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .clip(Ib.PillShape)
                        .background(Ib.CardHi)
                        .border(BorderStroke(1.dp, Ib.Orange), Ib.PillShape)
                        .clickable { state.clearMessage() }
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(text, color = Ib.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(state: DesktopState, tab: Tab, onTab: (Tab) -> Unit) {
    val settings by state.settings.collectAsState()
    val parts by state.parts.collectAsState()
    val result by state.nestResult.collectAsState()
    Column(
        Modifier
            .width(252.dp)
            .fillMaxHeight()
            .background(Ib.Bg2)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource("ibebels_logo.png"),
                contentDescription = "iBebels",
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("iBebels", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Ib.Text)
                Text(state.t("app_subtitle"), style = MaterialTheme.typography.bodySmall, color = Ib.TextDim)
            }
        }
        VGap(30)
        NavItem(Icons.Rounded.GridView, state.t("tab_parts"), tab == Tab.PARTS, parts.size.toString()) { onTab(Tab.PARTS) }
        VGap(6)
        NavItem(Icons.Rounded.Tune, state.t("tab_setup"), tab == Tab.SETUP, null) { onTab(Tab.SETUP) }
        VGap(6)
        NavItem(
            Icons.Rounded.Dashboard, state.t("tab_nest"), tab == Tab.NEST,
            result?.let { Fmt.f(it.overallUtilizationPercent, 0) + "%" }
        ) { onTab(Tab.NEST) }

        Spacer(Modifier.weight(1f))

        Text(state.t("language"), style = MaterialTheme.typography.labelMedium, color = Ib.TextDim)
        VGap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("فارسی", settings.language != "en") { state.updateSettings { it.copy(language = "fa") } }
            Pill("English", settings.language == "en") { state.updateSettings { it.copy(language = "en") } }
        }
        VGap(16)
        GhostButton(state.t("open_project"), Icons.Rounded.FolderOpen, Modifier.fillMaxWidth()) {
            chooseFile(state.t("open_project"), "iBebels project (*.json)", "json")?.let { state.loadProject(it) }
        }
        VGap(8)
        GhostButton(state.t("save_project"), Icons.Rounded.Save, Modifier.fillMaxWidth()) {
            chooseFile(
                state.t("save_project"), "iBebels project (*.json)", "json",
                save = true, suggestedName = "project.ibebels.json"
            )?.let { state.saveProject(it) }
        }
        VGap(14)
        Text("iBebels 2.1", style = MaterialTheme.typography.labelSmall, color = Ib.TextDim)
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, badge: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Ib.Orange.copy(alpha = 0.16f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) Ib.Orange.copy(alpha = 0.55f) else Color.Transparent), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Ib.Orange else Ib.TextDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (selected) Ib.Text else Ib.TextDim,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) Badge(badge, if (selected) Ib.Orange else Ib.Sky)
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Ib.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ib.TextDim)
        }
        actions()
    }
}

// ======================================================================
// Parts
// ======================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartsScreen(state: DesktopState) {
    val parts by state.parts.collectAsState()
    var editing by remember { mutableStateOf<PartInput?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val totalPieces = parts.sumOf { it.quantity }
    val subtitle = "${parts.size} ${state.t("types")} • $totalPieces ${state.t("pieces")}"

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        ScreenHeader(state.t("tab_parts"), if (parts.isEmpty()) null else subtitle) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (parts.isNotEmpty()) {
                    RoundIconButton(Icons.Rounded.DeleteOutline, state.t("clear_all"), Ib.Red) { confirmClear = true }
                }
                GhostButton(state.t("add_circle"), Icons.Rounded.Circle) {
                    editing = PartInput(0, "", PartShape.CIRCLE, 100.0, 100.0, 1, Material.MILD_STEEL, 3.0)
                }
                GhostButton(state.t("add_rect"), Icons.Rounded.Square) {
                    editing = PartInput(0, "", PartShape.RECTANGLE, 200.0, 100.0, 1, Material.MILD_STEEL, 3.0)
                }
                LaserButton(state.t("import_dxf"), Icons.Rounded.UploadFile) {
                    chooseFile(state.t("import_dxf"), "DXF (*.dxf)", "dxf", "DXF")?.let { state.importDxf(it) }
                }
            }
        }

        if (parts.isEmpty()) {
            EmptyHero(
                title = state.t("no_parts_title"),
                body = state.t("no_parts"),
                action = state.t("import_dxf")
            ) {
                chooseFile(state.t("import_dxf"), "DXF (*.dxf)", "dxf", "DXF")?.let { state.importDxf(it) }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 330.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(parts, key = { it.id }) { part ->
                    PartCard(state, part, onEdit = { editing = part })
                }
            }
        }
    }

    editing?.let { part ->
        PartEditorDialog(state, part, onDismiss = { editing = null }) {
            state.upsertPart(it)
            editing = null
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Ib.Card,
            shape = Ib.CardShape,
            title = { Text(state.t("clear_all"), color = Ib.Text) },
            text = { Text(state.t("confirm_clear"), color = Ib.TextDim) },
            confirmButton = {
                GhostButton(state.t("yes")) {
                    state.clearParts()
                    confirmClear = false
                }
            },
            dismissButton = { GhostButton(state.t("no")) { confirmClear = false } }
        )
    }
}

@Composable
private fun EmptyHero(title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Image(
                painter = painterResource("ibebels_logo.png"),
                contentDescription = null,
                modifier = Modifier.size(150.dp).clip(RoundedCornerShape(34.dp)).alpha(0.95f)
            )
            VGap(24)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ib.Text)
            VGap(8)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Ib.TextDim, textAlign = TextAlign.Center)
            VGap(24)
            LaserButton(action, Icons.Rounded.UploadFile, onClick = onAction)
        }
    }
}

@Composable
private fun PartCard(state: DesktopState, part: PartInput, onEdit: () -> Unit) {
    val color = partColor(parts_colorIndex(state, part))
    SoftCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(
                    Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Ib.Bg2)
                        .border(BorderStroke(1.dp, Ib.Line), RoundedCornerShape(18.dp))
                ) {
                    drawPartPreview(part.shape, part.widthMm, part.heightMm, part.outline, color, Offset.Zero, size)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        part.name.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Ib.Text,
                        maxLines = 1
                    )
                    val size = if (part.shape == PartShape.CIRCLE) "Ø ${Fmt.mm(part.widthMm)} mm"
                    else "${Fmt.mm(part.widthMm)} × ${Fmt.mm(part.heightMm)} mm"
                    Text(size, style = MaterialTheme.typography.bodyMedium, color = Ib.TextDim)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge(
                            when (part.shape) {
                                PartShape.CIRCLE -> state.t("circle")
                                PartShape.DXF -> "DXF"
                                else -> state.t("rectangle")
                            },
                            color
                        )
                        part.outline?.let { o ->
                            if (o.holes.isNotEmpty()) Badge("${o.holes.size} ${state.t("holes")}", Ib.Sky)
                            if (o.cutouts.isNotEmpty()) Badge("${o.cutouts.size} ${state.t("cutouts")}", Ib.Green)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIconButton(Icons.Rounded.Edit, state.t("edit"), onClick = onEdit)
                    RoundIconButton(Icons.Rounded.DeleteOutline, state.t("delete"), Ib.Red) { state.deletePart(part) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundIconButton(Icons.Rounded.Remove, "-") { state.setQuantity(part, part.quantity - 1) }
                Text(
                    part.quantity.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Ib.Text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 44.dp)
                )
                RoundIconButton(Icons.Rounded.Add, "+") { state.setQuantity(part, part.quantity + 1) }
                Spacer(Modifier.weight(1f))
                Pill(state.t("allow_rotation"), part.allowedRotations.size > 1) {
                    state.upsertPart(
                        part.copy(
                            allowedRotations = if (part.allowedRotations.size > 1) setOf(0) else setOf(0, 90, 180, 270)
                        )
                    )
                }
            }
        }
    }
}

/** Colour index of a part = its position in the list, matching the nest colours. */
private fun parts_colorIndex(state: DesktopState, part: PartInput): Int =
    state.parts.value.indexOfFirst { it.id == part.id }.coerceAtLeast(0)

@Composable
private fun PartEditorDialog(
    state: DesktopState,
    part: PartInput,
    onDismiss: () -> Unit,
    onSave: (PartInput) -> Unit
) {
    var name by remember { mutableStateOf(part.name) }
    var shape by remember { mutableStateOf(part.shape) }
    var width by remember { mutableStateOf(part.widthMm) }
    var height by remember { mutableStateOf(part.heightMm) }
    var quantity by remember { mutableIntStateOf(part.quantity) }
    var thickness by remember { mutableStateOf(part.thicknessMm) }
    var priority by remember { mutableIntStateOf(part.priority) }
    var rotations by remember { mutableStateOf(part.allowedRotations) }
    val isDxf = part.shape == PartShape.DXF
    val isNew = part.id == 0L

    val h = if (shape == PartShape.CIRCLE) width else height
    val valid = name.isNotBlank() && width > 0 && h > 0 && quantity > 0 && thickness > 0 && rotations.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ib.Card,
        shape = Ib.CardShape,
        title = { Text(if (isNew) state.t("new_part") else state.t("edit_part"), color = Ib.Text) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).widthIn(min = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextInput(state.t("name"), name, Modifier.fillMaxWidth()) { name = it }
                if (!isDxf) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(state.t("rectangle"), shape == PartShape.RECTANGLE) { shape = PartShape.RECTANGLE }
                        Pill(state.t("circle"), shape == PartShape.CIRCLE) { shape = PartShape.CIRCLE }
                    }
                    if (shape == PartShape.CIRCLE) {
                        DecimalField(state.t("diameter"), width, Modifier.fillMaxWidth()) { width = it }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DecimalField(state.t("width"), width, Modifier.weight(1f)) { width = it }
                            DecimalField(state.t("height"), height, Modifier.weight(1f)) { height = it }
                        }
                    }
                } else {
                    Text(
                        "${Fmt.mm(part.widthMm)} × ${Fmt.mm(part.heightMm)} mm",
                        color = Ib.TextDim, style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IntegerField(state.t("quantity"), quantity, Modifier.weight(1f)) { quantity = it }
                    DecimalField(state.t("thickness"), thickness, Modifier.weight(1f)) { thickness = it }
                    IntegerField(state.t("priority"), priority, Modifier.weight(1f)) { priority = it }
                }
                Text(state.t("rotations"), color = Ib.TextDim, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (deg in listOf(0, 90, 180, 270)) {
                        Pill("$deg°", deg in rotations) {
                            rotations = if (deg in rotations) rotations - deg else rotations + deg
                        }
                    }
                }
            }
        },
        confirmButton = {
            LaserButton(state.t("save"), enabled = valid) {
                val w = width
                val hh = if (shape == PartShape.CIRCLE) w else height
                onSave(
                    part.copy(
                        name = name.trim(), shape = shape, widthMm = w, heightMm = hh,
                        quantity = quantity, thicknessMm = thickness, priority = priority,
                        allowedRotations = rotations
                    )
                )
            }
        },
        dismissButton = { GhostButton(state.t("cancel"), onClick = onDismiss) }
    )
}

// ======================================================================
// Setup
// ======================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupScreen(state: DesktopState) {
    val s by state.settings.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenHeader(state.t("tab_setup"))

        SoftCard(Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle(state.t("machine") + " & " + state.t("sheet"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in MachinePreset.entries) {
                        Pill(m.displayName, s.machinePreset == m) { state.updateSettings { it.copy(machinePreset = m) } }
                    }
                }
                Text(state.t("sheet_size"), color = Ib.TextDim, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in SheetPreset.entries) {
                        Pill(
                            if (p == SheetPreset.CUSTOM) "…" else p.displayName,
                            s.sheetPreset == p
                        ) {
                            val pw = p.widthMm
                            val pl = p.lengthMm
                            state.updateSettings {
                                if (pw != null && pl != null) {
                                    it.copy(sheetPreset = p, sheetWidthMm = pw, sheetLengthMm = pl)
                                } else it.copy(sheetPreset = p)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DecimalField(state.t("sheet_width"), s.sheetWidthMm, Modifier.weight(1f)) { v ->
                        state.updateSettings { it.copy(sheetWidthMm = v, sheetPreset = presetFor(v, it.sheetLengthMm)) }
                    }
                    DecimalField(state.t("sheet_length"), s.sheetLengthMm, Modifier.weight(1f)) { v ->
                        state.updateSettings { it.copy(sheetLengthMm = v, sheetPreset = presetFor(it.sheetWidthMm, v)) }
                    }
                    DecimalField(state.t("sheet_thickness"), s.sheetThicknessMm, Modifier.weight(1f)) { v ->
                        state.updateSettings { it.copy(sheetThicknessMm = v) }
                    }
                    IntegerField(state.t("sheet_count"), s.sheetCount, Modifier.weight(1f)) { v ->
                        state.updateSettings { it.copy(sheetCount = v.coerceIn(1, 999)) }
                    }
                }
                Text(state.t("material"), color = Ib.TextDim, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in Material.entries) {
                        Pill(m.displayName, s.sheetMaterial == m) { state.updateSettings { it.copy(sheetMaterial = m) } }
                    }
                }
            }
        }

        SoftCard(Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle(state.t("nesting"))
                Text(state.t("mode"), color = Ib.TextDim, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(state.t("mode_auto"), s.nestMode == NestMode.AUTO) { state.updateSettings { it.copy(nestMode = NestMode.AUTO) } }
                    Pill(state.t("mode_true"), s.nestMode == NestMode.TRUE_SHAPE) { state.updateSettings { it.copy(nestMode = NestMode.TRUE_SHAPE) } }
                    Pill(state.t("mode_box"), s.nestMode == NestMode.FAST_BOX) { state.updateSettings { it.copy(nestMode = NestMode.FAST_BOX) } }
                }
                Text(
                    state.t(
                        when (s.nestMode) {
                            NestMode.AUTO -> "mode_hint_auto"
                            NestMode.TRUE_SHAPE -> "mode_hint_true"
                            NestMode.FAST_BOX -> "mode_hint_box"
                        }
                    ),
                    color = Ib.TextDim, style = MaterialTheme.typography.bodySmall
                )
                if (s.nestMode != NestMode.FAST_BOX) {
                    Text(state.t("precision"), color = Ib.TextDim, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(state.t("prec_auto"), s.precisionMm == 0.0) { state.updateSettings { it.copy(precisionMm = 0.0) } }
                        Pill(state.t("prec_fine") + " 1", s.precisionMm == 1.0) { state.updateSettings { it.copy(precisionMm = 1.0) } }
                        Pill(state.t("prec_normal") + " 2", s.precisionMm == 2.0) { state.updateSettings { it.copy(precisionMm = 2.0) } }
                        Pill(state.t("prec_fast") + " 3", s.precisionMm == 3.0) { state.updateSettings { it.copy(precisionMm = 3.0) } }
                    }
                }
                ToggleRow(state.t("allow_rotation"), s.allowRotation) { v -> state.updateSettings { it.copy(allowRotation = v) } }
                if (s.allowRotation && s.nestMode != NestMode.FAST_BOX) {
                    Text(state.t("rot_step"), color = Ib.TextDim, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(state.t("rot_off"), s.rotationStepDeg == 0) { state.updateSettings { it.copy(rotationStepDeg = 0) } }
                        for (d in listOf(45, 30, 15)) {
                            Pill("$d°", s.rotationStepDeg == d) { state.updateSettings { it.copy(rotationStepDeg = d) } }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DecimalField(state.t("kerf"), s.kerfMm, Modifier.weight(1f)) { v -> state.updateSettings { it.copy(kerfMm = v.coerceAtLeast(0.0)) } }
                    DecimalField(state.t("gap"), s.gapMm, Modifier.weight(1f)) { v -> state.updateSettings { it.copy(gapMm = v.coerceAtLeast(0.0)) } }
                    DecimalField(state.t("margin"), s.marginMm, Modifier.weight(1f)) { v -> state.updateSettings { it.copy(marginMm = v.coerceAtLeast(0.0)) } }
                }
            }
        }

        SoftCard(Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle(state.t("cutting"))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DecimalField(state.t("cutting_speed"), s.cuttingSpeedMmPerMin, Modifier.weight(1f)) { v ->
                        state.updateSettings { it.copy(cuttingSpeedMmPerMin = v.coerceAtLeast(0.0)) }
                    }
                    DecimalField(state.t("pierce_time"), s.pierceTimeSec, Modifier.weight(1f)) { v ->
                        state.updateSettings { it.copy(pierceTimeSec = v.coerceAtLeast(0.0)) }
                    }
                }
            }
        }
    }
}

private fun presetFor(w: Double, l: Double): SheetPreset =
    SheetPreset.entries.firstOrNull { it.widthMm == w && it.lengthMm == l } ?: SheetPreset.CUSTOM

// ======================================================================
// Nest
// ======================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NestScreen(state: DesktopState) {
    val s by state.settings.collectAsState()
    val parts by state.parts.collectAsState()
    val result by state.nestResult.collectAsState()
    val error by state.nestError.collectAsState()
    val nesting by state.nesting.collectAsState()
    val progress by state.progress.collectAsState()
    var sheetIndex by remember { mutableIntStateOf(0) }
    val persian = state.isPersian

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        ScreenHeader(state.t("tab_nest"), if (parts.isEmpty()) state.t("hint_first") else null) {
            if (nesting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0.02f, 1f) },
                        modifier = Modifier.width(180.dp).height(8.dp).clip(Ib.PillShape),
                        color = Ib.Orange,
                        trackColor = Ib.Line
                    )
                    GhostButton(state.t("cancel_nest"), Icons.Rounded.Stop) { state.cancelNesting() }
                }
            } else {
                LaserButton(state.t("run_nest"), Icons.Rounded.PlayArrow, enabled = parts.isNotEmpty()) {
                    sheetIndex = 0
                    state.runNesting()
                }
            }
        }

        error?.let {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(Ib.ControlShape)
                    .background(Color(0xFF4A1620)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, null, tint = Ib.Red)
                Spacer(Modifier.width(10.dp))
                Text(it, color = Color(0xFFFFD6DB))
            }
        }

        val r = result
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Dashboard, null, tint = Ib.Line, modifier = Modifier.size(96.dp))
                    VGap(12)
                    Text(
                        if (nesting) state.t("nesting_running") else state.t("hint_first"),
                        color = Ib.TextDim, style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            ResultView(state, r, sheetIndex, persian) { sheetIndex = it }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultView(
    state: DesktopState,
    r: NestResult,
    sheetIndex: Int,
    persian: Boolean,
    onSheet: (Int) -> Unit
) {
    val s by state.settings.collectAsState()
    val idx = sheetIndex.coerceIn(0, (r.sheets.size - 1).coerceAtLeast(0))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        UtilizationTile(state.t("utilization"), r.overallUtilizationPercent)
        StatTile(state.t("sheets"), r.sheetsUsed.toString(), Modifier.widthIn(min = 120.dp))
        StatTile(
            state.t("placed"), "${r.totalPartsPlaced} / ${r.totalPartsRequested}",
            Modifier.widthIn(min = 140.dp),
            if (r.totalPartsPlaced == r.totalPartsRequested) Ib.Green else Ib.Red
        )
        StatTile(state.t("cut_length"), Fmt.f(r.totalCutLengthMm / 1000.0, 1) + " m", Modifier.widthIn(min = 130.dp), Ib.Sky)
        StatTile(state.t("est_time"), formatMinutes(r.estimatedCutMinutes, persian), Modifier.widthIn(min = 130.dp), Ib.Sky)
        StatTile(state.t("pierces"), r.totalPierces.toString(), Modifier.widthIn(min = 110.dp), Ib.Sky)
        if (r.sheetsWeightKg > 0) {
            StatTile(state.t("weight"), Fmt.f(r.sheetsWeightKg, 1) + " kg", Modifier.widthIn(min = 120.dp), Ib.Steel)
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (r.sheets.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    r.sheets.forEachIndexed { i, sh ->
                        Pill(
                            state.tf("sheet_n", sh.index + 1) + " • " + Fmt.f(sh.utilizationPercent, 0) + "%",
                            i == idx
                        ) { onSheet(i) }
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(Ib.CardShape)
                    .background(Ib.Sheet)
                    .border(BorderStroke(1.dp, Ib.Line), Ib.CardShape)
            ) {
                val sheet = r.sheets.getOrNull(idx)
                if (sheet != null) {
                    SheetViewer(
                        sheet = sheet,
                        sheetWidthMm = s.sheetWidthMm,
                        sheetLengthMm = s.sheetLengthMm,
                        marginMm = s.marginMm,
                        remnantLabel = state.t("remnant"),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    state.t("zoom_hint"),
                    color = Ib.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
                )
            }
        }

        Column(
            Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SoftCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(state.t("export"))
                    GhostButton(state.t("export_dxf"), Icons.Rounded.ContentCut, Modifier.fillMaxWidth()) {
                        chooseFile(state.t("export_dxf"), "DXF (*.dxf)", "dxf", save = true, suggestedName = "iBebels-nest.dxf")
                            ?.let { state.exportDxf(it) }
                    }
                    GhostButton(state.t("export_report"), Icons.Rounded.Description, Modifier.fillMaxWidth()) {
                        chooseFile(state.t("export_report"), "HTML (*.html)", "html", save = true, suggestedName = "iBebels-report.html")
                            ?.let { state.exportReport(it) }
                    }
                    GhostButton(state.t("export_csv"), Icons.Rounded.Download, Modifier.fillMaxWidth()) {
                        chooseFile(state.t("export_csv"), "CSV (*.csv)", "csv", save = true, suggestedName = "iBebels-cutlist.csv")
                            ?.let { state.exportCsv(it) }
                    }
                    GhostButton(state.t("export_svg"), Icons.Rounded.Download, Modifier.fillMaxWidth()) {
                        chooseFile(state.t("export_svg"), "SVG (*.svg)", "svg", save = true, suggestedName = "iBebels-layout.svg")
                            ?.let { state.exportSvg(it) }
                    }
                }
            }

            val sheet = r.sheets.getOrNull(idx)
            if (sheet != null) {
                SoftCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(state.t("parts_on_sheet"))
                        val groups = sheet.placed.groupBy { it.colorIndex }.toSortedMap()
                        for ((color, list) in groups) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(partColor(color)))
                                Spacer(Modifier.width(10.dp))
                                Text(list.first().name, color = Ib.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("× ${list.size}", color = Ib.TextDim, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        sheet.remnant?.let {
                            VGap(4)
                            Text(
                                state.t("remnant") + ": " + Fmt.mm(it.widthMm) + " × " + Fmt.mm(it.heightMm) + " mm",
                                color = Ib.Green, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (r.unplaced.isNotEmpty()) {
                SoftCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null, tint = Ib.Red, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(state.t("unplaced"), color = Ib.Red, style = MaterialTheme.typography.titleSmall)
                        }
                        for (u in r.unplaced) {
                            Text("${u.name} × ${u.quantity}", color = Ib.Text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                state.t(
                                    when (u.reason) {
                                        UnplacedReason.INVALID -> "reason_invalid"
                                        UnplacedReason.NO_USABLE_AREA -> "reason_no_area"
                                        UnplacedReason.NO_FREE_SPACE -> "reason_no_space"
                                        UnplacedReason.DOES_NOT_FIT -> "reason_no_fit"
                                    }
                                ),
                                color = Ib.TextDim, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Text(
                "${state.t("engine")}: ${r.engineLabel} • ${state.t("took")} ${r.elapsedMs} ms",
                color = Ib.TextDim, style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Utilization with a ring gauge. */
@Composable
private fun UtilizationTile(label: String, percent: Double) {
    val color = when {
        percent >= 75 -> Ib.Green
        percent >= 55 -> Ib.Amber
        else -> Ib.Orange
    }
    Row(
        Modifier
            .clip(Ib.CardShape)
            .background(Ib.Card)
            .border(BorderStroke(1.dp, Ib.Line), Ib.CardShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8f
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(Ib.Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    color, -90f, (percent / 100.0 * 360.0).toFloat().coerceIn(0f, 360f), false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Text(Fmt.f(percent, 0) + "%", color = color, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(Fmt.f(percent, 1) + "%", style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Ib.TextDim)
        }
    }
}
