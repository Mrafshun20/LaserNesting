package com.mrafshun20.lasernesting.desktop

import com.mrafshun20.lasernesting.data.MachinePreset
import com.mrafshun20.lasernesting.data.Material
import com.mrafshun20.lasernesting.data.PartOutlineJson
import com.mrafshun20.lasernesting.data.PartShape
import com.mrafshun20.lasernesting.data.SheetPreset
import com.mrafshun20.lasernesting.dxf.DxfParser
import com.mrafshun20.lasernesting.dxf.toPartOutline
import com.mrafshun20.lasernesting.export.ExportCore
import com.mrafshun20.lasernesting.nesting.NestMode
import com.mrafshun20.lasernesting.nesting.NestOptions
import com.mrafshun20.lasernesting.nesting.NestResult
import com.mrafshun20.lasernesting.nesting.NestingEngine
import com.mrafshun20.lasernesting.nesting.PartInput
import com.mrafshun20.lasernesting.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Desktop settings. Persian is the default language. */
data class DesktopSettings(
    val machinePreset: MachinePreset = MachinePreset.BYSTAR_FIBER_3015,
    val sheetPreset: SheetPreset = SheetPreset.SHEET_2000x1000,
    val sheetWidthMm: Double = 2000.0,
    val sheetLengthMm: Double = 1000.0,
    val sheetMaterial: Material = Material.MILD_STEEL,
    val sheetThicknessMm: Double = 3.0,
    val sheetCount: Int = 1,
    val kerfMm: Double = 0.2,
    val gapMm: Double = 3.0,
    val marginMm: Double = 10.0,
    val allowRotation: Boolean = true,
    val cuttingSpeedMmPerMin: Double = 6000.0,
    /** "fa" (default), "en", or "system". */
    val language: String = "fa",
    val nestMode: NestMode = NestMode.AUTO,
    /** Raster cell for true-shape nesting in mm; 0 = automatic. */
    val precisionMm: Double = 0.0,
    /** Extra rotation step for DXF parts (0 = off). */
    val rotationStepDeg: Int = 0,
    val pierceTimeSec: Double = 0.5
)

/** Folder for the autosave and default project (~/iBebels). */
fun appDir(): File = File(System.getProperty("user.home") ?: ".", "iBebels")

/**
 * Desktop state: parts, settings, nesting result, messages. Parts are core
 * [PartInput] values (with their true DXF outline). Everything is autosaved
 * to ~/iBebels/autosave.json and restored on start.
 */
class DesktopState {
    private val _parts = MutableStateFlow<List<PartInput>>(emptyList())
    val parts: StateFlow<List<PartInput>> = _parts.asStateFlow()

    private val _settings = MutableStateFlow(DesktopSettings())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    private val _nestResult = MutableStateFlow<NestResult?>(null)
    val nestResult: StateFlow<NestResult?> = _nestResult.asStateFlow()

    private val _nestError = MutableStateFlow<String?>(null)
    val nestError: StateFlow<String?> = _nestError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _nesting = MutableStateFlow(false)
    val nesting: StateFlow<Boolean> = _nesting.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /** Increments each time a nesting run finishes, so the UI can open the Nest tab. */
    private val _finishedRuns = MutableStateFlow(0)
    val finishedRuns: StateFlow<Int> = _finishedRuns.asStateFlow()

    private val cancelFlag = AtomicBoolean(false)
    private var nextId = 1L
    private val autosaveFile: File get() = File(appDir(), "autosave.json")

    init {
        if (autosaveFile.exists()) loadProject(autosaveFile, silent = true)
    }

    fun t(key: String): String = DesktopStrings.get(key, _settings.value.language)

    /** Translated string with %s / %d arguments (ASCII digits always). */
    fun tf(key: String, vararg args: Any): String =
        String.format(Locale.ROOT, t(key), *args)

    val isPersian: Boolean get() = DesktopStrings.isPersian(_settings.value.language)

    private fun autosave() {
        saveProject(autosaveFile, silent = true)
    }

    fun updateSettings(transform: (DesktopSettings) -> DesktopSettings) {
        _settings.update(transform)
        _nestResult.value = null
        autosave()
    }

    fun upsertPart(part: PartInput) {
        _parts.update { list ->
            val existing = list.indexOfFirst { it.id == part.id && part.id != 0L }
            if (existing >= 0) {
                list.toMutableList().also { it[existing] = part }
            } else {
                val withId = if (part.id == 0L) part.copy(id = nextId++) else part
                if (part.id != 0L) nextId = maxOf(nextId, part.id + 1)
                list + withId
            }
        }
        _nestResult.value = null
        autosave()
    }

    fun setQuantity(part: PartInput, quantity: Int) {
        upsertPart(part.copy(quantity = quantity.coerceIn(1, 99_999)))
    }

    fun deletePart(part: PartInput) {
        _parts.update { it.filterNot { p -> p.id == part.id } }
        _nestResult.value = null
        autosave()
    }

    fun clearParts() {
        _parts.value = emptyList()
        _nestResult.value = null
        autosave()
    }

    /** Imports parts from a DXF file on disk. */
    fun importDxf(file: File) {
        val result = runCatching { DxfParser.parseFile(file) }.getOrElse {
            _message.value = t("dxf_read_error")
            return
        }
        if (result.parts.isEmpty()) {
            _message.value = result.warnings.firstOrNull() ?: t("dxf_no_parts")
            return
        }
        val s = _settings.value
        val inputs = result.parts.map { dxfPart ->
            PartInput(
                id = nextId++,
                name = dxfPart.name,
                shape = PartShape.DXF,
                widthMm = dxfPart.widthMm,
                heightMm = dxfPart.heightMm,
                quantity = 1,
                material = s.sheetMaterial,
                thicknessMm = s.sheetThicknessMm,
                allowedRotations = setOf(0, 90, 180, 270),
                priority = 0,
                outline = dxfPart.toPartOutline()
            )
        }
        _parts.update { it + inputs }
        _nestResult.value = null
        _message.value = tf("dxf_imported", inputs.size, file.name)
        autosave()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun postMessage(text: String) {
        _message.value = text
    }

    fun cancelNesting() {
        cancelFlag.set(true)
    }

    /** Runs the nesting on a background thread so the window stays responsive. */
    fun runNesting() {
        if (_nesting.value) return
        val s = _settings.value
        val currentParts = _parts.value
        if (currentParts.isEmpty()) {
            _nestError.value = t("need_parts")
            _nestResult.value = null
            return
        }
        if (s.sheetWidthMm <= 0 || s.sheetLengthMm <= 0) {
            _nestError.value = t("bad_sheet")
            _nestResult.value = null
            return
        }
        if (s.sheetWidthMm > s.machinePreset.maxWidthMm ||
            s.sheetLengthMm > s.machinePreset.maxLengthMm
        ) {
            _nestError.value = tf("too_big", s.machinePreset.displayName)
            _nestResult.value = null
            return
        }
        _nestError.value = null
        _nesting.value = true
        _progress.value = 0f
        cancelFlag.set(false)
        val options = NestOptions(
            sheetWidthMm = s.sheetWidthMm,
            sheetLengthMm = s.sheetLengthMm,
            sheetCount = s.sheetCount,
            kerfMm = s.kerfMm,
            gapMm = s.gapMm,
            marginMm = s.marginMm,
            allowRotationGlobal = s.allowRotation,
            cuttingSpeedMmPerMin = s.cuttingSpeedMmPerMin,
            mode = s.nestMode,
            precisionMm = s.precisionMm,
            rotationStepDeg = s.rotationStepDeg,
            pierceTimeSec = s.pierceTimeSec,
            sheetMaterial = s.sheetMaterial,
            sheetThicknessMm = s.sheetThicknessMm,
            isCancelled = { cancelFlag.get() },
            onProgress = { _progress.value = it }
        )
        val worker = Thread {
            try {
                val result = NestingEngine.nest(currentParts, options)
                if (cancelFlag.get()) {
                    _message.value = t("nest_cancelled")
                } else {
                    _nestResult.value = result
                    _finishedRuns.update { it + 1 }
                }
            } catch (e: Throwable) {
                _nestError.value = e.message ?: e.javaClass.simpleName
            } finally {
                _nesting.value = false
            }
        }
        worker.isDaemon = true
        worker.start()
    }

    // ---- Exports ----

    private fun withResult(block: (NestResult, DesktopSettings) -> Unit) {
        val r = _nestResult.value ?: return
        runCatching { block(r, _settings.value) }
            .onFailure { _message.value = it.message ?: t("project_error") }
    }

    fun exportCsv(file: File) = withResult { r, s ->
        file.writeText(ExportCore.cutListCsv(r, s.sheetWidthMm, s.sheetLengthMm))
        _message.value = tf("exported", file.name)
    }

    fun exportSvg(file: File) = withResult { r, s ->
        file.writeText(ExportCore.nestSvg(r, s.sheetWidthMm, s.sheetLengthMm))
        _message.value = tf("exported", file.name)
    }

    /** Writes one DXF per sheet: name.dxf for a single sheet, name_sheet1.dxf ... otherwise. */
    fun exportDxf(file: File) = withResult { r, s ->
        val base = file.name.removeSuffix(".dxf").removeSuffix(".DXF")
        val dir = file.parentFile ?: File(".")
        val single = r.sheets.size == 1
        for (sheet in r.sheets) {
            val name = if (single) "$base.dxf" else "${base}_sheet${sheet.index + 1}.dxf"
            File(dir, name).writeText(ExportCore.sheetDxf(sheet, s.sheetWidthMm, s.sheetLengthMm))
        }
        _message.value = tf("exported", if (single) "$base.dxf" else "${base}_sheet*.dxf")
    }

    fun exportReport(file: File) = withResult { r, s ->
        val material = "${s.sheetMaterial.displayName} ${Fmt.mm(s.sheetThicknessMm)} mm • " +
            "${Fmt.mm(s.sheetWidthMm)} × ${Fmt.mm(s.sheetLengthMm)} mm × ${s.sheetCount}"
        file.writeText(
            ExportCore.reportHtml(
                r, s.sheetWidthMm, s.sheetLengthMm, "iBebels", s.machinePreset.displayName,
                material, isPersian
            )
        )
        _message.value = tf("exported", file.name)
        runCatching { java.awt.Desktop.getDesktop().browse(file.toURI()) }
    }

    // ---- Project file (JSON) ----

    fun defaultProjectFile(): File = File(appDir(), "project.ibebels.json")

    fun saveProject(file: File, silent: Boolean = false): Boolean {
        return runCatching {
            val s = _settings.value
            val partsJson = JsonArray(
                _parts.value.map { part ->
                    Json.obj(
                        "id" to Json.num(part.id),
                        "name" to Json.str(part.name),
                        "shape" to Json.str(part.shape.name),
                        "widthMm" to Json.num(part.widthMm),
                        "heightMm" to Json.num(part.heightMm),
                        "quantity" to Json.num(part.quantity),
                        "material" to Json.str(part.material.name),
                        "thicknessMm" to Json.num(part.thicknessMm),
                        "allowRotation" to Json.bool(
                            part.allowedRotations != setOf(0)
                        ),
                        "allowedRotations" to Json.str(
                            part.allowedRotations.sorted().joinToString(",")
                        ),
                        "priority" to Json.num(part.priority),
                        "outlineJson" to Json.str(
                            PartOutlineJson.encode(part.outline) ?: ""
                        )
                    )
                }
            )
            val root = Json.obj(
                "version" to Json.num(2),
                "settings" to Json.obj(
                    "machinePreset" to Json.str(s.machinePreset.name),
                    "sheetPreset" to Json.str(s.sheetPreset.name),
                    "sheetWidthMm" to Json.num(s.sheetWidthMm),
                    "sheetLengthMm" to Json.num(s.sheetLengthMm),
                    "sheetMaterial" to Json.str(s.sheetMaterial.name),
                    "sheetThicknessMm" to Json.num(s.sheetThicknessMm),
                    "sheetCount" to Json.num(s.sheetCount),
                    "kerfMm" to Json.num(s.kerfMm),
                    "gapMm" to Json.num(s.gapMm),
                    "marginMm" to Json.num(s.marginMm),
                    "allowRotation" to Json.bool(s.allowRotation),
                    "cuttingSpeedMmPerMin" to Json.num(s.cuttingSpeedMmPerMin),
                    "language" to Json.str(s.language),
                    "nestMode" to Json.str(s.nestMode.name),
                    "precisionMm" to Json.num(s.precisionMm),
                    "rotationStepDeg" to Json.num(s.rotationStepDeg),
                    "pierceTimeSec" to Json.num(s.pierceTimeSec)
                ),
                "parts" to partsJson
            )
            file.parentFile?.mkdirs()
            file.writeText(Json.stringify(root))
            if (!silent) _message.value = t("project_saved")
            true
        }.getOrElse {
            if (!silent) _message.value = t("project_error")
            false
        }
    }

    fun loadProject(file: File, silent: Boolean = false): Boolean {
        return runCatching {
            val root = Json.parse(file.readText()) as? JsonObject
                ?: return@runCatching false
            val settingsObj = root.obj("settings")
            if (settingsObj != null) {
                _settings.value = DesktopSettings(
                    machinePreset = settingsObj.string("machinePreset")
                        ?.let { runCatching { MachinePreset.valueOf(it) }.getOrNull() }
                        ?: MachinePreset.BYSTAR_FIBER_3015,
                    sheetPreset = settingsObj.string("sheetPreset")
                        ?.let { runCatching { SheetPreset.valueOf(it) }.getOrNull() }
                        ?: SheetPreset.SHEET_2000x1000,
                    sheetWidthMm = settingsObj.double("sheetWidthMm") ?: 2000.0,
                    sheetLengthMm = settingsObj.double("sheetLengthMm") ?: 1000.0,
                    sheetMaterial = settingsObj.string("sheetMaterial")
                        ?.let { runCatching { Material.valueOf(it) }.getOrNull() }
                        ?: Material.MILD_STEEL,
                    sheetThicknessMm = settingsObj.double("sheetThicknessMm") ?: 3.0,
                    sheetCount = settingsObj.int("sheetCount") ?: 1,
                    kerfMm = settingsObj.double("kerfMm") ?: 0.2,
                    gapMm = settingsObj.double("gapMm") ?: 3.0,
                    marginMm = settingsObj.double("marginMm") ?: 10.0,
                    allowRotation = settingsObj.bool("allowRotation") ?: true,
                    cuttingSpeedMmPerMin = settingsObj.double("cuttingSpeedMmPerMin")
                        ?: 6000.0,
                    language = settingsObj.string("language") ?: "fa",
                    nestMode = settingsObj.string("nestMode")
                        ?.let { runCatching { NestMode.valueOf(it) }.getOrNull() }
                        ?: NestMode.AUTO,
                    precisionMm = settingsObj.double("precisionMm") ?: 0.0,
                    rotationStepDeg = settingsObj.int("rotationStepDeg") ?: 0,
                    pierceTimeSec = settingsObj.double("pierceTimeSec") ?: 0.5
                )
            }
            val loaded = mutableListOf<PartInput>()
            var maxId = 0L
            root.array("parts")?.items?.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val id = obj.double("id")?.toLong() ?: 0L
                maxId = maxOf(maxId, id)
                val shape = obj.string("shape")
                    ?.let { runCatching { PartShape.valueOf(it) }.getOrNull() }
                    ?: PartShape.RECTANGLE
                val material = obj.string("material")
                    ?.let { runCatching { Material.valueOf(it) }.getOrNull() }
                    ?: Material.MILD_STEEL
                val rotations = obj.string("allowedRotations")
                    ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                    ?.filter { it in setOf(0, 90, 180, 270) }?.toSet()
                    ?.ifEmpty { setOf(0) } ?: setOf(0, 90, 180, 270)
                val outlineJson = obj.string("outlineJson")
                loaded.add(
                    PartInput(
                        id = id,
                        name = obj.string("name") ?: "Part",
                        shape = shape,
                        widthMm = obj.double("widthMm") ?: 0.0,
                        heightMm = obj.double("heightMm") ?: 0.0,
                        quantity = obj.int("quantity") ?: 1,
                        material = material,
                        thicknessMm = obj.double("thicknessMm") ?: 3.0,
                        allowedRotations = rotations,
                        priority = obj.int("priority") ?: 0,
                        outline = PartOutlineJson.decode(outlineJson)
                    )
                )
            }
            nextId = maxId + 1
            _parts.value = loaded
            _nestResult.value = null
            if (!silent) _message.value = t("project_loaded")
            true
        }.getOrElse {
            if (!silent) _message.value = t("project_error")
            false
        }
    }
}

/** Formats a millimetre value without a trailing ".0" (ASCII digits always). */
fun formatMm(value: Double): String = Fmt.mm(value)

/** Formats a duration in minutes as "12 min" / "1 h 25 min" (or Persian). */
fun formatMinutes(minutes: Double, persian: Boolean = false): String {
    val h = if (persian) "ساعت" else "h"
    val m = if (persian) "دقیقه" else "min"
    if (minutes <= 0) return "0 $m"
    val totalSeconds = (minutes * 60).toInt()
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    return if (hours > 0) "$hours $h $mins $m" else "$mins $m"
}
